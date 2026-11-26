package l1j.server.server.model.npc.ai;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jctools.queues.MpscArrayQueue;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.CNpcMaker;
import l1j.server.server.model.L1World;
import l1j.server.server.model.npc.ai.action.CCreatureController;
import l1j.server.server.model.network.thread.CIOThreadCallback;


public final class CNPCServer {

    private static final Logger _log = Logger.getLogger(CNPCServer.class.getName());

    public static final int WORKERS = 4;

    public static final long TIMER_RESOLUTION_MS = 20L;

    private static final int WHEEL_SIZE = 1 << 12;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;

    private static final int COMMAND_CHUNK = 1024;
    // Bound each pass on the existing NPC owner. Backlogged commands remain
    // queued and are drained in subsequent passes without a new worker/queue.
    private static final int COMMAND_DRAIN_BUDGET = 256;
    // Timer catch-up must yield to queued cancel/register/wake work.
    // Resume from _cursorTick next pass: no timer bucket is skipped.
    private static final int WHEEL_CATCHUP_BUDGET = 64;
    // A single expiry bucket may hold thousands of NPCs. Yield between
    // batches so an unregister/cancel can overtake the remaining expiries.
    private static final int WHEEL_NPC_BUDGET = 128;
    private static final int CORPSE_POOL_CAPACITY = 4096;
    // Fixed reserve for skill mutations when retryable timer events exhaust
    // the common pool. Never grow an owner queue in the submission path.
    private static final int SKILL_RESERVE_CAPACITY = 512;
    // Every queued NPC event owns exactly one preallocated lease, including
    // the skill reserve. A single shard can therefore never hold more than
    // 4096 + 512 events. The next power of two keeps the MPSC ring fixed
    // without allocations as the owner catches up.
    private static final int EVENT_QUEUE_CAPACITY = 8192;
    private static final int CORPSE_DRAIN_BUDGET = 64;

