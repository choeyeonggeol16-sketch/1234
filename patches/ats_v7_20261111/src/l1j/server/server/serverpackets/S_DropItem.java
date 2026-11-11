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

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.ItemInstance;


public class S_DropItem extends ServerBasePacket {

	private static final String _S__OB_DropItem = "[S] S_DropItem";
	
	public S_DropItem(ItemInstance item) {
		buildPacket(item);
	}

	private void buildPacket(ItemInstance item) {
		writeC(Opcodes.S_OPCODE_DROPITEM);
		writeH(item.getX());
		writeH(item.getY());
		writeD(item.getId());
		writeH(item.getItem().getGroundGfxId());
		writeC(0);
		writeC(0);
		if (item.isNowLighting()) {
			writeC(item.getItem().getLightRange());
		} else {
			writeC(0);
		} 
		writeC(0);
		writeD(item.getCount()); // ## 원본
		writeC(0); // #
		writeC(0); // #
		if (item.getCount() > 1) {
			writeS(item.getItem().getName() + " (" + item.getCount() + ")");
		} else {
			int isId = item.isIdentified() ? 1 : 0;
			if (item.getItem().getItemId() == 20383 && isId == 1) { // 기마용 투구
				writeS(item.getItem().getName() + " [" + item.getChargeCount() + "]");
			} else if ((item.getItem().getItemId() == 40006 // 막대류
					|| item.getItem().getItemId() == 40007 || item.getItem().getItemId() == 40008
					|| item.getItem().getItemId() == 40009 || item.getItem().getItemId() == 140006
					|| item.getItem().getItemId() == 140008) && isId == 1) {
				writeS(item.getItem().getName() + " (" + item.getChargeCount() + ")");
				
			} else {
				if (isId == 1 && item.getEnchantLevel() > 0)
					writeS("+" + item.getEnchantLevel() + " " + item.getItem().getName());
				else if (item.getItem().getLightRange() != 0 && item.isNowLighting())
					writeS(item.getItem().getName() + " ($10)");
				else
					writeS(item.getItem().getName());
			}
		}
		writeC(0);
		writeD(0);
		writeD(0);
		writeC(255);
		writeC(0);
		writeC(0);
		writeC(0);
		writeH(65535);
		writeD(0);
		writeC(8);
		writeC(0);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
	@Override
	public String getType() {
		return _S__OB_DropItem;
	}

}
