package l1j.server.server.shared.listener;

import l1j.server.server.model.Instance.L1MonsterInstance;


public interface MonsterDeathHandler {
	/** true is death processing.. **/
	public boolean onDeathNotify(L1MonsterInstance m);
}
