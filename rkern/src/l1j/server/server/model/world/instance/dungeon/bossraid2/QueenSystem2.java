package l1j.server.server.model.world.instance.dungeon.bossraid2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.CParty;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.instance.dungeon.bossraid.CRaidAdmission;

public final class QueenSystem2 {
    private static final QueenSystem2 INSTANCE = new QueenSystem2();
    private static final Logger LOG = Logger.getLogger(QueenSystem2.class.getName());
    private final CInstantZoneManager<Queen1> zones = new CInstantZoneManager<>(49);
    private final ConcurrentHashMap<Integer, CInstantZone<Queen1>> clans = new ConcurrentHashMap<>();

    private QueenSystem2() {}

    public static QueenSystem2 getInstance() {
        return INSTANCE;
    }

    public void startQueen(L1PcInstance pc) {
        final CParty party = pc.getParty();
        final int clanId = pc.getClanid();
        if (party == null || !party.isLeader(pc) || clanId == 0) return;

        if (clans.containsKey(clanId)) {
            SC_SystemMessage.send(pc, "현재 혈맹 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
            return;
        }
        final var ticket = CRaidAdmission.findTicket(pc);
        if (ticket == null) return;

        CInstantZone<Queen1> reserved = null;
        try {
            final CInstantZone<Queen1> zone = zones.tryOpen(2151, 2101, 2101, 2150);
            reserved = zone;
            if (zone == null) {
                SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
                return;
            }
            if (clans.putIfAbsent(clanId, zone) != null) {
                zone.close(() -> {});
                SC_SystemMessage.send(pc, "현재 혈맹 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
                return;
            }
            final Queen1 encounter = new Queen1(zone, clanId);
            zone.bind(encounter);
            final Runnable enter = CRaidAdmission.travel(party, pc, zone.mapId(), 32728, 32811, true, encounter::play);
            CRaidAdmission.launch(zone, pc, ticket, enter, () -> {
                try { encounter.finish(); }
                finally { clans.remove(clanId, zone); }
            });
        } catch (RuntimeException failure) {
            if (reserved != null) {
                final var zone = reserved;
                zone.close(() -> clans.remove(clanId, zone));
            }
            LOG.log(Level.WARNING, "Clan raid admission failed", failure);
            SC_SystemMessage.send(pc, "던전 입장을 시작하지 못했습니다. 다시 시도해 주세요.");
        }
    }
}
