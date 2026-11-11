package l1j.server.server.serverpackets;

import java.util.List;

import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1ShopItem;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.NpcShopTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.shop.Shop;

public class S_PrivateShopforNpc extends ServerBasePacket {

	public S_PrivateShopforNpc(L1PcInstance pc, int objId, int type) {
		L1NpcInstance npc = (L1NpcInstance) L1World.getInstance().findObject(objId);

		if (npc == null) {
			return;
		}

		writeC(Opcodes.S_OPCODE_PRIVATESHOPLIST);
		writeC(type);
		writeD(objId);

		if (type == 0) {
			int npcId = npc.getNpcTemplate().get_npcId();
			Shop shop = null;
			if (npc instanceof L1NpcShopInstance) {
				shop = NpcShopTable.getInstance().get(npcId);
			}
			if (shop == null) {
				System.out.println("엔피시 상점 오류 : 번호" + npc.getNpcId() + " x :" + npc.getX() + " y :" + npc.getY() + " map :" + npc.getMapId());
				return;
			}
			List<L1ShopItem> shopItems = shop.getSellingItems();
			
			int size = shopItems.size();
			pc.setPartnersPrivateShopItemCount(size);
			writeH(size);
			ItemInstance dummy = new ItemInstance();
			for (int i = 0; i < size; i++) {
				L1ShopItem shopItem = shopItems.get(i);
				L1Item item = shopItem.getItem(); 
				if (item == null) {
					System.out.println("엔피시 상점 오류 : 아이템 폼묵이 없습니다.");
				}
				dummy.setItem(item);
				
				if (dummy != null) {
					writeC(i);
					writeC(dummy.getItem().getBless());
					writeH(dummy.getItem().getGfxId());
					writeD(shopItem.getCount());
					writeD(shopItem.getPrice());
					
					if (shopItem.getEnchant() > 0) {
						if (shopItem.getCount() > 1)
							writeS("+" + shopItem.getEnchant() + " " + dummy.getName() + " (" + shopItem.getCount() + ")");
						else
							writeS("+" + shopItem.getEnchant() + " " + dummy.getName());
					} else {
						if (shopItem.getCount() > 1)
							writeS(dummy.getName() + " (" + shopItem.getCount() + ")");
						else
							writeS(dummy.getName());
					}
					byte[] status = dummy.getStatusBytes();
					writeC(status.length);
					for (byte b : status) {
						writeC(b);
					}
				}
			} 
			writeC(0);
		} else if (type == 1) {
			int npcId = npc.getNpcTemplate().get_npcId();
			Shop shop = null;
			if (npc instanceof L1NpcShopInstance) {
				shop = NpcShopTable.getInstance().get(npcId);
			} 
			List<L1ShopItem> shopItems = shop.getBuyingItems();

			int size = shopItems.size();
			writeH(size);

			for (int i = 0; i < size; i++) {
				L1ShopItem shopItem = shopItems.get(i);
				// System.out.println(shopItem.getEnchant() + " " + shopItem.getItemId());
				for (ItemInstance pcItem : pc.getInventory().getItems()) {
					if (shopItem.getItemId() == pcItem.getItemId()
							&& shopItem.getEnchant() == pcItem.getEnchantLevel()) {
						// System.out.println("%" + pcItem.getEnchantLevel() + " " +
						// pcItem.getItemId());
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
