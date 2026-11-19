import java.util.*;
class HauntedLegacy {
static final int STATUS_CLEANUP=3;
int _count;
void setHauntedHouseStatus(int v){Trace.events.add("status:"+v);}
void despawnFire(){Trace.events.add("despawn");}
void npcBroadcast(String s){Trace.events.add("broadcast:"+s);}

	public void endHauntedHouse(L1PcInstance pc) {
		setHauntedHouseStatus(STATUS_CLEANUP);
		SC_SystemMessage.send(pc, "영혼의 불꽃을 파괴했습니다.");

		L1ItemInstance item = ItemTable.getInstance().createItem(41308);

		if (item != null) {
			if (pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
				item.setCount(1);
				pc.getInventory().storeItem(item);
				SC_ServerMessage.send(pc, 403, item.getLogName());

				Random random = new Random(TestRng.seed); // 펫레이싱

				if (random.nextInt() < 33) {
					pc.getInventory().storeItem(3000023, 1);

					item = ItemTable.getInstance().createItem(3000023);
					item.setCount(1);
					SC_ServerMessage.send(pc, 403, item.getLogName());
				}
			}
		}

		despawnFire();

		npcBroadcast("예상보다 일찍 도착했군.");

		_count = 5;
	}
public void run(L1PcInstance pc,boolean win){endHauntedHouse(pc);}
}
