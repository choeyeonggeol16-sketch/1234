package l1j.server.server.model.character.skill;

import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.effect.PcPassiveEffects;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistry;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistryInit;
import l1j.server.server.model.character.skill.core.MagicEffectApplier;
import l1j.server.server.model.character.skill.core.SkillUsabilityPolicy;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.eKind;

import static l1j.server.server.model.character.skill.L1SkillId.*;

/** Standalone Java 21 regression check. Run against the compiled server classes. */
public final class TestSkillEffectLifecycle {
    private record Buff(int id, int hp, int mp, int ac, int mr) {}

    private static final Buff[] BUFFS = {
        new Buff(L1SkillId.PRIDE, 201, 0, 0, 0),
        new Buff(L1SkillId.GIGANTIC, 401, 0, 0, 0),
        new Buff(L1SkillId.ADVANCE_SPIRIT, 198, 60, 0, 0),
        new Buff(L1SkillId.IllUSION_DIAMONDGOLEM, 0, 0, -10, 0),
        new Buff(L1SkillId.SHINING_AURA, 0, 0, -8, 0),
        new Buff(L1SkillId.SHIELD, 0, 0, -2, 0),
        new Buff(L1SkillId.SHADOW_ARMOR, 0, 0, 0, 5),
        new Buff(L1SkillId.SCALES_EARTH_DRAGON, 0, 0, -3, 5)
    };

    private static final class Cast extends L1SkillUse {
        private final int id;
        Cast(int id) { this.id = id; }
        @Override public int hdSkillId() { return id; }
        @Override public int hdGetBuffIconDuration() { return 38; }
    }

    private static final class Player extends L1PcInstance {
        private final Map<Integer, Integer> effects = new HashMap<>();
        @Override public boolean submitSkillPumpStart(int id, int duration) {
            CSkillInfo.pumpStartFromOwner(this, id, duration);
            return true;
        }
        @Override public void killSkillEffectTimer(int id) { effects.remove(id); }
        @Override public void setSkillEffect(int id, int duration) { effects.put(id, duration); }
        @Override public boolean hasSkillEffect(int id) { return effects.containsKey(id); }
        @Override public void removeSkillEffect(int id) {
            if (effects.remove(id) != null) SkillStopHandlers.dispatch(this, id);
        }
    }

    private static void verify(boolean condition, String operation, int skillId) {
        if (!condition) throw new AssertionError(operation + " for " + skillId);
    }

    private static void printState(Player pc, int id, String phase) {
        System.out.printf("%d %s %d %d %d %d %d %d %s%n", id, phase,
                pc.getMaxHp(), pc.getMaxMp(), pc.getAC().getAc(),
                pc.getResistance().getEffectedMrBySkill(),
                pc.getAdvenHp(), pc.getAdvenMp(), pc.effects.get(id));
    }

