package l1j.server.MJCompanion.Instance;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;

public class MJCompanionNameHandler {
	public static boolean exists_name(String name){
		final MJObjectWrapper<Boolean> wrapper = new MJObjectWrapper<Boolean>();
		wrapper.value = false;
		Selector.exec("select companion_name from companion_instance where lower(companion_name)=?", new SelectorHandler(){

			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, name.toLowerCase());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				wrapper.value = rs.next();
			}
			
		});
		return wrapper.value;
	}
}
