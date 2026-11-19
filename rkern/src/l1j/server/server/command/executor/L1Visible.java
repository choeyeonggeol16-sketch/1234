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

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Visible implements L1CommandExecutor {

	private L1Visible() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Visible();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			pc.setGmInvis(false);
			pc.killSkillEffectTimer(L1SkillId.INVISIBILITY);
			SC_Invis.send(pc, pc.getId(), 0);

			SC_WorldPutObject.broadcastPc(
					pc,
					"",
					false);

			SC_SystemMessage.send(pc, "투명상태를 해제했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");
		}
	}
}
