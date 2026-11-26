package l1j.server.server.model.character.combat.arena.dogfight.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightTicketInfo;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.character.combat.arena.dogfight.instance.IFighterDeadNotifyHandler;
import l1j.server.server.model.character.combat.arena.dogfight.instance.IFighterGameEndedHandler;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.MessengerInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

public class DogFightGame implements IFighterDeadNotifyHandler{
	public static DogFightGame newInstance(DogFightGameInfo gInfo, IFighterGameEndedHandler ended_handler){
		DogFightGame game = newInstance();
		game.set_game_info(gInfo);
		game.set_ready_remain_seconds(gInfo.get_ready_seconds());
		game.set_ended_handler(ended_handler);
		ArrayList<DogFightInstance> instances = gInfo.create_fighters(CompanionClassInfo.get_shuffles_classes());
		for(DogFightInstance instance : instances)
			game.add_fighter(instance);
		return game;
	}
	
	private static DogFightGame newInstance(){
		return new DogFightGame();
	}
	
	private L1NpcInstance m_caster;
	private MessengerInstance m_messenger;
	private int m_ready_remain_seconds;
	private CServerTimer countdownTimer;
	private CServerTimer noticeTimer;
	private volatile boolean m_is_run;
	private volatile boolean m_is_fight;
	private DogFightGameInfo m_game_info;
	private IFighterGameEndedHandler m_ended_handler;
	private Object m_fighter_dead_lock;
	private ArrayList<DogFightInstance> m_all_fighters;
	private final HashMap<Integer, ArrayList<DogFightInstance>> m_fighters;
	private HashMap<Integer, ArrayList<DogFightInstance>> m_down_fighters;
	private DogFightGame(){
		m_is_run = false;
		m_is_fight = false;
		m_all_fighters = new ArrayList<DogFightInstance>();
		m_fighters = new HashMap<Integer, ArrayList<DogFightInstance>>();
		m_down_fighters = new HashMap<Integer, ArrayList<DogFightInstance>>();
		m_caster = CWorld.getInstance().findNpc(DogFightSettings.CASTER_NPC_ID);
		L1NpcInstance messenger = CWorld.getInstance().findNpc(DogFightSettings.MESSENGER_NPC_ID);
		if(messenger != null && messenger instanceof MessengerInstance)
			m_messenger = (MessengerInstance)messenger;
		m_fighter_dead_lock = new Object();
		if(m_caster == null)
			throw new IllegalArgumentException(String.format("[투견] 캐스터npc를 찾을 수 없습니다. 엔피씨아이디 : %d", DogFightSettings.CASTER_NPC_ID));
		
		m_ready_remain_seconds = 0;
	}
	private void set_game_info(DogFightGameInfo game_info){
		m_game_info = game_info;
	}
	public DogFightGameInfo get_game_info(){
		return m_game_info;
	}
	private void set_ready_remain_seconds(int ready_remain_seconds){
		m_ready_remain_seconds = ready_remain_seconds;
	}
	private void set_ended_handler(IFighterGameEndedHandler ended_handler){
		m_ended_handler = ended_handler;
	}
	public void add_fighter(DogFightInstance fighter){
		ArrayList<DogFightInstance> fighters = m_fighters.get(fighter.get_corner_id());
		if(fighters == null){
			fighters = new ArrayList<DogFightInstance>();
			m_fighters.put(fighter.get_corner_id(), fighters);
		}
		m_all_fighters.add(fighter);
		fighters.add(fighter.set_dead_notify(this));
	}
	
	public DogFightInstance get_leader(int corner_id){
		ArrayList<DogFightInstance> fighters = m_fighters.get(corner_id);
		if(fighters == null || fighters.size() <= 0)
			return null;
		return fighters.get(0);
	}
	
	public static DogFightInstance getDogFight(int i) {
		return DogFightHistory.getInstance().get_leader(i);
	}
	
	public L1NpcInstance get_caster(){
		return m_caster;
	}
	public boolean is_run(){
		return m_is_run;
	}
	public boolean is_fight(){
		return m_is_fight;
	}
	
