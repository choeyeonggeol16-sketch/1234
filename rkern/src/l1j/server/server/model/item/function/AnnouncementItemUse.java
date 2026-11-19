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

/** Dedicated item action; original transaction and effect ordering preserved. */
final class AnnouncementItemUse {
    private AnnouncementItemUse() {}

    static ItemActionResult handleRequestAnnouncement(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getLevel() >= 82) {
            pc.setTemporaryItemObjectId(item.getId());
            pc.sendPackets("확성할 메시지를 입력해주세요.");
        } else
            pc.sendPackets("레벨 82이하의 용사님은 사용이 불가능 합니다.");
        return ItemActionResult.COMPLETE;
    }
}
