package l1j.server.server.model.character.effect.action.realize;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.effect.action.EffectModel;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.GameUtils;

/**
 * A scheduled callback only requests admission to the existing NPC owner.
 * Every damage/packet step runs on that owner, never on a timer or I/O thread.
 * At most one live cast/timer is retained per NPC (including queued casts).
 */
public class OnEffectThread {
    private static final Logger LOG = Logger.getLogger(OnEffectThread.class.getName());
    private static final int MAX_STEP_BUDGET = 512;
    private static final int MAX_PERIMETER_STEPS_PER_TICK = 4;
    private static final long MAX_CAST_NS = 60_000_000_000L;
    private static final AtomicInteger GENERATIONS = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, OnEffectThread> ACTIVE = new ConcurrentHashMap<>();
    // Passive, allocation-free-per-tick accounting for dense-effect owner load.
    private static final long SLOW_TICK_NS = 10_000_000L;
    private static final AtomicLong SLOW_OWNER_TICKS = new AtomicLong();
    private static final AtomicLong MAX_OWNER_TICK_NS = new AtomicLong();
    private static final AtomicLong MAX_PLAYER_CHECKS_PER_TICK = new AtomicLong();

    public static long getSlowOwnerTicks() { return SLOW_OWNER_TICKS.get(); }
    public static long getMaxOwnerTickNanos() { return MAX_OWNER_TICK_NS.get(); }
    public static long getMaxPlayerChecksPerTick() { return MAX_PLAYER_CHECKS_PER_TICK.get(); }

    private static void recordMaximum(AtomicLong metric, long value) {
        long seen = metric.get();
        while (value > seen && !metric.compareAndSet(seen, value)) seen = metric.get();
    }

    protected static final int[][] RADIATION = {
            {0,-1}, {1,-1}, {1,0}, {1,1}, {0,1}, {-1,1}, {-1,0}, {-1,-1}
    };
    private static final int[][] HEADINGS = {{7,0,1}, {6,-1,2}, {5,4,3}};

    protected final EffectModel model;
    protected final L1NpcInstance owner;
    protected ArrayList<L1PcInstance> players;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final EffectTimer timer;
    private volatile long deadlineNanos;
    private int steps;
    private int admissionRetries;
    private long playerChecksThisTick;
    private boolean initialized;
    private int radius;
    private int offset;

    public OnEffectThread(EffectModel model, L1NpcInstance owner) {
        this.model = model;
        this.owner = owner;
        this.timer = new EffectTimer(owner == null ? 0 : owner.getId(), nextGeneration());
    }

    /** Called by the NPC action owner, before any delayed work is submitted. */
    public final boolean start() {
        if (model == null || !model.isSafe() || owner == null
                || owner.isDead() || owner.isDestroyed() || !CNPCServer.getInstance().isRunning()) {
            return false;
        }
        if (!owner.tryBeginCast()) return false;
        if (ACTIVE.putIfAbsent(owner.getId(), this) != null) {
            owner.endCast();
            return false;
        }
        deadlineNanos = System.nanoTime() + MAX_CAST_NS;
        if (!schedule(0L)) return false;
        return true;
    }

    private static int nextGeneration() {
        int next;
        do {
            next = GENERATIONS.incrementAndGet();
        } while (next == 0);
        return next;
    }

    private record EffectTimer(int npcId, int generation) implements CIOThreadCallback.WheelTask {
        private OnEffectThread activeCast() {
            final OnEffectThread cast = ACTIVE.get(npcId);
            return cast != null && cast.timer.generation == generation ? cast : null;
        }

        @Override public boolean isCancelled() {
            return activeCast() == null;
        }

        @Override public void run() {
            final OnEffectThread cast = activeCast();
            if (cast != null) cast.publishOwnerTick();
        }

        @Override public void runOnNpc(L1NpcInstance npc) {
            final OnEffectThread cast = activeCast();
            if (cast != null) cast.runOnNpc(npc);
        }
    }

    /** Expire and retry independently of NPC owner admission. */
    private void publishOwnerTick() {
        if (finished.get()) return;
        final CNPCServer npcs = CNPCServer.getInstance();
        if (isExpired() || owner.isDestroyed() || CWorld.getInstance().smartIdOf(owner) == 0L) {
            finish();
        } else if (npcs.postNpcWheelTask(owner, timer)) {
            admissionRetries = 0;
        } else if (!npcs.isRunning() || isExpired()) {
            finish();
        } else {
            final long retryMs = 20L << Math.min(++admissionRetries, 4);
            schedule(Math.min(retryMs, 500L));
        }
    }

    private boolean isExpired() {
        return System.nanoTime() - deadlineNanos >= 0L;
    }

