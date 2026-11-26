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


/** Item and skill teleport restrictions. Original ordering/message behavior retained. */
final class TeleportItemRules {
    private TeleportItemRules() {}
private static final int[] TOWER_KEYS = {
        830022,830023,830024,830025,830026,830027,830028,830029,830030,830031
    };
    
    static boolean canBypassMapRestrictions(L1PcInstance pc) {
        if (pc == null || !pc.isGm()) return false;
        final var client = pc.getNetConnection();
        final var session = client == null ? null : client.getAutomationSession();
        return client == null || !client.requiresPlayerTeleportRules()
                && (session == null || session == ClientAutomationSession.SERVER);
    }

    static boolean preCheckTeleportState(L1PcInstance pc) {
        try {

    		if (pc.hasSkillEffect(L1SkillId.이터디스) || pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
    		    pc.tpEndFinish();
    			return false;
    		}
    		if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
    			SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
    		    pc.tpEndFinish();
    			return false;
    		}
        	// 스킬 상태나 어떠한 경우에 실패처리
        	
        } catch (Throwable ignore) {}
        return true;
    }

    static boolean isValidBookmark(L1PcInstance pc, int x, int y, short mapId) {
        return pc.getUserBookmark().CanUseBookmark(pc, x, y, mapId);
    }

    static boolean canRandomTeleportHere(L1PcInstance pc) {
        final int mapId = pc.getMapId();

        if (mapId >= 101 && mapId <= 110) {
            L1ItemInstance need = pc.getInventory().GetFirstItemByClassID(TOWER_KEYS[mapId - 101]);
            L1ItemInstance alt  = pc.getInventory().GetFirstItemByClassID(560028);
            if (need != null || alt != null) return true;
            SC_ServerMessage.send(pc, 276);
            pc.tpEndFinish();
            return false;
        }

        if (mapId >= 12852 && mapId <= 12861) {
            L1ItemInstance need = pc.getInventory().GetFirstItemByClassID(TOWER_KEYS[mapId - 12852]);
            L1ItemInstance alt  = pc.getInventory().GetFirstItemByClassID(4100135);
            if (need != null || alt != null) return true;
            SC_ServerMessage.send(pc, 276);
            pc.tpEndFinish();
            return false;
        }

        if (mapId == 54 && !TeleportUseService.canBypassMapRestrictions(pc)) {
            if (Config.PRISONTEL) {
                long t = L1GameTimeClock.getInstance().getGameTime().getSeconds() % 86400;
                if (t >= 60 * 60 * 20 || t <= 60 * 60 * 6) {
                    pc.tpEndFinish();
                    SC_ServerMessage.send(pc, 6648);
                    return false;
                }
                if (Config.PRISONTELS) return true;
                pc.tpEndFinish();
                SC_ServerMessage.send(pc, 276);
                return false;
            }

            if (Config.PRISONTELS) return true;

            pc.tpEndFinish();
            SC_ServerMessage.send(pc, 276);
            return false;
        }

        if (mapId >= 1708 && mapId <= 1709 && !TeleportUseService.canBypassMapRestrictions(pc)) {
            pc.tpEndFinish();
            SC_ServerMessage.send(pc, 276);
            return false;
        }

        L1Map map = pc.getMap();
        if (map.isTeleportable() || TeleportUseService.canBypassMapRestrictions(pc) || (pc.getInventory().HaveItemByClassId(900111) && map.isRuler())) {
            return true;
        }
        pc.tpEndFinish();
        SC_ServerMessage.send(pc, 276);
        return false;
    }
}
