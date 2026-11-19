package l1j.server.server.model.map;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.types.Point;

@SuppressWarnings("preview")
public class L1V1Map extends L1Map {

	private int _mapId;
	private int _baseMapId;
	private int _worldTopLeftX;
	private int _worldTopLeftY;
	private int _worldBottomRightX;
	private int _worldBottomRightY;

	// 타일 데이터: [x][y] 순서를 그대로 1차원 native memory로 평탄화한다.
	// Arena.ofAuto()는 여러 스레드에서 접근 가능하며 맵 객체가 GC되면 native memory도 회수된다.
	private final Arena _mapArena = Arena.ofAuto();
	private MemorySegment _map;
	private int _mapWidth;
	private int _mapHeight;

	private volatile MemorySegment _closedDoorCache;

	private boolean _isUnderwater;
	private boolean _isMarkable;
	private boolean _isTeleportable;
	private boolean _isEscapable;
	private boolean _isUseResurrection;
	private boolean _isUsePainwand;
	private boolean _isEnabledDeathPenalty;
	private boolean _isTakePets;
	private boolean _isRecallPets;
	private boolean _isUsableItem;
	private boolean _isUsableSkill;
	private boolean _is_ruler;
	private static final byte BITFLAG_IS_IMPASSABLE = (byte) 128; // 1000 0000

	protected L1V1Map() {  }

	public L1V1Map(int mapId, byte map[][], int worldTopLeftX,
			int worldTopLeftY, boolean underwater, boolean markable,
			boolean teleportable, boolean escapable, boolean useResurrection,
			boolean usePainwand, boolean enabledDeathPenalty, boolean takePets,
			boolean recallPets, boolean usableItem, boolean usableSkill, boolean isRuler) {
		_mapId = mapId;
		_baseMapId = mapId;

		if (map == null || map.length == 0 || map[0] == null || map[0].length == 0) {
			throw new IllegalArgumentException("map is null or empty");
		}

		_mapWidth = map.length;
		_mapHeight = map[0].length;
		_map = _mapArena.allocate((long) _mapWidth * _mapHeight, 1);

		for (int ix = 0; ix < _mapWidth; ix++) {
			final byte[] column = map[ix];
			if (column == null || column.length != _mapHeight) {
				throw new IllegalArgumentException("map is not rectangular: x=" + ix);
			}
			MemorySegment.copy(column, 0, _map, ValueLayout.JAVA_BYTE,
					(long) ix * _mapHeight, _mapHeight);
		}

		_worldTopLeftX = worldTopLeftX;
		_worldTopLeftY = worldTopLeftY;

		_worldBottomRightX = worldTopLeftX + _mapWidth - 1;
		_worldBottomRightY = worldTopLeftY + _mapHeight - 1;

		_isUnderwater = underwater;
		_isMarkable = markable;
		_isTeleportable = teleportable;
		_isEscapable = escapable;
		_isUseResurrection = useResurrection;
		_isUsePainwand = usePainwand;
		_isEnabledDeathPenalty = enabledDeathPenalty;
		_isTakePets = takePets;
		_isRecallPets = recallPets;
		_isUsableItem = usableItem;
		_isUsableSkill = usableSkill;
		_is_ruler = isRuler;
		initDoorCache();
	}

	/**
	 * CachedMapReader용 평탄 타일 생성자.
	 * flatMap의 배치는 기존 byte[x][y]를 x-major로 평탄화한 순서다.
	 */
	public L1V1Map(int mapId, byte[] flatMap, int mapWidth, int mapHeight,
			int worldTopLeftX, int worldTopLeftY,
			boolean underwater, boolean markable,
			boolean teleportable, boolean escapable, boolean useResurrection,
			boolean usePainwand, boolean enabledDeathPenalty, boolean takePets,
			boolean recallPets, boolean usableItem, boolean usableSkill, boolean isRuler) {

		if (flatMap == null || mapWidth <= 0 || mapHeight <= 0) {
			throw new IllegalArgumentException("flatMap/size invalid");
		}

		final long total = (long) mapWidth * mapHeight;
		if (total != flatMap.length) {
			throw new IllegalArgumentException(
					"flatMap size mismatch: expected=" + total + ", actual=" + flatMap.length);
		}

		_mapId = mapId;
		_baseMapId = mapId;
		_mapWidth = mapWidth;
		_mapHeight = mapHeight;
		_map = _mapArena.allocate(total, 1);
		MemorySegment.copy(flatMap, 0, _map, ValueLayout.JAVA_BYTE, 0, flatMap.length);

		_worldTopLeftX = worldTopLeftX;
		_worldTopLeftY = worldTopLeftY;
		_worldBottomRightX = worldTopLeftX + _mapWidth - 1;
		_worldBottomRightY = worldTopLeftY + _mapHeight - 1;

		_isUnderwater = underwater;
		_isMarkable = markable;
		_isTeleportable = teleportable;
		_isEscapable = escapable;
		_isUseResurrection = useResurrection;
		_isUsePainwand = usePainwand;
		_isEnabledDeathPenalty = enabledDeathPenalty;
		_isTakePets = takePets;
		_isRecallPets = recallPets;
		_isUsableItem = usableItem;
		_isUsableSkill = usableSkill;
		_is_ruler = isRuler;
		initDoorCache();
	}

