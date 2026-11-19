package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_TradeAccept {

    private CS_TradeAccept() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null
                || player.isGhost()) {

            return;
        }

        final int tradeId =
                player.getTradeID();

        if (tradeId <= 0) {
            return;
        }

        /*
         * 원본:
         *
         * (L1PcInstance) L1World.getInstance().findObject(tradeId)
         *
         * 여기서는 잘못된 object id가 들어와도
         * ClassCastException 내지 않게 안전하게 처리.
         */
        final L1Object object =
                L1World
                        .getInstance()
                        .findObject(tradeId);

        if (!(object instanceof L1PcInstance)) {

            new L1Trade()
                    .TradeCancel(player);

            return;
        }

        final L1PcInstance tradingPartner =
                (L1PcInstance) object;

        if (tradingPartner == player || tradingPartner.getTradeID() != player.getId()) {
            new L1Trade().TradeCancel(player);
            return;
        }
        final L1PcInstance coordinator = player.getId() < tradingPartner.getId()
                ? player : tradingPartner;
        final L1PcInstance other = coordinator == player ? tradingPartner : player;
        if (!coordinator.tryBeginTradeResolution()) return;
        if (!other.tryBeginTradeResolution()) {
            coordinator.endTradeResolution();
            return;
        }
        boolean asynchronous = false;
        try {
            player.setTradeOk(true);
            // Check both sides again after acquiring the per-pair settlement gate.
            // A simultaneous accept/cancel must not commit this trade twice.
            if (player.getTradeID() != tradingPartner.getId()
                    || tradingPartner.getTradeID() != player.getId()
                    || !player.getTradeOk() || !tradingPartner.getTradeOk()) return;

            asynchronous = new L1Trade().TradeOK(player);

        } finally {
            // The CacheD completion closes the settlement gate after COMMIT.
            if (!asynchronous) {
                other.endTradeResolution();
                coordinator.endTradeResolution();
            }
        }
    }
}
