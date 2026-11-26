package l1j.server.server.utils;

import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class DelaySender {
    private static final Logger LOG = Logger.getLogger(DelaySender.class.getName());

    private DelaySender() {
    }

    public static void sendGreen(L1PcInstance pc, String message, long delay) {
        if (pc == null) return;
        if (delay <= 0L) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
            return;
        }

        final CUserSocket session = pc.getNetConnection();
        if (session == null || !session.isConnected() || session.getActiveChar() != pc) return;
        final int sessionStamp = pc.getSessionStamp();
        final boolean accepted = CIOThreadCallback.getInstance().completeOnShard(session.affinityShard(), delay, () -> {
            if (session.isConnected() && session.getActiveChar() == pc
                    && pc.getNetConnection() == session && pc.getSessionStamp() == sessionStamp)
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
        }, failure -> {
            if (failure != null)
                LOG.log(Level.WARNING, "Delayed green message failed", failure);
        });
        if (!accepted && session.isConnected()) throw new RejectedExecutionException("Green message timer unavailable");
    }
}
