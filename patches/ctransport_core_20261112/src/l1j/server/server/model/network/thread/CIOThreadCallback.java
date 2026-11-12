package l1j.server.server.model.network.thread;


import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;


import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.action.CCreatureController;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.TomahawkBleedService;
import l1j.server.server.model.npc.ai.NpcServer;
import l1j.server.server.model.network.core.CIOShardContext;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.utils.MJCommons;

/**
 * CIOThreadCallback 
 */
public final class CIOThreadCallback {

	private static final Logger LOGGER =
			Logger.getLogger(CIOThreadCallback.class.getName());

	private static final int MAX_SHARDS = 8;
	private static final int SHARDS =
			resolveShardCount(Runtime.getRuntime().availableProcessors());

	private static int resolveShardCount(int cores) {
		if (cores <= 1) {
			return 1;
		}

		return Math.min(
				Integer.highestOneBit(cores - 1) << 1,
				MAX_SHARDS);
	}
	
	private static final int HP_BAR_MS = 300;

	private static final long CREATURE_HEARTBEAT_MS = 666L;
	private static final int CREATURE_HEARTBEAT_JITTER_MS = 666;
	private static final long SKILL_EFFECT_HEARTBEAT_MS = 1000L;

	private static final int WHEEL_TICK_MS = 50;
	private static final int WHEEL_SIZE = 200;
	private static final long IDLE_PARK_NS = 200_000L;
	private static final long MAX_SLEEP_MS = 50L;
	private static final int GENERIC_COMPACT_TRIGGER = 16;
	private static final long CIO_WATCHDOG_PERIOD_MS = 1000L;
	private static final long CIO_STALL_WARN_MS = 5000L;

    private static final CIOThreadCallback INSTANCE = new CIOThreadCallback(SHARDS);
    public static CIOThreadCallback getInstance() { return INSTANCE; }

	private static final AtomicInteger ITEM_TIMER_TOKEN = new AtomicInteger(1);

	public static int nextItemTimerToken() {
		int token = ITEM_TIMER_TOKEN.incrementAndGet();
		if (token == 0) {
			token = ITEM_TIMER_TOKEN.incrementAndGet();
		}
		return token;
	}


	public interface Handle {
		boolean cancel();
		boolean isCancelled();
	}

	static final class TaskHandle implements Handle {
		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		private final Runnable onCancel;
		TaskHandle() { this(null); }
		TaskHandle(Runnable onCancel) { this.onCancel = onCancel; }
		@Override public boolean cancel() {
			if (!cancelled.compareAndSet(false, true)) return false;
			if (onCancel != null) onCancel.run();
			return true;
		}
		@Override public boolean isCancelled() { return cancelled.get(); }
	}

	private final int shardCount;
	private final CIOThread[] shards;
	private final ThreadFactory ownerThreadFactory;
	private final Thread watchdogThread;
	private volatile boolean watchdogRunning = true;

	/*
	 * CUserSocket.affinityShard()와 동일한 network shard layout.
	 * 0이면 아직 RServer가 ingress를 bind하지 않은 상태.
	 */
	private volatile int networkShardCount;
	private volatile int networkIngressCapacity;

