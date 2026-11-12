package l1j.server.server.model.character.Attack.Helper;


import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.utils.CalcStat;

public final class L1AttackHelper {

	private L1AttackHelper() {}

	/** 앱솔 블레이드가 켜져있으면 앱솔 배리어 파괴 시도 */
	public static void tryBreakAbsoluteBarrier(final L1PcInstance attackerPc,
			final int weaponType,
			final L1PcInstance targetPc) {

		if (attackerPc == null || targetPc == null) return;

		if (!attackerPc.hasSkillEffect(L1SkillId.ABSOLUTE_BLADE)) return;
		if (!targetPc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) return;

		// 80렙부터 증가 (80->1 ... 87->8)
		final int lvlDelta = attackerPc.getLevel() - 79;
		if (lvlDelta <= 0) return;

		final boolean twoHand = isTwoHandSwordType(weaponType);

		int chance = twoHand ? (lvlDelta << 1) : lvlDelta;
        final AttackSkillChanceConfig.Settings rates = AttackSkillChanceConfig.get();
        chance = Math.min(chance, twoHand ? rates.absoluteBladeTwoHandCap() : rates.absoluteBladeOneHandCap());

		if (ThreadLocalRandom.current().nextInt(1, 101) > chance) return;

		// 중복 호출/경합 방지용 재확인
		if (!targetPc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) return;

		targetPc.removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);

