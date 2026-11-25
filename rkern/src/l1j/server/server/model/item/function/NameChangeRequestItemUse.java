package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.world.instance.InstanceEnums.InstStatus;
import l1j.server.server.model.world.instance.InstanceSpace;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class NameChangeRequestItemUse {
    private NameChangeRequestItemUse() {}

    static ItemActionResult handleRequestNameChange(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.is_combat_field() || !pc.getSafetyZone() || StadiumManager.getInstance().is_on_stadium(pc.getMapId())) {
            pc.sendPackets("해당 지역에서는 사용할 수 없습니다.");
            return ItemActionResult.STOP;
        }
        if (!pc.getInventory().HaveItemByClassId(408991)) {
            return ItemActionResult.STOP;
        }
        if (InstanceSpace.isInInstance(pc)) {
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
