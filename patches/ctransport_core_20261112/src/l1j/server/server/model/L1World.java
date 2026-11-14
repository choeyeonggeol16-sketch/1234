package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;


import l1j.server.Config;
import l1j.server.MJ3SEx.MJNpcSpeedData;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNRoom;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.types.Point;

public class L1World {
    private static Logger _log = Logger.getLogger(L1World.class.getName());
    
    private static final VarHandle VISIBLE_MAP =
            MethodHandles.arrayElementVarHandle(L1IntRegistry[].class);
    @SuppressWarnings("unchecked")
    private final L1IntRegistry<L1Object>[] _visibleObjects =
            (L1IntRegistry<L1Object>[]) new L1IntRegistry<?>[MAX_MAP_ID + 1];
    final ConcurrentHashMap<String, L1PcInstance> 			_allPlayers;
    final L1IntRegistry<L1PetInstance> _allPets;
    final L1IntRegistry<L1CompanionInstance> _allCompanions;
    final L1IntRegistry<L1SummonInstance> _allSummons;
    final L1IntRegistry<L1SupportInstance> _allSupports;

    private final L1IntRegistry<MJWar> _allClanWars;
    private final L1IntRegistry<L1ItemInstance> _allitem;
    private final L1IntRegistry<L1Clan> _allClans;
    final L1IntRegistry<L1NpcInstance> _allNpc;
    final L1IntRegistry<L1NpcShopInstance> _allShopNpc;
    private final L1IntRegistry<L1GuardInstance> _allGuard;
    private final L1IntRegistry<L1CastleGuardInstance> _allCastleGuard;

    // ─── 월드/AOI와 동적 충돌은 별도 서브시스템 ───
    private final CWorld _worldExecutor = new CWorld(this);

    WorldCollisionHash worldCollision() { return _worldExecutor.worldCollision(); }

    private int _weather = 4;

    private boolean _worldChatEnabled = true;

    private boolean _processingContributionTotal = false;

    static final int MAX_MAP_ID = 32768;

    private static volatile L1World _instance;

    private L1World() {
        _allPlayers = new ConcurrentHashMap<String, L1PcInstance>(8192);       
        _allPets = new L1IntRegistry<L1PetInstance>();      
        _allCompanions = new L1IntRegistry<L1CompanionInstance>(); 
        _allSummons = new L1IntRegistry<L1SummonInstance>();  
        _allSupports = new L1IntRegistry<L1SupportInstance>();
        
        _allClanWars = new L1IntRegistry<MJWar>();          
        _allClans = new L1IntRegistry<L1Clan>();              
        _allNpc = new L1IntRegistry<L1NpcInstance>();   
        _allShopNpc = new L1IntRegistry<L1NpcShopInstance>();
        _allitem = new L1IntRegistry<L1ItemInstance>();    

        _allGuard = new L1IntRegistry<L1GuardInstance>();   
        _allCastleGuard = new L1IntRegistry<L1CastleGuardInstance>();


        for (int mapid : MapsTable.getInstance().getMaps().keySet()) {
            if (mapid >= 0 && mapid <= MAX_MAP_ID && _visibleObjects[mapid] == null) {
                _visibleObjects[mapid] = new L1IntRegistry<L1Object>();
            }
        }

}

    public static L1World getInstance() {
        L1World instance = _instance;
        if (instance == null) {
            synchronized (L1World.class) {
                instance = _instance;
                if (instance == null) {
                    instance = new L1World();
                    _instance = instance;
                }
            }
        }
        return instance;
    }

    public void clear() {
        _instance = new L1World();
    }

    public long smartIdOf(int objectId) {
        return _worldExecutor.smartIdOf(objectId);
    }

    public long smartIdOf(L1Object object) {
        return _worldExecutor.smartIdOf(object);
    }

    public L1Object findObjectBySmartId(long smartId) {
        return _worldExecutor.findObjectBySmartId(smartId);
    }

