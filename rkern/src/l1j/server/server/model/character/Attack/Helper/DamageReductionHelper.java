package l1j.server.server.model.character.Attack.Helper;

import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

public final class DamageReductionHelper {

    private DamageReductionHelper() {}

    /**
     * 마법/마검/마법공격 공통: 타겟PC의 모든 DR(확률/고정/퍼센트)을 순서대로 적용.
     * @param targetPc 피격자(PC만)
     * @param attacker 공격자(PC or NPC)
     * @param ranged   원거리 판정(마법이면 보통 true, 접촉마법이면 false 같은 식으로 호출부에서 결정)
     * @param dmg      현재까지 계산된 dmg
     * @return 적용 후 dmg (0 floor)
     */
    public static int applyAllDamageReductions(L1PcInstance targetPc,
                                                    L1Character attacker,
                                                    boolean ranged,
                                                    boolean magic,
                                                    int dmg) {
        return applyAllDamageReductions(targetPc, attacker, ranged, magic, dmg, false, false);
    }

    /** Physical multiplier insertion point: fixed skill/AC/armor DR precedes
     * Burning/Fury, while percentage defense retains its later stage.
     * Legacy magic/NPC callers pass no deferred multiplier flags. */
    public static int applyAllDamageReductions(L1PcInstance targetPc,
            L1Character attacker, boolean ranged, boolean magic, int dmg,
            boolean burning, boolean fury) {
        if (targetPc == null || dmg <= 0) return dmg;

        int out = dmg;
        
        if(magic) {
        	final int pvpRed = targetPc.getPVPMagicDamageReduction();
    		if (pvpRed > 0) {
    			out -= (int) ((out * (pvpRed / 100.0)));
    		}
        }

        // =========================================================
        // 1) 확률 데미지 리덕션 (여러개 중 1개만, SFX 1회)
        // =========================================================
        // Original src22 ArmorProcEngine is unavailable in the 2019 source.
        // Equipment-specific procs are handled in the resident combat pipeline.

        // =========================================================
        // 2) REDUCTION_ARMOR / MAJESTY / DRAGON_SKIN
        // =========================================================
        int reduce = 0;
        final boolean hasReductionArmor = targetPc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR);
        final boolean hasMajesty        = targetPc.hasSkillEffect(L1SkillId.MAJESTY);
        final boolean hasDragonSkin     = targetPc.hasSkillEffect(L1SkillId.DRAGON_SKIN);

        if (hasReductionArmor || hasMajesty || hasDragonSkin) {
            final int lvl = targetPc.getLevel();

            if (hasReductionArmor) {
                int l = (lvl < 50) ? 50 : lvl;
                reduce += ((l - 50) / 5) + 1;
            }
            if (hasMajesty) {
                if (lvl < 80) reduce += 2;
                else reduce += 2 + ((lvl - 80) / 2);
            }
            if (hasDragonSkin) {
                int dr = 5;
                if (lvl >= 80) dr += ((lvl - 80) / 2) + 1;
                reduce += dr;
            }
            if (reduce != 0) {
                out -= reduce;
                if (out <= 0) return 0;
            }
        }

        // =========================================================
        // 3) ARMOR_GUARD (AC 기반 DR)
        // =========================================================
        if (targetPc.getPassive(MJPassiveID.ARMOR_GUARD.toInt()) != null) {
            final int ac = targetPc.getAC().getAc();
            final int armorGuardDr = (ac < 0) ? (-ac) / 10 : 0;
            if (armorGuardDr != 0) {
                out -= armorGuardDr;
                if (out <= 0) return 0;
            }
        }

        // =========================================================
        // 4) 인형/방어구 DR
        // =========================================================
        final int armorDr = targetPc.getDamageReductionByArmor();
        if (armorDr != 0) {
            final int ignore = Math.max(0, targetPc.getDamageReductionIgnore()); 
            final int effDr  = armorDr - ignore;

            if (effDr > 0) {
                out -= effDr;
                if (out <= 0) return 0;
            }
        }

        if (!magic) {
            out = L1DamageCalcHelper.applyFinalPhysicalMultipliers(out, burning, fury);
        }

        // =========================================================
        // 5) IMMUNE_TO_HARM (퍼센트 감쇠)
        // LUCIFER is applied once by the PC victim's GotDamage(), including
        // non-attack damage. Do not reduce the same physical hit twice here.
        // =========================================================
        if (!targetPc.hasSkillEffect(L1SkillId.LUCIFER)) {
            final double rate = targetPc.getImmuneReduction();
            if (rate > 0.0) {
                out -= (int) Math.round(out * rate);
            }
        }
        return (out > 0) ? out : 0;
    }
}