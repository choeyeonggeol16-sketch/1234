package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
final class NpcPetCommands {
    private NpcPetCommands() {}
    static void petbuy(CUserSocket client, int npcId, int currencyId, int price) {
        if (client != null && client.getActiveChar() != null)
            SC_SystemMessage.send(client.getActiveChar(), "구형 펫 구매 기능은 이용할 수 없습니다.");
    }
    static void withdrawPets(L1PcInstance pc, int objid) {
        if (pc != null) SC_SystemMessage.send(pc, "구형 펫 기능은 이용할 수 없습니다.");
    }
}
