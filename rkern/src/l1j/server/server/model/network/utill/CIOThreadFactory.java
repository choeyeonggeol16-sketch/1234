package l1j.server.server.model.network.utill;

import java.nio.channels.ShutdownChannelGroupException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CIOThreadFactory implements ThreadFactory {

	private static final String DEFAULT_BASE_NAME = "Thread";
	private static final int INITIAL_SEQUENCE_VALUE = 1;
	private static final int STACK_SIZE = 0; 
	
	private static final AtomicInteger POOL_SEQUENCE = new AtomicInteger(INITIAL_SEQUENCE_VALUE);
	
	private final AtomicInteger _threadSequence = new AtomicInteger(INITIAL_SEQUENCE_VALUE);
	private final String _threadPrefix;
	private final int _threadPriority;
	
	public CIOThreadFactory(String baseName, int priority) {
		final String safeBaseName = ((baseName == null) || baseName.isEmpty()) ? DEFAULT_BASE_NAME : baseName;
		_threadPrefix = safeBaseName + "-network-pool-" + POOL_SEQUENCE.getAndIncrement() + "-thread-";
		
		if (priority < Thread.MIN_PRIORITY) {
			_threadPriority = Thread.MIN_PRIORITY;
		} else if (priority > Thread.MAX_PRIORITY) {
			_threadPriority = Thread.MAX_PRIORITY;
		} else {
			_threadPriority = priority;
		}
	}

	@Override
	public Thread newThread(Runnable task) {
		final int threadIndex = nextIndex();
		final Thread thread = new Thread(null, task, _threadPrefix + threadIndex, STACK_SIZE);

		final ThreadGroup threadGroup = thread.getThreadGroup();
		final int groupMaxPriority = (threadGroup != null) ? threadGroup.getMaxPriority() : Thread.MAX_PRIORITY;
		final int effectivePriority = (_threadPriority > groupMaxPriority) ? groupMaxPriority : _threadPriority;

		thread.setPriority(effectivePriority);
		thread.setDaemon(false);
		thread.setUncaughtExceptionHandler((t, e) -> {
		    // 채널 그룹 종료 경합 전용 필터 — 이 두 개 외엔 절대 삼키지 않음
		    if (e instanceof ShutdownChannelGroupException) {
		        return;
		    }
		    Logger.getLogger(CIOThreadFactory.class.getName())
		            .log(Level.SEVERE, "스레드 미처리 예외: " + t.getName(), e);
		});

		return thread;
	}
	
	private int nextIndex() {
		final int currentValue = _threadSequence.getAndIncrement();
		return (currentValue == Integer.MIN_VALUE) ? _threadSequence.updateAndGet(x -> (x <= 0) ? INITIAL_SEQUENCE_VALUE : x) : currentValue; // Handle integer overflow by resetting to 1.
	}
}
