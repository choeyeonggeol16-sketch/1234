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
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_CommonNews;
import l1j.server.server.serverpackets.S_DeleteCharOK;


public class C_DeleteChar extends ClientBasePacket {

	private static final String C_DELETE_CHAR = "[C] RequestDeleteChar";

	private static Logger _log = Logger.getLogger(C_DeleteChar.class.getName());

	public C_DeleteChar(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);
		String name = readS();
		_log.fine("deleting character : " + name);
		try {
			L1PcInstance pc = CharacterTable.getInstance().restoreCharacter(name);
			if (pc == null) {
				client.sendPacket(new S_CommonNews("존재하지 않는 캐릭터 입니다."));
				return;
			}
			for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
				if (target.getId() == pc.getId()) {
					client.sendPacket(new S_CommonNews("접속 중인 캐릭터는 삭제할 수 없습니다."));
					return;
				}
			}
			client.sendPacket(new S_CommonNews("현재 케릭터는 삭제 불가능합니다."));
//			if (pc != null) {
//				CharacterTable.getInstance().restoreInventory(pc); 
//				for (L1ItemInstance item : pc.getInventory().getItems()) {
//					if (item.getBless() >= 128) {
//						client.sendPacket(new S_CommonNews("봉인된 아이템을 가진 캐릭터는 삭제할 수 없습니다."));
//						return;
//					}
//				}
//			}
//			if (pc != null) {
//				L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
//				if (clan != null) {
//					clan.delMemberName(name);
//				}
//			}
//			CharacterTable.getInstance().deleteCharacter(client.getAccountName(), name);
			
			
			
			
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			client.close();
			return;
		}
		client.sendPacket(new S_DeleteCharOK());
	}

	@Override
	public String getType() {
		return C_DELETE_CHAR;
	}

}
