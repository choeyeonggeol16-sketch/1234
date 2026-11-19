package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1AuctionBoardInstance;
import l1j.server.server.model.Instance.L1BoardInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public final class CS_Plate {

    private static final int NEW_BOARD_NPC_ID =
            7310127;

    private CS_Plate() {
    }

    public static void handle(
            CIOClient client,
            int objectId) {

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
                        .findObject(objectId);

        if (!(object instanceof L1BoardInstance)
                && !(object instanceof L1AuctionBoardInstance)) {

            return;
        }

        if (object instanceof L1NpcInstance) {

            final L1NpcInstance npc =
                    (L1NpcInstance) object;

            if (npc.getNpcTemplate().get_npcId()
                    == NEW_BOARD_NPC_ID) {

                final L1NpcTalkData talking =
                        NPCTalkDataTable
                                .getInstance()
                                .getTemplate(
                                        NEW_BOARD_NPC_ID);

                SC_NPCTalkReturn.send(pc, talking, objectId, 1);

                return;
            }
        }

        object.onAction(pc);
    }
}