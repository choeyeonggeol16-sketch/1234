/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.model.map;

import l1j.server.server.types.Point;

/**
 * L1Map MAP 정보를 보관 유지해, 그에 대하는 여러가지 인터페이스를 제공한다.
 */
public abstract class L1Map {
	private static L1NullMap _nullMap = new L1NullMap();

	protected L1Map() {
	}

	/**
	 * 이 MAP의 MAP ID를 돌려준다.
	 * @return MAP ID
	 */
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

	/**
	 * 지정된 좌표의 값을 돌려준다.
	 * 
	 * 추천 되고 있지 않습니다. 이 메소드는, 기존 코드와의 호환성을 위해 제공되고 있습니다.
	 * L1Map의 이용자는 통상, MAP에 어떠한 값이 격납되고 있을까를 알 필요는 없습니다.
	 * 또, 격납되고 있는 값에 의존하는 것 같은 코드를 써야 하는 것이 아닙니다.  디버그등의 특수한 경우에 한정해, 이 메소드를 이용할 수 있습니다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 지정된 좌표의 값
	 */
	public abstract int getTile(int x, int y);

	/**
	 * 지정된 좌표의 값을 돌려준다.
	 * 
	 * 추천 되고 있지 않습니다. 이 메소드는, 기존 코드와의 호환성을 위해 제공되고 있습니다.
	 * L1Map의 이용자는 통상, MAP에 어떠한 값이 격납되고 있을까를 알 필요는 없습니다.
	 * 또, 격납되고 있는 값에 의존하는 것 같은 코드를 써야 하는 것이 아닙니다.  디버그등의 특수한 경우에 한정해, 이 메소드를 이용할 수 있습니다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 지정된 좌표의 값
	 */
	public abstract int getOriginalTile(int x, int y);

	/**
	 * 지정된 좌표가 MAP의 범위내일까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 범위내이면 true
	 */
	public abstract boolean isInMap(Point pt);

	/**
	 * 지정된 좌표가 MAP의 범위내일까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 범위내이면 true
	 */
	public abstract boolean isInMap(int x, int y);

	/**
	 * 지정된 좌표가 통행 가능한가를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 통행 가능하면 true
	 */
	public abstract boolean isPassable(Point pt);

	/**
	 * 지정된 좌표가 통행 가능한가를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 통행 가능하면 true
	 */
	public abstract boolean isPassable(int x, int y);

	/**
	 * 지정된 좌표의 heading 방향이 통행 가능한가를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 통행 가능하면 true
	 */
	public abstract boolean isPassable(Point pt, int heading);
	
	public abstract boolean isUserPassable(Point pt);

	public abstract boolean isUserPassable(int x, int y, int heading);
	public abstract boolean isPassable(int x, int y, int heading);

	/**
	 * 지정된 좌표의 통행 가능, 불능을 설정한다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @param isPassable
	 *            통행 가능하면 true
	 */
	public abstract void setPassable(Point pt, boolean isPassable);

	/**
	 * 지정된 좌표의 통행 가능, 불능을 설정한다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @param isPassable
	 *            통행 가능하면 true
	 */
	public abstract void setPassable(int x, int y, boolean isPassable);

	/**
	 * 지정된 좌표가 세이프티 존일까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 세이프티 존이면 true
	 */
	public abstract boolean isSafetyZone(Point pt);

	/**
	 * 지정된 좌표가 세이프티 존일까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 세이프티 존이면 true
	 */
	public abstract boolean isSafetyZone(int x, int y);

	/**
	 * 지정된 좌표가 컴배트 존일까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 컴배트 존이면 true
	 */
	public abstract boolean isCombatZone(Point pt);

	/**
	 * 지정된 좌표가 컴배트 존일까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 컴배트 존이면 true
	 */
	public abstract boolean isCombatZone(int x, int y);

	/**
	 * 지정된 좌표가 노멀 존일까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 노멀 존이면 true
	 */
	public abstract boolean isNormalZone(Point pt);

	/**
	 * 지정된 좌표가 노멀 존일까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 노멀 존이면 true
	 */
	public abstract boolean isNormalZone(int x, int y);

	/**
	 * 지정된 좌표가 화살이나 마법을 통할까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @return 화살이나 마법을 통하는 경우, true
	 */
	public abstract boolean isArrowPassable(Point pt);

