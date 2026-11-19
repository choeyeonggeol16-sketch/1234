package l1j.server.MJTemplate.MapCleaner;

import java.util.Collection;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.L1Instance.MJL1LiftGateInstance;
import l1j.server.MJTemplate.TreasureChest.MJL1TreasureChest;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;

public class MJMapCleaner implements Runnable{
	public static void cleanup(short mapId, MJMapCleanerFilter filter){
		ThreadPool.getInstance().execute(new MJMapCleaner(mapId, filter));
	}
	
	private short 				_mapId;
	private MJMapCleanerFilter 	_filter;
	private MJMapCleaner(short mapId, MJMapCleanerFilter filter){
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
			
			if(obj.instanceOf(MJL1Type.L1TYPE_NPC)){
				((L1NpcInstance) obj).deleteMe();
			}else if(obj.instanceOf(MJL1Type.L1TYPE_INVENTORY)){
				((L1Inventory) obj).clearItems();
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
			
			if(obj.instanceOf(MJL1Type.L1TYPE_NPC)){
				((L1NpcInstance) obj).deleteMe();
			}else if(obj.instanceOf(MJL1Type.L1TYPE_INVENTORY)){
				((L1Inventory) obj).clearItems();
			}else if(obj.instanceOf(MJL1Type.L1TYPE_LIFT)){
				((MJL1LiftGateInstance)obj).dispose();
			}else if(obj.instanceOf(MJL1Type.L1TYPE_TREASURE_CHEST)){
				((MJL1TreasureChest)obj).dispose();
			}
		}
	}
}
