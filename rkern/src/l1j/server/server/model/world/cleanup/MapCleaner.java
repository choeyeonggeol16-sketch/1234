package l1j.server.server.model.world.cleanup;

import java.util.Collection;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.treasure.L1TreasureChest;
import l1j.server.server.model.world.instance.shared.L1LiftGateInstance;
import l1j.server.server.shared.L1Type;

public class MapCleaner implements Runnable{
	public static void cleanup(short mapId, MapCleanerFilter filter){
		ThreadPool.getInstance().execute(new MapCleaner(mapId, filter));
	}
	
	private short 				_mapId;
	private MapCleanerFilter 	_filter;
	private MapCleaner(short mapId, MapCleanerFilter filter){
		_mapId = mapId;
		_filter = filter;
	}
	
	@Override
	public void run() {
		try{
			if(_filter == null)
				clearNofilter();
			else
				clear();
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			_filter = null;
		}
	}
	
	private void clearNofilter(){
		Collection<L1Object> col = L1World.getInstance().getVisibleObjects(_mapId).values();
		if(col == null || col.size() <= 0)
			return;
		
		for(L1Object obj : col){
			if(obj == null)
				continue;
			
			if(obj.instanceOf(L1Type.L1TYPE_NPC)){
				((L1NpcInstance) obj).deleteMe();
			}else if(obj.instanceOf(L1Type.L1TYPE_INVENTORY)){
				((CInventory) obj).clearItems();
			}
		}
	}
	
	private void clear(){
		Collection<L1Object> col = L1World.getInstance().getVisibleObjects(_mapId).values();
		if(col == null || col.size() <= 0)
			return;
		
		for(L1Object obj : col){
			if(obj == null)
				continue;
			
			if(_filter.isFilter(obj))
				continue;
			
			if(obj.instanceOf(L1Type.L1TYPE_NPC)){
				((L1NpcInstance) obj).deleteMe();
			}else if(obj.instanceOf(L1Type.L1TYPE_INVENTORY)){
				((CInventory) obj).clearItems();
			}else if(obj.instanceOf(L1Type.L1TYPE_LIFT)){
				((L1LiftGateInstance)obj).dispose();
			}else if(obj.instanceOf(L1Type.L1TYPE_TREASURE_CHEST)){
				((L1TreasureChest)obj).dispose();
			}
		}
	}
}
