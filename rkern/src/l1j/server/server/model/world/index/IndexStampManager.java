package l1j.server.server.model.world.index;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.storage.sql.handler.SelectorHandler;

public class IndexStampManager {
	public static void update(StampIndex index){
		update(index, CGameTime.getRealTimeCalendar());
	}
	
	public static void update(StampIndex index, Calendar cal){
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
	
	public static Calendar select(StampIndex index){
		Calendar cal = CGameTime.getRealTimeCalendar();
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
