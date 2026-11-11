/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.WarSystem.War;
import l1j.server.configure.Config;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.types.Point;

public class L1World {
	private static Logger _log = Logger.getLogger(L1World.class.getName());
	
	private final ConcurrentHashMap<String,  L1PcInstance> _allPlayers;
	private final ConcurrentHashMap<Integer, PetInstance> _allPets;
	private final ConcurrentHashMap<Integer, SummonInstance> _allSummons;
	private final ConcurrentHashMap<Integer, L1Object> _allObjects;
	private final ConcurrentHashMap<Integer, L1Object>[] _visibleObjects;
	private final ConcurrentHashMap<Integer, War>					_allClanWars;
	private final ConcurrentHashMap<String,  L1Clan> _allClans;
	private final ConcurrentHashMap<Integer, L1Clan> _allClansById;
	private final ConcurrentHashMap<Integer, L1NpcInstance> _allNpc;
	private final ConcurrentHashMap<Integer, L1NpcShopInstance> _allShopNpc;
	private final ConcurrentHashMap<Integer, ItemInstance> 		_allitem;

	private int _weather = 4;

	private boolean _worldChatEnabled = true;

	private boolean _processingContributionTotal = false;

	private static final int MAX_MAP_ID = 32768;

	private static L1World _instance;
	
	@SuppressWarnings("unchecked")  
	private L1World() {
		_allPlayers = new ConcurrentHashMap<String, L1PcInstance>(); // 모든 플레이어
		_allPets = new ConcurrentHashMap<Integer, PetInstance>(); // 모든 애완동물
		_allSummons = new ConcurrentHashMap<Integer, SummonInstance>(); // 모든 사몬몬스타
		_allObjects = new ConcurrentHashMap<Integer, L1Object>(); // 모든 오브젝트(L1ItemInstance 들어가, L1Inventory는 없음)
		_visibleObjects = new ConcurrentHashMap[MAX_MAP_ID + 1]; // MAP 마다의 오브젝트(L1Inventory 들어가, L1ItemInstance는 없음)
		_allNpc = new ConcurrentHashMap<Integer, L1NpcInstance>(); 
		_allitem 		= new ConcurrentHashMap<Integer, ItemInstance>(180 * Config.MAX_ONLINE_USERS * 4);
		_allClanWars 	= new ConcurrentHashMap<Integer, War>(64); // 모든 전쟁
		_allClansById = new ConcurrentHashMap<Integer, L1Clan>(); // 모든 크란(Online/Offline 어느쪽이나)
		_allClans = new ConcurrentHashMap<String, L1Clan>(); // 모든 크란(Online/Offline 어느쪽이나)
		_allShopNpc = new ConcurrentHashMap<Integer, L1NpcShopInstance>(); // 모든 무인NPC상점

		for (int i = 0; i <= MAX_MAP_ID; i++) {
			_visibleObjects[i] = new ConcurrentHashMap<Integer, L1Object>();
		}
	}

	public static L1World getInstance() {
		if (_instance == null) {
			_instance = new L1World();
		}
		return _instance;
	}

	/**
	 * 모든 상태를 클리어 한다.<br>
	 * 디버그, 테스트등이 특수한 목적 이외로 호출해서는 안 된다.
	 */
	public void clear() {
		_instance = new L1World();
	}

    public void storeObject(L1Object object) {
		if (object == null) {
			throw new NullPointerException();
		}

		_allObjects.put(object.getId(), object);
		if (object instanceof L1PcInstance) {
			_allPlayers.put(((L1PcInstance) object).getName(), (L1PcInstance) object);
		}
		if (object instanceof PetInstance) {
			_allPets.put(object.getId(), (PetInstance) object);
		}
		if (object instanceof SummonInstance) {
			_allSummons.put(object.getId(), (SummonInstance) object);
		}
		if (object instanceof L1NpcInstance) {
			_allNpc.put(((L1NpcInstance) object).getNpcTemplate().get_npcId(), (L1NpcInstance) object);
		}
		if (object instanceof L1NpcShopInstance) {
			_allShopNpc.put(object.getId(), (L1NpcShopInstance) object);
		}
	}

