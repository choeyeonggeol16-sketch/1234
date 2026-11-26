package l1j.server.server.model.character.skill;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;

public final class TrueTargetManager {

    private TrueTargetManager() {
    }

    /**
     * 캐스터의 트루타겟 지정.
     * 같은 타겟:
     * - 기존 효과 제거 안 함
     * - 지속시간만 갱신
     * 
     * 다른 타겟:
     * - 이전 타겟 효과 제거
     * - 새 타겟 적용
     *
     * 자기 자신도 정상 처리.
     */
    public static void apply(
            final L1PcInstance caster,
            final L1Character newTarget,
            final int durationMs) {

        if (caster == null || newTarget == null) {
            return;
        }

        final int newTargetId = newTarget.getId();
        final int oldTargetId = caster.getTrueTargetId();

        // =====================================================
        // 같은 타겟 재지정
        // =====================================================
        if (oldTargetId == newTargetId) {
            newTarget.setSkillEffect(L1SkillId.TRUE_TARGET, durationMs);
            return;
        }

        // =====================================================
        // 기존 타겟이 있을 경우
        // =====================================================
        if (oldTargetId != 0) {

            final L1Object oldObj =
                    CWorld.getInstance()
                            .findObject(oldTargetId);

            if (oldObj instanceof L1Character) {

                final L1Character oldTarget =
                        (L1Character) oldObj;

                oldTarget.removeSkillEffect(L1SkillId.TRUE_TARGET);

                if (oldTarget instanceof L1PcInstance targetPc) {
                	targetPc.setTrueTarget(0);
                }
            }
        }

        // =====================================================
        // 새 타겟 저장
        // =====================================================
        caster.setTrueTargetId(newTargetId);

        // 새 효과 적용
        newTarget.setSkillEffect( L1SkillId.TRUE_TARGET, durationMs);
    }

    /**
     * 캐스터의 현재 트루타겟 완전 해제.
     */
    public static void clear(
            final L1PcInstance caster) {

        if (caster == null) {
            return;
        }

        final int targetId = caster.getTrueTargetId();

        if (targetId == 0) {
            return;
        }

        caster.clearTrueTargetId();

        final L1Object obj =
                CWorld.getInstance()
                        .findObject(targetId);

        if (!(obj instanceof L1Character)) {
            return;
        }

        final L1Character target = (L1Character) obj;

        target.removeSkillEffect(L1SkillId.TRUE_TARGET);

        if (target instanceof L1PcInstance targetPc) {
        	targetPc.setTrueTarget(0);
        }
    }
}