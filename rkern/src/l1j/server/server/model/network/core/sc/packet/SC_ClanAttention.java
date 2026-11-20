package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ClanAttention {
    private SC_ClanAttention() {}


    /** Exact S_PLEDGE_WATCH, C(on?32:31), H(269), MS949-S(name).
     * The full LE-framed ciphertext-independent packet is generated once.
     */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeMentWriter(boolean on,String name) {
        final String text=name==null?"":name;
        if(7L+((long)text.length()<<1)>0xFFFFL &&
                7L+text.getBytes(java.nio.charset.Charset.forName("MS949")).length>0xFFFFL)
            return null;
        return b -> {b.put((byte)Opcodes.S_PLEDGE_WATCH).put((byte)(on?32:31))
             .putShort((short)269); l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,text);b.put((byte)0);};
    }
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeListWriter(int count,List<String> source) {
        // Snapshot under the SAME lock as old sharedListWire. No mutable
        // member list survives into deferred packet assembly.
        final String[] names;
        if(source==null)names=new String[0];
        else synchronized(source){names=source.stream().filter(java.util.Objects::nonNull).toArray(String[]::new);}
        long reserve=9L;
        for(String name:names){
            reserve+=((long)name.length()<<1)+1;
            if(reserve>65535L)break;
        }
        if(reserve>65535L){
            reserve=9L;
            final java.nio.charset.Charset cs=java.nio.charset.Charset.forName("MS949");
            for(String name:names){reserve+=name.getBytes(cs).length+1L;
                if(reserve>65535L)return null;}
        }
        return b -> {b.put((byte)Opcodes.S_PLEDGE_WATCH).putShort((short)2).putInt(count);
            for(String name:names){ l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name);b.put((byte)0); } };
    }


    
    public static boolean sendMent(L1PcInstance pc, boolean on, String name) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : name.length() * 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeC(on ? 32 : 31);
            buffer.writeH(269);
            buffer.writeS(name == null ? "" : name);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendList(L1PcInstance pc, int count, List<String> source) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final ArrayList<String> snapshot = new ArrayList<>();
        int size = 2 + 1 + 2 + 4;
        if (source != null) {
            synchronized (source) {
                for (String name : source) {
                    if (name == null) continue;
                    snapshot.add(name);
                    size += 1 + name.length() * 2;
                }
            }
        }
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_PLEDGE_WATCH);
            buffer.writeH(2);
            buffer.writeD(count);
            for (int i = 0, n = snapshot.size(); i < n; i++) buffer.writeS(snapshot.get(i));
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
