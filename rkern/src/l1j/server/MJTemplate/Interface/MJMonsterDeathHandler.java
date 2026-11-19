package l1j.server.MJTemplate.Interface;

import l1j.server.server.model.Instance.L1MonsterInstance;


public interface MJMonsterDeathHandler {
	/** true is death processing.. **/
	public boolean onDeathNotify(L1MonsterInstance m);
}
