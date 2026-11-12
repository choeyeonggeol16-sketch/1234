package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_IvenBuffIcon {
    private SC_IvenBuffIcon() {}

    private static int varSize(int value) {
        long v = value & 0xffffffffL;
        int count = 1;
        while ((v >>>= 7) != 0L) count++;
        return count;
    }

    public static boolean send(L1PcInstance pc, int skillId, boolean on, int msgNum, int time) {
        return pc != null && send(pc.getNetConnection(), skillId, on, msgNum, time);
    }

    public static boolean send(CIOClient client, int skillId, boolean on, int msgNum, int time) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final boolean nBuff = skillId >= 4075 && skillId <= 4094;
        final int iconId = nBuff ? 6679 : skillId;
        final int priority = nBuff ? 0 : 3;
        final int durationType = nBuff ? 9 : 8;
        final int overlap = nBuff ? 1 : 0;
        final int tooltip = nBuff ? 4328 : 0;
        final int buffPriority = nBuff ? 1 : 0;
        final int size = 2 + 3 + 2 + 14
                + varSize(on ? 2 : 3) + varSize(skillId) + varSize(time)
                + varSize(durationType) + varSize(iconId) + varSize(0)
                + varSize(priority) + varSize(msgNum) + varSize(0)
                + varSize(0) + varSize(1) + varSize(overlap)
                + varSize(tooltip) + varSize(buffPriority);
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(110);
            buffer.writeC(0x08); buffer.writeBit(on ? 2 : 3);
            buffer.writeC(0x10); buffer.writeBit(skillId);
            buffer.writeC(0x18); buffer.writeBit(time);
            buffer.writeC(0x20); buffer.writeBit(durationType);
            buffer.writeC(0x28); buffer.writeBit(iconId);
            buffer.writeC(0x30); buffer.writeBit(0);
            buffer.writeC(0x38); buffer.writeBit(priority);
            buffer.writeC(0x40); buffer.writeBit(msgNum);
            buffer.writeC(0x48); buffer.writeBit(0);
            buffer.writeC(0x50); buffer.writeBit(0);
            buffer.writeC(0x58); buffer.writeBit(1);
            buffer.writeC(0x60); buffer.writeBit(overlap);
            buffer.writeC(0x68); buffer.writeBit(tooltip);
            buffer.writeC(0x70); buffer.writeBit(buffPriority);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
