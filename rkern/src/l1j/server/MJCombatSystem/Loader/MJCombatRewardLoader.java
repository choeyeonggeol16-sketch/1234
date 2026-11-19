package l1j.server.MJCombatSystem.Loader;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;

import l1j.server.MJCombatSystem.MJCombatEGameType;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.MJTemplate.Reward.AbstractReward;

public class MJCombatRewardLoader{
	private static final String[] REWARD_KIND = new String[]{
		"WINNER_KEEP", "WINNER_DEAD", "LOSER_KEEP", "LOSER_DEAD"
	};
	
	private static MJCombatRewardLoader _instance;
	public static MJCombatRewardLoader getInstance(){
		if(_instance == null)
			_instance = new MJCombatRewardLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJCombatRewardLoader old = _instance;
		_instance = new MJCombatRewardLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, AbstractReward[][]> _rewards;
	private MJCombatRewardLoader(){
		_rewards = load();
	}
	
	private HashMap<Integer, AbstractReward[][]> load(){
		MJCombatEGameType[] g_types = MJCombatEGameType.values();
		int types_size = g_types.length;
		HashMap<Integer, AbstractReward[][]> rewards = new HashMap<Integer, AbstractReward[][]>(types_size);

		for(int i = types_size - 1; i>=0; --i){
			if(g_types[i].equals(MJCombatEGameType.NONE))
				continue;
			
			final int type_index = i;
			AbstractReward[][] reward_2d = new AbstractReward[REWARD_KIND.length][];
			rewards.put(g_types[type_index].to_int(), reward_2d);
			
			for(int k=REWARD_KIND.length - 1; k>=0; --k){
				final int kind_index = k;
				Selector.exec("select * from tb_combat_rewards where combat_type=? and kind=?", new SelectorHandler(){
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						pstm.setString(1, g_types[type_index].to_kr_desc());
						pstm.setString(2, REWARD_KIND[kind_index]);
					}
					@Override
					public void result(ResultSet rs) throws Exception {
						LinkedList<AbstractReward> list = new LinkedList<AbstractReward>();
						while(rs.next())
							list.add(AbstractReward.newInstance(rs));
						
						int size = list.size();
						reward_2d[kind_index] = size > 0 ? list.toArray(new AbstractReward[size]) : null;
					}
				});
				
			}
		}
		return rewards;
	}

	public AbstractReward[][] get(MJCombatEGameType g_type){
		return _rewards.get(g_type.to_int());
	}
	
	public void dispose(){
		if(_rewards != null){
			_rewards.clear();
			_rewards = null;
		}
	}
}
