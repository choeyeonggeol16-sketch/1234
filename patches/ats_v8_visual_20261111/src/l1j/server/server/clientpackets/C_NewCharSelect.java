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

import java.util.logging.Logger;

import l1j.server.server.GameClient;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SelectCharacter;

public class C_NewCharSelect extends ClientBasePacket {
	private static final String C_NEW_CHAR_SELECT = "[C] C_NewCharSelect";
	private static Logger _log = Logger.getLogger(C_NewCharSelect.class.getName());

	public C_NewCharSelect(byte[] decrypt, GameClient client) throws Exception {
		super(decrypt);
		
		
		if (client.getActiveChar() != null) {
			L1PcInstance pc = client.getActiveChar();
			if (pc == null) {
				return;
			}
			// 성장의 낚시로 인한 한번더 검출 리스시
			if(pc.isFishing()){ 
				FishingTimeController.getInstance().endFishing(pc);
			}
			restartProcess(pc);
		} else {
			_log.fine("Disconnect Request from Account : " + client.getAccountName());
		}
	}

	public static void restartProcess(L1PcInstance pc){

		try {
			pc.save();
		} catch (Exception e) {
		}
		try {
			pc.saveInventory();
		} catch (Exception e) {
		}
		_log.fine("Disconnect from: " + pc.getName());
		GameClient client = pc.getNetConnection();
		if (client == null) {
			return;
		} 
		synchronized (pc) {
			try {
 
				client.CharReStart(true);  
				
				pc.logout();
				client.setActiveChar(null);
				client.setLoginAvailable();
				// 310 버전
				client.sendPacket(new S_SelectCharacter(1));
				// 270 버전 위에 주석
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
		
		 

	@Override
	public String getType() {
		return C_NEW_CHAR_SELECT;
	}
}
