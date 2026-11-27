package l1j.server.server.model.world.instance.dungeon.valakasroom;

import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1PcInstance;

public class ValakasRoomSystem {
    private static final ValakasRoomSystem INSTANCE = new ValakasRoomSystem();
    private static final int FIRST_MAP = 2600;
    private static final int CAPACITY = 49;
    private static final java.util.Random RANDOM = new java.util.Random();
    private final CInstantZoneManager<ValakasStart> rooms = new CInstantZoneManager<>(CAPACITY);

    public static ValakasRoomSystem getInstance() {
        return INSTANCE;
    }

    public ValakasStart reserve(L1PcInstance pc) {
        CInstantZone<ValakasStart> zone = rooms.tryOpen(FIRST_MAP, FIRST_MAP, FIRST_MAP + 1, FIRST_MAP + CAPACITY - 1);
        if (zone == null) return null;
        ValakasStart room = new ValakasStart(zone, pc);
        try {
            zone.setExit(33705, 32504, 4);
            zone.onClose(room::cleanup);
            zone.bind(room);
            int id = zone.mapId();
            ValakasRoomSpawn spawn = ValakasRoomSpawn.getInstance();
            room.BasicNpcList = spawn.fillRequiredSpawnTable(id, 2);
            room.BossList = spawn.fillRequiredSpawnTable(id, 1000 + RANDOM.nextInt(3));
            room.NpcList = spawn.fillRequiredSpawnTable(id, 1);
            return room;
        } catch (RuntimeException failure) {
            room.endRaid();
            throw failure;
        }
    }

    public void enter(ValakasStart room, L1PcInstance pc) {
        room.enter(pc);
    }

    public void cancel(ValakasStart room) {
        if (CInstanceWork.schedule(room::endRaid, 1L) == null) room.endRaid();
    }

    public int countStartRaid() {
        return rooms.size();
    }
}
