package l1j.server.server.model.world.instance;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import java.util.concurrent.Future;
import l1j.server.server.model.map.L1Map;

/** One dungeon generation. Callbacks retain this handle, never just its reusable map number. */
public final class CInstantZone<T> {
    final CInstantZoneManager<T> manager;
    final boolean borrowed;
    private final int id;
    private volatile L1Map map;
    private T encounter;
    private boolean closing;
    private boolean cleaned;
    private boolean retiring;
    private boolean eventPending;
    private Thread eventThread;
    private Future<?> event;
    private int entries;
    private Runnable cleanup;
    private int exitX, exitY, exitMap = -1;
    private long closedAt;
    private Set<Integer> evacuating;

    CInstantZone(CInstantZoneManager<T> manager, int id, boolean borrowed) {
        this.manager = manager;
        this.id = id;
        this.borrowed = borrowed;
    }

    void publish(L1Map map) { this.map = map; }
    public int mapId() { return id; }
    public L1Map map() { return map; }
    public boolean ownsMap() { return map != null && CInstantZoneManager.destination(id) == this; }

    public synchronized void setExit(int x, int y, int mapId) {
        exitX = x;
        exitY = y;
        exitMap = mapId;
    }

    void evacuateRemaining() {
        final L1Map terrain;
        final int x, y, destination;
        synchronized (this) {
            if (map == null || exitMap < 0 || !cleaned
                    || System.nanoTime() - closedAt < TimeUnit.SECONDS.toNanos(2L)) return;
            terrain = map;
            x = exitX;
            y = exitY;
            destination = exitMap;
        }
        for (L1PcInstance pc : CWorld.getInstance().getVisiblePlayers(terrain)) {
            if (pc.get_teleport()) continue;
            final var socket = pc.getNetConnection();
            if (socket == null || !socket.isConnected() || socket.getActiveChar() != pc) continue;
            final int playerId = pc.getId();
            synchronized (this) {
                if (evacuating == null) evacuating = new HashSet<>();
                if (!evacuating.add(playerId)) continue;
            }
            CIOThreadCallback.getInstance().completeSession(socket, () -> {
                if (ownsMap() && pc.getMap() == terrain && !pc.get_teleport())
                    L1Teleport.getInstance().doTeleport(pc, x, y, destination);
            }, failure -> { synchronized (this) { evacuating.remove(playerId); } });
        }
    }

    void closePrepared() {
        final Runnable action;
        synchronized (this) { action = cleanup; }
        if (action != null) close(action);
    }

    public synchronized void bind(T value) {
        if (closing || encounter != null || map == null) throw new IllegalStateException("Dungeon unavailable");
        encounter = Objects.requireNonNull(value);
    }

    synchronized T encounter() { return closing ? null : encounter; }

    /** Pin the generation while an already-admitted operation enters the world. */
    public boolean enter(Runnable action) {
        synchronized (this) {
            if (closing || encounter == null) return false;
            entries++;
        }
        try {
            action.run();
            return true;
        } finally {
            synchronized (this) { entries--; }
            finishClose();
        }
    }

    /** Finish a corpse/ground transaction on its original terrain, including during evacuation. */
    public boolean trySettle(L1Map expected, BooleanSupplier action) {
        synchronized (this) {
            if (expected == null || map != expected || retiring || (closing && cleanup == null && !cleaned))
                return false;
            entries++;
        }
        try {
            return action.getAsBoolean();
        } finally {
            synchronized (this) { entries--; }
            finishClose();
        }
    }

    public synchronized void onClose(Runnable action) {
        if (closing) throw new IllegalStateException("Dungeon closing");
        cleanup = Objects.requireNonNull(action);
    }

    public Future<?> start(Runnable script, Runnable onClose) { return start(script, onClose, 0L); }

    public Future<?> start(Runnable script, Runnable onClose, long delayMs) {
        Objects.requireNonNull(script);
        synchronized (this) {
            if (closing || eventPending || encounter == null) throw new IllegalStateException("Dungeon cannot start");
            cleanup = Objects.requireNonNull(onClose);
            eventPending = true;
        }
        final Future<?> scheduled;
        try {
            scheduled = CInstanceEvents.schedule(() -> {
                synchronized (this) {
                    if (closing) return;
                    eventThread = Thread.currentThread();
                }
                script.run();
            }, delayMs, this::eventRetired);
        } catch (RuntimeException | Error failure) {
            eventRetired();
            throw failure;
        }
        boolean cancel;
        synchronized (this) {
            cancel = closing;
            if (eventPending) event = scheduled;
        }
        if (cancel) scheduled.cancel(true);
        return scheduled;
    }

    private void eventRetired() {
        synchronized (this) {
            eventPending = false;
            eventThread = null;
            event = null;
            closing = true;
        }
        finishClose();
    }

    public void close(Runnable action) {
        Objects.requireNonNull(action);
        final Future<?> cancel;
        synchronized (this) {
            if (closing) return;
            closing = true;
            cleanup = action;
            cancel = eventThread == Thread.currentThread() ? null : event;
        }
        if (cancel != null) cancel.cancel(true);
        finishClose();
    }

    private void finishClose() {
        final Runnable action;
        synchronized (this) {
            if (!closing || entries != 0 || eventPending || cleanup == null) return;
            action = cleanup;
            cleanup = null;
        }
        try {
            action.run();
        } finally {
            synchronized (this) {
                encounter = null;
                closedAt = System.nanoTime();
                cleaned = true;
            }
            manager.cleaned(this);
        }
    }

    void reap() { manager.retire(this); }
    synchronized boolean beginRetirement() {
        if (!cleaned || entries != 0 || retiring || map == null) return false;
        retiring = true;
        return true;
    }
    synchronized void deferRetirement() { retiring = false; }
    synchronized void released() { map = null; }
}
