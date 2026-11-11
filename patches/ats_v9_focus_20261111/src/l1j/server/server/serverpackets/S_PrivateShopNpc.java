package l1j.server.server.serverpackets;

import java.util.List;

import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1ShopItem;
import l1j.server.configure.Config;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.shop.Shop;

public class S_PrivateShopNpc extends ServerBasePacket {

	public S_PrivateShopNpc(L1PcInstance pc, int objId, int type) {
		L1NpcInstance npc = (L1NpcInstance) L1World.getInstance().findObject(objId);

		if (npc == null) {
			return;
		}

		writeC(Opcodes.S_OPCODE_PRIVATESHOPLIST);
		writeC(type);
		writeD(objId);

		if (type == 0) {
			int npcId = npc.getNpcTemplate().get_npcId();
			Shop shop = ShopTable.getInstance().get(npcId);
			if (shop == null) {
				System.out.println("엔피시 상점 오류 : 번호" + npc.getNpcId() + " x :" + npc.getX() + " y :" + npc.getY() + " map :" + npc.getMapId());
				return;
			}
			List<L1ShopItem> shopItems = null;
			L1TaxCalculator calc = new L1TaxCalculator(npcId);
			try {
				shopItems = shop.getSellingItems(); 
			} catch (Exception e) {
				System.out.println("S_PrivateShopNpc 상점 판매 리스트가 없습니다. : " + npcId);
			} 
			
			if (shopItems != null) { 
				writeH(shopItems.size()); 
			} else {
				writeC(0);
				return;
			}
			ItemInstance dummy = new ItemInstance();
			int size = shopItems.size();
			for (int i = 0; i < size; i++) {
				L1ShopItem shopItem = shopItems.get(i);
				L1Item item = shopItem.getItem();
				int price = calc.layTax((int) (shopItem.getPrice() * Config.RATE_SHOP_SELLING_PRICE));
				dummy.setItem(item);
				if (dummy != null) {
					writeC(i);
					writeC(dummy.getItem().getBless()); 
					writeH(dummy.getItem().getGfxId());
					writeD(shopItem.getPackCount());
					writeD(price); 
					if (shopItem.getPackCount() > 1) {
						writeS(item.getNameId() + " (" + shopItem.getPackCount() + ")");
					}else if (shopItem.getEnchant() > 0){
						writeS("+" + shopItem.getEnchant() + " " + item.getName());
					}else if (shopItem.getItem().getMaxUseTime() > 0){
						writeS(item.getName() + " [" + item.getMaxUseTime() + "]");
						
					}	else {	
						writeS(item.getName());
					}
						byte[] status = dummy.getStatusBytes();
						writeC(status.length);
						for (byte b : status)
							writeC(b);
					}
					
				
			} 
			writeC(0x07); //x00:kaimo 0x01:pearl 0x07:adena
			writeC(0x00);
		} else if (type == 1) {
			int npcId = npc.getNpcTemplate().get_npcId();
			Shop shop = null;
			if (npc instanceof L1NpcInstance) {
				shop = ShopTable.getInstance().get(npcId);
			}
			List<L1ShopItem> shopItems = shop.getBuyingItems();

			int size = shopItems.size();
			writeH(size);

			for (int i = 0; i < size; i++) {
				L1ShopItem shopItem = shopItems.get(i);
				 System.out.println(shopItem.getEnchant() + " " + shopItem.getItemId());
				for (ItemInstance pcItem : pc.getInventory().getItems()) {
					if (shopItem.getItemId() == pcItem.getItemId()
							&& shopItem.getEnchant() == pcItem.getEnchantLevel()) {
						 System.out.println("%" + pcItem.getEnchantLevel() + " " +
					 pcItem.getItemId());
						writeC(i);
						writeD(pcItem.getId());
						writeD(1);
						writeD(1);
					}
				}
			}
		}
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
