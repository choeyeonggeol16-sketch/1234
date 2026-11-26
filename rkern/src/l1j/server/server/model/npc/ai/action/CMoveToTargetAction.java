package l1j.server.server.model.npc.ai.action;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.ai.core.PathFinder;
import l1j.server.server.model.ai.core.PathNodeWorld;
import l1j.server.server.model.npc.ai.AstarProvider;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.NpcEventFactory;

public final class CMoveToTargetAction {

    private static final int PATH_FAIL_LIMIT = 30;
    private static final int TARGET_CLEAR_DISTANCE = 40;
    private static final byte RESULT_PATH_FAILED = 5;
    private static final byte RESULT_TARGET_TOO_FAR = 6;
    private static final long PATH_FAIL_DISPATCH_DELAY_MS = 1_000L;
    private static final long DYNAMIC_BLOCK_RETRY_MS = 300L;
    private static final int RESERVED_SLOT_BLOCK_RETRY_LIMIT = 3;
    // Covers one slow animation/tick without leaving a permanent reservation.
    private static final long RESERVED_SLOT_CLAIM_MS = 6_000L;
    private static final long FAILED_MELEE_POSITION_COOLDOWN_MS = 2_000L;
    private static final long PASSING_INTENT_MS = 6_000L;

    private static final int[] DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private CMoveToTargetAction() {
    }

    public static void set(
            final L1NpcInstance creature,
            final byte pendingAction) {

        if (creature == null) {
            return;
        }

        // A repeated desire decision for the same target is not a new chase.
        // SetMoving keeps the path segment and collision response for that
        // target under _moveTargetId. Re-entering onStart() here invalidates
        // the path and can restart an expensive A* search every wake.
        final L1Character currentTarget = creature.getTarget();
        if (CCreatureController.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET
                && currentTarget != null
                && currentTarget.getId() != 0
                && creature._moveTargetId == currentTarget.getId()) {
            // The next Action may change without resetting this target's path.
            CCreatureController.pendingAction(creature, pendingAction);
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_MOVE_TO_TARGET,
                pendingAction);

        onStart(creature);
    }

    public static void onStart(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        final int pathFindFailCount =
                creature._pathFindFailCount;

        // Retargeting can re-enter this same action without transitionFrom().
        // Retire an obsolete melee hand-off before the next movement tick.
        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster = (L1MonsterInstance) creature;
            AbandonObsoleteAttackLeader(monster, creature.getTarget());
            discardObsoleteAttackSlot(monster, creature.getTarget());
        }

        SetMoving.clearPassingDestination(creature);
        SetMoving.clear(creature);
        CMoveToTargetWaitAction.clear(creature);
        CMoveAction.resetMoveBlockedState(creature);

