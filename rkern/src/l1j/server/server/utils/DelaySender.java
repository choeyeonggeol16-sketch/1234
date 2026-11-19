package l1j.server.server.utils;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

public final class DelaySender implements Runnable {
    private L1PcInstance pc;
    private String message;

    public static void sendGreen(L1PcInstance pc, String message, long delay) {
        if (pc == null) return;
        if (delay <= 0L) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
        } else {
            ThreadPool.getInstance().scheduleRequired(new DelaySender(pc, message), delay);
        }
    }

    private DelaySender(L1PcInstance pc, String message) {
        this.pc = pc;
        this.message = message;
    }

    @Override
    public void run() {
        try {
            if (pc != null) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
        } finally {
            pc = null;
            message = null;
        }
    }
}
