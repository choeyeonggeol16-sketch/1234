package l1j.server.server.model.network.core.cs.packet;

import java.sql.SQLException;

import l1j.server.Config;
import l1j.server.server.datatables.CharacterConfigTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.storage.CacheD;

public final class CS_SaveIo {

    private CS_SaveIo() {
    }

    public static void handle(
            CUserSocket client,
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

        final int objectId = pc.getId();
        final byte[] payload = data.clone();
        CacheD.getInstance().submitDbJobOrdered(objectId, () -> {
            try {
                CharacterConfigTable.getInstance().storeCharacterConfig(objectId, length, payload);
            } catch (SQLException failure) {
                throw new IllegalStateException("Character config save failed: " + objectId, failure);
            }
        });
    }
}