        creature._pathFindFailCount =
                pathFindFailCount;
    }

    public static byte onTick(
            final L1NpcInstance creature,
            final L1Character target,
            final boolean tryCombatAbility) {
        return onTick(creature, target, tryCombatAbility,
                CCreatureController.ACTION_MOVE_TO_TARGET);
    }

    static byte onTick(
            final L1NpcInstance creature,
            final L1Character target,
            final boolean tryCombatAbility,
            final byte expectedAction) {

        final int actionGeneration = creature.npcActionGeneration();

        // A target change or movement can happen while the previous chase
        // was sleeping or before its reserved-slot branch runs. Retire that
        // exact hand-off before ATTACK / ability / immobilized early exits.
        // A stale tick for the previous target must not clear its successor.
        if (creature instanceof L1MonsterInstance
                && creature.getTarget() == target) {
            discardObsoleteAttackSlot((L1MonsterInstance) creature, target);
        }

        if (target == null || creature.isDead()
                || creature.getTarget() != target) {
            // The old action tick cannot move or attack a retired target.
            // Its onEnd() owns only the old target's cleanup.
            return CCreatureController.RESULT_INVALID;
        }

        if (creature instanceof L1MonsterInstance) {
            if (target.getMapId() != creature.getMapId()) {
                // Do not clear the live target here. The captured target
                // may have been superseded while this chase tick ran.
                // onEnd() checks action and target ownership before cleanup.
                return CCreatureController.RESULT_INVALID;
            }

            final int targetDistance =
                    creature.getLocation()
                            .getTileLineDistance(
                                    target.getLocation());

            if (targetDistance >= TARGET_CLEAR_DISTANCE) {
                // The onEnd guard protects a newer target's ownership.
                return RESULT_TARGET_TOO_FAR;
            }
        }

        if (!validTarget(creature, target)) {
            return CCreatureController.RESULT_INVALID;
        }

        // Boost the captured ATTACK key once on this verified chase tick.
        if (creature instanceof L1MonsterInstance) {
            CDesirePQ.boostExecutedAttack((L1MonsterInstance) creature, target);
        }

        if (tryCombatAbility
                && CAttackAction.tryCombatAbility(
                        creature,
                        target)) {

            return CCreatureController.RESULT_RUNNING;
        }

        if (creature.isAttackPosition(
                target,
                creature.getNpcTemplate().get_ranged())) {

            creature._pathFindFailCount = 0;
            return CCreatureController.RESULT_DONE;
        }

        if (!creature.isMovable()) {
            return CCreatureController.RESULT_INVALID;
        }

        if (creature.발묶임상태()) {
            creature._pathFindFailCount = 0;
            return CCreatureController.RESULT_RUNNING;
        }

        if (CMoveToTargetAction.tryMoveToTargetTeleport(
                creature,
                target)) {
            return CCreatureController.RESULT_RUNNING;
        }

        // Distance/reach selection belongs to the native ActionBase role.
        // Preserve existing L1 ranged/LOS and 8-direction grid behavior.
        final int desiredDistance =
                CMoveToTargetActionBase.computeDistance(creature, target);

        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster =
                    (L1MonsterInstance) creature;

            // A claimed and still usable melee slot belongs to target A.
            // Another admitted ATTACK desire alone must not preempt it:
            // CNpcAI queues a genuine higher-priority winner safely.
            // Only blocked routing below may gate A and select another target.

            final byte resumeResult =
                    SetMoving(
                            monster,
                            target,
                            actionGeneration);

            if (resumeResult >= 0) {
                return resumeResult;
            }

            // Passing position was selected on an earlier blocked tick. Do not
            // make a second physical step in the same CIO callback.
            final long passing = SetMoving.getPassingDestination(
                    monster, target, System.currentTimeMillis());
            if (passing != SetMoving.NO_POS) {
                final int wx = SetMoving.unpackX(passing);
                final int wy = SetMoving.unpackY(passing);
                if (wx == monster.getX() && wy == monster.getY()) {
                    SetMoving.clearPassingDestination(monster);
                } else {
                    final int beforeX = monster.getX();
                    final int beforeY = monster.getY();
                    final byte code = SetMoving.tickToPoint(monster, wx, wy);
                    if (monster.isDead() || monster.getTarget() != target
                            || monster.npcActionGeneration() != actionGeneration
                            || CCreatureController.currentAction(monster) != expectedAction) {
                        SetMoving.clearPassingDestination(monster);
                        return CCreatureController.RESULT_INVALID;
                    }
                    if (code == SetMoving.RUNNING) {
                        if (beforeX != monster.getX() || beforeY != monster.getY()) {
                            UpdateAttackLeaderRelated(monster, target);
                        }
                        return CCreatureController.RESULT_RUNNING;
                    }
                    SetMoving.clearPassingDestination(monster);
                    if (code != SetMoving.AT_DISTANCE) {
                        CCreatureController.sleep(monster, DYNAMIC_BLOCK_RETRY_MS);
                        return CCreatureController.RESULT_RUNNING;
                    }
                }
            }
        }

        // The reserved-slot route may have yielded after its path scan.
        // Never continue the generic chase using an obsolete target snapshot.
        if (creature.isDead() || creature.getTarget() != target
                || creature.npcActionGeneration() != actionGeneration
                || CCreatureController.currentAction(creature) != expectedAction) {
            return CCreatureController.RESULT_INVALID;
        }

        // Relative chase obtains its preferred destination from the native
        // action role. SetMoving remains the only physical movement owner;
        // reserved attack-slot routing above is deliberately unchanged.
        final int stepBeforeX = creature.getX();
        final int stepBeforeY = creature.getY();
        final byte move =
                expectedAction == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                        ? SetMoving.tickRelative(creature, target, desiredDistance)
                        : SetMoving.tick(creature, target, desiredDistance);

        // Detect an actual A->B->A->B chase, not a valid one-way detour.
        // Movement already committed on this tick; select the next goal only.
        if (move == SetMoving.RUNNING
                && (creature.getX() != stepBeforeX || creature.getY() != stepBeforeY)
                && creature instanceof L1MonsterInstance
                && creature.getTarget() == target
                && creature.npcActionGeneration() == actionGeneration
                && CCreatureController.currentAction(creature) == expectedAction) {
            final L1MonsterInstance monster = (L1MonsterInstance) creature;
            UpdateAttackLeaderRelated(monster, target);
            if (SetMoving.reportChaseStep(monster, target,
                    stepBeforeX, stepBeforeY, creature.getX(), creature.getY())) {
                SetMoving.clear(monster);
                if (CMoveToTargetRelativeAction.GetMeleeAttackPosition(monster, target)) {
                    monster._pathFindFailCount = 0;
                    return CCreatureController.RESULT_RUNNING;
                }
                // If every valid melee approach is blocked, stop oscillating
                // and let the existing DesirePQ blocked/retarget policy decide.
                final int h = monster.targetDirection(target.getX(), target.getY());
                final int bx = monster.getX() + (h >= 0 && h < 8 ? DX[h] : 0);
                final int by = monster.getY() + (h >= 0 && h < 8 ? DY[h] : 0);
                CDesirePQ.blockAttackForMoveAround(monster, target, bx, by);
                monster._pathFindFailCount = 0;
                CCreatureController.think(monster);
                return CCreatureController.RESULT_RUNNING;
            }
        }

        if (move == SetMoving.DYNAMIC_BLOCKED) {
            creature._pathFindFailCount = 0;

            if (creature instanceof L1MonsterInstance) {
                final L1MonsterInstance monster =
                        (L1MonsterInstance) creature;

                int blockedHeading =
                        creature._moveSide;

                if (blockedHeading < 0
                        || blockedHeading >= 8) {
                    blockedHeading =
                            creature.targetDirection(
                                    target.getX(),
                                    target.getY());
                }

                int blockedX = creature.getX();
                int blockedY = creature.getY();

                if (blockedHeading >= 0
                        && blockedHeading < 8) {
                    blockedX += DX[blockedHeading];
                    blockedY += DY[blockedHeading];
                }

                // A nearer allied body does not invalidate this target. Try
                // the existing body-aware route BEFORE suppressing its desire
                // in favor of a different player. Ranged chases can also use
                // this fallback and stop as soon as their real range/LOS opens.
                final byte passing = tryPassBlockedChase(
                        monster, target, actionGeneration, expectedAction);
                if (passing >= 0) return passing;

                /*
                 * A movement collision is not an ATTACKED/SEE_CREATURE
                 * event. Do not create hate against a blocking PC/pet or
                 * promote a bystander just because its body occupies a tile.
                 * Re-evaluate only ATTACK desires already admitted by NPC AI.
                 */
                if (CDesirePQ.GetTopDesire(
                        monster,
                        target.getId()) != null) {

                    CDesirePQ.blockAttackForMoveAround(
                            monster,
                            target,
                            blockedX,
                            blockedY);

                    // A newly runnable ATTACK must be selected by the AI
                    // owner now, not after A's 300ms collision retry.
                    CCreatureController.rethinkNow(creature);
                    return CCreatureController.RESULT_RUNNING;
                }

                if (NpcEventFactory.addBlockedBodyAttackDesire(
                        monster, target, blockedX, blockedY,
                        System.currentTimeMillis()) != null) {

                    CDesirePQ.blockAttackForMoveAround(
                            monster, target, blockedX, blockedY);
                    CCreatureController.rethinkNow(creature);
                    return CCreatureController.RESULT_RUNNING;
                }

                /*
                 * All eight otherwise valid melee slots are body-occupied.
                 * There is no executable next step: a perpetual RUNNING here
                 * pins the NPC in MOVE_TO_TARGET instead of allowing the
                 * blocked ATTACK -> MOVE_AROUND transition. The separate ring
                 * gate only reopens when a real attack slot is available.
                 */
                if (desiredDistance == 1
                        && isMeleeAttackRingFull(monster, target)) {
                    CDesirePQ.blockAttackForMoveAroundRing(
                            monster, target);
                    SetMoving.clear(monster);
                    CCreatureController.sleep(creature, DYNAMIC_BLOCK_RETRY_MS);
                    CCreatureController.think(creature);
                    return CCreatureController.RESULT_RUNNING;
                }

                /*
                 * 좁은 통로/choke + 대체 ATTACK 없음:
                 * 기존 MOVE_AROUND random-walk 동작 그대로 유지.
                 */
                CDesirePQ.blockAttackForMoveAround(
                        monster,
                        target,
                        blockedX,
                        blockedY);

                CCreatureController.sleep(
                        creature,
                        DYNAMIC_BLOCK_RETRY_MS);

                CCreatureController.think(creature);
                return CCreatureController.RESULT_RUNNING;
            }

            if (isPetFamily(creature)
                    && tryPetFamilyRetargetWhenBlocked(
                            creature,
                            target)) {

                creature._pathFindFailCount = 0;
                CCreatureController.think(creature);
                return CCreatureController.RESULT_RUNNING;
            }

            CCreatureController.sleep(
                    creature,
                    DYNAMIC_BLOCK_RETRY_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (move == SetMoving.PATH_FAILED) {
            if (isGuard(creature)) {
                creature._pathFindFailCount = 0;
                return RESULT_PATH_FAILED;
            }

            // The macro/static search can miss a local route around bodies.
            // A bounded live-occupancy route must get a chance before this
            // target is counted as unreachable and eventually retired.
            if (creature instanceof L1MonsterInstance) {
                final L1MonsterInstance monster = (L1MonsterInstance) creature;
                // Bound repeated occupancy scans on a genuinely missing path.
                if (creature._pathFindFailCount % 3 == 0) {
                    final byte passing = tryPassBlockedChase(
                            monster, target, actionGeneration, expectedAction);
                    if (passing >= 0) return passing;
                }
                final int heading = creature.targetDirection(target.getX(), target.getY());
                if (heading >= 0 && heading < 8) {
                    final int x = creature.getX() + DX[heading];
                    final int y = creature.getY() + DY[heading];
                    final int block = COLLISION.testStep((short) creature.getMapId(),
                            x, y, creature.getId());
                    if (block == WorldCollisionHash.MOVE_NPC
                            || block == WorldCollisionHash.MOVE_PC
                            || block == WorldCollisionHash.MOVE_RESERVED) {
                        // A live body is temporary, not thirty static failures
                        // after which this still-known player disappears.
                        creature._pathFindFailCount = 0;
                        CDesirePQ.blockAttackForMoveAround(monster, target, x, y);
                        CCreatureController.sleep(creature, DYNAMIC_BLOCK_RETRY_MS);
                        CCreatureController.think(creature);
                        return CCreatureController.RESULT_RUNNING;
                    }
                }
            }
            
            final int failCount = ++creature._pathFindFailCount;

            if (failCount <= PATH_FAIL_LIMIT) {
                return CCreatureController.RESULT_RUNNING;
            }

            if (isPetFamily(creature)
                    && tryPetFamilyRetargetWhenBlocked(
                            creature,
                            target)) {

                creature._pathFindFailCount = 0;
                CCreatureController.think(creature);
                return CCreatureController.RESULT_RUNNING;
            }

            // L1 policy: retire only this failed ATTACK, then select from
            // admitted AI events without inventing a nearby attacker.
            creature._pathFindFailCount = 0;
            return RESULT_PATH_FAILED;
        }

        /*
         * The L1 navigation-failure counter clears on non-failed movement.
         * Body collision is handled separately above and never increments it.
         */
        creature._pathFindFailCount = 0;

        if (move == SetMoving.RUNNING) {
            return CCreatureController.RESULT_RUNNING;
        }

        if (move == SetMoving.AT_DISTANCE) {
            CMoveToTargetWaitAction.set(
                    creature,
                    target,
                    desiredDistance,
                    CCreatureController.pendingAction(
                            creature));

            return CCreatureController.RESULT_RUNNING;
        }

        return CCreatureController.RESULT_INVALID;
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final L1Character target,
            final byte result) {
        onEnd(creature, target, result,
                CCreatureController.ACTION_MOVE_TO_TARGET);
    }

    static void onEnd(
            final L1NpcInstance creature,
            final L1Character target,
            final byte result,
            final byte expectedAction) {

        if (creature == null
                || CCreatureController.currentAction(creature) != expectedAction) {
            return;
        }

        // A previous chase completion cannot execute its pending ATTACK
        // or clear state for a different, newly selected target.
        if (creature.getTarget() != target) {
            // The successor owns its movement state. Retire only an exact
            // reservation still tagged with the completed OLD target.
            if (creature instanceof L1MonsterInstance && target != null) {
                final L1MonsterInstance monster =
                        (L1MonsterInstance) creature;
                if (monster._desireResumeAttackTargetId == target.getId()) {
                    clearReservedAttackSlot(monster);
                }
            }
            return;
        }

        switch (result) {
        case RESULT_TARGET_TOO_FAR:
        case RESULT_PATH_FAILED:
            SetMoving.clear(creature);
            CMoveToTargetWaitAction.clear(creature);
            CMoveAction.resetMoveBlockedState(creature);

            if (creature instanceof L1MonsterInstance && target != null) {
                if (creature.getTarget() == target) {
                    // Retire both the failed path and an out-of-range
                    // ATTACK from its desire and L1 hate lists. Otherwise
                    // checkTarget() reacquires the same unreachable enemy
                    // indefinitely instead of selecting another target.
                    // targetRemove only clears this target; other enemies stay.
                    creature.targetRemove(target);
                } else {
                    // A new target already took over. Retire only the stale
                    // failed entry; never clear the current target's state.
                    CDesirePQ.removeAttack(
                            (L1MonsterInstance) creature, target);
                    creature._hateList.remove(target);
                }
            } else if ((creature instanceof L1GuardInstance
                    || creature instanceof L1CastleGuardInstance)
                    && target != null) {
                creature.tagertClear();
            }

            CCreatureController.pendingAction(
                    creature,
                    CCreatureController.ACTION_NONE);

            CCreatureController.sleep(
                    creature,
                    PATH_FAIL_DISPATCH_DELAY_MS);

            CCreatureController.think(creature);
            return;

        case CCreatureController.RESULT_DONE:
            clearBlocked(creature);
            CCreatureController.doPendingAction(
                    creature);

            if (CCreatureController.currentAction(creature)
                    == CCreatureController.ACTION_ATTACK
                    && creature.getTarget() == target
                    && validTarget(creature, target)
                    && creature.isAttackPosition(
                            target,
                            creature.getNpcTemplate().get_ranged())) {

                CAttackAction.basicAttack(creature, target);
            }
            return;

        case CCreatureController.RESULT_INVALID:
            clearBlocked(creature);

            if (!validTarget(creature, target)) {
                creature.tagertClear();
            } else {
                CCreatureController.think(creature);
            }
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }

    public static void onInterrupted(
            final L1NpcInstance creature,
            final boolean enteringWait) {

        if (creature == null) {
            return;
        }
        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster = (L1MonsterInstance) creature;
            AbandonObsoleteAttackLeader(monster, creature.getTarget());
            clearReservedAttackSlot(monster);
        }
        SetMoving.clearPassingDestination(creature);
        if (!enteringWait) {
            SetMoving.clear(creature);
            CMoveToTargetWaitAction.clear(creature);
            CMoveAction.resetMoveBlockedState(creature);
        }
    }

    public static boolean handleBlocked(
            final L1NpcInstance creature,
            final L1Character target) {

        return !validTarget(creature, target)
                || target != creature._target;
    }

    public static void onBlockDecayHate(
            final L1NpcInstance creature) {
    }

    public static void clearBlocked(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster =
                    (L1MonsterInstance) creature;

            clearReservedAttackSlot(monster);
            CDesirePQ.releaseBlockedAttack(monster);
        }

        SetMoving.clearPassingDestination(creature);
        SetMoving.clear(creature);
        CMoveToTargetWaitAction.clear(creature);
        CMoveAction.resetMoveBlockedState(creature);
        creature._pathFindFailCount = 0;
    }

    private static byte tryPassBlockedChase(
            final L1MonsterInstance monster,
            final L1Character target,
            final int generation,
            final byte expectedAction) {
        if (monster.isDead() || monster.getTarget() != target
                || monster.npcActionGeneration() != generation
                || CCreatureController.currentAction(monster) != expectedAction) {
            return CCreatureController.RESULT_INVALID;
        }
        if (CMoveToTargetRelativeAction.GetMeleeAttackPosition(monster, target)) {
            final byte result = SetMoving(monster, target, generation);
            if (result >= 0) return result;
        }
        if (monster.isDead() || monster.getTarget() != target
                || monster.npcActionGeneration() != generation
                || CCreatureController.currentAction(monster) != expectedAction) {
            return CCreatureController.RESULT_INVALID;
        }
        final int tx = target.getX();
        final int ty = target.getY();
        final PathFinder pathFinder = AstarProvider.get();
        final long preferred = target.GetPassingPosOnSight(
                monster, tx, ty, pathFinder.targetPassingPositions(),
                pathFinder.leaderPassingPositions());
        if (preferred == PathNodeWorld.NO_PATH) return -1;
        final int x = PathNodeWorld.unpackX(preferred);
        final int y = PathNodeWorld.unpackY(preferred);
        if ((x == tx && y == ty)
                || (x == monster.getX() && y == monster.getY())
                || !COLLISION.canEnter(
                        (short) monster.getMapId(), x, y, monster.getId())) {
            return -1;
        }
        if (monster.isDead() || monster.getTarget() != target
                || target.isDead() || target.getCurrentHp() <= 0
                || target.getMapId() != monster.getMapId()
                || target.getX() != tx || target.getY() != ty
                || monster.npcActionGeneration() != generation
                || CCreatureController.currentAction(monster) != expectedAction) {
            return CCreatureController.RESULT_INVALID;
        }
        SetMoving.setPassingDestination(monster, target,
                x, y, System.currentTimeMillis() + PASSING_INTENT_MS);
        CCreatureController.sleep(monster, 100L);
        return CCreatureController.RESULT_RUNNING;
    }

    private static byte SetMoving(
            final L1MonsterInstance monster,
            final L1Character target,
            final int actionGeneration) {

        final int packed =
                monster._desireResumeAttackPos;

        if (packed == 0
                || monster._desireResumeAttackTargetId
                        != target.getId()) {

            if (packed != 0
                    || monster._desireResumeAttackTargetId != 0) {

                clearReservedAttackSlot(monster);
            }

            return -1;
        }

        final int slotX =
                (packed >>> 16) & 0xffff;
        final int slotY =
                packed & 0xffff;

        /*
         * Target moved: the old reserved tile is no longer its melee slot.
         */
        if (monster._desireResumeAttackMapId != (short) monster.getMapId()
                || monster._desireResumeAttackTargetPos
                        != (((target.getX() & 0xffff) << 16)
                            | (target.getY() & 0xffff))
                || target.getMapId() != monster.getMapId()
                || Math.max(
                        Math.abs(slotX - target.getX()),
                        Math.abs(slotY - target.getY())) != 1
                || !isUsableMeleeSlot(target, slotX, slotY)) {

            clearReservedAttackSlot(monster);
            return -1;
        }

        /*
         * Refresh our short claim while we approach. testStep() treats our own
         * reservation as clear, but a real creature body still wins.
         */
        if (!COLLISION.reserveNpcMove(
                (short) monster.getMapId(),
                slotX,
                slotY,
                monster.getId(),
                RESERVED_SLOT_CLAIM_MS)) {

            clearReservedAttackSlot(monster);
            return -1;
        }

        final int oldX = monster.getX();
        final int oldY = monster.getY();
        final byte move =
                SetMoving.tickToReservedAttackSlot(
                        monster,
                        target,
                        target.getX(),
                        target.getY(),
                        slotX,
                        slotY,
                        actionGeneration);

        // A physical step may complete just before the target moves or the
        // reservation changes. Never fall through and issue a second ordinary
        // chase step on the same CIO tick: the first step has already consumed
        // the NPC walk interval. Drop only the obsolete claim, then let the
        // next owner tick choose the new target/route.
        final boolean stepped = oldX != monster.getX()
                || oldY != monster.getY();
        if (monster._desireResumeAttackPos != packed
                || monster._desireResumeAttackTargetId != target.getId()) {
            return stepped && monster.getTarget() == target
                    && !monster.isDead()
                    ? CCreatureController.RESULT_RUNNING : (byte) -1;
        }
        if (monster.isDead() || monster.getCurrentHp() <= 0
                || !validTarget(monster, target)
                || monster.getTarget() != target
                || monster._desireResumeAttackTargetPos
                        != (((target.getX() & 0xffff) << 16)
                            | (target.getY() & 0xffff))
                || target.getMapId() != monster.getMapId()
                || Math.max(Math.abs(slotX - target.getX()),
                            Math.abs(slotY - target.getY())) != 1) {
            clearReservedAttackSlot(monster);
            return stepped && monster.getTarget() == target
                    && !monster.isDead()
                    ? CCreatureController.RESULT_RUNNING : (byte) -1;
        }

        if (move == SetMoving.RUNNING
                && (oldX != monster.getX() || oldY != monster.getY())) {
            UpdateAttackLeaderRelated(monster, target);
            monster._desireResumeAttackBlockedRetries = 0;
            monster._pathFindFailCount = 0;
            if (SetMoving.reportChaseStep(monster, target,
                    oldX, oldY, monster.getX(), monster.getY())) {
                CMoveToTargetRelativeAction.rejectMeleeAttackPosition(
                        monster, target, slotX, slotY,
                        System.currentTimeMillis() + FAILED_MELEE_POSITION_COOLDOWN_MS);
                clearReservedAttackSlot(monster);
            }
            return CCreatureController.RESULT_RUNNING;
        }

        // Preserve the chosen melee position through transient body races.
        // Repeated no-route or no-step results retire only this position.
        if (move == SetMoving.DYNAMIC_BLOCKED || move == SetMoving.RUNNING) {
            if (++monster._desireResumeAttackBlockedRetries
                    < RESERVED_SLOT_BLOCK_RETRY_LIMIT) {
                CCreatureController.sleep(monster, DYNAMIC_BLOCK_RETRY_MS);
                monster._pathFindFailCount = 0;
                return CCreatureController.RESULT_RUNNING;
            }
            CMoveToTargetRelativeAction.rejectMeleeAttackPosition(
                    monster, target, slotX, slotY,
                    System.currentTimeMillis() + FAILED_MELEE_POSITION_COOLDOWN_MS);
            clearReservedAttackSlot(monster);
            return -1;
        }

        if (move == SetMoving.AT_DISTANCE) {
            final boolean attackable = monster.isAttackPosition(
                    target, monster.getNpcTemplate().get_ranged());
            if (!attackable) {
                CMoveToTargetRelativeAction.rejectMeleeAttackPosition(
                        monster, target, slotX, slotY,
                        System.currentTimeMillis() + FAILED_MELEE_POSITION_COOLDOWN_MS);
            }
            clearReservedAttackSlot(monster);
            monster._pathFindFailCount = 0;
            return attackable ? CCreatureController.RESULT_DONE : (byte) -1;
        }

        /*
         * Something changed between slot claim and approach. Drop only this
         * exact-slot hand-off; normal chase/ring detection owns the next step.
         */
        clearReservedAttackSlot(monster);
        return -1;
    }

    private static void discardObsoleteAttackSlot(
            final L1MonsterInstance monster,
            final L1Character target) {
        final int packed = monster._desireResumeAttackPos;
        if (packed == 0) {
            if (monster._desireResumeAttackTargetId != 0) {
                clearReservedAttackSlot(monster);
            }
            return;
        }
        final int x = (packed >>> 16) & 0xffff;
        final int y = packed & 0xffff;
        if (monster.isDead() || monster.getCurrentHp() <= 0
                || target == null || !validTarget(monster, target)
                || monster._desireResumeAttackTargetId != target.getId()
                || monster._desireResumeAttackTargetPos
                        != (((target.getX() & 0xffff) << 16)
                            | (target.getY() & 0xffff))
                || monster._desireResumeAttackMapId != (short) monster.getMapId()
                || Math.max(Math.abs(x - target.getX()),
                            Math.abs(y - target.getY())) != 1
                || !isUsableMeleeSlot(target, x, y)) {
            clearReservedAttackSlot(monster);
        }
    }

    static void clearReservedAttackSlot(
            final L1MonsterInstance monster) {

        final int packed =
                monster._desireResumeAttackPos;

        if (packed != 0) {
            COLLISION.cancelNpcMove(
                    monster._desireResumeAttackMapId,
                    (packed >>> 16) & 0xffff,
                    packed & 0xffff,
                    monster.getId());
        }

        monster._desireResumeAttackTargetId = 0;
        monster._desireResumeAttackTargetPos = 0;
        monster._desireResumeAttackPos = 0;
        monster._desireResumeAttackMapId = 0;
        monster._desireResumeAttackBlockedRetries = 0;
    }

    private static void AbandonObsoleteAttackLeader(
            final L1MonsterInstance monster, final L1Character currentTarget) {
        final int oldId = monster._ncAttackLeaderTargetId;
        if (oldId == 0 || (currentTarget != null && oldId == currentTarget.getId())) {
            return;
        }
        final L1Object object = l1j.server.server.model.L1World.getInstance()
                .findObject(oldId);
        if (object instanceof L1Character) {
            ((L1Character) object).AbandonAttackLeaderOf(monster);
        }
        monster._ncAttackLeaderTargetId = 0;
    }

    private static void UpdateAttackLeaderRelated(
            final L1MonsterInstance monster, final L1Character target) {
        if (target == null || monster.isDead() || monster.getCurrentHp() <= 0
                || !monster.isMovable() || monster.getTarget() != target
                || monster.getMapId() != target.getMapId()) {
            return;
        }
        AbandonObsoleteAttackLeader(monster, target);
        final int position = monster.GetMyPositionFrom(target);
        final int existingId = target.GetAttackLeaderId(position);
        if (existingId == monster.getId()) {
            monster._ncAttackLeaderTargetId = target.getId();
            return;
        }
        if (existingId != 0) {
            final L1Object object = l1j.server.server.model.L1World.getInstance()
                    .findObject(existingId);
            if (object instanceof L1MonsterInstance) {
                final L1MonsterInstance previous = (L1MonsterInstance) object;
                if (previous != monster && !previous.isDead()
                        && previous.getCurrentHp() > 0 && previous.isMovable()
                        && previous.getTarget() == target
                        && previous.getMapId() == target.getMapId()
                        && !monster.IsCloserThan(previous, target)) {
                    return;
                }
            }
        }
        if (target.SetAttackLeaderRelated(monster, existingId)) {
            monster._ncAttackLeaderTargetId = target.getId();
        }
    }

    private static boolean isMeleeAttackRingFull(
            final L1MonsterInstance monster,
            final L1Character target) {

        if (monster == null
                || target == null
                || target.getMap() == null
                || target.getMapId() != monster.getMapId()) {

            return false;
        }

        final short mapId =
                (short) monster.getMapId();

        final int tx = target.getX();
        final int ty = target.getY();

        /*
         * This is ONLY the wide-open 8-way ring case.
         *
         * If even one target-adjacent tile is statically unusable, this is
         * corridor/choke geometry and must remain eligible for the existing
         * DYNAMIC_BLOCKED -> MOVE_AROUND behavior.
         */
        for (int heading = 0;
             heading < 8;
             heading++) {

            final int x = tx + DX[heading];
            final int y = ty + DY[heading];

            if (!isUsableMeleeSlot(
                    target,
                    x,
                    y)) {

                return false;
            }

            if (COLLISION.canEnter(
                    mapId,
                    x,
                    y,
                    monster.getId())) {

                return false;
            }
        }

        return true;
    }

    private static boolean isUsableMeleeSlot(
            final L1Character target,
            final int x,
            final int y) {

        return target != null
                && target.getMap() != null
                && target.getMap().isPassable(x, y)
                && target.glanceCheck(x, y);
    }

    private static boolean tryPetFamilyRetargetWhenBlocked(
            final L1NpcInstance creature,
            final L1Character currentTarget) {

        if (!isPetFamily(creature)
                || currentTarget == null
                || currentTarget != creature._target) {

            return false;
        }

        final L1Character candidate =
                findNearestHatedTarget(
                        creature,
                        currentTarget);

        if (candidate == null) {
            return false;
        }

        creature._target = candidate;

        SetMoving.clear(creature);
        CMoveToTargetWaitAction.clear(creature);
        CMoveAction.resetMoveBlockedState(creature);
        creature._pathFindFailCount = 0;

        return true;
    }

    private static L1Character findNearestHatedTarget(
            final L1NpcInstance creature,
            final L1Character currentTarget) {

        final L1Character master = creature.getMaster();

        if (master == null) {
            return null;
        }

        L1Character best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (L1Object obj : creature.getKnownObjects()) {
            if (!(obj instanceof L1MonsterInstance)) {
                continue;
            }

            final L1MonsterInstance candidate =
                    (L1MonsterInstance) obj;

            if (candidate == currentTarget
                    || candidate.isDead()
                    || candidate.getCurrentHp() <= 0
                    || candidate.getMapId() != creature.getMapId()
                    || candidate.getHateList().getHate(master) <= 0) {
                continue;
            }

            final int distance =
                    creature.getLocation()
                            .getTileLineDistance(
                                    candidate.getLocation());

            if (distance >= bestDistance) {
                continue;
            }

            best = candidate;
            bestDistance = distance;
        }

        return best;
    }

    private static boolean isPetFamily(
            final L1NpcInstance creature) {

        return creature instanceof L1SummonInstance
                || creature instanceof L1CompanionInstance;
    }

    private static boolean isGuard(
            final L1NpcInstance creature) {

        return creature instanceof L1GuardInstance
                || creature instanceof L1CastleGuardInstance;
    }

    private static boolean validTarget(
            final L1NpcInstance creature,
            final L1Character target) {

        return creature != null
                && target != null
                && !target.isDead()
                && target.getCurrentHp() > 0
                && target.getMapId() == creature.getMapId()
                && (!target.isInvisble()
                        || creature.getNpcTemplate().is_agrocoi());
    }

    public static boolean tryMoveToTargetTeleport(
            final L1NpcInstance npc,
            final L1Character target) {
        if (!validTarget(npc, target) || npc.getTarget() != target
                || !npc.getNpcTemplate().is_teleport()
                || npc.getCurrentMp() < 10) {
            return false;
        }

        final int distance = npc.getLocation().getTileDistance(target.getLocation());
        if (npc.firstFound && distance > 3 && distance < 15) {
            if (TeleportToUser(npc, target)) {
                npc.firstFound = false;
                return true;
            }
        }

        return 20 > ThreadLocalRandom.current().nextInt(100)
                && distance > 6 && distance < 15
                && TeleportToUser(npc, target);
    }

    private static boolean TeleportToUser(L1NpcInstance npc, L1Character target) {
        if (!validTarget(npc, target) || npc.getTarget() != target) return false;
        if (!npc.nearTeleport(target.getX(), target.getY())) return false;
        OnTeleport(npc);
        return true;
    }

    private static void OnTeleport(L1NpcInstance npc) {
        clearBlocked(npc);
        SetMoving.clearActionMoveState(npc);
    }

}
