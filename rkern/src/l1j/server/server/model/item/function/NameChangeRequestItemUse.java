package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.thread.CIOThreadCallback;

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
        final var socket = pc.getNetConnection();
        if (socket == null || socket.getActiveChar() != pc) return ItemActionResult.STOP;
        if (!CIOThreadCallback.getInstance().completeSession(socket, 3000L,
                () -> SC_ChangeCharName.sendStart(pc), failure -> { })) return ItemActionResult.STOP;

        pc.setChangechaname(true);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "잠시후 캐릭터 이름 변경이 진행됩니다.");
        pc.sendPackets("잠시후 캐릭터 이름 변경이 진행됩니다.");
        return ItemActionResult.COMPLETE;
    }
}
