package l1j.server.server.model.character.skill;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;


import l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_IvenBuffIcon;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;


import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.eKind;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
import l1j.server.server.model.network.core.sc.packet.SC_Liquor;
import l1j.server.server.model.network.core.sc.packet.SC_Dexup;
import l1j.server.server.model.network.core.sc.packet.SC_Strup;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconShield;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconAura;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWisdomPotion;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWindShackle;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;
import l1j.server.server.model.network.core.sc.packet.SC_CurseBlind;
public final class SkillStopHandlers {

	private SkillStopHandlers() {}

	// ===== 결과 =====
	public enum StopResult {
		NOT_HANDLED, 
		HANDLED   

	}

	@FunctionalInterface
	public interface StopHandler {
		StopResult handle(L1Character cha, int skillId);
	}

	// ===== 작은 ID는 배열로 =====
	private static final int TABLE_SIZE = 32768; // 0..32767 fast
	private static final StopHandler[] TABLE = new StopHandler[TABLE_SIZE];

	// ===== 큰 ID는 맵으로 =====
	private static final Int2ObjectOpenHashMap<StopHandler> BIG = new Int2ObjectOpenHashMap<>(512);

	// 부팅 이후 런타임에서 실수로 register 못하게 잠금
	private static volatile boolean FROZEN = false;

