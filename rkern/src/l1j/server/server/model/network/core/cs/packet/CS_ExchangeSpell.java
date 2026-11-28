package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.acquire.CSkillAcquisition;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;

public final class CS_ExchangeSpell {
    private CS_ExchangeSpell() {}

    public static void handle(CUserSocket client, int selection, boolean invalidSid) {
        if (client == null) return;
        L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.isGhost()) return;
        if (invalidSid) {
            SC_Disconnect.send(pc);
            return;
        }
        CSkillAcquisition.learnFromNpc(pc, selection, true);
    }
}
