package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_AdenShop;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_WAREHOUSE_ITEM_LIST_NOTI;

import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_AdenShop {

    private CS_AdenShop() {
    }

    public static void handle(
            CUserSocket client,
            int type,
            int size,
            int firstId,
            int firstCount,
            Object object) {

        if (client == null) {
            return;
        }

        try {

            final L1PcInstance pc =
                    client.getActiveChar();

            if (pc == null) {
                return;
            }

            switch (type) {

            /*
             * 아덴 상점 열기.
             */
            case 1:

                openShop(pc);

                return;

            /*
             * 부가서비스 창고.
             */
            case 6:

                openSupplementaryWarehouse(
                        pc);

                return;

            default:
                return;
            }

        } catch (Exception e) {

            /*
             * 원본 runImpl() semantics 유지.
             */
            e.printStackTrace();
        }
    }

    private static void openShop(L1PcInstance pc) {

        if (Config.Arden) {

            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 아덴상점이 점검 중입니다. 잠시 후 다시 이용해주세요");

            SC_ChatPacket.sendDirect(pc, pc, "현재 아덴상점이 점검 중입니다. 잠시 후 다시 이용해주세요", 1);

            return;
        }

        if (Config.아덴상점타입
                == Config.아덴상점엔샵아덴샵설정) {
            // N-shop balance and purchasing were removed; do not redirect to Adena payment.
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 사용할 수 없는 상점입니다.");
            return;
        }

        SC_AdenShop.send(pc, pc);
    }

    private static void openSupplementaryWarehouse(
            L1PcInstance pc) {

        final SupplementaryService warehouse =
                WarehouseManager
                        .getInstance()
                        .getSupplementaryService(
                                pc.getAccountName());

        final int size =
                warehouse.getSize();

        if (size > 0) {

            SC_WAREHOUSE_ITEM_LIST_NOTI
                    .send_user_supplementary_service(
                            pc,
                            pc.getId());

        } else {

            SC_ServerMessage.send(pc, 1625);
        }
    }
}
