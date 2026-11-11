package I1j.server.server.model.Thread;

import java.util.TimerTask;

import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.EffectUtil;

public class HpRegenerationDollTimer extends TimerTask {
	private final L1PcInstance _pc;
	private final DollInstance _doll;
	private final int _hpr;

	public HpRegenerationDollTimer(L1PcInstance pc, DollInstance doll, int hpr) {
		this._pc = pc;
		this._doll = doll;
		this._hpr = hpr;
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
			regenHp();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void regenHp() {
		int newHp = this._pc.getCurrentHp() + _hpr;
		if (newHp < 0) {
			newHp = 0;
		}
		S_SkillSound s = S_SkillSound.get(this._pc.getId(), EffectUtil.인형_피_회복_이팩트);
		this._pc.sendPackets(s);
		this._pc.broadcastPacket(s);
		this._pc.setCurrentHp(newHp);
	}
}