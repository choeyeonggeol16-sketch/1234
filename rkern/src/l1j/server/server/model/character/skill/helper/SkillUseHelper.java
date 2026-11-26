package l1j.server.server.model.character.skill.helper;

import static l1j.server.server.model.character.skill.L1SkillId.*;

import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.world.trap.L1WorldTraps;
import l1j.server.server.templates.L1Skills;

public final class SkillUseHelper {
	private SkillUseHelper() {}

	private static final int LOW_MAX = 8192; 

	// =========================
	// Hybrid Skill Sets
	// =========================
	private static final SkillIdSet S_CAST_WITH_INVIS = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_EXCEPT_COUNTER_MAGIC = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_ICE_LANCE_BLOCK = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_ABSOLUTE_BARRIER_ALLOW = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_NPC_SINK_ALLOW = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_ERASE_ATK_EXCEPT = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_RECAST_SKIP_REFRESH = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_RECAST_EXCEPTION = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_BLOCKED_CASTLE_WAR = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_BLOCKED_SAFETY_ZONE = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_NOT_CANCELABLE = new SkillIdSet(LOW_MAX);

	// =========================================================
	// Skill groups (fast lookup)
	// =========================================================
	private static final SkillIdSet GROUPED_STUN_SKILLS = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet SHOCK_STUN_DURATION_SKILLS = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet STUN_LIKE_SKILLS = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_BLOCKED_ZONE_TYPE1 = new SkillIdSet(LOW_MAX);
	
	private static final SkillIdSet S_USE_SKILL_SILENCE = new SkillIdSet(LOW_MAX);

	// =========================
	// Magic stack / commit skip
	// =========================
	private static final SkillIdSet S_COMMIT_SKIP_SKILLS = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_MAGIC_STACK_EXCEPT = new SkillIdSet(LOW_MAX);

	private static final SkillIdSet S_SKILL_BLOCK = new SkillIdSet(LOW_MAX);

	// ===== Magic probability related groups =====
	private static final SkillIdSet S_EARTH_BIND_PC_ALLOW = new SkillIdSet(LOW_MAX);
	private static final SkillIdSet S_BOSS_BLOCKED_SKILL = new SkillIdSet(LOW_MAX);

	private static final int[] SCALE_SKILLS = {
			SCALES_EARTH_DRAGON,
			SCALES_WATER_DRAGON,
			SCALES_FIRE_DRAGON,
			SCALES_RINDVIOR_DRAGON,
	};

	// ( “중복 버프 제거” 그룹 그대로)
	private static final int[][] REPEAT_GROUPS = {
			{ EARTH_WEAPON, AQUA_SHOT, STORM_EYE, BURNING_WEAPON, STORM_SHOT },                 // 0
			{ SHIELD, FIRE_SHIELD, IRON_SKIN },                                                // 1
			{ HOLY_WALK, BLOOD_LUST, MOVING_ACCELERATION, STATUS_BRAVE, STATUS_ELFBRAVE,
			  FOCUS_WAVE, HURRICANE, SAND_STORM, DANCING_BLADES },                             // 2
			{ HASTE, GREATER_HASTE, STATUS_HASTE },                                            // 3
			{ PHYSICAL_ENCHANT_DEX, DRESS_DEXTERITY },                                         // 4
			{ PHYSICAL_ENCHANT_STR, DRESS_MIGHTY },                                            // 5
			{ COUNTER_MIRROR, DECREASE_WEIGHT, REDUCE_WEIGHT },                                // 6
			{ FAFU_MAAN, ANTA_MAAN, LIND_MAAN, VALA_MAAN, LIFE_MAAN, BIRTH_MAAN, SHAPE_MAAN }, // 7
			{ PAP_FIVEPEARLBUFF, PAP_MAGICALPEARLBUFF },                                       // 8
			{ DRAGON_HUNTER_BLESS, HUNTER_BLESS, HUNTER_BLESS1},                               // 9
			{ EXP_BUFF, EXP_POTION },                                                          // 10
			{ ADVANCE_SPIRIT, GIGANTIC, PRIDE },                                               // 11
			{ DRAGON_SET },                                                                    // 12
			{ CUBE_OGRE, IllUSION_OGRE },                                                      // 13
			{ CUBE_AVATAR, IllUSION_AVATAR },                                                  // 14
			{ CUBE_GOLEM, IllUSION_DIAMONDGOLEM },                                             // 15
			{ CUBE_RICH, IllUSION_LICH },                                                      // 16
	};

