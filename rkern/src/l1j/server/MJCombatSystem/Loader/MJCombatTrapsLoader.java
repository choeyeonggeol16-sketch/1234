package l1j.server.MJCombatSystem.Loader;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.MJTemplate.Trap.AbstractTrap;

public class MJCombatTrapsLoader{
	private static MJCombatTrapsLoader _instance;
	public static MJCombatTrapsLoader getInstance(){
		if(_instance == null)
			_instance = new MJCombatTrapsLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJCombatTrapsLoader old = _instance;
		_instance = new MJCombatTrapsLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private ArrayList<AbstractTrap> _traps;
	private MJCombatTrapsLoader(){
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
		return get(MJRnd.next(_traps.size()));
	}

	public void dispose(){
		if(_traps != null){
			_traps.clear();
			_traps = null;
		}
	}
}
