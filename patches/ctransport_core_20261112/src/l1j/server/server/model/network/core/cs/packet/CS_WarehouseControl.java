package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_RetrieveList;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_WarehouseControl {

    private CS_WarehouseControl() {
    }

    public static void handle(
            CIOClient client,
            int type,
            int value1,
            int value2) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        if (client.getAccount() == null) {
            return;
        }

        final int gamePassword =
                client.getAccount()
                        .getGamePassword();

        switch (type) {

        /*
         * 창고 비밀번호 설정
         *
         * value1 = oldPass
         * value2 = newPass
         */
        case 0:

            if (gamePassword == 0
                    || gamePassword == value1) {

                Account.setGamePassword(
                        client,
                        value2);

            } else {

                SC_ServerMessage.send(pc, 835);
            }

            return;

        /*
         * 창고 찾기
         *
         * value1 = chkPass
         * value2 = objId
         */
        case 1:

            if (gamePassword == 0
                    || gamePassword == value1) {

                if (pc.getLevel() >= 5) {

                    SC_RetrieveList.sendPrivate(pc, value2);
                }

            } else {

                SC_ServerMessage.send(pc, 835);
            }

            return;

        default:
            return;
        }
    }
}