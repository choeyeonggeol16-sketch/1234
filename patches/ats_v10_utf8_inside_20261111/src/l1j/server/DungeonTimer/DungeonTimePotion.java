package l1j.server.DungeonTimer;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.DungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class DungeonTimePotion{
	public static DungeonTimePotion newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_item_id(rs.getInt("item_id"))
				.set_timer_id(rs.getInt("timer_id"));
	}
	
	public static DungeonTimePotion newInstance(){
		return new DungeonTimePotion();
	}

	private int _item_id;
	private int _timer_id;
	private DungeonTimePotion(){
	}

	public DungeonTimePotion set_item_id(int val){
		_item_id = val;
		return this;
	}
	public int get_item_id(){
		return _item_id;
	}

	public DungeonTimePotion set_timer_id(int val){
		_timer_id = val;
		return this;
	}
	public int get_timer_id(){
		return _timer_id;
	}
	
	public void use_potion(L1PcInstance pc, ItemInstance item){
		
		DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(get_timer_id());
		
		if(dtInfo == null){
			pc.sendPackets("운영자에 의해 사용 중지된 아이템입니다.");
			return;
		}
		DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
		if(progress == null || progress.get_remain_seconds() > 0){  
			pc.sendPackets("아직 던전 이용시간이 남아있습니다.");
			return;
		} 
		pc.getInventory().removeItem(item, 1);
		progress.set_remain_seconds(dtInfo.get_amount_seconds());
		DungeonTimeProgressLoader.update(progress);
		pc.sendPackets(String.format("%s이(가) 충전되었습니다.", dtInfo.get_description()));
	}
}