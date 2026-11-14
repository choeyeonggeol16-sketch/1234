package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.BuddyTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_RemoveBuddy {

    private CS_RemoveBuddy() {
    }

    public static void handle(
            CIOClient client,
            String charName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        if (charName == null
                || charName.isEmpty()) {

            return;
        }

        final int pcId =
                pc.getId();

        ThreadPool
                .getInstance()
                .execute(() -> {

                    try {

                        BuddyTable
                                .getInstance()
                                .removeBuddy(
                                        pcId,
                                        charName);

                    } catch (Exception e) {

                        e.printStackTrace();
                    }
                });
    }
}