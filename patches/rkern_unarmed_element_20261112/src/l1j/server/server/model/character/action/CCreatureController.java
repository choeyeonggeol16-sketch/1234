package l1j.server.server.model.character.action;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;

/**
 * - PC당 controller 1개.
 * - current / pending action 1개씩.
 * - action timer는 CIOThread owner의 단일 timing wheel에 등록.
 * - TimerExpired -> AsyncTimerExpired -> current.OnTick() 흐름.
 * - packet/action마다 객체를 만들지 않고 controller와 action 객체를 재사용.
 */
public final class CCreatureController {

    public static final byte ACTION_NONE                = 0;
    public static final byte ACTION_MOVE                = 1;
    public static final byte ACTION_ATTACK              = 2;
    public static final byte ACTION_ATTACK_WAIT         = 3;
    public static final byte ACTION_MOVE_TO_TARGET      = 4;
    public static final byte ACTION_MOVE_TO_TARGET_WAIT = 5;
    public static final byte ACTION_SKILL               = 6;
    public static final byte ACTION_GET_ITEM            = 7;
    public static final byte ACTION_GENERAL             = 8;

    public static final int FLAG_AUTO_ATTACK = 1;
    public static final int FLAG_FORCE_ATTACK = 1 << 1;

    private static final int TIMER_ACTION = 1;
    private static final int ACTION_TICK_MS = 20;
    private static final long MIN_ACTION_TIMER_MS = 80L;
    private static final int MAX_PENDING_RECURSION = 8;

    // CCreatureController-local serialized execution (NC 32-slot policy).
    // Controller is the sole executor for PC actions and PC timer mutations.
    // The CIO wheel only schedules expirations. No new queue or worker.
    private static final Logger EXECUTION_LOG = Logger.getLogger(CCreatureController.class.getName());
    private static final int EXECUTION_SLOTS = 32;
    private static final int EXECUTION_MASK = EXECUTION_SLOTS - 1;
    private static final int EXECUTION_LIMIT = 24;
    private static final int EXECUTION_WORDS = 8;
    private static final int EXEC_ACTION = 1;
    private static final int EXEC_TIMER = 2;
    private static final int EXEC_RESET = 3;
    private static final int EXEC_HEADING = 4;
    private static final int EXEC_SKILL_EFFECT = 5;
    private static final int EXEC_SKILL_HEARTBEAT = 6;
    private static final int EXEC_TELEPORT = 7;
    private static final int EXEC_ITEM_DELAY = 8;
    private static final int EXEC_SIGHT = 9;
    private static final int EXEC_WHEEL_PC = 10;
    private static final int EXEC_REGEN_RESET = 11;
    private static final int EXEC_STOP_PSS_AUTO = 12;
    private static final int EXEC_DAMAGE = 13;
    private static final int EXEC_COUNTER_DAMAGE = 14;
    private static final int EXEC_MAGIC_DAMAGE = 15;
    private static final int EXEC_UNARMED_DAMAGE = 16;
    private static final int EXEC_CANCELLED = 0;

    private static final VarHandle EXEC_TAIL;
    private static final VarHandle EXEC_WORK;
    private static final VarHandle EXEC_SIGHT_REFRESH;
    private static final VarHandle EXEC_READY = MethodHandles.arrayElementVarHandle(long[].class);

    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            EXEC_TAIL = lookup.findVarHandle(CCreatureController.class, "executionTail", long.class);
            EXEC_WORK = lookup.findVarHandle(CCreatureController.class, "executionWork", int.class);
            EXEC_SIGHT_REFRESH = lookup.findVarHandle(CCreatureController.class, "sightRefreshIdentity", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Eight primitive words and two reusable references per slot.
    // No command objects, boxing, Runnable, or per-execution allocation.
    private final long[] executionWords = new long[EXECUTION_SLOTS * EXECUTION_WORDS];
    private final Object[] executionRefs = new Object[EXECUTION_SLOTS << 1];
    private final long[] executionReady = new long[EXECUTION_SLOTS];
    private volatile long executionTail;
    private volatile long executionHead;
    private volatile int executionWork;
    // A full controller ring does not block the NPC/world publisher. Instead,
    // one synthetic controller-owned refresh reconciles the current sight set.
    // High/low 32 bits hold the actor epoch/session; zero means not scheduled.
    private volatile long sightRefreshIdentity;
    private volatile Thread executionThread;
    private volatile Thread executionWaiter;

    /**
     * NC CSerializedDamageMessage-inspired overflow storage. A full 24-job controller ring must
     * never discard a hit or block another character's controller. The normal
     * path uses the ring's preallocated primitive slots; only overflow allocates
     * an event. afterTicket preserves its place behind all already reserved
     * controller commands when the ring is saturated.
     */
    private static final class DamageOverflow {
        final L1Character attacker;
        final int damage, kind, session, epoch;
        final long afterTicket;

        DamageOverflow(L1Character attacker, int damage, int kind,
                       int session, int epoch, long afterTicket) {
            this.attacker = attacker;
            this.damage = damage;
            this.kind = kind;
            this.session = session;
            this.epoch = epoch;
            this.afterTicket = afterTicket;
        }
    }

    private final ConcurrentLinkedQueue<DamageOverflow> damageOverflow =
            new ConcurrentLinkedQueue<>();

    public boolean AddExecutable(byte type, int a0, int a1, int a2, int a3,
            long l0, double d0, Object o0, String s0, int flags) {
        if (type <= ACTION_NONE || type > ACTION_GENERAL) {
            throw new IllegalArgumentException("invalid action type " + type);
        }
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        // The 32-slot controller owns action serialization. Admission must
        // not depend on the thread currently driving the timer wheel.
        // All controller actions, including foreign-thread requests, enter this
        // same 32-slot sequence; unrelated world events retain their owner path.
        return executeOrPublish(EXEC_ACTION, type, a0, a1, a2, a3,
                l0, Double.doubleToRawLongBits(d0), flags, o0, s0,
                pc.getSessionStamp(), epoch);
    }

    /**
     * NC CCreatureController::OnDamaged entry: serialize victim-side HP
     * damage and death/skill side effects (via OnDamagedMessage). No attack-side HP mutation or new worker.
     * A session/actor epoch snapshot prevents queued hits reaching a relogged PC.
     */
    public boolean OnDamaged(L1Character attacker, int damage, boolean counter) {
        return OnDamaged(attacker, damage, counter, false);
    }

    public boolean OnDamaged(L1Character attacker, int damage, boolean counter,
                             boolean applyMagicStack) {
        return OnDamaged(attacker, damage, counter, applyMagicStack, false);
    }

    public boolean OnDamaged(L1Character attacker, int damage, boolean counter,
                             boolean applyMagicStack, boolean unarmedPhysical) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(counter ? EXEC_COUNTER_DAMAGE
                : applyMagicStack ? EXEC_MAGIC_DAMAGE
                : unarmedPhysical ? EXEC_UNARMED_DAMAGE : EXEC_DAMAGE,
                0, damage, 0, 0, 0, 0L, 0L, 0, attacker, null,
                pc.getSessionStamp(), epoch);
    }

