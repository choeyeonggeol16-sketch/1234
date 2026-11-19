package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;

import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_JoinPledge {

    private CS_JoinPledge() {
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
                L1World
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
                        L1World
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

        /*
         * 동일 계정 캐릭터의 혈맹 가입 제한.
         *
         * 기존 CharacterClanCheck() 반환 구조와
         * DB/게임 semantics는 그대로 유지한다.
         */
        final ArrayList<String> clanIdList =
                CharacterTable
                        .getInstance()
                        .CharacterClanCheck(
                                player.getName());

        if (clanIdList != null) {

            for (String clanIdStr :
                    clanIdList) {

                if (clanIdStr == null
                        || clanIdStr.isEmpty()) {

                    continue;
                }

                try {

                    final int otherClanId =
                            Integer.parseInt(
                                    clanIdStr);

                    if (otherClanId != 0
                            && targetClanId
                                    != otherClanId) {

                        SC_SystemMessage.send(player, "\\fY한 계정 내 캐릭들은 같은 혈맹에만 가입이 가능합니다.");

                        return;
                    }

                } catch (NumberFormatException e) {
                    /*
                     * 원본 semantics 그대로 무시.
                     */
                }
            }
        }

        /*
         * 자동 혈맹 가입.
         */
        if (target.isAutoClanjoin()) {

            L1ClanJoin
                    .getInstance()
                    .ClanJoin(
                            target,
                            player);

            return;
        }

        /*
         * 수동 가입 승인 요청.
         */
        target.setTempID(
                player.getId());

        SC_MessageYN.send(target, 97,
                        player.getName());
    }
}