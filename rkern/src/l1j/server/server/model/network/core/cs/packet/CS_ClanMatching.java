package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1ClanMatching;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ClanMatching;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_ClanMatching {

    private CS_ClanMatching() {
    }

    public static void handle(
            CIOClient client,
            int type,
            int objid,
            String text,
            int htype) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1ClanMatching matching =
                L1ClanMatching.getInstance();

        switch (type) {

        /*
         * 혈맹 모집 등록 / 수정
         */
        case 0:
            if (!mayManageApplications(pc) || text==null || text.length()>300 || htype<0 || htype>2) break;
            if (!matching.isClanMatchingList(
                    pc.getClanname())) {

                matching.writeClanMatching(
                        pc.getClanname(),
                        text,
                        htype);

            } else {

                matching.updateClanMatching(
                        pc.getClanname(),
                        text,
                        htype);
            }

            break;

        /*
         * 모집 삭제
         */
        case 1:
            if (!mayManageApplications(pc)) break;
            if (matching.isClanMatchingList(
                    pc.getClanname())) {

                matching.deleteClanMatching(pc);
            }

            break;

        /*
         * 신청자 / 모집 목록
         */
        case 4:

            if (pc.getClanid() == 0) {

                if (!pc.isCrown()) {

                    matching
                            .loadClanMatchingApcList_User(
                                    pc);
                }

            } else {

                switch (pc.getClanRank()) {

                case 4:
                case 6:
                case 9:
                case 10:

                    matching
                            .loadClanMatchingApcList_Crown(
                                    pc);

                    break;

                default:
                    break;
                }
            }

            break;

        /*
         * 특정 혈맹에 가입 신청
         */
        case 5: {
            final L1Clan clan =
                    findClan(objid);

            if (pc.getClanid()==0 && clan != null
                    && matching.isClanMatchingList(clan.getClanName())
                    && !pc.getCMAList()
                            .contains(
                                    clan.getClanName())) {

                matching
                        .writeClanMatchingApcList_User(
                                pc,
                                clan);
            }

            break;
        }

        /*
         * 신청 처리
         */
        case 6:
            if(htype==3 ? pc.getClanid()!=0 : !mayManageApplications(pc)) break;
            handleApplicantControl(
                    pc,
                    matching,
                    objid,
                    htype);

            break;

        default:
            break;
        }

        /*
         * 중요:
         * 원본은 unknown type 포함
         * 모든 정상 runImpl 마지막에 이것을 보냄.
         */
        SC_ClanMatching.send(pc, type, objid, text, htype);
    }

    private static void handleApplicantControl(
            L1PcInstance pc,
            L1ClanMatching matching,
            int objid,
            int htype) {

        switch (htype) {

        /*
         * 가입 승인
         */
        case 1: {
            final L1Object target =
                    L1World
                            .getInstance()
                            .findObject(objid);

            if (target
                    instanceof L1PcInstance) {

                final L1PcInstance user =
                        (L1PcInstance) target;

                if(user==pc || user.getClanid()!=0 ||
                        !user.getCMAList().contains(pc.getClanname()) ||
                        !pc.getCMAList().contains(user.getName())) {

                    SC_SystemMessage.send(pc, "신청을 취소한 유저입니다.");

                    return;
                }

                // Preserve the application if SQL rollback/retry prevented
                // admission; only COMMIT consumes the pending application.
                L1ClanJoin.getInstance().ClanJoin(pc, user, committed -> {
                    if (committed) matching.deleteClanMatchingApcList(user);
                });

            } else if (target == null) {

                SC_SystemMessage.send(pc, "비접속중인 유저 입니다.");
            }

            return;
        }

        /*
         * 신청 거절 / 삭제
         */
        case 2: {
            final L1Object target =
                    L1World
                            .getInstance()
                            .findObject(objid);

            if (target instanceof L1PcInstance) {

                final L1PcInstance user =
                        (L1PcInstance) target;
                if(user.getClanid()!=0 ||
                   !user.getCMAList().contains(pc.getClanname()) ||
                   !pc.getCMAList().contains(user.getName()))return;

                user.removeCMAList(
                        pc.getClanname());

                pc.removeCMAList(
                        user.getName());

                matching
                        .deleteClanMatchingApcList(
                                user,
                                user.getId(),
                                pc.getClan());

            } else if (target == null) {

                matching
                        .deleteClanMatchingApcList(
                                null,
                                objid,
                                pc.getClan());
            }

            return;
        }

        /*
         * 유저가 자신의 혈맹 가입 신청 취소
         */
        case 3: {
            final L1Clan clan =
                    findClan(objid);

            if (clan != null
                    && pc.getCMAList()
                            .contains(
                                    clan.getClanName())) {

                matching
                        .deleteClanMatchingApcList(
                                pc,
                                clan);
            }

            return;
        }

        default:
            return;
        }
    }

    private static boolean mayManageApplications(L1PcInstance pc) {
        if(pc==null || pc.getClanid()<=0 || pc.getClan()==null ||
           pc.getClan().getClanId()!=pc.getClanid())return false;
        int rank=pc.getClanRank();
        return rank==4 || rank==6 || rank==9 || rank==10;
    }

    private static L1Clan findClan(
            int objid) {

        for (L1Clan clan :
                L1World
                        .getInstance()
                        .getAllClans()) {

            if (clan.getClanId()
                    == objid) {

                return clan;
            }
        }

        return null;
    }
}