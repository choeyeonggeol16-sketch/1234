class PacketCandidate {
	private static boolean 봉인템(L1PcInstance pc, int item_id, int count, int EnchantLevel, int Bless, int attr,
			boolean identi) {
		// 봉인템(pc, 5000045, 1, 5, 128);
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			try {
				item.setCount(count);
				item.setIdentified(identi);
				item.setEnchantLevel(EnchantLevel);
				item.setAttrEnchantLevel(attr);
				item.setIdentified(true);
				if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
					L1ItemInstance stored = pc.getInventory().storeItem(item);
					if (stored == null) return false;
					stored.setBless(Bless);
					pc.getInventory().updateItem(stored, CUserInventory.COL_BLESS);
					pc.getInventory().saveItem(stored, CUserInventory.COL_BLESS);
				} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
					SC_ServerMessage.send(pc, 82);
					// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
					return false;
				}
				SC_ServerMessage.send(pc, 403, item.getLogName()); //
				return true;
			} finally {
				// ItemTable registers before an inventory decision; rejected or
				// stack-merged item images must not survive in L1World.
				if (pc.getInventory().getItem(item.getId()) != item) {
					L1World.getInstance().removeObject(item);
				}
			}
		} else {
			return false;
		}
	}
	private boolean 인첸트지급(L1PcInstance pc, int item_id, int count, int EnchantLevel) {
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			try {
				item.setCount(count);
				item.setEnchantLevel(EnchantLevel);
				item.setIdentified(true);
				if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
					if (pc.getInventory().storeItem(item) == null) return false;
				} else {
					SC_ServerMessage.send(pc, 82);
					// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
					return false;
				}
				SC_ServerMessage.send(pc, 403, item.getLogName()); // %0를
				// 손에
				// 넣었습니다.
				return true;
			} finally {
				// ItemTable registers before an inventory decision; rejected or
				// stack-merged item images must not survive in L1World.
				if (pc.getInventory().getItem(item.getId()) != item) {
					L1World.getInstance().removeObject(item);
				}
			}
		} else {
			return false;
		}
	}
 public boolean grant(boolean sealed,L1PcInstance pc,int itemId,int count,int enchant,int bless,int attr,boolean identified){
  return sealed ? 봉인템(pc,itemId,count,enchant,bless,attr,identified) : 인첸트지급(pc,itemId,count,enchant);
 }
}
