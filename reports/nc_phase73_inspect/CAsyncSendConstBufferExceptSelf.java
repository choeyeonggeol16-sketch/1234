package l1j.server.server.model.network.core.broadcast;

/** Original ExceptSelf predicate compares the source object's ID. */
public final class CAsyncSendConstBufferExceptSelf extends CAsyncSendConstBuffer {
    public CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, int pendingLimit) {
        super(frame, senderId, pendingLimit);
    }
    private CAsyncSendConstBufferExceptSelf(
            CAsyncSendConstBuffer.Source pooled, int senderId) {
        super(pooled, senderId, Integer.MAX_VALUE);
    }

    /** Original NC-style pool acquire -> direct packet assembly -> shared ref. */
    static CAsyncSendConstBufferExceptSelf forMove(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);
    }

    /** Native pooled CIOBufferEx: 8-byte S_ACTION direct write, no Java byte[]. */
    static CAsyncSendConstBufferExceptSelf forAction(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int actionId) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledActionConstBuffer(opcode, objectId, actionId);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