	static {
		// =========================
		// (1) 인비 상태에서 스크롤/스킬 사용 가능 목록
		// =========================
		int[] castWithInvis = {
				L1SkillId.HEAL, L1SkillId.LIGHT, L1SkillId.SHIELD, L1SkillId.TELEPORT, 
				L1SkillId.HOLY_WEAPON, L1SkillId.CURE_POISON, L1SkillId.ENCHANT_WEAPON,
				L1SkillId.DETECTION, L1SkillId.DECREASE_WEIGHT, L1SkillId.EXTRA_HEAL, 
				L1SkillId.BLESSED_ARMOR, L1SkillId.PHYSICAL_ENCHANT_STR,
				L1SkillId.COUNTER_MAGIC, L1SkillId.MEDITATION, L1SkillId.GREATER_HEAL,
				L1SkillId.HASTE, L1SkillId.IMMUNE_TO_HARM, L1SkillId.MOVING_ACCELERATION,
				L1SkillId.PHYSICAL_ENCHANT_DEX, L1SkillId.HEAL_ALL, L1SkillId.HOLY_WALK, 
				L1SkillId.GREATER_HASTE, L1SkillId.BERSERKERS, L1SkillId.CANCELLATION,
				L1SkillId.FULL_HEAL, L1SkillId.LIFE_STREAM, L1SkillId.MASS_TELEPORT,
				L1SkillId.COUNTER_DETECTION, L1SkillId.ABSOLUTE_BARRIER, L1SkillId.ADVANCE_SPIRIT
		};
		S_CAST_WITH_INVIS.addAll(castWithInvis);

		// =========================
		// (2) 카운터매직 예외
		// =========================
		int[] exceptCounter = {
				1,2,3,5,8,9,12,13,14,19,21,26,31,32,35,37,42,43,44,48,49,52,54,55,57,60,61,
				63,67,68,69,72,73,75,78,79, EMPIRE,SHOCK_STUN, ARMOR_BRAKE,REDUCTION_ARMOR, BOUNCE_ATTACK, SOLID_CARRIAGE, COUNTER_BARRIER, 97,98,99,100,101,102,104,
				105,106,107,109,110,111,113,114,115,116,117,118,129,130,131,132,134,137,138,146,147,148,149,150,151,155,156,158,159,
				161,163,164,165,166,168,169,170,171,181, SOUL_OF_FLAME, ADDITIONAL_FIRE, FOU_SLAYER, SCALES_EARTH_DRAGON, SCALES_FIRE_DRAGON, DRAGON_SKIN,
				SCALES_WATER_DRAGON, MIRROR_IMAGE, IllUSION_OGRE, SCALES_RINDVIOR_DRAGON, PATIENCE, IllUSION_DIAMONDGOLEM, IllUSION_LICH, IllUSION_AVATAR, INSIGHT,
				SHAPE_CHANGE, 10026,10027,10028,10029, 30060,30000,30078,30079,30011,30081,30082,30083,30080,30084,30010,30002,30086,
				OMAN_CANCELLATION, ANTA_MESSAGE_2, ANTA_MESSAGE_3, ANTA_MESSAGE_4, ANTA_MESSAGE_5, ANTA_MESSAGE_6, ANTA_MESSAGE_7, ANTA_MESSAGE_8, ANTA_MESSAGE_10,
				22034, OMAN_STUN, Maeno_STUN, PAP_PREDICATE1, PAP_PREDICATE3, PAP_PREDICATE5, PAP_PREDICATE6, PAP_PREDICATE7, PAP_PREDICATE8, PAP_PREDICATE9,
				PAP_PREDICATE11, PAP_PREDICATE12, DESPERADO, POWERRIP, INFERNO, MAJESTY
		};
		S_EXCEPT_COUNTER_MAGIC.addAll(exceptCounter);

		// =========================
		// (3) ICE_LANCE / ABSOLUTE_BARRIER / NPC_SINK
		// =========================
		int[] iceBlock = {
				SHOCK_STUN, EMPIRE, DECAY_POTION, WEAPON_BREAK, SLOW, CURSE_PARALYZE,
				MANA_DRAIN, DARKNESS, FOG_OF_SLEEPING, ARMOR_BRAKE, EARTH_BIND,
				WIND_SHACKLE, POLLUTE_WATER, STRIKER_GALE, GUARD_BREAK, FEAR,
				HORROR_OF_DEATH, PANIC, IllUSION_AVATAR, DESPERADO, POWERRIP
		};
		S_ICE_LANCE_BLOCK.addAll(iceBlock);

		int[] abAllow = {
				CURSE_BLIND, WEAPON_BREAK, DARKNESS, WEAKNESS, DISEASE,
				FOG_OF_SLEEPING, SLOW, CANCELLATION, SILENCE, DECAY_POTION,
				MASS_TELEPORT, DETECTION, IZE_BREAK, HORROR_OF_DEATH,
				COUNTER_DETECTION, GUARD_BREAK, ERASE_MAGIC, FEAR,
				PHYSICAL_ENCHANT_DEX, PHYSICAL_ENCHANT_STR, BLESS_WEAPON, IMMUNE_TO_HARM,
				REMOVE_CURSE, CONFUSION,

				Sand_worms, Sand_worms1, Sand_worms2, Sand_worms3,

				MOB_SLOW_1, MOB_SLOW_18, MOB_WEAKNESS_1, MOB_DISEASE_1,
				MOB_BASILL, MOB_SHOCKSTUN_30, MOB_RANGESTUN_19, MOB_RANGESTUN_18,
				MOB_DISEASE_30, MOB_WINDSHACKLE_1, MOB_COCA,
				MOB_CURSEPARALYZ_19, MOB_CURSEPARALYZ_18, MOB_CURSEPARALYZ1,
				Mob_RANGESTUN_30,

				ANTA_MESSAGE_1, ANTA_MESSAGE_2, ANTA_MESSAGE_3, ANTA_MESSAGE_4, ANTA_MESSAGE_5,
				ANTA_MESSAGE_6, ANTA_MESSAGE_7, ANTA_MESSAGE_8, ANTA_MESSAGE_9, ANTA_MESSAGE_10,

				OMAN_STUN, Maeno_STUN, OMAN_CANCELLATION
		};
		S_ABSOLUTE_BARRIER_ALLOW.addAll(abAllow);

		int[] sinkAllow = { DETECTION, IZE_BREAK, EYE_OF_DRAGON, COUNTER_DETECTION };
		S_NPC_SINK_ALLOW.addAll(sinkAllow);

		// =========================
		// (4) 공격스킬일 때 ERASE_MAGIC 제거 예외
		// =========================
		S_ERASE_ATK_EXCEPT.addAll(SHOCK_STUN, TRIPLE_ARROW, FOU_SLAYER, EMPIRE, DESPERADO);

		// =========================
		// (5) 재시전 “시간 재갱신 제외”
		// =========================
		int[] skipRefresh = {
				STRIKER_GALE, GUARD_BREAK, EAGGLE_EYE, FOCUS_SPRITS, LUCIFER, CUBE_OGRE, CUBE_GOLEM, CYCLONE,
				CUBE_RICH, CUBE_AVATAR, IllUSION_OGRE, IllUSION_LICH, GIGANTIC,
				IllUSION_DIAMONDGOLEM, IllUSION_AVATAR, IMMUNE_TO_HARM,
				BLOW_ATTACK, PRIDE, MAJESTY, CLEAR_MIND, FREEZEENG_ARMOR,
				SHINING_ARMOR, ENCHANT_ACURUCY, SOLID_CARRIAGE, REDUCTION_ARMOR,
				DRESS_EVASION, ADVANCE_SPIRIT, SHADOW_FANG,
				HOLY_WEAPON, AQUA_PROTECTER, BLESS_WEAPON, ENCHANT_WEAPON, BLESSED_ARMOR
		};
		S_RECAST_SKIP_REFRESH.addAll(skipRefresh);

		// =========================
		// (6) 재시전 예외(걸려 있어도 continue 안 함)
		// =========================
		int[] recastExcept = {
				SHOCK_STUN, BONE_BREAK, THUNDER_GRAB, EMPIRE, OMAN_STUN, Maeno_STUN,
				ANTA_MESSAGE_6, ANTA_MESSAGE_7, ANTA_MESSAGE_8,
				DRAGON_HUNTER_BLESS, miso1, miso2, DRAGON_SET, HUNTER_BLESS, HUNTER_BLESS1, miso3,
				ELEMENTAL_FALL_DOWN, FOCUS_WAVE, 성혈버프, DESPERADO
		};
		S_RECAST_EXCEPTION.addAll(recastExcept);

		// =========================
		// (7) 공성/세이프존 차단
		// =========================
		S_BLOCKED_CASTLE_WAR.addAll(50, 69, 157, 66, 78, 116);
		
		S_BLOCKED_SAFETY_ZONE.addAll(69, 73, 66, 11, 123, 208, 67, 112);

		// =========================
		// (8) 캔슬로 해제 불가 스킬
		// =========================
		int[] notCancelable = {
				ABSOLUTE_BARRIER, ADVANCE_SPIRIT, SHOCK_STUN, REDUCTION_ARMOR, SOLID_CARRIAGE,
				COUNTER_BARRIER, COMA_A, COMA_B, ANTA_MAAN, FAFU_MAAN, MAJESTY,
				EXP_BUFF, LIND_MAAN, VALA_MAAN, BIRTH_MAAN, SHAPE_MAAN, IllUSION_LICH, IllUSION_DIAMONDGOLEM,
				LIFE_MAAN, ANTA_BUFF, FAFU_BUFF, ANTA_MESSAGE_6, ANTA_MESSAGE_7,
				ANTA_MESSAGE_8, PREDICATEDELAY, FEATHER_BUFF_A, FEATHER_BUFF_B,
				FEATHER_BUFF_C, FEATHER_BUFF_D, PAP_DEATH_PORTION, PAP_DEATH_HELL,
				PAP_REDUCE_HELL, STATUS_DRAGON_PEARL, UNCANNY_DODGE, DRESS_EVASION,
				SHADOW_ARMOR, OMAN_STUN, Maeno_STUN, SCALES_EARTH_DRAGON,
				SCALES_WATER_DRAGON, SCALES_FIRE_DRAGON, SCALES_RINDVIOR_DRAGON,
				COOK_STR, COOK_DEX, COOK_INT, COOK_GROW,
				COOK_STR_12, COOK_STR_1, COOK_DEX_1, COOK_INT_1, COOK_GROW_1,
				ARMOR_BRAKE, SHADOW_FANG, CLANBUFF_YES,
				God_buff, DESPERADO, MIRROR_IMAGE, TOP_RANKER,
				정상의가호, GIGANTIC, 메티스정성스프, 메티스정성요리, 메티스축복주문서,
				POLY_RING_MASTER, RANK_BUFF_1, RANK_BUFF_2, RANK_BUFF_3, RANK_BUFF_4,
				RANK_BUFF_5, RANK_BUFF_6, RANK_BUFF_7, RANK_BUFF_8, RANK_BUFF_9,
				RANK_BUFF_10_STR, RANK_BUFF_10_DEX, RANK_BUFF_10_INT, RANK_BUFF_11_STR,
				RANK_BUFF_11_DEX, RANK_BUFF_11_INT
		};
		S_NOT_CANCELABLE.addAll(notCancelable);
		
		int[] useSkillSilence = {
		        L1SkillId.SHOCK_STUN,
		        L1SkillId.REDUCTION_ARMOR,
		        L1SkillId.BOUNCE_ATTACK,
		        L1SkillId.SOLID_CARRIAGE,
		        L1SkillId.COUNTER_BARRIER,
		        L1SkillId.PRIDE,
		        L1SkillId.BLOW_ATTACK,
		        L1SkillId.HOWL,
		        L1SkillId.POWERRIP,
		        L1SkillId.DESPERADO,
		        L1SkillId.TOMAHAWK,
		        L1SkillId.GIGANTIC,
		        L1SkillId.TITANL_RISING,
		        L1SkillId.ABSOLUTE_BLADE,
		        L1SkillId.BURNING_SLASH,
		        L1SkillId.FOU_SLAYER
		};
		S_USE_SKILL_SILENCE.addAll(useSkillSilence);

		GROUPED_STUN_SKILLS.addAll(
				MOB_SHOCKSTUN_30,
				MOB_RANGESTUN_20,
				MOB_RANGESTUN_19,
				MOB_RANGESTUN_18,
				Mob_RANGESTUN_30,
				ANTA_MESSAGE_6,
				ANTA_MESSAGE_7,
				ANTA_MESSAGE_8,
				OMAN_STUN,
				Maeno_STUN
		);

		SHOCK_STUN_DURATION_SKILLS.addAll(
				DESPERADO,
				SHOCK_STUN,
				ANTA_MESSAGE_6,
				ANTA_MESSAGE_7,
				ANTA_MESSAGE_8,
				ANTA_SHOCKSTUN,
				OMAN_STUN,
				Maeno_STUN,
				EMPIRE,
				BONE_BREAK
		);

		STUN_LIKE_SKILLS.addAll(
				DESPERADO,
				SHOCK_STUN,
				EMPIRE,
				BONE_BREAK,
				EARTH_BIND,
				ANTA_MESSAGE_6,
				ANTA_MESSAGE_7,
				ANTA_MESSAGE_8,
				ANTA_SHOCKSTUN,
				OMAN_STUN,
				Maeno_STUN
		);

		// =========================
		// (9) commit 예외 + 마법중첩 제외(공격마법에서 제외할 물리/특수 스킬들)
		// =========================
		S_COMMIT_SKIP_SKILLS.addAll(
				FOU_SLAYER,
				TOMAHAWK,
				SHOCK_STUN,
				TRIPLE_ARROW,
				DESPERADO,
				BONE_BREAK,
				EMPIRE
		);

		S_MAGIC_STACK_EXCEPT.addAll(
				FOU_SLAYER,
				TOMAHAWK,
				SHOCK_STUN,
				TRIPLE_ARROW,
				DESPERADO,
				BONE_BREAK,
				EMPIRE
		);

		S_SKILL_BLOCK.addAll(
				EARTH_BIND,
				ICE_LANCE,
				MOB_BASILL,
				MOB_COCA,
				BONE_BREAK,
				SHOCK_STUN,
				EMPIRE,
				MOB_SHOCKSTUN_30,
				Mob_RANGESTUN_30,
				MOB_RANGESTUN_20,
				MOB_RANGESTUN_19,
				MOB_RANGESTUN_18,
				ANTA_SHOCKSTUN,
				OMAN_STUN,
				Maeno_STUN
		);

		// =========================
		// (10) EARTH_BIND 중 PC에게 허용되는 스킬
		// =========================
		S_EARTH_BIND_PC_ALLOW.addAll(
				WEAPON_BREAK,
				CANCELLATION,
				EXTRA_HEAL,
				HEAL,
				GREATER_HEAL,
				HEAL_ALL,
				FULL_HEAL,
				NATURES_BLESSING,
				MANA_DRAIN,
				CURSE_PARALYZE,
				THUNDER_GRAB,
				ERASE_MAGIC,
				SHOCK_STUN,
				EARTH_BIND,
				BONE_BREAK,
				EMPIRE
		);

		// =========================
		// (11) 보스급(특정 조건) NPC에게 무효인 스킬
		// =========================
		S_BOSS_BLOCKED_SKILL.addAll(
				WEAPON_BREAK,
				SLOW,
				CURSE_PARALYZE,
				MANA_DRAIN,
				WEAKNESS,
				DISEASE,
				DECAY_POTION,
				DESPERADO,
				QUAKE,
				ERASE_MAGIC,
				AREA_OF_SILENCE,
				EMPIRE,
				WIND_SHACKLE,
				STRIKER_GALE,
				SHOCK_STUN,
				FOG_OF_SLEEPING,
				ICE_LANCE,
				POLLUTE_WATER,
				ELEMENTAL_FALL_DOWN,
				RETURN_TO_NATURE,
				THUNDER_GRAB,
				ARMOR_BRAKE,
				DARKNESS
		);
		
		
	}

