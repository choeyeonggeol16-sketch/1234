package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_SkillBuy {
    private SC_SkillBuy() {}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc) {
        return receiver != null && send(receiver.getNetConnection(), pc);
    }

    public static boolean send(CUserSocket client, L1PcInstance pc) {
        if (client == null || !client.isConnected() || pc == null ) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        int skillMask = CSkillDB.getInstance().acquireDB().npcOfferMask(pc.getType());
        for (int candidates = skillMask; candidates != 0; candidates &= candidates - 1) {
            int bit = Integer.lowestOneBit(candidates);
            if (pc.HaveSkill(Integer.numberOfTrailingZeros(bit) + 1)) skillMask &= ~bit;
        }
        int skillCount = Integer.bitCount(skillMask);
        final CIOFrame frame = connection.beginDirectFrame(9 + (skillCount << 2));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUYABLE_SPELL_LIST);
            buffer.writeD(100);
            buffer.writeH(skillCount);
            for (int remaining = skillMask; remaining != 0; remaining &= remaining - 1)
                buffer.writeD(Integer.numberOfTrailingZeros(remaining));
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