	public L1V1Map(L1V1Map map) {
		_mapId = map._mapId;
		_baseMapId = map._baseMapId;

		_mapWidth = map._mapWidth;
		_mapHeight = map._mapHeight;
		_map = _mapArena.allocate((long) _mapWidth * _mapHeight, 1);
		_map.copyFrom(map._map);

		_worldTopLeftX = map._worldTopLeftX;
		_worldTopLeftY = map._worldTopLeftY;
		_worldBottomRightX = map._worldBottomRightX;
		_worldBottomRightY = map._worldBottomRightY;
		initDoorCache();

	}

	public L1V1Map clone(int id){//레이드
		L1V1Map map = new L1V1Map(this);
		map._mapId = id;
		map._isUnderwater = _isUnderwater;
		map._isMarkable = _isMarkable;
		map._isTeleportable = _isTeleportable;
		map._isEscapable = _isEscapable;
		map._isUseResurrection = _isUseResurrection;
		map._isUsePainwand = _isUsePainwand;
		map._isEnabledDeathPenalty = _isEnabledDeathPenalty;
		map._isTakePets = _isTakePets;
		map._isRecallPets = _isRecallPets;
		map._isUsableItem = _isUsableItem;
		map._isUsableSkill = _isUsableSkill;
		map._is_ruler = _is_ruler;
		return map;
	}

	private long tileOffset(int ix, int iy) {
		return (long) ix * _mapHeight + iy;
	}

	private int rawTileLocal(int ix, int iy) {
		return _map.get(ValueLayout.JAVA_BYTE, tileOffset(ix, iy));
	}

	private void rawTileLocal(int ix, int iy, byte value) {
		_map.set(ValueLayout.JAVA_BYTE, tileOffset(ix, iy), value);
	}

	public int accessTile(int x, int y) {
		if (!isInMap(x, y)) {
			return 0;
		}

		return rawTileLocal(x - _worldTopLeftX, y - _worldTopLeftY);
	}

	private int accessOriginalTile(int x, int y) {
		return accessTile(x, y) & (~BITFLAG_IS_IMPASSABLE);
	}

	private void setTile(int x, int y, int tile) {
		if (!isInMap(x, y)) {
			return;
		}
		rawTileLocal(x - _worldTopLeftX, y - _worldTopLeftY, (byte) tile);
	}

	/**
	 * 기존 외부 호출부 호환용 스냅샷.
	 * 실제 backing storage는 MemorySegment이며, 이 메서드는 새 byte[][]를 만들어 반환한다.
	 */
	public byte[][] getRawTiles() {
		final byte[][] raw = new byte[_mapWidth][_mapHeight];
		for (int ix = 0; ix < _mapWidth; ix++) {
			MemorySegment.copy(_map, ValueLayout.JAVA_BYTE,
					(long) ix * _mapHeight, raw[ix], 0, _mapHeight);
		}
		return raw;
	}

	/**
	 * 캐시 저장용: 2차원 배열을 만들지 않고 평탄 타일 데이터를 바로 복사한다.
	 */
	public void copyRawTilesTo(byte[] dst) {
		final int total = Math.multiplyExact(_mapWidth, _mapHeight);
		if (dst == null || dst.length < total) {
			throw new IllegalArgumentException("destination too small: required=" + total);
		}
		MemorySegment.copy(_map, ValueLayout.JAVA_BYTE, 0, dst, 0, total);
	}

	@Override
	public L1V1Map copyMap(int newMapId){//임시 추가
		return clone(newMapId);
	}

	@Override
	public int getId() {
		return _mapId;
	}

	@Override
	public int getX() {
		return _worldTopLeftX;
	}

	@Override
	public int getY() {
		return _worldTopLeftY;
	}

	@Override
	public int getWidth() {
		return _worldBottomRightX - _worldTopLeftX + 1;
	}

	@Override
	public int getHeight() {
		// TODO Auto-generated method stub
		return _worldBottomRightY - _worldTopLeftY + 1;
	}

	@Override
	public int getTile(int x, int y) {
		short tile = (short) rawTileLocal(x - _worldTopLeftX, y - _worldTopLeftY);
		if (0 != (tile & BITFLAG_IS_IMPASSABLE)) {
			return 300;
		}
		return accessOriginalTile(x, y);
	}

	@Override
	public int getOriginalTile(int x, int y) {
		return accessOriginalTile(x, y);
	}

	@Override
	public boolean isInMap(Point pt) {
		return isInMap(pt.getX(), pt.getY());
	}

	@Override
	public boolean isInMap(int x, int y) {
		if (_mapId == 4
				&& (x < 32520 || y < 32070 || (y < 32190 && x < 33950))) {
			return false;
		}
		return (_worldTopLeftX <= x && x <= _worldBottomRightX
				&& _worldTopLeftY <= y && y <= _worldBottomRightY);
	}

	@Override
	public boolean isPassable(Point pt) {
		return isPassable(pt.getX(), pt.getY());
	}

	@Override
	public boolean isPassable(int x, int y) {
		return isPassable(x, y - 1, 4) || isPassable(x + 1, y, 6)
				|| isPassable(x, y + 1, 0) || isPassable(x - 1, y, 2);
	}

	@Override
	public boolean isPassable(Point pt, int heading) {
		return isPassable(pt.getX(), pt.getY(), heading);
	}

