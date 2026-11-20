package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/**
 * CS_USER_PLAY_INFO
 */
public final class CS_USER_PLAY_INFO_REQ {
	
    private CS_USER_PLAY_INFO_REQ() {}

    public static void handleRequest(CIOClient client) {
        L1PcInstance pc = client.getActiveChar();
        if (pc == null)
            return;

        if (!pc.isPrivateReady() && pc.isPrivateShop() && pc.isAutoClanjoin()) {
            pc.setPrivateReady(true);
            final int shopVersion = pc.getPrivateShopVersion();
            if (ThreadPool.getInstance().schedule(() ->
                    CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (client.getActiveChar() != pc || pc.getPrivateShopVersion() != shopVersion) return;
                        if (!pc.tryBeginItemTransfer()) {
                            pc.setPrivateReady(false);
                            return;
                        }
                        try { pc.setPrivateShop(false); }
                        finally { pc.endItemTransfer(); }
                    }), 10000L) == null) pc.setPrivateReady(false);
        }

        SC_USER_PLAY_INFO_NOTI.send(pc);
    }
}