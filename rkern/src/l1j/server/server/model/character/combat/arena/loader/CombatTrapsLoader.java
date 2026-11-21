package l1j.server.server.model.character.combat.arena.loader;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.server.model.world.trap.AbstractTrap;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CombatTrapsLoader{
	private static CombatTrapsLoader _instance;
	public static CombatTrapsLoader getInstance(){
		if(_instance == null)
			_instance = new CombatTrapsLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		CombatTrapsLoader old = _instance;
		_instance = new CombatTrapsLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private ArrayList<AbstractTrap> _traps;
	private CombatTrapsLoader(){
		_traps = load();
	}
	
	private ArrayList<AbstractTrap> load(){
		ArrayList<AbstractTrap> traps = new ArrayList<AbstractTrap>(4);
		Selector.exec("select * from tb_combat_traps", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					traps.add(AbstractTrap.newInstance(rs));
			}
		});
		return traps;
	}
	
	public AbstractTrap get(int index){
		return _traps.get(index);
	}
	
	public AbstractTrap to_rand_trap(){
		return get(GameRandom.next(_traps.size()));
	}

	public void dispose(){
		if(_traps != null){
			_traps.clear();
			_traps = null;
		}
	}
}
