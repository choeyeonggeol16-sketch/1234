package l1j.server.server.model.map;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.types.Point;

public abstract class L1Map {
	private static L1NullMap _nullMap = new L1NullMap();

	protected L1Map() {
	}

	public abstract int getId();
	/**
	* 새로운 맵 ID를 설정합니다. 이 ID는 맵 오브젝트를 고유하게 식별하는 값임을 보?되어야합니다.
	*
	* @ param mapId
	* 새로 설정되는 맵 ID입니다.
	*/
	public abstract void setId(int mapId);

	/**
	* 맵 파일 ID를 반환합니다. 
	* 이 ID를 맵 객체 고유하게 식별하는 데 사용하지 마십시오.
	* 이 ID는 맵 파일을 식별하는 데 무슨 게임 클라이언트가 필요로하는 ID이며 고유성을 보?되지 않습니다.
	* 일부 개체가 동일한 맵 파일 ID를 가질 수 있습니다.
	* 클라이언트에게 전송하는 맵 파일 ID가 필요한 경우이 방법을 사용합니다.
	*
	* @ return 맵 파일 ID
	*/
	public abstract int getBaseMapId();
	// TODO JavaDoc
	public abstract int getX();

	public abstract int getY();

	public abstract int getWidth();

	public abstract int getHeight();

	public abstract int getTile(int x, int y);

	public abstract int getTestTile(int x, int y);
	
	public abstract int getOriginalTile(int x, int y);

	public abstract boolean isInMap(Point pt);

	public abstract boolean isInMap(int x, int y);

	public abstract boolean isPassable(Point pt);

	public abstract boolean isPassable(int x, int y);

	public abstract boolean isPassable(Point pt, int heading);

	public abstract boolean isUserPassable(int x, int y, int heading);

	public abstract boolean isPassable(int x, int y, int heading);

	public abstract void setPassable(Point pt, boolean isPassable);

	public abstract void setPassable(int x, int y, boolean isPassable);

	public abstract boolean isSafetyZone(Point pt);

	public abstract boolean isSafetyZone(int x, int y);

	public abstract boolean isCombatZone(Point pt);

	public abstract boolean isCombatZone(int x, int y);

	public abstract boolean isNormalZone(Point pt);

	public abstract boolean isNormalZone(int x, int y);

	public abstract boolean isArrowPassable(Point pt);

	public abstract boolean isArrowPassable(int x, int y);

	public abstract boolean isArrowPassable(Point pt, int heading);

	public abstract boolean isArrowPassable(int x, int y, int heading);

	public abstract boolean isUnderwater();

	public abstract L1Map set_isUnderwater(boolean isUnderwater);
	
	public abstract boolean isMarkable();

	public abstract L1Map set_isMarkable(boolean isMarkable);
	
	public abstract boolean isTeleportable();

	public abstract L1Map set_isTeleportable(boolean isTeleportable);
	
	public abstract boolean isEscapable();

	public abstract L1Map set_isEscapable(boolean isEscapable);
	
	public abstract boolean isUseResurrection();

	public abstract L1Map set_isUseResurrection(boolean isUseResurrection);
	
	public abstract boolean isUsePainwand();

	public abstract L1Map set_isUsePainwand(boolean isUsePainwand);
	
	public abstract boolean isEnabledDeathPenalty();

	public abstract L1Map set_isEnabledDeathPenalty(boolean isEnabledDeathPenalty);
	
	public abstract boolean isTakePets();

	public abstract L1Map set_isTakePets(boolean isTakePets);
	
	public abstract boolean isRecallPets();

	public abstract L1Map set_isRecallPets(boolean isRecallPets);
	
	public abstract boolean isUsableItem();

	public abstract L1Map set_isUsableItem(boolean isUsableItem);
	
	public abstract boolean isUsableSkill();

	public abstract L1Map set_isUsableSkill(boolean isUsableSkill);
	
	public abstract boolean isFishingZone(int x, int y);

	public abstract boolean isExistDoor(int x, int y);
	
	/**
	 * 닫힌 문 캐시 확인 (기본값: false)
	 */
	public boolean isClosedDoorCached(int x, int y) {
		return false; 
	}
	// 타일 벽(0x80)만 보는 저수준 체크
	public boolean isTileBlocked(int x, int y) {
		return false; 
	}
	public boolean isMovePassableGeo(int x, int y, int heading) {
		return false;
	}
	
	
	public boolean isMovePassableGeoNpc(int x, int y, int heading) {
		return false;
	}
	/**
	 * 닫힌 문 캐시 갱신 (기본값: 작동 안함)
	 */
	public void setClosedDoorCached(int x, int y, boolean closed) {
		// 기본 맵에서는 아무 동작도 안 함
	}

	/**
	 * 시야/공격 경로 체크 (RayTracing)
	 */
	public boolean isRayTraceable(int startX, int startY, int targetX, int targetY) {
		return false; 
	}
	
