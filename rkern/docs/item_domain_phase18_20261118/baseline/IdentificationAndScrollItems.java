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
/** Extracted IdentificationAndScrollItems handlers; effect, consumption and transaction order unchanged. */
final class IdentificationAndScrollItems {
    private IdentificationAndScrollItems() {}

    static ItemActionResult handleIdentifyItem(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            int add_mpr = targetItem.getItem().get_addmpr();
            int add_hpr = targetItem.getItem().get_addhpr();
            int safe_enchant = targetItem.getItem().get_safeenchant();
            if (!targetItem.isIdentified()) {
                targetItem.setIdentified(true);
                pc.getInventory().updateItem(targetItem, L1PcInventory.COL_IS_ID);
            }
            SC_IdentifyDesc.send(pc, targetItem);

            pc.getInventory().removeItem(item, 1);
            StringBuffer sb = new StringBuffer();
            if (targetItem.getItem().getType2() == 1 || targetItem.getItem().getType2() == 2) {
                if (safe_enchant == -1) {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: 불가능");
                } else if (safe_enchant == 0) {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: 0");
                } else {
                    sb.append("피틱: " + add_hpr + " /");
                    sb.append("엠틱: " + add_mpr + " /");
                    sb.append("기본 인첸트: " + safe_enchant + "");
                }
            }
            SC_SystemMessage.send(pc, sb.toString());
            sb = null;
        }
            return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleWriteBlankScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int blankScrollSkillId = use.blankScrollSkillId();
        BlankMagicScrolls.write(pc, item, blankScrollSkillId);
        return ItemActionResult.STOP;
    }
}
