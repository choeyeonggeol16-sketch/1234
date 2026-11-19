package l1j.server.server;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.utill.PacketThreadFactory;
import l1j.server.server.utils.ThreadConfig;

public class ThreadPool {
	private static final Logger _log = Logger.getLogger(ThreadPool.class.getName());
	private static volatile ThreadPool _instance;

	// 스레드풀 상수 도입
	private static final long ONE_HUNDRED_YEARS_MS = 3155695200000L;
	private static final long MIN_DELAY_MS = 0L;
	private static final int INSTANT_POOL_KEEP_ALIVE_MINUTES = 1;
	private static final int INSTANT_QUEUE_CAPACITY = 65536;
	// Bound the number of retained scheduled tasks, including long-delay and
	// recurring tasks. These caps are admission limits, not time-wheel slots.
	private static final int SCHEDULED_PENDING_CAPACITY = 131072;
	private static final int HIGH_PRIORITY_PENDING_CAPACITY = 32768;

	private ScheduledThreadPoolExecutor _scheduler;
	private ThreadPoolExecutor _instantPool;
	private ScheduledThreadPoolExecutor _highPriorityScheduler;
    private final Object lifecycle = new Object();
    private volatile boolean stopping;
    private final Semaphore blockingSlots = new Semaphore(256);
    private final Set<BlockingTask> blockingTasks = ConcurrentHashMap.newKeySet();
    private final Set<Thread> dedicatedThreads = ConcurrentHashMap.newKeySet();
    private static final int MAX_DEDICATED_THREADS = 64;
    private static final ThreadLocal<Boolean> WORKER = new ThreadLocal<>();

    private static java.util.concurrent.ThreadFactory workerFactory(String name, int priority) {
        final PacketThreadFactory factory = new PacketThreadFactory(name, priority);
        return work -> factory.newThread(() -> {
            WORKER.set(Boolean.TRUE);
            try { work.run(); } finally { WORKER.remove(); }
        });
    }

    public boolean isShutdown() { return stopping; }
    private void requireOpen() {
        if (stopping) throw new RejectedExecutionException("ThreadPool is stopping");
    }


	public static ThreadPool getInstance() {
		if (_instance == null) {
			synchronized (ThreadPool.class) {
				if (_instance == null) {
					_instance = new ThreadPool();
				}
			}
		}
		return _instance;
	}

	private ThreadPool() {
		_log.fine("ThreadPool: Initializing advanced thread pools.");
		
		ThreadConfig.load();

		int scheduledSize = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		int instantSize = ThreadConfig.INSTANT_THREAD_POOL_SIZE;
		int highPrioritySize = ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE;

		// 고우선순위 스케줄러 풀
		_highPriorityScheduler = new BoundedScheduledExecutor(
			highPrioritySize, HIGH_PRIORITY_PENDING_CAPACITY,
			workerFactory("HighPriority Thread", Thread.MAX_PRIORITY),
			// A rejected deadline must never run on the caller (CIO/owner).
			// Fail admission instead of returning a Future that never executes.
			new ThreadPoolExecutor.AbortPolicy()
		);
		// Cancelled long-delay tasks must not retain their captured game objects.
        _highPriorityScheduler.setRemoveOnCancelPolicy(true);
        _highPriorityScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        _highPriorityScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

		// 메인 스케줄러 풀 (취소된 작업 자동 정리 및 거부 정책 적용)
		_scheduler = new BoundedScheduledExecutor(
			scheduledSize, SCHEDULED_PENDING_CAPACITY,
			workerFactory("ScheduledThread", Thread.NORM_PRIORITY),
			new ThreadPoolExecutor.AbortPolicy()
		);
        _scheduler.setRemoveOnCancelPolicy(true);
        _scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        _scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		_scheduler.prestartAllCoreThreads();

		// General instant work only. CIO shards, scheduler wheels and CacheD
		// keep their existing owners. Never run a rejected job on its submitter.
		_instantPool = new ThreadPoolExecutor(
			instantSize, Math.max(1, instantSize),
			INSTANT_POOL_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES,
			new LinkedBlockingQueue<>(INSTANT_QUEUE_CAPACITY),
			workerFactory("InstantThread", Thread.NORM_PRIORITY),
			new ThreadPoolExecutor.AbortPolicy()
		);
		_instantPool.prestartAllCoreThreads();

		// Both scheduled pools remove cancelled futures immediately; do not
		// scan the entire delayed queue once per minute just to purge it.
	}

