/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.model.Instance;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Pet;
import l1j.server.Templates.L1PetType;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_NpcChatPacket;
import l1j.server.server.serverpackets.S_PetMenuPacket;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_WorldObject;

public class PetInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private static Random _random = new Random();

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		if (_currentPetStatus == 3) { // ● 휴게의 경우
			return true;
		} else if (_currentPetStatus == 4) { // ● 배치의 경우
			if (_petMaster != null
					&& _petMaster.getMapId() == getMapId()
					&& getLocation().getTileLineDistance(
							_petMaster.getLocation()) < 5) {
				int dir = targetReverseDirection(_petMaster.getX(), _petMaster
						.getY());
				dir = checkObject(getX(), getY(), getMapId(), dir);
				setDirectionMove(dir);
				//setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
			} else { // 주인을 잃을까 5 매스 이상은 될 수 있으면(자) 휴게 상태에
				_currentPetStatus = 3;
				return true;
			}
		} else if (_currentPetStatus == 5) { // ● 경계의 경우는 홈에
			if (Math.abs(getHomeX() - getX()) > 1
					|| Math.abs(getHomeY() - getY()) > 1) {
				int dir = moveDirection(getHomeX(), getHomeY());
				if (dir == -1) { // 홈이 너무 멀어지고 있으면(자) 현재지가 홈
					setHomeX(getX());
					setHomeY(getY());
				} else {
					setDirectionMove(dir);
				//	setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
				}
			}
		} else if (_currentPetStatus == 7) { // ● 펫의 피리로 주인의 슬하로
			if (_petMaster != null
					&& _petMaster.getMapId() == getMapId()
					&& getLocation().getTileLineDistance(
							_petMaster.getLocation()) <= 1) {
				_currentPetStatus = 3;
				return true;
			}
			int locx = _petMaster.getX() + _random.nextInt(1);
			int locy = _petMaster.getY() + _random.nextInt(1);
			int dir = moveDirection(locx, locy);
			if (dir == -1) { // 주인을 잃을까는 될 수 있으면(자) 그 자리에서 휴게 상태에
				_currentPetStatus = 3;
				return true;
			}
			setDirectionMove(dir);
			//setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
		} else if (_petMaster != null && _petMaster.getMapId() == getMapId()) { // ●
			// 주인을 추적
			if (getLocation().getTileLineDistance(_petMaster.getLocation()) > 2) {
				int dir = moveDirection(_petMaster.getX(), _petMaster.getY());
				if (dir == -1) { // 주인이 너무 떨어지면(자) 휴게 상태에
					_currentPetStatus = 3;
					return true;
				}
				setDirectionMove(dir);
			//	setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
			}
		} else { // ● 주인을 잃으면(자) 휴게 상태에
			_currentPetStatus = 3;
			return true;
		}
		return false;
	}

	// 애완동물을 꺼냈을 경우
	public PetInstance(L1Npc template, L1PcInstance master, L1Pet l1pet) {
		super(template);
 
		_petMaster = master;
		_itemObjId = l1pet.get_itemobjid();
		_type = PetTypeTable.getInstance().get(template.get_npcId());

		// 스테이터스를 덧쓰기
		setId(l1pet.get_objid());
		setName(l1pet.get_name());
		setLevel(l1pet.get_level());
		// HPMP는 MAX로 한다
		setMaxHp(l1pet.get_hp());
		setCurrentHpDirect(l1pet.get_hp());
		setMaxMp(l1pet.get_mp());
		setCurrentMpDirect(l1pet.get_mp());
		setExp(l1pet.get_exp());
		setLawful(l1pet.get_lawful());
		setTempLawful(l1pet.get_lawful());

		setMaster(master);
		setX(master.getX() + _random.nextInt(5) - 2);
		setY(master.getY() + _random.nextInt(5) - 2);
		setMap(master.getMapId());
		setHeading(5);
		setLightSize(template.getLightSize());

		_currentPetStatus = 3;

		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
		master.addPet(this);
	}

	// 애완동물을 테임 했을 경우
	public PetInstance(L1NpcInstance target, L1PcInstance master, int itemid) {
		super(null);

		_petMaster = master;
		_itemObjId = itemid;
		_type = PetTypeTable.getInstance().get(
				target.getNpcTemplate().get_npcId());

		// 스테이터스를 덧쓰기
		setId(IdFactory.getInstance().nextId());
		setting_template(target.getNpcTemplate());
		setCurrentHpDirect(target.getCurrentHp());
		setCurrentMpDirect(target.getCurrentMp());
		setExp(750); // Lv.5의 EXP
		setExpPercent(0);
		setLawful(0);
		setTempLawful(0);

		setMaster(master);
		setX(target.getX());
		setY(target.getY());
		setMap(target.getMapId());
		setHeading(target.getHeading());
		setLightSize(target.getLightSize());
		setPetcost(6);
		setInventory(target.getInventory());
		target.setInventory(null);

		_currentPetStatus = 3;

		target.deleteMe();
		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}

		master.addPet(this);
		PetTable.getInstance().storeNewPet(target, getId(), itemid);
	}

	// 공격으로 HP를 줄일 때는 여기를 사용
	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (getCurrentHp() > 0) {
			if (damage > 0) { // 회복의 경우는 공격하지 않는다.
				setHate(attacker, 0); // 펫은 헤이트 없음
				removeSkillEffect(SkillId.포그_오브_슬리핑);
			}

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.setPetTarget (this);
			}

			int newHp = getCurrentHp() - damage;
			if (newHp <= 0) {
				death(attacker);
			} else {
				setCurrentHp(newHp);
			}
		} else if (!isDead()) { // 만약을 위해
			death(attacker);
		}
	}

	public synchronized void death(L1Character lastAttacker) {
		if (!isDead()) {
			setDead(true);
			setCurrentHp(0);

			getMap().setPassable(getLocation(), true);
			broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
		}
	}

	public void evolvePet(int new_itemobjid) {

		L1Pet l1pet = PetTable.getInstance().getTemplate(_itemObjId);
		if (l1pet == null) {
			return;
		}

		int newNpcId = _type.getNpcIdForEvolving();
		// 진화전의 maxHp, maxMp를 퇴피
		int tmpMaxHp = getMaxHp();
		int tmpMaxMp = getMaxMp();

		transform(newNpcId);
		_type = PetTypeTable.getInstance().get(newNpcId);

		setLevel(1);
		// HPMP를 원의 반으로 한다
		setMaxHp(tmpMaxHp / 2);
		setMaxMp(tmpMaxMp / 2);
		setCurrentHpDirect(getMaxHp());
		setCurrentMpDirect(getMaxMp());
		setExp(0);
		setExpPercent(0);

		// 인벤트리를 비운다
		getInventory().clearItems();

		// 낡은 애완동물을 DB로부터 지운다
		PetTable.getInstance().deletePet(_itemObjId);

		// 새로운 애완동물을 DB에 기입한다
		l1pet.set_itemobjid(new_itemobjid);
		l1pet.set_npcid(newNpcId);
		l1pet.set_name(getName());
		l1pet.set_level(getLevel());
		l1pet.set_hp(getMaxHp());
		l1pet.set_mp(getMaxMp());
		l1pet.set_exp(getExp());
		PetTable.getInstance().storeNewPet(this, getId(), new_itemobjid);

		_itemObjId = new_itemobjid;
	}

	// 해방 처리
	public void liberate() {
		MonsterInstance monster = new MonsterInstance(getNpcTemplate());
		monster.setId(IdFactory.getInstance().nextId());

		monster.setX(getX());
		monster.setY(getY());
		monster.setMap(getMapId());
		monster.setHeading(getHeading());
		monster.set_storeDroped(true);
		monster.setInventory(getInventory());
		setInventory(null);
		monster.setLevel(getLevel());
		monster.setMaxHp(getMaxHp());
		monster.setCurrentHpDirect(getCurrentHp());
		monster.setMaxMp(getMaxMp());
		monster.setCurrentMpDirect(getCurrentMp());

		_petMaster.getPetList().remove(getId());
		deleteMe();

		// DB와 PetTable로부터 삭제해, 펫트아뮤도 파기
		_petMaster.getInventory().removeItem(_itemObjId, 1);
		PetTable.getInstance().deletePet(_itemObjId);

		L1World.getInstance().storeObject(monster);
		L1World.getInstance().addVisibleObject(monster);
		for (L1PcInstance pc : L1World.getInstance()
				.getRecognizePlayer(monster)) {
			onPerceive(pc);
		}
	}

	// 애완동물의 소지품을 수집
	public void collect() {
		L1Inventory targetInventory = _petMaster.getInventory();
		List<ItemInstance> items = _inventory.getItems();
		int size = _inventory.getSize();
		for (int i = 0; i < size; i++) {
			ItemInstance item = items.get(0);
			if (_petMaster.getInventory().checkAddItem( // 용량 중량 확인 및 메세지 송신
					item, item.getCount()) == L1Inventory.OK) {
				_inventory.tradeItem(item, item.getCount(), targetInventory);
				_petMaster.sendPackets(new S_ServerMessage(143, getName(), item
						.getLogName())); // \f1%0이%1를 주었습니다.
			} else { // 가질 수  없기 때문에 발밑에 떨어뜨린다
				targetInventory = L1World.getInstance().getInventory(getX(),
						getY(), getMapId());
				_inventory.tradeItem(item, item.getCount(), targetInventory);
			}
		}
	}

	// restart시에 DROP를 지면에 떨어뜨린다
	public void dropItem() {
		L1Inventory targetInventory = L1World.getInstance().getInventory(
				getX(), getY(), getMapId());
		List<ItemInstance> items = _inventory.getItems();
		int size = _inventory.getSize();
		for (int i = 0; i < size; i++) {
			ItemInstance item = items.get(0);
			_inventory.tradeItem(item, item.getCount(), targetInventory);
		}
	}

	// 애완동물의 피리를 사용했다
	public void call() {
		int id = _type.getMessageId(L1PetType.getMessageNumber(getLevel()));
		if (id != 0) {
			broadcastPacket(new S_NpcChatPacket(this, "$" + id, 0));
		}

		setCurrentPetStatus(7); // 주인의 근처에서 휴게 상태
	}

	public void setTarget(L1Character target) {
		if (target != null
				&& (_currentPetStatus == 1 || _currentPetStatus == 2 || _currentPetStatus == 5)) {
			setHate(target, 0);
			if (!isAiRunning()) {
				startAI();
			}
		}
	}

	public void setMasterTarget(L1Character target) {
		if (target != null
				&& (_currentPetStatus == 1 || _currentPetStatus == 5)) {
			setHate(target, 0);
			if (!isAiRunning()) {
				startAI();
			}
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.pet(this, perceivedFrom));
		if (isDead()) {
			perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
		}
	}

	@Override
	public void onAction(L1PcInstance player) {
		L1Character cha = this.getMaster();
		L1PcInstance master = (L1PcInstance) cha;
		if (master.getTeleport()) { // 텔레포트 처리중
			return;
		}
		if (getZoneType() == 1) { // 공격받는 측이 세이프티 존
			Attack attack_mortion = new Attack(player, this); // 공격 모션 송신
			attack_mortion.action();
			return;
		}

		if (player.checkNonPvP(player, this)) {
			return;
		}

		Attack attack = new Attack(player, this);
		if (attack.calcHit()) {
			attack.calcDamage();
		}
		attack.action();
		attack.commit();
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (isDead()) {
			return;
		}
		if (_petMaster.equals(player)) {
			player.sendPackets(new S_PetMenuPacket(this, getExpPercent()));
			L1Pet l1pet = PetTable.getInstance().getTemplate(_itemObjId);
			// XXX 펫에 말을 건넬 때마다 DB에 기입할 필요는 없다
			if (l1pet != null) {
				l1pet.set_exp(getExp());
				l1pet.set_level(getLevel());
				l1pet.set_hp(getMaxHp());
				l1pet.set_mp(getMaxMp());
				PetTable.getInstance().storePet(l1pet); // DB에 기입해
			}
		}
	}

	@Override
	public void onFinalAction(L1PcInstance player, String action) {
		int status = actionType(action);
		if (status == 0) {
			return;
		}
		if (status == 6) {
			liberate(); // 펫의 해방
		} else {
			// 같은 주인의 애완동물 상태를 모두 갱신
			Object[] petList = _petMaster.getPetList().values().toArray();
			for (Object petObject : petList) {
				if (petObject instanceof PetInstance) { // 펫
					PetInstance pet = (PetInstance) petObject;
					pet.setCurrentPetStatus(status);
				}
			}
		}
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			useItem(USEITEM_HASTE, 100); // 100%의 확률로 헤이 파업 일부 사용
		}
		if (getCurrentHp() * 100 / getMaxHp() < 40) { // HP가 40%
			useItem(USEITEM_HEAL, 100); // 100%의 확률로 회복 일부 사용
		}
	}

	@Override
	public void onGetItem(ItemInstance item) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		Arrays.sort(healPotions);
		Arrays.sort(haestPotions);
		if (Arrays.binarySearch(healPotions, item.getItem().getItemId()) >= 0) {
			if (getCurrentHp() != getMaxHp()) {
				useItem(USEITEM_HEAL, 100);
			}
		} else if (Arrays
				.binarySearch(haestPotions, item.getItem().getItemId()) >= 0) {
			useItem(USEITEM_HASTE, 100);
		}
	}

	private int actionType(String action) {

		int status = 0;
		if (action.equalsIgnoreCase("aggressive")) { // 공격 태세
			status = 1; 
		} else if (action.equalsIgnoreCase("defensive")) { // 방어 태세
			status = 2;
		} else if (action.equalsIgnoreCase("stay")) { // 휴게
			status = 3;
		} else if (action.equalsIgnoreCase("extend")) { // 배치
			status = 4;
		} else if (action.equalsIgnoreCase("alert")) { // 경계
			status = 5;
		} else if (action.equalsIgnoreCase("dismiss")) { // 해산
			status = 6;
		} else if (action.equalsIgnoreCase("getitem")) { // 수집
			collect();
		}
		return status;
	}

	@Override
	public void setCurrentHp(int i) {
		int currentHp = i;
		if (currentHp >= getMaxHp()) {
			currentHp = getMaxHp();
		}
		setCurrentHpDirect(currentHp);

		if (getMaxHp() > getCurrentHp()) {
			startHpRegeneration();
		}

		if (_petMaster != null) {
			int HpRatio = 100 * currentHp / getMaxHp();
			L1PcInstance Master = _petMaster;
			Master.sendPackets(new S_HPMeter(getId(), HpRatio));
		}
	}

	@Override
	public void setCurrentMp(int i) {
		int currentMp = i;
		if (currentMp >= getMaxMp()) {
			currentMp = getMaxMp();
		}
		setCurrentMpDirect(currentMp);

		if (getMaxMp() > getCurrentMp()) {
			startMpRegeneration();
		}
	}

	public void setCurrentPetStatus(int i) {
		_currentPetStatus = i;
		if (_currentPetStatus == 5) {
			setHomeX(getX());
			setHomeY(getY());
		}
		if (_currentPetStatus == 7) {
			allTargetClear();
		}

		if (_currentPetStatus == 3) {
			allTargetClear();
		} else {
			if (!isAiRunning()) {
				startAI();
			}
		}
	}

	public int getCurrentPetStatus() {
		return _currentPetStatus;
	}

	public int getItemObjId() {
		return _itemObjId;
	}

	public void setExpPercent(int expPercent) {
		_expPercent = expPercent;
	}

	public int getExpPercent() {
		return _expPercent;
	}
	private int _currentPetStatus;
	private L1PcInstance _petMaster;
	private int _itemObjId;
	private L1PetType _type;
	private int _expPercent;

	public L1PetType getPetType() {
		return _type;
	}
}
