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

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Kill implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1Kill.class.getName());

	private L1Kill() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Kill();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = L1World.getInstance(). getPlayer(arg);
			if(pc.getName().equalsIgnoreCase(target.getName())) {
				SC_SystemMessage.send(pc, "본인은 죽일 수 없습니다.");
				return;
			}
			if(target.isGm()) {
				SC_SystemMessage.send(pc, "운영자는 죽일 수 없습니다.");
				return;
			}
			if (target != null) {
				target.setCurrentHp(0);
				target.death(null, true);
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명]으로 입력해 주세요. ");
		}
	}
}
