package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;

public final class CS_Withdraw {

    private CS_Withdraw() {
    }

    public static void handle(
            CUserSocket client,
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

            final CastleWarBusiness business =
                    CastleWarBusiness
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

            final CastleWar war =
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

            try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                    l1j.server.server.model.inventory.L1InventoryTransaction.begin(pc, CastleWarBusiness.TREASURY_ORDER_KEY)) {
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