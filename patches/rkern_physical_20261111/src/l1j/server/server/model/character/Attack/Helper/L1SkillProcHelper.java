package l1j.server.server.model.character.Attack.Helper;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.utils.CalcStat;

/**
 * 무기 특수 발동 판정 헬퍼
 */
public final class L1SkillProcHelper {

	private L1SkillProcHelper() {}

	// =========================
	// Flags
	// =========================
	public static final int FLAG_DOUBLE_BREAK = 1 << 0;
	public static final int FLAG_BURNING      = 1 << 1;
	public static final int FLAG_NORMAL_CRIT  = 1 << 2;
	public static final int FLAG_CYCLONE_PROC = 1 << 3;	
	public static final int FLAG_CRASH = 1 << 4;
	public static final int FLAG_FURY  = 1 << 5;

	private static final int MODE_SHIFT = 8;
	private static final int MODE_MASK  = 0xFF;

	// =========================
	// Mode
	// =========================
	public enum WeaponProcMode {
		NONE,
		DUAL_DOUBLE // 이도류 더블(= 무기파트*2, 속성 제외 옵션은 DamageCalcHelper에서 처리)
	}

	/**
	 * @return flags (비트마스크)
	 */
	public static int rollAllFlags(
			final L1PcInstance pc,
			final boolean ranged,
			final int weaponType,            //  54=이도, 58=크로우
			final int weaponId,
			final int weaponDoubleDmgChance, // 무기별/옵션별 이미 합산된 값
			final boolean hasBurningBuff
			) {
		int flags = 0;
        final AttackSkillChanceConfig.Settings rates = AttackSkillChanceConfig.get();

		if (pc == null) return 0;

		// 1) 더블브레이크
		if (isProcDoubleBreak(pc, ranged, weaponType, rates)) {
			flags |= FLAG_DOUBLE_BREAK;
		}

		// 2) 무기 고유(이도류 더블만)
		final WeaponProcMode mode = rollWeaponProcMode(pc, weaponType, weaponId, weaponDoubleDmgChance);
		flags |= (mode.ordinal() << MODE_SHIFT);

		// 3) 버닝/엘파/블로우어택/브레이브멘탈/퀘이크/사이클론 (최종 1.5배 계열)
		if (isProcCyclone(pc, ranged, hasBurningBuff, rates)) {
		    flags |= FLAG_BURNING;
		    flags |= FLAG_CYCLONE_PROC;
		} else if (isProcBurningOrElementalFire(pc, ranged, hasBurningBuff, rates)) {
		    flags |= FLAG_BURNING;
		}

		// 4) 일반 치명타
		// - 크로우: 캐릭터/무기 독립 사건의 합집합 확률로 FLAG_NORMAL_CRIT 판정
		// - 이도류: 근치명은 별도(독립)로 굴림 (mode와 동시에 켜질 수 있음)
		if (isProcCritical(pc, ranged, weaponType, weaponDoubleDmgChance)) {
			flags |= FLAG_NORMAL_CRIT;
		}

		return flags;
	}

	// =========================
	// Decoders
	// =========================
	public static boolean hasDoubleBreak(final int flags) {
		return (flags & FLAG_DOUBLE_BREAK) != 0;
	}

	public static boolean hasBurning(final int flags) {
		return (flags & FLAG_BURNING) != 0;
	}

	public static boolean hasNormalCrit(final int flags) {
		return (flags & FLAG_NORMAL_CRIT) != 0;
	}

	public static boolean hasCycloneProc(final int flags) {
		return (flags & FLAG_CYCLONE_PROC) != 0;
	}

	public static WeaponProcMode getMode(final int flags) {
		final int ord = (flags >> MODE_SHIFT) & MODE_MASK;
		return ord == WeaponProcMode.DUAL_DOUBLE.ordinal()
                ? WeaponProcMode.DUAL_DOUBLE : WeaponProcMode.NONE;
	}

	/**
	 * DamageCalcHelper 쪽 critMode로 변환
	 */
	public static L1DamageCalcHelper.WeaponCritMode toCritMode(final WeaponProcMode mode) {
		switch (mode) {
		case DUAL_DOUBLE: return L1DamageCalcHelper.WeaponCritMode.DUAL_DOUBLE;
		default:          return L1DamageCalcHelper.WeaponCritMode.NONE;
		}
	}

