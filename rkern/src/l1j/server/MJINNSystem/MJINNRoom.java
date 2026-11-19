package l1j.server.MJINNSystem;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ScheduledFuture;

import l1j.server.MJTemplate.ObServer.MJAbstractCopyMapObserver;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class MJINNRoom extends MJAbstractCopyMapObserver implements Runnable{
    private static final Logger LOG = Logger.getLogger(MJINNRoom.class.getName());
    private static final ConcurrentHashMap<Integer, MJINNRoom> _keys = new ConcurrentHashMap<>(256);

    // Each admitted room owns exactly one usage slot until its terminal close.
    static MJINNRoom reserve(MJINNMapInfo mapInfo) {
        int used;
        do {
            used = mapInfo.usageCount.get();
            if (used >= MJINNHelper.USAGECOUNT) return null;
        } while (!mapInfo.usageCount.compareAndSet(used, used + 1));
        MJINNRoom room = new MJINNRoom();
        room._mapInfo = mapInfo;
        try {
            if (MJCopyMapObservable.getInstance().register(mapInfo.mapId, room) == null) {
                room.notifyObservable();
                return null;
            }
            synchronized (room) {
                room.registered = true;
                room._future = ThreadPool.getInstance().scheduleRequired(room, MJINNHelper.RENTAL_MINUTE * 60L * 1000L);
            }
            return room;
        } catch (RuntimeException e) {
            room.notifyObservable();
            LOG.log(Level.WARNING, "Inn room reservation failed", e);
            return null;
        }
    }

    // Called once by the durable receipt callback. Its private array is immutable
    // after submission.
    synchronized void finishRental(int[] keys) {
        if (closed || _keyIds != null || keys.length == 0) return;
        _keyIds = keys;
        _usageCount = keys.length;
        for (int id : keys) _keys.put(id, this);
    }

	public static boolean input(L1ItemInstance item, L1PcInstance pc){
		MJINNRoom room = _keys.get(item.getId());
        if (room != null && room.in(item.getId(), pc)) return true;
		pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, true, false, false);
		return false;
	}
	
	public static boolean input(L1ItemInstance[] items, L1PcInstance pc){
		for(L1ItemInstance item : items){
			MJINNRoom room = _keys.get(item.getId());
			
			if(room == null)
				continue;
			
			if (room.in(item.getId(), pc)) return true;
		}
		return false;
	}
	
	public static boolean input(L1PcInstance pc, int innId){
		L1ItemInstance[] items = pc.getInventory().findItemsId(MJINNHelper.INN_KEYID);
		
		if(items == null || items.length <= 0)
			return false;
		
		for(L1ItemInstance item : items){
			MJINNRoom room = _keys.get(item.getId());
			if(room == null)
				continue;
			
			if(room._mapInfo.innId == innId){
				if (room.in(item.getId(), pc)) return true;
			}
		}
		return false;
		
	}
	
	public static boolean input(L1PcInstance pc){
		L1ItemInstance[] items = pc.getInventory().findItemsId(MJINNHelper.INN_KEYID);
		if(items == null || items.length <= 0)
			return false;
		return input(items, pc);
	}
	
    public static void remove(L1ItemInstance item) {
        if (item == null) return;
        MJINNRoom room = _keys.get(item.getId());
        if (room != null) room.removeKey(item.getId());
    }

    private synchronized void removeKey(int id) {
        if (closed || !_keys.remove(id, this)) return;
        if (--_usageCount == 0) notifyObservable();
    }

    public static void remove(L1ItemInstance[] items) {
        for (L1ItemInstance item : items) remove(item);
    }

    private MJINNMapInfo _mapInfo;
    private int _usageCount;
    private int[] _keyIds;
    private ScheduledFuture<?> _future;
    private boolean registered;
    private boolean closed;

    @Override
    public void run() { notifyObservable(); }

    @Override
    public synchronized void notifyObservable() {
        if (closed) return;
        closed = true;
        if (_future != null) {
            _future.cancel(false);
            _future = null;
        }
        if (_keyIds != null) {
            for (int id : _keyIds) _keys.remove(id, this);
            _keyIds = null;
        }
        _usageCount = 0;
        _mapInfo.usageCount.decrementAndGet();
        if (registered) super.notifyObservable();
    }

	private synchronized boolean in(int keyId, L1PcInstance pc){
        if (closed || _keys.get(keyId) != this) return false;
		if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			return true;
		}
		
		if (pc.hasSkillEffect(L1SkillId.이터디스)) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			return true;
		}
		if (pc.getMapId() >= 1708 && pc.getMapId() <= 1710) {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			SC_SystemMessage.send(pc, "여기서는 사용할 수 없습니다.");
			return true;
		}
		if (_mapInfo.x == 32742){ //해적섬 여관 텔 방지
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			return true;
		}
		pc.start_teleport(_mapInfo.x, _mapInfo.y, _copyMapId, pc.getHeading(), 169, false, false, false);
        return true;
	}
}
