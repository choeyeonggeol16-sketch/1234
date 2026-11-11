package l1j.server.server.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Separate bounded-by-admission lanes protect gameplay from login and cleanup storms. */
final class SessionDispatcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SessionDispatcher.class.getName());
    
    private final Lane game;
    private final Lane auth;
    private final Lane cleanup;

    SessionDispatcher(NetworkConfig c) {
        game = new Lane("Game", c.gameThreads);
        auth = new Lane("Auth", c.authThreads);
        cleanup = new Lane("Close", c.closeThreads);
    }

    void schedule(NettySession.Mailbox m) {
        boolean authenticated = m.ownerAuthenticated();
        (authenticated ? game : auth).submit(() -> m.runBatch(authenticated));
    }

    void cleanup(Runnable task) {
        cleanup.submit(task);
    }

    int gameQueued() {
        return game.queued.get();
    }

    int gameRunning() {
        return game.running.get();
    }

    int authQueued() {
        return auth.queued.get();
    }

    int authRunning() {
        return auth.running.get();
    }

    boolean inWorker() {
        return game.ownsThread() || auth.ownsThread() || cleanup.ownsThread();
    }

    @Override
    public void close() {
        game.close();
        auth.close();
        cleanup.close();
    }

    private static final class Lane implements AutoCloseable {

        final ConcurrentLinkedQueue<Runnable> ready = new ConcurrentLinkedQueue<>();
        final Semaphore available = new Semaphore(0);
        final List<Thread> workers = new ArrayList<>();
        final AtomicBoolean stopping = new AtomicBoolean();
        final AtomicInteger queued = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger();

        Lane(String name, int threads) {
            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(this::run, "Network42-" + name + "-" + i);
                workers.add(t);
                t.start();
            }
        }

        void submit(Runnable task) {
            if (stopping.get()) {
                throw new IllegalStateException("Dispatcher stopped with retained sessions");
            }
            ready.add(task);
            queued.incrementAndGet();
            available.release();
        }

        void run() {
            while (!stopping.get() || !ready.isEmpty()) {
                try {
                    if (!available.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                        continue;
                    }
                    
                    Runnable r = ready.poll();
                    if (r != null) {
                        queued.decrementAndGet();
                        running.incrementAndGet();
                        try {
                            r.run();
                        } finally {
                            running.decrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    if (stopping.get()) {
                        break;
                    }
                } catch (Throwable failure) {
                    LOG.log(Level.SEVERE, "Dispatcher callback failed", failure);
                }
            }
        }

        boolean ownsThread() {
            return workers.contains(Thread.currentThread());
        }

        @Override
        public void close() {
            stopping.set(true);
            available.release(workers.size());
            
            for (Thread t : workers) {
                t.interrupt();
            }
            
            for (Thread t : workers) {
                if (t != Thread.currentThread()) {
                    try {
                        t.join(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}