	// =========================================================
	// exported helpers
	// =========================================================
	public static boolean isEmpireSkill(int skillId) {
		return skillId == EMPIRE;
	}

	public static boolean isGroupedStunSkill(int skillId) {
		return GROUPED_STUN_SKILLS.contains(skillId);
	}

	public static boolean isShockStunDurationSkill(int skillId) {
		return SHOCK_STUN_DURATION_SKILLS.contains(skillId);
	}

	public static boolean isStunLikeSkill(int skillId) {
		return STUN_LIKE_SKILLS.contains(skillId);
	}

	public static boolean isBlockedInZoneType1(int skillId) {
		return S_BLOCKED_ZONE_TYPE1.contains(skillId);
	}
	private static final int[] INVIS_EQUIP_ITEM_IDS = {
			20077, 20062, 120077
		};

	public static void unequipInvisItemsIfAny(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		final CUserInventory inv = pc.getInventory();
		if (inv == null) {
			return;
		}

		for (int i = 0; i < INVIS_EQUIP_ITEM_IDS.length; i++) {
			final L1ItemInstance item =
					inv.GetFirstEquippedItemByClassID(INVIS_EQUIP_ITEM_IDS[i]);

			if (item != null) {
				pc.UnequipItem(item);
			}
		}
	}


	/**
	 * 내가 직접 디텍해서 내 투망을 해제한 경우.
	 * 텍피 방지를 위해 3초 invisDelay 적용.
	 */
	public static void detectionSelf(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		if (pc.isInvisble() && !pc.isGhost()) {
			unequipInvisItemsIfAny(pc);

			pc.delInvis();

			pc.beginInvisTimer();
		}
	}