	// =========================
	// Proc logic
	// =========================

    public static boolean isProcDoubleBreak(final L1PcInstance pc, final boolean ranged, final int weaponType) {
        return isProcDoubleBreak(pc, ranged, weaponType, AttackSkillChanceConfig.get());
    }

    private static boolean isProcDoubleBreak(final L1PcInstance pc, final boolean ranged,
            final int weaponType, final AttackSkillChanceConfig.Settings rates) {
		if (pc == null) return false;
		if (ranged) return false;

		// 착용무기가 이도류/크로우일 때만
		if (weaponType != 54 && weaponType != 58) return false;

		if (!pc.hasSkillEffect(L1SkillId.DOUBLE_BRAKE)) return false;

        int chance = rates.doubleBreakChance();

        final int lvl = pc.getLevel();
        if (lvl >= rates.doubleBreakBonusStartLevel()) {
            chance += 1 + (lvl - rates.doubleBreakBonusStartLevel()) / rates.doubleBreakBonusStepLevel();
        }
        if (pc.isPassive(MJPassiveID.DOUBLE_BREAK_DESTINY.toInt())
                && lvl >= rates.doubleBreakDestinyStartLevel()) {
            chance += (lvl - rates.doubleBreakDestinyStartLevel()) * rates.doubleBreakDestinyPerLevel();
        }

		if (chance <= 0) return false;
		if (chance > 100) chance = 100;

		return rnd100() <= chance;
	}

	/**
	 * 무기 고유(이도류 더블) 발동 판정
	 */
	public static WeaponProcMode rollWeaponProcMode(
			final L1PcInstance pc,
			final int weaponType,
			final int weaponId,
			int weaponDoubleDmgChance
			) {
		if (pc == null) return WeaponProcMode.NONE;
		if (pc.getWeapon() == null) return WeaponProcMode.NONE;

		final boolean isEdoryu = (weaponType == 54);
		if (!isEdoryu) return WeaponProcMode.NONE;

		if (weaponId == 203018) {
			weaponDoubleDmgChance += pc.getWeapon().getEnchantLevel();
		}

		if (weaponDoubleDmgChance <= 0) return WeaponProcMode.NONE;
		if (weaponDoubleDmgChance > 100) weaponDoubleDmgChance = 100;

		return (rnd100() <= weaponDoubleDmgChance) ? WeaponProcMode.DUAL_DOUBLE : WeaponProcMode.NONE;
	}

    public static boolean isProcBurningOrElementalFire(final L1PcInstance pc,
            final boolean ranged, final boolean hasBurningBuff) {
        return isProcBurningOrElementalFire(pc, ranged, hasBurningBuff, AttackSkillChanceConfig.get());
    }

    private static boolean isProcBurningOrElementalFire(final L1PcInstance pc,
            final boolean ranged, final boolean hasBurningBuff,
            final AttackSkillChanceConfig.Settings rates) {
		if (!hasBurningBuff || pc == null) return false;

		if (ranged) return false;
				
		final boolean hasBlowSkill = pc.hasSkillEffect(L1SkillId.BLOW_ATTACK);
		// 근거리: 블로우어택 우선, 아니면 기본 33%
		
		if (pc.isKnight() && hasBlowSkill) {
			return rollLevelChance(pc.getLevel(), rates.blowAttackStartLevel(),
                    rates.blowAttackBaseChance(), rates.blowAttackPerLevel());
		}
		return rnd100() <= rates.burningChance();
	}

	/**
	 * startLevel에서 base% 시작, startLevel+1부터 perLevelInc%씩 증가
	 * 예) start=75, base=5, inc=1 => 75:5, 76:6, 77:7 ...
	 */
	private static boolean rollLevelChance(final int level,
			final int startLevel,
			final int base,
			final int perLevelInc) {
		int chance = base;
		if (level > startLevel) {
			chance += (level - startLevel) * perLevelInc;
		}
		if (chance > 100) chance = 100;
		if (chance <= 0) return false;
		return rnd100() <= chance;
	}

