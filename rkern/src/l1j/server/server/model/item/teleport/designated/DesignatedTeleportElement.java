package l1j.server.server.model.item.teleport.designated;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.geometry.GamePoint;
import l1j.server.server.utils.GameUtils;

public class DesignatedTeleportElement{
	public static DesignatedTeleportElement newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_item_id(rs.getInt("item_id"))
				.set_use_count(rs.getInt("use_count"))
				.set_x(rs.getInt("x"))
				.set_y(rs.getInt("y"))
				.set_map_id(rs.getInt("map_id"))
				.set_area(rs.getInt("area"))
				.set_validation(rs.getBoolean("validation"))
				.set_min_level(rs.getInt("min_level"))
				.set_max_level(rs.getInt("max_level"));
	}
	
	public static DesignatedTeleportElement newInstance(){
		return new DesignatedTeleportElement();
	}

	private int _item_id;
	private int _use_count;
	private int _x;
	private int _y;
	private int _map_id;
	private int _area;
	private boolean _validation;
	private int _min_level;
	private int _max_level;
	private DesignatedTeleportElement(){
	}

	public DesignatedTeleportElement set_item_id(int val){
		_item_id = val;
		return this;
	}
	public int get_item_id(){
		return _item_id;
	}

	public DesignatedTeleportElement set_use_count(int val){
		_use_count = val;
		return this;
	}
	public int get_use_count(){
		return _use_count;
	}

	public DesignatedTeleportElement set_x(int val){
		_x = val;
		return this;
	}
	public int get_x(){
		return _x;
	}

	public DesignatedTeleportElement set_y(int val){
		_y = val;
		return this;
	}
	public int get_y(){
		return _y;
	}

	public DesignatedTeleportElement set_map_id(int val){
		_map_id = val;
		return this;
	}
	public int get_map_id(){
		return _map_id;
	}

	public DesignatedTeleportElement set_area(int val){
		_area = val;
		return this;
	}
	public int get_area(){
		return _area;
	}

	public DesignatedTeleportElement set_validation(boolean val){
		_validation = val;
		return this;
	}
	public boolean get_validation(){
		return _validation;
	}

	public DesignatedTeleportElement set_min_level(int val){
		_min_level = val;
		return this;
	}
	public int get_min_level(){
		return _min_level;
	}

	public DesignatedTeleportElement set_max_level(int val){
		_max_level = val;
		return this;
	}
	public int get_max_level(){
		return _max_level;
	}
	
	private boolean is_valid(L1PcInstance pc){
		if(pc.isGm())
			return true;
			
		return pc.getMap().isEscapable() 
				&& !pc.isDead() 
				&& !GameUtils.isNonAction(pc) 
				&& !pc.get_teleport() 
				&& !pc.isGhost() 
				&& !pc.is_non_action()
				&& !pc.is_combat_field();
	}
	
	public void use(L1PcInstance pc, L1ItemInstance item){
		int lvl = pc.getLevel();
		if((_max_level > 0 && _max_level < lvl) || (_min_level > 0 && _min_level > lvl)){
			pc.sendPackets(String.format("%s 아이템은 %d레벨 부터 %d레벨 까지 사용할 수 있습니다.", item.getName(), _min_level, _max_level));
			return;
		}
		
		if(_validation && !is_valid(pc)){
			pc.sendPackets("사용할 수 없습니다.");
			return;
		}
		
		if(_use_count >0){
			int amount = 1;
			if(item.isStackable() && _use_count > 1)
				amount = _use_count;
			
			if(!pc.getInventory().DeleteItemInInventory(item, amount)){
				pc.sendPackets(String.format("%s 아이템은 %d개가 있어야 사용할 수 있습니다.", item.getName(), amount));
				return;
			}
		}
		
		GamePoint pt = GamePoint.newInstance(_x, _y, _area, (short)_map_id, 50);
		pc.start_teleport(pt.x, pt.y, pt.mapId, pc.getHeading(), 169, true, pc.getAutoHunt());
	}
}
