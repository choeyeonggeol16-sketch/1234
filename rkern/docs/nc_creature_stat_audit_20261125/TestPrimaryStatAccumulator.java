package l1j.server.server.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;

/** Exercises the real character and managed-effect pump on Java 21. */
public final class TestPrimaryStatAccumulator {
    private TestPrimaryStatAccumulator() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            testNonSkill();
            testSkill();
            testConcurrentSources();
        } else if (args.length == 1) {
            switch (args[0]) {
                case "non-skill" -> testNonSkill();
                case "skill" -> testSkill();
                case "concurrent" -> testConcurrentSources();
                default -> throw new IllegalArgumentException("Unknown regression mode " + args[0]);
            }
        } else {
            throw new IllegalArgumentException("Expected at most one regression mode");
        }
        System.out.println("PASS primary stat accumulation and effect cancellation");
    }

    private static void testNonSkill() {
        L1Character creature = new L1Character();
        creature.ResetPrimaryStats(20, 20, 20, 20, 20, 20);
        exerciseSource("STR", creature::AddStatBonusStr, creature::GetAddedStatStr, creature::GetTotalStatStr);
        exerciseSource("DEX", creature::AddStatBonusDex, creature::GetAddedStatDex, creature::GetTotalStatDex);
        exerciseSource("INT", creature::AddStatBonusInt, creature::GetAddedStatInt, creature::GetTotalStatInt);
        exerciseSource("WIS", creature::AddStatBonusWis, creature::GetAddedStatWis, creature::GetTotalStatWis);
        exerciseSource("CON", creature::AddStatBonusCon, creature::GetAddedStatCon, creature::GetTotalStatCon);
        exerciseSource("CHA", creature::AddStatBonusCha, creature::GetAddedStatCha, creature::GetTotalStatCha);

        creature.AddStatBonusStr(200);
        creature.SetSkillPrimaryStats(lanes(-30, 0, 0, 0, 0, 0), false);
        equal(97, creature.GetAddedStatStr(), "non-skill source projects before combining with skill");
        equal(117, creature.GetTotalStatStr(), "total still applies the original visible clamp");
        creature.AddStatBonusStr(-200);
        equal(-30, creature.GetAddedStatStr(), "source removal preserves skill");
    }

    private static void exerciseSource(String stat, IntConsumer apply, IntSupplier added, IntSupplier total) {
        apply.accept(120);
        apply.accept(20);
        equal(127, added.getAsInt(), stat + " positive projection");
        equal(127, total.getAsInt(), stat + " final positive clamp");
        apply.accept(-20);
        equal(120, added.getAsInt(), stat + " removes exact positive source");
        apply.accept(-120);

        apply.accept(-120);
        apply.accept(-20);
        equal(-128, added.getAsInt(), stat + " negative projection");
        equal(0, total.getAsInt(), stat + " final negative clamp");
        apply.accept(20);
        equal(-120, added.getAsInt(), stat + " removes exact negative source");
        apply.accept(120);

        apply.accept(-128);
        equal(-128, added.getAsInt(), stat + " exact minimum source");
        apply.accept(128);
        equal(0, added.getAsInt(), stat + " positive inverse of byte minimum");
        equal(20, total.getAsInt(), stat + " base restored");
    }

    private static void testSkill() {
        L1PcInstance creature = new L1PcInstance();
        SkillEffectContainer effects = new SkillEffectContainer(creature);
        effects.applySkillModifier(L1SkillId.N_BUFF_STR, lanes(127, 9, 7, 6, 5, 4));
        equal(127, creature.GetAddedStatStr(), "skill boundary 127");
        effects.applySkillModifier(L1SkillId.DRESS_MIGHTY, lanes(1, 0, 0, 0, 0, 0));
        equal(127, creature.GetAddedStatStr(), "skill boundary 128 does not turn negative");
        effects.applySkillModifier(L1SkillId.DRESS_MIGHTY, lanes(128, 0, 0, 0, 0, 0));
        equal(127, creature.GetAddedStatStr(), "skill boundary 255");
        effects.applySkillModifier(L1SkillId.DRESS_MIGHTY, lanes(129, 0, 0, 0, 0, 0));
        equal(127, creature.GetAddedStatStr(), "skill boundary 256 does not wrap");
        effects.applySkillModifier(L1SkillId.PANIC, lanes(245, 1, 0, 0, 0, 0));
        equal(11, creature.GetAddedStatStr(), "benefits and penalties cancel before clamp");
        effects.applySkillModifier(L1SkillId.HORROR_OF_DEATH, lanes(139, 0, 0, 0, 0, 0));
        equal(-128, creature.GetAddedStatStr(), "skill boundary -128");
        effects.applySkillModifier(L1SkillId.HORROR_OF_DEATH, lanes(140, 0, 0, 0, 0, 0));
        equal(-128, creature.GetAddedStatStr(), "skill boundary -129 does not turn positive");
        equal(8, creature.GetAddedStatDex(), "STR never carries into DEX");
        equal(7, creature.GetAddedStatInt(), "INT preserved");
        equal(6, creature.GetAddedStatWis(), "WIS preserved");
        equal(5, creature.GetAddedStatCon(), "CON preserved");
        equal(4, creature.GetAddedStatCha(), "CHA preserved");
        effects.removeSkillModifier(L1SkillId.DRESS_MIGHTY, false);
        effects.removeSkillModifier(L1SkillId.PANIC, false);
        equal(-13, creature.GetAddedStatStr(), "removal restores the real net value after overflow");
        effects.removeSkillModifier(L1SkillId.HORROR_OF_DEATH, false);
        equal(127, creature.GetAddedStatStr(), "last penalty fully removed");
        effects.removeSkillModifier(L1SkillId.N_BUFF_STR, false);
        equal(0, creature.GetAddedStatStr(), "last modifier clears STR");
        equal(0, creature.GetAddedStatCha(), "last modifier clears CHA");

        effects.applySkillModifier(L1SkillId.PANIC, lanes(255, 0, 0, 0, 0, 0));
        equal(-128, creature.GetAddedStatStr(), "skill boundary -255");
        effects.applySkillModifier(L1SkillId.HORROR_OF_DEATH, 1);
        equal(-128, creature.GetAddedStatStr(), "skill boundary -256 does not wrap");
        effects.removeSkillModifier(L1SkillId.PANIC, false);
        equal(-1, creature.GetAddedStatStr(), "remaining penalty preserved after removal");
        effects.removeSkillModifier(L1SkillId.HORROR_OF_DEATH, false);

        // Actual fixed definitions plus the captured amounts of the managed buff/debuff handlers.
        effects.applySkillModifier(L1SkillId.힘3, 0);
        effects.applySkillModifier(L1SkillId.혈맹버프올스텟, 0);
        effects.applySkillModifier(L1SkillId.N_BUFF_STR, 1);
        effects.applySkillModifier(L1SkillId.PANIC, 0x010101010101L);
        equal(6, creature.GetAddedStatStr(), "actual catalog bonuses and penalty coexist");
        equal(2, creature.GetAddedStatCha(), "actual catalog secondary lane");
        effects.removeSkillModifier(L1SkillId.PANIC, false);
        effects.removeSkillModifier(L1SkillId.혈맹버프올스텟, false);
        equal(4, creature.GetAddedStatStr(), "catalog effects remove independently");
        equal(0, creature.GetAddedStatCha(), "catalog secondary lane restored");
    }

    private static void testConcurrentSources() throws InterruptedException {
        L1Character creature = new L1Character();
        creature.AddStatBonusStr(23);
        creature.AddStatBonusCon(-19);
        creature.AddStatBonusInt(47);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] sources = new Thread[4];
        for (int i = 0; i < sources.length; i++) {
            final int sign = (i & 1) == 0 ? 1 : -1;
            sources[i] = Thread.ofPlatform().start(() -> {
                try {
                    ready.countDown();
                    if (!start.await(3, TimeUnit.SECONDS)) throw new AssertionError("source start timeout");
                    for (int delta = 0; delta < 40_000; delta++) {
                        creature.AddStatBonusStr(3 * sign);
                        creature.AddStatBonusCon(sign);
                        creature.AddStatBonusInt(5 * sign);
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
        }
        boolean started = ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        for (Thread source : sources) source.join();
        if (!started || failure.get() != null) throw new AssertionError("concurrent source task failed", failure.get());
        equal(23, creature.getNonSkillAddedStatStr(), "concurrent STR deltas cancel exactly");
        equal(-19, creature.getNonSkillAddedStatCon(), "concurrent CON deltas cancel exactly");
        equal(47, creature.getNonSkillAddedStatInt(), "concurrent INT deltas cancel exactly");
    }

    private static long lanes(int str, int dex, int intel, int wis, int con, int cha) {
        return (str & 0xffL) | ((dex & 0xffL) << 8) | ((intel & 0xffL) << 16)
                | ((wis & 0xffL) << 24) | ((con & 0xffL) << 32) | ((cha & 0xffL) << 40);
    }

    private static void equal(int expected, int actual, String context) {
        if (expected != actual) throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }
}
