package l1j.server.server.model.world.instance.dungeon.valakasroom;

import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1PcInstance;

public class ValakasReadyStart {
    private static final ValakasReadyStart INSTANCE = new ValakasReadyStart();
    private static final int FIRST_MAP = 2699;
    private static final int CAPACITY = 49;
    private final CInstantZoneManager<ValakasReady> rooms = new CInstantZoneManager<>(CAPACITY);

    public static ValakasReadyStart getInstance() {
        return INSTANCE;
    }

    public ValakasReady reserve(L1PcInstance pc) {
        CInstantZone<ValakasReady> zone = rooms.tryOpen(FIRST_MAP, FIRST_MAP, FIRST_MAP + 1, FIRST_MAP + CAPACITY - 1);
        if (zone == null) return null;
        ValakasReady room = new ValakasReady(zone, pc);
        try {
            zone.setExit(33705, 32504, 4);
            zone.onClose(room::cleanup);
            zone.bind(room);
            int id = zone.mapId();
            ValakasRoomSpawn spawn = ValakasRoomSpawn.getInstance();
            room.BasicNpcList = spawn.fillRequiredSpawnTable(id, 0);
            return room;
        } catch (RuntimeException failure) {
            room.endRaid();
            throw failure;
        }
    }

    public void enter(ValakasReady room, L1PcInstance pc) {
        room.enter(pc);
    }

    public void cancel(ValakasReady room) {
        if (CInstanceWork.schedule(room::endRaid, 1L) == null) room.endRaid();
    }

    public int countReadyRaid() {
        return rooms.size();
    }
}
