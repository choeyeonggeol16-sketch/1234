package l1j.server.server.model.world.instance.raid;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.model.network.thread.CServerTimer;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1RaidDoorInstance;
import l1j.server.server.model.Instance.L1RaidPortalInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.raid.boss.skill.RaidBossCombo;
import l1j.server.server.model.world.instance.raid.boss.skill.RaidBossSkill;
import l1j.server.server.model.world.instance.raid.compensator.CompensatorElement;
import l1j.server.server.model.world.instance.raid.compensator.ExpCompensator;
import l1j.server.server.model.world.instance.raid.compensator.GiveItemCompensator;
import l1j.server.server.model.world.instance.raid.compensator.ItemCompensator;
import l1j.server.server.model.world.instance.raid.loader.RaidBossSkillLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidCompensatorLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;
import l1j.server.server.model.world.instance.raid.loader.RaidSpawnLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidUserTimeStore;
import l1j.server.server.model.world.instance.raid.spawn.RaidNpcSpawn;

public abstract class RaidObject implements Runnable{
	protected static final int s_quakeDisplay = SC_DisplayEffect.QUAKE_DISPLAY;
	
	// RS is Raid State.
	protected static final int RS_CLOSE = 1;	// 닫힌 상태.
	protected static final int RS_OPEN 	= 2;	// 오픈 상태.
	protected static final int RS_READY = 4;	// 준비 상태.
	protected static final int RS_START = 8;	// 진행 상태.
	
	
	protected int								_state;			// 현재 오브젝트의 상태
	protected Object							_lock;			// 각각 오브젝트 인스턴스를 동기화하기 위한.
	protected int								_baseMapId;		// base map id.
	protected L1Map 							_copyMap;		// copy map.
	protected ArrayList<L1PcInstance> 			_users;			// 현재 입장 유저 리스트.
	protected L1PcInstance						_owner;			// 현재 인스턴스를 오픈한 유저
	protected L1RaidPortalInstance				_portal;		// 포탈 인스턴스.
	protected L1RaidDoorInstance				_door;			// 도어 인스턴스.
	protected L1NpcInstance						_boss;			// 보스 몬스터.
	protected RaidType						_type;			// 레이드 타입.
	protected ArrayList<CompensatorElement>		_compensators;	// 보상 리스트
	protected ArrayList<RaidBossSkill>		_skills;		// 스킬 리스트
	protected ArrayList<RaidBossCombo>		_combos;		// 콤보 리스트
	protected CloseTask							_task;			// 클로스 테스크
	protected ArrayList<L1NpcInstance> 			_npcList;
	protected RaidNpcSpawn					_bossSpawn;
	private Collection<L1PcInstance> 			_pcsView;
	public RaidObject(L1PcInstance owner, RaidType type){
		_state			= RS_CLOSE;
		_lock			= new Object();
		_baseMapId 		= type.getInputMapId();
		_copyMap		= null;
		_users			= null;
		_owner 			= owner;
		_portal			= null;
		_door			= null;
		_type			= type;
		_task			= null;
		_pcsView		= null;
		ArrayList<CompensatorElement> clist = RaidCompensatorLoader.getInstance().get(_type.getId());
		if(clist != null)
			setCompensators(clist);
		
		ArrayList<RaidBossSkill> sklist = RaidBossSkillLoader.getInstance().get(_type.getId());
		if(sklist != null)
			setSkills(sklist);
		
		ArrayList<RaidBossCombo> cblist = RaidBossSkillLoader.getInstance().getCombo(_type.getId());
		if(cblist != null)
			setCombos(cblist);
	}
	
	/**
	 ********************************************** 필드 접근자를 정의한다. **********************************************
	 *  **/
	/** raid type instance. **/
	public RaidType getRaidType(){
		return _type;
	}
	
	/** base map info. **/
	public int getBaseMapId(){
		return _baseMapId;
	}
	
	/** copy map info. **/
	public void setCopyMap(L1Map map){
		_copyMap = map;
	}
	public L1Map getCopyMap(){
		return _copyMap;
	}
	public int getCopyMapId(){
		if(_copyMap == null)
			return -1;
		return _copyMap.getId();
	}
	
	/**
	 ********************************************** 기본 공용 메서드를 정의한다. **********************************************
	 **/
	/** 사이즈 오버를 통지한다. **/
	public void notifySizeOver(){
		if(_owner != null)
			RaidMessage.RAID_OPEN_FAIL_SIZEOVER.sendMessage(_owner);
	}
	
