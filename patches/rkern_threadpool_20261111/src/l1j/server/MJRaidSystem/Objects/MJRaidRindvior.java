package l1j.server.MJRaidSystem.Objects;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJRaidSystem.MJRaidObject;
import l1j.server.MJRaidSystem.MJRaidType;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossCombo;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossSkill;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.MJRaidSystem.Spawn.MJRaidNpcSpawn;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class MJRaidRindvior extends MJRaidObject{
	private static final int[] s_startMessages = {1755, 1756, 1757, 1758};
	private static final String s_lastMessage = "시스템 메시지 : 린드비오르를 공략할 수 있습니다.";
	public MJRaidRindvior(L1PcInstance owner, MJRaidType type){
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
			int ssize 				= 0;
			int csize				= 0;
			int count				= 0;
			int phase				= 0;
			double perToHp			= 0;
			MJRaidBossSkill skill 	= null;
			MJRaidBossCombo combo	= null;
			
			perToHp = ((double)_boss.getMaxHp() / 100D);
			if(_skills != null)
				ssize = _skills.size();
			
			if(_combos != null)
				csize = _combos.size();
			
			while((_state & RS_START) > 0){
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
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
