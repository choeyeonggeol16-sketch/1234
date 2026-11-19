package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Siege ammunition information; no consumption on click. */
final class SiegeSupplyItems {
    private SiegeSupplyItems() {}

    static ItemActionResult showCatapultSupplyNotice(ItemActionContext use) {
        SC_SystemMessage.send(use.player(), "공성전 시 투석기를 사용할 때 필요한 소모성 아이템");
        return ItemActionResult.COMPLETE;
    }
}
