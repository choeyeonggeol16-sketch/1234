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
package l1j.server.server.serverpackets;

import java.util.logging.Logger;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_WhoCharinfo extends ServerBasePacket {
	
	private static Logger _log = Logger.getLogger(S_WhoCharinfo.class.getName());

	public S_WhoCharinfo(L1PcInstance pc) {
		_log.fine("Who charpack for : " + pc.getName());

		String lawfulness = "";
		float win = 0;
		float lose = 0;
		float total = 0;
		float winner = 0;
		
		win = pc.getKills();
		lose = pc.getDeaths();
		total = win + lose;
		winner = ((win * 100) / (total));

		int lawful = pc.getLawful();
		if (lawful < 0) {
			lawfulness = "(Chaotic)";
		} else if (lawful >= 0 && lawful < 500) {
			lawfulness = "(Neutral)";
		} else if (lawful >= 500) {
			lawfulness = "(Lawful)";
		}
		
		writeC(Opcodes.S_OPCODE_SYSMSG);
		writeC(0x08);

		String title = "";
		String clan = "";
		
		if (!pc.getTitle().equalsIgnoreCase("")) {
			title = pc.getTitle() + " ";
		}

		if (pc.getClanid() > 0) {
			clan = "[" + pc.getClanname() + "]";
		}

		if (pc.getHuntCount() > 0) {
			writeS(title + pc.getName() + " " + lawfulness + " " + clan + " -수배- " 
					 + "\n\r" + "[ Kill : " + pc.getKills()+ " ] [ Death : " + pc.getDeaths()+" ]  승률 = " +String.format("%.2f",winner));
		} else {
			writeS(title + pc.getName() + " " + lawfulness + " " + clan + " "
					 + "\n\r" + "[ Kill : " + pc.getKills()+ " ] [ Death : " + pc.getDeaths()+" ]  승률 = " +String.format("%.2f",winner));
		}
		writeD(0);
	}
	
	public S_WhoCharinfo(L1NpcShopInstance shopnpc) { 

		_log.fine("Who charpack for : " + shopnpc.getName());

		String lawfulness = "";
		float win = 0;
		float lose = 0;
		float winner = 0; 
		 
		writeC(Opcodes.S_OPCODE_SYSMSG);
		writeC(0x08);

		String title = "";
		String clan = "";
	//	String type = "";

		if (shopnpc.getTitle().equalsIgnoreCase("") == false) {
			title = shopnpc.getTitle() + " ";
		}

		writeS(title + shopnpc.getName() + " " + lawfulness + " " + clan + " "
				 + "\n\r" + "[ Kill : " + win+ " ] [ Death : " + lose+" ]  승률 = " +String.format("%.2f",winner));

		writeD(0);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
