package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Party;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_ChatParty {

    private CS_ChatParty() {}

    public static void handle(CUserSocket client, int type, String name) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost()) {
            return;
        }

        switch (type) {
            case 0:
                handleKick(pc, name);
                return;

            case 1:
                if (pc.isInChatParty()) {
                    final L1ChatParty party = pc.getChatParty();

                    if (party != null) {
                        party.leaveMember(pc);
                    }
                }
                return;

            case 2:
            case 4:
            case 5:
                sendPartyInfo(pc);
                return;

            default:
                return;
        }
    }

    private static void handleKick(L1PcInstance pc, String name) {

        if (name == null || name.isEmpty()) {
            return;
        }

        if (!pc.isInChatParty()) {
            SC_ServerMessage.send(pc, 425);
            return;
        }

        final L1ChatParty chatParty = pc.getChatParty();

        if (chatParty == null) {
            return;
        }

        if (!chatParty.isLeader(pc)) {
            SC_ServerMessage.send(pc, 427);
            return;
        }

        final L1PcInstance target = L1World.getInstance().getPlayer(name);

        if (target == null) {
            SC_ServerMessage.send(pc, 109, name);
            return;
        }

        if (pc.getId() == target.getId()) {
            return;
        }

        if (chatParty.isMember(target)) {
            chatParty.kickMember(target);
        } else {
            SC_ServerMessage.send(pc, 426, name);
        }
    }

    private static void sendPartyInfo(L1PcInstance pc) {

        final L1ChatParty chatParty = pc.getChatParty();
        final L1PcInstance leader = chatParty == null ? null : chatParty.getLeader();

        if (leader != null) {
            SC_Party.sendHypertext(pc, "party", pc.getId(),
                    leader.getName(), chatParty.getMembersNameList());
        } else {
            SC_Party.sendHypertext(pc, "party", pc.getId());
        }
    }
}
