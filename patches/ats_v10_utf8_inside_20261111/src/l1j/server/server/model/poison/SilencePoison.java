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

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;

public class SilencePoison extends Poison {
	private final L1Character _target;
	private final int _time;

	public static boolean doInfection(L1Character cha, int time) {
		if (!isValidTarget(cha, Poison.POISON_SILENCE)) {
			return false;
		}

		cha.setPoison(new SilencePoison(cha, time));
		return true;
	}

	private SilencePoison(L1Character cha,int time) {
		_target = cha;
		_time = time;
		setPoisonType(Poison.POISON_SILENCE);	
		doInfection();
	}

	private void doInfection() {
		_target.setPoisonEffect(1);
		sendMessageIfPlayer(_target, 310); 
		_target.setSkillEffect(SkillId.상태_침묵, _time * 1000);
	}

	@Override
	public int getEffectId() {
		return 1;
	}

	@Override
	public void cure() {
		if (_target instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) _target;
			pc.setPoisonEffect(0);
			sendMessageIfPlayer(pc, 311); 
			if(pc.hasSkillEffect(SkillId.상태_침묵)) {
				pc.removeSkillEffect(SkillId.상태_침묵);
			}
			pc.setPoison(null);
		}
		
	}
}