	@Override
	public boolean isUserPassable(int x, int y, int heading)
	{
		int tile1 = accessTile(x, y);
		int tile2;

		switch(heading){
		case 0: tile2 = accessTile(x, y - 1); break;
		case 1: tile2 = accessTile(x + 1, y - 1); break;
		case 2: tile2 = accessTile(x + 1, y); break;
		case 3: tile2 = accessTile(x + 1, y + 1); break;
		case 4: tile2 = accessTile(x, y + 1); break;
		case 5: tile2 = accessTile(x - 1, y + 1); break;
		case 6: tile2 = accessTile(x - 1, y); break;
		case 7: tile2 = accessTile(x - 1, y - 1); 	break;
		default: return false;
		}

		switch(heading){
		case 0:{ return (tile1 & 0x02) == 0x02; }
		case 1:{
			int tile3 = accessTile(x, y - 1);
			int tile4 = accessTile(x + 1, y);
			return (tile1 & 0x02) == 0x02 && (tile3 & 0x01) == 0x01 || (tile1 & 0x01) == 0x01 && (tile4 & 0x02) == 0x02; }
		case 2:{ return (tile1 & 0x01) == 0x01; }
		case 3:{
			int tile3 = accessTile(x, y + 1);
			return (tile3 & 0x03) == 0x03 || (tile1 & 0x01) == 0x01 && (tile2 & 0x02) == 0x02;
		}
		case 4:{ return (tile2 & 0x02) == 0x02; }
		case 5:{
			int tile3 = accessTile(x, y + 1);
			int tile4 = accessTile(x - 1, y);
			return (tile2 & 0x01) == 0x01 && ( tile3 & 0x02 ) == 0x02 || (tile2 & 0x02) == 0x02 && (tile4 & 0x01) == 0x01; }
		case 6:{ return (tile2 & 0x01) == 0x01; }
		case 7:{
			int tile3 = accessTile(x - 1, y);
			return (tile3 & 0x03) == 0x03 || (tile1 & 0x02) == 0x02 && (tile2 & 0x01) == 0x01;
		}
		default:break;
		}

		return false;		
	}

	@Override
	public boolean isPassable(int x, int y, int heading) {
		int tile1 = accessTile(x, y);
		int tile2;

		switch(heading){
		case 0: tile2 = accessTile(x, y - 1); break;
		case 1: tile2 = accessTile(x + 1, y - 1); break;
		case 2: tile2 = accessTile(x + 1, y); break;
		case 3: tile2 = accessTile(x + 1, y + 1); break;
		case 4: tile2 = accessTile(x, y + 1); break;
		case 5: tile2 = accessTile(x - 1, y + 1); break;
		case 6: tile2 = accessTile(x - 1, y); break;
		case 7: tile2 = accessTile(x - 1, y - 1); 	break;
		default: return false;
		}

		if ((tile2 & BITFLAG_IS_IMPASSABLE) == BITFLAG_IS_IMPASSABLE) {
			return false;
		}

		switch(heading){
		case 0:{ return (tile1 & 0x02) == 0x02; }
		case 1:{
			int tile3 = accessTile(x, y - 1);
			int tile4 = accessTile(x + 1, y);
			return (tile1 & 0x02) == 0x02 && (tile3 & 0x01) == 0x01 || (tile1 & 0x01) == 0x01 && (tile4 & 0x02) == 0x02; }
		case 2:{ return (tile1 & 0x01) == 0x01; }
		case 3:{
			int tile3 = accessTile(x, y + 1);
			return (tile3 & 0x03) == 0x03 || (tile1 & 0x01) == 0x01 && (tile2 & 0x02) == 0x02;
		}
		case 4:{ return (tile2 & 0x02) == 0x02; }
		case 5:{
			int tile3 = accessTile(x, y + 1);
			int tile4 = accessTile(x - 1, y);
			return (tile2 & 0x01) == 0x01 && ( tile3 & 0x02 ) == 0x02 || (tile2 & 0x02) == 0x02 && (tile4 & 0x01) == 0x01; }
		case 6:{ return (tile2 & 0x01) == 0x01; }
		case 7:{
			int tile3 = accessTile(x - 1, y);
			return (tile3 & 0x03) == 0x03 || (tile1 & 0x02) == 0x02 && (tile2 & 0x01) == 0x01;
		}
		default:break;
		}

		return false;
	}

	@Override
	public void setPassable(Point pt, boolean isPassable) {
		setPassable(pt.getX(), pt.getY(), isPassable);
	}

	@Override
	public void setPassable(int x, int y, boolean isPassable) {
		if (isPassable) {
			setTile(x, y, (short) (accessTile(x, y) & (~BITFLAG_IS_IMPASSABLE)));
		} else {
			setTile(x, y, (short) (accessTile(x, y) | BITFLAG_IS_IMPASSABLE));
		}
	}

	@Override
	public boolean isSafetyZone(Point pt) {
		return isSafetyZone(pt.getX(), pt.getY());
	}

	@Override
	public boolean isSafetyZone(int x, int y) {
		int tile = accessOriginalTile(x, y);

		return (tile & 0x30) == 0x10;
	}

	@Override
	public boolean isCombatZone(Point pt) {
		return isCombatZone(pt.getX(), pt.getY());
	}

