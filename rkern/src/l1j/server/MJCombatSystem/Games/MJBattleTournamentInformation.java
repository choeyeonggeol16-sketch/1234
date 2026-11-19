package l1j.server.MJCombatSystem.Games;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJTemplate.Builder.MJDropItemBuilder;
import l1j.server.MJTemplate.Builder.MJMonsterSpawnBuilder;
import l1j.server.MJTemplate.Builder.MJTreasureChestBuilder;
import l1j.server.MJTemplate.Lineage2D.MJPoint;
import l1j.server.MJTemplate.Lineage2D.MJRectangle;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.utils.MJCommons;

public class MJBattleTournamentInformation extends MJCombatInformation{
	public static MJBattleTournamentInformation newInstance(){
		return new MJBattleTournamentInformation();
	}
	
	private MJRectangle[][] _rt_tournament_areas;
	private MJPoint[][] _pt_lift_gates;
	private MJMonsterSpawnBuilder[] _boss_builders;
	private MJTreasureChestBuilder[] _chest_builders;
	private ArrayList<MJDropItemBuilder> _chest_item_builders;
	private ArrayList<MJDropItemBuilder> _drop_builders;
	private MJMonsterSpawnBuilder[] _monster_builders;
	private int[]					_keys;
	private MJBattleTournamentInformation(){}
	
	@Override
	public MJCombatInformation deep_copy(MJCombatInformation destination){
		super.deep_copy(destination);
		if(destination instanceof MJBattleTournamentInformation){
			((MJBattleTournamentInformation) destination)
			.set_rt_tournament_areas(get_rt_tournament_areas())
			.set_pt_lift_gates(get_pt_lift_gates())
			.set_boss_builders(get_boss_builders())
			.set_chest_builders(get_chest_builders())
			.set_chest_item_builders(get_chest_item_builders())
			.set_drop_builders(get_drop_builders())
			.set_monster_builders(get_monster_builders());
		}
		return destination;
	}
	
	public MJBattleTournamentInformation load_tournament_informations(){
		load_round_information();
		load_bosses_information();
		load_chest_information();
		load_chest_items();
		load_drop_items();
		load_monsters();
		return this;
	}
	
	private void load_round_information(){
		_rt_tournament_areas = new MJRectangle[2][5];
		_pt_lift_gates = new MJPoint[2][5];
		Selector.exec("select * from tb_combat_bt_area_information", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int team_id = rs.getInt("team_id");
					int round_id = rs.getInt("round_id");
					_rt_tournament_areas[team_id][round_id] = MJRectangle.newInstance(rs.getInt("area_left"), rs.getInt("area_top"), rs.getInt("area_right"), rs.getInt("area_bottom"));
					_pt_lift_gates[team_id][round_id] = MJPoint.newInstance().setX(rs.getInt("lift_x")).setY(rs.getInt("lift_y"));
				}
			}
		});
	}
	
	private void load_bosses_information(){
		_boss_builders = new MJMonsterSpawnBuilder[5];
		Selector.exec("select * from tb_combat_bt_bosses", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					_boss_builders[rs.getInt("round_id")] = new MJMonsterSpawnBuilder().setNpc(MJCommons.parseToIntQ(rs.getString("npc_id"), ","));
			}
		});
	}
	
	private void load_chest_information(){
		_chest_builders = new MJTreasureChestBuilder[5];
		_keys = new int[5];
		Selector.exec("select * from tb_combat_bt_chest_information", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int id = rs.getInt("id");
					int key = rs.getInt("key");
					_chest_builders[id] = new MJTreasureChestBuilder()
							.setName(rs.getString("description"))
							.setGfx(rs.getInt("gfx"))
							.setKey(key);
					_keys[id] = key;
				}
			}
		});
	}
	
	private void load_chest_items(){
		_chest_item_builders = new ArrayList<MJDropItemBuilder>(32);
		Selector.exec("select * from tb_combat_bt_chest_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					_chest_item_builders.add(new MJDropItemBuilder()
							.setItemId(rs.getInt("item_id"))
							.setMinimumEnchant(rs.getInt("enchant_min"))
							.setMaximumEnchant(rs.getInt("enchant_max"))
							.setMinCount(rs.getInt("min"))
							.setMaxCount(rs.getInt("max"))
							.setDice(rs.getInt("dice")));
				}
			}
		});
	}
	
	private void load_drop_items(){
		_drop_builders = new ArrayList<MJDropItemBuilder>(32);
		Selector.exec("select * from tb_combat_bt_drop_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					_drop_builders.add(new MJDropItemBuilder()
							.setItemId(rs.getInt("item_id"))
							.setMinimumEnchant(rs.getInt("enchant_min"))
							.setMaximumEnchant(rs.getInt("enchant_max"))
							.setMinCount(rs.getInt("min"))
							.setMaxCount(rs.getInt("max"))
							.setDice(-1));
				}
			}
		});
	}
	
	private void load_monsters(){
		_monster_builders = new MJMonsterSpawnBuilder[4];
		Selector.exec("select * from tb_combat_bt_monsters order by round_id asc", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					_monster_builders[rs.getInt("round_id")] = new MJMonsterSpawnBuilder().setNpc(MJCommons.parseToIntQ(rs.getString("npc_id"), ","));
			}
		});
	}
	
	public MJBattleTournamentInformation set_rt_tournament_areas(MJRectangle[][] val){
		_rt_tournament_areas = val;
		return this;
	}
	public MJRectangle[][] get_rt_tournament_areas(){
		return _rt_tournament_areas;
	}
	public MJRectangle get_tournament_area(int round_id, int team_id){
		return _rt_tournament_areas[team_id][round_id];
	}

	public MJBattleTournamentInformation set_pt_lift_gates(MJPoint[][] val){
		_pt_lift_gates = val;
		return this;
	}
	public MJPoint[][] get_pt_lift_gates(){
		return _pt_lift_gates;
	}

	public MJBattleTournamentInformation set_boss_builders(MJMonsterSpawnBuilder[] val){
		_boss_builders = val;
		return this;
	}
	public MJMonsterSpawnBuilder[] get_boss_builders(){
		return _boss_builders;
	}

	public MJBattleTournamentInformation set_chest_builders(MJTreasureChestBuilder[] val){
		_chest_builders = val;
		return this;
	}
	public MJTreasureChestBuilder[] get_chest_builders(){
		return _chest_builders;
	}

	public MJBattleTournamentInformation set_chest_item_builders(ArrayList<MJDropItemBuilder> val){
		_chest_item_builders = val;
		return this;
	}
	public ArrayList<MJDropItemBuilder> get_chest_item_builders(){
		return _chest_item_builders;
	}

	public MJBattleTournamentInformation set_drop_builders(ArrayList<MJDropItemBuilder> val){
		_drop_builders = val;
		return this;
	}
	public ArrayList<MJDropItemBuilder> get_drop_builders(){
		return _drop_builders;
	}

	public MJBattleTournamentInformation set_monster_builders(MJMonsterSpawnBuilder[] val){
		_monster_builders = val;
		return this;
	}
	public MJMonsterSpawnBuilder[] get_monster_builders(){
		return _monster_builders;
	}
	
	public int[] get_keys(){
		return _keys;
	}
}	