    public static void main(String[] args) {
        try {
            BuffRestoreRegistryInit.init();
            verifyLifecycle();
            verifyPackedPrimaryStatLanes();
            verifyPackedSpecialResistanceLanes();
            verifyCashAndIceModifierDefinitions();
            verifyModifierRetention();
            verifyUnknownCashScrollPreservesBuff();
            verifyIndependentModifiers();
            verifyLegacyHpAndPotential();
            verifyCompoundBuffs();
            verifyArmorAndSupportBuffs();
            verifyInstantManaEffects();
            verifySharedBuffTimers();
            verifyDebuffSources();
            verifyInventoryAndDressBuffs();
            verifyEvasionBuffs();
            verifyRestoredBuffs();
            verifyPhysicalAndClearMind();
            verifyCommonStatBuffs();
            verifyMeleeBuffs();
            verifyDamageAndSpellPower();
            verifyInventoryHealthAndArmor();
            verifyInventoryResistances();
            verifyLifetimeRefreshAndSleep();
            verifyMovementSpeedSources();
            verifyNpcBindLifetime();
            verifyStunLifetimeAndCastLock();
            verifyFreezeLifetime();
            verifyBraveLifetime();
            verifyOwnerDispatchAndExpiry();
            verifyExternallyGrantedResistanceSources();
            verifyStopOnlyInventory();
            verifyRegistry();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        // The player constructor starts shared server services.
        System.exit(0);
    }

    /** Packed six-byte skill deltas must not carry into neighboring stats. */
    private static void verifyPackedPrimaryStatLanes() {
        final long full = 0x00ff00ff00ffL;
        final long increment = 0x000100010001L;
        final long expected = 0x000000000000L;
        verify(SkillEffectContainer.SkillModifiers.addPrimaryLanes(full, increment) == expected,
                "independent even byte lane wrap", 0);
        final long oddFull = 0xff00ff00ff00L;
        final long oddIncrement = 0x010001000100L;
        verify(SkillEffectContainer.SkillModifiers.addPrimaryLanes(oddFull, oddIncrement) == expected,
                "independent odd byte lane wrap", 0);
        final long mixed = SkillEffectContainer.SkillModifiers.addPrimaryLanes(0x0000000000ffL, 1L);
        verify(mixed == 0L, "STR overflow must not change DEX", 0);
        final SkillEffectContainer.SkillModifiers modifiers = new SkillEffectContainer.SkillModifiers();
        CSkillEffect.Bonus.PRIMARY_STATS.pump(255, modifiers);
        CSkillEffect.Bonus.PRIMARY_STATS.pump(1, modifiers);
        verify(modifiers.primaryStats == 0, "primary stat pump independent lanes", 0);
        CSkillEffect.Bonus.PRIMARY_PENALTY.pump(255L << 8, modifiers);
        CSkillEffect.Bonus.PRIMARY_PENALTY.pump(1L << 8, modifiers);
        verify(modifiers.primaryPenalty == 0, "primary penalty pump independent lanes", 0);
    }

    /** Adjacent resistance and pierce bytes must never contaminate one another. */
    private static void verifyPackedSpecialResistanceLanes() {
        final SkillEffectContainer.SkillModifiers modifiers = new SkillEffectContainer.SkillModifiers();
        CSkillEffect.Bonus.SPECIAL_RESISTANCE.pump(255L, modifiers);
        CSkillEffect.Bonus.SPECIAL_RESISTANCE.pump(1L, modifiers);
        verify(modifiers.specialResistance == 255L,
                "resistance overflow cannot turn into spirit resistance", N_BUFF_STUN);
        CSkillEffect.Bonus.SPECIAL_RESISTANCE.pump(255L << 32, modifiers);
        CSkillEffect.Bonus.SPECIAL_RESISTANCE.pump(1L << 32, modifiers);
        verify(modifiers.specialResistance == (255L | (255L << 32)),
                "all resistance cannot carry into spirit pierce", God_buff);
        CSkillEffect.Bonus.SPIRIT_PIERCE.pump(3L << 32, modifiers);
        CSkillEffect.Bonus.ALL_PIERCE.pump(254L, modifiers);
        CSkillEffect.Bonus.ALL_PIERCE.pump(4L, modifiers);
        verify(modifiers.specialResistance == (255L | (255L << 32) | (3L << 40) | (255L << 48)),
                "pierce types accumulate in isolated saturated lanes", ELEMENTAL_PROTECTION);
    }

    /** The two cash scrolls and two ice-entry captures must stay distinct. */
    private static void verifyCashAndIceModifierDefinitions() {
        final Player target = new Player();
        final SkillEffectContainer.SkillModifiers out = new SkillEffectContainer.SkillModifiers();
        CSkillInfo.get(STATUS_CASHSCROLL4).pump(target, 0, out);
        verify(out.sp == 3 && out.baseMagicHit == 5 && out.pvpReduction == 3,
                "cash scroll 4 retains spell/magic-hit/PvP effects", STATUS_CASHSCROLL4);
        out.initializeSkillMod();
        CSkillInfo.get(STATUS_CASHSCROLL6).pump(target, 0, out);
        verify(out.weaponDamageRate == 3 && out.meleeHit == 5 && out.pvpReduction == 3,
                "cash scroll 6 retains weapon-rate/melee-hit/PvP effects", STATUS_CASHSCROLL6);
        out.initializeSkillMod();
        CSkillInfo.get(지식빙수).pump(target, 2L, out);
        verify(out.sp == 2 && out.magicHit == 0 && out.mp == 50
                        && ((out.primaryStats >>> 16) & 0xffL) == 1L,
                "knowledge ice item capture applies spell power", 지식빙수);
        out.initializeSkillMod();
        CSkillInfo.get(지식빙수).pump(target, 2L << 32, out);
        verify(out.sp == 0 && out.magicHit == 2 && out.mp == 50
                        && ((out.primaryStats >>> 16) & 0xffL) == 1L,
                "knowledge ice alternate capture applies magic hit", 지식빙수);
    }

    /** The final removed modifier must release its retained backing state. */
    private static void verifyModifierRetention() throws ReflectiveOperationException {
        L1PcInstance pc = new L1PcInstance();
        SkillEffectContainer effects = new SkillEffectContainer(pc);
        var stateField = SkillEffectContainer.class.getDeclaredField("skillModifiers");
        stateField.setAccessible(true);
        effects.applySkillModifier(PRIDE, 18);
        effects.applySkillModifier(GIGANTIC, 24);
        verify(stateField.get(effects) != null, "active modifier storage retained", PRIDE);
        verify(effects.removeSkillModifier(PRIDE, false), "first modifier removal", PRIDE);
        verify(stateField.get(effects) != null, "other modifier storage preserved", GIGANTIC);
        verify(effects.removeSkillModifier(GIGANTIC, false), "final modifier removal", GIGANTIC);
        verify(stateField.get(effects) == null, "final modifier storage released", GIGANTIC);
    }

    private static void verifyUnknownCashScrollPreservesBuff() throws ReflectiveOperationException {
        Player pc = new Player();
        pc.setSkillEffect(STATUS_CASHSCROLL3, 60_000);
        Class<?> cash = Class.forName("l1j.server.server.model.item.function.CashScrollPotionEffects");
        var method = cash.getDeclaredMethod("useCashScroll", L1PcInstance.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(null, pc, -1);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw new AssertionError("unknown cash scroll threw", failure.getCause());
        }
        verify(pc.hasSkillEffect(STATUS_CASHSCROLL3),
                "unrecognized cash scroll must preserve old effect", STATUS_CASHSCROLL3);
    }

    private static void verifyLifecycle() {
        for (int id : new int[]{-1, 0, 32767, 32768, Integer.MAX_VALUE}) {
            verify(SkillHandlers.dispatch(new Cast(id), null, null, 0, null) == SkillHandlers.HD_NOT_HANDLED,
                    "missing start", id);
            verify(!SkillStopHandlers.hasHandler(id), "missing end", id);
        }
        SkillStopHandlers.register(32767, (target, id) -> SkillStopHandlers.StopResult.HANDLED);
        verify(!SkillStopHandlers.hasHandler(32767), "frozen registration", 32767);

        int stopOnly = L1SkillId.STATUS_BLUE_POTION;
        verify(SkillHandlers.dispatch(new Cast(stopOnly), null, null, 0, null) == SkillHandlers.HD_NOT_HANDLED,
                "stop-only missing start", stopOnly);
        verify(SkillStopHandlers.dispatch(null, stopOnly) == SkillStopHandlers.StopResult.HANDLED,
                "stop-only end", stopOnly);

        for (Buff buff : BUFFS) {
            int id = buff.id();
            Player pc = new Player();
            pc.setLevel(81);
            pc.addBaseMaxHp((short) 991);
            pc.addBaseMaxMp(301);
            pc.getAC().setAc(-96);
            pc.getResistance().addMr(19);
            int hp = pc.getMaxHp(), mp = pc.getMaxMp();
            int mr = pc.getResistance().getEffectedMrBySkill();
            Cast cast = new Cast(id);
            L1Character npc = new L1Character();
            verify(SkillHandlers.dispatch(cast, npc, null, 0, null) == SkillHandlers.HD_NOT_HANDLED,
                    "non-player start", id);
            System.out.println("non-player end " + id + " " + SkillStopHandlers.dispatch(npc, id));

            verify(SkillHandlers.dispatch(cast, pc, null, 0, null) == SkillHandlers.HD_HANDLED, "start", id);
            verify(pc.effects.get(id) == 38_000, "effect duration", id);
            printState(pc, id, "start");
            int buffedHp = pc.getMaxHp(), buffedMp = pc.getMaxMp(), buffedAc = pc.getAC().getAc();
            int buffedMr = pc.getResistance().getEffectedMrBySkill();
            verify(buffedHp == hp + buff.hp() && buffedMp == mp + buff.mp() && buffedAc == -96 + buff.ac()
                    && buffedMr == mr + buff.mr(), "applied stats", id);

            verify(SkillHandlers.dispatch(cast, pc, null, 0, null) == SkillHandlers.HD_HANDLED, "recast", id);
            verify(pc.getMaxHp() == buffedHp && pc.getMaxMp() == buffedMp && pc.getAC().getAc() == buffedAc
                    && pc.getResistance().getEffectedMrBySkill() == buffedMr, "recast stats", id);
            printState(pc, id, "recast");
            pc.addBaseMaxHp((short) 37);
            pc.addBaseMaxMp(7);
            pc.removeSkillEffect(id);
            verify(!pc.hasSkillEffect(id), "removed effect", id);
            verify(pc.getMaxHp() == hp + 37 && pc.getMaxMp() == mp + 7 && pc.getAC().getAc() == -96
                    && pc.getResistance().getEffectedMrBySkill() == mr, "restored stats", id);
            printState(pc, id, "stop");
        }
    }

    private static void verifyInventoryAndDressBuffs() {
        Player pc = new Player();
        pc.getAbility().addAddedStr(6);
        pc.getResistance().addFire(124);
        int[] ids = {N_BUFF_STR, N_BUFF_DEX, N_BUFF_INT, N_BUFF_WIS, DRESS_MIGHTY, DRESS_DEXTERITY,
                N_BUFF_WATER, N_BUFF_WIND, N_BUFF_EARTH, N_BUFF_FIRE, N_BUFF_ALL_RESIST};
        for (int id : ids) {
            verify(CSkillInfo.isManaged(id), "managed registration", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast", id);
            verify(pc.effects.get(id) == 38_000, "timer after recast", id);
        }

        pc.validateSkillModifiersFromOwner();
        verify(pc.getAbility().getAddedStr() == 10 && pc.getAbility().getAddedDex() == 4
                && pc.getAbility().getAddedInt() == 1 && pc.getAbility().getAddedWis() == 1,
                "independent primary stats", N_BUFF_STR);
        verify(pc.getResistance().getFire() == 127 && pc.getResistance().getWater() == 10
                && pc.getResistance().getWind() == 10 && pc.getResistance().getEarth() == 10,
                "stacked elemental resistance", N_BUFF_ALL_RESIST);
        pc.getAbility().addAddedStr(2);
        pc.removeSkillEffect(N_BUFF_STR);
        verify(pc.getAbility().getAddedStr() == 11, "preserves other STR sources", N_BUFF_STR);
        pc.removeSkillEffect(N_BUFF_ALL_RESIST);
        verify(pc.getResistance().getFire() == 127 && pc.getResistance().getWater() == 5
                && pc.getResistance().getWind() == 5 && pc.getResistance().getEarth() == 5,
                "preserves single-element sources", N_BUFF_ALL_RESIST);

        for (int id : ids) pc.removeSkillEffect(id);
        verify(pc.getAbility().getAddedStr() == 8 && pc.getAbility().getAddedDex() == 0
                && pc.getAbility().getAddedInt() == 0 && pc.getAbility().getAddedWis() == 0,
                "cleared primary stats", DRESS_MIGHTY);
        verify(pc.getResistance().getFire() == 124 && pc.getResistance().getWater() == 0
                && pc.getResistance().getWind() == 0 && pc.getResistance().getEarth() == 0,
                "cleared elemental stats", N_BUFF_FIRE);
    }

    private static void verifyEvasionBuffs() {
        Player pc = new Player();
        pc.addEffectedER(7);
        pc.addDg(4);
        pc.setCharacterER(5);
        pc.setCharacterDG(8);
        int baselineEr = pc.getTotalER(), baselineDg = pc.getDg();
        int[] ids = {DRESS_EVASION, AQUA_PROTECTER, SOLID_CARRIAGE, SCALES_WATER_DRAGON, SCALES_RINDVIOR_DRAGON};
        for (int id : ids) {
            verify(CSkillInfo.isManaged(id), "managed registration", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast", id);
            verify(pc.hasSkillEffect(id), "timer after recast", id);
        }
        verify(pc.getTotalER() == baselineEr + 48 && pc.getDg() == baselineDg + 10,
                "independent evasion modifiers", SCALES_WATER_DRAGON);
        pc.removeSkillEffect(SCALES_WATER_DRAGON);
        verify(pc.getTotalER() == baselineEr + 38 && pc.getDg() == baselineDg + 10,
                "removes only water evasion", SCALES_WATER_DRAGON);
        for (int id : ids) pc.removeSkillEffect(id);
        verify(pc.getTotalER() == baselineEr && pc.getDg() == baselineDg
                && pc.getEffectedER() == 7, "removal preserves equipment and other evasion", SCALES_RINDVIOR_DRAGON);
    }

    private static void verifyRestoredBuffs() {
        Player pc = new Player();
        pc.addEffectedER(7);
        pc.getResistance().addMr(30);
        int baseMr = pc.getResistance().getMr();
        int[] ids = {FREEZEENG_ARMOR, SHINING_ARMOR, RESIST_MAGIC};
        int[] icons = new int[39];
        for (int id : ids) {
            verify(CSkillInfo.isManaged(id), "restored managed registration", id);
            BuffRestoreRegistry.find(id).restore(pc, id, 0, 38, icons);
            pc.setSkillEffect(id, 38_000);
        }
        verify(pc.getEffectedER() == 22 && pc.getResistance().getMr() == baseMr + 10
                && pc.getResistance().getAddedMr() == 30, "restore installs distinct sources", RESIST_MAGIC);
        for (int id : ids) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast after restore", id);
        }
        verify(pc.getEffectedER() == 22 && pc.getResistance().getMr() == baseMr + 10,
                "recast replaces restored sources", RESIST_MAGIC);
        pc.removeSkillEffect(FREEZEENG_ARMOR);
        verify(pc.getEffectedER() == 17, "one restored evasion source remains", SHINING_ARMOR);
        pc.removeSkillEffect(SHINING_ARMOR);
        pc.removeSkillEffect(RESIST_MAGIC);
        verify(pc.getEffectedER() == 7 && pc.getResistance().getMr() == baseMr,
                "restored sources leave baseline intact", RESIST_MAGIC);
    }

    private static void verifyPhysicalAndClearMind() {
        Player pc = new Player();
        pc.getAbility().addAddedStr(7);
        pc.getAbility().addAddedDex(8);
        pc.getAbility().addAddedInt(9);
        final int[] ids = {CLEAR_MIND, PHYSICAL_ENCHANT_STR, PHYSICAL_ENCHANT_DEX};
        for (int id : ids) verify(CSkillInfo.isManaged(id), "managed primary registration", id);
        BuffRestoreRegistry.find(CLEAR_MIND).restore(pc, CLEAR_MIND, 0, 38, new int[39]);
        pc.setSkillEffect(CLEAR_MIND, 38_000);
        CSkillInfo.pumpStartFromOwner(pc, DRESS_MIGHTY, 38);
        CSkillInfo.pumpStartFromOwner(pc, DRESS_DEXTERITY, 38);
        for (int id : ids) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "cast", id);
            CSkillInfo.pumpStartFromOwner(pc, id, 38);
        }
        verify(!pc.hasSkillEffect(DRESS_MIGHTY) && !pc.hasSkillEffect(DRESS_DEXTERITY),
                "physical enchant replaces dress", PHYSICAL_ENCHANT_STR);
        verify(pc.getAbility().getAddedStr() == 13 && pc.getAbility().getAddedDex() == 14
                && pc.getAbility().getAddedInt() == 10, "recast does not stack", CLEAR_MIND);
        pc.removeSkillEffect(CLEAR_MIND);
        verify(pc.getAbility().getAddedStr() == 12 && pc.getAbility().getAddedDex() == 13
                && pc.getAbility().getAddedInt() == 9, "clear mind expires independently", CLEAR_MIND);
        pc.removeSkillEffect(PHYSICAL_ENCHANT_STR);
        pc.removeSkillEffect(PHYSICAL_ENCHANT_DEX);
        verify(pc.getAbility().getAddedStr() == 7 && pc.getAbility().getAddedDex() == 8
                && pc.getAbility().getAddedInt() == 9, "equipment sources retained", PHYSICAL_ENCHANT_DEX);
    }