	public void removeObject(L1Object object) {
		if (object == null) {
			throw new NullPointerException();
		}

		_allObjects.remove(object.getId());
		if (object instanceof L1PcInstance) {
			_allPlayers.remove(((L1PcInstance) object).getName());
		}
		if (object instanceof PetInstance) {
			_allPets.remove(object.getId());
		}
		if (object instanceof SummonInstance) {
			_allSummons.remove(object.getId());
		}
		if (object instanceof L1NpcInstance) {
			_allNpc.remove(((L1NpcInstance) object).getNpcTemplate().get_npcId());
		}
		if (object instanceof L1NpcShopInstance) {
			_allShopNpc.remove(object.getId());
		}
	}

	public L1Object findObject(int oID) {
		return _allObjects.get(oID);
	}

	// _allObjects의 뷰
	private Collection<L1Object> _allValues;

	public Collection<L1Object> getObject() {
		Collection<L1Object> vs = _allValues;
		return (vs != null) ? vs : (_allValues = Collections
				.unmodifiableCollection(_allObjects.values()));
	}

	public L1GroundInventory getInventory(int x, int y, short map) {
		int inventoryKey = ((x - 30000) * 10000 + (y - 30000)) * -1; // xy의 마이너스치를 인벤트리 키로서 사용

		Object object = _visibleObjects[map].get(inventoryKey);
		if (object == null) {
			return new L1GroundInventory(inventoryKey, x, y, map);
		} else {
			return (L1GroundInventory) object;
		}
	}

	public L1GroundInventory getInventory(L1Location loc) {
		return getInventory(loc.getX(), loc.getY(), (short) loc.getMap()
				.getId());
	}

	public void addVisibleObject(L1Object object) {
		if (object.getMapId() <= MAX_MAP_ID) {
			_visibleObjects[object.getMapId()].put(object.getId(), object);
		}
	}

	public void removeVisibleObject(L1Object object) {
		if (object.getMapId() <= MAX_MAP_ID) {
			_visibleObjects[object.getMapId()].remove(object.getId());
		}
	}

	public void moveVisibleObject(L1Object object, int newMap) {// set_Map로 새로운 Map로 하기 전에 부르는 것
		
		if(object == null) {
			return;
		}
		int mapId = object.getMapId();
		int objectId = object.getId();
		if (mapId != newMap) {
			if (mapId <= MAX_MAP_ID) {
				_visibleObjects[mapId].remove(objectId);
			}
			if (newMap <= MAX_MAP_ID) {
				_visibleObjects[newMap].put(objectId, object);
			}
		}
	}
	
	public void moveVisibleObject(L1Object object, int newMap, int x, int y) // set_Map로 새로운 Map로 하기 전에 부르는 것
	{
		if (object.getMapId() != newMap) {
			if (object.getMapId() <= MAX_MAP_ID) {
				_visibleObjects[object.getMapId()].remove(object.getId());
			}
			if (newMap <= MAX_MAP_ID) { 
				_visibleObjects[newMap].put(object.getId(), object);
			}
		}
	}

	private ConcurrentHashMap<Integer, Integer> createLineMap(Point src, Point target) {
		ConcurrentHashMap<Integer, Integer> lineMap = new ConcurrentHashMap<Integer, Integer>();
		int E;
		int x;
		int y;
		int key;
		int i;
		int x0 = src.getX();
		int y0 = src.getY();
		int x1 = target.getX();
		int y1 = target.getY();
		int sx = (x1 > x0) ? 1 : -1;
		int dx = (x1 > x0) ? x1 - x0 : x0 - x1;
		int sy = (y1 > y0) ? 1 : -1;
		int dy = (y1 > y0) ? y1 - y0 : y0 - y1;

		x = x0;
		y = y0;
		/* 기울기가 1 이하의 경우 */
		if (dx >= dy) {
			E = -dx;
			for (i = 0; i <= dx; i++) {
				key = (x << 16) + y;
				lineMap.put(key, key);
				x += sx;
				E += 2 * dy;
				if (E >= 0) {
					y += sy;
					E -= 2 * dx;
				}
			}
			/* 기울기가 1보다 큰 경우 */
		} else {
			E = -dy;
			for (i = 0; i <= dy; i++) {
				key = (x << 16) + y;
				lineMap.put(key, key);
				y += sy;
				E += 2 * dx;
				if (E >= 0) {
					x += sx;
					E -= 2 * dy;
				}
			}
		}

		return lineMap;
	}

