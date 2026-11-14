package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_SlaveControl {

    private CS_SlaveControl() {
    }

    public static void handle(
            CIOClient client,
            int petId,
            int targetId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        /*
         * 명령 대상 pet 검증.
         */
        final L1Object petObject =
                L1World
                        .getInstance()
                        .findObject(petId);

        if (!(petObject instanceof L1PetInstance)) {
            return;
        }

        final L1PetInstance pet =
                (L1PetInstance) petObject;

        /*
         * 반드시 자기 pet이어야 한다.
         */
        if (pet.getMaster() == null
                || pet.getMaster().getId()
                    != pc.getId()) {

            return;
        }

        /*
         * 공격 target 검증.
         */
        final L1Object targetObject =
                L1World
                        .getInstance()
                        .findObject(targetId);

        if (!(targetObject instanceof L1Character)) {
            return;
        }

        final L1Character target =
                (L1Character) targetObject;

        /*
         * PC 타겟이면 기존 Non-PvP 검사 그대로.
         */
        if (target instanceof L1PcInstance targetPc) {
            if (pc.checkNonPvP(
                    pc,
                    targetPc)) {

                return;
            }
        }

        pet.setMasterTarget(target);
    }
}