		l1j.server.server.model.network.core.sc.packet.SC_SkillSound.send(targetPc, targetPc.getId(), 14539);
		l1j.server.server.model.network.core.sc.packet.SC_SkillSound.broadcastFast(targetPc, targetPc.getId(), 14539);
	}

	private static boolean isTwoHandSwordType(final int weaponType) {
		return weaponType == 50;
	}

	// 마안 3종 중 하나라도 있으면, 10% 확률로 회피 +10%p (hitChance -0.10)
	private static final double MAAN_EVADE_BONUS = 0.10;

	/** 마안(3종) 발동 시 회피 보너스(+%p). 없으면 0.0 */
	public static double rollMaanEvadeBonus(final L1PcInstance targetPc) {
		if (targetPc == null) return 0.0;
		if (!hasAnyMaan(targetPc)) return 0.0;
		return (ThreadLocalRandom.current().nextInt(1, 101) <= AttackSkillChanceConfig.get().maanEvadeChance()) ? MAAN_EVADE_BONUS : 0.0;
	}

	private static boolean hasAnyMaan(final L1PcInstance pc) {
		return pc.hasSkillEffect(L1SkillId.ANTA_MAAN)
				|| pc.hasSkillEffect(L1SkillId.BIRTH_MAAN)
				|| pc.hasSkillEffect(L1SkillId.SHAPE_MAAN);
	}

	public static int weaponRangeByType(final int type1) {
		if (type1 == 24) return 3;                 // 창 2칸
		if (type1 == 20 || type1 == 62) return 15; // 활/스팅 15칸
		return 2;                                  // 일반 근접 1칸
	}

	public static int calcPcHitH(
			final L1PcInstance pc,
			final boolean isRanged,
			final int weaponAddHit,
			final int weaponEnchant,
			final int durabilityLoss
			) {
		if (pc == null) return 0;

		// 공통: 무기 명중 + 인챈/2
		final long wpnPart = (long) weaponAddHit + (weaponEnchant >> 1);

		// 중량(Weight%) 명중 패널티
		final int weightPct = pc.getInventory().getWeight100();
		final int weightPenalty = weightHitPenalty(weightPct);

		// 손상치(내구) 패널티
		final int lossPenalty = Math.max(0, durabilityLoss);

		int baseHit;
		int statHit;
		long miscHit;

		if (isRanged) {
			baseHit = pc.getBaseBowHitByLevelClass();
			statHit = CalcStat.원거리명중(pc.getAbility().getTotalDex());
			miscHit = (long) pc.getBowHitup() + pc.getBowHitRate();
		} else {
			baseHit = pc.getBaseMeleeHitByLevelClass();
			statHit = CalcStat.근거리명중(pc.getAbility().getTotalStr());
			miscHit = (long) pc.getHitup() + pc.getHitRate();
		}

		// 최종 H
		long H = (long) baseHit + statHit + miscHit + wpnPart - weightPenalty - lossPenalty;

		if (H < 0) H = 0;
		if (H > 300) H = 300;

		return (int) H;
	}

	public static int calcNpcHitH(final L1NpcInstance npc, final boolean isRanged) {

		if (npc == null) return 0;

		final int baseHit = npc.getLevel();	   
		if (isRanged) {
			final int statHit = CalcStat.원거리명중(npc.getAbility().getTotalDex());
			final int miscHit = npc.getBowHitup();
			return baseHit + statHit + miscHit;
		} else {
			final int statHit = CalcStat.근거리명중(npc.getAbility().getTotalStr());
			final int miscHit = npc.getHitup();
			return baseHit + statHit + miscHit; 
		}
	}

	public static boolean shouldEvadeByMoebius(final L1Character target, final boolean ranged) {
		if (!ranged) return false;
		if (!(target instanceof L1PcInstance)) return false;
		return ((L1PcInstance) target).hasSkillEffect(L1SkillId.MOEBIUS);
	}

	/** PC 무기 type1 기준 원거리 판정 */
	public static boolean isRangedByType1(final int type1) {
		return type1 == 20 || type1 == 62;
	}

	/** NPC template ranged 기준 원거리 판정(너 코드 기준: >5면 원거리 취급) */
	public static boolean isRangedNpc(final int npcRangedValue) {
		return npcRangedValue > 5;
	}

	public static int weightHitPenalty(final int weightPercent) {
		// 0~100 가정
		// 34~49 -> 1, 50~66 -> 3, 67~100 -> 5
		if (weightPercent >= 67) return 5;
		if (weightPercent >= 50) return 3;
		if (weightPercent >= 34) return 1;
		return 0;
	}

	public static boolean isAttackableNpcTarget(final L1NpcInstance targetNpc) {
		if (targetNpc == null) return false;

		if (targetNpc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE)
			return false;

		if (targetNpc.getNpcTemplate().get_npcId() == 8500138)
			return false;

		return true;
	}

	public static boolean isSafeZoneAttackBlocked(L1PcInstance atk, L1Character target) {
		if (atk == null || target == null) return false;

		final int atkZone = atk.getZoneType();
		final int tarZone = target.getZoneType();

		return (atkZone == 1 && (tarZone == 0 || tarZone == -1));
	}

	public static boolean isSlayerDualAxe(L1PcInstance pc, L1ItemInstance left, L1ItemInstance right) {
		if (pc == null || left == null || right == null) return false;

		// 전사 + 슬레이어 패시브
		if (!pc.isWarrior()) return false;
		if (!pc.isPassive(MJPassiveID.SLAYER.toInt())) return false;

		if (left.getItem() == null || right.getItem() == null) return false;

		// type1==11, type==6 (한손도끼)
		return left.getItem().getType1() == 11
				&& right.getItem().getType1() == 11
				&& left.getItem().getType()  == 6
				&& right.getItem().getType() == 6;
	}


	public static int rollMaanPhysicalBonusDamage(final L1PcInstance pc,
			final boolean ranged,
			final int valaMaanSkillId,
			final int lifeMaanSkillId,
			final int chancePercent,
			final int bonusDamage) {
		if (pc == null) return 0;
		if (ranged) return 0;
		if (!pc.hasSkillEffect(valaMaanSkillId) && !pc.hasSkillEffect(lifeMaanSkillId)) {
			return 0;
		}

		int c = chancePercent;
		if (c <= 0) return 0;
		if (c > 100) c = 100;

		final int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1..100
		return (roll <= c) ? bonusDamage : 0;
	}

	public static int rollMaanPhysicalBonusDamage(final L1PcInstance pc,
			final boolean ranged,
			final int valaMaanSkillId,
			final int lifeMaanSkillId) {
		return rollMaanPhysicalBonusDamage(pc, ranged, valaMaanSkillId, lifeMaanSkillId,
                AttackSkillChanceConfig.get().maanDamageChance(), 2);
	}

	public static int consumeBurningSlashAddDamage(final L1PcInstance attacker, final boolean ranged) {
		if (attacker == null) return 0;
		if (ranged) return 0;
		if (!attacker.isDragonknight()) return 0;
		if (!attacker.hasSkillEffect(L1SkillId.BURNING_SLASH)) return 0;

		attacker.removeSkillEffect(L1SkillId.BURNING_SLASH);
		return 7;
	}

	/**
	 * 타겟 PC 기반 최종 배율(트루타겟/아바타/아머브레이크)
	 * @return 배율 적용된 dmg
	 */
	public static int applyTargetPcDamageMultipliers(final L1Character attacker,
			final L1PcInstance targetPc,
			final boolean ranged,
			int dmg) {
		if (attacker == null || targetPc == null) return dmg;
		if (dmg <= 0) return dmg;

        dmg = applyArmorBreak(attacker, targetPc, ranged, dmg);
        return applyTargetPcStatusMultipliers(targetPc, dmg);
    }

    /** The single Armor Break owner. Existing caster/other and ranged policies
     * remain intact; the PC physical caller supplies the complete pre-DR hit. */
    public static int applyArmorBreak(L1Character attacker, L1PcInstance targetPc,
                                      boolean ranged, int damage) {
        if (attacker == null || targetPc == null || ranged || damage <= 0
                || !targetPc.hasSkillEffect(L1SkillId.ARMOR_BRAKE)) return damage;
        final boolean caster = attacker instanceof L1PcInstance
                && attacker.hasSkillEffect(L1SkillId.ARMOR_BRAKE_ATTACKER);
        final long scaled = (long) damage * (caster ? 158L : 120L) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    public static int applyTargetPcStatusMultipliers(L1PcInstance targetPc, int dmg) {
        if (targetPc == null || dmg <= 0) return dmg;

		// 트루타겟: tt가 %라고 가정 (예: 10이면 10%)
		final int tt = targetPc.getTrueTarget();
		if (tt > 0) {
			dmg = (int) (dmg * (1.0 + (tt / 100.0)));
		}

		// 일루션/큐브 아바타: 맞는대미지 5% 증가
		if (targetPc.hasSkillEffect(L1SkillId.IllUSION_AVATAR)
				|| targetPc.hasSkillEffect(L1SkillId.CUBE_AVATAR)) {
			dmg = (int) (dmg * 1.05);
		}

		return dmg;
	}

	public static int applyAvatarPhysicalAtkPlus10(final L1PcInstance attackerPc, int dmg) {
		if (dmg <= 0) return dmg;
		if (attackerPc == null) return dmg;
		if (attackerPc.hasSkillEffect(L1SkillId.IllUSION_AVATAR)
				|| attackerPc.hasSkillEffect(L1SkillId.CUBE_AVATAR)) {
			dmg += 10; // 물리공격력 +10 -> 고정 대미지 +10
		}
		return dmg;
	}

	public static boolean applyInvalidHitRule(final L1Character targetPc) {
		if (targetPc == null) return false;
		return targetPc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
	}

	public static int applyInvalidDamageRule(final L1PcInstance targetPc, int dmg) {
		if (dmg <= 0) return 0;
		if (targetPc == null) return dmg;
		return targetPc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER) ? 0 : dmg;
	}

	public static boolean isUndeadDamage(final L1NpcInstance npc) {
		if (npc == null) return false;
		if (npc.getNpcTemplate() == null) return false;

		final int undead = npc.getNpcTemplate().get_undead();
		if (undead != 1 && undead != 3) return false;

		return L1GameTimeClock.getInstance()
				.getGameTime()
				.isNight();
	}
	public static boolean blockPetSummonCompanionHitInSafeZone(L1Character attacker, L1PcInstance targetPc) {
		if (attacker == null || targetPc == null) return false;
		if (targetPc.getZoneType() != 1) return false;
		return isPetSummonOrCompanion(attacker);
	}

	public static boolean isPetSummonOrCompanion(L1Character attacker) {
		return (attacker instanceof L1PetInstance)
				|| (attacker instanceof L1SummonInstance)
				|| (attacker instanceof L1CompanionInstance);
	}


}
