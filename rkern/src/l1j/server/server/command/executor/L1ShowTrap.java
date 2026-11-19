/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TrapInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1ShowTrap implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1ShowTrap.class.getName());

	private L1ShowTrap() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1ShowTrap();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		if (arg.equalsIgnoreCase("켬")) {
			pc.setSkillEffect(L1SkillId.GMSTATUS_SHOWTRAPS, 0);
		} else if (arg.equalsIgnoreCase("끔")) {
			pc.removeSkillEffect(L1SkillId.GMSTATUS_SHOWTRAPS);

			for (L1Object obj : pc.getKnownObjects()) {
				if (obj instanceof L1TrapInstance) {
					pc.removeKnownObject(obj);
					SC_RemoveObject.send(
							pc.getNetConnection(),
							obj);
				}
			}
		} else {
			SC_SystemMessage.send(pc, cmdName + " [켬,끔] 라고 입력해 주세요. ");
		}
	}
}
