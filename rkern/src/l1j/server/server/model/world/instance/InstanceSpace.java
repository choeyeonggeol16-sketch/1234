package l1j.server.server.model.world.instance;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.world.instance.loader.InstanceLoadManager;

public class InstanceSpace {
	private static Object	_lock 	= new Object();
	
	private static InstanceSpace 	_instance;
	public static InstanceSpace getInstance(){
		if(_instance == null)
			_instance = new InstanceSpace();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		InstanceSpace tmp = _instance;
		_instance = new InstanceSpace();
		tmp.clear();
		tmp = null;
	}
	
	private ArrayDeque<Integer>					_mapQ;
	private HashMap<Integer, InstanceObject> 	_objMap;
	private InstanceSpace(){
		_mapQ = new ArrayDeque<Integer>(InstanceLoadManager.MIS_COPYMAP_SIZE);
		int size = InstanceLoadManager.MIS_COPYMAP_START_ID + InstanceLoadManager.MIS_COPYMAP_SIZE;
		for(int i=InstanceLoadManager.MIS_COPYMAP_START_ID; i<size; i++)
			_mapQ.push(i);
		
		_objMap = new HashMap<Integer, InstanceObject>(InstanceLoadManager.MIS_COPYMAP_SIZE);
	}
	
	private L1Map popMap(InstanceObject obj){
		L1Map map = null;
		synchronized(_lock){
			if(_mapQ.isEmpty()){
				obj.notifySizeOver();
				return null;
			}
			
			if(obj.getBaseMapId() == -1)
				return null;
			
			map = L1WorldMap.getInstance().cloneMapAndGet(obj.getBaseMapId(), _mapQ.pop());
			obj.setCopyMap(map);
			_objMap.put(obj.getCopyMapId(), obj);
		}
		
		return map;
	}
	
	private void pushMap(InstanceObject obj){
		L1Map map = null;
		synchronized(_lock){
			map = obj.getCopyMap();
			if(map == null)
				return;
			
			_objMap.put(map.getId(), null);
			_mapQ.push(map.getId());
			L1WorldMap.getInstance().removeMap(map.getId());
		}
	}
	
	public void startInstance(InstanceObject obj){
		if(_mapQ.isEmpty()){
			obj.notifySizeOver();
			return;
		}
		
		if(popMap(obj) == null)
			return;
		
		try{
			obj.init();
			ThreadPool.getInstance().execute(obj);
		}catch(Exception e){
			e.printStackTrace();
			obj.dispose();
		}
	}
	
	public void releaseInstance(InstanceObject obj){
		if(obj == null || obj.getCopyMapId() == -1)
			return;
		
		try{
			
			int copyMapId = obj.getCopyMapId();
			Iterator<L1Object> it = L1World.getInstance().getVisibleObjects(copyMapId).values().iterator();
			while(it.hasNext()){
				L1Object l1obj = it.next();
				if((l1obj instanceof L1DollInstance) || (l1obj instanceof L1SummonInstance))
					continue;
				
				if((l1obj instanceof L1NpcInstance))
					((L1NpcInstance) l1obj).deleteMe();
			}
			
			Collection<L1ItemInstance> items 			= L1World.getInstance().getAllItem();
			L1ItemInstance[] 				itemArray 	= items.toArray(new L1ItemInstance[items.size()]);
			L1ItemInstance 				item			= null;
			CInventory						inv				=	 null;
			for(int i=0; i<itemArray.length; i++){
				item = itemArray[i];
				if(item == null)
					continue;
				
				if(copyMapId != item.getMapId())
					continue;
				
				inv = L1World.getInstance().getInventory(item.getX(), item.getY(), (short)copyMapId);
				if(inv == null)
					continue;
				
				inv.DeleteItemAmount(item);
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		pushMap(obj);
		obj.dispose();
		obj = null;
	}
	
	public static boolean isInInstance(L1PcInstance pc){
		return isInInstance(pc.getMapId());
	}
	
	public static boolean isInInstance(int mid){
		int size	= InstanceLoadManager.MIS_COPYMAP_START_ID + InstanceLoadManager.MIS_COPYMAP_SIZE;
		return (mid >= InstanceLoadManager.MIS_COPYMAP_START_ID && mid < size);
	}
	
	public void getBackPc(L1PcInstance pc){
		if(isInInstance(pc)){
			pc.setMap((short)InstanceLoadManager.MIS_ERRBACK_MAPID);
			pc.setX(InstanceLoadManager.MIS_ERRBACK_X);
			pc.setY(InstanceLoadManager.MIS_ERRBACK_Y);
		}
	}
	
	public int getIdenMap(int mid){
		if(isInInstance(mid)){
			InstanceObject obj = _objMap.get(mid);
			if(obj == null)
				return mid;
			
			return obj.getBaseMapId();
		}
		
		return mid;
	}
	
	public void clear(){
		if(_objMap != null){
			Iterator<InstanceObject> 	it 	= _objMap.values().iterator();
			InstanceObject 			obj	= null;
			while(it.hasNext()){
				obj = it.next();
				releaseInstance(obj);
			}
			
			_objMap.clear();
			_objMap = null;
		}
		
		if(_mapQ != null){
			_mapQ.clear();
			_mapQ = null;
		}
	}
	
	public static void teleportInstance(L1PcInstance pc, short mapId, int x, int y){
		pc.start_teleport(x, y, mapId, 5, 169, true, false);
	}
	
	public InstanceObject getOpenObject(int mapid){
		return _objMap.get(mapid);
	}
	
	public int getOpenSize(){
		return _objMap.size();
	}
	
	public Collection<InstanceObject> getOpens(){
		return _objMap.values();
	}
	
	public Collection<Integer> getOpensMaps(){
		return _objMap.keySet();
	}
}
