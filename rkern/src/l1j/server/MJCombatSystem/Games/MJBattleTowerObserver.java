package l1j.server.MJCombatSystem.Games;

import java.util.HashMap;

import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJCombatSystem.MJCombatObserver;
import l1j.server.MJTemplate.MJEStatus;
import l1j.server.MJTemplate.Builder.MJTowerBuilder;
import l1j.server.MJTemplate.Interface.MJDamageListener;
import l1j.server.MJTemplate.Kda.UserKda;
import l1j.server.MJTemplate.L1Instance.MJL1TowerInstance;
import l1j.server.MJTemplate.Reward.AbstractReward;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

public class MJBattleTowerObserver extends MJCombatObserver{
	protected static final double TOWER_DAMAGE_RATE = 1.5D;
	protected static final int TOWER_KILL_POINT = 5;
	
	public static MJBattleTowerObserver newInstance(MJCombatInformation combat_info, AbstractReward[][] rewards){
		return (MJBattleTowerObserver) newInstance()
			.set_combat_info(combat_info)
			.set_rewards(rewards);
	}
	
	public static MJBattleTowerObserver newInstance(){
		return new MJBattleTowerObserver();
	}
	
	protected MJL1TowerInstance[] _towers;
	protected MJBattleTowerObserver(){
		super();
	}
	
	@Override
	protected MJCombatObserver initialize(){
		return ((MJBattleTowerObserver) super
				.initialize())
				.initialize_tower();
	}
	
	@Override
	protected void is_live_and_quit(int team_id){
		try{
			_lock.lock();
			MJL1TowerInstance tower = _towers[team_id];
			boolean is_live = is_live_team(team_id);
			if((tower == null || tower.isDead() || !is_live) && !is_disposing()){
				set_status(MJEStatus.DISPOSING);
				ThreadPool.getInstance().execute(new Runnable(){
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
			
			MJL1TowerInstance tower = _towers[deather_team_id];
			if(tower == null || tower.isDead())
				return;
			
		}finally{
			_lock.unlock();
		}
		if(deather_team_id >= 0)
			is_live_and_quit(deather_team_id);
	}
	
	protected MJBattleTowerObserver initialize_tower(){
		dispose_tower();
		MJBattleTowerInformation t_info = (MJBattleTowerInformation)_combat_info;
		HashMap<Integer, MJTowerBuilder> builders = t_info.get_t_builders();
		_towers = new MJL1TowerInstance[builders.size()];
		for(Integer team_id : builders.keySet()){
			MJTowerBuilder builder = builders.get(team_id);
			MJL1TowerInstance tower = builder.set_map_id(_copyMapId).build();
			_towers[team_id] = tower;
			
			tower.add_damage_listeners(new MJDamageListener<MJL1TowerInstance>(){
				@Override
				public boolean is_filter(L1Character attacker, MJL1TowerInstance receiver, int damage) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					return attacker_kda != null && attacker_kda.get_team_id() != team_id;
				}
				@Override
				public Object on_damage(L1Character attacker, MJL1TowerInstance receiver, int damage) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					
					int rate_to_damage = (int) (damage * TOWER_DAMAGE_RATE);
					attacker_kda.add_damage(rate_to_damage);
					try{
						_lock.lock();
						_team_kda[attacker_kda.get_team_id()].add_damage(rate_to_damage);
					}finally{
						_lock.unlock();
					}
					return MJBattleTowerObserver.this;
				}
				@Override
				public Object on_daed(L1Character attacker, MJL1TowerInstance deather) {
					UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
					attacker_kda.add_kill(TOWER_KILL_POINT);
					is_live_and_quit(team_id);
					return MJBattleTowerObserver.this;
				}
			});
		}
		return this;
	}
	
	protected void dispose_tower(){
		if(_towers != null){
			int size = _towers.length;
			for(int i=size - 1; i>=0; --i){
				MJL1TowerInstance tower = _towers[i];
				if(tower != null){
					tower.deleteMe();
					_towers[i] = null;
				}
			}
			_towers = null;
		}
	}
	
	@Override
	public void dispose(){
		super.dispose();
		dispose_tower();
	}
}
