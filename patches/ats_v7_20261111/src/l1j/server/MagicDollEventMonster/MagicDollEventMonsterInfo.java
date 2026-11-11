package l1j.server.MagicDollEventMonster;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MagicDollEventMonsterInfo {
	static MagicDollEventMonsterInfo newInstance(ResultSet rs) throws SQLException {
		MagicDollEventMonsterInfo pInfo = newInstance();
		pInfo._item_id = rs.getInt("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._map_ids = rs.getString("map_ids");
		pInfo._spawn_npc = rs.getInt("spawn_npc");
		pInfo._spawn_npc_name = rs.getString("spawn_npc_name");
		pInfo._spawn_probability = rs.getString("spawn_probability");
		pInfo._delete_time = rs.getInt("delete_time");
		pInfo._spawn_effect = rs.getInt("spawn_effect");
		pInfo._spawn_ment = rs.getString("spawn_ment");
		return pInfo;
	}
	
	private static MagicDollEventMonsterInfo newInstance() {
		return new MagicDollEventMonsterInfo();
	}
	
	private int _item_id;
	private String _item_name;
	private String _map_ids;
	private int _spawn_npc;
	private String _spawn_npc_name;
	private String _spawn_probability;
	private int _delete_time;
	private int _spawn_effect;
	private String _spawn_ment;

	public int get_item_id() {
		return _item_id;
	}
	
	public String get_item_name() {
		return _item_name;
	}
	
	public String get_map_ids() {
		return _map_ids;
	}

	public int get_spawn_npc() {
		return _spawn_npc;
	}
	
	public String get_spawn_npc_name() {
		return _spawn_npc_name;
	}
	
	public int get_delete_time() {
		return _delete_time;
	}
	
	public String get_spawn_probability() {
		return _spawn_probability;
	}
	
	public int get_spawn_effect() {
		return _spawn_effect;
	}
	
	public String get_spawn_ment() {
		return _spawn_ment;
	}
}
