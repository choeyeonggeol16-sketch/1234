/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.configure.Config;
import l1j.server.server.model.monitor.L1PcMonitor;

public class GeneralThreadPool {
							
	private static final int SCHEDULED_CORE_POOL_SIZE = Config.SCHEDULED_CORE_POOLSIZE;
	
	private Executor _executor; // 범용 ExecutorService
	private ScheduledExecutorService _scheduler; // 범용 ScheduledExecutorService
	private ScheduledExecutorService _pcScheduler; // 플레이어의 모니터용 
	
	private final int PC_SCHEDULED_POOL_SIZE = 20;
	
	private static class LazyHolder {
		private static final GeneralThreadPool instance = new GeneralThreadPool();
	}
	public static GeneralThreadPool getInstance() {
		return LazyHolder.instance;
	}
	private GeneralThreadPool() {
		if (Config.THREAD_P_TYPE_GENERAL == 1) {
			_executor = Executors.newFixedThreadPool(Config.THREAD_P_SIZE_GENERAL);
		} else if (Config.THREAD_P_TYPE_GENERAL == 2) {
			_executor = Executors.newCachedThreadPool();
		} else if (Config.THREAD_P_TYPE_GENERAL == 3) {
			_executor = Executors.newWorkStealingPool();
		} else {
			_executor = null;
		}	
		_scheduler = Executors.newScheduledThreadPool(SCHEDULED_CORE_POOL_SIZE, new PriorityThreadFactory("GerenalSTPool", Thread.NORM_PRIORITY));
		_pcScheduler = Executors.newScheduledThreadPool(PC_SCHEDULED_POOL_SIZE, new PriorityThreadFactory("PcMonitorSTPool", Thread.NORM_PRIORITY));
        ((java.util.concurrent.ScheduledThreadPoolExecutor) _scheduler).setRemoveOnCancelPolicy(true);
        ((java.util.concurrent.ScheduledThreadPoolExecutor) _pcScheduler).setRemoveOnCancelPolicy(true);
	}

	public void execute(Runnable r) {
		if (_executor == null) {
			Thread t = new Thread(r);
			t.start();
		} else {
			_executor.execute(r);
		}
	}
	public void execute(Thread t) {
		t.start();
	}

	public ScheduledFuture<?> schedule(Runnable r, long delay) {
		try {
			if (delay <= 0) {
                execute(r);
				return null;
			}
			ScheduledFuture<?> future = _scheduler.schedule(r, delay, TimeUnit.MILLISECONDS);
            if (r instanceof RepeatTask) ((RepeatTask) r).track(future);
            return future;
		} catch (RejectedExecutionException e) {
			return null;
		}
	}

	public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long initialDelay, long period) {
		return _scheduler.scheduleAtFixedRate(r, initialDelay, period, TimeUnit.MILLISECONDS);
	}

	public ScheduledFuture<?> scheduleAtFixedRateLong(Runnable r, long initialDelay, long period) {
		return _scheduler.scheduleAtFixedRate(r, initialDelay, period, TimeUnit.MILLISECONDS);
	}

	public ScheduledFuture<?> pcSchedule(L1PcMonitor r, long delay) {
		try {
			if (delay <= 0) {
                execute(r);
				return null;
			}
			return _pcScheduler.schedule(r, delay, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException e) {
			return null;
		}
	}

	public ScheduledFuture<?> pcScheduleAtFixedRate(L1PcMonitor r, long initialDelay, long period) {
		return _pcScheduler.scheduleAtFixedRate(r, initialDelay, period, TimeUnit.MILLISECONDS);
	}

	// ThreadPoolManager 로부터 배차
	private class PriorityThreadFactory implements ThreadFactory {
		private final int _prio;
		private final String _name;
		private final AtomicInteger _threadNumber = new AtomicInteger(1);
		private final ThreadGroup _group;

		public PriorityThreadFactory(String name, int prio) {
			_prio = prio;
			_name = name;
			_group = new ThreadGroup(_name);
		}
		public Thread newThread(Runnable r) {
			Thread t = new Thread(_group, r);
			t.setName(_name + "-" + _threadNumber.getAndIncrement());
			t.setPriority(_prio);
			return t;
		}

	}
}
