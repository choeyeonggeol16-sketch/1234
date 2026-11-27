package l1j.server.server.model.character.skill;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.util.Arrays;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.eKind;

import static l1j.server.server.model.character.skill.L1SkillId.God_buff;
import static l1j.server.server.model.character.skill.L1SkillId.SCALES_FIRE_DRAGON;

/** In-process comparison on the server model. No database, network or real-time scheduler. */
public final class BenchmarkSkillEffectLifecycle {
    private static final int PLAYER_COUNT = 5_000;
    private static final int WARMUP_ROUNDS = 30;
    private static final int ROUNDS = 100;

    private static final class Cast extends L1SkillUse {
        private final int id;
        Cast(int id) { this.id = id; }
        @Override public int hdSkillId() { return id; }
        @Override public int hdGetBuffIconDuration() { return 38; }
    }

    private static final class Player extends L1PcInstance {
        private int timers;

        private int mask(int id) { return id == God_buff ? 1 : 2; }
        @Override public boolean hasSkillEffect(int id) {
            return (id == God_buff || id == SCALES_FIRE_DRAGON) && (timers & mask(id)) != 0;
        }
        @Override public void setSkillEffect(int id, int duration) { timers |= mask(id); }
        @Override public void killSkillEffectTimer(int id) { timers &= ~mask(id); }
        @Override public boolean submitSkillPumpStart(int id, int duration) {
            CSkillInfo.pumpStartFromOwner(this, id, duration);
            return true;
        }
        @Override public void removeSkillEffect(int id) {
            if (!hasSkillEffect(id)) return;
            killSkillEffectTimer(id);
            CSkillEffectPumpEnd.dispatch(this, id);
        }
    }

    private static void castPair(Player pc, Cast god, Cast fire) {
        CSkillEffectActivation.dispatch(god, pc, null, 0, null);
        pc.setSkillEffect(God_buff, 38_000); // The legacy caster installed this in the common timer stage.
        CSkillEffectActivation.dispatch(fire, pc, null, 0, null);
    }

    private static void cycle(Player pc, Cast god, Cast fire) {
        pc.removeSkillEffect(God_buff);
        pc.removeSkillEffect(SCALES_FIRE_DRAGON);
        castPair(pc, god, fire);
    }

    private static long retainedHeap() throws InterruptedException {
        System.gc();
        Thread.sleep(200);
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long gcCount() {
        long count = 0;
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, collector.getCollectionCount());
        }
        return count;
    }

    public static void main(String[] args) throws InterruptedException {
        final Cast god = new Cast(God_buff), fire = new Cast(SCALES_FIRE_DRAGON);
        final Player[] players = new Player[PLAYER_COUNT];
        final long[] elapsed = new long[PLAYER_COUNT * ROUNDS];
        for (int i = 0; i < players.length; i++) {
            Player pc = new Player();
            pc.addBaseMaxHp((short) 900);
            pc.addBaseMaxMp(200);
            players[i] = pc;
        }
        final long emptyHeap = retainedHeap();
        final int baselineHp = players[0].getMaxHp();
        for (Player pc : players) castPair(pc, god, fire);
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (Player pc : players) cycle(pc, god, fire);
        }
        final long activeHeap = retainedHeap();
        final var thread = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long bytesStart = thread.getCurrentThreadAllocatedBytes();
        final long cpuStart = thread.getCurrentThreadCpuTime();
        final long gcStart = gcCount();
        int sample = 0;
        for (int round = 0; round < ROUNDS; round++) {
            for (Player pc : players) {
                final long start = System.nanoTime();
                cycle(pc, god, fire);
                elapsed[sample++] = System.nanoTime() - start;
            }
        }
        final long cpuElapsed = thread.getCurrentThreadCpuTime() - cpuStart;
        final long allocated = thread.getCurrentThreadAllocatedBytes() - bytesStart;
        final long measuredGc = gcCount() - gcStart;

        for (Player pc : players) {
            if (pc.getMaxHp() != baselineHp + 20 || pc.getHitup() != 8
                    || pc.getResistance().getSpecialResistance(eKind.ABILITY) != 5) {
                throw new AssertionError("active modifier drift");
            }
            pc.removeSkillEffect(God_buff);
            pc.removeSkillEffect(SCALES_FIRE_DRAGON);
            pc.skillEffectClearFromOwner();
            if (pc.getMaxHp() != baselineHp || pc.getHitup() != 0
                    || pc.getResistance().getSpecialResistance(eKind.ABILITY) != 0) {
                throw new AssertionError("remaining modifier after cleanup");
            }
        }
        final long clearedHeap = retainedHeap();
        Arrays.sort(elapsed);
        System.out.printf("players=%d cycles=%d p99_ns=%d cpu_ns/cycle=%.1f bytes/cycle=%.1f gc=%d empty_heap=%d active_heap=%d cleared_heap=%d%n",
                PLAYER_COUNT, sample, elapsed[(int) Math.ceil(sample * .99) - 1], (double) cpuElapsed / sample,
                (double) allocated / sample, measuredGc, emptyHeap, activeHeap, clearedHeap);
        Reference.reachabilityFence(players);
        System.exit(0);
    }
}
