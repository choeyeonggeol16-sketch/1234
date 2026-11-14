package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_Withdraw {

    private CS_Withdraw() {
    }

    public static void handle(
            CIOClient client,
            int drawAmount) {

        if (client == null) {
            return;
        }

        try {

            /*
             * 원본도 player 조회보다 금액 검증이 먼저.
             */
            if (drawAmount <= 0) {
                return;
            }

            final L1PcInstance pc =
                    client.getActiveChar();

            if (pc == null
                    || pc.isGhost()) {

                return;
            }

            final L1World world =
                    L1World.getInstance();

            final L1Clan clan =
                    world.getClan(
                            pc.getClanid());

            if (clan == null) {
                return;
            }

            final int castleId =
                    clan.getCastleId();

            if (castleId == 0) {
                return;
            }

            final MJCastleWarBusiness business =
                    MJCastleWarBusiness
                            .getInstance();

            /*
             * 공성 중 인출 불가.
             */
            if (business.isNowWar(castleId)) {

                SC_SystemMessage.send(pc, 
                                "공성 중에는 세금을 찾으실 수 없습니다.");

                return;
            }

            /*
             * 군주 권한 검증.
             */
            if (pc.getClanRank()
                        != L1Clan.군주
                    || !pc.isCrown()
                    || pc.getId()
                        != clan.getLeaderId()) {

                return;
            }

            final MJCastleWar war =
                    business.get(castleId);

            if (war == null) {
                return;
            }

            final int currentMoney =
                    war.getPublicMoney();

            if (currentMoney <= 0
                    || currentMoney < drawAmount) {

                return;
            }

            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, MJCastleWarBusiness.TREASURY_ORDER_KEY)) {
                if (tx.grant(L1ItemId.ADENA, drawAmount) == null) return;
                final Runnable treasury = business.stageTreasury(tx, castleId, -drawAmount);
                tx.commit(() -> {
                    treasury.run();
                    SC_ServerMessage.send(pc, 143, "$457", String.format("$4(%d)", drawAmount));
                });
            }

        } catch (Exception e) {
            java.util.logging.Logger.getLogger(CS_Withdraw.class.getName())
                    .log(java.util.logging.Level.WARNING, "Castle withdrawal failed", e);
        }
    }
}