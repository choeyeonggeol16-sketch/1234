package l1j.server.MJCombatSystem.Games;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJTemplate.MJEStatus;
import l1j.server.MJTemplate.Builder.MJDropItemBuilder;
import l1j.server.MJTemplate.Builder.MJMonsterSpawnBuilder;
import l1j.server.MJTemplate.Interface.MJIGenericListener;
import l1j.server.MJTemplate.Interface.MJMonsterDeathHandler;
import l1j.server.MJTemplate.L1Instance.MJL1LiftGateInstance;
import l1j.server.MJTemplate.Lineage2D.MJRectangle;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

public class MJBattleTournamentArea implements MJMonsterDeathHandler{
	public static MJBattleTournamentArea newInstance(){
		return new MJBattleTournamentArea();
	}

	private int _team_id;
	private int _round_id;
	private int _map_id;
	private MJRectangle _area;
	private MJDropItemBuilder _key_builder;
	private MJMonsterSpawnBuilder _sbuilder;
	private MJMonsterSpawnBuilder _boss_sbuilder;
	private AtomicInteger _death_count;
	private MJEStatus _status;
	private MJL1LiftGateInstance _lift;
	private ArrayList<MJDropItemBuilder> _drop_builders;
	private MJIGenericListener<int[]> _clear_listener;
	private MJBattleTournamentArea(){
		_status = MJEStatus.CLOSED;
	}
	
	public boolean is_closed(){
		return _status.equals(MJEStatus.CLOSED);
	}
	public boolean is_ready(){
		return _status.equals(MJEStatus.READY);
	}
	public boolean is_running(){
		return _status.equals(MJEStatus.RUNNING);
	}
	public boolean is_disposing(){
		return _status.equals(MJEStatus.DISPOSING);
	}
	public boolean is_completed(){
		return _status.equals(MJEStatus.COMPLETED);
	}
	
	public MJBattleTournamentArea set_team_id(int val){
		_team_id = val;
		return this;
	}
	public int get_team_id(){
		return _team_id;
	}

	public MJBattleTournamentArea set_round_id(int val){
		_round_id = val;
		return this;
	}
	public int get_round_id(){
		return _round_id;
	}
	
	public MJBattleTournamentArea set_map_id(int map_id){
		_map_id = map_id;
		return this;
	}
	public int get_map_id(){
		return _map_id;
	}

	public MJBattleTournamentArea set_area(MJRectangle val){
		_area = val;
		return this;
	}
	public MJRectangle get_area(){
		return _area;
	}
	
	public MJBattleTournamentArea set_key_builder(MJDropItemBuilder val){
		_key_builder = val;
		return this;
	}
	public MJDropItemBuilder get_key_builder(){
		return _key_builder;
	}
	
	public MJBattleTournamentArea set_sbuilder(MJMonsterSpawnBuilder val){
		_sbuilder = val;
		return this;
	}
	public MJMonsterSpawnBuilder get_sbuilder(){
		return _sbuilder;
	}

	public MJBattleTournamentArea set_boss_sbuilder(MJMonsterSpawnBuilder val){
		_boss_sbuilder = val;
		return this;
	}
	public MJMonsterSpawnBuilder get_boss_sbuilder(){
		return _boss_sbuilder;
	}
	
	public MJBattleTournamentArea set_death_count(AtomicInteger val){
		_death_count = val;
		return this;
	}
	public AtomicInteger get_death_count(){
		return _death_count;
	}

	public MJBattleTournamentArea set_status(MJEStatus val){
		_status = val;
		return this;
	}
	public MJEStatus get_status(){
		return _status;
	}
	
	public MJBattleTournamentArea set_lift(MJL1LiftGateInstance lift){
		_lift = lift;
		return this;
	}
	public MJL1LiftGateInstance get_lift(){
		return _lift;
	}
	
	public MJBattleTournamentArea set_drop_builders(ArrayList<MJDropItemBuilder> drop_builders){
		_drop_builders = drop_builders;
		return this;
	}
	public ArrayList<MJDropItemBuilder> get_drop_builders(){
		return _drop_builders;
	}
	
	public MJBattleTournamentArea set_clear_listener(MJIGenericListener<int[]> val){
		_clear_listener = val;
		return this;
	}
	public MJIGenericListener<int[]> get_clear_listener(){
		return _clear_listener;
	}
	
	public void do_ready(){
		if(!is_closed())
			return;
		
		set_status(MJEStatus.READY);
		_lift.down();
	}
	