    public void storeObject(L1Object object) {
        _worldExecutor.storeObject(object);
    }

    public void storeAndEnterWorld(L1Object object) {
        _worldExecutor.storeAndEnterWorld(object);
    }

    public void leaveAndRemoveObject(L1Object object) {
        _worldExecutor.leaveAndRemoveObject(object);
    }

    public void removeObject(L1Object object) {
        _worldExecutor.removeObject(object);
    }

    public L1Object findObject(int oID) {
        return _worldExecutor.findObject(oID);
    }

    public L1Object findObject(String name) {
        if (name == null) {
            return null;
        }
        return _allPlayers.get(name.toUpperCase());
    }

    private Collection<L1Object> _allValues;
    public Collection<L1Object> getObject() {
        Collection<L1Object> vs = _allValues;
        return (vs != null) ? vs : (_allValues = Collections.unmodifiableCollection(_worldExecutor.values()));
    }

    L1IntRegistry<L1Object> visibleMap(int mapId, boolean create) {
        if (mapId < 0 || mapId > MAX_MAP_ID) {
            return null;
        }

        @SuppressWarnings("unchecked")
        L1IntRegistry<L1Object> map =
                (L1IntRegistry<L1Object>) VISIBLE_MAP.getAcquire(_visibleObjects, mapId);
        if (map != null || !create) {
            return map;
        }

        synchronized (_visibleObjects) {
            @SuppressWarnings("unchecked")
            final L1IntRegistry<L1Object> existing =
                    (L1IntRegistry<L1Object>) VISIBLE_MAP.getAcquire(_visibleObjects, mapId);
            map = existing;
            if (map == null) {
                map = new L1IntRegistry<L1Object>();
                VISIBLE_MAP.setRelease(_visibleObjects, mapId, map);
            }
            return map;
        }
    }

    public L1GroundInventory findGroundInventory(int x, int y, short map) {
        return _worldExecutor.findGroundInventory(x, y, map);
    }

    public L1GroundInventory getInventory(int x, int y, short map) {
        return _worldExecutor.getGroundInventory(x, y, map);
    }

    public L1GroundInventory getInventory(L1Location loc) {
        return getInventory(loc.getX(), loc.getY(), (short) loc.getMap().getId());
    }

    public void addVisibleObject(L1Object object) {
        _worldExecutor.addVisibleObject(object);
    }

    public void removeVisibleObject(L1Object object) {
        _worldExecutor.removeVisibleObject(object);
    }

    public void moveVisibleObject(L1Object object, int newMap) {
        _worldExecutor.moveVisibleObject(object, newMap);
    }

    public void relocateVisibleObject(L1Object object, int x, int y, int mapId) {
        _worldExecutor.relocateVisibleObject(object, x, y, mapId);
    }

    public void moveTo(L1Object object, int oldMap, int oldX, int oldY) {
        _worldExecutor.moveTo(object, oldMap, oldX, oldY);
    }

    public void attack(L1PcInstance attacker, L1Character target) {
        if (attacker == null || target == null) {
            return;
        }
        target.onAction(attacker);
    }


    private static int[] createLineKeys(Point src, Point target) {
        final int x0 = src.getX();
        final int y0 = src.getY();
        final int x1 = target.getX();
        final int y1 = target.getY();
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);
        final int[] keys = new int[Math.max(dx, dy) + 1];

        int x = x0;
        int y = y0;
        final int sx = x1 >= x0 ? 1 : -1;
        final int sy = y1 >= y0 ? 1 : -1;
        int count = 0;

