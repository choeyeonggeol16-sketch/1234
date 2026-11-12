package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_CharPacks {
    private SC_CharPacks() {}
    public static boolean send(CIOClient client, String name, String clanName, int type, int sex,
            int lawful, int hp, int mp, int ac, int level, int str, int dex,
            int con, int wis, int cha, int intel, int accessLevel, int birth) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int nameSize = name == null ? 0 : name.length() << 1;
        final int clanSize = clanName == null ? 0 : clanName.length() << 1;
        final CIOFrame frame = connection.beginDirectFrame(33 + nameSize + clanSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHARACTER_INFO);
            buffer.writeS(name == null ? "" : name);
            buffer.writeS(clanName == null ? "" : clanName);
            buffer.writeC(type);
            buffer.writeC(sex);
            buffer.writeH(lawful);
            buffer.writeH(hp);
            buffer.writeH(mp);
            buffer.writeC(ac);
            buffer.writeC(level);
            buffer.writeC(str);
            buffer.writeC(dex);
            buffer.writeC(con);
            buffer.writeC(wis);
            buffer.writeC(cha);
            buffer.writeC(intel);
            buffer.writeC(0);
            buffer.writeD(birth);
            buffer.writeC((level ^ str ^ dex ^ con ^ wis ^ cha ^ intel) & 0xff);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
