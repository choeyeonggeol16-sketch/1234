package l1j.server.server.model.character.combat.arena.dogfight.game.trigger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;

public class DogFightTriggerInfo {
	public static DogFightTriggerInfo do_load(final int game_id, final int corner_id, final int member_id){
		final DogFightTriggerInfo tInfo = newInstance();
		Selector.exec("select * from dogfight_fighter_trigger where game_id=? and corner_id=? and member_id=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, game_id);
				pstm.setInt(2, corner_id);
				pstm.setInt(3, member_id);
			}
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DogFightTrigger trigger = DogFightTrigger.newInstance(rs);
					switch(trigger.get_trigger_type().to_int()){
					case DogFightTiggerType.TRIGGER_INT_3SECONDS_BEFORE_THE_START:
						tInfo.set_three_seconds_before_start(trigger);
						break;
					case DogFightTiggerType.TRIGGER_INT_ON_GAME_START:
						tInfo.set_on_game_start(trigger);
						break;
					case DogFightTiggerType.TRIGGER_INT_ON_GAME_WIN:
						tInfo.set_on_game_win(trigger);
						break;
					case DogFightTiggerType.TRIGGER_INT_ON_GAME_LOSE:
						tInfo.set_on_game_lose(trigger);
						break;
					}
				}
			}
		});
		return tInfo;
	}
	
	private static DogFightTriggerInfo newInstance(){
		return new DogFightTriggerInfo();
	}

	private DogFightTrigger m_three_seconds_before_start;
	private DogFightTrigger m_on_game_start;
	private DogFightTrigger m_on_game_win;
	private DogFightTrigger m_on_game_lose;
	private DogFightTriggerInfo(){}

	public DogFightTriggerInfo set_three_seconds_before_start(DogFightTrigger three_seconds_before_start){
		m_three_seconds_before_start = three_seconds_before_start;
		return this;
	}
	public DogFightTriggerInfo set_on_game_start(DogFightTrigger on_game_start){
		m_on_game_start = on_game_start;
		return this;
	}
	public DogFightTriggerInfo set_on_game_win(DogFightTrigger on_game_win){
		m_on_game_win = on_game_win;
		return this;
	}
	public DogFightTriggerInfo set_on_game_lose(DogFightTrigger on_game_lose){
		m_on_game_lose = on_game_lose;
		return this;
	}
	public DogFightTrigger get_three_seconds_before_start(){
		return m_three_seconds_before_start;
	}
	public DogFightTrigger get_on_game_start(){
		return m_on_game_start;
	}
	public DogFightTrigger get_on_game_win(){
		return m_on_game_win;
	}
	public DogFightTrigger get_on_game_lose(){
		return m_on_game_lose;
	}

}

