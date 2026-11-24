package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import sun.misc.Unsafe;

/** Exercise the actual production party and generation-tagged world registry. */
public final class TestPartyRoster {
    private static final Unsafe UNSAFE;
    private static final Field DESTROYED;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            DESTROYED = L1PcInstance.class.getDeclaredField("_destroyed");
            DESTROYED.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static final class Fixture extends L1PcInstance {
        @Override public boolean isCrown() { return false; }
        @Override public double getCurrentHpPercent() { return 100; }
        @Override public double getCurrentMpPercent() { return 100; }
        @Override public int getClassNumber() { return 1; }
        @Override public short getMapId() { return 0; }
        @Override public int getLongLocation() { return 0; }
    }
    private static Fixture pc(int id, String name) throws Exception {
        Fixture pc = (Fixture) UNSAFE.allocateInstance(Fixture.class);
        DESTROYED.set(pc, new AtomicBoolean());
        pc.setId(id);
        pc.setName(name);
        L1World.getInstance().storeObject(pc);
        return pc;
    }
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static void roster(CParty party, L1PcInstance... expected) {
        check(party.getNumOfMembers() == expected.length, "member count");
        check(Arrays.equals(party.getMembers(), expected), "ordered roster");
        for (L1PcInstance member : expected)
            check(member.getParty() == party && party.isMember(member), "bidirectional membership");
    }
    private static void cleared(CParty party, L1PcInstance... members) {
        roster(party);
        check(party.isDismissed() && party.getLeader() == null, "dismissed");
        for (L1PcInstance member : members) check(member.getParty() == null, "detached");
    }
    private static Thread thread(CountDownLatch go, AtomicReference<Throwable> failure, Runnable task) {
        return new Thread(() -> {
            try { go.await(); task.run(); }
            catch (Throwable e) { failure.compareAndSet(null, e); }
        });
    }
    public static void main(String[] args) throws Exception {
        int original = Config.MAX_PT;
        Config.MAX_PT = 3;
        Fixture a = pc(950001, "PartyA"), b = pc(950002, "PartyB"),
                c = pc(950003, "PartyC"), d = pc(950004, "PartyD");
        try {
            CParty p = new CParty();
            check(p.addMember(a) && p.addMember(b) && p.addMember(c), "join");
            roster(p, a, b, c);
            check(!p.addMember(b) && !p.addMember(d), "duplicate/full");
            check(!new CParty().addMember(b), "other party");
            check(p.getLeader() == a, "master");
            p.getMembers()[0] = d;
            roster(p, a, b, c);
            p.setPartyMark(b, 2);
            p.setPartyMark(c, 3);
            p.leaveMember(b);
            roster(p, a, c);
            check(b.getParty() == null, "withdrawal");
            check(p.addMember(d), "reuse slot");
            roster(p, a, c, d);
            p.passLeader(c);
            check(p.getLeader() == c, "master transfer");
            p.leaveMember(c);
            cleared(p, a, c, d);
            check(!p.addMember(a), "dismissed party cannot revive");
            CParty arena = new l1j.server.server.shared.Party();
            check(arena.addMember(a) && arena.addMember(b) && arena.addMember(c), "arena join");
            arena.leaveMember(a);
            roster(arena, b, c);
            check(arena.getLeader() == b, "arena succession");
            arena.leaveMember(c);
            cleared(arena, b, c);
            CParty reuse = new CParty();
            check(reuse.addMember(a) && reuse.addMember(b), "smart-id setup");
            L1World.getInstance().removeObject(b);
            Fixture replacement = pc(950002, "PartyB2");
            try {
                check(Arrays.equals(reuse.getMembers(), new L1PcInstance[]{a}), "generation fence");
                check(!reuse.isMember(replacement), "no ID reuse alias");
                reuse.leaveMember(b);
                cleared(reuse, a, b);
                check(replacement.getParty() == null, "replacement unaffected");
            } finally { L1World.getInstance().removeObject(replacement); }
            L1World.getInstance().storeObject(b);
            for (int i = 0; i < 100; i++) {
                CParty left = new CParty(), right = new CParty();
                CountDownLatch go = new CountDownLatch(1);
                AtomicInteger wins = new AtomicInteger();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread t1 = thread(go, failure, () -> { if (left.addMember(a)) wins.incrementAndGet(); });
                Thread t2 = thread(go, failure, () -> { if (right.addMember(a)) wins.incrementAndGet(); });
                t1.start(); t2.start(); go.countDown(); t1.join(3000); t2.join(3000);
                check(!t1.isAlive() && !t2.isAlive() && failure.get() == null, "join concurrency");
                check(wins.get() == 1 && a.getParty() != null, "one party owner");
                CParty owner = a.getParty();
                roster(owner, a);
                owner.leaveMember(a);
                cleared(owner, a);
            }
            for (int i = 0; i < 100; i++) {
                CParty party = new CParty();
                check(party.addMember(a) && party.addMember(b), "dismiss setup");
                CountDownLatch go = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread t1 = thread(go, failure, () -> party.leaveMember(a));
                Thread t2 = thread(go, failure, () -> party.leaveMember(b));
                t1.start(); t2.start(); go.countDown(); t1.join(3000); t2.join(3000);
                check(!t1.isAlive() && !t2.isAlive() && failure.get() == null, "dismiss concurrency");
                cleared(party, a, b);
            }
            ((AtomicBoolean) DESTROYED.get(d)).set(true);
            check(!new CParty().addMember(d) && d.getParty() == null, "logout admission");
            System.out.println("PASS party roster: order, capacity, ownership, generation, leadership, arena, 100 join races, 100 dismissal races");
        } finally {
            for (Fixture member : new Fixture[]{a,b,c,d}) L1World.getInstance().removeObject(member);
            Config.MAX_PT = original;
        }
    }
}
