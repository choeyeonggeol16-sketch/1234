package l1j.server.server.model.world.instance.dungeon.bossraid;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.dungeon.ice.CIceCaveSpawn;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.L1SpawnUtil;

public final class BossRaid {
    private static final Logger LOG = Logger.getLogger(BossRaid.class.getName());
    private static final int LIMIT_SECONDS = 1800;

    private final CInstantZone<BossRaid> zone;
    private final int mapId;
    private final int[] monsters = {
        Config.boss1, Config.boss2, Config.boss3, Config.boss4, Config.boss5,
        Config.boss6, Config.boss7, Config.boss8, Config.boss9, Config.boss10,
        Config.boss10, Config.boss10, Config.boss10, Config.boss9, Config.boss9,
        Config.boss8, Config.boss8, Config.boss7, Config.boss7, Config.boss6
    };

    BossRaid(CInstantZone<BossRaid> zone) {
        this.zone = zone;
        mapId = zone.mapId();
        zone.setExit(33436, 32807, 4);
    }

    void play() {
        if (CInstanceEvents.isStopping() || !CIceCaveSpawn.pause(3000L)) return;
        final int bossId = monsters[GameRandom.nextInt(monsters.length)];
        if (CObjectDB.GetNPC(bossId) == null)
            throw new IllegalStateException("Missing boss raid NPC template " + bossId);
        L1SpawnUtil.spawn2(32801, 32797, (short) mapId, bossId, 0, 0, 0);

        final CWorld world = CWorld.getInstance();
        for (int time = LIMIT_SECONDS - 1; time > 0 && !CInstanceEvents.isStopping();) {
            if (!CIceCaveSpawn.pause(1000L)) return;
            --time;
            if (time % 60 == 0) {
                CSharedBroadcast.sendWrittenMapBestEffort(zone.map(),
                        SC_PacketBox.nativeGreenWriter(time / 60 + "분 후에 마을로 강제 이동 됩니다."));
            }
            if (time <= LIMIT_SECONDS - 10 && world.getVisiblePlayers(zone.map()).isEmpty())
                return;
        }
    }

    void finish() {
        final CWorld world = CWorld.getInstance();
        final L1Map map = zone.map();
        for (L1Object object : world.snapshotZoneOccupants(map)) {
            if (object.getMap() != map) continue;
            try {
                if (object instanceof L1PcInstance pc) {
                    pc.start_teleport(33436, 32807, 4, 5, 169, false, false);
                } else if (object instanceof L1NpcInstance npc && !CInstantZoneManager.isPlayerCompanion(npc)) {
                    npc.deleteMe();
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Boss raid cleanup failed for map " + mapId, failure);
            }
        }
        MapCleaner.removeGroundItems(map);
    }
}
