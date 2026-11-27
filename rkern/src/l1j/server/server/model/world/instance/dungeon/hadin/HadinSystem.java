package l1j.server.server.model.world.instance.dungeon.hadin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.CParty;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public final class HadinSystem {
    private static final Logger LOG = Logger.getLogger(HadinSystem.class.getName());
    private static final HadinSystem INSTANCE = new HadinSystem();
    private static final int BASE_MAP = 9000;

    private final CInstantZoneManager<Hadin> zones = new CInstantZoneManager<>(99);
    private final ConcurrentHashMap<CParty, CInstantZone<Hadin>> activeParties = new ConcurrentHashMap<>();

    private HadinSystem() {}

    public static HadinSystem getInstance() { return INSTANCE; }

    public void startHadin(L1PcInstance pc) {
        final CParty party = pc == null ? null : pc.getParty();
        if (party == null || !party.isLeader(pc) || activeParties.containsKey(party)) return;
        final CInstantZone<Hadin> zone = zones.tryOpen(BASE_MAP, BASE_MAP, BASE_MAP + 1, 9099);
        if (zone == null) {
            SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
            return;
        }
        if (activeParties.putIfAbsent(party, zone) != null) {
            zone.close(() -> {});
            return;
        }
        final Hadin encounter = new Hadin(zone, party, pc.getMap());
        final Runnable cleanup = () -> {
            try {
                encounter.cleanup();
            } finally {
                activeParties.remove(party, zone);
            }
        };
        try {
            zone.bind(encounter);
            zone.setExit(32572, 32944, 0);
            zone.start(encounter, cleanup);
        } catch (RuntimeException failure) {
            zone.close(cleanup);
            LOG.log(Level.WARNING, "Hadin startup failed for map " + zone.mapId(), failure);
        }
    }

    public Hadin getHadin(int id) { return zones.get(id); }

    public int countHadin() { return zones.size(); }
}
