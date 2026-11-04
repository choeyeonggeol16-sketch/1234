package l1j.server.server.model.network.core;

import l1j.server.server.model.network.packet.ServerPacket;

/** Deterministic admission/ownership contract, without sockets or a game DB. */
public final class CIOUserSocket {
    private final CIOFrame[] pool = new CIOFrame[12];
    public int begins, commits, aborts, active;
    public boolean connected = true, refuse, rejectCommit, throwCommit, capture = true;
    public int forcedCapacity;
    public byte[] lastBytes;
    public volatile int checksum;
    public CIOUserSocket() {
        for (int i = 0; i < pool.length; i++) pool[i] = new CIOFrame();
    }
    public boolean isClientConnected() { return connected; }
    public CIOFrame beginDirectFrame(int capacity) {
        if (refuse) return null;
        for (CIOFrame frame : pool) if (!frame.active) {
            frame.begin(forcedCapacity > 0 ? forcedCapacity : capacity, begins++);
            active++;
            return frame;
        }
        throw new AssertionError("unreleased test leases");
    }
    public boolean commitDirectFrame(CIOFrame frame) {
        if (!frame.active) throw new AssertionError("commit after release");
        if (throwCommit) throw new IllegalStateException("injected commit failure");
        if (rejectCommit) return false;
        int length = frame.writer.position();
        if (capture) {
            lastBytes = new byte[length - 2];
            for (int i = 2; i < length; i++) lastBytes[i - 2] = frame.writer.readByte(i);
        }
        checksum = length + frame.writer.readByte(length - 1);
        frame.release();
        active--;
        commits++;
        return true;
    }
    public void abortDirectFrame(CIOFrame frame) {
        frame.release();
        active--;
        aborts++;
    }
    public void sendLegacy(CIOClient client, ServerPacket packet) {
        CIOFrame frame = beginDirectFrame(65535);
        boolean sent = false;
        try {
            packet.serialize(client, frame.buffer());
            sent = commitDirectFrame(frame);
        } finally {
            if (!sent) abortDirectFrame(frame);
        }
    }
}
