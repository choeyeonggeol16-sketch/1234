package l1j.server.server.model.character.combat.arena.dogfight.game;

import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistoryInfo;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.character.combat.arena.dogfight.instance.IFighterGameEndedHandler;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.shared.GameRandom;

public class DogFightGameManager implements IFighterGameEndedHandler{
	public static DogFightGameManager newInstance(ArrayList<Integer> game_ids, int max_match_count){
		return newInstance()
				.set_game_ids(game_ids)
				.set_current_match_count(0)
				.set_max_match_count(max_match_count);
	}
	
	private static DogFightGameManager newInstance(){
		return new DogFightGameManager();
	}

	private ArrayList<Integer> m_game_ids;
	private DogFightGameInfo m_game_info;
	private int m_current_match_count;
	private volatile int m_max_match_count;
	private volatile DogFightGame m_current_game;
	private CServerTimer transition;
	private DogFightGameManager(){
	}
	public DogFightGameManager set_game_ids(ArrayList<Integer> game_ids){
		m_game_ids = game_ids;
		return this;
	}
	public DogFightGameManager set_game_info(DogFightGameInfo game_info){
		m_game_info = game_info;
		return this;
	}
	public DogFightGameManager set_current_match_count(int current_match_count){
		m_current_match_count = current_match_count;
		return this;
	}
	public DogFightGameManager set_max_match_count(int max_match_count){
		m_max_match_count = max_match_count;
		return this;
	}
	public ArrayList<Integer> get_game_ids(){
		return m_game_ids;
	}
	public DogFightGameInfo get_game_info(){
		return m_game_info;
	}
	public int get_current_match_count(){
		return m_current_match_count;
	}
	public int get_max_match_count(){
		return m_max_match_count;
	}
	
    public synchronized void execute() {
        if (transition != null || m_current_game != null || !is_next_match()) return;
        transition = CInstanceWork.scheduleRequired(this::startMatch, DogFightSettings.NEXT_MATCH_READY_SECONDS * 1_000L);
    }

    private synchronized void startMatch() {
        transition = null;
        if (!is_next_match()) return;
        m_current_game = create_games();
        m_current_game.execute();
    }

	private DogFightGame create_games(){
		++m_current_match_count;
		set_game_info(DogFightGameInfo.get_game_info(m_game_ids.get(GameRandom.next(m_game_ids.size()))));
		DogFightHistory.getInstance().create_new_history(this);
		return DogFightGame.newInstance(get_game_info(), this);
	}

	@Override
	public synchronized void on_ended(int winner_id, ArrayList<DogFightInstance> winners) {
		final DogFightGame finished = m_current_game;
		if (finished == null || transition != null) return;
		DogFightHistoryInfo hInfo = DogFightHistory.getInstance().get_current_history();
		if(hInfo != null){
			hInfo.set_winner_id(winner_id)
			.set_winner_members(make_winners_name(winners))
			.do_calculate(winner_id);
			DogFightHistory.getInstance().update_game();			
		}
        finished.set_fight(false);
        transition = CInstanceWork.scheduleRequired(() -> finishMatch(finished), 5_000L);
    }

    private synchronized void finishMatch(DogFightGame finished) {
        transition = null;
        if (m_current_game != finished) return;
        finished.send_caster_message(String.format(
                DogFightSettings.NEXT_GAME_MESSAGE, DogFightSettings.NEXT_MATCH_READY_SECONDS));
        finished.dispose();
        m_current_game = null;
        do_next_match();
    }

    public synchronized void stopTimers() {
        m_max_match_count = 0;
        if (transition != null) transition.cancel();
        transition = null;
        if (m_current_game != null) m_current_game.stopTimers();
    }

    public synchronized void do_cancel_game() {
        stopTimers();
        final DogFightGame game = m_current_game;
        m_current_game = null;
        if (game != null) game.dispose();
        DogFightHistory.getInstance().do_cancel_game();
    }

	private String make_winners_name(ArrayList<DogFightInstance> winners){
		int size = winners.size();
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<size; ++i){
			if(i != 0)
				sb.append(",");
			sb.append(winners.get(i).get_class_info().get_class_npc_name());
		}
		return sb.toString();
	}
	
	private void do_next_match(){
		if(!is_next_match())
			return;
		
		execute();
	}
	public boolean is_next_match(){
		return m_current_match_count < m_max_match_count;
	}
	public boolean is_fight(){
		return m_current_game != null && m_current_game.is_fight();
	}
	public boolean is_run(){
		return m_current_game != null && m_current_game.is_run();
	}
	
	public DogFightInstance get_leader(int corner_id){
		if(m_current_game == null)
			return null;
		
		return m_current_game.get_leader(corner_id);
	}
}

