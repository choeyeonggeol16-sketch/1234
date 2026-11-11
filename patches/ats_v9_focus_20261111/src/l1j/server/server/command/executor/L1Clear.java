package l1j.server.server.command.executor;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class L1Clear implements L1CommandExecutor {

	private L1Clear() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Clear();
	}

	public void execute(L1PcInstance pc, String cmdName, String arg) {
		GeneralThreadPool.getInstance().execute(new Runnable() {
			@Override
			public void run() {
				for (L1Object obj : L1World.getInstance().getVisibleObjects(pc, 20)) {
					if (obj instanceof MonsterInstance) {
						MonsterInstance npc = (MonsterInstance) obj;
						
						npc.receiveDamage(pc, 1000000);
					}
				}
			}
		});
	}
}