	public ArrayList<L1Object> getVisibleLineObjects(L1Object src,
			L1Object target) {
		ConcurrentHashMap<Integer, Integer> lineMap = createLineMap(src
				.getLocation(), target.getLocation());

		int map = target.getMapId();
		ArrayList<L1Object> result = new ArrayList<L1Object>();

		if (map <= MAX_MAP_ID) {
			for (L1Object element : _visibleObjects[map].values()) {
				if (element.equals(src)) {
					continue;
				}

				int key = (element.getX() << 16) + element.getY();
				if (lineMap.containsKey(key)) {
					result.add(element);
				}
			}
		}

		return result;
	}

	public ArrayList<L1Object> getVisibleBoxObjects(L1Object object,
			int heading, int width, int height) {
		int x = object.getX();
		int y = object.getY();
		int map = object.getMapId();
		L1Location location = object.getLocation();
		ArrayList<L1Object> result = new ArrayList<L1Object>();
		int headingRotate[] = { 6, 7, 0, 1, 2, 3, 4, 5 };
		double cosSita = Math.cos(headingRotate[heading] * Math.PI / 4);
		double sinSita = Math.sin(headingRotate[heading] * Math.PI / 4);

		if (map <= MAX_MAP_ID) {
			for (L1Object element : _visibleObjects[map].values()) {
				if (element.equals(object)) {
					continue;
				}
				if (map != element.getMapId()) {
					continue;
				}

				int distance = location.getTileLineDistance(element
						.getLocation());
				// 직선 거리가 높이, 폭어느 쪽보다 큰 경우, 계산할 것도 없이 범위외
				if (distance > height && distance > width) {
					continue;
				}

				// object의 위치를 원점과하기 위한 좌표 보정
				int x1 = element.getX() - x;
				int y1 = element.getY() - y;

				// Z축회전시키고 각도를 0번으로 한다.
				int rotX = (int) Math.round(x1 * cosSita + y1 * sinSita);
				int rotY = (int) Math.round(-x1 * sinSita + y1 * cosSita);

				int xmin = 0;
				int xmax = height;
				int ymin = -width;
				int ymax = width;

				// 깊이가 사정과 맞물리지 않기 때문에 직선 거리로 판정하도록(듯이) 변경.
				if (rotX > xmin && distance <= xmax && rotY >= ymin && rotY <= ymax) {
					result.add(element);
				}
			}
		}

		return result;
	}

	public ArrayList<L1Object> getVisibleObjects(L1Object object) {
		return getVisibleObjects(object, -1);
	}

	public ArrayList<L1Object> getVisibleObjects(L1Object object, int radius) {
		L1Map map = object.getMap();
		Point pt = object.getLocation();
		ArrayList<L1Object> result = new ArrayList<L1Object>();
		if (map.getId() <= MAX_MAP_ID) {
			for (L1Object element : _visibleObjects[map.getId()].values()) {
				if (element.equals(object)) {
					continue;
				}
				if (map != element.getMap()) {
					continue;
				}
				if (radius == -1) {
					if (pt.isInScreen(element.getLocation())) {
						result.add(element);
					}
				} else if (radius == 0) {
					if (pt.isSamePoint(element.getLocation())) {
						result.add(element);
					}
				} else {
					if (pt.getTileLineDistance(element.getLocation()) <= radius) {
						result.add(element);
					}
				}
			}
		}

		return result;
	}

