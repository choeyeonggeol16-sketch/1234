package l1j.server.server.model.world.instance.dungeon.valakasroom;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;

public class ValakasReadyStart {
    private static final ValakasReadyStart INSTANCE = new ValakasReadyStart();
    private static final int FIRST_MAP = 2699;
    private static final int CAPACITY = 49;
    private final ConcurrentHashMap<Integer, ValakasReady> rooms = new ConcurrentHashMap<>();

    public static ValakasReadyStart getInstance() {
        return INSTANCE;
    }

    public ValakasReady reserve(L1PcInstance pc) {
        final ValakasReady room;
        final int id;
        synchronized (rooms) {
            if (rooms.size() >= CAPACITY) return null;
            int candidate = FIRST_MAP;
            while (rooms.containsKey(candidate)) candidate++;
            id = candidate;
            room = new ValakasReady(id, pc);
            rooms.put(id, room);
        }
        try {
            if (id != FIRST_MAP) L1WorldMap.getInstance().cloneMap(FIRST_MAP, id);
            ValakasRoomSpawn spawn = ValakasRoomSpawn.getInstance();
            room.BasicNpcList = spawn.fillRequiredSpawnTable(id, 0);
            return room;
        } catch (RuntimeException failure) {
            discard(room);
            throw failure;
        }
    }

    public void enter(ValakasReady room, L1PcInstance pc) {
        room.Start();
        pc.isInValakas = true;
        pc.start_teleport(32624, 33059, room.mapId(), 5, 169, true, false);
    }

    public void cancel(ValakasReady room) {
        if (CInstanceWork.schedule(() -> discard(room), 1L) == null)
            discard(room);
    }

    private void discard(ValakasReady room) {
        room.endRaid();
    }

    void remove(ValakasReady room) {
        rooms.remove(room.mapId(), room);
    }

    public int countReadyRaid() {
        return rooms.size();
    }
}
