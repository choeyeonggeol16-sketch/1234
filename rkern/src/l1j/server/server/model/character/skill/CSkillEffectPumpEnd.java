package l1j.server.server.model.character.skill;

import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;


import l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.effect.PcClanBuffEffects;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
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
public final class CSkillEffectPumpEnd {

	private CSkillEffectPumpEnd() {}

	// ===== 결과 =====
	private static final java.util.logging.Logger LOG =
			java.util.logging.Logger.getLogger(CSkillEffectPumpEnd.class.getName());

	public enum StopResult {
		NOT_HANDLED, 
		HANDLED   

	}

	@FunctionalInterface
	public interface StopHandler extends CSkillEffect {
		StopResult handle(L1Character cha, int skillId);

		@Override default StopResult pumpEnd(L1Character target, int skillId) {
			return handle(target, skillId);
		}
	}

	static void registerEffects() {
		register(L1SkillId.혈맹버프AC,   CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프마방, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프PVP추가, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프PVP감소, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프HP증가, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프올내성, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프올적중, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프올스텟, CSkillEffectPumpEnd::offClanScroll);
		register(L1SkillId.혈맹버프회복,     CSkillEffectPumpEnd::offNoop);

		register(L1SkillId.천하장사버프,     CSkillEffectPumpEnd::offPlayerModifier);

		register(L1SkillId.N_BUFF_PVP_DMG,   CSkillEffectPumpEnd::offNBuffPvpDmg);
		register(L1SkillId.N_BUFF_HPMP,      CSkillEffectPumpEnd::offNBuffHpMp);
		register(L1SkillId.N_BUFF_DMG,       CSkillEffectPumpEnd::offNBuffDmg);
		register(L1SkillId.N_BUFF_REDUCT,    CSkillEffectPumpEnd::offNBuffReduct);
		register(L1SkillId.N_BUFF_SP,        CSkillEffectPumpEnd::offNBuffSp);
		register(L1SkillId.N_BUFF_STUN,      CSkillEffectPumpEnd::offNBuffStun);
		register(L1SkillId.N_BUFF_HOLD,      CSkillEffectPumpEnd::offNBuffHold);

		register(L1SkillId.N_BUFF_WATER_DMG, CSkillEffectPumpEnd::offInventoryElementalTimer);
		register(L1SkillId.N_BUFF_WIND_DMG,  CSkillEffectPumpEnd::offInventoryElementalTimer);
		register(L1SkillId.N_BUFF_EARTH_DMG, CSkillEffectPumpEnd::offInventoryElementalTimer);
		register(L1SkillId.N_BUFF_FIRE_DMG,  CSkillEffectPumpEnd::offInventoryElementalTimer);

		register(L1SkillId.N_BUFF_STR,       CSkillEffectPumpEnd::offNBuffStr);
		register(L1SkillId.N_BUFF_DEX,       CSkillEffectPumpEnd::offNBuffDex);
		register(L1SkillId.N_BUFF_INT,       CSkillEffectPumpEnd::offNBuffInt);
		register(L1SkillId.N_BUFF_WIS,       CSkillEffectPumpEnd::offNBuffWis);

		register(L1SkillId.N_BUFF_WATER,     CSkillEffectPumpEnd::offNBuffWater);
		register(L1SkillId.N_BUFF_WIND,      CSkillEffectPumpEnd::offNBuffWind);
		register(L1SkillId.N_BUFF_EARTH,     CSkillEffectPumpEnd::offNBuffEarth);
		register(L1SkillId.N_BUFF_FIRE,      CSkillEffectPumpEnd::offNBuffFire);
		register(L1SkillId.N_BUFF_ALL_RESIST,CSkillEffectPumpEnd::offNBuffAllResist);

		register(L1SkillId.FEAR,             CSkillEffectPumpEnd::offFear);

		register(L1SkillId.EXP_POTION,       CSkillEffectPumpEnd::offExpPotion);
		register(L1SkillId.TELEPORT_RULER,   CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.miso1, CSkillEffectPumpEnd::offMiso1);
		register(L1SkillId.miso2, CSkillEffectPumpEnd::offMiso2);
		register(L1SkillId.miso3, CSkillEffectPumpEnd::offMiso3);

		register(L1SkillId.DRAGON_SET, CSkillEffectPumpEnd::offDragonSet);

		register(L1SkillId.HUNTER_BLESS,  CSkillEffectPumpEnd::offMiso1);
		register(L1SkillId.HUNTER_BLESS1, CSkillEffectPumpEnd::offHunterBless1);

		register(L1SkillId.MEDITATION, CSkillEffectPumpEnd::offMiso1);

		register(L1SkillId.POLY_RING_MASTER, CSkillEffectPumpEnd::offPolyRingMaster);

		register(L1SkillId.THUNDER_GRAB, CSkillEffectPumpEnd::offThunderGrab);

		register(L1SkillId.SOUL_BARRIER, CSkillEffectPumpEnd::offSoulBarrier);

		register(L1SkillId.DOUBLE_BRAKE, CSkillEffectPumpEnd::offDoubleBrake);

		register(L1SkillId.EXP_BUFF,         CSkillEffectPumpEnd::offExpBuff);
		register(L1SkillId.TITANL_RISING,    CSkillEffectPumpEnd::offTitanlRising);
		register(L1SkillId.ABSOLUTE_BLADE,   CSkillEffectPumpEnd::offAbsoluteBlade);
		register(L1SkillId.DEATH_HEAL_Mob,   CSkillEffectPumpEnd::offMiso1);
		register(L1SkillId.DEATH_HEAL,       CSkillEffectPumpEnd::offMiso1);

		register(L1SkillId.CLAN_BUFF1,       CSkillEffectPumpEnd::offClanStance);
		register(L1SkillId.CLAN_BUFF2,       CSkillEffectPumpEnd::offClanStance);
		register(L1SkillId.CLAN_BUFF3,       CSkillEffectPumpEnd::offClanStance);
		register(L1SkillId.CLAN_BUFF4,       CSkillEffectPumpEnd::offClanStance);

		register(L1SkillId.정상의가호,       CSkillEffectPumpEnd::offPlayerModifier);

		register(L1SkillId.레벨업보너스,     CSkillEffectPumpEnd::offLevelUpBonus);
		register(L1SkillId.DRAGON_PUPLE,     CSkillEffectPumpEnd::offDragonPuple);
		register(L1SkillId.DRAGON_TOPAZ,     CSkillEffectPumpEnd::offDragonTopaz);

		register(L1SkillId.포커스3단,        CSkillEffectPumpEnd::offFocus3);

		register(L1SkillId.TOMAHAWK,   CSkillEffectPumpEnd::offMiso1);
		register(L1SkillId.완력빙수,   CSkillEffectPumpEnd::offStrIce);
		register(L1SkillId.민첩빙수,   CSkillEffectPumpEnd::offDexIce);
		register(L1SkillId.지식빙수,   CSkillEffectPumpEnd::offIntIce);

		register(L1SkillId.LIGHT,          CSkillEffectPumpEnd::offLight);
		register(L1SkillId.TRUE_TARGET,    CSkillEffectPumpEnd::offTrueTarget);
		register(L1SkillId.GLOWING_AURA, CSkillEffectPumpEnd::offGlowingWeapon);
		register(L1SkillId.God_buff,       CSkillEffectPumpEnd::offGodBuff);

		register(L1SkillId.BUFF_SAEL,      CSkillEffectPumpEnd::offNpcBloodBuff);
		register(L1SkillId.SHINING_AURA, CSkillEffectPumpEnd::offShiningShield);
		register(L1SkillId.BRAVE_AURA,   CSkillEffectPumpEnd::offBraveMental);
		register(L1SkillId.SHIELD,         CSkillEffectPumpEnd::offShield);
		register(L1SkillId.BLIND_HIDING,   CSkillEffectPumpEnd::offBlindHiding);
		register(L1SkillId.SHADOW_ARMOR,   CSkillEffectPumpEnd::offShadowArmor);
		register(L1SkillId.DRESS_DEXTERITY,CSkillEffectPumpEnd::offDressDexterity);
		register(L1SkillId.DRESS_MIGHTY,   CSkillEffectPumpEnd::offDressMighty);
		

		register(L1SkillId.PATIENCE, CSkillEffectPumpEnd::offPatience);
		
		register(L1SkillId.EARTH_GUARDIAN, CSkillEffectPumpEnd::offEarthGuardian);
		register(L1SkillId.RESIST_MAGIC,   CSkillEffectPumpEnd::offResistMagic);

		register(L1SkillId.RESIST_ELEMENTAL,      CSkillEffectPumpEnd::offResistElemental);
		register(L1SkillId.ELEMENTAL_PROTECTION, CSkillEffectPumpEnd::offElementalProtection);
		register(L1SkillId.ELEMENTAL_FALL_DOWN,  CSkillEffectPumpEnd::offElementalFallDown);
		register(L1SkillId.IRON_SKIN,            CSkillEffectPumpEnd::offIronSkin);
		register(L1SkillId.FIRE_SHIELD,          CSkillEffectPumpEnd::offFireShield);
		register(L1SkillId.PHYSICAL_ENCHANT_STR, CSkillEffectPumpEnd::offPhysicalEnchantStr);
		register(L1SkillId.PHYSICAL_ENCHANT_DEX, CSkillEffectPumpEnd::offPhysicalEnchantDex);
		register(L1SkillId.EARTH_WEAPON,         CSkillEffectPumpEnd::offEarthWeapon);

		register(L1SkillId.INFERNO,        CSkillEffectPumpEnd::offInferno);

		register(L1SkillId.FOCUS_WAVE,     CSkillEffectPumpEnd::offFocusWave);
		register(L1SkillId.HURRICANE,      CSkillEffectPumpEnd::offBraveSpeedReset);
		register(L1SkillId.SAND_STORM,     CSkillEffectPumpEnd::offBraveSpeedReset);
		register(L1SkillId.DANCING_BLADES, CSkillEffectPumpEnd::offBraveSpeedReset);

		register(L1SkillId.BUFF_CRAY,      CSkillEffectPumpEnd::offNpcBloodBuff);
		register(L1SkillId.BUFF_Vala,      CSkillEffectPumpEnd::offNpcBloodBuff);
		register(L1SkillId.BUFF_GUNTER,    CSkillEffectPumpEnd::offNpcBloodBuff);

		register(L1SkillId.UNCANNY_DODGE,  CSkillEffectPumpEnd::offUncannyDodge);
		register(L1SkillId.BURNING_WEAPON, CSkillEffectPumpEnd::offBurningWeapon);
		register(L1SkillId.MIRROR_IMAGE,   CSkillEffectPumpEnd::offUncannyDodge);
		register(L1SkillId.AQUA_SHOT,      CSkillEffectPumpEnd::offAquaShot);

		register(L1SkillId.INSIGHT,     CSkillEffectPumpEnd::offInsight);

		register(L1SkillId.Tam_Fruit1,  CSkillEffectPumpEnd::offTamFruitSync);
		register(L1SkillId.Tam_Fruit2,  CSkillEffectPumpEnd::offTamFruitSync);
		register(L1SkillId.Tam_Fruit3,  CSkillEffectPumpEnd::offTamFruitSync);
		register(L1SkillId.Tam_Fruit4,  CSkillEffectPumpEnd::offTamFruitSync);
		register(L1SkillId.Tam_Fruit5,  CSkillEffectPumpEnd::offTamFruitSync);

		register(L1SkillId.SHAPE_CHANGE, CSkillEffectPumpEnd::offShapeChange);

		register(L1SkillId.HOLY_WALK,            CSkillEffectPumpEnd::offBraveSpeedReset);
		register(L1SkillId.MOVING_ACCELERATION, CSkillEffectPumpEnd::offBraveSpeedReset);

		register(L1SkillId.BLOOD_LUST,           CSkillEffectPumpEnd::offBraveSpeedReset);

		register(L1SkillId.DARK_BLIND,           CSkillEffectPumpEnd::offSleepFamily);

		register(L1SkillId.CURSE_BLIND,          CSkillEffectPumpEnd::offCurseBlindFamily);
		register(L1SkillId.DARKNESS,             CSkillEffectPumpEnd::offCurseBlindFamily);
		register(L1SkillId.LINDBIOR_SPIRIT_EFFECT, CSkillEffectPumpEnd::offCurseBlindFamily);

		register(L1SkillId.CURSE_PARALYZE,       CSkillEffectPumpEnd::offCurseParalyzeFamily);
		register(L1SkillId.DESERT_SKILL1,        CSkillEffectPumpEnd::offCurseParalyzeFamily);

		register(L1SkillId.WEAKNESS,             CSkillEffectPumpEnd::offWeaknessFamily);
		register(L1SkillId.MOB_WEAKNESS_1,       CSkillEffectPumpEnd::offWeaknessFamily);

		register(L1SkillId.DISEASE,              CSkillEffectPumpEnd::offDiseaseFamily);
		register(L1SkillId.MOB_DISEASE_1,        CSkillEffectPumpEnd::offDiseaseFamily);
		register(L1SkillId.MOB_DISEASE_30,       CSkillEffectPumpEnd::offDiseaseFamily);

		register(L1SkillId.HORROR_OF_DEATH,      CSkillEffectPumpEnd::offHorrorOfDeath);
		register(L1SkillId.PANIC,                CSkillEffectPumpEnd::offPanic);

		register(L1SkillId.ICE_LANCE,            CSkillEffectPumpEnd::offFreezeFamily);

		register(L1SkillId.MOB_BERSERKERS,       CSkillEffectPumpEnd::offMobBerserkers);

		register(L1SkillId.EARTH_BIND,           CSkillEffectPumpEnd::offFreezeFamily);
		register(L1SkillId.MOB_BASILL,           CSkillEffectPumpEnd::offFreezeFamily);
		register(L1SkillId.MOB_COCA,             CSkillEffectPumpEnd::offFreezeFamily);

		register(L1SkillId.SHOCK_STUN,           CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.MOB_SHOCKSTUN_30,     CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_18,     CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_19,     CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.MOB_RANGESTUN_20,     CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.Mob_RANGESTUN_30,     CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_6,       CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_7,       CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.ANTA_MESSAGE_8,       CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.ANTA_SHOCKSTUN,       CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.OMAN_STUN,            CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.Maeno_STUN,           CSkillEffectPumpEnd::offStunFamily);

		register(L1SkillId.EMPIRE,               CSkillEffectPumpEnd::offStunFamily);
		register(L1SkillId.BONE_BREAK,           CSkillEffectPumpEnd::offStunFamily);

		register(L1SkillId.PHANTASM,             CSkillEffectPumpEnd::offSleepFamily);
		register(L1SkillId.FOG_OF_SLEEPING,      CSkillEffectPumpEnd::offSleepFamily);

		register(L1SkillId.SLOW,                  CSkillEffectPumpEnd::offSlowFamily);
		register(L1SkillId.MOB_SLOW_1,            CSkillEffectPumpEnd::offSlowFamily);
		register(L1SkillId.MOB_SLOW_18,           CSkillEffectPumpEnd::offSlowFamily);
		register(L1SkillId.STATUS_FREEZE,         CSkillEffectPumpEnd::offStatusFreeze);

		register(L1SkillId.STATUS_IGNITION,       CSkillEffectPumpEnd::offStatusIgnition);
		register(L1SkillId.STATUS_QUAKE,          CSkillEffectPumpEnd::offStatusQuake);
		register(L1SkillId.STATUS_SHOCK,          CSkillEffectPumpEnd::offStatusShock);

		register(L1SkillId.STATUS_BRAVE,          CSkillEffectPumpEnd::offBraveSpeedReset);
		register(L1SkillId.STATUS_FRUIT,          CSkillEffectPumpEnd::offBraveSpeedReset);
		register(L1SkillId.STATUS_ELFBRAVE,       CSkillEffectPumpEnd::offBraveSpeedReset);

		register(L1SkillId.STATUS_HASTE,          CSkillEffectPumpEnd::offHasteFamily);

		register(L1SkillId.STATUS_UNDERWATER_BREATH, CSkillEffectPumpEnd::offUnderwaterBreath);
		register(L1SkillId.STATUS_WISDOM_POTION,  CSkillEffectPumpEnd::offWisdomPotion);

		register(L1SkillId.STATUS_CHAT_PROHIBITED, CSkillEffectPumpEnd::offChatProhibited);

		register(L1SkillId.STATUS_CASHSCROLL,     CSkillEffectPumpEnd::offCashScroll);
		register(L1SkillId.STATUS_CASHSCROLL2,    CSkillEffectPumpEnd::offCashScroll);
		register(L1SkillId.STATUS_CASHSCROLL3,    CSkillEffectPumpEnd::offCashScroll);
		register(L1SkillId.STATUS_CASHSCROLL4,    CSkillEffectPumpEnd::offCashScroll);
		register(L1SkillId.STATUS_CASHSCROLL5,    CSkillEffectPumpEnd::offCashScroll);
		register(L1SkillId.STATUS_CASHSCROLL6,    CSkillEffectPumpEnd::offCashScroll);

		register(L1SkillId.STATUS_POISON,         CSkillEffectPumpEnd::offPoison);

		register(L1SkillId.성혈버프,               CSkillEffectPumpEnd::offSeonghyeolBuff);

		register(L1SkillId.COMA_A,                CSkillEffectPumpEnd::offComa);
		register(L1SkillId.COMA_B,                CSkillEffectPumpEnd::offComa);

		register(L1SkillId.FEATHER_BUFF_A,        CSkillEffectPumpEnd::offFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_B,        CSkillEffectPumpEnd::offFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_C,        CSkillEffectPumpEnd::offFeatherBuff);
		register(L1SkillId.FEATHER_BUFF_D,        CSkillEffectPumpEnd::offFeatherBuff);


		register(L1SkillId.ANTA_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.FAFU_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.LIND_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.VALA_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.BIRTH_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.SHAPE_MAAN, CSkillEffectPumpEnd::offDragonMaan);
		register(L1SkillId.LIFE_MAAN, CSkillEffectPumpEnd::offDragonMaan);

		register(L1SkillId.ANTA_BUFF, CSkillEffectPumpEnd::offDragonBlood);
		register(L1SkillId.FAFU_BUFF, CSkillEffectPumpEnd::offDragonBlood);
		register(L1SkillId.RIND_BUFF, CSkillEffectPumpEnd::offDragonBlood);

		register(L1SkillId.STATUS_DRAGON_PEARL, CSkillEffectPumpEnd::offDragonPearl);

		register(L1SkillId.BOUNCE_ATTACK, CSkillEffectPumpEnd::offBounceAttack);

		register(L1SkillId.COUNTER_MIRROR, CSkillEffectPumpEnd::offCounterMirror);

		register(L1SkillId.DECREASE_WEIGHT, CSkillEffectPumpEnd::offReduceOrDecreaseWeight);
		register(L1SkillId.REDUCE_WEIGHT,   CSkillEffectPumpEnd::offReduceOrDecreaseWeight);

		register(L1SkillId.RINDVIOR_WIND_SHACKLE,   CSkillEffectPumpEnd::offWindShackleIcon);
		register(L1SkillId.RINDVIOR_WIND_SHACKLE_1, CSkillEffectPumpEnd::offWindShackleIcon);
		register(L1SkillId.DRAKE_WIND_SHACKLE,      CSkillEffectPumpEnd::offWindShackleIcon);

		register(L1SkillId.STORM_EYE,  CSkillEffectPumpEnd::offStormEye);
		register(L1SkillId.STORM_SHOT, CSkillEffectPumpEnd::offStormShot);
		register(L1SkillId.BERSERKERS, CSkillEffectPumpEnd::offBerserkers);
		
		register(L1SkillId.MAJESTY, CSkillEffectPumpEnd::offMajesty);
		register(L1SkillId.IMMUNE_TO_HARM, CSkillEffectPumpEnd::offImmuneToHarm);

		register(L1SkillId.FOCUS_SPRITS, CSkillEffectPumpEnd::offFocusSpirits);

		register(L1SkillId.CUBE_RICH, CSkillEffectPumpEnd::offCubeRich);
		register(L1SkillId.CUBE_GOLEM, CSkillEffectPumpEnd::offCubeGolem);
		register(L1SkillId.CUBE_OGRE, CSkillEffectPumpEnd::offCubeOgre);
		register(L1SkillId.CUBE_AVATAR, CSkillEffectPumpEnd::offCubeAvatar);

		register(L1SkillId.IMPACT, CSkillEffectPumpEnd::offImpact);

		register(L1SkillId.LUCIFER, CSkillEffectPumpEnd::offLucifer);
		register(L1SkillId.BLOW_ATTACK, CSkillEffectPumpEnd::offBlowAttack);
		register(L1SkillId.ARMOR_BRAKE, CSkillEffectPumpEnd::offArmorBrake);

		register(L1SkillId.GRACE_AVATAR, CSkillEffectPumpEnd::offGraceAvatar);

		register(L1SkillId.DRESS_EVASION, CSkillEffectPumpEnd::offDressEvasion);
		register(L1SkillId.AQUA_PROTECTER, CSkillEffectPumpEnd::offAquaProtecter);
		register(L1SkillId.SOLID_CARRIAGE, CSkillEffectPumpEnd::offSolidCarriage);

		register(L1SkillId.GUARD_BREAK, CSkillEffectPumpEnd::offGuardBreak);
		register(L1SkillId.STRIKER_GALE, CSkillEffectPumpEnd::offStrikerGale);

		register(L1SkillId.CLEAR_MIND, CSkillEffectPumpEnd::offClearMind);

		register(L1SkillId.SHADOW_FANG, CSkillEffectPumpEnd::offWeaponBuff);
		register(L1SkillId.BLESS_WEAPON, CSkillEffectPumpEnd::offWeaponBuff);
		register(L1SkillId.ENCHANT_WEAPON, CSkillEffectPumpEnd::offWeaponBuff);
		register(L1SkillId.BLESSED_ARMOR, CSkillEffectPumpEnd::offBlessedArmor);

		register(L1SkillId.CYCLONE, CSkillEffectPumpEnd::offCyclone);

		register(L1SkillId.SHINING_ARMOR, CSkillEffectPumpEnd::offShiningArmor);
		register(L1SkillId.샤이닝아머, CSkillEffectPumpEnd::offPassiveShiningArmor);
		register(L1SkillId.USER_WANTED, CSkillEffectPumpEnd::offWanted);
		register(L1SkillId.USER_WANTED1, CSkillEffectPumpEnd::offWanted);
		register(L1SkillId.hp300, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.mp300, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.대미지감소5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.ac5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.mr15, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.모든속성저항20, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.힘3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.덱3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.콘3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.위즈3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.인트3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.pvp대미지7, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.근거리대미지5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.원거리대미지5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.근거리명중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.원거리명중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.sp5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.마법적중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.기술내성8, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.정령내성8, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.용언내성3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.공포내성3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.모든내성3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.정령적중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.모든적중3, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.pvp감소7, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.기술적중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.용언적중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.공포적중5, CSkillEffectPumpEnd::offPersistentPotential);
		register(L1SkillId.HOLY_WEAPON, CSkillEffectPumpEnd::offWeaponBuff);

		register(L1SkillId.MOEBIUS, CSkillEffectPumpEnd::offMoebius);

		register(L1SkillId.SCALES_EARTH_DRAGON, CSkillEffectPumpEnd::offScalesEarthDragon);
		register(L1SkillId.SCALES_WATER_DRAGON, CSkillEffectPumpEnd::offScalesWaterDragon);
		register(L1SkillId.SCALES_RINDVIOR_DRAGON, CSkillEffectPumpEnd::offScalesRindviorDragon);
		register(L1SkillId.SCALES_FIRE_DRAGON, CSkillEffectPumpEnd::offScalesFireDragon);

		register(L1SkillId.IllUSION_OGRE, CSkillEffectPumpEnd::offIllusionOgre);
		register(L1SkillId.IllUSION_LICH, CSkillEffectPumpEnd::offIllusionLich);
		register(L1SkillId.IllUSION_DIAMONDGOLEM, CSkillEffectPumpEnd::offIllusionDiamondGolem);
		register(L1SkillId.IllUSION_AVATAR, CSkillEffectPumpEnd::offIllusionAvatar);

		register(L1SkillId.REDUCTION_ARMOR, CSkillEffectPumpEnd::offReductionArmor);
		register(L1SkillId.FREEZEENG_ARMOR, CSkillEffectPumpEnd::offFreezeengArmor);

		register(L1SkillId.ENCHANT_ACURUCY, CSkillEffectPumpEnd::offEnchantAcurucy);

		register(L1SkillId.PRIDE,
				(target, id) -> offScaledHp(target, id, CSkillEffect.MaxHp.PRIDE));
		register(L1SkillId.GIGANTIC,
				(target, id) -> offScaledHp(target, id, CSkillEffect.MaxHp.GIGANTIC));

		register(L1SkillId.EAGGLE_EYE, CSkillEffectPumpEnd::offEaggleEye);

		register(L1SkillId.ABSOLUTE_BARRIER, CSkillEffectPumpEnd::offNoop);
		
		register(L1SkillId.POWERRIP,        CSkillEffectPumpEnd::offPowerRip);
		register(L1SkillId.DESPERADO,       CSkillEffectPumpEnd::offDesperado);
		register(L1SkillId.ADVANCE_SPIRIT,  CSkillEffectPumpEnd::offAdvanceSpirit);
		register(L1SkillId.HASTE,           CSkillEffectPumpEnd::offHasteFamily);
		register(L1SkillId.GREATER_HASTE,   CSkillEffectPumpEnd::offHasteFamily);
		
		register(L1SkillId.매직실드1, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.버닝샷, CSkillEffectPumpEnd::offBurningShot);
		register(L1SkillId.매직실드2, CSkillEffectPumpEnd::offMagicShield2);
		register(L1SkillId.기간틱, CSkillEffectPumpEnd::offLegacyScaledHp);
		register(L1SkillId.엠파이어오버로드DG, CSkillEffectPumpEnd::offStunControlOverloadDg);
		register(L1SkillId.이뮨투함블레이드, CSkillEffectPumpEnd::offImmuneBladeCustom);
		register(L1SkillId.비전텔레포트, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.이터니티대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.데몰리션대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.엘븐스트라이크대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.엘리멘탈대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.NO_DIS, CSkillEffectPumpEnd::offNoDis);
		register(L1SkillId.어드밴스대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.쉐도우대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.어벤저대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.버닝샷대기, CSkillEffectPumpEnd::offCooldownNotice);
		register(L1SkillId.DRAGON_HUNTER_BLESS, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.격전의주문서, CSkillEffectPumpEnd::offBattleScroll);
		register(L1SkillId.루돌프의버프, CSkillEffectPumpEnd::offRudolphBuff);
		register(L1SkillId.HUNTER_BLESS2, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.프라임, CSkillEffectPumpEnd::offPrime);
		register(L1SkillId.프라임1, CSkillEffectPumpEnd::offPrime1);
		register(L1SkillId.프라임85, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.프라임90, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.프라임95, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.일루션리치, CSkillEffectPumpEnd::offLegacyIllusionLich);
		register(L1SkillId.일루션골렘, CSkillEffectPumpEnd::offLegacyIllusionGolem);
		register(L1SkillId.에테리얼, CSkillEffectPumpEnd::offSimpleInventoryIcon);
		register(L1SkillId.솔리드캐리지, CSkillEffectPumpEnd::offLegacySolidCarriage);
		register(L1SkillId.버닝, CSkillEffectPumpEnd::offSimpleInventoryIcon);
		register(L1SkillId.이베이젼, CSkillEffectPumpEnd::offLegacyEvasion);
		register(L1SkillId.리버레이션, CSkillEffectPumpEnd::offLiberation);
		register(L1SkillId.잠재력저지먼트1, CSkillEffectPumpEnd::offJudgment);
		register(L1SkillId.잠재력저지먼트2, CSkillEffectPumpEnd::offJudgment);
		register(L1SkillId.잠재력저지먼트3, CSkillEffectPumpEnd::offJudgment);
		register(L1SkillId.잠재력저지먼트4, CSkillEffectPumpEnd::offJudgment);
		register(L1SkillId.잠재력저지먼트5, CSkillEffectPumpEnd::offJudgment);
		register(L1SkillId.할파스, CSkillEffectPumpEnd::offHalpas);
		register(L1SkillId.할파스의신의, CSkillEffectPumpEnd::offHalpasGod);
		register(L1SkillId.할파스의신의PVP, CSkillEffectPumpEnd::offHalpasGodPvp);
		register(L1SkillId.잠재력소울, CSkillEffectPumpEnd::offPotentialSoul);
		register(L1SkillId.잠재력소울스킬, CSkillEffectPumpEnd::offPotentialSoulSkill);
		register(L1SkillId.가드피어효과, CSkillEffectPumpEnd::offGuardFearEffect);
		register(L1SkillId.가드피어호러효과, CSkillEffectPumpEnd::offGuardFearHorrorEffect);
	//	register(800018, CSkillEffectPumpEnd::offTikalRespawn);
	//	register(800019, CSkillEffectPumpEnd::offTikalRespawn);
		register(L1SkillId.전투지휘, CSkillEffectPumpEnd::offCombatCommand);
		register(L1SkillId.DELAY, CSkillEffectPumpEnd::offDelay);
		register(L1SkillId.프라이드, CSkillEffectPumpEnd::offLegacyScaledHp);
		register(L1SkillId.포텐셜, CSkillEffectPumpEnd::offPotential);
		register(L1SkillId.어드밴스, CSkillEffectPumpEnd::offLegacyAdvance);
		register(L1SkillId.이터디스, CSkillEffectPumpEnd::offEtherDis);
		register(L1SkillId.STATUS_BLUE_POTION, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.STATUS_BLUE_POTION2, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.메티스축복주문서, CSkillEffectPumpEnd::offMetisBlessScroll);
		register(L1SkillId.버서크, CSkillEffectPumpEnd::offBerserkCustom);
		register(L1SkillId.버서크대기, CSkillEffectPumpEnd::offNoop);
		register(L1SkillId.SIDE_OF_ME_BLESSING, CSkillEffectPumpEnd::offPlayerModifier);
		register(L1SkillId.RE_START_BLESSING, CSkillEffectPumpEnd::offPlayerModifier);
		register(L1SkillId.NEW_START_BLESSING, CSkillEffectPumpEnd::offPlayerModifier);
		register(L1SkillId.LIFE_BLESSING, CSkillEffectPumpEnd::offPlayerModifier);
		register(L1SkillId.COUNTER_BARRIER, CSkillEffectPumpEnd::offCounterBarrierLegacy);

		register(L1SkillId.BUFF_특별한성장축복, CSkillEffectPumpEnd::offSpecialBuff);
		register(L1SkillId.BUFF_특별한방어축복, CSkillEffectPumpEnd::offSpecialBuff);
		register(L1SkillId.BUFF_특별한공격축복, CSkillEffectPumpEnd::offSpecialBuff);
	}

