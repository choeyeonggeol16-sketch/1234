package l1j.server.server.model.network.core.sc.packet;

import java.util.List;
import java.util.Objects;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_ClanAttention {
    private SC_ClanAttention() {}


    public static NativeWireWriter nativeMentWriter(boolean on, String name) {
        final byte[] text = ProtoBroadcastFields.encode(name);
        if (text == null || text.length > CIOBufferPool.MAX_WIRE_BYTES - 7) return null;
        return out -> out.put((byte) Opcodes.S_PLEDGE_WATCH).put((byte) (on ? 32 : 31))
                .putShort((short) 269).put(text).put((byte) 0);
    }

    public static NativeWireWriter nativeListWriter(int count, List<String> source) {
        final String[] names = snapshotNames(source);
        if (listCapacity(names) == 0) return null;
        return out -> {
            out.put((byte) Opcodes.S_PLEDGE_WATCH).putShort((short) 2).putInt(names.length);
            for (String name : names) {
                CSharedBroadcast.writeMS949(out, name);
                out.put((byte) 0);
            }
        };
    }

    private static String[] snapshotNames(List<String> source) {
        if (source == null) return new String[0];
        return source.stream().filter(Objects::nonNull).toArray(String[]::new);
    }

    private static int listCapacity(String[] names) {
        long reserve = 9;
        for (String name : names) reserve += 2L * name.length() + 1L;
        if (reserve <= CIOBufferPool.MAX_WIRE_BYTES) return (int) reserve;
        reserve = 9;
        for (String name : names) {
            final byte[] encoded = ProtoBroadcastFields.encode(name);
            if (encoded == null || (reserve += encoded.length + 1L) > CIOBufferPool.MAX_WIRE_BYTES) return 0;
        }
        return (int) reserve;
    }

    
    public static boolean sendMent(L1PcInstance pc, boolean on, String name) {
        final CUserSocket connection = pc == null ? null : pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final byte[] text = ProtoBroadcastFields.encode(name);
        if (text == null || text.length > CIOBufferPool.MAX_WIRE_BYTES - 7) return false;
        final CIOFrame frame = connection.beginDirectFrame(7 + text.length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeC(on ? 32 : 31);
            buffer.writeH(269);
            buffer.writeBytes(text);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendList(L1PcInstance pc, int count, List<String> source) {
        final CUserSocket connection = pc == null ? null : pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final String[] names = snapshotNames(source);
        final int size = listCapacity(names);
        if (size == 0) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeH(2);
            buffer.writeD(names.length);
            for (String name : names) buffer.writeS(name);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
