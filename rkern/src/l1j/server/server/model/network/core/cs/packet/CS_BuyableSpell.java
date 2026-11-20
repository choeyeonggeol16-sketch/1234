package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_BuyableSpell {

    private CS_BuyableSpell() {
    }

    public static void handle(
            CUserSocket client,
            int id) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        l1j.server.server.model.network.core.sc.packet.SC_SkillBuy.send(pc, pc);
    }
}