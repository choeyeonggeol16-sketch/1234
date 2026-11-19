import java.util.*;
class DeathLegacy {
 public void run(L1PcInstance pc,boolean win){
  Random random=new Random(TestRng.seed);
 if (random.nextInt() < 33) {
						pc.getInventory().storeItem(3000022, 1);

						L1ItemInstance item = ItemTable.getInstance().createItem(3000022);
						item.setCount(1);
						SC_ServerMessage.send(pc, 403, item.getLogName());
					}
}
}
