package I1j.server.server.model.Thread;

import java.util.TimerTask;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_EffectLocation;
import l1j.server.server.utils.EffectUtil;

public class LindviorBlessing extends TimerTask {

	private final L1PcInstance _pc;

	public LindviorBlessing(L1PcInstance pc) {
		_pc = pc;
	}

	@Override
	public void run() {
		try {
			if (_pc == null || _pc.isDead()) {
				return;
			}
			System.out.println("dd?");
			Papuregen();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void Papuregen() {
		_pc.sendPackets(S_EffectLocation.get(_pc.getX(), _pc.getY(), (short) EffectUtil.린드비오르_블레싱), true);
	}
}

