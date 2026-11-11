package l1j.server.server.model.Instance;

import l1j.server.Templates.L1Npc;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_Door;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_WorldObject;

public class DoorInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	public static final int PASS = 0;
	public static final int NOT_PASS = 1;
	
	private int _doorId = 0;
	private int _direction = 0;
	private int _leftEdgeLocation = 0;
	private int _rightEdgeLocation = 0;
	private int _openStatus = ActionCodes.ACTION_Close;
	private int _passable = NOT_PASS;
	private int _keeperId = 0;

	public DoorInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (getMaxHp() == 0 || getMaxHp() == 1) {
			return;
		}

		if (getCurrentHp() > 0 && !isDead()) {
			Attack attack = new Attack(pc, this);
			if (attack.calcHit()) {
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.door(this));
		sendDoorPacket(perceivedFrom);

		if( !isDead()) {
			if (_crackStatus == 5) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction5));
			} else if (_crackStatus == 4) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction4));
			} else if (_crackStatus == 3) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction3));
			} else if (_crackStatus == 2) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction2));
			} else if (_crackStatus == 1) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction1));
			}
		} else {
			perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorDie));			
		}
	}

	@Override
	public void deleteMe() {
		setPassable(PASS);
		sendDoorPacket(null);

		_destroyed = true;
		if (getInventory() != null) {
			getInventory().clearItems();
		}
		allTargetClear();
		_master = null;
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if(pc == null) {
				continue;
			}
			pc.removeKnownObject(this);
			pc.sendPackets(S_RemoveObject.get(this));
		}
		removeAllKnownObjects();
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (getMaxHp() == 0 || getMaxHp() == 1) { 
			return;
		}

		if (getCurrentHp() > 0 && !isDead()) {
			int newHp = getCurrentHp() - damage;
			if (newHp <= 0 && !isDead()) {
				setCurrentHp(0);
				setDead(true);
				setStatus(ActionCodes.ACTION_DoorDie);
				Death death = new Death(attacker);
				GeneralThreadPool.getInstance().execute(death);
			}
			if (newHp > 0) {
				if (attacker instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) attacker;
					L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
					if (clan != null) {
						int castleId = clan.getCastleId();
						if (castleId != L1CastleLocation.getCastleIdByArea(pc)) {
							CastleGuardInstance guard = null;
							for (L1Object object : L1World.getInstance().getVisibleObjects(pc)) {
								if (object instanceof CastleGuardInstance) {
									guard = (CastleGuardInstance) object;
									guard.setTarget(pc);
								}
							}
						}
					}else{
						CastleGuardInstance guard = null;
						for (L1Object object : L1World.getInstance().getVisibleObjects(pc)) {
							if (object instanceof CastleGuardInstance) {
								guard = (CastleGuardInstance) object;
								guard.setTarget(pc);
							}
						}
					}
				}
				setCurrentHp(newHp);
				if ((getMaxHp() * 1 / 6) > getCurrentHp()) {
					if (_crackStatus != 5) {
						broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction5));
						setStatus(ActionCodes.ACTION_DoorAction5);
						_crackStatus = 5;
					}
				} else if ((getMaxHp() * 2 / 6) > getCurrentHp()) {
					if (_crackStatus != 4) {
						broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction4));
						setStatus(ActionCodes.ACTION_DoorAction4);
						_crackStatus = 4;
					}
				} else if ((getMaxHp() * 3 / 6) > getCurrentHp()) {
					if (_crackStatus != 3) {
						broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction3));
						setStatus(ActionCodes.ACTION_DoorAction3);
						_crackStatus = 3;
					}
				} else if ((getMaxHp() * 4 / 6) > getCurrentHp()) {
					if (_crackStatus != 2) {
						broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction2));
						setStatus(ActionCodes.ACTION_DoorAction2);
						_crackStatus = 2;
					}
				} else if ((getMaxHp() * 5 / 6) > getCurrentHp()) {
					if (_crackStatus != 1) {
						broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorAction1));
						setStatus(ActionCodes.ACTION_DoorAction1);
						_crackStatus = 1;
					}
				}
			}
		} else if (!isDead()) {
			setDead(true);
			setStatus(ActionCodes.ACTION_DoorDie);
			//isPassibleDoor(true);
			Death death = new Death(attacker);
			GeneralThreadPool.getInstance().execute(death);
		}
	}
		
	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
	}

	class Death implements Runnable {
		L1Character _lastAttacker;

		public Death(L1Character lastAttacker) {
			_lastAttacker = lastAttacker;
		}

		@Override
		public void run() {
			setCurrentHp(0);
			setDead(true);
			isPassibleDoor(true);//하딘 시스템
			setStatus(ActionCodes.ACTION_DoorDie);
			getMap().setPassable(getLocation(), true);
			broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_DoorDie));
			setPassable(PASS);
			sendDoorPacket(null);
		}
	}

	class DoorTimer implements Runnable {
		@Override
		public void run() {
			if (_destroyed) { // 이미 파기되어 있지 않은가 체크
				return;
			}
			close();
		}
	}

	private void sendDoorPacket(L1PcInstance pc) {
		int entranceX = getEntranceX();
		int entranceY = getEntranceY();
		int leftEdgeLocation = getLeftEdgeLocation();
		int rightEdgeLocation = getRightEdgeLocation();

		int size = rightEdgeLocation - leftEdgeLocation;
		if (size == 0) {
			sendPacket(pc, entranceX, entranceY);
		} else { 
			if (getDirection() == 0) {
				for (int x = leftEdgeLocation; x <= rightEdgeLocation; x++) {
					sendPacket(pc, x, entranceY);
				}
			} else {
				for (int y = leftEdgeLocation; y <= rightEdgeLocation; y++) {
					sendPacket(pc, entranceX, y);
				}
			}
		}
	}

	private void sendPacket(L1PcInstance pc, int x, int y) {
		int att = 0;
		if(!isDead() || getOpenStatus() == ActionCodes.ACTION_Close){ // 문이 안부서졌거나, 닫혓다면
			att = 1;
		}
		if(isDead() || getOpenStatus() == ActionCodes.ACTION_Open){ // 문이 부서졌거나, 열려잇으면
			att = 0;
		}

		S_Door packet = new S_Door(x, y, getDirection(), att);
		if (pc != null) {
			pc.sendPackets(packet);
		} else {
			broadcastPacket(packet);
		}
	}

	public void open() {
		if (isDead()) {
			return;
		}
		if (getOpenStatus() == ActionCodes.ACTION_Close) {
			isPassibleDoor(true);//하딘 시스템
			if (this.getDoorId() == 113 || this.getDoorId() == 125){					
				GeneralThreadPool.getInstance().schedule(new DoorTimer(), 5000);
			}
			// 얼던 문 닫히는 시간 10분
			if (this.getDoorId() >= 5000 && this.getDoorId() <= 5009){				
				GeneralThreadPool.getInstance().schedule(new DoorTimer(), 600000);
			}
			broadcastPacket(S_DoActionGFX.get(this.getId(),ActionCodes.ACTION_Open));
			setOpenStatus(ActionCodes.ACTION_Open);
			setPassable(DoorInstance.PASS);
			sendDoorPacket(null);
		}
	}

	public void close() {
		if (isDead()) {
			return;
		}
		if (getOpenStatus() == ActionCodes.ACTION_Open) {
			isPassibleDoor(false);//하딘 시스템
			broadcastPacket(S_DoActionGFX.get(this.getId(), ActionCodes.ACTION_Close));
			setOpenStatus(ActionCodes.ACTION_Close);
			setPassable(DoorInstance.NOT_PASS);
			sendDoorPacket(null);
		}
	}

	public void isPassibleDoor(boolean flag) {//하딘 시스템
		int leftEdgeLocation = this.getLeftEdgeLocation();
		int rightEdgeLocation = this.getRightEdgeLocation();
		int size = rightEdgeLocation - leftEdgeLocation;
		if (size == 0) {
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY()-1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY()+1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+1,this.getY(),flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+1,this.getY()-1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+1,this.getY()+1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-1,this.getY(),flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-1,this.getY()-1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-1,this.getY()+1,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY()-2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY()+2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+2,this.getY(),flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+2,this.getY()-2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()+2,this.getY()+2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-2,this.getY(),flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-2,this.getY()-2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-2,this.getY()+2,flag);
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY(),flag);
		} else {
			if (this.getDirection() == 0) { 
				for (int doorX = leftEdgeLocation;
				doorX <= rightEdgeLocation; doorX++) {
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY(),flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()-1,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()+1 ,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()-2,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()+2 ,flag);
				}
			} else {
				for (int doorY = leftEdgeLocation;
				doorY <= rightEdgeLocation; doorY++) {
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() + 1,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() - 1,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() + 2,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() - 2,doorY,flag);
				}
			}
		}
	}
	
	public void repairGate() {
		if (getMaxHp() > 1) {
			setDead(false);
			setCurrentHp(getMaxHp());
			setStatus(0);
			setCrackStatus(0);
			setOpenStatus(ActionCodes.ACTION_Open);
			close();
		}
	}

	public int getDoorId() {	return _doorId;	}
	public void setDoorId(int i) {	_doorId = i;	}

	public int getDirection() {	return _direction;	}
	public void setDirection(int i) {
		if (i == 0 || i == 1) {
			_direction = i;
		}
	}

	public int getEntranceX() {
		int entranceX = 0;
		if (getDirection() == 0) { 
			entranceX = getX();
		} else {
			entranceX = getX() - 1;
		}
		return entranceX;
	}

	public int getEntranceY() {
		int entranceY = 0;
		if (getDirection() == 0) {
			entranceY = getY() + 1;
		} else {
			entranceY = getY();
		}
		return entranceY;
	}

	public int getLeftEdgeLocation() {	return _leftEdgeLocation;	}
	public void setLeftEdgeLocation(int i) {	_leftEdgeLocation = i;	}

	public int getRightEdgeLocation() {	return _rightEdgeLocation;	}
	public void setRightEdgeLocation(int i) {	_rightEdgeLocation = i;	}

	public int getOpenStatus() {	return _openStatus;	}
	public void setOpenStatus(int i) {
		if (i == ActionCodes.ACTION_Open || i == ActionCodes.ACTION_Close) {
			_openStatus = i;
		}
	}

	public int getPassable() {	return _passable;	}
	public void setPassable(int i) {
		if (i == PASS || i == NOT_PASS) {
			_passable = i;
		}
	}

	public int getKeeperId() {	return _keeperId;	}
	public void setKeeperId(int i) {	_keeperId = i;	}

	private int _crackStatus;

	public int getCrackStatus() {	return _crackStatus;	}
	public void setCrackStatus(int i) {	_crackStatus = i;	}

}