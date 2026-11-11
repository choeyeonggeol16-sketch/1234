package l1j.server.DTSSystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class IODTSElement {
	private int _area;
	private int _item_id;
	private int _map_id;
	private int _max_level;
	private int _min_level;
	private int _use_count;
	private boolean _validation;
	private boolean _pc_need_wanted; 
	private int _x;
	private int _y;
	private String _ment;
	private int _check_item_id;
	private int _check_item_count;

	public static IODTSElement newInstance(ResultSet rs) throws SQLException {
		return newInstance().set_item_id(rs.getInt("item_id"))
				.set_use_count(rs.getInt("use_count"))
				.set_x(rs.getInt("x"))
				.set_y(rs.getInt("y"))
				.set_map_id(rs.getInt("map_id"))
				.set_area(rs.getInt("area"))
				.set_validation(rs.getBoolean("validation"))
				.set_pc_need_wanted(rs.getBoolean("pc_need_wanted"))
				.set_min_level(rs.getInt("min_level"))
				.set_max_level(rs.getInt("max_level"))
				.set_ment(rs.getString("ment"))
				.set_check_item_id(rs.getInt("check_item_id"))
				.set_check_item_count(rs.getInt("check_item_count"));
	}

	public static IODTSElement newInstance() {
		return new IODTSElement();
	}

	private IODTSElement() {
	}

	public IODTSElement set_item_id(int val) {
		_item_id = val;
		return this;
	}

	public int get_item_id() {
		return _item_id;
	}

	public IODTSElement set_use_count(int val) {
		_use_count = val;
		return this;
	}

	public int get_use_count() {
		return _use_count;
	}

	public IODTSElement set_x(int val) {
		_x = val;
		return this;
	}

	public int get_x() {
		return _x;
	}

	public IODTSElement set_y(int val) {
		_y = val;
		return this;
	}

	public int get_y() {
		return _y;
	}

	public IODTSElement set_map_id(int val) {
		_map_id = val;
		return this;
	}

	public int get_map_id() {
		return _map_id;
	}

	public IODTSElement set_area(int val) {
		_area = val;
		return this;
	}

	public int get_area() {
		return _area;
	}

	public IODTSElement set_validation(boolean val) {
		_validation = val;
		return this;
	}

	public boolean get_validation() {
		return _validation;
	}
	
	public IODTSElement set_pc_need_wanted(boolean val) {
		_pc_need_wanted = val;
		return this;
	}

	public boolean get_pc_need_wanted() {
		return _pc_need_wanted;
	}

	public IODTSElement set_min_level(int val) {
		_min_level = val;
		return this;
	}

	public int get_min_level() {
		return _min_level;
	}

	public IODTSElement set_max_level(int val) {
		_max_level = val;
		return this;
	}
	public IODTSElement set_ment(String success_format_string){
		_ment = success_format_string;
		return this;
	}
	public String get_success_format_string(){
		return _ment;
	}

	public int get_max_level() {
		return _max_level;
	}
	
	
	public IODTSElement set_check_item_id(int val) {
		_check_item_id = val;
		return this;
	}

	public int get_check_item_id() {
		return _check_item_id;
	}
	
	public IODTSElement set_check_item_count(int val) {
		_check_item_count = val;
		return this;
	}

	public int get_check_item_count() {
		return _check_item_id;
	}

	private boolean is_valid(L1PcInstance pc) {
		if (pc.isGm()) {
			return true;
		}
		if (!pc.getMap().isEscapable() || pc.isDead() || pc.getTeleport() || pc.isGhost()) { 
			return false;
		}
		return true;
	}

	public void use(L1PcInstance pc, ItemInstance item) {
		long curtime = System.currentTimeMillis() / 1000;
		if (pc.getQuizTime3() + 1 > curtime) {  
			return;
		}
		int lvl = pc.getLevel();
  
		if (!_validation) {
			String success_message = String.format(get_success_format_string());
			pc.sendPackets(success_message);
			return;
		
		} else if (_pc_need_wanted && pc.getHuntCount() == 0) {
			pc.sendPackets("\\fY경고 : 수배 상태에서만 사용할수 있습니다."); 
			return;
			
		
		} else if ((_max_level > 0 && _max_level < lvl) || (_min_level > 0 && _min_level > lvl)) {
			pc.sendPackets(String.format("%s 아이템은 %d레벨 부터 %d레벨 까지 사용할 수 있습니다.", new Object[] { item.getName(), _min_level, _max_level }));
		} else if (is_valid(pc)) {
			if (_use_count > 0) {
				int amount = 1;
				if (item.isStackable() && _use_count > 1) {
					amount = _use_count;
				}
				if (!pc.getInventory().consumeItem(item, amount)) {
					pc.sendPackets(String.format("%s 아이템은 %d개가 있어야 사용할 수 있습니다.",new Object[] { item.getName(), amount }));
					return;
				}
			}
			
			if (_check_item_id > 0 && _check_item_count == 0) { 
				L1Item it = ItemTable.getInstance().getTemplate(_check_item_id);
				if (!pc.getInventory().checkItem(_check_item_id)){
					pc.sendPackets(new S_SystemMessage("입장아이템 : " + it.getName() + "가(이) 부족합니다."));
					return;
				}  

			} else if (_check_item_id > 0 && _check_item_count > 0) {

				if (!pc.getInventory().consumeItem(_check_item_id, _check_item_count)) {
					L1Item it = ItemTable.getInstance().getTemplate(_check_item_id);
					if (it != null) {
						pc.sendPackets(new S_SystemMessage("입장아이템 : " + it.getName() + "(" + _check_item_count + ")가(이) 부족합니다."));
						return;
					} else {
						pc.sendPackets(new S_SystemMessage("잘못된 아이템 지정입니다. 운영자에게 문의하세요."));
						return;
					}
				}

			}

			MJPoint pt = MJPoint.newInstance(_x, _y, _area, (short) _map_id, 50);  
			pc.teleport(pt.x, pt.y, (short) pt.mapId, pc.getHeading(), 169, true, false);  
			pc.setQuizTime3(curtime);
		} else {
			pc.sendPackets("현재 사용할 수 없습니다.");
		}
	}
}