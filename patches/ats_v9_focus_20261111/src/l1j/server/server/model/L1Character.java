package l1j.server.server.model;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.ActionCodes.EActionCodes;
import l1j.server.ActionCodes.SpriteInformation;
import l1j.server.ActionCodes.Loader.SpriteInformationLoader;
import l1j.server.configure.Config;
import l1j.server.server.datatables.MapChangeTable;
import l1j.server.server.model.L1ItemDelay.ItemDelayTimer;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.poison.Poison;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillTimer;
import l1j.server.server.serverpackets.S_ChangeShape;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_Poison;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.IntRange;

public class L1Character extends L1Object {

	private static final long serialVersionUID = 1L;

	private final Map<Integer, ItemDelayTimer> _item_delay = new ConcurrentHashMap<Integer, ItemDelayTimer>();	
	private final Map<Integer, L1NpcInstance> _petlist = new HashMap<Integer, L1NpcInstance>();
	private final Map<Integer, DollInstance> _dolllist = new HashMap<Integer, DollInstance>();
	private final Map<Integer, SkillTimer> _skillEffect = new HashMap<Integer, SkillTimer>();
	private final Map<Integer, L1ItemDelay.ItemDelayTimer> _itemdelay = new HashMap<Integer, L1ItemDelay.ItemDelayTimer>();

	private DollInstance _doll;
	private Poison _poison = null;
	protected Light light = null; // 케릭터 주위 빛
	protected Ability ability = null; // 능력치
	protected Resistance resistance = null; // 저항 (마방, 불, 물, 바람, 땅, 스턴, 동빙, 슬립, 석화)
	protected AC ac = null; // AC 방어
	// 클래스 타입 
	private int _type;
	public int getType() {
		return _type;
	}
	public void setType(int i) {
		_type = i;
	}
	private int _buffnoch;
	public int getBuffnoch() {
		return _buffnoch;
	}
	public void setBuffnoch(int buffnoch) {
		_buffnoch = buffnoch;
	}
	// 데미지
	private int _hitup = 0;
	private int _trueHitup = 0;
	private int _bowHitup = 0;
	private int _trueBowHitup = 0;
	private int _sp = 0; // ● 증가한 SP
	private boolean _paralyzed;
	private boolean _sleeped;

	public void dispose() {
		if (resistance != null) {
			resistance.dispose();
			resistance = null;
		}
		if (light != null) {
			light.dispose();// 오류
			light = null;
		}
		_petlist.clear(); 
		_dolllist.clear();
		clearSkillEffectTimer(); 
		_itemdelay.clear();
	}

