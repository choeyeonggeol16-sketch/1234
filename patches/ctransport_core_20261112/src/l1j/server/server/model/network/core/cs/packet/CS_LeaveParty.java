package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_LeaveParty {

    private CS_LeaveParty() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null) {
            return;
        }

        if (!player.is_world()) {

            player.sendPackets(
                    "해당 맵에서는 탈퇴할 수 없습니다.");

            return;
        }

        if (player.isInParty()) {

            player
                    .getParty()
                    .leaveMember(player);
        }
    }
}