	public ArrayList<L1Object> getVisiblePoint(L1Location loc, int radius) {
		ArrayList<L1Object> result = new ArrayList<L1Object>();
		int mapId = loc.getMapId(); // 루프내에서 부르면(자) 무겁기 때문에
		if (mapId <= MAX_MAP_ID) {
			for (L1Object element : _visibleObjects[mapId].values()) {
				if (mapId != element.getMapId()) {
					continue;
				}
				if (loc.getTileLineDistance(element.getLocation()) <= radius) {			
					result.add(element);
				}
			}
		}
		return result;
	}

	public ArrayList<L1PcInstance> getVisiblePlayer(L1Object object) {
		return getVisiblePlayer(object, -1);
	}

	public ArrayList<L1PcInstance> getVisiblePlayer(L1Object object, int radius) {
		int map = object.getMapId();
		Point pt = object.getLocation();
		Collection<L1PcInstance> pc = _allPlayers.values();
		ArrayList<L1PcInstance> result = null;
		if(radius < 0) {
			result = new ArrayList<L1PcInstance>(19 * 4);
		}
		else if(radius == 0) {
			result = new ArrayList<L1PcInstance>(2);
		}
		else {
			result = new ArrayList<L1PcInstance>(radius * 4);
		}		
		for (L1PcInstance element : pc) {
			if ((element == null) || (element.equals(object)) || (map != element.getMapId())) {
				continue;
			}
			if (radius == -1) {
				if (pt.isInScreen(element.getLocation())) {
					result.add(element);
				}
			} else if (radius == 0) {
				if (pt.isSamePoint(element.getLocation())) {
					result.add(element);
				}
			} else if (pt.getTileLineDistance(element.getLocation()) <= radius) {
				result.add(element);
			}
		}
		return result;
	}


	public ArrayList<L1PcInstance> getVisiblePlayerExceptTargetSight(L1Object object, L1Object target) {
		int map = object.getMapId();
		Point objectPt = object.getLocation();
		Point targetPt = target.getLocation();
		Collection<L1Object> col = _visibleObjects[map].values();
		ArrayList<L1PcInstance> result = new ArrayList<L1PcInstance>(64);
		for (L1Object targetObject : col) {
			if (!(targetObject instanceof L1PcInstance)) {
				continue;
			}

			L1PcInstance element = (L1PcInstance) (targetObject);

			if (element == null || element.equals(object)) {
				continue;
			}

			if (Config.PC_RECOGNIZE_RANGE == -1) {
				if ((objectPt.isInScreen(element.getLocation())) && (!targetPt.isInScreen(element.getLocation()))) {
					result.add(element);
				}
			} else if ((objectPt.getTileLineDistance(element.getLocation()) <= Config.PC_RECOGNIZE_RANGE)
					&& (targetPt.getTileLineDistance(element.getLocation()) > Config.PC_RECOGNIZE_RANGE)) {
				result.add(element);
			}
		}
		return result;
	}

	/**
	 * object를 인식할 수 있는 범위에 있는 플레이어를 취득한다
	 * 
	 * @param object
	 * @return
	 */
	public ArrayList<L1PcInstance> getRecognizePlayer(L1Object object) {
		return getVisiblePlayer(object, Config.PC_RECOGNIZE_RANGE);
	}
	
	public L1PcInstance[] getAllPlayers3() {
		return _allPlayers.values().toArray(new L1PcInstance[_allPlayers.size()]);
	}

	// _allPlayers의 뷰
	private Collection<L1PcInstance> _allPlayerValues;

	public Collection<L1PcInstance> getAllPlayers() {
		Collection<L1PcInstance> vs = _allPlayerValues;
		return (vs != null) ? vs : (_allPlayerValues = Collections
				.unmodifiableCollection(_allPlayers.values()));
	}

	/**
	 * 월드내에 있는 지정된 이름의 플레이어를 취득한다.
	 * 
	 * @param name -
	 *            플레이어명(소문자·대문자는 무시된다)
	 * @return 지정된 이름의 L1PcInstance. 해당 플레이어가 존재하지 않는 경우는 null를 돌려준다.
	 */
	public L1PcInstance getPlayer(String name) {
		if (_allPlayers.contains(name)) {
			return _allPlayers.get(name);
		}
		for (L1PcInstance each : getAllPlayers()) {
			if (each.getName().equalsIgnoreCase(name)) {
				return each;
			}
		}
		return null;
	}
	
