package l1j.server.server.model.character.combat.arena.games;

import java.util.HashMap;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.combat.arena.CombatInformation;
import l1j.server.server.model.character.combat.arena.CombatObserver;
import l1j.server.server.model.character.combat.statistics.shared.UserKda;
import l1j.server.server.model.item.reward.AbstractReward;
import l1j.server.server.model.world.instance.shared.GameL1TowerInstance;
import l1j.server.server.shared.EStatus;
import l1j.server.server.shared.builder.TowerBuilder;
import l1j.server.server.shared.listener.DamageListener;

public class BattleTowerObserver extends CombatObserver{
	protected static final double TOWER_DAMAGE_RATE = 1.5D;
	protected static final int TOWER_KILL_POINT = 5;
	
	public static BattleTowerObserver newInstance(CombatInformation combat_info, AbstractReward[][] rewards){
		return (BattleTowerObserver) newInstance()
			.set_combat_info(combat_info)
			.set_rewards(rewards);
	}
	
	public static BattleTowerObserver newInstance(){
		return new BattleTowerObserver();
	}
	
	protected GameL1TowerInstance[] _towers;
	protected BattleTowerObserver(){
		super();
	}
	
	@Override
	protected CombatObserver initialize(){
		return ((BattleTowerObserver) super
				.initialize())
				.initialize_tower();
	}
	
	@Override
	protected void is_live_and_quit(int team_id){
		try{
			_lock.lock();
			GameL1TowerInstance tower = _towers[team_id];
			boolean is_live = is_live_team(team_id);
			if((tower == null || tower.isDead() || !is_live) && !is_disposing()){
				set_status(EStatus.DISPOSING);
				CInstanceWork.execute(new Runnable(){
					@Override
					public void run(){
						batch_quit();					
					}
				});
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			_lock.unlock();
		}
	}
	
	public void on_kill(L1PcInstance attacker, L1PcInstance receiver){
		if(attacker == null || !attacker.is_combat_field() || receiver == null || !receiver.is_combat_field())
			return;
		
		int deather_team_id = -1;
		try{
			_lock.lock();
			UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
			UserKda receiver_kda = _user_kda_dictionary.get(receiver.getId());
			if(attacker_kda == null || receiver_kda == null)
				return;
			
			int attacker_team_id = attacker_kda.get_team_id();
			_team_kda[attacker_team_id].inc_kill();
			attacker_kda.inc_kill();
			deather_team_id = receiver_kda.get_team_id();
			receiver_kda.inc_death();
			_team_kda[deather_team_id].inc_death();
			
			GameL1TowerInstance tower = _towers[deather_team_id];
			if(tower == null || tower.isDead())
				return;
			
		}finally{
			_lock.unlock();
		}
		if(deather_team_id >= 0)
			is_live_and_quit(deather_team_id);
	}
	
	protected BattleTowerObserver initialize_tower(){
		dispose_tower();
		BattleTowerInformation t_info = (BattleTowerInformation)_combat_info;
		HashMap<Integer, TowerBuilder> builders = t_info.get_t_builders();
		_towers = new GameL1TowerInstance[builders.size()];
		for(Integer team_id : builders.keySet()){
			TowerBuilder builder = builders.get(team_id);
			GameL1TowerInstance tower = builder.set_map_id(_copyMapId).build();
			_towers[team_id] = tower;
			
			tower.add_damage_listeners(new DamageListener<GameL1TowerInstance>(){
				@Override
				public boolean is_filter(L1Character attacker, GameL1TowerInstance receiver, int damage) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					return attacker_kda != null && attacker_kda.get_team_id() != team_id;
				}
				@Override
				public Object on_damage(L1Character attacker, GameL1TowerInstance receiver, int damage) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					
					int rate_to_damage = (int) (damage * TOWER_DAMAGE_RATE);
					attacker_kda.add_damage(rate_to_damage);
					try{
						_lock.lock();
						_team_kda[attacker_kda.get_team_id()].add_damage(rate_to_damage);
					}finally{
						_lock.unlock();
					}
					return BattleTowerObserver.this;
				}
				@Override
				public Object on_daed(L1Character attacker, GameL1TowerInstance deather) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					attacker_kda.add_kill(TOWER_KILL_POINT);
					is_live_and_quit(team_id);
					return BattleTowerObserver.this;
				}
			});
		}
		return this;
	}
	
	protected void dispose_tower(){
		if(_towers != null){
			int size = _towers.length;
			for(int i=size - 1; i>=0; --i){
				GameL1TowerInstance tower = _towers[i];
				if(tower != null){
					tower.deleteMe();
					_towers[i] = null;
				}
			}
			_towers = null;
		}
	}
	
	@Override
	protected void disposeEncounterObjects(){
		dispose_tower();
	}
}