    private static void verifyCommonStatBuffs() {
        Player pc = new Player();
        pc.getAbility().addAddedCon(6);
        pc.getAbility().addAddedWis(4);
        pc.getResistance().addEarth(19);
        pc.getResistance().addFire(23);
        pc.getResistance().addSpecialPierce(eKind.SPIRIT, 126);
        pc.setElfAttr(1);
        pc.addDg(7);
        for (int id : new int[]{INSIGHT, RESIST_ELEMENTAL, ELEMENTAL_PROTECTION}) {
            verify(CSkillInfo.isManaged(id), "common stat registration", id);
            BuffRestoreRegistry.find(id).restore(pc, id, 0, 38, new int[39]);
            pc.setSkillEffect(id, 38_000);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "cast after restore", id);
        }
        verify(pc.getAbility().getAddedCon() == 7 && pc.getAbility().getAddedWis() == 5,
                "insight does not stack", INSIGHT);
        verify(pc.getResistance().getEarth() == 79 && pc.getResistance().getFire() == 33
                && pc.getResistance().getSpecialPierce(eKind.SPIRIT) == 127,
                "element and pierce capture", ELEMENTAL_PROTECTION);
        pc.setElfAttr(2);
        pc.removeSkillEffect(ELEMENTAL_PROTECTION);
        verify(pc.getResistance().getEarth() == 29 && pc.getResistance().getFire() == 33
                && pc.getResistance().getSpecialPierce(eKind.SPIRIT) == 126,
                "end removes original element even after attribute changes", ELEMENTAL_PROTECTION);
        pc.removeSkillEffect(RESIST_ELEMENTAL);
        pc.removeSkillEffect(INSIGHT);
        verify(pc.getAbility().getAddedCon() == 6 && pc.getAbility().getAddedWis() == 4
                && pc.getResistance().getEarth() == 19 && pc.getResistance().getFire() == 23,
                "common stats preserve legacy sources", INSIGHT);
        int baselineDg = pc.getDg();
        for (int id : new int[]{MIRROR_IMAGE, UNCANNY_DODGE}) {
            verify(CSkillInfo.isManaged(id), "managed dodge registration", id);
            CSkillInfo.pumpStartFromOwner(pc, id, 38);
            CSkillInfo.pumpStartFromOwner(pc, id, 38);
        }
        verify(pc.getDg() == baselineDg + 60, "independent dodge sources", MIRROR_IMAGE);
        pc.removeSkillEffect(MIRROR_IMAGE);
        verify(pc.getDg() == baselineDg + 30, "other dodge source stays", UNCANNY_DODGE);
        pc.removeSkillEffect(UNCANNY_DODGE);
        verify(pc.getDg() == baselineDg, "base dodge restored", UNCANNY_DODGE);
    }

    private static void verifyMeleeBuffs() {
        Player pc = new Player();
        pc.addHitup(113);
        pc.addDmgup(120);
        BuffRestoreRegistry.find(ENCHANT_ACURUCY).restore(pc, ENCHANT_ACURUCY, 0, 38, new int[39]);
        pc.setSkillEffect(ENCHANT_ACURUCY, 38_000);
        for (int id : new int[]{GLOWING_AURA, IllUSION_OGRE, ENCHANT_ACURUCY}) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start or recast melee modifier", id);
        }
        verify(pc.getHitup() == 127 && pc.getDmgup() == 127, "combined capped melee stats", GLOWING_AURA);
        pc.removeSkillEffect(GLOWING_AURA);
        verify(pc.getHitup() == 122 && pc.getDmgup() == 124, "other hit and damage sources retained", IllUSION_OGRE);
        pc.addHitup(7);
        pc.removeSkillEffect(IllUSION_OGRE);
        verify(pc.getHitup() == 125 && pc.getDmgup() == 120, "existing hit change retained", ENCHANT_ACURUCY);
        pc.removeSkillEffect(ENCHANT_ACURUCY);
        verify(pc.getHitup() == 120 && pc.getDmgup() == 120, "melee baseline retained", ENCHANT_ACURUCY);
    }

    private static void verifyDamageAndSpellPower() {
        Player pc = new Player();
        pc.addDmgup(4);
        pc.addBowDmgup(6);
        pc.getAbility().addSp(3);
        int sp = pc.getAbility().getSp();
        for (int id : new int[]{N_BUFF_DMG, N_BUFF_SP, IllUSION_LICH}) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast", id);
        }
        verify(pc.getDmgup() == 5 && pc.getBowDmgup() == 7 && pc.getAbility().getSp() == sp + 5,
                "weapon damage and spell power", N_BUFF_DMG);
        pc.removeSkillEffect(N_BUFF_SP);
        verify(pc.getAbility().getSp() == sp + 4, "keeps illusion SP", IllUSION_LICH);
        pc.removeSkillEffect(IllUSION_LICH);
        pc.removeSkillEffect(N_BUFF_DMG);
        verify(pc.getDmgup() == 4 && pc.getBowDmgup() == 6 && pc.getAbility().getSp() == sp,
                "preserves baseline weapon damage and SP", N_BUFF_DMG);
    }

    private static void verifyInventoryHealthAndArmor() {
        Player pc = new Player();
        pc.addBaseMaxHp((short) 991);
        pc.addBaseMaxMp(301);
        pc.addWeightReduction(7);
        pc.addDamageReductionByArmor(4);
        int hp = pc.getMaxHp(), mp = pc.getMaxMp();
        for (int id : new int[]{N_BUFF_HPMP, N_BUFF_REDUCT}) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast", id);
        }
        verify(pc.getMaxHp() == hp + 50 && pc.getMaxMp() == mp + 50 && pc.getWeightReduction() == 10
                && pc.getDamageReductionByArmor() == 5, "inventory health and reduction sources", N_BUFF_HPMP);
        pc.addWeightReduction(2);
        pc.setCurrentHp(pc.getMaxHp());
        pc.setCurrentMp(pc.getMaxMp());
        pc.removeSkillEffect(N_BUFF_HPMP);
        verify(pc.getMaxHp() == hp && pc.getMaxMp() == mp && pc.getCurrentHp() == hp && pc.getCurrentMp() == mp
                && pc.getWeightReduction() == 9 && pc.getDamageReductionByArmor() == 5,
                "health removal preserves weight baseline and unrelated reduction", N_BUFF_HPMP);
        pc.removeSkillEffect(N_BUFF_REDUCT);
        verify(pc.getDamageReductionByArmor() == 4, "preserves baseline reduction", N_BUFF_REDUCT);
    }

    private static void verifyInventoryResistances() {
        Player pc = new Player();
        var resistance = pc.getResistance();
        resistance.addcalcPcDefense(14);
        resistance.addPVPweaponTotalDamage(127);
        resistance.addSpecialResistance(eKind.ABILITY, 126);
        resistance.addSpecialResistance(eKind.SPIRIT, 9);
        for (int id : new int[]{N_BUFF_PVP_DMG, N_BUFF_STUN, N_BUFF_HOLD}) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "start", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "recast", id);
        }
        verify(resistance.getcalcPcDefense() == 15 && resistance.getPVPweaponTotalDamage() == 127
                && resistance.getSpecialResistance(eKind.ABILITY) == 127
                && resistance.getSpecialResistance(eKind.SPIRIT) == 11, "separate resistance types", N_BUFF_STUN);
        pc.removeSkillEffect(N_BUFF_STUN);
        verify(resistance.getSpecialResistance(eKind.ABILITY) == 126
                && resistance.getSpecialResistance(eKind.SPIRIT) == 11, "one removal preserves another", N_BUFF_HOLD);
        pc.removeSkillEffect(N_BUFF_HOLD);
        pc.removeSkillEffect(N_BUFF_PVP_DMG);
        verify(resistance.getSpecialResistance(eKind.SPIRIT) == 9 && resistance.getcalcPcDefense() == 14
                && resistance.getPVPweaponTotalDamage() == 127, "preserves baseline after removal", N_BUFF_PVP_DMG);
    }

    private static void verifyIndependentModifiers() {
        Player pc = new Player();
        pc.setLevel(81);
        pc.addBaseMaxHp((short) 991);
        pc.addBaseMaxMp(301);
        pc.addMaxHp(100);
        int baseHp = pc.getMaxHp();
        for (int id : new int[]{L1SkillId.PRIDE, L1SkillId.GIGANTIC, L1SkillId.ADVANCE_SPIRIT})
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED, "stack", id);
        verify(pc.getMaxHp() == baseHp + 201 + 401 + 198, "independent HP sources", L1SkillId.PRIDE);
        pc.removeSkillEffect(L1SkillId.PRIDE);
        pc.setCurrentHp(pc.getMaxHp());
        int hp = pc.getCurrentHp();
        pc.validateSkillModifiersFromOwner();
        pc.validateSkillModifiersFromOwner();
        verify(pc.getMaxHp() == baseHp + 401 + 198 && pc.getCurrentHp() == hp, "recompute and legacy HP isolation", L1SkillId.PRIDE);
        pc.removeSkillEffect(L1SkillId.GIGANTIC);
        pc.removeSkillEffect(L1SkillId.ADVANCE_SPIRIT);
        verify(pc.getMaxHp() == baseHp && pc.getCurrentHp() == baseHp, "clamp after removal", L1SkillId.ADVANCE_SPIRIT);

        pc.getAC().setAc(-96);
        int shield = L1SkillId.SHIELD;
        SkillHandlers.dispatch(new Cast(shield), pc, null, 0, null);
        pc.getAC().setAc(-104);
        verify(pc.getAC().getAc() == -106, "retain equipment AC change", shield);
        pc.removeSkillEffect(shield);
        verify(pc.getAC().getAc() == -104, "restore only managed AC", shield);
        int scales = L1SkillId.SCALES_EARTH_DRAGON;
        pc.getResistance().addMr(250);
        SkillHandlers.dispatch(new Cast(scales), pc, null, 0, null);
        pc.removeSkillEffect(scales);
        verify(pc.getResistance().getAddedMr() == 250, "capped MR preserves other sources", scales);
    }

    private static void verifySharedBuffTimers() {
        Player pc = new Player();
        for (int id : new int[] {MAJESTY, ABSOLUTE_BARRIER, ABSOLUTE_BLADE, REDUCTION_ARMOR, MOEBIUS,
                N_BUFF_WATER_DMG, N_BUFF_WIND_DMG, N_BUFF_EARTH_DMG, N_BUFF_FIRE_DMG}) {
            verify(CSkillInfo.isManaged(id), "shared timer registration", id);
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED
                    && pc.effects.get(id) == 38_000, "shared timer duration", id);
            pc.removeSkillEffect(id);
            verify(!pc.hasSkillEffect(id), "shared timer removal", id);
        }
    }

    private static void verifyDebuffSources() {
        Player pc = new Player();
        final int ac = pc.getAC().getAc();
        pc.addDg(30);
        pc.addHitup(12);
        pc.addDmgup(15);
        pc.submitSkillPumpStart(DISEASE, -64);
        SkillHandlers.dispatch(new Cast(WEAKNESS), pc, null, 0, null);
        SkillHandlers.dispatch(new Cast(MOB_DISEASE_1), pc, null, 0, null);
        verify(pc.getAC().getAc() == ac + 24 && pc.getHitup() == 5 && pc.getDmgup() == 4,
                "boss Disease affects damage and regular Disease affects accuracy", DISEASE);
        pc.removeSkillEffect(DISEASE);
        verify(pc.getAC().getAc() == ac + 12 && pc.getHitup() == 5 && pc.getDmgup() == 10,
                "boss Disease restores its original damage modifier", DISEASE);
        pc.removeSkillEffect(MOB_DISEASE_1);
        pc.removeSkillEffect(WEAKNESS);
        verify(pc.getAC().getAc() == ac && pc.getHitup() == 12 && pc.getDmgup() == 15,
                "all combat debuff sources retire", WEAKNESS);

        BuffRestoreRegistry.find(FEAR).restore(pc, FEAR, 0, 38, new int[39]);
        pc.setSkillEffect(FEAR, 38_000);
        verify(pc.getDg() == 5, "restored Fear applies dodge reduction", FEAR);
        pc.removeSkillEffect(FEAR);
        verify(pc.getDg() == 30, "Fear removal preserves original dodge", FEAR);

        pc.getAbility().addAddedStr(15);
        pc.getAbility().addAddedInt(12);
        pc.getAbility().addAddedCha(9);
        SkillHandlers.dispatch(new Cast(N_BUFF_STR), pc, null, 0, null);
        SkillHandlers.dispatch(new Cast(PANIC), pc, null, 0, null);
        BuffRestoreRegistry.find(HORROR_OF_DEATH).restore(pc, HORROR_OF_DEATH, 0, 38, new int[39]);
        pc.setSkillEffect(HORROR_OF_DEATH, 38_000);
        verify(pc.getAbility().getAddedStr() == 5 && pc.getAbility().getAddedInt() == 1
                && pc.getAbility().getAddedCha() == 8, "mixed stat bonuses and penalties", HORROR_OF_DEATH);
        pc.removeSkillEffect(HORROR_OF_DEATH);
        verify(pc.getAbility().getAddedStr() == 15 && pc.getAbility().getAddedInt() == 11,
                "restore removes all ten points only once", HORROR_OF_DEATH);
        pc.removeSkillEffect(PANIC);
        verify(pc.getAbility().getAddedStr() == 16 && pc.getAbility().getAddedInt() == 12
                && pc.getAbility().getAddedCha() == 9, "removing Panic preserves other stat sources", PANIC);
    }

    private static void verifyInstantManaEffects() {
        Player pc = new Player();
        pc.addBaseMaxMp(100);
        pc.setCurrentMp(10);
        verify(SkillHandlers.dispatch(new Cast(BODY_TO_MIND), pc, null, 0, null) == SkillHandlers.HD_HANDLED
                && pc.getCurrentMp() == 12, "Body to Mind restores two MP", BODY_TO_MIND);
        verify(SkillHandlers.dispatch(new Cast(BLOODY_SOUL), pc, null, 0, null) == SkillHandlers.HD_HANDLED
                && pc.getCurrentMp() == 27, "Bloody Soul restores fifteen MP", BLOODY_SOUL);
        pc.setCurrentMp(pc.getMaxMp() - 1);
        SkillHandlers.dispatch(new Cast(BLOODY_SOUL), pc, null, 0, null);
        verify(pc.getCurrentMp() == pc.getMaxMp() && !pc.hasSkillEffect(BLOODY_SOUL),
                "instant recovery clamps without a timer", BLOODY_SOUL);
    }

    private static void verifyArmorAndSupportBuffs() {
        Player pc = new Player();
        int ac = pc.getAC().getAc();
        pc.addDamageReductionByArmor(9);
        pc.addHitup(12);
        pc.addDmgup(17);
        for (int id : new int[]{IRON_SKIN, FIRE_SHIELD, BLESSED_ARMOR, BOUNCE_ATTACK, PATIENCE}) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "managed armor combat buff", id);
        }
        verify(pc.getAC().getAc() == ac - 17 && pc.getHitup() == 18 && pc.getDamageReductionByArmor() == 11,
                "independent armor and combat bonuses", BLESSED_ARMOR);
        pc.removeSkillEffect(FIRE_SHIELD);
        verify(pc.getAC().getAc() == ac - 13, "removing shield preserves remaining armor", FIRE_SHIELD);
        pc.removeSkillEffect(PATIENCE);
        verify(pc.getDamageReductionByArmor() == 9, "Patience leaves base reduction", PATIENCE);
        pc.removeSkillEffect(BOUNCE_ATTACK);
        verify(pc.getHitup() == 12, "Bounce Attack leaves base accuracy", BOUNCE_ATTACK);

        BuffRestoreRegistry.find(GUARD_BREAK).restore(pc, GUARD_BREAK, 0, 38, new int[39]);
        pc.setSkillEffect(GUARD_BREAK, 38_000);
        verify(pc.getAC().getAc() == ac + 2, "restore keeps its original guard break strength", GUARD_BREAK);
        pc.removeSkillEffect(GUARD_BREAK);
        verify(pc.getAC().getAc() == ac - 13, "restore removes its original strength", GUARD_BREAK);

        verify(CSkillInfo.submitSupportBerserkers(pc), "support berserkers is accepted", BERSERKERS);
        verify(pc.getHitup() == 14 && pc.getDmgup() == 22 && pc.effects.get(BERSERKERS) == 320_000,
                "support keeps its own values and duration", BERSERKERS);
        pc.removeSkillEffect(BERSERKERS);
        verify(pc.getHitup() == 12 && pc.getDmgup() == 17 && pc.getAC().getAc() == ac - 13,
                "support removes exactly its original amounts", BERSERKERS);
    }

    private static void verifyCompoundBuffs() {
        Player pc = new Player();
        pc.addBaseMaxHp((short) 900);
        pc.addBaseMaxMp(200);
        pc.addHitup(13);
        pc.getResistance().addSpecialResistance(eKind.ABILITY, 125);
        final int hp = pc.getMaxHp(), mp = pc.getMaxMp(), ac = pc.getAC().getAc();
        verify(SkillHandlers.dispatch(new Cast(God_buff), pc, null, 0, null) == SkillHandlers.HD_HANDLED
                && pc.getHitup() == 16, "cast applies God buff accuracy", God_buff);
        BuffRestoreRegistry.find(God_buff).restore(pc, God_buff, 0, 38, new int[39]);
        verify(pc.getHitup() == 13 && pc.getMaxHp() == hp + 20 && pc.getMaxMp() == mp + 13
                && pc.getAC().getAc() == ac - 2 && pc.getResistance().getSpecialResistance(eKind.SPIRIT) == 10,
                "login replaces God buff with its own recorded bonuses", God_buff);
        SkillHandlers.dispatch(new Cast(SCALES_FIRE_DRAGON), pc, null, 0, null);
        SkillHandlers.dispatch(new Cast(SCALES_FIRE_DRAGON), pc, null, 0, null);
        verify(pc.getHitup() == 18 && pc.getResistance().getSpecialResistance(eKind.ABILITY) == 127
                && pc.getResistance().getSpecialResistance(eKind.DRAGON_SPELL) == 5
                && pc.getResistance().getSpecialResistance(eKind.FEAR) == 5, "fire scales recast preserves caps", SCALES_FIRE_DRAGON);
        pc.removeSkillEffect(God_buff);
        verify(pc.getHitup() == 18 && pc.getMaxHp() == hp && pc.getMaxMp() == mp && pc.getAC().getAc() == ac
                && pc.getResistance().getSpecialResistance(eKind.SPIRIT) == 0, "restored buff cannot subtract ungranted hit", God_buff);
        pc.removeSkillEffect(SCALES_FIRE_DRAGON);
        verify(pc.getHitup() == 13 && pc.getResistance().getSpecialResistance(eKind.ABILITY) == 125
                && pc.getResistance().getSpecialResistance(eKind.FEAR) == 0, "fire scales removes its own sources", SCALES_FIRE_DRAGON);
        verify(SkillHandlers.dispatch(new Cast(BRAVE_AURA), pc, null, 0, null) == SkillHandlers.HD_HANDLED
                && pc.effects.get(BRAVE_AURA) == 38_000, "visual aura retains duration", BRAVE_AURA);
    }

    private static void verifyLegacyHpAndPotential() {
        Player pc = new Player();
        pc.setLevel(81);
        pc.addBaseMaxHp((short) 1000);
        pc.addBaseMaxMp(200);
        int hp = pc.getMaxHp(), mp = pc.getMaxMp(), sp = pc.getAbility().getSp();
        int mr = pc.getResistance().getMr(), er = pc.getEffectedER(), dg = pc.getDg();
        pc.setSkillEffect(프라이드, 0);
        pc.setSkillEffect(기간틱, 0);
        pc.setSkillEffect(포텐셜, 128_000);
        PcPassiveEffects.apply(pc, PcPassiveEffects.Kind.프라이드);
        PcPassiveEffects.apply(pc, PcPassiveEffects.Kind.기간틱);
        pc.applyPotentialModifierFromOwner(150, 40, 9, 8, 7, 6);
        verify(pc.getMaxHp() == hp + 750 && pc.getMaxMp() == mp + 40, "three independent HP sources", 포텐셜);
        pc.setCurrentHp(pc.getMaxHp());
        pc.setLevel(85);
        PcPassiveEffects.refreshLegacyScaledHp(pc, PcPassiveEffects.Kind.프라이드);
        PcPassiveEffects.refreshLegacyScaledHp(pc, PcPassiveEffects.Kind.기간틱);
        verify(pc.getMaxHp() == hp + 780 && pc.getCurrentHp() == hp + 750,
                "refresh does not clamp HP to an intermediate value", 프라이드);
        pc.removeSkillEffect(기간틱);
        verify(pc.getMaxHp() == hp + 360 && pc.getResistance().getMr() == mr + 9
                && pc.getAbility().getSp() == sp + 8 && pc.getEffectedER() == er + 7 && pc.getDg() == dg + 6,
                "Potential survives Gigantic removal", 기간틱);
        pc.removeSkillEffect(포텐셜);
        verify(pc.getMaxHp() == hp + 210 && pc.getMaxMp() == mp && pc.getResistance().getMr() == mr
                && pc.getAbility().getSp() == sp && pc.getEffectedER() == er && pc.getDg() == dg,
                "Potential removes all and only its captured bonuses", 포텐셜);
        pc.removeSkillEffect(프라이드);
        verify(pc.getMaxHp() == hp, "legacy HP returns to baseline", 프라이드);
    }

    private static void verifyLifetimeRefreshAndSleep() {
        L1PcInstance pc = new L1PcInstance();
        pc.addBaseMaxHp((short) 500);
        int initial = pc.getMaxHp();
        int pride = L1SkillId.PRIDE;
        pc.skillEffectSetFromOwner(pride, 0);
        pc.applySkillModifierFromOwner(pride, 100);
        pc.skillEffectSetFromOwner(pride, 0);
        verify(pc.hasSkillEffect(pride) && pc.getMaxHp() == initial + 100, "permanent refresh", pride);
        pc.skillEffectRemoveFromOwner(pride, false);
        verify(!pc.hasSkillEffect(pride) && pc.getMaxHp() == initial, "timer-only removal", pride);
        pc.skillEffectSetFromOwner(pride, 0);
        pc.applySkillModifierFromOwner(pride, 100);
        pc.skillEffectClearFromOwner();
        verify(pc.getMaxHp() == initial, "clear removes only applied modifiers", pride);
        pc.skillEffectSetFromOwner(포텐셜, 0);
        int sp = pc.getAbility().getSp();
        pc.applyPotentialModifierFromOwner(100, 0, 0, 2, 0, 0);
        pc.applyPotentialModifierFromOwner(100, 0, 0, 5, 0, 0);
        verify(pc.hasSkillEffect(포텐셜) && pc.getMaxHp() == initial + 100 && pc.getAbility().getSp() == sp + 5,
                "fresh container accepts changed auxiliary captures", 포텐셜);
        pc.skillEffectClearFromOwner();
        verify(!pc.hasSkillEffect(포텐셜) && pc.getMaxHp() == initial && pc.getAbility().getSp() == sp,
                "second clear retires fresh state too", 포텐셜);

        L1Character target = new L1Character();
        target.setSkillEffect(L1SkillId.PHANTASM, 0);
        target.setSkillEffect(L1SkillId.FOG_OF_SLEEPING, 0);
        target.killSkillEffectTimer(L1SkillId.PHANTASM);
        SkillStopHandlers.dispatch(target, L1SkillId.PHANTASM);
        verify(target.isSleeped(), "overlapping sleep survives first end", L1SkillId.PHANTASM);
        target.killSkillEffectTimer(L1SkillId.FOG_OF_SLEEPING);
        SkillStopHandlers.dispatch(target, L1SkillId.FOG_OF_SLEEPING);
        verify(!target.isSleeped(), "last sleep releases movement", L1SkillId.FOG_OF_SLEEPING);
    }

    private static void verifyMovementSpeedSources() {
        L1Character target = new L1Character();
        target.setMoveSpeed(2);
        target.setSkillEffect(MOB_SLOW_1, 0);
        target.setSkillEffect(MOB_SLOW_18, 0);
        target.removeSkillEffect(MOB_SLOW_1);
        verify(target.getMoveSpeed() == 2, "one monster slow survives", MOB_SLOW_18);
        target.removeSkillEffect(MOB_SLOW_18);
        verify(target.getMoveSpeed() == 0, "last monster slow releases speed", MOB_SLOW_18);

        target.setMoveSpeed(1);
        target.setSkillEffect(HASTE, 0);
        target.setSkillEffect(STATUS_HASTE, 0);
        SkillStopHandlers.dispatch(target, SLOW);
        verify(target.getMoveSpeed() == 1, "stale slow does not reset haste", SLOW);
        target.removeSkillEffect(HASTE);
        verify(target.getMoveSpeed() == 1, "potion haste survives spell expiry", STATUS_HASTE);
        target.removeSkillEffect(STATUS_HASTE);
        verify(target.getMoveSpeed() == 0, "last haste releases speed", STATUS_HASTE);

        L1Skills hasteTemplate = new L1Skills();
        hasteTemplate.setBuffDuration(38);
        L1SkillUse haste = new L1SkillUse() {
            @Override public int hdSkillId() { return HASTE; }
            @Override public int hdGetBuffIconDuration() { return 38; }
            @Override public L1Skills exSkill() { return hasteTemplate; }
        };
        target.setMoveSpeed(2);
        target.setSkillEffect(MOB_SLOW_18, 0);
        verify(SkillHandlers.dispatch(haste, target, null, 0, null) == SkillHandlers.HD_HANDLED,
                "haste dispels the slow", HASTE);
        MagicEffectApplier.INSTANCE.applySkillEffect(haste, target, false);
        verify(target.getMoveSpeed() == 0 && !target.hasSkillEffect(HASTE) && !target.hasSkillEffect(MOB_SLOW_18),
                "dispelling monster slow does not install haste", HASTE);

        Player pc = new Player();
        pc.addHasteItemEquipped(1);
        pc.setMoveSpeed(1);
        pc.setSkillEffect(GREATER_HASTE, 0);
        pc.removeSkillEffect(GREATER_HASTE);
        verify(pc.getMoveSpeed() == 1 && !MovementSpeedEffects.appliedOnCast(pc, HASTE),
                "equipment haste survives and prevents a phantom spell timer", HASTE);
        pc.addHasteItemEquipped(-1);
        pc.setMoveSpeed(2);
        pc.setSkillEffect(SLOW, 0);
        pc.setSkillEffect(MOB_SLOW_1, 0);
        verify(!MovementSpeedEffects.appliedOnCast(pc, HASTE), "haste cancellation has no haste timer", HASTE);
        verify(MovementSpeedEffects.cancelSlowWithPotion(pc) && pc.getMoveSpeed() == 2 && !pc.hasSkillEffect(SLOW),
                "potion cures one slow source", SLOW);
        verify(MovementSpeedEffects.cancelSlowWithPotion(pc) && pc.getMoveSpeed() == 0 && !pc.hasSkillEffect(MOB_SLOW_1),
                "potion cures last monster slow", MOB_SLOW_1);
        verify(!MovementSpeedEffects.appliedOnCast(pc, SLOW), "slow cancellation has no slow timer", SLOW);
    }

    private static void verifyNpcBindLifetime() {
        L1NpcInstance npc = new L1NpcInstance(null);
        npc.skillEffectSetFromOwner(THUNDER_GRAB, 0);
        verify(!npc.발묶임상태(), "visual spell timer alone does not bind", THUNDER_GRAB);
        npc.skillEffectSetFromOwner(STATUS_FREEZE, 0);
        npc.skillEffectSetFromOwner(POWERRIP, 0);
        npc.skillEffectSetFromOwner(DESPERADO, 0);
        npc.skillEffectRemoveFromOwner(THUNDER_GRAB, true);
        verify(npc.발묶임상태(), "effect timer ends before the actual bind", THUNDER_GRAB);
        npc.setParalyzed(true);
        npc.skillEffectRemoveFromOwner(STATUS_FREEZE, true);
        verify(npc.발묶임상태() && npc.isParalyzed(), "independent bind and paralysis survive", STATUS_FREEZE);
        npc.skillEffectRemoveFromOwner(POWERRIP, true);
        verify(npc.발묶임상태(), "Desperado survives Power Rip expiry", DESPERADO);
        npc.skillEffectRemoveFromOwner(DESPERADO, false);
        verify(!npc.발묶임상태() && npc.isParalyzed(), "timer-only removal releases bind alone", DESPERADO);
        npc.skillEffectSetFromOwner(STATUS_FREEZE, 0);
        npc.skillEffectClearFromOwner();
        verify(!npc.발묶임상태() && npc.isParalyzed(), "clear does not leave a phantom bind", STATUS_FREEZE);
    }

    private static void verifyStunLifetimeAndCastLock() {
        L1NpcInstance npc = new L1NpcInstance(null);
        npc.skillEffectSetFromOwner(SHOCK_STUN, 0);
        npc.skillEffectSetFromOwner(EMPIRE, 0);
        npc.skillEffectRemoveFromOwner(SHOCK_STUN, true);
        verify(npc.isParalyzed(), "NPC remains stunned by Empire", EMPIRE);
        npc.skillEffectRemoveFromOwner(EMPIRE, true);
        verify(!npc.isParalyzed(), "final stun releases NPC", EMPIRE);
        npc.setParalyzed(true);
        npc.skillEffectSetFromOwner(Maeno_STUN, 0);
        npc.skillEffectRemoveFromOwner(Maeno_STUN, true);
        verify(npc.isParalyzed(), "stun release preserves independent paralysis", Maeno_STUN);

        L1PcInstance pc = new L1PcInstance();
        L1SkillUse cast = new L1SkillUse() {
            @Override public L1PcInstance hdPlayer() { return pc; }
            @Override public L1Character hdUser() { return pc; }
        };
        pc.skillEffectSetFromOwner(ANTA_MESSAGE_7, 0);
        verify(!SkillUsabilityPolicy.INSTANCE.canCastNormal(cast)
                && !SkillUsabilityPolicy.INSTANCE.canCastSpellScroll(cast), "stun blocks spells and scrolls", ANTA_MESSAGE_7);
        pc.skillEffectRemoveFromOwner(ANTA_MESSAGE_7, false);
        pc.skillEffectSetFromOwner(STATUS_FREEZE, 0);
        verify(!SkillUsabilityPolicy.INSTANCE.canCastSpellScroll(cast), "bind blocks scroll use", STATUS_FREEZE);
        pc.skillEffectClearFromOwner();
        verify(SkillUsabilityPolicy.INSTANCE.canCastSpellScroll(cast), "clear releases cast lock", STATUS_FREEZE);
    }

    private static void verifyFreezeLifetime() {
        L1NpcInstance npc = new L1NpcInstance(null);
        npc.skillEffectSetFromOwner(ICE_LANCE, 0);
        npc.skillEffectSetFromOwner(EARTH_BIND, 0);
        npc.skillEffectSetFromOwner(EMPIRE, 0);
        npc.skillEffectRemoveFromOwner(ICE_LANCE, true);
        verify(npc.isParalyzed() && ControlSkillEffects.hasSkillFreeze(npc, ICE_LANCE),
                "Earth Bind keeps freeze when Ice Lance ends", EARTH_BIND);
        npc.skillEffectRemoveFromOwner(EARTH_BIND, true);
        verify(npc.isParalyzed() && !ControlSkillEffects.hasSkillFreeze(npc, EARTH_BIND),
                "stun still holds after final freeze", EMPIRE);
        npc.skillEffectRemoveFromOwner(EMPIRE, true);
        verify(!npc.isParalyzed(), "all timed restrictions gone", EMPIRE);
        verify(SkillHandlers.dispatch(new Cast(ICE_LANCE), npc, null, 0, null)
                == SkillHandlers.HD_HANDLED_WITHOUT_TIMER, "rejected freeze skips only timer stage", ICE_LANCE);
        L1PcInstance pc = new L1PcInstance();
        pc.setCurseParalyzed(true);
        pc.setPoisonParalyzed(true);
        pc.setCurseParalyzed(false);
        verify(pc.isParalyzed(), "ending curse preserves poison paralysis", STATUS_POISON_PARALYZED);
        pc.setPoisonParalyzed(false);
        verify(!pc.isParalyzed(), "final independent paralysis releases player", STATUS_POISON_PARALYZED);
    }

    private static void verifyBraveLifetime() {
        Player pc = new Player();
        pc.setStatusBraveMode(3);
        pc.setSkillEffect(STATUS_BRAVE, 0);
        pc.setSkillEffect(HOLY_WALK, 0);
        pc.setBraveSpeed(4);
        pc.setSkillEffect(FOCUS_WAVE, 0);
        pc.set_focus_wave_level(1);
        BraveSpeedEffects.cancelTimer(pc, FOCUS_WAVE);
        verify(pc.get_focus_wave_level() == 0 && pc.getBraveSpeed() == 4,
                "visual-only effect does not erase Holy Walk", FOCUS_WAVE);
        pc.removeSkillEffect(HOLY_WALK);
        verify(pc.getBraveSpeed() == 3, "captured Dark Horse potion mode survives", STATUS_BRAVE);
        pc.removeSkillEffect(STATUS_BRAVE);
        verify(pc.getBraveSpeed() == 0, "final brave source releases speed", STATUS_BRAVE);
    }

    private static void verifyOwnerDispatchAndExpiry() throws InterruptedException {
        L1PcInstance pc = new L1PcInstance();
        int shield = L1SkillId.SHIELD;
        int ac = pc.getAC().getAc();
        verify(SkillHandlers.dispatch(new Cast(shield), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                "controller accepted cast", shield);
        verify(pc.hasSkillEffect(shield) && pc.getAC().getAc() == ac - 2, "controller applied cast and timer", shield);
        pc.killSkillEffectTimer(shield);
        verify(!pc.hasSkillEffect(shield) && pc.getAC().getAc() == ac, "controller retired cast", shield);

        int wis = pc.getAbility().getAddedWis();
        int water = pc.getResistance().getWater();
        SkillHandlers.dispatch(new Cast(N_BUFF_WIS), pc, null, 0, null);
        SkillHandlers.dispatch(new Cast(N_BUFF_WATER), pc, null, 0, null);
        verify(pc.hasSkillEffect(N_BUFF_WIS) && pc.getAbility().getAddedWis() == wis + 1
                && pc.hasSkillEffect(N_BUFF_WATER) && pc.getResistance().getWater() == water + 5,
                "controller applies both stat groups", N_BUFF_WIS);
        pc.skillEffectRemoveFromOwner(N_BUFF_WIS, false);
        verify(!pc.hasSkillEffect(N_BUFF_WIS) && pc.getAbility().getAddedWis() == wis
                && pc.getResistance().getWater() == water + 5, "timer-only removal", N_BUFF_WIS);
        pc.skillEffectClearFromOwner();
        verify(!pc.hasSkillEffect(N_BUFF_WATER) && pc.getResistance().getWater() == water,
                "clear removes elemental modifier", N_BUFF_WATER);

        int er = pc.getEffectedER();
        SkillHandlers.dispatch(new Cast(SCALES_WATER_DRAGON), pc, null, 0, null);
        SkillHandlers.dispatch(new Cast(SCALES_WATER_DRAGON), pc, null, 0, null);
        verify(pc.hasSkillEffect(SCALES_WATER_DRAGON) && pc.getEffectedER() == er + 10,
                "controller refreshes water scales without stacking", SCALES_WATER_DRAGON);
        pc.skillEffectClearFromOwner();
        verify(pc.getEffectedER() == er, "clear removes evasion modifier", SCALES_WATER_DRAGON);

        // Exercise the real character-owned timer container, not only the mock map.
        final int baseStr = pc.getAbility().getAddedStr();
        final int baseDex = pc.getAbility().getAddedDex();
        final int baseCon = pc.getAbility().getAddedCon();
        final int baseInt = pc.getAbility().getAddedInt();
        final int baseWis = pc.getAbility().getAddedWis();
        final int baseEarth = pc.getResistance().getEarth();
        final int basePierce = pc.getResistance().getSpecialPierce(eKind.SPIRIT);
        final int baseDg = pc.getDg();
        pc.setElfAttr(1);
        final int[] owned = {CLEAR_MIND, PHYSICAL_ENCHANT_STR, PHYSICAL_ENCHANT_DEX,
                RESIST_ELEMENTAL, ELEMENTAL_PROTECTION, INSIGHT, MIRROR_IMAGE, UNCANNY_DODGE};
        for (int id : owned) {
            verify(SkillHandlers.dispatch(new Cast(id), pc, null, 0, null) == SkillHandlers.HD_HANDLED,
                    "owner accepts stat effect", id);
            verify(pc.hasSkillEffect(id), "owner installs timer", id);
        }
        verify(pc.getAbility().getAddedStr() == baseStr + 7
                && pc.getAbility().getAddedDex() == baseDex + 7
                && pc.getAbility().getAddedCon() == baseCon + 1
                && pc.getAbility().getAddedInt() == baseInt + 2
                && pc.getAbility().getAddedWis() == baseWis + 1
                && pc.getResistance().getEarth() == baseEarth + 60
                && pc.getDg() == baseDg + 60, "owner applies eight independent effects", INSIGHT);
        pc.skillEffectRemoveFromOwner(INSIGHT, false);
        verify(!pc.hasSkillEffect(INSIGHT) && pc.getAbility().getAddedCon() == baseCon
                && pc.getAbility().getAddedStr() == baseStr + 6,
                "timer-only insight removal", INSIGHT);
        pc.setElfAttr(2);
        pc.skillEffectClearFromOwner();
        for (int id : owned) verify(!pc.hasSkillEffect(id), "owner clears timer", id);
        verify(pc.getAbility().getAddedStr() == baseStr && pc.getAbility().getAddedDex() == baseDex
                && pc.getAbility().getAddedCon() == baseCon && pc.getAbility().getAddedInt() == baseInt
                && pc.getAbility().getAddedWis() == baseWis && pc.getResistance().getEarth() == baseEarth
                && pc.getResistance().getSpecialPierce(eKind.SPIRIT) == basePierce
                && pc.getDg() == baseDg, "owner clear preserves other sources", ELEMENTAL_PROTECTION);

        L1PcInstance expiring = new L1PcInstance();
        expiring.addBaseMaxHp((short) 600);
        int pride = L1SkillId.PRIDE;
        int baseline = expiring.getMaxHp();
        SkillEffectContainer effects = new SkillEffectContainer(expiring);
        effects.put(pride, 1_000);
        effects.applySkillModifier(pride, 87);
        int generation = effects.tryArmHeartbeat();
        Thread.sleep(1_050);
        verify(effects.onHeartbeat(generation) == 0L && !effects.contains(pride)
                && expiring.getMaxHp() == baseline, "heartbeat retires applied source", pride);
        verify(effects.onHeartbeat(generation) == 0L, "stale generation ignored", pride);
        int retiredGeneration = effects.clear();
        SkillEffectContainer replacement = new SkillEffectContainer(expiring, retiredGeneration);
        replacement.put(pride, 2_000);
        int replacementGeneration = replacement.tryArmHeartbeat();
        verify(replacementGeneration != generation && replacement.onHeartbeat(generation) == 0L
                && replacement.contains(pride), "retired heartbeat cannot tick a new container", pride);
    }

    /** Item-granted sources must coexist and retire independently at resistance cap. */
    private static void verifyExternallyGrantedResistanceSources() {
        Player pc = new Player();
        final var resistance = pc.getResistance();
        resistance.addSpecialResistance(eKind.ALL, 126);
        verify(CSkillInfo.isManaged(버서크) && CSkillInfo.isManaged(버닝샷),
                "item-granted special resistance registered", 버서크);
        pc.applySkillModifierFromOwner(버서크, 0);
        pc.setSkillEffect(버서크, 38_000);
        pc.applySkillModifierFromOwner(버닝샷, 0);
        pc.setSkillEffect(버닝샷, 38_000);
        verify(resistance.getSpecialResistance(eKind.ALL) == 127,
                "overlapping item buffs respect visible cap", 버닝샷);
        resistance.addSpecialResistance(eKind.ALL, -126);
        verify(resistance.getSpecialResistance(eKind.ALL) == 23,
                "both item sources remain separately counted", 버서크);
        pc.removeSkillEffect(버서크);
        verify(resistance.getSpecialResistance(eKind.ALL) == 3,
                "retiring Berserk preserves Burning Shot", 버서크);
        pc.removeSkillEffect(버닝샷);
        verify(resistance.getSpecialResistance(eKind.ALL) == 0,
                "retiring last item source restores baseline", 버닝샷);
        pc.removeSkillEffect(버닝샷);
        verify(resistance.getSpecialResistance(eKind.ALL) == 0,
                "duplicate item expiry cannot underflow", 버닝샷);
    }

    /** Numeric-only stop definitions are verified against the updated ledger. */
    private static void verifyStopOnlyInventory() {
        final int[] stopOnlyIds = {
            2, 32, 91, 105, 112, 136, 780, 985, 1000,
            1001, 1002, 1003, 1004, 1005, 1006, 1016, 1565, 1566,
            1607, 2754, 2794, 5256, 5263, 5285, 5300, 5307, 5984,
            7239, 7241, 7791, 7792, 7793, 7794, 7795, 8382, 8463,
            9999, 12120, 12132, 12133, 12134, 12135, 12136, 13001, 13002,
            13003, 15644, 20075, 20076, 20077, 20079, 20082, 22017, 23442,
            23445, 24444, 24445, 25133, 25155, 30010, 40007, 50009, 55441,
            55662, 55664, 60001, 61614, 61615, 61616, 72388, 80007, 80008,
            80009, 80012, 131313, 191918, 778844, 979744
        };
        verify(stopOnlyIds.length == 78, "stop-only numeric inventory size", 0);
        for (int id : stopOnlyIds) {
            final CSkillInfo info = CSkillInfo.get(id);
            verify(info != null && !info.hasCast() && info.stopHandler() != null,
                    "stop-only ID retains distinct end-only registration", id);
        }
    }

    private static void verifyRegistry() throws ReflectiveOperationException {
        var sparse = CSkillInfo.class.getDeclaredField("sparse");
        sparse.setAccessible(true);
        @SuppressWarnings("unchecked")
        var high = (it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<CSkillInfo>) sparse.get(null);
        int casts = 0, stops = 0, both = 0;
        for (int id = 0; id < 32768; id++) {
            CSkillInfo info = CSkillInfo.get(id);
            if (info == null) continue;
            if (info.hasCast()) casts++;
            if (info.stopHandler() != null) stops++;
            if (info.hasCast() && info.stopHandler() != null) both++;
        }
        for (CSkillInfo info : high.values()) {
            if (info.hasCast()) casts++;
            if (info.stopHandler() != null) stops++;
            if (info.hasCast() && info.stopHandler() != null) both++;
        }
        verify(casts == 310 && stops == 299 && both == 198, "numeric registry coverage", 0);
        System.out.println("PASS stats, resistances, refresh, clear, movement and bind overlap, 411 numeric IDs");
    }

}
