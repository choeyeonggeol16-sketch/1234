import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntToLongFunction;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.utils.StatContext;

/** CPU-local JVM fixture for the real stat getters, preview resolution and skill pump. */
public final class CreatureStatBenchmark {
    private static final int[] CLASS_IDS = {0, 61, 138, 734, 2786, 6658, 6671, 12490};
    private static final int[] LEVELS = {1, 50, 51, 89, 90, 99};
    private static final int SAMPLE_STRIDE = 32;
    private static final int PEERS = CLASS_IDS.length * LEVELS.length;
    private static volatile long sink;

    private final L1PcInstance[] actors = new L1PcInstance[PEERS];
    private final SkillEffectContainer[] buffs = new SkillEffectContainer[PEERS];

    public CreatureStatBenchmark() {
        Config.TIME_ZONE = "UTC";
        Config.RATE_WEIGHT_LIMIT = 1;
        for (int i = 0; i < PEERS; i++) {
            int type = i / LEVELS.length;
            var player = new L1PcInstance();
            player.setType(type);
            player.setClassId(CLASS_IDS[type]);
            player.setLevel(LEVELS[i % LEVELS.length]);
            player.ResetPrimaryStats(14 + i % 32, 18 + i % 30, 16 + i % 24,
                    12 + i % 30, 17 + i % 30, 13 + i % 24);
            player.addBaseMaxHp((short) (700 + i * 7));
            player.addBaseMaxMp(140 + i * 4);
            actors[i] = player;
            buffs[i] = new SkillEffectContainer(player);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("expected combat, preview or skill");
        var fixture = new CreatureStatBenchmark();
        switch (args[0]) {
            case "combat" -> measure(args[0], 6_000_000, 2_000_000, fixture::combat);
            case "preview" -> measure(args[0], 2_000_000, 600_000, fixture::preview);
            case "skill" -> measure(args[0], 220_000, 96_000, fixture::skill);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private long combat(int index) {
        var pc = actors[index % PEERS];
        boolean ranged = (index & 64) != 0;
        return mix(pc.GetStatPAttack(ranged), ranged ? pc.getBaseBowHitByLevelClass() : pc.getBaseMeleeHitByLevelClass(),
                pc.getBaseMagicHitByLevelClass(), pc.GetStatSp(), pc.getAc(), pc.GetMDefend(), pc.getTotalER(),
                pc.GetStatCarryWeight(), pc.GetStatHPRegen(), pc.GetStatMPRegen(), pc.GetStatMCriticalProb());
    }

    private long preview(int index) {
        var pc = actors[index % PEERS];
        // The draft belongs to the caller and resolves once, as in the packet handler.
        var context = new StatContext(pc.getType(), pc.GetStatAmount());
        context.setStat(0, pc.GetStatStr() + ((index >>> 6) & 3));
        context.setStat(3, pc.GetStatDex() + ((index >>> 9) & 3));
        boolean changed = context.resolve(pc);
        final int[] stats = context.getStats();
        return mix(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                context.getTargetType(), context.getClassType(), changed ? 1 : 0, 0, 0);
    }

    private long skill(int index) {
        int peer = index % PEERS;
        var pc = actors[peer];
        var effects = buffs[peer];
        effects.applySkillModifier(L1SkillId.힘3, 0);
        effects.applySkillModifier(L1SkillId.위즈3, 0);
        long beforeRemove = mix(pc.GetTotalStatStr(), pc.GetTotalStatWis(), pc.GetMDefend(),
                pc.getMaxMp(), pc.GetStatSp(), 0, 0, 0, 0, 0, 0);
        if (!effects.removeSkillModifier(L1SkillId.힘3, false)
                || !effects.removeSkillModifier(L1SkillId.위즈3, false))
            throw new AssertionError("each measured buff cycle removes both effects");
        return beforeRemove ^ mix(pc.GetTotalStatStr(), pc.GetTotalStatWis(), pc.GetMDefend(),
                pc.getMaxMp(), pc.GetStatSp(), 0, 0, 0, 0, 0, 0);
    }

    private static long mix(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k) {
        long result = a;
        result = result * 31 + b;
        result = result * 31 + c;
        result = result * 31 + d;
        result = result * 31 + e;
        result = result * 31 + f;
        result = result * 31 + g;
        result = result * 31 + h;
        result = result * 31 + i;
        result = result * 31 + j;
        return result * 31 + k;
    }

    private static void measure(String mode, int warmup, int count, IntToLongFunction job) {
        long checksum = 0;
        for (int i = 0; i < warmup; i++) checksum = Long.rotateLeft(checksum, 7) ^ job.applyAsLong(i);
        sink = checksum;
        final var threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threads.isThreadAllocatedMemorySupported() && !threads.isThreadAllocatedMemoryEnabled())
            threads.setThreadAllocatedMemoryEnabled(true);
        if (threads.isCurrentThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled())
            threads.setThreadCpuTimeEnabled(true);
        final long owner = Thread.currentThread().threadId();
        final var collectors = ManagementFactory.getGarbageCollectorMXBeans();
        final var memory = ManagementFactory.getMemoryMXBean();
        long[] durations = new long[(count + SAMPLE_STRIDE - 1) / SAMPLE_STRIDE];
        int samples = 0;
        checksum = 0;
        final long gcStart = collectors.stream().mapToLong(bean -> bean.getCollectionCount()).sum();
        final long gcTimeStart = collectors.stream().mapToLong(bean -> bean.getCollectionTime()).sum();
        final long heapStart = memory.getHeapMemoryUsage().getUsed();
        final long allocatedStart = threads.getThreadAllocatedBytes(owner);
        final long cpuStart = threads.getCurrentThreadCpuTime();
        final long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            final long value;
            if ((i & (SAMPLE_STRIDE - 1)) == 0) {
                long sampleStart = System.nanoTime();
                value = job.applyAsLong(i);
                durations[samples++] = System.nanoTime() - sampleStart;
            } else {
                value = job.applyAsLong(i);
            }
            checksum = Long.rotateLeft(checksum, 7) ^ value;
        }
        final long elapsed = System.nanoTime() - start;
        final long cpu = threads.getCurrentThreadCpuTime() - cpuStart;
        final long allocated = threads.getThreadAllocatedBytes(owner) - allocatedStart;
        final long heapEnd = memory.getHeapMemoryUsage().getUsed();
        final long collections = collectors.stream().mapToLong(bean -> bean.getCollectionCount()).sum() - gcStart;
        final long gcMs = collectors.stream().mapToLong(bean -> bean.getCollectionTime()).sum() - gcTimeStart;
        sink = checksum;
        Arrays.sort(durations, 0, samples);
        System.out.printf(Locale.ROOT,
                "RESULT\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%016x%n",
                mode, count, samples, count * 1e9 / elapsed, cpu / (double) count, allocated / (double) count,
                percentile(durations, samples, 500), percentile(durations, samples, 990),
                percentile(durations, samples, 999), collections, gcMs, heapStart, heapEnd, elapsed, sink);
    }

    private static long percentile(long[] sorted, int count, int rank) {
        return sorted[(int) ((count * (long) rank + 999L) / 1000L) - 1];
    }
}
