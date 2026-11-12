package l1j.server.server.packets.Protocol;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.buffer.WriteBuffer;

public final class Proto {

	private Proto() {}

	public static void send(L1PcInstance pc, ProtoPacket packet) {
		if (pc == null || packet == null) return;
		pc.sendPackets(packet);
	}

    /** Begin an ordinary pooled direct frame; maxWireBytes includes its 2-byte header. */
    public static ProtoWriter begin(L1PcInstance pc, int messageId, int maxWireBytes) {
        return pc == null ? null : begin(pc.getNetConnection(), messageId, maxWireBytes);
    }

    public static ProtoWriter begin(CIOClient client, int messageId, int maxWireBytes) {
        if (client == null || !client.isConnected()) return null;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return null;
        final CIOFrame frame = connection.beginDirectFrame(maxWireBytes);
        if (frame == null) return null;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(messageId);
            final ProtoWriter writer = ProtoWriter.direct(buffer);
            writer.directFrame = frame;
            writer.directConnection = connection;
            return writer;
        } catch (Throwable failure) {
            connection.abortDirectFrame(frame);
            throw failure;
        }
    }

    /** Finish the protobuf trailer before transferring this lease to the FIFO. */
    public static boolean commit(ProtoWriter writer) {
        if (writer == null || writer.directFrame == null) return false;
        final CUserSocket connection = writer.directConnection;
        final CIOFrame frame = writer.directFrame;
        boolean committed = false;
        try {
            writer.finishDirect(true);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            writer.detachDirect();
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    /** Safe in a caller's finally, including after commit or a rejected commit. */
    public static void abort(ProtoWriter writer) {
        if (writer == null || writer.directFrame == null) return;
        final CUserSocket connection = writer.directConnection;
        final CIOFrame frame = writer.directFrame;
        writer.detachDirect();
        connection.abortDirectFrame(frame);
    }

    public static int capacityBound(long bytes) {
        return (int) Math.max(7L, Math.min(65535L, bytes));
    }

    public static boolean sendRaw(L1PcInstance pc, byte[] encoded) {
        return pc != null && sendRaw(pc.getNetConnection(), encoded);
    }

    /** Cached bytes already include opcode/message ID/trailer; no Raw wrapper. */
    public static boolean sendRaw(CIOClient client, byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > 65533
                || client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(encoded.length + 2);
        if (frame == null) return false;
        boolean committed = false;
        try {
            frame.buffer().writeBytes(encoded);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
