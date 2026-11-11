package l1j.server.server.model;

/** 2016 MR/WIS research curve with the 2015 elemental A-coefficient candidate.
 * Stateless primitive arithmetic; ordinary spells against a known PC class only. */
public final class SpellDamageFormula {
    private SpellDamageFormula() {}

    // x^10 <= 240^10 < 2^80. Initialized once; no BigInteger/per-hit allocation.
    private static final long[] POW10_LOW = new long[241];
    private static final long[] POW10_HIGH = new long[241];
    static {
        for (int x = 0; x < POW10_LOW.length; x++) {
            long low = 1L, high = 0L;
            for (int i = 0; i < 10; i++) {
                high = high * x + Math.unsignedMultiplyHigh(low, x);
                low *= x;
            }
            POW10_LOW[x] = low;
            POW10_HIGH[x] = high;
        }
    }

    public static int classCoefficient100(int defenderType) {
        return switch (defenderType) {
        case 0 -> 230; // prince
        case 1, 5, 7 -> 240; // knight, dragon knight, warrior
        case 2, 4, 6 -> 220; // elf, dark elf, illusionist
        case 3 -> 200; // wizard
        default -> 0;
        };
    }

    public static boolean supports(int defenderType, int defenderWis, long mrMinusHit) {
        return classCoefficient100(defenderType) != 0 && defenderWis >= 0
                && classCoefficient100(defenderType) - (long) (defenderWis / 2) * 2L > 0
                && mrMinusHit >= 10L;
    }

    public static int coefficientA100(int totalInt, int totalSp, int trueSp, int elementResist) {
        final int er = Math.max(-100, Math.min(100, elementResist));
        // SP includes MB+ML. Remove them exactly once, as confirmed by Ability.
        // A*100 = 100 + floor(75*(INT+bonusSP-12)/8) - ER.
        final long stat = (long) totalInt + totalSp - trueSp - 12L;
        final long hundredths = 100L + Math.floorDiv(75L * stat, 8L) - er;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, hundredths));
    }

    public static int coefficientB100(int defenderType, int defenderWis, long mrMinusHit) {
        if (!supports(defenderType, defenderWis, mrMinusHit))
            throw new IllegalArgumentException("Unverified class/WIS or MR-MH below 10");
        final long c100 = classCoefficient100(defenderType) - 2L * (defenderWis / 2);
        // Approximate only to seed the integer candidate. Correct exact decimal
        // boundaries without epsilon: k <= c100 / mr^(3/10) iff
        // k^10 * mr^3 <= c100^10. For instance, 59049=3^10 is exact.
        int b = (int) Math.floor(c100 * StrictMath.pow((double) mrMinusHit, -0.3));
        while (b > 0 && !atLeast(b, (int) c100, mrMinusHit)) --b;
        while (b < 120 && atLeast(b + 1, (int) c100, mrMinusHit)) ++b;
        return b;
    }

    private static boolean atLeast(int k, int c100, long mr) {
        long low = POW10_LOW[k], high = POW10_HIGH[k];
        for (int i = 0; i < 3; i++) {
            final long carry = Math.unsignedMultiplyHigh(low, mr);
            final long highProduct = high * mr;
            final long nextHigh = highProduct + carry;
            if (Math.unsignedMultiplyHigh(high, mr) != 0L
                    || Long.compareUnsigned(nextHigh, highProduct) < 0) return false;
            low *= mr;
            high = nextHigh;
        }
        final int order = Long.compareUnsigned(high, POW10_HIGH[c100]);
        return order < 0 || (order == 0
                && Long.compareUnsigned(low, POW10_LOW[c100]) <= 0);
    }

    public static int calculate(int rolledDice, int a100, int b100,
            int addedMagicDamage, int damageReduction, int beforeMrBonus, boolean critical) {
        if (a100 < 0 || b100 < 0 || b100 > 120)
            throw new IllegalArgumentException("Invalid formula coefficients");
        long base = ((long) Math.max(0, rolledDice) * a100) / 100L;
        if (critical) base = (base * 3L) / 2L;
        // Existing DIS lawful adjustment is a separate pre-MR skill modifier.
        base = Math.max(0L, base + beforeMrBonus);
        final long resisted = (base * b100) / 100L;
        final long result = resisted + (long) addedMagicDamage - damageReduction;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, result));
    }
}
