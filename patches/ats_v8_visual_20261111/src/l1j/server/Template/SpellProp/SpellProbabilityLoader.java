package l1j.server.Template.SpellProp;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

public class SpellProbabilityLoader {
	private static SpellProbabilityLoader _instance;
	
	public static SpellProbabilityLoader getInstance() {
		if(_instance == null)
			_instance = new SpellProbabilityLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new SpellProbabilityLoader();
		}
	}
	private HashMap<Integer, spellProbabilityInfo> _spells;
	private SpellProbabilityLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, spellProbabilityInfo> spells = new HashMap<Integer, spellProbabilityInfo>(256);
		Selector.exec("select * from magic_probability", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					spellProbabilityInfo pInfo = spellProbabilityInfo.newInstance(rs);
					if(pInfo == null) {
						continue;
					}
					spells.put(pInfo.get_skill_id(), pInfo);
				}
			}
		});
		_spells = spells;
	}
	
	public int calc_probability(int skill_id, L1PcInstance pc, L1Character target, int attacker_int, int target_mr) {
		spellProbabilityInfo pInfo = _spells.get(skill_id);
		return pInfo == null ? -1 : pInfo.calc_probability(pc, target, attacker_int, target_mr);
	}
	
	public boolean containsProbability(int skill_id) {
		return _spells.containsKey(skill_id);
	}
}
