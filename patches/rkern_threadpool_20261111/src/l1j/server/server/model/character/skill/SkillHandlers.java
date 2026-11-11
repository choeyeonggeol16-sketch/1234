package l1j.server.server.model.character.skill;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;


import l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeName;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.Config;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1CurseParalysis;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.character.skill.helper.WeakExposeHelper;
import l1j.server.server.model.item.function.TeleportUseService;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eDurationShowType;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_IvenBuffIcon;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;



import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.eKind;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.poison.L1ParalysisPoison;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.utils.L1SpawnUtil;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
import l1j.server.server.model.network.core.sc.packet.SC_Dexup;
import l1j.server.server.model.network.core.sc.packet.SC_Strup;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconShield;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconAura;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWindShackle;
import l1j.server.server.model.network.core.sc.packet.SC_CurseBlind;
/**
 *   SkillHandlers
 */
public final class SkillHandlers {

	private SkillHandlers() {}

	private static final int TABLE_SIZE = 32767;

	public static final int HD_NOT_HANDLED = 0;
	public static final int HD_HANDLED     = 1;
	public static final int HD_CONTINUE    = 2;
	public static final int HD_RETURN      = 3;

	// ===== 중요: fallback/handled 분리 =====
	private enum HandlerResult {
		NOT_HANDLED, // 이 핸들러는 조건 불만족 -> 기존 switch로 처리
		HANDLED,     // 이 핸들러가 처리 완료 -> 기존 switch 스킵
		CONTINUE,    // 멀티타겟이면 다음 타겟으로
		RETURN       // runSkill 자체 종료
	}

	@FunctionalInterface
	private interface SkillHandler {
		HandlerResult handle(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter);
	}

	// ===== dense table (0..TABLE_SIZE-1) =====
	private static final SkillHandler[] TABLE = new SkillHandler[TABLE_SIZE];

	private static final Int2ObjectOpenHashMap<SkillHandler> BIG = new Int2ObjectOpenHashMap<SkillHandler>(512);


	static {
		register(L1SkillId.SHAPE_CHANGE, SkillHandlers::onShapeChange);
		register(L1SkillId.TRUE_TARGET, SkillHandlers::onTrueTarget);
		register(L1SkillId.SHOCK_STUN, SkillHandlers::onShockStun);
		register(L1SkillId.EMPIRE, SkillHandlers::onEmpire);
		register(L1SkillId.BONE_BREAK, SkillHandlers::onBoneBreak);
		register(L1SkillId.CANCELLATION, SkillHandlers::onCancellation);
		register(L1SkillId.ELEMENTAL_FALL_DOWN, SkillHandlers::onElementalFallDown);
		register(L1SkillId.RESURRECTION, SkillHandlers::onResurrection);
		register(L1SkillId.GREATER_RESURRECTION, SkillHandlers::onResurrection);
		register(L1SkillId.DESPERADO, SkillHandlers::onDesperado);

		// multi-hit
		register(L1SkillId.TRIPLE_ARROW, SkillHandlers::onTripleArrow);
		register(L1SkillId.FOU_SLAYER, SkillHandlers::onFouSlayer);

		// buffs (이관)
		register(L1SkillId.ADVANCE_SPIRIT, SkillHandlers::onAdvanceSpirit);
		register(L1SkillId.PRIDE, SkillHandlers::onPride);
		register(L1SkillId.GIGANTIC, SkillHandlers::onGigantic);

		register(L1SkillId.SHINING_ARMOR, SkillHandlers::onShiningArmor);
		register(L1SkillId.MAJESTY, SkillHandlers::onMajesty);
		register(L1SkillId.ABSOLUTE_BARRIER, SkillHandlers::onAbsoluteBarrier);
		register(L1SkillId.REDUCTION_ARMOR, SkillHandlers::onReductionArmor);

		register(L1SkillId.MOEBIUS, SkillHandlers::onMoebius);
		register(L1SkillId.DRESS_EVASION, SkillHandlers::onDressEvasion);
		register(L1SkillId.AQUA_PROTECTER, SkillHandlers::onAquaProtecter);
		register(L1SkillId.SOLID_CARRIAGE, SkillHandlers::onSolidCarriage);

		register(L1SkillId.GUARD_BREAK, SkillHandlers::onGuardBreak);
		register(L1SkillId.STRIKER_GALE, SkillHandlers::onStrikerGale);

		register(L1SkillId.SCALES_WATER_DRAGON, SkillHandlers::onScalesWaterDragon);
		register(L1SkillId.SCALES_EARTH_DRAGON, SkillHandlers::onScalesEarthDragon);
		register(L1SkillId.SCALES_RINDVIOR_DRAGON, SkillHandlers::onScalesRindviorDragon);
		register(L1SkillId.SCALES_FIRE_DRAGON, SkillHandlers::onScalesFireDragon);

		register(L1SkillId.PATIENCE, SkillHandlers::onPatience);

		register(L1SkillId.EAGGLE_EYE, SkillHandlers::onEaggleEye);


		register(L1SkillId.IllUSION_OGRE, SkillHandlers::onIllusionOgre);
		register(L1SkillId.IllUSION_LICH, SkillHandlers::onIllusionLich);
		register(L1SkillId.IllUSION_DIAMONDGOLEM, SkillHandlers::onIllusionDiamondGolem);
		register(L1SkillId.IllUSION_AVATAR, SkillHandlers::onIllusionAvatar);

		register(L1SkillId.CYCLONE, SkillHandlers::onCyclone);
		register(L1SkillId.FOCUS_SPRITS, SkillHandlers::onFocusSpirits);
		register(L1SkillId.FREEZEENG_ARMOR, SkillHandlers::onFreezeengArmor);

		register(L1SkillId.LUCIFER, SkillHandlers::onLucifer);
		register(L1SkillId.BLOW_ATTACK, SkillHandlers::onBlowAttack);
		register(L1SkillId.ENCHANT_ACURUCY, SkillHandlers::onEnchantAcurucy);

		register(L1SkillId.CUBE_AVATAR, SkillHandlers::onCubeAvatar);
		register(L1SkillId.CUBE_RICH, SkillHandlers::onCubeRich);
		register(L1SkillId.CUBE_GOLEM, SkillHandlers::onCubeGolem);
		register(L1SkillId.CUBE_OGRE, SkillHandlers::onCubeOgre);

		register(L1SkillId.TAMING_MONSTER, SkillHandlers::onTamingMonster);
		register(L1SkillId.CREATE_ZOMBIE, SkillHandlers::onCreateZombie);
		register(L1SkillId.WEAK_ELEMENTAL, SkillHandlers::onWeakElemental);
		register(L1SkillId.RETURN_TO_NATURE, SkillHandlers::onReturnToNature);
		register(L1SkillId.POWERRIP, SkillHandlers::onPowerRip);

		register(L1SkillId.LESSER_ELEMENTAL, SkillHandlers::onElementalSummon);
		register(L1SkillId.GREATER_ELEMENTAL, SkillHandlers::onElementalSummon);

		register(L1SkillId.TELEPORT, SkillHandlers::onTeleport);
		register(L1SkillId.MASS_TELEPORT, SkillHandlers::onTeleport);
		register(L1SkillId.TELEPORT_TO_MATHER, SkillHandlers::onTeleportToMather);

		register(L1SkillId.BRING_STONE, SkillHandlers::onBringStone);
		register(L1SkillId.SUMMON_MONSTER, SkillHandlers::onSummonMonster);

		register(L1SkillId.IMPACT, SkillHandlers::onImpact);
		register(L1SkillId.GRACE_AVATAR, SkillHandlers::onGraceAvatar);

		register(L1SkillId.RESIST_MAGIC, SkillHandlers::onResistMagic);
		register(L1SkillId.CLEAR_MIND, SkillHandlers::onClearMind);
		register(L1SkillId.GREATER_HASTE, SkillHandlers::onGreaterHaste);
		register(L1SkillId.HOLY_WALK, SkillHandlers::onHolyWalk);
		register(L1SkillId.MOVING_ACCELERATION, SkillHandlers::onMovingAcceleration);
		register(L1SkillId.BLOOD_LUST, SkillHandlers::onBloodLust);

		register(L1SkillId.GLOWING_AURA, SkillHandlers::onGlowingWeapon);
		register(L1SkillId.SHINING_AURA, SkillHandlers::onShiningShield);
		register(L1SkillId.BRAVE_AURA,   SkillHandlers::onBraveMental);
		register(L1SkillId.SHIELD,       SkillHandlers::onShield);
		register(L1SkillId.SHADOW_ARMOR, SkillHandlers::onShadowArmor);
		register(L1SkillId.DRESS_DEXTERITY, SkillHandlers::onDressDexterity);
		register(L1SkillId.DRESS_MIGHTY,    SkillHandlers::onDressMighty);

		register(L1SkillId.THUNDER_GRAB, SkillHandlers::onThunderGrab);

		register(L1SkillId.REMOVE_CURSE, SkillHandlers::onRemoveCurse);
		register(L1SkillId.CALL_OF_NATURE, SkillHandlers::onCallOfNature);

		register(L1SkillId.SLOW, SkillHandlers::onSlowLike);
		register(L1SkillId.MOB_SLOW_1, SkillHandlers::onSlowLike);
		register(L1SkillId.MOB_SLOW_18, SkillHandlers::onSlowLike);

		register(L1SkillId.IMMUNE_TO_HARM, SkillHandlers::onImmuneToHarm);
		register(L1SkillId.TOMAHAWK, SkillHandlers::onTomahawk);

		register(L1SkillId.SOUL_BARRIER, SkillHandlers::onSoulBarrier);
		register(L1SkillId.HASTE,        SkillHandlers::onHaste);
		register(L1SkillId.TITANL_RISING,SkillHandlers::onTitanRising);

		register(L1SkillId.UNCANNY_DODGE, SkillHandlers::onUncannyDodge);
		register(L1SkillId.FEAR,          SkillHandlers::onFear);

		// 은신몹 싱크 해제 계열
		register(L1SkillId.DETECTION,     SkillHandlers::onDetectionLike);
		register(L1SkillId.IZE_BREAK,     SkillHandlers::onDetectionLike);
		register(L1SkillId.EYE_OF_DRAGON, SkillHandlers::onDetectionLike);

		// PC면 마법데미지 / NPC면 sink면 드러내기 아니면 0
		register(L1SkillId.COUNTER_DETECTION, SkillHandlers::onCounterDetection);

		register(L1SkillId.HOLY_WEAPON,   SkillHandlers::onHolyweapon);
		register(L1SkillId.SHADOW_FANG,   SkillHandlers::onShadowFang);
		register(L1SkillId.ENCHANT_WEAPON,SkillHandlers::onEnchantWeapon);
		register(L1SkillId.BLESS_WEAPON,  SkillHandlers::onBlessWeapon);

		register(L1SkillId.MIND_BREAK, SkillHandlers::onMindBreak);

		register(L1SkillId.CURSE_BLIND, SkillHandlers::onCurseBlindLike);
		register(L1SkillId.DARKNESS,   SkillHandlers::onCurseBlindLike);

		register(L1SkillId.DARK_BLIND, SkillHandlers::onDarkBlind);

		register(L1SkillId.PHANTASM, SkillHandlers::onPhantasm);

		register(L1SkillId.WIND_SHACKLE,     SkillHandlers::onWindShackleLike);
		register(L1SkillId.MOB_WINDSHACKLE_1, SkillHandlers::onWindShackleLike);

		register(L1SkillId.TURN_UNDEAD, SkillHandlers::onTurnUndead);

		register(L1SkillId.MANA_DRAIN, SkillHandlers::onManaDrain);

		register(L1SkillId.WEAPON_BREAK, SkillHandlers::onWeaponBreak);

		register(L1SkillId.FOG_OF_SLEEPING, SkillHandlers::onFogOfSleeping);

		register(L1SkillId.STATUS_FREEZE, SkillHandlers::onStatusFreeze);

		register(L1SkillId.CURSE_POISON,       SkillHandlers::onCursePoisonLike);

		register(L1SkillId.CURSE_PARALYZE,        SkillHandlers::onCurseParalyzeLike);
		register(L1SkillId.CURSE_PARALYZE2,       SkillHandlers::onCurseParalyzeLike);
		register(L1SkillId.MOB_CURSEPARALYZ1,     SkillHandlers::onCurseParalyzeLike);
		register(L1SkillId.MOB_CURSEPARALYZ_18,   SkillHandlers::onCurseParalyzeLike);
		register(L1SkillId.MOB_CURSEPARALYZ_19,   SkillHandlers::onCurseParalyzeLike);

		register(L1SkillId.WEAKNESS,        SkillHandlers::onWeaknessLike);
		register(L1SkillId.MOB_WEAKNESS_1,  SkillHandlers::onWeaknessLike);

		register(L1SkillId.DISEASE,         SkillHandlers::onDiseaseLike);
		register(L1SkillId.MOB_DISEASE_1,   SkillHandlers::onDiseaseLike);
		register(L1SkillId.MOB_DISEASE_30,  SkillHandlers::onDiseaseLike);

		register(L1SkillId.HORROR_OF_DEATH, SkillHandlers::onHorrorOfDeath);
		register(L1SkillId.PANIC,           SkillHandlers::onPanic);

		register(L1SkillId.MOB_BASILL,      SkillHandlers::onMobBasillOrCoca);
		register(L1SkillId.MOB_COCA,        SkillHandlers::onMobBasillOrCoca);

		register(L1SkillId.MOB_RANGESTUN_18,   SkillHandlers::onMobRangeStunLike);
		register(L1SkillId.MOB_RANGESTUN_19,   SkillHandlers::onMobRangeStunLike);
		register(L1SkillId.MOB_SHOCKSTUN_30,   SkillHandlers::onMobRangeStunLike);

		// 힐계열: WATER_LIFE 타이머 제거 + 아이콘 삭제
		register(L1SkillId.HEAL,             SkillHandlers::onHealFamily);
		register(L1SkillId.EXTRA_HEAL,       SkillHandlers::onHealFamily);
		register(L1SkillId.GREATER_HEAL,     SkillHandlers::onHealFamily);
		register(L1SkillId.FULL_HEAL,        SkillHandlers::onHealFamily);
		register(L1SkillId.HEAL_ALL,         SkillHandlers::onHealFamily);
		register(L1SkillId.NATURES_TOUCH,    SkillHandlers::onHealFamily);
		register(L1SkillId.NATURES_BLESSING, SkillHandlers::onHealFamily);

		// 흡혈계열: heal = dmg/2 (데미지 계산 이후 훅에서 반영해야 함)
		register(L1SkillId.CHILL_TOUCH,   SkillHandlers::onVampiricTouchLike);
		register(L1SkillId.VAMPIRIC_TOUCH,SkillHandlers::onVampiricTouchLike);

		register(L1SkillId.DEATH_HEAL,     SkillHandlers::onDeathHeal);
		register(L1SkillId.DEATH_HEAL_Mob, SkillHandlers::onDeathHeal);

		register(L1SkillId.HUNTER_BLESS,   SkillHandlers::onHunterBless);
		register(L1SkillId.CURE_POISON,    SkillHandlers::onCurePoison);

		register(L1SkillId.God_buff,            SkillHandlers::onGodBuff);
		register(L1SkillId.IRON_SKIN,           SkillHandlers::onIronSkin);
		register(L1SkillId.FIRE_SHIELD,         SkillHandlers::onFireShield);
		register(L1SkillId.PHYSICAL_ENCHANT_STR,SkillHandlers::onPhysicalEnchantStr);
		register(L1SkillId.PHYSICAL_ENCHANT_DEX,SkillHandlers::onPhysicalEnchantDex);
		register(L1SkillId.EARTH_WEAPON,        SkillHandlers::onEarthWeapon);

		register(L1SkillId.REDUCE_WEIGHT,       SkillHandlers::onReduceWeightLike);
		register(L1SkillId.DECREASE_WEIGHT,     SkillHandlers::onReduceWeightLike);
		register(L1SkillId.COUNTER_MIRROR,      SkillHandlers::onCounterMirror);

		register(L1SkillId.HURRICANE,           SkillHandlers::onHurricane);
		register(L1SkillId.FOCUS_WAVE,          SkillHandlers::onFocusWave);
		register(L1SkillId.SAND_STORM,          SkillHandlers::onSandStorm);
		register(L1SkillId.DANCING_BLADES,      SkillHandlers::onDancingBlades);
		register(L1SkillId.BURNING_WEAPON,      SkillHandlers::onBurningWeapon);

		register(L1SkillId.MIRROR_IMAGE,        SkillHandlers::onMirrorImage);
		register(L1SkillId.AQUA_SHOT,           SkillHandlers::onAquaShot);
		register(L1SkillId.STORM_EYE,           SkillHandlers::onStormEye);
		register(L1SkillId.STORM_SHOT,          SkillHandlers::onStormShot);

		register(L1SkillId.BERSERKERS,          SkillHandlers::onBerserkers);
		register(L1SkillId.INSIGHT,             SkillHandlers::onInsight);

		register(L1SkillId.BOUNCE_ATTACK,       SkillHandlers::onBounceAttack);

		register(L1SkillId.ICE_LANCE, SkillHandlers::onIceLance);
		register(L1SkillId.EARTH_BIND, SkillHandlers::onEarthBind);

		register(L1SkillId.BLESSED_ARMOR, SkillHandlers::onBlessedArmor);


		register(L1SkillId.ABSOLUTE_BLADE, SkillHandlers::onAbsoluteBlade);

		register(L1SkillId.성혈버프,       SkillHandlers::onSeonghyeolBuff);

		register(10026, SkillHandlers::onDgUiAndKill);
		register(10027, SkillHandlers::onDgUiAndKill);
		register(10028, SkillHandlers::onDgUiAndKill);
		register(10029, SkillHandlers::onDgUiAndKill);

		register(10057, SkillHandlers::onTeleportFront5);

		register(L1SkillId.EARTH_GUARDIAN, SkillHandlers::onEarthGuardian);
		register(L1SkillId.RESIST_ELEMENTAL, SkillHandlers::onResistElemental);
		register(L1SkillId.BODY_TO_MIND, SkillHandlers::onBodyToMind);
		register(L1SkillId.BLOODY_SOUL, SkillHandlers::onBloodySoul);
		register(L1SkillId.ELEMENTAL_PROTECTION, SkillHandlers::onElementalProtection);
		register(L1SkillId.INVISIBILITY, SkillHandlers::onInvisibilityWithCastleTowerBlock);
		register(L1SkillId.BLIND_HIDING, SkillHandlers::onInvisibilityWithCastleTowerBlock);
		register(L1SkillId.정상의가호, SkillHandlers::onNormalBlessingDR);
		register(L1SkillId.AREA_OF_SILENCE, SkillHandlers::onAreaOfSilenceSound);
		register(L1SkillId.BOSS_COUNTER_BARRIER, SkillHandlers::onBossCounterBarrierEffect);

		register(L1SkillId.OMAN_STUN,  SkillHandlers::onOmanOrMaenoStun);
		register(L1SkillId.Maeno_STUN, SkillHandlers::onOmanOrMaenoStun);

		// 몹버서커
		register(L1SkillId.MOB_BERSERKERS, SkillHandlers::onMobBerserkers);

		// =====_BUFF =====
		register(L1SkillId.FEATHER_BUFF_A, SkillHandlers::onFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_B, SkillHandlers::onFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_C, SkillHandlers::onFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_D, SkillHandlers::onFeatherBuff);

		register(L1SkillId.COMA_A, SkillHandlers::onComa);
		register(L1SkillId.COMA_B, SkillHandlers::onComa);

		register(L1SkillId.miso1, SkillHandlers::onMiso);
		register(L1SkillId.miso2, SkillHandlers::onMiso);
		register(L1SkillId.miso3, SkillHandlers::onMiso);

		register(L1SkillId.CLAN_BUFF1, SkillHandlers::onClanBuff);
		register(L1SkillId.CLAN_BUFF2, SkillHandlers::onClanBuff);
		register(L1SkillId.CLAN_BUFF3, SkillHandlers::onClanBuff);
		register(L1SkillId.CLAN_BUFF4, SkillHandlers::onClanBuff);

		register(L1SkillId.BUFF_SAEL,   SkillHandlers::onNpcBloodBuff);
		register(L1SkillId.BUFF_GUNTER, SkillHandlers::onNpcBloodBuff);
		register(L1SkillId.BUFF_CRAY,   SkillHandlers::onNpcBloodBuff);
		register(L1SkillId.BUFF_Vala,   SkillHandlers::onNpcBloodBuff);

		// 마안
		register(L1SkillId.ANTA_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.FAFU_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.LIND_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.VALA_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.BIRTH_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.SHAPE_MAAN, SkillHandlers::onDragonMaan);
		register(L1SkillId.LIFE_MAAN, SkillHandlers::onDragonMaan);

		// 드래곤블러드(버프)
		register(L1SkillId.ANTA_BUFF, SkillHandlers::onDragonBlood);
		register(L1SkillId.FAFU_BUFF, SkillHandlers::onDragonBlood);
		register(L1SkillId.RIND_BUFF, SkillHandlers::onDragonBlood);


		// ===== N_BUFF =====
		register(L1SkillId.N_BUFF_PVP_DMG, SkillHandlers::onNBuffPvpDmg);
		register(L1SkillId.N_BUFF_HPMP, SkillHandlers::onNBuffHpMp);
		register(L1SkillId.N_BUFF_DMG, SkillHandlers::onNBuffDmg);
		register(L1SkillId.N_BUFF_REDUCT, SkillHandlers::onNBuffReduct);
		register(L1SkillId.N_BUFF_SP, SkillHandlers::onNBuffSp);
		register(L1SkillId.N_BUFF_STUN, SkillHandlers::onNBuffStun);
		register(L1SkillId.N_BUFF_HOLD, SkillHandlers::onNBuffHold);

		register(L1SkillId.N_BUFF_WATER_DMG, SkillHandlers::onNBuffWaterDmg);
		register(L1SkillId.N_BUFF_WIND_DMG, SkillHandlers::onNBuffWindDmg);
		register(L1SkillId.N_BUFF_EARTH_DMG, SkillHandlers::onNBuffEarthDmg);
		register(L1SkillId.N_BUFF_FIRE_DMG, SkillHandlers::onNBuffFireDmg);

		register(L1SkillId.N_BUFF_STR, SkillHandlers::onNBuffStr);
		register(L1SkillId.N_BUFF_DEX, SkillHandlers::onNBuffDex);
		register(L1SkillId.N_BUFF_INT, SkillHandlers::onNBuffInt);
		register(L1SkillId.N_BUFF_WIS, SkillHandlers::onNBuffWis);

		register(L1SkillId.N_BUFF_WATER, SkillHandlers::onNBuffWater);
		register(L1SkillId.N_BUFF_WIND, SkillHandlers::onNBuffWind);
		register(L1SkillId.N_BUFF_EARTH, SkillHandlers::onNBuffEarth);
		register(L1SkillId.N_BUFF_FIRE, SkillHandlers::onNBuffFire);
		register(L1SkillId.N_BUFF_ALL_RESIST, SkillHandlers::onNBuffAllResist);

		// 기르타스
		register(L1SkillId.TROGIR_MILPITAS1, SkillHandlers::onCursePoisonLike);
		register(L1SkillId.TROGIR_MILPITAS2, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.TROGIR_MILPITAS3, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.TROGIR_MILPITAS4, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.TROGIR_MILPITAS5, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.TROGIR_MILPITAS6, SkillHandlers::onBossDirectMagic);

		// 원본 공통 보조 / 이벤트 버프
		register(L1SkillId.CONCENTRATION, SkillHandlers::onConcentration);
		register(L1SkillId.DRAGON_HUNTER_BLESS, SkillHandlers::onLegacyEventIconBuff);
		register(L1SkillId.DRAGON_SET, SkillHandlers::onDragonSet);
		register(L1SkillId.격전의주문서, SkillHandlers::onBattleScroll);
		register(L1SkillId.HUNTER_BLESS2, SkillHandlers::onLegacyEventIconBuff);
		register(L1SkillId.천하장사버프, SkillHandlers::onStrongmanBuff);
		register(L1SkillId.SIDE_OF_ME_BLESSING, SkillHandlers::onLegacyBlessing);
		register(L1SkillId.RE_START_BLESSING, SkillHandlers::onLegacyBlessing);
		register(L1SkillId.NEW_START_BLESSING, SkillHandlers::onLegacyBlessing);
		register(L1SkillId.LIFE_BLESSING, SkillHandlers::onLegacyBlessing);

		// 원본에서 본문이 비어 있던 case도 handled로 보존
		register(L1SkillId.잠재력소울, SkillHandlers::onOriginalNoOp);
		register(L1SkillId.할파스의신의PVP, SkillHandlers::onOriginalNoOp);
		register(L1SkillId.할파스의신의, SkillHandlers::onOriginalNoOp);

		// 피닉스 / 드레이크
		register(L1SkillId.Phoenix_Skill1, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.Phoenix_Skill2, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.DRAKE_Skill1, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.DRAKE_Skill2, SkillHandlers::onBossDirectMagic);
		register(L1SkillId.DRAKE_MASSTELEPORT, SkillHandlers::onDrakeMassTeleport);
		register(L1SkillId.DRAKE_WIND_SHACKLE, SkillHandlers::onDrakeWindShackle);
		register(L1SkillId.EFRETE_SUMMON_MONSTER, SkillHandlers::onEfreetSummon);
		register(L1SkillId.PHOENIX_SUMMON_MONSTER, SkillHandlers::onPhoenixSummon);
		register(L1SkillId.PHOENIX_CANCELLATION, SkillHandlers::onPhoenixCancellation);

		// 샌드웜
		register(L1SkillId.Sand_worms, SkillHandlers::onSandWormAttack);
		register(L1SkillId.Sand_worms1, SkillHandlers::onSandWormAttack);
		register(L1SkillId.Sand_worms2, SkillHandlers::onSandWormAttack);
		register(L1SkillId.Sand_worms3, SkillHandlers::onSandWormAttack);

		// 오만
		register(L1SkillId.OMAN_CANCELLATION, SkillHandlers::onOmanCancellation);

		// 안타라스
		register(L1SkillId.ANTA_MESSAGE_1, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_2, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_3, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_4, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_5, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_6, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_7, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_8, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_9, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_MESSAGE_10, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_CANCELLATION, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_WEAPON_BREAK, SkillHandlers::onAntarasBoss);
		register(L1SkillId.ANTA_SHOCKSTUN, SkillHandlers::onAntarasBoss);

		// 파푸리온
		register(L1SkillId.PAP_PREDICATE1, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE3, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE5, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE6, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE7, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE8, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE9, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE11, SkillHandlers::onPapBoss);
		register(L1SkillId.PAP_PREDICATE12, SkillHandlers::onPapBoss);

		// 린드비오르
		register(L1SkillId.RINDVIOR_SUMMON_MONSTER_CLOUD, SkillHandlers::onRindviorSummonCloud);
		register(L1SkillId.RINDVIOR_PREDICATE, SkillHandlers::onRindviorPredicate);
		register(L1SkillId.RINDVIOR_SUMMON_MONSTER, SkillHandlers::onRindviorSummon);
		register(L1SkillId.RINDVIOR_SILENCE, SkillHandlers::onRindviorSilence);
		register(L1SkillId.RINDVIOR_BOW, SkillHandlers::onRindviorBow);
		register(L1SkillId.RINDVIOR_WIND_SHACKLE, SkillHandlers::onRindviorWindShackle);
		register(L1SkillId.RINDVIOR_WIND_SHACKLE_1, SkillHandlers::onRindviorWindShackle);
		register(L1SkillId.RINDVIOR_PREDICATE_CANCELLATION, SkillHandlers::onRindviorPredicateCancellation);
		register(L1SkillId.RINDVIOR_CANCELLATION, SkillHandlers::onRindviorCancellation);
		register(L1SkillId.RINDVIOR_WEAPON, SkillHandlers::onRindviorWeapon);
		register(L1SkillId.RINDVIOR_WEAPON_2, SkillHandlers::onRindviorWeapon);

		// 흑장로
		register(L1SkillId.BLACKELDER_DEATH_HELL, SkillHandlers::onBlackElderDeathHell);
		register(L1SkillId.BLACKELDER_DEATH_POTION, SkillHandlers::onBlackElderDeathPotion);

		// 에르자베 / 제니스
		register(L1SkillId.DESERT_SKILL1, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL2, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL3, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL4, SkillHandlers::onDesertBoss);
		register(L1SkillId.ZENITH_Poison, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL5, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL6, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL7, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL8, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL9, SkillHandlers::onDesertBoss);
		register(L1SkillId.DESERT_SKILL10, SkillHandlers::onDesertBoss);
		
		register(L1SkillId.BUFF_특별한성장축복, SkillHandlers::onSpecialBuff);
		register(L1SkillId.BUFF_특별한방어축복, SkillHandlers::onSpecialBuff);
		register(L1SkillId.BUFF_특별한공격축복, SkillHandlers::onSpecialBuff);

		register(L1SkillId.메티스축복주문서, SkillHandlers::onMetisBlessScroll);
	}

