package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_PetInventory;

public final class CS_CheckInventory {

    private CS_CheckInventory() {
    }

    public static void handle(
            CIOClient client,
            int petId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Object object =
                L1World
                        .getInstance()
                        .findObject(petId);

        if (!(object instanceof L1PetInstance)) {
            return;
        }

        final L1PetInstance pet =
                (L1PetInstance) object;

        SC_PetInventory.send(pc, pet);
    }
}