        if (dx >= dy) {
            int e = -dx;
            for (int i = 0; i <= dx; i++) {
                keys[count++] = (x << 16) + y;
                x += sx;
                e += dy << 1;
                if (e >= 0) {
                    y += sy;
                    e -= dx << 1;
                }
            }
        } else {
            int e = -dy;
            for (int i = 0; i <= dy; i++) {
                keys[count++] = (x << 16) + y;
                y += sy;
                e += dx << 1;
                if (e >= 0) {
                    x += sx;
                    e -= dy << 1;
                }
            }
        }
        return keys;
    }

    private static boolean containsLineKey(int[] keys, int key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<L1Object> getVisibleLineObjects(L1Object src, L1Object target) {
        int[] lineKeys = createLineKeys(src.getLocation(), target.getLocation());
        int map = target.getMapId();
        L1IntRegistry<L1Object> mapSrc = visibleMap(map, false);
        if(mapSrc == null || mapSrc.size() <= 0 || map > MAX_MAP_ID)
            return null;
        
        ArrayList<L1Object> result = new ArrayList<L1Object>(lineKeys.length);
        for (L1Object element : mapSrc.values()) {
            if (element == null || element.equals(src))
                continue;
            
            int key = (element.getX() << 16) + element.getY();
            if (containsLineKey(lineKeys, key))
                result.add(element);
        }
        return result;
    }

    public ArrayList<L1Object> getVisibleBoxObjects(L1Object object, int heading, int width, int height) {
        int x = object.getX(), y = object.getY(), map = object.getMapId();
        L1IntRegistry<L1Object> mapSrc = visibleMap(map, false);
        if(mapSrc == null || mapSrc.size() <= 0 || map > MAX_MAP_ID)
            return null;
        
        L1Location location = object.getLocation();
        ArrayList<L1Object> result = new ArrayList<L1Object>(width * height);
        int headingRotate[] = { 6, 7, 0, 1, 2, 3, 4, 5 };
        double cosSita = Math.cos(headingRotate[heading] * Math.PI / 4);
        double sinSita = Math.sin(headingRotate[heading] * Math.PI / 4);

        for (L1Object element : mapSrc.values()) {
            if (element == null || element.equals(object) || map != element.getMapId()) {
                continue;
            }
            if (location.isSamePoint(element.getLocation())) {
                result.add(element);
                continue;
            }
            int distance = location.getTileLineDistance(element.getLocation());
            if (distance > height && distance > width) {
                continue;
            }

            int x1 = element.getX() - x;
            int y1 = element.getY() - y;
            int rotX = (int) Math.round(x1 * cosSita + y1 * sinSita);
            int rotY = (int) Math.round(-x1 * sinSita + y1 * cosSita);

            if (rotX > 0 && distance <= height && rotY >= -width && rotY <= width)
                result.add(element);
        }
        return result;
    }
    
    public final Collection<L1PcInstance> getVisiblePlayers(int map_id){
        List<L1PcInstance> result = new ArrayList<L1PcInstance>();
        L1IntRegistry<L1Object> mapSrc = visibleMap(map_id, false);
        if (mapSrc != null) {
            for (L1Object obj : mapSrc.values()) {
                if (obj instanceof L1PcInstance) {
                    result.add((L1PcInstance) obj);
                }
            }
        }
        return result;
    }
    
    private static final class VisibleScratch {
        @SuppressWarnings("unchecked")
        ArrayList<L1PcInstance>[] lists = (ArrayList<L1PcInstance>[]) new ArrayList<?>[4];
        int depth;
    }

    private static final ThreadLocal<VisibleScratch> VISIBLE_SCRATCH =
            ThreadLocal.withInitial(VisibleScratch::new);

    /**
     * Borrow a thread-confined AOI snapshot for a synchronous broadcast.
     * Always pair with releaseVisiblePlayerFast() in finally. Nested
     * broadcasts use distinct lists, and each worker reuses its own lists.
     */
    public List<L1PcInstance> borrowVisiblePlayerFast(L1Object object) {
        final VisibleScratch scratch = VISIBLE_SCRATCH.get();
        final int depth = scratch.depth;
        if (depth == scratch.lists.length) {
            scratch.lists = java.util.Arrays.copyOf(scratch.lists, depth << 1);
        }
        ArrayList<L1PcInstance> list = scratch.lists[depth];
        if (list == null) {
            list = new ArrayList<>(16);
            scratch.lists[depth] = list;
        }
        scratch.depth = depth + 1;
        try {
            _worldExecutor.getClusterGrid().queryScreenPlayers(object, list);
            return list;
        } catch (RuntimeException | Error failure) {
            list.clear();
            scratch.depth = depth;
            throw failure;
        }
    }

    public void releaseVisiblePlayerFast(List<L1PcInstance> list) {
        final VisibleScratch scratch = VISIBLE_SCRATCH.get();
        final int depth = scratch.depth - 1;
        if (depth < 0 || scratch.lists[depth] != list) {
            throw new IllegalStateException("AOI snapshot release out of order");
        }
        scratch.depth = depth;
        // Do not retain exceptionally large AOI arrays for the worker lifetime.
        if (list.size() > 512) {
            scratch.lists[depth] = null;
        }
        list.clear();
    }

    public List<L1PcInstance> getVisiblePlayerFast(L1Object object) {
        return _worldExecutor.getClusterGrid().queryScreenPlayers(object);
    }
    
    public void broadcast_map(int map_id, String message){
        for (L1PcInstance pc : getVisiblePlayers(map_id)) {
            if (pc != null) SC_SystemMessage.send(pc, message);
        }
    }
    
    public void broadcast_map(int map_id, ServerPacket pck){
        broadcast_map(map_id, pck, true);
    }
    
    public void broadcast_map(int map_id, ServerPacket pck, boolean is_clear){
        for(L1PcInstance pc : getVisiblePlayers(map_id)){
            if(pc != null)
                pc.sendPackets(pck, false);
        }
    }
    
	public int getAllPlayersSize(){
		return _allPlayers.size();
	}
	
    
    public void broadcast_map(int map_id, ServerPacket[] pcks){
        broadcast_map(map_id, pcks, true);
    }
    
    public void broadcast_map(int map_id, ServerPacket[] pcks, boolean is_clear){
        for(L1PcInstance pc : getVisiblePlayers(map_id)){
            if(pc != null)
                pc.sendPackets(pcks, false);
        }
    }
    
    public ArrayList<L1Object> getVisibleObjects(L1Object object) {
        return getVisibleObjects(object, -1);
    }
    
    public ArrayList<L1Object> getVisibleObjects(L1Object object, int radius) {
        L1Map map = object.getMap();
        Point pt = object.getLocation();
        int mapId = object.getMapId();
        ArrayList<L1Object> result = null;

        if(radius < 0)
            result = new ArrayList<L1Object>(19 * 8);
        else if(radius == 0)
            result = new ArrayList<L1Object>(2);
        else
            result = new ArrayList<L1Object>(radius * 8);
        
        if (map.getId() <= MAX_MAP_ID) {
            L1IntRegistry<L1Object> mapSrc = visibleMap(map.getId(), false);
            if (mapSrc != null) {
                final int selfId = object.getId();

                for (L1Object element : mapSrc.values()) {
                    if (element == null || element.getId() == selfId) {
                        continue;
                    }
                    if (element.equals(object)) {
                        continue;
                    }
                    
                    if(mapId != element.getMapId()) {
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
        }
        return result;
    }

    public ArrayList<L1Object> getVisiblePoint(L1Location loc, int radius) {
        int mapId = loc.getMapId();
        L1IntRegistry<L1Object> mapSrc = visibleMap(mapId, false);
        if(mapSrc == null || mapSrc.size() <= 0 || mapId > MAX_MAP_ID)
            return new ArrayList<L1Object>();
        ArrayList<L1Object> result = new ArrayList<L1Object>(radius * 8);
        for (L1Object element : mapSrc.values()) {
            if (element == null || mapId != element.getMapId()) 
                continue;

            if (loc.getTileLineDistance(element.getLocation()) <= radius)
                result.add(element);
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
        if(radius < 0)
            result = new ArrayList<L1PcInstance>(19 * 4);
        else if(radius == 0)
            result = new ArrayList<L1PcInstance>(2);
        else
            result = new ArrayList<L1PcInstance>(radius * 4);

        final int selfId = object.getId();

        for (L1PcInstance element : pc) {
            if (element == null || element.getId() == selfId || map != element.getMapId())
                continue;

            if (radius == -1) {
                if (pt.isInScreen(element.getLocation()))
                    result.add(element);
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

    public ArrayList<L1Object> getVisiblePlayer(L1Object object, int radius, int i) {
        int map = object.getMapId();
        Point pt = object.getLocation();
        Collection<L1Object> npc = _worldExecutor.values();
        ArrayList<L1Object> result2 = null;
        if(radius < 0){
            result2 = new ArrayList<L1Object>(19 * 4);
        }else if(radius == 0){
            result2 = new ArrayList<L1Object>(2);
        }else{
            result2 = new ArrayList<L1Object>(radius * 4);
        }
        for (L1Object element : npc) {
            if ((element == null) || (element.equals(object)) || (map != element.getMapId()))
                continue;
            if (pt.getTileLineDistance(element.getLocation()) <= radius) {
                result2.add(element);
            }
        }
        return result2;
    }
    
    public boolean hasRecognizePlayerFast(L1Object obj) {
        return _worldExecutor.getClusterGrid().hasNearbyPlayer(obj, -1);
    }

    public ArrayList<L1PcInstance> getRecognizePlayer(L1Object object) {
        return getVisiblePlayer(object, Config.PC_RECOGNIZE_RANGE);
    }

    public L1PcInstance[] getAllPlayers3() {
        return _allPlayers.values().toArray(new L1PcInstance[_allPlayers.size()]);
    }

    private Collection<L1PcInstance> _allPlayerValues;

    public Collection<L1PcInstance> getAllPlayers() {
        Collection<L1PcInstance> vs = _allPlayerValues;
        return (vs != null) ? vs : (_allPlayerValues = Collections.unmodifiableCollection(_allPlayers.values()));
    }
    
    public Stream<L1PcInstance> getAllPlayerStream(){
        Collection<L1PcInstance> col = getAllPlayers();
        return col.size() > 1024 ? col.parallelStream() : col.stream();
    }
    
    public ArrayList<L1PcInstance> createAllPlayerArrayList(){
        return new ArrayList<L1PcInstance>(_allPlayers.values());
    }

    public Collection<L1NpcShopInstance> getAllNpcShop() {
        return Collections.unmodifiableCollection(_allShopNpc.values());
    }

    public Collection<L1NpcInstance> getAllNpc() {
        return Collections.unmodifiableCollection(_allNpc.values());
    }

    public Collection<L1Object> getAllObj() {
        return Collections.unmodifiableCollection(_worldExecutor.values());
    }

    public Collection<L1GuardInstance> getAllGuard() {
        return Collections.unmodifiableCollection(_allGuard.values());
    }

    public Collection<L1CastleGuardInstance> getAllCastleGuard() {
        return Collections.unmodifiableCollection(_allCastleGuard.values());
    }

    public L1PcInstance getPlayer(String name) {
        if (null == name)
            return null;
        return _allPlayers.get(name.toUpperCase());
    }

    public L1NpcShopInstance getShopNpc(String name) {
        Collection<L1NpcShopInstance> npc = getAllShopNpc();
        int nl = name.length();
        for (L1NpcShopInstance each : npc) {
            if (each == null)
                continue;
            
            String s = each.getName();
            if(s.length() != nl)
                continue;
            
            if (s.equalsIgnoreCase(name)) {
                return each;
            }
        }
        return null;
    }

    private Collection<L1NpcShopInstance> _allShopNpcValues;

    public Collection<L1NpcShopInstance> getAllShopNpc() {
        Collection<L1NpcShopInstance> vs = _allShopNpcValues;
        return (vs != null) ? vs : (_allShopNpcValues = Collections.unmodifiableCollection(_allShopNpc.values()));
    }

    private Collection<L1PetInstance> _allPetValues;

    public Collection<L1PetInstance> getAllPets() {
        Collection<L1PetInstance> vs = _allPetValues;
        return (vs != null) ? vs : (_allPetValues = Collections.unmodifiableCollection(_allPets.values()));
    }
    
    private Collection<L1CompanionInstance> _allCompanionValues;
    public Collection<L1CompanionInstance> getAllCompanions() {
        Collection<L1CompanionInstance> vs = _allCompanionValues;
        return (vs != null) ? vs : (_allCompanionValues = Collections.unmodifiableCollection(_allCompanions.values()));
    }

    public boolean contains_companion(int companion_id){
        return _allCompanions.containsKey(companion_id);
    }
    
    private Collection<L1SummonInstance> _allSummonValues;

    public Collection<L1SummonInstance> getAllSummons() {
        Collection<L1SummonInstance> vs = _allSummonValues;
        return (vs != null) ? vs : (_allSummonValues = Collections.unmodifiableCollection(_allSummons.values()));
    }

    public final L1IntRegistry<L1Object> getVisibleObjects(int mapId) {
        return visibleMap(mapId, false);
    }

    // ─── 클러스터 접근자 ───
    public LocalWorldMap getClusterGrid() {
        return _worldExecutor.getClusterGrid();
    }

    public void addWar(MJWar war) {
        _allClanWars.put(war.getId(), war);
    }

    public MJWar removeWar(MJWar war) {
        return _allClanWars.remove(war.getId());
    }
    
    public Stream<MJWar> createWarStream(){
        return _allClanWars.size() <= 0 ? null : _allClanWars.values().stream();
    }

    public void storeClan(L1Clan clan) {
        _allClans.put(clan.getClanId(), clan);
    }

    public void removeClan(L1Clan clan) {
        _allClans.remove(clan.getClanId());
    }

    public void clearClan() {
        _allClans.clear();
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

    public L1Clan getClan(int clanId) {
        return _allClans.get(clanId);
    }

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

    public L1Object[] getObject2() {
        return _worldExecutor.values().toArray(new L1Object[_worldExecutor.objectCount()]);
    }

    public void broadcastPacketToAll(ServerPacket[] pcks) {
        for (L1PcInstance pc : getAllPlayers()) {
            if(pc == null)
                continue;
            
            for(ServerPacket pck : pcks)
                pc.sendPackets(pck, false);
        }
    }
    
    public void broadcastPacketToAll(ServerPacket[] pcks, boolean isClear) {
        for (L1PcInstance pc : getAllPlayers()) {
            if(pc == null)
                continue;
            
            for(ServerPacket pck : pcks)
                pc.sendPackets(pck, false);
        }
    }
    
    public void broadcastPacketToAll(WritablePacket<CIOClient>[] packets) {
        if (packets == null) return;
        for (L1PcInstance pc : getAllPlayers()) {
            if (pc != null) pc.sendPackets(packets, false);
        }
    }

    public void broadcastPacketToAll(String message) {
        SC_SystemMessage.broadcast(message);
    }
    
    public void broadcastPacketToAll(ServerPacket packet) {
        for (L1PcInstance pc : getAllPlayers()) {
            if (pc != null)
                pc.sendPackets(packet);
        }
    }

    public void broadcastPacketToAll(ServerPacket packet, boolean clear) {
        Collection<L1PcInstance> pclist = getAllPlayers();
        _log.finest("players to notify : " + pclist.size());
        for (L1PcInstance pc : pclist) {
            if (pc != null)
                pc.sendPackets(packet, false);
        }
    }

    public void getMapObject(int mapid) {
        L1IntRegistry<L1Object> mapSrc = visibleMap(mapid, false);
        if (mapSrc == null) return;
        for (L1Object obj : mapSrc.values()) {
            if (obj instanceof L1MonsterInstance) {
                L1MonsterInstance mon = (L1MonsterInstance) obj;
                if (mon.getHiddenStatus() != 0 || mon.isDead())
                    continue;

                if (mon.getMapId() == mapid)
                    mon.re();
            }
        }
    }

    public void broadcastServerMessage(String message) {
        SC_SystemMessage.broadcast(message);
    }

    // Legacy callers pass template IDs, not world object IDs.
    public L1NpcInstance findNpc(int id) {
        return findNpcByNpcId(id);
    }

    public L1NpcInstance findNpcByNpcId(int npcId) {
        for (L1NpcInstance npc : _allNpc.values()) {
            if (npc != null && npc.getNpcTemplate() != null
                    && npc.getNpcTemplate().get_npcId() == npcId) return npc;
        }
        return null;
    }
    
    public L1NpcInstance findNpc(String name){
        int nl = name.length();
        for (L1NpcInstance each : getAllNpc()) {
            String s = each.getName();
            if(s.length() != nl)
                continue;
            
            if (s.equalsIgnoreCase(name)) {
                return each;
            }
        }
        return null;
    }

    // Called only under CWorld's canonical object-ID writer boundary.
    void registerWorldItem(L1ItemInstance item) {
        _allitem.put(item.getId(), item);
    }

    void unregisterWorldItem(L1ItemInstance item) {
        _allitem.remove(item.getId(), item);
    }

    private Collection<L1ItemInstance> _allItemValues;

    public Collection<L1ItemInstance> getAllItem() {
        Collection<L1ItemInstance> vs = _allItemValues;
        return (vs != null) ? vs : (_allItemValues = Collections.unmodifiableCollection(_allitem.values()));
    }

    public L1PcInstance findpc(String name) {
        if (name == null) {
            return null;
        }
        return _allPlayers.get(name.toUpperCase());
    }
    
    public Stream<L1Object> createVisibleObjectsStream(int mapId){
        L1IntRegistry<L1Object> mapSrc = visibleMap(mapId, false);
        if (mapSrc == null) return Stream.empty();
        Collection<L1Object> col = mapSrc.values();
        return col.size() > 1024 ? col.parallelStream() : col.stream();
    }
    
    public void fillVisibleObjectsLimit(
    		L1Object origin,
    		int radius,
    		int limit,
    		List<L1Object> out) {

    	if (out == null) {
    		return;
    	}
    	out.clear();
    	if (origin == null || limit <= 0) {
    		return;
    	}

    	getClusterGrid().queryNearby(origin, radius, out);

    	// limit 초과분 제거
    	for (int i = out.size() - 1; i >= limit; i--) {
    		out.remove(i);
    	}
    }
  
    
    public boolean AsyncStoreObject(L1Object object) {
        return _worldExecutor.AsyncStoreObject(object);
    }

    public boolean AsyncRemoveObject(L1Object object) {
        return _worldExecutor.AsyncRemoveObject(object);
    }

    public boolean AsyncStoreAndEnterWorld(L1Object object) {
        return _worldExecutor.AsyncStoreAndEnterWorld(object);
    }

    public boolean AsyncEnterWorld(L1Object object) {
        return _worldExecutor.AsyncEnterWorld(object);
    }

    public boolean AsyncLeaveWorld(L1Object object) {
        return _worldExecutor.AsyncLeaveWorld(object);
    }

    public boolean AsyncMove(L1Object object, int x, int y, int mapId) {
        return _worldExecutor.AsyncMove(object, x, y, mapId);
    }

    public boolean AsyncItemTimer(L1ItemInstance item, int timerId, int generation) {
        return _worldExecutor.AsyncItemTimer(item, timerId, generation);
    }

    public void enterWorld(L1Object object) {
        addVisibleObject(object);
    }

    public void leaveWorld(L1Object object) {
        removeVisibleObject(object);
    }

}
