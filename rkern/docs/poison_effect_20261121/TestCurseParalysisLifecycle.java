package l1j.server.server.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.character.effect.paralysis.CurseParalysis;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Pure in-process lifetime/identity regressions; no live socket required. */
public final class TestCurseParalysisLifecycle {
    private TestCurseParalysisLifecycle() {}

    private static final class Dummy extends L1Character {
        int visuals;
        int lastVisual;
        int skillSets;
        int lastSkill;
        int skillRemoves;

        @Override public void setPoisonEffect(int effect) {
            visuals++;
            lastVisual = effect;
        }

        @Override public void setSkillEffect(int skill, int time) {
            skillSets++;
            lastSkill = skill;
        }

        @Override public void removeSkillEffect(int skill) {
            skillRemoves++;
        }
    }

    private static final class DummyParalysis extends L1Paralysis {
        @Override public int getEffectId() { return 2; }
        @Override public void cure() {}
    }

    private static int assertions;

    private static void check(boolean pass, String name) {
        assertions++;
        if (!pass) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        check(CIOThreadCallback.WheelTask.class.isAssignableFrom(CurseParalysis.class),
                "curse delivers through CIO wheel");

        Dummy target = new Dummy();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        Thread[] threads = new Thread[16];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    if (target.claimParalysis(new DummyParalysis())) successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(3000L);
            check(!thread.isAlive(), "all claim producers terminate");
        }
        check(successes.get() == 1, "one concurrent owner claim");
        L1Paralysis first = target.getParalysis();
        check(first != null, "claim is visible");
        check(!target.clearParalysisIfSame(new DummyParalysis()), "foreign cure cannot erase");
        check(target.clearParalysisIfSame(first), "owner can clear");

        L1Paralysis replacement = new DummyParalysis();
        check(target.claimParalysis(replacement), "replacement claim");
        check(!target.clearParalysisIfSame(first), "stale expiry cannot clear replacement");
        check(target.getParalysis() == replacement, "replacement survives");

        check(target.clearParalysisIfSame(replacement), "release replacement");

        MonsterParalyze rule = new MonsterParalyze();
        rule.paralyze_delay = 5000;
        rule.paralyze_millis = 7000;
        Constructor<CurseParalysis> constructor = CurseParalysis.class
                .getDeclaredConstructor(L1Character.class, MonsterParalyze.class);
        constructor.setAccessible(true);
        CurseParalysis curse = constructor.newInstance(target, rule);
        Method onOwner = CurseParalysis.class.getDeclaredMethod("onOwner");
        onOwner.setAccessible(true);
        Field cureRequested = CurseParalysis.class.getDeclaredField("cureRequested");
        cureRequested.setAccessible(true);

        check(target.claimParalysis(curse), "curse claims target");
        onOwner.invoke(curse);
        check(target.lastVisual == 2, "initial visual preserved");
        check(target.lastSkill == L1SkillId.STATUS_CURSE_PARALYZING,
                "initial curse status preserved");
        check(!curse.isCancelled(), "pending curse remains active");

        cureRequested.setBoolean(curse, true);
        onOwner.invoke(curse);
        check(curse.isCancelled() && target.getParalysis() == null,
                "cure removes current lifetime");
        check(target.lastVisual == 0 && target.skillRemoves == 2,
                "cure removes both statuses and visual");
        int visuals = target.visuals;
        onOwner.invoke(curse);
        check(target.visuals == visuals, "late callback has no effects");

        System.out.println("PASS curse paralysis lifecycle checks=" + assertions);
    }
}
