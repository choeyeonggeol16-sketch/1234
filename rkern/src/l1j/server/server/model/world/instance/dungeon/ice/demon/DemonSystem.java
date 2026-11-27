package l1j.server.server.model.world.instance.dungeon.ice.demon;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.instance.dungeon.ice.CIceCaveSpawn;

public final class DemonSystem {
    private static final DemonSystem INSTANCE = new DemonSystem();
    private static final Logger LOG = Logger.getLogger(DemonSystem.class.getName());
    private final CInstantZoneManager<Demon> zones = new CInstantZoneManager<>(49);

    private DemonSystem() {}

    public static DemonSystem getInstance() {
        return INSTANCE;
    }

    public void startDemon(L1PcInstance pc) {
        CInstantZone<Demon> zone = null;
        try {
            zone = zones.tryOpen(2151, -1, 2152, 2200);
            if (zone != null) {
                final Demon demon = new Demon(zone);
                final int mapId = zone.mapId();
                final var session = pc.getNetConnection();
                final int stamp = pc.getSessionStamp();
                zone.bind(demon);
                zone.start(() -> {
                    if (session == null || !pc.isItemTransferSession(session, stamp)
                            || !session.ownsReadyGameSession()) return;
                    pc.start_teleport(32728, 32819, mapId, 5, 169, true, false);
                    if (CIceCaveSpawn.pause(2000L)) demon.play();
                }, demon::finish);
                return;
            }
        } catch (RuntimeException failure) {
            if (zone != null) zone.close(() -> {});
            LOG.log(Level.WARNING, "Ice demon admission failed", failure);
        }
        SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
    }
}
