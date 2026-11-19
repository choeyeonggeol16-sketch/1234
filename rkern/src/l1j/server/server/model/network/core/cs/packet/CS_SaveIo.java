package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.CharacterConfigTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_SaveIo {

    private CS_SaveIo() {
    }

    public static void handle(
            CIOClient client,
            int length,
            byte[] data) {

        if (client == null) {
            return;
        }


        if (!Config.CHARACTER_CONFIG_IN_SERVER_SIDE) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        if (length <= 0
                || length > 2048) {

            return;
        }

        if (data == null
                || data.length == 0) {

            return;
        }

        final int objectId =
                pc.getId();

        ThreadPool
                .getInstance()
                .execute(() -> {

                    try {
                        CharacterConfigTable
                                .getInstance()
                                .storeCharacterConfig(
                                        objectId,
                                        length,
                                        data);

                    } catch (Exception ignored) {
                    }
                });
    }
}