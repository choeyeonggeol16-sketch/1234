package l1j.server.DogFight.Game;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DogFight.Game.Trigger.MJDogFightTriggerInfo;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;

public class MJDogFightFighterInfo {
	public static HashMap<Integer, ArrayList<MJDogFightFighterInfo>> do_load(final int game_id){
		final MJObjectWrapper<HashMap<Integer, ArrayList<MJDogFightFighterInfo>>> wrapper = new MJObjectWrapper<HashMap<Integer, ArrayList<MJDogFightFighterInfo>>>();
		wrapper.value = new HashMap<Integer, ArrayList<MJDogFightFighterInfo>>();		
		Selector.exec("select * from dogfight_fighter_info where game_id=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, game_id);
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJDogFightFighterInfo o = newInstance(rs);
					ArrayList<MJDogFightFighterInfo> corner_members = wrapper.value.get(o.get_corner_id());
					if(corner_members == null){
						corner_members = new ArrayList<MJDogFightFighterInfo>();
						wrapper.value.put(o.get_corner_id(), corner_members);
					}
					corner_members.add(o);
				}
			}
		});
		return wrapper.value;
	}

	private static MJDogFightFighterInfo newInstance(ResultSet rs) throws SQLException{
		int game_id = rs.getInt("game_id");
		int corner_id = rs.getInt("corner_id");
		int member_id = rs.getInt("member_id");
		
		return newInstance()
				.set_game_id(game_id)
				.set_corner_id(corner_id)
				.set_corner_name(rs.getString("corner_name"))
				.set_member_id(member_id)
				.set_x(rs.getInt("x"))
				.set_y(rs.getInt("y"))
				.set_heading(rs.getInt("heading"))
				.set_level(rs.getInt("level"))
				.set_max_hp(rs.getInt("max_hp"))
				.set_attackdelay_reduce(rs.getDouble("attackdelay_reduce"))
				.set_movedelay_reduce(rs.getDouble("movedelay_reduce"))
				.set_min_damage(rs.getInt("min_damage"))
				.set_max_damage(rs.getInt("max_damage"))
				.set_min_hit(rs.getInt("min_hit"))
				.set_max_hit(rs.getInt("max_hit"))
				.set_trigger(MJDogFightTriggerInfo.do_load(game_id, corner_id, member_id));
	}

	private static MJDogFightFighterInfo newInstance(){
		return new MJDogFightFighterInfo();
	}

	private int m_game_id;
	private int m_corner_id;
	private String m_corner_name;
	private int m_member_id;
	private int m_x;
	private int m_y;
	private int m_heading;
	private int m_level;
	private int m_max_hp;
	private double m_attackdelay_reduce;
	private double m_movedelay_reduce;
	private int m_min_damage;
	private int m_max_damage;
	private int m_min_hit;
	private int m_max_hit;
	private MJDogFightTriggerInfo m_trigger;
	private MJDogFightFighterInfo(){}

	public MJDogFightFighterInfo set_game_id(int game_id){
		m_game_id = game_id;
		return this;
	}
	public MJDogFightFighterInfo set_corner_id(int corner_id){
		m_corner_id = corner_id;
		return this;
	}
	public MJDogFightFighterInfo set_corner_name(String corner_name){
		m_corner_name = corner_name;
		return this;
	}
	public MJDogFightFighterInfo set_member_id(int member_id){
		m_member_id = member_id;
		return this;
	}
	public MJDogFightFighterInfo set_x(int x){
		m_x = x;
		return this;
	}
	public MJDogFightFighterInfo set_y(int y){
		m_y = y;
		return this;
	}
	public MJDogFightFighterInfo set_heading(int heading){
		m_heading = heading;
		return this;
	}
	public MJDogFightFighterInfo set_level(int level){
		m_level = level;
		return this;
	}
	public MJDogFightFighterInfo set_max_hp(int max_hp){
		m_max_hp = max_hp;
		return this;
	}
	public MJDogFightFighterInfo set_attackdelay_reduce(double attackdelay_reduce){
		m_attackdelay_reduce = attackdelay_reduce;
		return this;
	}
	public MJDogFightFighterInfo set_movedelay_reduce(double movedelay_reduce){
		m_movedelay_reduce = movedelay_reduce;
		return this;
	}
	public MJDogFightFighterInfo set_min_damage(int min_damage){
		m_min_damage = min_damage;
		return this;
	}
	public MJDogFightFighterInfo set_max_damage(int max_damage){
		m_max_damage = max_damage;
		return this;
	}
	public MJDogFightFighterInfo set_min_hit(int min_hit){
		m_min_hit = min_hit;
		return this;
	}
	public MJDogFightFighterInfo set_max_hit(int max_hit){
		m_max_hit = max_hit;
		return this;
	}
	public MJDogFightFighterInfo set_trigger(MJDogFightTriggerInfo trigger){
		m_trigger = trigger;
		return this;
	}
	public int get_game_id(){
		return m_game_id;
	}
	public int get_corner_id(){
		return m_corner_id;
	}
	public String get_corner_name(){
		return m_corner_name;
	}
	public int get_member_id(){
		return m_member_id;
	}
	public int get_x(){
		return m_x;
	}
	public int get_y(){
		return m_y;
	}
	public int get_heading(){
		return m_heading;
	}
	public int get_level(){
		return m_level;
	}
	public int get_max_hp(){
		return m_max_hp;
	}
	public double get_attackdelay_reduce(){
		return m_attackdelay_reduce;
	}
	public double get_movedelay_reduce(){
		return m_movedelay_reduce;
	}
	public int get_min_damage(){
		return m_min_damage;
	}
	public int get_max_damage(){
		return m_max_damage;
	}
	public int get_min_hit(){
		return m_min_hit;
	}
	public int get_max_hit(){
		return m_max_hit;
	}
	public MJDogFightTriggerInfo get_trigger(){
		return m_trigger;
	}

	public MJDogFightInstance create_fight_instance(MJCompanionClassInfo cInfo){
		return MJDogFightInstance.newInstance(
				cInfo,
				String.format("[%s]#%d %s", get_corner_name(), cInfo.get_db_id(), cInfo.get_class_npc_name_id()), 
				get_level(), 
				get_max_hp(), 
				get_corner_id(), 
				get_corner_name(),
				get_attackdelay_reduce(), 
				get_movedelay_reduce(), 
				get_x(), 
				get_y(), 
				get_heading(), 
				4, 
				get_min_damage(), 
				get_max_damage(), 
				get_min_hit(), 
				get_max_hit(),
				get_trigger());
	}
}

