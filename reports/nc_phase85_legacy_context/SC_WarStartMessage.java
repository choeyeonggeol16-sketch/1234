package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

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
    /** NC-like pooled broadcast with immutable town names. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeWriter(String[] clanNames) {
        if(clanNames==null)throw new IllegalArgumentException("clan names");
        final String[] snapshot=clanNames.clone();
        return b -> {
            b.put((byte)Opcodes.S_EVENT).put((byte)0x4e).put((byte)0x07);
            for(String name:snapshot){
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name);
                b.put((byte)0);
            }
        };
    }

    public static byte[] sharedWire(String[] clanNames) {
        if(clanNames==null)throw new IllegalArgumentException("clan names");
        final java.nio.charset.Charset ms949=java.nio.charset.Charset.forName("MS949");
        int size=5;
        for(String name:clanNames){
            final int len=name==null?0:name.getBytes(ms949).length;
            size+=len+1;
            if(size>65535)throw new IllegalStateException("castle roster overflow");
        }
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=0x4e;wire[4]=0x07;
        int at=5;
        for(String name:clanNames){
            if(name!=null){
                final byte[] v=name.getBytes(ms949);
                System.arraycopy(v,0,wire,at,v.length);
                at+=v.length;
            }
            at++;
        }
        if(at!=wire.length)throw new IllegalStateException("roster wire length");
        return wire;
    }

    public static boolean send(L1PcInstance pc, String[] clanNames) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket conn = pc.getNetConnection().getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        int length = 5;
        for (String name : clanNames) length += 1 + (name == null ? 0 : name.length() * 2);
        final CIOFrame frame = conn.beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
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