	private final AtomicInteger _doorVersion = new AtomicInteger(0);

	public int getDoorVersion() {
	    return _doorVersion.get();
	}

	// 문 열림/닫힘/파괴/생성 등 "경로에 영향" 주는 시점마다 호출
	public void bumpDoorVersion() {
	    _doorVersion.incrementAndGet();
	}

	
	public abstract L1V1Map copyMap(int a);//레이드

	public static L1Map newNull() {
		return _nullMap;
	}

	public abstract String toString(Point pt);

	public boolean isNull() {
		return false;
	}
	
	public static boolean isTeleportable(int x, int y, int mapId){//버경 좌표
		if( mapId == 4 && x >= 33469 && x <= 33528 && y >= 32839 && y <= 32869 ){
			return false;
		}
		return true;
	}
	
	public abstract L1Map set_isRuler(boolean isRuler);
	
	public boolean isRuler(){
		return false;
	}
}

class L1NullMap extends L1Map {
	public L1NullMap() {
	}

	@Override
	public int getId() {
		return 0;
	}

	@Override
	public int getX() {
		return 0;
	}

	@Override
	public int getY() {
		return 0;
	}

	@Override
	public int getWidth() {
		return 0;
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public int getTile(int x, int y) {
		return 0;
	}

	@Override
	public int getOriginalTile(int x, int y) {
		return 0;
	}

	@Override
	public boolean isInMap(int x, int y) {
		return false;
	}

	@Override
	public boolean isInMap(Point pt) {
		return false;
	}

	@Override
	public boolean isPassable(int x, int y) {
		return false;
	}

	@Override
	public boolean isUserPassable(int x, int y, int heading) {
		return false;
	}

	@Override
	public boolean isPassable(Point pt) {
		return false;
	}

	@Override
	public boolean isPassable(int x, int y, int heading) {
		return false;
	}

	@Override
	public boolean isPassable(Point pt, int heading) {
		return false;
	}

	@Override
	public void setPassable(int x, int y, boolean isPassable) {
	}

	@Override
	public void setPassable(Point pt, boolean isPassable) {
	}

	@Override
	public boolean isSafetyZone(int x, int y) {
		return false;
	}

	@Override
	public boolean isSafetyZone(Point pt) {
		return false;
	}

	@Override
	public boolean isCombatZone(int x, int y) {
		return false;
	}

	@Override
	public boolean isCombatZone(Point pt) {
		return false;
	}

	@Override
	public boolean isNormalZone(int x, int y) {
		return false;
	}

	@Override
	public boolean isNormalZone(Point pt) {
		return false;
	}

	@Override
	public boolean isArrowPassable(int x, int y) {
		return false;
	}

	@Override
	public boolean isArrowPassable(Point pt) {
		return false;
	}

	@Override
	public boolean isArrowPassable(int x, int y, int heading) {
		return false;
	}

	@Override
	public boolean isArrowPassable(Point pt, int heading) {
		return false;
	}

	@Override
	public boolean isUnderwater() {
		return false;
	}

	@Override
	public boolean isMarkable() {
		return false;
	}

	@Override
	public boolean isTeleportable() {
		return false;
	}

	@Override
	public boolean isEscapable() {
		return false;
	}

	@Override
	public boolean isUseResurrection() {
		return false;
	}

	@Override
	public boolean isUsePainwand() {
		return false;
	}

	@Override
	public boolean isEnabledDeathPenalty() {
		return false;
	}

	@Override
	public boolean isTakePets() {
		return false;
	}

	@Override
	public boolean isRecallPets() {
		return false;
	}

	@Override
	public boolean isUsableItem() {
		return false;
	}

	@Override
	public boolean isUsableSkill() {
		return false;
	}

	@Override
	public boolean isFishingZone(int x, int y) {
		return false;
	}

	@Override
	public boolean isExistDoor(int x, int y) {
		return false;
	}

	@Override
	public String toString(Point pt) {
		return "null";
	}

	@Override
	public boolean isNull() {
		return true;
	}
	
	@Override
	public L1V1Map copyMap(int id){//레이드
		return null;
	}
	@Override
	public void setId(int mapId) {

	}

	@Override
	public int getBaseMapId() {
		return 0;
	}

	@Override
	public int getTestTile(int x, int y) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public L1Map set_isUnderwater(boolean isUnderwater) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isMarkable(boolean isMarkable) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isTeleportable(boolean isTeleportable) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isEscapable(boolean isEscapable) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isUseResurrection(boolean isUseResurrection) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isUsePainwand(boolean isUsePainwand) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isEnabledDeathPenalty(boolean isEnabledDeathPenalty) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isTakePets(boolean isTakePets) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isRecallPets(boolean isRecallPets) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isUsableItem(boolean isUsableItem) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isUsableSkill(boolean isUsableSkill) {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public L1Map set_isRuler(boolean isRuler) {
		// TODO Auto-generated method stub
		return this;
	}
	
}