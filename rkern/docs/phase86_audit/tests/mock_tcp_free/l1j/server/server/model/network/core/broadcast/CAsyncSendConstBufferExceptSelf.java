package l1j.server.server.model.network.core.broadcast;

/** Original ExceptSelf predicate compares the source object's ID. */
public final class CAsyncSendConstBufferExceptSelf extends CAsyncSendConstBuffer {
    public CAsyncSendConstBufferExceptSelf(CSharedBroadcast.NativeWireWriter writer,
                    int senderId,int pendingLimit) {
        super(writer,senderId,pendingLimit);
    }
    private CAsyncSendConstBufferExceptSelf(
            CAsyncSendConstBuffer.Source pooled, int senderId) {
        super(pooled, senderId, Integer.MAX_VALUE);
    }

    /** Original reference design pool acquire -> direct packet assembly -> shared ref. */
    static CAsyncSendConstBufferExceptSelf forMove(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }
    }

    /** Native pooled CIOBufferEx: 8-byte S_ACTION direct write, no Java byte[]. */
    static CAsyncSendConstBufferExceptSelf forAction(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int actionId) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledActionConstBuffer(opcode, objectId, actionId);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }
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

    /** The first eligible recipient still determines the Native pool owner.
     * Defer construction to send(): unlike eager 16KiB-only assembly, its
     * overflow branch can freeze a single protocol-valid larger frame once.
     * Both paths keep the exact recipient order and existing CIO FIFO.
     */
    static CAsyncSendConstBufferExceptSelf forWritten(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId,CSharedBroadcast.NativeWireWriter writer) {
        if(socket==null||writer==null||!socket.isClientConnected())return null;
        return new CAsyncSendConstBufferExceptSelf(
                writer,senderId,Integer.MAX_VALUE);
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
