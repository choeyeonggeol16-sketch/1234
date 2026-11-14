package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_ChangeCastleSecurity {

    private CS_ChangeCastleSecurity() {
    }

    public static void handle(
            CIOClient client,
            int objId,
            int type) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final int clanId =
                pc.getClanid();

        if (clanId <= 0) {
            return;
        }

        final L1Clan clan =
                L1World
                        .getInstance()
                        .getClan(clanId);

        if (clan == null) {
            return;
        }

        final int castleId =
                clan.getCastleId();

        if (castleId <= 0) {
            return;
        }

        final MJCastleWarBusiness business =
                MJCastleWarBusiness
                        .getInstance();

        final MJCastleWar war =
                business.get(castleId);

        if (war == null) {
            return;
        }

        if (war.getCastleSecurity()
                == type) {

            SC_CloseList.send(pc, objId);

            return;
        }

        final int money =
                war.getPublicMoney();

        if (money < 100000) {
            return;
        }

        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, MJCastleWarBusiness.TREASURY_ORDER_KEY)) {
            Runnable treasury = type == 1 ? business.stageTreasury(tx, castleId, -100000) : () -> {};
            if (type != 1) tx.sql(con -> { /* owner/FIFO receipt for the state transition */ });
            tx.commit(() -> {
                treasury.run(); war.setCastleSecurity(type); SC_CloseList.send(pc, objId);
            });
        } catch (RuntimeException failure) {
            java.util.logging.Logger.getLogger(CS_ChangeCastleSecurity.class.getName())
                    .log(java.util.logging.Level.WARNING, "Castle security change failed", failure);
        }
    }
}