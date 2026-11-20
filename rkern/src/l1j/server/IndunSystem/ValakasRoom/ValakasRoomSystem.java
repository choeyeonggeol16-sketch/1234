package l1j.server.IndunSystem.ValakasRoom;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;

public class ValakasRoomSystem {
    private static final ValakasRoomSystem INSTANCE = new ValakasRoomSystem();
    private static final int FIRST_MAP = 2600;
    private static final int CAPACITY = 49;
    private static final java.util.Random RANDOM = new java.util.Random();
    private final ConcurrentHashMap<Integer, ValakasStart> rooms = new ConcurrentHashMap<>();

    public static ValakasRoomSystem getInstance() {
        return INSTANCE;
    }

    public ValakasStart reserve(L1PcInstance pc) {
        final ValakasStart room;
        final int id;
        synchronized (rooms) {
            if (rooms.size() >= CAPACITY) return null;
            int candidate = FIRST_MAP;
            while (rooms.containsKey(candidate)) candidate++;
            id = candidate;
            room = new ValakasStart(id, pc);
            rooms.put(id, room);
        }
        try {
            if (id != FIRST_MAP) L1WorldMap.getInstance().cloneMap(FIRST_MAP, id);
            ValakasRoomSpawn spawn = ValakasRoomSpawn.getInstance();
            room.BasicNpcList = spawn.fillRequiredSpawnTable(id, 2);
            room.BossList = spawn.fillRequiredSpawnTable(id, 1000 + RANDOM.nextInt(3));
            room.NpcList = spawn.fillRequiredSpawnTable(id, 1);
            return room;
        } catch (RuntimeException failure) {
            discard(room);
            throw failure;
        }
    }

    public void enter(ValakasStart room, L1PcInstance pc) {
        room.Start();
        pc.isInValakas = true;
        pc.start_teleport(32624, 33059, room.mapId(), 5, 169, true, false);
    }

    public void cancel(ValakasStart room) {
        if (ThreadPool.getInstance().schedule(() -> discard(room), 1L) == null)
            discard(room);
    }

    private void discard(ValakasStart room) {
        room.endRaid();
    }

    void remove(ValakasStart room) {
        rooms.remove(room.mapId(), room);
    }

    public int countStartRaid() {
        return rooms.size();
    }
}