	@Override
	public boolean isCombatZone(int x, int y) {
		int tile = accessOriginalTile(x, y);

		return (tile & 0x30) == 0x20;
	}

	@Override
	public boolean isNormalZone(Point pt) {
		return isNormalZone(pt.getX(), pt.getY());
	}

	@Override
	public boolean isNormalZone(int x, int y) {
		int tile = accessOriginalTile(x, y);
		return (tile & 0x30) == 0x00;
	}

	@Override
	public boolean isArrowPassable(Point pt) {
		return isArrowPassable(pt.getX(), pt.getY());
	}

	@Override
	public boolean isArrowPassable(int x, int y) {
		return (accessOriginalTile(x, y) & 0x0e) != 0;
	}

	@Override
	public boolean isArrowPassable(Point pt, int heading) {
		return isArrowPassable(pt.getX(), pt.getY(), heading);
	}

	@Override
	public boolean isArrowPassable(int x, int y, int heading) {
		int tile1 = accessTile(x, y);
		int tile2;
		int newX;
		int newY;

		switch(heading){
		case 0: tile2 = accessTile(x, y - 1); newX = x; newY = y - 1; break;
		case 1: tile2 = accessTile(x + 1, y - 1); newX = x + 1; newY = y - 1; break;
		case 2: tile2 = accessTile(x + 1, y); newX = x + 1; newY = y; break;
		case 3: tile2 = accessTile(x + 1, y + 1); newX = x + 1; newY = y + 1; break;
		case 4: tile2 = accessTile(x, y + 1); newX = x; newY = y + 1; break;
		case 5: tile2 = accessTile(x - 1, y + 1); newX = x - 1; newY = y + 1; break;
		case 6: tile2 = accessTile(x - 1, y); newX = x - 1; newY = y; break;
		case 7: tile2 = accessTile(x - 1, y - 1); newX = x - 1; newY = y - 1; break;
		default: return false;
		}

		if (isClosedDoorCached(newX, newY)) {
			return false;
		}

		switch(heading){
		case 0:{ return (tile1 & 0x08) == 0x08; }
		case 1:{ int tile3 = accessTile(x, y - 1); int tile4 = accessTile(x + 1, y); return (tile3 & 0x04) == 0x04 || (tile4 & 0x08) == 0x08; }
		case 2:{ return (tile1 & 0x04) == 0x04; }
		case 3:{ int tile3 = accessTile(x, y + 1); return (tile3 & 0x04) == 0x04; }
		case 4:{ return (tile2 & 0x08) == 0x08; }
		case 5:{ return (tile2 & 0x04) == 0x04 || (tile2 & 0x08) == 0x08; }
		case 6:{ return (tile2 & 0x04) == 0x04; }
		case 7:{ int tile3 = accessTile(x - 1, y); return (tile3 & 0x08) == 0x08; }
		default: break;
		}
		return false;
	}

	@Override
	public boolean isUnderwater() {
		return _isUnderwater;
	}

	@Override
	public boolean isMarkable() {
		return _isMarkable;
	}

	@Override
	public boolean isTeleportable() {
		return _isTeleportable;
	}

	@Override
	public boolean isEscapable() {
		return _isEscapable;
	}

	@Override
	public boolean isUseResurrection() {
		return _isUseResurrection;
	}

	@Override
	public boolean isUsePainwand() {
		return _isUsePainwand;
	}

	@Override
	public boolean isEnabledDeathPenalty() {
		return _isEnabledDeathPenalty;
	}

	@Override
	public boolean isTakePets() {
		return _isTakePets;
	}

	@Override
	public boolean isRecallPets() {
		return _isRecallPets;
	}

	@Override
	public boolean isUsableItem() {
		return _isUsableItem;
	}

	@Override
	public boolean isUsableSkill() {
		return _isUsableSkill;
	}

	@Override
	public boolean isRuler(){
		return _is_ruler;
	}

	@Override
	public boolean isFishingZone(int x, int y) {
		return accessOriginalTile(x, y) == 16;
	}

	@Override
	public boolean isExistDoor(int x, int y) {
		List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
		for (L1DoorInstance door : doors) {
			if (_mapId != door.getMapId()) {
				continue;
			}
			if (door.getOpenStatus() == ActionCodes.ACTION_Open) {
				continue;
			}
			if (door.isDead()) {
				continue;
			}
			int leftEdgeLocation = door.getLeftEdgeLocation();
			int rightEdgeLocation = door.getRightEdgeLocation();
			int size = rightEdgeLocation - leftEdgeLocation;
			if (size == 0) {
				if (x == door.getX() && y == door.getY()) {
					return true;
				}
			} else {
				if (door.getDirection() == 0) { 
					for (int doorX = leftEdgeLocation;
							doorX <= rightEdgeLocation; doorX++) {
						if (x == doorX && y == door.getY()) {
							return true;
						}
					}
				} else {
					for (int doorY = leftEdgeLocation;
							doorY <= rightEdgeLocation; doorY++) {
						if (x == door.getX() && y == doorY) {
							return true;
						}
					}
				}
			}
		}
		doors = null;
		return false;
	}

	@Override
	public String toString(Point pt) {
		return "" + getOriginalTile(pt.getX(), pt.getY());
	} 
	@Override
	public void setId(int mapId) {
		_mapId = mapId;
	}

