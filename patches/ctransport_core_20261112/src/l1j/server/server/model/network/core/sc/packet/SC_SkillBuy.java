package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_SkillBuy {
    private SC_SkillBuy() {}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc) {
        return receiver != null && send(receiver.getNetConnection(), pc);
    }

    public static boolean send(CIOClient client, L1PcInstance pc) {
        if (client == null || !client.isConnected() || pc == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int count;
        switch (pc.getType()) {
        case 0: case 4: count = 16; break;
        case 1: case 7: count = 8; break;
        case 2: case 3: count = 23; break;
        default: count = 0;
        }
        int skillMask = 0;
        int skillCount = 0;
        for (int k = 0; k < count; k++) {
            if (!pc.isSkillMastery(k + 1)) {
                skillMask |= (1 << k);
                skillCount++;
            }
        }
        final CIOFrame frame = connection.beginDirectFrame(9 + (skillCount << 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUYABLE_SPELL_LIST);
            buffer.writeD(100);
            buffer.writeH(skillCount);
            for (int k = 0; k < count; k++) {
                if ((skillMask & (1 << k)) != 0) buffer.writeD(k);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
