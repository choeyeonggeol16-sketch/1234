package l1j.server.server.model.network.core.broadcast;

/** Original ExceptSelf predicate compares the source object's ID. */
public final class CAsyncSendConstBufferExceptSelf extends CAsyncSendConstBuffer {
    public CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, int pendingLimit) {
        super(frame, senderId, pendingLimit);
    }
    /** Only this class constructs the owned move bytes; no mutable array escapes. */
    private CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, boolean owned) {
        super(frame, senderId, Integer.MAX_VALUE, owned);
    }

    /** Create the entire immutable 17-byte movement frame inside its sole owner. */
    static CAsyncSendConstBufferExceptSelf forMove(
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final byte[] wire = new byte[17];
        wire[0] = 17;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) x;
        wire[8] = (byte) (x >>> 8);
        wire[9] = (byte) y;
        wire[10] = (byte) (y >>> 8);
        wire[11] = (byte) heading;
        wire[12] = (byte) 129;
        // The two-byte length high byte and trailing int remain zero.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }

    /**
     * NC-inspired once-assembled 8-byte L1 S_ACTION plaintext. The mutable
     * array is created here and never exposed to the caller. Only the existing
     * internal owned-frame constructor receives it, so there is no clone.
     * Native/Java SEND consumers retain their pre-existing FIFO/lease contract.
     */
    static CAsyncSendConstBufferExceptSelf forAction(
            int senderId, int opcode, int objectId, int actionId) {
        final byte[] wire = new byte[8];
        wire[0] = 8;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) actionId;
        // wire[1] is the zero high byte of the 2-byte framing header.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