	/** raid object dispose... **/
	public void dispose(){
		// Cancel the shared scheduled expiry to release its raid reference promptly.
		if (_task != null) {
			_task.cancel();
			_task = null;
		}
		_state		= RS_CLOSE;
		_baseMapId 	= -1;
		_copyMap 	= null;
		_owner		= null;
		if(_users != null){
			_users.clear();
			_users = null;
		}
		
		if(_portal != null){
			_portal.deleteMe();
			_portal = null;
		}
		
		if(_door != null){
			_door.deleteMe();
			_door = null;
		}
		
		_type			= null;
		_compensators 	= null;
	}
	
	/** 레이드 대기지역으로 이동한다. **/
	public void addUser(L1PcInstance pc){
		if (pc.hasSkillEffect(L1SkillId.DRAGONRAID_BUFF)){
			SC_ServerMessage.send(pc, 1626); 
			return; 
		}

		// 레이드가 닫혀 있거나, 이미 진행 중이라면,
		if((_state & RS_CLOSE) > 0 || (_state & RS_START) > 0){
			RaidMessage.RAID_INPUT_FAIL_BEGIN.sendMessage(pc);
			return;
		}

		synchronized(_lock){
			if(!_users.contains(pc)){
				if(_users.size() > _type.getMaxUser()){
					RaidMessage.RAID_INPUT_FAIL_OVERUSER.sendMessage(pc);
					return;
				}
				_users.add(pc);
			}
		}
		_type.inputRaid(pc, getCopyMapId());
	}
	
	public void delUser(L1PcInstance pc){
		if(_users == null)
			return;

		synchronized(_lock){
			if(_users.contains(pc))
				_users.remove(pc);
		}
	}
	
	/** 레이드 실전 지역으로 이동한다. **/
	public void doorMove(L1PcInstance pc){
		// 레이드가 닫혀 있거나, 이미 진행 중이라면,
		if((_state & RS_CLOSE) > 0 || (_state & RS_START) > 0){
			RaidMessage.RAID_INPUT_FAIL_BEGIN.sendMessage(pc);
			return;
		}
		
		int size 		= _users.size();
		L1PcInstance p 	= null;
		for(int i=0; i<size; i++){
			p = _users.get(i);
			if(p.getId() == pc.getId()){
				_type.move(pc, getCopyMapId());		
				if((_state & RS_OPEN) > 0){
                    _state = RS_READY;
                    try {
                        TaskLane.INSTANCE.execute(this);
                    } catch (java.util.concurrent.RejectedExecutionException rejected) {
                        if (_state == RS_READY) _state = RS_OPEN;
                        throw rejected;
                    }
				}
				break;
			}
		}
	}
	
	/** runnable **/
	/** 해당 인스턴스는 인스턴스 공간 전체를 관리하므로, run 메서드가 종료 시 호출됨, **/
	@Override
	public void run() {
		if((_state & RS_READY) > 0)			// 준비 상태에서 runnable 되었다면, 레이드 시작.
			readyRaid();
		else if((_state & RS_START) > 0)	// 레이드가 시작 되어 있는 상태에서 runnable되었다면, 종료시킬차례.
			runRaid();
	}
	
	/**
	 ********************************************** 레이드 메서드 및 추상메서드. **********************************************
	 *  **/
	/** initialize raid object. **/
	public void init(){
		_users 	= new ArrayList<L1PcInstance>(_type.getMaxUser());
		_state	= RS_OPEN;
		ArrayList<RaidNpcSpawn> slist = RaidSpawnLoader.getInstance().get(_type.getId());
		if(slist != null)
			setSpawn(slist);
		
		_task = new CloseTask(this);
	}
	
	protected void setCombos(ArrayList<RaidBossCombo> list){
		_combos = list;
	}
	
	protected void setSkills(ArrayList<RaidBossSkill> list){
		_skills = list;
	}
	
	protected void setCompensators(ArrayList<CompensatorElement> list){
		_compensators = list;
	}
	
	/** 스폰을 실시한다. **/
	protected void setSpawn(ArrayList<RaidNpcSpawn> spawnList){
		// spSize is spawn size.
		// stype is spawn type.
		int spSize 				= spawnList.size();	
		int stype				= 0;
		RaidNpcSpawn 	spawn 	= null;
		_npcList				= new ArrayList<L1NpcInstance>(spSize);
		for(int i=0; i<spSize; i++){
			spawn 	= spawnList.get(i);
			stype	= spawn.getSpawnType();
			
			if((stype & RaidNpcSpawn.ST_NORMAL) > 0)
				setNormalSpawn(spawn);
			else if((stype & RaidNpcSpawn.ST_RECT) > 0)
				setRectSpawn(spawn);
			else if((stype & RaidNpcSpawn.ST_CIRCLE) > 0)
				setCircleSpawn(spawn);
			else if((stype & RaidNpcSpawn.ST_BOSS) > 0)
				setBossSpawn(spawn);
			else if((stype & RaidNpcSpawn.ST_PORTAL) > 0)
				setPortalSpawn(spawn);
			else if((stype & RaidNpcSpawn.ST_DOOR) > 0)
				setDoorSpawn(spawn);
		}
	}
	
