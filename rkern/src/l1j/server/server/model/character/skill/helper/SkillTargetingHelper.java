package l1j.server.server.model.character.skill.helper;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.templates.L1Skills;

public final class SkillTargetingHelper {

    private SkillTargetingHelper() {
    }

    // =========================================================
    // Attack Skill
    // =========================================================

    public static boolean isAttackSkill(final L1SkillUse su) {
        if (su == null) {
            return false;
        }

        final L1Skills skill = su.tfSkill();

        if (skill == null) {
            return false;
        }

        return "attack".equals(skill.getTarget())
                || skill.getType() == L1Skills.TYPE_ATTACK;
    }

    // =========================================================
    // Base Target Rule
    // =========================================================

    public static boolean baseTargetByTargetTo(
            final L1SkillUse su,
            final L1Character cha) {

        if (su == null || cha == null) {
            return false;
        }

        final L1Skills skill =
                su.tfSkill();

        if (skill == null) {
            return false;
        }

        final int calcType =
                su.tfCalcType();

        final int targetTo =
                skill.getTargetTo();

        /*
         * NPC -> PC 타입일 경우
         * PC / PET / SUMMON / COMPANION 허용.
         */
        if (calcType == su.tfCalcTypeNpcPc()) {

            if (cha instanceof L1PcInstance
                    || cha instanceof L1SummonInstance
                    || cha instanceof L1CompanionInstance) {

                return true;
            }
        }

        boolean ok = false;

        // =====================================================
        // TARGET_TO_PC
        // =====================================================

        if ((targetTo & L1Skills.TARGET_TO_PC)
                == L1Skills.TARGET_TO_PC) {

            if (cha instanceof L1PcInstance) {
                ok = true;
            }
        }

        // =====================================================
        // TARGET_TO_NPC
        // =====================================================

        if ((targetTo & L1Skills.TARGET_TO_NPC)
                == L1Skills.TARGET_TO_NPC) {

            if (cha instanceof L1MonsterInstance
                    || cha instanceof L1NpcInstance
                    || cha instanceof L1SummonInstance
                    || cha instanceof L1CompanionInstance) {

                ok = true;
            }
        }

        // =====================================================
        // TARGET_TO_PET
        // =====================================================

        if (!ok
                && (targetTo & L1Skills.TARGET_TO_PET)
                        == L1Skills.TARGET_TO_PET
                && su.tfUser() instanceof L1PcInstance) {

            final L1PcInstance player =
                    su.tfPlayer();

            final int myId =
                    player != null
                    ? player.getId()
                    : 0;

            final int masterId =
                    getMasterIdOrZero(cha);

            if (masterId == 0 || myId == 0) {
                return false;
            }

            /*
             * RETURN_TO_NATURE:
             * 자기 펫이 아닌 소환체.
             */
            if (su.tfSkillId()
                    == L1SkillId.RETURN_TO_NATURE) {

                return masterId != myId;
            }

            return masterId == myId;
        }

        return ok;
    }

    // =========================================================
    // Controllable
    // =========================================================

    public static boolean isControllable(
            final L1Object obj) {

        return obj instanceof L1SummonInstance
                || obj instanceof L1CompanionInstance;
    }

    public static boolean isPcOrControllable(
            final L1Object obj) {

        return obj instanceof L1PcInstance
                || isControllable(obj);
    }

    // =========================================================
    // Master ID
    // =========================================================

    public static int getMasterIdOrZero(
            final L1Object obj) {

        if (obj == null) {
            return 0;
        }

        try {

            if (obj instanceof L1SummonInstance) {

                final L1SummonInstance summon =
                        (L1SummonInstance) obj;

                return summon.getMaster() != null
                        ? summon.getMaster().getId()
                        : 0;
            }


            if (obj instanceof L1SupportInstance) {

                final L1SupportInstance support =
                        (L1SupportInstance) obj;

                return support.getMaster() != null
                        ? support.getMaster().getId()
                        : 0;
            }

            if (obj instanceof L1CompanionInstance) {

                return ((L1CompanionInstance) obj)
                        .get_master_id();
            }

        } catch (Throwable ignore) {
        }

        return 0;
    }
}