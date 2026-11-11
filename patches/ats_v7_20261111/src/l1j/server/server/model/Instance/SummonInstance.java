package l1j.server.server.model.Instance;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.Random;

import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_PetMenuPacket;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_WorldObject;

public class SummonInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	private ScheduledFuture<?> _summonFuture;
	private static final long SUMMON_TIME = 3600000L;
	private int _currentPetStatus;
	private boolean _tamed;
	private boolean _isReturnToNature = false;
	private static Random _random = new Random();

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		if (_currentPetStatus == 3) {
			// ● 휴게의 경우
			return true;
		} else if (_currentPetStatus == 4) {
			// ● 배치의 경우
			if (_master != null
					&& _master.getMapId() == getMapId()
					&& getLocation().getTileLineDistance(_master.getLocation()) < 5) {
				int dir = targetReverseDirection(_master.getX(), _master.getY());
				dir = checkObject(getX(), getY(), getMapId(), dir);
				setDirectionMove(dir);
			} else {
				// 주인을 잃을까 5 매스 이상은 될 수 있으면(자) 휴게 상태에
				_currentPetStatus = 3;
				return true;
			}
		} else if (_currentPetStatus == 5) {
			// ● 경계의 경우는 홈에
			if (Math.abs(getHomeX() - getX()) > 1
					|| Math.abs(getHomeY() - getY()) > 1) {
				int dir = moveDirection(getHomeX(), getHomeY());
				if (dir == -1) {
					// 홈이 너무 멀어지고 있으면(자) 현재지가 홈
					setHomeX(getX());
					setHomeY(getY());
				} else {
					setDirectionMove(dir);
					//setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
				}
			}
		} else if (_master != null && _master.getMapId() == getMapId()) {
			// ●주인을 추적
			if (getLocation().getTileLineDistance(_master.getLocation()) > 2) {
				int dir = moveDirection(_master.getX(), _master.getY());
				if (dir == -1) {
					// 주인이 너무 떨어지면(자) 휴게 상태에
					_currentPetStatus = 3;
					return true;
				} else {
					setDirectionMove(dir);
					//setSleepTime(calcSleepTime(getPassispeed(), MOVE_SPEED));
				}
			}
		} else {
			// ● 주인을 잃으면(자) 휴게 상태에
			_currentPetStatus = 3;
			return true;
		}
		return false;
	}

	// 1시간 계측용
	class SummonTimer implements Runnable {
		@Override
		public void run() {
			if (_destroyed) { // 이미 파기되어 있지 않은가 체크
				return;
			}
			if (_tamed) {
				// 테이밍몬스타, 클리에 실 좀비의 해방
				liberate();
			} else {
				// 사몬의 해산
				Death(null);
			}
		}
	}

	// 사몬몬스타용
	public SummonInstance(L1Npc template, L1Character master) {
		super(template);
		setId(IdFactory.getInstance().nextId());

		_summonFuture = GeneralThreadPool.getInstance().schedule(
				new SummonTimer(), SUMMON_TIME);

		setMaster(master);
		setX(master.getX() + _random.nextInt(5) - 2);
		setY(master.getY() + _random.nextInt(5) - 2);
		setMap(master.getMapId());
		setHeading(5);
		setLightSize(template.getLightSize());

		_currentPetStatus = 3;
		_tamed = false;

		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
		master.addPet(this);
	}

	// 테이밍몬스타, 클리에 실 좀비용
	public SummonInstance(L1NpcInstance target, L1Character master,
			boolean createZombie) {
		super(null);
		setId(IdFactory.getInstance().nextId());

		if (createZombie) {// 클리에 실 좀비
		
			L1Npc template = NpcTable.getInstance().getTemplate(45065).clone(); // 보통 좀비를 베이스로 작성
			setting_template(template);
		} else // 테이밍몬스타
		{
			setting_template(target.getNpcTemplate());
			setCurrentHpDirect(target.getCurrentHp());
			setCurrentMpDirect(target.getCurrentMp());
		}

		_summonFuture = GeneralThreadPool.getInstance().schedule(
				new SummonTimer(), SUMMON_TIME);

		setMaster(master);
		setX(target.getX());
		setY(target.getY());
		setMap(target.getMapId());
		setHeading(target.getHeading());
		setLightSize(target.getLightSize());
		setPetcost(6);

		if (target instanceof MonsterInstance
				&& !((MonsterInstance) target).is_storeDroped()) {
			DropTable.getInstance().setDrop(target, target.getInventory());
		}
		setInventory(target.getInventory());
		target.setInventory(null);

		_currentPetStatus = 3;
		_tamed = true;

		// 애완동물이 공격중이었던 경우 멈추게 한다
		for (L1NpcInstance each : master.getPetList().values()) {
			each.targetRemove(target);
		}

		target.deleteMe();
		L1World.getInstance().storeObject(this);
		L1World.getInstance().addVisibleObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
		master.addPet(this);
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { // 공격으로 HP를 줄일 때는 여기를 사용
		if (getCurrentHp() > 0) {
			if (damage > 0) {
				setHate(attacker, 0); // 사몬은 헤이트 없음
				removeSkillEffect(SkillId.포그_오브_슬리핑);
			}

			if (!isExsistMaster()) {
				_currentPetStatus = 1;
				setTarget(attacker);
			}

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.setPetTarget (this);
			}

			int newHp = getCurrentHp() - damage;
			if (newHp <= 0) {
				Death(attacker);
			} else {
				setCurrentHp(newHp);
			}
		} else if (!isDead()) // 만약을 위해
		{
			System.out.println("경고：사몬의 HP감소 처리가 올바르게 행해지지 않은 개소가 있습니다.※혹은 최초부터 HP0");
			Death(attacker);
		}
	}

	public synchronized void Death(L1Character lastAttacker) {
		if (!isDead()) {
			setDead(true);
			setCurrentHp(0);

			getMap().setPassable(getLocation(), true);

			// 아이템 해방 처리
			L1Inventory targetInventory = _master.getInventory();
			List<ItemInstance> items = _inventory.getItems();
			for (ItemInstance item : items) {
				if (_master.getInventory().checkAddItem( // 용량 중량 확인 및 메세지 송신
						item, item.getCount()) == L1Inventory.OK) {
					_inventory
							.tradeItem(item, item.getCount(), targetInventory);
					// \f1%0이%1를 주었습니다.
					((L1PcInstance) _master).sendPackets(new S_ServerMessage(
							143, getName(), item.getLogName()));
				} else { // 가질 수  없기 때문에 발밑에 떨어뜨린다
					targetInventory = L1World.getInstance().getInventory(
							getX(), getY(), getMapId());
					_inventory
							.tradeItem(item, item.getCount(), targetInventory);
				}
			}

			if (_tamed) {
				broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die));
				startDeleteTimer();
			} else {
				deleteMe();
			}
		}
	}

	public synchronized void returnToNature() {
		_isReturnToNature = true;
		if (!_tamed) {
			getMap().setPassable(getLocation(), true);
			// 상품 해방 처리
			L1Inventory targetInventory = _master.getInventory();
			List<ItemInstance> items = _inventory.getItems();
			for (ItemInstance item : items) {
				if (_master.getInventory().checkAddItem( // 용량 무게 확인 및 메시지 전송
						item, item.getCount()) == L1Inventory.OK) {
					_inventory
							.tradeItem(item, item.getCount(), targetInventory);
					// \f1 % 0 % 1을주었습니다.
					((L1PcInstance) _master).sendPackets(new S_ServerMessage(
							143, getName(), item.getLogName()));
				} else { // 있을 수 없기 때문에 발등에 떨어질
					targetInventory = L1World.getInstance().getInventory(
							getX(), getY(), getMapId());
					_inventory
							.tradeItem(item, item.getCount(), targetInventory);
				}
			}
			deleteMe();
		} else {
			liberate();
		}
	}

	// 오브젝트 소거 처리
	@Override
	public synchronized void deleteMe() {
		if (_destroyed) {
			return;
		}
		if (!_tamed && !_isReturnToNature) {
			broadcastPacket(S_SkillSound.get(getId(), 169));
		}
		_master.getPetList().remove(getId());
		super.deleteMe();

		if (_summonFuture != null) {
			_summonFuture.cancel(false);
			_summonFuture = null;
		}
	}

	// 테이밍몬스타, 클리에 실 좀비때의 해방 처리
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
		monster.setCurrentHpDirect(getCurrentHp());
		monster.setCurrentMpDirect(getCurrentMp());
		monster.setExp(0);

		deleteMe();
		L1World.getInstance().storeObject(monster);
		L1World.getInstance().addVisibleObject(monster);
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
	public void onAction(L1PcInstance attacker) {
		// XXX:NullPointerException 회피.onAction의 인수의 형태는 L1Character 쪽이 좋아?
		if (attacker == null) {
			return;
		}
		L1Character cha = this.getMaster();
		if (cha == null) {
			return;
		}
		L1PcInstance master = (L1PcInstance) cha;
		if (master.getTeleport()) {
			// 텔레포트 처리중
			return;
		}
		if ((getZoneType() == 1 || attacker.getZoneType() == 1)
				&& isExsistMaster()) {
			// 공격받는 측이 세이프티 존
			// 공격 모션 송신
			Attack attack_mortion = new Attack(attacker, this);
			attack_mortion.action();
			return;
		}

		if (attacker.checkNonPvP(attacker, this)) {
			return;
		}

		Attack attack = new Attack(attacker, this);
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
		if (_master.equals(player)) {
			player.sendPackets(new S_PetMenuPacket(this, 0));
		}
	}

	@Override
	public void onFinalAction(L1PcInstance player, String action) {
		int status = ActionType(action);
		if (status == 0) {
			return;
		}
		if (status == 6) {
			if (_tamed) {
				// 테이밍몬스타, 클리에 실 좀비의 해방
				liberate();
			} else {
				// 사몬의 해산
				Death(null);
			}
		} else {
			// 같은 주인의 애완동물 상태를 모두 갱신
			Object[] petList = _master.getPetList().values().toArray();
			for (Object petObject : petList) {
				if (petObject instanceof SummonInstance) {
					// 사몬몬스타
					SummonInstance summon = (SummonInstance) petObject;
					summon.set_currentPetStatus(status);
				} else {
					// 애완동물
				}
			}
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.summon(this, perceivedFrom));
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			// 100%의 확률로 헤이 파업 일부 사용
			useItem(USEITEM_HASTE, 100);
		}
		if (getCurrentHp() * 100 / getMaxHp() < 40) {
			// HP가 40%
			// 100%의 확률로 회복 일부 사용
			useItem(USEITEM_HEAL, 100);
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

	private int ActionType(String action) {
		
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

		if (_master instanceof L1PcInstance) {
			int HpRatio = 100 * currentHp / getMaxHp();
			L1PcInstance Master = (L1PcInstance) _master;
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

	public void set_currentPetStatus(int i) {
		_currentPetStatus = i;
		if (_currentPetStatus == 5) {
			setHomeX(getX());
			setHomeY(getY());
		}

		if (_currentPetStatus == 3) {
			allTargetClear();
		} else {
			if (!isAiRunning()) {
				startAI();
			}
		}
	}

	public int get_currentPetStatus() {
		return _currentPetStatus;
	}

	public boolean isExsistMaster() {
		boolean isExsistMaster = true;
		if (this.getMaster() != null) {
			String masterName = this.getMaster().getName();
			if (L1World.getInstance().getPlayer(masterName) == null) {
				isExsistMaster = false;
			}
		}
		return isExsistMaster;
	}

}