	@Override
	public int getBaseMapId() {
		return _baseMapId;
	}

	@Override
	public int getTestTile(int x, int y) {
		return rawTileLocal(x - _worldTopLeftX, y - _worldTopLeftY);
	}

	@Override
	public L1Map set_isUnderwater(boolean isUnderwater) {
		_isUnderwater = isUnderwater;
		return this;
	}

	@Override
	public L1Map set_isMarkable(boolean isMarkable) {
		_isMarkable = isMarkable;
		return this;
	}

	@Override
	public L1Map set_isTeleportable(boolean isTeleportable) {
		_isTeleportable = isTeleportable;
		return this;
	}

	@Override
	public L1Map set_isEscapable(boolean isEscapable) {
		_isEscapable = isEscapable;
		return this;
	}

	@Override
	public L1Map set_isUseResurrection(boolean isUseResurrection) {
		_isUseResurrection = isUseResurrection;
		return this;
	}

	@Override
	public L1Map set_isUsePainwand(boolean isUsePainwand) {
		_isUsePainwand = isUsePainwand;
		return this;
	}

	@Override
	public L1Map set_isEnabledDeathPenalty(boolean isEnabledDeathPenalty) {
		_isEnabledDeathPenalty = isEnabledDeathPenalty;
		return this;
	}

	@Override
	public L1Map set_isTakePets(boolean isTakePets) {
		_isTakePets = isTakePets;
		return this;
	}

	@Override
	public L1Map set_isRecallPets(boolean isRecallPets) {
		_isRecallPets = isRecallPets;
		return this;
	}

	@Override
	public L1Map set_isUsableItem(boolean isUsableItem) {
		_isUsableItem = isUsableItem;
		return this;
	}

	@Override
	public L1Map set_isUsableSkill(boolean isUsableSkill) {
		_isUsableSkill = isUsableSkill;
		return this;
	}

	private void initDoorCache() {
		// boolean[][] 대신 타일 수만큼 1 byte native memory를 평탄하게 확보한다.
		// Arena.ofAuto()를 _map과 공유하므로 별도 Arena/해제 관리가 필요 없다.
		_closedDoorCache = _mapArena.allocate((long) _mapWidth * _mapHeight, 1);
	}

	private boolean rawDoorLocal(MemorySegment door, int ix, int iy) {
		return door.get(ValueLayout.JAVA_BYTE, (long) ix * _mapHeight + iy) != 0;
	}

	private void rawDoorLocal(MemorySegment door, int ix, int iy, boolean closed) {
		door.set(ValueLayout.JAVA_BYTE, (long) ix * _mapHeight + iy, (byte) (closed ? 1 : 0));
	}

	@Override
	public boolean isClosedDoorCached(int x, int y) {
		final MemorySegment door = _closedDoorCache;
		if (door == null || !isInMap(x, y)) return false;

		final int ix = x - _worldTopLeftX;
		final int iy = y - _worldTopLeftY;
		if ((ix | iy) < 0 || ix >= _mapWidth || iy >= _mapHeight) return false;

		return rawDoorLocal(door, ix, iy);
	}

	@Override
	public void setClosedDoorCached(int x, int y, boolean closed) {
		final MemorySegment door = _closedDoorCache;
		if (door == null || !isInMap(x, y)) return;

		final int ix = x - _worldTopLeftX;
		final int iy = y - _worldTopLeftY;
		if ((ix | iy) < 0 || ix >= _mapWidth || iy >= _mapHeight) return;

		// 값이 실제로 변할 때만 bump (기존 동작 유지)
		if (rawDoorLocal(door, ix, iy) != closed) {
			rawDoorLocal(door, ix, iy, closed);
			bumpDoorVersion();
		}
	}

	private static final int BIT_IMPASSABLE = 0x80; // 128