    /** Wheel callback follows the same controller serial execution order. */
    public boolean AddExecutableTimer(int id, int generation) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        // Timer callbacks enter the same controller sequencer as actions.
        // Stale actor/session identity is checked again at drain time.
        return executeOrPublish(EXEC_TIMER, 0, id, generation, 0, 0,
                0L, 0L, 0, null, null,
                pc.getSessionStamp(), epoch);
    }

    /** Skill state mutations use the same per-PC FIFO as movement and attack. */
    public boolean AddExecutableSkillEffect(byte op, int skillId, int timeMillis) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_SKILL_EFFECT, 0, op, skillId, timeMillis, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    /** A wheel expiration may only mutate a PC on its controller FIFO. */
    public boolean AddExecutableSkillHeartbeat(int generation) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_SKILL_HEARTBEAT, 0, generation, 0, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    /** CIO wheel only provides the due-time signal; PC state runs on this FIFO. */
    public boolean AddExecutableWheelTask(CIOThreadCallback.WheelTask task) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (task == null || pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_WHEEL_PC, 0, 0, 0, 0, 0,
                0L, 0L, 0, task, null, pc.getSessionStamp(), epoch);
    }

    /** Stop PSS-owned repeating combat without resetting unrelated PC actions. */
    public boolean StopPlaySupportAutoActions() {
        final L1PcInstance pc = actor;
        final int epoch = actorEpoch;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_STOP_PSS_AUTO, 0, 0, 0, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    public boolean AddExecutableRegenReset() {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_REGEN_RESET, 0, 0, 0, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    /** Item cooldown expiration changes controller-local state in FIFO order. */
    public boolean AddExecutableItemDelay(int delayId, int generation) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_ITEM_DELAY, 0, delayId, generation, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    /** Teleport completion is ordered with the PC's in-flight game actions. */
    public boolean AddExecutableTeleport(int teleportEpoch) {
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_TELEPORT, 0, teleportEpoch, 0, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    public boolean ChangeHeading(int heading) {
        if ((heading & ~7) != 0) return false;
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || (epoch & 1) != 0 || actorEpoch != epoch
                || ownerShardIndex() < 0) return false;
        return executeOrPublish(EXEC_HEADING, 0, heading, 0, 0, 0,
                0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
    }

    /**
     * LocalWorldMap may dispatch visibility notifications from a CIO wheel
     * thread while the PC is executing RECV on a NIO.2 completion thread.
     * Route the viewer's known-object mutation through the same 32-slot
     * controller serializer as movement, skills, and action timers.
     * No extra worker, Runnable, or command queue is allocated.
     */
    public boolean ReconcileSight(L1Object observed, boolean leaving) {
        if (observed == null) return false;
        final int epoch = actorEpoch;
        final L1PcInstance pc = actor;
        if (pc == null || pc == observed || (epoch & 1) != 0
                || actorEpoch != epoch) return false;
        return executeOrPublish(EXEC_SIGHT, 0, leaving ? 1 : 0,
                0, 0, 0, 0L, 0L, 0, observed, null,
                pc.getSessionStamp(), epoch);
    }

    public int ExecutableBacklog() {
        final long count = executionTail - executionHead;
        return (int) Math.max(0L, Math.min(EXECUTION_LIMIT, count));
    }

    /**
     * A saturated sight FIFO must never park an NPC shard or world publisher.
     * Coalesce the overflow into one controller-owned full reconciliation.
     * The synthetic work participates in executionWork just like a published
     * slot, but consumes no ring entry, reference, or newly allocated object.
     */
    private boolean requestSightRefresh(int session, int epoch) {
        final L1PcInstance pc = actor;
        if (pc == null || actorEpoch != epoch || pc.getSessionStamp() != session) {
            return false;
        }
        final long identity = ((long) epoch << 32) | (session & 0xFFFF_FFFFL);
        for (;;) {
            final long pending = (long) EXEC_SIGHT_REFRESH.getVolatile(this);
            if (pending == identity) return true;
            if (EXEC_SIGHT_REFRESH.compareAndSet(this, pending, identity)) {
                // Only the first pending refresh needs a synthetic work signal.
                // If a different session replaces it, the same signal will
                // reconcile the newer actor instead of replaying stale sight.
                if (pending == 0L) signalAndDrain();
                return true;
            }
        }
    }

    private boolean executeOrPublish(int kind, int action,
            int a0, int a1, int a2, int a3,
            long l0, long d0Bits, int flags, Object o0, String s0,
            int expectedSession, int expectedEpoch) {
        // Reentry is immediate, but never execute a stale actor or session.
        if (Thread.currentThread() == executionThread) {
            final L1PcInstance pc = actor;
            if (actorEpoch != expectedEpoch || (pc == null
                    ? kind != EXEC_RESET || expectedSession != 0
                    : pc.getSessionStamp() != expectedSession)) return false;
            executeDirect(kind, action, a0, a1, a2, a3, l0, d0Bits, flags, o0, s0);
            return true;
        }

        final long ticket;
        for (;;) {
            final long tail = executionTail;
            final boolean mustComplete = kind == EXEC_RESET || kind == EXEC_SKILL_EFFECT
                    || kind == EXEC_SKILL_HEARTBEAT || kind == EXEC_TELEPORT
                    || kind == EXEC_ITEM_DELAY || kind == EXEC_SIGHT
                    || kind == EXEC_REGEN_RESET || kind == EXEC_STOP_PSS_AUTO;
            if (tail - executionHead >= EXECUTION_LIMIT) {
                if (kind == EXEC_SIGHT) {
                    return requestSightRefresh(expectedSession, expectedEpoch);
                }
                if (kind == EXEC_DAMAGE || kind == EXEC_COUNTER_DAMAGE
                        || kind == EXEC_MAGIC_DAMAGE || kind == EXEC_UNARMED_DAMAGE) {
                    // Do not block another PC controller while its victim's
                    // ring is full: two PCs hitting each other could deadlock.
                    // Reserve a guaranteed overflow event instead of dropping HP.
                    final L1PcInstance current = actor;
                    if (actorEpoch != expectedEpoch || current == null
                            || current.getSessionStamp() != expectedSession) return false;
                    damageOverflow.offer(new DamageOverflow((L1Character) o0,
                            a0, kind, expectedSession, expectedEpoch, tail));
                    signalAndDrain();
                    return true;
                }
                if (!mustComplete) {
                    return false;
                }
                // Do not trap a producer waiting for a controller that was
                // rebound or logged out while its ring remained saturated.
                final L1PcInstance current = actor;
                if (actorEpoch != expectedEpoch || (current == null
                        ? kind != EXEC_RESET || expectedSession != 0
                        : current.getSessionStamp() != expectedSession)) return false;
                LockSupport.parkNanos(this, 100_000L);
                continue;
            }
            if (EXEC_TAIL.compareAndSet(this, tail, tail + 1L)) {
                ticket = tail;
                break;
            }
        }

        final int slot = (int) ticket & EXECUTION_MASK;
        final int offset = slot * EXECUTION_WORDS;
        try {
            // One long already reserved per slot: kind in low 32 bits,
            // producer session generation in high 32 bits. No extra slot data.
            executionWords[offset] = ((long) expectedSession << 32) | (kind & 0xFFFF_FFFFL);
            executionWords[offset + 1] = action;
            // a0 uses only the low half. Retain the actor epoch in its
            // previously unused upper half without enlarging a 32-slot entry.
            executionWords[offset + 2] = ((long) expectedEpoch << 32) | (a0 & 0xFFFF_FFFFL);
            executionWords[offset + 3] = a1;
            executionWords[offset + 4] = a2;
            executionWords[offset + 5] = a3;
            executionWords[offset + 6] = l0;
            executionWords[offset + 7] = d0Bits;
            // Encode flags in the otherwise unused upper 32 bits of action word.
            executionWords[offset + 1] |= (long) flags << 32;
            executionRefs[slot << 1] = o0;
            executionRefs[(slot << 1) + 1] = s0;
        } catch (Throwable failure) {
            // A reserved ticket must always become consumable.
            executionWords[offset] = EXEC_CANCELLED;
            EXEC_READY.setRelease(executionReady, slot, ticket + 1L);
            signalAndDrain();
            throw failure;
        }
        EXEC_READY.setRelease(executionReady, slot, ticket + 1L);
        signalAndDrain();
        return true;
    }

    private void signalAndDrain() {
        final Thread waiter = executionWaiter;
        if (waiter != null) {
            LockSupport.unpark(waiter);
        }
        if ((int) EXEC_WORK.getAndAdd(this, 1) == 0) {
            drainExecutables();
        }
    }

    private void drainExecutables() {
        final Thread runner = Thread.currentThread();
        executionThread = runner;
        for (;;) {
            final long ticket = executionHead;
            final DamageOverflow overflow = damageOverflow.peek();
            if (overflow != null && overflow.afterTicket <= ticket) {
                // Events overflow only after older ring tickets are reserved.
                // Before consuming a newer ticket, honor the damage boundary.
                final DamageOverflow event = damageOverflow.poll();
                if (event != null) {
                    try {
                        final L1PcInstance victim = actor;
                        if (victim != null && actorEpoch == event.epoch
                                && victim.getSessionStamp() == event.session) {
                            OnDamagedMessage(victim, event.kind,
                                    event.attacker, event.damage);
                        }
                    } catch (Throwable failure) {
                        EXECUTION_LOG.log(Level.SEVERE,
                                "CCreatureController overflow damage failed", failure);
                    }
                }
            } else if (ticket == executionTail) {
                // A full sight ring is represented by one virtual work item.
                // Its data is not stored in the ring and must be processed only
                // after all earlier real FIFO tickets have been consumed.
                final long identity = (long) EXEC_SIGHT_REFRESH.getAndSet(this, 0L);
                if (identity != 0L) {
                    try {
                        final L1PcInstance pc = actor;
                        if (pc != null && actorEpoch == (int) (identity >>> 32)
                                && pc.getSessionStamp() == (int) identity) {
                            pc.refreshSight();
                        }
                    } catch (Throwable failure) {
                        EXECUTION_LOG.log(Level.SEVERE, "CCreatureController sight refresh failed", failure);
                    }
                }
            } else {
                final int slot = (int) ticket & EXECUTION_MASK;
                final int offset = slot * EXECUTION_WORDS;
                int spins = 0;
                while ((long) EXEC_READY.getAcquire(executionReady, slot) != ticket + 1L) {
                    if (++spins <= 1000) {
                        Thread.onSpinWait();
                    } else {
                        executionWaiter = runner;
                        if ((long) EXEC_READY.getAcquire(executionReady, slot) != ticket + 1L) {
                            LockSupport.parkNanos(this, 10_000_000L);
                        }
                        executionWaiter = null;
                        spins = 0;
                    }
                }

                try {
                    final long kindAndSession = executionWords[offset];
                    final int kind = (int) kindAndSession;
                    final L1PcInstance pc = actor;
                    if (kind != EXEC_CANCELLED
                            && actorEpoch == (int) (executionWords[offset + 2] >>> 32)
                            && (pc == null
                                    ? kind == EXEC_RESET && (int) (kindAndSession >>> 32) == 0
                                    : pc.getSessionStamp() == (int) (kindAndSession >>> 32))) {
                        final long packed = executionWords[offset + 1];
                        executeDirect(kind, (int) packed,
                                (int) executionWords[offset + 2],
                                (int) executionWords[offset + 3],
                                (int) executionWords[offset + 4],
                                (int) executionWords[offset + 5],
                                executionWords[offset + 6],
                                executionWords[offset + 7], (int) (packed >>> 32),
                                executionRefs[slot << 1],
                                (String) executionRefs[(slot << 1) + 1]);
                    }
                } catch (Throwable failure) {
                    EXECUTION_LOG.log(Level.SEVERE, "CCreatureController executable failed", failure);
                } finally {
                    executionRefs[slot << 1] = null;
                    executionRefs[(slot << 1) + 1] = null;
                    EXEC_READY.setRelease(executionReady, slot, 0L);
                    executionHead = ticket + 1L;
                }
            }

            // Clear identity before allowing a new first drainer to start.
            executionThread = null;
            if ((int) EXEC_WORK.getAndAdd(this, -1) == 1) {
                return;
            }
            executionThread = runner;
        }
    }

    private void OnDamagedMessage(L1PcInstance victim, int kind,
                                               L1Character attacker, int damage) {
        if (kind == EXEC_COUNTER_DAMAGE) {
            victim.GotCounterBarrierDamage(attacker, damage);
        } else if (kind == EXEC_UNARMED_DAMAGE) {
            victim.GotUnarmedPhysicalDamage(attacker, damage);
        } else {
            victim.GotDamage(attacker, damage, kind == EXEC_MAGIC_DAMAGE);
        }
    }

    private void executeDirect(int kind, int action,
            int a0, int a1, int a2, int a3,
            long l0, long d0Bits, int flags, Object o0, String s0) {
        switch (kind) {
        case EXEC_ACTION:
            Submit((byte) action, a0, a1, a2, a3, l0,
                    Double.longBitsToDouble(d0Bits), o0, s0, flags);
            break;
        case EXEC_DAMAGE:
        case EXEC_MAGIC_DAMAGE:
        case EXEC_UNARMED_DAMAGE:
        case EXEC_COUNTER_DAMAGE: {
            final L1PcInstance victim = actor;
            if (victim != null) {
                OnDamagedMessage(victim, kind, (L1Character) o0, a0);
            }
            break;
        }
        case EXEC_TIMER:
            TimerExpired(a0, a1);
            break;
        case EXEC_RESET:
            resetDirect();
            break;
        case EXEC_STOP_PSS_AUTO:
            stopPssAutoDirect();
            break;
        case EXEC_SKILL_EFFECT: {
            final L1PcInstance pc = actor;
            if (pc != null) {
                CIOThreadCallback.getInstance().applyCharacterSkillFromController(
                        pc, (byte) a0, a1, a2);
            }
            break;
        }
        case EXEC_SKILL_HEARTBEAT: {
            final L1PcInstance pc = actor;
            if (pc != null) {
                CIOThreadCallback.getInstance().runSkillHeartbeatFromController(pc, a0);
            }
            break;
        }
        case EXEC_ITEM_DELAY:
            ItemDelayExpired(a0, a1);
            break;
        case EXEC_TELEPORT: {
            final L1PcInstance pc = actor;
            if (pc != null && pc.teleportTimerEpoch() == a0) {
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) pc.tpEndFinish();
                else pc.tpCommitTeleport();
            }
            break;
        }
        case EXEC_REGEN_RESET: {
            final L1PcInstance pc = actor;
            if (pc != null) pc.getRegenController().Reset();
            break;
        }
        case EXEC_WHEEL_PC: {
            final L1PcInstance pc = actor;
            if (pc != null && o0 instanceof CIOThreadCallback.WheelTask) {
                ((CIOThreadCallback.WheelTask) o0).runOnController(pc);
            }
            break;
        }
        case EXEC_SIGHT: {
            final L1PcInstance pc = actor;
            final L1Object observed = (L1Object) o0;
            if (pc != null && observed != null && pc != observed) {
                if (observed.getClusterMapId() < 0) pc.outOfSight(observed);
                else pc.reconcileSight(observed);
            }
            break;
        }
        case EXEC_HEADING: {
            final L1PcInstance pc = actor;
            if (pc != null && (a0 & ~7) == 0 && pc.getHeading() != a0) {
                pc.setHeading(a0);
                if (!pc.isGmInvis() && !pc.isGhost() && !pc.isInvisble()) {
                    SC_ChangeHeading.broadcast(pc, pc.getId(), a0);
                }
            }
            break;
        }
        default:
            break;
        }
    }

    // SetActor/session changes may be observed by another completion thread.
    private volatile L1PcInstance actor;
    // Even values denote stable actors; odd values mean SetActor is changing
    // the identity. A ticket captures this epoch, not just the session stamp,
    // so an old action cannot execute on a different actor with the same stamp.
    private volatile int actorEpoch = 2;
    // Keep the last stable CIO owner across the disconnect/cleanup boundary.
    private volatile int lastOwnerShard = -1;

    private byte currentType;
    private byte pendingType;

    private int c0, c1, c2, c3;
    private long cl0;
    private double cd0;
    private Object co0;
    private String cs0;
    private int cflags;

    private int p0, p1, p2, p3;
    private long pl0;
    private double pd0;
    private Object po0;
    private String ps0;
    private int pflags;

    private int timerGeneration;
    private boolean timerArmed;

    private int resumeAutoTargetId;
    private int resumeAutoX;
    private int resumeAutoY;

    /*
     * PC-owned item reuse delay.
     *
     * Normal cardinality is tiny, so keep primitive flat arrays rather than
     * Map<Integer, Timer>. No boxing, no Node, no per-use Runnable object.
     */
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private int[] itemDelayIds = EMPTY_INT_ARRAY;
    private int[] itemDelayGenerations = EMPTY_INT_ARRAY;
    private int itemDelayCount;
    private int itemDelayGenerationSeed;

    private final CMoveAction moveAction;
    private final CAttackAction attackAction;
    private final CAttackWaitAction attackWaitAction;
    private final CMoveToTargetAction moveToTargetAction;
    private final CMoveToTargetWaitAction moveToTargetWaitAction;
    
    private final CSkillAction skillAction;
    private final CGetItemAction getItemAction;
    
    private final CGeneralAction generalAction;

    public CCreatureController(L1PcInstance actor) {
        this.actor = actor;
        moveAction = new CMoveAction(this);
        skillAction = new CSkillAction(this);
        attackAction = new CAttackAction(this);
        getItemAction = new CGetItemAction(this);
        generalAction = new CGeneralAction(this);
        attackWaitAction = new CAttackWaitAction(this);
        moveToTargetAction = new CMoveToTargetAction(this);
        moveToTargetWaitAction = new CMoveToTargetWaitAction(this);
    }

    public synchronized void SetActor(L1PcInstance actor) {
        if (this.actor == actor) return;
        actorEpoch++;
        try {
            ClearAutoResume();
            lastOwnerShard = -1;
            this.actor = actor;
        } finally {
            actorEpoch++;
        }
    }

    public L1PcInstance actor() {
        return actor;
    }

    // Existing per-PC controller is the lifetime owner of combat scratch.
    CAttackAction attackAction() {
        return attackAction;
    }

    public int ownerShardIndex() {
        final L1PcInstance pc = actor;
        if (pc != null) {
            final int owner = CIOThreadCallback.getInstance().ownerShardIndex(pc);
            if (owner >= 0) {
                lastOwnerShard = owner;
                return owner;
            }
        }
        return lastOwnerShard;
    }

    public byte currentActionType() {
        return currentType;
    }

    public byte pendingActionType() {
        return pendingType;
    }

    public boolean HasPendingAction() {
        return pendingType != ACTION_NONE;
    }

    // ---------------------------------------------------------------------
    // Packet / AI entry points
    // ---------------------------------------------------------------------

    /** All actions enter this controller's existing 32-slot sequencer. */
    private void SubmitEntry(byte type, int a0, int a1, int a2, int a3,
            long l0, double d0, Object o0, String s0, int flags) {
        final L1PcInstance pc = actor;
        if (pc == null || ownerShardIndex() < 0 || actor != pc) return;
        AddExecutable(type, a0, a1, a2, a3,
                l0, d0, o0, s0, flags);
    }

    public void Move(int x, int y, int heading) {
        SubmitEntry(ACTION_MOVE, x, y, heading & 7, 0, 0L, 0D, null, null, 0);
    }

    public void Attack(int targetId, int x, int y) {
        Attack(targetId, x, y, false);
    }

    public void Attack(int targetId, int x, int y, boolean auto) {
        SubmitEntry(ACTION_ATTACK, targetId, x, y, 0, 0L, 0D, null, null,
                auto ? FLAG_AUTO_ATTACK : 0);
    }

    /**
     * Combat execution now crosses the world-level attack boundary first.
     * The underlying target onAction semantics are still preserved by L1World.attack().
     */
    void DoAttack(L1PcInstance attacker, L1Character target) {
        L1World.getInstance().attack(attacker, target);
    }

    public void MoveToTarget(int targetId, boolean forceAttack) {
        SubmitEntry(ACTION_MOVE_TO_TARGET, targetId, 0, 0, 0, 0L, 0D, null, null,
                forceAttack ? FLAG_FORCE_ATTACK : 0);
    }

    public void Skill(int skillId, int targetId, int targetX, int targetY, String message) {
        SubmitEntry(ACTION_SKILL, skillId, targetId, targetX, targetY, 0L, 0D, null, message, 0);
    }

    public void GetItem(int x, int y, int objectId, long amount) {
        SubmitEntry(ACTION_GET_ITEM, x, y, objectId, 0, amount, 0D, null, null, 0);
    }

    public void GeneralWand(
            L1ItemInstance item,
            int spriteId,
            double damageRevision,
            int targetId,
            int targetX,
            int targetY) {
    	
        if (actor == null || item == null) {
            return;
        }

        SubmitEntry(ACTION_GENERAL,
                targetId, targetX, targetY, spriteId,
                0L, damageRevision, item, null,
                CGeneralAction.TYPE_WAND);
    }

    /**
     * 현재 action이 없으면 즉시 first action으로 올리고,
     * 있으면 pending 최신 1개 슬롯에 넣는다.
     *
     * auto attack / attack wait는 수동 입력이 들어오면 즉시 양보한다.
     */
    private void Submit(
            byte type,
            int a0,
            int a1,
            int a2,
            int a3,
            long l0,
            double d0,
            Object o0,
            String s0,
            int flags) {

        if (actor == null) {
            return;
        }

        // Validate wand usage only inside the serialized action executor.
        if (type == ACTION_GENERAL && flags == CGeneralAction.TYPE_WAND
                && (currentType == ACTION_ATTACK
                        || currentType == ACTION_ATTACK_WAIT
                        || pendingType == ACTION_ATTACK
                        || pendingType == ACTION_ATTACK_WAIT)) {
            return;
        }

        if (currentType == ACTION_NONE) {
            if (type != ACTION_SKILL && resumeAutoTargetId != 0) {
                ClearAutoResume();
            }
            SetCurrent(type, a0, a1, a2, a3, l0, d0, o0, s0, flags);
            DoFirstAction();
            return;
        }

        if (isInterruptibleAutoCurrent(type, flags)) {
            if (type == ACTION_SKILL
                    && (pendingType == ACTION_NONE
                            || (pendingType == ACTION_ATTACK
                                    && (pflags & FLAG_AUTO_ATTACK) != 0))) {
                if (pendingType == ACTION_ATTACK) {
                    resumeAutoTargetId = p0;
                    resumeAutoX = p1;
                    resumeAutoY = p2;
                } else {
                    resumeAutoTargetId = c0;
                    resumeAutoX = c1;
                    resumeAutoY = c2;
                }
            } else {
                ClearAutoResume();
            }

            EndCurrent(false, false);
            ClearPending();
            SetCurrent(type, a0, a1, a2, a3, l0, d0, o0, s0, flags);
            DoFirstAction();
            return;
        }

        if (type == ACTION_ATTACK
                && (flags & FLAG_AUTO_ATTACK) != 0
                && resumeAutoTargetId > 0
                && currentType == ACTION_SKILL) {
            resumeAutoTargetId = a0;
            resumeAutoX = a1;
            resumeAutoY = a2;
            return;
        }

        if (type != ACTION_SKILL && resumeAutoTargetId != 0) {
            ClearAutoResume();
        }
        SetPending(type, a0, a1, a2, a3, l0, d0, o0, s0, flags);
    }

    private void ClearAutoResume() {
        resumeAutoTargetId = 0;
        resumeAutoX = 0;
        resumeAutoY = 0;
    }

    private boolean isInterruptibleAutoCurrent(byte incomingType, int incomingFlags) {
        if (currentType != ACTION_ATTACK && currentType != ACTION_ATTACK_WAIT) {
            return false;
        }

        if ((cflags & FLAG_AUTO_ATTACK) == 0) {
            return false;
        }

        // 새 auto attack target은 pending으로 순서 보존.
        return incomingType != ACTION_ATTACK
                || (incomingFlags & FLAG_AUTO_ATTACK) == 0;
    }

    // ---------------------------------------------------------------------
    //  controller flow
    // ---------------------------------------------------------------------

    public void DoFirstAction() {
        if (currentType == ACTION_NONE) {
            DoPendingAction(0);
            return;
        }

        final byte type = currentType;
        final CNewAction action = actionOf(type);
        if (action == null) {
            EndCurrent(false, true);
            return;
        }

        timerArmed = false;

        final boolean started;
        try {
            started = action.OnStart();
        } catch (Throwable t) {
            t.printStackTrace();
            EndCurrent(false, true);
            return;
        }

        if (currentType != type) {
            return;
        }

        if (!started) {
            EndCurrent(false, true);
            return;
        }

        if (!timerArmed) {
            EndCurrent(true, true);
        }
    }

    public void DoPendingAction(int nRecursiveCount) {
        if (currentType != ACTION_NONE) {
            return;
        }

        if (pendingType == ACTION_NONE) {
            final int targetId = resumeAutoTargetId;
            if (targetId > 0) {
                final int x = resumeAutoX;
                final int y = resumeAutoY;
                ClearAutoResume();

                final L1PcInstance pc = actor;
                if (pc != null
                        && !pc.isDead()
                        && !pc.isGhost()
                        && pc.getNetConnection() != null
                        && pc.getNetConnection().isConnected()) {
                    SetCurrent(ACTION_ATTACK, targetId, x, y, 0,
                            0L, 0D, null, null, FLAG_AUTO_ATTACK);
                    DoFirstAction();
                }
            }
            return;
        }

        if (nRecursiveCount >= MAX_PENDING_RECURSION) {
            ClearPending();
            return;
        }

        PromotePending();

        final byte type = currentType;
        final CNewAction action = actionOf(type);
        if (action == null) {
            ClearCurrent();
            DoPendingAction(nRecursiveCount + 1);
            return;
        }

        timerArmed = false;

        boolean started = false;
        try {
            started = action.OnStart();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (currentType != type) {
            return;
        }

        if (!started) {
            EndCurrent(false, false);
            DoPendingAction(nRecursiveCount + 1);
            return;
        }

        if (!timerArmed) {
            EndCurrent(true, false);
            DoPendingAction(nRecursiveCount + 1);
        }
    }

    public boolean AddItemDelay(int delayId, long delayMs) {
        if (actor == null || delayId <= 0 || delayMs <= 0L) {
            return false;
        }

        final int count = itemDelayCount;
        final int[] ids = itemDelayIds;

        for (int i = 0; i < count; i++) {
            if (ids[i] == delayId) {
                return false;
            }
        }

        ensureItemDelayCapacity(count + 1);

        int generation = ++itemDelayGenerationSeed;
        if (generation == 0) {
            generation = ++itemDelayGenerationSeed;
        }

        itemDelayIds[count] = delayId;
        itemDelayGenerations[count] = generation;
        itemDelayCount = count + 1;

        if (!CIOThreadCallback.getInstance().schedulePcItemDelay(
                actor,
                delayId,
                generation,
                delayMs)) {

            removeItemDelayAt(count);
            return false;
        }

        return true;
    }

    public boolean HasItemDelay(int delayId) {
        final int count = itemDelayCount;
        final int[] ids = itemDelayIds;

        for (int i = 0; i < count; i++) {
            if (ids[i] == delayId) {
                return true;
            }
        }

        return false;
    }

    public void RemoveItemDelay(int delayId) {
        final int count = itemDelayCount;
        final int[] ids = itemDelayIds;

        for (int i = 0; i < count; i++) {
            if (ids[i] == delayId) {
                removeItemDelayAt(i);
                return;
            }
        }
    }

    public void ItemDelayExpired(int delayId, int generation) {
        final int count = itemDelayCount;
        final int[] ids = itemDelayIds;
        final int[] generations = itemDelayGenerations;

        for (int i = 0; i < count; i++) {
            if (ids[i] == delayId) {
                if (generations[i] == generation) {
                    removeItemDelayAt(i);
                }
                return;
            }
        }
    }

    public void ClearItemDelays() {
        final int count = itemDelayCount;

        for (int i = 0; i < count; i++) {
            itemDelayIds[i] = 0;
            itemDelayGenerations[i] = 0;
        }

        itemDelayCount = 0;

        ++itemDelayGenerationSeed;
        if (itemDelayGenerationSeed == 0) {
            ++itemDelayGenerationSeed;
        }

        if (itemDelayIds.length > 8) {
            itemDelayIds = EMPTY_INT_ARRAY;
            itemDelayGenerations = EMPTY_INT_ARRAY;
        }
    }

    private void ensureItemDelayCapacity(int needed) {
        final int current = itemDelayIds.length;
        if (needed <= current) {
            return;
        }

        int next = current == 0 ? 2 : current << 1;
        if (next < needed) {
            next = needed;
        }

        final int[] nextIds = new int[next];
        final int[] nextGenerations = new int[next];

        System.arraycopy(itemDelayIds, 0, nextIds, 0, itemDelayCount);
        System.arraycopy(itemDelayGenerations, 0, nextGenerations, 0, itemDelayCount);

        itemDelayIds = nextIds;
        itemDelayGenerations = nextGenerations;
    }

    private void removeItemDelayAt(int index) {
        final int last = itemDelayCount - 1;
        if (last < 0 || index < 0 || index > last) {
            return;
        }

        if (index != last) {
            itemDelayIds[index] = itemDelayIds[last];
            itemDelayGenerations[index] = itemDelayGenerations[last];
        }

        itemDelayIds[last] = 0;
        itemDelayGenerations[last] = 0;
        itemDelayCount = last;
    }

    public void TimerExpired(int id, int generation) {
        if (id != TIMER_ACTION || generation != timerGeneration) {
            return;
        }
        AsyncTimerExpired(id, generation);
    }

    public void AsyncTimerExpired(int id, int generation) {
        if (id != TIMER_ACTION || generation != timerGeneration) {
            return;
        }

        if (currentType == ACTION_NONE) {
            timerArmed = false;
            return;
        }

        timerArmed = false;

        final byte type = currentType;
        final CNewAction action = actionOf(type);
        if (action == null) {
            EndCurrent(false, true);
            return;
        }

        final boolean finished;
        try {
            finished = action.OnTick(System.currentTimeMillis());
        } catch (Throwable t) {
            t.printStackTrace();
            EndCurrent(false, true);
            return;
        }

        if (currentType != type) {
            return;
        }

        if (finished) {
            EndCurrent(true, true);
            return;
        }

        if (!timerArmed && currentType != ACTION_NONE) {
            AddTimer(1);
        }
    }

    public void AddTimer(int nTickCount) {
        if (currentType == ACTION_NONE || actor == null) {
            return;
        }

        final int ticks = Math.max(1, nTickCount);
        final long delayMs = Math.max(MIN_ACTION_TIMER_MS,
                (long) ticks * ACTION_TICK_MS);
        final int generation = ++timerGeneration;

        timerArmed = CIOThreadCallback.getInstance().scheduleActionTimer(
                this, TIMER_ACTION, generation, delayMs);
        if (!timerArmed && currentType != ACTION_NONE) {
            // A failed admission cannot leave the action indefinitely armed.
            EndCurrent(false, true);
        }
    }

    public int millisToTicks(long delayMs) {
        if (delayMs <= 0L) {
            return 1;
        }
        return (int) Math.max(1L,
                (delayMs + ACTION_TICK_MS - 1L) / ACTION_TICK_MS);
    }

    public void Reset() {
        final L1PcInstance pc = actor;
        if (pc != null) {
            // Controller reset belongs to the controller serializer, not
            // the unrelated CIO timer owner's game-command queue.
            final int epoch = actorEpoch;
            if ((epoch & 1) == 0 && actorEpoch == epoch) {
                executeOrPublish(EXEC_RESET, 0, 0, 0, 0, 0,
                        0L, 0L, 0, null, null, pc.getSessionStamp(), epoch);
            }
            return;
        }
        // Null-actor cleanup must also enter the controller sequencer.
        // A direct reset here would race an in-flight action or timer.
        // The epoch prevents this reset from clearing a subsequently bound actor.
        final int epoch = actorEpoch;
        if ((epoch & 1) == 0 && actorEpoch == epoch) {
            executeOrPublish(EXEC_RESET, 0, 0, 0, 0, 0,
                    0L, 0L, 0, null, null, 0, epoch);
        }
    }

    /** Runs only on the controller serializer; stale action timers are invalidated by EndCurrent. */
    private void stopPssAutoDirect() {
        // A skill can park an auto-attack in the resume slot. Clear it first.
        ClearAutoResume();
        if ((pendingType == ACTION_ATTACK && (pflags & FLAG_AUTO_ATTACK) != 0)
                || ((pendingType == ACTION_MOVE_TO_TARGET
                        || pendingType == ACTION_MOVE_TO_TARGET_WAIT)
                        && (pflags & FLAG_FORCE_ATTACK) != 0)) {
            ClearPending();
        }
        if (((currentType == ACTION_ATTACK || currentType == ACTION_ATTACK_WAIT)
                    && (cflags & FLAG_AUTO_ATTACK) != 0)
                || ((currentType == ACTION_MOVE_TO_TARGET
                        || currentType == ACTION_MOVE_TO_TARGET_WAIT)
                        && (cflags & FLAG_FORCE_ATTACK) != 0)) {
            EndCurrent(false, true);
        }
    }

    private void resetDirect() {
        ++timerGeneration;
        timerArmed = false;
        ClearItemDelays();
        ClearAutoResume();

        final CNewAction current = actionOf(currentType);
        if (current != null) {
            try {
                current.OnEnd(false);
            } catch (Throwable ignored) {
            }
        }

        ClearCurrent();
        ClearPending();
    }

    // ---------------------------------------------------------------------
    // Action transitions used by C*Action
    // ---------------------------------------------------------------------

    void EnterAttackWait(int delayTicks) {
        if (currentType != ACTION_ATTACK) {
            return;
        }

        ++timerGeneration;
        timerArmed = false;
        currentType = ACTION_ATTACK_WAIT;
        c3 = Math.max(1, delayTicks);
        DoFirstAction();
    }

    void ResumeAttackFromWait() {
        if (currentType != ACTION_ATTACK_WAIT) {
            return;
        }

        ++timerGeneration;
        timerArmed = false;
        currentType = ACTION_ATTACK;
        c3 = 0;
        DoFirstAction();
    }

    void EnterMoveToTargetWait(int delayTicks) {
        if (currentType != ACTION_MOVE_TO_TARGET) {
            return;
        }
        ++timerGeneration;
        timerArmed = false;
        currentType = ACTION_MOVE_TO_TARGET_WAIT;
        c3 = Math.max(1, delayTicks);
        DoFirstAction();
    }

    void ResumeMoveToTargetFromWait() {
        if (currentType != ACTION_MOVE_TO_TARGET_WAIT) {
            return;
        }
        ++timerGeneration;
        timerArmed = false;
        currentType = ACTION_MOVE_TO_TARGET;
        c3 = 0;
        DoFirstAction();
    }

    private void EndCurrent(boolean finishedWell, boolean runPending) {
        final byte oldType = currentType;
        final CNewAction action = actionOf(oldType);

        ++timerGeneration;
        timerArmed = false;

        if (action != null) {
            try {
                action.OnEnd(finishedWell);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // OnEnd가 다른 current를 직접 세팅했다면 덮지 않는다.
        if (currentType == oldType) {
            ClearCurrent();
        }

        if (runPending && currentType == ACTION_NONE) {
            DoPendingAction(0);
        }
    }

    private CNewAction actionOf(byte type) {
        switch (type) {
        case ACTION_MOVE:
            return moveAction;
        case ACTION_ATTACK:
            return attackAction;
        case ACTION_ATTACK_WAIT:
            return attackWaitAction;
        case ACTION_MOVE_TO_TARGET:
            return moveToTargetAction;
        case ACTION_MOVE_TO_TARGET_WAIT:
            return moveToTargetWaitAction;
        case ACTION_SKILL:
            return skillAction;
        case ACTION_GET_ITEM:
            return getItemAction;
        case ACTION_GENERAL:
            return generalAction;
        default:
            return null;
        }
    }

    private void SetCurrent(
            byte type,
            int a0, int a1, int a2, int a3,
            long l0, double d0, Object o0, String s0, int flags) {

        ++timerGeneration;
        timerArmed = false;

        currentType = type;
        c0 = a0;
        c1 = a1;
        c2 = a2;
        c3 = a3;
        cl0 = l0;
        cd0 = d0;
        co0 = o0;
        cs0 = s0;
        cflags = flags;
    }

    private void SetPending(
            byte type,
            int a0, int a1, int a2, int a3,
            long l0, double d0, Object o0, String s0, int flags) {

        pendingType = type;
        p0 = a0;
        p1 = a1;
        p2 = a2;
        p3 = a3;
        pl0 = l0;
        pd0 = d0;
        po0 = o0;
        ps0 = s0;
        pflags = flags;
    }

    private void PromotePending() {
        currentType = pendingType;
        c0 = p0;
        c1 = p1;
        c2 = p2;
        c3 = p3;
        cl0 = pl0;
        cd0 = pd0;
        co0 = po0;
        cs0 = ps0;
        cflags = pflags;
        ClearPending();
    }

    private void ClearCurrent() {
        currentType = ACTION_NONE;
        c0 = c1 = c2 = c3 = 0;
        cl0 = 0L;
        cd0 = 0D;
        co0 = null;
        cs0 = null;
        cflags = 0;
    }

    private void ClearPending() {
        pendingType = ACTION_NONE;
        p0 = p1 = p2 = p3 = 0;
        pl0 = 0L;
        pd0 = 0D;
        po0 = null;
        ps0 = null;
        pflags = 0;
    }

    // ---------------------------------------------------------------------
    // Current payload accessors. Action package only.
    // ---------------------------------------------------------------------

    int i0() { return c0; }
    int i1() { return c1; }
    int i2() { return c2; }
    int i3() { return c3; }
    long l0() { return cl0; }
    double d0() { return cd0; }
    Object o0() { return co0; }
    String s0() { return cs0; }
    int flags() { return cflags; }

    boolean isAutoAttack() {
        return (cflags & FLAG_AUTO_ATTACK) != 0;
    }

    boolean isForceAttack() {
        return (cflags & FLAG_FORCE_ATTACK) != 0;
    }

    boolean MoveStepDirect(int sx, int sy, int heading, long nowMs) {
        return moveAction.MoveStep(actor, sx, sy, heading, nowMs);
    }
}

