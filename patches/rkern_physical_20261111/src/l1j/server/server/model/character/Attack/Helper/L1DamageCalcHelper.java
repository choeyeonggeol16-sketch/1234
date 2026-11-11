package l1j.server.server.model.character.Attack.Helper;

import java.util.concurrent.ThreadLocalRandom;

/** 근/원거리 데미지 계산 헬퍼. */
public final class L1DamageCalcHelper {

    private L1DamageCalcHelper() {}

    public enum WeaponCritMode {
        NONE,
        DUAL_DOUBLE,   // 이도 더블 이펙트 (weaponCore*2)
        CLAW_CRIT      // 크로우 이펙트 (aRandom=max)
    }

    private static int nz(int v) { return (v <= 0) ? 0 : v; } // non-negative
    
    private static int clampMax(int v, int min) { return (v < min) ? min : v; }

    public static int calcDamage(
            int randMin, int randMax,

            int weaponBonus,   // 무기 고유추타 + 팽/브레스/인챈무기마법 등
            int enchant,       // 인챈트

            int charMagic,     // 기타추타(더블/이도더블/크로우에는 영향 X, 버닝 1.5배엔 포함)
            int statBonus,
            int classBonus,

            int undeadBonus,
            int weaponLoss,

            int attrPart,      // 속뎀

            final WeaponCritMode critMode,
            final boolean procDoubleBreak,
            final boolean procBurning,
            final boolean normalCriticalMax
    ) {
        // Legacy callers retain their original unarmed/zero-core contract.
        return calcDamage(randMin, randMax, weaponBonus, enchant, charMagic,
                statBonus, classBonus, undeadBonus, weaponLoss, attrPart,
                critMode, procDoubleBreak, procBurning, normalCriticalMax, false);
    }

    public static int calcDamage(int randMin, int randMax, int weaponBonus, int enchant,
            int charMagic, int statBonus, int classBonus, int undeadBonus, int weaponLoss,
            int attrPart, WeaponCritMode critMode, boolean procDoubleBreak,
            boolean procBurning, boolean normalCriticalMax, boolean hasWeapon) {
        // ---- sanitize
        randMin = nz(randMin);
        randMax = clampMax(nz(randMax), randMin);

        weaponBonus = nz(weaponBonus);
        enchant     = nz(enchant);
        charMagic   = nz(charMagic);
        statBonus   = nz(statBonus);
        classBonus  = nz(classBonus);
        undeadBonus = nz(undeadBonus);
        weaponLoss  = nz(weaponLoss);
        attrPart    = nz(attrPart);

        // ---- aRandom (크로우/일반크리면 max)
        final boolean forceMax = (critMode == WeaponCritMode.CLAW_CRIT) || normalCriticalMax;
        final int aRandom = forceMax ? randMax
                : (randMax == randMin ? randMin
                : ThreadLocalRandom.current().nextInt(randMin, randMax + 1));

        // ---- weaponCore (속뎀 제외)
        int weaponCore = aRandom + weaponBonus + enchant + undeadBonus - weaponLoss;
        weaponCore = Math.max(hasWeapon ? 1 : 0, weaponCore);

        // ---- 이도 더블: weaponCore만 2배
        if (critMode == WeaponCritMode.DUAL_DOUBLE) weaponCore <<= 1;

        // ---- weaponPart (속뎀 포함) + 더블브레이크 2배
        int weaponPart = weaponCore + attrPart;
        if (procDoubleBreak) weaponPart <<= 1;

        int dmg = weaponPart + charMagic + statBonus + classBonus;
        if (dmg <= 0) return 0;

        // ---- 버닝/엘파: 최종 1.5배
        if (procBurning) dmg = applyFinalPhysicalMultipliers(dmg, true, false);

        return dmg;
    }
    /** Once per confirmed physical attack, after fixed DR and before percent defense. */
    public static int applyFinalPhysicalMultipliers(int damage, boolean burning, boolean fury) {
        if (damage <= 0) return 0;
        long out = damage;
        if (burning) out = (out * 3L) / 2L;
        if (fury) out *= 2L;
        return (int) Math.min(Integer.MAX_VALUE, out);
    }

}
