package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import sun.misc.Unsafe;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.npc.ai.CNpcSpecialAction;

public final class TestNpcLifecycleRaces {
    static final class Monster extends L1MonsterInstance {
        Monster() { super(null); }
        @Override public int getNpcId() { return 1; }
        @Override public int getMaxHp() { return 100; }
        @Override public int getMaxMp() { return 100; }
        @Override public void setHate(L1Character attacker, int damage) { }
        @Override public void onNpcAI() { }
    }

    static void check(boolean b, String why) {
        if (!b) throw new AssertionError(why);
    }
    static Field field(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    static void race(Runnable a, Runnable b) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread x = new Thread(() -> run(start, a, failure));
        Thread y = new Thread(() -> run(start, b, failure));
        x.start(); y.start(); start.countDown();
        x.join(5000); y.join(5000);
        check(!x.isAlive() && !y.isAlive(), "race threads must finish");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    static void run(CountDownLatch start, Runnable r, AtomicReference<Throwable> failure) {
        try { start.await(); r.run(); }
        catch (Throwable e) { failure.compareAndSet(null, e); }
    }

    public static void main(String[] args) throws Exception {
        Unsafe u = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Monster npc = (Monster) u.allocateInstance(Monster.class);
        Monster attacker = (Monster) u.allocateInstance(Monster.class);
        Field hp = field(L1Character.class, "_currentHp");
        Field dead = field(L1Character.class, "_isDead");

        // The existing deferred cancel flag and generation are retired by
        // the actual resurrection state transition, not a synthetic model.
        dead.setBoolean(npc, true);
        npc.npcActionRequestDeferredCancel();
        int oldEpoch = npc.npcActionAdmissionEpoch();
        int oldGeneration = npc.npcActionGeneration();
        npc._mobSkillUseCount = new int[] { 2, 3 };
        npc.setDead(false);
        check(!npc.npcActionConsumeDeferredCancel(), "old death cancel survived");
        check(npc.npcActionAdmissionEpoch() != oldEpoch, "old action epoch survived");
        check(npc.npcActionGeneration() != oldGeneration, "old timer generation survived");
        check(npc._mobSkillUseCount[0] == 0 && npc._mobSkillUseCount[1] == 0,
                "old skill-use counts survived");
        System.out.println("PASS old death cancel, admission epoch, timer generation and skill reset");

        // Race a return commit's shared monitor against the actual HP CAS.
        // Use the production primitive return marker; owner/path/teleport
        // integration is covered separately by the movement suite.
        for (int i = 0; i < 100; i++) {
            hp.setInt(npc, 20);
            npc._scriptMoveTargetId = 0;
            AtomicInteger result = new AtomicInteger();
            race(() -> {
                synchronized (npc) {
                    if (npc.getCurrentHp() > 0) {
                        npc._scriptMoveTargetId = -1;
                        npc.applyNpcHpDelta(100);
                    }
                }
            }, () -> result.set(npc.applyNpcHpDelta(-30)));
            check((npc.getCurrentHp() == 100 && result.get() == -1)
                    || (npc.getCurrentHp() == 0 && result.get() == 0),
                    "damage crossed the forced return commit");
        }
        npc._scriptMoveTargetId = 0;
        hp.setInt(npc, 100);
        System.out.println("PASS 100 return-immunity/HP-CAS contention rounds");

        {
            Field drained = field(L1NpcInstance.class, "_drainedMana");
            for (int i = 0; i < 100; i++) {
                drained.setInt(npc, 0);
                npc.setCurrentMp(15);
                AtomicInteger credit = new AtomicInteger();
                AtomicInteger cost = new AtomicInteger();
                race(() -> {
                    synchronized (npc) {
                        if (npc.getCurrentMp() >= 10) {
                            npc.setCurrentMp(npc.getCurrentMp() - 10);
                            cost.set(10);
                        }
                    }
                }, () -> credit.set(npc.ReceiveManaDrain(attacker, 10)));
                check(cost.get() + credit.get() + npc.getCurrentMp() == 15,
                        "mana cost/credit mismatch");
            }
        }
        System.out.println("PASS 100 MP debit / mana-credit contention rounds");

        // A dead summon with unsettled inventory must not be deleted by
        // outside callers or the stale lifetime deadline. A failed retry
        // stays death-processing and advances the retry generation.
        L1SummonInstance summon = (L1SummonInstance) u.allocateInstance(L1SummonInstance.class);
        dead.setBoolean(summon, true);
        field(L1NpcInstance.class, "_deathProcessing").setBoolean(summon, true);
        L1Inventory inv = new L1Inventory();
        L1ItemInstance item = (L1ItemInstance) u.allocateInstance(L1ItemInstance.class);
        item.setId(997001);
        item.setCount(1);
        inv.getItems().add(item);
        summon.setInventory(inv);
        summon.deleteMe();
        summon.TimerExpired(1, 1);
        summon.returnToNature();
        check(!summon.isDestroyed() && summon.getInventory().getItems().size() == 1,
                "pending death inventory was discarded");
        // Intentionally unregistered fixture: no real world timer is admitted.
        Method finish = L1SummonInstance.class.getDeclaredMethod("finishSummonDeath");
        finish.setAccessible(true);
        summon.setInventory(null);
        finish.invoke(summon);
        Field retry = field(L1SummonInstance.class, "deathReturnGeneration");
        check(retry.getInt(summon) != 0 && summon.isDeathProcessing() && !summon.isDestroyed(),
                "failed settlement did not request retry");
        int gen = retry.getInt(summon);
        summon.TimerExpired(0x53554D52, gen - 1);
        check(retry.getInt(summon) == gen, "stale retry generation was accepted");
        System.out.println("PASS summon pending-inventory delete guard and retry generation");
    }
}
