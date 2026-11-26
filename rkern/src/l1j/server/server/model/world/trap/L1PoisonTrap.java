package l1j.server.server.model.world.trap;

import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.effect.poison.DamagePoison;
import l1j.server.server.model.character.effect.poison.ParalysisPoison;
import l1j.server.server.model.character.effect.poison.SilencePoison;

public class L1PoisonTrap extends L1Trap {
	private final String _type;
	private final int _delay;
	private final int _time;
	private final int _damage;

	public L1PoisonTrap(TrapStorage storage) {
		super(storage);

		_type = storage.getString("poisonType");
		_delay = storage.getInt("poisonDelay");
		_time = storage.getInt("poisonTime");
		_damage = storage.getInt("poisonDamage");
	}

	@Override
	public void onTrod(L1PcInstance trodFrom, L1Object trapObj) {
		sendEffect(trapObj);

		if (_type.equals("d")) {
			if( trodFrom.getZoneType() != 1 )
			{
				DamagePoison.doInfection(trodFrom, trodFrom, _time, _damage, false, 30000);
			}
		} else if (_type.equals("s")) {
			SilencePoison.doInfection(trodFrom);
		} else if (_type.equals("p")) {
			MonsterParalyze paralyze = new MonsterParalyzeDelay.MonsterParalyze();
			paralyze.paralyze_delay = _delay;
			paralyze.paralyze_millis = _time;
			paralyze.skill_id = 0;
			ParalysisPoison.doInfection(trodFrom, paralyze);
		}
	}
}
