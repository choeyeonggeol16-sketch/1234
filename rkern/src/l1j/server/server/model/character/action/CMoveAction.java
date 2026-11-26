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
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_MoveChar;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.model.world.instance.inn.InnRoom;
import l1j.server.server.model.world.instance.inn.loader.InnMapInfoLoader;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.shared.observer.CopyMapObservable;
import l1j.server.server.utils.GameUtils;

public final class CMoveAction extends CNewAction {

    private static final WorldCollisionHash WORLD_COLLISION = WorldCollisionHash.getInstance();

    CMoveAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        final L1PcInstance pc = controller.actor();
        if (pc == null
                || pc.isDead()
                || pc.isGhost()
                || pc.get_teleport()
                || pc.getNetConnection() == null
                || !pc.getNetConnection().isConnected()) {
            return false;
        }

        return MoveStep(
                pc,
                controller.i0(),
                controller.i1(),
                controller.i2(),
                System.currentTimeMillis());
    }

    @Override
    public boolean OnTick(long nowMs) {
        final L1PcInstance pc = controller.actor();
        if (pc == null
                || pc.isDead()
                || pc.isGhost()
                || pc.get_teleport()
                || pc.getNetConnection() == null
                || !pc.getNetConnection().isConnected()) {
            return true;
        }

        MoveStep(pc, controller.i0(), controller.i1(), controller.i2(), nowMs);
        return true;
    }


    boolean MoveStep(
            L1PcInstance pc,
            int sx,
            int sy,
            int h,
            long nowMs) {

        if (pc == null || (h & ~7) != 0) {
            if (pc != null) {
                SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
            }
            return false;
        }

        final int nextX = sx + GameUtils.HEADING_TABLE_X[h];
        final int nextY = sy + GameUtils.HEADING_TABLE_Y[h];

        if (!pc.hasSkillEffect(L1SkillId.비욘드)) {
            pc.killSkillEffectTimer(L1SkillId.MEDITATION);
        }
        pc.setCallClanId(0);

        final long interval = pc.getCurrentSpriteInterval(
                EActionCodes.fromInt(pc.getCurrentWeapon()));

        final int moveMap = pc.getMapId();
        if (!WORLD_COLLISION.reservePcMove(moveMap, nextX, nextY, pc.getId(), 140L)) {
            WORLD_COLLISION.cancelPcMove(moveMap, nextX, nextY, pc.getId());
            return false;
        }

        if (!validateMove(pc, h, sx, sy, nextX, nextY, interval)) {
            WORLD_COLLISION.cancelPcMove(moveMap, nextX, nextY, pc.getId());
            return false;
        }

        if (!applyMove(pc, moveMap, h, sx, sy, nextX, nextY, nowMs)) {
            SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
            return false;
        }
        return true;
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

    private boolean validateMove(
            L1PcInstance pc,
            int h,
            int sx,
            int sy,
            int nextX,
            int nextY,
            long interval) {

        final L1Map map = pc.getMap();
        if (map == null
                || !map.isInMap(sx, sy)
                || !map.isInMap(nextX, nextY)
                || !map.isPassable(sx, sy, h)) {

            SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
            return false;
        }

        if ((h & 1) != 0) {
            if (!map.isInMap(nextX, sy)
                    || !map.isInMap(sx, nextY)
                    || map.isClosedDoorCached(nextX, sy)
                    || map.isClosedDoorCached(sx, nextY)
                    || map.isTileBlocked(nextX, sy)
                    || map.isTileBlocked(sx, nextY)) {

                SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
                return false;
            }
        }

        if (!WalkDelayValidator.validate(pc, sx, sy, interval)) {
            return false;
        }

        final int pcOcc = WORLD_COLLISION.getPcOccupier(
                (short) pc.getMapId(), nextX, nextY);

        if ((pcOcc != 0 && pcOcc != pc.getId()) || WORLD_COLLISION.getNpcOccupier((short) pc.getMapId(), nextX, nextY) != 0) {
            SC_PacketBox.send(pc, SC_PacketBox.유저빽스탭, pc);
            return false;
        }

        pc.offFishing();
        final int mid = pc.getMapId();

        try {
            final int inn = InnMapInfoLoader.isInInnArea(nextX, nextY, mid);
            if (inn != -1 && InnRoom.input(pc, inn)) {
                return false;
            }

            final short mapid = CopyMapObservable.getInstance()
                    .idenMap((short) mid);

            if (Dungeon.getInstance().dg(nextX, nextY, mapid, pc)) {
                return false;
            }
            if (DungeonRandom.getInstance().dg(nextX, nextY, mapid, pc)) {
                return false;
            }

            if (nextX >= 32750 && nextX <= 32752 && nextY == 32874 && mapid == 445) {
                L1NeigeShip.join(pc);
                return false;
            }

            if (nextX >= 32731 && nextX <= 32733 && nextY == 32796 && mapid == 447) {
                L1NeigeShip.out(pc);
                return false;
            }


        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean applyMove(
            L1PcInstance pc,
            int moveMap,
            int h,
            int sx,
            int sy,
            int nextX,
            int nextY,
            long nowMs) {

        boolean committed = false;
        try {
            if (pc.get_teleport() || pc.isDead() || pc.getMapId() != moveMap) {
                WORLD_COLLISION.cancelPcMove(moveMap, nextX, nextY, pc.getId());
                return false;
            }
            if (!WORLD_COLLISION.commitPcMove(
                    moveMap, sx, sy, nextX, nextY, pc.getId())) {
                return false;
            }
            committed = true;
            pc.setHeading(h);
            pc.getLocation().set(nextX, nextY);
            pc.AddPassingPos(sx, sy, nextX, nextY, moveMap);
            L1World.getInstance().moveTo(pc, moveMap, sx, sy);
            pc.setLastMoveActionMillis(nowMs);
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
        return committed;
    }
}
