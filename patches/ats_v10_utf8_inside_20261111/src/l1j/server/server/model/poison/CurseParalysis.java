
package l1j.server.server.model.poison;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Paralysis;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_ServerMessage;

public class CurseParalysis extends L1Paralysis {

	private final L1Character _target;

	private final int _delay;

	private final int _time;

	private Thread _timer;

	private class ParalysisDelayTimer extends Thread {
		
		private ParalysisDelayTimer() {
			super("l1j.server.server.model.L1CurseParalysis.ParalysisDelayTimer");
		}

		public void run() {
			try {
				_target.setSkillEffect(SkillId.상태_커스_패럴라이즈, 0);
				try {
					Thread.sleep(_delay);
				} catch (InterruptedException e) {
					_target.killSkillEffectTimer(SkillId.상태_커스_패럴라이즈);
					return;
				} 
				if (_target instanceof L1PcInstance) {
					L1PcInstance player = (L1PcInstance) _target;
					if (!player.isDead()) {
						player.sendPackets(new S_Paralysis(S_Paralysis.TYPE_PARALYSIS, true)); // 마비 상태로 한다
					}				
				} 
				_target.setParalyzed(true);
				_timer = new CurseParalysis.ParalysisTimer();
				GeneralThreadPool.getInstance().execute(_timer);
				if (isInterrupted()) {
					_timer.interrupt(); 
				}	
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}

	private class ParalysisTimer extends Thread {
		
		private ParalysisTimer() {
			super("l1j.server.server.model.L1CurseParalysis.ParalysisTimer");
		}
		public void run() {
			try {
				_target.killSkillEffectTimer(SkillId.상태_커스_패럴라이즈);
				_target.setSkillEffect(SkillId.상태_커스_패럴라이즈2, 0);
				try {
					Thread.sleep(_time);
				} catch (InterruptedException e) {
					if (_target instanceof L1PcInstance)
						_target.sendPackets(new S_Paralysis(S_Paralysis.TYPE_PARALYSIS, false));
				} 
				_target.killSkillEffectTimer(SkillId.상태_커스_패럴라이즈2);
				
				if (_target instanceof L1PcInstance) {
					L1PcInstance player = (L1PcInstance) _target;
					if (!player.isDead())
						player.sendPackets(new S_Paralysis(S_Paralysis.TYPE_PARALYSIS, false)); 
				} 
				_target.setParalyzed(false);
				cure();
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}
	private CurseParalysis(L1Character cha, int delay, int time) {
		this._target = cha;
		this._delay = delay;
		this._time = time;
		curse();
	}

	private void curse() {
		if (_target instanceof L1PcInstance) {
			L1PcInstance player = (L1PcInstance) _target;
			player.sendPackets(new S_ServerMessage(212), true);
		} 
		_target.setPoisonEffect(2);
		_timer = new ParalysisDelayTimer();
		GeneralThreadPool.getInstance().execute(_timer);
	}

	public static boolean curse(L1Character cha, int delay, int time) {
		if (!(cha instanceof L1PcInstance) && !(cha instanceof MonsterInstance)) {
			return false; 
		}

		if (cha.hasSkillEffect(SkillId.상태_커스_패럴라이즈) || cha.hasSkillEffect(SkillId.상태_커스_패럴라이즈2)) {
			return false; 
		}	
		cha.setParalaysis(new CurseParalysis(cha, delay, time));
		return true;
	}

	public int getEffectId() {
		return 2;
	}

	public void cure() {
		if (_timer != null) {
			_timer.interrupt(); 
		}
		_target.removeSkillEffect(SkillId.커스_패럴라이즈);
		_target.removeSkillEffect(SkillId.상태_커스_패럴라이즈);
		_target.removeSkillEffect(SkillId.상태_커스_패럴라이즈2);
		_target.setPoisonEffect(0);
		_target.setParalaysis(null);
	}
}


