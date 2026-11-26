package l1j.server.server.model.world.instance.dungeon.hadin;

import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.utils.L1SpawnUtil;

public class HadinTrap implements Runnable {

	private static final Logger LOG = Logger.getLogger(HadinTrap.class.getName());
	private final short _map;
	private CServerTimer nextTick;
	public volatile boolean Running = true;

	private boolean LEVEL_1_TRAP_1 = false;
	private boolean LEVEL_1_TRAP_2 = false;
	private boolean LEVEL_1_TRAP_3 = false;
	private boolean LEVEL_1_TRAP_4 = false;
	private boolean LEVEL_2_TRAP_1 = false;
	private boolean LEVEL_2_TRAP_2 = false;
	private boolean LEVEL_2_TRAP_3 = false;
	private boolean LEVEL_2_TRAP_4 = false;
	private boolean LEVEL_3_TRAP_1 = false;
	private boolean LEVEL_3_TRAP_2 = false;
	private boolean LEVEL_3_TRAP_3 = false;
	private boolean LEVEL_3_TRAP_4 = false;

	private boolean TRAP_1 = false;
	
	private boolean LAST_TRAP_1 = false;
	private boolean LAST_TRAP_2 = false;
	private boolean LAST_TRAP_3 = false;
	private boolean LAST_TRAP_4 = false;
	private boolean LAST_TRAP_5 = false;
	private boolean LAST_TRAP_6 = false;
	private boolean LAST_TRAP_7 = false;

	private boolean LAST_TRAP = false;

	public boolean MonCK = false;
	public volatile boolean LEVEL_1_TRAP_CK = false;//첫방 발판 4개
	public volatile boolean LEVEL_2_TRAP_CK = false;//둘방 발판 1개
	public volatile boolean LEVEL_3_TRAP_CK = false;//셋방 발판 4개
	public volatile boolean LEVEL_4_TRAP_CK = false;//마지막방 발판

	public boolean BossRoomCK = false;
	public volatile boolean LAST_TRAP_CK = false;

	private HashMap<String, L1DoorInstance> doorList;

	public HadinTrap(short id){
		_map = (short)id;
		doorList = new HashMap<String, L1DoorInstance>();
		nextTick = CInstanceWork.scheduleRequired(this, 5000L);
	}

	@Override
	public synchronized void run() {
		if (!Running) return;
		nextTick = null;
		try {
			TRAP_RESET();
			for (L1Object ob : L1World.getInstance().getVisibleObjects(_map).values()) {
				if (ob instanceof L1PcInstance pc) {
					TRAP_ON(pc);
				} else if (ob instanceof L1DoorInstance door) {
					final int npcId = door.getNpcTemplate().get_npcId();
					if (npcId == 900151 || npcId == 900152)
						doorList.putIfAbsent(door.getSpawnLocation(), door);
				}
			}
			if (!MonCK) TRAP_ON_CK();
		} catch (RuntimeException failure) {
			LOG.log(Level.WARNING, "Hadin trap tick failed for map " + _map, failure);
		}
		if (Running) {
			nextTick = CInstanceWork.schedule(this, 2000L);
			if (nextTick == null) Running = false;
		}
	}

	public synchronized void stop() {
		Running = false;
		if (nextTick != null) {
			nextTick.cancel();
			nextTick = null;
		}
		doorList.clear();
	}


	private void TRAP_RESET(){
		LEVEL_1_TRAP_1 = false;
		LEVEL_1_TRAP_2 = false;
		LEVEL_1_TRAP_3 = false;
		LEVEL_1_TRAP_4 = false;
		LEVEL_2_TRAP_1 = false;
		LEVEL_2_TRAP_2 = false;
		LEVEL_2_TRAP_3 = false;
		LEVEL_2_TRAP_4 = false;
		LEVEL_3_TRAP_1 = false;
		LEVEL_3_TRAP_2 = false;
		LEVEL_3_TRAP_3 = false;
		LEVEL_3_TRAP_4 = false;
		TRAP_1 = false;
		LAST_TRAP_1 = false;
		LAST_TRAP_2 = false;
		LAST_TRAP_3 = false;
		LAST_TRAP_4 = false;
		LAST_TRAP_5 = false;
		LAST_TRAP_6 = false;
		LAST_TRAP_7 = false;
		LAST_TRAP = false;
	}

