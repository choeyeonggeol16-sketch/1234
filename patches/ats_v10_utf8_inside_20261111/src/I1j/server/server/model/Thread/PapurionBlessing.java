package I1j.server.server.model.Thread;

import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.EffectUtil;

public class PapurionBlessing extends TimerTask {
	
	private static Logger _log = Logger.getLogger(PapurionBlessing.class.getName());
	private final L1PcInstance _pc;

	public PapurionBlessing(L1PcInstance pc) {
		_pc = pc;
	}

	@Override
	public void run() {
		try {
			if (_pc == null || _pc.isDead()) {
				return;
			}
			Papuregen();
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void Papuregen() {  
		_pc.sendPackets(S_SkillSound.get(_pc.getId(), EffectUtil.파푸리온_블레싱), true); 
	} 
}
