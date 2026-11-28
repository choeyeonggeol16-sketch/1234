package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.acquire.CSkillAcquisition;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_BuySpell {
    private CS_BuySpell() {}

    public static void handle(CUserSocket client, int level1, int level2, int level3, int invalidSid) {
        if (client == null) return;
        L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.isGhost()) return;
        if (invalidSid != 0) {
            client.kick();
            client.close();
            return;
        }
        CSkillAcquisition.learnFromNpc(pc, level1 | (level2 << 8) | (level3 << 16), false);
    }
}
