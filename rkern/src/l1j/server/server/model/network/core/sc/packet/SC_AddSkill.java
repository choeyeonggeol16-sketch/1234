package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_AddSkill {
    private SC_AddSkill() {}

    public static boolean send(L1PcInstance pc, int level, int id) {
        final int headerType = level >= 8 && level <= 9 && id > 0 ? 100
                : level >= 4 && level <= 7 && id > 0 ? 50 : 22;
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(36);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_ADD_SPELL);
            out.writeC(headerType);
            for (int i = 0; i < 24; i++) out.writeC(i == level ? id : 0);
            out.writeD(0);
            out.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int[] levels) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int count = levels == null ? 0 : levels.length;
        if (count > Integer.MAX_VALUE - 7) return false;
        final int elf = pc.getElfAttr();
        final int elfWire = elf == 1 ? 4 : elf == 2 ? 1 : elf == 4 ? 2 : elf == 8 ? 3 : 0;
        final CIOFrame frame = connection.beginDirectFrame(7 + count);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_ADD_SPELL);
            out.writeC(0x20);
            for (int i = 0; i < count; i++) out.writeC(levels[i]);
            out.writeC(0);
            out.writeC(0);
            out.writeC(elfWire);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc,
            int p0,
            int p1,
            int p2,
            int p3,
            int p4,
            int p5,
            int p6,
            int p7,
            int p8,
            int p9,
            int p10,
            int p11,
            int p12,
            int p13,
            int p14,
            int p15,
            int p16,
            int p17,
            int p18,
            int p19,
            int p20,
            int p21,
            int p22,
            int p23,
            int p24,
            int p25,
            int p26,
            int p27,
            int p28,
            int p29,
            int p30) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int elf = p30 == 1 ? 4 : p30 == 2 ? 1 : p30 == 4 ? 2 : p30 == 8 ? 3 : 0;
        final CIOFrame frame = connection.beginDirectFrame(37);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_ADD_SPELL);
            out.writeC(0x20);
            out.writeC(p0);
            out.writeC(p1);
            out.writeC(p2);
            out.writeC(p3);
            out.writeC(p4);
            out.writeC(p5);
            out.writeC(p6);
            out.writeC(p7);
            out.writeC(p8);
            out.writeC(p9);
            out.writeC(p10);
            out.writeC(p11);
            out.writeC(p12);
            out.writeC(p13);
            out.writeC(p14);
            out.writeC(p15);
            out.writeC(p16);
            out.writeC(p17);
            out.writeC(p18);
            out.writeC(p19);
            out.writeC(p20);
            out.writeC(p21);
            out.writeC(p22);
            out.writeC(p23);
            out.writeC(p24);
            out.writeC(p25);
            out.writeC(p26);
            out.writeC(p27);
            out.writeC(p28);
            out.writeC(p29);
            out.writeH(0);
            out.writeC(elf);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
