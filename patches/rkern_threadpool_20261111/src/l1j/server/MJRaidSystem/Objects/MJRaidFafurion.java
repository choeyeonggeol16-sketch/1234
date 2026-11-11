package l1j.server.MJRaidSystem.Objects;

import java.util.ArrayList;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJRaidSystem.MJRaidObject;
import l1j.server.MJRaidSystem.MJRaidType;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossCombo;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossSkill;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.MJRaidSystem.Spawn.MJRaidNpcSpawn;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class MJRaidFafurion extends MJRaidObject{
	private static final int[] s_startMessages = {1657, 1658, 1659, 1660, 1661, 1662};
	private static final String s_lastMessage = "시스템 메세지 : 파푸리온을 공략 하실수 있습니다.";
	public MJRaidFafurion(L1PcInstance owner, MJRaidType type){
		super(owner, type);
	}

	@Override
	protected void setNormalSpawn(MJRaidNpcSpawn spawn) {
		_npcList.add(spawn.spawn(getCopyMapId()));
	}

	@Override
	protected void setRectSpawn(MJRaidNpcSpawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnRectangle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setCircleSpawn(MJRaidNpcSpawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnCircle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setBossSpawn(MJRaidNpcSpawn spawn) {
		_bossSpawn = spawn;
	}

	@Override
	public void dispose(){
		super.dispose();
		
		if(_npcList != null){
			_npcList.clear();
			_npcList = null;
		}
	}
	
	@Override
	protected void readyRaid() {
		L1PcInstance 	pc 		= null;
		int 			size 	= 0;
		int 			cpMap	= 0;
		try{
			cpMap				= getCopyMapId();
			L1PcInstance[] pcs	= null;
			boolean isInArea 	= true;
	
			while(isInArea){
				Thread.sleep(500);
				pcs = _users.toArray(new L1PcInstance[_users.size()]);
				for(int i=0; i<pcs.length; i++){
					pc = pcs[i];
					if(pc == null)
						continue;
					
					if(pc.getMapId() != cpMap){
						delUser(pc);						
						continue;
					}
					
					if(_bossSpawn.isInArea(pc)){
						isInArea = false;
						break;
					}
				}
			}
			
			size = _users.size();
			for(int i=0; i<=s_startMessages.length; i++){
				Thread.sleep(MJRaidLoadManager.MRS_MESSAGE_DELAY);
				for(int j=0; j<size; j++){
					pc = _users.get(j);
					if(pc != null && pc.getMapId() == cpMap){
						if (i < s_startMessages.length) SC_ServerMessage.send(pc, s_startMessages[i]);
						else SC_SystemMessage.send(pc, s_lastMessage);
						
						// [TEST] 3.8 클라이언트 크래시 방지 - s_quakeDisplay(화면 흔들림) 차단
						//if(i == 0)
						//	pc.sendPackets(s_quakeDisplay, false);
					}
				}
			}
			if(_bossSpawn != null)
				_boss = _bossSpawn.spawnBoss(getCopyMapId());
			
			_state = RS_START;
			ThreadPool.getInstance().scheduleRequired(this, MJRaidLoadManager.MRS_MESSAGE_DELAY);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	@Override
	protected void runRaid() {
		try{
			int ssize 					= 0;
			int csize					= 0;
			int count					= 0;
			int phase					= 0;
			double perToHp				= 0;
			MJRaidBossSkill skill 		= null;
			MJRaidBossCombo combo		= null;
			long startTime				= System.currentTimeMillis();
			long cur					= 0;
			
			perToHp = ((double)_boss.getMaxHp() / 100D);
			if(_skills != null)
				ssize = _skills.size();
			
			if(_combos != null)
				csize = _combos.size();
			
			while((_state & RS_START) > 0){
				try{
					Thread.sleep(MJRaidLoadManager.MRS_THREAD_CLOCK);
					
					// 한번 더 검사.
					if((_state & RS_START) == 0)
						return;
					
					if(!isInRaid()){
						resetRaid();
						return;
					}
					
					if(_boss == null)
						continue;
					
					if(_boss.isDead()){
						successRaid();
						waitClose();
						closeRaid();
						return;
					}
					
					cur = System.currentTimeMillis();
					if(cur - startTime >= MJRaidLoadManager.MRS_SP_PRODUCT_TIME){
						startTime = cur;
						SC_NpcChatPacket.broadcastNpc(_boss, _boss, "$8467", 0);
						new SpawnProductTask();
						Thread.sleep(3000);
						continue;
					}
					
					if(csize != 0 && (++count % MJRaidLoadManager.MRS_CB_COUNT) == 0){
						combo = _combos.get(ThreadLocalRandom.current().nextInt(csize));
						phase = combo.getPhase();
						if(((int)(perToHp * phase)) > _boss.getCurrentHp()){
							combo.action(_boss, _users);
							continue;
						}
					}
					
					if(ssize != 0 && ThreadLocalRandom.current().nextInt(100) <= MJRaidLoadManager.MRS_BS_USE_RATE){
						skill = _skills.get(ThreadLocalRandom.current().nextInt(ssize));
						skill.action(_boss, _users);
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private MJRaidNpcSpawn createSpawn(int npcId, int type, int numOfSpawn, int range, int heading){
		return new MJRaidNpcSpawn(
				npcId, 
				type, 
				numOfSpawn, 
				_boss.getX() - range,
				_boss.getY() - range,
				_boss.getX() + range,
				_boss.getY() + range,
				0);
	}
	
	class SpawnProductTask extends TimerTask{
		ArrayList<L1NpcInstance> 	_pearlList;
		ArrayList<L1NpcInstance> 	_torList;
		L1NpcInstance 				_mystPearl;
		L1NpcInstance 				_sael1;
		L1NpcInstance 				_sael2;
		
		public SpawnProductTask(){
			int mid				= getCopyMapId();
			MJRaidNpcSpawn sp 	= createSpawn(MJRaidLoadManager.MRS_SP_5COLOR_PEARL_ID, MJRaidNpcSpawn.ST_RECT, 3, 10, 0);
			_pearlList 			= sp.spawnRectangleBoss(mid);
			sp					= createSpawn(MJRaidLoadManager.MRS_SP_MYST_5COLOR_PEARL_ID, MJRaidNpcSpawn.ST_RECT, 1, 10, 0);
			_mystPearl			= sp.spawnRandomBoss(mid);
			sp					= createSpawn(MJRaidLoadManager.MRS_SP_TORNAR_ID, MJRaidNpcSpawn.ST_RECT, 4, 20, 0);
			_torList			= sp.spawnRectangleBoss(mid);
			sp					= createSpawn(MJRaidLoadManager.MRS_SP_SAEL1_ID, MJRaidNpcSpawn.ST_RECT, 1, 5, 0);
			_sael1				= sp.spawnRandomBoss(mid);
			sp					= createSpawn(MJRaidLoadManager.MRS_SP_SAEL2_ID, MJRaidNpcSpawn.ST_RECT, 1, 5, 0);
			_sael2				= sp.spawnRandomBoss(mid);
			if (ThreadPool.getInstance().schedule(this, MJRaidLoadManager.MRS_SP_PRODUCT_DESTTIME - 50) == null)
				throw new RejectedExecutionException("Raid product deadline admission failed");
		}
		
		@Override
		public void run() {
			if((_state & RS_START) == 0 || _boss == null || _boss.isDead())
				return;
			
			L1NpcInstance npc = null;
			if(_pearlList != null){
				int size = _pearlList.size();
				for(int i=0; i<size; i++){
					npc = _pearlList.get(i);
					if(npc != null){
						npc.deleteMe();
						npc = null;
					}
				}
				_pearlList.clear();
				_pearlList = null;
			}
			
			if(_torList != null){
				int size = _torList.size();
				for(int i=0; i<size; i++){
					npc = _torList.get(i);
					if(npc != null){
						npc.deleteMe();
						npc = null;
					}
				}
				_torList.clear();
				_torList = null;
			}
			
			if(_mystPearl != null){
				_mystPearl.deleteMe();
				_mystPearl = null;
			}
			
			if(_sael1 != null){
				_sael1.deleteMe();
				_sael1 = null;
			}
			
			if(_sael2 != null){
				_sael2.deleteMe();
				_sael2 = null;
			}
		}
	}
}
