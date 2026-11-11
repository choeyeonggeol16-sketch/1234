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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import l1j.server.Templates.L1Item;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.CraftListTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.CraftListTable.CraftMeterialTemp;
import l1j.server.server.datatables.CraftListTable.CraftTemp;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_CraftItem extends ServerBasePacket {
	
	public S_CraftItem(int npcid, int objid) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(objid);
		writeS("craftitem");
		writeC(0);
		writeH(20);
		
		ArrayList<CraftTemp> craftList = CraftListTable.getInstance().getCraftList(npcid);
		if(craftList == null) {
			for(int i = 0; i < 20; i++) {
				writeS(" ");
			}
			return;
		}
		
		Collections.sort(craftList, new Comparator<CraftTemp>() {
			@Override
			public int compare(CraftTemp o1, CraftTemp o2) {
				// TODO 자동 생성된 메소드 스텁
				return o1.order_id - o2.order_id;
			}
		});
		
		for(CraftTemp temp : craftList) {
			L1Item item = ItemTable.getInstance().getTemplate(temp.create_id);
			if(item != null) {
				String itemName = "";
				if(temp.create_chance != 100) {
					itemName += "[확률] ";
				}
				
				if(temp.create_bless == 0) {
					itemName += "(축) ";
				}
				
				if(temp.create_enchant != 0) {
					itemName += "+" + temp.create_enchant + " ";
				}
				
				itemName += item.getName();
				
				writeS(itemName);
			}else {
				writeS("제작하려는 아이템이 없음");
			}
		}
		
		int nullSize = 20 - craftList.size();
		if(nullSize > 0) {
			for(int i = 0; i < nullSize; i++) {
				writeS(" ");
			}
		}
	}

	public S_CraftItem(L1PcInstance pc, int objid) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(objid);
		writeS("craftitem2");
		writeC(0);
		writeH(11);
		
		CraftTemp temp = CraftListTable.getInstance().getCraftTemp(pc.Craft_Npcid, pc.Craft_Orderid);
		if(temp == null) {
			writeS("제작리스트 오류입니다.");
			for(int i = 0; i < 10; i++) {
				writeS(" ");
			}
			return;
		}
		
		
		
		L1Item item = ItemTable.getInstance().getTemplate(temp.create_id);
		if(item != null) {
			String itemName = "";
			if(temp.create_chance != 100) {
				itemName += "[확률] ";
			}
			
			if(temp.create_bless == 0) {
				itemName += "(축) ";
			}
			
			if(temp.create_enchant != 0) {
				itemName += "+" + temp.create_enchant + " ";
			}
			
			itemName += item.getName();
			
			if(temp.create_count != 0) {
				itemName += "(" + temp.create_count + ")";
			}
			writeS(itemName);
			
		}else {
			writeS("제작하려는 아이템이 없음");
		}
		
		for(CraftMeterialTemp mtemp : temp._MeterialList) {
			item = ItemTable.getInstance().getTemplate(mtemp.itemid);
			if(item != null) {
				String itemName = "";
				if(mtemp.bless == 0) {
					itemName += "(축) ";
				}
				
				if(mtemp.enchant != 0) {
					itemName += "+" + mtemp.enchant + " ";
				}
				
				itemName += item.getName();
				
				if(mtemp.count != 0) {
					if(mtemp.itemid == 40308) {
						DecimalFormat priceformat = new DecimalFormat("#,###,###,###");
						itemName += "(" + priceformat.format(mtemp.count) + ")";
					}else{
						itemName += "(" + mtemp.count + ")";
					}
				}
				writeS(itemName);
			}else {
				writeS("재료아이템이 없습니다.");
			}
		}
		
		int nullSize = 10 - temp._MeterialList.size();
		if(nullSize > 0) {
			for(int i = 0; i < nullSize; i++) {
				writeS(" ");
			}
		}
	}
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