	private CIOThreadCallback(int shardCount) {
		this.shardCount = shardCount;
		this.shards = new CIOThread[shardCount];
		ThreadFactory tf = new ThreadFactory() {
			private final AtomicInteger seq = new AtomicInteger(1);
			@Override public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "CIOThread-" + seq.getAndIncrement());
				t.setDaemon(false);
				return t;
			}
		};

		this.ownerThreadFactory = tf;

		for (int i = 0; i < shardCount; i++) {
			shards[i] = new CIOThread(i, tf);
			shards[i].start();
		}

		this.watchdogThread =
				new Thread(
						this::runWatchdog,
						"CIO-Watchdog");
		this.watchdogThread.setDaemon(true);
		this.watchdogThread.start();
	}


	private void runWatchdog() {
		while (watchdogRunning) {
			try {
				LockSupport.parkNanos(
						CIO_WATCHDOG_PERIOD_MS * 1_000_000L);

				if (!watchdogRunning) {
					break;
				}

				final long nowNs =
						System.nanoTime();

				for (int i = 0; i < shardCount; i++) {
					final CIOThread shard =
							shards[i];

					if (shard == null || !shard.running) {
						continue;
					}

					final Thread t =
							shard.thread;

					if (t == null || !t.isAlive()) {
						shard.restartIfDead();
						continue;
					}

					final long stalledMs =
							(nowNs - shard.lastProgressNs)
									/ 1_000_000L;

					if (stalledMs >= CIO_STALL_WARN_MS) {
						LOGGER.severe(
								"[CIO-WATCHDOG] owner stalled"
										+ " shard=" + i
										+ " stalledMs=" + stalledMs
										+ " progress=" + shard.lastProgressCounter);

						LockSupport.unpark(t);
					}
				}
			} catch (Throwable t) {
				/*
				 * Watchdog must also be self-preserving.
				 */
				LOGGER.log(
						Level.SEVERE,
						"[CIO-WATCHDOG] watchdog failure; continuing",
						t);
			}
		}
	}


	public int shardCount() {
		return shardCount;
	}

    /** Packet-only replies may publish already prepared outbound data from
     * CacheD. State-mutating work must use completeSession/world mutation.
     */
    public boolean completeSessionReply(CIOClient client, Runnable reply) {
        if (client == null || reply == null || !client.isConnected()) return false;
        try {
            reply.run();
            return true;
        } catch (Throwable failure) {
            LOGGER.log(Level.SEVERE, "CacheD reply failed", failure);
            return false;
        }
    }

    /** Publish post-COMMIT replay on the existing CIO wheel without waiting on
     * a CacheD worker. The completion retains/releases CacheD's owner lanes;
     * returning from this method is NOT an acknowledgement of memory replay.
     * A stale session or cancelled wheel registration must report failure too.
     */
    public void completeWarehouseMove(L1PcInstance pc, int expectedSession,
            Runnable action, java.util.function.Consumer<Throwable> completion) {
        java.util.Objects.requireNonNull(completion, "completion");
        final AtomicBoolean resolved = new AtomicBoolean();
        final java.util.function.Consumer<Throwable> finish = failure -> {
            if (!resolved.compareAndSet(false, true)) return;
            completion.accept(failure);
        };
        if (pc == null || action == null || pc.getSessionStamp() != expectedSession) {
            finish.accept(new IllegalStateException("Stale warehouse completion"));
            return;
        }
        final CIOThread owner = shardOf(pc);
        if (owner == null || !owner.running) {
            finish.accept(new IllegalStateException("Warehouse game owner not available"));
            return;
        }
        final TaskHandle handle = new TaskHandle(() -> finish.accept(
                new IllegalStateException("Warehouse owner completion cancelled")));
        final WheelTask task = new WheelTask() {
            @Override public void run() {
                try {
                    if (handle.isCancelled() || pc.getSessionStamp() != expectedSession
                            || shardOf(pc) != owner)
                        throw new IllegalStateException("Stale warehouse owner completion");
                    action.run();
                    finish.accept(null);
                } catch (Throwable failure) {
                    finish.accept(failure);
                } finally {
                    handle.cancel();
                }
            }
            @Override public boolean isCancelled() { return handle.isCancelled(); }
        };
        if (currentShardIndex() == owner.idx) {
            task.run();
        } else if (!enqueuePcWheelSchedule(owner, pc, 1L, task, handle, SlotKind.GENERIC)) {
            handle.cancel();
        }
    }

    /** Continue a selected warehouse list after the previous receipt has been
     * applied and its CacheD owner lanes have been released. This is the
     * existing PC wheel, not a new thread, worker or global item queue.
     */
    public boolean scheduleWarehouseBatchNext(CIOClient session, L1PcInstance pc,
            int stamp, Runnable next, Runnable abandoned) {
        if (session == null || pc == null || next == null || abandoned == null) return false;
        final CIOThread owner = shardOf(pc);
        if (owner == null || !owner.running || pc.getSessionStamp() != stamp
                || pc.getNetConnection() != session || !session.isConnected()) {
            abandoned.run();
            return false;
        }
        final AtomicBoolean resolved = new AtomicBoolean();
        final TaskHandle handle = new TaskHandle(() -> {
            if (resolved.compareAndSet(false, true)) abandoned.run();
        });
        final WheelTask task = new WheelTask() {
            @Override public void run() {
                if (handle.isCancelled() || !resolved.compareAndSet(false, true)) return;
                try {
                    synchronized (session) {
                        if (pc.getSessionStamp() != stamp || pc.getNetConnection() != session
                                || session.getActiveChar() != pc || !session.isConnected()
                                || shardOf(pc) != owner)
                            throw new IllegalStateException("Warehouse batch session replaced");
                        next.run();
                    }
                } catch (Throwable failure) {
                    LOGGER.log(Level.WARNING, "Warehouse batch continuation failed", failure);
                    abandoned.run();
                } finally {
                    handle.cancel();
                }
            }
            @Override public boolean isCancelled() { return handle.isCancelled(); }
        };
        return enqueuePcWheelSchedule(owner, pc, 1L, task, handle, SlotKind.GENERIC);
    }

    /** Schedule on an existing CIO shard; the return value means admission,
     * never completion. Cancellation/rejection/stopped owners also resolve once.
     * The terminal callback must only release/freeze persistence metadata.
     */
    public boolean completeOnShard(int shardIndex, Runnable action,
            java.util.function.Consumer<Throwable> completion) {
        java.util.Objects.requireNonNull(completion, "completion");
        final AtomicBoolean resolved = new AtomicBoolean();
        final java.util.function.Consumer<Throwable> finish = failure -> {
            if (!resolved.compareAndSet(false, true)) return;
            try { completion.accept(failure); }
            catch (Throwable callbackFailure) {
                LOGGER.log(Level.SEVERE, "CIO terminal callback failed", callbackFailure);
            }
        };
        if (action == null || shardIndex < 0 || shardIndex >= shardCount) {
            finish.accept(new IllegalStateException("Invalid CIO completion owner"));
            return false;
        }
        final CIOThread owner = shards[shardIndex];
        if (owner == null || !owner.running) {
            finish.accept(new IllegalStateException("CIO completion owner unavailable"));
            return false;
        }
        final TaskHandle handle = new TaskHandle(() -> finish.accept(
                new IllegalStateException("CIO owner completion cancelled")));
        final WheelTask task = new WheelTask() {
            @Override public void run() {
                if (resolved.get()) return;
                try {
                    if (handle.isCancelled() || !owner.running || currentShardIndex() != owner.idx)
                        throw new IllegalStateException("Stale CIO owner completion");
                    action.run();
                    finish.accept(null);
                } catch (Throwable failure) { finish.accept(failure); }
                finally { handle.cancel(); }
            }
            @Override public boolean isCancelled() { return handle.isCancelled(); }
        };
        if (currentShardIndex() == owner.idx) {
            task.run();
            return true;
        }
        if (!owner.admitTimer(TimerOp.RAW_TIMER, 0, 1L, task, handle, null)) {
            handle.cancel();
            return false;
        }
        return !handle.isCancelled();
    }

    /**
     * Network packet handoff must not use the 50ms timer wheel. Only one
     * native frame per connection may be outstanding; each connection supplies
     * a reusable Runnable and keeps its direct decode buffer leased until the
     * owner processes it. A bounded FIFO avoids unbounded ingress memory.
     * A rejection is terminal to that connection (the READ pump closes it).
     */
    /** A packet is either executed on its owner or cancelled during shutdown. */
    public interface PacketTask extends l1j.server.server.model.network.core.CIOObject {
    }

    public boolean enqueuePacketImmediate(int shardIndex, PacketTask packetTask) {
        if (packetTask == null || shardIndex < 0 || shardIndex >= shardCount)
            return false;
        final CIOThread owner = shards[shardIndex];
        if (owner == null) return false;
        // Admission and stop share the same short boundary. The owner never
        // executes packet handlers while this monitor is held.
        synchronized (owner) {
            if (!owner.running || !owner.packetIngress.offer(packetTask)) return false;
        }
        owner.signalWork(true);
        return true;
    }

    /** Global metadata has no player socket. Existing shard zero serializes
     * publication; do not apply a result prepared for a replaced world.
     */
    public void completeWorldMutation(L1World expectedWorld, Runnable action,
            java.util.function.Consumer<Throwable> completion) {
        java.util.Objects.requireNonNull(completion, "completion");
        if (expectedWorld == null || expectedWorld != L1World.getInstance() || action == null) {
            completion.accept(new IllegalStateException("Stale world completion"));
            return;
        }
        completeOnShard(0, () -> {
            if (expectedWorld != L1World.getInstance())
                throw new IllegalStateException("World replaced before committed replay");
            action.run();
        }, completion);
    }

    public boolean completeSession(CIOClient client, Runnable action) {
        return completeSession(client, action, failure -> {
            if (failure != null) LOGGER.log(Level.WARNING, "CIO session completion failed", failure);
        });
    }

    /** Auth/creation/login can run before a PC is published, so use the
     * original socket's affinity rather than a world/creature timer binding.
     * The old inline session monitor did not change the executing thread.
     */
    public boolean completeSession(CIOClient client, Runnable action,
            java.util.function.Consumer<Throwable> completion) {
        java.util.Objects.requireNonNull(completion, "completion");
        if (client == null || action == null || !client.isConnected() || client.getConnection() == null) {
            completion.accept(new IllegalStateException("Session completion unavailable"));
            return false;
        }
        final var socket = client.getConnection();
        final int shardIndex = socket.affinityShard();
        final var account = client.getAccount();
        final var actor = client.getActiveChar();
        final int stamp = actor == null ? 0 : actor.getSessionStamp();
        final var state = client.getConnectionState();
        final L1World world = L1World.getInstance();
        return completeOnShard(shardIndex, () -> {
            synchronized (client) {
                if (world != L1World.getInstance() || !client.isConnected() || client.getConnection() != socket
                        || socket.affinityShard() != shardIndex || client.getAccount() != account
                        || client.getActiveChar() != actor || client.getConnectionState() != state
                        || (actor != null && (actor.getSessionStamp() != stamp || actor.getNetConnection() != client)))
                    throw new IllegalStateException("Stale session owner completion");
                action.run();
            }
        }, completion);
    }

	/** Current CIOThread owner index, or -1 outside a CIOThread owner thread. */
	public static int currentShardIndex() {
		return CIOShardContext.currentShardIndex();
	}

	/** One-shot pre-login deadline on the existing CThreadLocalTimer (no new timer thread). */
	public boolean scheduleLoginTimeout(int shardIndex, WheelTask task) {
		if (task == null || shardIndex < 0 || shardIndex >= networkShardCount
				|| shardIndex >= shardCount) return false;
		return shards[shardIndex].admitTimer(
				TimerOp.RAW_TIMER, 0, 61_000L,
				0, 0, 0, task, null, null);
	}

    /** Existing owner wheel drives connection deadlines; no extra worker or queue. */
    public boolean scheduleNetworkTimeout(int shardIndex, WheelTask task, long delayMs) {
        if (task == null || delayMs <= 0L || shardIndex < 0
                || shardIndex >= networkShardCount || shardIndex >= shardCount) return false;
        return shards[shardIndex].admitTimer(TimerOp.RAW_TIMER, 0, delayMs,
                0, 0, 0, task, null, null);
    }

	/** Wake one owner after a spatial delta is published. */
	public void wakeShard(int shardIndex) {
		if (shardIndex < 0 || shardIndex >= shardCount) {
			return;
		}
		shards[shardIndex].signalWork(false);
	}

	public synchronized int bindNetworkIngress(
			ConnectionConfig config) {

		if (config == null) {
			throw new NullPointerException("config");
		}

		final int requestedCount =
				resolveNetworkShardCount(
						Runtime.getRuntime().availableProcessors(),
						config.packetExecutorShardCount,
						config.packetExecutorMaxShards);

		final int count =
				Math.min(
						requestedCount,
						shardCount);

		if (requestedCount != count) {
			LOGGER.fine(
					"[CIOThreadCallback] network shard request capped by owner count: requested="
							+ requestedCount
							+ ", owners="
							+ shardCount
							+ ", actual="
							+ count);
		}

		final int capacity =
				normalizeIngressCapacity(
						config.packetExecutorRingSize);

		final int previousCount =
				networkShardCount;

		if (previousCount != 0) {
			if (previousCount != count
					|| networkIngressCapacity != capacity) {

				throw new IllegalStateException(
						"network ingress already bound: oldCount="
								+ previousCount
								+ ", newCount="
								+ count
								+ ", oldCapacity="
								+ networkIngressCapacity
								+ ", newCapacity="
								+ capacity);
			}

			return previousCount;
		}

		for (int i = 0; i < count; i++) {
			shards[i].bindInbound(
					capacity);
		}


		networkIngressCapacity =
				capacity;

		networkShardCount =
				count;

		LOGGER.fine(
				"[CIOThreadCallback] network ingress bound shards="
						+ count
						+ ", slots/shard="
						+ capacity);

		return count;
	}

	private static int resolveNetworkShardCount(
			int cores,
			int configuredShardCount,
			int maxShards) {

		if (maxShards <= 0) {
			throw new IllegalArgumentException(
					"packetExecutorMaxShards <= 0");
		}

		int count;

		if (configuredShardCount > 0) {
			count =
					Math.min(
							configuredShardCount,
							maxShards);

		} else if (cores <= 1) {
			count = 1;

		} else {
			count =
					Math.min(
							Integer.highestOneBit(
									cores - 1) << 1,
							maxShards);
		}

		if (Integer.bitCount(count) != 1) {
			count =
					Integer.highestOneBit(
							Math.max(
									1,
									count - 1))
							<< 1;
		}

		return Math.min(
				Math.max(
						count,
						1),
				maxShards);
	}

	private static int normalizeIngressCapacity(
			int requested) {

		int capacity =
				Math.max(
						requested,
						ConnectionConfig.MAX_PACKETS_PER_COMPLETION);

		if (Integer.bitCount(capacity) == 1) {
			return capacity;
		}

		final int highest =
				Integer.highestOneBit(
						capacity - 1);

		if (highest >= (1 << 29)) {
			return 1 << 30;
		}

		return highest << 1;
	}

    /** Admission load is only placement metadata for timer-wheel affinity. */
    public int networkIngressPressure(int shardIndex) {
        if (shardIndex < 0 || shardIndex >= networkShardCount) return 0;
        return shards[shardIndex].lastBacklogMask != 0 ? 16 : 0;
    }

	/*
	 * PC shard의 단일 기준.
	 *
	 * objId를 다시 hash하지 않는다.
	 * accept 시 RConnection에 박힌 affinityShard를 그대로 사용한다.
	 */
	private int affinityShardIndex(L1PcInstance pc) {
		if (pc == null) return -1;
		final CIOClient client = pc.getNetConnection();
		if (client == null) return -1;
		final int shard = client.affinityShard();

		if (shard < 0 || shard >= shardCount) {
			throw new IllegalStateException(
					"CIOThread/network shard mismatch: affinity="
							+ shard
							+ ", CIOThreadCount="
							+ shardCount
							+ ", pcId="
							+ pc.getId());
		}

		return shard;
	}

	CIOThread shardOf(L1PcInstance pc) {
		final int shard = affinityShardIndex(pc);
		return shard < 0 ? null : shards[shard];
	}

	/** LocalWorldMap view-delta routing: canonical CIO owner index for a PC. */
	public int ownerShardIndex(L1PcInstance pc) {
		return affinityShardIndex(pc);
	}

	public void registerPc(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return;
		}

		shard.admitTimer(
						TimerOp.TIMER_SESSION_BIND,
						pc.getId(),
						0L,
						pc.getSessionStamp(), 0, 0,
						pc, null, null);
	}

	public void unregisterPc(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		final CIOThread shard = shardOf(pc);

		if (shard != null) {
			shard.admitTimer(
							TimerOp.TIMER_SESSION_UNBIND,
							pc.getId(),
							0L,
							pc.getSessionStamp(), 0, 0,
							pc, null, null);
			return;
		}

		/*
		 * 비정상 종료 순서로 connection이 먼저 떨어진 경우만
		 * 모든 shard에서 stale 등록을 정리한다.
		 */
		for (int i = 0; i < shardCount; i++) {
			shards[i].admitTimer(
							TimerOp.TIMER_SESSION_UNBIND,
							pc.getId(),
							0L,
							pc.getSessionStamp(), 0, 0,
							pc, null, null);
		}
	}

    // Timer arm binds the expected session and smartId at admission.
    // Capture the session and exact world generation before publication; a
    // Stale requests must not replace the new session's wheel task.
    private boolean enqueuePcWheelSchedule(CIOThread owner, L1PcInstance pc,
            long delayMs, WheelTask task, TaskHandle handle, SlotKind kind) {
        if (owner == null || pc == null || handle == null || task == null || delayMs <= 0L) {
            if (handle != null) handle.cancel();
            return false;
        }
        final int session = pc.getSessionStamp();
        final long smartId = L1World.getInstance().smartIdOf(pc);
        if (smartId == 0L || pc.getSessionStamp() != session
                || shardOf(pc) != owner
                || (task instanceof EndGhostTask && ((EndGhostTask) task).stamp != session)
                || (task instanceof InvisDelayTask && ((InvisDelayTask) task).stamp != session)) {
            handle.cancel();
            return false;
        }
        if (!owner.admitTimer(TimerOp.SCHED, pc.getId(), delayMs,
                session, (int) (smartId >>> 32), (int) smartId,
                task, handle, kind)) {
            handle.cancel();
            return false;
        }
        return true;
    }

    private void stopPcWheelTask(CIOThread owner, L1PcInstance pc, int commandType) {
        if (owner == null || pc == null) return;
        owner.admitTimer(commandType, pc.getId(), 0L,
                pc.getSessionStamp(), 0, 0, pc, null, null);
    }

	public Handle scheduleEndGhost(
			int pcObjId,
			int sessionStamp,
			long delayMs) {

		if (pcObjId <= 0 || delayMs <= 0L) {
			return null;
		}

		final Object current = L1World.getInstance().findObject(pcObjId);
		if (!(current instanceof L1PcInstance)) {
			return null;
		}

		final L1PcInstance pc = (L1PcInstance) current;
        if (pc.getSessionStamp() != sessionStamp) return null;
		final long smartId = L1World.getInstance().smartIdOf(pc);
		if (smartId == 0L) {
			return null;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return null;
		}

		final TaskHandle h = new TaskHandle();

		if (!enqueuePcWheelSchedule(shard, pc, delayMs, new EndGhostTask(
								pcObjId,
								smartId,
								sessionStamp,
								h), h, SlotKind.GHOST_END)) {
			h.cancel();
			return null;
		}
		return h;
	}

	public Handle scheduleInvisDelay(
			int pcObjId,
			int sessionStamp,
			long delayMs) {

		if (pcObjId <= 0 || delayMs <= 0L) {
			return null;
		}

		final Object current = L1World.getInstance().findObject(pcObjId);
		if (!(current instanceof L1PcInstance)) {
			return null;
		}

		final L1PcInstance pc = (L1PcInstance) current;
        if (pc.getSessionStamp() != sessionStamp) return null;
		final long smartId = L1World.getInstance().smartIdOf(pc);
		if (smartId == 0L) {
			return null;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return null;
		}

		final TaskHandle h = new TaskHandle();

		if (!enqueuePcWheelSchedule(shard, pc, delayMs, new InvisDelayTask(
								pcObjId,
								smartId,
								sessionStamp,
								h), h, SlotKind.INVIS_DELAY)) {
			h.cancel();
			return null;
		}
		return h;
	}

	public Handle startFishing(L1PcInstance pc) {
		if (pc == null || pc.getId() <= 0 || !pc.isFishing()) {
			return null;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return null;
		}

		final long delayMs =
				FishingTimeController.nextDelayMs(pc, System.currentTimeMillis());

		if (delayMs <= 0L) {
			return null;
		}

		final TaskHandle h = new TaskHandle();
		final FishingTask task =
				new FishingTask(pc, h, shard);

        if (!enqueuePcWheelSchedule(shard, pc, delayMs, task, h, SlotKind.FISHING)) return null;
        return h;
	}

	public void stopFishing(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return;
		}

        stopPcWheelTask(shard, pc, TimerOp.STOP_FISHING);
	}

	/**
	 * GM HP BAR 전용 recurring task 시작.
	 * HP BAR를 실제로 켠 PC만 wheel slot을 가진다.
	 */
	public Handle startHpBar(L1PcInstance pc) {
		if (pc == null || !pc.hasSkillEffect(L1SkillId.GMSTATUS_HPBAR)) {
			return null;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return null;
		}

		final long smartId = L1World.getInstance().smartIdOf(pc);
		if (smartId == 0L) {
			return null;
		}

		final TaskHandle h = new TaskHandle();
		final HpBarTask task =
				new HpBarTask(
                        pc.getId(),
                        smartId,
                        pc.getSessionStamp(),
                        HP_BAR_MS,
						h,
						shard);

		if (!enqueuePcWheelSchedule(shard, pc, HP_BAR_MS, task, h, SlotKind.HP_BAR)) return null;

		return h;
	}

	/**
	 * HP BAR를 끌 때 owner shard에 종료 command만 전달한다.
	 */
	public void stopHpBar(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		final CIOThread shard = shardOf(pc);
		if (shard == null) {
			return;
		}

		stopPcWheelTask(shard, pc, TimerOp.STOP_HP_BAR);
	}

	private static final class TimerOp {
		static final int TIMER_SESSION_BIND = 1;
		static final int TIMER_SESSION_UNBIND = 2;
		static final int SCHED = 3;
		static final int STOP_HP_BAR = 5;
		static final int RAW_TIMER = 6;
		static final int STOP_FISHING = 9;
		
		static final int ACTION_TIMER = 12;
		static final int ITEM_TIMER = 13;
		static final int NPC_MAKER_TIMER = 14;
		static final int CHARACTER_SKILL_HEARTBEAT = 16;
		static final int PC_ITEM_DELAY_TIMER = 17;
		static final int NPC_TIMER = 18;
		static final int PC_TELEPORT_TIMER = 20;
        static final int PC_TIMER_COMPLETE = 21;

		private TimerOp() {
		}
	}

	private static final byte SKILL_OP_SET = 1;
	private static final byte SKILL_OP_REMOVE_STOP = 2;
	private static final byte SKILL_OP_REMOVE_KILL = 3;
	private static final byte SKILL_OP_CLEAR = 4;
    private static final byte SKILL_OP_REPLACE = 6;
	private static final byte SKILL_OP_ARM = 5;

	/**
     * Timer admission is direct from any producer, serialized by the shard monitor.
     * The packet RECV path never posts an owner event; encrypted READ data stays
     * in the existing socket buffer until OnRead gets the shard lease.
     * Independent owner events use the existing completion transport, separate from NC socket/controller serializers.
	 */


	public interface WheelTask {
		void run();
		boolean isCancelled();
        default void runOnController(L1PcInstance pc) { run(); }
        default void runOnNpc(L1NpcInstance npc) { run(); }
	}

	/**
	 * One object per registered PC/session, reused for every heartbeat.
	 * No per-heartbeat allocation. Game phases are intentionally wired in
	 * incrementally so legacy regen/effect behavior is not double-executed.
	 */
    private static void finishPcWheelTask(CIOThread owner, WheelTask task,
            int mode, long delayMs) {
        owner.admitTimer(TimerOp.PC_TIMER_COMPLETE,
                0, delayMs, mode, 0, 0, task, null, null);
    }

	private static final class CreatureHeartbeatTask implements WheelTask {
		final L1PcInstance actor;
		final int pcId;
        final int sessionStamp;
		final TaskHandle h;
		final CIOThread owner;

		CreatureHeartbeatTask(L1PcInstance actor, TaskHandle h, CIOThread owner) {
			this.actor = actor;
			this.pcId = actor.getId();
            this.sessionStamp = actor.getSessionStamp();
			this.h = h;
			this.owner = owner;
		}

		@Override
		public void run() {
            owner.dispatchPcWheelTask(this, actor, h, sessionStamp);
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (h.isCancelled() || pc != actor || pc.getSessionStamp() != sessionStamp) return;
            try {
                pc.getRegenController().TimerExpired();
            } finally {
                finishPcWheelTask(owner, this, 1, CREATURE_HEARTBEAT_MS);
            }
        }

		@Override
		public boolean isCancelled() {
			return h.isCancelled();
		}
	}

	private static final class EndGhostTask implements WheelTask {
		final int pcId; final long smartId; final int stamp; final TaskHandle h;
		EndGhostTask(int pcId, long smartId, int stamp, TaskHandle h) { this.pcId = pcId; this.smartId = smartId; this.stamp = stamp; this.h = h; }
		@Override public void run() {
            if (h.isCancelled()) return;
            final Object live = L1World.getInstance().findObjectBySmartId(smartId);
            if (!(live instanceof L1PcInstance)) { h.cancel(); return; }
            final L1PcInstance pc = (L1PcInstance) live;
            final CIOThread owner = CIOThreadCallback.getInstance().shardOf(pc);
            if (owner != null) owner.dispatchPcWheelTask(this, pc, h, stamp);
            else h.cancel();
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (!h.isCancelled() && pc.getId() == pcId && pc.getSessionStamp() == stamp
                    && L1World.getInstance().findObjectBySmartId(smartId) == pc) {
                try { pc.endGhost() ; } catch (Throwable failure) {
                    LOGGER.log(Level.SEVERE, "PC one-shot timer failed", failure);
                }
            }
        }
		@Override public boolean isCancelled() { return h.isCancelled(); }
	}

	private static final class InvisDelayTask implements WheelTask {
		final int pcId; final long smartId; final int stamp; final TaskHandle h;
		InvisDelayTask(int pcId, long smartId, int stamp, TaskHandle h) { this.pcId = pcId; this.smartId = smartId; this.stamp = stamp; this.h = h; }
		@Override public void run() {
            if (h.isCancelled()) return;
            final Object live = L1World.getInstance().findObjectBySmartId(smartId);
            if (!(live instanceof L1PcInstance)) { h.cancel(); return; }
            final L1PcInstance pc = (L1PcInstance) live;
            final CIOThread owner = CIOThreadCallback.getInstance().shardOf(pc);
            if (owner != null) owner.dispatchPcWheelTask(this, pc, h, stamp);
            else h.cancel();
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (!h.isCancelled() && pc.getId() == pcId && pc.getSessionStamp() == stamp
                    && L1World.getInstance().findObjectBySmartId(smartId) == pc) {
                try { pc.clearInvisDelay() ; } catch (Throwable failure) {
                    LOGGER.log(Level.SEVERE, "PC one-shot timer failed", failure);
                }
            }
        }
		@Override public boolean isCancelled() { return h.isCancelled(); }
	}

	private static final class FishingTask implements WheelTask {
		final L1PcInstance actor;
		final int pcId;
        final int sessionStamp;
		final TaskHandle h;
		final CIOThread owner;

		FishingTask(
				L1PcInstance actor,
				TaskHandle h,
				CIOThread owner) {

			this.actor = actor;
			this.pcId = actor.getId();
            this.sessionStamp = actor.getSessionStamp();
			this.h = h;
			this.owner = owner;
		}

		@Override
		public void run() {
            owner.dispatchPcWheelTask(this, actor, h, sessionStamp);
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (h.isCancelled() || pc != actor || pc.getSessionStamp() != sessionStamp
                    || !pc.isFishing()) {
                h.cancel(); finishPcWheelTask(owner, this, 5, 0L); return;
            }
            long nextDelay = -1L;
            try {
                nextDelay = FishingTimeController.getInstance().processFromCio(pc,
                        System.currentTimeMillis());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Fishing tick failed", t);
                if (pc.isFishing()) nextDelay = 1000L;
            } finally {
                if (!pc.isFishing() || nextDelay <= 0L) h.cancel();
                finishPcWheelTask(owner, this, 5, h.isCancelled() ? 0L : nextDelay);
            }
        }

		@Override
		public boolean isCancelled() {
			return h.isCancelled();
		}
	}

	private static final class HpBarTask implements WheelTask {
		final int pcId;
        final int sessionStamp;
		final long smartId;
		final long periodMs;
		final TaskHandle h;
		final CIOThread owner;

		HpBarTask(
				int pcId,
				            long smartId,
            int sessionStamp,
            long periodMs,
				TaskHandle h,
				CIOThread owner) {

			this.pcId = pcId;
            this.smartId = smartId;
            this.sessionStamp = sessionStamp;
			this.periodMs = periodMs;
			this.h = h;
			this.owner = owner;
		}

		@Override
		public void run() {
            final Object live = L1World.getInstance().findObjectBySmartId(smartId);
            owner.dispatchPcWheelTask(this, live instanceof L1PcInstance ? (L1PcInstance) live : null,
                    h, sessionStamp);
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (h.isCancelled() || pc.getSessionStamp() != sessionStamp
                    || L1World.getInstance().findObjectBySmartId(smartId) != pc
                    || !pc.hasSkillEffect(L1SkillId.GMSTATUS_HPBAR)) {
                h.cancel(); finishPcWheelTask(owner, this, 6, 0L); return;
            }
            try {
                pc.updateHpBar();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "HpBar tick failed", t);
            } finally {
                if (!pc.hasSkillEffect(L1SkillId.GMSTATUS_HPBAR)) h.cancel();
                finishPcWheelTask(owner, this, 6, h.isCancelled() ? 0L : periodMs);
            }
        }

		@Override
		public boolean isCancelled() {
			return h.isCancelled();
		}
	}

	private enum SlotKind { GHOST_END, INVIS_DELAY, FISHING, HP_BAR, GENERIC }


	static final class CIOThread implements Runnable {
        // 0: working, 1: published work, 2: parked or entering park.
        // Wake signals track wheel deadlines; no command queue is used.
        // The owner changes 0->2 only after its drain pass; if it sees state 1,
        // it runs another pass rather than sleeping. This prevents lost wakes.
        private static final VarHandle WAKE_GATE;
        static {
            try {
                WAKE_GATE = MethodHandles.lookup().findVarHandle(CIOThread.class, "wakeGate", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        private volatile int wakeGate;

        void signalWork(boolean inbound) {
            final Thread target = thread;
            if (target == null) return;
            // Timer admission on the owner still needs a wake when parked.
            // It must force another loop, rather than silently park until tick.
            if (target == Thread.currentThread()) {
                WAKE_GATE.setVolatile(this, 1);
                return;
            }
            // Multiple producers share a pending bit while the timer loop is busy.
            if (wakeGate == 1) return;
            final int previous = (int) WAKE_GATE.getAndSet(this, 1);
            if (previous == 2) {
                LockSupport.unpark(target);
            }
        }

        private boolean preparePark() {
            if (WAKE_GATE.compareAndSet(this, 0, 2)) return true;
            // A concurrent producer published work: never sleep this pass.
            WAKE_GATE.compareAndSet(this, 1, 0);
            return false;
        }

        private void finishPark() {
            // If a producer changed 2->1, keep its signal for the next pass.
            WAKE_GATE.compareAndSet(this, 2, 0);
        }

        // Separate from threadLocalTimer: FIFO is consumed as soon as the
        // owner wakes, not when a 50ms wheel slot expires. The queue itself
        // is bounded; it never grows with unbounded client traffic.
        static final int PACKET_INGRESS_CAPACITY = 8192;
        static final int MAX_PACKET_BURST = 64;
        // Replaced at network bind, before the first accepted connection.
        volatile ArrayBlockingQueue<PacketTask> packetIngress =
                new ArrayBlockingQueue<>(PACKET_INGRESS_CAPACITY);

		final int idx;
		final ThreadFactory threadFactory;
		volatile Thread thread;

		/*
         * Cross-thread lifecycle/DB/world command posting only.
         * RECV packets never enter this timer structure.
         * The wheel is directly writable under its bucket monitor.
		 */
        volatile int inboundLimit;

        // Session/timer admission is directly serialized by this shard monitor.
        // There is no CIO deferred command queue or game owner mailbox.
        volatile long lastProgressNs = System.nanoTime();
        volatile long lastProgressCounter;
        volatile int lastBacklogMask;
        volatile int restartCount;
		final PcStateTable states = new PcStateTable(2048);
		final CThreadLocalTimer threadLocalTimer = new CThreadLocalTimer(WHEEL_TICK_MS, WHEEL_SIZE, this);


		/*
		 * bindNetworkIngress() 이후 같은 canonical ClusterGrid를 연결한다.
		 * CIOThread thread 생성 시점에는 L1World를 건드리지 않아 초기화 race를 피한다.
		 */

		volatile long lastWheelAtMs;
		volatile boolean running = true;

		CIOThread(int idx, ThreadFactory tf) {
			this.idx = idx;
			this.threadFactory = tf;
			this.thread = null;
		}

		synchronized void start() {
			final Thread current = thread;
			if (current != null && current.isAlive()) {
				return;
			}

			final Thread next =
					threadFactory.newThread(this);
            WAKE_GATE.setVolatile(this, 0);
			thread = next;
			next.start();
		}

		synchronized boolean restartIfDead() {
			if (!running) {
				return false;
			}

			final Thread current = thread;
			if (current != null && current.isAlive()) {
				return false;
			}

			restartCount++;

			final Thread next =
					threadFactory.newThread(this);
            WAKE_GATE.setVolatile(this, 0);
			thread = next;

			LOGGER.severe(
					"[CIO-WATCHDOG] restarting dead owner"
							+ " shard=" + idx
							+ " restart=" + restartCount);

			next.start();
			return true;
		}


        synchronized void bindInbound(int capacity) {
            if (capacity <= 0)
                throw new IllegalArgumentException("invalid network ingress capacity: " + capacity);
            if (inboundLimit != 0) {
                if (inboundLimit != capacity)
                    throw new IllegalStateException("ingress already bound with different capacity");
                return;
            }
            if (!packetIngress.isEmpty())
                throw new IllegalStateException("cannot resize active packet ingress");
            packetIngress = new ArrayBlockingQueue<>(capacity);
            inboundLimit = capacity;
        }

        void applyTimerRequest(
				int type,
				int pcId,
				long delayMs,
				int p0,
				int p1,
				int p2,
				Object o0,
				Object o1,
				Object o2) {

			switch (type) {
			case TimerOp.TIMER_SESSION_BIND:
				onRegister(pcId, (L1PcInstance) o0, p0);
				break;

			case TimerOp.TIMER_SESSION_UNBIND:
				onUnregister(pcId, (L1PcInstance) o0, p0);
				break;

            case TimerOp.SCHED:
                onSchedule(pcId, delayMs, (WheelTask) o0, (TaskHandle) o1,
                        (SlotKind) o2, p0, ((long) p1 << 32) | (p2 & 0xffffffffL));
                break;


			case TimerOp.STOP_HP_BAR:
				if (isCurrentSessionCommand(pcId, (L1PcInstance) o0, p0)) onStopHpBar(pcId);
				break;

            case TimerOp.PC_TIMER_COMPLETE:
                onPcWheelCompletion((WheelTask) o0, p0, delayMs);
                break;

			case TimerOp.RAW_TIMER:
				onRawSchedule(
						delayMs,
						(WheelTask) o0,
						(TaskHandle) o1);
				break;

			case TimerOp.ACTION_TIMER: {
                final CCreatureController controller = (CCreatureController) o0;
                final L1PcInstance pc = controller == null ? null : controller.actor();
                if (pc != null && pc.getSessionStamp() == pcId
                        && controller.ownerShardIndex() == idx) {
                    onActionTimerSchedule(delayMs, controller, p0, p1, pcId);
                }
				break;
            }

			case TimerOp.PC_TELEPORT_TIMER: {
                final long smartId = ((long) p0 << 32) | (pcId & 0xffffffffL);
                final Object obj = L1World.getInstance().findObjectBySmartId(smartId);
                if (obj instanceof L1PcInstance) {
                    final L1PcInstance pc = (L1PcInstance) obj;
                    if (pc.getSessionStamp() == p1 && pc.teleportTimerEpoch() == p2
                            && CIOThreadCallback.getInstance().shardOf(pc) == this) {
                        threadLocalTimer.scheduleTeleport(delayMs, smartId, p1, p2);
                    }
                }
                break;
            }

			case TimerOp.PC_ITEM_DELAY_TIMER: {
				final long smartId =
						((long) p0 << 32)
								| (pcId & 0xffffffffL);

				onPcItemDelayTimerSchedule(
						delayMs,
						smartId,
						p1,
						p2);
				break;
			}

			case TimerOp.NPC_TIMER: {
				final long smartId =
						((long) p0 << 32)
								| (pcId & 0xffffffffL);

				onNpcTimerSchedule(
						delayMs,
						smartId,
						p1,
						p2);
				break;
			}

			case TimerOp.ITEM_TIMER: {
				final long smartId =
						((long) p0 << 32)
								| (pcId & 0xffffffffL);

				onItemTimerSchedule(
						delayMs,
						smartId,
						p1,
						p2);
				break;
			}

			case TimerOp.NPC_MAKER_TIMER:
				onNpcMakerTimerSchedule(
						delayMs,
						(L1Spawn) o0,
						p0,
						p1);
				break;


			case TimerOp.CHARACTER_SKILL_HEARTBEAT:
				onCharacterSkillHeartbeatSchedule(
						delayMs,
						(L1Character) o0,
						p0);
				break;


			case TimerOp.STOP_FISHING:
				if (isCurrentSessionCommand(pcId, (L1PcInstance) o0, p0)) onStopFishing(pcId);
				break;


			default:
				break;
			}
		}


        /**
         * One lock per existing CThreadLocalTimer shard session state, never a
         * cross-thread command FIFO. The wheel bucket lock is distinct:
         * timer callbacks execute AFTER releasing that bucket lock.
         * No packet RECV work executes in the timer thread.
         */
        boolean admitTimer(int type, int pcId, long delayMs,
                WheelTask task, TaskHandle handle, SlotKind kind) {
            return admitTimer(type, pcId, delayMs,
                    0, 0, 0, task, handle, kind);
        }

        boolean admitTimer(int type, int pcId, long delayMs,
                int p0, int p1, int p2, Object o0, Object o1, Object o2) {
            synchronized (this) {
                if (!running) {
                    if (o1 instanceof TaskHandle) ((TaskHandle) o1).cancel();
                    return false;
                }
                try {
                    applyTimerRequest(type, pcId, delayMs,
                            p0, p1, p2, o0, o1, o2);
                } catch (Throwable t) {
                    if (o1 instanceof TaskHandle) ((TaskHandle) o1).cancel();
                    LOGGER.log(Level.SEVERE,
                            "[CIOThread] direct timer admission failed shard="
                            + idx + " type=" + type + " pcId=" + pcId, t);
                    return false;
                }
            }
            // The wheel advances every 50 ms even without a signal. Admission
            // above is already complete; waking here cannot advance a timer tick.
            // Explicit cross-thread work still uses wakeShard()/signalWork().
            return true;
        }

		void onRawSchedule(long delayMs, WheelTask task, TaskHandle handle) {
			if (task == null || delayMs <= 0L) {
				return;
			}
			threadLocalTimer.schedule(delayMs, task, handle);
		}

		void onActionTimerSchedule(
				long delayMs,
				CCreatureController controller,
				int timerId,
				int generation,
				int expectedSession) {

			if (controller == null || delayMs <= 0L) {
				return;
			}

			threadLocalTimer.scheduleAction(
					delayMs,
					controller,
					timerId,
					generation,
					expectedSession);
		}

		void onPcItemDelayTimerSchedule(
				long delayMs,
				long smartId,
				int delayId,
				int generation) {

			if (smartId == 0L
					|| delayId <= 0
					|| generation == 0
					|| delayMs <= 0L) {
				return;
			}

			threadLocalTimer.schedulePcItemDelay(
					delayMs,
					smartId,
					delayId,
					generation);
		}

		void onNpcTimerSchedule(
				long delayMs,
				long smartId,
				int timerId,
				int generation) {

			if (smartId == 0L
					|| timerId <= 0
					|| generation == 0
					|| delayMs <= 0L) {
				return;
			}

			threadLocalTimer.scheduleNpcTimer(
					delayMs,
					smartId,
					timerId,
					generation);
		}

		void onItemTimerSchedule(
				long delayMs,
				long smartId,
				int timerId,
				int generation) {

			if (smartId == 0L || delayMs <= 0L) {
				return;
			}

			threadLocalTimer.scheduleItem(
					delayMs,
					smartId,
					timerId,
					generation);
		}

		void onNpcMakerTimerSchedule(
				long delayMs,
				L1Spawn maker,
				int timerId,
				int generation) {

			if (maker == null || delayMs <= 0L) {
				return;
			}

			threadLocalTimer.scheduleNpcMaker(
					delayMs,
					maker,
					timerId,
					generation);
		}


		void onCharacterSkillHeartbeatSchedule(
				long delayMs,
				L1Character actor,
				int generation) {

			if (actor == null || generation == 0 || delayMs <= 0L) {
				return;
			}

			threadLocalTimer.scheduleCharacterSkill(
					delayMs,
					actor,
					generation);
		}

		void onRegister(int pcId, L1PcInstance pc, int expectedSession) {
			// A prior login's timer bind can be published after reconnect.
			// Never bind its heartbeat or timers to a different session or object.
			if (pc == null || pc.getId() != pcId
					|| pc.getSessionStamp() != expectedSession) return;
			final Object liveActor = L1World.getInstance().findObject(pcId);
			// A different PC may already own a recycled object ID. Ignore a
			// late old-session timer bind instead of replacing the new CIOState.
			if (liveActor != null && liveActor != pc) return;

			CIOState st = states.get(pcId);
			if (st != null && st.sessionActor != null
					&& (st.sessionActor != pc || st.sessionStamp != expectedSession)) {
				onUnregister(pcId, st.sessionActor, st.sessionStamp);
				st = null;
			}
			if (st == null) st = states.getOrCreate(pcId);
			if (st == null) return;

			st.pcId = pcId;
			st.sessionActor = pc;
			st.sessionStamp = expectedSession;
			st.alive = true;

			// Preserve the old behavior when registration precedes world entry,
			// but never arm a heartbeat on another object reusing the same ID.
			if (liveActor == pc) {
				ensureCreatureHeartbeat(pc, st);
				CIOThreadCallback.getInstance()
						.ensureCharacterSkillHeartbeatFromOwner(pc);
				if (pc.hasSkillEffect(L1SkillId.GMSTATUS_HPBAR)) {
					ensureHpBarTask(pcId, st);
				}
			}
		}

		void onUnregister(int pcId, L1PcInstance pc, int expectedSession) {
			final CIOState st = states.get(pcId);
			if (st == null || st.sessionActor != pc
					|| st.sessionStamp != expectedSession) return;
            if (st.heartbeatTask != null) {
                final L1PcInstance actor = st.heartbeatTask.actor;
                final CCreatureController controller = actor.getCreatureController();
                if (controller != null && controller.actor() == actor) {
                    controller.AddExecutableRegenReset();
                }
            }
			st.alive = false;
			if (st.ghostHandle != null) { st.ghostHandle.cancel(); st.ghostHandle = null; }
			if (st.invisHandle != null) { st.invisHandle.cancel(); st.invisHandle = null; }
			if (st.fishingHandle != null) { st.fishingHandle.cancel(); st.fishingHandle = null; }
			if (st.hpBarHandle != null) { st.hpBarHandle.cancel(); st.hpBarHandle = null; }
			if (st.heartbeatHandle != null) { st.heartbeatHandle.cancel(); st.heartbeatHandle = null; }
			st.heartbeatTask = null;
			st.cancelAllGeneric();
			st.sessionActor = null;
			states.remove(pcId);
		}

		void onStopFishing(int pcId) {
			final CIOState st = states.get(pcId);
			if (st == null) {
				return;
			}

			final TaskHandle h = st.fishingHandle;
			if (h != null) {
				h.cancel();
				st.fishingHandle = null;
			}
		}


		void onStopHpBar(int pcId) {
			final CIOState st = states.get(pcId);
			if (st == null) {
				return;
			}

			final TaskHandle h = st.hpBarHandle;
			if (h != null) {
				h.cancel();
				st.hpBarHandle = null;
			}
		}

        synchronized void dispatchPcWheelTask(WheelTask task, L1PcInstance pc,
                TaskHandle handle, int sessionStamp) {
            if (task == null || handle == null || handle.isCancelled()) return;
            if (pc == null || pc.getId() <= 0 || pc.getSessionStamp() != sessionStamp
                    || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()
                    || L1World.getInstance().findObject(pc.getId()) != pc) {
                handle.cancel();
                return;
            }
            final CIOState st = states.get(pc.getId());
            if (st == null || !st.alive || st.sessionActor != pc
                    || st.sessionStamp != sessionStamp) {
                handle.cancel();
                return;
            }
            final CCreatureController controller = pc.getCreatureController();
            if (controller == null || controller.actor() != pc) {
                handle.cancel();
                return;
            }
            if (!controller.AddExecutableWheelTask(task)) {
                // Do not lose a timer when the 32-slot controller is full.
                threadLocalTimer.schedule(WHEEL_TICK_MS, task, handle);
            }
        }

        void onPcWheelCompletion(WheelTask task, int mode, long delayMs) {
            if (task == null) return;
            switch (mode) {
            case 1:
                if (task instanceof CreatureHeartbeatTask) {
                    final CreatureHeartbeatTask t = (CreatureHeartbeatTask) task;
                    if (t.h.isCancelled()) clearCreatureHeartbeat(t.pcId, t.h, t);
                    else rescheduleCreatureHeartbeat(t);
                }
                break;
            case 5:
                if (task instanceof FishingTask) {
                    final FishingTask t = (FishingTask) task;
                    if (t.h.isCancelled() || delayMs <= 0L) clearFishingHandle(t.pcId, t.h);
                    else rescheduleFishing(t, delayMs);
                }
                break;
            case 6:
                if (task instanceof HpBarTask) {
                    final HpBarTask t = (HpBarTask) task;
                    if (t.h.isCancelled() || delayMs <= 0L) clearHpBarHandle(t.pcId, t.h);
                    else rescheduleHpBar(t);
                }
                break;
            case 7:
                if (task instanceof TomahawkTask) {
                    final TomahawkTask t = (TomahawkTask) task;
                    if (!t.handle.isCancelled() && delayMs > 0L
                            && t.target != null && t.target.isTomahawkState(t.token)) {
                        threadLocalTimer.schedule(delayMs, t, t.handle);
                    } else {
                        t.handle.cancel();
                    }
                }
                break;
            default:
                break;
            }
        }

		void ensureCreatureHeartbeat(L1PcInstance pc, CIOState st) {
			if (st == null || pc == null || pc.getId() <= 0) {
				return;
			}

			final int pcId = pc.getId();

			final TaskHandle current = st.heartbeatHandle;
			if (current != null
					&& !current.isCancelled()
					&& st.heartbeatTask != null) {

				return;
			}

			final TaskHandle h = new TaskHandle();
			final CreatureHeartbeatTask task =
					new CreatureHeartbeatTask(pc, h, this);

			st.heartbeatHandle = h;
			st.heartbeatTask = task;

			threadLocalTimer.schedule(
					initialCreatureHeartbeatDelay(pcId),
					task,
					h);
		}

		void clearCreatureHeartbeat(
				int pcId,
				TaskHandle h,
				CreatureHeartbeatTask task) {

			final CIOState st = states.get(pcId);
			if (st != null
					&& st.heartbeatHandle == h
					&& st.heartbeatTask == task) {

				st.heartbeatHandle = null;
				st.heartbeatTask = null;
			}
		}

		void rescheduleCreatureHeartbeat(CreatureHeartbeatTask task) {
			if (task == null
					|| task.owner != this
					|| task.h.isCancelled()) {

				return;
			}

			final CIOState st = states.get(task.pcId);
			if (st == null
					|| !st.alive
					|| st.heartbeatHandle != task.h
					|| st.heartbeatTask != task) {

				task.h.cancel();
				return;
			}

			threadLocalTimer.schedule(
					CREATURE_HEARTBEAT_MS,
					task,
					task.h);
		}

		private static long initialCreatureHeartbeatDelay(int pcId) {
			int x = pcId;
			x ^= x >>> 16;
			x *= 0x7feb352d;
			x ^= x >>> 15;
			x *= 0x846ca68b;
			x ^= x >>> 16;

			final int phase =
					(int) ((x & 0x7fffffffL) % CREATURE_HEARTBEAT_JITTER_MS);

			return CREATURE_HEARTBEAT_MS + 1L + phase;
		}

		void ensureHpBarTask(int pcId, CIOState st) {
			if (st == null) {
				return;
			}

			final TaskHandle current = st.hpBarHandle;
			if (current != null && !current.isCancelled()) {
				return;
			}

			final long smartId = L1World.getInstance().smartIdOf(pcId);
			if (smartId == 0L) {
				return;
			}

			final TaskHandle h = new TaskHandle();
			final HpBarTask task =
					new HpBarTask(
							pcId,
							smartId,
                            st.sessionStamp,
							HP_BAR_MS,
							h,
							this);

			st.hpBarHandle = h;
			threadLocalTimer.schedule(
					HP_BAR_MS,
					task,
					h);
		}

		void clearHpBarHandle(int pcId, TaskHandle h) {
			final CIOState st = states.get(pcId);
			if (st != null && st.hpBarHandle == h) {
				st.hpBarHandle = null;
			}
		}

		void rescheduleHpBar(HpBarTask task) {
			if (task == null
					|| task.owner != this
					|| task.h.isCancelled()) {

				return;
			}

			final CIOState st =
					states.get(task.pcId);

			if (st == null
					|| !st.alive
					|| st.hpBarHandle != task.h) {

				task.h.cancel();
				return;
			}

			threadLocalTimer.schedule(
					task.periodMs,
					task,
					task.h);
		}

		void clearFishingHandle(int pcId, TaskHandle h) {
			final CIOState st = states.get(pcId);
			if (st != null && st.fishingHandle == h) {
				st.fishingHandle = null;
			}
		}

		void rescheduleFishing(FishingTask task, long delayMs) {
			if (task == null
					|| task.owner != this
					|| task.h.isCancelled()
					|| delayMs <= 0L) {
				return;
			}

			final CIOState st = states.get(task.pcId);
			if (st == null
					|| !st.alive
					|| st.fishingHandle != task.h
					|| !task.actor.isFishing()) {

				task.h.cancel();
				return;
			}

			threadLocalTimer.schedule(delayMs, task, task.h);
		}

        private boolean isCurrentSessionCommand(int pcId, L1PcInstance expectedPc,
                int expectedSession) {
            final CIOState state = states.get(pcId);
            return expectedPc != null && state != null
                    && state.sessionActor == expectedPc
                    && state.sessionStamp == expectedSession
                    && expectedPc.getSessionStamp() == expectedSession;
        }

        void onSchedule(int pcId, long delayMs, WheelTask task, TaskHandle handle,
                SlotKind kind, int expectedSession, long expectedSmartId) {
            if (task == null || handle == null || kind == null || delayMs <= 0L || pcId <= 0) {
                if (handle != null) handle.cancel();
                return;
            }
            final Object live = L1World.getInstance().findObjectBySmartId(expectedSmartId);
            if (!(live instanceof L1PcInstance)) { handle.cancel(); return; }
            final L1PcInstance pc = (L1PcInstance) live;
            if (pc.getId() != pcId || pc.getSessionStamp() != expectedSession
                    || L1World.getInstance().findObject(pcId) != pc
                    || CIOThreadCallback.getInstance().shardOf(pc) != this) {
                handle.cancel();
                return;
            }
            CIOState st = states.get(pcId);
            if (st != null && st.sessionActor != null
                    && (st.sessionActor != pc || st.sessionStamp != expectedSession)) {
                onUnregister(pcId, st.sessionActor, st.sessionStamp);
                st = null;
            }
            if (st == null) st = states.getOrCreate(pcId);
            if (st == null) { handle.cancel(); return; }
            st.pcId = pcId;
            st.sessionActor = pc;
            st.sessionStamp = expectedSession;
            st.alive = true;

			switch (kind) {
			case GHOST_END: if (st.ghostHandle != null) st.ghostHandle.cancel(); st.ghostHandle = handle; break;
			case INVIS_DELAY: if (st.invisHandle != null) st.invisHandle.cancel(); st.invisHandle = handle; break;
			case FISHING: if (st.fishingHandle != null && st.fishingHandle != handle) st.fishingHandle.cancel(); st.fishingHandle = handle; break;
			case HP_BAR: if (st.hpBarHandle != null && st.hpBarHandle != handle) st.hpBarHandle.cancel(); st.hpBarHandle = handle; break;
			case GENERIC: default: st.addGeneric(handle); break;
			}
			threadLocalTimer.schedule(delayMs, task, handle);
		}

		@Override
		public void run() {
            CIOShardContext.enterShard(idx);
            try {
                lastWheelAtMs = nowMs();
                while (running) {
                    if (Thread.interrupted() && !running) break;
                    boolean backlog;
                    boolean ownerFailed = false;
                    long sleepMs;
                    try {
                        lastProgressNs = System.nanoTime();
                        lastProgressCounter++;
                        final long now = nowMs();
                        final long elapsed = now - lastWheelAtMs;
                        if (elapsed >= WHEEL_TICK_MS) {
                            final int steps = (int) Math.min(100L, elapsed / WHEEL_TICK_MS);
                            for (int i = 0; i < steps; i++) {
                                lastWheelAtMs += WHEEL_TICK_MS;
                                threadLocalTimer.tick(this);
                            }
                        }
                        // Run accepted network commands immediately, on their
                        // assigned shard. Never attach RECV to the timer wheel.
                        // A bounded burst lets overdue timers run next pass.
                        int packetDrained = 0;
                        PacketTask packet;
                        while (packetDrained < MAX_PACKET_BURST
                                && running && (packet = packetIngress.poll()) != null) {
                            try {
                                packet.run();
                            } catch (Throwable packetFailure) {
                                LOGGER.log(Level.SEVERE,
                                        "[CIO-PACKET] shard handler failed idx=" + idx,
                                        packetFailure);
                            }
                            packetDrained++;
                        }
                        sleepMs = lastWheelAtMs + WHEEL_TICK_MS - nowMs();
                        backlog = !packetIngress.isEmpty();
                        // Snapshot on the owner only; accepting sockets never
                        // lock/inspect packetIngress merely to choose a shard.
                        lastBacklogMask = backlog ? 1 : 0;
                    } catch (Throwable t) {
                        LOGGER.log(Level.SEVERE,
                                "[CIOThread] owner loop failure; shard survives idx=" + idx, t);
                        lastProgressNs = System.nanoTime();
                        lastProgressCounter++;
                        ownerFailed = true;
                        backlog = true;
                        sleepMs = 0L;
                    }
                    if (ownerFailed) {
                        // Preserve the original anti-exception-spin delay.
                        LockSupport.parkNanos(1_000_000L);
                        continue;
                    }
                    if (backlog) continue;
                    if (!preparePark()) continue;
                    try {
                        if (sleepMs <= 0L) {
                            LockSupport.parkNanos(IDLE_PARK_NS);
                        } else {
                            LockSupport.parkNanos(
                                    Math.min(MAX_SLEEP_MS, sleepMs) * 1_000_000L);
                        }
                    } finally {
                        finishPark();
                    }
                }
            } finally {
                try {
                    if (!running) {
                        // The stop monitor prevents any further successful
                        // admissions. Cancel every accepted, unexecuted frame;
                        // cancellation releases its codec/READ lifetime barrier.
                        PacketTask pending;
                        while ((pending = packetIngress.poll()) != null) {
                            try { pending.cancel(); }
                            catch (Throwable failure) {
                                LOGGER.log(Level.SEVERE,
                                        "[CIO-PACKET] shutdown cancellation failed idx=" + idx, failure);
                            }
                        }
                        synchronized (this) {
                            threadLocalTimer.discardOnShutdown();
                            states.discardOnShutdown();
                        }
                        lastBacklogMask = 0;
                    }
                } finally {
                    CIOShardContext.exitShard();
                }
            }
        }

		static long nowMs() { return System.nanoTime() / 1_000_000L; }
	}

	private static final class CIOState {
		int pcId;
		L1PcInstance sessionActor;
		int sessionStamp;
		boolean alive;
		TaskHandle ghostHandle, invisHandle, fishingHandle, hpBarHandle, heartbeatHandle, generic[];
		CreatureHeartbeatTask heartbeatTask;
		int genericSize;

		void addGeneric(TaskHandle h) {
			if (h == null) return;
			if (generic == null) generic = new TaskHandle[4];
			else {
				if (genericSize >= GENERIC_COMPACT_TRIGGER) compactGenericIfNeeded();
				if (genericSize >= generic.length) {
					TaskHandle[] n = new TaskHandle[generic.length << 1];
					System.arraycopy(generic, 0, n, 0, genericSize);
					generic = n;
				}
			}
			generic[genericSize++] = h;
		}

		void compactGenericIfNeeded() {
			if (generic == null || genericSize == 0) return;
			int live = 0;
			for (int i = 0; i < genericSize; i++) {
				TaskHandle h = generic[i];
				if (h != null && !h.isCancelled()) generic[live++] = h;
			}
			for (int i = live; i < genericSize; i++) generic[i] = null;
			genericSize = live;
		}

		void cancelAllGeneric() {
			if (generic == null || genericSize == 0) return;
			for (int i = 0; i < genericSize; i++) { if (generic[i] != null) generic[i].cancel(); generic[i] = null; }
			genericSize = 0;
		}

	}

	private static final class PcStateTable {
		private static final int EMPTY = 0, TOMBSTONE = Integer.MIN_VALUE;
		int[] keys; CIOState[] vals; int capMask, size, tombstones, resizeAt;

		PcStateTable(int initialCap) {
			int c = 1; while (c < initialCap) c <<= 1;
			init(c);
		}
		private void init(int c) { keys = new int[c]; vals = new CIOState[c]; capMask = c - 1; size = tombstones = 0; resizeAt = (int) (c * 0.70); }
		CIOState get(int key) { if (key <= 0) return null; int idx = findIndex(key); return (idx >= 0) ? vals[idx] : null; }
		CIOState getOrCreate(int key) {
			if (key <= 0) return null;
			if ((size + tombstones) >= resizeAt || tombstones > (keys.length >> 2)) rehash(keys.length << 1);
			int idx = findSlotForInsert(key); int k = keys[idx];
			if (k == key) { if (vals[idx] == null) vals[idx] = new CIOState(); return vals[idx]; }
			if (k == TOMBSTONE) tombstones--;
			keys[idx] = key; if (vals[idx] == null) vals[idx] = new CIOState(); size++; return vals[idx];
		}
		void remove(int key) {
			if (key <= 0) return;
			int idx = findIndex(key); if (idx < 0) return;
			keys[idx] = TOMBSTONE; vals[idx] = null; size--; tombstones++;
			if (tombstones > (keys.length >> 2)) rehash(keys.length);
		}
		private int findIndex(int key) { int idx = mix(key) & capMask; while (true) { int k = keys[idx]; if (k == EMPTY) return -1; if (k == key) return idx; idx = (idx + 1) & capMask; } }
		private int findSlotForInsert(int key) { int idx = mix(key) & capMask, firstTomb = -1; while (true) { int k = keys[idx]; if (k == key) return idx; if (k == EMPTY) return (firstTomb >= 0) ? firstTomb : idx; if (k == TOMBSTONE && firstTomb < 0) firstTomb = idx; idx = (idx + 1) & capMask; } }
        void discardOnShutdown() {
            for (int i = 0; i < vals.length; i++) {
                final CIOState st = vals[i];
                if (st == null) continue;
                if (st.ghostHandle != null) st.ghostHandle.cancel();
                if (st.invisHandle != null) st.invisHandle.cancel();
                if (st.fishingHandle != null) st.fishingHandle.cancel();
                if (st.hpBarHandle != null) st.hpBarHandle.cancel();
                if (st.heartbeatHandle != null) st.heartbeatHandle.cancel();
                st.cancelAllGeneric();
                st.sessionActor = null;
                st.heartbeatTask = null;
                vals[i] = null;
                keys[i] = EMPTY;
            }
            size = 0;
            tombstones = 0;
        }

		private void rehash(int newCap) { int c = 1; while (c < newCap) c <<= 1; int[] oldK = keys; CIOState[] oldV = vals; init(c); for (int i = 0; i < oldK.length; i++) { int k = oldK[i]; if (k == EMPTY || k == TOMBSTONE) continue; CIOState v = oldV[i]; int idx = findSlotForInsert(k); keys[idx] = k; vals[idx] = v; size++; } }
		private static int mix(int x) { x ^= (x >>> 16); x *= 0x7feb352d; x ^= (x >>> 15); x *= 0x846ca68b; x ^= (x >>> 16); return x; }
	}



	

	/**
	 * lawful → 바포레벨 매핑
	 */
	public static int bapoLevelByLawful(int lawful) {
		if (lawful >= 10000) {
			if (lawful >= 30000) return 2;   // -6AC, +9MR
			if (lawful >= 20000) return 1;   // -4AC, +6MR
			return 0;                        // -2AC, +3MR
		}
		if (lawful >= -9999)  return 7;      // 중립
		if (lawful >= -19999) return 3;      // SP+1, AT+1
		if (lawful >= -29999) return 4;      // SP+2, AT+3
		return 5;                            // SP+3, AT+5
	}
	
	public static void LawfulBonus(L1PcInstance pc) {
		final int bapo = bapoLevelByLawful(pc.getLawful());

		// 바포레벨 → 스탯 보너스
		int ACvalue = 0, MRvalue = 0, SPvalue = 0, ATvalue = 0;
		switch (bapo) {
			case 2: ACvalue = -6; MRvalue = 9; break;   // 30000~ (순수)
			case 1: ACvalue = -4; MRvalue = 6; break;   // 20000~
			case 0: ACvalue = -2; MRvalue = 3; break;   // 10000~
			case 7: break;                               // 중립
			case 3: SPvalue = 1; ATvalue = 1; break;    // -10000~ (카오)
			case 4: SPvalue = 2; ATvalue = 3; break;    // -20000~
			case 5: SPvalue = 3; ATvalue = 5; break;    // -30000~
			default: break;
		}

		pc.setOBapoLevel(pc.getNBapoLevel());
		pc.setNBapoLevel(bapo);

		if (pc.getOBapoLevel() != pc.getNBapoLevel()) {
			SC_PacketBox.send(pc, SC_PacketBox.BAPO, pc.getOBapoLevel(), false);
			SC_PacketBox.send(pc, SC_PacketBox.BAPO, pc.getNBapoLevel(), true);
			pc.setOBapoLevel(pc.getNBapoLevel());
			SC_PacketBox.send(pc, SC_PacketBox.BAPO, 6, false);
		}

		// ── AC ──
		if (ACvalue != pc.LawfulAC) {
			if (pc.LawfulAC != 0) pc.getAC().addAc(pc.LawfulAC * -1);
			pc.LawfulAC = ACvalue;
			if (ACvalue != 0) pc.getAC().addAc(ACvalue);
			SC_OwnCharStatus.send(pc);
		}
		// ── MR ──
		if (MRvalue != pc.LawfulMR) {
			if (pc.LawfulMR != 0) pc.getResistance().addMr(pc.LawfulMR * -1);
			pc.LawfulMR = MRvalue;
			if (MRvalue != 0) pc.getResistance().addMr(MRvalue);
			SC_SPMR.send(pc);
		}
		// ── SP ──
		if (SPvalue != pc.LawfulSP) {
			if (pc.LawfulSP != 0) pc.getAbility().addSp(pc.LawfulSP * -1);
			pc.LawfulSP = SPvalue;
			if (SPvalue != 0) pc.getAbility().addSp(SPvalue);
			SC_SPMR.send(pc);
		}
		// ── AT (공격력) ──
		if (ATvalue != pc.LawfulAT) {
			if (pc.LawfulAT != 0) pc.setBapodmg(pc.LawfulAT * -1);
			pc.LawfulAT = ATvalue;
			if (ATvalue != 0) pc.setBapodmg(ATvalue);
			SC_OwnCharStatus.send(pc);
		}
	}
	
	/**
	 * Timer registration executes directly under the existing CThreadLocalTimer shard lock.
	 */
	/**
	 * PC-owned item delay timer.
	 *
	 * Timer admission is directly serialized by the CThreadLocalTimer; no owner FIFO. Wheel node에는 PC reference 대신
	 * smartId + delayId + generation primitive만 저장한다.
	 */
	public boolean schedulePcItemDelay(
			L1PcInstance pc,
			int delayId,
			int generation,
			long delayMs) {

		if (pc == null
				|| delayId <= 0
				|| generation == 0
				|| delayMs <= 0L) {
			return false;
		}

		final long smartId =
				L1World.getInstance().smartIdOf(pc);

		if (smartId == 0L) {
			return false;
		}

		final CIOThread owner = shardOf(pc);
		if (owner == null) {
			return false;
		}

		final int expectedSession = pc.getSessionStamp();
		if (currentShardIndex() == owner.idx) {
			if (pc.getSessionStamp() != expectedSession
					|| L1World.getInstance().smartIdOf(pc) != smartId) return false;
			owner.onPcItemDelayTimerSchedule(
					delayMs,
					smartId,
					delayId,
					generation);
			return true;
		}

		if (pc.getSessionStamp() != expectedSession || shardOf(pc) != owner
				|| L1World.getInstance().smartIdOf(pc) != smartId) return false;

		return owner.admitTimer(
				TimerOp.PC_ITEM_DELAY_TIMER,
				(int) smartId,
				delayMs,
				(int) (smartId >>> 32),
				delayId,
				generation,
				null,
				null,
				null);
	}


    /** Existing CThreadLocalTimer owns deferred teleport completion, not a separate ThreadPool. */
    public boolean schedulePcTeleport(L1PcInstance pc, long delayMs) {
        if (pc == null || delayMs <= 0L) return false;
        final int expectedSession = pc.getSessionStamp();
        final int expectedEpoch = pc.nextTeleportTimerEpoch();
        final CIOThread owner = shardOf(pc);
        if (owner == null || !owner.running) return false;
        final long smartId = L1World.getInstance().smartIdOf(pc);
        if (smartId == 0L) return false;

        if (currentShardIndex() == owner.idx) {
            if (!owner.running || pc.getSessionStamp() != expectedSession
                    || pc.teleportTimerEpoch() != expectedEpoch || shardOf(pc) != owner
                    || L1World.getInstance().smartIdOf(pc) != smartId) return false;
            owner.threadLocalTimer.scheduleTeleport(delayMs, smartId, expectedSession, expectedEpoch);
            return true;
        }
        if (!owner.running || pc.getSessionStamp() != expectedSession
                || pc.teleportTimerEpoch() != expectedEpoch || shardOf(pc) != owner
                || L1World.getInstance().smartIdOf(pc) != smartId) return false;
        return owner.admitTimer(TimerOp.PC_TELEPORT_TIMER,
                (int) smartId, delayMs, (int) (smartId >>> 32),
                expectedSession, expectedEpoch, null, null, null);
    }




    public boolean scheduleActionTimer(
            CCreatureController controller, int timerId,
            int generation, long delayMs) {
        if (controller == null || delayMs <= 0L) return false;
        final L1PcInstance pc = controller.actor();
        if (pc == null) return false;
        final int expectedSession = pc.getSessionStamp();
        final CIOThread owner = shardOf(pc);
        if (owner == null || !owner.running || controller.actor() != pc) return false;

        if (currentShardIndex() == owner.idx) {
            if (!owner.running || controller.actor() != pc
                    || pc.getSessionStamp() != expectedSession) return false;
            owner.onActionTimerSchedule(delayMs, controller, timerId,
                    generation, expectedSession);
            return true;
        }
        if (!owner.running || controller.actor() != pc
                || pc.getSessionStamp() != expectedSession
                || shardOf(pc) != owner) return false;

        return owner.admitTimer(TimerOp.ACTION_TIMER,
                expectedSession, delayMs, timerId, generation, 0,
                controller, null, null);
    }


	/**
	 * Arm at most one timed-skill heartbeat for any L1Character.
	 *
	 * Permanent effects do not arm this timer.  When the last timed effect
	 * disappears, SkillEffectContainer invalidates the generation and the
	 * stale wheel node dies in O(1).
	 */
	public void submitCharacterSkillSet(
			L1Character cha,
			int skillId,
			int timeMillis) {

		submitCharacterSkillCommand(
				cha,
				SKILL_OP_SET,
				skillId,
				timeMillis);
	}

	public void submitCharacterSkillRemove(
			L1Character cha,
			int skillId,
			boolean stop) {

		submitCharacterSkillCommand(
				cha,
				stop
						? SKILL_OP_REMOVE_STOP
						: SKILL_OP_REMOVE_KILL,
				skillId,
				0);
	}

	public void submitCharacterSkillClear(L1Character cha) {
		submitCharacterSkillCommand(
				cha,
				SKILL_OP_CLEAR,
				0,
				0);
	}



    /** Non-blocking NPC skill admission. False means NOTHING was enqueued.
     * Callers that can retry must retain their own operation and retry later.
     * No worker, queue, or per-command object is allocated by this API.
     */
    public boolean trySubmitNpcSkillSet(L1NpcInstance npc, int skillId, int duration) {
        return npc != null && NpcServer.getInstance().postNpcSkill(
                npc, SKILL_OP_SET, skillId, duration);
    }

    public boolean trySubmitNpcSkillRemove(L1NpcInstance npc, int skillId, boolean stop) {
        return npc != null && NpcServer.getInstance().postNpcSkill(
                npc, stop ? SKILL_OP_REMOVE_STOP : SKILL_OP_REMOVE_KILL, skillId, 0);
    }

    public boolean trySubmitNpcSkillClear(L1NpcInstance npc) {
        return npc != null && NpcServer.getInstance().postNpcSkill(
                npc, SKILL_OP_CLEAR, 0, 0);
    }

    /** Atomic relative to other NPC-owner skill commands: remove then set
     * with ONE bounded admission, so a failed second enqueue cannot strand
     * the target with its previous effect already removed.
     */
    public boolean trySubmitNpcSkillReplace(L1NpcInstance npc, int skillId, int duration) {
        return npc != null && NpcServer.getInstance().postNpcSkill(
                npc, SKILL_OP_REPLACE, skillId, duration);
    }

	private void submitCharacterSkillCommand(
			L1Character cha,
			byte op,
			int skillId,
			int timeMillis) {

		if (cha == null) return;
        if (cha instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) cha;
            final CCreatureController controller = pc.getCreatureController();
            if (controller != null && controller.actor() == pc) {
                controller.AddExecutableSkillEffect(op, skillId, timeMillis);
            }
            return;
        }

        if (cha instanceof L1NpcInstance) {
            final L1NpcInstance npc = (L1NpcInstance) cha;
            if (npc.isDestroyed()) {
                // Teardown must not compete for a bounded skill-event lease.
                // The existing NPC-owner wake clears the retired NPC's effects.
                if (op == SKILL_OP_CLEAR) {
                    NpcServer.getInstance().clearDestroyedNpcSkills(npc);
                }
                return;
            }
            if (!NpcServer.getInstance().postNpcSkill(npc, op,
                    skillId, timeMillis) && NpcServer.getInstance().isRunning()
                    && !npc.isDestroyed()) {
                // Public skill mutators are void; do not silently lose an
                // accepted-looking mutation when the bounded NPC pool is full.
                throw new java.util.concurrent.RejectedExecutionException(
                        "NPC skill command admission exhausted");
            }
            return;
        }
        // The only concrete game characters in this server are PCs and NPCs.
        // Do not redirect skill effects to a timer-thread command mailbox.
        // The generic base character, if used in isolation, has no entity
        // execution context and therefore executes inline.
        runCharacterSkillDirect(cha, op, skillId, timeMillis);
	}

    /** Called on the NPC's existing NpcServer owner, not the CIO wheel thread. */
    public void applyNpcCharacterSkill(L1NpcInstance npc, byte op,
            int skillId, int timeMillis) {
        if (npc != null && !npc.isDestroyed()) {
            runCharacterSkillDirect(npc, op, skillId, timeMillis);
        }
    }

    public void runNpcSkillHeartbeatFromOwner(L1NpcInstance npc, int generation) {
        if (npc == null || npc.isDestroyed() || !npc.skillEffectHeartbeatValid(generation)) return;
        long nextDelay = 0L;
        try {
            nextDelay = npc.skillEffectHeartbeatTick(generation);
        } catch (Throwable failure) {
            LOGGER.log(Level.SEVERE, "NPC skill heartbeat failed", failure);
        }
        if (nextDelay <= 0L || !npc.skillEffectHeartbeatValid(generation)
                || !scheduleSkillHeartbeat(npc, generation, nextDelay)) {
            npc.skillEffectHeartbeatRelease(generation);
        }
    }

    /** Called only by the PC's 32-slot serializer. */
    public void applyCharacterSkillFromController(L1PcInstance pc,
            byte op, int skillId, int timeMillis) {
        runCharacterSkillDirect(pc, op, skillId, timeMillis);
    }

	private void runCharacterSkillDirect(
			L1Character cha,
			byte op,
			int skillId,
			int timeMillis) {

		switch (op) {
		case SKILL_OP_SET:
			cha.skillEffectSetFromOwner(skillId, timeMillis);
			break;
		case SKILL_OP_REMOVE_STOP:
			cha.skillEffectRemoveFromOwner(skillId, true);
			break;
		case SKILL_OP_REMOVE_KILL:
			cha.skillEffectRemoveFromOwner(skillId, false);
			break;
		case SKILL_OP_CLEAR:
			cha.skillEffectClearFromOwner();
			break;
        case SKILL_OP_REPLACE:
            cha.skillEffectRemoveFromOwner(skillId, false);
            cha.skillEffectSetFromOwner(skillId, timeMillis);
            break;
		case SKILL_OP_ARM:
            armCharacterSkillHeartbeatDirect(cha);
			break;
		default:
			break;
		}
	}

	/**
	 * Must execute on the character owner.
	 */
	public void ensureCharacterSkillHeartbeatFromOwner(L1Character cha) {
        if (cha == null || cha.getId() <= 0) return;
        if (cha instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) cha;
            final CCreatureController controller = pc.getCreatureController();
            if (controller != null && controller.actor() == pc) {
                controller.AddExecutableSkillEffect(SKILL_OP_ARM, 0, 0);
            }
            return;
        }
        if (cha instanceof L1NpcInstance) {
            if (!NpcServer.getInstance().postNpcSkill((L1NpcInstance) cha, SKILL_OP_ARM, 0, 0)
                    && NpcServer.getInstance().isRunning()) {
                throw new java.util.concurrent.RejectedExecutionException(
                        "NPC skill heartbeat admission exhausted");
            }
            return;
        }
        // Bare L1Character is not a PC/NPC world object. No CIO game owner.
        armCharacterSkillHeartbeatDirect(cha);
    }

    /** Run only inside a character execution boundary, then register timer work. */
    private void armCharacterSkillHeartbeatDirect(L1Character cha) {
        final int generation = cha.skillEffectHeartbeatTryArm();
        if (generation != 0 && !scheduleSkillHeartbeat(cha, generation,
                SKILL_EFFECT_HEARTBEAT_MS)) {
            cha.skillEffectHeartbeatRelease(generation);
        }
    }

    private boolean scheduleSkillHeartbeat(L1Character cha, int generation, long delayMs) {
        final CIOThread owner = characterOwner(cha);
        if (owner == null || !owner.running || generation == 0 || delayMs <= 0L) {
            return false;
        }
        if (Thread.currentThread() == owner.thread) {
            owner.onCharacterSkillHeartbeatSchedule(delayMs, cha, generation);
            return true;
        }
        return owner.admitTimer(TimerOp.CHARACTER_SKILL_HEARTBEAT,
                0, delayMs, generation, 0, 0, cha, null, null);
    }

    /** PC skill expiration is serialized with movement, attack and skill edits. */
    public void runSkillHeartbeatFromController(L1PcInstance pc, int generation) {
        if (!pc.skillEffectHeartbeatValid(generation)) return;
        long nextDelay = 0L;
        try {
            nextDelay = pc.skillEffectHeartbeatTick(generation);
        } catch (Throwable failure) {
            LOGGER.log(Level.SEVERE, "PC skill heartbeat failed", failure);
        }
        if (nextDelay <= 0L || !pc.skillEffectHeartbeatValid(generation)
                || !scheduleSkillHeartbeat(pc, generation, nextDelay)) {
            pc.skillEffectHeartbeatRelease(generation);
        }
    }



	/** AI-side primitive decision -> fixed NPC CIO owner. */
	public void submitNpcAction(L1NpcInstance npc, byte action, byte pendingAction) {
        if (npc == null) return;
        if (npc.getId() <= 0 || !npc.aiEngineIsRunning()) {
            npc.npcActionReleaseCadence();
            return;
        }
        NpcServer.getInstance().submitAction(npc, action, pendingAction);
    }

    public void cancelNpcAction(L1NpcInstance npc) {
        if (npc != null && npc.getId() > 0) NpcServer.getInstance().cancelAction(npc);
    }

    public void scheduleNpcActionTimer(L1NpcInstance npc, int generation, long delayMs) {
        NpcServer.getInstance().armActionTimer(npc, generation, delayMs);
    }


	/**
	 * NPC object lifetime / one-shot timer.
	 *
	 * 30~60분짜리 summon/doll timer가 NPC reference를 오래 잡고 있지 않도록
	 * wheel에는 smartId + timerId + generation만 저장한다.
	 */
	public void scheduleNpcTimer(
			L1NpcInstance npc,
			int timerId,
			int generation,
			long delayMs) {

		if (npc == null
				|| npc.getId() <= 0
				|| timerId <= 0
				|| generation == 0
				|| delayMs <= 0L) {
			return;
		}

		final long smartId =
				L1World.getInstance()
						.smartIdOf(npc);

		if (smartId == 0L) {
			return;
		}

		final CIOThread owner =
				npcOwner(npc);

		if (owner == null) {
			return;
		}

		if (currentShardIndex() == owner.idx) {
			owner.onNpcTimerSchedule(
					delayMs,
					smartId,
					timerId,
					generation);
			return;
		}

		owner.admitTimer(
				TimerOp.NPC_TIMER,
				(int) smartId,
				delayMs,
				(int) (smartId >>> 32),
				timerId,
				generation,
				null,
				null,
				null);
	}


	/**
	 * PC affinity와 무관한 world object이므로 objectId hash로 고정 owner를 정한다.
	 * 이미 owner thread 안이면 MPSC command allocation 없이 바로 wheel에 등록한다.
	 */
	public void scheduleItemTimer(
			int objectId,
			int timerId,
			int generation,
			long delayMs) {

		if (objectId <= 0
				|| timerId <= 0
				|| generation == 0
				|| delayMs <= 0L) {
			return;
		}

		final long smartId = L1World.getInstance().smartIdOf(objectId);
		if (smartId == 0L) {
			return;
		}

		int h = objectId;
		h ^= (h >>> 16);

		final CIOThread owner =
				shards[(h & 0x7fffffff) % shardCount];

		if (currentShardIndex() == owner.idx) {
			owner.onItemTimerSchedule(
					delayMs,
					smartId,
					timerId,
					generation);
			return;
		}

		owner.admitTimer(
						TimerOp.ITEM_TIMER,
						(int) smartId,
						delayMs,
						(int) (smartId >>> 32),
						timerId,
						generation,
						null,
						null,
						null);
	}


	/**
	 * 외부 death/decay producer -> 고정 CIOThread owner wake.
	 *
	 * 같은 Maker에 여러 decay가 동시에 들어와도 npcMakerTryQueue()가
	 * wake task를 하나로 합치고, 실제 slot state commit은 owner가 수행한다.
	 */
	public void wakeNpcMaker(L1Spawn maker) {
		if (maker == null || !maker.npcMakerTryQueue()) {
			return;
		}

		scheduleNpcMakerTimer(
				maker,
				L1Spawn.TIMER_NPC_MAKER_WAKE,
				0,
				1L);
	}

	/**
	 * startup initial spawn 실패 slot을 CIO owner에 등록한다.
	 * 이미 deadline은 L1Spawn에 들어 있으므로 wake만 필요하다.
	 */
	public void activateNpcMaker(L1Spawn maker) {
		wakeNpcMaker(maker);
	}

	/**
	 * NpcMaker AddTimer -> CIO shared threadLocalTimer.
	 *
	 * spawn id hash로 owner를 고정한다. PC connection affinity와 무관한
	 * server object이므로 같은 Maker는 항상 같은 CIOThread에서 직렬 처리된다.
	 */
	public void scheduleNpcMakerTimer(
			L1Spawn maker,
			int timerId,
			int generation,
			long delayMs) {

		if (maker == null
				|| timerId <= 0
				|| delayMs <= 0L) {
			return;
		}

		final CIOThread owner =
				npcMakerOwner(maker);

		if (owner == null) {
			/*
			 * wake CAS를 이미 올린 상태라면 owner 선택 실패 시 다시 시도 가능하게 내린다.
			 * 현재 shardCount는 항상 1 이상이라 정상적으로는 도달하지 않는다.
			 */
			if (timerId == L1Spawn.TIMER_NPC_MAKER_WAKE) {
				maker.npcMakerClearQueued();
			}
			return;
		}

		if (!owner.running) {
			if (timerId == L1Spawn.TIMER_NPC_MAKER_WAKE) maker.npcMakerClearQueued();
			return;
		}

		if (currentShardIndex() == owner.idx) {
			owner.onNpcMakerTimerSchedule(
					delayMs,
					maker,
					timerId,
					generation);
			return;
		}

		if (!owner.admitTimer(
						TimerOp.NPC_MAKER_TIMER,
						0,
						delayMs,
						timerId,
						generation,
						0,
						maker,
						null,
						null)
				&& timerId == L1Spawn.TIMER_NPC_MAKER_WAKE) {
			// Admission failed during shutdown: no wheel node owns this wake.
			// Release the existing coalescing flag so a later producer can retry.
			maker.npcMakerClearQueued();
		}
	}



	private CIOThread characterOwner(L1Character cha) {
		if (cha == null || shardCount <= 0) {
			return null;
		}

		if (cha instanceof L1PcInstance) {
			return shardOf((L1PcInstance) cha);
		}

		if (cha instanceof L1NpcInstance) {
			return npcOwner((L1NpcInstance) cha);
		}

		int h = cha.getId();
		h ^= (h >>> 16);

		return shards[(h & 0x7fffffff) % shardCount];
	}


	private CIOThread npcOwner(L1NpcInstance npc) {
		if (npc == null || shardCount <= 0) {
			return null;
		}

		int h = npc.getId();
		h ^= (h >>> 16);

		return shards[(h & 0x7fffffff) % shardCount];
	}

	private CIOThread npcMakerOwner(L1Spawn maker) {
		if (maker == null || shardCount <= 0) {
			return null;
		}

		int h = maker.getId();

		if (h <= 0) {
			h = maker.getNpcId();
		}

		h ^= (h >>> 16);

		return shards[
				(h & 0x7fffffff)
						% shardCount];
	}



	public void markSpellActive(
			L1PcInstance pc,
			int skillId,
			int tId,
			int tX,
			int tY,
			String msg) {
		if (pc != null && skillId > 0) {
			pc.getCreatureController().Skill(skillId, tId, tX, tY, msg);
		}
	}

	public void markWandActive(
			L1PcInstance pc,
			L1ItemInstance item,
			int tId,
			int tX,
			int tY) {

		if (pc == null || item == null) {
			return;
		}

		int spriteId = 10;
		double damageRevision = 0.16D;

		switch (item.getItemId()) {
		case 40007:
		case 40412:
			break;
		case 40006:
		case 140006:
			spriteId = 11736;
			damageRevision = 0.5D;
			break;
		default:
			break;
		}

		pc.getCreatureController().GeneralWand(
				item, spriteId, damageRevision, tId, tX, tY);
	}

	private CIOThread actionThreadOf(L1Character target, int attackerId) {
		if (target instanceof L1PcInstance) {
			final CIOThread owner = shardOf((L1PcInstance) target);
			if (owner != null) {
				return owner;
			}
		}

		if (attackerId > 0) {
			final Object attacker = L1World.getInstance().findObject(attackerId);
			if (attacker instanceof L1PcInstance) {
				final CIOThread owner = shardOf((L1PcInstance) attacker);
				if (owner != null) {
					return owner;
				}
			}
		}

		if (target == null) {
			return null;
		}

		int h = target.getId();
		h ^= (h >>> 16);
		return shards[(h & 0x7fffffff) % shardCount];
	}

	private static final class TomahawkTask implements WheelTask {
		final L1Character target;
		final int attackerId;
		final int mapId;
		final int totalTicks;
		final int baseDamage;
		final int remainder;
		final long token;
		final CIOThread owner;
		final TaskHandle handle;
		int tickIndex;

		TomahawkTask(
				L1Character target, int attackerId, int mapId,
				int totalTicks, int baseDamage, int remainder, long token,
				CIOThread owner, TaskHandle handle) {
			this.target = target;
			this.attackerId = attackerId;
			this.mapId = mapId;
			this.totalTicks = totalTicks;
			this.baseDamage = baseDamage;
			this.remainder = remainder;
			this.token = token;
			this.owner = owner;
			this.handle = handle;
		}

		@Override
		public void run() {
            if (handle.isCancelled() || target == null || !target.isTomahawkState(token)) {
                handle.cancel();
                return;
            }
            if (target instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) target;
                final CCreatureController controller = pc.getCreatureController();
                if (controller == null || controller.actor() != pc
                        || !controller.AddExecutableWheelTask(this)) {
                    // Retry the due-time signal when the controller is full.
                    owner.threadLocalTimer.schedule(WHEEL_TICK_MS, this, handle);
                }
            } else if (target instanceof L1NpcInstance) {
                if (!NpcServer.getInstance().postNpcWheelTask((L1NpcInstance) target, this)) {
                    // The bounded NPC event pool is full: retain the same
                    // owner timer/handle and retry without dropping the effect.
                    if (NpcServer.getInstance().isRunning()) {
                        owner.threadLocalTimer.schedule(WHEEL_TICK_MS, this, handle);
                    } else {
                        handle.cancel();
                    }
                }
            } else {
                // Non-PC/NPC L1Character is not owned by either game executor.
                synchronized (target) {
                    executeTick();
                }
            }
        }

        @Override public void runOnController(L1PcInstance pc) {
            if (target == pc) executeTick();
        }

        @Override public void runOnNpc(L1NpcInstance npc) {
            if (target == npc) executeTick();
        }

        private void executeTick() {
            if (handle.isCancelled() || target == null || !target.isTomahawkState(token)) {
                handle.cancel();
                finishPcWheelTask(owner, this, 7, 0L);
                return;
            }
            final int index = ++tickIndex;
            boolean again = false;
            try {
                again = TomahawkBleedService.getInstance().processWheelTick(target,
                        attackerId, mapId, index, totalTicks, baseDamage, remainder, token);
            } catch (Throwable failure) {
                target.finishTomahawkState(token);
                LOGGER.log(Level.WARNING, "Tomahawk tick failed", failure);
            } finally {
                if (!again || !target.isTomahawkState(token)) handle.cancel();
                finishPcWheelTask(owner, this, 7, handle.isCancelled() ? 0L : 1000L);
            }
        }

		@Override
		public boolean isCancelled() {
			return handle.isCancelled();
		}
	}

	public void markTomahawkActive(
			L1Character target,
			int attackerId,
			int mapId,
			int totalTicks,
			int baseDamage,
			int remainder,
			long token) {

		if (target == null || totalTicks <= 0 || token == 0L) {
			return;
		}

		final CIOThread owner = actionThreadOf(target, attackerId);
		if (owner == null) {
			return;
		}

		final TaskHandle handle = new TaskHandle();
		final TomahawkTask task = new TomahawkTask(
				target, attackerId, mapId, totalTicks,
				baseDamage, remainder, token, owner, handle);

		if (currentShardIndex() == owner.idx) {
			owner.onRawSchedule(1000L, task, handle);
		} else {
			owner.admitTimer(
					TimerOp.RAW_TIMER, 0, 1000L, task, handle, null);
		}
	}


	/** Reset game actions on the PC controller; CIO retains only timer metadata. */
	public void clearPcState(L1PcInstance pc) {
        if (pc == null) return;
        final CCreatureController controller = pc.getCreatureController();
        if (controller != null && controller.actor() == pc) {
            controller.Reset();
        }
    }

	public static boolean isWorldTickWand(int itemId) {
		return itemId == 40007 || itemId == 40412 || itemId == 40006 || itemId == 140006;
	}
	
	public static void do_clanjoin(L1PcInstance pc, L1ClanJoinInstance cj) {
		int tx = cj.getX();
		int ty = cj.getY();
		int ox = pc.getX();
		int oy = pc.getY();
		int cx = Math.abs(ox - tx);
		int cy = Math.abs(oy - ty);
		int r = pc.getAttackRang();
		if (cx > r || cy > r)
			return;

		int h = MJCommons.calcheading(ox, oy, tx, ty);
		if (!pc.getMap().isUserPassable(ox, oy, h))
			return;

		pc.setHeading(pc.targetDirection(tx, ty));
		SC_AttackStatus.sendAndBroadcast(pc, 0, pc.getCurrentWeapon() + 1, false);
	}
	
    /** Bounded join after stopping producer admission and the owner loops. */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw new NullPointerException("unit");
        if (timeout < 0L) throw new IllegalArgumentException("timeout");
        if (watchdogRunning) return false;
        final long budget = unit.toNanos(timeout);
        final long started = System.nanoTime();
        boolean selfJoin = false;
        for (int i = 0; i < shardCount; i++) {
            final Thread worker = shards[i].thread;
            if (worker == null) continue;
            if (worker == Thread.currentThread()) {
                selfJoin = true;
                continue;
            }
            while (worker.isAlive()) {
                final long remaining = budget - (System.nanoTime() - started);
                if (remaining <= 0L) return false;
                worker.join(Math.max(1L, remaining / 1_000_000L));
            }
        }
        if (watchdogThread == Thread.currentThread()) {
            selfJoin = true;
        } else {
            while (watchdogThread.isAlive()) {
                final long remaining = budget - (System.nanoTime() - started);
                if (remaining <= 0L) return false;
                watchdogThread.join(Math.max(1L, remaining / 1_000_000L));
            }
        }
        return !selfJoin;
    }

	public void shutdown() {
		watchdogRunning = false;
		LockSupport.unpark(watchdogThread);

		for (int i = 0; i < shardCount; i++) {
            if (shards[i] != null) {
                final CIOThread owner = shards[i];
                // Serialize stop against both event admission and READ-wait
                // publication. No producer may publish after final cleanup.
                synchronized (owner) {
                    owner.running = false;
                }
                final Thread t = owner.thread;
                if (t != null) LockSupport.unpark(t);
            }
		}
	}
}
