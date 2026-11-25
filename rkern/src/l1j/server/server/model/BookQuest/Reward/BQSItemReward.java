package l1j.server.server.model.BookQuest.Reward;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1PcInstance;

public class BQSItemReward extends BQSAbstractReward{
	public static BQSItemReward newInstance(ResultSet rs) throws SQLException{
		return newInstance(rs.getInt("reward_grade"), rs.getInt("reward_item_id"), rs.getInt("reward_amount"));
	}
	
	public static BQSItemReward newInstance(int grade, int itemId, int amount){
		BQSItemReward reward = new BQSItemReward();
		reward.setGrade(grade);
		reward.setItemId(itemId);
		reward.setAmount(amount);
		return reward;
	}
	
	private int _itemId;
	private int _amount;
	private BQSItemReward(){
	}
	
	public int getItemId(){
		return _itemId;
	}
	
	public void setItemId(int itemId){
		_itemId = itemId;
	}
	
	public int getAmount(){
		return _amount;
	}
	
	public void setAmount(int amount){
		_amount = amount;
	}
	
	@Override
	public void doReward(L1PcInstance pc) {
		pc.getInventory().CreateOrAddItem(_itemId, _amount);
	}
}
