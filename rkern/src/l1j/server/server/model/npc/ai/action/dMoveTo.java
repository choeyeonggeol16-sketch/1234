package l1j.server.server.model.npc.ai.action;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.ai.core.PathNodeWorld;
import l1j.server.server.model.npc.ai.CNPCEvent;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.npc.ai.CNPCServer;

/**
 * NC CNPC::dMoveTo (0x004934a4), adapted to L1's 2D eight-direction tiles.
 *
 * NC continuous-world distances of 8192 and sqrt(1000) map to an 82-tile
 * long jump and an exact-tile arrival respectively (100 world units/tile).
 * Existing L1 movement and teleport commits own collision, broadcasts,
 * visibility, destination reservation and world changes.
 *
 * The request API is strictly CIO-owner-only. Its payload lives in the
 * NPC's primitive fields, not an allocated command or a second work queue.
 */
public final class dMoveTo {
    private static final long NEXT_MS = 1_000L;
    private static final long TELEPORT_MS = 3_000L;
    private static final long TELEPORT_DISTANCE_SQ = 82L * 82L;
    private static final int PATH_FAIL_TELEPORT = 20;
    private static final int RETURN_WALK = -1;
    private static final int RETURN_TELEPORT = -2;
    private static final int RETURN_RETRY_LIMIT = 10;

    private dMoveTo() {
    }

    /** Native-like positional desire entry, installed on its fixed CIO owner. */
    public static boolean request(
            final L1NpcInstance npc,
            final int x,
            final int y) {
        if (isReturningHome(npc)) {
            return false;
        }
        return request(npc, x, y, 0);
    }

    private static boolean request(
            final L1NpcInstance npc, final int x, final int y,
            final int targetId) {
        if (npc == null
                || !CNPCServer.getInstance().isOwnerThread(npc)
                || !npc.aiEngineIsRunning()
                || !validDestination(npc, x, y)) {
            return false;
        }
        npc._scriptMoveMapId = npc.getMapId();
        npc._scriptMoveGoalXY = SetMoving.pack(x, y);
        npc._scriptMoveTargetId = targetId;
        npc._scriptMoveStopDistance = 0;
        npc._pathFindFailCount = 0;
        CMoveAction.invalidatePath(npc);
        CCreatureController.submit(npc, CCreatureController.ACTION_MOVE_TO_POINT);
        // The owner applies the action inline. A rejected shutdown/death
        // admission must not leave the return marker and immunity behind.
        if (targetId < 0 && isReturningHome(npc)
                && CCreatureController.currentAction(npc)
                        != CCreatureController.ACTION_MOVE_TO_POINT) {
            CMoveAction.clearScriptMove(npc);
            return false;
        }
        return true;
    }

    /** Reuse the existing positional payload; negative IDs are never object IDs. */
    public static boolean isReturningHome(final L1NpcInstance npc) {
        if (!(npc instanceof L1MonsterInstance)) return false;
        final int mode = npc._scriptMoveTargetId;
        return mode == RETURN_WALK || mode == RETURN_TELEPORT;
    }

    /** NC absolute return, using the existing spawn radius and positional action. */
    public static boolean ReturnStartPoint(final L1NpcInstance npc) {
        if (!(npc instanceof L1MonsterInstance)
                || !CNPCServer.getInstance().isOwnerThread(npc)
                || !npc.aiEngineIsRunning() || npc.isDestroyed()
                || npc.isDead() || npc.getCurrentHp() <= 0
                || isReturningHome(npc)) {
            return false;
        }

        // L1 migration: prefer the spawn-specific movement radius; legacy
        // rows without a radius retain their existing global fallback.
        final int configured = npc.getMovementDistance();
        final int radius = configured > 0 ? configured
                : (npc.getMaxHp() >= 100000 ? Config.Boss_Location : Config.Npc_Location);
        final int x = npc.getHomeX();
        final int y = npc.getHomeY();
        if (radius <= 0 || npc.getLocation().getLineDistance(x, y) <= radius) {
            return false;
        }
        final L1Map map = npc.getMap();
        if (map == null) return false;
        if (!map.isInMap(x, y)) {
            // Invalid spawn coordinates, as in NC: adopt the current position.
            npc.setHomeX(npc.getX());
            npc.setHomeY(npc.getY());
            return false;
        }
        if (!validDestination(npc, x, y)) return false;

        final boolean reachable = npc.isMovable()
                && PathNodeWorld.moveStraight(map, npc.getX(), npc.getY(), x, y)
                && PathNodeWorld.moveStraight(map, x, y, npc.getX(), npc.getY());

        synchronized (npc) {
            // The damage CAS and the return admission use the same monitor.
            if (!CNPCServer.getInstance().isRunning() || !npc.aiEngineIsRunning()
                    || npc.isDestroyed() || npc.isDead() || npc.getCurrentHp() <= 0
                    || npc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE
                    || npc.getMap() != map || isReturningHome(npc)
                    || !validDestination(npc, x, y)) return false;
            CCreatureController.think(npc);
            npc.allTargetClear();
            npc.npcChaseRetargetConsume();
            npc.setFirstAttack(false);
            CNPCEvent.ReleaseChain(npc.TakeNpcEvents());

            if (!request(npc, x, y, reachable ? RETURN_WALK : RETURN_TELEPORT)) {
                return false;
            }
            // The inline action may finish instantly. No external hit can
            // commit until both HP and MP recovery have completed.
            if (npc.applyNpcHpDelta(npc.getMaxHp()) <= 0
                    || npc.isDead() || npc.isDestroyed()) return true;
            npc.setCurrentMp(npc.getMaxMp());
            return true;
        }
    }

