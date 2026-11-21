package l1j.server.server.model.Instance;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import l1j.server.server.Account;

/** Exercise the production PC's primitive 15-minute heartbeat deadline. */
public final class TestEinhasadHeartbeat {
    private TestEinhasadHeartbeat() {}

    private static final class FakeAccount extends Account {
        int recoveryCalls;
        int balance;

        @Override
        public synchronized int recoverBlessOfAin(long units, int ceiling) {
            if (units != 1L || ceiling != 2_000_000)
                throw new AssertionError("legacy Einhasad recovery units/cap");
            recoveryCalls++;
            if (balance < ceiling) balance++;
            return balance;
        }

        @Override
        public int getBlessOfAin() {
            return balance;
        }
    }

    private static final class TestPc extends L1PcInstance {
        private final FakeAccount fakeAccount = new FakeAccount();

        @Override
        public Account getAccount() {
            return fakeAccount;
        }
    }

    private static int checks;
    private static void check(boolean valid, String name) {
        checks++;
        if (!valid) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        final TestPc pc = new TestPc();
        final Field due = L1PcInstance.class.getDeclaredField("einhasadDeadlineNanos");
        due.setAccessible(true);
        check(due.getLong(pc) == 0L, "inactive initially");
        pc.einhasadHeartbeatFromController();
        check(pc.fakeAccount.recoveryCalls == 0, "inactive heartbeat does not recover");

        pc.startEinhasadTimer();
        final long initialDue = due.getLong(pc);
        check(initialDue != 0L, "safety zone starts deadline");
        pc.startEinhasadTimer();  // Called every step during normal movement.
        check(due.getLong(pc) == initialDue, "movement does not extend deadline");

        pc.einhasadHeartbeatFromController();
        check(pc.fakeAccount.recoveryCalls == 0, "no early recovery");

        due.setLong(pc, System.nanoTime() - 1L); // Force a due-time signal.
        pc.einhasadHeartbeatFromController();
        check(pc.fakeAccount.recoveryCalls == 1 && pc.fakeAccount.balance == 1,
                "first 15-minute recovery uses existing account method");
        long advanced = due.getLong(pc);
        check(advanced != 0L && advanced - System.nanoTime() > 0L, "next deadline armed");
        pc.einhasadHeartbeatFromController();
        check(pc.fakeAccount.recoveryCalls == 1, "same heartbeat cannot recover twice");

        pc.stopEinhasadTimer();
        check(due.getLong(pc) == 0L, "leaving safe zone cancels deadline");
        pc.einhasadHeartbeatFromController();
        check(pc.fakeAccount.recoveryCalls == 1, "cancelled heartbeat is inert");
        pc.startEinhasadTimer();
        check(due.getLong(pc) != 0L, "reentry arms a fresh 15-minute interval");
        pc.stopEinhasadTimer();

        final CountDownLatch gate = new CountDownLatch(1);
        final Thread[] producers = new Thread[12];
        for (int i = 0; i < producers.length; i++) {
            producers[i] = new Thread(() -> {
                try {
                    gate.await();
                    pc.startEinhasadTimer();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            producers[i].start();
        }
        gate.countDown();
        for (Thread producer : producers) {
            producer.join(3000);
            check(!producer.isAlive(), "concurrent start terminates");
        }
        check(due.getLong(pc) != 0L, "concurrent starts preserve one deadline");
        pc.stopEinhasadTimer();
        System.out.println("PASS Einhasad heartbeat checks=" + checks);
    }
}
