package l1j.server.server.model.item.function;

import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Floor scrolls 830001..830011 target maps 101..111. */
public final class OmanTowerTeleportHandler {

    private static final OmanTowerTeleportHandler instance = new OmanTowerTeleportHandler();
    public static OmanTowerTeleportHandler getInstance() { return instance; }

    // Primitive lookup: replaces 11 floor objects and HashMap entries.
    private static final int[] X = {
        32735, 32726, 32726, 32613, 32597, 32607,
        32618, 32598, 32609, 32726, 32657
    };
    private static final int[] Y = {
        32798, 32803, 32803, 32863, 32867, 32865,
        32866, 32867, 32866, 32803, 32797
    };

    private OmanTowerTeleportHandler() { }

    public void clickItem(L1PcInstance pc, int itemId, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != itemId
                || pc.getInventory().GetByServerID(item.getId()) != item) return;
        if (pc.isParalyzed() || pc.isSleeped() || pc.isDead() || !pc.getMap().isEscapable()) return;

        final int floor = itemId - 830001;
        if (floor < 0 || floor >= X.length) return;
        final int destinationMap = floor + 101;
        final int x;
        final int y;
        final short map;
        if (pc.getMapId() == destinationMap) {
            L1Location location = pc.getLocation().randomLocation(200, true);
            x = location.getX();
            y = location.getY();
            map = (short) location.getMapId();
        } else {
            x = X[floor];
            y = Y[floor];
            map = (short) destinationMap;
        }
        final int heading = pc.getHeading();
        final boolean autoHunt = pc.getAutoHunt();

        // No teleport until this exact scroll has been durably consumed.
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "오만의 탑 이동 주문서 소모에 실패했습니다."));
            tx.commitAfterInventoryRelease(() ->
                    pc.start_teleport(x, y, map, heading, 169, true, autoHunt));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 이동할 수 없습니다.");
        }
    }
}
