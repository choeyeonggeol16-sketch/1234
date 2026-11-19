package l1j.server.MJTemplate.Interface;

import l1j.server.server.model.L1Character;

public interface MJDamageListener<T extends L1Character>{
	public boolean is_filter(L1Character attacker, T receiver, int damage);
	public Object on_damage(L1Character attacker, T receiver, int damage);
	public Object on_daed(L1Character attacker, T deather);
}