	/**
	 * Bound retained delayed work without wrapping ScheduledFutureTask. Keeping
	 * the JDK task gives removeOnCancelPolicy its O(log N) heap-index removal
	 * instead of an O(N) search through the delayed queue.
	 *
	 * All external schedule entry points share this admission monitor. Active
	 * periodic tasks may requeue while producers fill the queue, so the hard
	 * upper bound is queueLimit + core thread count (no unbounded growth).
	 */
	private static final class BoundedScheduledExecutor extends ScheduledThreadPoolExecutor {
		private final int queueLimit;

		BoundedScheduledExecutor(int threads, int queueLimit,
				java.util.concurrent.ThreadFactory factory, RejectedExecutionHandler rejection) {
			super(threads, factory, rejection);
			this.queueLimit = queueLimit;
		}

		private void checkAdmission() {
			if (isShutdown() || getQueue().size() >= queueLimit)
				throw new RejectedExecutionException("Scheduled queue is closed or full: " + queueLimit);
		}

		@Override
		public synchronized ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
			checkAdmission();
			return super.schedule(task, delay, unit);
		}

		@Override
		public synchronized <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
			checkAdmission();
			return super.schedule(task, delay, unit);
		}

		@Override
		public synchronized ScheduledFuture<?> scheduleAtFixedRate(
				Runnable task, long initialDelay, long period, TimeUnit unit) {
			checkAdmission();
			return super.scheduleAtFixedRate(task, initialDelay, period, unit);
		}

		@Override
		public synchronized ScheduledFuture<?> scheduleWithFixedDelay(
				Runnable task, long initialDelay, long delay, TimeUnit unit) {
			checkAdmission();
			return super.scheduleWithFixedDelay(task, initialDelay, delay, unit);
		}
	}

	/**
	 * 취소된 태스크들을 메모리에서 즉시 해제합니다.
	 */
	public static void purge() {
		if (_instance != null) {
			try {
				if (_instance._scheduler != null) _instance._scheduler.purge();
				if (_instance._highPriorityScheduler != null) _instance._highPriorityScheduler.purge();
			} catch (Exception e) {
				_log.log(Level.WARNING, "ThreadPool purge error", e);
			}
		}
	}

	public void execute(Runnable r) {
		if (r == null) return;
		// No per-submission wrapper allocation. On task failure the existing
		// PacketThreadFactory uncaught handler logs it; the JDK pool replaces
		// a failed worker. Saturation/shutdown still throws to the submitter.
        requireOpen();
		_instantPool.execute(r);
	}

    /** Compatibility alias for legacy Thread subclasses; explicitly bounded and
     * rejected after shutdown. New callers use startDedicatedThread(). */
    @Deprecated
    public void execute(Thread t) { if (t != null) startDedicatedThread(t); }

    public void startDedicatedThread(Thread thread) {
        if (thread == null) throw new NullPointerException("thread");
        synchronized (lifecycle) {
            requireOpen();
            dedicatedThreads.removeIf(t -> !t.isAlive());
            if (dedicatedThreads.size() >= MAX_DEDICATED_THREADS)
                throw new RejectedExecutionException("Dedicated thread limit reached");
            thread.start();
            dedicatedThreads.add(thread);
        }
    }

    /** A countdown calling System.exit waits for the JVM hook. Detach it just
     * before exit so that hook can drain this pool without waiting on itself. */
    public void detachCurrentDedicatedThreadForJvmExit() {
        synchronized (lifecycle) { dedicatedThreads.remove(Thread.currentThread()); }
    }

    /** A bounded number of blocking jobs, each on its own virtual thread.
     * Cancellation releases a reservation only after a running job really exits. */
    public Future<?> scheduleBlocking(Runnable task, long delayMs) {
        if (task == null) throw new NullPointerException("task");
        synchronized (lifecycle) {
            requireOpen();
            if (!blockingSlots.tryAcquire())
                throw new RejectedExecutionException("Blocking task limit reached");
            final BlockingTask work = new BlockingTask(task);
            blockingTasks.add(work);
            try {
                work.launch = _scheduler.schedule(() -> {
                    if (stopping) { work.cancel(false); return; }
                    try { Thread.ofVirtual().name("ManagedBlocking").start(work); }
                    catch (Throwable failure) { work.cancel(false); throw failure; }
                }, validateDelay(delayMs), TimeUnit.MILLISECONDS);
                if (work.isCancelled()) work.launch.cancel(false);
                return work;
            } catch (RuntimeException | Error failure) {
                work.cancel(false);
                throw failure;
            }
        }
    }

    private final class BlockingTask extends FutureTask<Void> {
        // 0=reserved, 1=running, 2=physically retired (not merely Future-cancelled).
        private final java.util.concurrent.atomic.AtomicInteger state =
                new java.util.concurrent.atomic.AtomicInteger();
        volatile ScheduledFuture<?> launch;
        BlockingTask(Runnable task) { super(task, null); }
        @Override public void run() {
            if (!state.compareAndSet(0, 1)) return;
            WORKER.set(Boolean.TRUE);
            try { super.run(); }
            finally {
                WORKER.remove();
                if (state.compareAndSet(1, 2)) retire();
            }
        }
        @Override protected void done() {
            if (state.compareAndSet(0, 2)) retire();
            if (!isCancelled()) {
                try { get(); }
                catch (java.util.concurrent.ExecutionException failed) {
                    _log.log(Level.SEVERE, "Managed blocking task failed", failed.getCause());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        @Override public boolean cancel(boolean interrupt) {
            final boolean result = super.cancel(interrupt);
            final ScheduledFuture<?> timer = launch;
            if (timer != null) timer.cancel(false);
            return result;
        }
        private void retire() {
            blockingTasks.remove(this);
            blockingSlots.release();
        }
    }

    /** Required scheduling exposes refusal to legacy fire-and-forget callers.
     * Nullable schedule APIs remain available to callers that already check. */
    public ScheduledFuture<?> scheduleRequired(Runnable r, long delay) {
        return requireScheduled(schedule(r, delay));
    }
    public ScheduledFuture<?> scheduleAtFixedRateRequired(Runnable r, long delay, long period) {
        return requireScheduled(scheduleAtFixedRate(r, delay, period));
    }
    public ScheduledFuture<?> scheduleAtFixedRateLongRequired(Runnable r, long delay, long period) {
        return requireScheduled(scheduleAtFixedRateLong(r, delay, period));
    }
    public ScheduledFuture<?> scheduleWithFixedDelayRequired(Runnable r, long delay, long period) {
        return requireScheduled(scheduleWithFixedDelay(r, delay, period));
    }
    public ScheduledFuture<?> schedulePriorityTaskAtFixedRateRequired(Runnable r, long delay, long period) {
        return requireScheduled(schedulePriorityTaskAtFixedRate(r, delay, period));
    }
    private static ScheduledFuture<?> requireScheduled(ScheduledFuture<?> future) {
        if (future == null) throw new RejectedExecutionException("Required scheduled task was not admitted");
        return future;
    }

	public ScheduledFuture<?> schedule(Runnable r, long delay) {
		if (r == null) return null;
		try {
			requireOpen();
			return _scheduler.schedule(new RunnableWrapper(r), validateDelay(delay), TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			_log.warning("ThreadPool: Failed to schedule task " + r.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long initialDelay, long period) {
		if (r == null) return null;
		try {
			requireOpen();
			return _scheduler.scheduleAtFixedRate(new RunnableWrapper(r), validateDelay(initialDelay), validateDelay(period), TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			_log.warning("ThreadPool: Failed to schedule recurring task " + r.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	/** A single recurring task keeps its deadline without resubmitting on every tick. */
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable r, long initialDelay, long delay) {
		if (r == null) return null;
		try {
			requireOpen();
			return _scheduler.scheduleWithFixedDelay(new RunnableWrapper(r),
					validateDelay(initialDelay), Math.max(1L, validateDelay(delay)), TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			_log.log(Level.WARNING, "ThreadPool recurring delayed task admission failed", e);
			return null;
		}
	}

	public ScheduledFuture<?> scheduleAtFixedRateLong(Runnable r, long initialDelay, long period) {
		return scheduleAtFixedRate(r, initialDelay, period);
	}
	
	public ScheduledFuture<?> schedulePriorityTaskAtFixedRate(Runnable r, long initialDelay, long period) {
		if (r == null) return null;
		if (_highPriorityScheduler == null) {
			return scheduleAtFixedRate(r, initialDelay, period);
		}
		try {
			requireOpen();
			return _highPriorityScheduler.scheduleAtFixedRate(new RunnableWrapper(r), validateDelay(initialDelay), validateDelay(period), TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			_log.warning("ThreadPool: Failed to schedule high priority task " + r.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	private long validateDelay(long delay) {
		if (delay < MIN_DELAY_MS) {
			return MIN_DELAY_MS;
		}
		if (delay > ONE_HUNDRED_YEARS_MS) {
			return ONE_HUNDRED_YEARS_MS;
		}
		return delay;
	}


	/**
	 * 태스크 실행 중 발생하는 예외를 안전하게 잡아주는 래퍼
	 */
	private static class RunnableWrapper implements Runnable {
		private final Runnable _wrappedRunnable;

		public RunnableWrapper(Runnable runnable) {
			_wrappedRunnable = runnable;
		}

		@Override
		public void run() {
			try {
				_wrappedRunnable.run();
			} catch (Throwable t) {
				final Thread currentThread = Thread.currentThread();
				final Thread.UncaughtExceptionHandler exceptionHandler = currentThread.getUncaughtExceptionHandler();
				if (exceptionHandler != null) {
					exceptionHandler.uncaughtException(currentThread, t);
				} else {
					_log.log(Level.SEVERE, "Uncaught exception in ThreadPool task", t);
				}
			}
		}
	}
	
	/**
	 * 서버 종료 시 모든 스레드 풀을 안전하게 종료합니다.
	 */
    public void shutdown() {
        if (!shutdown(30L, TimeUnit.SECONDS))
            throw new IllegalStateException("ThreadPool shutdown incomplete; accepted instant work retained");
    }

    /** Cancels background timers/managed blocking jobs, but drains every accepted
     * instant job. Timeout reports failure and retains the queue for a later wait. */
    public boolean shutdown(long timeout, TimeUnit unit) {
        if (unit == null || timeout < 0L) throw new IllegalArgumentException("invalid shutdown budget");
        if (Boolean.TRUE.equals(WORKER.get()))
            throw new IllegalStateException("Pool worker cannot await its own pool shutdown");
        final long started = System.nanoTime(), budget = unit.toNanos(timeout);
        synchronized (lifecycle) {
            if (!stopping) {
                stopping = true;
                for (BlockingTask task : blockingTasks) task.cancel(true);
                for (Thread thread : dedicatedThreads)
                    if (thread != Thread.currentThread()) thread.interrupt();
                _scheduler.shutdown();
                _highPriorityScheduler.shutdown();
                _instantPool.shutdown();
            }
        }
        try {
            if (!awaitPool(_instantPool, started, budget)
                    || !awaitPool(_scheduler, started, budget)
                    || !awaitPool(_highPriorityScheduler, started, budget)) return false;
            for (;;) {
                dedicatedThreads.removeIf(t -> !t.isAlive());
                boolean otherDedicated = false;
                for (Thread t : dedicatedThreads)
                    if (t != Thread.currentThread()) { otherDedicated = true; break; }
                if (blockingTasks.isEmpty() && !otherDedicated) return true;
                final long left = budget - (System.nanoTime() - started);
                if (left <= 0L) return false;
                TimeUnit.NANOSECONDS.sleep(Math.min(left, 1_000_000L));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean awaitPool(java.util.concurrent.ExecutorService pool,
            long started, long budget) throws InterruptedException {
        if (pool.isTerminated()) return true;
        final long left = budget - (System.nanoTime() - started);
        return left > 0L && pool.awaitTermination(left, TimeUnit.NANOSECONDS);
    }
}
