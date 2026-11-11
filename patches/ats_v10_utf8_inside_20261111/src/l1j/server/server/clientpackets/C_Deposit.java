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

import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_ChatPacket;

public class C_Deposit extends ClientBasePacket {

	private static final String C_DEPOSIT = "[C] C_Deposit";

	public C_Deposit(byte abyte0[], GameClient clientthread)
			throws Exception {
		super(abyte0);
		int i = readD();
		int j = readD();

		L1PcInstance player = clientthread.getActiveChar();
		if (player == null) {
			return;
		}
		if (i == player.getId()) {
			L1Clan clan = L1World.getInstance().getClan(player.getClanname());
			if (clan != null) {
				int castle_id = clan.getCastleId();

				if (castle_id == 0) {
					return;
				}

				if(!player.isCrown() || clan.getLeaderId() != player.getId()){
					return;
		        }
				
				if( CastleWarBusiness.getInstance().isNowWar(castle_id) )
				{
					player.sendPackets(new S_ChatPacket(player,"공성 중에는 불가능합니다."));
					return;
				}

				if (castle_id != 0) { // 성주 크란
					CastleWar war = CastleWarBusiness.getInstance().get(castle_id);
					int money = war.getPublicMoney();
					if(j < 0 || money <= 0)
						return;
					
					if(player.getInventory().consumeItem(ItemId.아데나, j)){
						war.setPublicMoney(money + j);
						CastleWarBusiness.getInstance().updateCastle(castle_id);
					}
				}
			}
		}
	}

	@Override
	public String getType() {
		return C_DEPOSIT;
	}

}
