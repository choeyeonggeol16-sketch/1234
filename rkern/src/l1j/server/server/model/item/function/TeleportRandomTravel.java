package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.BookmarkSlot;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;


/** Random-location teleport, including random clan mass teleport and action packets. */
final class TeleportRandomTravel {
    private TeleportRandomTravel() {}
    static void doRandomTeleport(L1PcInstance pc, int skillId, L1ItemInstance consumeItem) {
        final L1Location loc = pc.getLocation().randomLocation(200, true);
        final int x = loc.getX();
        final int y = loc.getY();
        final short mapId = (short) loc.getMapId();

        if (consumeItem != null) {
            ItemReceiptOperations.UseDurableConsume(pc, consumeItem, () -> {
                try {
                    executeRandomTeleport(pc, skillId, x, y, mapId);
                } finally {
                    try {
                        pc.cancelAbsoluteBarrier();
                        pc.cancelMoebius();
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, consumeItem);
                    }
                }
            });
        } else {
            executeRandomTeleport(pc, skillId, x, y, mapId);
        }
    }


    static void executeRandomTeleport(L1PcInstance pc, int skillId,
                                              int x, int y, short mapId) {
        if (skillId == L1SkillId.MASS_TELEPORT) {
            doMassTeleportRandom(pc, x, y, mapId);
        } else if (skillId == L1SkillId.TELEPORT) {
            startAction(pc);
            pc.start_teleport(x, y, mapId, pc.getHeading(), 169, true, true);
        } else {
            pc.start_teleport(x, y, mapId, pc.getHeading(), 169, true, false);
        }
    }


    static void doMassTeleportRandom(L1PcInstance pc, int x, int y, short mapId) {
        final int clanId = pc.getClanid();
        if (clanId == 0) { // 원본도 clan 체크 있었음
            startAction(pc);
            pc.start_teleport(x, y, mapId, pc.getHeading(), 169, true, true);
            return;
        }

        final L1Map map = L1WorldMap.getInstance().getMap(mapId);
        startAction(pc);

        for (L1PcInstance m : CWorld.getInstance().getVisiblePlayer(pc, 3)) {
            if (m == null) continue;
            if (m.getClanid() != clanId) continue;
            if (m.getId() == pc.getId()) continue;
            if (m.isPrivateShop()) continue;

            int nx = x + ThreadLocalRandom.current().nextInt(3) + 1;
            int ny = y + ThreadLocalRandom.current().nextInt(3) + 1;

            if (map.isInMap(nx, ny) && map.isPassable(nx, ny)) {
                m.start_teleport(nx, ny, mapId, m.getHeading(), 169, true, true);
            } else {
                m.start_teleport(x, y, mapId, m.getHeading(), 169, true, true);
            }
        }

        int nx = x + ThreadLocalRandom.current().nextInt(3) + 1;
        int ny = y + ThreadLocalRandom.current().nextInt(3) + 1;

        if (map.isInMap(nx, ny) && map.isPassable(nx, ny)) {
            pc.start_teleport(nx, ny, mapId, pc.getHeading(), 169, true, true);
        } else {
            pc.start_teleport(x, y, mapId, pc.getHeading(), 169, true, true);
        }
    }

    static void startAction(L1PcInstance pc) {
    	SC_DoActionGFX.send(
    	        pc.getNetConnection(),
    	        pc.getId(),
    	        19);

    	SC_DoActionGFX.broadcast(
    	        pc,
    	        19);
    }
}
