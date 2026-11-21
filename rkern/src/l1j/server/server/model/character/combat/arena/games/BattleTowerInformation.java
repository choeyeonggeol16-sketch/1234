package l1j.server.server.model.character.combat.arena.games;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.server.model.character.combat.arena.CombatInformation;
import l1j.server.server.model.world.instance.shared.GameL1TowerInstance;
import l1j.server.server.shared.builder.TowerBuilder;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.utils.SQLUtil;

public class BattleTowerInformation extends CombatInformation{
	public static BattleTowerInformation newInstance(){
		return new BattleTowerInformation();
	}
	
	private HashMap<Integer, TowerBuilder> _t_builders;
	private BattleTowerInformation(){}
	
	@Override
	public CombatInformation deep_copy(CombatInformation destination){
		super.deep_copy(destination);
		if(destination instanceof BattleTowerInformation){
			((BattleTowerInformation) destination).set_t_builders(get_t_builders());
		}
		return destination;
	}
	
	public BattleTowerInformation load_tower_informations(){
		Selector.exec("select * from tb_combat_towers where combat_id=? order by team_id asc", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, get_combat_id());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				_t_builders = new HashMap<Integer, TowerBuilder>(SQLUtil.calcRows(rs));
				while(rs.next()){
					_t_builders.put(
							rs.getInt("team_id"), 
							new TowerBuilder()
								.set_npcid(rs.getInt("npc_id"))
								.set_x(rs.getInt("x"))
								.set_y(rs.getInt("y"))
					);
				}
			}
		});
		return this;
	}
	
	public BattleTowerInformation set_t_builders(HashMap<Integer, TowerBuilder> t_builders){
		_t_builders = t_builders;
		return this;
	}
	
	public HashMap<Integer, TowerBuilder> get_t_builders(){
		return _t_builders;
	}
	
	public GameL1TowerInstance do_spawn_tower(Short map_id, int team_id){
		return _t_builders.get(team_id).set_map_id(map_id).build();
	}
}
