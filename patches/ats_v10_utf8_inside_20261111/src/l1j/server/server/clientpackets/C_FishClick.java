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
 * 
 * # FishingThread.java - Team Void Factory
 * 
 */

package l1j.server.server.clientpackets;

import l1j.server.server.GameClient;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1PcInstance;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket

public class C_FishClick extends ClientBasePacket {

	private static final String C_FISHCLICK = "[C] C_FishClick";

	public C_FishClick(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);
		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null)
			return;

		FishingTimeController.getInstance().endFishing(pc);
	}

	@Override
	public String getType() {
		return C_FISHCLICK;
	}
}