	/**
	 * 치명타(근/원) 판정
	 * - 크로우(58): 캐릭터/무기 치명타 독립 합집합 확률을 한 번 추첨
	 * - 이도류(54): 근치명은 별도 판정(weaponDoubleDmgChance 합산 X)
	 */
	public static boolean isProcCritical(
			final L1PcInstance pc,
			final boolean ranged,
			final int weaponType,
			final int weaponDoubleDmgChance
			) {
		if (pc == null) return false;

		int chance;

		if (ranged) {
			final int baseCri = pc.getBaseBowCriByLevelClass();
			final int statCri = CalcStat.원거리치명타(pc.getAbility().getTotalDex());
			final int miscCri = pc.get_missile_critical_rate();
			chance = baseCri + statCri + miscCri;
		} else {
			final int baseCri = pc.getBaseMeleeCriByLevelClass(); 
			final int statCri = CalcStat.근거리치명타(pc.getAbility().getTotalStr());
			final int miscCri = pc.get_melee_critical_rate();
			chance = baseCri + statCri + miscCri;

            if (weaponType == 58) {
                final int basisPoints = criticalChanceBasisPoints(chance, weaponDoubleDmgChance);
                return basisPoints > 0
                        && ThreadLocalRandom.current().nextInt(1, 10001) <= basisPoints;
            }
		}

		if (chance <= 0) return false;
		if (chance > 100) chance = 100;

		return rnd100() <= chance;
	}

	/**
	 * 파이널번 치명타 처리
	 */
	public static int finalBurnCriticalRate(boolean hasFinalBurnPassive, int hpPercent, int level) {
		if (!hasFinalBurnPassive || hpPercent > 70) return 0;

		int rate = 5;
		if (level == 50) {
			rate = 6;
		} else if (level > 50) {
			rate += ((level - 50) / 2);
		}
		if (rate > 10) rate = 10;
		return rate;
	}

	private static int rnd100() {
		return ThreadLocalRandom.current().nextInt(1, 101); // 1..100
	}

    private static boolean isProcCyclone(final L1PcInstance pc, final boolean ranged,
            final boolean hasBurningBuff, final AttackSkillChanceConfig.Settings rates) {
		if (!hasBurningBuff || pc == null) return false;
		if (!ranged) return false;
		final boolean hasBlowSkill = pc.hasSkillEffect(L1SkillId.CYCLONE);		
		return pc.isElf() && hasBlowSkill && rollLevelChance(pc.getLevel(),
                rates.cycloneStartLevel(), rates.cycloneBaseChance(), rates.cyclonePerLevel());
	}
	
    /** Exact 0.01 percentage points; one roll for the independent union. */
    public static int criticalChanceBasisPoints(int characterChance, int weaponChance) {
        final int pc = Math.max(0, Math.min(100, characterChance));
        final int pw = Math.max(0, Math.min(100, weaponChance));
        return 10000 - (100 - pc) * (100 - pw);
    }

	public static boolean isSoulOfFlameMax(final L1PcInstance pc, final boolean ranged) {
	    if (pc == null) return false;
	    if (ranged) return false;
	    return pc.getWeapon() != null && pc.hasSkillEffect(L1SkillId.SOUL_OF_FLAME);
	}
	
	public static boolean hasCrash(int flags) { return (flags & FLAG_CRASH) != 0; }
	public static boolean hasFury(int flags)  { return (flags & FLAG_FURY)  != 0; }

	/** 크래쉬/퓨리 발동 롤 (근접 전용이면 ranged 체크) */
	public static int rollCrashFuryFlags(final L1PcInstance pc, final boolean ranged) {
	    if (pc == null) return 0;
	    if (ranged) return 0;

	    if (pc.getPassive(MJPassiveID.CRASH.toInt()) == null) return 0;

        int flags = 0;
        final AttackSkillChanceConfig.Settings rates = AttackSkillChanceConfig.get();

        if (rnd100() <= rates.crashChance()) {
	        flags |= FLAG_CRASH;
            // Fury is conditional on Crash, as in src22.
            if (pc.getPassive(MJPassiveID.FURY.toInt()) != null) {
                if (rnd100() <= rates.furyChance()) {
	                flags |= FLAG_FURY;
	            }
	        }
	    }
	    return flags;
	}
	
	public static int crashDamageByLevel(final int level) {
	    if (level <= 0) return 0;
	    return level / 2;
	}
}