	private static void register(int skillId, SkillHandler h) {
		if (h == null || skillId < 0) return;

		if (skillId < TABLE_SIZE) TABLE[skillId] = h;
		else BIG.put(skillId, h);
	}

	public static int dispatch(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final int id = su.hdSkillId();
		if (id < 0) return HD_NOT_HANDLED;

		final SkillHandler h = (id < TABLE_SIZE) ? TABLE[id] : BIG.get(id);
		if (h == null) return HD_NOT_HANDLED;

		final HandlerResult r = h.handle(su, cha, magic, undeadType, iter);
		switch (r) {
		case RETURN:
			return HD_RETURN;
		case CONTINUE:
			return HD_CONTINUE;
		case HANDLED:
			return HD_HANDLED;
		case NOT_HANDLED:
		default:
			return HD_NOT_HANDLED;
		}
	}

	// =========================
	//  handler implementations
	// =========================
	private static HandlerResult onShapeChange(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		boolean isSameClan = false;
		final L1PcInstance caster = su.hdPlayer();

		if (cha instanceof L1PcInstance && caster != null) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getClanid() != 0 && caster.getClanid() == pc.getClanid()) {
				isSameClan = true;
			}
			// 폴리링 마스터 보호 (타인 시전 차단)
			if (pc.getId() != caster.getId() && pc.getSkillEffectTimeSec(L1SkillId.POLY_RING_MASTER) > 0) {
				sendSkillSoundBroadcast(pc, 15846);
				SC_ServerMessage.send(caster, 280);
				if (iter != null) iter.remove(); // 대상만 제거
				return HandlerResult.CONTINUE;
			}
		}

		if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance mon = (L1MonsterInstance) cha;
			// 멀티타겟에서 전체 취소되는 문제 방지: 대상만 스킵
			if (!mon.getNpcTemplate().isShapeChange()) {
				return HandlerResult.CONTINUE;
			}
		}

		// 타인/타클랜이면 확률 적용
		if (caster != null && caster.getId() != cha.getId() && !isSameClan) {
			final int probability = 80;
			final int rnd = su.hdRandom().nextInt(100) + 1;
			if (rnd > probability) {
				return HandlerResult.CONTINUE;
			}
		}

		final int polyId = SkillUseHelper.pickRandomPolyId();

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getInventory().checkEquipped(20281)) {
				SC_MessageYN.send(pc, 180);
			} else {
				final L1Skills skillTemp = SkillsTable.getInstance().getTemplate(L1SkillId.SHAPE_CHANGE);
				L1PolyMorph.doPoly(pc, polyId, skillTemp.getBuffDuration(), L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
				if (caster != null && caster.getId() != pc.getId()) {
					SC_ServerMessage.send(pc, 241, caster.getName());
				}
			}
		} else if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance mon = (L1MonsterInstance) cha;
			final L1Skills skillTemp = SkillsTable.getInstance().getTemplate(L1SkillId.SHAPE_CHANGE);
			L1PolyMorph.doPoly(mon, polyId, skillTemp.getBuffDuration(), L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTrueTarget(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    final L1Character user = su.hdUser();
	    final L1Character target =
	            (cha != null ? cha : su.hdTarget());

	    if (!(user instanceof L1PcInstance)) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    if (target == null) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    final L1PcInstance pri =
	            (L1PcInstance) user;

	    final int targetId =
	            target.getId();

	    // =====================================================
	    // 시전자 본인 표시
	    // =====================================================
	    SC_TrueTargetNew.send(pri, targetId, true);

	    // =====================================================
	    // 공격 계산용 트루타겟 단계
	    // =====================================================
	    if (target instanceof L1PcInstance) {

	        final L1PcInstance targetPc =
	                (L1PcInstance) target;

	        final int step =
	                pri.getLevel() / 15;

	        if (step > 0) {
	            targetPc.setTrueTarget(step);
	        }
	    }

	    // =====================================================
	    // 같은 혈맹원에게 트루타겟 표시
	    // =====================================================
	    for (L1PcInstance pc
	            : L1World.getInstance()
	                    .getRecognizePlayer((L1Object) target)) {

	        if (pri.getClanid() == pc.getClanid()) {

	            SC_TrueTargetNew.send(pc, targetId, true);
	        }
	    }

	    // =====================================================
	    // 실제 트루타겟 상태 관리
	    // =====================================================
	    TrueTargetManager.apply(
	            pri,
	            target,
	            16 * 1000);
	    return HandlerResult.HANDLED;
	}

	// ===== dup 제거: 이펙트 스폰 + 스턴/마비 적용 공통 =====
	private static void spawnEffectAtTarget(final L1Character target, final int effectId, final int durationMs) {
		if (target == null) return;
		L1EffectSpawn.getInstance().spawnEffect(effectId, durationMs, target.getX(), target.getY(), target.getMapId());
	}

	private static boolean isNpcLike(final L1Character cha) {
		return (cha instanceof L1MonsterInstance) || (cha instanceof L1NpcInstance);
	}

	/**
	 * 스턴류 공통 처리: 이펙트 + PC 패킷 + (NPC일 때) paralyzed(+선택적으로 paralysisTime)
	 */
	private static void applyStunLikeEffect(final L1Character target, final int durationMs, final int effectId, final boolean npcSetParalysisTime) {
		if (target == null) return;
		spawnEffectAtTarget(target, effectId, durationMs);
		if (target instanceof L1PcInstance targetPc) {
			SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_STUN, true);
		} else if (isNpcLike(target)) {
			final L1NpcInstance npc = (L1NpcInstance) target;
			npc.setParalyzed(true);
			if (npcSetParalysisTime) npc.setParalysisTime(durationMs);
		}
	}


	private static HandlerResult onShockStun(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final int dur = SkillUseHelper.pickMillis(SkillUseHelper.STUN_OFFICIAL);
		if (dur <= 0) return HandlerResult.NOT_HANDLED;

		su.hdSetShockStunDuration(dur);

		applyStunLikeEffect(cha, dur, 81162, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEmpire(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final int dur = SkillUseHelper.pickMillis(SkillUseHelper.STUN_OFFICIAL);
		if (dur <= 0) return HandlerResult.NOT_HANDLED;

		su.hdSetShockStunDuration(dur);

		applyStunLikeEffect(cha, dur, 50000066, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBoneBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final int dur = SkillUseHelper.pickMillis(SkillUseHelper.BONE_BREAK_DUR);

		su.hdSetShockStunDuration(dur);

		applyStunLikeEffect(cha, dur, 200020, false);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCancellation(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		try {
			// =========================
			// NPC: 특수 처리
			// =========================
			if (cha instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				int npcId = npc.getNpcTemplate().get_npcId();

				if (npcId == 71092) {
					if (!npc.equalsCurrentSprite(1314)) {
						npc.setCurrentSprite(1314);
						npc.sendShape(1314);
					}
					return HandlerResult.RETURN;
				}

				if (npcId == 45640) {
					if (npc.equalsCurrentSprite(2755)) {
						npc.setCurrentHp(npc.getMaxHp());
						npc.setCurrentSprite(2332);
						npc.sendShape(2332);
						npc.setName("$2103");
						npc.setNameId("$2103");
						SC_ChangeName.broadcastFast(npc, npc.getId(), "$2103");
					} else {
						npc.setCurrentHp(npc.getMaxHp());
						npc.setCurrentSprite(2755);
						npc.sendShape(2755);
						npc.setName("$2488");
						npc.setNameId("$2488");
						SC_ChangeName.broadcastFast(npc, npc.getId(), "$2488");
					}
				}

				if (npcId == 81209) {
					if (!npc.equalsCurrentSprite(4310)) {
						npc.setCurrentSprite(4310);
						npc.sendShape(4310);
					}
					return HandlerResult.RETURN;
				}
			}

			// =========================
			// NPC: 이동/브레이브/상태 리셋
			// =========================
			if (!(cha instanceof L1PcInstance)) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				npc.setMoveSpeed(0);
				npc.setBraveSpeed(0);
				SC_SkillHaste.broadcastFast(npc, cha.getId(), 0, 0);
				SC_SkillBrave.broadcastFast(npc, cha.getId(), 0, 0);
				npc.setWeaponBreaked(false);
				npc.setParalyzed(false);
			}

			// =========================
			// PC: 디텍션 처리
			// =========================
			if (cha instanceof L1PcInstance) {
				su.hdDetection((L1PcInstance) cha, false);
			}

			// =========================
			// 캔슬  "절대 해제 불가"를 확실히 보호
			// - 실제 걸린 효과만 remove
			// =========================
			final int skillsBegin = su.hdSkillsBegin();
			final int skillsEnd   = su.hdSkillsEnd();

			long ss = SC_SkillSound.pack(cha.getId(), 15846);

			for (int skillNum = skillsBegin; skillNum <= skillsEnd; skillNum++) {
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;
				if (!cha.hasSkillEffect(skillNum)) continue;

				// 폴리 보호
				if (skillNum == L1SkillId.SHAPE_CHANGE) {
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;

						if (pc.getRankLevel() >= 9 && PolyTable.getInstance().isRankingPoly(pc.getCurrentSpriteId())) {
							continue;
						}
						if (pc.getSkillEffectTimeSec(L1SkillId.POLY_RING_MASTER) > 0) {
							SC_SkillSound.sendPacked(pc, ss);
							SC_SkillSound.broadcastFastPacked(pc, ss);
							continue;
						}
					}
				}
				cha.removeSkillEffect(skillNum);
			}

			// =========================
			// 상태(Status) 제거
			// =========================
			final int statusBegin = su.hdStatusBegin();
			final int statusEnd   = su.hdStatusEnd();

			for (int skillNum = statusBegin; skillNum <= statusEnd; skillNum++) {
				// 예외 상태는 건너뜀
				if (skillNum == su.hdStatusChatProhibited()
						|| skillNum == su.hdStatusCurseBarlog()
						|| skillNum == su.hdStatusCurseYahee()) {
					continue;
				}
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;

				if (!cha.hasSkillEffect(skillNum)) continue;
				cha.removeSkillEffect(skillNum);
			}

			// 과일/포이즌/패럴 제거
			try {
				int fruit = su.hdStatusFruit();
				if (fruit > 0 && cha.hasSkillEffect(fruit)) {
					cha.removeSkillEffect(fruit);
				}
			} catch (Throwable t) {
				// ignore
			}

			cha.curePoison();
			cha.cureParalaysis();

			// =========================
			// 요리(Cooking) 제거 
			// =========================
			final int cookBegin = su.hdCookingBegin();
			final int cookEnd   = su.hdCookingEnd();

			for (int skillNum = cookBegin; skillNum <= cookEnd; skillNum++) {
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;

				if (!cha.hasSkillEffect(skillNum)) continue;
				cha.removeSkillEffect(skillNum);
			}

			// =========================
			// 헤이스트 아이템 착용이면 CONTINUE
			// =========================
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				if (pc.getHasteItemEquipped() > 0) {
					return HandlerResult.CONTINUE;
				}
			}

			// 프리즈 제거
			int freeze = su.hdStatusFreeze();
			if (freeze > 0 && cha.hasSkillEffect(freeze)) {
				cha.removeSkillEffect(freeze);
			}

			// 핑크네임
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				L1Character user = su.hdUser();
				L1PinkName.onAction(pc, user);
			}

			return HandlerResult.HANDLED;

		} catch (Exception e) {
			e.printStackTrace();
			return HandlerResult.NOT_HANDLED;
		}
	}

	private static HandlerResult onElementalFallDown(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(su.hdUser() instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;

		final L1PcInstance player = su.hdPlayer();
		if (player == null) return HandlerResult.NOT_HANDLED;

		final int playerAttr = player.getElfAttr();
		final int i = -50;
		final int iconDuration = su.hdGetBuffIconDuration();

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;

			if (pc.hasSkillEffect(L1SkillId.ELEMENTAL_FALL_DOWN)) {
				pc.removeSkillEffect(L1SkillId.ELEMENTAL_FALL_DOWN);
			}

			switch (playerAttr) {
			case 0:
				SC_ServerMessage.send(player, 79);
				break;
			case 1:
				pc.getResistance().addEarth(i);
				pc.setAddAttrKind(1);
				break;
			case 2:
				pc.getResistance().addFire(i);
				pc.setAddAttrKind(2);
				break;
			case 4:
				pc.getResistance().addWater(i);
				pc.setAddAttrKind(4);
				break;
			case 8:
				pc.getResistance().addWind(i);
				pc.setAddAttrKind(8);
				break;
			default:
				break;
			}
			SC_InventoryIcon.icoNew(pc, L1SkillId.ELEMENTAL_FALL_DOWN, 1009, iconDuration, true);
			sendSkillSoundBroadcast(pc, 4399);
			return HandlerResult.HANDLED;
		}

		if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance mob = (L1MonsterInstance) cha;

			switch (playerAttr) {
			case 0:
				SC_ServerMessage.send(player, 79);
				break;
			case 1:
				mob.getResistance().addEarth(i);
				mob.setAddAttrKind(1);
				break;
			case 2:
				mob.getResistance().addFire(i);
				mob.setAddAttrKind(2);
				break;
			case 4:
				mob.getResistance().addWater(i);
				mob.setAddAttrKind(4);
				break;
			case 8:
				mob.getResistance().addWind(i);
				mob.setAddAttrKind(8);
				break;
			default:
				break;
			}

			sendSkillSoundBroadcast(mob, 4399);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	private static HandlerResult onResurrection(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		final int skillId = su.hdSkillId();

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;

			if (caster.getId() != pc.getId()) {

				if (L1World.getInstance().getVisiblePlayer(pc, 0).size() > 0) {
					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(pc, 0)) {
						if (!visiblePc.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				int castle_id = L1CastleLocation.getCastleIdByArea(pc);
				if (castle_id != 0) {
					SC_SystemMessage.send(pc, "사용할 수 없는 지역입니다.");
					return HandlerResult.RETURN;
				}

				if (pc.getCurrentHp() == 0 && pc.isDead()) {
					if (pc.getMap().isUseResurrection()) {
						if (skillId == L1SkillId.RESURRECTION) {
							pc.setGres(false);
						} else if (skillId == L1SkillId.GREATER_RESURRECTION) {
							pc.setGres(true);
						}
						pc.setTempID(caster.getId());
						SC_MessageYN.send(pc, 322, "");
					}
				}
			}
			return HandlerResult.HANDLED;
		}

		if (cha instanceof L1NpcInstance) {
			if (!(cha instanceof L1TowerInstance)) {
				final L1NpcInstance npc = (L1NpcInstance) cha;
				final int npcId = npc.getNpcId();

				if (npcId != 0) {
					caster.sendPackets("부활 할 수 없습니다.");
					return HandlerResult.RETURN;
				}

				if (npc.getNpcTemplate().isCantResurrect()
						&& !(npc instanceof L1PetInstance)
						&& !(npc instanceof L1CompanionInstance)) {
					return HandlerResult.RETURN;
				}

				if ((npc instanceof L1CompanionInstance || npc instanceof L1PetInstance)
						&& L1World.getInstance().getVisiblePlayer(npc, 0).size() > 0) {

					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) {
						if (!visiblePc.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				if (npc.getCurrentHp() == 0 && npc.isDead()) {
					npc.resurrect(npc.getMaxHp() / 4);
					npc.setResurrect(true);
				}
				return HandlerResult.HANDLED;
			}
		}

		return HandlerResult.NOT_HANDLED;
	}

	// =====================================================
	// Multi-hit hook (스킬별 before/after)
	// =====================================================
	@FunctionalInterface
	private interface MultiHitHook {
		void onHit(L1PcInstance a, L1Character t, int hitIndex, int hits, boolean before);
		MultiHitHook NONE = (a, t, i, h, before) -> {};
	}

	// =====================================================
	// Triple Arrow
	// =====================================================
	private static HandlerResult onTripleArrow(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (player == null || target == null) return HandlerResult.NOT_HANDLED;

		final int wt = weaponType1(player);
		if (wt != 20) return HandlerResult.NOT_HANDLED;
		if (player.getCurrentSpriteId() == 3784) return HandlerResult.NOT_HANDLED;

		final int hits = 3;
		scheduleMultiHit(
				player,
				target,
				hits,
				80,
				Integer.MAX_VALUE,
				(a, t, idx, hs, before) -> {
					if (!before && idx == hs - 1) {
						sendSkillSoundOnce(a, 15103);
					}
				}
				);

		return HandlerResult.HANDLED;
	}

	// =====================================================
	// Fou Slayer
	// =====================================================
	private static HandlerResult onFouSlayer(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (player == null || target == null) return HandlerResult.NOT_HANDLED;

		final int wt = weaponType1(player);
		if (!isOneOf(wt, 4, 11, 24, 50, 83)) return HandlerResult.NOT_HANDLED;

		final int hits = 3;

		// token 생성
		final int token = player.newMultiHitToken();

		// 현재 스택(충전) 스냅샷
		final int charges = fouPeekCharges(player); // 0~4
		player.fouBegin(token, charges, hits);

		WeakExposeHelper.consumeAllNowOnFouCast(player);

		int effect = 6509;
		if (player.isPassive(MJPassiveID.FOU_SLAYER_BRAVE.toInt())) {
			effect = 17231;
		}
		sendSkillSoundBroadcast(target, effect);

		scheduleMultiHitWithToken(player, target, hits, 80, Integer.MAX_VALUE, token, (a, t, idx, hs, before) -> {
			if (!before) {
				if (idx == hs - 1) {
					sendSkillSoundOnce(a, 7020);
				}
			}
		});

		return HandlerResult.HANDLED;
	}

	// =====================================================
	// stage(1~4) -> charges(1~4)
	// =====================================================
	private static int fouPeekCharges(L1PcInstance pc) {
		if (pc == null) return 0;

		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD4)) return 4;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD3)) return 3;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD2)) return 2;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD1)) return 1;
		return 0;
	}


	private static HandlerResult onAdvanceSpirit(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.ADVANCE_SPIRIT;

		resetEffect(pc, skillId);

		pc.setAdvenHp(pc.getBaseMaxHp() / 5);
		pc.setAdvenMp(pc.getBaseMaxMp() / 5);
		pc.addMaxHp(pc.getAdvenHp());
		pc.addMaxMp(pc.getAdvenMp());

		refreshPartyStatusIfNeeded(pc);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// icon=1607, tooltip/new=982, end=2180
		sendRestatBuffNoti(pc, skillId, durSec, 1607, 0, 982, 982, 2180, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPride(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.PRIDE;

		resetEffect(pc, skillId);

		final double percent = pc.getLevel() / 4.0;
		final int addHp = (int) Math.round(pc.getBaseMaxHp() * (percent * 0.01));

		pc.setMagicBuffHp(addHp);
		pc.addMaxHp(pc.getMagicBuffHp());

		refreshPartyStatusIfNeeded(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// icon=8854, tooltip/new=5264, end=5265
		sendRestatBuffNoti(pc, skillId, durSec, 8854, 0, 5264, 5264, 5265, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onGigantic(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.GIGANTIC;

		resetEffect(pc, skillId);

		final double percent = pc.getLevel() / 2.0;
		final int addHp = (int) Math.round(pc.getBaseMaxHp() * (percent * 0.01));

		pc.setMagicBuffHp(addHp);
		pc.addMaxHp(pc.getMagicBuffHp());

		refreshPartyStatusIfNeeded(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// icon=6168, tooltip/new=3918, end=3919
		sendRestatBuffNoti(pc, skillId, durSec, 6168, 0, 3918, 3918, 3919, true);
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onGuardBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final int skillId = L1SkillId.GUARD_BREAK;

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;

			resetEffect(pc, skillId);

			pc.getAC().addAc(10);
			sendSkillSoundOnce(pc, 9801);

			final int durSec = durationSecForBuff(su, skillId);
			applySkillEffectSec(pc, skillId, durSec);

			sendRestatBuffNoti(pc, skillId, durSec, 3187, 10, 5109, 5111, 5110, true);

			SC_OwnCharAttrDef.send(pc);
			return HandlerResult.HANDLED;
		}

		if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance || cha instanceof L1CompanionInstance) {
			final L1NpcInstance npc = (L1NpcInstance) cha;
			sendSkillSoundBroadcast(npc, 9801);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	private static HandlerResult onStrikerGale(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.STRIKER_GALE;

		resetEffect(pc, skillId);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		sendRestatBuffNoti(pc, skillId, durSec, 2357, 10, 1084, 1084, 2200, true);
		return HandlerResult.HANDLED;
	}

	// =========================
	// N_BUFF handlers
	// =========================

	private static HandlerResult onNBuffPvpDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_PVP_DMG;

		resetEffect(pc, skillId);

		pc.getResistance().addPVPweaponTotalDamage(1);
		pc.getResistance().addcalcPcDefense(1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffHpMp(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_HPMP;

		resetEffect(pc, skillId);

		pc.addMaxHp(50);
		pc.addMaxMp(50);
		pc.addWeightReduction(3);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_DMG;

		resetEffect(pc, skillId);

		pc.addDmgup(1);
		pc.addBowDmgup(1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffReduct(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_REDUCT;

		resetEffect(pc, skillId);

		pc.addDamageReductionByArmor(1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBlessWeapon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = Math.max(1, su.hdGetBuffIconDuration());

		// 쉐팡 있으면 안됨
		if (pc.hasSkillEffect(L1SkillId.SHADOW_FANG)) {
			return HandlerResult.HANDLED;
		}
		
		// 상호배타 제거 후 적용
		removeSkillEffects(pc, L1SkillId.BLESS_WEAPON, L1SkillId.HOLY_WEAPON, L1SkillId.ENCHANT_WEAPON);

		pc.setSkillEffect(L1SkillId.BLESS_WEAPON, durSec * 1000);
		pc.addDmgup(2);
		pc.addHitup(2);

		sendRestatBuffNoti(
				pc,
				L1SkillId.BLESS_WEAPON,
				durSec,
				728,
				728,
				0,
				857,
				857,
				2167,
				true
				);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEnchantWeapon(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    if (!(cha instanceof L1PcInstance)) {
	        return HandlerResult.HANDLED;
	    }

	    final L1PcInstance pc =
	            (L1PcInstance) cha;

	    if (hasAnySkillEffect(
	            pc,
	            L1SkillId.SHADOW_FANG,
	            L1SkillId.BLESS_WEAPON)) {

	        return HandlerResult.HANDLED;
	    }

	    final int durSec =
	            Math.max(
	                    1,
	                    su.hdGetBuffIconDuration());
	    removeSkillEffects(
	            pc,
	            L1SkillId.ENCHANT_WEAPON,
	            L1SkillId.HOLY_WEAPON);

	    pc.setSkillEffect(
	            L1SkillId.ENCHANT_WEAPON,
	            durSec * 1000);

	    pc.addDmgup(2);

	    sendRestatBuffNoti(
	            pc,
	            L1SkillId.ENCHANT_WEAPON,
	            durSec,
	            5265,
	            5265,
	            0,
	            3064,
	            0,
	            2239,
	            true);

	    return HandlerResult.HANDLED;
	}

	private static HandlerResult onHolyweapon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = Math.max(1, su.hdGetBuffIconDuration());

		removeSkillEffects(pc, L1SkillId.HOLY_WEAPON);
		
		if (hasAnySkillEffect(pc, L1SkillId.BLESS_WEAPON, L1SkillId.SHADOW_FANG, L1SkillId.ENCHANT_WEAPON)) {
			return HandlerResult.HANDLED;
		}

		// 효과
		pc.addDmgup(1);
		pc.addHitup(1);
		sendRestatBuffNoti(
				pc,
				L1SkillId.HOLY_WEAPON,
				durSec,
				777, // icon
				777,
				0,
				685, // tooltip
				0,    // new 없음
				718, // end
				true
				);
		pc.setSkillEffect(L1SkillId.HOLY_WEAPON, durSec * 1000);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onShadowFang(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = Math.max(1, su.hdGetBuffIconDuration());

		removeSkillEffects(pc,
				L1SkillId.SHADOW_FANG,
				L1SkillId.BLESS_WEAPON,
				L1SkillId.HOLY_WEAPON,
				L1SkillId.ENCHANT_WEAPON
				);

		// 효과
		pc.addDmgup(5);
		sendRestatBuffNoti(
				pc,
				L1SkillId.SHADOW_FANG,
				durSec,
				1128, // icon
				1128,
				0,
				1339, // tooltip
				0,    // new 없음
				2234, // end
				true
				);
		// 타이머 걸어야 유지됨
		pc.setSkillEffect(L1SkillId.SHADOW_FANG, durSec * 1000);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffSp(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_SP;

		resetEffect(pc, skillId);

		pc.getAbility().addSp(1);
		SC_SPMR.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffStun(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_STUN;

		resetEffect(pc, skillId);

		pc.getResistance().addSpecialResistance(eKind.ABILITY, 2);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffHold(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_HOLD;

		resetEffect(pc, skillId);

		pc.getResistance().addSpecialResistance(eKind.SPIRIT, 2);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffStr(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_STR;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedStr((byte) 1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffDex(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_DEX;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedDex((byte) 1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffInt(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_INT;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedInt((byte) 1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffWis(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_WIS;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedWis((byte) 1);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffWater(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_WATER;

		resetEffect(pc, skillId);

		pc.getResistance().addWater(5);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffWind(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_WIND;

		resetEffect(pc, skillId);

		pc.getResistance().addWind(5);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffEarth(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_EARTH;

		resetEffect(pc, skillId);

		pc.getResistance().addEarth(5);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffFire(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_FIRE;

		resetEffect(pc, skillId);

		pc.getResistance().addFire(5);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNBuffAllResist(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.N_BUFF_ALL_RESIST;

		resetEffect(pc, skillId);

		pc.getResistance().addFire(5);
		pc.getResistance().addEarth(5);
		pc.getResistance().addWater(5);
		pc.getResistance().addWind(5);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onScalesWaterDragon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.SCALES_WATER_DRAGON;

		resetScalesEffectIfPresent(pc, skillId);

		// ER +10
		addERAndNotify(pc, 10);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// tooltip=2271, end=2424, icon=3184, prio=10
		sendRestatBuffNoti(pc, skillId, durSec, 3184, 10, 2271, 0, 2424, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onScalesEarthDragon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.SCALES_EARTH_DRAGON;

		resetScalesEffectIfPresent(pc, skillId);
		// AC -3, MR +5
		pc.getAC().addAc(-3);
		pc.getResistance().addMr(5);

		// 갱신 패킷
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// tooltip=2267, end=2420, icon=3182, prio=10
		sendRestatBuffNoti(pc, skillId, durSec, 3182, 10, 2267, 0, 2420, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onScalesRindviorDragon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.SCALES_RINDVIOR_DRAGON;

		resetScalesEffectIfPresent(pc, skillId);
		// DG +10
		pc.addDg(10);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// tooltip=5270, new=5270, end=5271, icon=8887, prio=10
		sendRestatBuffNoti(pc, skillId, durSec, 8887, 10, 5270, 5270, 5271, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onScalesFireDragon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.SCALES_FIRE_DRAGON;

		resetScalesEffectIfPresent(pc, skillId);

		// 특수저항 + 히트
		pc.getResistance().addSpecialResistance(eKind.ABILITY, 5);
		pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
		pc.getResistance().addSpecialResistance(eKind.FEAR, 5);
		pc.addHitup(5);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		sendRestatBuffNoti(pc, skillId, durSec, 3078, 10, 2274, 0, 2427, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEaggleEye(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.EAGGLE_EYE;

		resetEffect(pc, skillId);

		pc.add_missile_critical_rate(2);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// icon=8490, prio=10, tooltip=5157, end=5158, new_str=0(미설정)
		sendRestatBuffNoti(pc, skillId, durSec, 8490, 10, 5157, 0, 5158, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onIllusionOgre(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.IllUSION_OGRE;

		resetEffect(pc, skillId);

		// 효과
		pc.addDmgup(4);
		pc.addHitup(4);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// noti: icon=3117, prio=10, tooltip=1340, new=1366, end=2212
		sendRestatBuffNoti(pc, skillId, durSec, 3117, 10, 1340, 1366, 2212, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onIllusionLich(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.IllUSION_LICH;

		resetEffect(pc, skillId);

		// 효과
		pc.getAbility().addSp(4);
		SC_SPMR.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// noti: icon=3115, prio=10, tooltip=1343, new=1369, end=1369(원본 그대로)
		sendRestatBuffNoti(pc, skillId, durSec, 3115, 10, 1343, 1369, 1369, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onIllusionDiamondGolem(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.IllUSION_DIAMONDGOLEM;

		resetEffect(pc, skillId);

		// 효과
		pc.getAC().addAc(-10);
		SC_OwnCharAttrDef.send(pc);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// noti: icon=3113, prio=10, tooltip=1373, new=1373, end=2214
		sendRestatBuffNoti(pc, skillId, durSec, 3113, 10, 1373, 1373, 2214, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onIllusionAvatar(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.IllUSION_AVATAR;

		resetEffect(pc, skillId);

		// 효과
		pc.addDmgup(10);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// noti: icon=3111, prio=10, tooltip=1377, new=1377, end=2215
		sendRestatBuffNoti(pc, skillId, durSec, 3111, 10, 1377, 1377, 2215, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCyclone(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 원본이 "cha"가 아니라 "_user"에 걸리는 형태라서 user 기준으로 처리
		final L1Character u = su.hdUser();
		if (!(u instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) u;

		final int skillId = L1SkillId.CYCLONE;
		resetEffect(pc, skillId);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// on=9190, off=9192, prio=10, tooltip/new=5446, end=5450
		sendRestatBuffNoti(pc, skillId, durSec, 9190, 9192, 10, 5446, 5446, 5450, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFocusSpirits(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character u = su.hdUser();
		if (!(u instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) u;

		final int skillId = L1SkillId.FOCUS_SPRITS;

		resetEffect(pc, skillId);
		pc.add_magic_critical_rate(5);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// icon=4832, prio=10, tooltip=5272, new=0, end=5273
		sendRestatBuffNoti(pc, skillId, durSec, 4832, 10, 5272, 0, 5273, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onLucifer(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance pc = su.hdPlayer();
		if (pc == null) return HandlerResult.NOT_HANDLED;

		if (pc.hasSkillEffect(L1SkillId.LUCIFER)) {
			pc.removeSkillEffect(L1SkillId.LUCIFER);
		}
		if (pc.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)) {
			pc.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
		}
		final int skillId = L1SkillId.LUCIFER;

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// noti
		sendRestatBuffNoti(
				pc,
				L1SkillId.LUCIFER,
				durSec,
				4503,  // icon
				10,    // priority
				5268,  // tooltip
				5268,  // new
				5269,  // end
				true
				);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBlowAttack(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance pc = su.hdPlayer();
		if (pc == null) return HandlerResult.NOT_HANDLED;

		if (pc.hasSkillEffect(L1SkillId.BLOW_ATTACK)) {
			pc.removeSkillEffect(L1SkillId.BLOW_ATTACK);
		}

		final int skillId = L1SkillId.BLOW_ATTACK;
		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		sendRestatBuffNoti(
				pc,
				L1SkillId.BLOW_ATTACK,
				durSec,
				8843, // icon
				10,   // priority
				5266, // tooltip
				5266, // new
				5267, // end
				true
				);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEnchantAcurucy(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		if (pc.hasSkillEffect(L1SkillId.ENCHANT_ACURUCY)) {
			pc.removeSkillEffect(L1SkillId.ENCHANT_ACURUCY);
		}

		final int skillId = L1SkillId.ENCHANT_ACURUCY;
		// 원본 유지: 근거리 명중 +5
		pc.addHitup(5);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		SC_SystemMessage.send(pc, "인챈트 어큐러시: 근거리 명중+5");

		sendRestatBuffNoti(
				pc,
				L1SkillId.ENCHANT_ACURUCY,
				durSec,
				9487, // icon
				0,    // priority
				5888, // tooltip
				0,    // new 
				0,    // end 없음
				true
				);
		return HandlerResult.HANDLED;
	}
	// ===== Cube common =====
	private static final int CUBE_RANGE = 18;

	@FunctionalInterface
	private interface PcApplier {
		void apply(L1PcInstance pc, boolean isPartyMember);
	}

	private static int skillBuffDurationSecFromTemplate(final int skillId, final int fallbackSec) {
		try {
			final L1Skills s = SkillsTable.getInstance().getTemplate(skillId);
			final int d = (s != null ? s.getBuffDuration() : 0);
			return (d > 0 ? d : (fallbackSec > 0 ? fallbackSec : 1));
		} catch (Throwable t) {
			return (fallbackSec > 0 ? fallbackSec : 1);
		}
	}

	private static HandlerResult handleCubePartyBuff(
			final L1SkillUse su,
			final int skillId,
			final int iconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final PcApplier applier
			) {
		final L1PcInstance caster = su.hdPlayer(); // 큐브는 시전자 기반
		if (caster == null) return HandlerResult.NOT_HANDLED;

		final int castGfx = su.hdSkillCastGfx();
		final int iconDurSec = durationSecForBuff(su, skillId);
		final int buffDurSec = skillBuffDurationSecFromTemplate(skillId, iconDurSec); // 실제 버프 시간(기존 _skill.getBuffDuration 대응)

		// 1) 시전자 본인 적용 (파티 없어도 무조건 적용되게)
		applier.apply(caster, false);
		caster.send_effect(castGfx);
		applySkillEffectSec(caster, skillId, buffDurSec);
		sendRestatBuffNoti(
				caster,
				skillId,
				iconDurSec,
				iconId,
				iconPriority,
				tooltipStrId,
				newStrId,
				endStrId,
				true
				);

		// 2) 파티원 중 "화면상(가시범위)" 에 있는 멤버만 적용
		final L1Party party = caster.getParty();
		if (party == null) return HandlerResult.HANDLED;

		for (L1PcInstance m : L1World.getInstance().getVisiblePlayer(caster, CUBE_RANGE)) {
			if (m == null) continue;
			if (!party.isMember(m)) continue;

			applier.apply(m, true);
			m.send_effect(castGfx);
			applySkillEffectSec(m, skillId, buffDurSec);
			sendRestatBuffNoti(
					m,
					skillId,
					iconDurSec,
					iconId,
					iconPriority,
					tooltipStrId,
					newStrId,
					endStrId,
					true
					);
		}
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onDeathHeal(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (!(target instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) target;
		final Random rnd = su.hdRandom();

		final int chance = (rnd != null ? rnd.nextInt(10) + 1 : (int)(Math.random() * 10) + 1);

		if (pc.hasSkillEffect(L1SkillId.DEATH_HEAL)) {
			SC_InventoryIcon.icoReset(pc, L1SkillId.DEATH_HEAL, chance, false);
			pc.removeSkillEffect(L1SkillId.DEATH_HEAL);
		} else {
			SC_InventoryIcon.icoNew(pc, L1SkillId.DEATH_HEAL, chance, false);
		}

		pc.setSkillEffect(L1SkillId.DEATH_HEAL, chance * 1000);

		SC_SkillSound.send(pc, pc.getId(), 14501);
		SC_SkillSound.broadcastFast(pc, pc.getId(), 14501);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHunterBless(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (!(target instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) target;

		// (아이콘만 갱신)
		if (pc.hasSkillEffect(L1SkillId.HUNTER_BLESS)) {
			SC_InventoryIcon.icoReset(pc, L1SkillId.HUNTER_BLESS, 4992, 1800L, true);
		} else {
			SC_InventoryIcon.icoNew(pc, L1SkillId.HUNTER_BLESS, 4992, 1800L, true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCurePoison(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		target.curePoison();
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onSeonghyeolBuff(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (!(target instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) target;

		if (pc.hasSkillEffect(L1SkillId.성혈버프)) {
			pc.removeSkillEffect(L1SkillId.성혈버프);
		}

		pc.addMaxHp(300);
		pc.addMaxMp(150);
		pc.addDmgup(3);
		pc.addBowDmgup(3);
		pc.getAbility().addSp(3);
		pc.getAC().addAc(-10);
		pc.getResistance().addMr(15);

		pc.getResistance().addPVPweaponTotalDamage(5);
		pc.getResistance().addcalcPcDefense(5);

		SC_InventoryIcon.icoNew(pc, L1SkillId.성혈버프, 3944, 3600, true);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);


		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDetectionLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1NpcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) 
			return HandlerResult.HANDLED;

		final L1NpcInstance npc = (L1NpcInstance) cha;
		if (npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
			npc.appearOnGround(caster);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCounterDetection(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		int dmg = 0;

		if (cha instanceof L1PcInstance) {
			if (magic != null) {	
				dmg = caster.getId() != cha.getId() ? magic.calcMagicDamage(L1SkillId.COUNTER_DETECTION) : 0;
			}	
		} else if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance npc = (L1MonsterInstance) cha;
			if (npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
				if (caster != null) 
					npc.appearOnGround(caster);
				dmg = 0;
			} else {
				dmg = 0;
			}
		} else {
			dmg = 0;
		}
		su.hdSetDamage(dmg);   
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMindBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;
		if (target.getZoneType() == 1) {
			return HandlerResult.HANDLED;
		}
		if (target.getCurrentMp() < 5) {
			return HandlerResult.HANDLED;
		}
		target.setCurrentMp(target.getCurrentMp() - 5);

		su.hdSetDamage(15); 
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHealFamily(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			pc.killSkillEffectTimer(L1SkillId.WATER_LIFE);
			SC_PacketBox.send(pc, SC_PacketBox.DEL_ICON);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onVampiricTouchLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final int dmg = su.hdGetDamage();
		if (dmg > 0) {
			su.hdSetHeal(dmg / 2);   
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMobBerserkers(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1NpcInstance) {
			cha.setMoveSpeed(1);
			cha.setBraveSpeed(1);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onUncannyDodge(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			((L1PcInstance) cha).addDg(30);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFear(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			((L1PcInstance) cha).addDg(-25);
		}
		return HandlerResult.HANDLED;
	}

	// ===== CUBE_AVATAR =====
	private static HandlerResult onCubeAvatar(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleCubePartyBuff(
				su,
				L1SkillId.CUBE_AVATAR,
				5322, // icon
				10,   // priority
				3073, // tooltip
				3073, // new
				0,    // end
				(pc, isPartyMember) -> {
					resetEffect(pc, L1SkillId.CUBE_AVATAR);
					if (isPartyMember) {
						resetEffect(pc, L1SkillId.IllUSION_AVATAR);
						pc._CubeEffect = true;
					}
				}
				);
	}

	// ===== CUBE_RICH =====
	private static HandlerResult onCubeRich(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleCubePartyBuff(su, L1SkillId.CUBE_RICH, 5309, 10, 1348, 1348, 0,
				(pc, isPartyMember) -> {
					if (pc.hasSkillEffect(L1SkillId.IllUSION_LICH)) {
						return;
					}

					resetEffect(pc, L1SkillId.CUBE_RICH);
					pc.getAbility().addSp(2);
					SC_SPMR.send(pc);

					if (isPartyMember) pc._CubeEffect = true;
				}
				);
	}

	// ===== CUBE_GOLEM =====
	private static HandlerResult onCubeGolem(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleCubePartyBuff(
				su,
				L1SkillId.CUBE_GOLEM,
				5314,
				10,
				3075,
				3075,
				0,
				(pc, isPartyMember) -> {
					// 충돌(다이아골렘/일루션골렘)은 "해당 인원만 스킵" (기존 버프도 건드리지 않음)
					if (pc.hasSkillEffect(L1SkillId.IllUSION_DIAMONDGOLEM)) {
						return;
					}

					resetEffect(pc, L1SkillId.CUBE_GOLEM);

					// AC -8
					pc.getAC().addAc(-8);
					SC_OwnCharAttrDef.send(pc);

					if (isPartyMember) pc._CubeEffect = true;
				}
				);
	}

	// ===== CUBE_OGRE =====
	private static HandlerResult onCubeOgre(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleCubePartyBuff(
				su,
				L1SkillId.CUBE_OGRE,
				5312,
				10,
				3074,
				3074,
				0,
				(pc, isPartyMember) -> {
					resetEffect(pc, L1SkillId.CUBE_OGRE);

					resetEffect(pc, L1SkillId.IllUSION_OGRE);

					pc.addDmgup(4);
					pc.addHitup(4);

					if (isPartyMember) pc._CubeEffect = true;
				}
				);
	}

	private static HandlerResult onElementalSummon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int attr = pc.getElfAttr();
		if (attr == 0) {
			return HandlerResult.HANDLED;
		}

		if (!canRecallElemental(pc)) {
			SC_ServerMessage.send(pc, 79);
			return HandlerResult.HANDLED;
		}

		int summonCount = countSummons(pc);

		if (summonCount == 1) {
			deleteAllSummons(pc);
			summonCount = 0;
		}

		if (summonCount != 0) {
			SC_ServerMessage.send(pc, 319);
			return HandlerResult.HANDLED;
		}

		final int skillId = su.hdSkillId();
		final boolean mythPoly = isOneOfSprite(pc.getCurrentSpriteId(), MYTH_SPRITES);

		final int[] candidates = pickElementalCandidates(skillId, mythPoly);
		final int summonId = pickSummonIdByAttr(su.hdRandom(), candidates, attr);

		if (summonId == 0) {
			return HandlerResult.HANDLED;
		}

		final L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonId);
		if (npcTemp == null) {
			return HandlerResult.HANDLED;
		}

		new L1SummonInstance(npcTemp, pc);
		return HandlerResult.HANDLED;
	}
	
	private static HandlerResult onTeleport(
			L1SkillUse su,
			L1Character cha,
			L1Magic magic,
			int undeadType,
			Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.NOT_HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = su.hdSkillId();
		TeleportUseService.handleSkillTeleport(
				pc,
				skillId,
				su.hdBookmarkX(),
				su.hdBookmarkY(),
				su.hdBookmarkMapId()
				);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTeleportToMather(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		if (pc.getMap().isEscapable() || pc.isGm()) {
			pc.start_teleport(33051, 32337, 4, 5, 169, true, true);
		} else {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			SC_ServerMessage.send(pc, 647);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBringStone(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int itemObjId = su.hdItemObjId();
		if (itemObjId <= 0) return HandlerResult.HANDLED;

		final L1ItemInstance item = pc.getInventory().getItem(itemObjId);
		if (item == null || item.getItem() == null) return HandlerResult.HANDLED;

		final int chance = su.hdRandom().nextInt(100) + 1;

		final int dark  = (int) (10 + (pc.getLevel() * 0.8) + (pc.getAbility().getTotalWis() - 6) * 1.2);
		final int brave = (int) (dark / 2.1);
		final int wise  = (int) (brave / 2.0);
		final int kayser= (int) (wise / 1.9);

		final int itemId = item.getItem().getItemId();

		if (itemId == 40320) {
			pc.getInventory().removeItem(item, 1);
			if (dark >= chance) {
				pc.getInventory().storeItem(40321, 1);
				SC_ServerMessage.send(pc, 403, "$2475");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40321) {
			pc.getInventory().removeItem(item, 1);
			if (brave >= chance) {
				pc.getInventory().storeItem(40322, 1);
				SC_ServerMessage.send(pc, 403, "$2476");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40322) {
			pc.getInventory().removeItem(item, 1);
			if (wise >= chance) {
				pc.getInventory().storeItem(40323, 1);
				SC_ServerMessage.send(pc, 403, "$2477");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40323) {
			pc.getInventory().removeItem(item, 1);
			if (kayser >= chance) {
				pc.getInventory().storeItem(40324, 1);
				SC_ServerMessage.send(pc, 403, "$2478");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onImmuneToHarm(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;

		final L1PcInstance target = (L1PcInstance) cha;
		final L1PcInstance caster = su.hdPlayer(); // 원본의 _player 대응

		// 1) 루시퍼/이뮨 토글
		if (target.hasSkillEffect(L1SkillId.LUCIFER)) {
			target.removeSkillEffect(L1SkillId.LUCIFER);
		} else if (target.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)) {
			target.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
		}
		sendSkillSoundOnce(target, 15959);

		if (caster != null) {
			if (caster.getId() == target.getId()) {
				target.setLastImmuneLevel(caster.getLevel());
			} else {
				// 타인 시전
				target.setLastImmuneLevel(caster.getLevel());
			}
		}
		// 4) 지속시간
		final int buffDurSec = durationSecForBuff(su, L1SkillId.IMMUNE_TO_HARM);
		applySkillEffectSec(target, L1SkillId.IMMUNE_TO_HARM, buffDurSec);

		// 5) UI NOTI 
		int iconDurSec = 0;
		try { iconDurSec = su.hdGetBuffIconDuration(); } catch (Throwable ignore) {}
		if (iconDurSec <= 0) iconDurSec = buffDurSec;

		sendRestatBuffNoti(
				target,
				L1SkillId.IMMUNE_TO_HARM,
				iconDurSec,
				1562, // on/off icon
				10,   // priority
				966,  // tooltip
				314,  // new
				315,  // end
				true
				);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onSummonMonster(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		// 원본: 이미 SM 상태면 319
		if (pc.isSM()) {
			SC_ServerMessage.send(pc, 319);
			return HandlerResult.HANDLED;
		}

		final boolean canRecall =
				((pc.getMap().isRecallPets() && !pc.isInWarArea()
						&& pc.getMapId() != 781 && pc.getMapId() != 782)
						|| pc.isGm());

		if (!canRecall) {
			SC_ServerMessage.send(pc, 79);
			return HandlerResult.HANDLED;
		}

		if (pc.getInventory().checkEquipped(20284)) {
			// 원본: summonMonster(pc, _bookmark_x);
			su.hdSummonMonster(pc, su.hdBookmarkX());
		} else {
			final L1Npc npcTemp = NpcTable.getInstance().getTemplate(7320137);
			if (npcTemp != null) {
				final L1SummonInstance summon = new L1SummonInstance(npcTemp, pc);
				summon.setPetcost(0);
			}
		}

		pc.setSM(true);
		return HandlerResult.HANDLED;
	}

	// ===== tame/zombie helpers =====
	private static int calcPetCost(final L1PcInstance pc) {
		if (pc == null) return 0;
		int petcost = 0;
		try {
			for (Object o : pc.getPetList().values()) {
				if (o instanceof L1NpcInstance) {
					petcost += ((L1NpcInstance) o).getPetcost();
				}
			}
		} catch (Throwable t) {
			// ignore
		}
		return petcost;
	}

	private static int calcRemainingCharisma(final L1PcInstance pc) {
		if (pc == null) return 0;

		int cha = 0;
		try {
			cha = pc.getAbility().getTotalCha();
			if (pc.isElf()) cha += 12;
			else if (pc.isWizard()) cha += 6;
		} catch (Throwable t) {
			// ignore
		}
		cha -= calcPetCost(pc);
		return cha;
	}

	private static HandlerResult tryCreateSummonByCharisma(
			final L1PcInstance caster,
			final L1NpcInstance targetNpc,
			final boolean isZombie
			) {
		if (caster == null || targetNpc == null) return HandlerResult.NOT_HANDLED;

		final int charisma = calcRemainingCharisma(caster);
		if (charisma >= 6) {
			new L1SummonInstance(targetNpc, caster, isZombie);
			return HandlerResult.HANDLED;
		} else {
			SC_ServerMessage.send(caster, 319);
			return HandlerResult.HANDLED;
		}
	}

	// ===== TAMING_MONSTER =====
	private static HandlerResult onTamingMonster(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 원본: (_calcType==PC_NPC||NPC_NPC) 안에서 monster tamable 일 때만
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		final L1MonsterInstance mon = (L1MonsterInstance) cha;
		if (!mon.getNpcTemplate().isTamable()) return HandlerResult.NOT_HANDLED;

		return tryCreateSummonByCharisma(caster, mon, false);
	}

	// ===== CREATE_ZOMBIE =====
	private static HandlerResult onCreateZombie(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		// 원본은 tamable 체크 없이 _targetNpc로 소환(좀비)했는데,
		// 여기선 대상이 monster면 그대로 진행
		final L1MonsterInstance mon = (L1MonsterInstance) cha;
		return tryCreateSummonByCharisma(caster, mon, true);
	}

	// ===== WEAK_ELEMENTAL =====
	private static HandlerResult onWeakElemental(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		final L1Npc npcTemp = ((L1MonsterInstance) cha).getNpcTemplate();
		if (npcTemp == null) return HandlerResult.HANDLED;

		final int weakAttr = npcTemp.get_weakAttr();

		// 원본 사운드 그대로
		if ((weakAttr & 1) == 1) SC_SkillSound.broadcastFast(cha, cha.getId(), 2169);
		if ((weakAttr & 2) == 2) SC_SkillSound.broadcastFast(cha, cha.getId(), 2167);
		if ((weakAttr & 4) == 4) SC_SkillSound.broadcastFast(cha, cha.getId(), 2166);
		if ((weakAttr & 8) == 8) SC_SkillSound.broadcastFast(cha, cha.getId(), 2168);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onComa(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try { skillId = su.hdSkillId(); }
		catch (Throwable ignore) { return HandlerResult.NOT_HANDLED; }

		switch (skillId) {
		case L1SkillId.COMA_A:
			pc.getAbility().addAddedCon(1);
			pc.getAbility().addAddedDex(5);
			pc.getAbility().addAddedStr(5);
			pc.addHitRate(3);
			pc.getAC().addAc(-3);
			break;

		case L1SkillId.COMA_B:
			pc.getAbility().addSp(1);
			pc.getAbility().addAddedCon(3);
			pc.getAbility().addAddedDex(5);
			pc.getAbility().addAddedStr(5);
			pc.addHitRate(5);
			pc.getAC().addAc(-8);
			try { SC_SPMR.send(pc); } catch (Throwable ignore) {}
			break;

		default:
			return HandlerResult.NOT_HANDLED;
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMiso(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try { skillId = su.hdSkillId(); }
		catch (Throwable ignore) { return HandlerResult.NOT_HANDLED; }

		switch (skillId) {
		case L1SkillId.miso1: {
			if (pc.hasSkillEffect(L1SkillId.miso1)) {
				SC_InventoryIcon.icoReset(pc, L1SkillId.miso1, 4995, 1800L, true);
			} else {
				SC_InventoryIcon.icoNew(pc, L1SkillId.miso1, 4995, 1800L, true);
			}
			break;
		}
		case L1SkillId.miso2: {
			if (pc.hasSkillEffect(L1SkillId.miso2)) {
				SC_InventoryIcon.icoReset(pc, L1SkillId.miso2, 4996, 1800L, true);
			} else {
				pc.addMaxHp(100);
				pc.getResistance().addMr(10);
				pc.addHpr(2);
				try { SC_SPMR.send(pc); } catch (Throwable ignore) {}
				try { SC_OwnCharAttrDef.send(pc); } catch (Throwable ignore) {}
				SC_InventoryIcon.icoNew(pc, L1SkillId.miso2, 4996, 1800L, true);
			}
			break;
		}
		case L1SkillId.miso3: {
			if (pc.hasSkillEffect(L1SkillId.miso3)) {
				SC_InventoryIcon.icoReset(pc, L1SkillId.miso3, 4997, 1800L, true);
			} else {
				pc.addDmgup(3);
				pc.addBowDmgup(3);
				pc.addMaxMp(50);
				pc.getAbility().addSp(3);
				pc.addMpr(2);
				try { SC_SPMR.send(pc); } catch (Throwable ignore) {}
				try { SC_OwnCharAttrDef.send(pc); } catch (Throwable ignore) {}
				SC_InventoryIcon.icoNew(pc, L1SkillId.miso3, 4997, 1800L, true);
			}
			break;
		}

		default:
			return HandlerResult.NOT_HANDLED;
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onClanBuff(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try { skillId = su.hdSkillId(); }
		catch (Throwable ignore) { return HandlerResult.NOT_HANDLED; }

		if (pc.getClan() == null) return HandlerResult.HANDLED;

		final int blessIdx = Math.max(0, pc.getClan().getBless() - 1);
		final int[] times = pc.getClan().getBuffTime();
		final int buffTime = (times != null && blessIdx < times.length ? times[blessIdx] : 0);

		switch (skillId) {
		case L1SkillId.CLAN_BUFF1: { // 일반 공격 태세
			pc.addDmgup(2);
			pc.addBowDmgup(2);
			SC_ActionUI2.send(pc, 2724, buffTime, 7, 7233, 4650);
			SC_ServerMessage.send(pc, 4618, "$22503");
			SC_SkillSound.send(pc, pc.getId(), 14482);
			break;
		}
		case L1SkillId.CLAN_BUFF2: { // 일반 방어 태세
			pc.getAC().addAc(-3);
			try { SC_OwnCharAttrDef.send(pc); } catch (Throwable ignore) {}
			SC_ActionUI2.send(pc, 2725, buffTime, 7, 7235, 4651);
			SC_ServerMessage.send(pc, 4618, "$22504");
			SC_SkillSound.send(pc, pc.getId(), 14482);
			break;
		}
		case L1SkillId.CLAN_BUFF3: { // 전투 공격 태세
			SC_ActionUI2.send(pc, 2726, buffTime, 7, 7237, 4652);
			SC_ServerMessage.send(pc, 4618, "$22505");
			SC_SkillSound.send(pc, pc.getId(), 14482);
			break;
		}
		case L1SkillId.CLAN_BUFF4: { // 전투 방어 태세
			SC_ActionUI2.send(pc, 2727, buffTime, 7, 7239, 4653);
			SC_ServerMessage.send(pc, 4618, "$22506");
			SC_SkillSound.send(pc, pc.getId(), 14482);
			break;
		}
		default:
			return HandlerResult.NOT_HANDLED;
		}

		return HandlerResult.HANDLED;
	}

	// ===== RETURN_TO_NATURE =====
	private static HandlerResult onReturnToNature(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1SummonInstance) {
			final L1SummonInstance summon = (L1SummonInstance) cha;
			SC_SkillSound.broadcastFast(summon, summon.getId(), 2245);
			summon.returnToNature();
			return HandlerResult.HANDLED;
		}

		final L1Character user = su.hdUser();
		if (user instanceof L1PcInstance) {
			SC_ServerMessage.send(((L1PcInstance) user), 79);
			return HandlerResult.HANDLED;
		}
		return HandlerResult.HANDLED;
	}

	// ===== POWERRIP =====
	private static HandlerResult onPowerRip(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		final int dur = SkillUseHelper.pickMillis(SkillUseHelper.GRIP_OFFICIAL);

		if (target instanceof L1PcInstance) {
			spawnEffectAtTarget(target, 9415, dur);
			final L1PcInstance pc = (L1PcInstance) target;

			pc.setSkillEffect(L1SkillId.POWERRIP, dur);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_RIP, true);
			return HandlerResult.HANDLED;

		} else if (target instanceof L1MonsterInstance
				|| target instanceof L1SummonInstance
				|| target instanceof L1PetInstance
				|| target instanceof L1CompanionInstance) {

			final L1NpcInstance npc = (L1NpcInstance) target;
			if (!npc.trySetSkillEffect(L1SkillId.POWERRIP, dur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC POWERRIP admission exhausted");
			}
			spawnEffectAtTarget(target, 9415, dur);
			npc.set발묶임상태(true);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	private static ArrayList<L1PcInstance> collectPartyMembersInSight(
	        final L1PcInstance center,
	        final int range,
	        final boolean includeSelf) {

	    final ArrayList<L1PcInstance> list = new ArrayList<>(8);

	    if (center == null) {
	        return list;
	    }

	    if (includeSelf) {
	        list.add(center);
	    }

	    final L1Party party = center.getParty();

	    if (party == null) {
	        return list;
	    }

	    for (L1PcInstance p : L1World.getInstance().getVisiblePlayer(center, range)) {
	        if (p == null) {
	            continue;
	        }
	        if (p == center) {
	            continue;
	        }

	        if (!party.isMember(p)) {
	            continue;
	        }

	        list.add(p);
	    }

	    return list;
	}

	// ===== 인벤 아이콘 + 버프 리프레시(UI 정책 그대로 유지) =====
	private static void refreshInventoryBuffIcon(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;

		if (pc.hasSkillEffect(skillId)) {
			pc.removeSkillEffect(skillId); // 종료(원복) 먼저 태움
			SC_InventoryIcon.icoReset(pc, skillId, durSec, true);
		} else {
			SC_InventoryIcon.icoNew(pc, skillId, durSec, true);
		}
	}

	private static int clampImpactUpByLevel(final int lvl) {
		// 5, lvl>=80이면 5+(lvl-79) 를 5~10으로 clamp
		// => lvl 80=6 ... lvl84=10, 이후 10 고정
		int up = 5;
		if (lvl >= 80) up = 5 + (lvl - 79);
		if (up < 5) up = 5;
		if (up > 10) up = 10;
		return up;
	}

	// =========================
	// IMPACT (파티 시야 18셀 버프)
	// =========================
	private static HandlerResult onImpact(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;


		final int durSec = durationSecForBuff(su, L1SkillId.IMPACT);
		final int impactUp = clampImpactUpByLevel(caster.getLevel());

		// 본인 + (파티면) 시야 18셀 파티원
		final ArrayList<L1PcInstance> members = collectPartyMembersInSight(caster, 18, true);

		for (L1PcInstance member : members) {
			if (member == null) continue;

			// 기존 로직: 있으면 remove 후 icoReset, 없으면 icoNew
			refreshInventoryBuffIcon(member, L1SkillId.IMPACT, durSec);

			// 적용
			member.setImpactUp(impactUp);
			member.getResistance().addSpecialPierce(eKind.ALL, impactUp);
			applySkillEffectSec(member, L1SkillId.IMPACT, durSec);

		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhantasm(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, true);
		}
		cha.setSleeped(true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onWindShackleLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;


		int skillid = su.hdSkillId();

		L1PcInstance pc = (L1PcInstance) cha;
		final int dur = su.hdGetBuffIconDuration(); 
		SC_SkillIconWindShackle.send(pc, pc.getId(), dur);

		pc.setSkillEffect(skillid, dur * 1000);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTurnUndead(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (undeadType == 1 || undeadType == 3) {
			su.hdSetDamage(cha.getCurrentHp());

			L1Character target = su.hdTarget();
			if (target instanceof L1MonsterInstance) {
				L1MonsterInstance m = (L1MonsterInstance) target;
				L1Character user = su.hdUser();
				if (user instanceof L1PcInstance) {
					m.setHate(user, 1);
				}
			}
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onManaDrain(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		int chance = su.hdRandom().nextInt(5) + 10;

		L1Character user = su.hdUser();
		L1PcInstance pc = su.hdPlayer();
		int drainMana = chance + (user.getAbility().getTotalInt() / 2);

		if (cha.getCurrentMp() < drainMana) {
			drainMana = cha.getCurrentMp();
		}

		if (user instanceof L1PcInstance) {
			SC_SkillSound.send(pc, pc.getId(), 2171);
			SC_SkillSound.broadcastFast(pc, pc.getId(), 2171);
		} else {
			SC_SkillSound.broadcastFast(user, user.getId(), 2171);
		}

		su.hdSetDrainMana(drainMana);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onWeaponBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final int calcType = su.hdCalcType();
		if (calcType == su.hdCalcTypePcPc() || calcType == su.hdCalcTypeNpcPc()) {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				L1ItemInstance weapon = pc.getWeapon();
				if (weapon != null) {
					L1Character user = su.hdUser();
					int weaponDamage = su.hdRandom().nextInt(user.getAbility().getTotalInt() / 3) + 1;
					SC_ServerMessage.send(pc, 268, weapon.getLogName());
					pc.getInventory().receiveDamage(weapon, weaponDamage);
				}
			}
		} else {
			if (cha instanceof L1NpcInstance) {
				((L1NpcInstance) cha).setWeaponBreaked(true);
			}
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFogOfSleeping(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillSound.send(pc, pc.getId(), 9218);
			SC_SkillSound.send(pc, pc.getId(), 760);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, true);
		}
		SC_SkillSound.broadcastFast(cha, cha.getId(), 9218);
		SC_SkillSound.broadcastFast(cha, cha.getId(), 760);
		cha.setSleeped(true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onStatusFreeze(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		L1PcInstance pc = (L1PcInstance) cha;
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
		return HandlerResult.HANDLED;
	}

	// =========================
	// GRACE_AVATAR (자기 + 파티 시야 18셀)
	// =========================
	private static HandlerResult onGraceAvatar(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 원본은 cha 기준이었음. (대체로 자기 자신)
		final L1PcInstance pc = (cha instanceof L1PcInstance) ? (L1PcInstance) cha : su.hdPlayer();
		if (pc == null) return HandlerResult.NOT_HANDLED;

		final int durSec = 15;

		// ===== 버프 아이콘 NOTI =====
		sendRestatBuffNotiDualIcon(
				pc,
				L1SkillId.GRACE_AVATAR,
				durSec,
				7427,   // on icon
				7427,   // off icon (같음)
				10,     // priority
				4734,   // tooltip
				4734,   // new
				4741,   // end
				true
				);

		// ===== 본인 적용 =====
		pc.setGraceLv(1); // 강제로 1
		final int resistance = 5 + pc.getGraceLv();
		pc.getResistance().addSpecialResistance(eKind.ALL, resistance);

		applySkillEffectSec(pc, L1SkillId.GRACE_AVATAR, durSec);

		// 사운드
		sendSkillSoundOnce(pc, 14495);

		// ===== 파티원 적용(파티일 때만, 시야 18셀) =====
		final L1Party party = pc.getParty();
		if (party == null) return HandlerResult.HANDLED;

		for (L1PcInstance member : L1World.getInstance().getVisiblePlayer(pc, 18)) {
			if (member == null) continue;
			if (member.getId() == pc.getId()) continue;
			if (!party.isMember(member)) continue;

			// 파티원도 동일 NOTI
			sendRestatBuffNotiDualIcon(
					member,
					L1SkillId.GRACE_AVATAR,
					durSec,
					7427,
					7427,
					10,
					4734,
					4734,
					4741,
					true
					);
			member.setGraceLv(1);
			final int r2 = 5 + member.getGraceLv();
			member.getResistance().addSpecialResistance(eKind.ALL, r2);

			applySkillEffectSec(member, L1SkillId.GRACE_AVATAR, durSec);

			// 파티원 알림 메시지
			SC_ServerMessage.send(member, 4734);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onResistMagic(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		// 재시전 누적 방지(종료 원복이 확실하지 않은 서버들에서 특히 중요)
		if (pc.hasSkillEffect(L1SkillId.RESIST_MAGIC)) {
			pc.removeSkillEffect(L1SkillId.RESIST_MAGIC);
		}

		pc.getResistance().addMr(10);
		SC_SPMR.send(pc);

		final int durSec = durationSecForBuff(su, L1SkillId.RESIST_MAGIC);
		applySkillEffectSec(pc, L1SkillId.RESIST_MAGIC, durSec);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onGodBuff(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(-2);
		pc.addHitup(3);
		pc.addMaxHp(20);
		pc.addMaxMp(13);
		pc.getResistance().addSpecialResistance(eKind.SPIRIT, 10);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onIronSkin(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(-10);
		SC_SkillIconShield.send(pc, 10, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFireShield(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(-4);
		SC_SkillIconShield.send(pc, 4, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhysicalEnchantStr(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {	  
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedStr((byte) 5);
		SC_Strup.send(pc, pc, 5, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhysicalEnchantDex(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedDex((byte) 5);
		SC_Dexup.send(pc, pc, 5, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEarthWeapon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addHitup(4);
		pc.addDmgup(2);

		return HandlerResult.HANDLED;
	}

	/** REDUCE_WEIGHT / DECREASE_WEIGHT 공용 */
	private static HandlerResult onReduceWeightLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addWeightReduction(180);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCounterMirror(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addWeightReduction(300);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHurricane(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		SC_SkillBrave.send(pc, pc.getId(), 9, su.hdGetBuffIconDuration());
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 9, 0);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFocusWave(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		if (pc.hasSkillEffect(L1SkillId.FOCUS_WAVE)) {
			pc.removeSkillEffect(L1SkillId.FOCUS_WAVE);
		}
		pc.set_focus_wave_level(1);

		SC_SkillBrave.send(pc, pc.getId(), 10, su.hdGetBuffIconDuration());
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 10, 0);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onSandStorm(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		SC_SkillBrave.send(pc, pc.getId(), 1, su.hdGetBuffIconDuration());
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 1, 0);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDancingBlades(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		SC_SkillBrave.send(pc, pc.getId(), 1, su.hdGetBuffIconDuration());
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 1, 0);
		SC_SkillIconAura.send(pc, 154, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCurseBlindLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		
		boolean is_floating_eye = pc.hasSkillEffect(su.hdStatusFloatingEye());
		pc.sendPackets(is_floating_eye ? 2 : 1);
		if (is_floating_eye && !pc.isBlind()) {
			pc.showFloatingEyeToInvis();
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDarkBlind(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (cha instanceof L1PcInstance) {
			final L1PcInstance target = (L1PcInstance) cha;
			SC_Paralysis.send(target, SC_Paralysis.TYPE_SLEEP, true);
		}
		cha.setSleeped(true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCursePoisonLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();
		L1DamagePoison.doInfection(user, cha, 3000, 5, false, 7000);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCurseParalyzeLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;
		L1CurseParalysis.curse(cha, su.hdSkillId()); // 원본: _skillId
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onWeaknessLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		pc.addDmgup(-5);
		pc.addHitup(-1);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDiseaseLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		pc.addHitup(-6);
		pc.getAC().addAc(12);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHorrorOfDeath(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		pc.getAbility().addAddedStr((byte) -3);
		pc.getAbility().addAddedInt((byte) -3);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPanic(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		pc.getAbility().addAddedStr((byte) -1);
		pc.getAbility().addAddedDex((byte) -1);
		pc.getAbility().addAddedCon((byte) -1);
		pc.getAbility().addAddedInt((byte) -1);
		pc.getAbility().addAddedWis((byte) -1);
		pc.getAbility().addAddedCha((byte) -1);
		pc.resetBaseMr();
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMobBasillOrCoca(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (!cha.hasSkillEffect(L1SkillId.MOB_COCA) && !cha.hasSkillEffect(L1SkillId.MOB_BASILL)) {
			L1CurseParalysis.curse(cha, su.hdSkillId());
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPatience(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(2);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEarthGuardian(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(2);
		SC_SkillIconShield.send(pc, 7, su.hdGetBuffIconDuration());
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onResistElemental(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addAllNaturalResistance(10);
		SC_OwnCharAttrDef.send(pc);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBodyToMind(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.setCurrentMp(pc.getCurrentMp() + 2);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBloodySoul(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.setCurrentMp(pc.getCurrentMp() + 15);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onElementalProtection(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		int attr = pc.getElfAttr();
		if (attr == 1) pc.getResistance().addEarth(50);
		else if (attr == 2) pc.getResistance().addFire(50);
		else if (attr == 4) pc.getResistance().addWater(50);
		else if (attr == 8) pc.getResistance().addWind(50);

		pc.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
		SC_OwnCharAttrDef.send(pc);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onInvisibilityWithCastleTowerBlock(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = su.hdSkillId(); // 또는 전달받는 스킬ID
		if (skillId != L1SkillId.INVISIBILITY && skillId != L1SkillId.BLIND_HIDING) {
			return HandlerResult.NOT_HANDLED;
		}

		if (pc.is_combat_field()) {
			return HandlerResult.RETURN; // 기존처럼 전장 제한
		}

		// 블라인드 하이딩 토글
		if (skillId == L1SkillId.BLIND_HIDING && pc.hasSkillEffect(L1SkillId.BLIND_HIDING)) {
			pc.delBlindHiding();
			return HandlerResult.HANDLED;
		}

		// 인형 제거 
		try {
			if (pc.getDollList() != null) {
				for (Object o : pc.getDollList().values()) {
					if (o instanceof L1DollInstance) {
						((L1DollInstance) o).deleteDoll(false);
					}
				}
			}
		} catch (Throwable ignore) {}

		final int durSec = durationSecForBuff(su, skillId);
		pc.setSkillEffect(skillId, durSec * 1000);
		SC_Invis.send(pc, pc.getId(), 1);
		SC_Invis.broadcastFast(pc, pc.getId(), 1);
		NpcEventFactory.onPlayerInvisibility(pc);
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onNormalBlessingDR(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 정상의가호
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(8);
		SC_OwnCharAttrDef.send(pc);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onAreaOfSilenceSound(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_SkillSound.send(pc, pc.getId(), 10708);
		SC_SkillSound.broadcastFast(pc, pc.getId(), 10708);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBossCounterBarrierEffect(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.send_effect(10709);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMobRangeStunLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();

		int levelDiff = user.getLevel() - cha.getLevel();
		int duration = 2250 + levelDiff * 80;

		duration += su.hdRandom().nextInt(1600) - 800;

		if (duration < 1000) duration = 1000;
		else if (duration > 5000) duration = 5000;

		su.hdSetShockStunDuration(duration);

		applyStunLikeEffect(cha, duration, 81162, false);

		return HandlerResult.HANDLED;
	}


	private static HandlerResult onBurningWeapon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addDmgup(6);
		pc.addHitup(3);
		SC_SkillIconAura.send(pc, 162, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMirrorImage(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		((L1PcInstance) cha).addDg(30);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onAquaShot(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		((L1PcInstance) cha).addBowHitup(4);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onStormEye(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addBowHitup(2);
		pc.addBowDmgup(3);
		SC_SkillIconAura.send(pc, 155, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onStormShot(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.addBowDmgup(5);
		pc.addBowHitup(-3);
		SC_SkillIconAura.send(pc, 165, su.hdGetBuffIconDuration());

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBerserkers(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(10);
		pc.addDmgup(2);
		pc.addHitup(8);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onInsight(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedStr((byte) 1);
		pc.getAbility().addAddedDex((byte) 1);
		pc.getAbility().addAddedCon((byte) 1);
		pc.getAbility().addAddedInt((byte) 1);
		pc.getAbility().addAddedWis((byte) 1);
		pc.resetBaseMr();

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onClearMind(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		if (pc.hasSkillEffect(L1SkillId.CLEAR_MIND)) {
			pc.removeSkillEffect(L1SkillId.CLEAR_MIND);
		}

		pc.getAbility().addAddedStr((byte) 1);
		pc.getAbility().addAddedDex((byte) 1);
		pc.getAbility().addAddedInt((byte) 1);

		final int durSec = durationSecForBuff(su, L1SkillId.CLEAR_MIND);
		applySkillEffectSec(pc, L1SkillId.CLEAR_MIND, durSec);

		// on_icon=745, off_icon=5279 (서로 다름)
		sendRestatBuffNotiDualIcon(
				pc,
				L1SkillId.CLEAR_MIND,
				durSec,
				745,    // on icon
				5279,   // off icon
				0,      // priority
				861,    // tooltip
				861,    // new
				0,      // end 없음
				true
				);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onGreaterHaste(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		// 원본: haste 장착이면 continue
		if (pc.getHasteItemEquipped() > 0) {
			return HandlerResult.CONTINUE;
		}

		final int durSec = durationSecForBuff(su, L1SkillId.GREATER_HASTE);

		if (pc.getMoveSpeed() == 2) {
			int skillNum = 0;
			if (pc.hasSkillEffect(L1SkillId.SLOW)) {
				skillNum = L1SkillId.SLOW;
			} else if (pc.hasSkillEffect(L1SkillId.MOB_SLOW_1)) {
				skillNum = L1SkillId.MOB_SLOW_1;
			} else if (pc.hasSkillEffect(L1SkillId.MOB_SLOW_18)) {
				skillNum = L1SkillId.MOB_SLOW_18;
			}

			if (skillNum != 0) {
				pc.removeSkillEffect(skillNum);
				pc.removeSkillEffect(L1SkillId.GREATER_HASTE);
				pc.setMoveSpeed(0);
				return HandlerResult.CONTINUE;
			}

			// slow인데 원인 스킬이 없으면(특수상태) 건드리지 않음
			return HandlerResult.HANDLED;
		}

		pc.setDrink(false);
		pc.setMoveSpeed(1);
		SC_SkillHaste.send(pc, pc.getId(), 1, durSec);
		SC_SkillHaste.broadcastFast(pc, pc.getId(), 1, 0);

		applySkillEffectSec(pc, L1SkillId.GREATER_HASTE, durSec);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHolyWalk(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = durationSecForBuff(su, L1SkillId.HOLY_WALK);

		pc.setBraveSpeed(4);
		SC_SkillBrave.send(pc, pc.getId(), 4, durSec);
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 4, 0);

		applySkillEffectSec(pc, L1SkillId.HOLY_WALK, durSec);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMovingAcceleration(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = durationSecForBuff(su, L1SkillId.MOVING_ACCELERATION);

		pc.setBraveSpeed(4);
		SC_SkillBrave.send(pc, pc.getId(), 4, durSec);
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 4, 0);

		applySkillEffectSec(pc, L1SkillId.MOVING_ACCELERATION, durSec);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBloodLust(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = durationSecForBuff(su, L1SkillId.BLOOD_LUST);

		pc.setBraveSpeed(1);
		SC_SkillBrave.send(pc, pc.getId(), 6, durSec);
		SC_SkillBrave.broadcastFast(pc, pc.getId(), 6, 0);

		applySkillEffectSec(pc, L1SkillId.BLOOD_LUST, durSec);
		return HandlerResult.HANDLED;
	}
	private static HandlerResult onGlowingWeapon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.GLOWING_AURA;

		// 같은 스킬 재시전 누적 방지(종료/원복 로직 태우기)
		resetEffect(pc, skillId);

		// 효과 적용
		pc.addHitup(5);
		pc.addDmgup(5);

		SC_SkillIconAura.send(pc, 113, su.hdGetBuffIconDuration());

		// 타이머
		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onShiningShield(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.SHINING_AURA;

		resetEffect(pc, skillId);

		pc.getAC().addAc(-8);

		// AC 변동은 즉시 갱신해주는 게 안전
		SC_OwnCharAttrDef.send(pc);
		SC_SkillIconAura.send(pc, 114, su.hdGetBuffIconDuration());

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBraveMental(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.BRAVE_AURA;

		resetEffect(pc, skillId);

		SC_SkillIconAura.send(pc, 116, su.hdGetBuffIconDuration());

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onShield(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.SHIELD;

		resetEffect(pc, skillId);

		pc.getAC().addAc(-2);
		SC_OwnCharAttrDef.send(pc);
		SC_SkillIconShield.send(pc, 2, su.hdGetBuffIconDuration());

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onShadowArmor(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.SHADOW_ARMOR;

		resetEffect(pc, skillId);

		pc.getResistance().addMr(5);
		SC_SPMR.send(pc);

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDressDexterity(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.DRESS_DEXTERITY;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedDex((byte) 3);
		SC_Dexup.send(pc, pc, 2, su.hdGetBuffIconDuration());

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDressMighty(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = L1SkillId.DRESS_MIGHTY;

		resetEffect(pc, skillId);

		pc.getAbility().addAddedStr((byte) 3);
		SC_Strup.send(pc, pc, 2, su.hdGetBuffIconDuration());

		applySkillEffectSec(pc, skillId, durationSecForBuff(su, skillId));
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onDesperado(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character user = su.hdUser();
		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());

		if (user == null || target == null) return HandlerResult.NOT_HANDLED;

		int shockDur = SkillUseHelper.pickMillis(SkillUseHelper.DEPE_OFFICIAL);
		int effectNpcId = 9416;

		if (player != null && player.isPassive(MJPassiveID.DESPERADO_ABSOLUTE.toInt())) {		
			int bonusTime = SkillUseHelper.pickMillis(SkillUseHelper.BOUNS);
			shockDur += bonusTime;
			effectNpcId = 9418;
		}

		su.hdSetShockStunDuration(shockDur);

		spawnEffectAtTarget(target, effectNpcId, shockDur);

		if (target instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) target;

			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				pc.removeSkillEffect(L1SkillId.DESPERADO);
			}

			pc.Desperadolevel = user.getLevel();
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_PERADO, true);
			pc.setSkillEffect(L1SkillId.DESPERADO, shockDur);

		} else if (target instanceof L1MonsterInstance
				|| target instanceof L1SummonInstance
				|| target instanceof L1PetInstance
				|| target instanceof L1CompanionInstance) {

			L1NpcInstance npc = (L1NpcInstance) target;
			if (!npc.trySetSkillEffect(L1SkillId.DESPERADO, shockDur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC DESPERADO admission exhausted");
			}
			npc.set발묶임상태(true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onThunderGrab(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final L1PcInstance player = su.hdPlayer();
		// 확률 판정
		boolean ok = false;
		try {
			if (magic != null) ok = magic.calcProbabilityMagic(su.hdSkillId());
		} catch (Throwable ignore) {
		}
		if (!ok) return HandlerResult.HANDLED;

		int shockDur = SkillUseHelper.pickMillis(SkillUseHelper.DEPE_OFFICIAL);

		if (player != null && player.isPassive(MJPassiveID.THUNDER_GRAP_BRAVE.toInt())) {		
			int bonusTime = SkillUseHelper.pickMillis(SkillUseHelper.BOUNS);
			shockDur += bonusTime;
		}
		// PC
		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			spawnEffectAtTarget(cha, 81182, shockDur);
			pc.setSkillEffect(L1SkillId.STATUS_FREEZE, shockDur);
			sendSkillSoundOnce(pc, 4184);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
			return HandlerResult.HANDLED;
		}

		// NPC/소환/펫/동료
		if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance || cha instanceof L1CompanionInstance) {
			final L1NpcInstance npc = (L1NpcInstance) cha;

			if (!npc.trySetSkillEffect(L1SkillId.STATUS_FREEZE, shockDur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC STATUS_FREEZE admission exhausted");
			}
			spawnEffectAtTarget(cha, 81182, shockDur);
			sendSkillSoundBroadcast(npc, 4184);
			npc.set발묶임상태(true);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	// =========================
	//  REMOVE_CURSE
	// =========================
	private static HandlerResult onRemoveCurse(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    if (cha == null) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    cha.curePoison();

	    if (cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING)
	            || cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_1)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_6)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_7)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_8)
	            || cha.hasSkillEffect(L1SkillId.OMAN_STUN)
	            || cha.hasSkillEffect(L1SkillId.Maeno_STUN)) {
	        cha.cureParalaysis();
	    }

	    // 블라인드 / 다크니스 해제
	    if (cha.hasSkillEffect(L1SkillId.CURSE_BLIND)
	            || cha.hasSkillEffect(L1SkillId.DARKNESS)) {
	        if (cha.hasSkillEffect(L1SkillId.CURSE_BLIND)) {
	            cha.removeSkillEffect( L1SkillId.CURSE_BLIND);

	        } else if (cha.hasSkillEffect(L1SkillId.DARKNESS)) {
	            cha.removeSkillEffect(L1SkillId.DARKNESS);
	        }

	        if (cha instanceof L1PcInstance) {

	            final L1PcInstance pc =
	                    (L1PcInstance) cha;

	            SC_CurseBlind.send(pc, 0);
	            pc.hideBlindToInvis();
	        }
	    }

	    return HandlerResult.HANDLED;
	}

	private static HandlerResult onIceLance(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final int skillId = su.hdSkillId();

		final boolean freeze = magic.calcProbabilityMagic(skillId);
		if (!freeze) return HandlerResult.HANDLED;

		final int timeMs = su.hdGetBuffIconDuration() * 1000;

		spawnEffectAtTarget(cha, 81168, timeMs);

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			SC_Poison.send(pc, pc.getId(), 2);
			SC_Poison.broadcastFast(pc, pc.getId(), 2);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
		} else if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance || cha instanceof L1CompanionInstance) {
			final L1NpcInstance npc = (L1NpcInstance) cha;
			SC_Poison.broadcastFast(npc, npc.getId(), 2);
			npc.setParalyzed(true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEarthBind(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final int earthBindMs = SkillUseHelper.pickMillis(SkillUseHelper.EARTH_BIND_DUR);

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			SC_Poison.send(pc, pc.getId(), 2);
			SC_Poison.broadcastFast(pc, pc.getId(), 2);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
		} else if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance || cha instanceof L1CompanionInstance) {
			final L1NpcInstance npc = (L1NpcInstance) cha;
			SC_Poison.broadcastFast(npc, npc.getId(), 2);
			npc.setParalyzed(true);
			npc.setParalysisTime(earthBindMs);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDgUiAndKill(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character user = su.hdUser();
		final L1PcInstance player = su.hdPlayer();

		if (user instanceof L1NpcInstance) {
			final L1NpcInstance npc = (L1NpcInstance) user;
			SC_NpcChatPacket.broadcastNpcFast(npc, npc, "$3717", 0);
		} else if (player != null) {
			SC_ChatPacket.broadcastComplex0(player, "$3717", 0);
		}

		if (cha != null) {
			su.hdSetDamage(cha.getCurrentHp());
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTeleportFront5(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 10057 : 타겟 앞 5칸 소환
		if (cha == null) return HandlerResult.HANDLED;
		final L1Character user = su.hdUser();
		if (user == null) return HandlerResult.HANDLED;

		L1Teleport.getInstance().teleportToTargetFront(cha, user, 5, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onOmanOrMaenoStun(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final int[] stunTimeArray = { 2500, 3000, 3500 };
		final int rnd = su.hdRandom().nextInt(stunTimeArray.length);
		final int durationMs = stunTimeArray[rnd];

		su.hdSetShockStunDuration(durationMs);

		applyStunLikeEffect(cha, durationMs, 81162, true);


		return HandlerResult.HANDLED;
	}

	private static HandlerResult onNpcBloodBuff(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try { skillId = su.hdSkillId(); }
		catch (Throwable ignore) { return HandlerResult.NOT_HANDLED; }

		switch (skillId) {
		case L1SkillId.BUFF_SAEL: {
			if (pc.hasSkillEffect(L1SkillId.BUFF_SAEL)) {
				pc.removeSkillEffect(L1SkillId.BUFF_SAEL);
			}
			pc.getAC().addAc(-8);
			pc.addBowHitup(6);
			pc.addBowDmgup(3);
			pc.addMaxHp(80);
			pc.addMaxMp(10);
			pc.addHpr(8);
			pc.addMpr(1);
			pc.getResistance().addWater(30);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
			break;
		}

		case L1SkillId.BUFF_GUNTER: {
			if (pc.hasSkillEffect(L1SkillId.BUFF_GUNTER)) {
				pc.removeSkillEffect(L1SkillId.BUFF_GUNTER);
			}
			pc.getAbility().addAddedDex((byte) 5);
			pc.addBowHitup(7);
			pc.addBowDmgup(5);
			pc.addMaxHp(100);
			pc.addMaxMp(40);
			pc.addHpr(10);
			pc.addMpr(3);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
			break;
		}
		case L1SkillId.BUFF_CRAY: { // 크레이 혈흔
			pc.addHitup(5);
			pc.addDmgup(1);
			pc.addBowHitup(5);
			pc.addBowDmgup(1);
			pc.addMaxHp(100);
			pc.addMaxMp(50);
			pc.addHpr(3);
			pc.addMpr(3);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_SPMR.send(pc);
			break;
		}
		case L1SkillId.BUFF_Vala: {
			pc.addHitup(5);
			pc.addDmgup(1);
			pc.addBowHitup(5);
			pc.addBowDmgup(1);
			pc.addMaxHp(100);
			pc.addMaxMp(50);
			pc.addHpr(3);
			pc.addMpr(3);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_SPMR.send(pc);
			break;
		}

		default:
			return HandlerResult.NOT_HANDLED;
		}

		return HandlerResult.HANDLED;
	}

	// =========================
	//  CALL_OF_NATURE (부활계열)
	// =========================
	private static HandlerResult onCallOfNature(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		// 대상이 PC
		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (caster.getId() != pc.getId()) {
				if (L1World.getInstance().getVisiblePlayer(pc, 0).size() > 0) {
					for (L1PcInstance v : L1World.getInstance().getVisiblePlayer(pc, 0)) {
						if (!v.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				// 죽어있으면 Y/N 부활창
				if (pc.getCurrentHp() == 0 && pc.isDead()) {
					pc.setTempID(caster.getId());
					SC_MessageYN.send(pc, 322, "");
				}
			}
			return HandlerResult.HANDLED;
		}

		// 대상이 NPC
		if (cha instanceof L1NpcInstance) {
			if (cha instanceof L1TowerInstance) return HandlerResult.HANDLED;

			final L1NpcInstance npc = (L1NpcInstance) cha;
			if ((npc instanceof L1PetInstance || npc instanceof L1CompanionInstance)
					&& L1World.getInstance().getVisiblePlayer(npc, 0).size() > 0) {
				for (L1PcInstance v : L1World.getInstance().getVisiblePlayer(npc, 0)) {
					if (!v.isDead()) {
						SC_ServerMessage.send(caster, 592);
						return HandlerResult.RETURN;
					}
				}
			}

			// 죽어있으면 부활
			if (npc.getCurrentHp() == 0 && npc.isDead()) {
				npc.resurrect(cha.getMaxHp());
				npc.resurrect(cha.getMaxMp() / 100);
				npc.setResurrect(true);
			}
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	// =========================
	//  SLOW / MOB_SLOW_1 / MOB_SLOW_18
	// =========================
	private static HandlerResult onSlowLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final int id = su.hdSkillId();
		final int durSec = durationSecForBuff(su, id);

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getHasteItemEquipped() > 0) {
				return HandlerResult.CONTINUE;
			}
		}

		if (cha.getMoveSpeed() == 0) {
			if (cha instanceof L1PcInstance) {
				final L1PcInstance pc = (L1PcInstance) cha;
				SC_SkillHaste.send(pc, pc.getId(), 2, durSec);
			}
			SC_SkillHaste.broadcastFast(cha, cha.getId(), 2, durSec);
			cha.setMoveSpeed(2);
			return HandlerResult.HANDLED;
		}

		if (cha.getMoveSpeed() == 1) {
			int skillNum = 0;
			if (cha.hasSkillEffect(L1SkillId.HASTE)) {
				skillNum = L1SkillId.HASTE;
			} else if (cha.hasSkillEffect(L1SkillId.GREATER_HASTE)) {
				skillNum = L1SkillId.GREATER_HASTE;
			} else if (cha.hasSkillEffect(L1SkillId.STATUS_HASTE)) {
				skillNum = L1SkillId.STATUS_HASTE;
			}

			if (skillNum != 0) {
				cha.removeSkillEffect(skillNum);
				cha.killSkillEffectTimer(skillNum);
				cha.removeSkillEffect(id);

				if (cha instanceof L1PcInstance) {
					SC_SkillHaste.send(((L1PcInstance) cha), cha.getId(), 1, 0);
				}
				cha.setMoveSpeed(0);
				return HandlerResult.CONTINUE;
			}
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDragonMaan(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try {
			skillId = su.hdSkillId();
		} catch (Throwable ignore) {
			return HandlerResult.NOT_HANDLED;
		}

		boolean needSpMr = false;

		switch (skillId) {
		case L1SkillId.ANTA_MAAN: // 지룡
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
			break;

		case L1SkillId.FAFU_MAAN: // 수룡
			pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
			break;

		case L1SkillId.LIND_MAAN: // 풍룡
			pc.getAbility().addSp(1);
			pc.getResistance().addSpecialResistance(eKind.FEAR, 5);
			needSpMr = true;
			break;

		case L1SkillId.VALA_MAAN: // 화룡
			pc.addDmgup(2);
			pc.getResistance().addSpecialResistance(eKind.ABILITY, 5);
			break;

		case L1SkillId.BIRTH_MAAN: // 탄생
			pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
			break;

		case L1SkillId.SHAPE_MAAN: // 형상
			pc.getAbility().addSp(1);
			pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
			pc.getResistance().addSpecialResistance(eKind.FEAR, 5);
			needSpMr = true;
			break;

		case L1SkillId.LIFE_MAAN: // 생명
			pc.getResistance().addSpecialResistance(eKind.ALL, 5);
			pc.getResistance().addSpecialPierce(eKind.ALL, 3);
			pc.addDmgup(2);
			pc.getAbility().addSp(1);
			needSpMr = true;
			break;

		default:
			return HandlerResult.NOT_HANDLED;
		}

		if (needSpMr) {
			try { SC_SPMR.send(pc); } catch (Throwable ignore) {}
		}
		return HandlerResult.HANDLED;
	}


	private static HandlerResult onDragonBlood(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try {
			skillId = su.hdSkillId();
		} catch (Throwable ignore) {
			return HandlerResult.NOT_HANDLED;
		}

		int durSec = 0;
		try {
			durSec = durationSecForBuff(su, skillId); 
		} catch (Throwable ignore) {}
		if (durSec <= 0) durSec = 600; 
		final int durMin = Math.max(1, durSec / 60);

		switch (skillId) {
		case L1SkillId.ANTA_BUFF: {
			if (pc.hasSkillEffect(L1SkillId.ANTA_BUFF)) pc.removeSkillEffect(L1SkillId.ANTA_BUFF);
			pc.getAC().addAc(-2);
			pc.getResistance().addWater(50);
			sendOwnStatus(pc);
			sendDragonBloodIcon(pc, 82, durMin);
			break;
		}
		case L1SkillId.FAFU_BUFF: {
			if (pc.hasSkillEffect(L1SkillId.FAFU_BUFF)) pc.removeSkillEffect(L1SkillId.FAFU_BUFF);
			pc.addHpr(3);
			pc.addMpr(1);
			pc.getResistance().addWind(50);
			sendOwnStatus(pc);
			sendDragonBloodIcon(pc, 85, durMin);
			break;
		}
		case L1SkillId.RIND_BUFF: {
			if (pc.hasSkillEffect(L1SkillId.RIND_BUFF)) pc.removeSkillEffect(L1SkillId.RIND_BUFF);
			pc.addHitup(3);
			pc.addBowHitup(3);
			pc.getResistance().addFire(50);
			sendOwnStatus(pc);
			sendDragonBloodIcon(pc, 88, durMin);
			break;
		}
		default:
			return HandlerResult.NOT_HANDLED;
		}

		return HandlerResult.HANDLED;
	}

	private static void sendOwnStatus(L1PcInstance pc) {
		try { SC_OwnCharStatus.send(pc); } catch (Throwable ignore) {}
	}

	private static void sendDragonBloodIcon(L1PcInstance pc, int iconId, int minutes) {
		try { SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, iconId, minutes); } catch (Throwable ignore) {}
	}

	/**
	 * 토마호크:
	 */
	private static HandlerResult onTomahawk(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		TomahawkBleedService.getInstance().start(su, caster, target);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onSoulBarrier(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = durationSecForBuff(su, L1SkillId.SOUL_BARRIER);

		if (pc.hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
			pc.removeSkillEffect(L1SkillId.SOUL_BARRIER);
			SC_InventoryIcon.icoReset(pc, L1SkillId.SOUL_BARRIER, durSec, true);
		} else {
			SC_InventoryIcon.icoNew(pc, L1SkillId.SOUL_BARRIER, durSec, true);
		}

		pc.setSkillEffect(L1SkillId.SOUL_BARRIER, durSec * 1000);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHaste(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final int durSec = durationSecForBuff(su, L1SkillId.HASTE);

		if (cha.getMoveSpeed() != 2) {
			if (cha instanceof L1PcInstance) {
				final L1PcInstance pc = (L1PcInstance) cha;

				if (pc.getHasteItemEquipped() > 0) {
					return HandlerResult.HANDLED;
				}

				pc.setDrink(false);
				SC_SkillHaste.send(pc, pc.getId(), 1, durSec);
			}

			SC_SkillHaste.broadcastFast(cha, cha.getId(), 1, 0);
			cha.setMoveSpeed(1);
			return HandlerResult.HANDLED;

		} else {
			int skillNum = 0;

			if (cha.hasSkillEffect(L1SkillId.SLOW)) {
				skillNum = L1SkillId.SLOW;
			} else if (cha.hasSkillEffect(L1SkillId.MOB_SLOW_1)) {
				skillNum = L1SkillId.MOB_SLOW_1;
			} else if (cha.hasSkillEffect(L1SkillId.MOB_SLOW_18)) {
				skillNum = L1SkillId.MOB_SLOW_18;
			}

			if (skillNum != 0) {
				cha.removeSkillEffect(skillNum);
				cha.removeSkillEffect(L1SkillId.HASTE);
				cha.setMoveSpeed(0);
				return HandlerResult.HANDLED; // 기존 continue 효과
			}
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTitanRising(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;

		final int durSec = durationSecForBuff(su, L1SkillId.TITANL_RISING);

		if (pc.hasSkillEffect(L1SkillId.TITANL_RISING)) {
			pc.removeSkillEffect(L1SkillId.TITANL_RISING);
			SC_InventoryIcon.icoReset(pc, L1SkillId.TITANL_RISING, durSec, true);
		} else {
			SC_InventoryIcon.icoNew(pc, L1SkillId.TITANL_RISING, durSec, true);
		}

		pc.setSkillEffect(L1SkillId.TITANL_RISING, durSec * 1000);

		int upHP = pc.getLevel() - 60;
		if (upHP < 1) upHP = 1;
		if (upHP >= 5) upHP = 5;

		pc.setRisingUp(5 + upHP);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFeatherBuff(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId;
		try {
			skillId = su.hdSkillId(); 
		} catch (Throwable ignore) {

			return HandlerResult.NOT_HANDLED;
		}

		switch (skillId) {
		case L1SkillId.FEATHER_BUFF_A: { // 매우 좋은
			pc.addHpr(3);
			pc.addMpr(3);
			pc.addDmgup(2);
			pc.addHitup(2);
			pc.addMaxHp(50);
			pc.addMaxMp(30);
			pc.getAbility().addSp(2);
			pc.addDamageReductionByArmor(3);
			sendSpMr(pc);
			refreshParty(pc);
			sendHpMp(pc);
			break;
		}
		case L1SkillId.FEATHER_BUFF_B: { // 좋은
			pc.addHitup(2);
			pc.getAbility().addSp(1);
			pc.addMaxHp(50);
			pc.addMaxMp(30);
			pc.addDamageReductionByArmor(2);
			sendSpMr(pc);
			refreshParty(pc);
			sendHpMp(pc);
			break;
		}
		case L1SkillId.FEATHER_BUFF_C: { // 보통
			pc.addMaxHp(50);
			pc.addMaxMp(30);
			pc.getAC().addAc(-2);
			sendAttrDef(pc);
			refreshParty(pc);
			sendHpMp(pc);
			break;
		}
		case L1SkillId.FEATHER_BUFF_D: { // 나쁜
			pc.getAC().addAc(-1);
			sendAttrDef(pc);
			break;
		}
		default:
			return HandlerResult.NOT_HANDLED;
		}

		return HandlerResult.HANDLED;
	}

	// ===== helpers (SkillHandlers 안에 같이 둬도 되고 Helper 클래스로 빼도 됨) =====

	private static void refreshParty(L1PcInstance pc) {
		try {
			if (pc.isInParty() && pc.getParty() != null) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
		} catch (Throwable ignore) {}
	}

	private static void sendHpMp(L1PcInstance pc) {
		try {
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		} catch (Throwable ignore) {}
	}

	private static void sendSpMr(L1PcInstance pc) {
		try {
			SC_SPMR.send(pc);
		} catch (Throwable ignore) {}
	}

	private static void sendAttrDef(L1PcInstance pc) {
		try {
			SC_OwnCharAttrDef.send(pc);
		} catch (Throwable ignore) {}
	}

	private static HandlerResult onBlessedArmor(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance pc = (L1PcInstance) cha;
		final int durSec = su.hdGetBuffIconDuration(); 

		// 중복이면 제거
		if (pc.hasSkillEffect(L1SkillId.BLESSED_ARMOR)) {
			pc.removeSkillEffect(L1SkillId.BLESSED_ARMOR);
		}

		// 스킬 타이머 적용
		pc.setSkillEffect(L1SkillId.BLESSED_ARMOR, durSec * 1000);

		// 능력치 적용
		pc.getAC().addAc(-3);
		sendRestatBuffNoti(
				pc,
				L1SkillId.BLESSED_ARMOR,
				durSec,
				5316,   // on
				5316,   // off
				0,      // priority (없으면 0)
				3060,   // tooltip
				3060,   // new
				2240,   // end
				true
				);
		// 상태 갱신
		SC_OwnCharStatus.send(pc);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onFreezeengArmor(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// ER+5, icon=9490, tooltip/new=5889, end 없음
		return handlePcOnlyErBuff(su, cha, L1SkillId.FREEZEENG_ARMOR, 5, 9490, 5889, 5889, 0);
	}

	private static HandlerResult onShiningArmor(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// ER+10, icon=9483, tooltip/new=5892
		return handlePcOnlyErBuff(su, cha, L1SkillId.SHINING_ARMOR, 10, 9483, 0, 5892, 5892, 0, true);
	}

	private static HandlerResult onMajesty(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// icon=9518, tooltip/new=5893
		return handlePcOnlyNoti(su, cha, L1SkillId.MAJESTY, 9518, 0, 5893, 5893, 0, true);
	}

	private static HandlerResult onAbsoluteBarrier(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handlePcOnlyNoti(su, cha, L1SkillId.ABSOLUTE_BARRIER, 717, 10, 700, 700, 2179, true);
	}

	private static HandlerResult onAbsoluteBlade(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handlePcOnlyNoti(su, cha, L1SkillId.ABSOLUTE_BLADE, 7432, 0, 4735, 4735, 4742, true);
	}

	private static HandlerResult onBounceAttack(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;
		pc.addHitup(6);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onReductionArmor(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// icon=1889, tooltip/new=1043, end=2197
		return handlePcOnlyNoti(su, cha, L1SkillId.REDUCTION_ARMOR, 1889, 0, 1043, 1043, 2197, true);
	}

	private static HandlerResult onMoebius(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// icon=9443, tooltip=5550, new=5552
		return handlePcOnlyNoti(su, cha, L1SkillId.MOEBIUS, 9443, 0, 5550, 5552, 0, true);
	}

	private static HandlerResult onDressEvasion(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// ER+18, icon=1608, tooltip/new=970, end=2176
		return handlePcOnlyErBuff(su, cha, L1SkillId.DRESS_EVASION, 18, 1608, 0, 970, 970, 2176, true);
	}

	private static HandlerResult onAquaProtecter(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// ER+5, icon=2342, tooltip/new=1085, end=2201
		return handlePcOnlyErBuff(su, cha, L1SkillId.AQUA_PROTECTER, 5, 2342, 0, 1085, 1085, 2201, true);
	}

	private static HandlerResult onSolidCarriage(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// ER+15, icon=2351, tooltip/new=1087, end=2203
		return handlePcOnlyErBuff(su, cha, L1SkillId.SOLID_CARRIAGE, 15, 2351, 0, 1087, 1087, 2203, true);
	}

	private static HandlerResult onNBuffWaterDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleNBuffIconOnly(su, cha, L1SkillId.N_BUFF_WATER_DMG);
	}

	private static HandlerResult onNBuffWindDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleNBuffIconOnly(su, cha, L1SkillId.N_BUFF_WIND_DMG);
	}

	private static HandlerResult onNBuffEarthDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleNBuffIconOnly(su, cha, L1SkillId.N_BUFF_EARTH_DMG);
	}

	private static HandlerResult onNBuffFireDmg(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		return handleNBuffIconOnly(su, cha, L1SkillId.N_BUFF_FIRE_DMG);
	}

	// =========================
	// helpers
	// =========================
	private static HandlerResult handleNBuffIconOnly(L1SkillUse su, L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		resetEffect(pc, skillId);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);
		sendIvenBuffIcon(pc, skillId, durSec);
		return HandlerResult.HANDLED;
	}

	private static int weaponType1(final L1PcInstance pc) {
		if (pc == null) return -1;
		if (pc.getWeapon() == null || pc.getWeapon().getItem() == null) return -1;
		return pc.getWeapon().getItem().getType1();
	}

	private static boolean isOneOf(int v, int... arr) {
		for (int a : arr) if (v == a) return true;
		return false;
	}

	private static int durationSecForBuff(final L1SkillUse su, final int skillId) {
		int d = 0;
		if (su != null) d = su.hdGetBuffIconDuration();
		if (d > 0) return d;

		try {
			final L1Skills t = SkillsTable.getInstance().getTemplate(skillId);
			if (t != null && t.getBuffDuration() > 0) return t.getBuffDuration();
		} catch (Throwable ignore) {
		}

		return 1;
	}

	private static void applySkillEffectSec(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;
		if (durSec <= 0) return;
		pc.setSkillEffect(skillId, durSec * 1000);
	}

	private static void refreshPartyStatusIfNeeded(L1PcInstance pc) {
		if (pc != null && pc.isInParty() && pc.getParty() != null) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
	}

	private static void addERAndNotify(final L1PcInstance pc, final int erDelta) {
		if (pc == null) return;
		if (erDelta != 0) {
			pc.addEffectedER(erDelta);
			SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		}
	}

	private static void sendRestatBuffNoti(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int iconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;

		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eDurationShowType notiWire_durationShowType = null;
		int notiWire_onIconId = 0;
		int notiWire_offIconId = 0;
		int notiWire_iconPriority = 0;
		int notiWire_tooltipStrId = 0;
		int notiWire_newStrId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
		{ notiWire_bit |= 2; notiWire_spellId = spellId; }
		{ notiWire_bit |= 4; notiWire_duration = durSec; }
		{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }

		{ notiWire_bit |= 16; notiWire_onIconId = iconId; }
		{ notiWire_bit |= 32; notiWire_offIconId = iconId; }

		if (iconPriority > 0) { notiWire_bit |= 64; notiWire_iconPriority = iconPriority; }

		if (tooltipStrId > 0) { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipStrId; }
		if (newStrId > 0)     { notiWire_bit |= 256; notiWire_newStrId = newStrId; }
		if (endStrId > 0)     { notiWire_bit |= 512; notiWire_endStrId = endStrId; }

		{ notiWire_bit |= 1024; notiWire_isGood = isGood; }
		SC_SpellBuffNoti.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
		        notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
		        notiWire_isGood, 0, 0, 0, notiWire_bit);
	}

	private static HandlerResult handlePcOnlyNoti(
			final L1SkillUse su,
			final L1Character cha,
			final int skillId,
			final int iconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		resetEffect(pc, skillId);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		sendRestatBuffNoti(pc, skillId, durSec, iconId, iconId, iconPriority, tooltipStrId, newStrId, endStrId, isGood);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult handlePcOnlyErBuff(
			final L1SkillUse su,
			final L1Character cha,
			final int skillId,
			final int erDelta,
			final int iconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		resetEffect(pc, skillId);

		addERAndNotify(pc, erDelta);

		final int durSec = durationSecForBuff(su, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		sendRestatBuffNoti(pc, skillId, durSec, iconId, iconId, iconPriority, tooltipStrId, newStrId, endStrId, isGood);
		return HandlerResult.HANDLED;
	}

	private static void sendRestatBuffNoti(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int onIconId,
			final int offIconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;

		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eDurationShowType notiWire_durationShowType = null;
		int notiWire_onIconId = 0;
		int notiWire_offIconId = 0;
		int notiWire_iconPriority = 0;
		int notiWire_tooltipStrId = 0;
		int notiWire_newStrId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
		{ notiWire_bit |= 2; notiWire_spellId = spellId; }
		{ notiWire_bit |= 4; notiWire_duration = durSec; }
		{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }

		{ notiWire_bit |= 16; notiWire_onIconId = onIconId; }
		{ notiWire_bit |= 32; notiWire_offIconId = offIconId; }

		if (iconPriority > 0) { notiWire_bit |= 64; notiWire_iconPriority = iconPriority; }

		if (tooltipStrId > 0) { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipStrId; }
		if (newStrId > 0)     { notiWire_bit |= 256; notiWire_newStrId = newStrId; }
		if (endStrId > 0)     { notiWire_bit |= 512; notiWire_endStrId = endStrId; }

		{ notiWire_bit |= 1024; notiWire_isGood = isGood; }
		SC_SpellBuffNoti.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
		        notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
		        notiWire_isGood, 0, 0, 0, notiWire_bit);
	}

	private static void sendRestatBuffNotiDualIcon(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int onIconId,
			final int offIconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;
		sendRestatBuffNoti(pc, spellId, durSec, onIconId, offIconId, iconPriority, tooltipStrId, newStrId, endStrId, isGood);
	}

	private static HandlerResult handlePcOnlyErBuff(
			final L1SkillUse su,
			final L1Character cha,
			final int skillId,
			final int erDelta,
			final int iconId,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId) {
		return handlePcOnlyErBuff(su, cha, skillId, erDelta, iconId, 0, tooltipStrId, newStrId, endStrId, true);
	}
	private static void sendIvenBuffIcon(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;

		SC_IvenBuffIcon.send(pc, skillId, true, 0, durSec);
	}



	// =====================================================
	// 원본 L1SkillUse 누락 이관 handlers
	// 기존 handler 본문은 수정하지 않음
	// =====================================================

	private static HandlerResult onAntarasBoss(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();
		if (!(user instanceof L1NpcInstance)) return HandlerResult.HANDLED;

		final L1NpcInstance npc = (L1NpcInstance) user;
		final int npcId = npc.getNpcTemplate().get_npcId();

		// 원본 안타라스 3개 템플릿에서만 전용 효과.
		if (npcId != 900011 && npcId != 900012 && npcId != 900013) {
			return HandlerResult.HANDLED;
		}

		final int skillId = su.hdSkillId();
// 용언1 / 전용 캔슬
		if (skillId == L1SkillId.ANTA_MESSAGE_1
				|| skillId == L1SkillId.ANTA_CANCELLATION) {

			if (cha instanceof L1PcInstance) {
				applyBossCancellation(su, (L1PcInstance) cha, 870, true);
			}
		}

		/*
		 * 원본 그대로 보존.
		 * 1~5를 뽑고 time > 10 검사라 사실상 발동하지 않는다.
		 */
		if (skillId == L1SkillId.ANTA_MESSAGE_1
				|| skillId == L1SkillId.ANTA_MESSAGE_10) {

			final int time = su.hdRandom().nextInt(5) + 1;

			if (cha instanceof L1PcInstance && time > 10) {
				L1ParalysisPoison.doInfection(
						(L1PcInstance) cha,
						skillId);
			}
		}

		// 대미지 독
		if (skillId == L1SkillId.ANTA_MESSAGE_2
				|| skillId == L1SkillId.ANTA_MESSAGE_5
				|| skillId == L1SkillId.ANTA_MESSAGE_9) {

			final int poisonDmg = su.hdRandom().nextInt(50) + 1;
			final int poisonTime = su.hdRandom().nextInt(15) + 1;

			if (cha instanceof L1PcInstance && poisonTime > 2) {
				final L1PcInstance pc = (L1PcInstance) cha;
				final L1Character primary =
						(su.hdTarget() != null)
								? su.hdTarget()
								: cha;

				L1DamagePoison.doInfection(
						pc,
						primary,
						poisonTime * 1000,
						poisonDmg,
						true,
						7000);
			}
		}

		// 스턴
		if (skillId == L1SkillId.ANTA_MESSAGE_6
				|| skillId == L1SkillId.ANTA_MESSAGE_7
				|| skillId == L1SkillId.ANTA_MESSAGE_8
				|| skillId == L1SkillId.ANTA_SHOCKSTUN) {

			final int[] stunTimes = {
					4500, 5000, 5500
			};

			final int duration =
					stunTimes[
							su.hdRandom()
									.nextInt(stunTimes.length)];

			su.hdSetShockStunDuration(duration);

			final L1Character primary =
					(su.hdTarget() != null)
							? su.hdTarget()
							: cha;

			final int effectDuration =
					primary.hasSkillEffect(L1SkillId.리버레이션)
							? (int) (duration * Config.리버레이션절감)
							: duration;

			L1EffectSpawn.getInstance().spawnEffect(
					81162,
					effectDuration,
					cha.getX(),
					cha.getY(),
					cha.getMapId());

			if (cha instanceof L1PcInstance) {
				SC_Paralysis.send(((L1PcInstance) cha), SC_Paralysis.TYPE_STUN, true);

			} else if (cha instanceof L1MonsterInstance
					|| cha instanceof L1SummonInstance
					|| cha instanceof L1PetInstance
					|| cha instanceof L1CompanionInstance) {

				final L1NpcInstance targetNpc =
						(L1NpcInstance) cha;

				targetNpc.setParalyzed(true);

				// 원본: 리버레이션 적용 여부와 무관하게 NPC 타이머는 원 duration.
				targetNpc.setParalysisTime(duration);
			}
		}

		// 웨폰 브레이크
		if (skillId == L1SkillId.ANTA_MESSAGE_9
				|| skillId == L1SkillId.ANTA_MESSAGE_10
				|| skillId == L1SkillId.ANTA_WEAPON_BREAK) {

			applyBossWeaponDamage(
					su,
					cha,
					3,
					false,
					false);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBattleScroll(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.격전의주문서;

		if (pc.hasSkillEffect(skillId)) {
			return HandlerResult.HANDLED;
		}

		pc.getAC().addAc(-5);
		pc.addHitRate(5);
		pc.addBowHitup(5);
		pc.addMagicHit(2);
		pc.getResistance().addcalcPcDefense(5);
		pc.getResistance().addPVPweaponTotalDamage(5);

		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);

		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eDurationShowType notiWire_durationShowType = null;
		int notiWire_onIconId = 0;
		int notiWire_offIconId = 0;
		int notiWire_tooltipStrId = 0;
		int notiWire_newStrId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
		{ notiWire_bit |= 2; notiWire_spellId = skillId; }
		{ notiWire_bit |= 4; notiWire_duration = (su.hdGetBuffIconDuration()); }
		{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
		{ notiWire_bit |= 16; notiWire_onIconId = 9151; }
		{ notiWire_bit |= 32; notiWire_offIconId = 9151; }
		{ notiWire_bit |= 128; notiWire_tooltipStrId = 5021; }
		{ notiWire_bit |= 256; notiWire_newStrId = 5022; }
		{ notiWire_bit |= 512; notiWire_endStrId = 5023; }
		{ notiWire_bit |= 1024; notiWire_isGood = true; }
		SC_SpellBuffNoti.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
		        notiWire_offIconId, 0, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId, notiWire_isGood, 0, 0,
		        0, notiWire_bit);

		pc.setSkillEffect(skillId, 300 * 1000);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBlackElderDeathHell(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final int chance =
				su.hdRandom().nextInt(100) + 1;

		applyTimedBossDebuff(
				cha,
				7780,
				L1SkillId.PAP_DEATH_HELL);

		if (chance > 33
				&& cha instanceof L1PcInstance) {

			final L1PcInstance pc =
					(L1PcInstance) cha;

			applyBossCancellation(
					su,
					pc,
					su.hdSkillCastGfx(),
					false);

			// 원본은 캔슬 후 870을 한 번 더 전송.
			sendSkillSoundBroadcast(
					pc,
					870);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBlackElderDeathPotion(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		applyTimedBossDebuff(
				cha,
				7781,
				L1SkillId.PAP_DEATH_PORTION);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBossDirectMagic(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null || magic == null) {
			return HandlerResult.HANDLED;
		}

		final int skillId = su.hdSkillId();

		switch (skillId) {
		case L1SkillId.TROGIR_MILPITAS2:
			SC_Sound.sendAndBroadcastFast(cha, 11009);
			SC_Sound.sendAndBroadcastFast(cha, 11006);
			SC_SkillSound.sendAndBroadcast(cha, 11473);

			if (cha instanceof L1PcInstance) {
				L1SpawnUtil.spawn(
						(L1PcInstance) cha,
						181163,
						5,
						6 * 2000,
						true);
			}
			break;

		case L1SkillId.TROGIR_MILPITAS3:
			SC_Sound.sendAndBroadcastFast(cha, 11002);
			break;

		case L1SkillId.TROGIR_MILPITAS4:
			SC_Sound.sendAndBroadcastFast(cha, 11003);
			break;

		case L1SkillId.TROGIR_MILPITAS5:
			SC_Sound.sendAndBroadcastFast(cha, 11004);
			break;

		case L1SkillId.TROGIR_MILPITAS6:
			SC_Sound.sendAndBroadcastFast(cha, 11008);
			break;

		case L1SkillId.Phoenix_Skill1:
			if (cha instanceof L1PcInstance) {
				sendSkillSoundBroadcast(cha, 16258);
			}
			break;

		case L1SkillId.Phoenix_Skill2:
			if (cha instanceof L1PcInstance) {
				sendSkillSoundBroadcast(cha, 16299);
			}
			break;

		case L1SkillId.DRAKE_Skill1:
			if (cha instanceof L1PcInstance) {
				sendSkillSoundBroadcast(cha, 16333);
			}
			break;

		case L1SkillId.DRAKE_Skill2:
			if (cha instanceof L1PcInstance) {
				sendSkillSoundBroadcast(cha, 16335);
			}
			break;

		default:
			return HandlerResult.NOT_HANDLED;
		}

		su.hdSetDamage(magic.calcMagicDamage(skillId));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onConcentration(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();
		if (user instanceof L1PcInstance) {
			L1PinkName.onHelp(cha, user);
		}

		return HandlerResult.HANDLED;
	}
	
	private static HandlerResult onMetisBlessScroll(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {


		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		
		removeSkillEffects(pc, L1SkillId.메티스축복주문서);
		
		pc.addHitup(2);
		pc.addDmgup(2);
		pc.addBowHitup(2);
		pc.addBowDmgup(2);
		pc.getAbility().addSp(2);
		pc.addHpr(3);
		pc.addMpr(3);
		SC_SPMR.send(pc);

		pc.addMaxHp(50);
		pc.addMaxMp(30);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		return HandlerResult.HANDLED;
	}
	
	private static HandlerResult onSpecialBuff(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    if (cha == null || cha.isDead()) {
	        return HandlerResult.HANDLED;
	    }

	    final int skillId = su.hdSkillId();

	    if (!isSpecialBuff(skillId)) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    if (!(cha instanceof L1PcInstance)) {
	        return HandlerResult.HANDLED;
	    }
	    final L1PcInstance pc = (L1PcInstance) cha;
	    
	    if(pc.hasSkillEffect(skillId)) {
	    	pc.removeSkillEffect(skillId);
	    }

	    switch (skillId) {
	    case L1SkillId.BUFF_특별한성장축복:
	        sendSpecialBuffIcon(
	                pc,
	                skillId,
	                su.hdGetBuffIconDuration());
	        break;

	    case L1SkillId.BUFF_특별한방어축복:
	        applySpecialDefenseBuff(pc);

	        sendSpecialBuffIcon(
	                pc,
	                skillId,
	                su.hdGetBuffIconDuration());
	        break;

	    case L1SkillId.BUFF_특별한공격축복:
	        applySpecialAttackBuff(pc);

	        sendSpecialBuffIcon(
	                pc,
	                skillId,
	                su.hdGetBuffIconDuration());
	        break;

	    default:
	        return HandlerResult.NOT_HANDLED;
	    }

	    return HandlerResult.HANDLED;
	}


	private static boolean isSpecialBuff(int skillId) {
	    switch (skillId) {
	    case L1SkillId.BUFF_특별한성장축복:
	    case L1SkillId.BUFF_특별한방어축복:
	    case L1SkillId.BUFF_특별한공격축복:
	        return true;

	    default:
	        return false;
	    }
	}


	private static void applySpecialDefenseBuff(
	        L1PcInstance pc) {

	    pc.addDamageReductionByArmor(1);
	    pc.getResistance().addMr(5);
	    pc.addMaxHp(50);

	    SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());

	    SC_OwnCharAttrDef.send(pc);

	    SC_SPMR.send(pc);
	}


	private static void applySpecialAttackBuff(
	        L1PcInstance pc) {

	    pc.addDmgup(2);
	    pc.addBowDmgup(2);
	    pc.getAbility().addSp(2);
	    pc.addMaxMp(30);

	    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

	    SC_OwnCharAttrDef.send(pc);

	    SC_SPMR.send(pc);
	}


	private static void sendSpecialBuffIcon(
	        L1PcInstance pc,
	        int skillId,
	        int duration) {

	    SC_InventoryIcon.icoNew(pc, skillId,
	                    duration,
	                    true);
	}

	private static HandlerResult onDesertBoss(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null) {
			return HandlerResult.HANDLED;
		}

		final int skillId =
				su.hdSkillId();

		switch (skillId) {
		case L1SkillId.DESERT_SKILL1:
			if (!cha.hasSkillEffect(L1SkillId.EARTH_BIND)
					&& !cha.hasSkillEffect(L1SkillId.ICE_LANCE)
					&& !cha.hasSkillEffect(L1SkillId.DESERT_SKILL1)
					&& !cha.hasSkillEffect(L1SkillId.DESERT_SKILL2)
					&& cha instanceof L1PcInstance) {

				final L1PcInstance pc =
						(L1PcInstance) cha;

				if (pc.isDead()) {
					return HandlerResult.CONTINUE;
				}

				L1CurseParalysis.curse(
						pc,
						skillId);
			}

			return HandlerResult.HANDLED;

		case L1SkillId.DESERT_SKILL2:
			if (!cha.hasSkillEffect(L1SkillId.EARTH_BIND)
					&& !cha.hasSkillEffect(L1SkillId.ICE_LANCE)
					&& !cha.hasSkillEffect(L1SkillId.DESERT_SKILL1)
					&& !cha.hasSkillEffect(L1SkillId.DESERT_SKILL2)
					&& cha instanceof L1PcInstance) {

				final L1PcInstance pc =
						(L1PcInstance) cha;

				if (pc.isDead()) {
					return HandlerResult.CONTINUE;
				}

				pc.setSkillEffect(
						L1SkillId.EARTH_BIND,
						12 * 1000);

				SC_Poison.send(pc, pc.getId(), 2);

				SC_Poison.broadcastFast(pc, pc.getId(), 2);

				SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);

				sendSkillSoundBroadcast(
						pc,
						2251);
			}

			return HandlerResult.HANDLED;

		case L1SkillId.DESERT_SKILL3: {
			final int ranMp =
					su.hdRandom().nextInt(20);

			if (cha instanceof L1PcInstance) {
				final L1PcInstance pc =
						(L1PcInstance) cha;

				if (pc.getCurrentMp() <= ranMp
						|| pc.isDead()) {
					return HandlerResult.CONTINUE;
				}

				pc.setCurrentMp(
						pc.getCurrentMp()
								- ranMp);

				sendSkillSoundBroadcast(
						pc,
						2172);
			}

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL4:
		case L1SkillId.ZENITH_Poison: {
			final int poisonTime =
					su.hdRandom().nextInt(5) + 1;

			if (cha instanceof L1PcInstance
					&& poisonTime > 2) {

				L1DamagePoison.doInfection(
						su.hdUser(),
						cha,
						poisonTime * 1000,
						500,
						true,
						7000);
			}

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL5: {
			if (!(cha instanceof L1PcInstance)) {
				return HandlerResult.HANDLED;
			}

			final L1PcInstance pc =
					(L1PcInstance) cha;

			// 커스 패럴라이즈
			if (pc.isDead()
					|| pc.hasSkillEffect(
							L1SkillId.CURSE_PARALYZE)) {
				return HandlerResult.CONTINUE;
			}

			L1CurseParalysis.curse(
					cha,
					skillId);

			pc.setSkillEffect(
					L1SkillId.CURSE_PARALYZE,
					4 * 1000);

			sendSkillSoundBroadcast(
					pc,
					10704);

			// 디케이 포션
			if (pc.isDead()
					|| pc.hasSkillEffect(
							L1SkillId.DECAY_POTION)) {
				return HandlerResult.CONTINUE;
			}

			pc.setSkillEffect(
					L1SkillId.DECAY_POTION,
					16 * 1000);

			sendSkillSoundBroadcast(
					pc,
					2232);

			// 다크니스
			if (pc.isDead()
					|| pc.hasSkillEffect(
							L1SkillId.DARKNESS)) {
				return HandlerResult.CONTINUE;
			}

			if (pc.hasSkillEffect(
					L1SkillId.STATUS_FLOATING_EYE)) {

				SC_CurseBlind.send(pc, 2);

			} else {
				SC_CurseBlind.send(pc, 1);
			}

			pc.setSkillEffect(
					L1SkillId.DARKNESS,
					32 * 1000);

			sendSkillSoundBroadcast(
					pc,
					2175);

			// 디지즈
			if (pc.isDead()
					|| pc.hasSkillEffect(
							L1SkillId.DISEASE)) {
				return HandlerResult.CONTINUE;
			}

			pc.addDmgup(-6);
			pc.getAC().addAc(12);

			pc.setSkillEffect(
					L1SkillId.DISEASE,
					64 * 1000);

			sendSkillSoundBroadcast(
					pc,
					2230);

			// 위크니스
			if (pc.isDead()
					|| pc.hasSkillEffect(
							L1SkillId.WEAKNESS)) {
				return HandlerResult.CONTINUE;
			}

			pc.addDmgup(-5);
			pc.addHitup(-1);

			pc.setSkillEffect(
					L1SkillId.WEAKNESS,
					64 * 1000);

			sendSkillSoundBroadcast(
					pc,
					2228);

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL6: {
			if (cha instanceof L1PcInstance) {
				final L1PcInstance pc =
						(L1PcInstance) cha;

				if (pc.isDead()
						|| pc.hasSkillEffect(
								L1SkillId.DARKNESS)) {
					return HandlerResult.CONTINUE;
				}

				if (pc.hasSkillEffect(
						L1SkillId.STATUS_FLOATING_EYE)) {

					SC_CurseBlind.send(pc, 2);

				} else {
					SC_CurseBlind.send(pc, 1);
				}

				pc.setSkillEffect(
						L1SkillId.DARKNESS,
						32 * 1000);

				sendSkillSoundBroadcast(
						pc,
						2175);
			}

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL7: {
			if (cha instanceof L1PcInstance) {
				final L1PcInstance pc =
						(L1PcInstance) cha;

				if (pc.isDead()
						|| pc.hasSkillEffect(
								L1SkillId.FOG_OF_SLEEPING)) {
					return HandlerResult.CONTINUE;
				}

				pc.setSkillEffect(
						L1SkillId.FOG_OF_SLEEPING,
						32 * 1000);

				SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, true);

				sendSkillSoundBroadcast(
						pc,
						760);
			}

			cha.setSleeped(true);

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL8:
			if (cha instanceof L1PcInstance) {
				sendSkillSoundBroadcast(
						(L1PcInstance) cha,
						10082);
			}

			return HandlerResult.HANDLED;

		case L1SkillId.DESERT_SKILL9: {
			final L1Character user =
					su.hdUser();

			if (user instanceof L1NpcInstance) {
				final L1NpcInstance npc =
						(L1NpcInstance) user;

				for (int i = 0; i < 4; i++) {
					for (int npcId = 5138;
							npcId <= 5145;
							npcId++) {

						L1SpawnUtil.spawn(
								npc,
								npcId,
								6,
								120 * 1000);
					}
				}
			}

			return HandlerResult.HANDLED;
		}

		case L1SkillId.DESERT_SKILL10: {
			final L1Character user =
					su.hdUser();

			if (user instanceof L1NpcInstance) {
				final L1NpcInstance npc =
						(L1NpcInstance) user;

				final int count =
						su.hdRandom()
								.nextInt(3) + 1;

				for (int i = 0;
						i < count;
						i++) {

					L1SpawnUtil.spawn(
							npc,
							5095,
							6,
							3 * 1000);
				}
			}

			return HandlerResult.HANDLED;
		}

		default:
			return HandlerResult.NOT_HANDLED;
		}
	}

	private static HandlerResult onDragonSet(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.DRAGON_SET;

		if (pc.hasSkillEffect(skillId)) {
			SC_InventoryIcon.icoReset(pc, skillId, 5016, 600L, true);
			return HandlerResult.HANDLED;
		}

		pc.addDmgup(3);
		pc.addDmgRate(3);
		pc.addBowDmgup(3);
		pc.addBowHitup(3);
		pc.getAC().addAc(-3);
		pc.getAbility().addSp(2);

		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_InventoryIcon.icoNew(pc, skillId, 5016, 600L, true);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDrakeMassTeleport(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final L1Location newLocation =
				pc.getLocation()
						.randomLocation(
								5,
								true);

		pc.start_teleport(
				newLocation.getX(),
				newLocation.getY(),
				pc.getMapId(),
				pc.getHeading(),
				169,
				true,
				true);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onDrakeWindShackle(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		pc.setSkillEffect(
				L1SkillId.WIND_SHACKLE,
				12 * 1000);

		if (pc.getCurrentWeapon() != 88) {
			SC_SkillIconWindShackle.send(pc, pc.getId(), su.hdGetBuffIconDuration());
		}

		sendSkillSoundBroadcast(
				pc,
				1799);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onEfreetSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		summonBossMinions(
				su,
				5121);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onLegacyEventIconBuff(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = su.hdSkillId();

		switch (skillId) {
		case L1SkillId.DRAGON_HUNTER_BLESS:
			if (pc.hasSkillEffect(skillId)) {
				SC_InventoryIcon.icoReset(pc, skillId, 5016, 1800L, true);
			} else {
				SC_InventoryIcon.icoNew(pc, skillId, 5016, 1800L, true);
			}
			return HandlerResult.HANDLED;

		case L1SkillId.HUNTER_BLESS:
			if (pc.hasSkillEffect(skillId)) {
				SC_InventoryIcon.icoReset(pc, skillId, 4992, 1800L, true);
			} else {
				SC_InventoryIcon.icoNew(pc, skillId, 4992, 1800L, true);
			}
			return HandlerResult.HANDLED;

		case L1SkillId.HUNTER_BLESS2:
			if (pc.hasSkillEffect(skillId)) {
				SC_InventoryIcon.icoReset(pc, skillId, 993, 1800L, true);
			} else {
				SC_InventoryIcon.icoNew(pc, skillId, 993, 1800L, true);
			}
			return HandlerResult.HANDLED;

		default:
			return HandlerResult.NOT_HANDLED;
		}
	}

	private static HandlerResult onPapBoss(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();
		if (!(user instanceof L1NpcInstance)) return HandlerResult.HANDLED;

		final L1NpcInstance npc = (L1NpcInstance) user;
		final int npcId = npc.getNpcTemplate().get_npcId();

		if (npcId != 900038
				&& npcId != 900039
				&& npcId != 900040) {
			return HandlerResult.HANDLED;
		}

		final int skillId = su.hdSkillId();
// 리콜 소환: 원본 for 2회 그대로.
		if (skillId == L1SkillId.PAP_PREDICATE1) {
			for (int i = 0; i < 2; i++) {

				L1SpawnUtil.spawn2(
						user.getX(),
						user.getY(),
						(short) user.getMap().getId(),
						900049,
						8,
						60 * 1000,
						0);

				L1SpawnUtil.spawn2(
						user.getX(),
						user.getY(),
						(short) user.getMap().getId(),
						900050,
						8,
						60 * 1000,
						0);

				L1SpawnUtil.spawn2(
						user.getX(),
						user.getY(),
						(short) user.getMap().getId(),
						900051,
						8,
						60 * 1000,
						0);

				L1SpawnUtil.spawn2(
						user.getX(),
						user.getY(),
						(short) user.getMap().getId(),
						900052,
						8,
						120 * 1000,
						0);
			}
		}

		// 매스/일반 캔슬
		if (skillId == L1SkillId.PAP_PREDICATE7
				|| skillId == L1SkillId.PAP_PREDICATE11
				|| skillId == L1SkillId.PAP_PREDICATE12) {

			if (cha instanceof L1PcInstance) {
				applyBossCancellation(
						su,
						(L1PcInstance) cha,
						870,
						true);
			}
		}

		// 웨폰
		if (skillId == L1SkillId.PAP_PREDICATE8) {
			applyBossWeaponDamage(
					su,
					cha,
					2,
					true,
					false);
		}

		// 데스 포션
		if (skillId == L1SkillId.PAP_PREDICATE3) {
			applyTimedBossDebuff(
					cha,
					7781,
					L1SkillId.PAP_DEATH_PORTION);
		}

		// 리듀스 힐
		if (skillId == L1SkillId.PAP_PREDICATE5) {
			applyTimedBossDebuff(
					cha,
					7782,
					L1SkillId.PAP_REDUCE_HELL);
		}

		// 데스 힐
		if (skillId == L1SkillId.PAP_PREDICATE6
				|| skillId == L1SkillId.PAP_PREDICATE11
				|| skillId == L1SkillId.PAP_PREDICATE12) {

			applyTimedBossDebuff(
					cha,
					7780,
					L1SkillId.PAP_DEATH_HELL);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhoenixCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (su.hdRandom().nextInt(100) + 1 > 33
				&& cha instanceof L1PcInstance) {

			applyBossCancellation(
					su,
					(L1PcInstance) cha,
					870,
					true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhoenixSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		summonBossMinions(
				su,
				900177);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorBow(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		// 원본은 cha가 아니라 primary _target 기준.
		final L1Character primary =
				su.hdTarget();

		if (!(primary instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) primary;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		int sprNum = 0;
		int pcX = pc.getX();
		int pcY = pc.getY();

		switch (npc.getNpcTemplate().get_npcId()) {
		case 5097:
			pcY -= 6;
			sprNum = 7987;
			break;

		case 5098:
			pcX += 4;
			pcY -= 4;
			sprNum = 8050;
			break;

		case 5099:
			pcX += 5;
			sprNum = 8051;
			break;

		default:
			break;
		}

		final long packet = SC_EffectLocation.pack(
						pcX,
						pcY,
						sprNum);

		SC_EffectLocation.sendPacked(pc, packet);
		SC_EffectLocation.broadcastFastPacked(pc, packet);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (su.hdRandom().nextInt(100) + 1 > 33
				&& cha instanceof L1PcInstance) {

			applyBossCancellation(
					su,
					(L1PcInstance) cha,
					870,
					true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorPredicate(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1Character user = su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		if (npc.getLocation()
				.getTileLineDistance(
						pc.getLocation()) > 4) {

			for (int count = 0;
					count < 10;
					count++) {

				final L1Location newLoc =
						npc.getLocation()
								.randomLocation(
										3,
										4,
										false);

				if (npc.glanceCheck(
						newLoc.getX(),
						newLoc.getY())) {

					pc.start_teleport(
							newLoc.getX(),
							newLoc.getY(),
							npc.getMapId(),
							pc.getHeading(),
							169,
							true,
							true);

					break;
				}
			}
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorPredicateCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (su.hdRandom().nextInt(100) + 1 <= 33) {
			return HandlerResult.HANDLED;
		}

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		if (npc.getLocation()
				.getTileLineDistance(
						pc.getLocation()) > 4) {

			for (int count = 0;
					count < 10;
					count++) {

				final L1Location newLoc =
						npc.getLocation()
								.randomLocation(
										3,
										4,
										false);

				if (npc.glanceCheck(
						newLoc.getX(),
						newLoc.getY())) {

					pc.start_teleport(
							newLoc.getX(),
							newLoc.getY(),
							npc.getMapId(),
							5,
							169,
							true,
							true);

					break;
				}
			}
		}

		applyBossCancellation(
				su,
				pc,
				870,
				true);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorSilence(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		pc.setSkillEffect(
				L1SkillId.SILENCE,
				12 * 1000);

		sendSkillSoundBroadcast(
				pc,
				2177);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1Character user = su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		final int[] mobIds = {
				5106,
				5107,
				5108,
				5109
		};

		final int rnd =
				su.hdRandom().nextInt(100);

		final int count =
				su.hdRandom().nextInt(2) + 1;

		for (int i = 0; i < count; i++) {
			L1SpawnUtil.spawn(
					npc,
					mobIds[rnd % mobIds.length],
					su.hdRandom().nextInt(3) + 8);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorSummonCloud(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1Character user = su.hdUser();

		if (user instanceof L1NpcInstance) {
			L1SpawnUtil.spawn(
					(L1NpcInstance) user,
					5110,
					10);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorWeapon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		return applyBossWeaponDamage(
				su,
				cha,
				3,
				true,
				true);
	}

	private static HandlerResult onRindviorWindShackle(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		pc.setSkillEffect(
				L1SkillId.WIND_SHACKLE,
				12 * 1000);

		if (pc.getCurrentWeapon() != 88) {
			SC_SkillIconWindShackle.send(pc, pc.getId(), su.hdGetBuffIconDuration());
		}

		sendSkillSoundBroadcast(
				pc,
				1799);

		return HandlerResult.HANDLED;
	}

	private static void applyBossCancellation(
			L1SkillUse su,
			L1PcInstance pc,
			int soundGfx,
			boolean preserveChatProhibited) {

		if (pc == null) return;

		sendSkillSoundBroadcast(
				pc,
				soundGfx);

		// 일반 스킬 효과
		for (int skillNum = su.hdSkillsBegin();
				skillNum <= su.hdSkillsEnd();
				skillNum++) {

			if (SkillUseHelper.isNotCancelable(skillNum)
					&& !pc.isDead()) {
				continue;
			}

			// 랭킹 변신 / 폴리링 보호
			if (skillNum == L1SkillId.SHAPE_CHANGE) {

				if (pc.getRankLevel() >= 9
						&& PolyTable.getInstance()
								.isRankingPoly(
										pc.getCurrentSpriteId())) {
					continue;
				}

				if (pc.getSkillEffectTimeSec(
						L1SkillId.POLY_RING_MASTER) > 0) {
					continue;
				}
			}

			pc.removeSkillEffect(
					skillNum);
		}

		// 상태 효과
		for (int skillNum = su.hdStatusBegin();
				skillNum <= su.hdStatusEnd();
				skillNum++) {

			if (preserveChatProhibited
					&& skillNum
							== L1SkillId.STATUS_CHAT_PROHIBITED) {
				continue;
			}

			pc.removeSkillEffect(
					skillNum);
		}

		// 요리
		for (int skillNum = su.hdCookingBegin();
				skillNum <= su.hdCookingEnd();
				skillNum++) {

			if (SkillUseHelper.isNotCancelable(skillNum)
					&& !pc.isDead()) {
				continue;
			}

			pc.removeSkillEffect(
					skillNum);
		}

		pc.curePoison();
		pc.cureParalaysis();

		// 랭킹 변신 보호. 원본 그대로.
		if (!(pc.getRankLevel() >= 9
				&& PolyTable.getInstance()
						.isRankingPoly(
								pc.getCurrentSpriteId()))) {

			L1PolyMorph.undoPoly(pc);

			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));

			SC_CharVisualUpdate.broadcastFastPacked(pc, SC_CharVisualUpdate.pack(pc));
		}

		if (pc.getHasteItemEquipped() > 0) {
			pc.setMoveSpeed(0);

			SC_SkillHaste.send(pc, pc.getId(), 0, 0);

			SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
		}

		removeBossInvisibility(pc);

		pc.removeSkillEffect(
				L1SkillId.STATUS_FREEZE);

		/*
		 * 원본 getTreePotionBuff(pc)는
		 * 현재 원본에서도 본문 전체가 주석이라 실제 동작 없음.
		 */
	}

	private static HandlerResult applyBossWeaponDamage(
			L1SkillUse su,
			L1Character target,
			int maxDamage,
			boolean chanceCheck,
			boolean continueIfDead) {

		if (!(target instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) target;

		final L1ItemInstance weapon =
				pc.getWeapon();

		if (weapon == null) {
			return HandlerResult.HANDLED;
		}

		if (chanceCheck
				&& su.hdRandom().nextInt(100) + 1 <= 33) {
			return HandlerResult.HANDLED;
		}

		if (continueIfDead
				&& pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final int weaponDamage =
				su.hdRandom()
						.nextInt(maxDamage) + 1;

		SC_ServerMessage.send(pc, 268, weapon.getLogName());

		pc.getInventory()
				.receiveDamage(
						weapon,
						weaponDamage);

		sendSkillSoundBroadcast(
				pc,
				172);

		return HandlerResult.HANDLED;
	}

	private static void applyTimedBossDebuff(
			L1Character target,
			int soundId,
			int effectId) {

		if (!(target instanceof L1PcInstance)) {
			return;
		}

		final L1PcInstance pc =
				(L1PcInstance) target;

		sendSkillSoundBroadcast(
				pc,
				soundId);

		pc.setSkillEffect(
				effectId,
				12 * 1000);
	}

	private static void removeBossInvisibility(
			L1PcInstance pc) {

		if (pc == null
				|| !pc.isInvisble()) {
			return;
		}

		if (pc.hasSkillEffect(
				L1SkillId.INVISIBILITY)) {

			pc.setGmInvis(false);

			pc.killSkillEffectTimer(
					L1SkillId.INVISIBILITY);

			SC_Invis.send(pc, 
							pc.getId(),
							0);

			SC_Invis.broadcastFast(pc, 
							pc.getId(),
							0);

			SC_Sound.send(pc, 147);
		}

		if (pc.hasSkillEffect(
				L1SkillId.BLIND_HIDING)) {

			pc.setGmInvis(false);

			pc.killSkillEffectTimer(
					L1SkillId.BLIND_HIDING);

			SC_Invis.send(pc, 
							pc.getId(),
							0);

			SC_Invis.broadcastFast(pc, 
							pc.getId(),
							0);
		}
	}

	private static void summonBossMinions(
			L1SkillUse su,
			int npcId) {

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		for (int i = 0; i < 2; i++) {
			L1SpawnUtil.spawn(
					npc,
					npcId,
					su.hdRandom()
							.nextInt(3) + 8);
		}
	}

	private static HandlerResult onOmanCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			applyBossCancellation(su, (L1PcInstance) cha, 870, true);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onLegacyBlessing(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = su.hdSkillId();
		final int amount;

		switch (skillId) {
		case L1SkillId.SIDE_OF_ME_BLESSING:
			amount = 5;
			break;
		case L1SkillId.RE_START_BLESSING:
			amount = 3;
			break;
		case L1SkillId.NEW_START_BLESSING:
			amount = 2;
			break;
		case L1SkillId.LIFE_BLESSING:
			amount = 1;
			break;
		default:
			return HandlerResult.NOT_HANDLED;
		}

		pc.addDamageReductionByArmor(amount);
		pc.addDmgup(amount);
		pc.addBowDmgup(amount);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onStrongmanBuff(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;
		final int skillId = L1SkillId.천하장사버프;

		if (pc.hasSkillEffect(skillId)) {
			pc.removeSkillEffect(skillId);
		}

		pc.addDamageReductionByArmor(5);
		SC_PacketBox.send(pc, SC_PacketBox.ICON_COOKING, pc, 187, 1800);
		pc.setDessertId(skillId);
		SC_ServerMessage.send(pc, 1426);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onSandWormAttack(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		final L1PcInstance pc = su.hdPlayer();
		final L1Character user = su.hdUser();

		if (pc == null || user == null) {
			return HandlerResult.HANDLED;
		}

		final int gfx;
		switch (su.hdSkillId()) {
		case L1SkillId.Sand_worms:
			gfx = 10145;
			break;
		case L1SkillId.Sand_worms1:
			gfx = 10195;
			break;
		case L1SkillId.Sand_worms2:
			gfx = 10194;
			break;
		case L1SkillId.Sand_worms3:
			gfx = 10191;
			break;
		default:
			return HandlerResult.NOT_HANDLED;
		}

		SC_UseAttackSkill.broadcastPair(
				user,
				user,
				pc,
				pc.getId(),
				gfx,
				pc.getX(),
				pc.getY(),
				ActionCodes.ACTION_Attack,
				false);
		SC_SkillSound.send(pc, pc.getId(), gfx);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onOriginalNoOp(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		switch (su.hdSkillId()) {
		case L1SkillId.잠재력소울:
		case L1SkillId.할파스의신의PVP:
		case L1SkillId.할파스의신의:
			return HandlerResult.HANDLED;
		default:
			return HandlerResult.NOT_HANDLED;
		}
	}

	// ===== common small utilities (dup 제거) =====
	private static void resetEffect(final L1Character cha, final int skillId) {
		if (cha == null) return;
		if (cha.hasSkillEffect(skillId)) cha.removeSkillEffect(skillId);
	}

	private static void sendSkillSoundBroadcast(final L1Character cha, final int gfx) {
		if (cha == null) return;
		final long snd = SC_SkillSound.pack(cha.getId(), gfx);

		// PC/NPC는 sendPackets가 존재 (캐스팅 없이도 안전하게 처리)
		if (cha instanceof L1PcInstance) {
			SC_SkillSound.sendPacked(((L1PcInstance) cha), snd);
		} 
		SC_SkillSound.broadcastFastPacked(cha, snd);
	}


	private static void sendSkillSoundOnce(final L1PcInstance pc, final int gfx) {
		if (pc == null) return;
		final long snd = SC_SkillSound.pack(pc.getId(), gfx);
		SC_SkillSound.sendPacked(pc, snd);
		SC_SkillSound.broadcastFastPacked(pc, snd);
	}

	private static final int[] ELEM_LESSER = { 45306, 45303, 45304, 45305 };

	private static final int[] ELEM_GREATER = { 460000355, 460000355, 460000355, 460000355 };

	private static final int[] MYTH_SPRITES = { 21070, 21053, 21066, 21049, 21140, 19514 };

	private static boolean isOneOfSprite(int sprite, int[] arr) {
		for (int v : arr) if (v == sprite) return true;
		return false;
	}

	private static boolean canRecallElemental(final L1PcInstance pc) {
		if (pc == null) return false;
		return ((pc.getMap().isRecallPets() && !pc.isInWarArea()) || pc.isGm());
	}

	private static int countSummons(final L1PcInstance pc) {
		if (pc == null) return 0;
		int c = 0;
		try {
			for (Object pet : pc.getPetList().values()) {
				if (pet instanceof L1SummonInstance) c += 1;
			}
		} catch (Throwable t) {
			// ignore
		}
		return c;
	}

	private static void deleteAllSummons(final L1PcInstance pc) {
		if (pc == null) return;
		try {
			for (Object pet : pc.getPetList().values()) {
				if (pet instanceof L1SummonInstance) {
					((L1NpcInstance) pet).deleteMe();
				}
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	private static int[] pickElementalCandidates(final int skillId, final boolean mythPoly) {
		if (skillId == L1SkillId.LESSER_ELEMENTAL) {
			return ELEM_LESSER;
		}
		// GREATER_ELEMENTAL
		if (mythPoly) {
			return ELEM_GREATER;
		} else {
			return ELEM_GREATER;
		}
	}

	private static int pickSummonIdByAttr(final Random rng, final int[] candidates, final int elfAttr) {
		if (candidates == null || candidates.length == 0) return 0;

		int summonId = 0;
		int npcattr = 1;
		for (int i = 0; i < candidates.length; i++) {
			if (npcattr == elfAttr) {
				summonId = candidates[i];
				break;
			}
			npcattr *= 2;
		}

		if (summonId == 0) {
			summonId = candidates[rng.nextInt(candidates.length)];
		}
		return summonId;
	}

	private static boolean hasAnySkillEffect(final L1PcInstance pc, final int... skillIds) {
		if (pc == null || skillIds == null) return false;
		for (int id : skillIds) {
			if (id > 0 && pc.hasSkillEffect(id)) return true;
		}
		return false;
	}

	private static void removeSkillEffects(final L1PcInstance pc, final int... skillIds) {
		if (pc == null || skillIds == null) return;
		for (int id : skillIds) {
			if (id > 0 && pc.hasSkillEffect(id)) {
				pc.removeSkillEffect(id);
			}
		}
	}

	private static void resetScalesEffectIfPresent(final L1PcInstance pc, final int skillId) {
		if (pc == null) return;
		if (!pc.hasSkillEffect(skillId)) return;

		switch (skillId) {
		case L1SkillId.SCALES_WATER_DRAGON:
			addERAndNotify(pc, -10);
			break;
		case L1SkillId.SCALES_EARTH_DRAGON:
			pc.getAC().addAc(3);
			pc.getResistance().addMr(-5);
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
			break;
		case L1SkillId.SCALES_RINDVIOR_DRAGON:
			pc.addDg(-10);
			break;
		case L1SkillId.SCALES_FIRE_DRAGON:
			pc.getResistance().addSpecialResistance(eKind.ABILITY, -5);
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
			pc.getResistance().addSpecialResistance(eKind.FEAR, -5);
			pc.addHitup(-5);
			break;
		}
		pc.killSkillEffectTimer(skillId);
	}

	private static void scheduleMultiHit(final L1PcInstance attacker, final L1Character target, final int hits, 
            final int intervalMs, final int maxRange, final MultiHitHook hook) {

        if (attacker == null || target == null || hits <= 0) {
            return;
        }

        final int token = attacker.newMultiHitToken();
        scheduleMultiHitWithToken(attacker, target, hits, intervalMs, maxRange, token, hook);
    }

    // =====================================================
    // token fixed multi-hit
    // Runnable 1개만 생성해서 순차 재사용
    // =====================================================
    private static void scheduleMultiHitWithToken(final L1PcInstance attacker, final L1Character target, 
            final int hits, final int intervalMs, final int maxRange, final int token, final MultiHitHook hook) {

        if (attacker == null || target == null || hits <= 0) {
            return;
        }

        if (attacker.isDead() || target.isDead()) {
            return;
        }

        final int attackerId = attacker.getId();
        final int targetId = target.getId();
        final short mapId = attacker.getMapId();

        final int delay = Math.max(0, intervalMs);
        final MultiHitHook h = hook != null ? hook : MultiHitHook.NONE;
        final long startNanos = System.nanoTime();

        final Runnable task = new Runnable() {
            private int hitIndex;

            @Override
            public void run() {
                boolean cancelled = false;

                try {
                    final L1World world = L1World.getInstance();
                    final L1Object attackerObj = world.findObject(attackerId);

                    if (!(attackerObj instanceof L1PcInstance)) {
                        cancelled = true;
                        return;
                    }

                    final L1PcInstance a = (L1PcInstance) attackerObj;

                    if (a.getMultiHitToken() != token) {
                        cancelled = true;
                        return;
                    }

                    final L1Object targetObj = world.findObject(targetId);

                    if (!(targetObj instanceof L1Character)) {
                        cancelled = true;
                        return;
                    }

                    final L1Character t = (L1Character) targetObj;

                    if (a.isDead() || t.isDead()) return;
                    if (a.getMapId() != mapId || t.getMapId() != mapId) return;

                    if (maxRange != Integer.MAX_VALUE) {
                        final int dx = Math.abs(a.getX() - t.getX());
                        final int dy = Math.abs(a.getY() - t.getY());

                        if (Math.max(dx, dy) > maxRange) {
                            return;
                        }
                    }
                    h.onHit(a, t, hitIndex, hits, true);
                    t.onAction(a);
                    h.onHit(a, t, hitIndex, hits, false);

                } catch (Throwable ignore) {
                } finally {
                    if (!cancelled) {
                        scheduleNext();
                    }
                }
            }

            private void scheduleNext() {
                final int nextHit = ++hitIndex;

                if (nextHit >= hits) {
                    return;
                }
                final long targetNanos = startNanos + ((long) nextHit * (long) delay * 1_000_000L);
                final long remainNanos = targetNanos - System.nanoTime();
                final long nextDelayMs;

                if (remainNanos <= 0L) {
                    nextDelayMs = 0L;
                } else {
                    nextDelayMs = (remainNanos + 999_999L) / 1_000_000L;
                }

                if (nextDelayMs <= 0L) {
                    ThreadPool.getInstance().execute(this);
                } else {
                    ThreadPool.getInstance().scheduleRequired(this, nextDelayMs);
                }
            }
        };

        ThreadPool.getInstance().execute(task);
    }
}