	/** Compatibility entry point. Definitions are frozen automatically during initialization. */
	public static void freeze() {
	}
	
	/** Compatibility entry point. Effect definitions are frozen after boot registration. */
	public static void register(int skillId, StopHandler handler) {
		CSkillInfo.registerPumpEnd(skillId, handler);
	}

	/** stop 처리 디스패치: 핸들러 있으면 처리, 없으면 NOT_HANDLED */
	public static StopResult dispatch(L1Character cha, int skillId) {
		final CSkillInfo info = CSkillInfo.get(skillId);
		final StopHandler h = info == null ? null : info.stopHandler();
		if (h == null) return StopResult.NOT_HANDLED;

		try {
			final StopResult r = h.pumpEnd(cha, skillId);
			return (r != null) ? r : StopResult.NOT_HANDLED;
		} catch (Throwable failure) {
			// A failure in an end handler must not disappear just because the
			// effect was registered as a legacy/stop-only skill.
			LOG.log(java.util.logging.Level.WARNING, "Skill stop failed: " + skillId, failure);
			return StopResult.NOT_HANDLED;
		}
	}

	/** 디버그/점검용: 해당 스킬 stop 핸들러 존재 여부 */
	public static boolean hasHandler(int skillId) {
		return getHandlerOrNull(skillId) != null;
	}

