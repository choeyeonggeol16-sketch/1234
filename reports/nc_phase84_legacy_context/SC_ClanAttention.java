package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ClanAttention {
    private SC_ClanAttention() {}


    /** Exact S_PLEDGE_WATCH, C(on?32:31), H(269), MS949-S(name).
     * The full LE-framed ciphertext-independent packet is generated once.
     */
    public static byte[] sharedMentWire(boolean on,String name) {
        final byte[] value=(name==null?"":name).getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final long needed=7L+value.length;
        if(needed>0xffffL)return null;
        final int n=(int)needed;
        final byte[] wire=new byte[n];
        wire[0]=(byte)n;
        wire[1]=(byte)(n>>>8);
        wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
        wire[3]=(byte)(on?32:31);
        wire[4]=(byte)269;
        wire[5]=(byte)(269>>>8);
        System.arraycopy(value,0,wire,6,value.length);
        return wire;
    }

    /** Identical S_PLEDGE_WATCH H(2), D(count), S(name)* frame. The
     * source's original synchronized monitor protects both size and byte
     * snapshot; null entries are skipped exactly like sendList().
     */
    public static byte[] sharedListWire(int count,List<String> source) {
        final java.nio.charset.Charset charset=
                java.nio.charset.Charset.forName("MS949");
        if(source==null){
            final byte[] wire=new byte[9];
            wire[0]=9;
            wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
            wire[3]=2;
            wire[5]=(byte)count;
            wire[6]=(byte)(count>>>8);
            wire[7]=(byte)(count>>>16);
            wire[8]=(byte)(count>>>24);
            return wire;
        }
        synchronized(source){
            long required=9L;
            for(String name:source) {
                if(name==null)continue;
                required+=name.getBytes(charset).length+1L;
                if(required>0xffffL)return null;
            }
            final int n=(int)required;
            final byte[] wire=new byte[n];
            wire[0]=(byte)n;
            wire[1]=(byte)(n>>>8);
            wire[2]=(byte)Opcodes.S_PLEDGE_WATCH;
            wire[3]=2;
            wire[5]=(byte)count;
            wire[6]=(byte)(count>>>8);
            wire[7]=(byte)(count>>>16);
            wire[8]=(byte)(count>>>24);
            int offset=9;
            for(String name:source){
                if(name==null)continue;
                final byte[] bytes=name.getBytes(charset);
                System.arraycopy(bytes,0,wire,offset,bytes.length);
                offset+=bytes.length+1;
            }
            if(offset!=n)throw new IllegalStateException("gaze list length");
            return wire;
        }
    }

    public static boolean sendMent(L1PcInstance pc, boolean on, String name) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return false;
        final CUserSocket connection = pc.getNetConnection().getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : name.length() * 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
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
            final WriteBuffer buffer = frame.buffer();
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