	// 스폰 지정
	protected abstract void setNormalSpawn(RaidNpcSpawn spawn);	// normal spawn 주로 npc가 사용될듯.
	protected abstract void setRectSpawn(RaidNpcSpawn spawn);		// rect spawn 지정된 영역에서 지정된 마리 만큼 랜덤으로 스폰.
	protected abstract void setCircleSpawn(RaidNpcSpawn spawn);	// circle spawn 원형 라인으로 오브젝트를 생성함. 발라카스 레이드 때문,
	protected abstract void setBossSpawn(RaidNpcSpawn spawn);		// boss spawn 용레이드는 바로 스폰되지 않으나, spawn 객체를 전달받을 필요성도 있고, 용레이드 이외의 클래스에서도 사용할 수 있게. 
	protected void setPortalSpawn(RaidNpcSpawn spawn){			// portal spawn은 공용으로 자주 사용될 것이므로 abstract가 아니다.
		_portal = (L1RaidPortalInstance)spawn.spawn(_owner.getMapId(), _owner.getX(), _owner.getY());
		_portal.setRaidObject(this);
	}
	protected void setDoorSpawn(RaidNpcSpawn spawn){				// door 역시 portal과 같은맥락.
		_door	= (L1RaidDoorInstance)spawn.spawn(getCopyMapId());
		_door.setRaidObject(this);
		_npcList.add(_door);
	}
	
	// 레이드 진행
	protected abstract void readyRaid();							// 레이드를 준비 상태로 만든다.
	protected abstract void runRaid();								// 레이드를 진행 상태로 만든다.
	protected void closeRaid(){										// 레이드를 종료시킨다. 공용으로 사용될 가능성이 높기에 abstract가 아님.
		_state = RS_CLOSE;
		if(_task != null){
			_task.cancel();
			_task = null;
		}
		
		if(_users != null){
			int 			size		= _users.size();
			int				mid		= getCopyMapId();
			L1PcInstance 	pc		= null;
			for(int i=0; i<size; i++){
				pc = _users.get(i);
				if(pc != null && pc.getMapId() == mid)
					_type.outRaid(pc);
			}
		}
		
		if(_npcList != null){
			_npcList.clear();
			_npcList = null;
		}
		_bossSpawn = null;
		RaidSpace.getInstance().releaseInstance(this);		
	}
	
	private void compensatePc(CompensatorElement element){
		int usize 	= _users.size();
		int mid		= getCopyMapId();
		L1PcInstance pc;
		for(int i=0; i<usize; i++){
			pc = _users.get(i);
			if(pc != null && !pc.isDead() && pc.getMapId() == mid)
				element.compensate(pc);
		}
	}
	
	protected void successRaid(){
		if(_compensators != null && _boss != null){
			int size 				= _compensators.size();
			int mid					= getCopyMapId();
			int x					= _boss.getX();
			int y					= _boss.getY();
			CompensatorElement element = null;
			for(int i=0; i<size; i++){
				element = _compensators.get(i);
				if(element instanceof ExpCompensator || element instanceof GiveItemCompensator)
					compensatePc(element);
				else if(element instanceof ItemCompensator)
					element.compensate(new int[]{mid, x, y});
			}
			_compensators = null;
			
			size					= _users.size();
			
			L1PcInstance pc 		= null;
			try(var raidShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_PacketBox.nativeDragonRaidBuffWriter(
                       (int)(RaidLoadManager.MRS_RAID_DELAY/1000)),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(int i=0; i<size; i++){
				pc = _users.get(i);
				if(pc == null || pc.getMapId() != mid)
					continue;
				
				SC_OnlyEffect.send(pc, pc.getId(), 7783);
                SC_OnlyEffect.broadcast(pc, pc.getId(), 7783);
				pc.setSkillEffect(L1SkillId.DRAGONRAID_BUFF, (int)RaidLoadManager.MRS_RAID_DELAY);
				Timestamp deleteTime = new Timestamp(System.currentTimeMillis() + RaidLoadManager.MRS_RAID_DELAY);
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(raidShared,pc);
				pc.getNetConnection().getAccount().setDragonRaid(deleteTime);
			}
        }
			
			ArrayList<L1PcInstance> pcs = new ArrayList<L1PcInstance>();
			pcs.addAll(L1World.getInstance().getAllPlayers());
			RaidMessage.RAID_CLEAR_SUCCESS_MESSAGE.sendMessage(pcs);
			pcs.clear();
			
			TaskLane.PERSISTENCE.execute(new RaidUserTimeStore(_users));
		}
	}
	
	public void failRaid(){
		closeRaid();
	}
	
