package l1j.server.MJCombatSystem.Games;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJTemplate.Builder.MJTowerBuilder;
import l1j.server.MJTemplate.L1Instance.MJL1TowerInstance;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.utils.SQLUtil;

public class MJBattleTowerInformation extends MJCombatInformation{
	public static MJBattleTowerInformation newInstance(){
		return new MJBattleTowerInformation();
	}
	
	private HashMap<Integer, MJTowerBuilder> _t_builders;
	private MJBattleTowerInformation(){}
	
	@Override
	public MJCombatInformation deep_copy(MJCombatInformation destination){
		super.deep_copy(destination);
		if(destination instanceof MJBattleTowerInformation){
			((MJBattleTowerInformation) destination).set_t_builders(get_t_builders());
		}
		return destination;
	}
	
	public MJBattleTowerInformation load_tower_informations(){
		Selector.exec("select * from tb_combat_towers where combat_id=? order by team_id asc", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, get_combat_id());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				_t_builders = new HashMap<Integer, MJTowerBuilder>(SQLUtil.calcRows(rs));
				while(rs.next()){
					_t_builders.put(
							rs.getInt("team_id"), 
							new MJTowerBuilder()
								.set_npcid(rs.getInt("npc_id"))
								.set_x(rs.getInt("x"))
								.set_y(rs.getInt("y"))
					);
				}
			}
		});
		return this;
	}
	
	public MJBattleTowerInformation set_t_builders(HashMap<Integer, MJTowerBuilder> t_builders){
		_t_builders = t_builders;
		return this;
	}
	
	public HashMap<Integer, MJTowerBuilder> get_t_builders(){
		return _t_builders;
	}
	
	public MJL1TowerInstance do_spawn_tower(Short map_id, int team_id){
		return _t_builders.get(team_id).set_map_id(map_id).build();
	}
}
