package l1j.server.server.model.world.instance.dungeon.clandungeon;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public final class AzmodanSystem {
    private static final Logger LOG = Logger.getLogger(AzmodanSystem.class.getName());
    private static final AzmodanSystem INSTANCE = new AzmodanSystem();
    private static final int BASE_MAP = 12014;

    private final CInstantZoneManager<Azmodan> zones = new CInstantZoneManager<>(100);
    private final ConcurrentHashMap<Integer, Integer> dailyClanMaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CInstantZone<Azmodan>> activeClans = new ConcurrentHashMap<>();

    private AzmodanSystem() {}

    public static AzmodanSystem getInstance() { return INSTANCE; }

    public void startAzmodan(int clanId, L1PcInstance pc) {
        if (pc == null || clanId <= 0 || pc.getClanid() != clanId || activeClans.containsKey(clanId)) return;
        final CInstantZone<Azmodan> zone = zones.tryOpen(BASE_MAP, BASE_MAP, BASE_MAP + 1, 12114);
        if (zone == null) {
            SC_SystemMessage.send(pc, "현재 오픈중인 던전이 너무 많습니다. 잠시후에 이용해주세요");
            return;
        }
        if (activeClans.putIfAbsent(clanId, zone) != null) {
            zone.close(() -> {});
            return;
        }
        final Azmodan encounter = new Azmodan(zone, clanId);
        final Runnable cleanup = () -> {
            try {
                encounter.cleanup();
            } finally {
                if (!encounter.began()) dailyClanMaps.remove(clanId, zone.mapId());
                activeClans.remove(clanId, zone);
            }
        };
        try {
            zone.bind(encounter);
            zone.setExit(33426, 32811, 4);
            dailyClanMaps.put(clanId, zone.mapId());
            zone.start(encounter, cleanup, 2_000L);
        } catch (RuntimeException failure) {
            zone.close(cleanup);
            LOG.log(Level.WARNING, "Clan dungeon startup failed for clan " + clanId, failure);
        }
    }

    public Azmodan getAzmodan(int id) { return zones.get(id); }

    public int getAzmodanClanid(int clanId) { return dailyClanMaps.getOrDefault(clanId, 0); }

    public int getClanid(int id) {
        final Azmodan encounter = getAzmodan(id);
        return encounter == null ? 0 : encounter.clanId();
    }

    public void removeClan(int clanId) { dailyClanMaps.remove(clanId); }

    public int countAzmodan() { return zones.size(); }
}
