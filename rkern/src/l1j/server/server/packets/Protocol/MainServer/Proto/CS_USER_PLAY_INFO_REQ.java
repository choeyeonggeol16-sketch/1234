package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** CS_USER_PLAY_INFO */
public final class CS_USER_PLAY_INFO_REQ {
    private CS_USER_PLAY_INFO_REQ() {}

    public static void handleRequest(CUserSocket client) {
        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        if (pc.isAutoClanjoin()) {
            final int shopVersion = pc.getPrivateShopVersion();
            final int stamp = pc.getSessionStamp();
            if (pc.setPrivateReady(shopVersion, true)) {
                CIOThreadCallback.getInstance().completeSession(client, 10_000L, () -> {
                    if (pc.getPrivateShopVersion() != shopVersion) return;
                    final int token = pc.tryReserveItemTransfer(client, stamp);
                    if (token == 0) {
                        clearPendingShop(pc, client, stamp, shopVersion);
                        return;
                    }
                    try {
                        pc.closePrivateStore(shopVersion);
                    } finally {
                        pc.endItemTransfer(token);
                    }
                }, failure -> {
                    if (failure != null) clearPendingShop(pc, client, stamp, shopVersion);
                });
            }
        }

        SC_USER_PLAY_INFO_NOTI.send(pc);
    }

    private static void clearPendingShop(L1PcInstance pc, CUserSocket client, int stamp, int shopVersion) {
        if (pc.getNetConnection() == client && pc.getSessionStamp() == stamp) {
            pc.setPrivateReady(shopVersion, false);
        }
    }
}
