public final class L1NewUltimateBattleLegacy {
 private final L1Location _location=new L1Location(120,240);
 private short _mapId=4;
 private int getLocX2(){return 180;}private int getLocX1(){return 20;}
 	private void spawnGroundItem(int itemId, int stackCount, int count) {
		L1Item temp = ItemTable.getInstance().getTemplate(itemId);
		if (temp == null) {
			return;
		}
		L1Location loc = null;
		L1ItemInstance item = null;
		L1GroundInventory ground = null;
		for (int i = 0; i < count; i++) {
			loc = _location.randomLocation((getLocX2() - getLocX1()) / 2, false);
			if (temp.isStackable()) {
				item = ItemTable.getInstance().createItem(itemId);
				item.setEnchantLevel(0);
				item.setCount(stackCount);
				ground = L1World.getInstance().getInventory(loc.getX(), loc.getY(), _mapId);
				if (ground.checkAddItem(item, stackCount) == CInventory.OK) {
					ground.storeItem(item);
				}
			} else {
				item = null;
				for (int createCount = 0; createCount < stackCount; createCount++) {
					item = ItemTable.getInstance().createItem(itemId);
					item.setEnchantLevel(0);
					ground = L1World.getInstance().getInventory(loc.getX(), loc.getY(), _mapId);
					if (ground.checkAddItem(item, stackCount) == CInventory.OK) {
						ground.storeItem(item);
					}
				}
			}
		}
	}
 public void run(int id,int stackCount,int count){spawnGroundItem(id,stackCount,count);}
}
