package l1j.server.MJCombatSystem.Games;

import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJCombatSystem.MJCombatObserver;
import l1j.server.MJTemplate.Reward.AbstractReward;

public class MJBattleFieldObserver extends MJCombatObserver{
	public static MJBattleFieldObserver newInstance(MJCombatInformation combat_info, AbstractReward[][] rewards){
		return (MJBattleFieldObserver) newInstance()
			.set_combat_info(combat_info)
			.set_rewards(rewards);
	}
	
	public static MJBattleFieldObserver newInstance(){
		return new MJBattleFieldObserver();
	}
	
	protected MJBattleFieldObserver(){
		super();
	}
}
