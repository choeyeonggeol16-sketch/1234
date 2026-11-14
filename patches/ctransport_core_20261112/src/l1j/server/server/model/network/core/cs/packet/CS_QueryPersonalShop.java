package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Packet.SC_PersonalShopItemListNoti;

public final class CS_QueryPersonalShop {

    private CS_QueryPersonalShop() {
    }

    public static void handle(
            CIOClient client,
            int type,
            int objectId) {

        if (client == null) {
            return;
        }

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

            if (pc.getAccountName()
                    .equalsIgnoreCase(
                            shopPc.getAccountName())) {

                pc.sendPackets(
                        "한 계정 내 무인상점은 이용할 수 없습니다.");

                return;
            }

            SC_PersonalShopItemListNoti.do_send(
                    pc,
                    objectId,
                    SC_PersonalShopItemListNoti
                            .ePersonalShopType
                            .fromInt(type));

            return;
        }

        /*
         * NPC 개인상점
         */
        if (shopObject instanceof L1NpcShopInstance) {

            SC_PersonalShopItemListNoti
                    .do_send_for_npc(
                            pc,
                            objectId,
                            SC_PersonalShopItemListNoti
                                    .ePersonalShopType
                                    .fromInt(type));

            return;
        }

        /*
         * 혈맹 가입 오브젝트
         */
        if (shopObject != null
                && shopObject.instanceOf(
                        MJL1Type.L1TYPE_CLANJOIN)) {

            pc.sendPackets(
                    "혈맹 가입을 원하면 칼질해주세요.");

            return;
        }

        pc.sendPackets(
                "상점 오브젝트가 발견되지 않았습니다");
    }
}