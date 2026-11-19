import java.util.*;
class DeathCandidate {
 public void run(L1PcInstance pc,boolean win){
  Random random=new Random(TestRng.seed);
 if (random.nextInt() < 33) {
						// The stored award already carries the template needed for its message.
						// No second world-registered dummy object is necessary.
						L1ItemInstance reward = pc.getInventory().storeItem(3000022, 1);
						if (reward != null) {
							SC_ServerMessage.send(pc, 403, reward.getNumberedName(1));
						}
					}
}
}
