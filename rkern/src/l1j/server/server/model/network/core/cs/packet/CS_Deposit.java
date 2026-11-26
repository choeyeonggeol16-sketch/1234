package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;

public final class CS_Deposit {

    private CS_Deposit() {
    }

    public static void handle(
            CUserSocket client,
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
                CWorld
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

        final CastleWarBusiness business =
                CastleWarBusiness
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

        final CastleWar war =
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

        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                l1j.server.server.model.inventory.L1InventoryTransaction.begin(player, CastleWarBusiness.TREASURY_ORDER_KEY)) {
            if (!tx.debit(L1ItemId.ADENA, amount)) return;
            final Runnable treasury = business.stageTreasury(tx, castleId, amount);
            tx.commit(treasury);
        } catch (RuntimeException failure) {
            SC_ChatPacket.sendDirect(player, player, "입금을 완료하지 못했습니다. 잔액을 확인해 주세요.");
        }
    }
}