	/**
	 * 지정된 좌표가 화살이나 마법을 통할까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @return 화살이나 마법을 통하는 경우, true
	 */
	public abstract boolean isArrowPassable(int x, int y);

	/**
	 * 지정된 좌표의 heading 방향이 화살이나 마법을 통할까를 돌려준다.
	 * 
	 * @param pt
	 *            좌표를 보관 유지하는 Point 오브젝트
	 * @param heading
	 *            방향
	 * @return 화살이나 마법을 통하는 경우, true
	 */
	public abstract boolean isArrowPassable(Point pt, int heading);

	/**
	 * 지정된 좌표의 heading 방향이 화살이나 마법을 통할까를 돌려준다.
	 * 
	 * @param x
	 *            좌표의 X치
	 * @param y
	 *            좌표의 Y치
	 * @param heading
	 *            방향
	 * @return 화살이나 마법을 통하는 경우, true
	 */
	public abstract boolean isArrowPassable(int x, int y, int heading);

	/**
	 * 이 MAP가, 수중 MAP일까를 돌려준다.
	 * 
	 * @return 수중이면, true
	 */
	public abstract boolean isUnderwater();

	/**
	 * 이 MAP가, 북마크 가능한가를 돌려준다.
	 * 
	 * @return 북마크 가능하면, true
	 */
	public abstract boolean isMarkable();

	/**
	 * 이 MAP가, 랜덤 텔레포트 가능한가를 돌려준다.
	 * 
	 * @return 랜덤 텔레포트 가능하면, true
	 */
	public abstract boolean isTeleportable();

	/**
	 * 이 MAP가, MAP를 넘은 텔레포트 가능한가를 돌려준다.
	 * 
	 * @return 텔레포트 가능하면, true
	 */
	public abstract boolean isEscapable();

	/**
	 * 이 MAP가, restore 가능한가를 돌려준다.
	 * 
	 * @return restore 가능하면, true
	 */
	public abstract boolean isUseResurrection();

	/**
	 * 이 MAP가, 파인쥬스 wand 사용 가능한가를 돌려준다.
	 * 
	 * @return 파인쥬스 wand 사용 가능하면, true
	 */
	public abstract boolean isUsePainwand();

	/**
	 * 이 MAP가, 데스페나르티가 있을까를 돌려준다.
	 * 
	 * @return 데스페나르티가 있으면, true
	 */
	public abstract boolean isEnabledDeathPenalty();

	/**
	 * 이 MAP가, 애완동물·사몬을 데리고 갈 수 있을까를 돌려준다.
	 * 
	 * @return 펫·사몬을 데리고 갈 수 있다면 true
	 */
	public abstract boolean isTakePets();

	/**
	 * 이 MAP가, 애완동물·사몬을 호출할 수 있을까를 돌려준다.
	 * 
	 * @return 펫·사몬을 호출할 수 있다면 true
	 */
	public abstract boolean isRecallPets();


	public abstract boolean isUsableItem();

	public abstract boolean isUsableSkill();

	public abstract boolean isFishingZone(int x, int y);

	public abstract boolean isExistDoor(int x, int y);
	
	public abstract L1V1Map copyMap(int a);//레이드

	public static L1Map newNull() {
		return _nullMap;
	}

	/**
	 * 지정된 pt의 타일의 캐릭터 라인 표현을 돌려준다.
	 */
	public abstract String toString(Point pt);

	/**
	 * 이 MAP가 null일까를 돌려준다.
	 * 
	 * @return null이면, true
	 */
	public boolean isNull() {
		return false;
	}
	
	public static boolean isTeleportable(int x, int y, int mapId)
	{
		if( mapId == 4 && x >= 33469 && x <= 33528 && y >= 32839 && y <= 32869 )
		{
			if((x >= 33479 && x <= 32528 && y >= 32849 && y <= 32859) || (x == 33478 && y >= 32850 && y <= 32859) || (x == 33477 && y >= 32851 && y <= 32859)
					|| (x == 33476 && y >= 32852 && y <= 32858) || (x == 33475 && y >= 32853 && y <= 32857))
				return true;
			return false;
		}

		return true;
	}
}

/**
 * 아무것도 하지 않는 Map.
 */
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
	public boolean isUserPassable(Point pt) {
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

	public void setDoorPassable(Point pt, boolean directionX, boolean isPassable) {
	}

	public void setDoorPassable(int x, int y, boolean directionX, boolean isPassable) {
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
}