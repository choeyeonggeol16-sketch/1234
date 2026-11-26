package l1j.server.server.model.network.core.broadcast;

import l1j.server.server.model.network.core.connection.CUserSocket;

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

    static CAsyncSendConstBufferExceptSelf forMove(
            CUserSocket socket,
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        return wrap(socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading), senderId);
    }

    static CAsyncSendConstBufferExceptSelf forAction(
            CUserSocket socket,
            int senderId, int opcode, int objectId, int actionId) {
        return wrap(socket.createPooledActionConstBuffer(opcode, objectId, actionId), senderId);
    }

    static CAsyncSendConstBufferExceptSelf forFormatted(
            CUserSocket socket,
            int senderId, int kind, int opcode,
            int p0, int p1, int p2, int p3) {
        return wrap(socket.createPooledFormattedConstBuffer(kind, opcode, p0, p1, p2, p3), senderId);
    }

    static CAsyncSendConstBufferExceptSelf forExtended(
            CUserSocket socket,
            int senderId,int kind,int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        return wrap(socket.createPooledExtendedConstBuffer(
                kind, opcode, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10), senderId);
    }

    private static CAsyncSendConstBufferExceptSelf wrap(Source pooled, int senderId) {
        if (pooled == null) return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled, senderId);
        } catch (RuntimeException | Error failure) {
            try { pooled.release(); }
            catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }
}