	@Override
	public boolean isRayTraceable(
	        final int startX,
	        final int startY,
	        final int targetX,
	        final int targetY) {

	    if (startX == targetX && startY == targetY) {
	        return true;
	    }

	    if (!isInMap(startX, startY)
	            || !isInMap(targetX, targetY)) {
	        return false;
	    }

	    final MemorySegment raw = _map;
	    final MemorySegment door = _closedDoorCache;

	    final int offX = _worldTopLeftX;
	    final int offY = _worldTopLeftY;
	    final int w = _mapWidth;
	    final int h = _mapHeight;

	    int x = startX;
	    int y = startY;

	    final int dx = Math.abs(targetX - startX);
	    final int dy = Math.abs(targetY - startY);

	    final int sx = startX < targetX ? 1 : -1;
	    final int sy = startY < targetY ? 1 : -1;

	    int err = dx - dy;
	    int limit = dx + dy + 1;

	    while (limit-- > 0) {

	        if (x == targetX && y == targetY) {
	            return true;
	        }

	        final int prevX = x;
	        final int prevY = y;

	        final int e2 = err << 1;

	        if (e2 > -dy) {
	            err -= dy;
	            x += sx;
	        }

	        if (e2 < dx) {
	            err += dx;
	            y += sy;
	        }

	        final int ix1 = prevX - offX;
	        final int iy1 = prevY - offY;
	        final int ix2 = x - offX;
	        final int iy2 = y - offY;

	        if ((ix1 | iy1 | ix2 | iy2) < 0
	                || ix1 >= w
	                || ix2 >= w
	                || iy1 >= h
	                || iy2 >= h) {
	            return false;
	        }

	        /*
	         * 닫힌 문:
	         * 중간에 있는 문은 공격선을 막는다.
	         * 단, 최종 목적지 자체가 문이면 문을 공격할 수 있어야 하므로
	         * 마지막 타일의 closed-door는 허용한다.
	         */
	        final boolean finalStep =
	                x == targetX && y == targetY;

	        if (!finalStep
	                && door != null
	                && rawDoorLocal(door, ix2, iy2)) {
	            return false;
	        }

	        final int stepX = x - prevX;
	        final int stepY = y - prevY;

	        final int heading;

	        if (stepX == 0) {
	            heading = stepY < 0 ? 0 : 4;
	        } else if (stepY == 0) {
	            heading = stepX > 0 ? 2 : 6;
	        } else if (stepX > 0) {
	            heading = stepY < 0 ? 1 : 3;
	        } else {
	            heading = stepY > 0 ? 5 : 7;
	        }

	        /*
	         * 공격 LOS는 이동 비트(0x01 / 0x02)가 아니라
	         * 기존 Lineage arrow 비트(0x04 / 0x08)를 사용한다.
	         */
	        if (!isArrowStepPassableRaw(
	                raw,
	                ix1,
	                iy1,
	                ix2,
	                iy2,
	                heading,
	                w,
	                h)) {

	            return false;
	        }
	    }

	    return false;
	}


	/**
	 * isArrowPassable(x, y, heading)의 타일 비트 판정을
	 * door 체크 없이 그대로 수행한다.
	 *
	 * door는 isRayTraceable()에서 따로 처리한다.
	 * 그래야 중간 문은 차단하면서 최종 목적지인 문 자체는 공격 가능하다.
	 */
	private static boolean isArrowStepPassableRaw(
	        final MemorySegment raw,
	        final int ix1,
	        final int iy1,
	        final int ix2,
	        final int iy2,
	        final int heading,
	        final int w,
	        final int h) {

	    final int tile1 =
	            raw.get(
	                    ValueLayout.JAVA_BYTE,
	                    (long) ix1 * h + iy1) & 0xFF;

	    final int tile2 =
	            raw.get(
	                    ValueLayout.JAVA_BYTE,
	                    (long) ix2 * h + iy2) & 0xFF;

	    switch (heading) {

	    case 0: // N
	        return (tile1 & 0x08) != 0;

	    case 1: { // NE
	        final int iyN = iy1 - 1;
	        final int ixE = ix1 + 1;

	        if (iyN < 0 || ixE >= w) {
	            return false;
	        }

	        final int tileN =
	                raw.get(
	                        ValueLayout.JAVA_BYTE,
	                        (long) ix1 * h + iyN) & 0xFF;

	        final int tileE =
	                raw.get(
	                        ValueLayout.JAVA_BYTE,
	                        (long) ixE * h + iy1) & 0xFF;

	        return (tileN & 0x04) != 0
	                || (tileE & 0x08) != 0;
	    }

	    case 2: // E
	        return (tile1 & 0x04) != 0;

	    case 3: { // SE
	        final int iyS = iy1 + 1;

	        if (iyS >= h) {
	            return false;
	        }

	        final int tileS =
	                raw.get(
	                        ValueLayout.JAVA_BYTE,
	                        (long) ix1 * h + iyS) & 0xFF;

	        return (tileS & 0x04) != 0;
	    }

	    case 4: // S
	        return (tile2 & 0x08) != 0;

	    case 5: // SW
	        return (tile2 & 0x04) != 0
	                || (tile2 & 0x08) != 0;

	    case 6: // W
	        return (tile2 & 0x04) != 0;

	    case 7: { // NW
	        final int ixW = ix1 - 1;

	        if (ixW < 0) {
	            return false;
	        }

	        final int tileW =
	                raw.get(
	                        ValueLayout.JAVA_BYTE,
	                        (long) ixW * h + iy1) & 0xFF;

	        return (tileW & 0x08) != 0;
	    }

	    default:
	        return false;
	    }
	}

