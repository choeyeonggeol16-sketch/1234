package l1j.server.server.model.character.combat.arena.games;

import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.CombatInformation;
import l1j.server.server.shared.builder.DropItemBuilder;
import l1j.server.server.shared.builder.MonsterSpawnBuilder;
import l1j.server.server.shared.builder.TreasureChestBuilder;
import l1j.server.server.shared.geometry.GamePoint;
import l1j.server.server.shared.geometry.GameRectangle;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.utils.GameUtils;

public class BattleTournamentInformation extends CombatInformation{
	public static BattleTournamentInformation newInstance(){
		return new BattleTournamentInformation();
	}
	
	private GameRectangle[][] _rt_tournament_areas;
	private GamePoint[][] _pt_lift_gates;
	private MonsterSpawnBuilder[] _boss_builders;
	private TreasureChestBuilder[] _chest_builders;
	private ArrayList<DropItemBuilder> _chest_item_builders;
	private ArrayList<DropItemBuilder> _drop_builders;
	private MonsterSpawnBuilder[] _monster_builders;
	private int[]					_keys;
	private BattleTournamentInformation(){}
	
	@Override
	public CombatInformation deep_copy(CombatInformation destination){
		super.deep_copy(destination);
		if(destination instanceof BattleTournamentInformation){
			((BattleTournamentInformation) destination)
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
	
	public BattleTournamentInformation load_tournament_informations(){
		load_round_information();
		load_bosses_information();
		load_chest_information();
		load_chest_items();
		load_drop_items();
		load_monsters();
		return this;
	}
	
	private void load_round_information(){
		_rt_tournament_areas = new GameRectangle[2][5];
		_pt_lift_gates = new GamePoint[2][5];
		Selector.exec("select * from tb_combat_bt_area_information", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int team_id = rs.getInt("team_id");
					int round_id = rs.getInt("round_id");
					_rt_tournament_areas[team_id][round_id] = GameRectangle.newInstance(rs.getInt("area_left"), rs.getInt("area_top"), rs.getInt("area_right"), rs.getInt("area_bottom"));
					_pt_lift_gates[team_id][round_id] = GamePoint.newInstance().setX(rs.getInt("lift_x")).setY(rs.getInt("lift_y"));
				}
			}
		});
	}
	
	private void load_bosses_information(){
		_boss_builders = new MonsterSpawnBuilder[5];
		Selector.exec("select * from tb_combat_bt_bosses", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					_boss_builders[rs.getInt("round_id")] = new MonsterSpawnBuilder().setNpc(GameUtils.parseToIntQ(rs.getString("npc_id"), ","));
			}
		});
	}
	
	private void load_chest_information(){
		_chest_builders = new TreasureChestBuilder[5];
		_keys = new int[5];
		Selector.exec("select * from tb_combat_bt_chest_information", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int id = rs.getInt("id");
					int key = rs.getInt("key");
					_chest_builders[id] = new TreasureChestBuilder()
							.setName(rs.getString("description"))
							.setGfx(rs.getInt("gfx"))
							.setKey(key);
					_keys[id] = key;
				}
			}
		});
	}
	
	private void load_chest_items(){
		_chest_item_builders = new ArrayList<DropItemBuilder>(32);
		Selector.exec("select * from tb_combat_bt_chest_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					_chest_item_builders.add(new DropItemBuilder()
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
		_drop_builders = new ArrayList<DropItemBuilder>(32);
		Selector.exec("select * from tb_combat_bt_drop_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					_drop_builders.add(new DropItemBuilder()
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
		_monster_builders = new MonsterSpawnBuilder[4];
		Selector.exec("select * from tb_combat_bt_monsters order by round_id asc", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					_monster_builders[rs.getInt("round_id")] = new MonsterSpawnBuilder().setNpc(GameUtils.parseToIntQ(rs.getString("npc_id"), ","));
			}
		});
	}
	
	public BattleTournamentInformation set_rt_tournament_areas(GameRectangle[][] val){
		_rt_tournament_areas = val;
		return this;
	}
	public GameRectangle[][] get_rt_tournament_areas(){
		return _rt_tournament_areas;
	}
	public GameRectangle get_tournament_area(int round_id, int team_id){
		return _rt_tournament_areas[team_id][round_id];
	}

	public BattleTournamentInformation set_pt_lift_gates(GamePoint[][] val){
		_pt_lift_gates = val;
		return this;
	}
	public GamePoint[][] get_pt_lift_gates(){
		return _pt_lift_gates;
	}

	public BattleTournamentInformation set_boss_builders(MonsterSpawnBuilder[] val){
		_boss_builders = val;
		return this;
	}
	public MonsterSpawnBuilder[] get_boss_builders(){
		return _boss_builders;
	}

	public BattleTournamentInformation set_chest_builders(TreasureChestBuilder[] val){
		_chest_builders = val;
		return this;
	}
	public TreasureChestBuilder[] get_chest_builders(){
		return _chest_builders;
	}

	public BattleTournamentInformation set_chest_item_builders(ArrayList<DropItemBuilder> val){
		_chest_item_builders = val;
		return this;
	}
	public ArrayList<DropItemBuilder> get_chest_item_builders(){
		return _chest_item_builders;
	}

	public BattleTournamentInformation set_drop_builders(ArrayList<DropItemBuilder> val){
		_drop_builders = val;
		return this;
	}
	public ArrayList<DropItemBuilder> get_drop_builders(){
		return _drop_builders;
	}

	public BattleTournamentInformation set_monster_builders(MonsterSpawnBuilder[] val){
		_monster_builders = val;
		return this;
	}
	public MonsterSpawnBuilder[] get_monster_builders(){
		return _monster_builders;
	}
	
	public int[] get_keys(){
		return _keys;
	}
}	
