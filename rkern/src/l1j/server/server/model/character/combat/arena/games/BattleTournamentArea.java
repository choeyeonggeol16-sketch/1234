package l1j.server.server.model.character.combat.arena.games;

import l1j.server.server.model.world.instance.CInstanceEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.shared.L1LiftGateInstance;
import l1j.server.server.shared.EStatus;
import l1j.server.server.shared.builder.DropItemBuilder;
import l1j.server.server.shared.builder.MonsterSpawnBuilder;
import l1j.server.server.shared.geometry.GameRectangle;
import l1j.server.server.shared.listener.IGenericListener;
import l1j.server.server.shared.listener.MonsterDeathHandler;
import l1j.server.server.templates.L1Npc;

public class BattleTournamentArea implements MonsterDeathHandler{
	public static BattleTournamentArea newInstance(){
		return new BattleTournamentArea();
	}

	private int _team_id;
	private int _round_id;
	private int _map_id;
	private L1Map _terrain;
	private volatile BattleTournamentObserver _observer;
	private GameRectangle _area;
	private DropItemBuilder _key_builder;
	private MonsterSpawnBuilder _sbuilder;
	private MonsterSpawnBuilder _boss_sbuilder;
	private AtomicInteger _death_count;
	private EStatus _status;
	private L1LiftGateInstance _lift;
	private ArrayList<DropItemBuilder> _drop_builders;
	private IGenericListener<int[]> _clear_listener;
	private BattleTournamentArea(){
		_status = EStatus.CLOSED;
	}
	
	public boolean is_closed(){
		return _status.equals(EStatus.CLOSED);
	}
	public boolean is_ready(){
		return _status.equals(EStatus.READY);
	}
	public boolean is_running(){
		return _status.equals(EStatus.RUNNING);
	}
	public boolean is_disposing(){
		return _status.equals(EStatus.DISPOSING);
	}
	public boolean is_completed(){
		return _status.equals(EStatus.COMPLETED);
	}
	
	public BattleTournamentArea set_team_id(int val){
		_team_id = val;
		return this;
	}
	public int get_team_id(){
		return _team_id;
	}

	public BattleTournamentArea set_round_id(int val){
		_round_id = val;
		return this;
	}
	public int get_round_id(){
		return _round_id;
	}
	
	BattleTournamentArea set_terrain(L1Map terrain, BattleTournamentObserver observer) {
		_terrain = terrain;
		_map_id = terrain.getId();
		_observer = observer;
		return this;
	}
	public int get_map_id(){
		return _map_id;
	}

	public BattleTournamentArea set_area(GameRectangle val){
		_area = val;
		return this;
	}
	public GameRectangle get_area(){
		return _area;
	}
	
	public BattleTournamentArea set_key_builder(DropItemBuilder val){
		_key_builder = val;
		return this;
	}
	public DropItemBuilder get_key_builder(){
		return _key_builder;
	}
	
	public BattleTournamentArea set_sbuilder(MonsterSpawnBuilder val){
		_sbuilder = val;
		return this;
	}
	public MonsterSpawnBuilder get_sbuilder(){
		return _sbuilder;
	}

	public BattleTournamentArea set_boss_sbuilder(MonsterSpawnBuilder val){
		_boss_sbuilder = val;
		return this;
	}
	public MonsterSpawnBuilder get_boss_sbuilder(){
		return _boss_sbuilder;
	}
	
	public BattleTournamentArea set_death_count(AtomicInteger val){
		_death_count = val;
		return this;
	}
	public AtomicInteger get_death_count(){
		return _death_count;
	}

	public BattleTournamentArea set_status(EStatus val){
		_status = val;
		return this;
	}
	public EStatus get_status(){
		return _status;
	}
	
	public BattleTournamentArea set_lift(L1LiftGateInstance lift){
		_lift = lift;
		return this;
	}
	public L1LiftGateInstance get_lift(){
		return _lift;
	}
	
	public BattleTournamentArea set_drop_builders(ArrayList<DropItemBuilder> drop_builders){
		_drop_builders = drop_builders;
		return this;
	}
	public ArrayList<DropItemBuilder> get_drop_builders(){
		return _drop_builders;
	}
	
	public BattleTournamentArea set_clear_listener(IGenericListener<int[]> val){
		_clear_listener = val;
		return this;
	}
	public IGenericListener<int[]> get_clear_listener(){
		return _clear_listener;
	}
	
	public void do_ready(){
		if(!is_closed())
			return;
		
		set_status(EStatus.READY);
		_lift.down();
	}
	
