package l1j.server.RaidSystem;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Random;

import l1j.server.Templates.L1Npc;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_WorldObject;

public class Dragon_Raid_Spawn {
	
	public static final String NAME = "raid_spawns";

	public static final int ST_NORMAL	= 1;
	public static final int ST_RECT 	= 2;
	public static final int ST_CIRCLE	= 4;
	public static final int ST_BOSS		= 8;
	public static final int ST_PORTAL	= 16;
	public static final int ST_DOOR		= 32;
	
	protected static Random _rnd = new Random(System.nanoTime());
	
	protected int			_raidId;		// 레이드 id.
	protected int 			_npcid;			// 스폰 대상이 될 npcid
	protected int			_type;			// 스폰 타입(범위 지정 스폰을 위해)
	protected int			_numOfSpawn;	// 스폰 마릿수(범위 지정 스폰을 위해)
	protected int 			_left;			// 스폰 영역(left)
	protected int 			_top;			// 스폰 영역(top)
	protected int 			_right;			// 스폰 영역(right)
	protected int 			_bottom;		// 스폰 영역(bottom)
	protected int 			_heading;		// 스폰시 헤딩
	protected L1Location	_loc;
	
	public Dragon_Raid_Spawn(){}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(64);
		sb.append("npc id : ").append(_npcid);
		return sb.toString();
	}
	
	public Dragon_Raid_Spawn(int npcId, int type, int numOfSpawn, int left, int top, int right, int bottom, int heading){
		_npcid 		= npcId;
		_type		= type;
		_numOfSpawn = numOfSpawn;
		_left		= left;
		_top		= top;
		_right		= right;
		_bottom		= bottom;
		_heading	= heading;
	}
	
	public boolean isInArea(L1PcInstance pc) {
		int cx = Math.abs(pc.getX() - _left);
		int cy = Math.abs(pc.getY() - _top);
		return (cx <= 18 && cy <= 18);
	}
	public boolean setInformation(ResultSet rs) {
		String column 	= null;
		String tmp 		= null;
		try{
			column 	= "raidId";
			_raidId = rs.getInt(column);
			
			column	= "npcId";
			_npcid	= rs.getInt(column);
			
			column	= "type";
			tmp		= rs.getString(column);
			if(tmp.equalsIgnoreCase("normal"))
				_type	= ST_NORMAL;
			else if(tmp.equalsIgnoreCase("random"))
				_type 	= ST_RECT;
			else if(tmp.equalsIgnoreCase("circle"))
				_type 	= ST_CIRCLE;
			else if(tmp.equalsIgnoreCase("boss"))
				_type	= ST_BOSS;
			else if(tmp.equalsIgnoreCase("portal"))
				_type	= ST_PORTAL;
			else if(tmp.equalsIgnoreCase("door"))
				_type	= ST_DOOR;
			else
				return false;
			
			column		= "numOfSpawn";
			_numOfSpawn	= rs.getInt(column);
			
			column		= "left";
			_left		= rs.getInt(column);
			
			column		= "top";
			_top		= rs.getInt(column);
			
			column		= "right";
			_right		= rs.getInt(column);
			
			column		= "bottom";
			_bottom		= rs.getInt(column);
			
			column		= "heading";
			_heading	= rs.getInt(column);
			
			_loc		= new L1Location(_left, _top, 4);
		} catch(Exception e) {
			StringBuilder sb = new StringBuilder();
			sb.append("Dragon_Raid_Spawn at setInformation()...invalid column! ").append(column);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public int getId(){
		return _raidId;
	}

	public int getSpawnType(){
		return _type;
	}
	
	/** normal spawn 1. (left, top) point spawn. **/
	public L1NpcInstance spawn(int mapId){
		return spawnNpc(mapId, _left, _top);
	}
	
	public L1NpcInstance spawn(int mapId, int x, int y) {
		return spawnNpc(mapId, x, y);
	}
	

	/** for random spawn **/
	public L1NpcInstance spawnRandom(int mapId){
		return spawnNpc(mapId, _rnd.nextInt(_right - _left) + _left, _rnd.nextInt(_bottom - _top) + _top);		
	}
	
	public L1NpcInstance spawnRandomBoss(int mapId){
		return spawnBoss(mapId, _rnd.nextInt(_right - _left) + _left, _rnd.nextInt(_bottom - _top) + _top);		
	}
	
	/** for rectangle spawn. this is random spawn. **/
	public ArrayList<L1NpcInstance> spawnRectangle(int mapId){
		ArrayList<L1NpcInstance> list = null;
		list = new ArrayList<L1NpcInstance>(_numOfSpawn);
		for(int i=0; i<_numOfSpawn; i++) {
			list.add(spawnRandom(mapId));
		}
		return list;
	}
	
	/** for rectangle spawn. this is random spawn. **/
	public ArrayList<L1NpcInstance> spawnRectangleBoss(int mapId){
		ArrayList<L1NpcInstance> list = null;
		list = new ArrayList<L1NpcInstance>(_numOfSpawn);
		for(int i=0; i<_numOfSpawn; i++)
			list.add(spawnRandomBoss(mapId));
		
		return list;
	}

	public ArrayList<L1NpcInstance> spawnCircleNonPass(int mapId){
		double radX	= (_right - _left) / 2;	// radius x
		double radY	= (_bottom - _top) / 2;	// radius y
		double cx	= radX + _left;			// center x
		double cy	= radY + _top;			// center y
		int add 	= 360 / _numOfSpawn;
		int rx		= 0;
		int ry		= 0;
		ArrayList<L1NpcInstance> list = new ArrayList<L1NpcInstance>(add);
		for(int i=1; i<=360; i+=add){
			rx		= (int)(cx + (Math.cos(i) * radX));
			ry		= (int)(cy + (Math.sin(i) * radY));
			list.add(spawnNpcNonPass(mapId, rx, ry));
		}
		return list;
	}
	
	/** for circle spawn **/
	public ArrayList<L1NpcInstance> spawnCircle(int mapId){
		double radX	= (_right - _left) / 2;	// radius x
		double radY	= (_bottom - _top) / 2;	// radius y
		double cx	= radX + _left;			// center x
		double cy	= radY + _top;			// center y
		int add 	= 360 / _numOfSpawn;
		int rx		= 0;
		int ry		= 0;
		ArrayList<L1NpcInstance> list = new ArrayList<L1NpcInstance>(add);
		for(int i=1; i<=360; i+=add){
			rx		= (int)(cx + (Math.cos(i) * radX));
			ry		= (int)(cy + (Math.sin(i) * radY));
			list.add(spawnNpc(mapId, rx, ry));
		}
		return list;
	}
	
	public L1NpcInstance spawnBoss(int mapId){
		return spawnBoss(mapId, _left, _top);
	}
	
	public L1NpcInstance spawnBoss(int mapId, int x, int y){
		L1NpcInstance npc = null;
		try{
			npc = NpcTable.getInstance().newNpcInstance(_npcid);
			if(npc == null)
				return npc;
			
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap((short)mapId);
			npc.getLocation().forward(0);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(_heading);
			npc.setLightSize(npc.getNpcTemplate().getLightSize());
			npc.getLight().turnOnOffLight();
			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);
			Broadcaster.broadcastPacket(npc, S_WorldObject.npc(npc), true);
			Broadcaster.broadcastPacket(npc, S_DoActionGFX.get(npc.getId(), 11), true);
			npc.getMap().setPassable(x, y, false);
		}catch(Exception e){
			e.printStackTrace();
		}
		return npc;
	}
	
	protected L1NpcInstance spawnNpcNonPass(int mapId, int x, int y){
		L1NpcInstance npc = null;
		try{
			npc = NpcTable.getInstance().newNpcInstance(_npcid);
			if(npc == null)
				return npc;
			
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap((short)mapId);
			npc.getLocation().forward(0);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(_heading);
			npc.setLightSize(npc.getNpcTemplate().getLightSize());
			npc.getLight().turnOnOffLight();
			npc.setPassObject(false);
			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);
			npc.getMap().setPassable(x, y, false);
		}catch(Exception e){
			e.printStackTrace();
		}
		return npc;
	}
	
	protected L1NpcInstance spawnNpcNonPass(int npcid, int mapId, int x, int y){
		L1NpcInstance npc = null;
		try{
			npc = NpcTable.getInstance().newNpcInstance(npcid);
			if(npc == null)
				return npc;
					
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap((short)mapId);
			npc.getLocation().forward(0);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(_heading);
			npc.setLightSize(npc.getNpcTemplate().getLightSize());
			npc.getLight().turnOnOffLight();
			npc.setPassObject(false);
			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);
			npc.getMap().setPassable(x, y, false);
		}catch(Exception e){
			e.printStackTrace();
		}
		return npc;
	}
	
	protected L1NpcInstance spawnNpc(int mapId, int x, int y){
		L1NpcInstance npc = null;
		try{
			npc = NpcTable.getInstance().newNpcInstance(_npcid);
			if(npc == null)
				return npc;
			
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap((short)mapId);
			npc.getLocation().forward(0);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(_heading);
			npc.setLightSize(npc.getNpcTemplate().getLightSize());
			npc.getLight().turnOnOffLight();
			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);
			npc.getMap().setPassable(x, y, false);
		}catch(Exception e){
			e.printStackTrace();
		}
		return npc;
	}
	
	public L1NpcInstance spawnNpcForDummy(int mapId){
		return spawnNpcForDummy(mapId, _left, _top);
	}
	
	public L1NpcInstance spawnNpcForDummy(int mapId, int x, int y){
		L1NpcInstance npc = null;
		try{
			npc = NpcTable.getInstance().newNpcInstance(_npcid);
			if(npc == null)
				return npc;
			
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap((short)mapId);
			npc.getLocation().forward(0);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(_heading);
		}catch(Exception e){
			e.printStackTrace();
		}
		return npc;
	}
	
	public void manualNpc(L1NpcInstance npc){
		npc.setLightSize(npc.getNpcTemplate().getLightSize());
		npc.getLight().turnOnOffLight();
		L1World.getInstance().storeObject(npc);
		L1World.getInstance().addVisibleObject(npc);
		npc.getMap().setPassable(npc.getX(), npc.getY(), false);
	}
	
	public ArrayList<L1NpcInstance> spawnForArray(int npcid, int mapId, int[][] posArray){
		ArrayList<L1NpcInstance> npcs 	= new ArrayList<L1NpcInstance>(posArray.length / 2);
		L1NpcInstance npc 				= null;
		try{
			L1Npc npcTemp = NpcTable.getInstance().getTemplate(npcid);
			if(npcTemp == null)
				return null;
			
			int x = 0;
			int y = 0;
			for(int i=0; i<posArray.length; i++){
				x = posArray[i][0];
				y = posArray[i][1];
				npc = new L1NpcInstance(npcTemp);
				npc.setId(IdFactory.getInstance().nextId());
				npc.setMap((short)mapId);
				npc.getLocation().forward(0);
				npc.setX(x);
				npc.setY(y);
				npc.setHomeX(x);
				npc.setHomeY(y);
				npc.setHeading(_heading);
				npc.setLightSize(npc.getNpcTemplate().getLightSize());
				npc.getLight().turnOnOffLight();
				L1World.getInstance().storeObject(npc);
				L1World.getInstance().addVisibleObject(npc);
				npc.setPassObject(false);
				npcs.add(npc);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return npcs;
	}
	
	public void setPos(int left, int top, int right, int bottom) {
		_left 	= left;
		_top	= top;
		_right	= right;
		_bottom	= bottom;
	}
	
	public int getLeft() {
		return _left;
	}
	
	public int getTop() {
		return _top;
	}
	
	public int getRight() {
		return _right;
	}
	
	public int getBottom() {
		return _bottom;
	}
	
}
