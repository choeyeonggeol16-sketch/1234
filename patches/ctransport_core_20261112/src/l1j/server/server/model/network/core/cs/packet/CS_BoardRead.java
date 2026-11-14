package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_BoardRead;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1BoardInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_BoardRead {

    private CS_BoardRead() {
    }

    public static void handle(
            CIOClient client,
            int objId,
            int topicNumber) {

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
                        .findObject(objId);


        if (!(object instanceof L1BoardInstance)) {
            return;
        }

        final L1BoardInstance board =
                (L1BoardInstance) object;

        board.onActionRead(
                pc,
                topicNumber);
    }
}