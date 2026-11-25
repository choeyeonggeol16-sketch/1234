package l1j.server.server.model.character.skill.helper;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.Attack.Helper.AttackSkillChanceConfig;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/**
 *  약점노출(포우 슬레이어) 헬퍼
 * - 평타(체인소드)로 스택 누적
 * - 포우(멀티히트)에서는 충전 1개당 타당 +20
 * - 포우 시전 즉시 스택/아이콘 삭제
 * - 타겟 변경 시 스택 초기화
 */
public final class WeakExposeHelper {

    private WeakExposeHelper() {}

    private static final int DURATION_MS = 16_000;

    // stage(0~4) -> skillId
    private static final int[] STAGE_SKILL = {
            0,
            L1SkillId.CHAINSWORD1,
            L1SkillId.CHAINSWORD2,
            L1SkillId.CHAINSWORD3,
            L1SkillId.CHAINSWORD4
    };

    // =====================================================
    // 메인: 스택 누적 + 포우 추가데미지 계산
    // =====================================================
    public static int WeakExpose(L1Character caster, L1Character target, int addChance) {
        if (!(caster instanceof L1PcInstance)) return 0;
        if (target == null) return 0;

        final L1PcInstance pc = (L1PcInstance) caster;

        // =====================================================
        // 0) 포우(멀티히트) 진행 중
        // =====================================================
        final int token = pc.getMultiHitToken();
        if (pc.fouIsActive(token)) {
            final int base = pc.GetFouDmgUp();
            final int add  = pc.fouConsumeAddThisHit(token);

            if (pc.fouIsFinished(token)) {
                pc.fouClear();
            }
            return base + add;
        }

        // =====================================================
        // 타겟 변경 시 초기화
        // =====================================================
        if (pc.getTargetId() != target.getId()) {
            pc.setTargetId(target.getId());
            clearAllStagesAndIcon(pc);
        }

        // =====================================================
        // 확률 체크
        // =====================================================
        final int probability = AttackSkillChanceConfig.get().weakExposeBaseChance() + addChance;
        if (probability <= 0 || (probability < 100
                && ThreadLocalRandom.current().nextInt(100) > probability)) {
            return 0;
        }

        final int stage = getCurrentStage(pc);

        switch (stage) {
        case 0:
            applyStage(pc, 1);
            break;

        case 1:
            pc.killSkillEffectTimer(STAGE_SKILL[1]);
            applyStage(pc, 2);
            break;

        case 2:
            pc.killSkillEffectTimer(STAGE_SKILL[2]);
            applyStage(pc, 3);
            break;
        case 3:
            if (pc.isPassive(PassiveID.FOU_SLAYER_BRAVE.toInt())) {
                pc.killSkillEffectTimer(STAGE_SKILL[3]);
                applyStage(pc, 4);
            } else {
                pc.setSkillEffect(STAGE_SKILL[3], DURATION_MS);
                SC_PacketBox.send(pc, SC_PacketBox.SPOT, 3);
            }
            break;
        case 4:
        default:
            break;
        }

        return 0;
    }

    private static int getCurrentStage(L1PcInstance pc) {
        if (pc.hasSkillEffect(STAGE_SKILL[4])) return 4;
        if (pc.hasSkillEffect(STAGE_SKILL[3])) return 3;
        if (pc.hasSkillEffect(STAGE_SKILL[2])) return 2;
        if (pc.hasSkillEffect(STAGE_SKILL[1])) return 1;
        return 0;
    }

    private static void applyStage(L1PcInstance pc, int stage) {
        pc.setSkillEffect(STAGE_SKILL[stage], DURATION_MS);
        SC_PacketBox.send(pc, SC_PacketBox.SPOT, stage);
    }

    // =====================================================
    // 포우슬레이어 시전 즉시 스택/아이콘 삭제
    // =====================================================
    public static void consumeAllNowOnFouCast(L1PcInstance pc) {
        if (pc == null) return;
        clearAllStagesAndIcon(pc);
    }

    private static void clearAllStagesAndIcon(L1PcInstance pc) {
        pc.removeSkillEffect(L1SkillId.CHAINSWORD1);
        pc.removeSkillEffect(L1SkillId.CHAINSWORD2);
        pc.removeSkillEffect(L1SkillId.CHAINSWORD3);
        pc.removeSkillEffect(L1SkillId.CHAINSWORD4);
        SC_PacketBox.send(pc, SC_PacketBox.SPOT, 0);
    }

    // =====================================================
    // Attack에서 호출
    // =====================================================
    public static int applyPowExposeOnHit(L1PcInstance pc,
                                         L1Character target,
                                         L1ItemInstance weapon,
                                         int weaponType,
                                         int dmg) {
        if (pc == null || target == null) return 0;
        if (dmg <= 0) return 0;

        if (!pc.isDragonknight()) return 0;
        if (weaponType != 18) return 0;

        final int weaponId = (weapon != null ? weapon.getItemId() : 0);
        final int addChance =
                (weaponId == L1ItemId.섬멸자_체인소드 || weaponId == L1ItemId.축복_섬멸자_체인소드) ? 4 : 0;

        return WeakExpose(pc, target, addChance);
    }
}