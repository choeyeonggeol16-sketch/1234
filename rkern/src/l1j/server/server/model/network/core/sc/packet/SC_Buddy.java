package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import java.nio.charset.Charset;
import l1j.server.server.model.CFriends;

public final class SC_Buddy {
    private SC_Buddy() {}
    private static final Charset NAME_CHARSET = Charset.forName("MS949");

    public static boolean send(CUserSocket client, CFriends buddy) {
        if (client == null || !client.isConnected()) return false;
        final CFriends.FriendSnapshot friends = buddy == null ? null : buddy.Snapshot();
        final int count = friends == null ? 0 : friends.ids().length;
        final byte[][] names = new byte[count][];
        int size = 9;
        for (int i = 0; i < count; i++) {
            names[i] = friends.names()[i].getBytes(NAME_CHARSET);
            size += 8 + names[i].length;
        }
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeD(0x01080151);
            for (int i = 0; i < count; ++i) {
                final byte[] name = names[i];
                final int len = name.length;
                buffer.writeC(0x12);
                buffer.writeC(len + 6);
                buffer.writeC(0x0a);
                buffer.writeC(len);
                buffer.writeByte(name);
                buffer.writeC(0x10);
                buffer.writeC(CFriends.FindOnlineFriend(friends.ids()[i]) != null ? 1 : 0);
                buffer.writeH(0x001a);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, CFriends buddy) {
        return pc != null && send(pc.getNetConnection(), buddy);
    }

}
