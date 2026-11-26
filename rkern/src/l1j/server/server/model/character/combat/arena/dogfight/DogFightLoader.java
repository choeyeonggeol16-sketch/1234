package l1j.server.server.model.character.combat.arena.dogfight;

import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGameInfo;
import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGameManager;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightTicketIdManager;
import l1j.server.server.model.character.combat.arena.dogfight.skills.DogFightSkill;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.MessengerInstance;
import l1j.server.server.model.L1World;

public class DogFightLoader {
	private static DogFightLoader m_instance;
	public static DogFightLoader getInstance(){
		if(m_instance == null)
			m_instance = new DogFightLoader();
		return m_instance;
	}

	private volatile DogFightGameManager m_current_manager;
	private CServerTimer autoStart;
	private DogFightLoader(){
		DogFightSettings.do_load();
		DogFightGameInfo.do_load();
		DogFightSkill.do_load();
		DogFightHistory.getInstance();
		DogFightTicketIdManager.getInstance();
		L1NpcInstance messenger = L1World.getInstance().findNpc(DogFightSettings.MESSENGER_NPC_ID);
		if(messenger != null && messenger instanceof MessengerInstance)
			((MessengerInstance)messenger).set_current_message(DogFightSettings.MESSENGER_SELL_CLOSING_MESSAGE);
	}
	
    public synchronized void startAutomaticReservation() {
        if (DogFightSettings.IS_ON_RUN && autoStart == null) {
            autoStart = CInstanceWork.scheduleRequired(this::startReservedMatches, 30_000L);
        }
    }

    private synchronized void startReservedMatches() {
        if (autoStart != null && !is_reservation_game()) {
            do_reservation_game(DogFightSettings.IS_ON_GAMES, -1);
        }
    }

    public synchronized void stopTimers() {
        if (autoStart != null) autoStart.cancel();
        autoStart = null;
        if (m_current_manager != null) m_current_manager.stopTimers();
    }

	public synchronized void close_current_game(){
		if(m_current_manager != null){
			m_current_manager.do_cancel_game();
			m_current_manager = null;
		}
	}
	
	public synchronized void do_reservation_game(ArrayList<Integer> games_id, int match_count){
		m_current_manager = DogFightGameManager.newInstance(games_id, match_count == -1 ? Integer.MAX_VALUE : match_count);
		m_current_manager.execute();
	}
	
	public boolean is_reservation_game(){
		return m_current_manager != null;
	}
}


