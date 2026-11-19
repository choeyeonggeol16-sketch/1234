package l1j.server.MJRaidSystem;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

/** 레이드 공간을 할당하고 시작하는 헬퍼. **/
public class MJRaidSpace {
	private static Logger 	_log 	= Logger.getLogger(MJRaidSpace.class.getName());
	private static Object	_lock 	= new Object();
	
	private static MJRaidSpace _instance;
	public static MJRaidSpace getInstance(){
		if(_instance == null)
			_instance = new MJRaidSpace();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		MJRaidSpace tmp = _instance;
		_instance = new MJRaidSpace();
		tmp.clear();
		tmp = null;
	}
	
	/** 효율적으로 mapid를 관리하기 위한 Q **/
	private ArrayDeque<Integer> 			_mapQ;
	private HashMap<Integer, MJRaidObject> 	_objMap;
	private MJRaidSpace(){
		_mapQ = new ArrayDeque<Integer>(MJRaidLoadManager.MRS_COPYMAP_SIZE);
		int size = MJRaidLoadManager.MRS_COPYMAP_START_ID + MJRaidLoadManager.MRS_COPYMAP_SIZE;
		for(int i=MJRaidLoadManager.MRS_COPYMAP_START_ID; i<size; i++)
			_mapQ.push(i);
		
		_objMap = new HashMap<Integer, MJRaidObject>(MJRaidLoadManager.MRS_COPYMAP_SIZE);
	}
	
	/** 새 맵을 세팅한다. **/
	private L1Map popMap(MJRaidObject obj){
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
	
	/** 맵을 반환한다. **/
	private void pushMap(MJRaidObject obj){
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
	
	/** 인스턴스를 시작한다. **/
	public void startInstance(MJRaidObject obj){
		if(_mapQ.isEmpty()){
			obj.notifySizeOver();
			return;
		}
		
		popMap(obj);
		try{
			obj.init();
		}catch(Exception e){
			_log.log(Level.SEVERE,obj.toString() + " - " + e.getLocalizedMessage(), e);
			obj.dispose();
			e.printStackTrace();
		}
	}
	
	/** 인스턴스를 반환한다. **/
	public void releaseInstance(MJRaidObject obj){
		
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
			L1Inventory						inv				=	 null;
			for(int i=0; i<itemArray.length; i++){
				item = itemArray[i];
				if(item == null)
					continue;
				
				if(copyMapId != item.getMapId())
					continue;
				
				inv = L1World.getInstance().getInventory(item.getX(), item.getY(), (short)copyMapId);
				if(inv == null)
					continue;
				
				inv.removeItem(item);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		
		pushMap(obj);
		obj.dispose();
		obj = null;
	}
	
	public boolean isInInstance(L1PcInstance pc){
		return isInInstance(pc.getMapId());
	}
	
	public boolean isInInstance(int mid){
		int size 	= MJRaidLoadManager.MRS_COPYMAP_START_ID + MJRaidLoadManager.MRS_COPYMAP_SIZE;
		return (mid >= MJRaidLoadManager.MRS_COPYMAP_START_ID && mid < size);		
	}
	
	public void getBackPc(L1PcInstance pc){
		MJRaidObject obj = null;
		int mid = 0;
		int x	= 0;
		int y	= 0; 
		if(isInInstance(pc)){
			obj = _objMap.get(pc.getMapId());
			if(obj == null){
				mid = MJRaidLoadManager.MRS_ERRBACK_MAPID;
				x	= MJRaidLoadManager.MRS_ERRBACK_X;
				y	= MJRaidLoadManager.MRS_ERRBACK_Y;
			}else{
				MJRaidType type = obj.getRaidType();
				mid	= type.getOutMapId();
				x	= type.getOutX();
				y	= type.getOutY();
			}
			
			pc.setX(x);
			pc.setY(y);
			pc.setMap((short)mid);
		}
	}
	
	public int getIdenMap(int mid){
		if(isInInstance(mid)){
			MJRaidObject obj = _objMap.get(mid);
			if(obj == null)
				return mid;
			
			return obj.getBaseMapId();
		}
		return mid;
	}
	
	public void clear(){
		if(_objMap != null){
			Iterator<MJRaidObject> 	it 	= _objMap.values().iterator();
			MJRaidObject 			obj = null;
			while(it.hasNext()){
				obj = it.next();
				releaseInstance(obj);
			}
			_objMap.clear();
			_objMap	= null;
		}
		
		if(_mapQ != null){
			_mapQ.clear();
			_mapQ = null;
		}
	}
	
	public MJRaidObject getOpenObject(int mapid){
		return _objMap.get(mapid);
	}
	
	public int getOpenSize(){
		return _objMap.size();
	}
	
	public Collection<MJRaidObject> getOpens(){
		return _objMap.values();
	}
	
	public Collection<Integer> getOpensMaps(){
		return _objMap.keySet();
	}
}
