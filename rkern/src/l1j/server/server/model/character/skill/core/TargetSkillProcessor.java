package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.Light;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.SkillHandlers;
import l1j.server.server.model.character.skill.helper.SkillGfxHelper;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.templates.L1Skills;

public enum TargetSkillProcessor {

    INSTANCE;

    public enum Outcome {
        CONTINUE,
        REMOVE_AND_CONTINUE,
        RETURN_ALL
    }

    public Outcome processOne(final L1SkillUse su, final L1Character target, final boolean calcOk) {

        if (su == null) return Outcome.CONTINUE;
        if (target == null) return Outcome.REMOVE_AND_CONTINUE;

        if (!calcOk) return Outcome.CONTINUE;

        final L1Skills skill = su.exSkill();
        if (skill == null) return Outcome.CONTINUE;

        final L1Character user = su.hdUser();
        if (user == null) return Outcome.CONTINUE;

        final L1PcInstance player = su.hdPlayer();
        final int skillId = su.hdSkillId();
        final int skillType = skill.getType();
        final int targetId = target.getId();
        final int userId = user.getId();
        final int calcType = su.hdCalcType();

        final boolean isAttack = skillType == L1Skills.TYPE_ATTACK;
        final boolean isCurseOrProb = skillType == L1Skills.TYPE_CURSE || skillType == L1Skills.TYPE_PROBABILITY;
        final boolean targetIsPc = target instanceof L1PcInstance;

        // =====================================================
        // 실제 계산 가능 대상인가
        // calcOk와 다른 의미이므로 유지.
        // =====================================================
        if (!TargetRules.INSTANCE.isTargetCalc(su, target)) {
            return Outcome.CONTINUE;
        }

        // =====================================================
        // Handler override reset
        // =====================================================
        su.hdResetCommitValues();

        // =====================================================
        // Buff icon duration
        // =====================================================
        if (targetIsPc && su.hdGetBuffIconDuration() == 0) {
            su.exSetBuffIconDurationSec(su.exBuffIconDurationSec());
        }

        // =====================================================
        // CURSE / PROBABILITY 대상 실패 판정
        // =====================================================
        if (isCurseOrProb && TargetRules.INSTANCE.isTargetFailure(su, target)) {
            return Outcome.REMOVE_AND_CONTINUE;
        }

        // =====================================================
        // Magic
        // =====================================================
        final L1Magic magic = new L1Magic(user, target,
                su.hdSkillUseType() != L1SkillUse.TYPE_GMBUFF);
        magic.setLeverage(su.getLeverage());

        // =====================================================
        // 재시전 전 기존 효과 삭제
        // =====================================================
        SkillUseHelper.deleteRepeatedSkills(target, skillId);

        // =====================================================
        // ATTACK Counter Magic
        // =====================================================
        if (isAttack && userId != targetId) {
            if (CounterMagicService.INSTANCE.consumeIfPresent(su.exIsCounterMagicEnabled(), target)) {
                return Outcome.REMOVE_AND_CONTINUE;
            }
        }

        // =====================================================
        // Undead
        // =====================================================
        final int undeadType = SkillCalc.INSTANCE.resolveUndeadType(target);

        // =====================================================
        // 기본 계산
        // =====================================================
        int dmg = 0;

        if (isCurseOrProb) {
            final boolean success = SkillCalc.INSTANCE.calcProbability(su, magic, target);
            if (!success) {
                return Outcome.REMOVE_AND_CONTINUE;
            } 
            if (CounterMagicService.INSTANCE.consumeIfPresent(su.exIsCounterMagicEnabled(), target)) {
                return Outcome.REMOVE_AND_CONTINUE;
            }
        } else {
            dmg = SkillCalc.INSTANCE.calcDamage(su, magic, target);
        }

        // =====================================================
        // Recast
        // =====================================================
        if (RecastPolicy.INSTANCE.refreshAndSkipFurther(su, target)) {
            return Outcome.CONTINUE;
        }

        // =====================================================
        // Skill Handler
        // =====================================================
        final int hd = SkillHandlers.dispatch(su, target, magic, undeadType, null);

        if (hd == SkillHandlers.HD_RETURN) return Outcome.RETURN_ALL;
        if (hd == SkillHandlers.HD_CONTINUE) return Outcome.CONTINUE;
        if (hd == SkillHandlers.HD_NOT_HANDLED) su.hdClearOverrides();

        // =====================================================
        // HEAL undead 처리
        // =====================================================
        if (skillType == L1Skills.TYPE_HEAL && calcType == su.tfCalcTypePcNpc()) {
            if (undeadType == 1) {
                dmg *= -1;
            } else if (undeadType == 3) {
                dmg = 0;
            }
        }

        // =====================================================
        // Tower / Door 음수 damage 방지
        // =====================================================
        if ((target instanceof L1TowerInstance || target instanceof L1DoorInstance) && dmg < 0) {
            dmg = 0;
        }

        // =====================================================
        // Handler override
        // =====================================================
        final int hdDamage = su.hdGetDamage();
        final int hdDrain = su.hdGetDrainMana();
        final int hdHeal = su.hdGetHeal();

        final int finalDmg = hdDamage != Integer.MIN_VALUE ? hdDamage : dmg;
        final int finalDrain = hdDrain != Integer.MIN_VALUE ? hdDrain : 0;
        final int finalHeal = hdHeal != Integer.MIN_VALUE ? hdHeal : 0;

        // =====================================================
        // 단일 공격 hit/miss GFX
        // =====================================================
        final boolean isSingleAttackHitGfx = isAttack && player != null && userId != targetId 
                && skill.getArea() == 0 && "attack".equals(skill.getTarget());

        if (isSingleAttackHitGfx) {
            SkillGfxHelper.sendAttackHitFromProcessor(su, target, finalDmg);
        }

        // =====================================================
        // Magic commit
        // =====================================================
        if ((finalDmg != 0 || finalDrain != 0) && !SkillUseHelper.isCommitSkipSkill(skillId)) {
            magic.commit(finalDmg, finalDrain);
        }

        // =====================================================
        // 직접 heal
        // =====================================================
        if (finalHeal > 0) {
            final int currentHp = user.getCurrentHp();
            final int maxHp = user.getMaxHp();
            final int nextHp = currentHp + finalHeal;

            user.setCurrentHp(nextHp > maxHp ? maxHp : nextHp);
        }

        // =====================================================
        // PC 후처리
        // =====================================================
        if (targetIsPc) {
            PcAfterEffect.INSTANCE.onAffected(su, (L1PcInstance) target);
        }

        // =====================================================
        // Magic effect
        // =====================================================
        su.exAddMagicList(target, false);

        // =====================================================
        // Light refresh
        // =====================================================
        if (targetIsPc) {
            final Light light = ((L1PcInstance) target).getLight();
            if (light != null) {
                light.turnOnOffLight();
            }
        }

        return Outcome.CONTINUE;
    }
}