	/**
	 ********************************************** 로깅용 메서드. **********************************************
	 *  **/
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(128);
		sb.append("class name is RaidObject.").append("\n");
		sb.append("baseMapId : ").append(getBaseMapId()).append("\n");
		sb.append("copyMapId : ").append(getCopyMapId()).append("\n");
		sb.append("state : ").append(_state).append("\n");
		sb.append("open user : ").append(_owner.getName()).append("\n");
		sb.append("user size : ").append(_users.size()).append("\n");
		sb.append("raid type : ").append(_type.toString()).append("\n");
		return sb.toString();
	}
	
	protected boolean isInRaid(){
		if(_users == null)
			return false;
		
		int size 		= _users.size();
		int cpMap 		= getCopyMapId();
		L1PcInstance pc = null;
		for(int i=0; i<size; i++){
			pc = _users.get(i);
			if(pc == null)
				continue;
			
			// 한명이라도 맵 안에 있다면,
			if(pc.getMapId() == cpMap)
				return true;
		}
		
		return false;
	}
	
	protected void resetRaid(){
		Iterator<L1Object> it 	= L1World.getInstance().getVisibleObjects(getCopyMapId()).values().iterator();
		L1NpcInstance 	obj 	= null;
		L1NpcInstance	npc		= null;
		int npcSize				= 0;
		boolean isNpc;
		if(_npcList != null)
			npcSize = _npcList.size();
		
		while(it.hasNext()){
			try{
				L1Object l1obj = it.next();
				if(l1obj == null || (l1obj instanceof L1DollInstance) || (l1obj instanceof L1SummonInstance))
					continue;
				
				if(_portal != null && l1obj.getId() == _portal.getId())
					continue;
				
				if((l1obj instanceof L1NpcInstance)){
					obj = (L1NpcInstance)l1obj;
					
					isNpc = false;
					for(int i=0; i<npcSize; i++){
						npc = _npcList.get(i);
						if(npc.getId() == obj.getId()){
							isNpc = true;
							break;
						}
					}
					
					if(!isNpc){
						obj.deleteMe();
						obj = null;
					}
				}else{
					obj.deleteMe();
					obj = null;
				}
			}catch(Exception e){}
		}
		
		if(_boss != null){
			_boss.deleteMe();
			_boss = null;
		}
		
		if(_users != null)
			_users.clear();
		_state = RS_OPEN;
	}
	
	/** 지정한 시간이 되면 종료를 시전할 객체 **/
	public class CloseTask extends TimerTask{
		private final RaidObject _obj;
		private final CServerTimer _future;
		public CloseTask(RaidObject obj){
			_obj = obj;
			_future = TaskLane.INSTANCE.schedule(this, obj.getRaidType().getRaidTime());
			if (_future == null)
				throw new RejectedExecutionException("Raid close deadline admission failed");
		}

		@Override
		public boolean cancel() {
			boolean cancelled = super.cancel();
			return _future.cancel() || cancelled;
		}

		@Override
		public void run() {
			if ((_obj._state & RS_CLOSE) == 0)
				_obj.failRaid();
		}
	}
	
	private static final long _divTime = 10 * 1000; 
	protected void waitClose(){
		if(_users == null || _users.size() <= 0)
			return;
		
		long allTime 			= RaidLoadManager.MRS_BOSSSPAWN_DELAY;
		int	size				= 0;
		int mid					= getCopyMapId();
		String pck = null;
		L1PcInstance pc			= null;
		while(allTime > 10){
			try{
				pck = String.format("%d초 후 마을로 이동합니다.", allTime / 1000);
				if(_users == null)
					return;
				
				size = _users.size();				
				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_PacketBox.nativeGreenWriter(pck),Integer.MIN_VALUE,Integer.MAX_VALUE)){
for(int i=0; i<size; i++){
					pc = _users.get(i);
					
					if(pc == null || pc.getMapId() != mid)
						continue;
					
					// [TEST] s_quakeDisplay 임시 주석 처리 - 종료 카운트다운 중 지진파 이펙트 차단
					//SC_DisplayEffect.send(pc, s_quakeDisplay);
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
				}
                }
			//	pck.clear();
				allTime -= _divTime;
				Thread.sleep(_divTime);
			}catch(Exception e){
				e.printStackTrace();
				return;
			}
		}
	}
	
	public Collection<L1PcInstance> getUsersView(){
		if(_pcsView == null)
			_pcsView = Collections.unmodifiableCollection(_users);
		return _pcsView;
	}
	public void notifyRaidPickupItem(L1PcInstance pc, L1ItemInstance item, int count){
		try{
			String msg = String.format("[%s]님이 %s(을)를 %d개를 획득했습니다.", pc.getName(), item.getLogName(), count);
			SC_SystemMessage.broadcastRecipients(getUsersView(),msg);
		//	msg.clear();
		}catch(NullPointerException excp){
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
