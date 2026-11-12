package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_NewCharPacket {
    private SC_NewCharPacket() {}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc) {
        return receiver != null && send(receiver.getNetConnection(), pc);
    }

    public static boolean send(CIOClient client, L1PcInstance pc) {
        if (client == null || !client.isConnected() || pc == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String name = pc.getName() == null ? "" : pc.getName();
        final int type = pc.getType(), sex = pc.get_sex(), lawful = pc.getLawful();
        final int maxHp = pc.getMaxHp(), maxMp = pc.getMaxMp();
        final int ac = pc.getAC() == null ? 0 : pc.getAC().getAc();
        final int level = pc.getLevel();
        final int str = pc.getAbility() == null ? 0 : pc.getAbility().getStr();
        final int dex = pc.getAbility() == null ? 0 : pc.getAbility().getDex();
        final int con = pc.getAbility() == null ? 0 : pc.getAbility().getCon();
        final int wis = pc.getAbility() == null ? 0 : pc.getAbility().getWis();
        final int cha = pc.getAbility() == null ? 0 : pc.getAbility().getCha();
        final int intel = pc.getAbility() == null ? 0 : pc.getAbility().getInt();
        final int birthDay = pc.getBirthDay();
        final int code = level ^ str ^ dex ^ con ^ wis ^ cha ^ intel;
        final CIOFrame frame = connection.beginDirectFrame(64 + (name.length() << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_NEW_CHAR_INFO);
                    buffer.writeS(name);
                    buffer.writeS("");
                    buffer.writeC(type);
                    buffer.writeC(sex);
                    buffer.writeH(lawful);
                    buffer.writeH(maxHp);
                    buffer.writeH(maxMp);
                    buffer.writeC(ac);
                    buffer.writeC(level);
                    buffer.writeC(str);
                    buffer.writeC(dex);
                    buffer.writeC(con);
                    buffer.writeC(wis);
                    buffer.writeC(cha);
                    buffer.writeC(intel);
                    buffer.writeC(0);
                    buffer.writeD(birthDay);
                    buffer.writeC(code & 0xFF);
                    buffer.writeD(0000);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
