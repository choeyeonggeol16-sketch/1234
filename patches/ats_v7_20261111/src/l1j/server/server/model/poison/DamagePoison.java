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
package l1j.server.server.model.poison;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Poison;

public class DamagePoison extends Poison {

	private Thread _timer;
	private final L1Character _attacker;
	private final L1Character _target;
	private final int _damageSpan;
	private final int _damage;

	private DamagePoison(L1Character attacker, L1Character cha, int damageSpan, int damage) {
		_attacker = attacker;
		_target = cha;
		_damageSpan = damageSpan;
		_damage = damage;
		setPoisonType(Poison.POISON_DAMAGE);

		doInfection();
	}

	private class NormalPoisonTimer extends Thread {

		private NormalPoisonTimer() {
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(_damageSpan);
				} catch (InterruptedException e) {
					break;
				}
				if (!_target.hasSkillEffect(SkillId.상태_독)) {
					break;
				}
				if (_target instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) _target;
					pc.receiveDamage(_attacker, _damage);
					if (pc.isDead()) {
						break;
					}
					continue;
				}
				if (_target instanceof MonsterInstance) {
					MonsterInstance npc = (MonsterInstance) _target;
					npc.receiveDamage(_attacker, _damage);
					if (npc.isDead()) {
						return;
					}
				}
			}
			cure();
		}
	}

	boolean isDamageTarget(L1Character cha) {
		return (cha instanceof L1PcInstance) || (cha instanceof MonsterInstance);
	}

	private void doInfection() {
		if (_target instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _target;	
			
			pc.setSkillEffect(SkillId.상태_독, 30000);
			pc.setPoisonEffect(1);
			
			if (isDamageTarget(pc)) {
				_timer = new NormalPoisonTimer();
				GeneralThreadPool.getInstance().execute(_timer);
			}
		}
		
	}

	public static boolean doInfection(L1Character attacker, L1Character cha, int damageSpan, int damage) {
		if (!isValidTarget(cha, Poison.POISON_DAMAGE)) {
			return false;
		}

		cha.setPoison(new DamagePoison(attacker, cha, damageSpan, damage));
		return true;
	}

	@Override
	public int getEffectId() {
		return 1;
	}

	@Override
	public void cure() {
		if (_timer != null) {
			_timer.interrupt();
		}
		
		if (_target instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _target;		
			pc.sendPackets(new S_Poison(pc.getId(), 0), true);
			if(pc.hasSkillEffect(SkillId.커스_포이즌)) {
				pc.removeSkillEffect(SkillId.커스_포이즌);
			}
			if(pc.hasSkillEffect(SkillId.상태_독)) {
				pc.removeSkillEffect(SkillId.상태_독);
			}
			pc.setPoisonEffect(0);
			pc.setPoison(null);
		}
	}
}
