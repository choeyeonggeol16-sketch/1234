package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Pledge;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_PledgeContent {

    private static final int ANNOUNCEMENT = 15;
    private static final int MEMBER_MEMO = 16;

    private CS_PledgeContent() {
    }

    public static void handle(
            CIOClient client,
            int data,
            String content) {

        if (client == null) {
            return;
        }

        try {

            final L1PcInstance pc =
                    client.getActiveChar();

            if (pc == null) {
                return;
            }

            final int clanId =
                    pc.getClanid();

            if (clanId == 0) {
                return;
            }

            switch (data) {

            /*
             * 혈맹 공지
             */
            case ANNOUNCEMENT: {

                final ClanTable clanTable =
                        ClanTable.getInstance();

                final L1Clan clan =
                        clanTable.getTemplate(
                                clanId);

                if (clan == null) {

                    SC_SystemMessage.send(pc, "존재하지 않는 혈맹입니다.");

                    return;
                }

                /*
                 * content == null도 원본에서는
                 * 그대로 setAnnouncement()로 들어간다.
                 * 이번 이관에서는 임의 validation 추가 안 함.
                 */
                clan.setAnnouncement(
                        content);

                clanTable.updateClan(
                        clan);

                SC_PacketBox.send(pc, 
                                SC_PacketBox.HTML_PLEDGE_REALEASE_ANNOUNCE,
                                content);

                return;
            }

            /*
             * 혈맹원 메모
             */
            case MEMBER_MEMO: {

                final L1Clan clan =
                        L1World
                                .getInstance()
                                .getClan(clanId);

                if (clan == null) {

                    SC_SystemMessage.send(pc, "존재하지 않는 혈맹입니다.");

                    return;
                }

                final String name =
                        pc.getName();

                clan.removeClanMember(
                        name);

                clan.addClanMember(
                        name,
                        pc.getClanRank(),
                        pc.getLevel(),
                        content,
                        pc.getId(),
                        pc.getType(),
                        pc.getOnlineStatus(),
                        pc);

                pc.setClanMemberNotes(
                        content);

                SC_Pledge.sendWriteNotes(pc, name, content);

                try {

                    pc.save();

                } catch (Exception e) {

                    e.printStackTrace();
                }

                return;
            }

            default:
                return;
            }

        } catch (Exception e) {

            /*
             * 원본 runImpl 전체 try/catch semantics 유지.
             */
            e.printStackTrace();
        }
    }
}