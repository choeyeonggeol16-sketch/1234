package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.ai.core.PathFinder;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.npc.ai.AstarProvider;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.L1Character;

public final class CMoveToTargetRelativeAction {
    private static final long OPEN_LANE_CLAIM_MAX_MS = 6_000L;
    private static final int[] DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY = { -1, -1, 0, 1, 1, 1, 0, -1 };
    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private CMoveToTargetRelativeAction() {
    }

    public static void set(
            final L1NpcInstance creature,
            final L1Character target,
            final byte pendingAction) {
        if (creature == null || target == null
                || target.getId() == 0
                || creature.getTarget() != target
                || target.getMapId() != creature.getMapId()) {
            if (creature != null) {
                CCreatureController.rethinkNow(creature);
            }
            return;
        }

        if (CCreatureController.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                && creature._relativeTargetId == target.getId()
                && creature._relativeMapId == creature.getMapId()) {
            CCreatureController.pendingAction(creature, pendingAction);
            return;
        }

        CCreatureController.set(creature,
                CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE,
                pendingAction);
        // Preserve existing chase movement/collision initialization.
        CMoveToTargetAction.onStart(creature);
        creature._relativeTargetId = target.getId();
        creature._relativeMapId = creature.getMapId();
    }

    public static void tick(
            final L1NpcInstance creature,
            final L1Character target) {
        if (creature == null || CCreatureController.currentAction(creature)
                != CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE) {
            return;
        }

        final int generation = creature.npcActionGeneration();
        if (target == null || creature.getTarget() != target
                || target.getId() != creature._relativeTargetId
                || creature.getMapId() != creature._relativeMapId
                || target.getMapId() != creature._relativeMapId) {
            CCreatureController.rethinkNow(creature);
            return;
        }

        final byte result = CMoveToTargetAction.onTick(
                creature, target, true,
                CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE);
        if (result != CCreatureController.RESULT_RUNNING
                && creature.npcActionGeneration() == generation
                && CCreatureController.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                && creature.getTarget() == target
                && creature._relativeTargetId == target.getId()
                && creature._relativeMapId == creature.getMapId()
                && creature._relativeMapId == target.getMapId()) {
            CMoveToTargetAction.onEnd(
                    creature, target, result,
                    CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE);
        }
    }

