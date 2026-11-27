package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;

import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.templates.BookmarkSlot;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;


/** Bookmark mass teleport nearby clan-member fanout. */
final class TeleportClanBookmarkTravel {
    private TeleportClanBookmarkTravel() {}
    static void teleportClanMembersAroundBookmarkMass(L1PcInstance pc,
                                                             int bx, int by, short mapId,
                                                             L1Map map) {
        final int clanId = pc.getClanid();
        if (clanId == 0) return;

        for (L1PcInstance m : CWorld.getInstance().getVisiblePlayer(pc, 3)) {
            if (m == null) continue;
            if (m.getClanid() != clanId) continue;
            if (m.getId() == pc.getId()) continue;
            if (m.isPrivateShop()) continue;

            int nx = bx + GameRandom.nextInt(3) + 1;
            int ny = by + GameRandom.nextInt(3) + 1;

            if (map.isInMap(nx, ny) && map.isPassable(nx, ny)) {
                m.start_teleport(nx, ny, mapId, m.getHeading(), 169, true, true);
            } else {
                m.start_teleport(bx, by, mapId, m.getHeading(), 169, true, true);
            }
        }
    }
}
