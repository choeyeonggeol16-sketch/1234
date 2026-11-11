/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.model;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.RepeatTask;
import l1j.server.server.model.Instance.EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;

public class HpRegeneration extends RepeatTask {

	private static Logger _log = Logger.getLogger(HpRegeneration.class.getName());

	private final L1PcInstance _pc;

	private int _regenMax = 0;

	private int _regenPoint = 0;

	private int _curPoint = 4;

	private static Random _random = new Random(System.nanoTime());

	public HpRegeneration(L1PcInstance pc, long interval) {
		super(interval);
		_pc = pc;
		updateLevel();
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
			_regenPoint += _curPoint;
			_curPoint = 4;

			synchronized (this) {
				if (_regenMax <= _regenPoint) {
					_regenPoint = 0;
					regenHp();
				}
			}
		} catch (Exception e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void updateLevel() {
		final int lvlTable[] = new int[] { 30, 25, 20, 16, 14, 12, 11, 10, 9, 3, 2 };

		int regenLvl = Math.min(10, _pc.getLevel());
		if (30 <= _pc.getLevel() && _pc.isKnight()) {
			regenLvl = 11;
		}

		synchronized (this) {
			_regenMax = lvlTable[regenLvl - 1] * 4;
		}
	}

	public void regenHp() {
		try {
			if (_pc.isDead()) {
				return;
			}
			if (_pc.getCurrentHp() == _pc.getMaxHp() && !isUnderwater(_pc)) {
				return;
			}

			// 베이스 CON 보너스
			int conStatHpr = _pc.getAbility().getTotalCon() / 2, 
					baseHpr = CalcUtil.calcBaseHpr(_pc.getType(), _pc.getAbility().getBaseCon()), 
					equipHpr = _pc.getHpr(), newHpr = 0,
					bonus = CommonUtil.randomRange(1, conStatHpr);
			
			newHpr += bonus + equipHpr + baseHpr;
			
			if (isInn(_pc)) {
				newHpr += 15;
			}
			if (L1HouseLocation.isInHouse(_pc.getX(), _pc.getY(), _pc.getMapId())) {
				newHpr += 15;
			}

			boolean inLifeStream = false;
			if (isPlayerInLifeStream(_pc)) {
				inLifeStream = true;
				// 고대의 공간, 마족의 신전에서는 HPR+3은 없어져?
				newHpr += 20;
			}

			// 공복과 중량의 체크
			if (_pc.getFood() < 40 || isOverWeight(_pc) || _pc.hasSkillEffect(SkillId.버서커스)) {
				newHpr = 0;
				baseHpr = 0;
				// 장비에 의한 HPR 증가는 만복도, 중량에 의해 없어지지만, 감소인 경우는 만복도, 중량에 관계없이 효과가
				// 남는다
				if (equipHpr > 0) {
					equipHpr = 0;
				}
			}

			int newHp = _pc.getCurrentHp() + newHpr;

			if (newHp < 1) {
				newHp = 1; // HPR 감소 장비에 의해 사망은 하지 않는다
			}
			// 수중에서의 감소 처리
			// 라이프 시냇물로 감소를 없앨 수 있을까 불명
			if (isUnderwater(_pc)) {
				newHp -= 20;
				if (newHp < 1) {
					if (_pc.isGm()) {
						newHp = 1;
					} else {
						_pc.death(null); // HP가 0이 되었을 경우는 사망한다.
					}
				}
			}
			// Lv50 퀘스트의 고대의 공간 1 F2F에서의 감소 처리
			if (isLv50Quest(_pc) && !inLifeStream) {
				newHp -= 10;
				if (newHp < 1) {
					if (_pc.isGm()) {
						newHp = 1;
					} else {
						_pc.death(null); // HP가 0이 되었을 경우는 사망한다.
					}
				}
			}

			if (!_pc.isDead()) {
				_pc.setCurrentHp(Math.min(newHp, _pc.getMaxHp()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	private boolean isUnderwater(L1PcInstance pc) {
		// 워터 부츠 장비시인가, 에바의 축복 상태이면, 수중은 아니면 간주한다.
		if (pc.toArmorEquipped().isUnderwaterBreathArmor()) {
			return false;
		}
		if (pc.hasSkillEffect(SkillId.상태_호흡)) {
			return false;
		}
		return pc.getMap().isUnderwater();
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

	private boolean isLv50Quest(L1PcInstance pc) {
		int mapId = pc.getMapId();
		return (mapId == 2000 || mapId == 2001) ? true : false;
	}

	/**
	 * 지정한 PC가 라이프 시냇물의 범위내에 있는지 체크한다
	 * 
	 * @param pc
	 *            PC
	 * @return true PC가 라이프 시냇물의 범위내에 있는 경우
	 */
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