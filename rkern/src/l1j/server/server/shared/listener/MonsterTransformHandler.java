package l1j.server.server.shared.listener;

import l1j.server.server.model.Instance.L1MonsterInstance;

public interface MonsterTransformHandler {
	public void onTransFormNotify(L1MonsterInstance m);
}
