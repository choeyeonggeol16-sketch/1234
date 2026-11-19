package l1j.server.MJCompanion.Basic.Exp;

import java.sql.ResultSet;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionExpPenalty {
	private static int[] m_companion_penalties;
	public static void do_load(){
		int[] companion_penalties = new int[128];
		Selector.exec("select * from companion_penalty", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					companion_penalties[rs.getInt("level")] = rs.getInt("penalty");
			}
		});
		m_companion_penalties = companion_penalties;
	}
	
	public static int get_companion_penalty(int level){
		return m_companion_penalties[level];
	}
	
	public static double get_companion_penalty_rate(int level) {
		return 1.0 / m_companion_penalties[level];
	}
}
