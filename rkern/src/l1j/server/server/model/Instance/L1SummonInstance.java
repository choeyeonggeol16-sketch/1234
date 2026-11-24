package l1j.server.server.model.Instance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.ThreadPool;

import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
import l1j.server.server.model.network.core.sc.packet.SC_PetMenuPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNpcItemUse;
import l1j.server.server.model.npc.ai.action.dFollow;
import l1j.server.server.model.npc.ai.action.dPetDefault;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.shared.L1Type;
import l1j.server.server.templates.L1Npc;
public class L1SummonInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	private static final long SUMMON_TIME = 3600000L;
	private static final int TIMER_LIFETIME = 1;
	private static final int TIMER_LIFETIME_GENERATION = 1;
    private static final int TIMER_DEATH_RETURN = 0x53554D52;
    private static final long DEATH_RETURN_RETRY_MS = 1000L;
    private int deathReturnGeneration;
    private static final Logger LOG = Logger.getLogger(L1SummonInstance.class.getName());
	private int _currentPetStatus;
	private boolean _tamed;
	private boolean _isReturnToNature = false;

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		final L1Character master = _master;
		if (_currentPetStatus == 3) {
			return dPetDefault.onTick(this, master, 3);
		}
		if (master == null || master.getMapId() != getMapId()) {
			_currentPetStatus = 3;
			return dPetDefault.onTick(this, master, 3);
		}
		// NC DESIRE_PET_DEFAULT. 3=stay, 4=extend, 5=alert,
		// remaining aggressive/defensive command states keep normal follow.
		return dPetDefault.onTick(this, master, _currentPetStatus);
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
        if (timerId == TIMER_DEATH_RETURN) {
            synchronized (this) {
                if (generation != deathReturnGeneration || !isDeathProcessing()
                        || !isDead() || _destroyed) return;
                if (++deathReturnGeneration == 0) ++deathReturnGeneration;
            }
            try {
                ThreadPool.getInstance().execute(this::finishSummonDeath);
            } catch (RejectedExecutionException rejected) {
                synchronized (this) {
                    if (isDeathProcessing() && isDead() && !_destroyed) retryDeathReturn();
                }
            }
            return;
        }
		if (timerId != TIMER_LIFETIME) {
			super.TimerExpired(timerId, generation);
			return;
		}
		if (generation != TIMER_LIFETIME_GENERATION || _destroyed || isDead() || isDeathProcessing()) {
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
        if (!Die()) return;
        setStatus(ActionCodes.ACTION_Die);
        releaseNpcTileNow();
        finishSummonDeath();
    }

    // Keep death processing open until every confirmed transfer settles.
    private synchronized void finishSummonDeath() {
        if (_destroyed || !isDead() || !isDeathProcessing()) return;
        boolean settled = false;
        try {
            final L1Character master = _master;
            final L1Inventory masterInventory = master == null ? null : master.getInventory();
            for (L1ItemInstance item : _inventory.getItems()) {
                if (item == null) continue;
                final int count = item.getCount();
                if (masterInventory != null
                        && masterInventory.checkAddItem(item, count) == L1Inventory.OK
                        && _inventory.tradeItem(item, count, masterInventory) != null) {
                    if (master instanceof L1PcInstance pc) {
                        try {
                            SC_ServerMessage.send(pc, 143, getName(), item.getLogName());
                        } catch (RuntimeException failure) {
                            LOG.log(Level.WARNING, "Summon return notification failed", failure);
                        }
                    }
                } else {
                    final L1Inventory ground = L1World.getInstance().getInventory(getX(), getY(), getMapId());
                    if (_inventory.tradeItem(item, count, ground) == null) return;
                }
            }
            settled = _inventory.getItems().isEmpty();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Summon death settlement failed id=" + getId(), failure);
        } finally {
            if (!settled && !_destroyed) retryDeathReturn();
        }
        if (!settled) return;
        if (++deathReturnGeneration == 0) ++deathReturnGeneration;
        if (_tamed) {
            try {
                SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Die);
            } finally {
                DeathFinished(true);
            }
        } else {
            try {
                deleteMe();
            } finally {
                DeathFinished(false);
            }
        }
    }

    private void retryDeathReturn() {
        if (++deathReturnGeneration == 0) ++deathReturnGeneration;
        CIOThreadCallback.getInstance().scheduleNpcTimer(
                this, TIMER_DEATH_RETURN, deathReturnGeneration, DEATH_RETURN_RETRY_MS);
    }

	public synchronized void returnToNature() {
        if (_destroyed || isDead() || isDeathProcessing()) return;
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
        if (_destroyed) return;
        if (isDead() && _inventory != null && !_inventory.getItems().isEmpty()) {
            // External deletion must not discard an unsettled dead servant.
            return;
        }
        final L1Character master = _master;
        if (master instanceof L1PcInstance pc) {
            if (pc.isSM() && getNpcId() >= 7320137 && getNpcId() <= 7320147) {
                pc.setSM(false);
            }
            if (!_tamed && !_isReturnToNature) {
                try {
                    SC_SkillSound.broadcast(this, getId(), 169);
                } catch (RuntimeException failure) {
                    LOG.log(Level.WARNING, "Summon removal notification failed", failure);
                }
            }
            final Object[] pets = pc.getPetList().values().toArray();
            try {
                for (int i = 0; i < pets.length; i++) {
                    if (pets[i] == this) {
                        SC_ReturnedStat.sendPetParty(pc, i * 3, getId(), false);
                        break;
                    }
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Summon party notification failed", failure);
            }
            pc.getPetList().remove(Integer.valueOf(getId()), this);
        }
        try {
            super.deleteMe();
        } finally {
            _master = null;
        }
    }

	public synchronized void liberate() {
        if (_destroyed || isDead() || isDeathProcessing()) return;
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
		if (perceivedFrom == null) return;
		
		if (SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(perceivedFrom, this, perceivedFrom)) {
			perceivedFrom.addKnownObject(this);
		}
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			useItem(CNpcItemUse.USEITEM_HASTE, 100);
		}
		if (getCurrentHp() * 100 / getMaxHp() < 40) {
			useItem(CNpcItemUse.USEITEM_HEAL, 100);
		}
	}

	@Override
	public void onGetItem(L1ItemInstance item) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		CNpcItemUse.OnGetItem(this, item.getItem().getItemId());
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
			    SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(visiblePc, this, visiblePc);
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
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_SUMMON : _instanceType;		
	}
}
