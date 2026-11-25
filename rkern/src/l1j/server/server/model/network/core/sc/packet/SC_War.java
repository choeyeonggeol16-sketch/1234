package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_War {
    private SC_War() {}
    public static boolean send(CUserSocket client, int type, String clan1, String clan2) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client;
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(6 + ((clan1 == null ? 0 : clan1.length()) + (clan2 == null ? 0 : clan2.length())) * 2);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
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
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenAll(b -> writeNativeWar(b,type,clan1,clan2));
    }
    public static void broadcastClan(l1j.server.server.model.L1Clan clan, int type, String clan1, String clan2) {
        if (clan == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenClanBestEffort(clan,b -> writeNativeWar(b,type,clan1,clan2));
    }


    private static void writeNativeWar(java.nio.ByteBuffer b,int type,
            String clan1,String clan2) {
        b.put((byte)Opcodes.S_WAR).put((byte)type);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,clan1);
        b.put((byte)0);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,clan2);
        b.put((byte)0);
    }


}
