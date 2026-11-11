package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.RepeatTask;
import l1j.server.server.model.Instance.EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;

public class MpRegeneration extends RepeatTask {
	private static Logger _log = Logger.getLogger(MpRegeneration.class.getName());

	private final L1PcInstance _pc;

	private int _regenPoint = 0;

	private int _curPoint = 4;

	public MpRegeneration(L1PcInstance pc, long interval) {
		super(interval);
		_pc = pc;
	}

	public void setState(int state) {
		if (_curPoint < state) {
			return;
		}

		_curPoint = state;
	}

	@Override
	public void execute() {
		try {
			if (_pc.isDead()) {
				return;
			}
			if (_pc.getCurrentMp() == _pc.getMaxMp()) {
				return;
			}

			_regenPoint += _curPoint;
			_curPoint = 4;//엠틱4->1초

			if (40 <= _regenPoint) { //원본 64 -> 40
				_regenPoint = 0;
				regenMp();
			}
		} catch (Exception e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void regenMp() {
		try {
			int wisStatMpr = _pc.getAbility().getTotalWis() / 5, 
					baseMpr = CalcUtil.calcBaseMpr(_pc.getType(), _pc.getAbility().getBaseWis()), 
					equipMpr = _pc.getMpr(), newMpr = 0,
					bonus = CommonUtil.randomRange(1, wisStatMpr);
			
			newMpr += bonus + equipMpr + baseMpr;
			
			if (L1HouseLocation.isInHouse(_pc.getX(), _pc.getY(), _pc.getMapId())) {
				newMpr += 3;
			}
			if (isInn(_pc)) {
				newMpr += 15;
			}
			if (isPlayerInLifeStream(_pc)) {
				newMpr += 20;
			}
			if (_pc.getFood() < 24 || isOverWeight(_pc)) {
				newMpr = 0;
				if (equipMpr > 0) {
					equipMpr = 0;
				}
			}
			
			int newMp = _pc.getCurrentMp() + newMpr;

			_pc.setCurrentMp(newMp);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isOverWeight(L1PcInstance pc) {
		if (pc.hasSkillEffect(SkillId.엑조틱_바이탈라이즈) || pc.hasSkillEffect(SkillId.어디셔널_파이어)) {
			return false;
		} 
		if (isInn(pc)) {
			return false;
		}
		return (50 <= pc.getInventory().getWeight100()) ? true : false;
	}
	
	private static boolean isPlayerInLifeStream(L1PcInstance pc) {
		EffectInstance effect = null;
		for (L1Object object : pc.getKnownObjects()) {
			if (!(object instanceof EffectInstance)) {
				continue;
			}
			effect = (EffectInstance) object;
			if (effect.getNpcId() == 81169 && effect.getLocation().getTileLineDistance(pc.getLocation()) < 4) {
				return true;
			}
		}
		return false;
	}

	private boolean isInn(L1PcInstance pc) {
		int mapId = pc.getMapId();
		return (mapId == 16384 || mapId == 16896 || mapId == 17408 || mapId == 17492 || mapId == 17820
				|| mapId == 17920 || mapId == 18432 || mapId == 18944 || mapId == 19456 || mapId == 19968
				|| mapId == 20480 || mapId == 20992 || mapId == 21504 || mapId == 22016 || mapId == 22528
				|| mapId == 23040 || mapId == 23552 || mapId == 24064 || mapId == 24576 || mapId == 25088) ? true
				: false;
	}
}
