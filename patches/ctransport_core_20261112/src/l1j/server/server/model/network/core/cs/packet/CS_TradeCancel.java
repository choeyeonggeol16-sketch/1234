package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_TradeCancel {

    private CS_TradeCancel() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null) {
            return;
        }

        final int partnerId = player.getTradeID();
        if (partnerId <= 0) {
            new L1Trade().TradeCancel(player);
            return;
        }
        final L1Object other = L1World.getInstance().findObject(partnerId);
        final L1PcInstance partner = other instanceof L1PcInstance ? (L1PcInstance) other : null;
        final L1PcInstance coordinator = partner != null && partner.getId() < player.getId()
                ? partner : player;
        if (!coordinator.tryBeginTradeResolution()) return;
        try {
            if (player.getTradeID() != partnerId) return;
            new L1Trade().TradeCancel(player);
        } finally {
            coordinator.endTradeResolution();
        }
    }
}