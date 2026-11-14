package l1j.server.MJCompanion.Instance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1CompanionInstance;
import LIN.SC_UPDATE_INVENTORY_NOTI;

public class MJCompanionInstanceCache {

	private static ConcurrentHashMap<Integer, MJCompanionInstanceCache> m_caches;
	public static void do_load(){
		ConcurrentHashMap<Integer, MJCompanionInstanceCache> caches = new ConcurrentHashMap<Integer, MJCompanionInstanceCache>();
		Selector.exec("select * from companion_instance", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionInstanceCache cache = newInstance(rs);
					caches.put(cache.get_control_object_id(), cache);
				}
			}
		});
		m_caches = caches;
	}
	
	public static MJCompanionInstanceCache get(int control_object_id){
		return m_caches.get(control_object_id);
	}
	public static boolean is_companion_oblivion(int control_object_id){
		MJCompanionInstanceCache cache = get(control_object_id);
		return cache == null ? true : cache.get_is_oblivion();
	}
	
	public static MJCompanionInstanceCache update_cache(L1CompanionInstance instance, boolean is_summoned){
	    final int control_object_id = instance.get_control_object_id();
	    MJCompanionInstanceCache cache = get(control_object_id);
	    if(cache == null){
	        cache = newInstance();
	        m_caches.put(control_object_id, cache);
	    }
	    cache.set_object_id(instance.getId())
	            .set_is_dead(instance.isDead())
	            .set_is_oblivion(instance.isDead() && !is_summoned)
	            .set_is_summoned(is_summoned)
	            .set_is_oblivion(instance.get_is_oblivion())
	            .set_level(instance.getLevel())
	            .set_name(instance.getName())
	            .set_elixir_use_count(instance.get_elixir_use_count())
	            .set_name_changed_count(instance.get_name_changed_count());

	    LIN.SC_UPDATE_INVENTORY_NOTI.send_companion_control_item(instance.get_master(), control_object_id, cache);
	    return cache;
	}
	
	private static MJCompanionInstanceCache newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_object_id(rs.getInt("object_id"))
				.set_control_object_id(rs.getInt("control_object_id"))
				.set_is_dead(rs.getInt("current_hp") <= 0)
				.set_is_oblivion(rs.getInt("is_oblivion") != 0)
				.set_is_summoned(false)
				.set_level(rs.getInt("level"))
				.set_name(rs.getString("companion_name"))
				.set_elixir_use_count(rs.getInt("elixir_use_count"))
				.set_name_changed_count(rs.getInt("name_changed_count"));
	}
	
	private static MJCompanionInstanceCache newInstance(){
		return new MJCompanionInstanceCache();
	}
	
	private int m_object_id;
	private int m_control_object_id;
	private boolean m_is_oblivion;
	private boolean m_is_dead;
	private boolean m_is_summoned;
	private int m_level;
	private String m_name;
	private int m_elixir_use_count;
	private int m_name_changed_count;
	private MJCompanionInstanceCache(){}
	
	public MJCompanionInstanceCache set_object_id(int object_id){
		m_object_id = object_id;
		return this;
	}
	public MJCompanionInstanceCache set_control_object_id(int control_object_id){
		m_control_object_id = control_object_id;
		return this;
	}
	public MJCompanionInstanceCache set_is_oblivion(boolean is_oblivion){
		m_is_oblivion = is_oblivion;
		return this;
	}
	public MJCompanionInstanceCache set_is_dead(boolean is_dead){
		m_is_dead = is_dead;
		return this;
	}
	public MJCompanionInstanceCache set_is_summoned(boolean is_summoned){
		m_is_summoned = is_summoned;
		return this;
	}
	public MJCompanionInstanceCache set_level(int level){
		m_level = level;
		return this;
	}
	public MJCompanionInstanceCache set_name(String name){
		m_name = name;
		return this;
	}
	public MJCompanionInstanceCache set_elixir_use_count(int elixir_use_count){
		m_elixir_use_count = elixir_use_count;
		return this;
	} 
	public MJCompanionInstanceCache set_name_changed_count(int name_changed_count){
		m_name_changed_count = name_changed_count;
		return this;
	}
	public int get_object_id(){
		return m_object_id;
	}
	public int get_control_object_id(){
		return m_control_object_id;
	}
	public boolean get_is_oblivion(){
		return m_is_oblivion;
	}
	public boolean get_is_dead(){
		return m_is_dead;
	}
	public boolean get_is_summoned(){
		return m_is_summoned;
	}
	public int get_level(){
		return m_level;
	}
	public String get_name(){
		return m_name;
	}
	public int get_elixir_use_count(){
		return m_elixir_use_count;
	}
	public int get_name_changed_count(){
		return m_name_changed_count;
	}
}
