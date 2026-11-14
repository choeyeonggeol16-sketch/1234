package l1j.server.server.model.network.core.broadcast;

/** Original ExceptSelf predicate compares the source object's ID. */
public final class CAsyncSendConstBufferExceptSelf extends CAsyncSendConstBuffer {
    public CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, int pendingLimit) {
        super(frame, senderId, pendingLimit);
    }
    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
