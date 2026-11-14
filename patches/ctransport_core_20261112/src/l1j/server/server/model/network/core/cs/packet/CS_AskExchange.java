package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_AskExchange {

    private CS_AskExchange() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null
                || player.isGhost()
                || player.isItemTransferBusy()
                || player.isTradeResolving()) {

            return;
        }

        /*
         * 원본 순서 그대로.
         */
        if (player.getOnlineStatus() == 0) {

            client.kick();

            return;
        }

        if (hasAnyChangeItem(player)) {

            player.sendPackets(
                    "변경권을 사용 중이거나 취소하셨으므로 리스 후에 교환 가능합니다.");

            return;
        }

        if (player.getTradeID() > 0) {

            SC_SystemMessage.send(player, "당신은 현재 교환 중인 상태입니다.");

            return;
        }

        if (player.getMapId() == 5554) {

            player.sendPackets(
                    "해당 맵에서는 사용할 수 없습니다.");

            return;
        }

        if (player.isInvisble()) {

            SC_ServerMessage.send(player, 334);

            return;
        }

        final L1PcInstance target =
                FaceToFace.faceToFace(player);

        if (target == null || target == player) {
            return;
        }
        // A cancellation is deliberately refused while a durable transfer is
        // pending. Never overwrite that pair's IDs with a new exchange request.
        if (target.isItemTransferBusy() || target.isTradeResolving()) {
            SC_SystemMessage.send(player, "상대방이 거래를 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }

        /*
         * 같은 계정 캐릭터끼리 거래 시
         * 양쪽 모두 disconnect packet.
         */
        if (player
                .getAccountName()
                .equalsIgnoreCase(
                        target.getAccountName())) {

            SC_Disconnect.send(player);

            SC_Disconnect.send(target);

            return;
        }

        if (hasAnyChangeItem(target)) {

            player.sendPackets(
                    "상대방이 변경권을 사용 중이거나 취소하셨으므로 상대방이 리스 후에 교환 가능합니다.");

            return;
        }

        /*
         * 대상이 마비 상태면 아무 동작 없음.
         */
        if (target.isParalyzed()) {
            return;
        }


        final L1PcInstance first = player.getId() < target.getId() ? player : target;
        final L1PcInstance second = first == player ? target : player;
        if (!first.tryBeginTradeResolution()) return;
        if (!second.tryBeginTradeResolution()) {
            first.endTradeResolution();
            return;
        }
        try {
            // Do not forcibly cancel an existing pair: cancellation may be
            // refused because its DB transaction or escrow return is pending.
            if (player.getTradeID() != 0 || target.getTradeID() != 0
                    || player.isItemTransferBusy() || target.isItemTransferBusy()
                    || player.getTradeWindowInventory().getSize() != 0
                    || target.getTradeWindowInventory().getSize() != 0) {
                SC_SystemMessage.send(player, "상대방이 거래를 처리 중입니다. 잠시 후 다시 시도해 주세요.");
                return;
            }
            player.setTradeOk(false);
            target.setTradeOk(false);
            player.setTradeID(target.getId());
            target.setTradeID(player.getId());
            SC_MessageYN.send(target, 252, player.getName());
        } finally {
            second.endTradeResolution();
            first.endTradeResolution();
        }
    }

    private static boolean hasAnyChangeItem(
            L1PcInstance pc) {

        return pc.getIsChangeItem()
                || pc.getIsChangeItem1()
                || pc.getIsChangeItem2()
                || pc.getIsChangeItem3()
                || pc.getIsChangeItem4()
                || pc.getIsChangeItem5()
                || pc.getIsChangeItem6()
                || pc.getIsChangeItem7()
                || pc.getIsChangeItem8()
                || pc.getIsChangeItem9()
                || pc.getIsChangeItem10()
                || pc.getIsChangeItem11()
                || pc.getIsChangeItem12()
                || pc.getIsChangeItem13()
                || pc.getIsChangeItem14()
                || pc.getIsChangeItem15()
                || pc.getIsChangeItem16()
                || pc.getIsChangeItem17()
                || pc.getIsChangeItem18()
                || pc.getIsChangeItem19()
                || pc.getIsChangeItem20();
    }
}