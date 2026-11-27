package l1j.server.server.model.world.trap;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TrapInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.types.Point;

public class L1WorldTraps {
    private static final Logger LOG = Logger.getLogger(L1WorldTraps.class.getName());
    private static volatile L1WorldTraps instance;

    // Null values are armed traps or bases. Non-null values own a cooldown.
    private final IdentityHashMap<L1TrapInstance, Respawn> traps = new IdentityHashMap<>();
    private final IdentityHashMap<L1Map, Integer> trapCounts = new IdentityHashMap<>();
    private volatile Set<L1Map> trapMaps = Set.of();
    private volatile ArrayDeque<Respawn> rejected;
    private volatile boolean stopped;

    public static L1WorldTraps getInstance() {
        L1WorldTraps current = instance;
        if (current == null) {
            synchronized (L1WorldTraps.class) {
                current = instance;
                if (current == null) {
                    current = new L1WorldTraps();
                    List<L1TrapInstance> loaded = load();
                    if (loaded != null) loaded.forEach(current::addTrap);
                    instance = current;
                }
            }
        }
        return current;
    }

    private static List<L1TrapInstance> load() {
        final List<L1TrapInstance> loaded = new ArrayList<>();
        try (var connection = DatabaseFactory.getInstance().getConnection();
                var statement = connection.prepareStatement("SELECT * FROM spawnlist_trap");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                int templateId = rows.getInt("trapId");
                L1Trap template = TrapTable.getInstance().getTemplate(templateId);
                if (template == null) {
                    LOG.warning("Unknown trap template in spawnlist_trap: " + templateId);
                    continue;
                }
                L1Location location = new L1Location(rows.getInt("locX"), rows.getInt("locY"), rows.getInt("mapId"));
                Point offset = new Point(rows.getInt("locRndX"), rows.getInt("locRndY"));
                int count = rows.getInt("count");
                int span = rows.getInt("span");
                for (int i = 0; i < count; i++)
                    loaded.add(new L1TrapInstance(IdFactory.getInstance().nextId(), template, location, offset, span));
                loaded.add(new L1TrapInstance(IdFactory.getInstance().nextId(), location));
            }
            return loaded;
        } catch (SQLException error) {
            LOG.log(Level.WARNING, "Could not load world traps", error);
            return null;
        }
    }

    public static synchronized void reloadTraps() {
        TrapTable.reload();
        List<L1TrapInstance> loaded = load();
        if (loaded == null) return;
        L1WorldTraps old = instance;
        if (old != null) old.stop();
        L1WorldTraps replacement = new L1WorldTraps();
        loaded.forEach(replacement::addTrap);
        instance = replacement;
    }

    public static synchronized void retireTerrain(L1Map terrain) {
        L1WorldTraps current = instance;
        if (current == null) return;
        final List<L1TrapInstance> retired = new ArrayList<>();
        synchronized (current) {
            for (L1TrapInstance trap : current.traps.keySet()) {
                if (trap.getMap() == terrain) retired.add(trap);
            }
        }
        retired.forEach(current::removeTrap);
    }

    private void stop() {
        final List<L1TrapInstance> retired;
        synchronized (this) {
            stopped = true;
            retired = new ArrayList<>(traps.keySet());
        }
        retired.forEach(this::removeTrap);
    }

    public void addTrap(L1TrapInstance trap) {
        synchronized (trap) {
            synchronized (this) {
                if (stopped || traps.containsKey(trap) || !currentTerrain(trap.getMap())) return;
                traps.put(trap, null);
                if (trap.isTriggerable() && trapCounts.merge(trap.getMap(), 1, Integer::sum) == 1)
                    trapMaps = Set.copyOf(trapCounts.keySet());
                trap.enableTrap();
            }
            CWorld.getInstance().addVisibleObject(trap);
        }
    }

    public void removeTrap(L1TrapInstance trap) {
        synchronized (trap) {
            synchronized (this) {
                if (!traps.containsKey(trap)) return;
                Respawn cooldown = traps.remove(trap);
                if (trap.isTriggerable()
                        && trapCounts.computeIfPresent(trap.getMap(), (map, count) -> count == 1 ? null : count - 1) == null)
                    trapMaps = Set.copyOf(trapCounts.keySet());
                if (cooldown != null) cooldown.cancel();
                trap.disableTrap();
            }
            CWorld.getInstance().removeVisibleObject(trap);
        }
        trap.clearViewers();
    }

    public void resetAllTraps() {
        final List<L1TrapInstance> snapshot;
        synchronized (this) {
            if (stopped) return;
            snapshot = new ArrayList<>(traps.keySet());
        }
        for (L1TrapInstance trap : snapshot) {
            if (!trap.isTriggerable()) continue;
            final Respawn reset;
            synchronized (this) {
                if (stopped || !traps.containsKey(trap)) continue;
                Respawn previous = traps.get(trap);
                if (previous != null && previous.applying) {
                    previous.reset = true;
                    continue;
                }
                if (previous != null) previous.cancel();
                reset = new Respawn(trap);
                reset.applying = true;
                reset.reset = true;
                traps.put(trap, reset);
                trap.disableTrap();
            }
            try {
                trap.clearViewers();
            } finally {
                reset.finishEffect();
            }
        }
    }

    public void onPlayerMoved(L1PcInstance player) {
        if (stopped) return;
        L1Map map = player.getMap();
        if (!trapMaps.contains(map)) return;
        int x = player.getX(), y = player.getY();
        L1TrapInstance trap;
        while ((trap = CWorld.getInstance().getClusterGrid().findArmedTrapAt(map, x, y)) != null) {
            if (trigger(player, trap, map, x, y, false) || stopped
                    || trap.isEnable() || player.getMap() != map
                    || player.getX() != x || player.getY() != y) return;
        }
    }

    public void onDetection(L1PcInstance caster) {
        if (stopped) return;
        L1Map map = caster.getMap();
        if (!trapMaps.contains(map)) return;
        int x = caster.getX(), y = caster.getY();
        for (L1Object candidate : CWorld.getInstance().getVisibleObjects(caster)) {
            if (candidate instanceof L1TrapInstance trap && trap.isDetectionable())
                trigger(caster, trap, map, x, y, true);
        }
    }

    private boolean trigger(L1PcInstance player, L1TrapInstance trap, L1Map map, int x, int y, boolean detection) {
        final Respawn cooldown;
        synchronized (this) {
            if (stopped || !trap.isEnable() || !traps.containsKey(trap) || traps.get(trap) != null
                    || player.getMap() != map || player.getX() != x || player.getY() != y
                    || !trap.isPublishedIn(map) || !currentTerrain(map)
                    || (detection ? !player.getLocation().isInScreen(trap.getLocation())
                            : trap.getX() != x || trap.getY() != y)) return false;
            cooldown = new Respawn(trap);
            cooldown.applying = true;
            traps.put(trap, cooldown);
            trap.disableTrap();
        }
        try {
            if (detection) trap.onDetection(player);
            else trap.onTrod(player);
        } finally {
            try {
                trap.clearViewers();
            } finally {
                cooldown.finishEffect();
            }
        }
        return true;
    }

    /** Existing world maintenance retries a bounded number of refused timer admissions. */
    public static void retryRespawns() {
        L1WorldTraps current = instance;
        if (current == null || current.rejected == null) return;
        synchronized (current) {
            var queue = current.rejected;
            if (current.stopped || queue == null) return;
            for (int count = 0; count < 32 && !queue.isEmpty(); count++) {
                if (!queue.peekFirst().schedule()) break;
                queue.removeFirst();
            }
            if (queue.isEmpty()) current.rejected = null;
        }
    }

    private static boolean currentTerrain(L1Map map) {
        return !map.isNull() && L1WorldMap.getInstance().findMap(map.getId()) == map;
    }

    private final class Respawn implements Runnable {
        private final L1TrapInstance trap;
        private CServerTimer timer;
        private long deadline;
        private boolean applying;
        private boolean reset;

        private Respawn(L1TrapInstance trap) {
            this.trap = trap;
        }

        private void finishEffect() {
            synchronized (L1WorldTraps.this) {
                if (stopped || traps.get(trap) != this) return;
                applying = false;
                if (!reset) {
                    deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, trap.getSpan()));
                    if (!schedule()) {
                        if (rejected == null) {
                            rejected = new ArrayDeque<>();
                            LOG.warning("Trap timer admission refused. World maintenance will retry.");
                        }
                        rejected.addLast(this);
                    }
                    return;
                }
            }
            run();
        }

        private boolean schedule() {
            long delay = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            timer = CWorldWork.schedule(this, Math.max(1L, delay));
            return timer != null;
        }

        private void cancel() {
            if (timer != null) timer.cancel();
            else if (rejected != null && rejected.remove(this) && rejected.isEmpty()) rejected = null;
        }

        @Override
        public void run() {
            synchronized (trap) {
                synchronized (L1WorldTraps.this) {
                    if (stopped || traps.get(trap) != this) return;
                    if (currentTerrain(trap.getMap()) && trap.resetPublishedLocation()) {
                        traps.put(trap, null);
                        trap.enableTrap();
                    }
                }
            }
        }
    }
}
