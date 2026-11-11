package I1j.server.server.model.Thread;

import java.util.TimerTask;

import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.EffectUtil;

public class MpRegenerationDollTimer extends TimerTask {
	private final L1PcInstance _pc;
	private final DollInstance _doll;
	private final int _hpr;

	public MpRegenerationDollTimer(L1PcInstance pc, DollInstance doll, int mpr) {
		_pc = pc;
		_doll = doll;
		_hpr = mpr;
	}

	@Override
	public void run() {
		try {
			if (_pc == null || _pc.isDead()) {
				if (_doll != null) {
					_doll.deleteDoll();
				}
				return;
			}
			regenMp();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void regenMp() {
		int newMp = _pc.getCurrentMp() + _hpr;
		if (newMp < 0) {
			newMp = 0;
		}
		_pc.setCurrentMp(newMp);
		S_SkillSound s = S_SkillSound.get(this._pc.getId(), EffectUtil.ÀÎÇü_¿¥ÇÇ_È¸º¹_ÀÌÆåÆ®);
		this._pc.sendPackets(s);
		this._pc.broadcastPacket(s);
	}
}