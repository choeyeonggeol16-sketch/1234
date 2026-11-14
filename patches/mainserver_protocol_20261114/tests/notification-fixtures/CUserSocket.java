package l1j.server.server.model.network.core.connection;
public final class CUserSocket {
    public byte[] sent;
    public int aborted;
    public boolean rejectCommit;
    public boolean isClientConnected() { return true; }
    public CIOFrame beginDirectFrame(int capacity) { return new CIOFrame(); }
    public boolean commitDirectFrame(CIOFrame frame) {
        if (rejectCommit) return false;
        sent = new byte[frame.buffer().position()];
        frame.bytes.get(0, sent);
        return true;
    }
    public void abortDirectFrame(CIOFrame frame) { aborted++; }
}
