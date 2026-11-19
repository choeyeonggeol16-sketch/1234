package l1j.server.server.model.item.function;

import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

/** Pure item ID routing; all return/teleport side effects stay in specialized handlers. */
final class ReturnAndTeleportItems {
    private ReturnAndTeleportItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance item, int itemId) {
        switch (itemId) {
            case 3000147, 202099, 3000229, 875640424, 875640415, 3000413, 240100 ->
                    IslandReturnScrollUse.use(client, pc, item, itemId);
            case 748, 749 -> ShopTeleportAmuletUse.use(client, pc, item, itemId);
            case 40124, 30086, MJINNHelper.INN_KEYID, 700023 ->
                    ClanInnReturnScrollUse.use(client, pc, item, itemId);
            default -> { }
        }
    }
}