	private static final int[] DX8 = { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static final int[] DY8 = { -1, -1, 0, 1, 1, 1, 0, -1 };


	@Override
	public boolean isMovePassableGeo(int x, int y, int heading) {
		if ((heading & ~7) != 0) return false;

		// 시작점은 보통 무조건 맵 안이지만, 꼬인 좌표 방지
		if (!isInMap(x, y)) return false;

		final int nx = x + DX8[heading];
		final int ny = y + DY8[heading];

		// isInMap 특수룰(맵4) 포함
		if (!isInMap(nx, ny)) return false;

		// 로컬 캐시
		final MemorySegment raw = _map;
		final int w = _mapWidth;
		final int h = _mapHeight;
		final int offX = _worldTopLeftX;
		final int offY = _worldTopLeftY;

		final int ix1 = x  - offX;
		final int iy1 = y  - offY;
		final int ix2 = nx - offX;
		final int iy2 = ny - offY;

		// bounds (비트 OR로 음수 빠른 체크)
		if ((ix1 | iy1 | ix2 | iy2) < 0) return false;
		if (ix1 >= w || ix2 >= w || iy1 >= h || iy2 >= h) return false;

		// 1) 문 닫힘 차단(목적지)
		final MemorySegment door = _closedDoorCache;
		if (door != null && rawDoorLocal(door, ix2, iy2)) return false;

		// 2) 목적지 벽 차단
		final int tile2 = raw.get(ValueLayout.JAVA_BYTE, (long) ix2 * h + iy2) & 0xFF;
		if ((tile2 & 0x80) != 0) return false;

		// 3) 방향 비트 로직
		final int tile1 = raw.get(ValueLayout.JAVA_BYTE, (long) ix1 * h + iy1) & 0xFF;

		switch (heading) {
		case 0:
			return (tile1 & 0x02) == 0x02;
		case 2:
			return (tile1 & 0x01) == 0x01;
		case 4:
			return (tile2 & 0x02) == 0x02;
		case 6:
			return (tile2 & 0x01) == 0x01;
		case 1: {
			// (x, y-1) and (x+1, y)
			final int ix3 = ix1;
			final int iy3 = iy1 - 1;
			final int ix4 = ix1 + 1;

			if (iy3 < 0 || ix4 >= w) return false;
			final int tile3 = raw.get(ValueLayout.JAVA_BYTE, (long) ix3 * h + iy3) & 0xFF;
			final int tile4 = raw.get(ValueLayout.JAVA_BYTE, (long) ix4 * h + iy1) & 0xFF;

			return (((tile1 & 0x02) == 0x02) && ((tile3 & 0x01) == 0x01))
					|| (((tile1 & 0x01) == 0x01) && ((tile4 & 0x02) == 0x02));
		}
		case 3: {
			// (x, y+1)
			final int iy3 = iy1 + 1;
			if (iy3 >= h) return false;
			final int tile3 = raw.get(ValueLayout.JAVA_BYTE, (long) ix1 * h + iy3) & 0xFF;

			return ((tile3 & 0x03) == 0x03)
					|| (((tile1 & 0x01) == 0x01) && ((tile2 & 0x02) == 0x02));
		}
		case 5: {
			// (x, y+1) and (x-1, y)
			final int iy3 = iy1 + 1;
			final int ix4 = ix1 - 1;

			if (iy3 >= h || ix4 < 0) return false;
			final int tile3 = raw.get(ValueLayout.JAVA_BYTE, (long) ix1 * h + iy3) & 0xFF;
			final int tile4 = raw.get(ValueLayout.JAVA_BYTE, (long) ix4 * h + iy1) & 0xFF;

			return (((tile2 & 0x01) == 0x01) && ((tile3 & 0x02) == 0x02))
					|| (((tile2 & 0x02) == 0x02) && ((tile4 & 0x01) == 0x01));
		}
		case 7: {
			// (x-1, y)
			final int ix3 = ix1 - 1;
			if (ix3 < 0) return false;
			final int tile3 = raw.get(ValueLayout.JAVA_BYTE, (long) ix3 * h + iy1) & 0xFF;

			return ((tile3 & 0x03) == 0x03)
					|| (((tile1 & 0x02) == 0x02) && ((tile2 & 0x01) == 0x01));
		}
		default:
			return false;
		}
	}


	@Override
	public boolean isMovePassableGeoNpc(int x, int y, int heading) {
		if ((heading & ~7) != 0) return false;
		if (!isInMap(x, y)) return false;

		final int nx = x + DX8[heading];
		final int ny = y + DY8[heading];
		if (!isInMap(nx, ny)) return false;

		final MemorySegment raw = _map;
		final MemorySegment door = _closedDoorCache;
		final int offX = _worldTopLeftX;
		final int offY = _worldTopLeftY;
		final int w = _mapWidth;
		final int h = _mapHeight;

		final int ix1 = x  - offX;
		final int iy1 = y  - offY;
		final int ix2 = nx - offX;
		final int iy2 = ny - offY;

		if ((ix1 | iy1 | ix2 | iy2) < 0) return false;
		if (ix1 >= w || ix2 >= w || iy1 >= h || iy2 >= h) return false;

		// 목적지 문/벽 차단
		if (door != null && rawDoorLocal(door, ix2, iy2)) return false;

		final int tile2 = raw.get(ValueLayout.JAVA_BYTE, (long) ix2 * h + iy2) & 0xFF;
		if ((tile2 & BIT_IMPASSABLE) != 0) return false;

		final int tile1 = raw.get(ValueLayout.JAVA_BYTE, (long) ix1 * h + iy1) & 0xFF;

		switch (heading) {
		case 0:  return (tile1 & 0x02) == 0x02; // N
		case 2:  return (tile1 & 0x01) == 0x01; // E
		case 4:  return (tile2 & 0x02) == 0x02; // S
		case 6:  return (tile2 & 0x01) == 0x01; // W

		// ----- 대각: 2개의 L자 후보 중 하나라도 성립하면 true -----
		case 1: { // NE: (x, y-1)=N tile, (x+1, y)=E tile
			final int ixN = ix1,     iyN = iy1 - 1;   // (x, y-1)
			final int ixE = ix1 + 1, iyE = iy1;       // (x+1, y)
			if (iyN < 0 || ixE >= w) return false;

			// 후보 A: N -> E
			boolean a = false;
			if ((tile1 & 0x02) != 0) { // 북 이동 비트
				if (door == null || !rawDoorLocal(door, ixN, iyN)) {
					final int tileN = raw.get(ValueLayout.JAVA_BYTE, (long) ixN * h + iyN) & 0xFF;
					if ((tileN & BIT_IMPASSABLE) == 0 && (tileN & 0x01) != 0) { // N타일에서 E 가능
						a = true;
					}
				}
			}

			// 후보 B: E -> N
			boolean b = false;
			if ((tile1 & 0x01) != 0) { // 동 이동 비트
				if (door == null || !rawDoorLocal(door, ixE, iyE)) {
					final int tileE = raw.get(ValueLayout.JAVA_BYTE, (long) ixE * h + iyE) & 0xFF;
					if ((tileE & BIT_IMPASSABLE) == 0 && (tileE & 0x02) != 0) { // E타일에서 N 가능(=0x02)
						b = true;
					}
				}
			}

			return a || b;
		}

		case 3: { // SE: (x, y+1)=S tile, (x+1, y)=E tile
			final int ixS = ix1,     iyS = iy1 + 1;   // (x, y+1)
			final int ixE = ix1 + 1, iyE = iy1;       // (x+1, y)
			if (iyS >= h || ixE >= w) return false;

			// 후보 A: S -> E
			boolean a = false;
			// S는 1스텝에서 목적지 타일이 (x,y+1)라서 tileS의 0x02가 필요
			if (door == null || !rawDoorLocal(door, ixS, iyS)) {
				final int tileS = raw.get(ValueLayout.JAVA_BYTE, (long) ixS * h + iyS) & 0xFF;
				if ((tileS & BIT_IMPASSABLE) == 0 && (tileS & 0x02) != 0 && (tileS & 0x01) != 0) {
					// tileS가 S(0x02)도 되고, 그 자리에서 E(0x01)도 되어야 함
					a = true;
				}
			}

			// 후보 B: E -> S
			boolean b = false;
			if ((tile1 & 0x01) != 0) {
				if (door == null || !rawDoorLocal(door, ixE, iyE)) {
					final int tileE = raw.get(ValueLayout.JAVA_BYTE, (long) ixE * h + iyE) & 0xFF;
					if ((tileE & BIT_IMPASSABLE) == 0 && (tile2 & 0x02) != 0) {
						// E타일은 벽/문만 확인(중간 스텝), S는 최종 목적지 tile2의 0x02
						b = true;
					}
				}
			}

			return a || b;
		}

		case 5: { // SW: (x, y+1)=S tile, (x-1, y)=W tile
			final int ixS = ix1,     iyS = iy1 + 1;   // (x, y+1)
			final int ixW = ix1 - 1, iyW = iy1;       // (x-1, y)
			if (iyS >= h || ixW < 0) return false;

			// 후보 A: S -> W (중간: tileS, 마지막: tile2(목적지))
			boolean a = false;
			if ((tile2 & 0x01) != 0) { // W는 목적지(tile2) 0x01
				if (door == null || !rawDoorLocal(door, ixS, iyS)) {
					final int tileS = raw.get(ValueLayout.JAVA_BYTE, (long) ixS * h + iyS) & 0xFF;
					if ((tileS & BIT_IMPASSABLE) == 0 && (tileS & 0x02) != 0) {
						a = true;
					}
				}
			}

			// 후보 B: W -> S (중간: tileW, 마지막: tile2)
			boolean b = false;
			if (door == null || !rawDoorLocal(door, ixW, iyW)) {
				final int tileW = raw.get(ValueLayout.JAVA_BYTE, (long) ixW * h + iyW) & 0xFF;
				if ((tileW & BIT_IMPASSABLE) == 0 && (tileW & 0x01) != 0 && (tile2 & 0x02) != 0) {
					b = true;
				}
			}

			return a || b;
		}

		case 7: { // NW: (x, y-1)=N tile, (x-1, y)=W tile
			final int ixN = ix1,     iyN = iy1 - 1;   // (x, y-1)
			final int ixW = ix1 - 1, iyW = iy1;       // (x-1, y)
			if (iyN < 0 || ixW < 0) return false;

			// 후보 A: N -> W (중간: tileN, 마지막: tile2)
			boolean a = false;
			if ((tile1 & 0x02) != 0 && (tile2 & 0x01) != 0) {
				if (door == null || !rawDoorLocal(door, ixN, iyN)) {
					final int tileN = raw.get(ValueLayout.JAVA_BYTE, (long) ixN * h + iyN) & 0xFF;
					if ((tileN & BIT_IMPASSABLE) == 0) {
						a = true;
					}
				}
			}

			// 후보 B: W -> N (중간: tileW가 0x03, 마지막: tile2는 문/벽 이미 통과)
			boolean b = false;
			if (door == null || !rawDoorLocal(door, ixW, iyW)) {
				final int tileW = raw.get(ValueLayout.JAVA_BYTE, (long) ixW * h + iyW) & 0xFF;
				if ((tileW & BIT_IMPASSABLE) == 0 && (tileW & 0x03) == 0x03) {
					b = true;
				}
			}

			return a || b;
		}

		default:
			return false;
		}
	}


	@Override
	public L1Map set_isRuler(boolean isRuler) {
		_is_ruler = isRuler;
		return this;
	}
}
