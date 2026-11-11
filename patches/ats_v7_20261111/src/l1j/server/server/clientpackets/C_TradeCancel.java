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

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.GameClient;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_TradeCancel extends ClientBasePacket {

	private static final String C_TRADE_CANCEL = "[C] C_TradeCancel";
	private static Logger _log = Logger.getLogger(C_TradeCancel.class.getName());

	public C_TradeCancel(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);
		try {
			L1PcInstance player = clientthread.getActiveChar();
			if (player == null) {
				return;
			}
			L1Trade trade = new L1Trade();
			trade.TradeCancel(player); 
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	@Override
	public String getType() {
		return C_TRADE_CANCEL;
	}

}