	/**
	 * 내가 디텍을 사용해서 주변 다른 사람의 투망을 해제.
	 * 대상에게는 invisDelay를 걸지 않는다.
	 * 즉시 공격/스킬 가능.
	 */
	public static void detectionAround(L1PcInstance pc) {
		if (pc == null) return;

		final L1World world = L1World.getInstance();

		for (L1PcInstance tgt : world.getVisiblePlayer(pc)) {
			if (tgt == null) continue;

			if (tgt.getId() == pc.getId()) continue;

			if (!tgt.isGmInvis()
					&& tgt.isInvisble()
					&& !tgt.isGhost()) {

				unequipInvisItemsIfAny(tgt);
				tgt.delInvis();
				tgt.clearInvisDelay();
			}
		}

		L1WorldTraps.getInstance().onDetection(pc);
	}
	public static void detection(L1PcInstance pc, boolean detectAll) {
		if (pc == null) return;

		detectionSelf(pc);
		if (detectAll) detectionAround(pc);
	}

	/** 캔슬로 해제할 수 없는 스킬인지를 돌려준다. */
	public static boolean isNotCancelable(int skillNum) { return S_NOT_CANCELABLE.contains(skillNum); }

	// =========================
	// exposed queries
	// =========================
	public static boolean canCastWithInvis(int skillId) { return S_CAST_WITH_INVIS.contains(skillId); }
	public static boolean isExceptCounterMagic(int skillId) { return S_EXCEPT_COUNTER_MAGIC.contains(skillId); }

