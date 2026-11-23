package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.PetItemTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.npc.ai.NpcPotionPolicy;
import l1j.server.server.model.npc.ai.action.dFollow;
import l1j.server.server.model.npc.ai.action.dPetDefault;
import l1j.server.server.model.npc.ai.action.CGetItemAction;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PetMenuPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PetPack;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.templates.L1PetItem;
import l1j.server.server.templates.L1PetType;

import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
public class L1PetInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	public synchronized void deleteMe() {
		Object aobj[] = _master.getPetList().values().toArray();
		for(int i = 0; i < aobj.length; i++)
			if(aobj[i] == this)
				SC_ReturnedStat.sendPetParty(_petMaster, i * 3, getId(), false);
		super.deleteMe();
	}

	@Override
	public boolean noTarget() {
		final L1PcInstance master = _petMaster;
		if (_currentPetStatus == 3) {
			return dPetDefault.onTick(this, master, 3);
		}
		if (master == null || master.getMapId() != getMapId()) {
			_currentPetStatus = 3;
			return dPetDefault.onTick(this, master, 3);
		}
		if (_currentPetStatus == 7) {
			// L1 call command stays distinct from NC's default follow.
			dFollow.clear(this);
			final int distance = getLocation()
					.getTileLineDistance(master.getLocation());
			return handleCallNoTarget(master, distance);
		}
		// NC DESIRE_PET_DEFAULT: owner execution, existing L1 command modes.
		return dPetDefault.onTick(this, master, _currentPetStatus);
	}

	private boolean handleCallNoTarget(final L1PcInstance master, final int dist) {
		if (dist <= 1) {
			_currentPetStatus = 3;
			return true;
		}
		final int dir = moveDirection(master.getX(), master.getY());
		if (dir != -1) {
			setSleepTime(setDirectionMove(dir));
		}
		return false;
	}

	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}

	public L1PetInstance(L1Npc template, L1PcInstance master, L1Pet l1pet) {
		super(template);

		_petMaster = master;
		_petMasterId = master.getId();
		_itemObjId = l1pet.get_itemobjid();
		_type = PetTypeTable.getInstance().get(template.get_npcId());

		setId(l1pet.get_objid());
		setName(l1pet.get_name());
		setLevel(l1pet.get_level());
		setMaxHp(l1pet.get_hp());
		setCurrentHp(l1pet.get_hp());
		setMaxMp(l1pet.get_mp());
		setCurrentMp(l1pet.get_mp());
		setExp(l1pet.get_exp());
		setExpPercent(ExpTable.getExpPercentage(l1pet.get_level(), l1pet.get_exp()));
		setLawful(l1pet.get_lawful());
		setTempLawful(l1pet.get_lawful());

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
	
		setHeading(5);
		setLightSize(template.getLightSize());

		_currentPetStatus = 3;

		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
		master.addPet(this);
		Object aobj[] = master.getPetList().values().toArray();
		SC_ReturnedStat.sendPetParty(master, (aobj.length + 1) * 3, getId(), true);
	}

	public L1PetInstance(L1NpcInstance target, L1PcInstance master, int itemid) {
		this(target, master, itemid, IdFactory.getInstance().nextId());
		target.deleteMe();
		enterPetWorld(master);
		PetTable.getInstance().storeNewPet(target, getId(), itemid);
	}

	public static L1PetInstance prepareNpcPurchase(L1NpcInstance target, L1PcInstance master, int itemid) {
		return new L1PetInstance(target, master, itemid, IdFactory.getInstance().nextId());
	}

	private L1PetInstance(L1NpcInstance target, L1PcInstance master, int itemid, int objectId) {
		super(null);

		_petMaster = master;
		_petMasterId = master.getId();
		_itemObjId = itemid;
		_type = PetTypeTable.getInstance().get(
				target.getNpcTemplate().get_npcId());

		setId(objectId);
		setting_template(target.getNpcTemplate());
		setCurrentHp(target.getCurrentHp());
		setCurrentMp(target.getCurrentMp());
		setExp(750);
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
	}

	public void enterPurchasedPetWorld() {
        // Use the original shop spawn position (one tile in front of the
        // owner), refreshed on the owner thread once the receipt is committed.
        getLocation().set(_petMaster.getLocation());
        getLocation().forward(_petMaster.getHeading());
        setHeading(_petMaster.getHeading());
        try {
            enterPetWorld(_petMaster);
        } catch (RuntimeException failure) {
            try {
                deleteMe();
            } finally {
                if (_petMaster.getPetList().get(getId()) == this) _petMaster.removePet(this);
            }
            throw failure;
        }
	}

	private void enterPetWorld(L1PcInstance master) {
		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}

		master.addPet(this);
		SC_ReturnedStat.sendPetParty(master, (master.getPetList().size() + 1) * 3, getId(), true);
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
			}

			if (attacker instanceof L1PcInstance && damage > 0) {
				L1PcInstance player = (L1PcInstance) attacker;
				player.set_pet_target(this);
			}

			int newHp = applyNpcHpDelta(-(long) damage);
            if (newHp == 0) onDeath(attacker);
        }
	}

	/**
	 * 펫은 죽어도 시체를 유지하고 부활할 수 있다.
	 * 별도 Death 작업이나 삭제 타이머를 등록하지 않는다.
	 * 기존 death()의 사망 시 1회 처리 계약을 유지한다.
	 */
	@Override
	protected synchronized void onDeath(L1Character lastAttacker) {
		if (isDead()) {
			return;
		}
		setDead(true);
		setStatus(ActionCodes.ACTION_Die);
		setCurrentHp(0);
		releaseNpcTileNow();
		SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Die);
	}

	public void evolvePet(int new_itemobjid) {

		L1Pet l1pet = PetTable.getInstance().getTemplate(_itemObjId);
		if (l1pet == null) {
			return;
		}

		int newNpcId = _type.getNpcIdForEvolving();
		int tmpMaxHp = getMaxHp();
		int tmpMaxMp = getMaxMp();

		transform(newNpcId);
		_type = PetTypeTable.getInstance().get(newNpcId);

		setLevel(1);
		setMaxHp(tmpMaxHp / 2);
		setMaxMp(tmpMaxMp / 2);
		setCurrentHp(getMaxHp());
		setCurrentMp(getMaxMp());
		setExp(0);
		setExpPercent(0);

		getInventory().clearItems();

		PetTable.getInstance().deletePet(_itemObjId);

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
		monster.setLevel(getLevel());
		monster.setMaxHp(getMaxHp());
		monster.setCurrentHp(getCurrentHp());
		monster.setMaxMp(getMaxMp());
		monster.setCurrentMp(getCurrentMp());
		
		_petMaster.getPetList().remove(getId());
		deleteMe();

		if (_petMaster.getPetList().isEmpty()) {
			l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(_petMaster, (L1PcInstance) _master, monster, false);
		}
		
		_petMaster.getInventory().removeItem(_itemObjId, 1);
		PetTable.getInstance().deletePet(_itemObjId);

		L1World.getInstance().storeAndEnterWorld(monster);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(monster)) {
			onPerceive(pc);
		}
	}

	public void collect() {
		L1Inventory targetInventory = _petMaster.getInventory();
		List<L1ItemInstance> items = _inventory.getItems();
		int size = _inventory.getSize();
		L1ItemInstance item = null;
		for (int i = 0; i < size; i++) {
			item = items.get(0);
			if (item.isEquipped()) {
				continue;
			}
			if (_petMaster.getInventory().checkAddItem( 
					item, item.getCount()) == L1Inventory.OK) {
				_inventory.tradeItem(item, item.getCount(), targetInventory);
				SC_ServerMessage.send(_petMaster, 143, getName(), item
						.getLogName()); 
			} else { 
				targetInventory = L1World.getInstance().getInventory(getX(),
						getY(), getMapId());
				_inventory.tradeItem(item, item.getCount(), targetInventory);
			}
		}
	}
	
	public void unloadMaster(){
		dropItem();
		if(_petMaster != null)
			_petMaster.getPetList().remove(getId());
		deleteMe();
	}

	public void dropItem() {
		L1Inventory targetInventory = null;
		List<L1ItemInstance> items = _inventory.getItems();
		if(items == null || items.size() <= 0)
			return;
		
		int size = _inventory.getSize();
		L1ItemInstance item = null;
		if (_petMaster != null) {
			targetInventory = _petMaster.getInventory();
		} else {
			L1Object obj = L1World.getInstance().findObject(_petMasterId);
			if(obj == null || !(obj instanceof L1PcInstance)){
				if(!MySqlCharacterStorage.isValidUserObjectId(_petMasterId)){
					targetInventory = L1World.getInstance().getInventory(getX(), getY(), getMapId());
				}
			}else{
				L1PcInstance pc = (L1PcInstance)obj;
				if(pc.getNetConnection() != null && pc.getNetConnection().isConnected()){
					targetInventory = pc.getInventory();	
				}
			}
		}
		
		// non-stop db inserted.
		if(targetInventory == null){
			CharactersItemStorage storage = CharactersItemStorage.create();
			for(int i=size - 1; i>=0; --i){
				item = items.get(0);
				item.setEquipped(false);
				try {
					storage.storeItem(_petMasterId, item);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			_inventory.clearItems();
			
			// default owner trade item.
		}else{
			for (int i = 0; i < size; i++) {
				item = items.get(0);
				item.setEquipped(false);
				_inventory.tradeItem(item, item.getCount(), targetInventory);
			}
		}
	}

	public void call() {
		int id = _type.getMessageId(L1PetType.getMessageNumber(getLevel()));
		if (id != 0) {
			SC_NpcChatPacket.broadcastNpc(this, this, "$" + id, 0);
		}

		setCurrentPetStatus(7);
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
		if (target != null && (_currentPetStatus == 1 || _currentPetStatus == 5)) {
			setHate(target, 0);
			if (!isAiRunning()) {
				startAI();
			}
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		
		
		if (!SC_PetPack.send(perceivedFrom, this)) return;
		perceivedFrom.addKnownObject(this);

		/** 피바 갱신 **/
		if (_petMaster != null && _petMaster == perceivedFrom) {
			SC_HPMeter.send(_petMaster, this);
		}
	}

	@Override
	public void onAction(L1PcInstance player) {
		if(player == null)
			return;
		
		L1Character cha = this.getMaster();
		L1PcInstance master = (L1PcInstance) cha;
		if ( master == null)return;
		if (master.get_teleport()) { 
			return;
		}
		if (getZoneType() == 1) {
			CAttackAction.swingOnly(player, this);
			return;
		}

		if (player.checkNonPvP(player, this)) {
			return;
		}

		CAttackAction.attackPcNpc(player, this, false);
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (isDead()) {
			return;
		}
		if (_petMaster.equals(player)) {
			SC_PetMenuPacket.send(player, this, getExpPercent());
			L1Pet l1pet = PetTable.getInstance().getTemplate(_itemObjId);
			if (l1pet != null) {
				l1pet.set_exp(getExp());
				l1pet.set_level(getLevel());
				l1pet.set_hp(getMaxHp());
				l1pet.set_mp(getMaxMp());
				PetTable.getInstance().storePet(l1pet);
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
			liberate();
		} else {
			Object[] petList = _petMaster.getPetList().values().toArray();
			L1PetInstance pet = null;
			L1PetType type = null;
			for (Object petObject : petList) {
				if (petObject instanceof L1PetInstance) {
					pet = (L1PetInstance) petObject;
					if (_petMaster != null && _petMaster.getLevel() >= pet.getLevel()) {
						pet.setCurrentPetStatus(status);
					} else {
						type = PetTypeTable.getInstance().get(pet.getNpcTemplate().get_npcId());
						int id = type.getDefyMessageId();
						if (id != 0) {
							SC_NpcChatPacket.broadcastNpc(this, pet, "$" + id, 0);
						}
					}
				}
			}
			SC_PetMenuPacket.send(player, this, getExpPercent());
		}
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
		if (item == null || item.getItem() == null)
			return;
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

	private int actionType(String action) {
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
		} else if (action.equalsIgnoreCase("getitem")) {
			status = 8;
			//collect();
			collection();
		}
		return status;
	}

	private void collection() {
		// NC GET_ITEM: select a live exact object without temporary ground
		// collections. The shared picker preserves the L1 high-pet exclusion.
		CGetItemAction.searchTarget(this, 10);
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);

		if (_petMaster != null) {
			L1PcInstance Master = _petMaster;
			SC_HPMeter.send(Master, this);
		}
	}

    @Override
    protected void onNpcAtomicHpChanged(int hp) {
        if (_petMaster != null) SC_HPMeter.send(_petMaster, this);
    }

	@Override
	public void setCurrentMp(int i) {
		super.setCurrentMp(i);
	}

	public void setCurrentPetStatus(int i) {
		_currentPetStatus = i;
		// Status 8 is the L1 pickup command. Publish mode before the CIO
		// owner reads the next GET_ITEM action; no separate pickup worker.
		setPickupItem(i == 8);
		if (i != 8) {
			_targetItem = null;
		}
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

	public void usePetWeapon(L1ItemInstance weapon) {
		if (getWeapon() == null) {
			setPetWeapon(weapon);
		} else { // 이미 무엇인가를 장비 하고 있는 경우, 전의 장비를 뗀다
			if (getWeapon().equals(weapon)) {
				removePetWeapon(getWeapon());
			} else {
				removePetWeapon(getWeapon());
				setPetWeapon(weapon);
			}
		}
	}

	public void usePetArmor(L1ItemInstance armor) {
		if (getArmor() == null) {
			setPetArmor(armor);
		} else { // 이미 무엇인가를 장비 하고 있는 경우, 전의 장비를 뗀다
			if (getArmor().equals(armor)) {
				removePetArmor(getArmor());
			} else {
				removePetArmor(getArmor());
				setPetArmor(armor);
			}
		}
	}

	private void setPetWeapon(L1ItemInstance weapon) {
		int itemId = weapon.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);

		if (petItem == null) { return; }

		setHitByWeapon(petItem.getHitModifier());
		setDamageByWeapon(petItem.getDamageModifier());
		getAbility().addAddedStr(petItem.getAddStr());
		getAbility().addAddedCon(petItem.getAddCon());
		getAbility().addAddedDex(petItem.getAddDex());
		getAbility().addAddedInt(petItem.getAddInt());
		getAbility().addAddedWis(petItem.getAddWis());
		addMaxHp(petItem.getAddHp());
		addMaxMp(petItem.getAddMp());
		getAbility().addSp(petItem.getAddSp());
		getResistance().addMr(petItem.getAddMr());

		setWeapon(weapon);
		weapon.setEquipped(true);
	}

	public void setPetArmor(L1ItemInstance armor) {
		int itemId = armor.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);

		if (petItem == null) { return; }

		getAC().addAc(petItem.getAddAc());
		getAbility().addAddedStr(petItem.getAddStr());
		getAbility().addAddedCon(petItem.getAddCon());
		getAbility().addAddedDex(petItem.getAddDex());
		getAbility().addAddedInt(petItem.getAddInt());
		getAbility().addAddedWis(petItem.getAddWis());
		addMaxHp(petItem.getAddHp());
		addMaxMp(petItem.getAddMp());
		getAbility().addSp(petItem.getAddSp());
		getResistance().addMr(petItem.getAddMr());

		setArmor(armor);
		armor.setEquipped(true);
	}

	public void removePetWeapon(L1ItemInstance weapon) {
		int itemId = weapon.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);

		if (petItem == null) { return; }

		setHitByWeapon(0);
		setDamageByWeapon(0);
		getAbility().addAddedStr(-petItem.getAddStr());
		getAbility().addAddedCon(-petItem.getAddCon());
		getAbility().addAddedDex(-petItem.getAddDex());
		getAbility().addAddedInt(-petItem.getAddInt());
		getAbility().addAddedWis(-petItem.getAddWis());
		addMaxHp(-petItem.getAddHp());
		addMaxMp(-petItem.getAddMp());
		getAbility().addSp(-petItem.getAddSp());
		getResistance().addMr(-petItem.getAddMr());

		setWeapon(null);
		weapon.setEquipped(false);
	}

	public void removePetArmor(L1ItemInstance armor) {
		int itemId = armor.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);

		if (petItem == null) { return; }

		getAC().addAc(-petItem.getAddAc());
		getAbility().addAddedStr(-petItem.getAddStr());
		getAbility().addAddedCon(-petItem.getAddCon());
		getAbility().addAddedDex(-petItem.getAddDex());
		getAbility().addAddedInt(-petItem.getAddInt());
		getAbility().addAddedWis(-petItem.getAddWis());
		addMaxHp(-petItem.getAddHp());
		addMaxMp(-petItem.getAddMp());
		getAbility().addSp(-petItem.getAddSp());
		getResistance().addMr(-petItem.getAddMr());

		setArmor(null);
		armor.setEquipped(false);
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
	private L1ItemInstance _weapon;

	public void setWeapon(L1ItemInstance weapon) {
		_weapon = weapon;
	}

	public L1ItemInstance getWeapon() {
		return _weapon;
	}

	private L1ItemInstance _armor;

	public void setArmor(L1ItemInstance armor) {
		_armor = armor;
	}

	public L1ItemInstance getArmor() {
		return _armor;
	}

	private int _hitByWeapon;

	public void setHitByWeapon(int i) {
		_hitByWeapon = i;
	}

	public int getHitByWeapon() {
		return _hitByWeapon;
	}

	private int _damageByWeapon;

	public void setDamageByWeapon(int i) {
		_damageByWeapon = i;
	}

	public int getDamageByWeapon() {
		return _damageByWeapon;
	}

	private volatile int _currentPetStatus;
	private L1PcInstance _petMaster;
	private int _petMasterId;
	private int _itemObjId;
	private L1PetType _type;
	private int _expPercent;

	public L1PcInstance getMaster(){
		return _petMaster;
	}
	
	public int getMasterId(){
		return _petMasterId;
	}
	
	public L1PetType getPetType() {
		return _type;
	}

	@Override
	public boolean checkCondition() {
		if (_petMaster == null) {
			return true;
		}

		if (_petMaster.isInWarArea()) {
			dropItem();
			_petMaster.getPetList().remove(getId());
			deleteMe();

			return true;
		}

		return false;
	}
	
	public void usePetWeapon(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance weapon) {
		if (pet.getWeapon() == null) {
			setPetWeapon(pc, pet, weapon);
		} else { // 이미 무엇인가를 장비 하고 있는 경우, 전의 장비를 뗀다
			if (pet.getWeapon().equals(weapon)) {
				removePetWeapon(pc, pet, pet.getWeapon());
			} else {
				removePetWeapon(pc, pet, pet.getWeapon());
				setPetWeapon(pc, pet, weapon);
			}
		}
	}

	public void usePetArmor(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance armor) {
		if (pet.getArmor() == null) {
			setPetArmor(pc, pet, armor);
		} else { // 이미 무엇인가를 장비 하고 있는 경우, 전의 장비를 뗀다
			if (pet.getArmor().equals(armor)) {
				removePetArmor(pc, pet, pet.getArmor());
			} else {
				removePetArmor(pc, pet, pet.getArmor());
				setPetArmor(pc, pet, armor);
			}
		}
	}

	public void setPetWeapon(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance weapon) {
		int itemId = weapon.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);
		if (petItem == null) {
			return;
		}

		pet.setHitByWeapon(petItem.getHitModifier());
		pet.setDamageByWeapon(petItem.getDamageModifier());
		pet.getAbility().addAddedStr(petItem.getAddStr());
		pet.getAbility().addAddedCon(petItem.getAddCon());
		pet.getAbility().addAddedDex(petItem.getAddDex());
		pet.getAbility().addAddedInt(petItem.getAddInt());
		pet.getAbility().addAddedWis(petItem.getAddWis());
		pet.addMaxHp(petItem.getAddHp());
		pet.addMaxMp(petItem.getAddMp());
		pet.addSp(petItem.getAddSp());
		pet.getResistance().addMr(petItem.getAddMr());

		pet.setWeapon(weapon);
		weapon.setEquipped(true);
	}

	public void removePetWeapon(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance weapon) {
		int itemId = weapon.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);
		if (petItem == null) {
			return;
		}

		pet.setHitByWeapon(0);
		pet.setDamageByWeapon(0);
		pet.getAbility().addAddedStr(-petItem.getAddStr());
		pet.getAbility().addAddedCon(-petItem.getAddCon());
		pet.getAbility().addAddedDex(-petItem.getAddDex());
		pet.getAbility().addAddedInt(-petItem.getAddInt());
		pet.getAbility().addAddedWis(-petItem.getAddWis());
		pet.addMaxHp(-petItem.getAddHp());
		pet.addMaxMp(-petItem.getAddMp());
		pet.addSp(-petItem.getAddSp());
		pet.getResistance().addMr(-petItem.getAddMr());

		pet.setWeapon(null);
		weapon.setEquipped(false);
	}

	public void setPetArmor(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance armor) {
		int itemId = armor.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);
		if (petItem == null) {
			return;
		}

		pet.getAC().addAc(petItem.getAddAc());
		pet.getAbility().addAddedStr(petItem.getAddStr());
		pet.getAbility().addAddedCon(petItem.getAddCon());
		pet.getAbility().addAddedDex(petItem.getAddDex());
		pet.getAbility().addAddedInt(petItem.getAddInt());
		pet.getAbility().addAddedWis(petItem.getAddWis());
		pet.addMaxHp(petItem.getAddHp());
		pet.addMaxMp(petItem.getAddMp());
		pet.addSp(petItem.getAddSp());
		pet.getResistance().addMr(petItem.getAddMr());

		pet.setArmor(armor);
		armor.setEquipped(true);
	}

	public void removePetArmor(L1PcInstance pc, L1PetInstance pet,
			L1ItemInstance armor) {
		int itemId = armor.getItem().getItemId();
		L1PetItem petItem = PetItemTable.getInstance().getTemplate(itemId);
		if (petItem == null) {
			return;
		}

		pet.getAC().addAc(-petItem.getAddAc());
		pet.getAbility().addAddedStr(-petItem.getAddStr());
		pet.getAbility().addAddedCon(-petItem.getAddCon());
		pet.getAbility().addAddedDex(-petItem.getAddDex());
		pet.getAbility().addAddedInt(-petItem.getAddInt());
		pet.getAbility().addAddedWis(-petItem.getAddWis());
		pet.addMaxHp(-petItem.getAddHp());
		pet.addMaxMp(-petItem.getAddMp());
		pet.addSp(-petItem.getAddSp());
		pet.getResistance().addMr(-petItem.getAddMr());

		pet.setArmor(null);
		armor.setEquipped(false);
	}

}
