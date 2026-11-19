package l1j.server.MJTemplate.Trap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import l1j.server.server.model.Instance.L1PcInstance;

public abstract class AbstractTrap {
	public static final int TRAP_TYPE_DISPLAY_EFFECT = 0;
	
	public static int parse_trap_type(String s){
		switch(s){
		case "DISPLAY_EFFECT":
			return TRAP_TYPE_DISPLAY_EFFECT;
		default:
			throw new IllegalArgumentException(String.format("invalid trap type. %s", s));
		}
	}
	
	public static AbstractTrap newInstance(ResultSet rs) throws SQLException{
		int type = parse_trap_type(rs.getString("trap_type"));
		AbstractTrap trap = null;
		switch(type){
		case TRAP_TYPE_DISPLAY_EFFECT:
			trap = DisplayEffectTrap.newInstance();
			break;
		default:
			break;
		}
		
		if(trap != null){
			trap
			.set_trap_id(rs.getInt("trap_id"))
			.set_asset_id(rs.getInt("trap_asset_id"));
		}
		return trap;
	}
	
	private int _trap_id;
	private int _asset_id;
	public AbstractTrap set_trap_id(int trap_id){
		_trap_id = trap_id;
		return this;
	}
	
	public int get_trap_id(){
		return _trap_id;
	}
	
	public AbstractTrap set_asset_id(int asset_id){
		_asset_id = asset_id;
		return this;
	}
	
	public int get_asset_id(){
		return _asset_id;
	}
	
	public abstract void do_trap(L1PcInstance pc);
	public abstract void do_traps(L1PcInstance[] pc_array);
	public abstract void do_traps(Collection<L1PcInstance> pc_collections);
	
}
