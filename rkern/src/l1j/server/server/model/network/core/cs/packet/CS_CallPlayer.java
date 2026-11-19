package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_CallPlayer {

    private CS_CallPlayer() {
    }

    public static void handle(
            CIOClient client,
            String name) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || !pc.isGm()) {

            return;
        }

        if (name == null
                || name.isEmpty()) {

            return;
        }

        final L1PcInstance target =
                L1World
                        .getInstance()
                        .getPlayer(name);

        if (target == null) {
            return;
        }

        final L1Location loc =
                L1Location.randomLocation(
                        target.getLocation(),
                        1,
                        2,
                        false);

        pc.start_teleport(
                loc.getX(),
                loc.getY(),
                target.getMapId(),
                pc.getHeading(),
                169,
                false,
                false);
    }
}