	/** 디버그용: 핸들러 직접 조회 */
	public static StopHandler getHandlerOrNull(int skillId) {
		final CSkillInfo effect = CSkillInfo.get(skillId);
		return effect == null ? null : effect.stopHandler();
	}






	private static StopResult offClanScroll(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		PcClanBuffEffects.notifyChange(pc, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offBurningShot(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);

		return StopResult.HANDLED;
	}




	// ===== N_BUFF =====

	private static CSkillEffectPumpEnd.StopResult offNBuffPvpDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4346, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffHpMp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_IvenBuffIcon.send(pc, skillId, false, 4347, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffDmg(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4348, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffReduct(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4349, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffSp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_IvenBuffIcon.send(pc, skillId, false, 4350, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffStun(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4351, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffHold(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4352, 0);
		return StopResult.HANDLED;
	}

	// dmg-attribute icons only

	private static StopResult offInventoryElementalTimer(L1Character target, int skillId) {
		if (!(target instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		SC_IvenBuffIcon.send(pc, skillId, false, 4342 + skillId - L1SkillId.N_BUFF_WATER_DMG, 0);
		return StopResult.HANDLED;
	}

	// stat

	private static CSkillEffectPumpEnd.StopResult offNBuffStr(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4338, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffDex(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4339, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffInt(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4340, 0);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffWis(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4341, 0);
		return StopResult.HANDLED;
	}

	// elemental resist

	private static CSkillEffectPumpEnd.StopResult offNBuffWater(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4333, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffWind(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4334, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffEarth(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4335, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffFire(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4336, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offNBuffAllResist(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_IvenBuffIcon.send(pc, skillId, false, 4337, 0);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	// ===== misc =====

	private static CSkillEffectPumpEnd.StopResult offFear(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		return StopResult.HANDLED;
	}

	private static CSkillEffectPumpEnd.StopResult offExpPotion(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// The activation zone can differ from the expiry zone: close either icon ID.
		SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_POTION);
		SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_POTION + 1);
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

		cha.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offMiso3(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		cha.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offDragonSet(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}


	private static StopResult offHunterBless1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}


    private static StopResult offPolyRingMaster(L1Character cha, int skillId) {
        CTransform.PumpEndTransformSkill(cha, skillId);
        return StopResult.HANDLED;
    }

	private static StopResult offThunderGrab(L1Character cha, int skillId) {
		// STATUS_FREEZE owns the actual bind lifetime.
		return cha instanceof L1NpcInstance ? StopResult.HANDLED : StopResult.NOT_HANDLED;
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

		// Blessing can cross the threshold while active; never infer the old icon at expiry.
		SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_BUFF);
		SC_InventoryIcon.icoEnd(pc, L1SkillId.EXP_BUFF + 1);
		return StopResult.HANDLED;
	}

	private static StopResult offTitanlRising(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// removed: pc.removeSkillEffect(TITANL_RISING);
		pc.setRisingUp(0);
		return StopResult.HANDLED;
	}

	private static StopResult offClanStance(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		if (skillId == L1SkillId.CLAN_BUFF2) SC_OwnCharAttrDef.send(pc);
		SC_ServerMessage.send(pc, 4619, CSkillEffectActivation.clanStanceName(skillId));
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
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;

		// A child cooldown can expire after its parent visual was removed.
		// Only restore stage-1 FOCUS_WAVE if that parent is still active.
		if (!pc.hasSkillEffect(L1SkillId.FOCUS_WAVE)) {
			if (pc.get_focus_wave_level() != 0) {
				pc.set_focus_wave_level(0);
				BraveSpeedEffects.onEnd(pc, skillId);
			}
			return StopResult.HANDLED;
		}
		if (pc.get_focus_wave_level() != 1) {
			SC_SkillSound.send(pc, pc.getId(), 16540);
			SC_SkillSound.broadcastFast(pc, pc.getId(), 16540);

			SC_SkillBrave.send(pc, pc.getId(), 10,
					Math.max(0, pc.getSkillEffectTimeSec(L1SkillId.FOCUS_WAVE)));
			SC_SkillBrave.broadcastFast(pc, pc.getId(), 10, 0);

			pc.set_focus_wave_level(1);
		}
		return StopResult.HANDLED;
	}


	private static StopResult offStrIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			pc.removeSkillModifierFromOwner(skillId);
			SC_OwnCharStatus2.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDexIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance  pc) {
			pc.removeSkillModifierFromOwner(skillId);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offIntIce(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance  pc) {
			pc.removeSkillModifierFromOwner(skillId);
			SC_OwnCharStatus2.send(pc);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offLight(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			if (!cha.isInvisble()) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.refreshLight();
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
			pc.removeSkillModifierFromOwner(skillId);
			SC_SkillIconAura.send(pc, 113, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offGodBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		return StopResult.HANDLED;
	}


	private static StopResult offNpcBloodBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		if (skillId == L1SkillId.BUFF_SAEL || skillId == L1SkillId.BUFF_GUNTER) SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offShiningShield(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
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
		cha.removeSkillModifierFromOwner(skillId);
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
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SPMR.send(pc);
			// SC_SkillIconShield.send(pc, 3, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDressDexterity(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Dexup.send(pc, pc, 3, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDressMighty(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Strup.send(pc, pc, 3, 0);
		}
		return StopResult.HANDLED;
	}
	
	private static StopResult offPatience(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		
	    pc.removeSkillModifierFromOwner(skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offEarthGuardian(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		
	    cha.removeSkillModifierFromOwner(skillId);
		SC_SkillIconShield.send(pc, 7, 0);
		return StopResult.HANDLED;
	}

	private static StopResult offResistMagic(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offResistElemental(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offElementalProtection(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offElementalFallDown(L1Character target, int skillId) {
		if (target instanceof L1PcInstance pc) {
			pc.removeSkillModifierFromOwner(skillId);
			SC_InventoryIcon.icoEnd(pc, skillId);
			SC_OwnCharAttrDef.send(pc);
			return StopResult.HANDLED;
		}
		if (target instanceof L1NpcInstance) {
			target.removeSkillModifierFromOwner(skillId);
			return StopResult.HANDLED;
		}
		return StopResult.NOT_HANDLED;
	}

	private static StopResult offIronSkin(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconShield.send(pc, 10, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFireShield(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconShield.send(pc, 4, 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPhysicalEnchantStr(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			SC_Strup.send(pc, pc, 1, 0);		
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPhysicalEnchantDex(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			SC_Dexup.send(pc, pc, 1, 0);			
		}
		return StopResult.HANDLED;
	}

	private static StopResult offEarthWeapon(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
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
		BraveSpeedEffects.onEnd(cha, skillId);
		return StopResult.HANDLED;
	}

	// 크레이 혈흔



	// UI DG표시
	private static StopResult offUncannyDodge(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offBurningWeapon(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_SkillIconAura.send(pc, 162, 0);
		}
		return StopResult.HANDLED;
	}


	private static StopResult offAquaShot(L1Character cha, int skillId) {
		cha.removeSkillModifierFromOwner(skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offInsight(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			pc.resetBaseMr();
		}
		return StopResult.HANDLED;
	}

	private static StopResult offTamFruitSync(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			TamFruitUseService.onTamSkillStopped(pc, skillId);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offShapeChange(L1Character cha, int skillId) {
        CTransform.PumpEndTransformSkill(cha, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offCurseBlindFamily(L1Character cha, int skillId) {
		// The removed timer is already absent. Do not clear client blindness
		// or floating-eye visibility while another independent source remains.
		if (cha instanceof L1PcInstance pc && !pc.isBlind()) {
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
			pc.removeSkillModifierFromOwner(skillId);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offDiseaseFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offHorrorOfDeath(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offPanic(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
			pc.resetBaseMr();
		}
		return StopResult.HANDLED;
	}

	private static StopResult offMobBerserkers(L1Character cha, int skillId) {
		if (cha instanceof L1NpcInstance) {
			MovementSpeedEffects.endHaste(cha, skillId);
			BraveSpeedEffects.onEnd(cha, skillId);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offFreezeFamily(L1Character target, int skillId) {
		if (ControlSkillEffects.hasSkillFreeze(target, skillId) || target.getId() == 0)
			return StopResult.HANDLED;
		if (target instanceof L1PcInstance pc) {
			if (pc.isParalyzed()) return StopResult.HANDLED;
			SC_Poison.send(pc, pc.getId(), 0);
			SC_Poison.broadcastFast(pc, pc.getId(), 0);
			if (ControlSkillEffects.isSkillFreeze(skillId))
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
		} else if (target instanceof L1MonsterInstance || target instanceof L1SummonInstance
				 || target instanceof L1CompanionInstance) {
			SC_Poison.broadcastFast(target, target.getId(), 0);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStunFamily(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc && !ControlSkillEffects.hasStun(pc, skillId)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_STUN, false);
		}
		return StopResult.HANDLED;
	}

	private static boolean hasOtherSleepEffect(L1Character target, int endId) {
		return (endId != L1SkillId.DARK_BLIND && target.hasSkillEffect(L1SkillId.DARK_BLIND))
				|| (endId != L1SkillId.PHANTASM && target.hasSkillEffect(L1SkillId.PHANTASM))
				|| (endId != L1SkillId.FOG_OF_SLEEPING && target.hasSkillEffect(L1SkillId.FOG_OF_SLEEPING));
	}

	private static StopResult offSleepFamily(L1Character cha, int skillId) {
		if (hasOtherSleepEffect(cha, skillId)) return StopResult.HANDLED;
		if (cha instanceof L1PcInstance) {
			SC_Paralysis.send(((L1PcInstance) cha), SC_Paralysis.TYPE_SLEEP, false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offSlowFamily(L1Character cha, int skillId) {
		MovementSpeedEffects.endSlow(cha, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offStatusFreeze(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, false);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusIgnition(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.AddFireResist(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusQuake(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.AddEarthResist(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offStatusShock(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.AddWindResist(-30);
			SC_OwnCharAttrDef.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offUnderwaterBreath(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance pc) {
			SC_SkillIconBlessOfEva.send(pc, pc.getId(), pc.getEquipment().evaIconDuration(0));
		}
		return StopResult.HANDLED;
	}

	private static StopResult offWisdomPotion(L1Character cha, int skillId) {
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.removeSkillModifierFromOwner(skillId);
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

	private static StopResult offCashScroll(L1Character target, int id) {
		if (target instanceof L1PcInstance pc) {
			pc.removeSkillModifierFromOwner(id);
			CSkillEffectActivation.notifyCashScrollStats(pc, id);
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
			pc.removeSkillModifierFromOwner(skillId);
			SC_InventoryIcon.icoEnd(pc, L1SkillId.성혈버프);
			
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		return StopResult.HANDLED;
	}

	private static StopResult offComa(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		if (skillId == L1SkillId.COMA_B) SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}


	private static StopResult offFeatherBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		if (skillId == L1SkillId.FEATHER_BUFF_A || skillId == L1SkillId.FEATHER_BUFF_B) SC_SPMR.send(pc);
		else SC_OwnCharAttrDef.send(pc);
		if (skillId != L1SkillId.FEATHER_BUFF_D) {
			if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		return StopResult.HANDLED;
	}



	
	private static StopResult offDragonMaan(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		if (CSkillEffectActivation.hasDragonMaanSpellPower(skillId)) SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}







	private static StopResult offDragonBlood(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_OwnCharStatus.send(pc);
		SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, CSkillEffectActivation.dragonBloodIcon(skillId), 0);
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
	        cha.removeSkillModifierFromOwner(skillId);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCounterMirror(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance pc) {
	    	cha.removeSkillModifierFromOwner(skillId);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offReduceOrDecreaseWeight(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance pc) {
	    	cha.removeSkillModifierFromOwner(skillId);
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
	    cha.removeSkillModifierFromOwner(skillId);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillIconAura.send(pc, 155, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offStormShot(L1Character cha, int skillId) {
	    cha.removeSkillModifierFromOwner(skillId);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_SkillIconAura.send(pc, 165, 0);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBerserkers(L1Character cha, int skillId) {
	    cha.removeSkillModifierFromOwner(skillId);
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
	        cha.removeSkillModifierFromOwner(skillId);
	        sendEndNoti(pc, L1SkillId.FOCUS_SPRITS, 4832, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeRich(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_SPMR.send(pc);
	        sendEndNoti(pc, L1SkillId.CUBE_RICH, 5309, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeGolem(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_OwnCharAttrDef.send(pc);
	        sendEndNoti(pc, L1SkillId.CUBE_GOLEM, 5314, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offCubeOgre(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
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
	        pc.removeSkillModifierFromOwner(skillId);
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
	        pc.removeSkillModifierFromOwner(skillId);

	        sendEndNoti(pc, L1SkillId.GRACE_AVATAR, 7427, 4741, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offDressEvasion(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        SC_OwnCharStatus.send(pc);
	        sendEndNotiMsgOnly(pc, L1SkillId.DRESS_EVASION, 2203, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offAquaProtecter(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        sendEndNotiMsgOnly(pc, L1SkillId.AQUA_PROTECTER, 2201, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offSolidCarriage(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNotiMsgOnly(pc, L1SkillId.SOLID_CARRIAGE, 2203, true);

	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
	        SC_OwnCharStatus.send(pc);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offGuardBreak(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.removeSkillModifierFromOwner(skillId);
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

	        pc.removeSkillModifierFromOwner(skillId);

	        sendEndNoti(pc, L1SkillId.CLEAR_MIND, 5279, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offBlessedArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
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

	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendEndNoti(pc, L1SkillId.SHINING_ARMOR, 9483, true);
	    }
	    return StopResult.HANDLED;
	}
	private static StopResult offPersistentPotential(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		switch (skillId) {
		case L1SkillId.hp300 -> {
			if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		}
		case L1SkillId.콘3 -> {
			pc.setCurrentHp(Math.min(pc.getCurrentHp(), pc.getMaxHp()));
			if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		}
		case L1SkillId.mp300 -> SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		case L1SkillId.위즈3 -> {
			pc.setCurrentMp(Math.min(pc.getCurrentMp(), pc.getMaxMp()));
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_SPMR.send(pc);
		}
		case L1SkillId.mr15, L1SkillId.sp5 -> SC_SPMR.send(pc);
		case L1SkillId.ac5, L1SkillId.모든속성저항20 -> SC_OwnCharAttrDef.send(pc);
		default -> { }
		}
		sendLegacyEndNoti(pc, skillId, 0, true);
		return StopResult.HANDLED;
	}

	private static StopResult offWanted(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_InventoryIcon.icoEnd(pc,
				skillId == L1SkillId.USER_WANTED1 ? L1SkillId.수배1 : L1SkillId.수배);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus2.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPassiveShiningArmor(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_InventoryIcon.icoEnd(pc, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offWeaponBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		switch (skillId) {
		case L1SkillId.BLESS_WEAPON -> sendScalesEndNoti(pc, skillId, 728, 2167, true);
		case L1SkillId.HOLY_WEAPON -> sendScalesEndNoti(pc, skillId, 777, 718, true);
		case L1SkillId.ENCHANT_WEAPON -> sendScalesEndNoti(pc, skillId, 5265, 2239, true);
		case L1SkillId.SHADOW_FANG -> sendScalesEndNoti(pc, skillId, 1128, 2234, true);
		default -> throw new IllegalArgumentException("Unknown weapon buff " + skillId);
		}
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

	        pc.removeSkillModifierFromOwner(skillId);
	        SC_OwnCharAttrDef.send(pc);
	        SC_SPMR.send(pc);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_EARTH_DRAGON, 3182, 2420, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesWaterDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendScalesEndNoti(pc, L1SkillId.SCALES_WATER_DRAGON, 3184, 2424, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesRindviorDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.removeSkillModifierFromOwner(skillId);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_RINDVIOR_DRAGON, 8887, 5271, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScalesFireDragon(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.removeSkillModifierFromOwner(skillId);

	        sendScalesEndNoti(pc, L1SkillId.SCALES_FIRE_DRAGON, 3078, 2427, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionOgre(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        sendEndNoti(pc, L1SkillId.IllUSION_OGRE, 3117, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionLich(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_SPMR.send(pc);
	        sendEndNoti(pc, L1SkillId.IllUSION_LICH, 3115, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionDiamondGolem(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        SC_OwnCharAttrDef.send(pc);
	        sendEndNoti(pc, L1SkillId.IllUSION_DIAMONDGOLEM, 3113, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offFreezeengArmor(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        pc.removeSkillModifierFromOwner(skillId);
	        SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());

	        sendEndNoti(pc, L1SkillId.FREEZEENG_ARMOR, 9490, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offIllusionAvatar(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        cha.removeSkillModifierFromOwner(skillId);
	        sendEndNoti(pc, L1SkillId.IllUSION_AVATAR, 3111, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offEnchantAcurucy(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        pc.removeSkillModifierFromOwner(skillId);
	        sendEndNoti(pc, L1SkillId.ENCHANT_ACURUCY, 9487, true);
	    }
	    return StopResult.HANDLED;
	}

	private static StopResult offScaledHp(L1Character cha, int skillId, CSkillEffect.MaxHp effect) {
		if (cha instanceof L1PcInstance pc) {
			sendEndNotiMsgOnly(pc, skillId, effect.stopMessage, true);
			pc.removeSkillModifierFromOwner(skillId);
			if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		}
		return StopResult.HANDLED;
	}

	private static StopResult offEaggleEye(L1Character cha, int skillId) {
	    cha.removeSkillModifierFromOwner(skillId);
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;

	        sendEndNoti(pc, L1SkillId.EAGGLE_EYE, 8490, true);
	    }
	    return StopResult.HANDLED;
	}


	

	private static StopResult offNoop(L1Character cha, int skillId) {
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

	private static StopResult offLegacyScaledHp(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_InventoryIcon.icoEnd(pc, skillId);
		pc.removeSkillModifierFromOwner(skillId);

		if (pc.isInParty()) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		return StopResult.HANDLED;
	}

	private static StopResult offStunControlOverloadDg(L1Character cha, int skillId) {
		return cha instanceof L1PcInstance ? StopResult.HANDLED : StopResult.NOT_HANDLED;
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
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offRudolphBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPrime(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);

		SC_InventoryIcon.icoEnd(pc, L1SkillId.프라임);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPrime1(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_InventoryIcon.icoEnd(pc, L1SkillId.프라임);
		pc.removeSkillModifierFromOwner(skillId);

		SC_SPMR.send(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyIllusionLich(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyIllusionGolem(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.removeSkillModifierFromOwner(skillId);
		SC_OwnCharAttrDef.send(pc);
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
		pc.removeSkillModifierFromOwner(skillId);
		SC_InventoryIcon.icoEnd(pc, L1SkillId.솔리드캐리지);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyEvasion(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;
		pc.removeSkillModifierFromOwner(skillId);
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
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		// The active modifier was already captured by the target owner at
		// first grant. Removing this skill leaves every other source intact.
		pc.removeSkillModifierFromOwner(skillId);
		if (skillId == L1SkillId.잠재력저지먼트5) SC_SPMR.send(pc);
		else SC_OwnCharAttrDef.send(pc);
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

		if (pc.getInventory().IsEquippedByClassID(123111)
				|| pc.getInventory().IsEquippedByClassID(123112)
				|| pc.getInventory().IsEquippedByClassID(123113)) {

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
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		sendLegacyEndNoti(pc, L1SkillId.가드피어효과, 5106, true);
		SC_SystemMessage.send(pc, "디스트로이:피어 효과가 사라집니다.");
		return StopResult.HANDLED;
	}

	private static StopResult offGuardFearHorrorEffect(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
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


	private static StopResult offPotential(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);

		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_SystemMessage.send(pc, "포텐셜의 효과가 사라집니다.");
		return StopResult.HANDLED;
	}
	
	private static StopResult offSpecialBuff(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		if (skillId != L1SkillId.BUFF_특별한성장축복) {
			pc.removeSkillModifierFromOwner(skillId);
			if (skillId == L1SkillId.BUFF_특별한방어축복) SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			else SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		SC_InventoryIcon.icoEnd(pc, skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offLegacyAdvance(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
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
			int damage = GameRandom.nextInt(
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
	pc.removeSkillModifierFromOwner(skillId);
		SC_SPMR.send(pc);

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		return StopResult.HANDLED;
	}

	private static StopResult offBerserkCustom(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.removeSkillModifierFromOwner(skillId);
		SC_SystemMessage.send(pc, "버서크 효과가 사라집니다.");
		SC_OwnCharAttrDef.send(pc);
		return StopResult.HANDLED;
	}

	private static StopResult offPlayerModifier(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance pc)) return StopResult.NOT_HANDLED;
		pc.removeSkillModifierFromOwner(skillId);
		return StopResult.HANDLED;
	}

	private static StopResult offCounterBarrierLegacy(L1Character cha, int skillId) {
		if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		// The sprite alone is not proof of an active ring effect. Never grant
		// ring bonuses when only COUNTER_BARRIER is ending.
		if (pc.getCurrentSpriteId() == 21169 && pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
			int keepTime = pc.getSkillEffectTimeSec(L1SkillId.POLY_RING_MASTER);
			pc.Transform(21140,
					keepTime,
					TransformType.ITEM_MAGIC,
					true);
		}
		return StopResult.HANDLED;
	}

	private static void sendLegacyEndNoti(
			L1PcInstance pc,
			int spellId,
			int endStrId,
			boolean isGood) {

		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
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
		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
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
		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
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
	            
	            || cha instanceof L1CompanionInstance) {
	        return StopResult.HANDLED;
	    }

	    return StopResult.NOT_HANDLED;
	}

	private static StopResult offDesperado(L1Character cha, int skillId) {
	    if (cha instanceof L1PcInstance) {
	        L1PcInstance pc = (L1PcInstance) cha;
	        SC_Paralysis.send(pc, SC_Paralysis.TYPE_PERADO, false);
	    }

	    cha.Desperadolevel = 0;
	    return StopResult.HANDLED;
	}

	private static StopResult offAdvanceSpirit(L1Character cha, int skillId) {
	    if (!(cha instanceof L1PcInstance)) return StopResult.NOT_HANDLED;
	    L1PcInstance pc = (L1PcInstance) cha;

	    pc.removeSkillModifierFromOwner(skillId);

	    SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
	    if (pc.isInParty()) {
	        pc.getParty().refreshPartyMemberStatus(pc);
	    }
	    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

	    l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
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
		MovementSpeedEffects.endHaste(cha, skillId);
		return StopResult.HANDLED;
	}
	
	private static int notiSpellId(int spellId) {
	    if (spellId <= 0) return spellId;
	    return spellId - 1;
	}
}