	public static boolean isIceLanceBlockedDebuff(int skillId) { return S_ICE_LANCE_BLOCK.contains(skillId); }
	public static boolean isAbsoluteBarrierAllowed(int skillId) { return S_ABSOLUTE_BARRIER_ALLOW.contains(skillId); }
	public static boolean isNpcSinkAllowed(int skillId) { return S_NPC_SINK_ALLOW.contains(skillId); }

	public static boolean shouldRemoveEraseOnAttack(int skillId) { return !S_ERASE_ATK_EXCEPT.contains(skillId); }
	public static boolean isRecastSkipRefresh(int skillId) { return S_RECAST_SKIP_REFRESH.contains(skillId); }
	public static boolean isRecastException(int skillId) { return S_RECAST_EXCEPTION.contains(skillId); }

	public static boolean isBlockedInCastleWar(int skillId) { return S_BLOCKED_CASTLE_WAR.contains(skillId); }
	public static boolean isBlockedInSafetyZone(int skillId) { return S_BLOCKED_SAFETY_ZONE.contains(skillId); }

	// =========================
	// actions
	// =========================
	public static void removeEraseMagicIfPresent(L1Character cha) {
		if (cha == null) return;
		if (!cha.hasSkillEffect(ERASE_MAGIC)) return;

		cha.killSkillEffectTimer(ERASE_MAGIC);
		if (cha instanceof L1PcInstance targetPc) {
			SC_PacketBox.send(targetPc, SC_PacketBox.ICON_AURA);
		}
	}

