package l1j.server.DogFight.Game.Trigger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;

public class MJDogFightTriggerInfo {
	public static MJDogFightTriggerInfo do_load(final int game_id, final int corner_id, final int member_id){
		final MJDogFightTriggerInfo tInfo = newInstance();
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
					MJDogFightTrigger trigger = MJDogFightTrigger.newInstance(rs);
					switch(trigger.get_trigger_type().to_int()){
					case MJDogFightTiggerType.TRIGGER_INT_3SECONDS_BEFORE_THE_START:
						tInfo.set_three_seconds_before_start(trigger);
						break;
					case MJDogFightTiggerType.TRIGGER_INT_ON_GAME_START:
						tInfo.set_on_game_start(trigger);
						break;
					case MJDogFightTiggerType.TRIGGER_INT_ON_GAME_WIN:
						tInfo.set_on_game_win(trigger);
						break;
					case MJDogFightTiggerType.TRIGGER_INT_ON_GAME_LOSE:
						tInfo.set_on_game_lose(trigger);
						break;
					}
				}
			}
		});
		return tInfo;
	}
	
	private static MJDogFightTriggerInfo newInstance(){
		return new MJDogFightTriggerInfo();
	}

	private MJDogFightTrigger m_three_seconds_before_start;
	private MJDogFightTrigger m_on_game_start;
	private MJDogFightTrigger m_on_game_win;
	private MJDogFightTrigger m_on_game_lose;
	private MJDogFightTriggerInfo(){}

	public MJDogFightTriggerInfo set_three_seconds_before_start(MJDogFightTrigger three_seconds_before_start){
		m_three_seconds_before_start = three_seconds_before_start;
		return this;
	}
	public MJDogFightTriggerInfo set_on_game_start(MJDogFightTrigger on_game_start){
		m_on_game_start = on_game_start;
		return this;
	}
	public MJDogFightTriggerInfo set_on_game_win(MJDogFightTrigger on_game_win){
		m_on_game_win = on_game_win;
		return this;
	}
	public MJDogFightTriggerInfo set_on_game_lose(MJDogFightTrigger on_game_lose){
		m_on_game_lose = on_game_lose;
		return this;
	}
	public MJDogFightTrigger get_three_seconds_before_start(){
		return m_three_seconds_before_start;
	}
	public MJDogFightTrigger get_on_game_start(){
		return m_on_game_start;
	}
	public MJDogFightTrigger get_on_game_win(){
		return m_on_game_win;
	}
	public MJDogFightTrigger get_on_game_lose(){
		return m_on_game_lose;
	}

}

