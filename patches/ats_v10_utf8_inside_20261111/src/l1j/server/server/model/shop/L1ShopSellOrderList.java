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
package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Disconnect; //## (버그 방지) 상점 버그 방지
import l1j.server.server.serverpackets.S_SystemMessage;

class L1ShopSellOrder {
	private final L1AssessedItem _item;
	private final int _count;

	public L1ShopSellOrder(L1AssessedItem item, int count) {
		_item = item;
		_count = count;
	}

	public L1AssessedItem getItem() {
		return _item;
	}

	public int getCount() {
		return _count;
	}

}

public class L1ShopSellOrderList {
	private final Shop _shop;
	private final L1PcInstance _pc;
	private final List<L1ShopSellOrder> _list = new ArrayList<L1ShopSellOrder>();
	private int bugok = 0; // ** 상점 판매 비셔스 방어 **//

	L1ShopSellOrderList(Shop shop, L1PcInstance pc) {
		_shop = shop;
		_pc = pc;
	}

	public void add(int itemObjectId, int count, L1PcInstance pc) {
		/** 날짜 및 시간을 기록하자 **/
		// Calendar rightNow = Calendar.getInstance();
		// int day = rightNow.get(Calendar.DATE);
		// int year = rightNow.get(Calendar.YEAR);
		// int month = rightNow.get(Calendar.MONTH)+1;
		// String totime = "[" + year + ":" + month + ":" + day + "]";

		ItemInstance item;
		item = pc.getInventory().getItem(itemObjectId);

		// item instance null pointer exception
		if (item == null || item.getItem() == null)
			return;
		// item instance null pointer exception
 
		DollInstance doll = pc.getMagicDoll();
		if ((doll != null) && (item.getId() == doll.getItemObjId())) {
			pc.sendPackets(new S_SystemMessage("\\fW소환된 인형은팔수없습니다.."));
			return;
		}
		if (item.getCount() < count) {
			bugok = 1;
			return;
		}

		if (!item.isStackable() && count != 1) {
			bugok = 1;
			return;
		}

		if (item.getCount() <= 0 || count <= 0) {
			bugok = 1;
			return;
		} 
		
		if(item.getBless() >= 128){
			return;
		}

		L1AssessedItem assessedItem = _shop.assessItem(_pc.getInventory().getItem(itemObjectId));
		if (assessedItem == null) {
			/*
			 * 매입 리스트에 없는 아이템이 지정되었다. 부정 패키지의 가능성.
			 */
			throw new IllegalArgumentException();
		}

//## (버그 방지) 상점 버그 방지
		if (count <= 0) {
			_pc.sendPackets(new S_Disconnect());
			return;
		}
//## (버그 방지) 상점 버그 방지
		_list.add(new L1ShopSellOrder(assessedItem, count));
	}
	
	// ** 상점 판매 비셔스 방어 **//
	public int BugOk() {
		return bugok;
	}

	// ** 상점 판매 비셔스 방어 **//


	L1PcInstance getPc() {
		return _pc;
	}

	List<L1ShopSellOrder> getList() {
		return _list;
	}
}
