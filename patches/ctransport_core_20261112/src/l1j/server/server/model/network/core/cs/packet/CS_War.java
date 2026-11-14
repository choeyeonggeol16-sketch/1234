package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_War {

    private static final String RED_KNIGHT = "붉은 기사단";

    private CS_War() {}

    public static void handle(CIOClient client, int type, String targetClanName) {

        if (client == null) {
            return;
        }

        final L1PcInstance player = client.getActiveChar();

        if (player == null || player.isGhost()) {
            return;
        }

        try {
            if (type == 0) {
                handleProclaim(player, targetClanName);
                return;
            }

            handleWarRequest(player, type, targetClanName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // TYPE 0 : 선포
    // =========================================================

    private static void handleProclaim(L1PcInstance player, String targetClanName) {

        final MJCastleWarBusiness warBusiness = MJCastleWarBusiness.getInstance();

        /*
         * 붉은 기사단은 현재 위치의 공성전에 선포.
         */
        if (targetClanName != null && RED_KNIGHT.equalsIgnoreCase(targetClanName)) {
            final int castleId = L1CastleLocation.getCastleIdByArea(player);

            if (castleId == 0) {
                player.sendPackets("공성전이 진행 중이지 않습니다.");
                return;
            }

            final MJCastleWar war = warBusiness.get(castleId);

            if (war == null) {
                player.sendPackets("공성전이 진행 중이지 않습니다.");
                return;
            }

            warBusiness.proclaim(player, castleId);
            return;
        }

        if (targetClanName == null || targetClanName.isEmpty()) {
            return;
        }

        final L1World world = L1World.getInstance();
        final MJCastleWar war = warBusiness.findWar(targetClanName);

        if (war != null) {
            warBusiness.proclaim(player, war.getCastleId());
            return;
        }

        final L1Clan targetClan = world.findClan(targetClanName);

        if (targetClan != null) {
            warBusiness.proclaim(player, targetClan.getCastleId());
            return;
        }

        /*
         * String.format 제거.
         * Formatter 계열 객체 생성(Allocation) 낭비를 막기 위해 불필요하게 사용하지 않습니다.
         */
        SC_SystemMessage.send(player, "대상 혈맹을 찾을 수 없습니다. [" + targetClanName + "]");
    }

    // =========================================================
    // TYPE != 0
    // =========================================================

    private static void handleWarRequest(L1PcInstance player, int type, String targetClanName) {

        final L1World world = L1World.getInstance();
        final int redKnightClanId = player.getRedKnightClanId();
        
        String playerClanName = player.getClanname();
        int playerClanId = player.getClanid();

        /*
         * 붉은 기사단 대리 혈맹.
         */
        if (redKnightClanId != 0) {
            final L1Clan redClan = world.getClan(redKnightClanId);

            if (redClan == null) {
                SC_SystemMessage.send(player, "대상 혈맹이 발견되지 않았습니다.");
                player.setRedKnightClanId(0);
                return;
            }

            playerClanName = redClan.getClanName();
            playerClanId = redClan.getClanId();
        }

        /*
         * 일반 혈맹이면 군주만 가능.
         */
        if (!player.isCrown() && redKnightClanId == 0) {
            SC_ServerMessage.send(player, 478);
            return;
        }

        if (playerClanId == 0) {
            SC_ServerMessage.send(player, 272);
            return;
        }

        final L1Clan clan = world.getClan(playerClanId);

        if (clan == null) {
            SC_SystemMessage.send(player, "대상 혈맹이 발견되지 않았습니다.");
            return;
        }

        if (player.getId() != clan.getLeaderId() && redKnightClanId == 0) {
            SC_ServerMessage.send(player, 478);
            return;
        }

        if (targetClanName == null) {
            return;
        }

        if (playerClanName != null && playerClanName.equalsIgnoreCase(targetClanName)) {
            return;
        }

        final L1Clan enemyClan = world.findClan(targetClanName);

        if (enemyClan == null) {
            SC_SystemMessage.send(player, "대상 혈맹이 발견되지 않았습니다.");
            return;
        }

        final String enemyClanName = enemyClan.getClanName();

        /*
         * type 0은 이미 위에서 완전히 처리됐으므로
         * 원본 코드 아래쪽의 type == 0 분기는 전부 제거 가능합니다.
         */
        final MJWar war = clan.getCurrentWar();
        final boolean inWar = war != null;

        /*
         * 항복 / 종결은 현재 전쟁 중일 때만 가능합니다.
         */
        if (!inWar && (type == 2 || type == 3)) {
            return;
        }

        /*
         * 성 보유 혈맹은 항복/종결 요청을 무시합니다.
         */
        if (clan.getCastleId() != 0 && (type == 2 || type == 3)) {
            return;
        }

        /*
         * 상대가 일반 혈맹인 경우.
         */
        if (enemyClan.getCastleId() == 0 && player.getLevel() < 52) {
            SC_ServerMessage.send(player, 232);
            return;
        }

        /*
         * 상대가 성 보유 혈맹인 경우.
         */
        if (enemyClan.getCastleId() != 0 && player.getLevel() < Config.선포레벨) {
            SC_SystemMessage.send(player, "\\aA공성: 군주/공주 레벨\\aG[" + Config.선포레벨 + "]\\aA 부터 선포할 수 있습니다.");
            return;
        }

        if (redKnightClanId == 0) {
            if (clan.getOnlineClanMember().length <= Config.혈맹접속인원) {
                SC_SystemMessage.send(player, "접속한 혈맹원이 " + Config.혈맹접속인원 + "명 이상이면 선포가 가능합니다.");
                return;
            }
        }

        final MJWar enemyWar = enemyClan.getCurrentWar();

        /*
         * 일반 혈맹전.
         */
        if (enemyClan.getCastleId() == 0) {
            final boolean enemyInWar = enemyWar != null;

            /*
             * 항복/종결의 경우 반드시 같은 전쟁이어야 합니다.
             */
            if (enemyInWar && (type == 2 || type == 3)) {
                if (war == null || war.getId() != enemyWar.getId()) {
                    return;
                }
            }

            if (!enemyInWar && (type == 2 || type == 3)) {
                return;
            }

            final L1PcInstance enemyLeader = world.getPlayer(enemyClan.getLeaderName());

            if (enemyLeader == null) {
                SC_ServerMessage.send(player, 218, enemyClanName);
                return;
            }

            /*
             * 원본 코드는 type 1(선포) 같은 값이어도 여기까지 도달하면
             * tempID를 설정했으므로 그 의미론(semantics)을 그대로 유지합니다.
             */
            enemyLeader.setTempID(player.getId());

            switch (type) {
                case 2:
                    /*
                     * 항복 신청.
                     */
                    SC_MessageYN.send(enemyLeader, 221, playerClanName);
                    return;

                case 3:
                    /*
                     * 종결 신청.
                     */
                    SC_MessageYN.send(enemyLeader, 222, playerClanName);
                    return;

                default:
                    return;
            }
        }
    }
}