    static long getDestination(final int actorX,
                               final int actorY,
                               final int targetX,
                               final int targetY,
                               final int distance) {
        final long dx = (long) actorX - targetX;
        final long dy = (long) actorY - targetY;
        final double length = Math.sqrt((double) dx * dx + (double) dy * dy);
        if (length < 1.0e-9d || distance <= 0) {
            return ((long) targetX << 32) | (targetY & 0xffffffffL);
        }
        final double scale = (double) distance / length;
        final int x = targetX + (int) Math.round(dx * scale);
        final int y = targetY + (int) Math.round(dy * scale);
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /**
     * Check all eight melee positions from the current NPC position, not the
     * two sides of an obsolete blocked heading. Use a local static straight
     * test only; dynamic ownership is decided by one atomic reservation.
     */
    static boolean GetMeleeAttackPosition(
            final L1MonsterInstance monster,
            final L1Character target) {

        if (monster.getMap() == null) {
            return false;
        }
        final int mx = monster.getX();
        final int my = monster.getY();
        final int tx = target.getX();
        final int ty = target.getY();
        // The router tries its local window first, then a larger reusable
        // window covering both actor and target. Do not reject by old 12-tile
        // distance before asking whether another entrance can be reached.
        final short mapId = (short) monster.getMapId();
        final int baseHeading = target.targetDirection(mx, my);
        final boolean clockwiseFirst =
                ((monster.getId() ^ target.getId()) & 1) == 0;

        // Fully occupied melee rings are common during mass pulls. Avoid a
        // 25x25 occupancy scan until a physically usable attack slot exists.
        int openSlotMask = 0;
        final long nowMs = System.currentTimeMillis();
        for (int h = 0; h < 8; h++) {
            final int x = tx + DX[h];
            final int y = ty + DY[h];
            if (isUsableMeleeSlot(target, x, y)
                    && !CMoveToTargetRelativeAction.rejectedMeleeAttackPosition(
                            monster, target, x, y, nowMs)
                    && COLLISION.canEnter(mapId, x, y, monster.getId())) {
                openSlotMask |= 1 << h;
            }
        }
        if (openSlotMask == 0) {
            return false;
        }

        // Scan all currently usable ring candidates with the existing bounded
        // workspace. Expand only if the local window cannot reach any of them.
        final PathFinder pathFinder = AstarProvider.get();
        pathFinder.scanBodyRouteToMeleeRing(monster, tx, ty, openSlotMask);

        int attempted = 0;
        // Admission is atomic per destination tile. If another monster claims
        // our preferred slot between canEnter() and reserveNpcMove(), try the
        // next eligible slot instead of stalling despite free neighboring tiles.
        for (int attempt = 0; attempt < 8; attempt++) {
            int bestHeading = -1;
            int bestDistance = Integer.MAX_VALUE;
            int bestTurn = Integer.MAX_VALUE;
            int bestX = 0;
            int bestY = 0;
            for (int heading = 0; heading < 8; heading++) {
                if ((openSlotMask & (1 << heading)) == 0
                        || (attempted & (1 << heading)) != 0) {
                    continue;
                }
                final int x = tx + DX[heading];
                final int y = ty + DY[heading];
                final int distance = pathFinder.bodyRouteDistanceTo(x, y);
                final int turn = ringTurnRank(
                        baseHeading, heading, clockwiseFirst);
                if (distance <= 0
                        || distance > bestDistance
                        || (distance == bestDistance && turn >= bestTurn)
                        || !isUsableMeleeSlot(target, x, y)
                        || !COLLISION.canEnter(mapId, x, y, monster.getId())) {
                    continue;
                }
                bestHeading = heading;
                bestDistance = distance;
                bestTurn = turn;
                bestX = x;
                bestY = y;
            }
            if (bestHeading < 0) {
                return false;
            }
            attempted |= 1 << bestHeading;
            // The destination was computed for a snapshot of the target.
            // Never publish a claim after the target moved or the NPC died.
            if (monster.isDead() || monster.getCurrentHp() <= 0
                    || monster.getTarget() != target
                    || target.isDead() || target.getCurrentHp() <= 0
                    || target.getMapId() != monster.getMapId()
                    || target.getX() != tx || target.getY() != ty) {
                return false;
            }
            // The destination hand-off can cross a slow NPC animation or
            // owner wake. Use the same bounded claim lifetime as subsequent
            // chase ticks, never a shorter distance-based first lease.
            if (!COLLISION.reserveNpcMove(
                    mapId, bestX, bestY, monster.getId(),
                    OPEN_LANE_CLAIM_MAX_MS)) {
                continue;
            }
            // The claim and route scan are separate operations. Revalidate
            // before handing this exact destination to the next action.
            if (monster.isDead() || monster.getCurrentHp() <= 0
                    || monster.getTarget() != target
                    || target.isDead() || target.getCurrentHp() <= 0
                    || target.getMapId() != monster.getMapId()
                    || target.getX() != tx || target.getY() != ty
                    || !isUsableMeleeSlot(target, bestX, bestY)) {
                COLLISION.cancelNpcMove(mapId, bestX, bestY, monster.getId());
                return false;
            }
            // The new claim already belongs to this NPC. Retire only an
            // obsolete DIFFERENT slot: canceling the same tile here would
            // revoke the claim we just refreshed. An old different claim
            // must also be released when no blocked ATTACK gate was set.
            final int newPacked =
                    ((bestX & 0xffff) << 16) | (bestY & 0xffff);
            final int oldPacked = monster._desireResumeAttackPos;
            if (oldPacked != 0
                    && (oldPacked != newPacked
                        || monster._desireResumeAttackMapId != mapId)) {
                CMoveToTargetAction.clearReservedAttackSlot(monster);
            }
            CDesirePQ.releaseBlockedAttackGateOnly(monster, target);
            monster._desireResumeAttackMapId = mapId;
            monster._desireResumeAttackBlockedRetries = 0;
            monster._desireResumeAttackTargetId = target.getId();
            monster._desireResumeAttackTargetPos =
                    ((tx & 0xffff) << 16) | (ty & 0xffff);
            monster._desireResumeAttackPos = newPacked;
            // Death/retarget can finish during publication. Cancel this exact
            // claim rather than calling releaseBlockedAttack(), which only
            // clears an active blocked gate and can leave a ghost reservation.
            if (monster.isDead() || monster.getCurrentHp() <= 0
                    || monster.getTarget() != target
                    || target.isDead() || target.getCurrentHp() <= 0
                    || target.getMapId() != monster.getMapId()
                    || target.getX() != tx || target.getY() != ty) {
                if (monster._desireResumeAttackTargetId == target.getId()
                        && monster._desireResumeAttackPos
                                == (((bestX & 0xffff) << 16) | (bestY & 0xffff))) {
                    CMoveToTargetAction.clearReservedAttackSlot(monster);
                } else {
                    COLLISION.cancelNpcMove(mapId, bestX, bestY, monster.getId());
                }
                return false;
            }
            return true;
        }
        return false;
    }

    private static int ringTurnRank(
            final int baseHeading,
            final int heading,
            final boolean clockwiseFirst) {

        if (baseHeading < 0
                || baseHeading >= 8) {

            return heading;
        }

        final int clockwise =
                (heading - baseHeading) & 7;

        final int counterClockwise =
                (baseHeading - heading) & 7;

        final int turn =
                Math.min(
                        clockwise,
                        counterClockwise);

        if (clockwise == counterClockwise) {
            return turn << 1;
        }

        final boolean isClockwise =
                clockwise < counterClockwise;

        return (turn << 1)
                + (isClockwise == clockwiseFirst
                        ? 0
                        : 1);
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

    public static void clear(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }
        creature._relativeTargetId = 0;
        creature._relativeMapId = -1;
    }

    static void rejectMeleeAttackPosition(final L1NpcInstance npc,
            final L1Character target, final int x, final int y,
            final long untilMs) {
        if (npc == null || target == null || npc.getId() == 0) return;
        final int bit = meleePositionBit(target.getX(), target.getY(), x, y);
        if (bit == 0) return;

        SetMoving.initActionMoveState(npc);
        final long targetPos = SetMoving.pack(target.getX(), target.getY());
        final long nowMs = System.currentTimeMillis();
        if (npc._actionMoveKind != SetMoving.KIND_COMBAT
                || npc._actionMoveAnchorId != target.getId()
                || npc._actionMoveAnchorPos != targetPos
                || npc._actionMoveWaitReason != 1
                || nowMs >= npc._actionMoveWaitUntil) {
            npc._actionMoveRejectedMeleeMask = 0;
            npc._actionMoveWaitUntil = 0L;
        }
        npc._actionMoveKind = SetMoving.KIND_COMBAT;
        npc._actionMoveAnchorId = target.getId();
        npc._actionMoveAnchorPos = targetPos;
        npc._actionMoveRejectedMeleeMask |= (byte) bit;
        npc._actionMoveWaitReason = 1;
        npc._actionMoveWaitUntil = Math.max(npc._actionMoveWaitUntil, untilMs);
        npc._actionMovePreviousOrigin = SetMoving.NO_POS;
        npc._actionMoveNoProgress = 0;
    }

    static boolean rejectedMeleeAttackPosition(final L1NpcInstance npc,
            final L1Character target, final int x, final int y,
            final long nowMs) {
        if (npc == null || target == null || npc.getId() == 0) return false;
        final int bit = meleePositionBit(target.getX(), target.getY(), x, y);
        if (bit == 0) return false;
        return SetMoving.hasActionMoveState(npc)
                && npc._actionMoveKind == SetMoving.KIND_COMBAT
                && npc._actionMoveAnchorId == target.getId()
                && npc._actionMoveAnchorPos == SetMoving.pack(target.getX(), target.getY())
                && npc._actionMoveWaitReason == 1
                && nowMs < npc._actionMoveWaitUntil
                && ((npc._actionMoveRejectedMeleeMask & 0xff) & bit) != 0;
    }

    private static int meleePositionBit(final int tx, final int ty,
            final int x, final int y) {
        final int dx = x - tx;
        final int dy = y - ty;
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1
                || (dx | dy) == 0) return 0;
        final int heading;
        if (dy < 0) {
            heading = dx < 0 ? 7 : (dx > 0 ? 1 : 0);
        } else if (dy > 0) {
            heading = dx < 0 ? 5 : (dx > 0 ? 3 : 4);
        } else {
            heading = dx > 0 ? 2 : 6;
        }
        return 1 << heading;
    }
}