	static {    
		register(L1SkillId.혈맹버프AC,   SkillStopHandlers::offClanBuffAc);
		register(L1SkillId.혈맹버프마방, SkillStopHandlers::offClanBuffMr);
		register(L1SkillId.혈맹버프PVP추가, SkillStopHandlers::offClanBuffPvpAdd);
		register(L1SkillId.혈맹버프PVP감소, SkillStopHandlers::offClanBuffPvpReduce);
		register(L1SkillId.혈맹버프HP증가, SkillStopHandlers::offClanBuffHp);
		register(L1SkillId.혈맹버프올내성, SkillStopHandlers::offClanBuffAllResist);
		register(L1SkillId.혈맹버프올적중, SkillStopHandlers::offClanBuffAllPierce);
		register(L1SkillId.혈맹버프올스텟, SkillStopHandlers::offClanBuffAllStat);
		register(L1SkillId.혈맹버프회복,     SkillStopHandlers::offClanBuffHeal); 

		register(L1SkillId.천하장사버프,     SkillStopHandlers::offCheonHaJangSaBuff);

		register(L1SkillId.N_BUFF_PVP_DMG,   SkillStopHandlers::offNBuffPvpDmg);
		register(L1SkillId.N_BUFF_HPMP,      SkillStopHandlers::offNBuffHpMp);
		register(L1SkillId.N_BUFF_DMG,       SkillStopHandlers::offNBuffDmg);
		register(L1SkillId.N_BUFF_REDUCT,    SkillStopHandlers::offNBuffReduct);
		register(L1SkillId.N_BUFF_SP,        SkillStopHandlers::offNBuffSp);
		register(L1SkillId.N_BUFF_STUN,      SkillStopHandlers::offNBuffStun);
		register(L1SkillId.N_BUFF_HOLD,      SkillStopHandlers::offNBuffHold);

		register(L1SkillId.N_BUFF_WATER_DMG, SkillStopHandlers::offNBuffWaterDmg);
		register(L1SkillId.N_BUFF_WIND_DMG,  SkillStopHandlers::offNBuffWindDmg);
		register(L1SkillId.N_BUFF_EARTH_DMG, SkillStopHandlers::offNBuffEarthDmg);
		register(L1SkillId.N_BUFF_FIRE_DMG,  SkillStopHandlers::offNBuffFireDmg);

		register(L1SkillId.N_BUFF_STR,       SkillStopHandlers::offNBuffStr);
		register(L1SkillId.N_BUFF_DEX,       SkillStopHandlers::offNBuffDex);
		register(L1SkillId.N_BUFF_INT,       SkillStopHandlers::offNBuffInt);
		register(L1SkillId.N_BUFF_WIS,       SkillStopHandlers::offNBuffWis);

		register(L1SkillId.N_BUFF_WATER,     SkillStopHandlers::offNBuffWater);
		register(L1SkillId.N_BUFF_WIND,      SkillStopHandlers::offNBuffWind);
		register(L1SkillId.N_BUFF_EARTH,     SkillStopHandlers::offNBuffEarth);
		register(L1SkillId.N_BUFF_FIRE,      SkillStopHandlers::offNBuffFire);
		register(L1SkillId.N_BUFF_ALL_RESIST,SkillStopHandlers::offNBuffAllResist);

		register(L1SkillId.FEAR,             SkillStopHandlers::offFear);

		register(L1SkillId.EXP_POTION,       SkillStopHandlers::offExpPotion);
		register(L1SkillId.TELEPORT_RULER,   SkillStopHandlers::offTeleportRuler);
		register(L1SkillId.miso1, SkillStopHandlers::offMiso1);
		register(L1SkillId.miso2, SkillStopHandlers::offMiso2);
		register(L1SkillId.miso3, SkillStopHandlers::offMiso3);

		register(L1SkillId.DRAGON_SET, SkillStopHandlers::offDragonSet);

		register(L1SkillId.HUNTER_BLESS,  SkillStopHandlers::offHunterBless);
		register(L1SkillId.HUNTER_BLESS1, SkillStopHandlers::offHunterBless1);

		register(L1SkillId.MEDITATION, SkillStopHandlers::offMeditation);

		register(L1SkillId.POLY_RING_MASTER, SkillStopHandlers::offPolyRingMaster);

		register(L1SkillId.THUNDER_GRAB, SkillStopHandlers::offThunderGrab);

		register(L1SkillId.SOUL_BARRIER, SkillStopHandlers::offSoulBarrier);

		register(L1SkillId.DOUBLE_BRAKE, SkillStopHandlers::offDoubleBrake);

		register(L1SkillId.EXP_BUFF,         SkillStopHandlers::offExpBuff);
		register(L1SkillId.TITANL_RISING,    SkillStopHandlers::offTitanlRising);
		register(L1SkillId.ABSOLUTE_BLADE,   SkillStopHandlers::offAbsoluteBlade);
		register(L1SkillId.DEATH_HEAL_Mob,   SkillStopHandlers::offDeathHealMob);
		register(L1SkillId.DEATH_HEAL,       SkillStopHandlers::offDeathHeal);

		register(L1SkillId.CLAN_BUFF1,       SkillStopHandlers::offClanBuff1);
		register(L1SkillId.CLAN_BUFF2,       SkillStopHandlers::offClanBuff2);
		register(L1SkillId.CLAN_BUFF3,       SkillStopHandlers::offClanBuff3);
		register(L1SkillId.CLAN_BUFF4,       SkillStopHandlers::offClanBuff4);

		register(L1SkillId.정상의가호,       SkillStopHandlers::offNormalBlessing);

		register(L1SkillId.레벨업보너스,     SkillStopHandlers::offLevelUpBonus);
		register(L1SkillId.DRAGON_PUPLE,     SkillStopHandlers::offDragonPuple);
		register(L1SkillId.DRAGON_TOPAZ,     SkillStopHandlers::offDragonTopaz);

		register(L1SkillId.포커스3단,        SkillStopHandlers::offFocus3);

		register(L1SkillId.TOMAHAWK,   SkillStopHandlers::offTomahawk);
		register(L1SkillId.완력빙수,   SkillStopHandlers::offStrIce);
		register(L1SkillId.민첩빙수,   SkillStopHandlers::offDexIce);
		register(L1SkillId.지식빙수,   SkillStopHandlers::offIntIce);

		register(L1SkillId.LIGHT,          SkillStopHandlers::offLight);
		register(L1SkillId.TRUE_TARGET,    SkillStopHandlers::offTrueTarget);
		register(L1SkillId.GLOWING_AURA, SkillStopHandlers::offGlowingWeapon);
		register(L1SkillId.God_buff,       SkillStopHandlers::offGodBuff);

		register(L1SkillId.BUFF_SAEL,      SkillStopHandlers::offBuffSael);
		register(L1SkillId.SHINING_AURA, SkillStopHandlers::offShiningShield);
		register(L1SkillId.BRAVE_AURA,   SkillStopHandlers::offBraveMental);
		register(L1SkillId.SHIELD,         SkillStopHandlers::offShield);
		register(L1SkillId.BLIND_HIDING,   SkillStopHandlers::offBlindHiding);
		register(L1SkillId.SHADOW_ARMOR,   SkillStopHandlers::offShadowArmor);
		register(L1SkillId.DRESS_DEXTERITY,SkillStopHandlers::offDressDexterity);
		register(L1SkillId.DRESS_MIGHTY,   SkillStopHandlers::offDressMighty);
		

		register(L1SkillId.PATIENCE, SkillStopHandlers::offPatience);
		
		register(L1SkillId.EARTH_GUARDIAN, SkillStopHandlers::offEarthGuardian);
		register(L1SkillId.RESIST_MAGIC,   SkillStopHandlers::offResistMagic);

		register(L1SkillId.RESIST_ELEMENTAL,      SkillStopHandlers::offResistElemental);
		register(L1SkillId.ELEMENTAL_PROTECTION, SkillStopHandlers::offElementalProtection);
		register(L1SkillId.ELEMENTAL_FALL_DOWN,  SkillStopHandlers::offElementalFallDown);
		register(L1SkillId.IRON_SKIN,            SkillStopHandlers::offIronSkin);
		register(L1SkillId.FIRE_SHIELD,          SkillStopHandlers::offFireShield);
		register(L1SkillId.PHYSICAL_ENCHANT_STR, SkillStopHandlers::offPhysicalEnchantStr);
		register(L1SkillId.PHYSICAL_ENCHANT_DEX, SkillStopHandlers::offPhysicalEnchantDex);
		register(L1SkillId.EARTH_WEAPON,         SkillStopHandlers::offEarthWeapon);

		register(L1SkillId.INFERNO,        SkillStopHandlers::offInferno);

		register(L1SkillId.FOCUS_WAVE,     SkillStopHandlers::offFocusWave);
		register(L1SkillId.HURRICANE,      SkillStopHandlers::offBraveSpeedReset);
		register(L1SkillId.SAND_STORM,     SkillStopHandlers::offBraveSpeedReset);
		register(L1SkillId.DANCING_BLADES, SkillStopHandlers::offBraveSpeedReset);

		register(L1SkillId.BUFF_CRAY,      SkillStopHandlers::offBuffCray);
		register(L1SkillId.BUFF_Vala,      SkillStopHandlers::offBuffVala);
		register(L1SkillId.BUFF_GUNTER,    SkillStopHandlers::offBuffGunter);

		register(L1SkillId.UNCANNY_DODGE,  SkillStopHandlers::offUncannyDodge);
		register(L1SkillId.BURNING_WEAPON, SkillStopHandlers::offBurningWeapon);
		register(L1SkillId.MIRROR_IMAGE,   SkillStopHandlers::offMirrorImage);
		register(L1SkillId.AQUA_SHOT,      SkillStopHandlers::offAquaShot);

		register(L1SkillId.INSIGHT,     SkillStopHandlers::offInsight);

		register(L1SkillId.Tam_Fruit1,  SkillStopHandlers::offTamFruitSync);
		register(L1SkillId.Tam_Fruit2,  SkillStopHandlers::offTamFruitSync);
		register(L1SkillId.Tam_Fruit3,  SkillStopHandlers::offTamFruitSync);
		register(L1SkillId.Tam_Fruit4,  SkillStopHandlers::offTamFruitSync);
		register(L1SkillId.Tam_Fruit5,  SkillStopHandlers::offTamFruitSync);

		register(L1SkillId.SHAPE_CHANGE, SkillStopHandlers::offShapeChange);

		register(L1SkillId.HOLY_WALK,            SkillStopHandlers::offBraveSpeedWalk);
		register(L1SkillId.MOVING_ACCELERATION, SkillStopHandlers::offBraveSpeedWalk);

		register(L1SkillId.BLOOD_LUST,           SkillStopHandlers::offBloodLust);

		register(L1SkillId.DARK_BLIND,           SkillStopHandlers::offDarkBlind);

		register(L1SkillId.CURSE_BLIND,          SkillStopHandlers::offCurseBlindFamily);
		register(L1SkillId.DARKNESS,             SkillStopHandlers::offCurseBlindFamily);
		register(L1SkillId.LINDBIOR_SPIRIT_EFFECT, SkillStopHandlers::offCurseBlindFamily);

		register(L1SkillId.CURSE_PARALYZE,       SkillStopHandlers::offCurseParalyzeFamily);
		register(L1SkillId.DESERT_SKILL1,        SkillStopHandlers::offCurseParalyzeFamily);

		register(L1SkillId.WEAKNESS,             SkillStopHandlers::offWeaknessFamily);
		register(L1SkillId.MOB_WEAKNESS_1,       SkillStopHandlers::offWeaknessFamily);

		register(L1SkillId.DISEASE,              SkillStopHandlers::offDiseaseFamily);
		register(L1SkillId.MOB_DISEASE_1,        SkillStopHandlers::offDiseaseFamily);
		register(L1SkillId.MOB_DISEASE_30,       SkillStopHandlers::offDiseaseFamily);

		register(L1SkillId.HORROR_OF_DEATH,      SkillStopHandlers::offHorrorOfDeath);
		register(L1SkillId.PANIC,                SkillStopHandlers::offPanic);

		register(L1SkillId.ICE_LANCE,            SkillStopHandlers::offIceLance);

		register(L1SkillId.MOB_BERSERKERS,       SkillStopHandlers::offMobBerserkers);

		register(L1SkillId.EARTH_BIND,           SkillStopHandlers::offEarthBindBasillCoca);
		register(L1SkillId.MOB_BASILL,           SkillStopHandlers::offEarthBindBasillCoca);
		register(L1SkillId.MOB_COCA,             SkillStopHandlers::offEarthBindBasillCoca);

		register(L1SkillId.SHOCK_STUN,           SkillStopHandlers::offStunFamily);
		register(L1SkillId.MOB_SHOCKSTUN_30,     SkillStopHandlers::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_18,     SkillStopHandlers::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_19,     SkillStopHandlers::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_20,     SkillStopHandlers::offStunFamily);
		register(L1SkillId.Mob_RANGESTUN_30,     SkillStopHandlers::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_6,       SkillStopHandlers::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_7,       SkillStopHandlers::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_8,       SkillStopHandlers::offStunFamily);
		register(L1SkillId.ANTA_SHOCKSTUN,       SkillStopHandlers::offStunFamily);
		register(L1SkillId.OMAN_STUN,            SkillStopHandlers::offStunFamily);
		register(L1SkillId.Maeno_STUN,           SkillStopHandlers::offStunFamily);

		register(L1SkillId.EMPIRE,               SkillStopHandlers::offEmpire);
		register(L1SkillId.BONE_BREAK,           SkillStopHandlers::offBoneBreak);

		register(L1SkillId.PHANTASM,             SkillStopHandlers::offSleepFamily);
		register(L1SkillId.FOG_OF_SLEEPING,      SkillStopHandlers::offSleepFamily);

		register(L1SkillId.SLOW,                  SkillStopHandlers::offSlow);
		register(L1SkillId.STATUS_FREEZE,         SkillStopHandlers::offStatusFreeze);

		register(L1SkillId.STATUS_IGNITION,       SkillStopHandlers::offStatusIgnition);
		register(L1SkillId.STATUS_QUAKE,          SkillStopHandlers::offStatusQuake);
		register(L1SkillId.STATUS_SHOCK,          SkillStopHandlers::offStatusShock);

		register(L1SkillId.STATUS_BRAVE,          SkillStopHandlers::offStatusBraveFamily);
		register(L1SkillId.STATUS_FRUIT,          SkillStopHandlers::offStatusBraveFamily);
		register(L1SkillId.STATUS_ELFBRAVE,       SkillStopHandlers::offStatusElfBrave);

		register(L1SkillId.STATUS_HASTE,          SkillStopHandlers::offStatusHaste);

		register(L1SkillId.STATUS_UNDERWATER_BREATH, SkillStopHandlers::offUnderwaterBreath);
		register(L1SkillId.STATUS_WISDOM_POTION,  SkillStopHandlers::offWisdomPotion);

		register(L1SkillId.STATUS_CHAT_PROHIBITED, SkillStopHandlers::offChatProhibited);

		register(L1SkillId.STATUS_CASHSCROLL,     SkillStopHandlers::offCashScroll1);
		register(L1SkillId.STATUS_CASHSCROLL2,    SkillStopHandlers::offCashScroll2);
		register(L1SkillId.STATUS_CASHSCROLL3,    SkillStopHandlers::offCashScroll3);
		register(L1SkillId.STATUS_CASHSCROLL4,    SkillStopHandlers::offCashScroll4);
		register(L1SkillId.STATUS_CASHSCROLL5,    SkillStopHandlers::offCashScroll5);
		register(L1SkillId.STATUS_CASHSCROLL6,    SkillStopHandlers::offCashScroll6);

		register(L1SkillId.STATUS_POISON,         SkillStopHandlers::offPoison);

		register(L1SkillId.성혈버프,               SkillStopHandlers::offSeonghyeolBuff);

		register(L1SkillId.COMA_A,                SkillStopHandlers::offComaA);
		register(L1SkillId.COMA_B,                SkillStopHandlers::offComaB);

		register(L1SkillId.FEATHER_BUFF_A,        SkillStopHandlers::offFeatherBuffA);
		register(L1SkillId.FEATHER_BUFF_B,        SkillStopHandlers::offFeatherBuffB);
		register(L1SkillId.FEATHER_BUFF_C,        SkillStopHandlers::offFeatherBuffC);
		register(L1SkillId.FEATHER_BUFF_D,        SkillStopHandlers::offFeatherBuffD);


		register(L1SkillId.ANTA_MAAN, SkillStopHandlers::offAntaMaan);
		register(L1SkillId.FAFU_MAAN, SkillStopHandlers::offFafuMaan);
		register(L1SkillId.LIND_MAAN, SkillStopHandlers::offLindMaan);
		register(L1SkillId.VALA_MAAN, SkillStopHandlers::offValaMaan);
		register(L1SkillId.BIRTH_MAAN, SkillStopHandlers::offBirthMaan);
		register(L1SkillId.SHAPE_MAAN, SkillStopHandlers::offShapeMaan);
		register(L1SkillId.LIFE_MAAN, SkillStopHandlers::offLifeMaan);

		register(L1SkillId.ANTA_BUFF, SkillStopHandlers::offAntaBuff);
		register(L1SkillId.FAFU_BUFF, SkillStopHandlers::offFafuBuff);
		register(L1SkillId.RIND_BUFF, SkillStopHandlers::offRindBuff);

		register(L1SkillId.STATUS_DRAGON_PEARL, SkillStopHandlers::offDragonPearl);

		register(L1SkillId.BOUNCE_ATTACK, SkillStopHandlers::offBounceAttack);

		register(L1SkillId.COUNTER_MIRROR, SkillStopHandlers::offCounterMirror);

		register(L1SkillId.DECREASE_WEIGHT, SkillStopHandlers::offReduceOrDecreaseWeight);
		register(L1SkillId.REDUCE_WEIGHT,   SkillStopHandlers::offReduceOrDecreaseWeight);

		register(L1SkillId.RINDVIOR_WIND_SHACKLE,   SkillStopHandlers::offWindShackleIcon);
		register(L1SkillId.RINDVIOR_WIND_SHACKLE_1, SkillStopHandlers::offWindShackleIcon);
		register(L1SkillId.DRAKE_WIND_SHACKLE,      SkillStopHandlers::offWindShackleIcon);

		register(L1SkillId.STORM_EYE,  SkillStopHandlers::offStormEye);
		register(L1SkillId.STORM_SHOT, SkillStopHandlers::offStormShot);
		register(L1SkillId.BERSERKERS, SkillStopHandlers::offBerserkers);
		
		register(L1SkillId.MAJESTY, SkillStopHandlers::offMajesty);
		register(L1SkillId.IMMUNE_TO_HARM, SkillStopHandlers::offImmuneToHarm);

		register(L1SkillId.FOCUS_SPRITS, SkillStopHandlers::offFocusSpirits);

		register(L1SkillId.CUBE_RICH, SkillStopHandlers::offCubeRich);
		register(L1SkillId.CUBE_GOLEM, SkillStopHandlers::offCubeGolem);
		register(L1SkillId.CUBE_OGRE, SkillStopHandlers::offCubeOgre);
		register(L1SkillId.CUBE_AVATAR, SkillStopHandlers::offCubeAvatar);

		register(L1SkillId.IMPACT, SkillStopHandlers::offImpact);

		register(L1SkillId.LUCIFER, SkillStopHandlers::offLucifer);
		register(L1SkillId.BLOW_ATTACK, SkillStopHandlers::offBlowAttack);
		register(L1SkillId.ARMOR_BRAKE, SkillStopHandlers::offArmorBrake);

		register(L1SkillId.GRACE_AVATAR, SkillStopHandlers::offGraceAvatar);

		register(L1SkillId.DRESS_EVASION, SkillStopHandlers::offDressEvasion);
		register(L1SkillId.AQUA_PROTECTER, SkillStopHandlers::offAquaProtecter);
		register(L1SkillId.SOLID_CARRIAGE, SkillStopHandlers::offSolidCarriage);

		register(L1SkillId.GUARD_BREAK, SkillStopHandlers::offGuardBreak);
		register(L1SkillId.STRIKER_GALE, SkillStopHandlers::offStrikerGale);

		register(L1SkillId.CLEAR_MIND, SkillStopHandlers::offClearMind);

		register(L1SkillId.SHADOW_FANG, SkillStopHandlers::offShadowFang);
		register(L1SkillId.BLESS_WEAPON, SkillStopHandlers::offBlessWeapon);
		register(L1SkillId.ENCHANT_WEAPON, SkillStopHandlers::offEnchantWeapon);
		register(L1SkillId.BLESSED_ARMOR, SkillStopHandlers::offBlessedArmor);

		register(L1SkillId.CYCLONE, SkillStopHandlers::offCyclone);

		register(L1SkillId.SHINING_ARMOR, SkillStopHandlers::offShiningArmor);
		register(L1SkillId.HOLY_WEAPON, SkillStopHandlers::offHolyWeapon);

		register(L1SkillId.MOEBIUS, SkillStopHandlers::offMoebius);

		register(L1SkillId.SCALES_EARTH_DRAGON, SkillStopHandlers::offScalesEarthDragon);
		register(L1SkillId.SCALES_WATER_DRAGON, SkillStopHandlers::offScalesWaterDragon);
		register(L1SkillId.SCALES_RINDVIOR_DRAGON, SkillStopHandlers::offScalesRindviorDragon);
		register(L1SkillId.SCALES_FIRE_DRAGON, SkillStopHandlers::offScalesFireDragon);

		register(L1SkillId.IllUSION_OGRE, SkillStopHandlers::offIllusionOgre);
		register(L1SkillId.IllUSION_LICH, SkillStopHandlers::offIllusionLich);
		register(L1SkillId.IllUSION_DIAMONDGOLEM, SkillStopHandlers::offIllusionDiamondGolem);
		register(L1SkillId.IllUSION_AVATAR, SkillStopHandlers::offIllusionAvatar);

		register(L1SkillId.REDUCTION_ARMOR, SkillStopHandlers::offReductionArmor);
		register(L1SkillId.FREEZEENG_ARMOR, SkillStopHandlers::offFreezeengArmor);

		register(L1SkillId.ENCHANT_ACURUCY, SkillStopHandlers::offEnchantAcurucy);

		register(L1SkillId.PRIDE, SkillStopHandlers::offPride);
		register(L1SkillId.GIGANTIC, SkillStopHandlers::offGigantic);

		register(L1SkillId.EAGGLE_EYE, SkillStopHandlers::offEaggleEye);

		register(L1SkillId.ABSOLUTE_BARRIER, SkillStopHandlers::offAbsoluteBarrier);
		
		register(L1SkillId.POWERRIP,        SkillStopHandlers::offPowerRip);
		register(L1SkillId.DESPERADO,       SkillStopHandlers::offDesperado);
		register(L1SkillId.ADVANCE_SPIRIT,  SkillStopHandlers::offAdvanceSpirit);
		register(L1SkillId.HASTE,           SkillStopHandlers::offHasteFamily);
		register(L1SkillId.GREATER_HASTE,   SkillStopHandlers::offHasteFamily);
		
		register(L1SkillId.매직실드1, SkillStopHandlers::offNoop);
		register(L1SkillId.버닝샷, SkillStopHandlers::offLegacyBurningShot);
		register(L1SkillId.매직실드2, SkillStopHandlers::offMagicShield2);
		register(L1SkillId.기간틱, SkillStopHandlers::offLegacyGigantic);
		register(L1SkillId.엠파이어오버로드DG, SkillStopHandlers::offEmpireOverloadDg);
		register(L1SkillId.이뮨투함블레이드, SkillStopHandlers::offImmuneBladeCustom);
		register(L1SkillId.비전텔레포트, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.이터니티대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.데몰리션대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.엘븐스트라이크대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.엘리멘탈대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.NO_DIS, SkillStopHandlers::offNoDis);
		register(L1SkillId.어드밴스대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.쉐도우대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.어벤저대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.버닝샷대기, SkillStopHandlers::offCooldownNotice);
		register(L1SkillId.DRAGON_HUNTER_BLESS, SkillStopHandlers::offNoop);
		register(L1SkillId.격전의주문서, SkillStopHandlers::offBattleScroll);
		register(L1SkillId.루돌프의버프, SkillStopHandlers::offRudolphBuff);
		register(L1SkillId.HUNTER_BLESS2, SkillStopHandlers::offNoop);
		register(L1SkillId.프라임, SkillStopHandlers::offPrime);
		register(L1SkillId.프라임1, SkillStopHandlers::offPrime1);
		register(L1SkillId.프라임85, SkillStopHandlers::offNoop);
		register(L1SkillId.프라임90, SkillStopHandlers::offNoop);
		register(L1SkillId.프라임95, SkillStopHandlers::offNoop);
		register(L1SkillId.일루션리치, SkillStopHandlers::offLegacyIllusionLich);
		register(L1SkillId.일루션골렘, SkillStopHandlers::offLegacyIllusionGolem);
		register(L1SkillId.에테리얼, SkillStopHandlers::offSimpleInventoryIcon);
		register(L1SkillId.솔리드캐리지, SkillStopHandlers::offLegacySolidCarriage);
		register(L1SkillId.버닝, SkillStopHandlers::offSimpleInventoryIcon);
		register(L1SkillId.이베이젼, SkillStopHandlers::offLegacyEvasion);
		register(L1SkillId.리버레이션, SkillStopHandlers::offLiberation);
		register(L1SkillId.잠재력저지먼트1, SkillStopHandlers::offJudgment);
		register(L1SkillId.잠재력저지먼트2, SkillStopHandlers::offJudgment);
		register(L1SkillId.잠재력저지먼트3, SkillStopHandlers::offJudgment);
		register(L1SkillId.잠재력저지먼트4, SkillStopHandlers::offJudgment);
		register(L1SkillId.잠재력저지먼트5, SkillStopHandlers::offJudgment);
		register(L1SkillId.할파스, SkillStopHandlers::offHalpas);
		register(L1SkillId.할파스의신의, SkillStopHandlers::offHalpasGod);
		register(L1SkillId.할파스의신의PVP, SkillStopHandlers::offHalpasGodPvp);
		register(L1SkillId.잠재력소울, SkillStopHandlers::offPotentialSoul);
		register(L1SkillId.잠재력소울스킬, SkillStopHandlers::offPotentialSoulSkill);
		register(L1SkillId.가드피어효과, SkillStopHandlers::offGuardFearEffect);
		register(L1SkillId.가드피어호러효과, SkillStopHandlers::offGuardFearHorrorEffect);
	//	register(800018, SkillStopHandlers::offTikalRespawn);
	//	register(800019, SkillStopHandlers::offTikalRespawn);
		register(L1SkillId.전투지휘, SkillStopHandlers::offCombatCommand);
		register(L1SkillId.DELAY, SkillStopHandlers::offDelay);
		register(L1SkillId.프라이드, SkillStopHandlers::offLegacyPride);
		register(L1SkillId.포텐셜, SkillStopHandlers::offPotential);
		register(L1SkillId.어드밴스, SkillStopHandlers::offLegacyAdvance);
		register(L1SkillId.이터디스, SkillStopHandlers::offEtherDis);
		register(L1SkillId.STATUS_BLUE_POTION, SkillStopHandlers::offNoop);
		register(L1SkillId.STATUS_BLUE_POTION2, SkillStopHandlers::offNoop);
		register(L1SkillId.메티스축복주문서, SkillStopHandlers::offMetisBlessScroll);
		register(L1SkillId.버서크, SkillStopHandlers::offBerserkCustom);
		register(L1SkillId.버서크대기, SkillStopHandlers::offNoop);
		register(L1SkillId.SIDE_OF_ME_BLESSING, SkillStopHandlers::offStartBlessing);
		register(L1SkillId.RE_START_BLESSING, SkillStopHandlers::offStartBlessing);
		register(L1SkillId.NEW_START_BLESSING, SkillStopHandlers::offStartBlessing);
		register(L1SkillId.LIFE_BLESSING, SkillStopHandlers::offStartBlessing);
		register(L1SkillId.COUNTER_BARRIER, SkillStopHandlers::offCounterBarrierLegacy);

		register(L1SkillId.BUFF_특별한성장축복, SkillStopHandlers::offSpecialBuff);
		register(L1SkillId.BUFF_특별한방어축복, SkillStopHandlers::offSpecialBuff);
		register(L1SkillId.BUFF_특별한공격축복, SkillStopHandlers::offSpecialBuff);
		
		
		// 등록 끝나면 잠궈라 (실수/레이스 방지)
		freeze();
	}
	/** 부팅 후 더 이상 register 못하게 잠금 */
	public static void freeze() {
		FROZEN = true;
	}
	
