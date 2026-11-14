package LIN;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;


/**
 * CS_USER_PLAY_INFO
 */
public final class CS_USER_PLAY_INFO_REQ {
	
    private CS_USER_PLAY_INFO_REQ() {}

    public static void handle(CIOClient client) {
        L1PcInstance pc = client.getActiveChar();
        if (pc == null)
            return;

        if (!pc.isPrivateReady() && pc.isPrivateShop() && pc.isAutoClanjoin()) {
            pc.setPrivateReady(true);
            // Admission can fail when the bounded scheduler is saturated.
            // Roll back the one-shot guard so a later play-info request may
            // schedule the close instead of leaving the shop permanently ready.
            if (ThreadPool.getInstance().schedule(() -> {
                if (pc.getNetConnection() == client && client.getActiveChar() == pc)
                    pc.setPrivateShop(false);
            }, 10000L) == null) {
                pc.setPrivateReady(false);
            }
        }

        LIN.SC_USER_PLAY_INFO_NOTI.send(pc);
    }
}