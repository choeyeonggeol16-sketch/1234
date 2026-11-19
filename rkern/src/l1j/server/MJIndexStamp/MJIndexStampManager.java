package l1j.server.MJIndexStamp;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.gametime.RealTimeClock;

public class MJIndexStampManager {
	public static void update(MJEStampIndex index){
		update(index, RealTimeClock.getInstance().getRealTimeCalendar());
	}
	
	public static void update(MJEStampIndex index, Calendar cal){
		Updator.exec("insert into tb_index_stamp set idx=?, stamp=? on duplicate key update stamp=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				Timestamp ts = new Timestamp(cal.getTimeInMillis());
				pstm.setInt(1, index.to_int());
				pstm.setTimestamp(2, ts);
				pstm.setTimestamp(3, ts);
			}
		});
	}
	
	public static Calendar select(MJEStampIndex index){
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		long millis = cal.getTimeInMillis();
		Selector.exec("select * from tb_index_stamp where idx=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, index.to_int());
			}
			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next()){
					cal.setTimeInMillis(rs.getTimestamp("stamp").getTime());
				}
			}
		});
		return millis == cal.getTimeInMillis() ? null : cal;
	}
}
