package l1j.server.server.model.npc.ai.action;

import java.util.Random;


import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.PathFinder;
import l1j.server.server.model.ai.core.PathNodeWorld;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.npc.ai.AstarProvider;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.shared.GameRandom;

/**
 * Retail-observation MOVE_AROUND adapter.
 *
 * Current behavior target:
 * - first MOVE_AROUND entry starts moving immediately.
 * - one wander segment is 3..6 tiles.
 * - after a segment finishes, wait 6000ms.
 * - then start the next 3..6 tile wander segment.
 * - when a stored wander path becomes dynamically blocked, do not wait 6s;
 *   immediately choose another wander direction.
 *
 * Static/dynamic physical movement is still delegated to SetMoving.
 */
public final class CMoveAroundAction {

    private static final long REST_DELAY_MS = 6_000L;
    private static final long BLOCKED_RETRY_MS = 300L;

    private static final int MIN_MOVE_DISTANCE = 3;
    private static final int MAX_MOVE_DISTANCE = 6;
    private static final int DESTINATION_ATTEMPTS = 12;

    private static final byte WAIT_NONE  = 0;
    private static final byte WAIT_REST  = 1;
    private static final byte WAIT_RETRY = 2;

    private static final double TWO_PI =
            Math.PI * 2.0d;

