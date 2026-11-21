package l1j.server.server.shared.listener;

import l1j.server.server.model.L1Character;

public interface DamageListener<T extends L1Character>{
	public boolean is_filter(L1Character attacker, T receiver, int damage);
	public Object on_damage(L1Character attacker, T receiver, int damage);
	public Object on_daed(L1Character attacker, T deather);
}
