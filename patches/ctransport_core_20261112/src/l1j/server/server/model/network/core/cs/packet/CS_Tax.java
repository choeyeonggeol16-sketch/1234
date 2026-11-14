package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_Tax {

    private CS_Tax() {
    }

    public static void handle(
            CIOClient client,
            int taxRate) {

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
         * 허용 세율: 10 ~ 50
         */
        if (taxRate < 10
                || taxRate > 50) {

            return;
        }

        final int clanId =
                player.getClanid();

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

        /*
         * 성 소유 혈맹의 군주만 변경 가능.
         */
        if (!player.isCrown()
                || clan.getLeaderId()
                        != player.getId()) {

            return;
        }

        final MJCastleWarBusiness business =
                MJCastleWarBusiness
                        .getInstance();

        business.setTaxRate(
                castleId,
                taxRate);

        business.updateCastle(
                castleId);
    }
}