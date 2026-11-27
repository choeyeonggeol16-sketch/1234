package l1j.server.server.model.world.instance.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.Account;
import l1j.server.server.model.network.thread.CServerTimer;

import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1RaidDoorInstance;
import l1j.server.server.model.Instance.L1RaidPortalInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.instance.CInstanceWork;
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
	
	
	protected volatile int								_state;			// 현재 오브젝트의 상태
	protected final Object							_lock;			// 각각 오브젝트 인스턴스를 동기화하기 위한.
	protected int								_baseMapId;		// base map id.
	protected volatile L1Map 							_copyMap;		// copy map.
	protected volatile ArrayList<L1PcInstance> 			_users;			// 현재 입장 유저 리스트.
	protected L1PcInstance						_owner;			// 현재 인스턴스를 오픈한 유저
	protected L1RaidPortalInstance				_portal;		// 포탈 인스턴스.
	protected L1RaidDoorInstance				_door;			// 도어 인스턴스.
	protected L1NpcInstance						_boss;			// 보스 몬스터.
	protected RaidType						_type;			// 레이드 타입.
	protected ArrayList<CompensatorElement>		_compensators;	// 보상 리스트
	protected ArrayList<RaidBossSkill>		_skills;		// 스킬 리스트
	protected ArrayList<RaidBossCombo>		_combos;		// 콤보 리스트
	private static final Logger LOG = Logger.getLogger(RaidObject.class.getName());
    private final Set<Thread> eventThreads = new HashSet<>();
    private final Set<CServerTimer> productTimers = new HashSet<>();
    private CServerTimer expiry;
    private volatile CInstantZone<RaidObject> zone;
    private boolean closing;
    private boolean opened;
    private static final int RS_RESET = 16;
    private int active;
    protected ArrayList<L1NpcInstance> _npcList;
    protected RaidNpcSpawn _bossSpawn;
    public RaidObject(L1PcInstance owner, RaidType type){
		_state			= RS_CLOSE;
		_lock			= new Object();
		_baseMapId 		= type.getInputMapId();
		_copyMap		= null;
		_users           = new ArrayList<>();
		_owner 			= owner;
		_portal			= null;
		_door			= null;
		_type			= type;
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
    final void attach(CInstantZone<RaidObject> instantZone) {
        zone = instantZone;
        _copyMap = instantZone.map();
        instantZone.setExit(_type.getOutX(), _type.getOutY(), _type.getOutMapId());
        instantZone.onClose(this::disposeWorld);
    }

    final boolean open(BooleanSupplier accept) {
        final CInstantZone<RaidObject> room = zone;
        if (room == null) return false;
        final boolean[] accepted = {false};
        room.enter(() -> {
            synchronized (_lock) {
                if (closing) return;
                try {
                    init();
                    if (accept.getAsBoolean()) {
                        opened = true;
                        _state = RS_OPEN;
                        accepted[0] = true;
                    }
                } finally {
                    if (!opened) closeRaid();
                }
            }
        });
        return accepted[0];
    }

	public L1Map getCopyMap(){
		return _copyMap;
	}
	public int getCopyMapId(){
        L1Map map = _copyMap;
        return map == null ? -1 : map.getId();
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
    protected void dispose() {
        _owner = null;
        _users = new ArrayList<>();
        _boss = null;
        _bossSpawn = null;
        _compensators = null;
        _skills = null;
        _combos = null;
        _npcList = null;
        if (_portal != null) {
            _portal.setRaidObject(null);
            deleteNpc(_portal);
            _portal = null;
        }
        _door = null;
        _copyMap = null;
        zone = null;
    }

    private static void deleteNpc(L1NpcInstance npc) {
        try {
            npc.deleteMe();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Removing dragon raid NPC " + npc.getId(), failure);
        }
    }

    public void addUser(L1PcInstance pc) {
        if (pc.hasSkillEffect(L1SkillId.DRAGONRAID_BUFF)) {
            SC_ServerMessage.send(pc, 1626);
            return;
        }
        final CInstantZone<RaidObject> room = zone;
        if (room == null) return;
        room.enter(() -> {
            RaidMessage rejection = null;
            synchronized (_lock) {
                if (closing || !opened || (_state != RS_READY && _state != RS_OPEN)) {
                    rejection = RaidMessage.RAID_INPUT_FAIL_BEGIN;
                } else if (!_users.contains(pc)) {
                    if (_users.size() >= _type.getMaxUser()) {
                        rejection = RaidMessage.RAID_INPUT_FAIL_OVERUSER;
                    } else {
                        var members = new ArrayList<>(_users);
                        members.add(pc);
                        _users = members;
                    }
                }
            }
            if (rejection != null) rejection.sendMessage(pc);
            else _type.inputRaid(pc, room.mapId());
        });
    }

    public void delUser(L1PcInstance pc) {
        synchronized (_lock) {
            if (_users.contains(pc)) {
                var members = new ArrayList<>(_users);
                members.remove(pc);
                _users = members;
            }
        }
    }

    public void doorMove(L1PcInstance pc) {
        final CInstantZone<RaidObject> room = zone;
        if (room == null) return;
        room.enter(() -> {
            final boolean rejected;
            final boolean launch;
            synchronized (_lock) {
                rejected = closing || !opened || (_state != RS_READY && _state != RS_OPEN);
                if (!rejected && (!_users.contains(pc) || pc.getMap() != room.map())) return;
                launch = !rejected && _state == RS_OPEN;
                if (launch) _state = RS_READY;
            }
            if (rejected) {
                RaidMessage.RAID_INPUT_FAIL_BEGIN.sendMessage(pc);
                return;
            }
            try {
                _type.move(pc, room.mapId());
                if (launch) CInstanceEvents.start(() -> runActivity(() -> {
                    try {
                        run();
                    } finally {
                        if (_state != RS_RESET) closeRaid();
                    }
                }, null, true));
            } catch (RuntimeException failure) {
                synchronized (_lock) { if (launch && _state == RS_READY) _state = RS_OPEN; }
                throw failure;
            }
        });
    }

    void startEffect(L1NpcInstance caster, Runnable effect) {
        CInstanceEvents.start(() -> runActivity(effect, caster, true));
    }

    protected void scheduleProduct(Runnable action, long delay) {
        synchronized (_lock) {
            if (closing || _state != RS_START) return;
            final L1NpcInstance boss = _boss;
            final CServerTimer[] handle = new CServerTimer[1];
            handle[0] = CInstanceWork.schedule(() -> {
                synchronized (_lock) { productTimers.remove(handle[0]); }
                runActivity(action, boss, false);
            }, delay);
            if (handle[0] == null) throw new RejectedExecutionException("Raid product deadline unavailable");
            productTimers.add(handle[0]);
        }
    }

    private void runActivity(Runnable action, L1NpcInstance expectedBoss, boolean interruptible) {
        final CInstantZone<RaidObject> room = zone;
        if (room == null) return;
        room.enter(() -> {
            Thread current = Thread.currentThread();
            synchronized (_lock) {
                if (closing || (expectedBoss != null && (_state != RS_START || _boss != expectedBoss))) return;
                active++;
                if (interruptible) eventThreads.add(current);
            }
            try {
                action.run();
            } finally {
                final boolean reset;
                synchronized (_lock) {
                    eventThreads.remove(current);
                    reset = --active == 0 && !closing && _state == RS_RESET;
                }
                if (reset) {
                    try {
                        resetRaid();
                        synchronized (_lock) { if (!closing) _state = RS_OPEN; }
                    } catch (RuntimeException | Error failure) {
                        closeRaid();
                        throw failure;
                    }
                }
            }
        });
    }

    /** The same room event owns preparation and the subsequent combat loop. */
    @Override
    public void run() {
        if (_state != RS_READY || CInstanceEvents.isStopping()) return;
        readyRaid();
        if (_state != RS_START || CInstanceEvents.isStopping()) return;
        try {
            Thread.sleep(RaidLoadManager.MRS_MESSAGE_DELAY);
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
            return;
        }
        if (_state == RS_START && !CInstanceEvents.isStopping()) runRaid();
    }

    protected final void enterCombatPhase() {
        synchronized (_lock) {
            if (_state == RS_READY && !CInstanceEvents.isStopping()) _state = RS_START;
        }
    }

	/**
	 ********************************************** 레이드 메서드 및 추상메서드. **********************************************
	 *  **/
	/** initialize raid object. **/
	protected void init(){
        ArrayList<RaidNpcSpawn> spawns = RaidSpawnLoader.getInstance().get(_type.getId());
        if (spawns != null) setSpawn(spawns);
        expiry = CInstanceWork.schedule(this::closeRaid, _type.getRaidTime());
        if (expiry == null) throw new RejectedExecutionException("Raid close deadline unavailable");
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
    protected final void closeRaid() {
        final CInstantZone<RaidObject> room;
        synchronized (_lock) {
            if (closing) return;
            stopScripts();
            room = zone;
        }
        if (room != null) room.close(this::disposeWorld);
    }

    private void stopScripts() {
        closing = true;
        _state = RS_CLOSE;
        if (expiry != null) expiry.cancel();
        expiry = null;
        for (CServerTimer timer : productTimers) timer.cancel();
        productTimers.clear();
        for (Thread thread : eventThreads) {
            if (thread != Thread.currentThread()) thread.interrupt();
        }
    }

    private void disposeWorld() {
        synchronized (_lock) { if (!closing) stopScripts(); }
        final var room = zone;
        try {
            for (L1Object object : CWorld.getInstance().snapshotZoneOccupants(room.map())) {
                if (object instanceof L1PcInstance pc) {
                    try { _type.outRaid(pc); }
                    catch (RuntimeException failure) {
                        LOG.log(Level.WARNING, "Evacuating dragon raid member " + pc.getId(), failure);
                    }
                } else if (object instanceof L1NpcInstance npc && !(npc instanceof L1DollInstance)
                        && !(npc instanceof L1SummonInstance)
                        && !CInstantZoneManager.isPlayerCompanion(npc)) {
                    if (npc == _door) _door.setRaidObject(null);
                    deleteNpc(npc);
                }
            }
            MapCleaner.removeGroundItems(room.map());
        } finally {
            dispose();
        }
    }

	private void compensatePc(CompensatorElement element){
		var members = _users;
		int usize = members.size();
		int mid		= getCopyMapId();
		L1PcInstance pc;
		for(int i=0; i<usize; i++){
			pc = members.get(i);
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
			
			var members = _users;
			size = members.size();
			
			L1PcInstance pc 		= null;
			try(var raidShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_PacketBox.nativeDragonRaidBuffWriter(
                       (int)(RaidLoadManager.MRS_RAID_DELAY/1000)),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(int i=0; i<size; i++){
				pc = members.get(i);
				if(pc == null || pc.getMapId() != mid)
					continue;
				
				final Account account = pc.getAccount();
				SC_OnlyEffect.send(pc, pc.getId(), 7783);
                SC_OnlyEffect.broadcast(pc, pc.getId(), 7783);
				pc.setSkillEffect(L1SkillId.DRAGONRAID_BUFF, (int)RaidLoadManager.MRS_RAID_DELAY);
				long raidBuffUntil = System.currentTimeMillis() + RaidLoadManager.MRS_RAID_DELAY;
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(raidShared,pc);
				RaidUserTimeStore.update(account, raidBuffUntil);
			}
        }
			
			ArrayList<L1PcInstance> pcs = new ArrayList<L1PcInstance>();
			pcs.addAll(CWorld.getInstance().getAllPlayers());
			RaidMessage.RAID_CLEAR_SUCCESS_MESSAGE.sendMessage(pcs);
			pcs.clear();

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
        L1PcInstance owner = _owner;
        return "Dragon raid " + _type.getName() + ", map " + getCopyMapId() + ", state " + _state
                + ", owner " + (owner == null ? "-" : owner.getName()) + ", members " + _users.size();
    }

    protected boolean isInRaid() {
        for (L1PcInstance pc : _users) if (pc.getMap() == _copyMap) return true;
        return false;
    }

    protected final void requestReset() {
        synchronized (_lock) {
            if (closing) return;
            _state = RS_RESET;
            for (CServerTimer timer : productTimers) timer.cancel();
            productTimers.clear();
            for (Thread thread : eventThreads) {
                if (thread != Thread.currentThread()) thread.interrupt();
            }
        }
    }

    protected void resetRaid() {
        for (L1Object object : CWorld.getInstance().snapshotZoneOccupants(_copyMap)) {
            if (object instanceof L1NpcInstance npc && !(npc instanceof L1DollInstance)
                    && !(npc instanceof L1SummonInstance)
                    && !CInstantZoneManager.isPlayerCompanion(npc)
                    && npc != _portal && (_npcList == null || !_npcList.contains(npc))) {
                deleteNpc(npc);
            }
        }
        synchronized (_lock) {
            _boss = null;
            _users = new ArrayList<>();
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
				
				var members = _users;
				size = members.size();
				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_PacketBox.nativeGreenWriter(pck),Integer.MIN_VALUE,Integer.MAX_VALUE)){
for(int i=0; i<size; i++){
					pc = members.get(i);
					
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
			}catch(InterruptedException stopping){
                Thread.currentThread().interrupt();
                return;
			}
		}
	}
	
	public Collection<L1PcInstance> getUsersView(){
        return Collections.unmodifiableList(_users);
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
