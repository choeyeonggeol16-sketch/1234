package l1j.server.server.model.character.companion.instance;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.server.shared.ObjectWrapper;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;

public class CompanionNameHandler {
	public static boolean exists_name(String name){
		final ObjectWrapper<Boolean> wrapper = new ObjectWrapper<Boolean>();
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
