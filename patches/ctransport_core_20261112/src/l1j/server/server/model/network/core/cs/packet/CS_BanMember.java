package l1j.server.server.model.network.core.cs.packet;

import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_BanMember {

    private static final Logger LOG =
            Logger.getLogger(
                    CS_BanMember.class.getName());

    private CS_BanMember() {
    }

    public static void handle(
            CIOClient client,
            String targetName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (targetName == null
                || targetName.isEmpty()) {

            return;
        }

        /*
         * 군주 본인 추방 방지.
         */
        if (targetName.equalsIgnoreCase(
                pc.getName())) {

            return;
        }

        /*
         * 원본은 clanId lookup이 아니라
         * clanName 기반 findClan().
         * semantics 그대로 유지.
         */
        final L1Clan clan =
                L1World
                        .getInstance()
                        .findClan(
                                pc.getClanname());

        if (clan == null) {
            return;
        }

        /*
         * 군주 권한 검증.
         */
        if (!pc.isCrown()
                || pc.getId()
                        != clan.getLeaderId()) {

            SC_ServerMessage.send(pc, 518);

            return;
        }

        final int castleId =
                clan.getCastleId();

        /*
         * 공성 중 추방 불가.
         */
        if (castleId != 0
                && MJCastleWarBusiness
                        .getInstance()
                        .isNowWar(castleId)) {

            SC_ServerMessage.send(pc, 439);

            return;
        }

        /*
         * 먼저 온라인 캐릭터 검색.
         */
        final L1PcInstance target =
                L1World
                        .getInstance()
                        .getPlayer(
                                targetName);

        if (target != null) {

            handleOnline(
                    pc,
                    clan,
                    target,
                    targetName);

            return;
        }

        /*
         * 오프라인 캐릭터 복원.
         */
        handleOffline(
                client,
                pc,
                clan,
                targetName);
    }

    private static void handleOnline(
            L1PcInstance pc,
            L1Clan clan,
            L1PcInstance target,
            String targetName) {

        if (target.getClanid()
                != pc.getClanid()) {

            SC_ServerMessage.send(pc, 109, targetName);

            return;
        }

        try {

            target.ClearPlayerClanData(
                    clan);

        } catch (Exception error) {
            LOG.log(Level.WARNING, "Online clan ban save failed: " + targetName, error);
            SC_SystemMessage.send(pc, "혈맹원 추방 저장에 실패했습니다.");
            return;
        }

        clan.removeClanMember(
                target.getName());

        SC_PacketBox.send(pc, 
                        pc,
                        SC_PacketBox.PLEDGE_REFRESH_MINUS);

        SC_ServerMessage.send(target, 238, pc.getClanname());

        SC_ServerMessage.send(pc, 240, target.getName());
    }

    private static void handleOffline(
            CIOClient client,
            L1PcInstance pc,
            L1Clan clan,
            String targetName) {

        // Do not restore an offline character (JDBC) on the NIO.2 completion
        // worker. A target-owner DB lane also waits behind earlier saves.
        final l1j.server.server.templates.L1CharName known =
                CharacterTable.getInstance().getCharName(targetName);
        if (known == null || known.getId() <= 0) {
            SC_ServerMessage.send(pc, 109, targetName);
            return;
        }
        final int targetId = known.getId();
        final int sourceClanId = pc.getClanid();
        try {
            CacheD.getInstance().submitDbJobOrdered(targetId, () -> {
                final L1PcInstance restored;
                try {
                    restored = CharacterTable.getInstance().restoreCharacter(targetName);
                } catch (Exception error) {
                    LOG.log(Level.WARNING, "Offline clan member load failed: " + targetName, error);
                    CIOThreadCallback.getInstance().completeSession(client, () ->
                            SC_SystemMessage.send(pc, "혈맹원 정보를 불러오지 못했습니다."));
                    return;
                }
                if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                    // Session, privileges, active clan, and siege state may
                    // have changed while the target was being restored.
                    if (client.getActiveChar() != pc || pc.isGhost()
                            || pc.getClanid() != sourceClanId
                            || !pc.isCrown() || pc.getId() != clan.getLeaderId()
                            || L1World.getInstance().findClan(pc.getClanname()) != clan) {
                        return;
                    }
                    final int castle = clan.getCastleId();
                    if (castle != 0 && MJCastleWarBusiness.getInstance().isNowWar(castle)) {
                        SC_ServerMessage.send(pc, 439);
                        return;
                    }
                    // The target may have logged in while JDBC was loading.
                    // Never persist a detached copy over a live character.
                    final L1PcInstance live = L1World.getInstance().getPlayer(targetName);
                    final L1PcInstance target = live != null ? live : restored;
                    if (target == null || target.getId() != targetId
                            || target.getClanid() != sourceClanId) {
                        SC_ServerMessage.send(pc, 109, targetName);
                        return;
                    }
                    if (live != null) {
                        handleOnline(pc, clan, live, targetName);
                        return;
                    }
                    try {
                        target.ClearPlayerClanData(clan);
                        clan.removeClanMember(target.getName());
                        SC_ServerMessage.send(pc, 240, target.getName());
                    } catch (Exception error) {
                        LOG.log(Level.SEVERE, "Offline clan ban persistence failed: " + targetName, error);
                        SC_SystemMessage.send(pc, "혈맹원 추방 저장에 실패했습니다.");
                    }
                })) {
                    LOG.warning("Offline clan ban owner completion rejected: " + targetName);
                }
            });
        } catch (RejectedExecutionException rejected) {
            LOG.log(Level.WARNING, "Offline clan ban DB admission rejected: " + targetName, rejected);
            SC_SystemMessage.send(pc, "혈맹원 추방 처리가 지연되고 있습니다. 다시 시도해 주세요.");
        }
    }
}
