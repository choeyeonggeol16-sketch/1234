package l1j.server.MJTemplate.Reward;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1PcInstance;

public abstract class AbstractReward {
	public static final int REWARD_TYPE_EXP = 0;
	public static final int REWARD_TYPE_ITEM = 1;
	
	public static int parse_reward_type(String s){
		switch(s){
		case "EXP":
			return REWARD_TYPE_EXP;
		case "ITEM":
			return REWARD_TYPE_ITEM;
		default:
			throw new IllegalArgumentException(String.format("invalid reward type. %s", s));
		}
	}
	
	public static AbstractReward newInstance(ResultSet rs) throws SQLException{
		int type = parse_reward_type(rs.getString("reward_type"));
		AbstractReward reward = null;
		switch(type){
		case REWARD_TYPE_EXP:
			reward = ExpReward.newInstance();
			break;
		case REWARD_TYPE_ITEM:
			reward = ItemReward.newInstance();
			break;
		default:
			break;
		}
		
		if(reward != null){
			reward
			.set_reward_id(rs.getInt("reward_id"))
			.set_reward_asset_id(rs.getInt("reward_asset_id"))
			.set_reward_amount(rs.getInt("reward_amount"));
		}
		return reward;
	}
	
	private int _reward_id;
	private int _reward_asset_id;
	private int _reward_amount;
	public int get_reward_id(){
		return _reward_id;
	}
	
	public AbstractReward set_reward_id(int reward_id){
		_reward_id = reward_id;
		return this;
	}
	
	public int get_reward_asset_id(){
		return _reward_asset_id;
	}
	
	public AbstractReward set_reward_asset_id(int reward_asset_id){
		_reward_asset_id = reward_asset_id;
		return this;
	}
	
	public int get_reward_amount(){
		return _reward_amount;
	}
	
	public AbstractReward set_reward_amount(int reward_amount){
		_reward_amount = reward_amount;
		return this;
	}
	
	public abstract void do_reward(L1PcInstance pc);
}