	public void set_fight(boolean is_fight){
		m_is_fight = is_fight;
	}
	@Override
	public void on_dead_fighter(DogFightInstance dogfighter){
		try{
			ArrayList<DogFightInstance> winners = null;
			int select_corner_id = -1;
			synchronized(m_fighter_dead_lock){
				if(!is_run())
					return;
				
				ArrayList<DogFightInstance> fighters = m_fighters.get(dogfighter.get_corner_id());
				if(fighters == null)
					return;
				
				for(DogFightInstance fighter : fighters){
					if(!fighter.isDead())
						return;
				}

				m_down_fighters.put(dogfighter.get_corner_id(), fighters);
				if(m_down_fighters.size() == m_fighters.size() - 1){
					for(Integer corner_id : m_fighters.keySet()){
						if(m_down_fighters.containsKey(corner_id))
							continue;
						
						m_is_run = false;
						winners = m_fighters.get(corner_id);
						select_corner_id = corner_id;
						break;
					}
				}
			}
			if(winners != null && select_corner_id != -1){
				send_caster_message(
						String.format(
								DogFightSettings.WINNER_MESSAGE, 
								DogFightHistory.getInstance().get_current_game_number(), 
								winners.get(0).get_corner_name()
						));
				do_game_ended_trigger(select_corner_id);
				m_ended_handler.on_ended(select_corner_id, winners);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	public void send_caster_message(String message){
		if(m_caster != null)
			SC_NpcChatPacket.broadcastNpc(m_caster, m_caster, message, 2);
	}
	public void send_messenger_message(String message){
		if(m_messenger != null){
			m_messenger.set_current_message(message);
			m_messenger.broadcast_message();			
		}
	}
    private final class CMatchNoticeTimer implements Runnable {
        private int phase;
        private ArrayList<DogFightTicketInfo> tickets;
        private int nextTicket;

        @Override
        public void run() {
            synchronized (DogFightGame.this) {
                if (!is_run()) return;
                if (phase == 0) {
                    send_caster_message(DogFightSettings.START_MESSAGE);
                    phase++;
                } else if (phase == 1) {
                    send_caster_message(DogFightSettings.DIVIDEND_START_MESSAGE);
                    phase++;
                } else {
                    if (tickets == null) {
                        final var currentTickets = DogFightHistory.getInstance().get_current_tickets_info();
                        if (currentTickets == null) return;
                        tickets = new ArrayList<>(currentTickets);
                    }
                    if (nextTicket >= tickets.size()) return;
                    final DogFightTicketInfo ticket = tickets.get(nextTicket++);
                    final DogFightInstance leader = get_leader(ticket.get_corner_id());
                    if (leader != null) {
                        send_caster_message(String.format(DogFightSettings.DIVIDEND_MESSAGE,
                                leader.getName(), ticket.get_dividend()));
                    }
                    if (nextTicket == tickets.size()) return;
                }
                noticeTimer = CInstanceWork.schedule(this, 1_000L);
            }
        }
    }

    public synchronized void execute() {
        if (m_is_run) return;
        m_is_run = true;
        countdownTimer = CInstanceWork.schedule(this::TimerExpired, 1L);
        if (countdownTimer == null) dispose();
    }

    private synchronized void TimerExpired() {
        if (!is_run()) return;
        final int seconds = m_ready_remain_seconds;
        if (seconds > 0) {
            if (seconds % 60 == 0) {
                send_caster_message(String.format(DogFightSettings.MESSAGE_BY_MINUTE,
                        seconds / 60, m_game_info.get_game_name()));
                send_messenger_message(String.format(DogFightSettings.MESSENGER_MESSAGE_BY_MINUTE, seconds / 60));
            }
            if (seconds <= DogFightSettings.MESSENGER_REMAIN_MESSAGE_SECONDS) {
                send_messenger_message(String.format(DogFightSettings.MESSENGER_REMAIN_MESSAGE, seconds));
            }
            if (seconds <= m_game_info.get_ready_remain_message_cutline()) {
                send_caster_message(String.format(DogFightSettings.REMAIN_MESSAGE, seconds));
            }
            if (seconds <= 3) do_three_seconds_trigger(seconds);
            m_ready_remain_seconds--;
            countdownTimer = CInstanceWork.schedule(this::TimerExpired, 1_000L);
            if (countdownTimer == null) dispose();
            return;
        }
        countdownTimer = null;
        m_is_fight = true;
        send_messenger_message(DogFightSettings.MESSENGER_SELL_CLOSING_MESSAGE);
        noticeTimer = CInstanceWork.schedule(new CMatchNoticeTimer(), 1L);
        do_game_start_trigger();
        for (Integer cornerId : m_fighters.keySet()) {
            final ArrayList<DogFightInstance> opponents = create_different_fighters(cornerId);
            for (DogFightInstance fighter : m_fighters.get(cornerId)) {
                fighter.set_targets(opponents);
            }
        }
    }

	private void do_three_seconds_trigger(int remain_seconds){
		ArrayList<DogFightInstance> fighters = get_shuffle_fighters();
		for(DogFightInstance fighter : fighters)
			fighter.on_three_seconds_trigger(remain_seconds);
	}
	
	private void do_game_start_trigger(){
		ArrayList<DogFightInstance> fighters = get_shuffle_fighters();
		for(DogFightInstance fighter : fighters)
			fighter.on_game_start_trigger();
	}
	
	private void do_game_ended_trigger(int winner_id){
		ArrayList<DogFightInstance> fighters = get_shuffle_fighters();
		for(DogFightInstance fighter : fighters){
			if(fighter.get_corner_id() == winner_id)
				fighter.on_game_win_trigger();
			else
				fighter.on_game_lose_trigger();
		}
	}
	
	private ArrayList<DogFightInstance> get_shuffle_fighters(){
		ArrayList<DogFightInstance> fighters = new ArrayList<DogFightInstance>(m_all_fighters);
		Collections.shuffle(fighters);
		return fighters;
	}
	
	private ArrayList<DogFightInstance> create_different_fighters(Integer corner_id){
		ArrayList<DogFightInstance> fighters = new ArrayList<DogFightInstance>();
		for(Integer c_id : m_fighters.keySet()){
			if(c_id == corner_id)
				continue;
			
			fighters.addAll(m_fighters.get(c_id));
		}
		return fighters;
	}
	
	public synchronized void stopTimers(){
		m_is_run = false;
		m_is_fight = false;
		if (countdownTimer != null) countdownTimer.cancel();
		if (noticeTimer != null) noticeTimer.cancel();
		countdownTimer = null;
		noticeTimer = null;
	}
	public synchronized void dispose(){
		stopTimers();
		if(m_fighters != null){
			for(ArrayList<DogFightInstance> fighters : m_fighters.values()){
				for(DogFightInstance fighter : fighters)
					fighter.deleteMe();
			}
		}
		m_fighters.clear();
	}
}

