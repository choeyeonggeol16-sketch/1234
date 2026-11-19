package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public final class CS_QueryCastleSecurity {

    private CS_QueryCastleSecurity() {
    }

    public static void handle(
            CIOClient client,
            int objId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Clan clan =
                L1World
                        .getInstance()
                        .getClan(
                                pc.getClanid());

        if (clan == null
                || clan.getCastleId() == 0) {

            return;
        }

        final int castleId =
                clan.getCastleId();

        String npcName = null;

        switch (castleId) {

        case 1:
        case 2:
        case 3:
        case 5:
            break;

        case 4:
            npcName = "$1238";
            break;

        default:
            break;
        }

        final String status;

        if (MJCastleWarBusiness
                .getInstance()
                .getSecurity(castleId) == 0) {

            status = "$1118";

        } else {

            status = "$1117";
        }

        /*
         * S packet 쪽 allocation은 지금 단계에서 건드리지 않는다.
         * 기존 semantics 그대로.
         */
        final String[] htmlData = {
                npcName,
                status
        };

        SC_NPCTalkReturn.send(pc, objId, "CastleS", htmlData);
    }
}