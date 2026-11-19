
package l1j.server.MJCombatSystem.Loader;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJCombatSystem.MJCombatEGameType;
import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJCombatSystem.Games.MJBattleFieldInformation;
import l1j.server.MJCombatSystem.Games.MJBattleTournamentInformation;
import l1j.server.MJCombatSystem.Games.MJBattleTowerInformation;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCombatInformationLoader{
	private static MJCombatInformationLoader _instance;
	public static MJCombatInformationLoader getInstance(){
		if(_instance == null)
			_instance = new MJCombatInformationLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJCombatInformationLoader old = _instance;
		_instance = new MJCombatInformationLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, ArrayList<MJCombatInformation>> _combats;
	private MJCombatInformationLoader(){
		_combats = load();
	}
	
	private HashMap<Integer, ArrayList<MJCombatInformation>> load(){
		HashMap<Integer, ArrayList<MJCombatInformation>> combats = new HashMap<Integer, ArrayList<MJCombatInformation>>(MJCombatEGameType.values().length);
		Selector.exec("select * from tb_combat_informations", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCombatInformation cInfo = MJCombatInformation.newInstance(rs);
					MJCombatEGameType g_type = cInfo.get_game_type();
					if(g_type.equals(MJCombatEGameType.BATTLE_FIELD)){
						cInfo = cInfo.deep_copy(MJBattleFieldInformation.newInstance());
					}else if(g_type.equals(MJCombatEGameType.BATTLE_TOWER)){
						cInfo = cInfo.deep_copy(MJBattleTowerInformation.newInstance());
						((MJBattleTowerInformation) cInfo).load_tower_informations();
					}else if(g_type.equals(MJCombatEGameType.BATTLE_TOURNAMENT)){
						cInfo = cInfo.deep_copy(MJBattleTournamentInformation.newInstance());
						((MJBattleTournamentInformation) cInfo).load_tournament_informations();
					}else if(g_type.equals(MJCombatEGameType.NONE))
						continue;
					
					ArrayList<MJCombatInformation> informations = combats.get(g_type.to_int());
					if(informations == null){
						informations = new ArrayList<MJCombatInformation>(4);
						combats.put(g_type.to_int(), informations);
					}
					informations.add(cInfo);
				}
			}
		});
		
		return combats;
	}
	
	public MJCombatInformation select(MJCombatEGameType g_type){
		ArrayList<MJCombatInformation> list = _combats.get(g_type.to_int());
		return list.get(MJRnd.next(list.size()));
	}
	
	public void dispose(){
		if(_combats != null){
			for(ArrayList<MJCombatInformation> list : _combats.values())
				list.clear();
			_combats.clear();
			_combats = null;
		}
	}
}
