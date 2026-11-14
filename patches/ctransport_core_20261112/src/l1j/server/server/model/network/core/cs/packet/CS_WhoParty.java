package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Party;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_WhoParty {

    private CS_WhoParty() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final L1Party party =
                pc.getParty();

        if (pc.isInParty()) {

            SC_Party.sendHypertext(pc, "party", pc.getId(),
                    party.getLeader().getName(), party.getMembersNameList());

        } else {

            SC_ServerMessage.send(pc, 425);
        }
    }
}