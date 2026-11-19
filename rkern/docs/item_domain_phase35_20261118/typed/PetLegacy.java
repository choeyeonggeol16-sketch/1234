import java.util.*;
class PetLegacy {
static final int[] PET_MATCH_MAPID={1};
	private void giveMedal(L1PcInstance pc, int petMatchNo, boolean isWin) {
		if (pc == null) {
			return;
		}
		if (pc.getMapId() != PET_MATCH_MAPID[petMatchNo]) {
			return;
		}
		if (isWin) {
			SC_ServerMessage.send(pc, 1166, pc.getName());  // %0%s펫 매치로 승리를 거두었습니다.
			L1ItemInstance item = ItemTable.getInstance().createItem(41309);
			int count = 3;
			if (item != null) {
				if (pc.getInventory().checkAddItem(item, count) == L1Inventory
						.OK) {
					item.setCount(count);
					pc.getInventory().storeItem(item);
					SC_ServerMessage.send(pc, 403, item.getLogName());
				}
			}
			Random random = new Random(TestRng.seed); // 펫레이싱

			if(random.nextInt() < 33)
			{
				pc.getInventory().storeItem(3000025, 1);

				item = ItemTable.getInstance().createItem(3000025); 
				item.setCount(1);
				SC_ServerMessage.send(pc, 403, item.getLogName()); 
			}
		} else {
			L1ItemInstance item = ItemTable.getInstance().createItem(41309);
			int count = 1;
			if (item != null) {
				if (pc.getInventory().checkAddItem(item, count) == L1Inventory
						.OK) {
					item.setCount(count);
					pc.getInventory().storeItem(item);
					SC_ServerMessage.send(pc, 403, item.getLogName());
				}
			}
		}
	}
public void run(L1PcInstance pc,boolean win){giveMedal(pc,0,win);}
}
