package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_ShowHelp {
    private static final String HELP_CONTENT = "\n\n" +
			"<게임 이용 관련>\n\n" +
			" \n" +
			".혈마크 (.emblem)\n" +
			".혈맹파티 (.party)\n" +
			".라이트 켬/끔 (.light on/off)\n" +
			".멘트 켬/끔 (.ment on/off)\n" +
			".파티멘트 켬/끔 (.bpment on/off)\n" +
			".. (좌표복구)\n" +
			"\n";

    private SC_ShowHelp() {}

    public static boolean send(CIOClient client, int number) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(32 + (HELP_CONTENT.length() << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BOARD_READ);
            buffer.writeD(number);
            buffer.writeS(" GM ");
            buffer.writeS(" 도움말 ");
            buffer.writeS("");
            buffer.writeS(HELP_CONTENT);
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
