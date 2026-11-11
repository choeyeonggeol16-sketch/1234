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

package l1j.server.Templates;

public class L1MobSkill implements Cloneable {
	public static final int TYPE_NONE = 0;

	public static final int TYPE_PHYSICAL_ATTACK = 1;

	public static final int TYPE_MAGIC_ATTACK = 2;

	public static final int TYPE_SUMMON = 3;

	public static final int TYPE_POLY = 4;

	public static final int CHANGE_TARGET_NO = 0;

	public static final int CHANGE_TARGET_COMPANION = 1;

	public static final int CHANGE_TARGET_ME = 2;

	public static final int CHANGE_TARGET_RANDOM = 3;

	private final int skillSize;

	@Override
	public L1MobSkill clone() {
		try {
			return (L1MobSkill) (super.clone());
		} catch (CloneNotSupportedException e) {
			throw (new InternalError(e.getMessage()));
		}
	}

	public int getSkillSize() {
		return skillSize;
	}

	public L1MobSkill(int sSize) {
		skillSize = sSize;

		type = new int[skillSize];
		triRnd = new int[skillSize];
		triHp = new int[skillSize];
		triCompanionHp = new int[skillSize];
		triRange = new int[skillSize];
		triCount = new int[skillSize];
		changeTarget = new int[skillSize];
		range = new int[skillSize];
		areaWidth = new int[skillSize];
		areaHeight = new int[skillSize];
		leverage = new int[skillSize];
		skillId = new int[skillSize];
		gfxid = new int[skillSize];
		actid = new int[skillSize];
		summon = new int[skillSize];
		summonMin = new int[skillSize];
		summonMax = new int[skillSize];
		polyId = new int[skillSize];
	}

	private int mobid;

	public int get_mobid() {
		return mobid;
	}

	public void set_mobid(int i) {
		mobid = i;
	}

	private String mobName;

	public String getMobName() {
		return mobName;
	}

	public void setMobName(String s) {
		mobName = s;
	}

	/*
	 * 스킬의 타입 0→아무것도 하지 않는, 1→물리 공격, 2→마법 공격, 3→사몬
	 */
	private int type[];

	public int getType(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return type[idx];
	}

	public void setType(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		type[idx] = i;
	}

	/*
	 * 스킬 발동 조건：랜덤인 확률(0%~100%)로 스킬 발동
	 */
	private int triRnd[];

	public int getTriggerRandom(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return triRnd[idx];
	}

	public void setTriggerRandom(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		triRnd[idx] = i;
	}

	/*
	 * 스킬 발동 조건：HP가%이하로 발동
	 */
	int triHp[];

	public int getTriggerHp(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return triHp[idx];
	}

	public void setTriggerHp(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		triHp[idx] = i;
	}

	/*
	 * 스킬 발동 조건：동족의 HP가%이하로 발동
	 */
	int triCompanionHp[];

	public int getTriggerCompanionHp(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return triCompanionHp[idx];
	}

	public void setTriggerCompanionHp(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		triCompanionHp[idx] = i;
	}

	/*
	 * 스킬 발동 조건：triRange<0의 경우, 대상과의 거리가 abs(triRange) 이하 때 발동
	 * triRange>0의 경우, 대상과의 거리가 triRange 이상 때 발동
	 */
	int triRange[];

	public int getTriggerRange(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return triRange[idx];
	}

	public void setTriggerRange(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		triRange[idx] = i;
	}

	// distance가 지정 idx 스킬의 발동 조건을 채우고 있을까
	public boolean isTriggerDistance(int idx, int distance) {
		int triggerRange = getTriggerRange(idx);

		if ((triggerRange < 0 && distance <= Math.abs(triggerRange))
				|| (triggerRange > 0 && distance >= triggerRange)) {
			return true;
		}
		return false;
	}

	int triCount[];

	/*
	 * 스킬 발동 조건：스킬의 발동 회수가 triCount 이하 때 발동
	 */
	public int getTriggerCount(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return triCount[idx];
	}

	public void setTriggerCount(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		triCount[idx] = i;
	}

	/*
	 * 스킬 발동시, 타겟을 변경할까
	 */
	int changeTarget[];

	public int getChangeTarget(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return changeTarget[idx];
	}

	public void setChangeTarget(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		changeTarget[idx] = i;
	}

	/*
	 * range까지의 거리라면 공격 가능, 물리 공격을 한다면 근접 공격의 경우에서도 1이상을 설정
	 */
	int range[];

	public int getRange(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return range[idx];
	}

	public void setRange(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		range[idx] = i;
	}

	/*
	 * 범위 공격의 가로폭, 단체 공격이라면 0을 설정, 범위 공격한다면 0이상을 설정
	 * Width와 Height의 설정은 공격자로부터 봐 가로폭을 Width, 깊이를 Height로 한다.
	 * Width는+-있으므로, 1을 지정하면, 타겟을 중심으로서 좌우 1까지가 대상이 된다.
	 */
	int areaWidth[];

	public int getAreaWidth(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return areaWidth[idx];
	}

	public void setAreaWidth(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		areaWidth[idx] = i;
	}

	/*
	 * 범위 공격의 높이, 단체 공격이라면 0을 설정, 범위 공격한다면 1이상을 설정
	 */
	int areaHeight[];

	public int getAreaHeight(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return areaHeight[idx];
	}

	public void setAreaHeight(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		areaHeight[idx] = i;
	}

	/*
	 * 데미지의 배율,1/10그리고 나타낸다. 물리 공격, 마법 공격 모두 유효
	 */
	int leverage[];

	public int getLeverage(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return leverage[idx];
	}

	public void setLeverage(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		leverage[idx] = i;
	}

	/*
	 * 마법을 사용하는 경우, SkillId를 지정
	 */
	int skillId[];

	public int getSkillId(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return skillId[idx];
	}

	public void setSkillId(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		skillId[idx] = i;
	}

	/*
	 * 물리 공격의 모션 그래픽
	 */
	int gfxid[];

	public int getGfxid(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return gfxid[idx];
	}

	public void setGfxid(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		gfxid[idx] = i;
	}

	/*
	 * 물리 공격의 그래픽의 액션 ID
	 */
	int actid[];

	public int getActid(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return actid[idx];
	}

	public void setActid(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		actid[idx] = i;
	}

	/*
	 * 사몬 하는 monster의 NPCID
	 */
	int summon[];

	public int getSummon(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return summon[idx];
	}

	public void setSummon(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		summon[idx] = i;
	}

	/*
	 * 사몬 하는 monster의 최소수
	 */
	int summonMin[];

	public int getSummonMin(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return summonMin[idx];
	}

	public void setSummonMin(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		summonMin[idx] = i;
	}

	/*
	 * 사몬 하는 monster의 최대수
	 */
	int summonMax[];

	public int getSummonMax(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return summonMax[idx];
	}

	public void setSummonMax(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		summonMax[idx] = i;
	}

	/*
	 * 무엇에 강제 변신시킬까
	 */
	int polyId[];

	public int getPolyId(int idx) {
		if (idx < 0 || idx >= getSkillSize()) {
			return 0;
		}
		return polyId[idx];
	}

	public void setPolyId(int idx, int i) {
		if (idx < 0 || idx >= getSkillSize()) {
			return;
		}
		polyId[idx] = i;
	}
}
