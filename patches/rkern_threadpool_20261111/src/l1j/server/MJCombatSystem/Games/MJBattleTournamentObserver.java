package l1j.server.MJCombatSystem.Games;

import java.util.ArrayList;
import java.util.Collections;

import l1j.server.MJCombatSystem.MJCombatInformation;
import l1j.server.MJCombatSystem.MJCombatObserver;
import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJTemplate.MJEPcStatus;
import l1j.server.MJTemplate.MJEStatus;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.Builder.MJDropItemBuilder;
import l1j.server.MJTemplate.Builder.MJLiftGateBuilder;
import l1j.server.MJTemplate.Builder.MJMonsterSpawnBuilder;
import l1j.server.MJTemplate.Builder.MJTreasureChestBuilder;
import l1j.server.MJTemplate.Interface.MJIGenericListener;
import l1j.server.MJTemplate.Kda.UserKda;
import l1j.server.MJTemplate.Lineage2D.MJPoint;
import l1j.server.MJTemplate.Lineage2D.MJRectangle;
import l1j.server.MJTemplate.MapCleaner.MJMapCleaner;
import l1j.server.MJTemplate.MapCleaner.MJMapCleanerFilterFactory;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.MJTemplate.PacketHelper.MJPacketFactory;
import l1j.server.MJTemplate.Reward.AbstractReward;
import l1j.server.MJTemplate.TreasureChest.MJL1TreasureChest;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestAttackFilter;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestCompensator;
import l1j.server.MJTemplate.TreasureChest.MJTreasureChestOpenFilter;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class MJBattleTournamentObserver extends MJCombatObserver{
	private static final int[][][] _chestPos = new int[][][]{
		{{32852, 32823}, {32852, 32825}, {32852, 32827},},
		{{32852, 32834}, {32852, 32836}, {32852, 32838},},
	};
	
	private static final int[][] _shopPos = new int[][]{
		{32727, 32839, 4},
		{32728, 32823, 4}
	};
	
	public static MJBattleTournamentObserver newInstance(MJCombatInformation combat_info, AbstractReward[][] rewards){
		return (MJBattleTournamentObserver) newInstance()
				.set_combat_info(combat_info)
				.set_rewards(rewards);
	}
	
	public static MJBattleTournamentObserver newInstance(){
		return new MJBattleTournamentObserver();
	}
	
	private MJBattleTournamentArea[][] _areas;
	private MJDropItemBuilder[][] _keys;
	private boolean _is_last_level;
	private int[] _key_ids;
	protected MJBattleTournamentObserver(){
		super();
		_is_last_level = false;
	}
	
	public MJBattleTournamentObserver set_is_last_level(boolean val){
		_is_last_level = val;
		return this;
	}
	public boolean get_is_last_level(){
		return _is_last_level;
	}
	
	@Override
	public MJCombatObserver execute_combat(){
		if(is_closed()){
			initialize_area();
			_status = MJEStatus.READY;
			long ready_seconds = _combat_info.get_ready_seconds();
			
			_start_sec = (System.currentTimeMillis() / 1000L) + ready_seconds;
			ThreadPool.getInstance().scheduleRequired(this, ready_seconds * 1000L);
		}
		return this;
	}
	
	@Override
	protected MJCombatObserver initialize_box(){
		return this;
	}
	
	@Override
	protected MJCombatObserver initialize_team_box(){
		return this;
	}
	
	protected MJCombatObserver initialize_chest(){
		MJBattleTournamentInformation t_info = get_tournament_info();
		MJTreasureChestBuilder[] c_builders = t_info.get_chest_builders();
		_keys = new MJDropItemBuilder[2][3];
		
		for(int team_id = 1; team_id>=0; --team_id){
			for(int i = 2; i>=0; --i){
				int grade = MJRnd.next(c_builders.length - 1);
				_keys[team_id][i] = new MJDropItemBuilder()
						.setItemId(buildChest(c_builders[grade], t_info.get_chest_item_builders(), grade, team_id, _chestPos[team_id][i][0], _chestPos[team_id][i][1]))
						.setMinCount(1)
						.setMaxCount(1)
						.setDice(-1);
			}
		}
		return this;
	}
	
	private int buildChest(MJTreasureChestBuilder builder, ArrayList<MJDropItemBuilder> chest_rewards, int grade, int team_id, int x, int y){
		builder
			.setOpenFilter(createChestOpenFilter(team_id, builder.getKey()))
			.setAttackFilter(createChestAttackFilter(team_id))
			.setCompensator(createChestCompensator(grade, chest_rewards))
			.build(x, y, 6, _copyMapId);
		
		return builder.getKey();
	}
	
	private MJTreasureChestOpenFilter createChestOpenFilter(int team_id, int key_item_id){
		if(team_id != -1){
			return new MJTreasureChestOpenFilter(){
				@Override
				public boolean isFilter(L1PcInstance pc){
					if(pc != null && _user_kda_dictionary != null){
						UserKda kda = _user_kda_dictionary.get(pc.getId());
						return !(kda != null && kda.get_team_id() == team_id && pc.getInventory().consumeItem(key_item_id, 1));
					}else{
						return true;
					}
				}
			};
		}else{
			return new MJTreasureChestOpenFilter(){
				@Override
				public boolean isFilter(L1PcInstance pc){
					return !pc.getInventory().consumeItem(key_item_id, 1);
				}
			};
		}
	}
	
	private MJTreasureChestCompensator createChestCompensator(int grade, ArrayList<MJDropItemBuilder> chest_rewards){
		return new MJTreasureChestCompensator(){
			@Override
			public void compensate(MJL1TreasureChest chest){
				ArrayList<MJDropItemBuilder> list = new ArrayList<MJDropItemBuilder>(chest_rewards);
				Collections.shuffle(list);
				int count 		= (grade + 1) * MJCombatLoadManager.COMBAT_BT_CHEST_DROPITEM_AMOUNT;
				int size		= list.size();
				for(int i = count - 1; i>=0; --i){
					MJDropItemBuilder builder = list.get(i % size);
					if(builder.isWinning(grade))
						builder.build(chest, MJCombatLoadManager.COMBAT_BT_CHEST_DROP_RANGE);
				}
				
				list.clear();
				list = null;
			}
		};
	}
	
	private MJTreasureChestAttackFilter createChestAttackFilter(int team_id){
		if(team_id != -1){
			return new MJTreasureChestAttackFilter(){
				@Override
				public boolean isFilter(L1PcInstance pc){
					UserKda kda = _user_kda_dictionary.get(pc.getId());
					return !(kda != null && kda.get_team_id() != team_id);
				}
			};
		}else{
			return new MJTreasureChestAttackFilter(){
				@Override
				public boolean isFilter(L1PcInstance pc){
					return true;
				}
			};
		}
	}
	
	protected void spawn_shop(int npc_id, int x, int y, int h){
		try{
			L1NpcInstance npc = NpcTable.getInstance(). newNpcInstance(npc_id);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(_copyMapId);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(x);
			npc.setHomeY(y);
			npc.setHeading(h);
			L1World.getInstance().storeAndEnterWorld(npc);
			npc.getLight().turnOnOffLight();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	protected MJCombatObserver initialize_shop(){
		try{
			for(int[] pos : _shopPos)
				spawn_shop(MJCombatLoadManager.COMBAT_BT_CHANDLERY_SHOP_ID, pos[0], pos[1], pos[2]);
		}catch(Exception e){
			e.printStackTrace();
		}
		return this;
	}
	
	protected MJCombatObserver initialize_area(){
		initialize_chest();
		initialize_shop();
		
		L1Map m = L1WorldMap.getInstance().getMap(_copyMapId);
		m.set_isUseResurrection(true);
		
		MJBattleTournamentInformation t_info = get_tournament_info();
		_key_ids = t_info.get_keys();
		_areas = new MJBattleTournamentArea[2][5];
		MJMonsterSpawnBuilder[] boss_builders = t_info.get_boss_builders();
		MJRectangle[][] area_2d = t_info.get_rt_tournament_areas();
		MJLiftGateBuilder lbuilder = new MJLiftGateBuilder().setGfx(MJCombatLoadManager.COMBAT_BT_LIFT_GFXID);
		MJPoint[][] lift_2d = t_info.get_pt_lift_gates();
		MJMonsterSpawnBuilder[] mbuilders = t_info.get_monster_builders();
		ArrayList<MJDropItemBuilder> dbuilders = t_info.get_drop_builders();
		for(int team_id = 1; team_id>=0; --team_id){
			for(int round_id = 4; round_id>=0; --round_id){	
				_areas[team_id][round_id] = MJBattleTournamentArea.newInstance()
				.set_team_id(team_id)
				.set_round_id(round_id)
				.set_area(area_2d[team_id][round_id])
				.set_key_builder((round_id == 0 || round_id == 4) ? null : _keys[team_id][round_id - 1])
				.set_lift(lbuilder.build(lift_2d[team_id][round_id].x, lift_2d[team_id][round_id].y, _copyMapId))
				.set_sbuilder(round_id == 4 ? null : mbuilders[round_id])
				.set_drop_builders(new ArrayList<MJDropItemBuilder>(dbuilders))
				.set_boss_sbuilder(boss_builders[round_id])
				.set_map_id(_copyMapId)
				.set_clear_listener(new MJIGenericListener<int[]>(){
					@Override
					public int[] on(int[] t) {
						int team_id = t[0];
						int round_id = t[1];
						_areas[team_id][round_id].do_complete();
						if(round_id == 3){
							if(get_is_last_level()){
								_areas[team_id][round_id + 1].do_complete();
							}else{
								set_is_last_level(true);
								_areas[team_id][round_id + 1].do_complete();
								ThreadPool.getInstance().scheduleRequired(new Runnable(){
									@Override
									public void run(){
										_areas[team_id][round_id + 1].do_boss_spawn();										
									}
								}, MJCombatLoadManager.COMBAT_BT_STAGE_PASS_SECOND * 1000L);
							}
							return t;
						}else if(round_id == 4){
							MJBattleTournamentInformation t_info = get_tournament_info();
							MJRectangle rt = t_info.get_rt_tournament_areas()[team_id][round_id];
							MJTreasureChestBuilder[] c_builders = t_info.get_chest_builders();
							new MJDropItemBuilder()
							.setItemId(buildChest(c_builders[c_builders.length - 1], t_info.get_chest_item_builders(), 4, -1, rt.x, rt.y))
							.setMinCount(1)
							.setMaxCount(1)
							.setDice(-1)
							.build(rt.x, rt.y, _copyMapId, -1);
							ThreadPool.getInstance().scheduleRequired(new Runnable(){
								@Override
								public void run(){
									set_status(MJEStatus.DISPOSING);
									batch_quit();									
								}
							}, MJCombatLoadManager.COMBAT_BT_MASTER_DEATH_DELAY * 1000L);
							return t;
						}
						_areas[team_id][round_id].do_complete();
						_areas[team_id][round_id + 1].do_ready();
						ThreadPool.getInstance().execute(new Runnable(){
							@Override
							public void run(){
								try{
									L1World.getInstance().broadcast_map(_copyMapId, String.format("%d초 후 문이 닫힙니다. 빠르게 다음 스테이지로 이동해주세요.", MJCombatLoadManager.COMBAT_BT_STAGE_PASS_SECOND));
									Thread.sleep(MJCombatLoadManager.COMBAT_BT_STAGE_PASS_SECOND * 1000L);
								}catch(Exception e){
									e.printStackTrace();
								}finally{
									_areas[team_id][round_id + 1].do_running();
								}
							}
						});
						return t;
					}
				});
			}
		}
		return this;
	}
	
	protected void dispose_area(){
		for(int team_id = 1; team_id>=0; --team_id){
			for(int round_id = 4; round_id>=0; --round_id){
				if(_areas[team_id][round_id] != null){
					_areas[team_id][round_id].dispose();
					_areas[team_id][round_id] = null;
				}
			}
		}
	}
	
	public MJBattleTournamentInformation get_tournament_info(){
		return (MJBattleTournamentInformation)_combat_info;
	}
	
	@Override
	public void on_kill(L1PcInstance attacker, L1PcInstance receiver){
		if(attacker == null || !attacker.is_combat_field() || receiver == null || !receiver.is_combat_field())
			return;
		
		try{
			_lock.lock();
			UserKda attacker_kda = _user_kda_dictionary.get(attacker.getId());
			UserKda receiver_kda = _user_kda_dictionary.get(receiver.getId());
			if(attacker_kda == null || receiver_kda == null)
				return;
			
			int attacker_team_id = attacker_kda.get_team_id();
			_team_kda[attacker_team_id].inc_kill();
			attacker_kda.inc_kill();
			int deather_team_id = receiver_kda.get_team_id();
			receiver_kda.inc_death();
			_team_kda[deather_team_id].inc_death();
		}finally{
			_lock.unlock();
		}
	}
	
	@Override
	protected void on_running(){
		super.on_running();
		_areas[0][0].do_ready();
		_areas[1][0].do_ready();
		ThreadPool.getInstance().execute(new Runnable(){
			@Override
			public void run(){
				try{
					L1World.getInstance().broadcast_map(_copyMapId, String.format("%d초 후 문이 닫힙니다. 빠르게 다음 스테이지로 이동해주세요.", MJCombatLoadManager.COMBAT_BT_STAGE_PASS_SECOND));
					Thread.sleep(MJCombatLoadManager.COMBAT_BT_STAGE_PASS_SECOND * 1000L);
				}catch(Exception e){
					e.printStackTrace();
				}finally{
					_areas[0][0].do_running();
					_areas[1][0].do_running();
				}
			}
		});
	}
	
	@Override
	public void leave(L1PcInstance pc){
		if(pc == null || !pc.is_combat_field())
			return;
		L1Party party = pc.getParty(); 
		if(party != null){
			party.leaveMember(pc);
			pc.setParty(null);
			
		}
		pc.set_instance_status(MJEPcStatus.WORLD);
		pc.set_is_non_action(false);
		pc.set_mark_status(0);
		pc.set_current_combat_id(0);
		pc.set_current_combat_team_id(-1);
		pc.getInventory().findAndRemoveItemId(_key_ids);
		
		if(!pc.isDead())
			pc.start_teleport(MJCopyMapObservable.RESET_X, MJCopyMapObservable.RESET_Y, MJCopyMapObservable.RESET_MAPID, pc.getHeading(), 169, true, false);
		
		try{
			MJPacketFactory.sendOffTime(pc);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	@Override
	public void remove(L1PcInstance pc){
		if(is_closed() || is_disposing())
			return;
		
		int team_id = pc.get_current_combat_team_id();
		pc.set_instance_status(MJEPcStatus.WORLD);
		pc.set_is_non_action(false);
		pc.set_mark_status(0);
		pc.set_current_combat_id(0);
		pc.set_current_combat_team_id(-1);
		if(team_id == -1)
			return;
		
		if(is_ready())
			return;
		
		_user_kda_dictionary.remove(pc.getId());
		L1Party party = _parties[team_id];
		if(party != null)
			party.leaveMember(pc);		
	}
	
	@Override
	public void dispose(){
		dispose_area();
		super.dispose();
		_keys = null;
	}
}
