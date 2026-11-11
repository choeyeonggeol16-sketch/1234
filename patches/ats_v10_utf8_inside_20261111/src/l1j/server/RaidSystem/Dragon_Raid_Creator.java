package l1j.server.RaidSystem;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Logger;

import l1j.server.RaidSystem.Loader.Dragon_Raid_Types_Loader;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class Dragon_Raid_Creator {
	public static final String NAME 	= "raid_creator_items";
	protected static Logger	_log		= Logger.getLogger(Dragon_Raid_Creator.class.getName());
	
	private int _needItemId;	// 레이드를 오픈하기 위한 아이템 아이디
	private int _needCount;		// 레이드를 오픈하기 위해 필요한 아이템의 수량.
	private int _raidId;		// 오픈될 레이드 id.
	
	public Dragon_Raid_Creator() {
		_needItemId = -1;
		_needCount 	= -1;
		_raidId		= -1;
	}
	public int getNeedItemId(){
		return _needItemId;
	}
	public int getNeedCount(){
		return _needCount;
	}
	public int getRaidId(){
		return _raidId;
	}
	
	public boolean setInformation(ResultSet rs) {
		String column = null;
		try{
			column		= "needItemId";
			_needItemId = rs.getInt(column);
			
			column 		= "needCount";
			_needCount	= rs.getInt(column);
			
			column		= "raidId";
			_raidId		= rs.getInt(column);
		} catch(Exception e) {
			StringBuilder sb = new StringBuilder();
			sb.append("RaidCreator at setInformation()...invalid column! ").append(column);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void create(L1PcInstance pc, ItemInstance item) {
		if(item.getCount() < _needCount) {
			Dragon_Raid_Message.RAID_OPEN_FAIL_INVALID_ITEM.sendMessage(pc);
			return;
		}
		
		Dragon_Raid_Type type = Dragon_Raid_Types_Loader.getInstance().get(_raidId);
		if(type == null){
			Dragon_Raid_Message.RAID_OPEN_FAIL_INVALID_TYPE.sendMessage(pc);
			return;
		}
		
		if(!pc.getInventory().checkItem(_needItemId, _needCount)) {
			Dragon_Raid_Message.RAID_OPEN_FAIL_INVALID_ITEM.sendMessage(pc);
			return;
		}
		pc.getInventory().removeItem(item, _needCount);
		type.create(pc);
		ArrayList<L1PcInstance> pcs = new ArrayList<L1PcInstance>();
		pcs.addAll(L1World.getInstance().getAllPlayers());
		Dragon_Raid_Message.RAID_OPEN_SUCCESS_MESSAGE.sendMessage(pcs);
		pcs.clear();
	}
}
