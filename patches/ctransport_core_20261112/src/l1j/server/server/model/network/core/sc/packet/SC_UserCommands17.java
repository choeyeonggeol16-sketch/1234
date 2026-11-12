package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.Config;

public final class SC_UserCommands17 {
    private SC_UserCommands17() {}

    public static boolean send(CIOClient client, int number) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(64 + (((Config.공지제목 == null ? 0 : Config.공지제목.length()) + (Config.공지1번줄 == null ? 0 : Config.공지1번줄.length()) + (Config.공지2번줄 == null ? 0 : Config.공지2번줄.length()) + (Config.공지3번줄 == null ? 0 : Config.공지3번줄.length()) + (Config.공지4번줄 == null ? 0 : Config.공지4번줄.length()) + (Config.공지5번줄 == null ? 0 : Config.공지5번줄.length()) + (Config.공지6번줄 == null ? 0 : Config.공지6번줄.length()) + (Config.공지7번줄 == null ? 0 : Config.공지7번줄.length()) + (Config.공지8번줄 == null ? 0 : Config.공지8번줄.length()) + (Config.공지9번줄 == null ? 0 : Config.공지9번줄.length()) + (Config.GAME_SERVER_NAME == null ? 0 : Config.GAME_SERVER_NAME.length())) << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(number);
            buffer.writeS("Gold");
            buffer.writeC(0);
            buffer.writeH(15);
            buffer.writeS(Config.공지제목);
            buffer.writeS(Config.공지1번줄);
            buffer.writeS(Config.공지2번줄);
            buffer.writeS(Config.공지3번줄);
            buffer.writeS(Config.공지4번줄);
            buffer.writeS(Config.공지5번줄);
            buffer.writeS(Config.공지6번줄);
            buffer.writeS(Config.공지7번줄);
            buffer.writeS(Config.공지8번줄);
            buffer.writeS(Config.공지9번줄);
            buffer.writeS("──────────────");
            buffer.writeS(Config.GAME_SERVER_NAME);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int number) {
        return pc != null && send(pc.getNetConnection(), number);
    }
}