	public static void deleteRepeatedSkills(L1Character cha, int skillId) {
		if (cha == null) return;
		
		if(handleScaleSkillBeforeApply(cha, skillId)) {
			return;
		}

		int[] group = null;
		for (int g = 0; g < REPEAT_GROUPS.length; g++) {
			int[] cand = REPEAT_GROUPS[g];
			for (int i = 0; i < cand.length; i++) {
				if (cand[i] == skillId) { group = cand; break; }
			}
			if (group != null) break;
		}
		if (group == null) return;

		for (int i = 0; i < group.length; i++) {
			final int other = group[i];
			if (other == skillId) continue;

			if (cha.hasSkillEffect(other)) {
				cha.removeSkillEffect(other);
			}
		}
	}
	public static boolean handleScaleSkillBeforeApply(L1Character cha, int applyingSkillId) {
	    if (cha == null) return false;
	    if (!isScaleSkill(applyingSkillId)) return false;

	    final boolean aurakia = cha.isPassive(PassiveID.AURAKIA.toInt());

	    if (!aurakia) {
	        // 다른 스케일은 전부 제거
	        for (int sid : SCALE_SKILLS) {
	            if (sid == applyingSkillId) continue;
	            if (cha.hasSkillEffect(sid)) cha.removeSkillEffect(sid);
	        }
	        return true;
	    }

	    // 아우라키아: 다른 스케일 최대 2개
	    int activeOthers = 0;
	    int removeSid = 0;
	    int minRemain = Integer.MAX_VALUE;

	    for (int sid : SCALE_SKILLS) {
	        if (sid == applyingSkillId) continue;

	        final int remain = cha.getSkillEffectTimeSec(sid);
	        if (remain > 0) {
	            activeOthers++;
	            if (remain < minRemain) { minRemain = remain; removeSid = sid; }
	        }
	    }
	    if (activeOthers >= 2 && removeSid != 0) cha.removeSkillEffect(removeSid);
	    return true;
	}

	private static boolean isScaleSkill(int skillId) {
		for (int i = 0; i < SCALE_SKILLS.length; i++) {
			if (SCALE_SKILLS[i] == skillId) return true;
		}
		return false;
	}

	public static boolean weaponTypeIs(L1ItemInstance weapon, int type) {
	    if (weapon == null || weapon.getItem() == null) return false;
	    return weapon.getItem().getType() == type;
	}

	public static boolean isSpecialOrderSkill(int skillId) {
		return (skillId == L1SkillId.DANCING_BLADES
				|| skillId == L1SkillId.SOLID_CARRIAGE
				|| skillId == L1SkillId.SAND_STORM
				|| skillId == L1SkillId.HURRICANE
				|| skillId == L1SkillId.FOCUS_WAVE);
	}

	/** PC/NPC 공통: 얼림/바인드류면 damage/drain 무효 */
	public static boolean isCommonDamageBlocked(L1Character target) {
		if (target == null) return true;

		if (target.hasSkillEffect(L1SkillId.ICE_LANCE)) return true;
		if (target.hasSkillEffect(L1SkillId.EARTH_BIND)) return true;
		if (target.hasSkillEffect(L1SkillId.MOB_BASILL)) return true;
		if (target.hasSkillEffect(L1SkillId.MOB_COCA)) return true;
		return false;
	}

	/** PC 전용: 앱솔루트 배리어 */
	public static boolean isPcExtraDamageBlocked(L1PcInstance pc) {
		if (pc == null) return true;
		return pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
	}

	public static boolean shouldZeroCommitPc(L1PcInstance targetPc) {
		if (targetPc == null) return true;
		return isPcExtraDamageBlocked(targetPc) || isCommonDamageBlocked(targetPc);
	}

	public static boolean shouldZeroCommitNpc(L1NpcInstance targetNpc) {
		if (targetNpc == null) return true;
		return isCommonDamageBlocked(targetNpc);
	}

	public static boolean shouldZeroFireWallPc(L1PcInstance targetPc) {
		if (targetPc == null) return true;
		if (targetPc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) return true;
		return isCommonDamageBlocked(targetPc);
	}

