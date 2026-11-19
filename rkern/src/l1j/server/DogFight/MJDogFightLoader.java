package l1j.server.DogFight;

import java.util.ArrayList;

import l1j.server.DogFight.Game.MJDogFightGameInfo;
import l1j.server.DogFight.Game.MJDogFightGameManager;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.DogFight.History.MJDogFightTicketIdManager;
import l1j.server.DogFight.Skills.MJDogFightSkill;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.MJMessengerInstance;

public class MJDogFightLoader {
	private static MJDogFightLoader m_instance;
	public static MJDogFightLoader getInstance(){
		if(m_instance == null)
			m_instance = new MJDogFightLoader();
		return m_instance;
	}

	private MJDogFightGameManager m_current_manager;
	private MJDogFightLoader(){
		MJDogFightSettings.do_load();
		MJDogFightGameInfo.do_load();
		MJDogFightSkill.do_load();
		MJDogFightHistory.getInstance();
		MJDogFightTicketIdManager.getInstance();
		if(MJDogFightSettings.IS_ON_RUN){
			ThreadPool.getInstance().scheduleRequired(new Runnable(){
				@Override
				public void run(){
					if(!is_reservation_game())
						do_reservation_game(MJDogFightSettings.IS_ON_GAMES, -1);					
				}
			}, 30000);
		}
		L1NpcInstance messenger = L1World.getInstance().findNpc(MJDogFightSettings.MESSENGER_NPC_ID);
		if(messenger != null && messenger instanceof MJMessengerInstance)
			((MJMessengerInstance)messenger).set_current_message(MJDogFightSettings.MESSENGER_SELL_CLOSING_MESSAGE);
	}
	
	public void close_current_game(){
		if(m_current_manager != null){
			m_current_manager.do_cancel_game();
			m_current_manager = null;
		}
	}
	
	public void do_reservation_game(ArrayList<Integer> games_id, int match_count){
		m_current_manager = MJDogFightGameManager.newInstance(games_id, match_count == -1 ? Integer.MAX_VALUE : match_count);
		m_current_manager.execute();
	}
	
	public boolean is_reservation_game(){
		return m_current_manager != null;
	}
}


