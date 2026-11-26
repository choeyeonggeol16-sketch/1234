package l1j.server.server.model.character.action;

import l1j.server.server.model.Dungeon;
import l1j.server.server.model.DungeonRandom;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.transport.L1NeigeShip;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1World;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.actionValidator.WalkDelayValidator;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_MoveChar;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.world.trap.L1WorldTraps;
import l1j.server.server.model.world.instance.inn.InnRoom;
import l1j.server.server.model.world.instance.inn.loader.InnMapInfoLoader;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.shared.observer.CopyMapObservable;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.GameUtils;

public final class CMoveAction extends CNewAction {

    private static final WorldCollisionHash WORLD_COLLISION = WorldCollisionHash.getInstance();
    static final long MOVED = 0L;
    static final long BLOCKED = -1L;

    private final WalkDelayValidator cadence = new WalkDelayValidator();

    CMoveAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        return moveRequestedStep(System.currentTimeMillis());
    }

    @Override
    public boolean OnTick(long nowMs) {
        moveRequestedStep(nowMs);
        return true;
    }

    private boolean moveRequestedStep(long nowMs) {
        final L1PcInstance pc = controller.actor();
        if (pc == null
                || pc.getMapId() != controller.l0()
                || pc.teleportTimerEpoch() != controller.i3()) {
            return false;
        }
        return moveStep(pc, controller.i0(), controller.i1(), controller.i2(),
                nowMs, System.nanoTime(), true) == MOVED;
    }

    /** One approved step, a blocked step, or time until the next eligible step. */
    long moveStep(L1PcInstance pc, int originX, int originY, int heading,
            long nowMs, long nowNanos, boolean predictedByClient) {
        if (pc == null || pc.isDead() || pc.isGhost() || pc.get_teleport() || pc.isLogoutStarted()) {
            return BLOCKED;
        }
        final CUserSocket session = pc.getNetConnection();
        if (session == null || !session.isConnected() || session.getActiveChar() != pc) {
            return BLOCKED;
        }
        final int stamp = pc.getSessionStamp();
        final int teleportEpoch = pc.teleportTimerEpoch();
        final L1Map map = pc.getMap();
        final int sx = pc.getX();
        final int sy = pc.getY();
        if (map == null || (heading & ~7) != 0 || originX != sx || originY != sy
                || isMovementRestricted(pc)) {
            return rejectStep(pc, predictedByClient, BLOCKED);
        }

        final short moveMap = pc.getMapId();
        final int nextX = sx + GameUtils.HEADING_TABLE_X[heading];
        final int nextY = sy + GameUtils.HEADING_TABLE_Y[heading];

        final long interval = pc.getCurrentSpriteInterval(
                EActionCodes.fromInt(pc.getCurrentWeapon()));
        final long wait = cadence.remainingNanos(stamp, moveMap, sx, sy, nowNanos, interval);
        if (wait > 0) return rejectStep(pc, predictedByClient, wait);

        if (!canWalkTile(map, heading, sx, sy, nextX, nextY)
                || !WORLD_COLLISION.reservePcMove(moveMap, nextX, nextY, pc.getId(), 140L)) {
            return rejectStep(pc, predictedByClient, BLOCKED);
        }

        boolean committed = false;
        try {
            final int pcOccupier = WORLD_COLLISION.getPcOccupier(moveMap, nextX, nextY);
            if ((pcOccupier != 0 && pcOccupier != pc.getId())
                    || WORLD_COLLISION.getNpcOccupier(moveMap, nextX, nextY) != 0
                    || !stillAtOrigin(pc, session, stamp, teleportEpoch, map, sx, sy)) {
                return rejectStep(pc, predictedByClient, BLOCKED);
            }

            if (!pc.hasSkillEffect(L1SkillId.비욘드)) {
                pc.killSkillEffectTimer(L1SkillId.MEDITATION);
            }
            pc.setCallClanId(0);
            pc.offFishing();
            if (usesMapTransition(pc, moveMap, nextX, nextY)) return BLOCKED;

            if (isMovementRestricted(pc) || !stillAtOrigin(pc, session, stamp, teleportEpoch, map, sx, sy)
                    || !WORLD_COLLISION.commitPcMove(moveMap, sx, sy, nextX, nextY, pc.getId())) {
                return rejectStep(pc, predictedByClient, BLOCKED);
            }
            committed = true;
            pc.setHeading(heading);
            pc.getLocation().set(nextX, nextY);
            cadence.recordStep(stamp, moveMap, sx, sy, nextX, nextY, nowNanos, interval);
            pc.setLastMoveActionMillis(nowMs);
            publishStep(pc, moveMap, sx, sy, nextX, nextY);
            return MOVED;
        } finally {
            if (!committed) WORLD_COLLISION.cancelPcMove(moveMap, nextX, nextY, pc.getId());
        }
    }

    private static long rejectStep(L1PcInstance pc, boolean predictedByClient, long result) {
        if (predictedByClient && !pc.get_teleport()) {
            SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
        }
        return result;
    }

    private static boolean stillAtOrigin(L1PcInstance pc, CUserSocket session, int stamp,
            int teleportEpoch, L1Map map, int x, int y) {
        return !pc.isDead() && !pc.isGhost() && !pc.get_teleport() && !pc.isLogoutStarted()
                && pc.getNetConnection() == session && session.isConnected() && session.getActiveChar() == pc
                && pc.getSessionStamp() == stamp && pc.teleportTimerEpoch() == teleportEpoch
                && pc.getMap() == map && pc.getX() == x && pc.getY() == y;
    }

    private static boolean isMovementRestricted(L1PcInstance pc) {
        return pc.isParalyzed() || pc.isSleeped() || CommonUtil.isnonMoveSkill(pc);
    }

    public boolean OnStopMove() {
        return true;
    }

    public void SetMoving() {
    }

    public void Start() {
        AddTimer(1);
    }

    public boolean Tick(long nowMs) {
        return OnTick(nowMs);
    }

    private static boolean canWalkTile(L1Map map, int heading, int sx, int sy, int nextX, int nextY) {
        if (!map.isInMap(sx, sy) || !map.isInMap(nextX, nextY)
                || map.isClosedDoorCached(nextX, nextY) || !map.isPassable(sx, sy, heading)) {
            return false;
        }
        return (heading & 1) == 0 || (map.isInMap(nextX, sy)
                && map.isInMap(sx, nextY)
                && !map.isClosedDoorCached(nextX, sy)
                && !map.isClosedDoorCached(sx, nextY)
                && !map.isTileBlocked(nextX, sy)
                && !map.isTileBlocked(sx, nextY));
    }

    private static boolean usesMapTransition(L1PcInstance pc, int mapId, int nextX, int nextY) {
        try {
            final int inn = InnMapInfoLoader.isInInnArea(nextX, nextY, mapId);
            if (inn != -1 && InnRoom.input(pc, inn)) {
                return true;
            }

            final short mapid = CopyMapObservable.getInstance().idenMap((short) mapId);

            if (Dungeon.getInstance().dg(nextX, nextY, mapid, pc)
                    || DungeonRandom.getInstance().dg(nextX, nextY, mapid, pc)) {
                return true;
            }

            if (nextX >= 32750 && nextX <= 32752 && nextY == 32874 && mapid == 445) {
                L1NeigeShip.join(pc);
                return true;
            }

            if (nextX >= 32731 && nextX <= 32733 && nextY == 32796 && mapid == 447) {
                L1NeigeShip.out(pc);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }

        return false;
    }

    private static void publishStep(L1PcInstance pc, int moveMap, int sx, int sy, int nextX, int nextY) {
        try {
            pc.AddPassingPos(sx, sy, nextX, nextY, moveMap);
            L1World.getInstance().moveTo(pc, moveMap, sx, sy);
            SC_MoveChar.broadcast(pc);

            final int ztype = pc.getZoneType();
            if (ztype == 0) {
                if (pc.getSafetyZone()) {
                    SC_ActionUI.send(pc, SC_ActionUI.SAFETYZONE, false);
                    pc.setSafetyZone(false);
                }
                pc.stopEinhasadTimer();
            } else {
                if (ztype == 1) {
                    pc.startEinhasadTimer();
                } else {
                    pc.stopEinhasadTimer();
                }

                if (!pc.getSafetyZone()) {
                    SC_ActionUI.send(pc, SC_ActionUI.SAFETYZONE, true);
                    pc.setSafetyZone(true);
                }
            }

            CastleWarBusiness.move(pc);

//            if (L1HauntedHouse.getInstance().getHauntedHouseStatus()
//                    == L1HauntedHouse.STATUS_PLAYING) {
//
//                if (L1HauntedHouse.getInstance().isMember(pc)
//                        && nextX >= 32872 && nextX <= 32875
//                        && nextY >= 32828 && nextY <= 32833) {
//                    L1HauntedHouse.getInstance().endHauntedHouse(pc);
//                }
//            }

            L1WorldTraps.getInstance().onPlayerMoved(pc);

            final CParty party = pc.getParty();
            if (party != null) {
                party.refreshPartyMemberStatus(pc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
