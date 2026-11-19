package l1j.server.MJRaidSystem.Creator;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Logger;

import l1j.server.MJRaidSystem.MJRaidMessage;
import l1j.server.MJRaidSystem.MJRaidType;
import l1j.server.MJRaidSystem.Loader.MJRaidTypesLoader;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** 차후 확장성과 복잡도 제거를 위해 RaidCreator를 정의한다. **/
/** 레이드 오픈에 대한 매개체가 되는 아이템을 전달받아 레이드 해당하는 레이드 시스템을 오픈한다. **/
public class MJRaidCreator {
	public static final String TB_NAME 	= "tb_mjraid_creator_items";
	protected static Logger	_log		= Logger.getLogger(MJRaidCreator.class.getName());
	
	private int _needItemId;	// 레이드를 오픈하기 위한 아이템 아이디
	private int _needCount;		// 레이드를 오픈하기 위해 필요한 아이템의 수량.
	private int _raidId;		// 오픈될 레이드 id.
	public MJRaidCreator(){
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
	
	public boolean setInformation(ResultSet rs){
		String column = null;
		try{
			column		= "needItemId";
			_needItemId = rs.getInt(column);
			
			column 		= "needCount";
			_needCount	= rs.getInt(column);
			
			column		= "raidId";
			_raidId		= rs.getInt(column);
		}catch(Exception e){
			StringBuilder sb = new StringBuilder();
			sb.append("MJRaidCreator at setInformation()...invalid column! ").append(column);
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void create(L1PcInstance pc, L1ItemInstance item){
		if(item.getCount() < _needCount){
			MJRaidMessage.RAID_OPEN_FAIL_INVALID_ITEM.sendMessage(pc);
			return;
		}
		
		MJRaidType type = MJRaidTypesLoader.getInstance().get(_raidId);
		if(type == null){
			MJRaidMessage.RAID_OPEN_FAIL_INVALID_TYPE.sendMessage(pc);
			return;
		}
		
		if(!pc.getInventory().checkItem(_needItemId, _needCount)){
			MJRaidMessage.RAID_OPEN_FAIL_INVALID_ITEM.sendMessage(pc);
			return;
		}
		pc.getInventory().removeItem(item, _needCount);
		type.create(pc);
		ArrayList<L1PcInstance> pcs = new ArrayList<L1PcInstance>();
		pcs.addAll(L1World.getInstance().getAllPlayers());
		MJRaidMessage.RAID_OPEN_SUCCESS_MESSAGE.sendMessage(pcs);
		pcs.clear();
	}
	
	
}
