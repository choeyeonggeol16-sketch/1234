package l1j.server.server.model.world.instance.raid.objects;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.raid.RaidObject;
import l1j.server.server.model.world.instance.raid.RaidType;
import l1j.server.server.model.world.instance.raid.boss.skill.RaidBossCombo;
import l1j.server.server.model.world.instance.raid.boss.skill.RaidBossSkill;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;
import l1j.server.server.model.world.instance.raid.spawn.RaidNpcSpawn;

public class RaidRindvior extends RaidObject{
	private static final int[] s_startMessages = {1755, 1756, 1757, 1758};
	private static final String s_lastMessage = "시스템 메시지 : 린드비오르를 공략할 수 있습니다.";
	public RaidRindvior(L1PcInstance owner, RaidType type){
		super(owner, type);
	}
	
	@Override
	protected void setNormalSpawn(RaidNpcSpawn spawn) {
		_npcList.add(spawn.spawn(getCopyMapId()));
	}

	@Override
	protected void setRectSpawn(RaidNpcSpawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnRectangle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setCircleSpawn(RaidNpcSpawn spawn) {
		ArrayList<L1NpcInstance> list = spawn.spawnCircle(getCopyMapId());
		if(list != null)
			_npcList.addAll(list);
	}

	@Override
	protected void setBossSpawn(RaidNpcSpawn spawn) {
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
		int 			size 			= 0;
		int 			cpMap		= 0;
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
				Thread.sleep(RaidLoadManager.MRS_MESSAGE_DELAY);
				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                  i<s_startMessages.length
                    ? SC_ServerMessage.nativeWriter(s_startMessages[i])
                    : SC_SystemMessage.nativeWriter(s_lastMessage),
                  Integer.MIN_VALUE,Integer.MAX_VALUE)){
                 for(int j=0; j<size; j++){
					pc = _users.get(j);
					if(pc != null && pc.getMapId() == cpMap){
												l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
						
						// [TEST] 3.8 클라이언트 크래시 방지 - s_quakeDisplay(화면 흔들림) 차단
						//if(i == 0)
						//	pc.sendPackets(s_quakeDisplay, false);
					}
				}
             }
			}
			if(_bossSpawn != null)
				_boss = _bossSpawn.spawnBoss(getCopyMapId());
			
			_state = RS_START;
			TaskLane.INSTANCE.scheduleRequired(this, RaidLoadManager.MRS_MESSAGE_DELAY);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	@Override
	protected void runRaid() {
		try{
			int ssize 				= 0;
			int csize				= 0;
			int count				= 0;
			int phase				= 0;
			double perToHp			= 0;
			RaidBossSkill skill 	= null;
			RaidBossCombo combo	= null;
			
			perToHp = ((double)_boss.getMaxHp() / 100D);
			if(_skills != null)
				ssize = _skills.size();
			
			if(_combos != null)
				csize = _combos.size();
			
			while((_state & RS_START) > 0){
				Thread.sleep(RaidLoadManager.MRS_THREAD_CLOCK);
				
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
				
				if(csize != 0 && (++count % RaidLoadManager.MRS_CB_COUNT) == 0){
					combo = _combos.get(ThreadLocalRandom.current().nextInt(csize));
					phase = combo.getPhase();
					if(((int)(perToHp * phase)) > _boss.getCurrentHp()){
						combo.action(_boss, _users);
						continue;
					}
				}
				
				if(ssize != 0 && ThreadLocalRandom.current().nextInt(100) <= RaidLoadManager.MRS_BS_USE_RATE){
					skill = _skills.get(ThreadLocalRandom.current().nextInt(ssize));
					skill.action(_boss, _users);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