	public void do_running(){
		if(!is_ready())
			return;
		
		set_status(MJEStatus.RUNNING);
		_lift.up();
		int spawns = (L1World.getInstance().getVisiblePlayers(_map_id).size() / 2) * MJCombatLoadManager.COMBAT_BT_MONSTER_PACK;
		set_death_count(new AtomicInteger(spawns));
		ThreadPool.getInstance().execute(new Runnable(){
			@Override
			public void run(){
				try{
					for(int i=spawns - 1; i>=0; --i){
						L1MonsterInstance m = _sbuilder.build(_area.left, _area.top, _area.right, _area.bottom, (short) _map_id, MJBattleTournamentArea.this);
						SC_EffectLocation.broadcast(m, m.getX(), m.getY(), 2236);
						Thread.sleep(200L);
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		});
	}
	
	public void do_close(){
		if(is_closed())
			return;
		
		set_status(MJEStatus.CLOSED);
		_lift.up();
	}
	
	public void do_complete(){
		if(is_completed())
			return;
		
		set_status(MJEStatus.COMPLETED);
		_lift.down();
	}

	private int[] create_boss_notify_packet(){
		switch(get_round_id()){
		case 0:
			return new int[]{SC_DisplayEffect.RINDVIOR_BORDER_DISPLAY};
		case 1:
			return new int[]{SC_DisplayEffect.PUPLE_BORDER_DISPLAY};
		case 2:
			return new int[]{SC_DisplayEffect.RINDVIOR_BORDER_DISPLAY};
		case 3:
			return new int[]{SC_DisplayEffect.VALAKAS_BORDER_DISPLAY};
		case 4:
			return new int[]{
					SC_DisplayEffect.VALAKAS_BORDER_DISPLAY, SC_DisplayEffect.QUAKE_DISPLAY};
		}
		return null;
	}
	
	
    private void broadcast(int[] packets, boolean ignored) {
        if (packets == null || packets.length == 0) return;
        final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter[] frames=new l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter[packets.length];
        for(int i=0;i<packets.length;i++)
            frames[i]=SC_DisplayEffect.nativeWriter(packets[i]);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendWrittenMapTeamFrames(_map_id,_team_id,_round_id<4,frames);
    }
	
	private void broadcast(String s){
        if (_round_id >= 4) {
            l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcastRecipients(L1World.getInstance().getVisiblePlayers(_map_id),s);
        } else {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendWrittenMapTeamBestEffort(_map_id, _team_id, SC_SystemMessage.nativeWriter(s));
        }
    }
	
	public void do_boss_spawn(){
		ThreadPool.getInstance().execute(new Runnable(){
			@Override
			public void run(){
				try{
					L1Npc npc = _boss_sbuilder.getNpc();
					if(npc == null){
						System.out.println(String.format("MJBattleHunterArea boss spawn is null!!! team_id : %d, round_id : %d", _team_id, _round_id));
						return;
					}
					
					for(int i=MJCombatLoadManager.COMBAT_BT_BOSS_SPAWN_SECOND; i>=1; --i){
						broadcast(String.format("%d초 후 %s가 출현합니다.", i, npc.get_name()));
						Thread.sleep(1000L);
					}
					int[] pcks = create_boss_notify_packet();
					broadcast(pcks, false);
					_boss_sbuilder.build(_area.x, _area.y, 0, (short) _map_id, new MJMonsterDeathHandler(){
						@Override
						public boolean onDeathNotify(L1MonsterInstance m){
							if(m._destroyed)
								return false;
							
							ThreadPool.getInstance().execute(new Runnable(){
								@Override
								public void run(){
									try{
										int mx = m.getX();
										int my = m.getY();
										SC_EffectLocation.broadcast(m, mx, my, 2236);
										m.setDeathProcessing(true);
										m.allTargetClear();
										m.setCurrentHp(0);
										m.setDead(true);
										m.setStatus(ActionCodes.ACTION_Die);
										m.deleteMe();
										
										if(_key_builder != null){
											_key_builder.build(mx, my, (short)_map_id, -1);
											_key_builder = null;
										}
									}catch(Exception e){
										e.printStackTrace();
									}finally{
										_clear_listener.on(new int[]{get_team_id(), get_round_id()});
									}
								}
							});
							
							return true;
						}
					});
					Thread.sleep(500L);
					broadcast(pcks, true);
				}catch(Exception e){
					e.printStackTrace();
				}finally{
				}
			}
		});
	}
	
	@Override
	public boolean onDeathNotify(L1MonsterInstance m) {
		if(m._destroyed)
			return false;
		
		SC_EffectLocation.broadcast(m, m.getX(), m.getY(), 2236);
		m.setDeathProcessing(true);
		m.allTargetClear();
		m.setCurrentHp(0);
		m.setDead(true);
		m.setStatus(ActionCodes.ACTION_Die);
		m.deleteMe();
		if(_death_count.decrementAndGet() <= 0){
			if(_drop_builders != null){
				ThreadPool.getInstance().execute(new Runnable(){
					@Override
					public void run(){
						Collections.shuffle(_drop_builders);
						int size = _drop_builders.size();
						for(int i = MJCombatLoadManager.COMBAT_BT_CLEAR_BONUS_AMOUNT - 1; i>=0; --i)
							_drop_builders.get(i % size).build(_area.left, _area.top, _area.right, _area.bottom, (short) _map_id);
					}
				});
			}
			
			if(_boss_sbuilder == null){
				_clear_listener.on(new int[]{get_team_id(), get_round_id()});
				return true;
			}
			do_boss_spawn();
		}	
		return true;
	}
	
	public void dispose(){
		if(_lift != null){
			_lift.dispose();
			_lift = null;
		}
		
		_death_count = null;
		_sbuilder = null;
		_clear_listener = null;
		_boss_sbuilder = null;
	}
}
