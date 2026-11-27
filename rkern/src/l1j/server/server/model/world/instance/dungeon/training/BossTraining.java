package l1j.server.server.model.world.instance.dungeon.training;

import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.thread.CServerTimer;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class BossTraining implements Runnable {
    private static final Logger LOG = Logger.getLogger(BossTraining.class.getName());
	final CInstantZone<BossTraining> zone;
	final int mapId;
	volatile boolean cancelled;
	volatile boolean closing;
	boolean expiredPending;
	L1NpcInstance keeper;
	CServerTimer expiry;
	private volatile int[] activeKeys;

	BossTraining(CInstantZone<BossTraining> zone) {
		this.zone = zone;
		this.mapId = zone.mapId();
	}

	public int mapId() {
		return mapId;
	}

	public void activate(int[] keyIds) {
        boolean expire;
        synchronized (this) {
            if (cancelled || closing) return;
            activeKeys = keyIds;
            expire = expiredPending;
        }
        if (expire) BossTrainingSystem.getInstance().expire(this);
    }

	public boolean isActive() {
		return activeKeys != null;
	}

	boolean acceptsKey(int id) {
		int[] keys = activeKeys;
		if (keys == null || cancelled || closing) return false;
		for (int key : keys) if (key == id) return true;
		return false;
	}

	void cleanup() {
        synchronized (this) {
            closing = true;
            activeKeys = null;
            if (expiry != null) expiry.cancel();
        }
        if (cancelled) {
            if (keeper != null) keeper.deleteMe();
        } else {
            run();
        }
    }

    @Override
    public void run() {
        CWorld world = CWorld.getInstance();
        for (L1Object obj : world.snapshotZoneOccupants(zone.map())) {
            if (obj.getMap() != zone.map() || CInstantZoneManager.isPlayerCompanion(obj)) continue;
            try {
                if (obj instanceof L1NpcInstance npc) {
                    npc.deleteMe();
                } else if (obj instanceof L1PcInstance pc) {
                    CIOThreadCallback.getInstance().completeSession(pc.getNetConnection(), () -> {
                        if (zone.ownsMap() && pc.getMap() == zone.map() && !pc.get_teleport())
                            pc.start_teleport(34065, 32313, 4, 0, 169, true, false);
                    });
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Training room cleanup failed for object " + obj.getId(), failure);
                if (obj instanceof L1NpcInstance npc && npc.isDestroyed() && world.findObject(npc.getId()) == npc)
                    world.leaveAndRemoveObject(npc);
            }
        }
        MapCleaner.removeGroundItems(zone.map());
    }
}
