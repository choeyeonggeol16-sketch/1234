package l1j.server.server.model.world.instance.dungeon.fanstasyisland;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public final class FantasyIslandSystem {
    private static final Logger LOG = Logger.getLogger(FantasyIslandSystem.class.getName());
    private static final FantasyIslandSystem INSTANCE = new FantasyIslandSystem();
    private static final int BASE_MAP = 1936;

    private final CInstantZoneManager<FantasyIsland> zones = new CInstantZoneManager<>(49);
    private final ConcurrentHashMap<Integer, CInstantZone<FantasyIsland>> activePlayers = new ConcurrentHashMap<>();

    private FantasyIslandSystem() {}

    public static FantasyIslandSystem getInstance() { return INSTANCE; }

    public void startRaid(L1PcInstance pc) {
        if (pc == null || activePlayers.containsKey(pc.getId())) return;
        final CInstantZone<FantasyIsland> zone = zones.tryOpen(BASE_MAP, BASE_MAP, BASE_MAP + 1, 2035);
        if (zone == null) {
            SC_SystemMessage.send(pc, "몽환의 섬 던전에 진입한 인원이 너무 많습니다");
            return;
        }
        if (activePlayers.putIfAbsent(pc.getId(), zone) != null) {
            zone.close(() -> {});
            return;
        }
        final FantasyIsland encounter = new FantasyIsland(zone, pc);
        final Runnable cleanup = () -> {
            try {
                encounter.cleanup();
            } finally {
                activePlayers.remove(pc.getId(), zone);
            }
        };
        try {
            zone.bind(encounter);
            zone.setExit(33437, 32813, 4);
            zone.start(encounter, cleanup);
        } catch (RuntimeException failure) {
            zone.close(cleanup);
            LOG.log(Level.WARNING, "Fantasy Island startup failed for map " + zone.mapId(), failure);
        }
    }

    public int countRaid() { return zones.size(); }
}
