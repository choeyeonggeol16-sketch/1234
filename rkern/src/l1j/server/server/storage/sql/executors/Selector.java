package l1j.server.server.storage.sql.executors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.sql.Executor;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.utils.SQLUtil;

public class Selector implements Executor{
	public static void exec(String query, SelectorHandler handler){
		new Selector().execute(query, handler);
	}
	
	@Override
	public void execute(String query, Handler handler){
		if(!(handler instanceof SelectorHandler))
			throw new IllegalArgumentException("handler is not SelectorHandler...!");
		
		SelectorHandler sHandler = (SelectorHandler)handler;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try{
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(query);
			sHandler.handle(pstm);
			rs = pstm.executeQuery();
			sHandler.result(rs);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
}
