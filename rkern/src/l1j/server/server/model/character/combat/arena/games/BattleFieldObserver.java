package l1j.server.server.model.character.combat.arena.games;

import l1j.server.server.model.character.combat.arena.CombatInformation;
import l1j.server.server.model.character.combat.arena.CombatObserver;
import l1j.server.server.model.item.reward.AbstractReward;

public class BattleFieldObserver extends CombatObserver{
	public static BattleFieldObserver newInstance(CombatInformation combat_info, AbstractReward[][] rewards){
		return (BattleFieldObserver) newInstance()
			.set_combat_info(combat_info)
			.set_rewards(rewards);
	}
	
	public static BattleFieldObserver newInstance(){
		return new BattleFieldObserver();
	}
	
	protected BattleFieldObserver(){
		super();
	}
}
