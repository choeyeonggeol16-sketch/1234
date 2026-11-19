package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Skills;

public enum SkillCalc {
    INSTANCE;

    // =========================================================
    // ATTACK / HEAL damage
    // =========================================================
    public int calcDamage(final L1SkillUse su, final L1Magic magic, final L1Character target) {
        if (su == null || magic == null || target == null) return 0;

        final L1Skills skill = su.exSkill();
        if (skill == null) return 0;

        final L1Character user = su.hdUser();
        if (user == null) return 0;

        final int skillId = su.hdSkillId();
        final int skillType = skill.getType();

        // =====================================================
        // ATTACK
        // =====================================================
        if (skillType == L1Skills.TYPE_ATTACK && user.getId() != target.getId()) {
            final int dmg = magic.calcMagicDamage(skillId);
            su.exSetCriticalDamage(magic.isCriticalDamage());

            if (SkillUseHelper.shouldRemoveEraseOnAttack(skillId)) {
                SkillUseHelper.removeEraseMagicIfPresent(target);
            }
            return dmg;
        }

        // =====================================================
        // HEAL
        // =====================================================
        if (skillType == L1Skills.TYPE_HEAL) {
            int dmg = -magic.calcHealing(skillId);

            final boolean waterLife = target.hasSkillEffect(L1SkillId.WATER_LIFE);
            final boolean pollute = target.hasSkillEffect(L1SkillId.POLLUTE_WATER);
            final boolean papReduce = target.hasSkillEffect(L1SkillId.PAP_REDUCE_HELL);
            final boolean deathHeal = target.hasSkillEffect(L1SkillId.BLACKELDER_DEATH_HELL) 
                    || target.hasSkillEffect(L1SkillId.DEATH_HEAL) 
                    || target.hasSkillEffect(L1SkillId.DEATH_HEAL_Mob);

            if (waterLife) dmg <<= 1;
            if (pollute) dmg >>= 1;
            if (papReduce) dmg >>= 1;

            if (deathHeal) {
                dmg = -dmg;
                if (waterLife) dmg <<= 1;
                if (pollute) dmg >>= 1;
            }

            return dmg;
        }

        return 0;
    }


    // =========================================================
    // CURSE / PROBABILITY
    // =========================================================
    public boolean calcProbability(final L1SkillUse su, final L1Magic magic, final L1Character target) {
        if (su == null || magic == null || target == null) return false;

        final L1Skills skill = su.exSkill();
        if (skill == null) return false;

        final int skillType = skill.getType();
        if (skillType != L1Skills.TYPE_CURSE && skillType != L1Skills.TYPE_PROBABILITY) {
            return false;
        }

        final int skillId = su.hdSkillId();
        final L1Character user = su.hdUser();
        final boolean success = magic.calcProbabilityMagic(skillId);

        // =====================================================
        // PinkName
        // =====================================================
        if (target instanceof L1PcInstance && user instanceof L1PcInstance) {
            L1PinkName.onAction((L1PcInstance) target, user);
        }

        // =====================================================
        // ERASE 제거
        // =====================================================
        if (skillId != L1SkillId.ERASE_MAGIC && skillId != L1SkillId.EARTH_BIND) {
            SkillUseHelper.removeEraseMagicIfPresent(target);
        }

        // =====================================================
        // 수면 / 팬텀 제거
        // =====================================================
        if (skillId != L1SkillId.FOG_OF_SLEEPING) {
            target.removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
        }

        if (skillId != L1SkillId.PHANTASM) {
            target.removeSkillEffect(L1SkillId.PHANTASM);
        }

        // =====================================================
        // 실패
        // =====================================================
        if (!success) {
            if (skillId == L1SkillId.FOG_OF_SLEEPING && target instanceof L1PcInstance) {
                SC_ServerMessage.send(((L1PcInstance) target), 297);
            }
            return false;
        }

        return true;
    }

    // =========================================================
    // Undead
    // =========================================================
    public int resolveUndeadType(final L1Character target) {
        if (target instanceof L1MonsterInstance) {
            try {
                return ((L1MonsterInstance) target).getNpcTemplate().get_undead();
            } catch (Throwable ignore) {}
        }
        return 0;
    }
}