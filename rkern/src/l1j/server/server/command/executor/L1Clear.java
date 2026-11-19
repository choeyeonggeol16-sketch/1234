package l1j.server.server.command.executor;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1Clear implements L1CommandExecutor{

	private L1Clear(){ }

	public static L1CommandExecutor getInstance(){
		return new L1Clear();
	}

	public void execute(L1PcInstance pc, String cmdName, String arg) {
		for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(pc, -1)) {
			if (!(obj instanceof L1MonsterInstance)) {
				continue;
			}

			L1MonsterInstance mob = (L1MonsterInstance) obj;

			// 이미 죽었거나 삭제 중인 객체는 제외
			if (mob.isDead() || mob.getCurrentHp() <= 0) {
				continue;
			}

			try {
				mob.receiveDamage(pc, mob.getCurrentHp());		

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}