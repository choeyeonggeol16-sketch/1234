package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_SPMR {
    private SC_SPMR() {}

    public static boolean send(L1PcInstance pc) {
        if (pc == null || pc.getAbility() == null || pc.getResistance() == null) return false;
        int sp = pc.getAbility().getSp() - pc.getAbility().getTrueSp();
        if (pc.hasSkillEffect(l1j.server.server.model.character.skill.L1SkillId.STATUS_WISDOM_POTION)) sp -= 2;
        if (sp < -128) sp = -128;
        if (sp > 300) sp = 300;
        final int mr = pc.getResistance().getMr() - pc.getResistance().getBaseMr();
        return sendRaw(pc.getNetConnection(), sp, mr);
    }
    private static boolean sendRaw(CIOClient client, int sp, int mr) {

        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(7);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGIC_STATUS);
            buffer.writeC(sp);
            buffer.writeH(mr);
            buffer.writeC(0);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }

    }
}
