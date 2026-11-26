package l1j.server.server.model.world.instance.dungeon.training;

import l1j.server.server.datatables.CObjectDB;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import l1j.server.server.IdFactory;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.map.L1WorldMap;

public class BossTrainingSystem {
    private static final BossTrainingSystem INSTANCE = new BossTrainingSystem();
    private static final int FIRST_MAP = 1400;
    private static final int CAPACITY = 99;
    private static final long LIFETIME_MS = 7_200_000L;
    private final ConcurrentHashMap<Integer, BossTraining> rooms = new ConcurrentHashMap<>();

    public static BossTrainingSystem getInstance() {
        return INSTANCE;
    }

    public BossTraining reserveRaid() {
        final BossTraining room;
        synchronized (rooms) {
            if (rooms.size() >= CAPACITY) return null;
            int id = FIRST_MAP;
            while (rooms.containsKey(id)) id++;
            room = new BossTraining(id);
            rooms.put(id, room);
        }
        try {
            if (room.mapId != FIRST_MAP) L1WorldMap.getInstance().cloneMap(FIRST_MAP, room.mapId);
            room.keeper = CObjectDB.CreateNPC(7000080);
            room.keeper.setId(IdFactory.getInstance().nextId());
            room.keeper.setX(32902);
            room.keeper.setY(32818);
            room.keeper.setMap((short) room.mapId);
            room.keeper.setHomeX(room.keeper.getX());
            room.keeper.setHomeY(room.keeper.getY());
            room.keeper.setHeading(5);
            room.keeper.setLightSize(0);
            room.keeper.refreshLight();
            CWorld.getInstance().storeAndEnterWorld(room.keeper);
            room.expiry = CInstanceWork.schedule(() -> expire(room), LIFETIME_MS);
            if (room.expiry == null) throw new RejectedExecutionException("Training expiry admission failed");
            return room;
        } catch (Exception failure) {
            room.cancelled = true;
            expire(room);
            throw new IllegalStateException("Training room preparation failed", failure);
        }
    }

    public void cancel(BossTraining room) {
        room.cancelled = true;
        // Keep the slot reserved until the old keeper is out of the world.
        // If this submission fails, the already-admitted expiry cleans it up.
        CInstanceWork.schedule(() -> expire(room), 0L);
    }

    private void expire(BossTraining room) {
        synchronized (room) {
            if (rooms.get(room.mapId) != room) return;
            if (!room.cancelled && !room.isActive()) {
                room.expiry = CInstanceWork.schedule(() -> expire(room), 30_000L);
                return;
            }
            try {
                if (room.cancelled) {
                    if (room.keeper != null) room.keeper.deleteMe();
                    if (room.mapId != FIRST_MAP) L1WorldMap.getInstance().removeMap(room.mapId);
                } else {
                    room.closeEntry();
                    room.run();
                }
            } finally {
                if (room.expiry != null) room.expiry.cancel();
                rooms.remove(room.mapId, room);
            }
        }
    }

    public boolean isActive(L1ItemInstance key) {
        BossTraining room = rooms.get(key.getKeyId());
        return room != null && room.acceptsKey(key.getId());
    }

    public int countRaidPotal() {
        return rooms.size();
    }
}
