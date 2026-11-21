package l1j.server.server.model.character.skill;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.character.effect.PcClanBuffEffects;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.utils.eKind;

import static l1j.server.server.model.character.skill.CSkillEffect.Bonus.*;
import static l1j.server.server.model.character.skill.L1SkillId.*;

/** One immutable runtime definition per ID. L1Skills still owns the game metadata. */
public final class CSkillInfo {
    private static final int PAGED_LIMIT = 32768;
    private static final int PAGE_SHIFT = 5;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final CSkillInfo[][] pages = new CSkillInfo[PAGED_LIMIT >>> PAGE_SHIFT][];
    private static final Int2ObjectOpenHashMap<CSkillInfo> sparse = new Int2ObjectOpenHashMap<>(48);

    private static boolean frozen;

    static {
        SkillHandlers.registerEffects();
        SkillStopHandlers.registerEffects();
        registerManaged(ADVANCE_SPIRIT, SkillHandlers::startAdvanceSpirit, MAX_HP, MAX_MP);
        registerManaged(PRIDE, SkillHandlers::startScaledHp, MAX_HP);
        registerManaged(GIGANTIC, SkillHandlers::startScaledHp, MAX_HP);
        registerManaged(SHINING_AURA, SkillHandlers::startShiningShield, AC);
        registerManaged(SHIELD, SkillHandlers::startShiningShield, AC);
        registerManaged(SHADOW_ARMOR, SkillHandlers::startShadowArmor, MR);
        registerManaged(IllUSION_DIAMONDGOLEM, SkillHandlers::startIllusionDiamondGolem, AC);
        registerManaged(SCALES_EARTH_DRAGON, SkillHandlers::startScalesEarthDragon, AC, MR);
        registerManaged(N_BUFF_STR, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_DEX, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_INT, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_WIS, SkillHandlers::startInventoryStatBuff, PRIMARY_STATS);
        registerManaged(N_BUFF_WATER, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_WIND, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_EARTH, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_FIRE, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(N_BUFF_ALL_RESIST, SkillHandlers::startInventoryStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(DRESS_MIGHTY, SkillHandlers::startDressStatBuff, PRIMARY_STATS);
        registerManaged(DRESS_DEXTERITY, SkillHandlers::startDressStatBuff, PRIMARY_STATS);
        registerManaged(DRESS_EVASION, SkillHandlers::startEvasionBuff, ER);
        registerManaged(AQUA_PROTECTER, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SOLID_CARRIAGE, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SCALES_WATER_DRAGON, SkillHandlers::startScalesEvasion, ER);
        registerManaged(SCALES_RINDVIOR_DRAGON, SkillHandlers::startScalesEvasion, DG);
        registerManaged(FREEZEENG_ARMOR, SkillHandlers::startEvasionBuff, ER);
        registerManaged(SHINING_ARMOR, SkillHandlers::startEvasionBuff, ER);
        // 9483 is a separate externally granted passive from spell 116.
        registerManaged(샤이닝아머, null, fixed(ER, 10));
        // Persistent external potential bonuses share one source per skill ID.
        registerManaged(hp300, null, fixed(MAX_HP, 300));
        registerManaged(mp300, null, fixed(MAX_MP, 300L << 32));
        registerManaged(대미지감소5, null, fixed(ARMOR_REDUCTION, 5));
        registerManaged(ac5, null, fixed(AC, -5));
        registerManaged(mr15, null, fixed(MR, 15L << 32));
        registerManaged(모든속성저항20, null, fixed(ELEMENTAL_RESISTANCE, 0x14141414L));
        registerManaged(힘3, null, fixed(PRIMARY_STATS, 3));
        registerManaged(덱3, null, fixed(PRIMARY_STATS, 3L << 8));
        registerManaged(콘3, null, fixed(PRIMARY_STATS, 3L << 32));
        registerManaged(위즈3, null, fixed(PRIMARY_STATS, 3L << 24));
        registerManaged(인트3, null, fixed(PRIMARY_STATS, 3L << 16));
        registerManaged(pvp대미지7, null, fixed(PVP_DAMAGE, 7));
        registerManaged(근거리대미지5, null, fixed(MELEE_DAMAGE, 5L << 32));
        registerManaged(원거리대미지5, null, fixed(RANGED_DAMAGE, 5));
        registerManaged(근거리명중5, null, fixed(MELEE_HIT, 5));
        registerManaged(원거리명중5, null, fixed(RANGED_HIT, 5));
        registerManaged(sp5, null, fixed(SP, 5));
        registerManaged(마법적중5, null, fixed(MAGIC_HIT, 5));
        registerManaged(기술내성8, null, specialResistance(eKind.ABILITY, 8));
        registerManaged(정령내성8, null, specialResistance(eKind.SPIRIT, 8));
        registerManaged(용언내성3, null, specialResistance(eKind.DRAGON_SPELL, 3));
        registerManaged(공포내성3, null, specialResistance(eKind.FEAR, 3));
        registerManaged(모든내성3, null, specialResistance(eKind.ALL, 3));
        registerManaged(정령적중5, null, fixed(SPIRIT_PIERCE, 5L << 32));
        registerManaged(모든적중3, null, fixed(ALL_PIERCE, 3));
        registerManaged(pvp감소7, null, fixed(PVP_MAGIC_REDUCTION, 7));
        registerManaged(기술적중5, null, fixed(SPECIAL_PIERCE_FOUR, 5));
        registerManaged(용언적중5, null, fixed(SPECIAL_PIERCE_FOUR, 5L << 16));
        registerManaged(공포적중5, null, fixed(SPECIAL_PIERCE_FOUR, 5L << 24));
        registerManaged(RESIST_MAGIC, SkillHandlers::startResistMagic, MR);
        registerManaged(GLOWING_AURA, SkillHandlers::startMeleeBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(IllUSION_OGRE, SkillHandlers::startMeleeBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(ENCHANT_ACURUCY, SkillHandlers::startMeleeBuff, MELEE_HIT);
        registerManaged(N_BUFF_DMG, SkillHandlers::startInventoryDamageBuff, MELEE_DAMAGE, RANGED_DAMAGE);
        registerManaged(N_BUFF_SP, SkillHandlers::startSpellPowerBuff, SP);
        registerManaged(IllUSION_LICH, SkillHandlers::startSpellPowerBuff, SP);
        registerManaged(N_BUFF_HPMP, SkillHandlers::startInventoryHealthBuff,
                MAX_HP, MAX_MP, new CSkillEffect.CarryLimit(3));
        registerManaged(N_BUFF_REDUCT, SkillHandlers::startInventoryArmorBuff, ARMOR_REDUCTION);
        registerManaged(N_BUFF_PVP_DMG, SkillHandlers::startInventoryResistanceBuff, PVP_DAMAGE, PVP_REDUCTION);
        registerManaged(N_BUFF_STUN, SkillHandlers::startInventoryResistanceBuff, SPECIAL_RESISTANCE);
        registerManaged(N_BUFF_HOLD, SkillHandlers::startInventoryResistanceBuff, SPECIAL_RESISTANCE);
        registerManaged(CLEAR_MIND, SkillHandlers::startClearMind, PRIMARY_STATS);
        registerManaged(PHYSICAL_ENCHANT_STR, SkillHandlers::startPhysicalEnchant, PRIMARY_STATS);
        registerManaged(PHYSICAL_ENCHANT_DEX, SkillHandlers::startPhysicalEnchant, PRIMARY_STATS);
        registerManaged(RESIST_ELEMENTAL, SkillHandlers::startCommonStatBuff, ELEMENTAL_RESISTANCE);
        registerManaged(ELEMENTAL_PROTECTION, SkillHandlers::startCommonStatBuff, ELEMENTAL_RESISTANCE, SPIRIT_PIERCE);
        registerManaged(INSIGHT, SkillHandlers::startCommonStatBuff, PRIMARY_STATS);
        registerManaged(MIRROR_IMAGE, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(UNCANNY_DODGE, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(기간틱, null, MAX_HP);
        registerManaged(프라이드, null, MAX_HP);
        registerManaged(포텐셜, null, MAX_HP, MAX_MP, CSkillEffect.Potential.MR,
                CSkillEffect.Potential.SP, CSkillEffect.Potential.ER, CSkillEffect.Potential.DG);
        registerManaged(God_buff, SkillHandlers::startGodBuff, MELEE_HIT,
                new CSkillEffect.Fixed(AC, -2), new CSkillEffect.Fixed(MAX_HP, 20),
                new CSkillEffect.Fixed(MAX_MP, 13L << 32), specialResistance(eKind.SPIRIT, 10));
        registerManaged(SCALES_FIRE_DRAGON, SkillHandlers::startScalesFireDragon, MELEE_HIT,
                specialResistance(eKind.ABILITY, 5), specialResistance(eKind.DRAGON_SPELL, 5),
                specialResistance(eKind.FEAR, 5));
        registerManaged(BRAVE_AURA, SkillHandlers::startBraveAura);
        registerManaged(IRON_SKIN, SkillHandlers::startArmorBuff, AC);
        registerManaged(FIRE_SHIELD, SkillHandlers::startArmorBuff, AC);
        registerManaged(BLESSED_ARMOR, SkillHandlers::startArmorBuff, AC);
        registerManaged(BOUNCE_ATTACK, SkillHandlers::startCommonStatBuff, MELEE_HIT);
        registerManaged(PATIENCE, SkillHandlers::startCommonStatBuff, ARMOR_REDUCTION);
        registerManaged(BERSERKERS, SkillHandlers::startBerserkers,
                new CSkillEffect.Fixed(AC, 10), MELEE_HIT, MELEE_DAMAGE);
        registerManagedWithFallback(GUARD_BREAK, SkillHandlers::startGuardBreak,
                SkillHandlers::castGuardBreakOnNpc, AC);
        registerManaged(WEAKNESS, SkillHandlers::startCommonStatBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_WEAKNESS_1, SkillHandlers::startCommonStatBuff, MELEE_HIT, MELEE_DAMAGE);
        registerManaged(DISEASE, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_DISEASE_1, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(MOB_DISEASE_30, SkillHandlers::startDisease, new CSkillEffect.Fixed(AC, 12), MELEE_HIT, MELEE_DAMAGE);
        registerManaged(FEAR, SkillHandlers::startCommonStatBuff, DG);
        registerManaged(HORROR_OF_DEATH, SkillHandlers::startCommonStatBuff, PRIMARY_PENALTY);
        registerManaged(PANIC, SkillHandlers::startCommonStatBuff, PRIMARY_PENALTY);
        registerNotifiedTimer(MAJESTY, 9518, 0, 5893, 5893, 0);
        registerNotifiedTimer(ABSOLUTE_BARRIER, 717, 10, 700, 700, 2179);
        registerNotifiedTimer(ABSOLUTE_BLADE, 7432, 0, 4735, 4735, 4742);
        registerNotifiedTimer(REDUCTION_ARMOR, 1889, 0, 1043, 1043, 2197);
        registerNotifiedTimer(MOEBIUS, 9443, 0, 5550, 5552, 0);
        registerManaged(N_BUFF_WATER_DMG, SkillHandlers::startInventoryIconTimer);
        registerManaged(N_BUFF_WIND_DMG, SkillHandlers::startInventoryIconTimer);
        registerManaged(N_BUFF_EARTH_DMG, SkillHandlers::startInventoryIconTimer);
        registerManaged(N_BUFF_FIRE_DMG, SkillHandlers::startInventoryIconTimer);
        registerManaged(DECREASE_WEIGHT, SkillHandlers::startFixedCombatBuff, new CSkillEffect.CarryLimit(180));
        registerManaged(REDUCE_WEIGHT, SkillHandlers::startFixedCombatBuff, new CSkillEffect.CarryLimit(180));
        registerManaged(COUNTER_MIRROR, SkillHandlers::startFixedCombatBuff, new CSkillEffect.CarryLimit(300));
        registerManaged(EARTH_WEAPON, SkillHandlers::startFixedCombatBuff, fixed(MELEE_HIT, 4), fixed(MELEE_DAMAGE, 2L << 32));
        registerManaged(BURNING_WEAPON, SkillHandlers::startFixedCombatBuff, fixed(MELEE_HIT, 3), fixed(MELEE_DAMAGE, 6L << 32));
        registerManaged(AQUA_SHOT, SkillHandlers::startFixedCombatBuff, fixed(RANGED_HIT, 4));
        registerManaged(STORM_EYE, SkillHandlers::startFixedCombatBuff, fixed(RANGED_HIT, 2), fixed(RANGED_DAMAGE, 3));
        registerManaged(STORM_SHOT, SkillHandlers::startFixedCombatBuff, fixed(RANGED_HIT, -3), fixed(RANGED_DAMAGE, 5));
        registerManaged(EARTH_GUARDIAN, SkillHandlers::startFixedCombatBuff, fixed(ARMOR_REDUCTION, 2));
        registerNotifiedTimer(EAGGLE_EYE, 8490, 10, 5157, 0, 5158, fixed(RANGED_CRITICAL, 2));
        registerNotifiedTimer(IllUSION_AVATAR, 3111, 10, 1377, 1377, 2215, fixed(MELEE_DAMAGE, 10L << 32));
        registerNotifiedTimer(FOCUS_SPRITS, 4832, 10, 5272, 0, 5273, fixed(MAGIC_CRITICAL, 5));
        registerManaged(CYCLONE, SkillHandlers::startCasterTimer);
        registerManaged(LUCIFER, SkillHandlers::startCasterTimer);
        registerManaged(miso1, SkillHandlers::startMiso);
        registerManaged(miso2, SkillHandlers::startMiso, fixed(MAX_HP, 100), fixed(MR, 10L << 32), fixed(HP_REGEN, 2));
        registerManaged(miso3, SkillHandlers::startMiso, fixed(MELEE_DAMAGE, 3L << 32), fixed(RANGED_DAMAGE, 3),
                fixed(MAX_MP, 50L << 32), fixed(SP, 3), fixed(MP_REGEN, 2));
        registerManagedPlayerBuff(ANTA_MAAN, SkillHandlers::startDragonMaan, specialResistance(eKind.DRAGON_SPELL, 5));
        registerManagedPlayerBuff(FAFU_MAAN, SkillHandlers::startDragonMaan, specialResistance(eKind.SPIRIT, 5));
        registerManagedPlayerBuff(LIND_MAAN, SkillHandlers::startDragonMaan, fixed(SP, 1), specialResistance(eKind.FEAR, 5));
        registerManagedPlayerBuff(VALA_MAAN, SkillHandlers::startDragonMaan,
                fixed(MELEE_DAMAGE, 2L << 32), specialResistance(eKind.ABILITY, 5));
        registerManagedPlayerBuff(BIRTH_MAAN, SkillHandlers::startDragonMaan,
                specialResistance(eKind.SPIRIT, 5), specialResistance(eKind.DRAGON_SPELL, 5));
        registerManagedPlayerBuff(SHAPE_MAAN, SkillHandlers::startDragonMaan, fixed(SP, 1),
                specialResistance(eKind.SPIRIT, 5), specialResistance(eKind.DRAGON_SPELL, 5), specialResistance(eKind.FEAR, 5));
        registerManagedPlayerBuff(LIFE_MAAN, SkillHandlers::startDragonMaan, fixed(SP, 1),
                fixed(MELEE_DAMAGE, 2L << 32), specialResistance(eKind.ALL, 5), fixed(ALL_PIERCE, 3));
        registerManagedPlayerBuff(ANTA_BUFF, SkillHandlers::startDragonBlood, fixed(AC, -2), fixed(ELEMENTAL_RESISTANCE, 50));
        registerManagedPlayerBuff(FAFU_BUFF, SkillHandlers::startDragonBlood,
                fixed(HP_REGEN, 3), fixed(MP_REGEN, 1), fixed(ELEMENTAL_RESISTANCE, 50L << 8));
        registerManagedPlayerBuff(RIND_BUFF, SkillHandlers::startDragonBlood,
                fixed(MELEE_HIT, 3), fixed(RANGED_HIT, 3), fixed(ELEMENTAL_RESISTANCE, 50L << 24));
        final CSkillEffect seasonalHp = fixed(MAX_HP, 50);
        final CSkillEffect seasonalMp = fixed(MAX_MP, 30L << 32);
        registerManagedPlayerBuff(FEATHER_BUFF_A, SkillHandlers::startFeatherBuff, seasonalHp, seasonalMp,
                fixed(HP_REGEN, 3), fixed(MP_REGEN, 3), fixed(MELEE_DAMAGE, 2L << 32), fixed(MELEE_HIT, 2),
                fixed(SP, 2), ARMOR_REDUCTION);
        registerManagedPlayerBuff(FEATHER_BUFF_B, SkillHandlers::startFeatherBuff, seasonalHp, seasonalMp,
                fixed(MELEE_HIT, 2), fixed(SP, 1), ARMOR_REDUCTION);
        registerManagedPlayerBuff(FEATHER_BUFF_C, SkillHandlers::startFeatherBuff, seasonalHp, seasonalMp, fixed(AC, -2));
        registerManagedPlayerBuff(FEATHER_BUFF_D, SkillHandlers::startFeatherBuff, fixed(AC, -1));
        registerManagedPlayerBuff(성혈버프, SkillHandlers::startSeonghyeolBuff, fixed(MAX_HP, 300), fixed(MAX_MP, 150L << 32),
                fixed(MELEE_DAMAGE, 3L << 32), fixed(RANGED_DAMAGE, 3), fixed(SP, 3), fixed(AC, -10), fixed(MR, 15L << 32),
                fixed(PVP_DAMAGE, 5), fixed(PVP_REDUCTION, 5L << 32));
        registerManagedPlayerBuff(메티스축복주문서, SkillHandlers::startMetisBlessScroll, seasonalHp, seasonalMp,
                fixed(MELEE_HIT, 2), fixed(MELEE_DAMAGE, 2L << 32), fixed(RANGED_HIT, 2), fixed(RANGED_DAMAGE, 2),
                fixed(SP, 2), fixed(HP_REGEN, 3), fixed(MP_REGEN, 3));
        registerManagedPlayerBuff(DRAGON_HUNTER_BLESS, SkillHandlers::startEventIconBuff);
        registerManagedPlayerBuff(HUNTER_BLESS, SkillHandlers::startEventIconBuff);
        registerManagedPlayerBuff(HUNTER_BLESS2, SkillHandlers::startEventIconBuff);
        registerManagedPlayerBuff(COMA_A, SkillHandlers::startComa, fixed(PRIMARY_STATS, (1L << 32) | 0x0505),
                fixed(WEAPON_HIT_RATE, 3), fixed(AC, -3));
        registerManagedPlayerBuff(COMA_B, SkillHandlers::startComa, fixed(PRIMARY_STATS, (3L << 32) | 0x0505),
                fixed(WEAPON_HIT_RATE, 5), fixed(AC, -8), fixed(SP, 1));
        registerManagedPlayerBuff(CLAN_BUFF1, SkillHandlers::startClanStance, fixed(MELEE_DAMAGE, 2L << 32), fixed(RANGED_DAMAGE, 2));
        registerManagedPlayerBuff(CLAN_BUFF2, SkillHandlers::startClanStance, fixed(AC, -3));
        registerManagedPlayerBuff(CLAN_BUFF3, SkillHandlers::startClanStance);
        registerManagedPlayerBuff(CLAN_BUFF4, SkillHandlers::startClanStance);
        registerManagedPlayerBuff(정상의가호, SkillHandlers::startNormalBlessing, fixed(ARMOR_REDUCTION, 8));
        registerManagedPlayerBuff(천하장사버프, SkillHandlers::startStrongmanBuff, fixed(ARMOR_REDUCTION, 5));
        registerBlessing(SIDE_OF_ME_BLESSING, 5);
        registerBlessing(RE_START_BLESSING, 3);
        registerBlessing(NEW_START_BLESSING, 2);
        registerBlessing(LIFE_BLESSING, 1);
        registerManagedPlayerBuff(격전의주문서, SkillHandlers::startBattleScroll, fixed(AC, -5), fixed(MAGIC_HIT, 2),
                fixed(WEAPON_HIT_RATE, 5), fixed(RANGED_HIT, 5), fixed(PVP_DAMAGE, 5), fixed(PVP_REDUCTION, 5L << 32));
        registerManagedPlayerBuff(BUFF_특별한성장축복, SkillHandlers::startSpecialBuff);
        registerManagedPlayerBuff(BUFF_특별한방어축복, SkillHandlers::startSpecialBuff,
                seasonalHp, fixed(ARMOR_REDUCTION, 1), fixed(MR, 5L << 32));
        registerManagedPlayerBuff(BUFF_특별한공격축복, SkillHandlers::startSpecialBuff,
                seasonalMp, fixed(MELEE_DAMAGE, 2L << 32), fixed(RANGED_DAMAGE, 2), fixed(SP, 2));
        registerManagedPlayerBuff(BUFF_SAEL, SkillHandlers::startNpcBloodBuff, fixed(AC, -8),
                fixed(RANGED_HIT, 6), fixed(RANGED_DAMAGE, 3), fixed(MAX_HP, 80), fixed(MAX_MP, 10L << 32),
                fixed(HP_REGEN, 8), fixed(MP_REGEN, 1), fixed(ELEMENTAL_RESISTANCE, 30));
        registerManagedPlayerBuff(BUFF_GUNTER, SkillHandlers::startNpcBloodBuff, fixed(PRIMARY_STATS, 5 << 8),
                fixed(RANGED_HIT, 7), fixed(RANGED_DAMAGE, 5), fixed(MAX_HP, 100), fixed(MAX_MP, 40L << 32),
                fixed(HP_REGEN, 10), fixed(MP_REGEN, 3));
        final CSkillEffect[] crayBlood = { fixed(MELEE_HIT, 5), fixed(MELEE_DAMAGE, 1L << 32),
                fixed(RANGED_HIT, 5), fixed(RANGED_DAMAGE, 1), fixed(MAX_HP, 100), fixed(MAX_MP, 50L << 32),
                fixed(HP_REGEN, 3), fixed(MP_REGEN, 3) };
        registerManagedPlayerBuff(BUFF_CRAY, SkillHandlers::startNpcBloodBuff, crayBlood);
        registerManagedPlayerBuff(BUFF_Vala, SkillHandlers::startNpcBloodBuff, crayBlood);
        registerManagedPlayerBuff(DRAGON_SET, SkillHandlers::startDragonSet, fixed(MELEE_DAMAGE, 3L << 32),
                fixed(WEAPON_DAMAGE_RATE, 3), fixed(RANGED_DAMAGE, 3), fixed(RANGED_HIT, 3), fixed(AC, -3), fixed(SP, 2));
        registerManagedPlayerBuff(SOUL_BARRIER, SkillHandlers::startTimedInventoryIcon);
        registerManagedPlayerBuff(TITANL_RISING, SkillHandlers::startTimedInventoryIcon);
        registerNotifiedTimer(STRIKER_GALE, 2357, 10, 1084, 1084, 2200);
        registerNotifiedTimer(BLOW_ATTACK, 8843, 10, 5266, 5266, 5267);
        registerManagedPlayerBuff(BLESS_WEAPON, SkillHandlers::startWeaponBuff, fixed(MELEE_DAMAGE, 2L << 32), fixed(MELEE_HIT, 2));
        registerManagedPlayerBuff(HOLY_WEAPON, SkillHandlers::startWeaponBuff, fixed(MELEE_DAMAGE, 1L << 32), fixed(MELEE_HIT, 1));
        registerManagedPlayerBuff(ENCHANT_WEAPON, SkillHandlers::startWeaponBuff, fixed(MELEE_DAMAGE, 2L << 32));
        registerManagedPlayerBuff(SHADOW_FANG, SkillHandlers::startWeaponBuff, fixed(MELEE_DAMAGE, 5L << 32));
        registerCube(CUBE_AVATAR, 5322, 3073);
        registerCube(CUBE_RICH, 5309, 1348, fixed(SP, 2));
        registerCube(CUBE_GOLEM, 5314, 3075, fixed(AC, -8));
        registerCube(CUBE_OGRE, 5312, 3074, fixed(MELEE_DAMAGE, 4L << 32), fixed(MELEE_HIT, 4));
        registerContextual(IMPACT, SkillHandlers::castImpact, SkillHandlers::startImpact, ALL_PIERCE, IMPACT_PROBABILITY);
        registerContextual(GRACE_AVATAR, SkillHandlers::castGraceAvatar, SkillHandlers::startGraceAvatar,
                specialResistance(eKind.ALL, 5));
        registerContextual(IMMUNE_TO_HARM, SkillHandlers::castImmuneToHarm, SkillHandlers::startImmuneToHarm);
        registerContextual(ELEMENTAL_FALL_DOWN, SkillHandlers::castElementalFallDown, SkillHandlers::startElementalFallDown,
                ELEMENTAL_WEAKNESS);
        registerContextual(FOG_OF_SLEEPING, SkillHandlers::castSleep, SkillHandlers::startSleep);
        registerContextual(DARK_BLIND, SkillHandlers::castSleep, SkillHandlers::startSleep);
        registerContextual(PHANTASM, SkillHandlers::castSleep, SkillHandlers::startSleep);
        registerContextual(CURSE_BLIND, SkillHandlers::castBlind, SkillHandlers::startBlind);
        registerContextual(DARKNESS, SkillHandlers::castBlind, SkillHandlers::startBlind);
        registerClanBuff(혈맹버프AC, fixed(AC, -5));
        registerClanBuff(혈맹버프마방, fixed(MR, 10L << 32));
        registerClanBuff(혈맹버프PVP추가, fixed(PVP_DAMAGE, 3));
        registerClanBuff(혈맹버프PVP감소, fixed(PVP_REDUCTION, 3L << 32));
        registerClanBuff(혈맹버프HP증가, fixed(MAX_HP, 150));
        registerClanBuff(혈맹버프올내성, specialResistance(eKind.ALL, 3));
        registerClanBuff(혈맹버프올적중, fixed(ALL_PIERCE, 3));
        registerClanBuff(혈맹버프올스텟, fixed(PRIMARY_STATS, 0x030303030303L));
        registerClanBuff(혈맹버프회복);
        registerManaged(HOLY_WALK, SkillHandlers::startBraveSpeed);
        registerManaged(MOVING_ACCELERATION, SkillHandlers::startBraveSpeed);
        registerManaged(BLOOD_LUST, SkillHandlers::startBraveSpeed);
        registerManagedPlayerBuff(HURRICANE, SkillHandlers::startBraveVisual);
        registerManagedPlayerBuff(FOCUS_WAVE, SkillHandlers::startBraveVisual);
        registerManagedPlayerBuff(SAND_STORM, SkillHandlers::startBraveVisual);
        registerManagedPlayerBuff(DANCING_BLADES, SkillHandlers::startBraveVisual);
        registerContextual(WIND_SHACKLE, SkillHandlers::castWindShackle, SkillHandlers::startWindShackle);
        registerContextual(MOB_WINDSHACKLE_1, SkillHandlers::castWindShackle, SkillHandlers::startWindShackle);
        registerContextual(RINDVIOR_WIND_SHACKLE, SkillHandlers::castWindShackle, SkillHandlers::startWindShackle);
        registerContextual(RINDVIOR_WIND_SHACKLE_1, SkillHandlers::castWindShackle, SkillHandlers::startWindShackle);
        registerContextual(DRAKE_WIND_SHACKLE, SkillHandlers::castWindShackle, SkillHandlers::startWindShackle);
        registerContextual(DEATH_HEAL, SkillHandlers::castDeathHeal, SkillHandlers::startDeathHeal);
        registerContextual(DEATH_HEAL_Mob, SkillHandlers::castDeathHeal, SkillHandlers::startDeathHeal);
        registerContextual(STATUS_FREEZE, SkillHandlers::castStatusFreeze, SkillHandlers::startStatusFreeze);
        registerContextual(INVISIBILITY, SkillHandlers::castInvisibility, SkillHandlers::startInvisibility);
        registerContextual(BLIND_HIDING, SkillHandlers::castInvisibility, SkillHandlers::startInvisibility);
        registerContextual(RINDVIOR_SILENCE, SkillHandlers::castRindviorSilence, SkillHandlers::startRindviorSilence);
        // Dedicated control casts: target PC state/visuals must execute on that
        // target's existing 32-slot controller FIFO. NPC admission remains on NpcServer.
        registerContextual(SHAPE_CHANGE, SkillHandlers::onShapeChange, SkillHandlers::startShapeChangeFromOwner);
        registerContextual(TOMAHAWK, SkillHandlers::onTomahawk, SkillHandlers::startTomahawkFromOwner);
        registerContextual(POWERRIP, SkillHandlers::onPowerRip, SkillHandlers::startPowerRipFromOwner);
        registerContextual(DESPERADO, SkillHandlers::onDesperado, SkillHandlers::startDesperadoFromOwner);
        registerContextual(THUNDER_GRAB, SkillHandlers::onThunderGrab, SkillHandlers::startThunderGrabFromOwner);
        registerContextual(프라임, null, SkillHandlers::startPrimeFromOwner,
                fixed(BOW_HIT_RATE, 3), fixed(BOW_DAMAGE_RATE, 3),
                fixed(WEAPON_HIT_RATE, 3), fixed(WEAPON_DAMAGE_RATE, 3),
                fixed(SP, 2), fixed(MAGIC_HIT, 2));
        registerContextual(프라임1, null, SkillHandlers::startPrimeFromOwner,
                fixed(BOW_HIT_RATE, 9), fixed(BOW_DAMAGE_RATE, 9),
                fixed(WEAPON_HIT_RATE, 9), fixed(WEAPON_DAMAGE_RATE, 9),
                fixed(SP, 6), fixed(MAGIC_HIT, 6), fixed(MAX_HP, 500));
        // Legacy item/skill grant paths formerly changed base stats directly.
        // Five externally imposed Judgment debuffs must be removed by source,
        // not by inverse arithmetic against already-capped resistance totals.
        registerManaged(잠재력저지먼트1, null, new CSkillEffect.SpecialPenalty(eKind.ABILITY.value(), -15));
        registerManaged(잠재력저지먼트2, null, new CSkillEffect.SpecialPenalty(eKind.SPIRIT.value(), -15));
        registerManaged(잠재력저지먼트3, null, new CSkillEffect.SpecialPenalty(eKind.DRAGON_SPELL.value(), -15));
        registerManaged(잠재력저지먼트4, null, new CSkillEffect.SpecialPenalty(eKind.FEAR.value(), -15));
        registerManaged(잠재력저지먼트5, null, fixed(MR, -15L << 32));
        registerManaged(어드밴스, null, MAX_HP, MAX_MP);
        registerManaged(일루션리치, null, fixed(SP, 4));
        registerManaged(일루션골렘, null, fixed(AC, -10));
        registerManaged(솔리드캐리지, null, fixed(ER, 15));
        registerManaged(이베이젼, null, fixed(ER, 18));
        registerManaged(STATUS_WISDOM_POTION, null, fixed(SP, 2), fixed(MP_REGEN, 2));
        // The master polymorph ring grants fixed bonuses independent of other sources.
        registerManaged(POLY_RING_MASTER, null, fixed(MAX_HP, 200),
                fixed(PRIMARY_STATS, 0x010101010101L));
        registerManaged(버서크, null, specialResistance(eKind.ALL, 20));
        registerManaged(버닝샷, null, specialResistance(eKind.ALL, 3));
        registerManaged(STATUS_CASHSCROLL, null, fixed(MAX_HP, 50));
        registerManaged(STATUS_CASHSCROLL2, null, fixed(MAX_MP, 40L << 32));
        registerManaged(STATUS_CASHSCROLL3, null, fixed(MELEE_DAMAGE, 3L << 32), fixed(MELEE_HIT, 3), fixed(SP, 3));
        registerManaged(STATUS_CASHSCROLL4, null, fixed(SP, 3), fixed(BASE_MAGIC_HIT, 5), fixed(PVP_REDUCTION, 3L << 32));
        registerManaged(STATUS_CASHSCROLL5, null, fixed(RANGED_DAMAGE, 3), fixed(RANGED_HIT, 5), fixed(PVP_REDUCTION, 3L << 32));
        registerManaged(STATUS_CASHSCROLL6, null, fixed(WEAPON_DAMAGE_RATE, 3), fixed(MELEE_HIT, 5), fixed(PVP_REDUCTION, 3L << 32));
        registerManaged(완력빙수, null, fixed(PRIMARY_STATS, 1), fixed(MELEE_DAMAGE, 3L << 32), fixed(MELEE_HIT, 5));
        registerManaged(민첩빙수, null, fixed(PRIMARY_STATS, 1 << 8), fixed(RANGED_DAMAGE, 3), fixed(RANGED_HIT, 5));
        registerManaged(지식빙수, null, fixed(PRIMARY_STATS, 1 << 16), fixed(MAX_MP, 50L << 32),
                SP, new CSkillEffect.UpperWord(MAGIC_HIT));
        frozen = true;
    }

    private final SkillHandlers.SkillHandler cast;
    private final SkillStopHandlers.StopHandler stop;
    private final PumpStartEffect start;
    private final CSkillEffect[] effects;
    private final boolean instant;

    @FunctionalInterface
    interface PumpStartEffect extends CSkillEffect {
        void apply(L1PcInstance target, int skillId, int duration);

        @Override default void pumpStart(L1PcInstance target, int skillId, int duration) {
            apply(target, skillId, duration);
        }
    }

    @FunctionalInterface
    private interface ContextStartEffect {
        void apply(L1PcInstance target, int skillId, int duration, int option, int gfx, boolean partyMember);
    }

    private record ContextualCast(SkillHandlers.SkillHandler cast, ContextStartEffect start)
            implements SkillHandlers.SkillHandler {
        @Override public SkillHandlers.HandlerResult handle(L1SkillUse su, L1Character target,
                L1Magic magic, int undeadType, Iterator<?> targets) {
            return cast != null ? cast.handle(su, target, magic, undeadType, targets)
                    : SkillHandlers.HandlerResult.NOT_HANDLED;
        }
    }

    private CSkillInfo(SkillHandlers.SkillHandler cast, SkillStopHandlers.StopHandler stop,
            PumpStartEffect start, CSkillEffect[] effects, boolean instant) {
        this.cast = cast;
        this.stop = stop;
        this.start = start;
        this.effects = effects;
        this.instant = instant;
    }

    static CSkillInfo get(int skillId) {
        if (skillId < 0) return null;
        if (skillId >= PAGED_LIMIT) return sparse.get(skillId);
        final CSkillInfo[] page = pages[skillId >>> PAGE_SHIFT];
        return page == null ? null : page[skillId & (PAGE_SIZE - 1)];
    }

    private static void put(int skillId, CSkillInfo info) {
        if (skillId >= PAGED_LIMIT) {
            sparse.put(skillId, info);
        } else {
            final int index = skillId >>> PAGE_SHIFT;
            CSkillInfo[] page = pages[index];
            if (page == null) pages[index] = page = new CSkillInfo[PAGE_SIZE];
            page[skillId & (PAGE_SIZE - 1)] = info;
        }
    }

    static void registerInstant(int skillId, SkillHandlers.SkillHandler cast) {
        registerCast(skillId, cast, true);
    }

    static void registerCast(int skillId, SkillHandlers.SkillHandler cast, boolean instant) {
        if (frozen || skillId < 0 || cast == null) return;
        if (get(skillId) != null) throw new IllegalStateException("Duplicate skill cast " + skillId);
        put(skillId, new CSkillInfo(cast, null, null, null, instant));
    }

    static void registerPumpEnd(int skillId, SkillStopHandlers.StopHandler stop) {
        if (frozen || skillId < 0 || stop == null) return;
        CSkillInfo prior = get(skillId);
        if (prior != null && prior.stop != null) throw new IllegalStateException("Duplicate skill end " + skillId);
        put(skillId, new CSkillInfo(prior == null ? null : prior.cast, stop, null, null,
                prior != null && prior.instant));
    }

    private static void registerManaged(int skillId, PumpStartEffect start, CSkillEffect... effects) {
        registerManagedWithFallback(skillId, start, null, effects);
    }

    private static void registerCube(int id, int icon, int tooltip, CSkillEffect... effects) {
        registerContextual(id, SkillHandlers::castCubeBuff,
                (pc, skillId, duration, iconDuration, gfx, partyMember) ->
                        SkillHandlers.startCubeBuff(pc, skillId, duration, iconDuration, gfx, partyMember, icon, tooltip),
                effects);
    }

    private static void registerContextual(int id, SkillHandlers.SkillHandler cast, ContextStartEffect start,
            CSkillEffect... effects) {
        final CSkillInfo prior = get(id);
        if (prior != null && (prior.cast != null || prior.start != null || prior.effects != null))
            throw new IllegalStateException("Duplicate contextual skill definition " + id);
        put(id, new CSkillInfo(new ContextualCast(cast, start), prior == null ? null : prior.stop, null, effects, false));
    }

    private static void registerClanBuff(int id, CSkillEffect... effects) {
        registerContextual(id, null, PcClanBuffEffects::startScrollFromOwner, effects);
    }

    private static void registerBlessing(int skillId, int amount) {
        registerManagedPlayerBuff(skillId, SkillHandlers::startLegacyBlessing,
                fixed(ARMOR_REDUCTION, amount), fixed(MELEE_DAMAGE, (long) amount << 32), fixed(RANGED_DAMAGE, amount));
    }

    private static void registerManagedPlayerBuff(int skillId, PumpStartEffect start, CSkillEffect... effects) {
        registerManagedWithFallback(skillId, start, SkillHandlers::ignoreNonPlayerBuff, effects);
    }

    private static void registerNotifiedTimer(int skillId, int icon, int priority, int tooltip, int start, int end,
            CSkillEffect... effects) {
        final boolean statModifier = effects.length != 0;
        registerManaged(skillId, (pc, id, duration) ->
                SkillHandlers.startNoticeTimer(pc, id, duration, icon, priority, tooltip, start, end, statModifier), effects);
    }

    private static void registerManagedWithFallback(int skillId, PumpStartEffect start,
            SkillHandlers.SkillHandler fallback, CSkillEffect... effects) {
        CSkillInfo prior = get(skillId);
        if (prior == null || prior.stop == null || prior.cast != null)
            throw new IllegalStateException("Invalid managed skill definition " + skillId);
        put(skillId, new CSkillInfo(fallback, prior.stop, start, effects, false));
    }

    private static CSkillEffect specialResistance(eKind kind, int amount) {
        final int shift = (kind.value() - 1) << 3;
        return fixed(SPECIAL_RESISTANCE, (long) amount << shift);
    }

    private static CSkillEffect fixed(CSkillEffect.Bonus effect, long captured) {
        return new CSkillEffect.Fixed(effect, captured);
    }

    SkillHandlers.HandlerResult activateSkillEffects(L1SkillUse su, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        if (start != null) {
            final int skillId = su.hdSkillId();
            final L1Character recipient = switch (skillId) {
                case CYCLONE, FOCUS_SPRITS -> su.hdUser();
                case LUCIFER, BLOW_ATTACK -> su.hdPlayer();
                case HUNTER_BLESS, 성혈버프 -> target != null ? target : su.hdTarget();
                default -> target;
            };
            if (!(recipient instanceof L1PcInstance pc))
                return cast == null ? SkillHandlers.HandlerResult.NOT_HANDLED
                        : cast.handle(su, target, magic, undeadType, targets);
            int duration = skillId == SHINING_AURA || skillId == SHIELD
                    || skillId == DRESS_DEXTERITY || skillId == DRESS_MIGHTY || skillId == GLOWING_AURA
                    || skillId == PHYSICAL_ENCHANT_STR || skillId == PHYSICAL_ENCHANT_DEX || skillId == BRAVE_AURA
                    || skillId == BLESSED_ARMOR || skillId == IRON_SKIN || skillId == FIRE_SHIELD
                    || skillId == BURNING_WEAPON || skillId == STORM_EYE || skillId == STORM_SHOT
                    || skillId == HURRICANE || skillId == FOCUS_WAVE || skillId == SAND_STORM || skillId == DANCING_BLADES
                    || skillId == EARTH_GUARDIAN || skillId == 격전의주문서
                    || skillId == BLESS_WEAPON || skillId == HOLY_WEAPON || skillId == ENCHANT_WEAPON || skillId == SHADOW_FANG
                    || skillId == BUFF_특별한성장축복 || skillId == BUFF_특별한방어축복 || skillId == BUFF_특별한공격축복
                    ? su.hdGetBuffIconDuration() : SkillHandlers.durationSecForBuff(su, skillId);
            return pc.submitSkillPumpStart(skillId, duration)
                    ? SkillHandlers.HandlerResult.HANDLED : SkillHandlers.HandlerResult.CONTINUE;
        }
        if (cast == null) return SkillHandlers.HandlerResult.NOT_HANDLED;
        return instant ? cast.instant(su, target, magic, undeadType, targets)
                : cast.handle(su, target, magic, undeadType, targets);
    }

    public static void contextStartFromOwner(L1PcInstance target, int skillId, int duration, int option,
            int gfx, boolean partyMember) {
        final CSkillInfo info = get(skillId);
        if (info == null || !(info.cast instanceof ContextualCast contextual)) return;
        try {
            contextual.start().apply(target, skillId, duration, option, gfx, partyMember);
        } catch (RuntimeException | Error failure) {
            if (!target.hasSkillEffect(skillId)) target.removeSkillModifierFromOwner(skillId);
            throw failure;
        }
    }

    public static void pumpStartFromOwner(L1PcInstance target, int skillId, int duration) {
        CSkillInfo info = get(skillId);
        if (info == null || info.start == null) return;
        try {
            info.start.pumpStart(target, skillId, duration);
        } catch (RuntimeException | Error failure) {
            if (!target.hasSkillEffect(skillId)) target.removeSkillModifierFromOwner(skillId);
            throw failure;
        }
    }

    public static boolean isManaged(int skillId) {
        CSkillInfo info = get(skillId);
        return info != null && info.effects != null;
    }

    public static boolean submitSupportBerserkers(L1PcInstance target) {
        // The negative duration selects the support NPC's distinct attack bonuses.
        return target.submitSkillPumpStart(BERSERKERS, -320);
    }

    /** Shared stat application for casts and login restores with distinct timer and packet rules. */
    public static void applyFixedModifierFromOwner(L1PcInstance target, int skillId) {
        final long captured = switch (skillId) {
            case AQUA_PROTECTER, FREEZEENG_ARMOR -> 5;
            case SHINING_ARMOR -> 10;
            case SOLID_CARRIAGE -> 15;
            case DRESS_EVASION -> 18;
            case RESIST_MAGIC -> 10L << 32;
            case GLOWING_AURA -> (5L << 32) | 5;
            case IllUSION_OGRE -> (4L << 32) | 4;
            case ENCHANT_ACURUCY -> 5;
            case BOUNCE_ATTACK -> 6;
            case PATIENCE -> 2;
            case BERSERKERS -> (2L << 32) | 8;
            case WEAKNESS, MOB_WEAKNESS_1 -> (-5L << 32) | Integer.toUnsignedLong(-1);
            case DISEASE, MOB_DISEASE_1, MOB_DISEASE_30 -> Integer.toUnsignedLong(-6);
            case FEAR -> -25;
            case HORROR_OF_DEATH -> 0x030003L;
            case PANIC -> 0x010101010101L;
            case CLEAR_MIND -> 0x00010101;
            case PHYSICAL_ENCHANT_STR -> 5;
            case PHYSICAL_ENCHANT_DEX -> 5 << 8;
            case RESIST_ELEMENTAL -> 0x0a0a0a0a;
            case ELEMENTAL_PROTECTION -> (3L << 32) | switch (target.getElfAttr()) {
                case 1 -> 50L << 16;
                case 2 -> 50L << 24;
                case 4 -> 50L;
                case 8 -> 50L << 8;
                default -> 0L;
            };
            case INSIGHT -> 0x0101010101L;
            case MIRROR_IMAGE, UNCANNY_DODGE -> 30;
            default -> throw new IllegalArgumentException("No fixed modifier for " + skillId);
        };
        target.applySkillModifierFromOwner(skillId, captured);
    }

    void pump(L1Character target, long captured, SkillEffectContainer.SkillModifiers modifiers) {
        for (CSkillEffect effect : effects) {
            if (effect.checkPumpCondition(target)) effect.pump(captured, modifiers);
        }
    }

    boolean hasCast() {
        return start != null || (cast != null && (!(cast instanceof ContextualCast contextual) || contextual.cast() != null));
    }

    SkillStopHandlers.StopHandler stopHandler() { return stop; }

    public static boolean grantSupportAdvance(L1PcInstance target, int seconds) {
        return CIOThreadCallback.getInstance().submitSupportAdvance(target, seconds);
    }
}
