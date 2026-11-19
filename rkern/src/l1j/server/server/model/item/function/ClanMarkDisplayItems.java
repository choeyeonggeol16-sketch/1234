package l1j.server.server.model.item.function;

import java.util.Iterator;
import l1j.server.Config;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_IdentifyDesc;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;

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

            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                if (clan != null) {
                    SC_War.send(pc, i, pc.getClanname(), clan.getClanName());
                }
                pc.setQuizTime(curtime);
            }
        }
            return ItemActionResult.COMPLETE;
    }
}
