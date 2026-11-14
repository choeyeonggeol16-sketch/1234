package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;


import java.util.Arrays;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.npc.ai.NpcPotionPolicy;
import l1j.server.server.model.npc.ai.dFollow;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.Proto;
import LIN.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PetMenuPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.NpcAiUtil;

import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
public class L1SummonInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	private static final long SUMMON_TIME = 3600000L;
	private static final int TIMER_LIFETIME = 1;
	private static final int TIMER_LIFETIME_GENERATION = 1;
	private int _currentPetStatus;
	private boolean _tamed;
	private boolean _isReturnToNature = false;

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		final L1Character master = _master;
		final boolean hasMasterHere = master != null && master.getMapId() == getMapId();

		// stay
		if (_currentPetStatus == 3) {
			return true;
		}

		// 주인을 완전히 잃었을 때만 stay 전환
		if (!hasMasterHere) {
			_currentPetStatus = 3;
			return true;
		}

		final int dist = getLocation().getTileLineDistance(master.getLocation());

		// extend
		if (_currentPetStatus == 4) {
			return handleExtendNoTarget(master, dist);
		}

		// alert
		if (_currentPetStatus == 5) {
			return handleAlertNoTarget();
		}

		// aggressive / defensive 등 일반 추종
		return handleFollowNoTarget(master, dist);
	}

	private boolean handleExtendNoTarget(final L1Character master, final int dist) {
		// 너무 가까우면 주인 반대 방향으로 벌어짐
		if (dist < 4) {
			int dir = targetReverseDirection(master.getX(), master.getY());
			dir = NpcAiUtil.checkObject(getX(), getY(), getMapId(), dir);
			if (dir != -1) {
				setSleepTime(setDirectionMove(dir));
			}
			return false;
		}

		// 너무 멀어지면 다시 주인 쪽으로 붙음
		if (dist > 7) {
			final int dir = moveDirection(master.getX(), master.getY());
			if (dir != -1) {
				setSleepTime(setDirectionMove(dir));
			}
			return false;
		}

		// 적정 거리 유지
		return false;
	}

	private boolean handleAlertNoTarget() {
		// 경계 상태는 홈 주변 유지
		if (Math.abs(getHomeX() - getX()) > 1 || Math.abs(getHomeY() - getY()) > 1) {
			final int dir = moveDirection(getHomeX(), getHomeY());
			if (dir == -1) {
				// 길이 완전히 꼬였으면 현재 위치를 새 홈으로 잡음
				setHomeX(getX());
				setHomeY(getY());
			} else {
				setSleepTime(setDirectionMove(dir));
			}
		}
		return false;
	}

	private boolean handleFollowNoTarget(final L1Character master, final int dist) {
		if (dist > 2) {
			return dFollow.follow(this, master);
		}
		return false;
	}

	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}


	public L1SummonInstance(L1Npc template, L1PcInstance master) {
		super(template);
		setId(IdFactory.getInstance().nextId());


		setMaster(master);
		setMap(master.getMapId());

		int nx = master.getX() + ThreadLocalRandom.current().nextInt(5) - 2;
		int ny = master.getY() + ThreadLocalRandom.current().nextInt(5) - 2;

		if (!getMap().isInMap(nx, ny) || !getMap().isPassable(nx, ny)) {
			nx = master.getX();
			ny = master.getY();
		}

		setX(nx);
		setY(ny);
		
		setHeading(ThreadLocalRandom.current().nextInt(8));
		setLightSize(template.getLightSize());
		_currentPetStatus = 1;
		
		_tamed = false;

		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if (pc != null)
				onPerceive(pc);
		}
		
		master.addPet(this);
		Object aobj[] = master.getPetList().values().toArray();
		SC_ReturnedStat.sendPetParty((L1PcInstance) master, (aobj.length + 1) * 3, getId(), true);
		
		L1NpcInstance Npc = (L1NpcInstance) this;
		Npc.startAI();

		armLifetimeTimer();
		
	}

	public L1SummonInstance(L1NpcInstance target, L1PcInstance master,boolean isCreateZombie) {
		super(null);
		setId(IdFactory.getInstance().nextId());

		if (isCreateZombie) { 
			int npcId = 45065;
			L1PcInstance pc = (L1PcInstance) master;
			int level = pc.getLevel();
			if (pc.isWizard()) {
				if (level >= 24 && level <= 31) {
					npcId = 81183;
				} else if (level >= 32 && level <= 39) {
					npcId = 81184;
				} else if (level >= 40 && level <= 43) {
					npcId = 81185;
				} else if (level >= 44 && level <= 47) {
					npcId = 81186;
				} else if (level >= 48 && level <= 51) {
					npcId = 81187;
				} else if (level >= 52) {
					npcId = 81188;
				}
			} else if (pc.isElf()) {
				if (level >= 48) {
					npcId = 81183;
				}
			}
			L1Npc template = NpcTable.getInstance().getTemplate(npcId);
			setting_template(template);
		} else { 

			setting_template(target.getNpcTemplate());
			setCurrentHp(target.getCurrentHp());
			setCurrentMp(target.getCurrentMp());
		}


		setMaster(master);
		setX(target.getX());
		setY(target.getY());
		setMap(target.getMapId());
		setHeading(target.getHeading());
		setLightSize(target.getLightSize());
		setPetcost(6);

		if (target instanceof L1MonsterInstance && !((L1MonsterInstance) target).is_storeDroped()) {
			DropTable.getInstance().setDrop(target, target.getInventory());
		}

		setInventory(target.getInventory());
		target.setInventory(null);

		_currentPetStatus = 3;
		_tamed = true;

		for (L1NpcInstance each : master.getPetList().values()) {
			if(each != null)
				each.targetRemove(target);
		}

		target.deleteMe();
		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if(pc != null)
				onPerceive(pc);
		}
		master.addPet(this);
		Object aobj[] = master.getPetList().values().toArray();
		SC_ReturnedStat.sendPetParty((L1PcInstance) master, (aobj.length + 1) * 3, getId(), true);

		armLifetimeTimer();
	}

	private void armLifetimeTimer() {
		CIOThreadCallback.getInstance().scheduleNpcTimer(
				this,
				TIMER_LIFETIME,
				TIMER_LIFETIME_GENERATION,
				SUMMON_TIME);
	}

	@Override
	public void TimerExpired(int timerId, int generation) {
		if (timerId != TIMER_LIFETIME) {
			super.TimerExpired(timerId, generation);
			return;
		}
		if (generation != TIMER_LIFETIME_GENERATION || _destroyed) {
			return;
		}

		if (_tamed) {
			liberate();
		} else {
			onDeath(null);
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { 
		if (getCurrentHp() > 0) {
			if (damage > 0) {
				setHate(attacker, 0); 
				if(hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)){
					removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
				}else if (hasSkillEffect(L1SkillId.PHANTASM)){
					removeSkillEffect(L1SkillId.PHANTASM);
				}
				if (!isExsistMaster()) {
					_currentPetStatus = 1;
					setTarget(attacker);
				}
			}

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.set_pet_target(this);
			}

			int newHp = applyNpcHpDelta(-(long) damage);
            if (newHp == 0) onDeath(attacker);
        }
	}

	@Override
	protected synchronized void onDeath(L1Character lastAttacker) {
		if (!isDead()) {
			setDead(true);
			setCurrentHp(0);
			setStatus(ActionCodes.ACTION_Die);
			releaseNpcTileNow();
			L1Inventory targetInventory = _master.getInventory();
			List<L1ItemInstance> items = _inventory.getItems();
			for (L1ItemInstance item : items) {
				if(item == null) continue;
				if (_master.getInventory().checkAddItem(item, item.getCount()) == L1Inventory.OK) {
					_inventory.tradeItem(item, item.getCount(), targetInventory);
					SC_ServerMessage.send(((L1PcInstance) _master), 143, getName(), item.getLogName());
				} else {
					targetInventory = L1World.getInstance().getInventory(getX(), getY(), getMapId());
					_inventory.tradeItem(item, item.getCount(), targetInventory);
				}
			}
			if (_tamed) {
				SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Die);
				startDeleteTimer();
			} else {
				deleteMe();
			}
		}
	}

	public synchronized void returnToNature() {
		_isReturnToNature = true;
		if (!_tamed) {
			L1Inventory targetInventory = _master.getInventory();
			List<L1ItemInstance> items = _inventory.getItems();
			for (L1ItemInstance item : items) {
				if(item == null) continue;
				if (_master.getInventory().checkAddItem( item, item.getCount()) == L1Inventory.OK) {
					_inventory.tradeItem(item, item.getCount(), targetInventory);
					SC_ServerMessage.send(((L1PcInstance) _master), 143, getName(), item.getLogName());
				} else {
					targetInventory = L1World.getInstance().getInventory(getX(), getY(), getMapId());
					_inventory.tradeItem(item, item.getCount(), targetInventory);
				}
			}
			deleteMe();
		} else {
			liberate();
		}
	}

	@Override
	public synchronized void deleteMe() {
		if (_destroyed) {
			return;
		}
		if(((L1PcInstance)_master).isSM() && this.getNpcId() >= 7320137 && this.getNpcId() <= 7320147)
			((L1PcInstance)_master).setSM(false);
		if (!_tamed && !_isReturnToNature) {
			SC_SkillSound.broadcast(this, getId(), 169);		
		}
		
		Object aobj[] = _master.getPetList().values().toArray();
		if ( aobj == null) return;
		for (int i = 0; i < aobj.length; i++){
			if (aobj[i] == this){
				SC_ReturnedStat.sendPetParty((L1PcInstance) _master, i * 3, getId(), false);
			}
		}
			
		this._master.getPetList().remove(Integer.valueOf(getId()));
		super.deleteMe();
	
		_master = null;

	}

	public void liberate() {
		L1MonsterInstance monster = new L1MonsterInstance(getNpcTemplate());
		monster.setId(IdFactory.getInstance().nextId());

		monster.setX(getX());
		monster.setY(getY());
		monster.setMap(getMapId());
		monster.setHeading(getHeading());
		monster.set_storeDroped(true);
		monster.setInventory(getInventory());
		setInventory(null);
		monster.setCurrentHp(getCurrentHp());
		monster.setCurrentMp(getCurrentMp());
		monster.setExp(0);
		monster.setLawful(0);

		deleteMe();
		L1World.getInstance().storeAndEnterWorld(monster);
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
		if (attacker == null) {
			return;
		}
		L1Character cha = this.getMaster();
		if (cha == null) {
			return;
		}
		L1PcInstance master = (L1PcInstance) cha;
		if (master.get_teleport()) {
			return;
		}
		if ((getZoneType() == 1 || attacker.getZoneType() == 1)
				&& isExsistMaster()) {
			CAttackAction.swingOnly(attacker, this);
			return;
		}

		if (attacker.checkNonPvP(attacker, this)) {
			return;
		}

		CAttackAction.attackPcNpc(attacker, this, false);
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (isDead()) {
			return;
		}
		if (_master.equals(player)) {
			SC_PetMenuPacket.sendTwice(player, this, 0);
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
				liberate();
			} else {
				onDeath(null);
			}
		} else {
			Object[] petList = _master.getPetList().values().toArray();
			L1SummonInstance summon = null;
			for (Object petObject : petList) {
				if(petObject == null) continue;
				if (petObject instanceof L1SummonInstance) {
					summon = (L1SummonInstance) petObject;
					summon.set_currentPetStatus(status);
				} else {
				}
			}
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		LIN.SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(perceivedFrom, this, perceivedFrom);
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			useItem(NpcPotionPolicy.USEITEM_HASTE, 100);
		}
		if (getCurrentHp() * 100 / getMaxHp() < 40) {
			useItem(NpcPotionPolicy.USEITEM_HEAL, 100);
		}
	}

	@Override
	public void onGetItem(L1ItemInstance item) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		Arrays.sort(NpcPotionPolicy.healPotions);
		Arrays.sort(NpcPotionPolicy.haestPotions);
		if (Arrays.binarySearch(NpcPotionPolicy.healPotions, item.getItem().getItemId()) >= 0) {
			if (getCurrentHp() != getMaxHp()) {
				useItem(NpcPotionPolicy.USEITEM_HEAL, 100);
			}
		} else if (Arrays
				.binarySearch(NpcPotionPolicy.haestPotions, item.getItem().getItemId()) >= 0) {
			useItem(NpcPotionPolicy.USEITEM_HASTE, 100);
		}
	}

	private int ActionType(String action) {
		int status = 0;
		if (action.equalsIgnoreCase("aggressive")) { 
			status = 1;
		} else if (action.equalsIgnoreCase("defensive")) { 
			status = 2;
		} else if (action.equalsIgnoreCase("stay")) { 
			status = 3;
		} else if (action.equalsIgnoreCase("extend")) { 
			status = 4;
		} else if (action.equalsIgnoreCase("alert")) { 
			status = 5;
		} else if (action.equalsIgnoreCase("dismiss")) { 
			status = 6;
		}
		return status;
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
		if (_master instanceof L1PcInstance) {
			L1PcInstance Master = (L1PcInstance) _master;
			SC_HPMeter.send(Master, this);
		}
	}

    @Override
    protected void onNpcAtomicHpChanged(int hp) {
        if (_master instanceof L1PcInstance) SC_HPMeter.send((L1PcInstance) _master, this);
    }

	@Override
	public void setCurrentMp(int i) {
		super.setCurrentMp(i);
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
		boolean isExistMaster = false;
		if (this.getMaster() != null) {
			String masterName = this.getMaster().getName();
			if (L1World.getInstance().getPlayer(masterName) != null) {
				isExistMaster = true;
			}
		}
		return isExistMaster;
	}

	@Override
	public boolean checkCondition(){
		if(_master == null)	{
			return true;
		}

		if( _master instanceof L1PcInstance && ((L1PcInstance)_master).isInWarArea()){

			for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(this)) {
			    LIN.SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(visiblePc, this, visiblePc);
			}		
			deleteMe();
			_master = null;
			return true;
		}
		return false;
	}
	
	private static int _instanceType = -1;
	@Override
	public int getL1Type(){
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_SUMMON : _instanceType;		
	}
}
