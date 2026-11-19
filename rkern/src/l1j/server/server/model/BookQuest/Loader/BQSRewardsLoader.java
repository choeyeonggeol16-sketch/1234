package l1j.server.server.model.BookQuest.Loader;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.BookQuest.Reward.BQSAbstractReward;
import l1j.server.server.model.BookQuest.Reward.BQSExpReward;
import l1j.server.server.model.BookQuest.Reward.BQSItemReward;
import l1j.server.server.model.Instance.L1PcInstance;

public class BQSRewardsLoader {
	private static BQSRewardsLoader _instance;
	public static BQSRewardsLoader getInstance(){
		if(_instance == null)
			_instance = new BQSRewardsLoader();
		return _instance;
	}
	
	public static void reload(){
		BQSRewardsLoader old = _instance;
		_instance = new BQSRewardsLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}
	
	private HashMap<Integer, ArrayList<BQSAbstractReward>> _rewards;
	private BQSRewardsLoader(){
		loadRewards();
	}
	
	private HashMap<Integer, ArrayList<BQSAbstractReward>> createEmptyReward(){
		HashMap<Integer, ArrayList<BQSAbstractReward>> rewards = new HashMap<Integer, ArrayList<BQSAbstractReward>>(3);
		for(int i=3; i>=1; --i)
			rewards.put(i, new ArrayList<BQSAbstractReward>(4));
		return rewards;
	}
	
	private void loadRewards(){
		_rewards = createEmptyReward();
		Selector.exec("select * from tb_mbook_reward_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					BQSExpReward reward = BQSExpReward.newInstance(rs);
					_rewards.get(reward.getGrade()).add(reward);
				}
			}
		});
		
		Selector.exec("select * from tb_mbook_reward_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					BQSItemReward reward = BQSItemReward.newInstance(rs);
					_rewards.get(reward.getGrade()).add(reward);
				}
			}
		});
	}
	
	public void doReward(L1PcInstance pc, int grade){
		for(BQSAbstractReward reward : _rewards.get(grade))
			reward.doReward(pc);
	}
	
	
	public void dispose(){
		if(_rewards != null){
			for(ArrayList<BQSAbstractReward> list : _rewards.values())
				list.clear();
			_rewards.clear();
			_rewards = null;
		}
	}
}
