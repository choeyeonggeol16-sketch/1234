package l1j.server.DogFight.Instance;

import java.util.ArrayList;
import java.util.Collections;

import l1j.server.DogFight.Game.Trigger.MJDogFightTrigger;
import l1j.server.DogFight.Game.Trigger.MJDogFightTriggerInfo;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_WorldPutObjectNoti;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.MJCommons;

import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
public class MJDogFightInstance extends L1NpcInstance{
	private static final long serialVersionUID = 1L;

	public static MJDogFightInstance newInstance(
			MJCompanionClassInfo cInfo, 
			String name, 
			int level, 
			int max_hp, 
			int corner_id, 
			String corner_name, 
			double attackdelay_reduce, 
			double movedelay_reduce, 
			int x, 
			int y, 
			int heading, 
			int map_id,
			int min_damage,
			int max_damage,
			int min_hit,
			int max_hit,
			MJDogFightTriggerInfo trigger
			){
		MJDogFightInstance dogfight = newInstance();
		dogfight.begin_update();
		dogfight.setId(IdFactory.getInstance().nextId());
		dogfight.set_class_info(cInfo);
		dogfight.setName(name);
		dogfight.setLevel(level);
		dogfight.setExp(ExpTable.getExpByLevel(level));
		dogfight.setMaxHp(max_hp);
		dogfight.setCurrentHp(max_hp);
		dogfight.setMaxMp(1);
		dogfight.setLawful(0);
		dogfight.set_corner_id(corner_id);
		dogfight.set_corner_name(corner_name);
		dogfight.set_attackdelay_reduce(attackdelay_reduce);
		dogfight.set_movedelay_reduce(movedelay_reduce);
		dogfight.setX(x);
		dogfight.setY(y);
		dogfight.setHeading(heading);
		dogfight.setMap((short)map_id);
		dogfight.setLightSize(dogfight.getNpcTemplate().getLightSize());
		dogfight.set_min_damage(min_damage);
		dogfight.set_max_damage(max_damage);
		dogfight.set_min_hit(min_hit);
		dogfight.set_max_hit(max_hit);
		dogfight.set_trigger(trigger);
		dogfight.end_update();
		dogfight.do_store_object();
		return dogfight;
	}
	
	public static MJDogFightInstance newInstance(){
		return new MJDogFightInstance();
	}
	
	private MJCompanionClassInfo m_class_info;
	private int m_corner_id;
	private String m_corner_name;
	private int m_explosion_time_mills;
	private double m_attackdelay_reduce;
	private double m_movedelay_reduce;
	private Object m_death_lock;
	private boolean m_is_non_update;
	private int m_min_damage;
	private int m_max_damage;
	private int m_min_hit;
	private int m_max_hit;
	private MJIFighterDeadNotifyHandler m_dead_notify_handler;
	private MJDogFightTriggerInfo m_trigger;
	public MJDogFightInstance() {
		super(null);
		m_death_lock = new Object();
		m_is_non_update = true;
	}
	
