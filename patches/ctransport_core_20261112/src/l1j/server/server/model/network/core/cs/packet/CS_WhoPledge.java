package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Pledge;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_WhoPledge {

    private static final Logger LOG = Logger.getLogger(CS_WhoPledge.class.getName());

    private static final int MEMBERS_PER_PAGE = 90;

    private CS_WhoPledge() {}

    public static void handle(CIOClient client) {

        if (client == null) {
            return;
        }

        try {
            final L1PcInstance pc = client.getActiveChar();

            if (pc == null || pc.isGhost()) {
                return;
            }

            final int clanId = pc.getClanid();

            if (clanId <= 0) {
                SC_ServerMessage.send(pc, 1064);
                return;
            }

            final L1Clan clan = L1World.getInstance().getClan(clanId);

            if (clan == null) {
                return;
            }

            SC_Pledge.sendAnnounce(pc, clan.getClanId());

            final String[] members = clan.getAllMembersName();

            if (members == null || members.length == 0) {
                return;
            }

            final int count = members.length;

            final int page1Capacity = Math.min(count, MEMBERS_PER_PAGE);

            final int page2Capacity = count > MEMBERS_PER_PAGE 
                    ? Math.min(count - MEMBERS_PER_PAGE, MEMBERS_PER_PAGE) 
                    : 0;

            final int page3Capacity = count > MEMBERS_PER_PAGE * 2 
                    ? count - MEMBERS_PER_PAGE * 2 
                    : 0;

            final ArrayList<String> page1 = new ArrayList<>(page1Capacity);
            final ArrayList<String> page2 = new ArrayList<>(page2Capacity);
            final ArrayList<String> page3 = new ArrayList<>(page3Capacity);

            for (int i = 0; i < count; i++) {
                final String memberName = members[i];

                if (memberName == null) {
                    continue;
                }

                if (i < MEMBERS_PER_PAGE) {
                    page1.add(memberName);
                } else if (i < MEMBERS_PER_PAGE * 2) {
                    page2.add(memberName);
                } else {
                    page3.add(memberName);
                }
            }

            final int div;

            if (!page3.isEmpty()) {
                div = 3;
            } else if (!page2.isEmpty()) {
                div = 2;
            } else {
                div = 1;
            }

            if (!page1.isEmpty()) {
                SC_Pledge.sendMembers(pc, div, 0, page1);
            }

            if (!page2.isEmpty()) {
                SC_Pledge.sendMembers(pc, div, 1, page2);
            }

            if (!page3.isEmpty()) {
                SC_Pledge.sendMembers(pc, div, 2, page3);
            }

            SC_ActionUI.send(pc, clan.getClanName(), pc.getClanRank());

            SC_PacketBox.send(pc, SC_PacketBox.HTML_PLEDGE_ONLINE_MEMBERS, clan.getOnlineClanMember());

            SC_Pledge.sendBless(pc, clan, clan.getBless());

            l1j.server.server.model.network.core.sc.packet.SC_EinhasadClanBuff.send(pc, pc);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "CS_WhoPledge 패킷 처리 중 예외 발생", e);
        }
    }
}