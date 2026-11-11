package l1j.server.Template.MapCleaner;

import java.util.Collection;

import l1j.server.Template.L1Type;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;

public class MapCleaner implements Runnable {
	public static void cleanup(short mapId, MapCleanerFilter filter) {
		System.out.println("맵 클리너 시작");
		GeneralThreadPool.getInstance().execute(new MapCleaner(mapId, filter));
	}
	
	private short 				_mapId;
	private MapCleanerFilter 	_filter;
	private MapCleaner(short mapId, MapCleanerFilter filter) {
		_mapId = mapId;
		_filter = filter;
	}

	@Override
	public void run() {
		// TODO 자동 생성된 메소드 스텁
		try{
			System.out.println("Tmfpem tlfgod");
			if(_filter == null) {
				clearNofilter();
			} 
			else {
				clear();
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			_filter = null;
		}
	}
	
	private void clearNofilter(){
//		Collection<L1Object> col = L1World.getInstance().getVisibleObjects(_mapId).values();
//		if(col == null || col.size() <= 0) {
//			return;
//		}
//		for(L1Object obj : col) {
//			if(obj == null) {
//				continue;
//			}
//			if(obj.instanceOf(L1Type.L1TYPE_NPC)) {
//				((L1NpcInstance) obj).deleteMe();
//			} else if(obj.instanceOf(L1Type.L1TYPE_INVENTORY)) {
//				((L1Inventory) obj).clearItems();
//			}
//		}
	}
	
	private void clear(){
//		Collection<L1Object> col = L1World.getInstance().getVisibleObjects(_mapId).values();
//		if(col == null || col.size() <= 0) {
//			return;
//		}
//		for(L1Object obj : col) {
//			if(obj == null)
//				continue;
//			
//			if(_filter.isFilter(obj))
//				continue;
//			
//			if(obj.instanceOf(L1Type.L1TYPE_NPC)){
//				((L1NpcInstance) obj).deleteMe();
//			}else if(obj.instanceOf(L1Type.L1TYPE_INVENTORY)) {
//				((L1Inventory) obj).clearItems();
//			}
//		}
	}
}
