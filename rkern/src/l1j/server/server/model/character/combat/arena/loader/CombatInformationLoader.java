
package l1j.server.server.model.character.combat.arena.loader;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.server.model.character.combat.arena.CombatEGameType;
import l1j.server.server.model.character.combat.arena.CombatInformation;
import l1j.server.server.model.character.combat.arena.games.BattleFieldInformation;
import l1j.server.server.model.character.combat.arena.games.BattleTournamentInformation;
import l1j.server.server.model.character.combat.arena.games.BattleTowerInformation;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CombatInformationLoader{
	private static CombatInformationLoader _instance;
	public static CombatInformationLoader getInstance(){
		if(_instance == null)
			_instance = new CombatInformationLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		CombatInformationLoader old = _instance;
		_instance = new CombatInformationLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, ArrayList<CombatInformation>> _combats;
	private CombatInformationLoader(){
		_combats = load();
	}
	
	private HashMap<Integer, ArrayList<CombatInformation>> load(){
		HashMap<Integer, ArrayList<CombatInformation>> combats = new HashMap<Integer, ArrayList<CombatInformation>>(CombatEGameType.values().length);
		Selector.exec("select * from tb_combat_informations", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CombatInformation cInfo = CombatInformation.newInstance(rs);
					CombatEGameType g_type = cInfo.get_game_type();
					if(g_type.equals(CombatEGameType.BATTLE_FIELD)){
						cInfo = cInfo.deep_copy(BattleFieldInformation.newInstance());
					}else if(g_type.equals(CombatEGameType.BATTLE_TOWER)){
						cInfo = cInfo.deep_copy(BattleTowerInformation.newInstance());
						((BattleTowerInformation) cInfo).load_tower_informations();
					}else if(g_type.equals(CombatEGameType.BATTLE_TOURNAMENT)){
						cInfo = cInfo.deep_copy(BattleTournamentInformation.newInstance());
						((BattleTournamentInformation) cInfo).load_tournament_informations();
					}else if(g_type.equals(CombatEGameType.NONE))
						continue;
					
					ArrayList<CombatInformation> informations = combats.get(g_type.to_int());
					if(informations == null){
						informations = new ArrayList<CombatInformation>(4);
						combats.put(g_type.to_int(), informations);
					}
					informations.add(cInfo);
				}
			}
		});
		
		return combats;
	}
	
	public CombatInformation select(CombatEGameType g_type){
		ArrayList<CombatInformation> list = _combats.get(g_type.to_int());
		return list.get(GameRandom.next(list.size()));
	}
	
	public void dispose(){
		if(_combats != null){
			for(ArrayList<CombatInformation> list : _combats.values())
				list.clear();
			_combats.clear();
			_combats = null;
		}
	}
}
