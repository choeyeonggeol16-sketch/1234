import java.util.*;
class PetCandidate {
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
		}
		final int count = isWin ? 3 : 1;
		final L1ItemInstance medal = ItemTable.getInstance().createItem(41309);
		if (medal != null) {
			try {
				if (pc.getInventory().checkAddItem(medal, count) == CInventory.OK) {
					medal.setCount(count);
					if (pc.getInventory().storeItem(medal) != null) {
						SC_ServerMessage.send(pc, 403, medal.getLogName());
					}
				}
			} finally {
				if (pc.getInventory().getItem(medal.getId()) != medal) {
					L1World.getInstance().removeObject(medal);
				}
			}
		}
		if (isWin) {
			Random random = new Random(TestRng.seed); // 펫레이싱
			if (random.nextInt() < 33) {
				L1ItemInstance bonus = pc.getInventory().storeItem(3000025, 1);
				if (bonus != null) {
					SC_ServerMessage.send(pc, 403, bonus.getNumberedName(1));
				}
			}
		}
	}
public void run(L1PcInstance pc,boolean win){giveMedal(pc,0,win);}
}
