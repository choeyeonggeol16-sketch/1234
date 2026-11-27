package l1j.server.server.model.network.thread;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.network.core.execution.CIOShardContext;

/** A fixed group that executes exclusive, reusable CIO shard turns. */
final class CIOSharedPacketWorkers {
    private final ArrayDeque<CIOThreadCallback.CIOThread> ready;
    private final int capacity;
    private final Thread[] workers;
    private boolean stopping;

    CIOSharedPacketWorkers(int count, int capacity) {
        this.capacity = capacity;
        ready = new ArrayDeque<>(capacity);
        workers = new Thread[count];
        for (int i = 0; i < count; i++) {
            workers[i] = Thread.ofPlatform().name("CIO-PacketWorker-" + (i + 1))
                    .daemon(false).unstarted(this::run);
        }
        try {
            for (Thread worker : workers) worker.start();
        } catch (RuntimeException | Error failure) {
            requestStop();
            throw failure;
        }
    }

    synchronized boolean offer(CIOThreadCallback.CIOThread shard) {
        if (stopping || ready.size() == capacity) return false;
        ready.addLast(shard);
        notify();
        return true;
    }

    synchronized boolean hasQueuedTurns() {
        return !ready.isEmpty();
    }

    synchronized void requestStop() {
        stopping = true;
        notifyAll();
    }

    int size() {
        return workers.length;
    }

    boolean isCurrentWorker() {
        final Thread current = Thread.currentThread();
        for (Thread worker : workers) {
            if (worker == current) return true;
        }
        return false;
    }

    boolean awaitTermination(long started, long budget) throws InterruptedException {
        for (Thread worker : workers) {
            while (worker.isAlive()) {
                long remaining = budget - (System.nanoTime() - started);
                if (remaining <= 0L) return false;
                worker.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }
        }
        return true;
    }

    private synchronized CIOThreadCallback.CIOThread take() {
        while (ready.isEmpty() && !stopping) {
            try {
                wait();
            } catch (InterruptedException ignored) {
                // Only requestStop closes admission. One handler's interrupt
                // does not cancel accepted turns for other logical shards.
            }
        }
        return ready.pollFirst();
    }

    private void run() {
        try {
            CIOThreadCallback.CIOThread shard;
            while ((shard = take()) != null) {
                Thread.interrupted();
                shard.runSharedTurn();
            }
        } finally {
            CIOShardContext.exitShard();
        }
    }
}
