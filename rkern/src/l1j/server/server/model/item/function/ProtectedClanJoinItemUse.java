package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class ProtectedClanJoinItemUse {
    private static final String[] PROTECTED_CLAN_NAMES = { "중립" };

    private ProtectedClanJoinItemUse() {}

    static ItemActionResult handleJoinProtectedClan(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            if (pc.getLevel() >= Config.신규혈맹보호레벨) {
                SC_SystemMessage.send(pc, Config.신규혈맹보호레벨 + "레벨 이상은 "+Config.신규혈맹이름+"혈맹에 가입할 수 없습니다.");
                return ItemActionResult.STOP;
            }

            if (pc.getClanid() != 0) {
                SC_SystemMessage.send(pc, "당신은 이미 혈맹에 가입하였습니다.");
                return ItemActionResult.COMPLETE;
            }

            int random_id = CommonUtil.random(1);

            L1Clan clan = L1World.getInstance().findClan(PROTECTED_CLAN_NAMES[random_id]);
            L1ClanJoin.getInstance().tutorialJoin(clan, pc);
            pc.getInventory().removeItem(item, 1);
            pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
            return ItemActionResult.COMPLETE;
        }
    }
}
