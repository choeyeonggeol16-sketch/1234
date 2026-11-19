package l1j.server.server.monitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoggerInstance extends FileLogger {
    private static volatile LoggerInstance _instance = null;
    
    private final ExecutorService _logExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean _flushQueued = new AtomicBoolean();

    private LoggerInstance() {
        // 기존 종료 코드(Shutdown.java 등)에서 flush()를 직접 호출해주므로
        // 생성자 내 ShutdownHook은 생략 가능합니다.
    }

    public static LoggerInstance getInstance() {
        if (_instance == null) {
            synchronized (LoggerInstance.class) {
                if (_instance == null) {
                    _instance = new LoggerInstance();
                }
            }
        }
        return _instance;
    }

    /**
     * 1,000줄 적재 시 비동기로 파일 쓰기 수행
     */
    public void triggerFlush() {
        if (!_flushQueued.compareAndSet(false, true)) return;
        try {
            _logExecutor.execute(() -> {
                try {
                    flushImmediately();
                } finally {
                    _flushQueued.set(false);
                    if (pendingLogs() >= 1000 && lastFlushSucceeded()) triggerFlush();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            _flushQueued.set(false);
        }
    }
    
    /**
     * 서버 종료 시 수동 호출용 (버퍼 비우기)
     */
    public void flush() {
        flushImmediately();
    }
}