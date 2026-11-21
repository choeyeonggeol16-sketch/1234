package l1j.server.server.model.character.combat.arena.dogfight.game;

import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistoryInfo;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.character.combat.arena.dogfight.instance.IFighterGameEndedHandler;
import l1j.server.server.ThreadPool;
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
	private int m_max_match_count;
	private DogFightGame m_current_game;
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
	
	public void execute(){
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				if(!is_next_match()) return;
				m_current_game = create_games();
				m_current_game.execute();
			}
		}, (DogFightSettings.NEXT_MATCH_READY_SECONDS) * 1000L);
	}
	
	private DogFightGame create_games(){
		++m_current_match_count;
		set_game_info(DogFightGameInfo.get_game_info(m_game_ids.get(GameRandom.next(m_game_ids.size()))));
		DogFightHistory.getInstance().create_new_history(this);
		return DogFightGame.newInstance(get_game_info(), this);
	}

	@Override
	public void on_ended(int winner_id, ArrayList<DogFightInstance> winners) {
		DogFightHistoryInfo hInfo = DogFightHistory.getInstance().get_current_history();
		if(hInfo != null){
			hInfo.set_winner_id(winner_id)
			.set_winner_members(make_winners_name(winners))
			.do_calculate(winner_id);
			DogFightHistory.getInstance().update_game();			
		}
		m_current_game.set_fight(false);
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				m_current_game.send_caster_message(String.format(DogFightSettings.NEXT_GAME_MESSAGE, DogFightSettings.NEXT_MATCH_READY_SECONDS));
				m_current_game.dispose();
				m_current_game = null;
				do_next_match();
			}
		}, 5000L);//5초후 다음경기 시작 알림
	}
	
	public void do_cancel_game(){
	    m_max_match_count = 0;
	    // 진행 중/대기 중 경기 강제 종료
	    DogFightGame game = m_current_game;
	    if(game != null){
	        game.dispose();  // 카운트다운 중이면 is_run=false로 다음 틱에 자동 탈출
	    }
	    // 히스토리 정리 — 안 하면 현재 회차 티켓이 내 is_current_round_ticket 가드에 걸려 영구 판매 불가로 남음
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

