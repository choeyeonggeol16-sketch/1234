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

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Loc implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1Loc.class.getName());

	private L1Loc() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Loc();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			
			int locx = pc.getX();
			int locy = pc.getY();
			short mapid = pc.getMapId();
			int gab = L1WorldMap.getInstance(). getMap(mapid).getOriginalTile(locx, locy);
			int g2 = L1WorldMap.getInstance().getMap(mapid).getTestTile(locx, locy);
			String msg = String.format("좌표 (%d, %d, %d) %d %d, %s", locx, locy, mapid, gab, g2, String.valueOf(L1WorldMap.getInstance().getMap(mapid).isPassable(locx, locy)));
			SC_SystemMessage.send(pc, msg);
			//pc.sendPackets(new SC_SystemMessage("타일번호: "+pc.getMap().getOriginalTile(locx, locy)));
			System.out.println(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
