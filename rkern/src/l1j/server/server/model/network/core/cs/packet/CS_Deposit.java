package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

public final class CS_Deposit {

    private CS_Deposit() {
    }

    public static void handle(
            CIOClient client,
            int targetId,
            int amount) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null
                || player.isGhost()) {

            return;
        }

        /*
         * 자기 자신을 대상으로 보낸 패킷인지 검증.
         */
        if (targetId
                != player.getId()) {

            return;
        }

        final L1Clan clan =
                L1World
                        .getInstance()
                        .getClan(
                                player.getClanid());

        if (clan == null) {
            return;
        }

        final int castleId =
                clan.getCastleId();

        if (castleId == 0) {
            return;
        }

        /*
         * 성 소유 혈맹 군주 권한 검증.
         */
        if (!player.isCrown()
                || clan.getLeaderId()
                        != player.getId()) {

            return;
        }

        final MJCastleWarBusiness business =
                MJCastleWarBusiness
                        .getInstance();

        /*
         * 공성 중 입금 불가.
         */
        if (business.isNowWar(castleId)) {

            SC_ChatPacket.sendDirect(player, player, "공성 중에는 불가능합니다.");

            return;
        }

        if (amount <= 0) {
            return;
        }

        final MJCastleWar war =
                business.get(castleId);

        if (war == null) {
            return;
        }

        final long currentMoney =
                war.getPublicMoney();

        final long newMoney =
                currentMoney + (long) amount;

        /*
         * 기존 상한 및 overflow 방어 그대로.
         */
        if (newMoney > 2_000_000_000L
                || newMoney < currentMoney) {

            SC_ChatPacket.sendDirect(player, player, "더 이상 공금을 입금할 수 없습니다.");

            return;
        }

        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(player, MJCastleWarBusiness.TREASURY_ORDER_KEY)) {
            if (!tx.debit(L1ItemId.ADENA, amount)) return;
            final Runnable treasury = business.stageTreasury(tx, castleId, amount);
            tx.commit(treasury);
        } catch (RuntimeException failure) {
            SC_ChatPacket.sendDirect(player, player, "입금을 완료하지 못했습니다. 잔액을 확인해 주세요.");
        }
    }
}