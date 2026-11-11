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

import java.io.IOException;
import java.util.ArrayList;

import l1j.server.Templates.L1Drop;
import l1j.server.Templates.L1Item;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;

public class S_MonsterDropList extends ServerBasePacket {

	/**
	 * 가게의 물건 리스트를 표시한다. 캐릭터가 BUY 버튼을 눌렀을 때에 보낸다.
	 */
	public S_MonsterDropList(int npcid) {
		writeC(Opcodes.S_OPCODE_SHOWSHOPBUYLIST);
		writeD(0);

		int size = 0;
		int i = 0;
		ArrayList<L1Drop> drop_items = DropTable.getInstance().getDropList(npcid);
		if (drop_items != null) {
			ItemInstance dummy = new ItemInstance(); 
			for (L1Drop drop : drop_items) {
				if (drop != null) {
					size++;
				}
			}
			
			writeH(size);
			
			for (L1Drop drop : drop_items) {
				if (drop != null) {
					L1Item temp = ItemTable.getInstance().getTemplate(drop.getItemid());
					if (temp != null) {
						writeD(i);
						writeH(temp.getGfxId());
						writeD(0);
						writeS(temp.getName());

						dummy.setItem(temp);
						byte[] status = dummy.getStatusBytes();
						writeC(status.length);
						for (byte b : status) {
							writeC(b);
						}
					}
					i++;
				}
			}
		} else {
			writeH(0);
			return;
		}

		writeC(0x07);
		writeC(0x00);
	}

	@Override
	public byte[] getContent() throws IOException {
		return _bao.toByteArray();
	}
}
