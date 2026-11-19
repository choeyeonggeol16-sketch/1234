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
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.IntRange;

public class L1Level implements L1CommandExecutor {

	private static Logger _log = Logger.getLogger(L1Level.class.getName());

	private L1Level() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Level();
	}

	  public void execute(L1PcInstance pc, String cmdName, String poby)
	  {
	    try {
	      StringTokenizer token = new StringTokenizer(poby);
	      String name = token.nextToken();
	      int level = Integer.parseInt(token.nextToken());

	      L1PcInstance target = L1World.getInstance().getPlayer(name);
	      if (target == null) {
//	        pc.sendPackets(new SC_ServerMessage(73, new String[] { name }));
	    	SC_SystemMessage.send(pc, "해당 아이가 없습니다.");
	        return;
	      }

	      if (!IntRange.includes(level, 1, 127)) {
	        SC_SystemMessage.send(pc, "입력한 레벨이 범위를 벗어났습니다. (1~99)");
	        return;
	      }
	      int percent = 0;
	      if (token.hasMoreTokens()) {
	        percent = Integer.parseInt(token.nextToken());
	      }
	      if (!IntRange.includes(percent, 0, 127)) {
	        SC_SystemMessage.send(pc, "입력한 퍼센트가 범위를 벗어났습니다. (0~99)");
	        return;
	      }

	      int exp = ExpTable.getExpByLevel(level);
	      exp = (int)(exp + percent * 0.01D * ExpTable.getNeedExpNextLevel(level));
	      target.setExp(exp);
	      SC_SystemMessage.send(pc, "<레벨 변경> " + target.getName() + " - Lv. " + level + " / Exp. " + percent + "%");
	    } catch (Exception e) {
	      SC_SystemMessage.send(pc, "." + cmdName + " <캐릭터명> <레벨:1-127> <퍼센트:0-99>");
	    }
	  }
	}