    private static final VarHandle SHARD_WAKE_GATE;
    static {
        try {
            SHARD_WAKE_GATE = MethodHandles.lookup().findVarHandle(
                    Shard.class, "_wakeGate", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final CNPCServer INSTANCE = new CNPCServer();

    public static CNPCServer getInstance() {
        return INSTANCE;
    }

    private static final class CorpseEvent {
        boolean skillReserved;
        L1NpcInstance npc;
        int generation;
        long smartId;
        int timerId;
        CNpcMaker maker;
        byte skillOp;
        int skillId;
        int skillDuration;
        byte kind;
        CIOThreadCallback.WheelTask wheelTask;
        byte action;
        byte pendingAction;
        long delayMs;
    }

    private static final byte EVENT_CORPSE = 0;
    private static final byte EVENT_TIMER = 1;
    private static final byte EVENT_MAKER = 2;
    private static final byte EVENT_SKILL = 3;
    private static final byte EVENT_CANCEL = 5;
    private static final byte EVENT_WHEEL_NPC = 6;
    private static final byte EVENT_ACTION = 7;
    private static final byte EVENT_ARM_ACTION = 8;

    private final MpmcArrayQueue<CorpseEvent> _corpseFree =
            new MpmcArrayQueue<>(CORPSE_POOL_CAPACITY);
    private final MpmcArrayQueue<CorpseEvent> _skillReserveFree =
            new MpmcArrayQueue<>(SKILL_RESERVE_CAPACITY);
    private final Shard[] _shards = new Shard[WORKERS];
    private boolean started;
    private volatile boolean _running = true;
    // A producer that was admitted before shutdown must finish publishing
    // before an owner drains/clears its queues. The counter avoids a monitor,
    // lock or allocation on the hot producer path.
    private final AtomicInteger _activePosts = new AtomicInteger();

    private boolean beginPost() {
        if (!_running) return false;
        _activePosts.incrementAndGet();
        if (_running) return true;
        _activePosts.decrementAndGet();
        return false;
    }

    private void endPost() {
        _activePosts.decrementAndGet();
    }


    private CNPCServer() {
        for (int i = 0; i < CORPSE_POOL_CAPACITY + SKILL_RESERVE_CAPACITY; i++) {
            final CorpseEvent event = new CorpseEvent();
            if (i < CORPSE_POOL_CAPACITY) {
                _corpseFree.offer(event);
            } else {
                event.skillReserved = true;
                _skillReserveFree.offer(event);
            }
        }
        for (int i = 0; i < WORKERS; i++) {
            _shards[i] = new Shard(i);
        }
    }

    /** Initial spawn registrations can queue before the world is ready to run AI. */
    public synchronized void start() {
        if (!_running) throw new IllegalStateException("NPC server is stopped");
        if (started) return;
        try {
            for (Shard shard : _shards) shard.start();
            started = true;
        } catch (RuntimeException | Error failure) {
            shutdown();
            throw failure;
        }
    }

    private static int shardIndex(L1NpcInstance npc) {
        return (npc.getId() & 0x7fffffff) & (WORKERS - 1);
    }

    /** AI가 시작될 때 최초 deadline을 owner timer에 등록한다. */
    public void register(L1NpcInstance npc) {
        if (!beginPost()) return;
        try {
        if (npc == null || !_running) {
            return;
        }
        if (!npc.aiEngineTryQueueRegister()) return;
        final Shard shard = _shards[shardIndex(npc)];
        shard._register.offer(npc);
        shard.signalWork();
    
        } finally {
            endPost();
        }
    }

    /** AI가 sleep/종료/삭제될 때 wheel에서 제거한다. */
    public void unregister(L1NpcInstance npc) {
        if (!beginPost()) return;
        try {
        if (npc == null || !_running) {
            return;
        }
        if (!npc.aiEngineTryQueueUnregister()) return;
        final Shard shard = _shards[shardIndex(npc)];
        shard._unregister.offer(npc);
        shard.signalWork();
    
        } finally {
            endPost();
        }
    }

    /**
     * damage/hate/perceive/cast-complete 같은 외부 사건으로 owner를 즉시 깨운다.
     * 동일 NPC에 wake가 폭주해도 CAS 하나로 한 번만 queue에 들어간다.
     */
    /** Accept a lease and publish through the existing coalesced wake.
     * Never execute arbitrary AI callbacks on an external producer.
     */
    public void HandleEvent(L1NpcInstance npc, CNPCEvent event) {
        if (!beginPost()) {
            CNPCEvent.Release(event);
            return;
        }
        try {
            if (npc == null) {
                CNPCEvent.Release(event);
                return;
            }
            if (npc.PostNpcEvent(event) && npc.aiEngineIsRunning()) {
                signalDeferred(npc);
            }
        } finally {
            endPost();
        }
    }

    public void wake(L1NpcInstance npc) {
        if (!beginPost()) return;
        try {
        if (npc == null || !_running || !npc.aiEngineIsRunning()) {
            return;
        }
        if (!npc.aiEngineTryQueueWake()) {
            return;
        }

        final Shard shard = _shards[shardIndex(npc)];
        shard._wake.offer(npc);
        shard.signalWork();
    
        } finally {
            endPost();
        }
    }

    public void scheduleDelete(L1NpcInstance npc, int delayMs) {
        if (!beginPost()) return;
        try {
        if (npc == null || delayMs <= 0 || !_running || npc.isDestroyed()) {
            return;
        }
        npc.npcDeleteDeadlineMs(System.currentTimeMillis() + (long) delayMs);
        if (!npc.aiEngineTryQueueRegister()) return;
        final Shard shard = _shards[shardIndex(npc)];
        shard._register.offer(npc);
        shard.signalWork();
    
        } finally {
            endPost();
        }
    }

    public boolean enqueueCorpse(L1NpcInstance npc, int generation, long smartId) {
        if (!beginPost()) return false;
        try {
        if (!_running || npc == null || smartId == 0L || generation == 0) {
            return false;
        }
        final CorpseEvent event = _corpseFree.poll();
        if (event == null) {
            return false;
        }
        event.npc = npc;
        event.generation = generation;
        event.smartId = smartId;
        final Shard shard = _shards[shardIndex(npc)];
        if (!shard._corpse.offer(event)) {
            event.npc = null;
            event.generation = 0;
            event.smartId = 0L;
            _corpseFree.offer(event);
            return false;
        }
        shard.signalWork();
        return true;
    
        } finally {
            endPost();
        }
    }

    /** Work admission remains open while loaders queue initial registrations. */
    public boolean isRunning() { return _running; }

    public boolean isOwnerThread(L1NpcInstance npc) {
        return npc != null && Thread.currentThread() == _shards[shardIndex(npc)]._thread;
    }

    /** CNpcAI normally calls this inline on its existing NPC owner. */
    public void submitAction(L1NpcInstance npc, byte action, byte pending) {
        if (!beginPost()) {
            // CCreatureController.submit claimed cadence before reaching here.
            // Shutdown must not leave that NPC action permanently claimed.
            if (npc != null) npc.npcActionReleaseCadence();
            return;
        }
        try {
        if (npc == null || !_running) {
            if (npc != null) npc.npcActionReleaseCadence();
            return;
        }
        if (isOwnerThread(npc)) {
            if (action == CCreatureController.ACTION_NONE) CCreatureController.cancelFromAi(npc);
            else CCreatureController.acceptFromAi(npc, action, pending);
        } else {
            // Preserve exact action and pending intent across the owner boundary.
            final CorpseEvent e = obtainEvent();
            if (e == null) {
                // No command was admitted. Release the AI's claimed cadence
                // and ask the existing coalesced wake path to re-evaluate.
                npc.npcActionReleaseCadence();
                signalDeferred(npc);
                return;
            }
            e.generation = npc.npcActionAdmissionEpoch();
            e.kind = EVENT_ACTION;
            e.npc = npc;
            e.action = action;
            e.pendingAction = pending;
            publish(npc.getId(), e);
        }
    
        } finally {
            endPost();
        }
    }

    public void cancelAction(L1NpcInstance npc) {
        if (!beginPost()) {
            if (npc != null) {
                npc.npcActionInvalidateGeneration();
                npc.npcActionReleaseCadence();
            }
            return;
        }
        try {
            if (npc == null) return;
            if (isOwnerThread(npc)) {
                synchronized (npc) {
                    CCreatureController.cancelFromAi(npc);
                    npc.npcControllerDisarm();
                }
                // Disarming from outside the expiry bucket must remove the
                // old wheel deadline, not leave a canceled timer linked.
                _shards[shardIndex(npc)].scheduleNext(npc);
                return;
            }
            if (!_running) {
                npc.npcActionInvalidateGeneration();
                npc.npcActionReleaseCadence();
                return;
            }
            // Cancel must invalidate the old tick BEFORE it can run again,
            // even if thousands of pooled commands are ahead of it. Use the
            // existing per-NPC coalesced wake as the owner handoff: no pool
            // lease is needed, and the owner processes cancel before timers.
            synchronized (npc) {
                npc.npcActionRequestDeferredCancel();
                npc.npcActionInvalidateGeneration();
                npc.npcActionReleaseCadence();
            }
            signalDeferred(npc);
        } finally {
            endPost();
        }
    }

    public void armActionTimer(L1NpcInstance npc, int generation, long delayMs) {
        if (!beginPost()) return;
        try {
        if (npc == null || generation == 0 || delayMs <= 0L || !_running
                || generation != npc.npcActionGeneration()) return;
        if (!isOwnerThread(npc)) {
            final CorpseEvent e = obtainEvent();
            if (e == null) {
                // A deadline is replaceable by a newer generation. Preserve
                // its latest exact due-time in the NPC itself, not a heap event.
                npc.npcActionDeferArm(generation, System.currentTimeMillis() + delayMs);
                signalDeferred(npc);
                return;
            }
            e.kind = EVENT_ARM_ACTION;
            e.npc = npc;
            e.generation = generation;
            // Capture the due time at admission, not when the NPC owner
            // eventually drains a backlogged event queue.
            e.delayMs = System.currentTimeMillis() + delayMs;
            publish(npc.getId(), e);
            return;
        }
        npc.npcControllerArm(System.currentTimeMillis() + delayMs, generation);
        // The caller's processBucket() will scheduleNext() after the action.
        // If called by an AI wake outside processBucket, also register it now.
        _shards[shardIndex(npc)].scheduleNext(npc);
    
        } finally {
            endPost();
        }
    }

    /** Reuse the existing NPC owner completion transport for wheel expirations. */
    public boolean postNpcTimer(L1NpcInstance npc, long smartId, int timerId, int generation) {
        if (!beginPost()) return false;
        try {
        if (npc == null || !_running || smartId == 0L) return false;
        // A wheel owner retries the SAME timer node if this fixed pool is
        // exhausted. Do not allocate one new event per expired timer.
        final CorpseEvent e = _corpseFree.poll();
        if (e == null) return false;
        e.kind = EVENT_TIMER;
        e.npc = npc;
        e.smartId = smartId;
        e.timerId = timerId;
        e.generation = generation;
        publish(npc.getId(), e);
        return true;
    
        } finally {
            endPost();
        }
    }

    public boolean postMakerTimer(CNpcMaker maker, int timerId, int generation) {
        if (!beginPost()) return false;
        try {
        if (maker == null || !_running) return false;
        final CorpseEvent e = _corpseFree.poll();
        if (e == null) return false;
        e.kind = EVENT_MAKER;
        e.maker = maker;
        e.timerId = timerId;
        e.generation = generation;
        final int id = maker.getId() > 0 ? maker.getId() : maker.getNpcId();
        publish(id, e);
        return true;
    
        } finally {
            endPost();
        }
    }

    /** Retired-NPC teardown is never rejected by the bounded skill-event pool.
     * The existing per-NPC wake ticket serializes the clear on its NPC shard,
     * even when deletion originates in an event/controller thread.
     */
    public void clearDestroyedNpcSkills(L1NpcInstance npc) {
        if (npc == null || !npc.isDestroyed() || !beginPost()) return;
        try {
            if (isOwnerThread(npc)) {
                npc.skillEffectClearFromOwner();
                npc.npcSkillHeartbeatClearDeferredArm();
                npc.npcSkillHeartbeatClearDeferredTick();
            } else {
                npc.npcSkillRequestDestroyClear();
                signalDeferred(npc);
            }
        } finally {
            endPost();
        }
    }

    public boolean postNpcSkill(L1NpcInstance npc, byte op, int skillId, int duration) {
        if (!beginPost()) return false;
        try {
        if (npc == null || npc.isDestroyed() || !_running) return false;
        if (isOwnerThread(npc)) {
            CIOThreadCallback.getInstance().applyNpcCharacterSkill(npc, op, skillId, duration);
            return true;
        }
        // ARM is idempotent and the actual mutation already runs on the
        // NPC owner. Coalesce this heartbeat request through the existing
        // per-NPC wake ticket rather than consuming a scarce skill lease.
        if (op == 5) {
            npc.npcSkillHeartbeatRequestDeferredArm();
            signalDeferred(npc);
            return true;
        }
        CorpseEvent e = _corpseFree.poll();
        if (e == null) e = _skillReserveFree.poll();
        if (e == null) return false;
        e.kind = EVENT_SKILL;
        e.npc = npc;
        e.skillOp = op;
        e.skillId = skillId;
        e.skillDuration = duration;
        publish(npc.getId(), e);
        return true;
    
        } finally {
            endPost();
        }
    }

    public boolean postNpcSkillHeartbeat(L1NpcInstance npc, int generation) {
        if (!beginPost()) return false;
        try {
            if (npc == null || !_running || generation == 0) return false;
            // The CIO timer owns only the deadline; the NPC owner owns the
            // effect mutation. A stale wheel callback is consumed normally.
            if (!npc.skillEffectHeartbeatValid(generation)) return true;
            // At most one live expiration is outstanding per NPC. The
            // per-NPC wake ticket is already bounded by the NPC population;
            // unrelated timer traffic cannot starve this expiration.
            npc.npcSkillHeartbeatRequestDeferredTick(generation);
            signalDeferred(npc);
            return true;
        } finally {
            endPost();
        }
    }

    /** Existing pooled NPC completion transport also delivers rare effect ticks. */
    public boolean postNpcWheelTask(L1NpcInstance npc, CIOThreadCallback.WheelTask task) {
        if (!beginPost()) return false;
        try {
        if (npc == null || task == null || !_running) return false;
        final CorpseEvent e = _corpseFree.poll();
        if (e == null) return false;
        e.kind = EVENT_WHEEL_NPC;
        e.npc = npc;
        e.wheelTask = task;
        publish(npc.getId(), e);
        return true;
    
        } finally {
            endPost();
        }
    }

    /** Death settlement uses the existing coalesced wake, even with AI stopped. */
    public boolean postNpcDeath(L1NpcInstance npc, Runnable task) {
        if (!beginPost()) return false;
        try {
            if (npc == null || !npc.PublishDeathSettlement(task)) return false;
            signalDeferred(npc);
            return true;
        } finally {
            endPost();
        }
    }

    // Used only after a producer has been admitted by beginPost(). Unlike
    // ordinary AI wake, cancellation must reach the owner even if AI stopped.
    private void signalDeferred(L1NpcInstance npc) {
        if (!npc.aiEngineTryQueueWake()) return;
        final Shard shard = _shards[shardIndex(npc)];
        shard._wake.offer(npc);
        shard.signalWork();
    }

    private CorpseEvent obtainEvent() {
        final CorpseEvent event = _corpseFree.poll();
        // All successful posts own one of the preallocated 4,096 leases.
        // No allocation is permitted under owner/backpressure saturation.
        return event;
    }

    private void publish(int objectId, CorpseEvent e) {
        final int index = objectId & (WORKERS - 1);
        final Shard shard = _shards[index];
        if (!shard._corpse.offer(e)) {
            // Cannot happen for correctly leased events: across ALL shards
            // at most 4,608 leases exist. Do not silently drop an accepted
            // operation if the invariant is violated by a future producer.
            e.npc = null;
            e.maker = null;
            e.wheelTask = null;
            e.skillOp = 0;
            e.skillId = 0;
            e.skillDuration = 0;
            e.kind = EVENT_CORPSE;
            if (e.skillReserved) _skillReserveFree.offer(e);
            else _corpseFree.offer(e);
            throw new IllegalStateException("NPC event queue exceeded preallocated leases");
        }
        shard.signalWork();
    }

    public synchronized void shutdown() {
        if (!_running) return;
        _running = false;
        // The only wait is on the shutdown thread, not a CIO/NPC producer.
        // A post admitted just before close must finish before the owner
        // clears its final pending queue, otherwise NPC refs can be retained.
        while (_activePosts.get() != 0) {
            LockSupport.parkNanos(this, 100_000L);
        }
        for (Shard shard : _shards) {
            final Thread owner = shard._thread;
            if (owner == null) {
                shard.clearOwnerState();
            } else {
                LockSupport.unpark(owner);
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (_running) return false;
        final long budget = unit.toNanos(timeout);
        final long startedAt = System.nanoTime();
        for (Shard shard : _shards) {
            final Thread owner = shard._thread;
            if (owner == null) continue;
            if (owner == Thread.currentThread()) return false;
            while (owner.isAlive()) {
                final long remaining = budget - (System.nanoTime() - startedAt);
                if (remaining <= 0L) return false;
                TimeUnit.NANOSECONDS.timedJoin(owner, remaining);
            }
        }
        return true;
    }

    private final class Shard implements Runnable {

        private final int _id;

        /** 다중 producer -> 단일 NPC owner. queue item은 NPC reference뿐이며 command object를 만들지 않는다. */
        private final MpscUnboundedArrayQueue<L1NpcInstance> _register = new MpscUnboundedArrayQueue<>(COMMAND_CHUNK);
        private final MpscUnboundedArrayQueue<L1NpcInstance> _unregister = new MpscUnboundedArrayQueue<>(COMMAND_CHUNK);
        private final MpscUnboundedArrayQueue<L1NpcInstance> _wake = new MpscUnboundedArrayQueue<>(COMMAND_CHUNK);
        private final MpscArrayQueue<CorpseEvent> _corpse = new MpscArrayQueue<>(EVENT_QUEUE_CAPACITY);

        private final L1NpcInstance[] _wheelHeads = new L1NpcInstance[WHEEL_SIZE];

        private volatile Thread _thread;
        // 0: running, 1: work posted, 2: parked or about to park.
        // Queue publication always happens before signalWork().
        private volatile int _wakeGate;
        private long _cursorTick;
        private int _scheduledCount;
        // Owner-only detached expiry chain. The bucket index remains on each
        // waiting NPC so unregister/scheduleNext can unlink it in O(1).
        private L1NpcInstance _pendingBucketHead;
        private int _pendingBucketIndex = -1;

        private Shard(int id) {
            _id = id;
            _cursorTick = System.currentTimeMillis() / TIMER_RESOLUTION_MS;
        }

        private void start() {
            final Thread t = new Thread(this, "NpcAiOwner-" + _id);
            t.setDaemon(true);
            _thread = t;
            try {
                t.start();
            } catch (RuntimeException | Error failure) {
                _thread = null;
                throw failure;
            }
        }

        private void signalWork() {
            final Thread target = _thread;
            if (target == null) return;
            if (_wakeGate == 1) return;
            final int previous = (int) SHARD_WAKE_GATE.getAndSet(this, 1);
            if (previous == 2) LockSupport.unpark(target);
        }

        private boolean preparePark() {
            if (SHARD_WAKE_GATE.compareAndSet(this, 0, 2)) return true;
            // A producer posted work while the owner was processing commands.
            SHARD_WAKE_GATE.compareAndSet(this, 1, 0);
            return false;
        }

        private void finishPark() {
            // Leave 2->1 intact: a producer may have woken us during park.
            SHARD_WAKE_GATE.compareAndSet(this, 2, 0);
        }

        @Override
        public void run() {
            // Shutdown may race with a producer already past its running
            // check. Keep the owner alive until all admitted posts finish.
            while (_running || _activePosts.get() != 0) {
                try {
                    if (_scheduledCount == 0 && _pendingBucketIndex < 0) {
                        final long currentTick = System.currentTimeMillis() / TIMER_RESOLUTION_MS;
                        if (_cursorTick < currentTick) {
                            _cursorTick = currentTick;
                        }
                    }

                    drainCommands();
                    // Commands may have removed every NPC left in a detached
                    // expiry batch. Advance that completed tick before parking
                    // or inspecting a newly scheduled future wheel entry.
                    if (_pendingBucketIndex >= 0 && _pendingBucketHead == null) {
                        _pendingBucketIndex = -1;
                        _cursorTick++;
                    }

                    if (_scheduledCount == 0) {
                        if (hasQueuedCommands()) {
                            continue;
                        }
                        if (preparePark()) {
                            try {
                                LockSupport.park(this);
                            } finally {
                                finishPark();
                            }
                        }
                        continue;
                    }

                    final long nowMs = System.currentTimeMillis();
                    final long targetTick = nowMs / TIMER_RESOLUTION_MS;

                    int processedBuckets = 0;
                    while (_cursorTick <= targetTick && processedBuckets < WHEEL_CATCHUP_BUDGET) {
                        if (!processBucket((int) (_cursorTick & WHEEL_MASK), nowMs)) {
                            // An expiry bucket still contains NPCs. Process
                            // commands before resuming the SAME tick.
                            break;
                        }
                        _cursorTick++;
                        processedBuckets++;
                    }

                    // command가 처리 중 들어왔을 수 있으므로 owner-local state를 다시 확인한다.
                    drainCommands();

                    if (_scheduledCount == 0 || hasQueuedCommands() || _cursorTick <= targetTick) {
                        // Do not park while catching up. Drain commands first
                        // on the next pass even under a very large timer backlog.
                        continue;
                    }

                    final long nextBoundaryMs = _cursorTick * TIMER_RESOLUTION_MS;
                    final long sleepMs = nextBoundaryMs - System.currentTimeMillis();
                    if (sleepMs > 0L && preparePark()) {
                        try {
                            LockSupport.parkNanos(this, sleepMs * 1_000_000L);
                        } finally {
                            finishPark();
                        }
                    }
                } catch (Throwable t) {
                    _log.log(Level.WARNING, "[NpcAiEngine] owner exception shard=" + _id, t);
                    LockSupport.parkNanos(this, 1_000_000L);
                }
            }

            clearOwnerState();
        }

        private boolean hasQueuedCommands() {
            return _unregister.peek() != null || _register.peek() != null
                    || _wake.peek() != null || _corpse.peek() != null;
        }

        private void drainCommands() {
            L1NpcInstance npc;
            int count;

            // stop/cancel을 가장 먼저 반영한다.
            count = 0;
            while (count++ < COMMAND_DRAIN_BUDGET
                    && (npc = _unregister.poll()) != null) {
                npc.aiEngineClearQueuedUnregister();
                synchronized (npc) {
                    if (npc.aiEngineIsRunning()) {
                        // An unregister from a prior life/stop must not disarm
                        // a restarted NPC or overwrite its new AI deadline.
                        scheduleNext(npc);
                        continue;
                    }
                    unlink(npc);
                    npc.npcControllerDisarm();
                // The wake ticket is still in _wake. Releasing its CAS here
                // allows another producer to enqueue the same NPC again
                // before the owner drains the first wake, defeating bounded
                // per-NPC coalescing under repeated stop/start races.
                // Only the _wake consumer releases its own queued ticket.
                    npc.aiEngineDeadlineMs(0L);
                    scheduleNext(npc);
                }
            }

            count = 0;
            while (count++ < COMMAND_DRAIN_BUDGET
                    && (npc = _register.poll()) != null) {
                npc.aiEngineClearQueuedRegister();
                if (npc.aiEngineIsRunning() && npc.aiEngineDeadlineMs() <= 0L) {
                    npc.aiEngineDeadlineMs(System.currentTimeMillis() + 1L);
                }
                scheduleNext(npc);
            }

            count = 0;
            while (count++ < COMMAND_DRAIN_BUDGET
                    && (npc = _wake.poll()) != null) {
                npc.aiEngineClearQueuedWake();
                if (npc.npcSkillConsumeDestroyClear()) {
                    try {
                        npc.skillEffectClearFromOwner();
                        npc.npcSkillHeartbeatClearDeferredArm();
                        npc.npcSkillHeartbeatClearDeferredTick();
                    } catch (Throwable failure) {
                        _log.log(Level.SEVERE, "[NPC-SKILL] destroy clear failed shard=" + _id,
                                failure);
                    }
                }
                if (npc.npcSkillHeartbeatConsumeDeferredArm() && !npc.isDestroyed()) {
                    try {
                        CIOThreadCallback.getInstance().applyNpcCharacterSkill(npc,
                                (byte) 5, 0, 0);
                    } catch (Throwable failure) {
                        _log.log(Level.SEVERE, "[NPC-SKILL] arm failure shard=" + _id,
                                failure);
                    }
                }
                final int skillExpiryGeneration = npc.npcSkillHeartbeatConsumeDeferredTick();
                if (skillExpiryGeneration != 0 && !npc.isDestroyed()) {
                    try {
                        CIOThreadCallback.getInstance().runNpcSkillHeartbeatFromOwner(npc,
                                skillExpiryGeneration);
                    } catch (Throwable failure) {
                        // A broken effect callback must not strand later
                        // cancels, registrations or other NPC owner commands.
                        _log.log(Level.SEVERE, "[NPC-SKILL] expiry failure shard=" + _id,
                                failure);
                    }
                }
                if (npc.npcActionHasDeferredCancel()) {
                    synchronized (npc) {
                        // Resurrection may have retired the pending request.
                        if (npc.npcActionConsumeDeferredCancel()) {
                            CCreatureController.cancelFromAi(npc);
                            npc.npcControllerDisarm();
                        }
                    }
                }
                npc.npcActionApplyDeferredArm();
                try {
                    npc.DispatchDeathSettlement();
                } catch (Throwable failure) {
                    _log.log(Level.SEVERE, "[NPC-DEATH] settlement dispatch failed", failure);
                }
                if (npc.aiEngineIsRunning()) {
                    schedule(npc, System.currentTimeMillis());
                } else {
                    scheduleNext(npc);
                }
            }

            for (int i = 0; i < CORPSE_DRAIN_BUDGET; i++) {
                final CorpseEvent event = _corpse.poll();
                if (event == null) break;
                try {
                    switch (event.kind) {
                    case EVENT_CORPSE:
                        event.npc.processCorpseDelete(event.generation, event.smartId);
                        break;
                    case EVENT_TIMER:
                        if (L1World.getInstance().findObjectBySmartId(event.smartId) == event.npc
                                && !event.npc.handleCorpseDeleteTimer(event.timerId, event.generation)
                                && !event.npc.handleDigestTimer(event.timerId, event.generation)) {
                            event.npc.TimerExpired(event.timerId, event.generation);
                        }
                        break;
                    case EVENT_MAKER:
                        event.maker.TimerExpired(event.timerId, event.generation);
                        break;
                    case EVENT_SKILL:
                        // The NPC may have been destroyed after admission.
                        // Never reapply an obsolete effect to a retired owner.
                        if (event.npc != null && !event.npc.isDestroyed()) {
                            CIOThreadCallback.getInstance().applyNpcCharacterSkill(event.npc,
                                    event.skillOp, event.skillId, event.skillDuration);
                        }
                        break;
                    case EVENT_WHEEL_NPC:
                        if (event.npc != null && event.wheelTask != null) {
                            event.wheelTask.runOnNpc(event.npc);
                        }
                        break;
                    case EVENT_ACTION:
                        if (event.npc != null && event.npc.aiEngineIsRunning()
                                && event.generation == event.npc.npcActionAdmissionEpoch()) {
                            if (event.action == CCreatureController.ACTION_NONE) {
                                synchronized (event.npc) {
                                    if (event.npc.aiEngineIsRunning()
                                            && event.generation == event.npc.npcActionAdmissionEpoch()) {
                                        CCreatureController.cancelFromAi(event.npc);
                                    }
                                }
                            } else {
                                CCreatureController.acceptFromAi(event.npc, event.action, event.pendingAction);
                            }
                        }
                        break;
                    case EVENT_ARM_ACTION:
                        if (event.npc != null && event.npc.aiEngineIsRunning()
                                && event.generation != 0 && event.delayMs > 0L
                                && event.generation == event.npc.npcActionGeneration()) {
                            // A canceled/replaced action cannot resurrect its
                            // old timer. Preserve the original admission deadline.
                            event.npc.npcControllerArm(event.delayMs, event.generation);
                            scheduleNext(event.npc);
                        }
                        break;
                    case EVENT_CANCEL:
                        if (event.npc != null) {
                            synchronized (event.npc) {
                                if (event.generation == event.npc.npcActionAdmissionEpoch()) {
                                    CCreatureController.cancelFromAi(event.npc);
                                    event.npc.npcControllerDisarm();
                                }
                            }
                            scheduleNext(event.npc);
                        }
                        break;
                    default:
                        break;
                    }
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[NPC-OWNER] event failure shard=" + _id, t);
                } finally {
                    event.npc = null;
                    event.maker = null;
                    event.wheelTask = null;
                    event.action = 0;
                    event.pendingAction = 0;
                    event.delayMs = 0L;
                    event.generation = 0;
                    event.smartId = 0L;
                    event.timerId = 0;
                    event.skillOp = 0;
                    event.skillId = 0;
                    event.skillDuration = 0;
                    event.kind = EVENT_CORPSE;
                    if (event.skillReserved) _skillReserveFree.offer(event);
                    else _corpseFree.offer(event);
                }
            }

        }

        private boolean processBucket(int bucket, long nowMs) {
            if (_pendingBucketIndex < 0) {
                _pendingBucketHead = _wheelHeads[bucket];
                _wheelHeads[bucket] = null;
                _pendingBucketIndex = bucket;
            }
            int processed = 0;
            while (_pendingBucketHead != null && processed++ < WHEEL_NPC_BUDGET) {
                final L1NpcInstance npc = _pendingBucketHead;
                final L1NpcInstance next = npc.aiEngineWheelNext();
                _pendingBucketHead = next;
                if (next != null) next.aiEngineWheelPrev(null);

                npc.aiEngineWheelPrev(null);
                npc.aiEngineWheelNext(null);
                npc.aiEngineWheelBucket(-1);
                _scheduledCount--;

                if (npc.isDestroyed()) {
                    continue;
                }

                final long deleteAt = npc.npcDeleteDeadlineMs();
                if (deleteAt > 0L && deleteAt <= nowMs) {
                    try {
                        npc.TimerExpired(L1NpcInstance.TIMER_TIMED_DELETE,
                                npc.npcDeleteGeneration());
                    } catch (Throwable t) {
                        _log.log(Level.SEVERE,
                                "[NPC-DELETE] failed shard=" + _id + " npc=" + safeNpcId(npc), t);
                    }
                    scheduleNext(npc);
                    continue;
                }

                final long regenDue = npc.npcRegenerationDeadlineMs();
                if (regenDue > 0L && regenDue <= nowMs) {
                    try {
                        npc.tickRegeneration(nowMs);
                    } catch (Throwable failure) {
                        _log.log(Level.SEVERE, "[NPC-REGEN] failed npc=" + safeNpcId(npc), failure);
                    }
                }

                // Dialogue is independent of combat AI, including corpses.
                final long chatDue = npc.npcChatDeadlineMs();
                if (chatDue > 0L && chatDue <= nowMs) {
                    try {
                        npc.tickNpcChat(nowMs);
                    } catch (Throwable t) {
                        _log.log(Level.WARNING, "[NPC-CHAT] failed shard=" + _id
                                + " npc=" + safeNpcId(npc), t);
                    }
                }

                if (!npc.aiEngineIsRunning()) {
                    scheduleNext(npc);
                    continue;
                }

                final long actionDue = npc.npcControllerDeadlineMs();
                if (actionDue > 0L && actionDue <= nowMs) {
                    final int generation = npc.npcControllerDueGeneration();
                    npc.npcControllerDisarm();
                    try {
                        CCreatureController.TimerExpired(npc, generation);
                    } catch (Throwable t) {
                        _log.log(Level.SEVERE, "[NPC-ACTION] tick failed npc=" + safeNpcId(npc), t);
                    }
                }

                final long aiDeadline = npc.aiEngineDeadlineMs();
                if (aiDeadline <= 0L || aiDeadline > nowMs) {
                    scheduleNext(npc);
                    continue;
                }

                try {
                    final long nextDeadline = npc.aiEngineStep(nowMs);
                    npc.aiEngineDeadlineMs(nextDeadline > 0L
                            && npc.aiEngineIsRunning() ? nextDeadline : 0L);
                } catch (Throwable t) {
                    _log.log(Level.WARNING,
                            "[NpcAiEngine] AI exception shard=" + _id + " npc=" + safeNpcId(npc), t);
                    if (npc.aiEngineIsRunning()) {
                        npc.aiEngineDeadlineMs(nowMs + 1000L);
                    }
                }

                scheduleNext(npc);
            }
            if (_pendingBucketHead != null) return false;
            _pendingBucketIndex = -1;
            return true;
        }

        private void schedule(L1NpcInstance npc, long deadlineMs) {
            npc.aiEngineDeadlineMs(deadlineMs);
            scheduleNext(npc);
        }

        private void scheduleNext(L1NpcInstance npc) {
            unlink(npc);

            if (npc.isDestroyed()) {
                return;
            }

            long deadlineMs = 0L;
            if (npc.aiEngineIsRunning()) {
                deadlineMs = npc.aiEngineDeadlineMs();
            }

            final long regenDue = npc.npcRegenerationDeadlineMs();
            if (regenDue > 0L && (deadlineMs <= 0L || regenDue < deadlineMs)) {
                deadlineMs = regenDue;
            }
            final long chatDue = npc.npcChatDeadlineMs();
            if (chatDue > 0L && (deadlineMs <= 0L || chatDue < deadlineMs)) {
                deadlineMs = chatDue;
            }
            final long deleteAt = npc.npcDeleteDeadlineMs();
            if (deleteAt > 0L && (deadlineMs <= 0L || deleteAt < deadlineMs)) {
                deadlineMs = deleteAt;
            }
            if (npc.aiEngineIsRunning()) {
                final long actionDue = npc.npcControllerDeadlineMs();
                if (actionDue > 0L && (deadlineMs <= 0L || actionDue < deadlineMs)) {
                    deadlineMs = actionDue;
                }
            }

            if (deadlineMs <= 0L) {
                return;
            }

            long deadlineTick = (deadlineMs + TIMER_RESOLUTION_MS - 1L) / TIMER_RESOLUTION_MS;
            if (deadlineTick <= _cursorTick) {
                deadlineTick = _cursorTick + 1L;
            }

            final int bucket = (int) (deadlineTick & WHEEL_MASK);
            final L1NpcInstance oldHead = _wheelHeads[bucket];

            npc.aiEngineWheelBucket(bucket);
            npc.aiEngineWheelPrev(null);
            npc.aiEngineWheelNext(oldHead);

            if (oldHead != null) {
                oldHead.aiEngineWheelPrev(npc);
            }
            _wheelHeads[bucket] = npc;
            _scheduledCount++;
        }

        private void unlink(L1NpcInstance npc) {
            final int bucket = npc.aiEngineWheelBucket();
            if (bucket < 0) {
                return;
            }

            final L1NpcInstance prev = npc.aiEngineWheelPrev();
            final L1NpcInstance next = npc.aiEngineWheelNext();

            if (prev != null) {
                prev.aiEngineWheelNext(next);
            } else if (_pendingBucketIndex == bucket && _pendingBucketHead == npc) {
                _pendingBucketHead = next;
            } else if (_wheelHeads[bucket] == npc) {
                _wheelHeads[bucket] = next;
            }

            if (next != null) {
                next.aiEngineWheelPrev(prev);
            }

            npc.aiEngineWheelPrev(null);
            npc.aiEngineWheelNext(null);
            npc.aiEngineWheelBucket(-1);
            if (_scheduledCount > 0) {
                _scheduledCount--;
            }
        }

        private void clearOwnerState() {
            // CNPCServer is a static singleton. Stop paths must not retain NPCs
            // and timer payloads in its queues after the owner exits.
            L1NpcInstance queued;
            while ((queued = _unregister.poll()) != null) {
                queued.aiEngineClearQueuedUnregister();
                queued.aiEngineClearQueuedWake();
            }
            while ((queued = _register.poll()) != null) {
                queued.aiEngineClearQueuedRegister();
                queued.aiEngineClearQueuedWake();
            }
            while ((queued = _wake.poll()) != null) {
                queued.aiEngineClearQueuedWake();
                queued.npcSkillConsumeDestroyClear();
                queued.npcSkillHeartbeatClearDeferredArm();
                queued.npcSkillHeartbeatClearDeferredTick();
                queued.npcActionClearDeferredCommands();
                try {
                    queued.DispatchDeathSettlement();
                } catch (Throwable failure) {
                    _log.log(Level.SEVERE, "[NPC-DEATH] shutdown settlement dispatch failed", failure);
                }
            }
            CorpseEvent event;
            while ((event = _corpse.poll()) != null) {
                event.npc = null;
                event.maker = null;
                event.wheelTask = null;
                event.generation = 0;
                event.smartId = 0L;
                event.timerId = 0;
                event.skillId = 0;
                event.skillDuration = 0;
                event.delayMs = 0L;
                event.action = 0;
                event.pendingAction = 0;
                event.skillOp = 0;
                event.kind = EVENT_CORPSE;
                if (event.skillReserved) _skillReserveFree.offer(event);
                else _corpseFree.offer(event);
            }
            L1NpcInstance pending = _pendingBucketHead;
            _pendingBucketHead = null;
            _pendingBucketIndex = -1;
            while (pending != null) {
                final L1NpcInstance next = pending.aiEngineWheelNext();
                pending.aiEngineWheelPrev(null);
                pending.aiEngineWheelNext(null);
                pending.aiEngineWheelBucket(-1);
                pending.aiEngineClearQueuedWake();
                pending = next;
            }
            for (int i = 0; i < _wheelHeads.length; i++) {
                L1NpcInstance npc = _wheelHeads[i];
                _wheelHeads[i] = null;
                while (npc != null) {
                    final L1NpcInstance next = npc.aiEngineWheelNext();
                    npc.aiEngineWheelPrev(null);
                    npc.aiEngineWheelNext(null);
                    npc.aiEngineWheelBucket(-1);
                    npc.aiEngineClearQueuedWake();
                    npc = next;
                }
            }
            _scheduledCount = 0;
        }
    }

    private static int safeNpcId(L1NpcInstance npc) {
        try {
            return npc.getNpcTemplate().get_npcId();
        } catch (Throwable t) {
            return -1;
        }
    }
}
