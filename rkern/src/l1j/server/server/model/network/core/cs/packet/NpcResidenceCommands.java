package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.town.CResidenceSettlement;

final class NpcResidenceCommands {
    private NpcResidenceCommands() {}

    static boolean handle(L1PcInstance pc, L1Object object, int objectId, String command) {
        boolean enroll = "set".equalsIgnoreCase(command);
        boolean leave = "clear".equalsIgnoreCase(command);
        if (!enroll && !leave && !"ask".equalsIgnoreCase(command)) return false;
        if (!(object instanceof L1NpcInstance npc)) return true;
        int id = L1TownLocation.getTownIdByNpcid(npc.getNpcId());
        if (!CResidenceSettlement.isTown(id)) return true;
        var town = TownTable.getInstance().getTownTable(id);
        if (!enroll && !leave) {
            String leader = town == null ? null : town.leaderName();
            if (leader == null || leader.isEmpty()) SC_NPCTalkReturn.send(pc, objectId, "noowner");
            else SC_NPCTalkReturn.send(pc, objectId, "owner", new String[]{leader});
            return true;
        }
        if (town == null) return true;
        if (!CResidenceSettlement.tryMembershipChange()) {
            SC_SystemMessage.send(pc, "마을 정산 중입니다. 잠시 후 다시 시도해 주세요.");
            return true;
        }
        try {
            if (enroll) enroll(pc, id);
            else leave(pc, id);
        } finally {
            CResidenceSettlement.unlockPersistence();
        }
        SC_NPCTalkReturn.send(pc, objectId, "");
        return true;
    }

    private static void enroll(L1PcInstance pc, int townId) {
        int current = pc.getHomeTownId();
        if (current == -1) {
            SC_ServerMessage.send(pc, 759);
            return;
        }
        if (current > 0) {
            if (current != townId) {
                var previous = TownTable.getInstance().getTownTable(current);
                if (previous != null) SC_ServerMessage.send(pc, 758, previous.name());
            }
            return;
        }
        if (pc.getLevel() < 10) {
            SC_ServerMessage.send(pc, 757);
            return;
        }
        long cost = 10L * pc.getLevel() * pc.getLevel();
        if (cost > CInventory.MAX_AMOUNT || !pc.getInventory().DeleteItemInInventory(L1ItemId.ADENA, (int) cost)) {
            SC_ServerMessage.send(pc, 337, "$4");
            return;
        }
        pc.setHomeTownId(townId);
        pc.save();
    }

    private static void leave(L1PcInstance pc, int townId) {
        int current = pc.getHomeTownId();
        if (current == townId) {
            pc.setHomeTownId(-1);
            pc.save();
        } else if (current > 0) {
            SC_ServerMessage.send(pc, 756);
        }
    }
}
