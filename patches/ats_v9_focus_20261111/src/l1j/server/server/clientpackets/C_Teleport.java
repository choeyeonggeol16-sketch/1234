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

package l1j.server.server.clientpackets;

import l1j.server.CharacterAction.Teleport.Teleport;
import l1j.server.server.GameClient;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.ServerBasePacket;

public class C_Teleport extends ClientBasePacket {

	private static final String C_TELEPORT = "[C] C_Teleport";

	public C_Teleport(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);

		try {
			if (client == null) {
				return;
			}
			L1PcInstance pc = client.getActiveChar();
			if (pc == null || pc.isPrivateShop() || pc.isFishing()) {
				return;
			}	
			ServerBasePacket pck = pc.getTemporaryEffect();
			if(pck != null){
				pc.sendPackets(pck, false);
				Broadcaster.broadcastPacket(pc, pck);
				pc.clearTemporaryEffect();
			}
			Runnable teleport = () -> {Teleport.getInstance().Teleportation(pc);};
			GeneralThreadPool.getInstance().schedule(teleport, 200L);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getType() {
		return C_TELEPORT;
	}
}