	/** stop 핸들러 등록 (부팅 시점에서만 호출 권장) */
	public static void register(int skillId, StopHandler h) {
		if (h == null || skillId < 0) return;

		if (FROZEN) {
			return;
		}

		if (skillId < TABLE_SIZE) {
			TABLE[skillId] = h;
		} else {
			BIG.put(skillId, h);
		}
	}

	/** stop 처리 디스패치: 핸들러 있으면 처리, 없으면 NOT_HANDLED */
	public static StopResult dispatch(L1Character cha, int skillId) {
		if (skillId < 0) return StopResult.NOT_HANDLED;

		final StopHandler h = (skillId < TABLE_SIZE) ? TABLE[skillId] : BIG.get(skillId);
		if (h == null) return StopResult.NOT_HANDLED;

		try {
			final StopResult r = h.handle(cha, skillId);
			return (r != null) ? r : StopResult.NOT_HANDLED;
		} catch (Throwable t) {
			return StopResult.NOT_HANDLED;
		}
	}

	/** 디버그/점검용: 해당 스킬 stop 핸들러 존재 여부 */
	public static boolean hasHandler(int skillId) {
		if (skillId < 0) return false;
		return (skillId < TABLE_SIZE) ? (TABLE[skillId] != null) : (BIG.get(skillId) != null);
	}

	/** 디버그용: 핸들러 직접 조회 */
	public static StopHandler getHandlerOrNull(int skillId) {
		if (skillId < 0) return null;
		return (skillId < TABLE_SIZE) ? TABLE[skillId] : BIG.get(skillId);
	}

