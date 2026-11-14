package l1j.server.server.model.network.core.cs.packet;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanMatching;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.utils.ServerMessage;

public final class CS_LeavePledge {

    private static final Logger LOG =
            Logger.getLogger(
                    CS_LeavePledge.class.getName());

    private CS_LeavePledge() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null
                || player.isGhost()) {

            return;
        }

        final int clanId =
                player.getClanid();

        if (clanId == 0) {
            return;
        }

        final L1Clan clan =
                L1World
                        .getInstance()
                        .getClan(clanId);

        if (clan == null) {
            return;
        }

        try {

            /*
             * 혈맹 군주:
             * 혈맹 자체 해산.
             */
            if (player.isCrown()
                    && player.getId()
                            == clan.getLeaderId()) {

                leaveClanBoss(
                        clan,
                        player);

                return;
            }

            /*
             * 일반 혈맹원:
             * 본인만 탈퇴.
             */
            leaveClanMember(
                    clan,
                    player);

        } catch (Exception e) {

            LOG.log(
                    Level.SEVERE,
                    "CS_LeavePledge 실행 중 예외 발생",
                    e);
        }
    }

    private static void leaveClanBoss(
            L1Clan clan,
            L1PcInstance player)
            throws Exception {

        final String playerName =
                player.getName();

        final String clanName =
                player.getClanname();

        /*
         * 혈맹 무인 로봇 존재 시 해산 불가.
         */
        if (player.hasSkillEffect(
                L1SkillId.무인혈맹)) {

            SC_PacketBox.send(player, SC_PacketBox.GREEN_MESSAGE, "혈맹무인로봇을 제거 후 혈맹해산이 가능합니다.");

            player.sendPackets(
                    "\\f3혈맹무인로봇을 제거 후 혈맹해산이 가능합니다.");

            return;
        }

        /*
         * 성/아지트 보유 상태.
         */
        if (clan.getCastleId() > 0
                || clan.getHouseId() > 0) {

            SC_ServerMessage.send(player, ServerMessage.HAVING_NEST_OF_CLAN);

            return;
        }

        /*
         * 전쟁 중.
         */
        if (clan.getCurrentWar() != null) {

            SC_ServerMessage.send(player, ServerMessage.CANNOT_BREAK_CLAN);

            return;
        }

        /*
         * 동맹 존재.
         */
        if (clan.getAlliance() > 0) {

            SC_ServerMessage.send(player, ServerMessage.CANNOT_BREAK_CLAN_HAVING_FRIENDS);

            return;
        }

        /*
         * 오프라인 혈맹원 DB 데이터 일괄 초기화.
         */
        ClanTable.getInstance().deleteClanWithMembers(clan);

        /*
         * 접속 중 혈맹원들의 실시간 상태 초기화.
         *
         * 기존 getClanMemberList() 순회 semantics 그대로.
         */
        if (clan.getClanMemberList() != null) {

            for (L1Clan.ClanMember member :
                    clan.getClanMemberList()) {

                if (member == null) {
                    continue;
                }

                final L1PcInstance pc =
                        L1World
                                .getInstance()
                                .getPlayer(
                                        member.name);

                if (pc == null) {
                    continue;
                }

                try {
                    if (!pc.getName().equalsIgnoreCase(playerName)) {
                        SC_ServerMessage.send(pc, 269, playerName, clanName);
                    }
                    pc.ClearPlayerClanData(clan);
                } catch (Exception memberFailure) {
                    // SQL COMMIT already succeeded. Do not leave every later
                    // online member attached because one notification/save failed.
                    LOG.log(Level.SEVERE, "Clan disband online cleanup failed: " + pc.getName(), memberFailure);
                }
            }
        }

        // Matchmaking is only removed after the durable commit and the
        // online-character reconciliation. It must not abort that recovery.
        try {
            final L1ClanMatching clanMatching = L1ClanMatching.getInstance();
            if (clanMatching != null) clanMatching.deleteClanMatching(player);
        } catch (RuntimeException matchingFailure) {
            LOG.log(Level.WARNING, "Clan disband matchmaking cleanup failed: " + clanName, matchingFailure);
        }

        /*
         * 혈맹 엠블럼 파일 삭제.
         */
        final String emblemFile =
                String.valueOf(
                        clan.getClanId());

        final File file =
                new File(
                        "emblem/"
                                + emblemFile);

        if (file.exists()) {
            file.delete();
        }

    }

    /** No packet, buff or membership change is published before SQL COMMIT. */
    public static void leaveClanMember(L1Clan clan, L1PcInstance player) {
        if (clan == null || player == null || player.getClanid() != clan.getClanId()
                || player.getId() == clan.getLeaderId()) return;
        final int oldClanId = clan.getClanId();
        final String playerName = player.getName();
        final String clanName = clan.getClanName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(player,
                        l1j.server.server.storage.CacheD.accountOrderKey("$clan-membership"))) {
            tx.requireTables("characters");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET ClanID=0,Clanname='',ClanRank=0,Title='',notes='' "
                        + "WHERE objid=? AND ClanID=?")) {
                    ps.setInt(1, player.getId());
                    ps.setInt(2, oldClanId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Clan member leave before-image changed");
                }
            });
            tx.onFailure(() ->
                    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                            player, "혈맹 탈퇴를 완료하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (player.getClanid() != oldClanId || player.getId() == clan.getLeaderId()
                        || L1World.getInstance().getClan(oldClanId) != clan)
                    throw new IllegalStateException("Clan leave memory preimage changed after COMMIT");
                // Make the membership state authoritative before any observable effect.
                try {
                    player.ClearPlayerClanData(clan);
                } catch (Exception failure) {
                    throw new IllegalStateException("Committed clan leave memory cleanup failed", failure);
                }
                clan.removeClanMember(playerName);
                if (player.isClanBuff()) {
                    player.killSkillEffectTimer(L1SkillId.CLANBUFF_YES);
                    SC_PacketBox.send(player, SC_PacketBox.UNLIMITED_ICON1, 450, false);
                    player.setClanBuff(false);
                }
                for (L1PcInstance member : clan.getOnlineClanMember()) {
                    if (member != null) SC_ServerMessage.send(member, 178, playerName, clanName);
                }
            });
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "Clan leave receipt not admitted: " + playerName, error);
            l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                    player, "혈맹 탈퇴를 시작하지 못했습니다.");
        }
    }

}