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

    static CAsyncSendConstBufferExceptSelf forFormatted(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int kind, int opcode,
            int p0, int p1, int p2, int p3) {
        final CAsyncSendConstBuffer.Source pooled = socket.createPooledFormattedConstBuffer(
                kind, opcode, p0, p1, p2, p3);
        if (pooled == null) return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled, senderId);
        } catch (RuntimeException | Error failure) {
            pooled.release();
            throw failure;
        }
    }

    static CAsyncSendConstBufferExceptSelf forExtended(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId,int kind,int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        final CAsyncSendConstBuffer.Source pooled=
                socket.createPooledExtendedConstBuffer(
                        kind,opcode,p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }
    }

    static CAsyncSendConstBufferExceptSelf forWritten(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId,CSharedBroadcast.NativeWireWriter writer) {
        final CAsyncSendConstBuffer.Source source=
                CAsyncSendConstBuffer.openWrittenSource(socket,writer);
        if(source==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(source,senderId);
        } catch(RuntimeException|Error failure) {
            source.release();
            throw failure;
        }
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