    private void runOnNpc(L1NpcInstance npc) {
        if (finished.get() || npc != owner) return;
        if (owner.isDead() || owner.isDestroyed() || isExpired()
                || ++steps > MAX_STEP_BUDGET) {
            finish();
            return;
        }
        final long tickStart = System.nanoTime();
        playerChecksThisTick = 0L;
        try {
            if (!initialized) {
                initialized = true;
                players = CWorld.getInstance().getVisiblePlayer(owner, model.getRange());
                SC_DoActionGFX.broadcast(owner, 19);
                radius = model.getRange();
                offset = 0;
                initializePattern();
            }
            final long nextDelay = executeStep();
            if (nextDelay < 0L || finished.get()) finish();
            else schedule(nextDelay);
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "MJEffect owner tick failed npc=" + owner.getId(), failure);
            finish();
        } finally {
            final long elapsed = System.nanoTime() - tickStart;
            recordMaximum(MAX_OWNER_TICK_NS, elapsed);
            recordMaximum(MAX_PLAYER_CHECKS_PER_TICK, playerChecksThisTick);
            if (elapsed >= SLOW_TICK_NS) SLOW_OWNER_TICKS.incrementAndGet();
        }
    }

    /** First tick setup, on NPC owner only. */
    protected void initializePattern() { }

    /** Returns the next delay; negative means complete. A tick has finite work. */
    protected long executeStep() {
        final int impact = model.getImpact();
        final int limit = radius * 2;
        final int left = owner.getX() - radius;
        final int top = owner.getY() - radius;
        final int right = owner.getX() + radius;
        final int bottom = owner.getY() + radius;
        int count = 0;
        while (offset < limit && count++ < MAX_PERIMETER_STEPS_PER_TICK && !finished.get()) {
            final int d = offset;
            applyPoint(left + d, top, impact);
            applyPoint(right, top + d, impact);
            applyPoint(right - d, bottom, impact);
            applyPoint(left, bottom - d, impact);
            offset += impact;
        }
        if (offset < limit) return 1L; // Yield after at most 16 locations.
        radius -= model.getZPos();
        offset = 0;
        return radius > 0 ? model.getDelay() : -1L;
    }

    protected final void applyPoint(int x, int y, int impact) {
        if (finished.get() || owner.isDead() || owner.isDestroyed()) return;
        final ArrayList<L1PcInstance> snapshot = players;
        if (snapshot == null) return;
        playerChecksThisTick += snapshot.size();
        var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_EffectLocation.nativeWriter(x, y, model.getEffectId()),
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        try {
            for (int i = 0, size = snapshot.size(); i < size; i++) {
                final L1PcInstance pc = snapshot.get(i);
                if (pc == null || pc.getMapId() != owner.getMapId()) continue;
                // Keep the effect ahead of this player's damage reaction in CIO FIFO.
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(shared, pc);
                if (pc.isDead() || isUnBeatable(pc) || isCounterMagic(pc)) continue;
                final int dx = Math.abs(pc.getX() - x);
                final int dy = Math.abs(pc.getY() - y);
                if (dx <= impact && dy <= impact) {
                    try {
                        SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
                        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
                    } catch (RuntimeException visualFailure) {
                        LOG.log(Level.WARNING, "Area-effect damage reaction notice failed", visualFailure);
                    }
                    pc.receiveDamage(owner, calcDamage(owner, pc, model.getDamageMin(), model.getDamageMax()));
                }
            }
        } finally {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.closeOptional(shared);
        }
    }

    protected final synchronized boolean schedule(long delayMs) {
        if (finished.get()) return false;
        final long remainingNs = deadlineNanos - System.nanoTime();
        if (remainingNs <= 0L) {
            finish();
            return false;
        }
        // Never keep a cast alive past its deadline just because the owner
        // queue was saturated or the final effect step requested a long delay.
        if (delayMs > 0L) {
            delayMs = Math.min(delayMs,
                    Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNs)));
        }
        if (!CNPCServer.getInstance().isRunning() || !CIOThreadCallback.getInstance()
                .scheduleCharacterEffectTick(owner, timer, delayMs)) {
            finish();
            return false;
        }
        return !finished.get();
    }

    private synchronized void finish() {
        if (!finished.compareAndSet(false, true)) return;
        players = null;
        ACTIVE.remove(owner.getId(), this);
        owner.endCast();
    }

    /** Clear the cast; the owner wheel only retains its numeric token. */
    public static void cancelNpc(L1NpcInstance npc) {
        if (npc == null) return;
        final OnEffectThread task = ACTIVE.get(npc.getId());
        if (task != null && task.owner == npc) task.finish();
    }

    public static void cancelAll() {
        for (OnEffectThread task : ACTIVE.values()) task.finish();
    }

    protected static int calcHeading(int x1, int y1, int x2, int y2) {
        return HEADINGS[Integer.compare(x1, x2) + 1][Integer.compare(y1, y2) + 1];
    }

    protected static boolean isCounterMagic(L1PcInstance pc) {
        if (!pc.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) return false;
        pc.removeSkillEffect(L1SkillId.COUNTER_MAGIC);
        final int cast = SkillsTable.getInstance().getTemplate(L1SkillId.COUNTER_MAGIC).getCastGfx();
        SC_OnlyEffect.send(pc, pc.getId(), cast);
        SC_OnlyEffect.broadcast(pc, pc.getId(), cast);
        return true;
    }

    protected static boolean isUnBeatable(L1PcInstance pc) {
        return pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)
                || pc.hasSkillEffect(L1SkillId.ICE_LANCE)
                || pc.hasSkillEffect(L1SkillId.EARTH_BIND);
    }

    protected static int calcDamage(L1Character attacker, L1PcInstance pc, int min, int max) {
        final double imm = pc.getImmuneReduction();
        if (imm != 0) {
            min -= (int) (min * imm);
            max -= (int) (max * imm);
        }
        int damage = GameUtils.getMagicDamage(attacker, pc, min, max);
        if (pc.hasSkillEffect(L1SkillId.FAFU_MAAN) || pc.hasSkillEffect(L1SkillId.LIFE_MAAN)
                || pc.hasSkillEffect(L1SkillId.SHAPE_MAAN) || pc.hasSkillEffect(L1SkillId.BIRTH_MAAN)) {
            if (GameRandom.nextInt(100) < 35) damage /= 2;
        }
        damage -= pc.getDamageReductionByArmor();
        if (pc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR))
            damage -= (Math.max(pc.getLevel(), 50) - 50) / 5 + 1;
        if (pc.hasSkillEffect(L1SkillId.IllUSION_AVATAR)) damage += damage / 5;
        if (pc.hasSkillEffect(L1SkillId.PATIENCE)) damage -= 4;
        if (pc.hasSkillEffect(L1SkillId.DRAGON_SKIN)) damage -= 3;
        return damage;
    }
}
