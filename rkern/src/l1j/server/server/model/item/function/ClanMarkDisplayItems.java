package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_War;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Extracted ClanMarkDisplayItems handlers; effect, consumption and transaction order unchanged. */
final class ClanMarkDisplayItems {
    private ClanMarkDisplayItems() {}

    static ItemActionResult handleFishingRod(ItemActionContext use) {
        L1PcInstance pc = use.player();
        {
            long curtime = System.currentTimeMillis() / 1000;
            if (pc.getQuizTime() + 5 > curtime) {
                long time = (pc.getQuizTime() + 5) - curtime;
                SC_ChatPacket.sendDirect(pc, pc, time + " 초 후 사용 하시길 바랍니다.");
                return ItemActionResult.STOP;
            }
            int i = 1;
            if (pc.문장주시) {
                i = 3;
                pc.문장주시 = false;
            } else
                pc.문장주시 = true;
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "모든 혈맹의 마크를 표시하거나 종료하였습니다.");

            for (L1Clan clan : CWorld.getInstance().getAllClans()) {
                if (clan != null) {
                    SC_War.send(pc, i, pc.getClanname(), clan.getClanName());
                }
                pc.setQuizTime(curtime);
            }
        }
            return ItemActionResult.COMPLETE;
    }
}
