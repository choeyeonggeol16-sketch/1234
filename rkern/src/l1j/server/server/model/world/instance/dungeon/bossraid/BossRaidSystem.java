package l1j.server.server.model.world.instance.dungeon.bossraid;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.CParty;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public final class BossRaidSystem {
    private static final BossRaidSystem INSTANCE = new BossRaidSystem();
    private static final Logger LOG = Logger.getLogger(BossRaidSystem.class.getName());
    private final CInstantZoneManager<BossRaid> zones = new CInstantZoneManager<>(49);
    private final ConcurrentHashMap<CParty, CInstantZone<BossRaid>> parties = new ConcurrentHashMap<>();

    private BossRaidSystem() {}

    public static BossRaidSystem getInstance() {
        return INSTANCE;
    }

    public void startBossRaid(L1PcInstance pc) {
        final CParty party = pc.getParty();
        if (party == null || !party.isLeader(pc)) return;

        if (parties.containsKey(party)) {
            SC_SystemMessage.send(pc, "현재 보스 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
            return;
        }
        final var ticket = CRaidAdmission.findTicket(pc);
        if (ticket == null) return;

        CInstantZone<BossRaid> reserved = null;
        try {
            final CInstantZone<BossRaid> zone = zones.tryOpen(12358, -1, 12359, 12407);
            reserved = zone;
            if (zone == null) {
                SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
                return;
            }
            if (parties.putIfAbsent(party, zone) != null) {
                zone.close(() -> {});
                SC_SystemMessage.send(pc, "현재 보스 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
                return;
            }
            final BossRaid encounter = new BossRaid(zone);
            zone.bind(encounter);
            final Runnable enter = CRaidAdmission.travel(party, pc, zone.mapId(), 32801, 32797, false, encounter::play);
            CRaidAdmission.launch(zone, pc, ticket, enter, () -> {
                try { encounter.finish(); }
                finally { parties.remove(party, zone); }
            });
        } catch (RuntimeException failure) {
            if (reserved != null) {
                final var zone = reserved;
                zone.close(() -> parties.remove(party, zone));
            }
            LOG.log(Level.WARNING, "Boss raid admission failed", failure);
            SC_SystemMessage.send(pc, "던전 입장을 시작하지 못했습니다. 다시 시도해 주세요.");
        }
    }
}
