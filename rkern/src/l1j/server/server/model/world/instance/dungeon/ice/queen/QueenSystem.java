package l1j.server.server.model.world.instance.dungeon.ice.queen;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.instance.dungeon.ice.CIceCaveSpawn;

public final class QueenSystem {
    private static final QueenSystem INSTANCE = new QueenSystem();
    private static final Logger LOG = Logger.getLogger(QueenSystem.class.getName());
    private final CInstantZoneManager<Queen> zones = new CInstantZoneManager<>(49);

    private QueenSystem() {}

    public static QueenSystem getInstance() {
        return INSTANCE;
    }

    public void startQueen(L1PcInstance pc) {
        CInstantZone<Queen> zone = null;
        try {
            zone = zones.tryOpen(2151, 2101, 2101, 2150);
            if (zone != null) {
                final Queen queen = new Queen(zone);
                final int mapId = zone.mapId();
                final var session = pc.getNetConnection();
                final int stamp = pc.getSessionStamp();
                zone.bind(queen);
                zone.start(() -> {
                    if (session == null || !pc.isItemTransferSession(session, stamp)
                            || !session.ownsReadyGameSession()) return;
                    pc.start_teleport(32728, 32819, mapId, 5, 169, true, false);
                    if (CIceCaveSpawn.pause(2000L)) queen.play();
                }, queen::finish);
                return;
            }
        } catch (RuntimeException failure) {
            if (zone != null) zone.close(() -> {});
            LOG.log(Level.WARNING, "Ice queen admission failed", failure);
        }
        SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
    }
}
