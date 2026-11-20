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
}
