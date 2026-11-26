package l1j.server.server.model.BookQuest.Loader;

import java.sql.ResultSet;

import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.MonsterBookV2Info;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class BQSWQRewardsLoader {
	private static BQSWQRewardsLoader _instance;
	public static BQSWQRewardsLoader getInstance(){
		if(_instance == null)
			_instance = new BQSWQRewardsLoader();
		return _instance;
	}
	
	public static void reload(){
		BQSWQRewardsLoader old = _instance;
		_instance = new BQSWQRewardsLoader();
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
	
	private MonsterBookV2Info.SystemT _rewards;
	private BQSWQRewardsLoader(){
		_rewards = loadRewardInfo();
	}
	
	private MonsterBookV2Info.SystemT loadRewardInfo(){
		MonsterBookV2Info.SystemT sysT = MonsterBookV2Info.SystemT.newInstance();
		Selector.exec("select * from tb_mbook_wq_reward_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					sysT.add_rewards(MonsterBookV2Info.SystemT.RewardT.newInstance(rs));
			}
		});
		Selector.exec("select * from tb_mbook_wq_reward_items", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MonsterBookV2Info.SystemT.RewardT rewardT = sysT.get_rewards(rs.getInt("reward_grade"));
					rewardT.add_item_rewards(MonsterBookV2Info.SystemT.RewardT.ItemRewardT.newInstance(rs));
				}
			}
		});
		sysT.set_min_level(BQSLoadManager.BQS_WQ_MINLEVEL);
		return sysT;
	}
	
	public MonsterBookV2Info.SystemT get(){
		return _rewards;
	}
	
	public void dispose(){
		if(_rewards != null){
			_rewards.dispose();
			_rewards = null;
		}
	}
}