	public L1Character() {
		_level = 1;
		ability = new Ability();
		resistance = new Resistance(this);
		ac = new AC();
		light = new Light(this);
	}
	/**
	 * 캐릭터를 restore시킨다.
	 * @param hp restore 후의 HP
	 */
	public void resurrect(int hp) {
		if (!isDead()) {
			return;
		}
		if (hp <= 0) {
			hp = 1;
		}
		setCurrentHp(hp);
		setDead(false);
		L1PolyMorph.undoPoly(this);
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(S_RemoveObject.get(this));
			pc.removeKnownObject(this);
			pc.updateObject(); 
		} 
	}

	private int _currentHp;
	/**
	 * 캐릭터의 현재의 HP를 돌려준다.
	 * @return 현재의 HP
	 */
	public int getCurrentHp() {
		return _currentHp;
	}
	/**
	 * 캐릭터의 HP를 설정한다.
	 * @param i 캐릭터의 새로운 HP
	 */
	// 특수한 처리가 있는 경우는 여기를 오바라이드(패킷 송신등 )
	public void setCurrentHp(int i) {
		if (i >= getMaxHp()) {
			i = getMaxHp();
		}
		if (i < 0) {
			i = 0;
		}
		_currentHp = i;
	}

	/**
	 * 캐릭터의 HP를 설정한다.
	 * @param i 캐릭터의 새로운 HP
	 */
	public void setCurrentHpDirect(int i) {
		_currentHp = i;
	}
	private int _currentMp;

	/**
	 * 캐릭터의 현재의 MP를 돌려준다.
	 * @return 현재의 MP
	 */
	public int getCurrentMp() {
		return _currentMp;
	}
	/**
	 * 캐릭터의 MP를 설정한다.
	 * @param i 캐릭터의 새로운 MP
	 */
	// 특수한 처리가 있는 경우는 여기를 오바라이드(패킷 송신등 )
	public void setCurrentMp(int i) {
		_currentMp = i;
		if (_currentMp >= getMaxMp()) {
			_currentMp = getMaxMp();
		}
	}
	/**
	 * 캐릭터의 MP를 설정한다.
	 * @param i 캐릭터의 새로운 MP
	 */
	public void setCurrentMpDirect(int i) {
		_currentMp = i;
	}
	/**
	 * 캐릭터의 잠상태를 돌려준다.
	 * @return 잠상태를 나타내는 값. 잠상태이면 true.
	 */
	public boolean isSleeped() {
		return _sleeped;
	}
	/**
	 * 캐릭터의 잠상태를 설정한다.
	 * @param sleeped 잠상태를 나타내는 값. 잠상태이면 true.
	 */
	public void setSleeped(boolean sleeped) {
		_sleeped = sleeped;
	}
	/**
	 * 캐릭터의 마비 상태를 돌려준다.
	 * @return 마비 상태를 나타내는 값.마비 상태이면 true.
	 */
	public boolean isParalyzed() {
		return _paralyzed;
	}
	/**
	 * 캐릭터의 마비 상태를 설정한다.
	 * @param i 마비 상태를 나타내는 값.마비 상태이면 true.
	 */
	public void setParalyzed(boolean paralyzed) {
		_paralyzed = paralyzed;
	}

	L1Paralysis _paralysis;
	public L1Paralysis getParalysis() {
		return _paralysis;
	}
	public void setParalaysis(L1Paralysis p) {
		_paralysis = p;
	}
	public void cureParalaysis() {
		if (_paralysis != null) {
			_paralysis.cure();
		}
	}
	/**
	 * 캐릭터의 가시 범위에 있는 플레이어에, 패킷을 송신한다.
	 * @param packet 송신하는 패킷을 나타내는 ServerBasePacket 오브젝트.
	 */
	public void broadcastPacket(ServerBasePacket packet) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false);
		}
		packet.clear();
	}
	public void broadcastPacket(ServerBasePacket packet, boolean isClear) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false);
		}
		if (isClear) {
			packet.clear();
		}
	}
	public void broadcastPacket(ServerBasePacket packet, L1Character target) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false);
		}
		packet.clear();
	}
	/**
	 * 캐릭터의 가시 범위에 있는 플레이어에, 패킷을 송신한다. 다만 타겟의 화면내에는 송신하지 않는다.
	 * @param packet 송신하는 패킷을 나타내는 ServerBasePacket 오브젝트.
	 */
	public void broadcastPacketExceptTargetSight(ServerBasePacket packet, L1Character target) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerExceptTargetSight(this, target)) {
			if (pc.knownsObject(this)) {
				pc.sendPackets(packet, false);
			}
		}
		packet.clear();
	}
	/**
	 * 캐릭터의 50 매스 이내에 있는 플레이어에, 패킷을 송신한다.
	 * @param packet 송신하는 패킷을 나타내는 ServerBasePacket 오브젝트.
	 */
	public void wideBroadcastPacket(ServerBasePacket packet) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this, 50)) {
			pc.sendPackets(packet, false);
		}
		packet.clear();
	}	
	public void sendForMe(int effect_id) {
		if (effect_id > 0) {
			sendPackets(S_SkillSound.get(getId(), effect_id));
		}
	}
	public void sendAll(int effect_id) {
		if (effect_id > 0) {
			S_SkillSound sound = S_SkillSound.get(getId(), effect_id);
			sendPackets(sound, false);
			broadcastPacket(sound);
		}
	}
	public void sendActionAll(int action_id) {
		if (action_id > 0) {
			S_DoActionGFX gfx = S_DoActionGFX.get(getId(), action_id);
			sendPackets(gfx, false);
			broadcastPacket(gfx);
		}
	}
	public void sendPackets(ServerBasePacket pck, boolean clear) {
		if (clear)
			pck.clear();
	}
	public void sendPackets(ServerBasePacket sbp) {
		sbp.clear();
		sbp = null;
	}
	/**
	 * 캐릭터의 정면의 좌표를 돌려준다.
	 * @return 정면의 좌표
	 */
	public int[] getFrontLoc() {
		int[] loc = new int[2];
		int x = getX();
		int y = getY();
		int heading = getHeading();
		switch (heading) {
		case 0: {
			y--;
		}
		break;
		case 1: {
			x++;
			y--;
		}
		break;
		case 2: {
			x++;
		}
		break;
		case 3: {
			x++;
			y++;
		}
		break;
		case 4: {
			y++;
		}
		break;
		case 5: {
			x--;
			y++;
		}
		break;
		case 6: {
			x--;
		}
		break;
		case 7: {
			x--;
			y--;
		}
		break;
		default:
			break;
		}
		loc[0] = x;
		loc[1] = y;
		return loc;
	}
	/**
	 * 지정된 좌표에 대할 방향을 돌려준다.
	 * @param tx 좌표의 X치
	 * @param ty 좌표의 Y치
	 * @return 지정된 좌표에 대할 방향
	 */
	public int targetDirection(int tx, int ty) {
		float dis_x = Math.abs(getX() - tx); // X방향의 타겟까지의 거리
		float dis_y = Math.abs(getY() - ty); // Y방향의 타겟까지의 거리
		float dis = Math.max(dis_x, dis_y); // 타겟까지의 거리
		if (dis == 0) {
			return getHeading(); // 같은 위치라면 지금 향하고 있는 방향을 반환과 구
		}
		int avg_x = (int) Math.floor((dis_x / dis) + 0.59f); // 상하 좌우가 조금 우선인 둥근
		int avg_y = (int) Math.floor((dis_y / dis) + 0.59f); // 상하 좌우가 조금 우선인 둥근

		int dir_x = 0;
		int dir_y = 0;
		if (getX() < tx) {
			dir_x = 1;
		}
		if (getX() > tx) {
			dir_x = -1;
		}
		if (getY() < ty) {
			dir_y = 1;
		}
		if (getY() > ty) {
			dir_y = -1;
		}

		if (avg_x == 0) {
			dir_x = 0;
		}
		if (avg_y == 0) {
			dir_y = 0;
		}

		if (dir_x == 1 && dir_y == -1) {
			return 1; // 상
		}
		if (dir_x == 1 && dir_y == 0) {
			return 2; // 우상
		}
		if (dir_x == 1 && dir_y == 1) {
			return 3; // 오른쪽
		}
		if (dir_x == 0 && dir_y == 1) {
			return 4; // 우하
		}
		if (dir_x == -1 && dir_y == 1) {
			return 5; // 하
		}
		if (dir_x == -1 && dir_y == 0) {
			return 6; // 좌하
		}
		if (dir_x == -1 && dir_y == -1) {
			return 7; // 왼쪽
		}
		if (dir_x == 0 && dir_y == -1) {
			return 0; // 좌상
		}
		return getHeading(); // 여기에는 오지 않는다.(은)는 두
	}

	/**
	 * 지정된 좌표까지의 직선상에, 장애물이 존재*하지 않는가*를 돌려준다.
	 * @param tx 좌표의 X치
	 * @param ty 좌표의 Y치
	 * @return 장애물이 없으면 true, 어느 false를 돌려준다.
	 */
	public boolean glanceCheck(int tx, int ty) {
		L1Map map = getMap();
		int chx = getX();
		int chy = getY();
		for (int i = 0; i < 15; i++) {
			if ((chx == tx && chy == ty) || (chx == tx && chy + 1 == ty)// 0 0 0 1
					|| (chx == tx && chy - 1 == ty) || (chx + 1 == tx && chy == ty)// 0 -1 1 0
					|| (chx + 1 == tx && chy + 1 == ty) || (chx + 1 == tx && chy - 1 == ty)// 1 1 1 -1
					|| (chx - 1 == tx && chy == ty) || (chx - 1 == tx && chy + 1 == ty)// -1 0 -1 1
					|| (chx - 1 == tx && chy - 1 == ty)) { // -1 -1
				break;
			}

			if (!map.isArrowPassable(chx, chy, targetDirection(tx, ty))) {
				return false;
			}
			if (chx < tx && chy == ty) {
				chx++;
			} else if (chx > tx && chy == ty) {
				chx--;
			} else if (chx == tx && chy < ty) {
				chy++;
			} else if (chx == tx && chy > ty) {
				chy--;
			} else if (chx < tx && chy < ty) {
				chx++;
				chy++;
			} else if (chx < tx && chy > ty) {
				chx++;
				chy--;
			} else if (chx > tx && chy < ty) {
				chx--;
				chy++;
			} else if (chx > tx && chy > ty) {
				chx--;
				chy--;
			}
		}

		return true;
	}
	/**
	 * 지정된 좌표에 공격 가능한가를 돌려준다.
	 * @param x     좌표의 X치.
	 * @param y     좌표의 Y치.
	 * @param range 공격 가능한 범위(타일수)
	 * @return 공격 가능하면 true, 불가능하면 false
	 */
	public boolean isAttackPosition(int x, int y, int range) {
		if (range >= 7) {// 원격 무기(7이상의 경우 기울기를 고려하면(자) 화면외에 나온다)
			if (getLocation().getTileDistance(new Point(x, y)) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(new Point(x, y)) > range)
				return false;
		}

		return glanceCheck(x, y);
	}
	public boolean isAttackPosition(L1Character target, int range) {
		if (range >= 7) {// 원격 무기(7이상의 경우 기울기를 고려하면(자) 화면외에 나온다)
			if (getLocation().getTileDistance(target.getLocation()) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(target.getLocation()) > range)
				return false;
		}

		return glanceCheck(target.getX(), target.getY()) && target.glanceCheck(getX(), getY());
	}
	/**
	 * 캐릭터의 인벤트리를 돌려준다.
	 * @return 캐릭터의 인벤트리를 나타내는, L1Inventory 오브젝트.
	 */
	public L1Inventory getInventory() {
		return null;
	}
	/**
	 * 캐릭터에, 새롭게 스킬 효과를 추가한다.
	 * @param skillId    추가하는 효과의 스킬 ID.
	 * @param timeMillis 추가하는 효과의 지속 시간.무한의 경우는 0.
	 */
	private void addSkillEffect(int skillId, int timeMillis) {
		SkillTimer timer = null;
		if (0 < timeMillis) {
			timer = new SkillTimer(this, skillId, timeMillis);
			timer.begin();
		}
		_skillEffect.put(skillId, timer);
	}
	/**
	 * 캐릭터에, 스킬 효과를 설정한다.<br>
	 * 중복 하는 스킬이 없는 경우는, 새롭게 스킬 효과를 추가한다.<br>
	 * 중복 하는 스킬이 있는 경우는, 나머지 효과 시간과 파라미터의 효과 시간의 긴 (분)편을 우선해 설정한다.
	 * @param skillId    설정하는 효과의 스킬 ID.
	 * @param timeMillis 설정하는 효과의 지속 시간.무한의 경우는 0.
	 */
	public void setSkillEffect(int skillId, int timeMillis) {
		if (hasSkillEffect(skillId)) {
			int remainingTimeMills = getSkillEffectTimeSec(skillId) * 1000;

			// 남은 시간이 유한해, 파라미터의 효과 시간이 긴가 무한의 경우는 덧쓰기한다.
			if (remainingTimeMills >= 0 && (remainingTimeMills < timeMillis || timeMillis == 0)) {
				killSkillEffectTimer(skillId);
				addSkillEffect(skillId, timeMillis);
			}
		} else {
			addSkillEffect(skillId, timeMillis);
		}
	}
	/**
	 * 캐릭터로부터, 스킬 효과를 삭제한다.
	 * @param skillId 삭제하는 효과의 스킬 ID
	 */
	public void removeSkillEffect(int skillId) {
		SkillTimer timer = _skillEffect.remove(skillId);
		if (timer != null) {
			timer.end();
		}
	}
	/**
	 * 캐릭터로부터, 스킬 효과의 타이머를 삭제한다. 스킬 효과는 삭제되지 않는다.
	 * @param skillId 삭제하는 타이머의 스킬 ID
	 */
	public void killSkillEffectTimer(int skillId) {
		SkillTimer timer = _skillEffect.remove(skillId);
		if (timer != null) {
			timer.kill();
		}
	}
	/**
	 * 캐릭터로부터, 모든 스킬 효과 타이머를 삭제한다.스킬 효과는 삭제되지 않는다.
	 */
	public void clearSkillEffectTimer() {
		for (SkillTimer timer : _skillEffect.values()) {
			if (timer != null) {
				timer.kill();
			}
		}
		_skillEffect.clear();
	}
	/**
	 * 캐릭터에, 스킬 효과가 걸려있을까를 돌려준다.
	 * @param skillId 조사하는 효과의 스킬 ID.
	 * @return 마법 효과가 있으면 true, 없으면 false.
	 */
	public boolean hasSkillEffect(int skillId) {
		return _skillEffect.containsKey(skillId);
	}
	/**
	 * 캐릭터의 스킬 효과의 지속 시간을 돌려준다.
	 * @param skillId 조사하는 효과의 스킬 ID
	 * @return 스킬 효과의 남은 시간(초).스킬이 걸리지 않은가 효과 시간이 무한의 경우,-1.
	 */
	public int getSkillEffectTimeSec(int skillId) {
		SkillTimer timer = _skillEffect.get(skillId);
		if (timer == null) {
			return -1;
		}
		return timer.getRemainingTime();
	}
	private long _skillDelay;
	public void setSkillDelay(int delay) {
		this._skillDelay = (System.currentTimeMillis() + delay);
	}
	public boolean isSkillDelay() {
		return this._skillDelay > System.currentTimeMillis();
	}
	/**
	 * 캐릭터에, 아이템 지연을 추가한다.
	 * @param delayId 아이템 지연 ID. 통상의 아이템이면 0, 인비지비리티크로크, 바르로그브랏디크로크이면 1.
	 * @param timer   지연 시간을 나타내는, L1ItemDelay.ItemDelayTimer 오브젝트.
	 */
	public void addItemDelay(int delayId, L1ItemDelay.ItemDelayTimer timer) {
		_itemdelay.put(delayId, timer);
	}
	/**
	 * 캐릭터로부터, 아이템 지연을 삭제한다.
	 * @param delayId 아이템 지연 ID. 통상의 아이템이면 0, 인비지비리티크로크, 바르로그브랏디크로크이면 1.
	 */
	public void removeItemDelay(int delayId) {
		_itemdelay.remove(delayId);
	}
	/**
	 * 캐릭터에, 아이템 지연이 있을까를 돌려준다.
	 * @param delayId 조사하는 아이템 지연 ID. 통상의 아이템이면 0, 인비지비리티크로크, 바르로그브랏디 클로크이면 1.
	 * @return 아이템 지연이 있으면 true, 없으면 false.
	 */
	public boolean hasItemDelay(int delayId) {
		return _itemdelay.containsKey(delayId);
	}
	/**
	 * 캐릭터의 아이템 지연 시간을 나타내는, L1ItemDelay.ItemDelayTimer를 돌려준다.
	 * @param delayId 조사하는 아이템 지연 ID. 통상의 아이템이면 0, 인비지비리티크로크, 바르로그브랏디 클로크이면 1.
	 * @return 아이템 지연 시간을 나타내는, L1ItemDelay.ItemDelayTimer.
	 */
	public L1ItemDelay.ItemDelayTimer getItemDelayTimer(int delayId) {
		return _itemdelay.get(delayId);
	}
	/**
	 * 캐릭터에, 새롭게 애완동물, 사몬몬스타, 테이밍몬스타, 혹은 클리에 실 좀비를 추가한다.
	 * @param npc 추가하는 Npc를 나타내는, L1NpcInstance 오브젝트.
	 */
	public void addPet(L1NpcInstance npc) {
		_petlist.put(npc.getId(), npc);
	}

	/**
	 * 캐릭터로부터, 애완동물, 사몬몬스타, 테이밍몬스타, 혹은 클리에 실 좀비를 삭제한다.
	 * @param npc 삭제하는 Npc를 나타내는, L1NpcInstance 오브젝트.
	 */
	public void removePet(L1NpcInstance npc) {
		_petlist.remove(npc.getId());
	}

	/**
	 * 캐릭터의 애완동물 리스트를 돌려준다.
	 * @return 캐릭터의 애완동물 리스트를 나타내는, HashMap 오브젝트.이 오브젝트의 Key는 오브젝트 ID, Value는
	 *         L1NpcInstance.
	 */
	public Map<Integer, L1NpcInstance> getPetList() {
		return _petlist;
	}
	/**
	 * 캐릭터에 마법 인형을 추가한다.
	 * 	@param doll 추가하는 doll를 나타내는, L1DollInstance 오브젝트.
	 */
	public void addDoll(DollInstance doll) {
		_dolllist.put(doll.getId(), doll);
	}

	/**
	 * 캐릭터로부터 마법 인형을 삭제한다.
	 * @param doll 삭제하는 doll를 나타내는, L1DollInstance 오브젝트.
	 */
	public void removeDoll(DollInstance doll) {
		_dolllist.remove(doll.getId());
	}
	public DollInstance getMagicDoll() {
		return _doll;
	}
	public void setMagicDoll(DollInstance doll) {
		_doll = doll;
	}
	public Map<Integer, DollInstance> getDollList() {
		return _dolllist;
	}
	public int getDollListSize() {
		synchronized (_dolllist) {
			return _dolllist.size();
		}
	}
	/**
	 * 캐릭터에, 독을 추가한다.
	 * @param poison 독을 나타내는, L1Poison 오브젝트.
	 */
	public void setPoison(Poison poison) {
		_poison = poison;
	}
	/**
	 * 캐릭터의 독을 치료한다.
	 */
	public void curePoison() {
		if (_poison == null) {
			return;
		}
		_poison.cure();
	}
	/**
	 * 캐릭터의 독상태를 돌려준다.
	 * @return 캐릭터의 독을 나타내는, L1Poison 오브젝트.
	 */
	public Poison getPoison() {
		return _poison;
	}

	/**
	 * 캐릭터에 독의 effect를 부가한다
	 * @param effectId
	 * @see S_Poison#S_Poison(int, int)
	 */
	public void setPoisonEffect(int effectId) {
		broadcastPacket(new S_Poison(getId(), effectId));
	}

	/**
	 * 캐릭터가 존재하는 좌표가, 어느 존에 속하고 있을까를 돌려준다.
	 * @return 좌표의 존을 나타내는 값.세이프티 존이면 1, 컴배트 존이면―1, 노멀 존이면 0.
	 */
	public int getZoneType() {
		if (MapChangeTable.getInstance().isMapZoneChange(getMapId())) {
			int zonetype = MapChangeTable.getInstance().getMapZoneType(getMapId());
			return zonetype;
		}
		if (getMap().isSafetyZone(getLocation())) {
			if (getMapId() == 13053) {
				return -1;
			}
			return 1;
		}
		if (getMap().isCombatZone(getLocation())) {
			return -1;
		}
		return 0;
	}

	private int _exp; // ● 경험치

	/**
	 * 캐릭터가 보관 유지하고 있는 경험치를 돌려준다.
	 * @return 경험치.
	 */
	public int getExp() {
		return _exp;
	}
	/**
	 * 캐릭터가 보관 유지하는 경험치를 설정한다.
	 * @param exp 경험치.
	 */
	public void setExp(int exp) {
		_exp = exp;
	}
	// ■■■■■■■■■■ L1PcInstance에 이동하는 프롭퍼티 ■■■■■■■■■■
	private final List<L1Object> _knownObjects = new CopyOnWriteArrayList<L1Object>();
	private final List<L1PcInstance> _knownPlayer = new CopyOnWriteArrayList<L1PcInstance>();

	/**
	 * 지정된 오브젝트를, 캐릭터가 인식하고 있을까를 돌려준다.
	 * @param obj 조사하는 오브젝트.
	 * @return 오브젝트를 캐릭터가 인식하고 있으면 true, 하고 있지 않으면 false. 자기 자신에 대해서는 false를 돌려준다.
	 */
	public boolean knownsObject(L1Object obj) {
		return _knownObjects.contains(obj);
	}
	/**
	 * 캐릭터가 인식하고 있는 모든 오브젝트를 돌려준다.
	 * @return 캐릭터가 인식하고 있는 오브젝트를 나타내는 L1Object가 격납된 ArrayList.
	 */
	public List<L1Object> getKnownObjects() {
		return _knownObjects;
	}
	/**
	 * 캐릭터가 인식하고 있는 모든 플레이어를 돌려준다.
	 * @return 캐릭터가 인식하고 있는 오브젝트를 나타내는 L1PcInstance가 격납된 ArrayList.
	 */
	public List<L1PcInstance> getKnownPlayers() {
		return _knownPlayer;
	}
	/**
	 * 캐릭터에, 새롭게 인식하는 오브젝트를 추가한다.
	 * @param obj 새롭게 인식하는 오브젝트.
	 */
	public void addKnownObject(L1Object obj) {
		if (!_knownObjects.contains(obj)) {
			_knownObjects.add(obj);
			if (obj instanceof L1PcInstance) {
				_knownPlayer.add((L1PcInstance) obj);
			}
		}
	}

	/**
	 * 캐릭터로부터, 인식하고 있는 오브젝트를 삭제한다.
	 * @param obj 삭제하는 오브젝트.
	 */
	public void removeKnownObject(L1Object obj) {
		if (_knownObjects.contains(obj))
			_knownObjects.remove(obj);
		if (obj instanceof L1PcInstance) {
			if (_knownPlayer.contains((L1PcInstance) obj))
				_knownPlayer.remove((L1PcInstance) obj);
		}
	}
	/**
	 * 캐릭터로부터, 모든 인식하고 있는 오브젝트를 삭제한다.
	 */
	public void removeAllKnownObjects() {
		if (_knownObjects.size() > 0)
			_knownObjects.clear();
		if (_knownPlayer.size() > 0)
			_knownPlayer.clear();
	}

	// ■■■■■■■■■■ 프롭퍼티 ■■■■■■■■■■

	private String _name; // ● 이름
	public String getName() {
		return _name;
	}
	public void setName(String s) {
		_name = s;
	}
	private int _level; // ● 레벨
	public synchronized int getLevel() {
		return _level;
	}
	public synchronized void setLevel(long level) {
		_level = (int) level;
	}
	public AC getAC() {
		return ac;
	}
	private int _maxHp = 0;
	private int _trueMaxHp = 0; // ● 진정한 MAXHP
	public int getMaxHp() {
		return _maxHp;
	}
	public void setMaxHp(int hp) {
		_trueMaxHp = hp;
		_maxHp = IntRange.ensure(_trueMaxHp, 1, 32767);
		_currentHp = Math.min(_currentHp, _maxHp);
	}
	public void addMaxHp(int i) {
		setMaxHp(_trueMaxHp + i);
		if(this != null) {
			if(this instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) this;
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()), true);
				if (pc.isInParty()) {
					pc.getParty().updateMiniHP(pc);
				}
			}		
		}	
	}

	private int _maxMp = 0;
	private int _trueMaxMp = 0; // ● 진정한 MAXMP
	public int getMaxMp() {
		return _maxMp;
	}
	public void setMaxMp(int mp) {
		_trueMaxMp = mp;
		_maxMp = IntRange.ensure(_trueMaxMp, 0, 500000000);
		_currentMp = Math.min(_currentMp, _maxMp);
	}
	public void addMaxMp(int i) {
		setMaxMp(_trueMaxMp + i);
		if(this != null) {
			if(this instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) this;
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()), true);
			}		
		}	
	}
	private int _addAttrKind; // 엘리멘탈 폴 다운으로 감소한 속성의 종류
	public int getAddAttrKind() {
		return _addAttrKind;
	}
	public void setAddAttrKind(int i) {
		_addAttrKind = i;
	}
	public Light getLight() {
		return light;
	}
	public Ability getAbility() {
		return ability;
	}
	public Resistance getResistance() {
		return resistance;
	}
	private int _meleeDmg = 0;
	private int _trueDmgup = 0;
	private int _bowDmgup = 0;
	private int _trueBowDmgup = 0;
	/**
	 * 모든 방어구, 스킬, 인형 에 대한 근거리 데미지를 종합적으로 계산한다. (엔피시 포함)
	 * @return
	 */
	public int getMeleeDmg() {
		return _meleeDmg;
	}  
	/**
	 * 모든 방어구, 스킬, 인형 에 대한 근거리 데미지를 종합적으로 계산한다. (엔피시 포함)
	 * @return
	 */
	public void addMeleeDmg(int i) {
		_trueDmgup += i;
		if (_trueDmgup >= 127) {
			_meleeDmg = 127;
		} else if (_trueDmgup <= -128) {
			_meleeDmg = -128;
		} else {
			_meleeDmg = _trueDmgup;
		}
	} 
	/**
	 * 모든 방어구, 스킬, 인형 에 대한 원거리 데미지를 종합적으로 계산한다. (엔피시 포함)
	 * @return
	 */
	public int getRangedDmg() {
		return _bowDmgup;
	} // 사용할 때

	/**
	 * 모든 방어구, 스킬, 인형 에 대한 원거리 데미지를 종합적으로 계산한다. (엔피시 포함)
	 * @return
	 */
	public void addRangedDmg(int i) {
		_trueBowDmgup += i;
		if (_trueBowDmgup >= 127) {
			_bowDmgup = 127;
		} else if (_trueBowDmgup <= -128) {
			_bowDmgup = -128;
		} else {
			_bowDmgup = _trueBowDmgup;
		}
	}
	public int getMeleeHit() {
		return _hitup;
	} 
	public void addMeleeHit(int i) {
		_trueHitup += i;
		if (_trueHitup >= 127) {
			_hitup = 127;
		} else if (_trueHitup <= -128) {
			_hitup = -128;
		} else {
			_hitup = _trueHitup;
		}
	} 
	public int getRangedHit() {
		return _bowHitup;
	} 
	public void addRangedHit(int i) {
		_trueBowHitup += i;
		if (_trueBowHitup >= 127) {
			_bowHitup = 127;
		} else if (_trueBowHitup <= -128) {
			_bowHitup = -128;
		} else {
			_bowHitup = _trueBowHitup;
		}
	}
	public int getSp() {
		return getTrueSp() + _sp;
	}
	public int getTrueSp() {
		return getMagicLevel() + getMagicBonus();
	}
	public void addSp(int i) {
		_sp += i;
		if(this != null) {
			if(this instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) this;
				pc.sendPackets(new S_SPMR(pc), true);
			}			
		}
	}
	private boolean _isDead; // ● 사망 상태
	public boolean isDead() {
		return _isDead;
	}
	public void setDead(boolean flag) {
		_isDead = flag;
	}
	private int _status; // ● 상태?
	public int getStatus() {
		return _status;
	}
	public void setStatus(int i) {
		_status = i;
	}
	private String _title; // ● 타이틀
	public String getTitle() {
		return _title;
	}
	public void setTitle(String s) {
		_title = s;
	}
	private int _lawful; // ● 아라이먼트
	public int getLawful() {
		return _lawful;
	}
	public void setLawful(int i) {
		_lawful = i;
	}
	public synchronized void addLawful(int i) {
		if (Config.STANDBY_SERVER) {
			return;
		}
		_lawful += i;
		if (_lawful > 32767) {
			_lawful = 32767;
		} else if (_lawful < -32768) {
			_lawful = -32768;
		}
	}
	// 무기에 대한 데미지 계산하기 위해 만듬 
	// getMeleeDmg 와 통일되버리면 무기데미지가 두번 들어가는 문제가 있어서 따로 분리함.
	private int _meleeWeaponDmg = 0;
	public int getMeleeWeaponDmg() {
		return _meleeWeaponDmg;
	} 
	public void addMeleeWeaponDmg(int i) {
		_meleeWeaponDmg += i;
	} 
	private int _rangedWeaponDmg = 0;
	public int getRangedWeaponDmg() {
		return _rangedWeaponDmg;
	} 
	public void addRangedWeaponDmg(int i) {
		_rangedWeaponDmg += i;
	}
	private int _heading; // ● 방향 0.좌상 1.상 2.우상 3.오른쪽 4.우하 5.하 6.좌하 7.좌
	public int getHeading() {
		return _heading;
	}
	public void setHeading(int i) {
		_heading = i;
	}
	private int _moveSpeed; // ● 스피드 0.통상 1.헤이 파업 2.슬로우
	public int getMoveSpeed() {
		return _moveSpeed;
	}
	public void setMoveSpeed(int i) {
		_moveSpeed = i;
	}
	private int _braveSpeed; // ● 치우침 이브 상태 0.통상 1.치우침 이브
	public int getBraveSpeed() {
		return _braveSpeed;
	}
	public void setBraveSpeed(int i) {
		_braveSpeed = i;
	}
	public void receiveDamage(L1Character attacker, int damage) {}

	protected SpriteInformation _currentSpriteInfo;
	public int getCurrentSpriteId() {
		if (_currentSpriteInfo != null) { 
			switch (_currentSpriteInfo.getSpriteId()) {
			case 129:
			case 167:
			case 1583:
			case 9510:
			case 12589:
			case 16008:
			case 17015:
				return 1120;
			}
			if (_currentSpriteInfo.getSpriteId() > 18436) {
				return 1120;
			}
		}
		return _currentSpriteInfo == null ? 1120 : _currentSpriteInfo.getSpriteId();
	}
	public SpriteInformation getCurrentSprite() {
		return _currentSpriteInfo;
	}
	public void setCurrentSprite(int spriteId) {
		if (!equalsCurrentSprite(spriteId)) {
			_currentSpriteInfo = SpriteInformationLoader.getInstance().get(spriteId);
		}
	}
	public boolean equalsCurrentSprite(int compareSpriteId) {
		return getCurrentSpriteId() == compareSpriteId;
	}
	public long getCurrentSpriteInterval(EActionCodes actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}
	public long getCurrentSpriteInterval(int actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	} 
	public int getMagicLevel() {
		return getLevel() / 4;
	}
	/**
	 * 스텟에 의한 매직보너스 
	 * @param pc
	 * @return
	 */
	public int getMagicBonus() {
		int i = getAbility().getTotalInt();
		return CalcUtil.getMagicBonusStat(i);
	}
	public boolean isInvisble() {
		return (hasSkillEffect(SkillId.인비지블리티) || hasSkillEffect(SkillId.블라인드_하이딩));
	}
	public void healHp(int pt) {
		setCurrentHp(getCurrentHp() + pt);
	}
	private int _karma;
	/**
	 * 캐릭터가 보관 유지하고 있는 업을 돌려준다.
	 * @return 업.
	 */
	public int getKarma() {
		return _karma;
	}
	/**
	 * 캐릭터가 보관 유지하는 업을 설정한다.
	 * @param karma 업.
	 */
	public void setKarma(int karma) {
		_karma = karma;
	} 
	private int _poly_number;
	public int getPolyBuffId() {
		return _poly_number;
	}
	public void setPolyBuffId(int i) {
		_poly_number = i;
	}
	public Timestamp getItemDelayLogTime(int ItemId) {
		ItemDelayTimer timer = _item_delay.get(ItemId);
		if (timer == null) {
			return null;
		}
		return timer.LogDelayTime();
	}
	// c_itemuse 에서는 이걸로 사용한다.
	public Timestamp getItemDelayLogTime(ItemInstance item) {
		int itemId = SameTypeCheck(item);
		ItemDelayTimer timer = _item_delay.get(itemId);
		if (timer == null) {
			return null;
		}
		return timer.LogDelayTime();
	}
	// 아이템 타입에 따른 조건.
	public int SameTypeCheck(ItemInstance item) {
		int itemid = item.getId();

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 6) {
			// 물약 계열 아이템들은 아이템아이디를 체력 회복제로 판별한다.
			itemid = 40010;
		}
		return itemid;
	}
	public boolean hasItemDelayTime(int ItemId) {
		return _item_delay.containsKey(ItemId);
	}
	public boolean hasItemDelayTime(ItemInstance item) {
		if (item == null) {
			return false;
		}
		int itemId = SameTypeCheck(item);	
		return _item_delay.containsKey(itemId);
	}
	public void sendShape(int poly) {
		S_ChangeShape shape = new S_ChangeShape(getId(), poly, 0);
		sendPackets(shape, false);
		broadcastPacket(shape);
	}

	/////////////////// 킬 ,데스 카운터 관련  //////////////////////
	private int _Kills;
	public int getKills() {
		return _Kills;
	}
	public void setKills(int Kills) {
		_Kills = Kills;
	}
	private int _Deaths;
	public int getDeaths() {
		return _Deaths;
	}
	public void setDeaths(int Deaths) {
		_Deaths = Deaths;
	}	
	/////////////////// 속도 스킬 , 아이템 관련 //////////////////////

	public boolean isFastMovable() {
		return (hasSkillEffect(SkillId.홀리_워크) || hasSkillEffect(SkillId.무빙_악셀레이션) || hasSkillEffect(SkillId.윈드_워크));
	}
	public boolean isBrave() {
		return hasSkillEffect(SkillId.상태_2단_가속);
	}
	public boolean isElfBrave() {
		return hasSkillEffect(SkillId.상태_요정_2단_가속);
	}
	public boolean isDragonPearl() {
		return hasSkillEffect(SkillId.상태_드래곤_진주_3단계);
	}
	public boolean isSlow() {
		return hasSkillEffect(SkillId.슬로우);
	}
	public boolean isWindShackle() {
		return hasSkillEffect(SkillId.윈드_셰클);
	}
	public boolean isParal() {
		return hasSkillEffect(SkillId.커스_패럴라이즈) || hasSkillEffect(SkillId.상태_커스_패럴라이즈) || hasSkillEffect(SkillId.상태_커스_패럴라이즈2) || isParalyzed();
	}
	public boolean isSlows() {
		return hasSkillEffect(SkillId.인탱글) || hasSkillEffect(SkillId.매스_슬로우);
	}
	public boolean isHaste() {
		return (hasSkillEffect(SkillId.상태_1단_가속) || hasSkillEffect(SkillId.헤이스트) || hasSkillEffect(SkillId.그레이터_헤이스트) || getMoveSpeed() == 1);
	}

	private int _magicHit;
	public int getMagicHit() {
		return _magicHit;
	}
	public void addMagicHit(int magicHit) {
		_magicHit += magicHit;
	}
}
