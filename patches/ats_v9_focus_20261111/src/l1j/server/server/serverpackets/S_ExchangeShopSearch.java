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
import java.util.Collections;
import java.util.Map;

import l1j.server.Templates.L1PrivateShopSellList;
import l1j.server.Templates.L1ShopItem;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcShopTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.NpcShopSystem;
import l1j.server.server.model.shop.Shop;

public class S_ExchangeShopSearch extends ServerBasePacket {
	/**
	 * 가게의 물건 리스트를 표시한다. 캐릭터가 BUY 버튼을 눌렀을 때에 보낸다.
	 */
	public S_ExchangeShopSearch(String code) {
		writeC(Opcodes.S_OPCODE_SHOWSHOPBUYLIST);
		writeD(1541);

		ArrayList<L1PrivateShopSellList> sellList = new ArrayList<L1PrivateShopSellList>();
		Map<Integer, Shop> npcshop_list = NpcShopTable.getInstance().get();
 
		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (pc.getSellList() != null) {
				sellList.addAll(pc.getSellList());  
			}
		}
		
		boolean power = NpcShopSystem.getInstance().isPower();
		if (power) {
			if (npcshop_list != null) { 
				for (Shop shop : npcshop_list.values()) {
					if (shop != null) {
						for (L1ShopItem item : shop.getSellingItems()) {
							if (item != null) {
								String name = ItemTable.getInstance().findItemIdByName(item.getItemId());
								if (name != null) {
									if (name.contains(code)) {
										ItemInstance items = ItemTable.getInstance().createItem(item.getItemId());
										items.setEnchantLevel(item.getEnchant());
										items.setOwnerId(shop.getNpcId());
										L1PrivateShopSellList info = new L1PrivateShopSellList();
										info.setItemObjectId(items.getId());
										info.setSellCount(item.getCount()); 
										info.setSellPrice(item.getPrice()); 
										info.setSellTotalCount(item.getCount());
										sellList.add(info);
									}
								}
							}
						}
					}
				}
			}
		} 
		
		int size = 0;
		if (sellList != null) {
			Collections.sort(sellList);
			
			for (L1PrivateShopSellList sell_list : sellList) {
				ItemInstance item = (ItemInstance) L1World.getInstance().findObject(sell_list.getItemObjectId());
				if (item != null) {
					if(item.getName().contains(code)) {
						size++;
					}
				}
			}
			
			writeH(size);
			
			for (L1PrivateShopSellList sell_list : sellList) {
				ItemInstance item = (ItemInstance) L1World.getInstance().findObject(sell_list.getItemObjectId());
				if (item != null) {
					if(item.getName().contains(code)) {
						writeD(item.getId());
						writeH(item.getItem().getGfxId());
						writeD(sell_list.getSellPrice());
						String PackCount = "";
						String MaxUseTime = "";
						String PobyBless = "";
						String Enchant = "";
						String Attr = "";
						if (item.getItem().getBless() != 1) { 
							if (item.getItem().getBless() == 0)
								PobyBless = "$227 ";
							else if (item.getItem().getBless() == 2)
								PobyBless = "$228 ";
						}
		
						if (item.getEnchantLevel() > 0){
							Enchant = "+" + item.getEnchantLevel() + " ";
						}
						
						if (item.getAttrEnchantLevel() > 0) {
							Attr = ItemInstance.get_attribute_enchant_description(item.getAttrEnchantLevel());
						}

						if (item.getItemId() >= 140074 && item.getItemId() <= 140100 || item.getItemId() == 3000124)
							writeS("\\fW[축복] " + item.getName());
						else if (item.getItemId() >= 240074 && item.getItemId() <= 240087 || item.getItemId() == 3000125)
							writeS("\\fY[저주] " + item.getName());
						else
							writeS(Attr + Enchant + PobyBless + item.getName() + PackCount + MaxUseTime);

						byte[] status = item.getStatusBytes();
						writeC(status.length);
						for (byte b : status) {
							writeC(b);
						}
					}
				}
			}
		}
		
		writeC(0x07);
		writeC(0x00);
	}

	@Override
	public byte[] getContent() throws IOException {
		return _bao.toByteArray();
	}
}
