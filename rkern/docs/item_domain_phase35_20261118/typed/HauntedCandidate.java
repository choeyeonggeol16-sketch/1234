import java.util.*;
class HauntedCandidate {
static final int STATUS_CLEANUP=3;
int _count;
void setHauntedHouseStatus(int v){Trace.events.add("status:"+v);}
void despawnFire(){Trace.events.add("despawn");}
void npcBroadcast(String s){Trace.events.add("broadcast:"+s);}

	public void endHauntedHouse(L1PcInstance pc) {
		setHauntedHouseStatus(STATUS_CLEANUP);
		SC_SystemMessage.send(pc, "영혼의 불꽃을 파괴했습니다.");

		final L1ItemInstance item = ItemTable.getInstance().createItem(41308);

		if (item != null) {
			try {
				if (pc.getInventory().checkAddItem(item, 1) == CInventory.OK) {
					item.setCount(1);
					if (pc.getInventory().storeItem(item) != null) {
						SC_ServerMessage.send(pc, 403, item.getLogName());

						Random random = new Random(TestRng.seed); // 펫레이싱
						if (random.nextInt() < 33) {
							// Reuse the actual grant for the one-item notice.
							L1ItemInstance bonus = pc.getInventory().storeItem(3000023, 1);
							if (bonus != null) {
								SC_ServerMessage.send(pc, 403, bonus.getNumberedName(1));
							}
						}
					}
				}
			} finally {
				// ItemTable pre-registers this object. Stack merge/rejection/exception
				// must not retain a world index entry for the unowned image.
				if (pc.getInventory().getItem(item.getId()) != item) {
					L1World.getInstance().removeObject(item);
				}
			}
		}

		despawnFire();

		npcBroadcast("예상보다 일찍 도착했군.");

		_count = 5;
	}
public void run(L1PcInstance pc,boolean win){endHauntedHouse(pc);}
}
