package l1j.server.server.model.character.skill;

import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

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
        System.out.println("PASS independent HP/AC/MR, refresh, clear, sleep overlap and 411 numeric IDs");
    }

}