	private void TRAP_ON_CK(){
		L1DoorInstance door = null;
		if(LEVEL_1_TRAP_1 && LEVEL_1_TRAP_2 && LEVEL_1_TRAP_3 && LEVEL_1_TRAP_4){//첫방 4개 발판
			LEVEL_1_TRAP_CK = true;
			door = doorList.get("해골문 2");
			door.open();
			BonginSendPacekt(7611);
		} else if (TRAP_1){//두번째방 1개 발판
			LEVEL_2_TRAP_CK = true;
			door = doorList.get("해골문 4");
			door = doorList.get("해골문 5");
			door = doorList.get("해골문 6");
			door = doorList.get("해골문 8");
			door.open();
		} else if(LEVEL_2_TRAP_1 && LEVEL_2_TRAP_2 && LEVEL_2_TRAP_3 && LEVEL_2_TRAP_4){//셋방 발판 4개 문 2개 오픈
			LEVEL_3_TRAP_CK = true;
			door = doorList.get("해골문 7");
			door = doorList.get("해골문 9");
			BonginSendPacekt(7628);
			door.open();			
		} else if(LEVEL_3_TRAP_1 && LEVEL_3_TRAP_2 && LEVEL_3_TRAP_3 && LEVEL_3_TRAP_4){
			Collection<L1Object> cklist;
			cklist = L1World.getInstance().getVisibleObjects(_map).values();
			for(L1Object ob : cklist){
				if(ob == null)
					continue;
				if(ob instanceof L1PcInstance){
					L1PcInstance pc = (L1PcInstance) ob;
//					L1Teleport.teleport(pc, 32785, 32821, _map, 5, true);
					pc.start_teleport(32785, 32821, _map, 5, 169, true, false);
				}
			}
			L1SpawnUtil.spawn2(32743, 32928, _map, 900160, 0, 0, 0);
			LEVEL_4_TRAP_CK = true;
		} else if (LAST_TRAP_1 && LAST_TRAP_2 && LAST_TRAP_4 && LAST_TRAP_5){
			Collection<L1Object> cklist;
			cklist = L1World.getInstance().getVisibleObjects(_map).values();
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenObjectPlayersBestEffort(cklist,SC_ServerMessage.nativeWriter(8719));
			LAST_TRAP_CK = true;
		} else if (LAST_TRAP){
			Running = false;
			Collection<L1Object> cklist;
			cklist = L1World.getInstance().getVisibleObjects(_map).values();
			try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        SC_ServerMessage.nativeWriter(403,"$9355 (1)"),
                        Integer.MIN_VALUE,Integer.MAX_VALUE)){
                    for(L1Object ob : cklist){
				if(ob == null)
					continue;
				if(ob instanceof L1PcInstance){
					L1PcInstance pc = (L1PcInstance) ob;
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
					pc.getInventory().CreateOrAddItem(410108, 1); //비밀보따리
				}
			}
                }
			BonginSendPacekt(8719);
			return;
		}

		if(door != null){
			door.open();
		}
	}

	private void TRAP_ON(L1PcInstance pc){

		if(pc.getX() == 32666 && pc.getY() == 32817)       LEVEL_1_TRAP_1 = true;
		else if(pc.getX() == 32668 && pc.getY() == 32817)  LEVEL_1_TRAP_2 = true;
		else if(pc.getX() == 32666 && pc.getY() == 32819)  LEVEL_1_TRAP_3 = true;
		else if(pc.getX() == 32668 && pc.getY() == 32819)  LEVEL_1_TRAP_4 = true;//첫방 4개 발판

		else if(pc.getX() == 32684 && pc.getY() == 32816)  TRAP_1 = true;//두번째방 1개 발판

		else if(pc.getX() == 32703 && pc.getY() == 32800)  LEVEL_2_TRAP_1 = true;
		else if(pc.getX() == 32703 && pc.getY() == 32791)  LEVEL_2_TRAP_2 = true;
		else if(pc.getX() == 32710 && pc.getY() == 32803)  LEVEL_2_TRAP_3 = true;
		else if(pc.getX() == 32712 && pc.getY() == 32793)  LEVEL_2_TRAP_4 = true;//셋째방 4개 발판

		else if(pc.getX() == 32807 && pc.getY() == 32837)  LEVEL_3_TRAP_1 = true;
		else if(pc.getX() == 32809 && pc.getY() == 32837)  LEVEL_3_TRAP_2 = true;
		else if(pc.getX() == 32807 && pc.getY() == 32839)  LEVEL_3_TRAP_3 = true;
		else if(pc.getX() == 32809 && pc.getY() == 32839)  LEVEL_3_TRAP_4 = true;

		else if(pc.getX() == 32798 && pc.getY() == 32866)  LAST_TRAP_1 = true;
		else if(pc.getX() == 32801 && pc.getY() == 32864)  LAST_TRAP_2 = true;
	//	else if(pc.getX() == 32805 && pc.getY() == 32864)  LAST_TRAP_3 = true;
		else if(pc.getX() == 32798 && pc.getY() == 32872)  LAST_TRAP_4 = true;
		else if(pc.getX() == 32800 && pc.getY() == 32874)  LAST_TRAP_5 = true;
	//	else if(pc.getX() == 32804 && pc.getY() == 32874)  LAST_TRAP_6 = true;
	//	else if(pc.getX() == 32805 && pc.getY() == 32871)  LAST_TRAP_7 = true;
		else if(pc.getX() == 32802 && pc.getY() == 32868)  LAST_TRAP = true;
	}

	private void BonginSendPacekt(int count){
		Collection<L1Object> cklist;
		cklist = L1World.getInstance().getVisibleObjects(_map).values();
		l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenObjectPlayersBestEffort(cklist,SC_PacketBox.nativeGreenWriter("$"+count));
	}

}