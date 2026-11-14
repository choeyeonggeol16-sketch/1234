package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_AdenShop;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.shop.L1AdenShop;
import l1j.server.server.packets.Protocol.Packet.SC_WarehouseItemListNoti;

import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SurvivalCry;

public final class CS_AdenShop {

    private CS_AdenShop() {
    }

    public static void handle(
            CIOClient client,
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

                openShop(
                        client,
                        pc);

                return;

            /*
             * OTP 입력 / 구매 요청.
             */
            case 4:

                handlePurchase(
                        client,
                        pc,
                        size,
                        firstId,
                        firstCount,
                        object);

                return;

            /*
             * 부가서비스 창고.
             */
            case 6:

                openSupplementaryWarehouse(
                        pc);

                return;

            /*
             * 동의 및 구매.
             */
            case 0x32:

                SC_SurvivalCry.send(client, pc, SC_SurvivalCry.OTP_SHOW);

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

    private static void openShop(
            CIOClient client,
            L1PcInstance pc) {

        if (Config.Arden) {

            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 아덴상점이 점검 중입니다. 잠시 후 다시 이용해주세요");

            SC_ChatPacket.sendDirect(pc, pc, "현재 아덴상점이 점검 중입니다. 잠시 후 다시 이용해주세요", 1);

            return;
        }

        if (Config.아덴상점타입
                == Config.아덴상점엔샵아덴샵설정) {

            SC_SurvivalCry.send(client, pc, SC_SurvivalCry.LIST);

            SC_SurvivalCry.send(client, pc, SC_SurvivalCry.EMAIL);

            SC_SurvivalCry.send(client, pc, SC_SurvivalCry.POINT);

            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "[반복] N코인은 '리니지 n샵'에게서 구매가능 합니다.");

            pc.sendPackets(
                    "\\aG[반복] N코인은 '리니지 n샵'에게서 구매가능 합니다.");

            return;
        }

        SC_AdenShop.send(pc, pc);
    }

    private static void handlePurchase(
            CIOClient client,
            L1PcInstance pc,
            int size,
            int firstId,
            int firstCount,
            Object object) {

        /*
         * legacy:
         *
         * if (_shopRequests == null
         *         || _shopRequests.isEmpty()) {
         *     return;
         * }
         *
         * _shopRequests는 size > 0에서만 생성된다.
         */
        if (size <= 0) {
            return;
        }

        final L1AdenShop shop =
                new L1AdenShop();

        /*
         * 첫 번째 항목.
         *
         * size==1이면 여기까지 C 입력 allocation 0.
         */
        if (firstCount <= 0
                || firstCount >= 10000) {

            return;
        }

        shop.add(
                pc,
                firstId,
                firstCount);

        if (size > 1) {

            final byte[] tail =
                    object instanceof byte[]
                            ? (byte[]) object
                            : null;

            final int length =
                    tail != null
                            ? tail.length
                            : 0;

            int pos = 0;

            for (int i = 1;
                    i < size;
                    i++) {

                int id = 0;
                int count = 0;

                /*
                 * legacy readD():
                 * remaining < 4 -> 0, position 유지.
                 */
                if (length - pos >= 4) {

                    id =
                            readIntLE(
                                    tail,
                                    pos);

                    pos += 4;
                }

                /*
                 * legacy readH():
                 * remaining < 2 -> 0, position 유지.
                 *
                 * 따라서 D가 실패한 상태에서
                 * 2~3 byte가 남아 있으면 그 앞 2byte를
                 * count로 읽는 동작까지 그대로 재현한다.
                 */
                if (length - pos >= 2) {

                    count =
                            readUnsignedShortLE(
                                    tail,
                                    pos);

                    pos += 2;
                }

                if (count <= 0
                        || count >= 10000) {

                    return;
                }

                shop.add(
                        pc,
                        id,
                        count);
            }
        }

        if (!shop.BugOk()) {

            if (shop.commit(pc)) {

                SC_SurvivalCry.send(client, pc, SC_SurvivalCry.OTP_CHECK_MSG);
            }
        }
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

            SC_WarehouseItemListNoti
                    .send_user_supplementary_service(
                            pc,
                            pc.getId());

        } else {

            SC_ServerMessage.send(pc, 1625);
        }
    }

    private static int readIntLE(
            byte[] data,
            int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readUnsignedShortLE(
            byte[] data,
            int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8);
    }
}