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

import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Move implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1Move.class.getName());

	private L1Move() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Move();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int locx = Integer.parseInt(st.nextToken());
			int locy = Integer.parseInt(st.nextToken());
			short mapid;
			if (st.hasMoreTokens()) {
				mapid = Short.parseShort(st.nextToken());
			} else {
				mapid = pc.getMapId();
			}
//			L1Teleport.teleport(pc, locx, locy, mapid, 5, false);
			pc.do_simple_teleport(locx, locy, mapid);
			//pc.start_teleport(locx, locy, mapid, 5, 169, false, false);
			SC_SystemMessage.send(pc, "좌표 " + locx + ", " + locy
					+ ", " + mapid + "로 이동했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
					+ " [X좌표] [Y좌표] [맵ID] 라고 입력해 주세요. ");
		}
	}
}
