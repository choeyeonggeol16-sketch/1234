package l1j.server.server;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
public abstract class RepeatTask implements Runnable {
    private final long interval;
    private volatile boolean active = true;
    private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();
    protected RepeatTask(long interval) { if (interval < 1) throw new IllegalArgumentException("interval"); this.interval = interval; }
    public long getInterval() { return interval; }
    public boolean isActive() { return active; }
    public abstract void execute();
    public final void track(ScheduledFuture<?> future) {
        if (future == null) return;
        futures.removeIf(ScheduledFuture::isDone);
        if (!active) { future.cancel(false); return; }
        futures.add(future);
        if (!active) { futures.remove(future); future.cancel(false); }
    }
    @Override public final void run() {
        if (!active) return;
        try { execute(); }
        finally { if (active) GeneralThreadPool.getInstance().schedule(this, interval); }
    }
    public void cancel() {
        active = false;
        for (ScheduledFuture<?> future : futures) future.cancel(false);
        futures.clear();
    }
}