	public static boolean shouldZeroFireWallNpc(L1NpcInstance targetNpc) {
		if (targetNpc == null) return true;
		return isCommonDamageBlocked(targetNpc);
	}

	public static boolean isCommitSkipSkill(final int skillId) {
		return S_COMMIT_SKIP_SKILLS.contains(skillId);
	}

	/**
	 * "공격마법 시도"에만 마법중첩을 적용한다.
	 * - TYPE_ATTACK 이면서
	 * - 제외(Set)에 없는 것만 true
	 */
	public static boolean isMagicStackableAttack(final int skillId, final int skillType) {
		if (skillType != L1Skills.TYPE_ATTACK) return false;
		return !S_MAGIC_STACK_EXCEPT.contains(skillId);
	}

	public static boolean isSkillBlock(L1Character body) {
		if (body == null) return true;
		return hasAnyEffect(body, S_SKILL_BLOCK);
	}

	/** 등록된 스킬ID(int[] 목록)만 순회 -> BitSet nextSetBit 제거 */
	private static boolean hasAnyEffect(L1Character cha, SkillIdSet set) {
		final int[] ids = set.idsUnsafe();
		for (int i = 0; i < ids.length; i++) {
			if (cha.hasSkillEffect(ids[i])) return true;
		}
		return false;
	}
	
	public static void cancelAbsoluteBarrier(L1PcInstance pc) {
		if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {
			pc.removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
		}
	}

	public static void cancelMoebius(L1PcInstance pc) {
		if (pc.hasSkillEffect(L1SkillId.MOEBIUS)) {
			pc.removeSkillEffect(L1SkillId.MOEBIUS);
		}
	}
	
	public static void cancelMeditation(L1PcInstance pc) {
		if (pc.hasSkillEffect(L1SkillId.MEDITATION)) {
			pc.removeSkillEffect(L1SkillId.MEDITATION);
		}
	}
	

	public static boolean isAllowedWhileEarthBindPc(int skillId) {
		return S_EARTH_BIND_PC_ALLOW.contains(skillId);
	}

	/** 보스급/특정 조건 NPC에게 무효인 스킬이면 true */
	public static boolean isBlockedSkillOnBoss(int skillId) {
		return S_BOSS_BLOCKED_SKILL.contains(skillId);
	}
	
	
	public static final int[] STUN_OFFICIAL = buildWeighted(
			5,  500,   // 0.5초: 5%
			10, 1000,  // 1.0초: 10%
			35, 2000,  // 2.0초: 35%
			35, 3000,  // 3.0초: 35%
			15, 4000   // 4.0초: 15%
			);

	public static final int[] DEPE_OFFICIAL = buildWeighted(
			10,  1000,  // 1.0초: 10% 
			20, 2000,  // 2.0초: 20% 
			40, 3000,  // 3.0초: 40% 
			30, 4000   // 4.0초: 30% 
			);
	
	public static final int[] GRIP_OFFICIAL = buildWeighted(
			25,  2000,  // 2.0초: 25퍼
			35,  3000,  // 3.0초: 35퍼
			20, 4000,  // 4.0초: 20%
			20, 5000   // 5.0초: 20% 
			);
	

	public static final int[] EARTH_BIND_DUR = {
			1000,2000,3000,4000,5000,6000,7000,8000
	};
	
	public static final int[] BOUNS = { 1000, 2000, 1000, 2000, 1000, 2000 };
	

	public static final int[] BONE_BREAK_DUR = { 500, 1000, 1500, 2000 };
	
	private static int[] buildWeighted(int... pairs) {
	    int total = 0;
	    for (int i = 0; i < pairs.length; i += 2) total += pairs[i];

	    int[] out = new int[total];
	    int idx = 0;

	    for (int i = 0; i < pairs.length; i += 2) {
	        int cnt = pairs[i];
	        int val = pairs[i + 1];
	        for (int k = 0; k < cnt; k++) out[idx++] = val;
	    }
	    return out;
	}
	
	
	public static int pickMillis(int[] arr) {
        if (arr == null || arr.length == 0) return 0;
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }
	
    // =========================================================
    // 변신지배반지 + 캔슬레이션이면 이팩트 숨김
    // =========================================================
	private static boolean isPolyRingMaster(final L1Character target) {
        if (!(target instanceof L1PcInstance)) return false;
        return ((L1PcInstance) target).hasSkillEffect(L1SkillId.POLY_RING_MASTER);
    }

	private static boolean isCancellationSkill(final int skillId) {
        return (skillId == L1SkillId.CANCELLATION)
                || (skillId == L1SkillId.OMAN_CANCELLATION)
                || (skillId == L1SkillId.ANTA_CANCELLATION)
                || (skillId == L1SkillId.PHOENIX_CANCELLATION);
    }
		
	public static boolean shouldHideCancellationEffect(final int skillId, final L1Character target) {
        return isCancellationSkill(skillId) && isPolyRingMaster(target);
    }
	
