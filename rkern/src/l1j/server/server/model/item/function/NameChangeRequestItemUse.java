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
final class NameChangeRequestItemUse {
    private NameChangeRequestItemUse() {}

    static ItemActionResult handleRequestNameChange(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.is_combat_field() || !pc.getSafetyZone() || StadiumManager.getInstance().is_on_stadium(pc.getMapId())) {
            pc.sendPackets("해당 지역에서는 사용할 수 없습니다.");
            return ItemActionResult.STOP;
        }
        if (!pc.getInventory().checkItem(408991)) {
            return ItemActionResult.STOP;
        }
        if (MJInstanceSpace.isInInstance(pc)) {
            if (pc.getInstStatus() != InstStatus.INST_USERSTATUS_NONE) {
                pc.sendPackets("해당 지역에서는 사용할 수 없습니다.");
                return ItemActionResult.STOP;
            }
        }

        pc.setChangechaname(true);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "잠시후 캐릭터 이름 변경이 진행됩니다.");
        pc.sendPackets("잠시후 캐릭터 이름 변경이 진행됩니다.");

        ThreadPool.getInstance().scheduleRequired(new Runnable() {
            @Override
            public void run() {
                SC_ChangeCharName.sendStart(pc);
            }
        }, 3000L);
        return ItemActionResult.COMPLETE;
    }
}
