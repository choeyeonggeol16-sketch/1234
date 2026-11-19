package l1j.server.server.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 캐릭터 부속 로드 병렬 실행기.
 */
public final class ParallelLoader {

    private static final Logger _log = Logger.getLogger(ParallelLoader.class.getName());

    /** 부속 로드 전용 풀 — 가상 스레드: 블로킹(DB 대기)해도 비용 제로 */
    private static final ExecutorService POOL =
            Executors.newVirtualThreadPerTaskExecutor();

    private static final long TIMEOUT_SECONDS = 5L;

    private static final class Task {
        final String name;
        final Runnable runnable;
        Task(String name, Runnable runnable) { this.name = name; this.runnable = runnable; }
    }

    private final List<Task> tasks = new ArrayList<>();
    private final L1PcInstance pc;

    public ParallelLoader(L1PcInstance pc) {
        this.pc = pc;
    }

    /** 부속 로드 작업 등록 */
    public void submit(String name, Runnable task) {
        tasks.add(new Task(name, task));
    }

    /** 전부 병렬 실행 후 완료 대기. 실패/타임아웃해도 로그만 남기고 계속 진행 */
    public void await() {
        if (tasks.isEmpty()) return;

        final List<Future<?>> futures = new ArrayList<>(tasks.size());
        for (final Task t : tasks) {
            futures.add(POOL.submit(() -> {
                try {
                    t.runnable.run();
                } catch (Throwable e) {
                    _log.log(Level.WARNING, "[ParallelLoad] " + t.name + " 실패 PC: "
                            + (pc != null ? pc.getName() : pcId()), e);
                }
            }));
        }

        for (final Future<?> f : futures) {
            try {
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                _log.warning("[ParallelLoad] 작업 타임아웃/취소 — 계속 진행: " + pcId());
            }
        }
        tasks.clear();
    }

    private String pcId() {
        return pc != null ? String.valueOf(pc.getId()) : "unknown";
    }
}