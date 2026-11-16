package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_War {
    private SC_War() {}
    public static boolean send(CIOClient client, int type, String clan1, String clan2) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(6 + ((clan1 == null ? 0 : clan1.length()) + (clan2 == null ? 0 : clan2.length())) * 2);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_WAR);
            buffer.writeC(type);
            buffer.writeS(clan1 == null ? "" : clan1);
            buffer.writeS(clan2 == null ? "" : clan2);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int type, String clan1, String clan2) {
        return pc != null && send(pc.getNetConnection(), type, clan1, clan2);
    }
    public static void broadcastAll(int type, String clan1, String clan2) {
        final byte[] wire=sharedWire(type, clan1, clan2);
        if(wire==null)return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(wire);
    }
    public static void broadcastClan(l1j.server.server.model.L1Clan clan, int type, String clan1, String clan2) {
        if (clan == null) return;
        final byte[] wire=sharedWire(type, clan1, clan2);
        if(wire==null)return;
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            clan.createOnlineMembers().forEach(member -> {
                final L1PcInstance player=member.player;
                if(player==null)return;
                final CIOClient client=player.getNetConnection();
                if(client!=null && client.isConnected()) shared.send(client.getConnection(),player.getId());
            });
        }
    }


    private static byte[] sharedWire(int type, String clan1, String clan2) {
        final java.nio.charset.Charset ms949=java.nio.charset.Charset.forName("MS949");
        final byte[] a=(clan1==null ? "" : clan1).getBytes(ms949);
        final byte[] b=(clan2==null ? "" : clan2).getBytes(ms949);
        final long size=6L+a.length+b.length;
        if(size>0xFFFF)return null;
        final java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate((int)size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.putShort((short)size).put((byte)Opcodes.S_WAR).put((byte)type)
           .put(a).put((byte)0).put(b).put((byte)0);
        return out.array();
    }

}
