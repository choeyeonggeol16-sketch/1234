package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_FixWeaponList;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_FixableItem {

    private CS_FixableItem() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        /*
         * 원본에는 ghost 검사가 없음.
         */
        if (pc == null) {
            return;
        }

        SC_FixWeaponList.send(pc);
    }
}