	public L1NpcShopInstance getShopNpc(String name) {
		Collection<L1NpcShopInstance> npc = null;
		npc = getAllShopNpc();
		for (L1NpcShopInstance each : npc) {
			if(each == null) continue;
			if (each.getName().equalsIgnoreCase(name)) {
				return each;
			}
		}
		return null;
	}
	public L1NpcShopInstance getNpcShop(int id) {
		Collection<L1NpcShopInstance> npc = null;
		npc = getAllShopNpc();
		for (L1NpcShopInstance each : npc) {
			if (each == null)
				continue;
			if (each.getNpcId() == id) {
				return each;
			}
		}
		return null;
	}

	// _allShopNpc의 뷰
	private Collection<L1NpcShopInstance> _allShopNpcValues;

	public Collection<L1NpcShopInstance> getAllShopNpc() {
		Collection<L1NpcShopInstance> vs = _allShopNpcValues;
		return (vs != null) ? vs : (_allShopNpcValues = Collections.unmodifiableCollection(_allShopNpc.values()));
	}
	
	// _allPets의 뷰
	private Collection<PetInstance> _allPetValues;

	public Collection<PetInstance> getAllPets() {
		Collection<PetInstance> vs = _allPetValues;
		return (vs != null) ? vs : (_allPetValues = Collections.unmodifiableCollection(_allPets.values()));
	}

	// _allSummons의 뷰
	private Collection<SummonInstance> _allSummonValues;

	public Collection<SummonInstance> getAllSummons() {
		Collection<SummonInstance> vs = _allSummonValues;
		return (vs != null) ? vs : (_allSummonValues = Collections
				.unmodifiableCollection(_allSummons.values()));
	}

	public final Map<Integer, L1Object> getAllVisibleObjects() {
		return _allObjects;
	}

	public final Map<Integer, L1Object>[] getVisibleObjects() {
		return _visibleObjects;
	}

	public final Map<Integer, L1Object> getVisibleObjects(int mapId) {
		return _visibleObjects[mapId];
	}

	public Object getRegion(Object object) {
		return null;
	}
	
	public void addWar(War war) {
		_allClanWars.put(war.getId(), war);
	}

	public War removeWar(War war) {
		return _allClanWars.remove(war.getId());
	}
	
	public Stream<War> createWarStream(){
		return _allClanWars.size() <= 0 ? null : _allClanWars.values().stream();
	}

	public void storeClan(L1Clan clan) {
		L1Clan temp = getClan(clan.getClanId());
		if (temp == null) {
			_allClansById.put(clan.getClanId(), clan);
			_allClans.put(clan.getClanName(), clan);
		}
	}

	public void removeClan(L1Clan clan) {
		L1Clan temp = getClan(clan.getClanId());
		if (temp != null) {
			_allClansById.remove(clan.getClanId());
			_allClans.remove(clan.getClanName());
		}
	}

	public L1Clan getClan(String clan_name) {
		return _allClans.get(clan_name);
	}
	
	public L1Clan getClan(int clanId)
	{
		return _allClansById.get(clanId);
	}

	// _allClans의 뷰
	private Collection<L1Clan> _allClanValues;

	public Collection<L1Clan> getAllClans() {
		Collection<L1Clan> vs = _allClanValues;
		return (vs != null) ? vs : (_allClanValues = Collections.unmodifiableCollection(_allClans.values()));
	}

	public void setWeather(int weather) {
		_weather = weather;
	}

	public int getWeather() {
		return _weather;
	}

	public void set_worldChatElabled(boolean flag) {
		_worldChatEnabled = flag;
	}

	public boolean isWorldChatElabled() {
		return _worldChatEnabled;
	}

	public void setProcessingContributionTotal(boolean flag) {
		_processingContributionTotal = flag;
	}