	private static StopResult offClanBuffAc(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(5);
		SC_OwnCharAttrDef.send(pc);

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffMr(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addMr(-10);
		SC_SPMR.send(pc); 

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffPvpAdd(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addPVPweaponTotalDamage(-3);

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffPvpReduce(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addcalcPcDefense(-3);

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffHp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addMaxHp(-150);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffAllResist(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addSpecialResistance(eKind.ALL, -3);

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffAllPierce(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addSpecialPierce(eKind.ALL, -3);

		return StopResult.HANDLED;
	}

	private static StopResult offClanBuffAllStat(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedStr((byte) -3);
		pc.getAbility().addAddedDex((byte) -3);
		pc.getAbility().addAddedCon((byte) -3);
		pc.getAbility().addAddedInt((byte) -3);
		pc.getAbility().addAddedWis((byte) -3);
		pc.getAbility().addAddedCha((byte) -3);
		SC_OwnCharStatus2.send(pc);

		return StopResult.HANDLED;
	}
	private static SkillStopHandlers.StopResult offClanBuffHeal(L1Character cha, int skillId) {
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offCheonHaJangSaBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(-5);
		return StopResult.HANDLED;
	}

	// ===== N_BUFF =====

	private static SkillStopHandlers.StopResult offNBuffPvpDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addPVPweaponTotalDamage(-1);
		pc.getResistance().addcalcPcDefense(-1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4346, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffHpMp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addMaxHp(-50);
		pc.addMaxMp(-50);
		pc.addWeightReduction(-3);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_IvenBuffIcon.send(pc, skillId, false, 4347, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDmgup(-1);
		pc.addBowDmgup(-1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4348, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffReduct(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(-1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4349, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffSp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addSp(-1);
		SC_SPMR.send(pc);
		SC_IvenBuffIcon.send(pc, skillId, false, 4350, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffStun(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addSpecialResistance(eKind.ABILITY, -2);
		SC_IvenBuffIcon.send(pc, skillId, false, 4351, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffHold(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addSpecialResistance(eKind.SPIRIT, -2);
		SC_IvenBuffIcon.send(pc, skillId, false, 4352, 0);
		return StopResult.HANDLED;
	}

	// dmg-attribute icons only

	private static SkillStopHandlers.StopResult offNBuffWaterDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_IvenBuffIcon.send(pc, skillId, false, 4342, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffWindDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_IvenBuffIcon.send(pc, skillId, false, 4343, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffEarthDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_IvenBuffIcon.send(pc, skillId, false, 4344, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffFireDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_IvenBuffIcon.send(pc, skillId, false, 4345, 0);
		return StopResult.HANDLED;
	}

	// stat

	private static SkillStopHandlers.StopResult offNBuffStr(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedStr((byte) -1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4338, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffDex(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedDex((byte) -1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4339, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffInt(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedInt((byte) -1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4340, 0);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffWis(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedWis((byte) -1);
		SC_IvenBuffIcon.send(pc, skillId, false, 4341, 0);
		return StopResult.HANDLED;
	}

	// elemental resist

	private static SkillStopHandlers.StopResult offNBuffWater(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addWater(-5);
		SC_IvenBuffIcon.send(pc, skillId, false, 4333, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffWind(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addWind(-5);
		SC_IvenBuffIcon.send(pc, skillId, false, 4334, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffEarth(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addEarth(-5);
		SC_IvenBuffIcon.send(pc, skillId, false, 4335, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffFire(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addFire(-5);
		SC_IvenBuffIcon.send(pc, skillId, false, 4336, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offNBuffAllResist(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addFire(-5);
		pc.getResistance().addEarth(-5);
		pc.getResistance().addWater(-5);
		pc.getResistance().addWind(-5);
		SC_IvenBuffIcon.send(pc, skillId, false, 4337, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	// ===== misc =====

	private static SkillStopHandlers.StopResult offFear(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDg(25);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offExpPotion(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (pc.getSafetyZone()) SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_POTION + 1);
		else SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_POTION);
		return StopResult.HANDLED;
	}

	private static SkillStopHandlers.StopResult offTeleportRuler(L1Character cha, int skillId) {
		// 원본 switch에서 "break;"만 있어서 동일하게 NO-OP
		return StopResult.HANDLED;
	}
	private static StopResult offMiso1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		// 원본: 내용 없음 (NO-OP)
		return StopResult.HANDLED;
	}

	private static StopResult offMiso2(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addMaxHp(-100);
		pc.getResistance().addMr(-10);
		pc.addHpr(-2);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offMiso3(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDmgup(-3);
		pc.addBowDmgup(-3);
		pc.addMaxMp(-50);
		pc.getAbility().addSp(-3);
		pc.addMpr(-2);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offDragonSet(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDmgup(-3);
		pc.addDmgRate(-3);
		pc.addBowDmgup(-3);
		pc.addBowHitup(-3);
		pc.getAC().addAc(3);
		pc.getAbility().addSp(-2);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offHunterBless(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;

		return StopResult.HANDLED;
	}

	private static StopResult offHunterBless1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(2);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offMeditation(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		// 원본: 내용 없음 (NO-OP)
		return StopResult.HANDLED;
	}

	private static StopResult offPolyRingMaster(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;

			pc.getAbility().addAddedStr((byte) -1);
			pc.getAbility().addAddedDex((byte) -1);
			pc.getAbility().addAddedCon((byte) -1);
			pc.getAbility().addAddedInt((byte) -1);
			pc.getAbility().addAddedWis((byte) -1);
			pc.getAbility().addAddedCha((byte) -1);

			pc.addMaxHp(-200);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());

			if (pc.isInParty()) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
		}

		L1PolyMorph.undoPoly(cha);
		return StopResult.HANDLED;
	}

	private static StopResult offThunderGrab(L1Character cha, int skillId) {
		if (!(cha instanceof L1NpcInstance)) return StopResult.NOT_HANDLED;
		L1NpcInstance npc = (L1NpcInstance) cha;

		npc.set발묶임상태(false);
		return StopResult.HANDLED;
	}

	private static StopResult offSoulBarrier(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// removed: pc.removeSkillEffect(SOUL_BARRIER);
		SC_InventoryIcon.icoEnd(pc, L1SkillId.SOUL_BARRIER);
		return StopResult.HANDLED;
	}

	private static StopResult offDoubleBrake(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 2949, 0, false, true);
		return StopResult.HANDLED;
	}

	private static StopResult offExpBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		long hasad = pc.getAccount().getBlessOfAin();
		if (hasad <= 10000) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_BUFF + 1);
		} else {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_BUFF);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offTitanlRising(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// removed: pc.removeSkillEffect(TITANL_RISING);
		pc.setRisingUp(0);
		return StopResult.HANDLED;
	}

	private static StopResult offDeathHealMob(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		// removed: pc.removeSkillEffect(DEATH_HEAL_Mob);
		return StopResult.HANDLED;
	}

	private static StopResult offDeathHeal(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		// removed: pc.removeSkillEffect(DEATH_HEAL);
		return StopResult.HANDLED;
	}

	private static StopResult offClanBuff1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// 일반 공격 태세
		pc.addDmgup(-2);
		pc.addBowDmgup(-2);
		SC_ServerMessage.send(pc, 4619, "$22503");
		return StopResult.HANDLED;
	}

	private static StopResult offClanBuff2(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// 일반 방어 태세
		pc.getAC().addAc(3);
		SC_OwnCharAttrDef.send(pc);
		SC_ServerMessage.send(pc, 4619, "$22504");
		return StopResult.HANDLED;
	}

	private static StopResult offClanBuff3(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// 전투 공격 태세
		// pc.addPvPDmgup(-1);
		SC_ServerMessage.send(pc, 4619, "$22505");
		return StopResult.HANDLED;
	}

	private static StopResult offClanBuff4(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// 전투 방어 태세
		// pc.addDmgReducPvp(-1);
		SC_ServerMessage.send(pc, 4619, "$22506");
		return StopResult.HANDLED;
	}

	private static StopResult offNormalBlessing(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDamageReductionByArmor(-8);
		return StopResult.HANDLED;
	}

	private static StopResult offLevelUpBonus(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_PacketBox.send(pc, 0, true, true);
		return StopResult.HANDLED;
	}

	private static StopResult offDragonPuple(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_PacketBox.send(pc, 0, 1, true, true);
		return StopResult.HANDLED;
	}

	private static StopResult offDragonTopaz(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_PacketBox.send(pc, 0, 2, true, true);
		return StopResult.HANDLED;
	}

	private static StopResult offFocus3(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (pc.get_focus_wave_level() != 1) {
			SC_SkillSound.send(pc, pc.getId(), 16540);
			SC_SkillSound.broadcastFast(pc, pc.getId(), 16540);

			SC_SkillBrave.send(pc, pc.getId(), 10, pc.getSkillEffectTimeSec(L1SkillId.FOCUS_WAVE));
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 10, 0);

			pc.set_focus_wave_level(1);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offTomahawk(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		return StopResult.HANDLED;
	}

	private static StopResult offStrIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			pc.addHitup(-5);
			pc.addDmgup(-3);
			pc.getAbility().addAddedStr(-1);
			SC_OwnCharStatus2.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDexIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance  pc) {
			pc.addBowHitup(-5);
			pc.addBowDmgup(-3);
			pc.getAbility().addAddedDex(-1);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offIntIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance  pc) {
			pc.addMaxMp(-50);
			pc.getAbility().addSp(-1);
			pc.addMagicHit(-3);
			pc.getAbility().addAddedInt(-1);
			SC_OwnCharStatus2.send(pc);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offLight(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			if (!cha.isInvisble()) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getLight().turnOnOffLight();
			}
			return StopResult.HANDLED;
		}
		return StopResult.NOT_HANDLED;
	}

	private static StopResult offTrueTarget(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			TrueTargetManager.clear(pc);
		}
		SC_TrueTargetNew.broadcastFast(cha, cha.getId(), false);

		return StopResult.HANDLED;
	}

	private static StopResult offGlowingWeapon(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			pc.addHitup(-5);
			pc.addDmgup(-5);
			SC_SkillIconAura.send(pc, 113, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offGodBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAC().addAc(2);
		pc.addHitup(-3);
		pc.addMaxHp(-20);
		pc.addMaxMp(-13);
		pc.getResistance().addSpecialResistance(eKind.SPIRIT, -10);
		return StopResult.HANDLED;
	}


	private static StopResult offBuffSael(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (pc.hasSkillEffect(L1SkillId.BUFF_SAEL)) {
			// removed: pc.removeSkillEffect(L1SkillId.BUFF_SAEL);
		}
		pc.getAC().addAc(8);
		pc.addBowHitup(-6);
		pc.addBowDmgup(-3);
		pc.addMaxHp(-80);
		pc.addMaxMp(-10);
		pc.addHpr(-8);
		pc.addMpr(-1);
		pc.getResistance().addWater(-30);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offShiningShield(L1Character cha, int skillId) {
		cha.getAC().addAc(8);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconAura.send(pc, 114, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBraveMental(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconAura.send(pc, 116, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offShield(L1Character cha, int skillId) {
		cha.getAC().addAc(2);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconShield.send(pc, 1, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBlindHiding(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.delBlindHiding();
		return StopResult.HANDLED;
	}

	private static StopResult offShadowArmor(L1Character cha, int skillId) {
		cha.getResistance().addMr(-5);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SPMR.send(pc);
			// SC_SkillIconShield.send(pc, 3, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDressDexterity(L1Character cha, int skillId) {
		cha.getAbility().addAddedDex((byte) -3);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Dexup.send(pc, pc, 3, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDressMighty(L1Character cha, int skillId) {
		cha.getAbility().addAddedStr((byte) -3);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Strup.send(pc, pc, 3, 0);
		}
		return StopResult.HANDLED;
	}
	
	private static StopResult offPatience(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		
	    pc.addDamageReductionByArmor(-2);
		return StopResult.HANDLED;
	}

	private static StopResult offEarthGuardian(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		
	    pc.addDamageReductionByArmor(-2);
		SC_SkillIconShield.send(pc, 7, 0);
		return StopResult.HANDLED;
	}

	private static StopResult offResistMagic(L1Character cha, int skillId) {
		cha.getResistance().addMr(-10);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offResistElemental(L1Character cha, int skillId) {
		cha.getResistance().addAllNaturalResistance(-10);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offElementalProtection(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		int attr = pc.getElfAttr();
		if (attr == 1) {
			cha.getResistance().addEarth(-50);
		} else if (attr == 2) {
			cha.getResistance().addFire(-50);
		} else if (attr == 4) {
			cha.getResistance().addWater(-50);
		} else if (attr == 8) {
			cha.getResistance().addWind(-50);
		}

		pc.getResistance().addSpecialPierce(eKind.SPIRIT, -3);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offElementalFallDown(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;

			int attr = pc.getAddAttrKind();
			int i = 50;
			switch (attr) {
			case 1: pc.getResistance().addEarth(i); break;
			case 2: pc.getResistance().addFire(i); break;
			case 4: pc.getResistance().addWater(i); break;
			case 8: pc.getResistance().addWind(i); break;
			default: break;
			}

			SC_InventoryIcon.icoEnd(pc, L1SkillId.ELEMENTAL_FALL_DOWN);
			pc.setAddAttrKind(0);
			SC_OwnCharAttrDef.send(pc);
			return StopResult.HANDLED;
		}

		if (cha instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) cha;

			int attr = npc.getAddAttrKind();
			int i = 50;
			switch (attr) {
			case 1: npc.getResistance().addEarth(i); break;
			case 2: npc.getResistance().addFire(i); break;
			case 4: npc.getResistance().addWater(i); break;
			case 8: npc.getResistance().addWind(i); break;
			default: break;
			}
			npc.setAddAttrKind(0);
			return StopResult.HANDLED;
		}

		return StopResult.NOT_HANDLED;
	}

	private static StopResult offIronSkin(L1Character cha, int skillId) {
		cha.getAC().addAc(10);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconShield.send(pc, 10, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFireShield(L1Character cha, int skillId) {
		cha.getAC().addAc(4);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconShield.send(pc, 4, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPhysicalEnchantStr(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedStr((byte) -5);
			SC_Strup.send(pc, pc, 1, 0);		
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPhysicalEnchantDex(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedDex((byte) -5);
			SC_Dexup.send(pc, pc, 1, 0);			
		}
		return StopResult.HANDLED;
	}

	private static StopResult offEarthWeapon(L1Character cha, int skillId) {
		cha.addHitup(-4);
		cha.addDmgup(-2);
		if (cha instanceof L1PcInstance) {
	//		L1PcInstance pc = (L1PcInstance) cha;
			// SC_SkillIconAura.send(pc, 147, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offInferno(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			SC_PacketBox.send(((L1PcInstance) cha), SC_PacketBox.UNLIMITED_ICON1, 755, false);
		}
		return StopResult.HANDLED;
	}

	// FOCUS_WAVE는 원래 fall-through로 아래 brave reset까지 같이 탐
	private static StopResult offFocusWave(L1Character cha, int skillId) {
		cha.set_focus_wave_level(0);
		return offBraveSpeedReset(cha, skillId);
	}

	private static StopResult offBraveSpeedReset(L1Character cha, int skillId) {
		cha.setBraveSpeed(0);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillBrave.send(pc, pc.getId(), 0, 0);
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 0, 0);
		}
		return StopResult.HANDLED;
	}

	// 크레이 혈흔
	private static StopResult offBuffCray(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addHitup(-5);
		pc.addDmgup(-1);
		pc.addBowHitup(-5);
		pc.addBowDmgup(-1);
		pc.addMaxHp(-100);
		pc.addMaxMp(-50);
		pc.addHpr(-3);
		pc.addMpr(-3);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offBuffVala(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addHitup(-5);
		pc.addDmgup(-1);
		pc.addBowHitup(-5);
		pc.addBowDmgup(-1);
		pc.addMaxHp(-100);
		pc.addMaxMp(-50);
		pc.addHpr(-3);
		pc.addMpr(-3);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offBuffGunter(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getAbility().addAddedDex((byte) -5);
		pc.addBowHitup(-7);
		pc.addBowDmgup(-5);
		pc.addMaxHp(-100);
		pc.addMaxMp(-40);
		pc.addHpr(-10);
		pc.addMpr(-3);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	// UI DG표시
	private static StopResult offUncannyDodge(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDg(-30);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBurningWeapon(L1Character cha, int skillId) {
		cha.addDmgup(-6);
		cha.addHitup(-3);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconAura.send(pc, 162, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offMirrorImage(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDg(-30);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offAquaShot(L1Character cha, int skillId) {
		cha.addBowHitup(-4);
		// 원본도 pc패킷은 주석이라 유지
		// if (cha instanceof L1PcInstance) { ... }
		return StopResult.HANDLED;
	}

	private static StopResult offInsight(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedStr((byte) -1);
			pc.getAbility().addAddedDex((byte) -1);
			pc.getAbility().addAddedCon((byte) -1);
			pc.getAbility().addAddedInt((byte) -1);
			pc.getAbility().addAddedWis((byte) -1);
			pc.resetBaseMr();
		}
		return StopResult.HANDLED;
	}

	private static StopResult offTamFruitSync(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			TamFruitUseService.syncForPc(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offShapeChange(L1Character cha, int skillId) {
		L1PolyMorph.undoPoly(cha);
		return StopResult.HANDLED;
	}

	private static StopResult offBraveSpeedWalk(L1Character cha, int skillId) {
		cha.setBraveSpeed(0);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillBrave.send(pc, pc.getId(), 0, 0);
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 0, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBloodLust(L1Character cha, int skillId) {
		cha.setBraveSpeed(0);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillBrave.send(pc, pc.getId(), 0, 0);
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 0, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDarkBlind(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, false);
		}
		cha.setSleeped(false);
		return StopResult.HANDLED;
	}

	private static StopResult offCurseBlindFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			SC_CurseBlind.send(pc, 0);
	        pc.hideBlindToInvis();
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCurseParalyzeFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			SC_Poison.send(pc, pc.getId(), 0);
			SC_Poison.broadcastFast(pc, pc.getId(), 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offWeaknessFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDmgup(5);
			pc.addHitup(1);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDiseaseFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addHitup(6);
			pc.getAC().addAc(-12);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offHorrorOfDeath(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedStr((byte) 3);
			pc.getAbility().addAddedInt((byte) 3);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPanic(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedStr((byte) 1);
			pc.getAbility().addAddedDex((byte) 1);
			pc.getAbility().addAddedCon((byte) 1);
			pc.getAbility().addAddedInt((byte) 1);
			pc.getAbility().addAddedWis((byte) 1);
			pc.getAbility().addAddedCha((byte) 1);
			pc.resetBaseMr();
		}
		return StopResult.HANDLED;
	}

	private static StopResult offIceLance(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Poison.send(pc, pc.getId(), 0);
			SC_Poison.broadcastFast(pc, pc.getId(), 0);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
		} else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			L1NpcInstance npc = (L1NpcInstance) cha;
			SC_Poison.broadcastFast(npc, npc.getId(), 0);
			npc.setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offMobBerserkers(L1Character cha, int skillId) {
		if (cha instanceof L1NpcInstance) {
			cha.setMoveSpeed(0);
			cha.setBraveSpeed(0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offEarthBindBasillCoca(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Poison.send(pc, pc.getId(), 0);
			SC_Poison.broadcastFast(pc, pc.getId(), 0);
			if (skillId == L1SkillId.EARTH_BIND) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
			}
		} else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			L1NpcInstance npc = (L1NpcInstance) cha;
			SC_Poison.broadcastFast(npc, npc.getId(), 0);
			// npc.setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStunFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_STUN, false);
		}
		else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			((L1NpcInstance) cha).setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offEmpire(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			SC_Paralysis.send(((L1PcInstance) cha), SC_Paralysis.TYPE_STUN, false);
		} else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			((L1NpcInstance) cha).setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBoneBreak(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			SC_Paralysis.send(((L1PcInstance) cha), SC_Paralysis.TYPE_STUN, false);
		} else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			((L1NpcInstance) cha).setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offSleepFamily(L1Character cha, int skillId) {
		cha.setSleeped(false);
		if (cha instanceof L1PcInstance) {
			SC_Paralysis.send(((L1PcInstance) cha), SC_Paralysis.TYPE_SLEEP, false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offSlow(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillHaste.send(pc, pc.getId(), 0, 0);
			SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
		}
		cha.setMoveSpeed(0);
		return StopResult.HANDLED;
	}

	private static StopResult offStatusFreeze(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, false);
		} else if (cha instanceof L1MonsterInstance
				|| cha instanceof L1SummonInstance
				|| cha instanceof L1PetInstance
				|| cha instanceof L1CompanionInstance) {
			((L1NpcInstance) cha).setParalyzed(false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusIgnition(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getResistance().addFire(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusQuake(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getResistance().addEarth(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusShock(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getResistance().addWind(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusBraveFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillBrave.send(pc, pc.getId(), 0, 0);
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 0, 0);
		}
		cha.setBraveSpeed(0);
		return StopResult.HANDLED;
	}

	private static StopResult offStatusElfBrave(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillBrave.send(pc, pc.getId(), 0, 0);
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 0, 0);
		}
		cha.setBraveSpeed(0);
		return StopResult.HANDLED;
	}

	private static StopResult offStatusHaste(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillHaste.send(pc, pc.getId(), 0, 0);
			SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
		}
		cha.setMoveSpeed(0);
		return StopResult.HANDLED;
	}

	private static StopResult offUnderwaterBreath(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			SC_SkillIconBlessOfEva.send(((L1PcInstance) cha), cha.getId(), 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offWisdomPotion(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addSp(-2);
			pc.addMpr(-2);
			SC_SkillIconWisdomPotion.send(pc, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offChatProhibited(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			SC_ServerMessage.send(((L1PcInstance) cha), 288);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll1(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMaxHp(-50);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			if (pc.isInParty()) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll2(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMaxMp(-40);
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll3(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDmgup(-3);
			pc.addHitup(-3);
			pc.getAbility().addSp(-3);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll4(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addSp(-3);
			pc.addMagicHit(-5);
			pc.getResistance().addcalcPcDefense(-3);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll5(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addBowDmgup(-3);
			pc.addBowHitup(-5);
			pc.getResistance().addcalcPcDefense(-3);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offCashScroll6(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDmgup(-3);
			pc.addHitup(-5);
			pc.getResistance().addcalcPcDefense(-3);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPoison(L1Character cha, int skillId) {
		cha.curePoison();
		return StopResult.HANDLED;
	}

	private static StopResult offSeonghyeolBuff(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMaxHp(-300);
			pc.addMaxMp(-150);
			pc.addDmgup(-3);
			pc.addBowDmgup(-3);
			pc.getAbility().addSp(-3);
			pc.getAC().addAc(10);
			pc.getResistance().addMr(-15);
			
			pc.getResistance().addPVPweaponTotalDamage(-5);
			pc.getResistance().addcalcPcDefense(-5);
			
			SC_InventoryIcon.icoEnd(pc, L1SkillId.성혈버프);
			
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offComaA(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedCon(-1);
			pc.getAbility().addAddedDex(-5);
			pc.getAbility().addAddedStr(-5);
			pc.addHitRate(-3);
			pc.getAC().addAc(3);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offComaB(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addSp(-1);
			pc.getAbility().addAddedCon(-3);
			pc.getAbility().addAddedDex(-5);
			pc.getAbility().addAddedStr(-5);
			pc.addHitRate(-5);
			pc.getAC().addAc(8);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFeatherBuffA(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addHpr(-3);
			pc.addMpr(-3);
			pc.addDmgup(-2);
			pc.addHitup(-2);
			pc.addMaxHp(-50);
			pc.addMaxMp(-30);
			pc.getAbility().addSp(-2);
			pc.addDamageReductionByArmor(-3);
			SC_SPMR.send(pc);
			if (pc.isInParty()) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFeatherBuffB(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addHitup(-2);
			pc.getAbility().addSp(-1);
			pc.addMaxHp(-50);
			pc.addMaxMp(-30);
			pc.addDamageReductionByArmor(-2);
			SC_SPMR.send(pc);
			if (pc.isInParty()) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFeatherBuffC(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMaxHp(-50);
			pc.addMaxMp(-30);
			pc.getAC().addAc(2);
			SC_OwnCharAttrDef.send(pc);
			if (pc.isInParty()) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFeatherBuffD(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(1);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}
	
	private static StopResult offAntaMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offFafuMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offLindMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAbility().addSp(-1);
	        pc.getResistance().addSpecialResistance(eKind.FEAR, -5);
	        SC_SPMR.send(pc);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offValaMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addDmgup(-2);
	        pc.getResistance().addSpecialResistance(eKind.ABILITY, -5);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBirthMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
	        pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offShapeMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAbility().addSp(-1);
	        pc.getResistance().addSpecialResistance(eKind.SPIRIT, -5);
	        pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
	        pc.getResistance().addSpecialResistance(eKind.FEAR, -5);
	        SC_SPMR.send(pc);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offLifeMaan(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getResistance().addSpecialResistance(eKind.ALL, -5);
	        pc.getResistance().addSpecialPierce(eKind.ALL, -3);
	        pc.addDmgup(-2);
	        pc.getAbility().addSp(-1);
	        SC_SPMR.send(pc);
	        SC_OwnCharAttrDef.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offAntaBuff(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAC().addAc(2);
	        pc.getResistance().addWater(-50);
	        SC_OwnCharStatus.send(pc);
	        SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 82, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offFafuBuff(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addHpr(-3);
	        pc.addMpr(-1);
	        pc.getResistance().addWind(-50);
	        SC_OwnCharStatus.send(pc);
	        SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 85, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offRindBuff(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addHitup(-3);
	        pc.addBowHitup(-3);
	        pc.getResistance().addFire(-50);
	        SC_OwnCharStatus.send(pc);
	        SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 88, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offDragonPearl(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_Liquor.send(pc, pc.getId(), 0);
	        SC_PacketBox.send(pc, SC_PacketBox.DRAGON_PEARL, 0, 0);
	        SC_ServerMessage.send(pc, 185);
	        pc.setPearl(0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBounceAttack(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        ((L1PcInstance) cha).addHitup(-6);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCounterMirror(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance pc) {
	    	pc.addWeightReduction(-300);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offReduceOrDecreaseWeight(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance pc) {
	    	pc.addWeightReduction(-180);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offWindShackleIcon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillIconWindShackle.send(pc, pc.getId(), 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offStormEye(L1Character cha, int skillId) {
	    cha.addBowHitup(-2);
	    cha.addBowDmgup(-3);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillIconAura.send(pc, 155, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offStormShot(L1Character cha, int skillId) {
	    cha.addBowDmgup(-5);
	    cha.addBowHitup(3);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillIconAura.send(pc, 165, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBerserkers(L1Character cha, int skillId) {
	    cha.getAC().addAc(-10);
	    cha.addDmgup(-2);
	    cha.addHitup(-8);
	    return StopResult.HANDLED;
	}
	
	private static StopResult offMajesty(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.MAJESTY, 9518, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offImmuneToHarm(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.IMMUNE_TO_HARM, 1562, 315, true);
	        pc.setLastImmuneLevel(0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offFocusSpirits(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.add_magic_critical_rate(-5);
	        sendEndNoti(pc, L1SkillId.FOCUS_SPRITS, 4832, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeRich(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAbility().addSp(-2);
	        SC_SPMR.send(pc);
	        sendEndNoti(pc, L1SkillId.CUBE_RICH, 5309, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeGolem(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAC().addAc(8);
	        SC_OwnCharAttrDef.send(pc);
	        sendEndNoti(pc, L1SkillId.CUBE_GOLEM, 5314, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeOgre(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addDmgup(-4);
	        pc.addHitup(-4);
	        sendEndNoti(pc, L1SkillId.CUBE_OGRE, 5312, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeAvatar(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.CUBE_AVATAR, 5322, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offImpact(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getResistance().addSpecialPierce(eKind.ALL, -pc.getImpactUp());
	        pc.setImpactUp(0);
	        SC_InventoryIcon.icoEnd(pc, L1SkillId.IMPACT);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offLucifer(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.LUCIFER, 4503, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBlowAttack(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;	                
	        sendEndNoti(pc, L1SkillId.BLOW_ATTACK, 8843, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offArmorBrake(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.ARMOR_BRAKE, 4473, false);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offGraceAvatar(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        int resistance = -5 + (pc.getGraceLv() * -1);
	        pc.getResistance().addSpecialResistance(eKind.ALL, resistance);

	        sendEndNoti(pc, L1SkillId.GRACE_AVATAR, 7427, 4741, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offDressEvasion(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addEffectedER(-18);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        SC_OwnCharStatus.send(pc);
	        sendEndNotiMsgOnly(pc, L1SkillId.DRESS_EVASION, 2203, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offAquaProtecter(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addEffectedER(-5);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        sendEndNotiMsgOnly(pc, L1SkillId.AQUA_PROTECTER, 2201, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offSolidCarriage(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNotiMsgOnly(pc, L1SkillId.SOLID_CARRIAGE, 2203, true);

	        pc.addEffectedER(-15);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        SC_OwnCharStatus.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offGuardBreak(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.getAC().addAc(-10);
	        SC_OwnCharAttrDef.send(pc);

	        sendEndNotiMsgOnly(pc, L1SkillId.GUARD_BREAK, 5110, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offStrikerGale(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNotiMsgOnly(pc, L1SkillId.STRIKER_GALE, 2200, true);

	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        SC_OwnCharStatus.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offClearMind(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.getAbility().addAddedStr((byte) -1);
	        pc.getAbility().addAddedDex((byte) -1);
	        pc.getAbility().addAddedInt((byte) -1);

	        sendEndNoti(pc, L1SkillId.CLEAR_MIND, 5279, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBlessedArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAC().addAc(3);
	        SC_OwnCharStatus.send(pc);
	        sendEndNotiMsgOnly(pc, L1SkillId.BLESSED_ARMOR, 2240, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCyclone(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.CYCLONE, 5446, 5450, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offShiningArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.addEffectedER(-10);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendEndNoti(pc, L1SkillId.SHINING_ARMOR, 9483, true);
	    }
	    return StopResult.HANDLED;
	}
	private static StopResult offShadowFang(
	        L1Character cha,
	        int skillId) {

	    if (!(cha instanceof L1PcInstance)) {
	        return StopResult.HANDLED;
	    }

	    final L1PcInstance pc =
	            (L1PcInstance) cha;

	    pc.addDmgup(-5);

	    sendScalesEndNoti(
	            pc,
	            L1SkillId.SHADOW_FANG,
	            1128,   // off icon
	            2234,   // end str
	            true);

	    return StopResult.HANDLED;
	}


	private static StopResult offBlessWeapon(
	        L1Character cha,
	        int skillId) {

	    if (!(cha instanceof L1PcInstance)) {
	        return StopResult.HANDLED;
	    }

	    final L1PcInstance pc =
	            (L1PcInstance) cha;

	    pc.addDmgup(-2);
	    pc.addHitup(-2);

	    sendScalesEndNoti(
	            pc,
	            L1SkillId.BLESS_WEAPON,
	            728,    // off icon
	            2167,   // end str
	            true);

	    return StopResult.HANDLED;
	}


	private static StopResult offEnchantWeapon(
	        L1Character cha,
	        int skillId) {

	    if (!(cha instanceof L1PcInstance)) {
	        return StopResult.HANDLED;
	    }

	    final L1PcInstance pc =
	            (L1PcInstance) cha;

	    pc.addDmgup(-2);

	    sendScalesEndNoti(
	            pc,
	            L1SkillId.ENCHANT_WEAPON,
	            5265,   // off icon
	            2239,   // end str
	            true);

	    return StopResult.HANDLED;
	}


	private static StopResult offHolyWeapon(
	        L1Character cha,
	        int skillId) {

	    if (!(cha instanceof L1PcInstance)) {
	        return StopResult.HANDLED;
	    }

	    final L1PcInstance pc =
	            (L1PcInstance) cha;

	    pc.addDmgup(-1);
	    pc.addHitup(-1);

	    sendScalesEndNoti(
	            pc,
	            L1SkillId.HOLY_WEAPON,
	            777,    // off icon
	            718,    // end str
	            true);

	    return StopResult.HANDLED;
	}

	private static StopResult offMoebius(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNoti(pc, L1SkillId.MOEBIUS, 9443, 5551, true);
	        SC_ServerMessage.send(pc, 5551);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesEarthDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.getAC().addAc(3);
	        pc.getResistance().addMr(-5);
	        SC_OwnCharAttrDef.send(pc);
	        SC_SPMR.send(pc);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_EARTH_DRAGON, 3182, 2420, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesWaterDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.addEffectedER(-10);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendScalesEndNoti(pc, L1SkillId.SCALES_WATER_DRAGON, 3184, 2424, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesRindviorDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.addDg(-10);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_RINDVIOR_DRAGON, 8887, 5271, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesFireDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.getResistance().addSpecialResistance(eKind.ABILITY, -5);
	        pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -5);
	        pc.getResistance().addSpecialResistance(eKind.FEAR, -5);
	        pc.addHitup(-5);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_FIRE_DRAGON, 3078, 2427, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionOgre(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addDmgup(-4);
	        pc.addHitup(-4);
	        sendEndNoti(pc, L1SkillId.IllUSION_OGRE, 3117, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionLich(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAbility().addSp(-4);
	        SC_SPMR.send(pc);
	        sendEndNoti(pc, L1SkillId.IllUSION_LICH, 3115, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionDiamondGolem(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.getAC().addAc(10);
	        SC_OwnCharAttrDef.send(pc);
	        sendEndNoti(pc, L1SkillId.IllUSION_DIAMONDGOLEM, 3113, true);
	    }
	    return StopResult.HANDLED;
	}



	private static StopResult offFreezeengArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.addEffectedER(-5);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendEndNoti(pc, L1SkillId.FREEZEENG_ARMOR, 9490, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionAvatar(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addDmgup(-10);
	        sendEndNoti(pc, L1SkillId.IllUSION_AVATAR, 3111, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offEnchantAcurucy(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.addHitup(-5);
	        sendEndNoti(pc, L1SkillId.ENCHANT_ACURUCY, 9487, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offPride(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNotiMsgOnly(pc, L1SkillId.PRIDE, 5265, true);

	        pc.addMaxHp(-pc.getMagicBuffHp());
	        pc.setMagicBuffHp(0);
	        if (pc.isInParty()) {
	            pc.getParty().refreshPartyMemberStatus(pc);
	        }
	        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offGigantic(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNotiMsgOnly(pc, L1SkillId.GIGANTIC, 3919, true);

	        pc.addMaxHp(-pc.getMagicBuffHp());
	        pc.setMagicBuffHp(0);
	        if (pc.isInParty()) {
	            pc.getParty().refreshPartyMemberStatus(pc);
	        }
	        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offEaggleEye(L1Character cha, int skillId) {
	    cha.add_missile_critical_rate(-2);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNoti(pc, L1SkillId.EAGGLE_EYE, 8490, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offAbsoluteBarrier(L1Character cha, int skillId) {
//	    if (cha instanceof L1PcInstance) {
//	        L1PcInstance pc = (L1PcInstance) cha;
//	        sendRestatNotiAbsoluteBarrier(pc);
//	    }
	    return StopResult.HANDLED;
	}
	

	private static StopResult offNoop(L1Character cha, int skillId) {
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyBurningShot(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.getResistance().addSpecialResistance(eKind.ALL, -3);
		return StopResult.HANDLED;
	}

	private static StopResult offMagicShield2(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		final String msg = "\\fA매직실드: 디버프마법면역이 해제되었습니다.";
		SC_InventoryIcon.icoEnd(pc, L1SkillId.매직실드2);
		SC_SystemMessage.send(pc, msg);
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyGigantic(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// 원본 '기간틱' 케이스는 프라이드 아이콘을 내렸다.
		SC_InventoryIcon.icoEnd(pc, L1SkillId.프라이드);
		pc.addMaxHp(-pc.getMagicBuffHp());
		pc.setMagicBuffHp(0);

		if (pc.isInParty()) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		return StopResult.HANDLED;
	}

	private static StopResult offEmpireOverloadDg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		((L1PcInstance) cha).addDg(10);
		return StopResult.HANDLED;
	}

	private static StopResult offImmuneBladeCustom(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		SC_InventoryIcon.icoEnd(pc, L1SkillId.이뮨투함블레이드);
		SC_SystemMessage.send(pc, "이뮨 투 함 블레이드가 사라졌습니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offCooldownNotice(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		String msg;
		if (skillId == L1SkillId.비전텔레포트) {
			msg = "\\fA비전 텔레포트: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.이터니티대기) {
			msg = "\\fA이터니티: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.데몰리션대기) {
			msg = "\\fA데몰리션: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.엘븐스트라이크대기) {
			msg = pc.getType() == 1
					? "\\fA블레이드 스턴: 쿨타임이 종료되었습니다."
					: "\\fA엘븐스트라이크: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.엘리멘탈대기) {
			msg = "\\fA엘리멘탈 가디언: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.어드밴스대기) {
			msg = "\\fA콜 클랜 어드밴스: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.쉐도우대기) {
			msg = "\\fA쉐도우 스탭: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.어벤저대기) {
			msg = "\\fA어벤저: 쿨타임이 종료되었습니다.";
		} else if (skillId == L1SkillId.버닝샷대기) {
			msg = "\\fA버닝샷 : 쿨타임이 종료되었습니다.";
		} else {
			return StopResult.NOT_HANDLED;
		}

		SC_SystemMessage.send(pc, msg);
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg);
		return StopResult.HANDLED;
	}

	private static StopResult offNoDis(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.setDisNum(0);
		if (pc.isGm()) {
			SC_SystemMessage.send(pc, "디스 중첩 쿨 종료");
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBattleScroll(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.getAC().addAc(5);
		pc.addHitRate(-5);
		pc.addBowHitup(-5);
		pc.addMagicHit(-2);
		pc.getResistance().addcalcPcDefense(-5);
		pc.getResistance().addPVPweaponTotalDamage(-5);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offRudolphBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.getAbility().addAddedWis((byte) -1);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPrime(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addBowHitRate(-3);
		pc.addBowDmgRate(-3);
		pc.addDmgRate(-3);
		pc.addHitRate(-3);
		pc.getAbility().addSp(-2);
		pc.addMagicHit(-2);

		SC_InventoryIcon.icoEnd(pc, L1SkillId.프라임);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPrime1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_InventoryIcon.icoNew(pc, L1SkillId.프라임, 3155, 600, true);
		pc.addBowHitRate(-9);
		pc.addBowDmgRate(-9);
		pc.addDmgRate(-9);
		pc.addHitRate(-9);
		pc.getAbility().addSp(-6);
		pc.addMagicHit(-6);
		pc.addMaxHp(-500);

		SC_SPMR.send(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyIllusionLich(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.getAC().addAc(10);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyIllusionGolem(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.getAbility().addSp(-4);
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offSimpleInventoryIcon(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		SC_InventoryIcon.icoEnd(pc, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacySolidCarriage(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.addEffectedER(-15);
		SC_InventoryIcon.icoEnd(pc, L1SkillId.솔리드캐리지);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyEvasion(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.addEffectedER(-18);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_InventoryIcon.icoEnd(pc, L1SkillId.이베이젼);
		return StopResult.HANDLED;
	}

	private static StopResult offLiberation(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		SC_InventoryIcon.icoEnd(pc, L1SkillId.리버레이션);
		SC_SystemMessage.send(pc, "리버레이션의 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offJudgment(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (skillId == L1SkillId.잠재력저지먼트1) {
			pc.getResistance().addSpecialResistance(eKind.ABILITY, 15);
			SC_OwnCharAttrDef.send(pc);
		} else if (skillId == L1SkillId.잠재력저지먼트2) {
			pc.getResistance().addSpecialResistance(eKind.SPIRIT, 15);
			SC_OwnCharAttrDef.send(pc);
		} else if (skillId == L1SkillId.잠재력저지먼트3) {
			pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 15);
			SC_OwnCharAttrDef.send(pc);
		} else if (skillId == L1SkillId.잠재력저지먼트4) {
			pc.getResistance().addSpecialResistance(eKind.FEAR, 15);
			SC_OwnCharAttrDef.send(pc);
		} else if (skillId == L1SkillId.잠재력저지먼트5) {
			pc.getResistance().addMr(15);
			SC_SPMR.send(pc);
		}

		SC_InventoryIcon.icoEnd(pc, L1SkillId.저지먼트);
		SC_SystemMessage.send(pc, "저지먼트의 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offHalpas(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		SC_SystemMessage.send(((L1PcInstance) cha), "할파스의 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offHalpasGod(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (pc.getInventory().checkEquipped(123111)
				|| pc.getInventory().checkEquipped(123112)
				|| pc.getInventory().checkEquipped(123113)) {

			SC_InventoryIcon.icoEnd(pc, L1SkillId.할파스의신의);
			SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.할파스의신의1, 3943, true);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offHalpasGodPvp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3상승된 PVP대미지리덕션이 원래대로 돌아왔습니다");
		return StopResult.HANDLED;
	}

	private static StopResult offPotentialSoul(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		((L1PcInstance) cha).removeSkillEffect(L1SkillId.잠재력소울스킬);
		return StopResult.HANDLED;
	}

	private static StopResult offPotentialSoulSkill(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		SC_InventoryIcon.icoEnd((L1PcInstance) cha, L1SkillId.소프);
		return StopResult.HANDLED;
	}

	private static StopResult offGuardFearEffect(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDg(20);
		sendLegacyEndNoti(pc, L1SkillId.가드피어효과, 5106, true);
		SC_SystemMessage.send(pc, "디스트로이:피어 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offGuardFearHorrorEffect(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addDg(20);
		pc.getAbility().addAddedStr((byte) 2);
		pc.getAbility().addAddedInt((byte) 2);
		pc.resetBaseMr();

		sendLegacyEndNoti(pc, L1SkillId.가드피어호러효과, 5107, true);
		SC_OwnCharStatus.send(pc);
		SC_SystemMessage.send(pc, "디스트로이:호러오브데스 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offCombatCommand(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		SC_TrueTargetNew.broadcastFast(cha, cha.getId(), false);
		return StopResult.HANDLED;
	}

	private static StopResult offDelay(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		if (!pc.hasSkillEffect(L1SkillId.DELAY)) {
			SC_PacketBox.send(pc, SC_PacketBox.MINIGAME_END);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyPride(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_InventoryIcon.icoEnd(pc, L1SkillId.프라이드);
		pc.addMaxHp(-pc.getMagicBuffHp());
		pc.setMagicBuffHp(0);

		if (pc.isInParty()) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		return StopResult.HANDLED;
	}

	private static StopResult offPotential(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addMaxHp(-pc.getMagicBuffHp());
		pc.setMagicBuffHp(0);

		pc.addMaxMp(-pc.getMagicBuffMp());
		pc.setMagicBuffMp(0);

		pc.getResistance().addMr(-pc.getMagicBuffMR());
		pc.setMagicBuffMR(0);

		pc.getAbility().addSp(-pc.getMagicBuffSP());
		pc.setMagicBuffSP(0);

		pc.addDg(-pc.getMagicBuffDG());
		pc.setMagicBuffDG(0);

		pc.addEffectedER(-pc.getMagicBuffER());
		pc.setMagicBuffER(0);

		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_SystemMessage.send(pc, "포텐셜의 효과가 사라집니다.");
		return StopResult.HANDLED;
	}
	
	private static StopResult offSpecialBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		
		switch (skillId) {
		case L1SkillId.BUFF_특별한성장축복:
			if (cha instanceof L1PcInstance pc) {
				SC_InventoryIcon.icoEnd(pc, L1SkillId.BUFF_특별한성장축복);
			}
			return StopResult.HANDLED;

		case L1SkillId.BUFF_특별한방어축복:
              if (cha instanceof L1PcInstance pc) {			
				pc.addDamageReductionByArmor(-1);
				pc.getResistance().addMr(-5);
				pc.addMaxHp(-50);
				SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
				SC_OwnCharAttrDef.send(pc);
				SC_SPMR.send(pc);				
				SC_InventoryIcon.icoEnd(pc, L1SkillId.BUFF_특별한방어축복);
			}
			return StopResult.HANDLED;
		case L1SkillId.BUFF_특별한공격축복: {
			if (cha instanceof L1PcInstance pc) {
				pc.addDmgup(-2);
				pc.addBowDmgup(-2);
				pc.getAbility().addSp(-2);
				pc.addMaxMp(-30);
				SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
				SC_OwnCharAttrDef.send(pc);
				SC_SPMR.send(pc);
				SC_InventoryIcon.icoEnd(pc, L1SkillId.BUFF_특별한공격축복);
			}
			return StopResult.HANDLED;
		}
		default:
			return StopResult.NOT_HANDLED;
		}
	}

	private static StopResult offLegacyAdvance(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addMaxHp(-pc.getAdvenHp());
		pc.addMaxMp(-pc.getAdvenMp());
		pc.setAdvenHp(0);
		pc.setAdvenMp(0);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		if (pc.isInParty()) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		return StopResult.HANDLED;
	}

	private static StopResult offEtherDis(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;

			if (pc.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) {
				pc.removeSkillEffect(L1SkillId.COUNTER_MAGIC);
				long sound = SC_SkillSound.pack(pc.getId(), 10702);
				SC_SkillSound.broadcastFastPacked(pc, sound);
				SC_SkillSound.sendPacked(pc, sound);
			} else {
				long sound = SC_SkillSound.pack(pc.getId(), 17872);
				SC_SkillSound.sendPacked(pc, sound);
				SC_SkillSound.broadcastFastPacked(pc, sound);
			}
			return StopResult.HANDLED;
		}

		if (cha instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) cha;
			SC_SkillSound.broadcastFast(npc, npc.getId(), 17872);

			L1Magic magic = new L1Magic(npc, cha);
			int damage = java.util.concurrent.ThreadLocalRandom.current().nextInt(
					Config.이터이차최소,
					Config.이터이차최대);
			magic.commit(damage, 0);
			return StopResult.HANDLED;
		}

		return StopResult.NOT_HANDLED;
	}

	private static StopResult offMetisBlessScroll(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.addHitup(-2);
		pc.addDmgup(-2);
		pc.addBowHitup(-2);
		pc.addBowDmgup(-2);
		pc.getAbility().addSp(-2);
		pc.addHpr(-3);
		pc.addMpr(-3);
		SC_SPMR.send(pc);

		pc.addMaxHp(-50);
		pc.addMaxMp(-30);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		return StopResult.HANDLED;
	}

	private static StopResult offBerserkCustom(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.getResistance().addSpecialResistance(eKind.ALL, -20);
		SC_SystemMessage.send(pc, "버서크 효과가 사라집니다.");
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offStartBlessing(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		int value;
		if (skillId == L1SkillId.SIDE_OF_ME_BLESSING) {
			value = -5;
		} else if (skillId == L1SkillId.RE_START_BLESSING) {
			value = -3;
		} else if (skillId == L1SkillId.NEW_START_BLESSING) {
			value = -2;
		} else if (skillId == L1SkillId.LIFE_BLESSING) {
			value = -1;
		} else {
			return StopResult.NOT_HANDLED;
		}

		pc.addDamageReductionByArmor(value);
		pc.addDmgup(value);
		pc.addBowDmgup(value);
		return StopResult.HANDLED;
	}

	private static StopResult offCounterBarrierLegacy(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		if (pc.getCurrentSpriteId() == 21169) {
			int keepTime = pc.getSkillEffectTimeSec(L1SkillId.POLY_RING_MASTER);
			L1PolyMorph.doPoly(
					pc,
					21140,
					keepTime,
					L1PolyMorph.MORPH_BY_ITEMMAGIC,
					true);
		}
		return StopResult.HANDLED;
	}

	private static void sendLegacyEndNoti(
			L1PcInstance pc,
			int spellId,
			int endStrId,
			boolean isGood) {

		l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
		{ notiWire_bit |= 2; notiWire_spellId = spellId; }
		{ notiWire_bit |= 4; notiWire_duration = 0; }
		if (endStrId > 0) {
			{ notiWire_bit |= 512; notiWire_endStrId = endStrId; }
		}
		{ notiWire_bit |= 1024; notiWire_isGood = isGood; }
		SC_SPELL_BUFF_NOTI.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, 0, 0, 0, 0, notiWire_endStrId,
		        notiWire_isGood, 0, 0, 0, notiWire_bit);
	}


	private static void sendEndNoti(L1PcInstance pc, int spellId, int offIconId, int endStrId, boolean isGood) {
		l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		int notiWire_offIconId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


	    { notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
	    { notiWire_bit |= 2; notiWire_spellId = (notiSpellId(spellId)); }
	    { notiWire_bit |= 4; notiWire_duration = 0; }
	    if (offIconId > 0) { notiWire_bit |= 32; notiWire_offIconId = offIconId; }
	    if (endStrId > 0) { notiWire_bit |= 512; notiWire_endStrId = endStrId; }
	    { notiWire_bit |= 1024; notiWire_isGood = isGood; }
	    SC_SPELL_BUFF_NOTI.sendFields(
	            pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0,
	            notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
	}
	
	private static void sendScalesEndNoti(L1PcInstance pc, int spellId, int offIconId, int endStrId, boolean isGood) {
		l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		int notiWire_offIconId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


	    { notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
	    { notiWire_bit |= 2; notiWire_spellId = spellId; }
	    { notiWire_bit |= 4; notiWire_duration = 0; }
	    if (offIconId > 0) { notiWire_bit |= 32; notiWire_offIconId = offIconId; }
	    if (endStrId > 0) { notiWire_bit |= 512; notiWire_endStrId = endStrId; }
	    { notiWire_bit |= 1024; notiWire_isGood = isGood; }
	    SC_SPELL_BUFF_NOTI.sendFields(
	            pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0,
	            notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
	}


	private static void sendEndNoti(L1PcInstance pc, int spellId, int offIconId, boolean isGood) {
	    sendEndNoti(pc, spellId, offIconId, 0, isGood);
	}

	private static void sendEndNotiMsgOnly(L1PcInstance pc, int spellId, int endStrId, boolean isGood) {
	    sendEndNoti(pc, spellId, 0, endStrId, isGood);
	}
	
	private static StopResult offAbsoluteBlade(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			sendEndNoti(pc, L1SkillId.ABSOLUTE_BLADE, 7432, 4742, true);
		}
		return StopResult.HANDLED;
	}
	
	private static StopResult offReductionArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        sendEndNoti(pc, L1SkillId.REDUCTION_ARMOR, 1889, 2197, true);
	    }
	    return StopResult.HANDLED;
	}

	
	private static StopResult offPowerRip(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_Paralysis.send(pc, SC_Paralysis.TYPE_RIP, false);
	        return StopResult.HANDLED;
	    }

	    if (cha instanceof L1MonsterInstance
	            || cha instanceof L1SummonInstance
	            || cha instanceof L1PetInstance
	            || cha instanceof L1CompanionInstance) {
	        L1NpcInstance npc = (L1NpcInstance) cha;
	        npc.set발묶임상태(false);
	        return StopResult.HANDLED;
	    }

	    return StopResult.NOT_HANDLED;
	}

	private static StopResult offDesperado(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_Paralysis.send(pc, SC_Paralysis.TYPE_PERADO, false);
	    } else if (cha instanceof L1MonsterInstance
	            || cha instanceof L1SummonInstance
	            || cha instanceof L1PetInstance
	            || cha instanceof L1CompanionInstance) {
	        L1NpcInstance npc = (L1NpcInstance) cha;
	        npc.set발묶임상태(false);
	    }

	    cha.Desperadolevel = 0;
	    return StopResult.HANDLED;
	}

	private static StopResult offAdvanceSpirit(L1Character cha, int skillId) {
	    if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
	    L1PcInstance pc = (L1PcInstance) cha;

	    pc.addMaxHp(-pc.getAdvenHp());
	    pc.addMaxMp(-pc.getAdvenMp());
	    pc.setAdvenHp(0);
	    pc.setAdvenMp(0);

	    SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
	    if (pc.isInParty()) {
	        pc.getParty().refreshPartyMemberStatus(pc);
	    }
	    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

	    l1j.server.server.packets.Protocol.Packet.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
	    int notiWire_spellId = 0;
	    int notiWire_duration = 0;
	    int notiWire_offIconId = 0;
	    boolean notiWire_isGood = false;
	    int notiWire_bit = 0;


	    { notiWire_bit |= 1; notiWire_notiType = eNotiType.END; }
	    { notiWire_bit |= 2; notiWire_spellId = (notiSpellId(L1SkillId.ADVANCE_SPIRIT)); }
	    { notiWire_bit |= 4; notiWire_duration = 0; }
	    { notiWire_bit |= 32; notiWire_offIconId = 2180; }
	    { notiWire_bit |= 1024; notiWire_isGood = true; }
	    SC_SPELL_BUFF_NOTI.sendFields(
	            pc, notiWire_notiType, notiWire_spellId, notiWire_duration, null, 0, notiWire_offIconId, 0, 0, 0, 0,
	            notiWire_isGood, 0, 0, 0, notiWire_bit);

	    return StopResult.HANDLED;
	}

	private static StopResult offHasteFamily(L1Character cha, int skillId) {
	    // HASTE / GREATER_HASTE 공통
	    cha.setMoveSpeed(0);

	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillHaste.send(pc, pc.getId(), 0, 0);
	        SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
	        return StopResult.HANDLED;
	    }
	    return StopResult.HANDLED;
	}
	
	private static int notiSpellId(int spellId) {
	    if (spellId <= 0) return spellId;
	    return spellId - 1;
	}
}