	public void do_running(){
		runOwned(() -> {
			if (!is_ready()) return false;
			set_status(EStatus.RUNNING);
			_lift.up();
			final int spawns = (CWorld.getInstance().getVisiblePlayers(_terrain).size() / 2)
					* CombatLoadManager.COMBAT_BT_MONSTER_PACK;
			set_death_count(new AtomicInteger(spawns));
			CInstanceEvents.start(() -> {
				try {
					for (int i = 0; i < spawns && spawnMonster(); i++) Thread.sleep(200L);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			});
			return true;
		});
	}

	private boolean runOwned(BooleanSupplier action) {
		final BattleTournamentObserver observer = _observer;
		return observer != null && observer.runAreaAction(_terrain, action);
	}

	private boolean spawnMonster() {
		return runOwned(() -> {
			final L1MonsterInstance monster = _sbuilder.build(
					_area.left, _area.top, _area.right, _area.bottom, (short) _map_id, this);
			SC_EffectLocation.broadcast(monster, monster.getX(), monster.getY(), 2236);
			return true;
		});
	}
	
	public void do_close(){
		if(is_closed())
			return;
		
		set_status(EStatus.CLOSED);
		_lift.up();
	}
	
	public void do_complete(){
		if(is_completed())
			return;
		
		set_status(EStatus.COMPLETED);
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
        final CSharedBroadcast.NativeWireWriter[] frames = new CSharedBroadcast.NativeWireWriter[packets.length];
        for(int i=0;i<packets.length;i++)
            frames[i]=SC_DisplayEffect.nativeWriter(packets[i]);
        CSharedBroadcast.sendWrittenMapTeamFrames(_terrain, _team_id, _round_id < 4, frames);
    }
	
	private void broadcast(String s){
        if (_round_id >= 4) {
            CSharedBroadcast.sendWrittenMapBestEffort(_terrain, SC_SystemMessage.nativeWriter(s));
        } else {
            CSharedBroadcast.sendWrittenMapTeamBestEffort(_terrain, _team_id, SC_SystemMessage.nativeWriter(s));
        }
    }
	
	public void do_boss_spawn(){
		runOwned(() -> {
			final L1Npc npc = _boss_sbuilder == null ? null : _boss_sbuilder.getNpc();
			if (npc == null) return false;
			CInstanceEvents.start(() -> runBossCountdown(npc));
			return true;
		});
	}

	private void runBossCountdown(L1Npc npc) {
		try {
			for (int remaining = CombatLoadManager.COMBAT_BT_BOSS_SPAWN_SECOND; remaining >= 1; remaining--) {
				final int seconds = remaining;
				if (!runOwned(() -> {
					broadcast(String.format("%d초 후 %s가 출현합니다.", seconds, npc.get_name()));
					return true;
				})) return;
				Thread.sleep(1000L);
			}
			final int[] packets = create_boss_notify_packet();
			if (!spawnBoss(packets)) return;
			Thread.sleep(500L);
			runOwned(() -> {
				broadcast(packets, true);
				return true;
			});
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean spawnBoss(int[] packets) {
		return runOwned(() -> {
			broadcast(packets, false);
			_boss_sbuilder.build(_area.x, _area.y, 0, (short) _map_id, this::onBossDeath);
			return true;
		});
	}

	private boolean onBossDeath(L1MonsterInstance monster) {
		return runOwned(() -> {
			if (monster._destroyed || monster.getMap() != _terrain) return false;
			CInstanceEvents.start(() -> finishBossDeath(monster));
			return true;
		});
	}

	private boolean finishBossDeath(L1MonsterInstance monster) {
		return runOwned(() -> {
			if (monster._destroyed || monster.getMap() != _terrain) return false;
			try {
				final int x = monster.getX(), y = monster.getY();
				deleteMonster(monster);
				if (_key_builder != null) {
					_key_builder.build(x, y, _terrain, -1);
					_key_builder = null;
				}
			} finally {
				_clear_listener.on(new int[]{get_team_id(), get_round_id()});
			}
			return true;
		});
	}

	@Override
	public boolean onDeathNotify(L1MonsterInstance monster) {
		return runOwned(() -> {
			if (monster._destroyed || monster.getMap() != _terrain) return false;
			deleteMonster(monster);
			if (_death_count.decrementAndGet() <= 0) {
				if (_drop_builders != null) CInstanceEvents.start(this::dropBonus);
				if (_boss_sbuilder == null) _clear_listener.on(new int[]{get_team_id(), get_round_id()});
				else do_boss_spawn();
			}
			return true;
		});
	}

	private boolean dropBonus() {
		return runOwned(() -> {
			Collections.shuffle(_drop_builders);
			final int size = _drop_builders.size();
			if (size == 0) return false;
			for (int i = CombatLoadManager.COMBAT_BT_CLEAR_BONUS_AMOUNT - 1; i >= 0; i--) {
				_drop_builders.get(i % size).build(_area.left, _area.top, _area.right, _area.bottom, (short) _map_id);
			}
			return true;
		});
	}

	private static void deleteMonster(L1MonsterInstance monster) {
		SC_EffectLocation.broadcast(monster, monster.getX(), monster.getY(), 2236);
		monster.setDeathProcessing(true);
		monster.allTargetClear();
		monster.setCurrentHp(0);
		monster.setDead(true);
		monster.setStatus(ActionCodes.ACTION_Die);
		monster.deleteMe();
	}

	public void dispose(){
		_observer = null;
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
