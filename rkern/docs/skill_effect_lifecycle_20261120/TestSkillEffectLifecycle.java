package l1j.server.server.model.character.skill;

import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistry;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistryInit;
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
        System.out.printf("%d %s %d %d %d %d %d %d %d %s%n", id, phase,
                pc.getMaxHp(), pc.getMaxMp(), pc.getAC().getAc(),
                pc.getResistance().getEffectedMrBySkill(), pc.getMagicBuffHp(),
                pc.getAdvenHp(), pc.getAdvenMp(), pc.effects.get(id));
    }

    public static void main(String[] args) {
        try {
            verifyLifecycle();
            verifyIndependentModifiers();
            verifyInventoryAndDressBuffs();
            verifyEvasionBuffs();
            verifyRestoredBuffs();
            verifyPhysicalAndClearMind();
            verifyMeleeBuffs();
            verifyDamageAndSpellPower();
            verifyInventoryHealthAndArmor();
            verifyInventoryResistances();
            verifyLifetimeRefreshAndSleep();
            verifyOwnerDispatchAndExpiry();
            verifyRegistry();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        // The player constructor starts shared server services.
        System.exit(0);
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
        BuffRestoreRegistryInit.init();
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
        pc.setMagicBuffHp(100);
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
        verify(pc.getMaxHp() == baseHp + 401 + 198 && pc.getCurrentHp() == hp
                && pc.getMagicBuffHp() == 100, "recompute and legacy HP isolation", L1SkillId.PRIDE);
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

        L1Character target = new L1Character();
        target.setSkillEffect(L1SkillId.PHANTASM, 0);
        target.setSkillEffect(L1SkillId.FOG_OF_SLEEPING, 0);
        target.setSleeped(true);
        target.killSkillEffectTimer(L1SkillId.PHANTASM);
        SkillStopHandlers.dispatch(target, L1SkillId.PHANTASM);
        verify(target.isSleeped(), "overlapping sleep survives first end", L1SkillId.PHANTASM);
        target.killSkillEffectTimer(L1SkillId.FOG_OF_SLEEPING);
        SkillStopHandlers.dispatch(target, L1SkillId.FOG_OF_SLEEPING);
        verify(!target.isSleeped(), "last sleep releases movement", L1SkillId.FOG_OF_SLEEPING);
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
        verify(casts == 310 && stops == 297 && both == 196, "numeric registry coverage", 0);
        System.out.println("PASS independent stats and resistances, refresh, clear, sleep overlap and 411 numeric IDs");
    }

}
