package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Retired legacy voucher entry: never consume a voucher or create a collar. */
public final class PetVoucherTransactions {
    private PetVoucherTransactions() {}
    public static void UsePetAmulet(CUserSocket client, L1ItemInstance input, int npcId) {
        if (client != null && client.getActiveChar() != null)
            SC_SystemMessage.send(client.getActiveChar(), "구형 펫 소환권은 이용할 수 없습니다.");
    }
}
