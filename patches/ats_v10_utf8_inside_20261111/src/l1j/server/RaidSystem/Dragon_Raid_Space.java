package l1j.server.RaidSystem;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class Dragon_Raid_Space {

	private static Logger 	_log 	= Logger.getLogger(Dragon_Raid_Space.class.getName());
	private static Object	_lock 	= new Object();

	private static Dragon_Raid_Space _instance;
	public static Dragon_Raid_Space getInstance(){
		if(_instance == null)
			_instance = new Dragon_Raid_Space();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}

	public static void reload(){
		Dragon_Raid_Space tmp = _instance;
		_instance = new Dragon_Raid_Space();
		tmp.clear();
		tmp = null;
	}
	private ArrayDeque<Integer> 			_mapQ;
	private HashMap<Integer, Dragon_Raid_Object> 	_objMap;
	private Dragon_Raid_Space() {
		_mapQ = new ArrayDeque<Integer>(Dragon_Raid_Load_Manager.MRS_COPYMAP_SIZE);
		int size = Dragon_Raid_Load_Manager.MRS_COPYMAP_START_ID + Dragon_Raid_Load_Manager.MRS_COPYMAP_SIZE;
		for(int i=Dragon_Raid_Load_Manager.MRS_COPYMAP_START_ID; i<size; i++)
			_mapQ.push(i);

		_objMap = new HashMap<Integer, Dragon_Raid_Object>(Dragon_Raid_Load_Manager.MRS_COPYMAP_SIZE);
	}

	/** 새 맵을 세팅한다. **/
	private L1Map popMap(Dragon_Raid_Object obj) {
		L1Map map = null;
		synchronized(_lock){
			if(_mapQ.isEmpty()) {
				obj.notifySizeOver();
				return null;
			}
			if(obj.getBaseMapId() == -1) {
				return null;
			}
			map = L1WorldMap.getInstance().cloneMapAndGet(obj.getBaseMapId(), _mapQ.pop());
			obj.setCopyMap(map);
			_objMap.put(obj.getCopyMapId(), obj);
		}
		return map;
	}
	/** 맵을 반환한다. **/
	private void pushMap(Dragon_Raid_Object obj) {
		L1Map map = null;
		synchronized(_lock) {
			map = obj.getCopyMap();
			if(map == null) {
				return;
			}
			_objMap.put(map.getId(), null);
			_mapQ.push(map.getId());
			L1WorldMap.getInstance().removeMap(map.getId());
		}
	}

	/** 인스턴스를 시작한다. **/
	public void startInstance(Dragon_Raid_Object obj) {
		if(_mapQ.isEmpty()) {
			obj.notifySizeOver();
			return;
		}

		popMap(obj);
		try {
			obj.init();
		} catch(Exception e) {
			_log.log(Level.SEVERE,obj.toString() + " - " + e.getLocalizedMessage(), e);
			obj.dispose();
			e.printStackTrace();
		}
	}

	/** 인스턴스를 반환한다. **/
	public void releaseInstance(Dragon_Raid_Object obj){

		if(obj == null || obj.getCopyMapId() == -1) {
			return;
		}
		try{
			int copyMapId = obj.getCopyMapId();
			Iterator<L1Object> it = L1World.getInstance().getVisibleObjects(copyMapId).values().iterator();
			while(it.hasNext()){
				L1Object l1obj = it.next();
				if((l1obj instanceof DollInstance) || (l1obj instanceof SummonInstance)) {
					continue;
				}
				if((l1obj instanceof L1NpcInstance)) {
					((L1NpcInstance) l1obj).deleteMe();
				}
			}

			Collection<ItemInstance>    items 			= L1World.getInstance().getAllItem();
			ItemInstance[] 				itemArray 	    = items.toArray(new ItemInstance[items.size()]);
			ItemInstance 				item			= null;
			L1Inventory					inv				= null;
			for(int i=0; i<itemArray.length; i++) {
				item = itemArray[i];
				if(item == null) { 
					continue;
				}
				if(copyMapId != item.getMapId())  {
					continue;
				}
				inv = L1World.getInstance().getInventory(item.getX(), item.getY(), (short)copyMapId);
				if(inv == null) {
					continue;
				}
				inv.removeItem(item);
			}
		} catch(Exception e) {
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
		int size 	= Dragon_Raid_Load_Manager.MRS_COPYMAP_START_ID + Dragon_Raid_Load_Manager.MRS_COPYMAP_SIZE;
		return (mid >= Dragon_Raid_Load_Manager.MRS_COPYMAP_START_ID && mid < size);		
	}

	public void getBackPc(L1PcInstance pc){
		Dragon_Raid_Object obj = null;
		int mid = 0;
		int x	= 0;
		int y	= 0; 
		if(isInInstance(pc)) {
			obj = _objMap.get(pc.getMapId());
			if(obj == null){
				mid = Dragon_Raid_Load_Manager.MRS_ERRBACK_MAPID;
				x	= Dragon_Raid_Load_Manager.MRS_ERRBACK_X;
				y	= Dragon_Raid_Load_Manager.MRS_ERRBACK_Y;
			}else{
				Dragon_Raid_Type type = obj.getRaidType();
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
			Dragon_Raid_Object obj = _objMap.get(mid);
			if(obj == null) {
				return mid;
			}
			return obj.getBaseMapId();
		}
		return mid;
	}

	public void clear(){
		if(_objMap != null){
			Iterator<Dragon_Raid_Object> 	it 	= _objMap.values().iterator();
			Dragon_Raid_Object 			obj = null;
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

	public Dragon_Raid_Object getOpenObject(int mapid){
		return _objMap.get(mapid);
	}

	public int getOpenSize(){
		return _objMap.size();
	}

	public Collection<Dragon_Raid_Object> getOpens(){
		return _objMap.values();
	}

	public Collection<Integer> getOpensMaps(){
		return _objMap.keySet();
	}
}

