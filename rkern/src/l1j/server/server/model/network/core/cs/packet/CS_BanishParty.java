package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Party;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_BanishParty {

    private CS_BanishParty() {
    }

    public static void handle(
            CIOClient client,
            String memberName) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null) {
            return;
        }

        final L1Party party =
                player.getParty();

        if (party == null) {
            return;
        }

        if (!party.isLeader(player)) {

            SC_ServerMessage.send(player, 427);

            return;
        }

        if (memberName == null
                || memberName.isEmpty()) {

            return;
        }

        for (L1PcInstance member :
                party.getMembers()) {

            if (member == null) {
                continue;
            }

            if (member.getName()
                    .equalsIgnoreCase(
                            memberName)) {

                party.kickMember(member);
                return;
            }
        }

        /*
         * 발견되지 않음.
         */
        SC_ServerMessage.send(player, 426, memberName);
    }
}