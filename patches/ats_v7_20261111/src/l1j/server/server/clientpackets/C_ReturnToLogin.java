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
import l1j.server.server.Controller.LoginController;

public class C_ReturnToLogin extends ClientBasePacket {

	private static final String C_RETURN_TO_LOGIN = "[C] C_ReturnToLogin";
	private static Logger _log = Logger.getLogger(C_ReturnToLogin.class.getName());

	public C_ReturnToLogin(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);
		String account = client.getAccountName();
		_log.finest((new StringBuilder()).append("account : ").append(account).toString());
		if (client.getActiveChar() != null) {
			client.kick();
			client.close();
			System.out.println("─────────────────────────────────");
			System.out.println("버그 의심됨 -- 계정명 " + client.getAccountName());
			System.out.println("─────────────────────────────────");
			return;
		}	
		LoginController.getInstance().logout(client);
	}

	@Override
	public String getType() {
		return C_RETURN_TO_LOGIN;
	}

}
