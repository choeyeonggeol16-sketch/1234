package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.utils.FaceToFace;

public final class CS_Duel {

    private CS_Duel() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final L1PcInstance target =
                FaceToFace.faceToFace(pc);

        if (target == null) {
            return;
        }

        /*
         * 원본은 대상이 마비 상태면
         * 메시지 없이 그냥 아무것도 안 한다.
         */
        if (target.isParalyzed()) {
            return;
        }

        /*
         * 신청자 본인이 이미 결투 중.
         */
        if (pc.getFightId() != 0) {

            SC_ServerMessage.send(pc, 633);

            return;
        }

        /*
         * 대상이 이미 결투 중.
         *
         * 주의:
         * 원본은 메시지 634를 pc가 아니라
         * target에게 보낸다.
         *
         * 이상해 보여도 그대로 유지.
         */
        if (target.getFightId() != 0) {

            SC_ServerMessage.send(target, 634);

            return;
        }

        pc.setFightId(
                target.getId());

        target.setFightId(
                pc.getId());

        SC_MessageYN.send(target, 630,
                        pc.getName());
    }
}