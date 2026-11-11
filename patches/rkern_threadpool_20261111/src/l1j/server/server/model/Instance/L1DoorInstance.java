package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.network.core.sc.packet.SC_Door;

import java.util.concurrent.RejectedExecutionException;

import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.templates.L1Npc;

public class L1DoorInstance extends L1NpcInstance {

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
	private int _autostatus = 0;

	public L1DoorInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (getMaxHp() == 0 || getMaxHp() == 1) {
			return;
		}

		if (getCurrentHp() > 0 && !isDead()) {
			CAttackAction.attackPcNpc(pc, this, true);
		}
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
		
		l1j.server.server.model.network.core.sc.packet.SC_DoorPack.send(perceivedFrom, this);
		sendDoorPacket(perceivedFrom);

		if( !isDead() )
		{
			if (_crackStatus == 5) {
				SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorAction5);
			} else if (_crackStatus == 4) {
				SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorAction4);
			} else if (_crackStatus == 3) {
				SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorAction3);
			} else if (_crackStatus == 2) {
				SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorAction2);
			} else if (_crackStatus == 1) {
				SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorAction1);
			}
		} else {
			SC_DoActionGFX.send(perceivedFrom.getNetConnection(), getId(), ActionCodes.ACTION_DoorDie);			
		}
	}

	@Override
	public void deleteMe() {
		setPassable(PASS);
		updateDoorCache(false); // 파괴됨 -> 맵 뚫림
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
			if (pc == null) {
				continue;
			}

			pc.removeKnownObject(this);

			SC_RemoveObject.send(
					pc.getNetConnection(),
					this);
		}
		removeAllKnownObjects();
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		//System.out.println("성문 대미지 : " + damage);
		if (getMaxHp() == 0 || getMaxHp() == 1) { 
			return;
		}
		int GfxId = getCurrentSpriteId();
		if (getCurrentHp() > 0 && !isDead()) {
			int newHp = applyNpcHpDelta(-(long) damage);
			if (newHp == 0 && !isDead()) {
				setCurrentHp(0);
				setDead(true);
				setStatus(DoorAction(GfxId, ActionCodes.ACTION_DoorDie));
				Death death = new Death(attacker);
				try {
					ThreadPool.getInstance().execute(death);
				} catch (RejectedExecutionException rejected) {
					// Passability and world collision must be released even when
					// the instant executor refuses this mandatory transition.
					death.run();
				}
			}
			if (newHp > 0) {
				if (attacker instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) attacker;
					L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
					if (clan != null) {
						int castleId = clan.getCastleId();
						if (castleId != L1CastleLocation.getCastleIdByArea(pc)) {
							L1CastleGuardInstance guard = null;
							for (L1Object object : L1World.getInstance().getVisibleObjects(pc)) {
								if (object instanceof L1CastleGuardInstance) {
									guard = (L1CastleGuardInstance) object;
									guard.setTarget(pc);
								}
							}
						}
					} else {
						L1CastleGuardInstance guard = null;
						for (L1Object object : L1World.getInstance().getVisibleObjects(pc)) {
							if (object instanceof L1CastleGuardInstance) {
								guard = (L1CastleGuardInstance) object;
								guard.setTarget(pc);
							}
						}
					}
				}
				//System.out.println("성문 뽀개지나? : " + newHp);
                if ((getMaxHp() * 1 / 6) > getCurrentHp()) {
					if (_crackStatus != 5) {
						SC_DoActionGFX.broadcast(this, DoorAction(GfxId, ActionCodes.ACTION_DoorAction5));
						setStatus(DoorAction(GfxId, ActionCodes.ACTION_DoorAction5));
						_crackStatus = 5;
					}
				} else if ((getMaxHp() * 2 / 6) > getCurrentHp()) {
					if (_crackStatus != 4) {
						SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_DoorAction4);
						setStatus(ActionCodes.ACTION_DoorAction4);
						_crackStatus = 4;
					}
				} else if ((getMaxHp() * 3 / 6) > getCurrentHp()) {
					if (_crackStatus != 3) {
						SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_DoorAction3);
						setStatus(ActionCodes.ACTION_DoorAction3);
						_crackStatus = 3;
					}
				} else if ((getMaxHp() * 4 / 6) > getCurrentHp()) {
					if (_crackStatus != 2) {
						SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_DoorAction2);
						setStatus(ActionCodes.ACTION_DoorAction2);
						_crackStatus = 2;
					}
				} else if ((getMaxHp() * 5 / 6) > getCurrentHp()) {
					if (_crackStatus != 1) {
						SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_DoorAction1);
						setStatus(ActionCodes.ACTION_DoorAction1);
						_crackStatus = 1;
					}
				}
			}
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
			int GfxId = getCurrentSpriteId();
			setCurrentHp(0);
			setDead(true);
			//isPassibleDoor(true);//하딘 시스템
			setStatus(DoorAction(GfxId, ActionCodes.ACTION_DoorDie));
		//	getMap().setPassable(getLocation(), true);
			SC_DoActionGFX.broadcast(L1DoorInstance.this, DoorAction(GfxId, ActionCodes.ACTION_DoorDie));
			setPassable(PASS);
			updateDoorCache(false); // 삭제됨 -> 맵 뚫림
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
			/** 성문뚫어방지 **/
			if (getDirection() == 0) {
				for (int x = leftEdgeLocation; x <= rightEdgeLocation; x++) {
					if(size >= 5) {
						sendPacket(pc, x, entranceY);
						sendPacket(pc, x, entranceY - 1);
					} else {
						sendPacket(pc, x, entranceY);
					}
				}
			} else {
				for (int y = leftEdgeLocation; y <= rightEdgeLocation; y++) {
					if(size >= 4) {
						sendPacket(pc, entranceX, y);
						sendPacket(pc, entranceX + 1, y);
					} else {
						sendPacket(pc, entranceX, y);
					}
				}
				/** 성문뚫어방지 **/
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
		if (pc != null) {
			SC_Door.send(pc, x, y, getDirection(), att);
		} else {
			SC_Door.broadcast(this, x, y, getDirection(), att);
		}
	}

	public void open() {
		if (isDead()) {
			return;
		}
		if (getOpenStatus() == ActionCodes.ACTION_Close) {
			isPassibleDoor(true);//하딘 시스템
			if (this.getDoorId() == 113 || this.getDoorId() == 125){					
				ThreadPool.getInstance().scheduleRequired(new DoorTimer(), 5000);
			}
			// 말하는 섬 던전 2층 보스문 시간 5분
			if (this.getDoorId() >= 4100 && this.getDoorId() <= 4111){
				ThreadPool.getInstance().scheduleRequired(new DoorTimer(), 300000);
			}
			if (this.getDoorId() >= 8001 && this.getDoorId() <= 8010) {
				ThreadPool.getInstance().scheduleRequired(new DoorTimer(), 1800000);
			}
			SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Open);
			setOpenStatus(ActionCodes.ACTION_Open);
			setPassable(L1DoorInstance.PASS);
			updateDoorCache(false); // 열림 -> 맵 뚫림
			sendDoorPacket(null);
		}
	}

	public void close() {
		if (isDead()) {
			return;
		}
		if (getOpenStatus() == ActionCodes.ACTION_Open) {
			isPassibleDoor(false);//하딘 시스템
			SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Close);
			setOpenStatus(ActionCodes.ACTION_Close);
			setPassable(L1DoorInstance.NOT_PASS);
			updateDoorCache(true); // 닫힘 -> 맵 막힘
			sendDoorPacket(null);
		}
	}

	public int DoorAction(int GfxId, int dooraction) {
		int Action = dooraction;
		if (Action == ActionCodes.ACTION_DoorDie) {
			if (GfxId == 11987 || GfxId == 11989 || GfxId == 11991 // 켄트성문
					|| GfxId == 12127 || GfxId == 12129 || GfxId == 12133// 기란성문
					|| GfxId == 12163 || GfxId == 12164 || GfxId == 12167 || GfxId == 12170) {
				Action = 36;
			}
		} else if (Action == ActionCodes.ACTION_DoorAction5) {
			if (GfxId == 11987 || GfxId == 11989 || GfxId == 11991 // 켄트성문
					|| GfxId == 12127 || GfxId == 12129 || GfxId == 12133// 기란성문
					|| GfxId == 12163 || GfxId == 12164 || GfxId == 12167 || GfxId == 12170) {
				Action = 35;
			}
		}
		return Action;
	}




	public void isPassibleDoor(boolean flag) {//하딘 시스템
		int leftEdgeLocation = this.getLeftEdgeLocation();
		int rightEdgeLocation = this.getRightEdgeLocation();
		int size = rightEdgeLocation - leftEdgeLocation;
		if (size == 0) {
			/*L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY()-1,flag);
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
			L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX()-2,this.getY()+2,flag);*/
		//	L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),this.getY(),flag);
		} else {
			if (this.getDirection() == 0) { 
				for (int doorX = leftEdgeLocation;
				doorX <= rightEdgeLocation; doorX++) {
				//	L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY(),flag);
					/*L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()-1,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()+1 ,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()-2,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(doorX,this.getY()+2 ,flag);*/
				}
			} else {
				for (int doorY = leftEdgeLocation;
				doorY <= rightEdgeLocation; doorY++) {
				//	L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX(),doorY,flag);
					/*L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() + 1,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() - 1,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() + 2,doorY,flag);
					L1WorldMap.getInstance().getMap(this.getMapId()).setPassable(this.getX() - 2,doorY,flag);*/
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
	
	public int getAutoStatus() {
		return _autostatus;
	}

	public void setAutoStatus(int i) {
		_autostatus = i;
	}
	
	// -----------------------------------------------------------
    // [ADD] 맵 캐시 갱신용 헬퍼 메서드
    // -----------------------------------------------------------
	private void updateDoorCache(boolean closed) {
        L1Map map = getMap();
        if (map == null) return;
        
        int start = Math.min(getLeftEdgeLocation(), getRightEdgeLocation());
        int end = Math.max(getLeftEdgeLocation(), getRightEdgeLocation());
        
        // 좌표값이 0이거나 이상하면 그냥 내 위치(X,Y) 하나만 막음
        if (start == 0 || end == 0) {
            map.setClosedDoorCached(getX(), getY(), closed);
            return;
        }

        int size = end - start;

        // 1칸짜리 문
        if (size == 0) {
            map.setClosedDoorCached(getX(), getY(), closed);
        } 
        // 큰 문 (루프가 안 돌아서 구멍 뚫리는 거 방지)
        else {
            if (getDirection() == 0) { // 가로 ( / ) : X좌표가 변함
                for (int x = start; x <= end; x++) {
                    map.setClosedDoorCached(x, getY(), closed);
                }
            } else { // 세로 ( \ ) : Y좌표가 변함
                for (int y = start; y <= end; y++) {
                    map.setClosedDoorCached(getX(), y, closed);
                }
            }
        }
    }

    public void refreshDoorCacheByStatus() {
        // 죽지 않았고 + 닫혀있으면 -> true(닫힘)
        boolean closed = (!isDead() && getOpenStatus() == ActionCodes.ACTION_Close);
        updateDoorCache(closed);
    }
}