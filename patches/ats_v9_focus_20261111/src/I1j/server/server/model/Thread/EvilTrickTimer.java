package I1j.server.server.model.Thread;

import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;

import l1j.server.commons;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_EffectLocation;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;

public class EvilTrickTimer extends TimerTask {
	
	private ScheduledFuture<?> _future = null;
	private int _timeCounter = 0;
	private final L1PcInstance _pc;
	private final L1Character _target;
	
	public EvilTrickTimer(L1PcInstance pc, L1Character target) {
		_pc = pc;
		_target = target;
		
	}
	@Override
	public void run() {
		// TODO 자동 생성된 메소드 스텁
		try {
			if (_target == null || _target.isDead() || _target.getCurrentMp() <= 0) {
				stop();
				return;
			}
			_timeCounter++;
			attack();
			if (_timeCounter >= 3) {
				stop();
				return;
			}
		} catch (Throwable e) {
			stop();
			e.printStackTrace();
			return;
		}
	}
	
	public void attack() {
		
		// 데미지 
		int dmg = CommonUtil.randomRange(2, 5);
		
		// 이팩트
		S_EffectLocation effect = S_EffectLocation.get(_target.getX(), _target.getY(), EffectUtil.이블_트릭_이팩트);
		_pc.sendPackets(effect);
		_pc.broadcastPacket(effect);	
		
		if (_target instanceof L1PcInstance) {
			L1PcInstance targetPc = (L1PcInstance) _target;		
			   // 모션 전달
			targetPc.sendActionAll(ActionCodes.ACTION_Damage);
			
			// 도중에 스킬 발동되면 데미지는 전달되지 않는다.
			if(commons.isLock(targetPc)) {
				return;
			}	
			if (dmg > 0 && targetPc.getCurrentMp() > 0) {
				if (dmg > targetPc.getCurrentMp()) {
					dmg = targetPc.getCurrentMp();
				}
				targetPc.setCurrentMp((int) (targetPc.getCurrentMp() - dmg));
				_pc.setCurrentMp((int) (_pc.getCurrentMp() + dmg));
			}
		} else if (_target instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) _target;
			
			npc.broadcastPacket(S_DoActionGFX.get(npc.getId(), ActionCodes.ACTION_Damage));
			
			if(commons.isLock(npc)) {
				return;
			}
			if (dmg > 0 && npc.getCurrentMp() > 0) {
				if (dmg > npc.getCurrentMp()) {
					dmg = npc.getCurrentMp();
				}
				npc.setCurrentMp((int) (npc.getCurrentMp() - dmg));
				_pc.setCurrentMp((int) (_pc.getCurrentMp() + dmg));
			}	
		}	
	}
	
	/** 타이머 시작 **/
	public void begin() {
		_future = GeneralThreadPool.getInstance().scheduleAtFixedRate(this, 1000, 1000);

	}
	/** 타이머 종료 **/
	public void stop() {
		if (_future != null) {
			_future.cancel(false);
		}
	}

	
}
