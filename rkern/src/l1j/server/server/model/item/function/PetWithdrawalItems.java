package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Old collar packets are rejected without consuming an item. */
public final class PetWithdrawalItems {
    private PetWithdrawalItems() {}
    public static boolean canWithdrawPet(L1PcInstance pc, int itemObjectId) {
        if (pc != null) SC_SystemMessage.send(pc, "구형 펫 기능은 이용할 수 없습니다.");
        return false;
    }
    public static boolean withdrawPet(L1PcInstance pc, int itemObjectId) {
        return canWithdrawPet(pc, itemObjectId);
    }
}