    /** Execute the stored DESIRE_MOVE_TO through the existing timer wheel. */
    static byte onTick(final L1NpcInstance npc) {
        if (npc == null || npc._scriptMoveMapId != npc.getMapId()) {
            return CCreatureController.RESULT_INVALID;
        }
        final int x = SetMoving.unpackX(npc._scriptMoveGoalXY);
        final int y = SetMoving.unpackY(npc._scriptMoveGoalXY);
        if (npc._scriptMoveTargetId == RETURN_WALK
                && (!npc.isMovable() || !validDestination(npc, x, y))) {
            npc._scriptMoveTargetId = RETURN_TELEPORT;
            npc._pathFindFailCount = 0;
        }
        if (npc._scriptMoveTargetId == RETURN_TELEPORT) {
            if (npc.isDestroyed() || npc.isDead() || npc.getCurrentHp() <= 0) {
                return CCreatureController.RESULT_INVALID;
            }
            if (npc.getX() == x && npc.getY() == y) {
                return CCreatureController.RESULT_DONE;
            }
            if (teleportIfFree(npc, x, y)) {
                npc._pathFindFailCount = 0;
                return CCreatureController.RESULT_DONE;
            }
            // Do not leave an untargetable NPC stranded at an occupied home.
            // NC also renews its spawn position after repeated failed returns.
            if (++npc._pathFindFailCount >= RETURN_RETRY_LIMIT) {
                npc.setHomeX(npc.getX());
                npc.setHomeY(npc.getY());
                return CCreatureController.RESULT_INVALID;
            }
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }
        return dMoveTo(npc, x, y);
    }

    /** One owner tick, without allocating a Point/path object. */
    public static byte dMoveTo(
            final L1NpcInstance npc,
            final int x,
            final int y) {
        if (npc == null || npc.isDestroyed()
                || npc.isDead() || npc.getCurrentHp() <= 0
                || !validDestination(npc, x, y)) {
            return CCreatureController.RESULT_INVALID;
        }

        final long dx = (long) x - npc.getX();
        final long dy = (long) y - npc.getY();
        final long distanceSq = dx * dx + dy * dy;

        // NC dMoveTo: completion radius sqrt(1000) = 31.62 units;
        // on L1's 100-unit tiles, only the exact destination is inside.
        if (distanceSq == 0L) {
            npc._pathFindFailCount = 0;
            return CCreatureController.RESULT_DONE;
        }
        if (!npc.isMovable()) {
            return CCreatureController.RESULT_INVALID;
        }
        if (npc.발묶임상태()) {
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        final boolean returning = isReturningHome(npc);
        if (!returning && distanceSq >= TELEPORT_DISTANCE_SQ) {
            if (teleportIfFree(npc, x, y)) {
                CCreatureController.sleep(npc, TELEPORT_MS);
                npc._pathFindFailCount = 0;
                return CCreatureController.RESULT_RUNNING;
            }
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        final int dir = CMoveAction.direction(npc, x, y);
        if (dir >= 0 && dir < 8) {
            if (CMoveAction.moveOneStep(npc, dir)) {
                npc._pathFindFailCount = 0;
                CCreatureController.sleep(npc, Math.max(NEXT_MS, npc.getSleepTime()));
                return CCreatureController.RESULT_RUNNING;
            }
            // Reservation lost between path probe and physical commit.
            if (returning && ++npc._pathFindFailCount > PATH_FAIL_TELEPORT) {
                npc._scriptMoveTargetId = RETURN_TELEPORT;
                npc._pathFindFailCount = 0;
            }
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (returning) {
            // Both static and transient blockage must have a bounded return.
            if (++npc._pathFindFailCount > PATH_FAIL_TELEPORT) {
                npc._scriptMoveTargetId = RETURN_TELEPORT;
                npc._pathFindFailCount = 0;
            }
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (dir == CMoveAction.DIR_HARD_BLOCK) {
            if (++npc._pathFindFailCount > PATH_FAIL_TELEPORT) {
                // NC teleports after >20 consecutive path troubles.
                // L1 may teleport only into a genuinely free map cell.
                if (teleportIfFree(npc, x, y)) {
                    npc._pathFindFailCount = 0;
                    CCreatureController.sleep(npc, TELEPORT_MS);
                    return CCreatureController.RESULT_RUNNING;
                }
                // Never teleport onto a PC, NPC, door or occupied cell.
                // Keep the desire alive and retry when occupancy changes.
                npc._pathFindFailCount = PATH_FAIL_TELEPORT;
            }
        } else if (dir == CMoveAction.DIR_INVALID) {
            return CCreatureController.RESULT_INVALID;
        }

        // NC uses 1000ms both for a move request and a blocked route pass.
        CCreatureController.sleep(npc, NEXT_MS);
        return CCreatureController.RESULT_RUNNING;
    }

    static boolean validDestination(
            final L1NpcInstance npc, final int x, final int y) {
        final L1Map map = npc != null ? npc.getMap() : null;
        return map != null && map.isInMap(x, y) && map.isPassable(x, y);
    }

    static boolean isLongMove(
            final int fromX, final int fromY, final int x, final int y) {
        final long dx = (long) x - fromX;
        final long dy = (long) y - fromY;
        return dx * dx + dy * dy >= TELEPORT_DISTANCE_SQ;
    }

    private static boolean teleportIfFree(
            final L1NpcInstance npc, final int x, final int y) {
        if (!validDestination(npc, x, y)
                || !npc.TeleportToLocation(x, y, npc.getHeading(), 0)) {
            return false;
        }
        CMoveAction.invalidatePath(npc);
        return true;
    }

}
