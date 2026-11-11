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
import l1j.server.server.utils.EffectUtil;

public class EvilRiversTimer extends TimerTask {

	private ScheduledFuture<?> _future = null;
	private int _timeCounter = 0;
	private final L1PcInstance _pc;
	private final L1Character _target;
	private double _dmg = 0;

	public EvilRiversTimer(L1PcInstance pc, L1Character target, double dmg) {
		_target = target;
		_pc = pc;
		_dmg = dmg;
	}

	@Override
	public void run() {
		// TODO 자동 생성된 메소드 스텁
		try {
			if (_target == null || _target.isDead() || _target.getCurrentHp() <= 0) {
				stop();
				return;
			}
			if(_dmg > _target.getCurrentHp()) {
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
		S_EffectLocation ettect = S_EffectLocation.get(_target.getX(), _target.getY(), EffectUtil.이블_리버스_이팩트);
		_pc.sendPackets(ettect);
		_pc.broadcastPacket(ettect);
			
		if (_target instanceof L1PcInstance) {
			L1PcInstance targetPc = (L1PcInstance) _target;	
			// 모션 전달
			targetPc.sendActionAll(ActionCodes.ACTION_Damage);
			// 도중에 스킬 발동되면 데미지는 전달되지 않는다.
			if(commons.isLock(targetPc)) {
				return;
			}	
			if (_dmg > 0) {
				targetPc.setCurrentHp((int) (targetPc.getCurrentHp() - _dmg));
				_pc.setCurrentHp((int) (_pc.getCurrentHp() + _dmg));
			}
			targetPc.receiveDamage(_pc, (int) _dmg);
		} else if (_target instanceof L1NpcInstance) {
			L1NpcInstance npc = (L1NpcInstance) _target;
			// 모션 전달			
			npc.broadcastPacket(S_DoActionGFX.get(npc.getId(), ActionCodes.ACTION_Damage));
			// 도중에 스킬 발동되면 데미지는 전달되지 않는다.
			if(commons.isLock(npc)) {
				return;
			}	
			if(_dmg > 0) {
				npc.setCurrentHp((int) (npc.getCurrentHp() - _dmg));
				_pc.setCurrentHp((int) (_pc.getCurrentHp() + _dmg));
			}
			npc.receiveDamage(_pc, (int) _dmg);	
		}		
	}

	/**
	 * 타이머 시작
	 */
	public void begin() {
		_future = GeneralThreadPool.getInstance().scheduleAtFixedRate(this, 1000, 1000);
	}
	/**
	 * 타이머 종료
	 */
	public void stop() {
		if (_future != null) {
			_future.cancel(false);
		}
	}

}
