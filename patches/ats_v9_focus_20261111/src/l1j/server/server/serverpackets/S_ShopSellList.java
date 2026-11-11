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
import java.util.List;

import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1ShopItem;
import l1j.server.configure.Config;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.shop.Shop;

public class S_ShopSellList extends ServerBasePacket {

	/**
	 * 가게의 물건 리스트를 표시한다. 캐릭터가 BUY 버튼을 눌렀을 때에 보낸다.
	 */
	public S_ShopSellList(L1PcInstance pc, int objId) {

		writeC(Opcodes.S_OPCODE_SHOWSHOPBUYLIST);
		writeD(objId);
	

		L1Object npcObj = L1World.getInstance().findObject(objId);
		if (!(npcObj instanceof L1NpcInstance)) {
			writeH(0);
			return;
		}
	
		int npcId = ((L1NpcInstance) npcObj).getNpcTemplate().get_npcId();
		List<L1ShopItem> shopItems = null;
		L1TaxCalculator calc = new L1TaxCalculator(npcId);
		Shop shop = ShopTable.getInstance().get(npcId);
		try {
			shopItems = shop.getSellingItems();
		} catch (Exception e) {
			System.out.println("S_ShopSellList 상점 판매 리스트가 없습니다. : " + npcId);
		} 
		if (shopItems != null) {

			writeH(shopItems.size());

		} else {
			writeH(0);
			return;
		}

	
		// L1ItemInstance의 getStatusBytes를 이용하기 위해(때문에)
		ItemInstance dummy = new ItemInstance(); 
		
		for (int i = 0; i < shopItems.size(); i++) {
			L1ShopItem shopItem = shopItems.get(i);
			L1Item item = shopItem.getItem();
			int price = calc.layTax((int) (shopItem.getPrice() * Config.RATE_SHOP_SELLING_PRICE));
			writeD(i);
			try {
				writeH(shopItem.getItem().getGfxId());
			} catch (Exception e) {
				System.out.println("엔피시 상점 오류 엔피시 번호 :" + npcId);
			}
			writeD(price);
			String PackCount = "";
			String MaxUseTime = "";
			String PobyBless = "";
			String Enchant = "";
			if (shopItem.getItem().getBless() != 1) { 
				if (shopItem.getItem().getBless() == 0)
					PobyBless = "$227 ";
				else if (shopItem.getItem().getBless() == 2)
					PobyBless = "$228 ";
			}
			if (shopItem.getPackCount() > 1) {
				PackCount = " (" + shopItem.getPackCount() + ")";
			}
			if (shopItem.getEnchant() > 0){
				Enchant = "+" + shopItem.getEnchant() + " ";
			}
			if (shopItem.getItem().getMaxUseTime() > 0) {
				MaxUseTime = " [" + item.getMaxUseTime() + "]";
			}
			if (item.getItemId() >= 140074 && item.getItemId() <= 140100 || item.getItemId() == 3000124) {
				writeS("[축복] " + item.getNameId());
			}		
			else if (item.getItemId() >= 240074 && item.getItemId() <= 240087  || item.getItemId() == 3000125) {
				writeS("[저주] " + item.getNameId());
			}
			else {
				writeS(Enchant + PobyBless + item.getNameId() + PackCount + MaxUseTime);
			}				
			L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
			if (template == null) {
				writeC(0);
			} else {
				dummy.setItem(template);
				dummy.setEnchantLevel(shopItem.getEnchant()); //추가 
				byte[] status = dummy.getStatusBytes();
				writeC(status.length);
				for (byte b : status) {
					writeC(b);
				}
			}
		}
		writeC(0x07); //x00:kaimo 0x01:pearl 0x07:adena
		writeC(0x00);
	}

	@Override
	public byte[] getContent() throws IOException {
		return _bao.toByteArray();
	}
}