	public void begin_update(){
		m_is_non_update = true;
	}
	public void end_update(){
		m_is_non_update = false;
	}
	public MJDogFightInstance set_class_info(MJCompanionClassInfo cInfo){
		m_class_info = cInfo;
		L1Npc npc = NpcTable.getInstance().getTemplate(cInfo.get_npc_id());
		if(npc == null)
			throw new IllegalArgumentException(String.format("MJDogFightInstance::set_class_info()... found companion npc template...class name %s, npc id : %d ", cInfo.get_class_name(), cInfo.get_npc_id()));
		setting_template(npc);
		return this;
	}
	public MJDogFightInstance set_corner_id(int corner_id){
		m_corner_id = corner_id;
		return this;
	}
	public MJDogFightInstance set_corner_name(String corner_name){
		m_corner_name = corner_name;
		return this;
	}
	public MJDogFightInstance set_explosion_time_millis(int explosion_time_mills){
		m_explosion_time_mills = explosion_time_mills;
		return this;
	}
	public MJDogFightInstance set_attackdelay_reduce(double attackdelay_reduce){
		m_attackdelay_reduce = attackdelay_reduce;
		return this;
	}
	public MJDogFightInstance set_movedelay_reduce(double movedelay_reduce){
		m_movedelay_reduce = movedelay_reduce;
		return this;
	}
	public MJDogFightInstance set_min_damage(int min_damage){
		m_min_damage = min_damage;
		return this;
	}
	public MJDogFightInstance set_max_damage(int max_damage){
		m_max_damage = max_damage;
		return this;
	}
	public MJDogFightInstance set_min_hit(int min_hit){
		m_min_hit = min_hit;
		return this;
	}
	public MJDogFightInstance set_max_hit(int max_hit){
		m_max_hit = max_hit;
		return this;
	}
	public MJDogFightInstance set_dead_notify(MJIFighterDeadNotifyHandler dead_notify_handler){
		m_dead_notify_handler = dead_notify_handler;
		return this;
	}
	public MJDogFightInstance set_trigger(MJDogFightTriggerInfo trigger){
		m_trigger = trigger;
		return this;
	}
	public MJCompanionClassInfo get_class_info(){
		return m_class_info;
	}
	public int get_corner_id(){
		return m_corner_id;
	}
	public String get_corner_name(){
		return m_corner_name;
	}
	public int get_explosion_time_mills(){
		return m_explosion_time_mills;
	}
	public double get_attackdelay_reduce(){
		return m_attackdelay_reduce;
	}
	public double get_movedelay_reduce(){
		return m_movedelay_reduce;
	}
	public int get_min_damage(){
		return m_min_damage;
	}
	public int get_max_damage(){
		return m_max_damage;
	}
	public int get_next_damage(){
		return MJRnd.next(m_min_damage, m_max_damage);
	}
	public int get_min_hit(){
		return m_min_hit;
	}
	public int get_max_hit(){
		return m_max_hit;
	}
	public int get_next_hit(){
		return MJRnd.next(m_min_hit, m_max_hit);
	}
	public MJDogFightTriggerInfo get_trigger(){
		return m_trigger;
	}
	public void set_target(L1Character target){
		if(target == null)
			return;
		
		if(m_is_non_update)
			return;
		
		setHate(target, 0);
		if(_target != null){
			setHate(_target, 0);
			_target = target;
		}
		if(!isAiRunning())
			startAI();
	}
	public void set_targets(ArrayList<MJDogFightInstance> targets){
		if(m_is_non_update)
			return;
		
		Collections.shuffle(targets);
		if(_target != null)
			setHate(_target, 0);
		for(MJDogFightInstance fighter : targets){
			setHate(fighter, 0);
			_target = fighter;
		}
		if(!isAiRunning())
			startAI();
	}
	public void do_store_object(){
		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			onPerceive(pc);
		}
	}
	
	public void death(MJDogFightInstance last_attacker){
		synchronized(m_death_lock){
			if(isDead())
				return;
			
			setDead(true);
		}
		
		setStatus(ActionCodes.ACTION_Die);
		setCurrentHp(0);
		send_action(ActionCodes.ACTION_Die);
		if(m_dead_notify_handler != null)
			m_dead_notify_handler.on_dead_fighter(this);
	}
	
	@Override
	public void setCurrentHp(int i){
		super.setCurrentHp(i);
		if(m_is_non_update)
			return;
		
		SC_HPMeter.broadcast(this, this);
	}
	
	@Override
	public void onPerceive(L1PcInstance perceivedFrom){
		if(m_is_non_update)
			return;
		
	    perceivedFrom.addKnownObject(this);
	    SC_WorldPutObjectNoti.sendNewInstance(perceivedFrom, this);
	    SC_HPMeter.send(perceivedFrom, this);
		if(isDead())
	        SC_DoActionGFX.send(
	                perceivedFrom.getNetConnection(),
	                getId(),
	                ActionCodes.ACTION_Die);
	}
	
	@Override
	public boolean noTarget(){
		return false;
	}
	
	@Override
	public void receiveDamage(L1Character attacker, int damage){
		if(!(attacker instanceof MJDogFightInstance))
			return;
		
		if(m_is_non_update)
			return;
		
		MJDogFightInstance attacker_dog = (MJDogFightInstance)attacker;
		if(getCurrentHp() <= 0){
			if(!isDead())
				death(attacker_dog);
			return;
		}
		int new_hp = getCurrentHp() - damage;
		if(new_hp <= 0)
			death(attacker_dog);
		else
			setCurrentHp(new_hp);
	}
	
	@Override
	public void onAction(L1PcInstance player){
	}
	@Override
	public boolean checkCondition(){
		return false;
	}
	
	private void on_trigger_sended(MJDogFightTrigger trigger){
		if(trigger.get_send_action() > 0 && !isDead())
			send_action(trigger.get_send_action());
		if(trigger.get_send_effect() > 0 && !isDead())
			send_effect(trigger.get_send_effect());
		if(!MJCommons.isNullOrEmpty(trigger.get_send_message()))
			SC_NpcChatPacket.broadcastNpc(this, this, trigger.get_send_message(), 0);
	}
	
	private void on_trigger_sended(MJDogFightTrigger trigger, boolean is_non_send){
		long sleeps = 0L;
		if(trigger.get_move_x() > 0 && trigger.get_move_y() > 0 && !isDead()){
			int dir = moveDirection(trigger.get_move_x(), trigger.get_move_y());
			if(dir != -1){
				sleeps = setDirectionMove(dir);
				setSleepTime(sleeps);
			}
		}
		if(is_non_send)
			return;
		
		if(trigger.get_is_move_ended_send()){
			if(sleeps > 0){
				ThreadPool.getInstance().scheduleRequired(new Runnable(){
					@Override
					public void run(){
						on_trigger_sended(trigger);
					}
				}, sleeps);
				return;
			}	
		}
		on_trigger_sended(trigger);
	}
	
	public void on_three_seconds_trigger(int remain_seconds){
		if(m_trigger.get_three_seconds_before_start() == null)
			return;
		
		final MJDogFightTrigger trigger = m_trigger.get_three_seconds_before_start();
		on_trigger_sended(trigger, trigger.get_is_move_ended_send() && remain_seconds != 1);
	}
	
	public void on_game_start_trigger(){
		if(m_trigger.get_on_game_start() == null)
			return;
	
		on_trigger_sended(m_trigger.get_on_game_start(), false);
	}
	
	public void on_game_win_trigger(){
		if(m_trigger.get_on_game_win() == null)
			return;
		
		on_trigger_sended(m_trigger.get_on_game_win(), false);
	}
	
	public void on_game_lose_trigger(){
		if(m_trigger.get_on_game_lose() == null)
			return;
		
		on_trigger_sended(m_trigger.get_on_game_lose(), false);
	}
	
	
	/**
	 * 승률 조작 
	 */
	private long m_total_price;
	public long getTotalPrice(){
		return m_total_price;
	}
	public void addTotalPrice(long i) {
		m_total_price += i;
	}
}