	public boolean isProcessingContributionTotal() {
		return _processingContributionTotal;
	}

	/**
	 * 월드상에 존재하는 모든 플레이어에 패킷을 송신한다.
	 * 
	 * @param packet
	 *            송신하는 패킷을 나타내는 ServerBasePacket 오브젝트.
	 */
	public void broadcastPacketToAll(ServerBasePacket packet) {
		_log.finest("players to notify : " + getAllPlayers().size());
		for (L1PcInstance pc : getAllPlayers()) {
			if(pc == null) {
				continue;
			}
			pc.sendPackets(packet); 
		}
	}
	
	public void broadcastPacketToAll(ServerBasePacket packet, boolean clear) {
		Collection<L1PcInstance> pclist = null;
		pclist = getAllPlayers();
		_log.finest("players to notify : " + pclist.size());
		for (L1PcInstance pc : pclist) {
			if (pc != null) {
				pc.sendPackets(packet, false);
			}
		}
		if (clear) {
			packet.clear();
			packet = null;
		}
	}
	
	public void broadcastPacketToAll(ServerBasePacket[] pcks) {
		for (L1PcInstance pc : getAllPlayers()) {
			if(pc == null)
				continue;
			
			for(ServerBasePacket pck : pcks)
				pc.sendPackets(pck);
		}
		
		for(ServerBasePacket pck : pcks)
			pck.clear();
	}

	/**
	 * 월드상에 존재하는 모든 플레이어에 서버 메세지를 송신한다.
	 * 
	 * @param message
	 *            송신하는 메세지
	 */
	public void broadcastServerMessage(String message) {
		broadcastPacketToAll(new S_SystemMessage(message));
	}
	
	public L1NpcInstance findNpc(int id) {
		return _allNpc.get(id);
	}
	public Collection<L1NpcInstance> getAllNpc() {
		return Collections.unmodifiableCollection(_allNpc.values());
	}
	
	public Stream<L1PcInstance> getAllPlayerStream(){
		Collection<L1PcInstance> col = getAllPlayers();
		return col.size() > 1024 ? col.parallelStream() : col.stream();
	}
	
	public void castPacketlogin(ServerBasePacket packet) {
		for (L1PcInstance pc : getAllPlayers()) {
			if (!pc.isDistributionlogin()) {
				continue;
			}
			if (pc != null && pc.isGm()) {
				pc.sendPackets(packet, false);
			}
		}
		packet.clear();
	}
	
	public void castPacketGm(ServerBasePacket packet) {
		for (L1PcInstance pc : getAllPlayers()) {
			if (!pc.isDistributionMsg()) {
				continue;
			}
			if (pc != null && pc.isGm()) {
				pc.sendPackets(packet, false);
			}
		}
		packet.clear();
	}
	
	public void broadcastPacketToAll1(ServerBasePacket packet) {
		for (L1PcInstance pc : getAllPlayers()) {
			if (pc != null) {
				if (!pc.isWorldKillMessage()) {
					continue;
				}
				pc.sendPackets(packet, false);
			}
		}
		
		packet.clear();
		packet = null;
	}
	
	public Stream<L1Object> createVisibleObjectsStream(int mapId){
		Collection<L1Object> col = _visibleObjects[mapId].values();
		return col.size() > 1024 ? col.parallelStream() : col.stream();
	}
	
	public L1Clan findClan(String clanName){
		int len = clanName.length();
		for(L1Clan clan : getAllClans()){
			String name = clan.getClanName();
			if(name.length() != len)
				continue;
			
			if(name.equalsIgnoreCase(clanName))
				return clan;
		}
		return null;
	}
	
	public void clearClan() {
		_allClans.clear();
	}
	// 레이드 용	
	private Collection<ItemInstance> _allItemValues;

	public Collection<ItemInstance> getAllItem() {
		Collection<ItemInstance> vs = _allItemValues;
		return (vs != null) ? vs : (_allItemValues = Collections.unmodifiableCollection(_allitem.values()));
	}
}