package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.MJKDASystem.MJKDA;
import l1j.server.MJKDASystem.MJKDALoadManager;

public final class SC_ChartRank {
    private static final Logger LOG = Logger.getLogger(SC_ChartRank.class.getName());
    private static final int SC_BASECAMP_CHART_NOTI_PACKET = 0x007F;
    private static final Snapshot ZERO = new Snapshot(new byte[0][], 0, true);

    private SC_ChartRank() {}

    public static final class Snapshot {
        private final byte[][] serialized;
        private final int serverPoint;
        private final boolean zero;
        private final int wireSize;

        private Snapshot(byte[][] serialized, int serverPoint, boolean zero) {
            this.serialized = serialized;
            this.serverPoint = serverPoint;
            this.zero = zero;
            long size = 2 + 3 + 1 + bitSize(serverPoint) + 2;
            if (zero) size = 2 + 3 + 1 + 1 + 2;
            else for (byte[] bytes : serialized) size += 1L + bitSize(bytes.length) + bytes.length;
            if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("chart frame too large");
            this.wireSize = (int) size;
        }
    }

    private static int bitSize(long value) {
        if (value < 0L) value &= 0xFFFFFFFFL;
        int n = 1;
        while ((value & ~0x7FL) != 0) { n++; value >>>= 7; }
        return n;
    }

    public static Snapshot getZero() { return ZERO; }

    public static Snapshot get(List<MJKDA> list) {
        if (list == null || list.isEmpty()) return ZERO;
        final ArrayList<byte[]> bytes = new ArrayList<>(10);
        try {
            synchronized (list) {
                for (MJKDA kda : list) {
                    if (kda == null) continue;
                    if (kda.kill <= 0) break;
                    byte[] payload = kda.serialize();
                    if (payload != null && payload.length > 0) {
                        bytes.add(payload);
                        if (bytes.size() >= 10) break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SC_ChartRank KDA serialize", e);
        }
        if (bytes.isEmpty()) return ZERO;
        return new Snapshot(bytes.toArray(new byte[bytes.size()][]), MJKDALoadManager.KDA_TOTAL_PVP, false);
    }


    /** Freeze the exact existing direct SC_BASECAMP_CHART_NOTI_PACKET
     * payload once for a chart update rather than rewriting the same
     * Snapshot fields for every eligible viewer.
     */
    /** Directly write the original chart snapshot into one native slab.
     * The original snapshot is captured before any recipient is inspected. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeWriter(Snapshot snapshot) {
        if(snapshot==null||snapshot.wireSize>0xffff)return null;
        return new l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter(){
            @Override public void write(java.nio.ByteBuffer out) {
                out.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
                out.putShort((short)SC_BASECAMP_CHART_NOTI_PACKET);
                if(snapshot.zero) {
                    out.put((byte)0x10).put((byte)0).putShort((short)0);
                } else {
                    for(byte[] payload:snapshot.serialized) {
                        out.put((byte)0x0a);
                        putBit(out,payload.length);
                        out.put(payload);
                    }
                    out.put((byte)0x10);
                    putBit(out,snapshot.serverPoint);
                    out.putShort((short)0);
                }
                if(out.position()!=snapshot.wireSize)
                    throw new IllegalStateException("Native chart length");
            }
            @Override public int wireLength() {return snapshot.wireSize;}
        };
    }

    public static byte[] sharedWire(Snapshot snapshot) {
        if(snapshot==null || snapshot.wireSize>0xffff)return null;
        final java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate(snapshot.wireSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.position(2);
        out.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        out.putShort((short)SC_BASECAMP_CHART_NOTI_PACKET);
        if(snapshot.zero){
            out.put((byte)0x10);
            out.put((byte)0);
            out.putShort((short)0);
        } else {
            for(byte[] payload:snapshot.serialized){
                out.put((byte)0x0a);
                putBit(out,payload.length);
                out.put(payload);
            }
            out.put((byte)0x10);
            putBit(out,snapshot.serverPoint);
            out.putShort((short)0);
        }
        if(out.position()!=snapshot.wireSize)
            throw new IllegalStateException("chart packet size mismatch");
        out.putShort(0,(short)snapshot.wireSize);
        return out.array();
    }

    /** WriteBuffer.writeBit uses 32-bit unsigned semantics for negatives. */
    private static void putBit(java.nio.ByteBuffer out,long value){
        if(value<0L)value &= 0xffffffffL;
        while((value & ~0x7fL)!=0L){
            out.put((byte)((value & 0x7fL)|0x80L));
            value >>>= 7;
        }
        out.put((byte)value);
    }

    public static boolean send(L1PcInstance pc, Snapshot snapshot) {
        if (pc == null || snapshot == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(snapshot.wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(SC_BASECAMP_CHART_NOTI_PACKET);
            if (snapshot.zero) {
                out.writeC(0x10);
                out.writeC(0);
                out.writeH(0);
            } else {
                for (byte[] bytes : snapshot.serialized) {
                    out.writeC(0x0A);
                    out.writeBit(bytes.length);
                    out.writeByte(bytes);
                }
                out.writeC(0x10);
                out.writeBit(snapshot.serverPoint);
                out.writeH(0);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
