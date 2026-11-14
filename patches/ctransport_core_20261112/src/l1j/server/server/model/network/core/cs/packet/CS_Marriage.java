package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_Marriage {

    private CS_Marriage() {
    }

    public static void handle(
            CIOClient client,
            int type) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        switch (type) {

        case 0:
            propose(pc);
            return;

        case 1:
            divorce(pc);
            return;

        default:
            return;
        }
    }

    private static void propose(
            L1PcInstance pc) {

        final L1PcInstance target =
                FaceToFace.faceToFace(pc);

        if (target == null
                || target.getId() == pc.getId()) {

            return;
        }

        /*
         * 자신이 이미 결혼 상태.
         */
        if (pc.getPartnerId() != 0) {

            SC_ServerMessage.send(pc, 657);

            return;
        }

        /*
         * 상대가 이미 결혼 상태.
         */
        if (target.getPartnerId() != 0) {

            SC_ServerMessage.send(pc, 658);

            return;
        }

        /*
         * 동성 여부.
         */
        if (pc.get_sex()
                == target.get_sex()) {

            SC_ServerMessage.send(pc, 661);

            return;
        }

        /*
         * 원본 결혼식장 범위 그대로.
         */
        if (!isWeddingZone(pc)
                || !isWeddingZone(target)) {

            return;
        }

        /*
         * 상대가 Y/N 응답할 때 사용할
         * 신청자 object id 보존.
         */
        target.setTempID(
                pc.getId());

        SC_MessageYN.send(target, 654,
                        pc.getName());
    }

    private static void divorce(
            L1PcInstance pc) {

        if (pc.getPartnerId() == 0) {

            SC_ServerMessage.send(pc, 662);

            return;
        }

        SC_MessageYN.send(pc, 653,
                        "");
    }

    private static boolean isWeddingZone(
            L1PcInstance pc) {

        return pc.getMapId() == 4
                && pc.getX() >= 33974
                && pc.getX() <= 33976
                && pc.getY() >= 33362
                && pc.getY() <= 33365;
    }
}