    private static final int[] DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };

    private static final int[] DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private CMoveAroundAction() {
    }

    public static void set(
            final L1NpcInstance creature) {

        if (creature == null
                || CCreatureController.currentAction(creature)
                        == CCreatureController.ACTION_MOVE_AROUND_NATIVE) {
            // Re-selecting an already owned random walk must not erase its
            // destination, remaining steps or 6-second rest deadline.
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_MOVE_AROUND_NATIVE);

        onStart(creature);
    }

    public static void onStart(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        SetMoving.clear(creature);
        CMoveToTargetWaitAction.clear(creature);
        clearDestination(creature);

        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster =
                    (L1MonsterInstance) creature;

            /*
             * First entry is immediate.
             * The 6-second rest begins only after one wander segment ends.
             */
            monster._desireMoveAroundDecisionAtMs = 0L;
            monster._desireMoveAroundWaitKind = WAIT_NONE;
            monster._desireMoveAroundCounter = 0;
        }
    }

    public static byte onTick(
            final L1NpcInstance creature) {

        if (!(creature instanceof L1MonsterInstance)) {
            return CCreatureController.RESULT_DONE;
        }

        final L1MonsterInstance monster =
                (L1MonsterInstance) creature;

        final long now =
                System.currentTimeMillis();

        // No extra timer: pickup-capable monsters discover ground items on
        // the existing wander action cadence, even after a segment/rest.
        if (CGetItemAction.prepareMonsterItem(monster, now)) {
            clearRuntime(monster);
            return CCreatureController.RESULT_DONE;
        }

        // The normal desire arbiter already switches to an ATTACKED/SEE
        // candidate when its value wins. Do not short-circuit MOVE_AROUND
        // merely because some lower-priority attack desire exists; that
        // would restart this action every 300ms without changing the winner.
        if (!CDesirePQ.isActive(monster)
                || !CDesirePQ.isMoveAroundTop(
                        monster,
                        now)) {

            clearRuntime(monster);
            return CCreatureController.RESULT_DONE;
        }

        /*
         * Check an opened blocked lane even during the 6-second wander rest.
         * Melee recovery claims a particular slot atomically. It does not
         * grant priority across all NPCs merely by their wakeup order.
         */
        if (CDesirePQ.hasBlockedAttack(monster)
                && tryResumeBlockedAttack(monster)) {

            clearRuntime(monster);
            return CCreatureController.RESULT_DONE;
        }

        if (hasDestination(monster)) {
            return moveToStoredDestination(
                    monster,
                    now);
        }

        final long decisionAt =
                monster._desireMoveAroundDecisionAtMs;

        if (decisionAt > now) {
            long sleep = decisionAt - now;

            /*
             * Keep the full 6-second rest semantics, but do not sleep through
             * a newly opened choke. The action wakes every 300ms only to test
             * the opening; it still starts the next random walk only when the
             * original 6-second deadline expires.
             */
            if (monster._desireMoveAroundWaitKind == WAIT_REST
                    && CDesirePQ.hasBlockedAttack(monster)
                    && sleep > BLOCKED_RETRY_MS) {

                sleep = BLOCKED_RETRY_MS;
            }

            CCreatureController.sleep(
                    monster,
                    sleep);

            return CCreatureController.RESULT_RUNNING;
        }

        monster._desireMoveAroundDecisionAtMs = 0L;
        monster._desireMoveAroundWaitKind = WAIT_NONE;

        /*
         * WAIT_REST expiry means exactly one thing: the normal 6-second pause
         * completed. If this NPC did not win an opened choke above, continue
         * the next 3..6 tile MOVE_AROUND cycle. ATTACK stays suppressed.
         * WAIT_RETRY likewise just re-rolls a wander vector.
         */
        return startRandomMove(
                monster,
                now);
    }

    /**
     * Pick a usable destination and consume the first movement tick
     * immediately. If a candidate is already hard-blocked, try another one in
     * the same action tick instead of entering the 6-second rest.
     */
    private static byte startRandomMove(
            final L1MonsterInstance monster,
            final long now) {


        for (int attempt = 0;
             attempt < DESTINATION_ATTEMPTS;
             attempt++) {

            if (!chooseRandomDestination(
                    monster,
                    GameRandom.current())) {

                continue;
            }

            final int beforeX = monster.getX();
            final int beforeY = monster.getY();

            final byte move =
                    SetMoving.tickToPoint(
                            monster,
                            monster._desireMoveAroundTargetX,
                            monster._desireMoveAroundTargetY);

            if (move == SetMoving.RUNNING) {
                return afterRunningStep(
                        monster,
                        beforeX,
                        beforeY,
                        now);
            }

            if (move == SetMoving.AT_DISTANCE) {
                clearDestination(monster);
                return restAfterMove(
                        monster,
                        now);
            }

            /*
             * DYNAMIC_BLOCKED / PATH_FAILED / INVALID:
             * discard this random vector and try another immediately.
             */
            clearDestination(monster);
            SetMoving.clear(monster);
        }

        /*
         * Every immediate candidate was unusable. This is not the normal
         * 6-second idle phase; retry shortly because the crowd can change.
         */
        scheduleDecision(
                monster,
                now,
                BLOCKED_RETRY_MS,
                WAIT_RETRY);

        return CCreatureController.RESULT_RUNNING;
    }

    private static boolean chooseRandomDestination(
            final L1MonsterInstance monster,
            final Random rnd) {

        final int distance =
                rnd.nextInt(
                        MIN_MOVE_DISTANCE,
                        MAX_MOVE_DISTANCE + 1);

        // A blocked ATTACK must not bias the entire random walk away from the
        // target: current ATTACK viability is rechecked before every step.
        final double angle = rnd.nextDouble(TWO_PI);

        final double vx =
                Math.cos(angle);

        final double vy =
                Math.sin(angle);

        final double norm =
                Math.max(
                        Math.abs(vx),
                        Math.abs(vy));

        if (norm <= 0.0d) {
            return false;
        }

        final int dx =
                (int) Math.round(
                        (vx / norm) * distance);

        final int dy =
                (int) Math.round(
                        (vy / norm) * distance);

        if (dx == 0 && dy == 0) {
            return false;
        }

        final int stepX =
                Integer.compare(dx, 0);

        final int stepY =
                Integer.compare(dy, 0);

        final int heading =
                vectorToHeading(
                        stepX,
                        stepY);

        if (heading < 0
                || monster.getMap() == null
                || !monster.getMap()
                        .isMovePassableGeoNpc(
                                monster.getX(),
                                monster.getY(),
                                heading)) {

            return false;
        }

        final int firstX =
                monster.getX() + DX[heading];

        final int firstY =
                monster.getY() + DY[heading];

        if (!COLLISION.canEnter(
                (short) monster.getMapId(),
                firstX,
                firstY,
                monster.getId())) {

            return false;
        }

        final int destX = monster.getX() + dx;
        final int destY = monster.getY() + dy;

        // Do not choose a goal that triggers L1's post-step home teleport.
        final int homeRadius = monster.getMovementDistance();
        if (homeRadius > 0) {
            final int homeX = monster.getHomeX();
            final int homeY = monster.getHomeY();
            if (homeX != 0 || homeY != 0) {
                final long hx = (long) destX - homeX;
                final long hy = (long) destY - homeY;
                final long radius = homeRadius;
                if (hx * hx + hy * hy > radius * radius) {
                    return false;
                }
            }
        }

        monster._desireMoveAroundTargetX = destX;
        monster._desireMoveAroundTargetY = destY;

        /* Keep the accepted random intent until it finishes or becomes
         * invalid. A collision slide never becomes a permanent new heading. */
        monster._randomMoveDirection = heading;
        monster._randomMoveDistance = distance;

        /*
         * New vector => fresh physical collision state.
         */
        SetMoving.clear(monster);

        return true;
    }

    private static byte moveToStoredDestination(
            final L1MonsterInstance monster,
            final long now) {

        final int tx =
                monster._desireMoveAroundTargetX;

        final int ty =
                monster._desireMoveAroundTargetY;

        if (monster.getX() == tx
                && monster.getY() == ty) {

            clearDestination(monster);
            return restAfterMove(
                    monster,
                    now);
        }

        final int beforeX = monster.getX();
        final int beforeY = monster.getY();

        final byte move =
                SetMoving.tickToPoint(
                        monster,
                        tx,
                        ty);

        if (move == SetMoving.RUNNING) {
            return afterRunningStep(
                    monster,
                    beforeX,
                    beforeY,
                    now);
        }

        if (move == SetMoving.AT_DISTANCE) {
            clearDestination(monster);
            return restAfterMove(
                    monster,
                    now);
        }

        /*
         * Random-walk path became unusable before arrival.
         * Do not enter the 6-second rest. Re-roll immediately.
         */
        clearDestination(monster);
        SetMoving.clear(monster);
        monster._desireMoveAroundDecisionAtMs = 0L;

        return startRandomMove(
                monster,
                now);
    }


    /**
     * The native random move chooses a new vector on its next decision.
     * Never turn an incidental physical collision slide into a persistent
     * clockwise/counterclockwise wander direction.
     */
    private static byte afterRunningStep(
            final L1MonsterInstance monster,
            final int beforeX,
            final int beforeY,
            final long now) {

        final int afterX = monster.getX();
        final int afterY = monster.getY();
        if (afterX == beforeX && afterY == beforeY) {
            return CCreatureController.RESULT_RUNNING;
        }

        // A teleport during the step invalidates the previous wander goal.
        if (Math.abs(afterX - beforeX) > 1
                || Math.abs(afterY - beforeY) > 1) {
            clearDestination(monster);
            SetMoving.clear(monster);
            monster._desireMoveAroundDecisionAtMs = 0L;
            monster._desireMoveAroundWaitKind = WAIT_NONE;
            return CCreatureController.RESULT_RUNNING;
        }

        final int remaining = monster._randomMoveDistance - 1;
        monster._randomMoveDistance = remaining;
        if (remaining <= 0) {
            clearDestination(monster);
            return restAfterMove(monster, now);
        }

        // A valid physical step is part of this 3..6-tile wander even when
        // collision avoidance changed its actual heading. Do NOT clear the
        // destination or change the 6s rest to a 300ms retry here.
        // SetMoving handles a genuinely blocked next step; only that failure
        // returns to startRandomMove() for a new vector.
        return CCreatureController.RESULT_RUNNING;
    }

    private static byte restAfterMove(
            final L1MonsterInstance monster,
            final long now) {

        SetMoving.clear(monster);

        scheduleDecision(
                monster,
                now,
                REST_DELAY_MS,
                WAIT_REST);

        return CCreatureController.RESULT_RUNNING;
    }

    private static void scheduleDecision(
            final L1MonsterInstance monster,
            final long now,
            final long delay,
            final byte waitKind) {

        monster._desireMoveAroundDecisionAtMs =
                now + delay;
        monster._desireMoveAroundWaitKind =
                waitKind;

        final long firstSleep =
                waitKind == WAIT_REST
                        && CDesirePQ.hasBlockedAttack(monster)
                        ? Math.min(delay, BLOCKED_RETRY_MS)
                        : delay;

        CCreatureController.sleep(
                monster,
                firstSleep);
    }

    private static boolean hasDestination(
            final L1NpcInstance creature) {

        return creature._desireMoveAroundTargetX != 0
                || creature._desireMoveAroundTargetY != 0;
    }

    private static void clearDestination(
            final L1NpcInstance creature) {

        creature._desireMoveAroundTargetX = 0;
        creature._desireMoveAroundTargetY = 0;
        creature._randomMoveDistance = 0;
        creature._randomMoveDirection = 0;
    }

    private static void clearRuntime(
            final L1MonsterInstance monster) {

        clearDestination(monster);
        SetMoving.clear(monster);
        monster._desireMoveAroundDecisionAtMs = 0L;
        monster._desireMoveAroundWaitKind = WAIT_NONE;
    }

    /**
     * Desire is evaluated against the CURRENT target/position, never just the
     * first tile where the chase got blocked. The L1 8-tile melee geometry is
     * the 2D adapter for native current-position move/action selection.
     */
    private static boolean tryResumeBlockedAttack(
            final L1MonsterInstance monster) {

        final L1Character target =
                CDesirePQ.blockedAttackTarget(monster);
        if (!validDirectionTarget(monster, target)) {
            // A dead/invisible/removed blocker cannot resume an ATTACK.
            // Retire ONLY that obsolete desire and its own reserved tile.
            // Unconditional releaseBlockedAttack() could cancel an already
            // published melee claim for a different newer target B.
            // Do not reset the L1 3..6-step walk or six-second rest.
            CDesirePQ.removeAttack(monster,
                    monster._desireBlockedAttackTargetId);
            return false;
        }

        final int ranged = Math.max(1,
                monster.getNpcTemplate().get_ranged());
        if (monster.isAttackPosition(target, ranged)) {
            CDesirePQ.releaseBlockedAttack(monster);
            return true;
        }

        // Every melee recovery must prove a CURRENT body-clear route to an
        // attack slot. A narrow local-search miss must not suppress the larger
        // opposite-entrance search, nor fall back to a stale straight first step.
        if (ranged == 1) {
            final boolean resumed = CMoveToTargetRelativeAction.GetMeleeAttackPosition(monster, target);
            if (SetMoving.debugTraceActor(monster)) {
                System.out.println("[CHASE-RESUME-V3] actor=" + monster.getId()
                        + " pos=" + monster.getX() + "," + monster.getY()
                        + " target=" + target.getX() + "," + target.getY()
                        + " resumed=" + resumed);
            }
            return resumed;
        }

        // A clear ordinary ranged lane remains the cheap recovery path.
        if (CDesirePQ.blockedAttackMode(monster)
                    == CDesirePQ.BLOCKED_ATTACK_LANE
                && isBlockedAttackLaneOpen(monster)) {
            CDesirePQ.releaseBlockedAttack(monster);
            return true;
        }
        // A stationary allied monster can block that exact first step while
        // a side route is open. Do not wait for it to die. The existing claimed
        // route is valid for ranged NPCs too; ATTACK takes over as soon as the
        // normal range/LOS condition is met, before reaching the melee slot.
        return CMoveToTargetRelativeAction.GetMeleeAttackPosition(monster, target);
    }

    private static int chebyshev(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {

        return Math.max(
                Math.abs(x1 - x0),
                Math.abs(y1 - y0));
    }

    /** Test a fresh route from the current position, not a historical choke. */
    private static boolean isBlockedAttackLaneOpen(
            final L1MonsterInstance monster) {

        final L1Character target =
                CDesirePQ.blockedAttackTarget(monster);

        if (!validDirectionTarget(
                monster,
                target)) {
            return true;
        }

        final int ranged =
                Math.max(
                        1,
                        monster.getNpcTemplate()
                                .get_ranged());

        if (monster.isAttackPosition(
                target,
                ranged)) {
            return true;
        }

        if (monster.getMap() == null) {
            return false;
        }

        final PathFinder pathFinder =
                AstarProvider.get();

        final PathFinder.Node next =
                pathFinder.searchTail(
                        monster,
                        target.getX(),
                        target.getY(),
                        monster.getMapId(),
                        true);

        if (next == null
                || (next.x == monster.getX()
                        && next.y == monster.getY())) {
            return false;
        }

        final int heading =
                pathFinder.calcheading(
                        monster.getX(),
                        monster.getY(),
                        next.x,
                        next.y);

        if (heading < 0
                || heading >= 8
                || !monster.getMap()
                        .isMovePassableGeoNpc(
                                monster.getX(),
                                monster.getY(),
                                heading)) {
            return false;
        }

        final int nextX =
                monster.getX() + DX[heading];
        final int nextY =
                monster.getY() + DY[heading];

        return COLLISION.canEnter(
                (short) monster.getMapId(),
                nextX,
                nextY,
                monster.getId());
    }

    private static boolean validDirectionTarget(
            final L1MonsterInstance monster,
            final L1Character target) {

        return monster != null
                && target != null
                && !target.isDead()
                && target.getCurrentHp() > 0
                && target.getMapId()
                        == monster.getMapId()
                && (!target.isInvisble()
                        || monster.getNpcTemplate().is_agrocoi());
    }

    private static int vectorToHeading(
            final int x,
            final int y) {

        for (int heading = 0;
             heading < 8;
             heading++) {

            if (DX[heading] == x
                    && DY[heading] == y) {

                return heading;
            }
        }

        return -1;
    }

    public static void onInterrupted(
            final L1NpcInstance creature) {

        if (creature instanceof L1MonsterInstance) {
            clearRuntime((L1MonsterInstance) creature);
        } else if (creature != null) {
            clearDestination(creature);
            SetMoving.clear(creature);
        }
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final byte result) {

        if (creature == null
                || CCreatureController.currentAction(creature)
                        != CCreatureController.ACTION_MOVE_AROUND_NATIVE) {
            // An old MOVE_AROUND completion cannot clear successor Action
            // movement state after an attack/skill takeover.
            return;
        }

        if (creature instanceof L1MonsterInstance) {
            clearRuntime(
                    (L1MonsterInstance) creature);
        } else {
            clearDestination(creature);
            SetMoving.clear(creature);
        }

        if (result
                == CCreatureController.RESULT_DONE) {

            // The completed wander no longer owns a walk/rest deadline.
            // Invalidate its queued timer and wake desire selection now; merely
            // setting THINK makes the CIO owner re-arm the old sleep value.
            CCreatureController.rethinkNow(creature);
        }
    }
}
