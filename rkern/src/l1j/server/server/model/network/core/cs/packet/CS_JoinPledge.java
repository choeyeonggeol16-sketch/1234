package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.CReplyChecker;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_JoinPledge {

    private CS_JoinPledge() {
    }

    public static void handle(
            CUserSocket client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final L1PcInstance target =
                FaceToFace.faceToFace(pc);

        if (target == null) {
            return;
        }

        joinClanProcess(
                pc,
                target);
    }

    private static void joinClanProcess(
            L1PcInstance player,
            L1PcInstance target) {

        if (player == null
                || target == null) {

            return;
        }

        /*
         * 상대방이 군주 또는 수호기사인지 확인.
         */
        if (!target.isCrown()
                && target.getClanRank()
                        != L1Clan.수호) {

            SC_SystemMessage.send(player, target.getName()
                                    + "는 왕자나 공주 수호기사가 아닙니다.");

            return;
        }

        final int targetClanId =
                target.getClanid();

        if (targetClanId == 0) {

            SC_ServerMessage.send(player, 90, target.getName());

            return;
        }

        final L1Clan clan =
                CWorld
                        .getInstance()
                        .getClan(targetClanId);

        if (clan == null) {
            return;
        }

        /*
         * 이미 혈맹 가입 상태인 경우.
         */
        final int playerClanId =
                player.getClanid();

        if (playerClanId != 0) {

            if (player.isCrown()) {

                final L1Clan playerClan =
                        CWorld
                                .getInstance()
                                .getClan(playerClanId);

                if (playerClan != null) {

                    if (player.getId()
                            != playerClan.getLeaderId()) {

                        SC_ServerMessage.send(player, 89);

                        return;
                    }

                    if (playerClan.getCastleId() != 0
                            || playerClan.getHouseId() != 0) {

                        SC_ServerMessage.send(player, 665);

                        return;
                    }
                }

            } else {

                SC_ServerMessage.send(player, 89);

                return;
            }
        }

        // Reserve the request before the ordered DB read; the owner callback
        // decides automatic/manual admission only after revalidating both PCs.
        if (!CReplyChecker.AttachReplyChecker(player, target, clan)) {
            SC_SystemMessage.send(player, "혈맹 가입 승인 대기 중이거나 요청할 수 없는 상태입니다.");
        }
    }
}