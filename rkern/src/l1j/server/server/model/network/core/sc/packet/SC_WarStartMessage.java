package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_WarStartMessage {
    private static final String[] names = {
        "켄트성", "오크 요새", "윈다우드성", "기란성", "하이네성", "지저성", "아덴성"
    };
    private SC_WarStartMessage() {}

    public static String[] snapshot() {
        synchronized (names) {
            for (L1Clan clan : L1World.getInstance().getAllClans()) {
                final int index = clan.getCastleId() - 1;
                if (index > 0 && index < names.length) names[index] = clan.getClanName();
            }
            return names.clone();
        }
    }


    /** Immutable full S_EVENT 0x4e castle roster. Seven nul-terminated MS949
     * castle names in the snapshot's original order, including null as "".
     */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(String[] clanNames) {
        if(clanNames==null)throw new IllegalArgumentException("clan names");
        final String[] snapshot=clanNames.clone();
        long reserve=5;
        for(String name:snapshot){
            reserve+=1L+(name==null?0L:((long)name.length()<<1));
            if(reserve>65535L)break;
        }
        if(reserve>65535L){
            reserve=5L;
            final java.nio.charset.Charset cs=java.nio.charset.Charset.forName("MS949");
            for(String name:snapshot){
                reserve+=1L+(name==null?0L:name.getBytes(cs).length);
                if(reserve>65535L)throw new IllegalStateException("castle roster overflow");
            }
        }
        return b -> {b.put((byte)Opcodes.S_EVENT).put((byte)0x4e).put((byte)0x07);
            for(String name:snapshot){if(name!=null)l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name);b.put((byte)0);}};
    }


    public static boolean send(L1PcInstance pc, String[] clanNames) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket conn = pc.getNetConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        int length = 5;
        for (String name : clanNames) length += 1 + (name == null ? 0 : name.length() * 2);
        final CIOFrame frame = conn.beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x4E);
            buffer.writeC(0x07);
            for (String name : clanNames) buffer.writeS(name);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
