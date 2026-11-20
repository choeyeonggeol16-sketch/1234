package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CUserSession;

public final class CS_BuyableSpell {

    private CS_BuyableSpell() {
    }

    public static void handle(
            CUserSession client,
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