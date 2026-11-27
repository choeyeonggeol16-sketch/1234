package l1j.server.server.model.world.instance.dungeon.training;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import java.util.concurrent.RejectedExecutionException;

import l1j.server.server.IdFactory;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class BossTrainingSystem {
    private static final BossTrainingSystem INSTANCE = new BossTrainingSystem();
    private static final int FIRST_MAP = 1400;
    private static final int CAPACITY = 99;
    private static final long LIFETIME_MS = 7_200_000L;
    private final CInstantZoneManager<BossTraining> rooms = new CInstantZoneManager<>(CAPACITY);

    public static BossTrainingSystem getInstance() {
        return INSTANCE;
    }

    public BossTraining reserveRaid() {
        CInstantZone<BossTraining> zone = rooms.tryOpen(FIRST_MAP, FIRST_MAP, FIRST_MAP + 1, FIRST_MAP + CAPACITY - 1);
        if (zone == null) return null;
        BossTraining room = new BossTraining(zone);
        try {
            zone.setExit(34065, 32313, 4);
            zone.onClose(room::cleanup);
            zone.bind(room);
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
        // A rejected cleanup submission can close the same generation inline.
        if (CInstanceWork.schedule(() -> expire(room), 0L) == null) expire(room);
    }

    void expire(BossTraining room) {
        synchronized (room) {
            if (room.closing) return;
            if (!room.cancelled && !room.isActive()) {
                // An unresolved key receipt still owns the reservation. A rejected
                // retry is completed by its eventual activation or terminal cancel.
                room.expiry = CInstanceWork.schedule(() -> expire(room), 30_000L);
                room.expiredPending = room.expiry == null;
                return;
            }
            room.closing = true;
        }
        room.zone.close(room::cleanup);
    }

    public boolean enter(L1PcInstance pc, L1ItemInstance key) {
        if (pc == null || key == null) return false;
        BossTraining room = rooms.get(key.getKeyId());
        if (room == null) return false;
        synchronized (room) {
            if (!room.acceptsKey(key.getId())) return false;
            return room.zone.enter(() -> pc.start_teleport(32901, 32814, room.mapId(), pc.getHeading(), 169, true, false));
        }
    }

    public boolean isActive(L1ItemInstance key) {
        if (key == null) return false;
        BossTraining room = rooms.get(key.getKeyId());
        return room != null && room.acceptsKey(key.getId());
    }

    public int countRaidPotal() {
        return rooms.size();
    }
}
