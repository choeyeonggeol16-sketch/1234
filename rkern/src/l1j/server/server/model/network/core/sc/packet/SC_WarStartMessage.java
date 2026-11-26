package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_WarStartMessage {
    private static final String[] CASTLE_NAMES = {
        "켄트성", "오크 요새", "윈다우드성", "기란성", "하이네성", "지저성", "아덴성"
    };
    private SC_WarStartMessage() {}

    public static String[] snapshot() {
        final String[] names = CASTLE_NAMES.clone();
        for (L1Clan clan : L1World.getInstance().getAllClans()) {
            final int index = clan.getCastleId() - 1;
            if (index >= 0 && index < names.length) names[index] = clan.getClanName();
        }
        return names;
    }


    public static NativeWireWriter nativeWriter(String[] clanNames) {
        if (clanNames == null || clanNames.length != CASTLE_NAMES.length) return null;
        final String[] names = clanNames.clone();
        if (rosterCapacity(names) == 0) return null;
        return out -> {
            out.put((byte) Opcodes.S_EVENT).put((byte) 0x4e).put((byte) names.length);
            for (String name : names) {
                if (name != null) CSharedBroadcast.writeMS949(out, name);
                out.put((byte) 0);
            }
        };
    }

    private static int rosterCapacity(String[] names) {
        long reserve = 5 + names.length;
        for (String name : names) {
            if (name != null) reserve += 2L * name.length();
        }
        if (reserve <= CIOBufferPool.MAX_WIRE_BYTES) return (int) reserve;
        reserve = 5 + names.length;
        for (String name : names) {
            final byte[] encoded = ProtoBroadcastFields.encode(name);
            if (encoded == null || (reserve += encoded.length) > CIOBufferPool.MAX_WIRE_BYTES) return 0;
        }
        return (int) reserve;
    }

    public static boolean send(L1PcInstance pc, String[] clanNames) {
        if (pc == null || clanNames == null || clanNames.length != CASTLE_NAMES.length) return false;
        final CUserSocket conn = pc.getNetConnection();
        if (conn == null || !conn.isConnected() || !conn.isClientConnected()) return false;
        final String[] names = clanNames.clone();
        final int length = rosterCapacity(names);
        if (length == 0) return false;
        final CIOFrame frame = conn.beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x4E);
            buffer.writeC(names.length);
            for (String name : names) buffer.writeS(name);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
