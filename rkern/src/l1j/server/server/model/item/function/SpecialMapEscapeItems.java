package l1j.server.server.model.item.function;

import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.polyAction;

/** Item-specific action effects dispatched by item ID. */
final class SpecialMapEscapeItems {
    private SpecialMapEscapeItems() {}

    static ItemActionResult handleReturnFromSpecialMap(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getMapId() >= 1708 && pc.getMapId() == 1712 ||
            pc.getMapId() == 7783 || pc.getMapId() == 3 ||
            pc.getMapId() == 13004 || pc.getMapId() == 12152 ||
            pc.getMapId() == 12149 || pc.getMapId() == 12154 ||
            pc.getMapId() == 12358 || pc.getMapId() == 12153 ||
            pc.getMapId() == 12146 || pc.getMapId() == 12147 ||
            pc.getMapId() == 12148 || pc.getMapId() == 12258 ||
            pc.getMapId() == 12150 || pc.getMapId() == 12257 ||
            pc.getMapId() == 12358) {
            SC_SystemMessage.send(pc, "해당 위치 및 맵에서는 사용이 불가능 합니다.");
            return ItemActionResult.STOP;
        }

        if (pc.getMap().isEscapable() || pc.isGm()) {
            final int rx = _random.nextInt(7);
            final int ux = 32768 + rx;
            final int uy = 32834 + rx;
            UseDurableConsume(pc, item, () -> {
                try {
                    pc.start_teleport(ux, uy, 622, pc.getHeading(), 169, true, pc.getAutoHunt());
                    cancelAbsoluteBarrier(pc);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            return ItemActionResult.STOP;
        } else {
            SC_ServerMessage.send(pc, 647);
            cancelAbsoluteBarrier(pc);
        }
        return ItemActionResult.COMPLETE;
    }

}
