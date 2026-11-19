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

import l1j.server.server.RepeatTask;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

public class L1DamagePoison extends L1Poison {

	private RepeatTask _timer;
	private final L1Character _attacker;
	private final L1Character _target;
	private final int _damageSpan;
	private final int _damage;
	private final int _time;
	private boolean _tomahawk;

	private L1DamagePoison(L1Character attacker, L1Character cha, int damageSpan, int damage, boolean tomahawk, int time) {
		_attacker = attacker;
		_target = cha;
		_damageSpan = damageSpan;
		_damage = damage;
		_tomahawk = tomahawk;
		_time = time;
		doInfection();
	}

	private class NormalPoisonTimer extends RepeatTask {

		NormalPoisonTimer() {
			super(_damageSpan);
		}

		@Override
		protected void onScheduleRejected() {
			L1DamagePoison.this.cure();
		}

		@Override
		public void execute() {
			L1PcInstance player = null;
			L1MonsterInstance mob = null;
			do {
				if (!_target.hasSkillEffect(L1SkillId.STATUS_POISON) && !_target.hasSkillEffect(L1SkillId.STATUS_TOMAHAWK)) {
					cure();
					break;
				}

				if (_target.hasSkillEffect(L1SkillId.ICE_LANCE) || _target.hasSkillEffect(L1SkillId.MOB_COCA)
						|| _target.hasSkillEffect(L1SkillId.MOB_BASILL) || _target.hasSkillEffect(L1SkillId.EARTH_BIND)) {
					cure();
					break;
				}

				if (_target instanceof L1PcInstance) {
					player = (L1PcInstance) _target;
					player.receiveDamage(_attacker, _damage);

					if (player.isDead()) {
						cure();
						break;
					}
				} else if (_target instanceof L1MonsterInstance) {
					mob = (L1MonsterInstance) _target;
					mob.receiveDamage(_attacker, _damage);
	
					if (mob.isDead()) {
						cancel();
						return;
					}
				}
			} while (false);
		}
	}

	boolean isDamageTarget(L1Character cha) {
		return (cha instanceof L1PcInstance) || (cha instanceof L1MonsterInstance);
	}

	private void doInfection() {
		if(_tomahawk) {
			_target.setSkillEffect(L1SkillId.STATUS_TOMAHAWK, _time);
		} else {
			if(_target instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) _target;
				SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 1, 30);
			}
			_target.setSkillEffect(L1SkillId.STATUS_POISON, _time);
			_target.setPoisonEffect(1);
		}
		if (isDamageTarget(_target)) {
			_timer = new NormalPoisonTimer();
		}
	}

	public static boolean doInfection(L1Character attacker, L1Character cha, int damageSpan, int damage, boolean tomahawk, int time) {
		if (!isValidTarget(cha)) {
			return false;
		}

		final L1DamagePoison poison = new L1DamagePoison(attacker, cha, damageSpan, damage, tomahawk, time);
		// Publish the poison identity before a worker can execute its first tick.
		cha.setPoison(poison);
		if (poison._timer != null) {
			try {
				ThreadPool.getInstance().execute(poison._timer);
			} catch (RejectedExecutionException rejected) {
				poison.cure();
				return false;
			}
		}
		return cha.getPoison() == poison;
	}

	@Override
	public int getEffectId() {
		return 1;
	}

	@Override
	public void cure() {
		if (_timer != null) {
			_timer.cancel();
		}
		if(_tomahawk) {
			_target.killSkillEffectTimer(L1SkillId.STATUS_TOMAHAWK);
		} else {
			if(_target instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) _target;
				SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 0, 0);
			}
			_target.setPoisonEffect(0);
			_target.killSkillEffectTimer(L1SkillId.STATUS_POISON);
		}
		_target.setPoison(null);
	}
}