	/** 침묵/사일런스류 상태에서 "사용 허용" 스킬인지 */
	public static boolean canUseWhileSilenced(int skillId) {
	    return S_USE_SKILL_SILENCE.contains(skillId);
	}
	
	// =========================
	// Poly candidates
	// =========================
	private static final int[] POLY_ARRAY = {
	    945, 979, 1037, 1039, 3860, 3863, 3864, 3865, 3904, 3906, 95, 146,
	    2374, 2376, 2377, 2378, 3866, 3867, 3868, 3869, 3870, 3871, 3872,
	    3873, 3874, 3875, 3876, 3882, 3883, 3884, 3885, 11358, 11396,
	    11397, 12225, 12226, 11399, 11398, 12227, 15638, 15635, 15636
	};

	/** 랜덤 변신 ID 하나 뽑기 */
	public static int pickRandomPolyId() {
	    final int[] arr = POLY_ARRAY;
	    if (arr == null || arr.length == 0) return 0;
	    return arr[ThreadLocalRandom.current().nextInt(arr.length)];
	}

	// =========================================================
	// Set 구현 (BitSet + IntHashSet)
	// =========================================================
	private static final class SkillIdSet {
		private final int lowMax;
		private final BitSet low; // low range only
		private final IntHashSet high;      // high range
		private int[] ids;
		private int size;

		SkillIdSet(int lowMax) {
			this.lowMax = (lowMax > 0 ? lowMax : 0);
			this.low = new BitSet(this.lowMax + 1);
			this.high = new IntHashSet(64);
			this.ids = new int[64];
			this.size = 0;
		}

		void addAll(int... arr) {
			if (arr == null) return;
			for (int i = 0; i < arr.length; i++) add(arr[i]);
		}

		void add(int id) {
			if (id < 0) return;

			boolean newlyAdded;
			if (id <= lowMax) {
				if (low.get(id)) return;
				low.set(id);
				newlyAdded = true;
			} else {
				newlyAdded = high.add(id);
			}

			if (newlyAdded) {
				ensureCap(size + 1);
				ids[size++] = id;
			}
		}

		boolean contains(int id) {
			if (id < 0) return false;
			if (id <= lowMax) return low.get(id);
			return high.contains(id);
		}

		int[] idsUnsafe() {
			if (size == 0) return EMPTY_INT;
			if (size == ids.length) return ids;
			int[] out = new int[size];
			System.arraycopy(ids, 0, out, 0, size);
			return out;
		}

		private void ensureCap(int need) {
			if (need <= ids.length) return;
			int n = ids.length;
			while (n < need) n <<= 1;
			int[] nn = new int[n];
			System.arraycopy(ids, 0, nn, 0, size);
			ids = nn;
		}
	}

	private static final int[] EMPTY_INT = new int[0];

	/**
	 * remove 필요 없는 "추가/조회" 전용 IntHashSet.
	 * - 오픈 어드레싱
	 * - 0은 sentinel로 사용 (키 0 들어오면 별도 처리)
	 */
	private static final class IntHashSet {
		private static final float LOAD = 0.70f;

		private int[] table;
		private int mask;
		private int size;
		private int threshold;
		private boolean hasZero;

		IntHashSet(int initialCap) {
			int cap = 1;
			while (cap < initialCap) cap <<= 1;
			table = new int[cap];
			mask = cap - 1;
			threshold = (int) (cap * LOAD);
			size = 0;
			hasZero = false;
		}

		boolean add(int k) {
			if (k == 0) {
				if (hasZero) return false;
				hasZero = true;
				size++;
				if (size >= threshold) rehash(table.length << 1);
				return true;
			}

			if (size >= threshold) rehash(table.length << 1);

			int idx = mix(k) & mask;
			while (true) {
				int cur = table[idx];
				if (cur == 0) {
					table[idx] = k;
					size++;
					return true;
				}
				if (cur == k) return false;
				idx = (idx + 1) & mask;
			}
		}

		boolean contains(int k) {
			if (k == 0) return hasZero;

			int idx = mix(k) & mask;
			while (true) {
				int cur = table[idx];
				if (cur == 0) return false;
				if (cur == k) return true;
				idx = (idx + 1) & mask;
			}
		}

		private void rehash(int newCap) {
			int cap = 1;
			while (cap < newCap) cap <<= 1;

			int[] old = table;
			table = new int[cap];
			mask = cap - 1;
			threshold = (int) (cap * LOAD);

			size = hasZero ? 1 : 0;

			for (int i = 0; i < old.length; i++) {
				int v = old[i];
				if (v == 0) continue;
				int idx = mix(v) & mask;
				while (table[idx] != 0) idx = (idx + 1) & mask;
				table[idx] = v;
				size++;
			}
		}

		private static int mix(int x) {
			x ^= (x >>> 16);
			x *= 0x7feb352d;
			x ^= (x >>> 15);
			x *= 0x846ca68b;
			x ^= (x >>> 16);
			return x;
		}
	}
}
