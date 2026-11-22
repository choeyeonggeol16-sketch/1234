package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;
import l1j.server.server.shared.L1Type;

public final class CS_QueryPersonalShop {

    private CS_QueryPersonalShop() {
    }

    public static void handle(
            CUserSocket client,
            int type,
            int objectId) {

        if (client == null) {
            return;
        }

        client.setPlayerShopView(null);
        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final L1Object shopObject =
                L1World
                        .getInstance()
                        .findObject(objectId);

        /*
         * PC 개인상점
         */
        if (shopObject instanceof L1PcInstance) {

            final L1PcInstance shopPc =
                    (L1PcInstance) shopObject;

            if (!PlayerShopTransactions.canReach(pc, shopPc)) return;
            if (pc.getAccountName()
                    .equalsIgnoreCase(
                            shopPc.getAccountName())) {

                pc.sendPackets(
                        "한 계정 내 무인상점은 이용할 수 없습니다.");

                return;
            }

            SC_PERSONAL_SHOP_ITEM_LIST_NOTI.do_send(
                    pc,
                    objectId,
                    SC_PERSONAL_SHOP_ITEM_LIST_NOTI
                            .ePersonalShopType
                            .fromInt(type));

            return;
        }

        /*
         * NPC 개인상점
         */
        if (shopObject instanceof L1NpcShopInstance) {
            if (pc.isDead() || pc.getMapId() != shopObject.getMapId()
                    || Math.abs((long) pc.getX() - shopObject.getX()) > 12
                    || Math.abs((long) pc.getY() - shopObject.getY()) > 12) return;

            SC_PERSONAL_SHOP_ITEM_LIST_NOTI
                    .do_send_for_npc(
                            pc,
                            objectId,
                            SC_PERSONAL_SHOP_ITEM_LIST_NOTI
                                    .ePersonalShopType
                                    .fromInt(type));

            return;
        }

        /*
         * 혈맹 가입 오브젝트
         */
        if (shopObject != null
                && shopObject.instanceOf(
                        L1Type.L1TYPE_CLANJOIN)) {

            pc.sendPackets(
                    "혈맹 가입을 원하면 칼질해주세요.");

            return;
        }

        pc.sendPackets(
                "상점 오브젝트가 발견되지 않았습니다");
    }
}