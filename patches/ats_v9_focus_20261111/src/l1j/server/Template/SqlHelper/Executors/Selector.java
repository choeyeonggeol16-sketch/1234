package l1j.server.Template.SqlHelper.Executors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.L1DatabaseFactory;
import l1j.server.Template.SqlHelper.Executor;
import l1j.server.Template.SqlHelper.Handler.Handler;
import l1j.server.Template.SqlHelper.Handler.SelectorHandler;
import l1j.server.server.utils.SQLUtil;

public class Selector implements Executor{
	public static void exec(String query, SelectorHandler handler){
		new Selector().execute(query, handler);
	}
	
	@Override
	public int execute(String query, Handler handler){
		if(!(handler instanceof SelectorHandler))
			throw new IllegalArgumentException("handler is not SelectorHandler...!");
		
		SelectorHandler sHandler = (SelectorHandler)handler;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try{
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(query);
			sHandler.handle(pstm);
			rs = pstm.executeQuery();
			sHandler.result(rs);
			return 1;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
		return 0;
	}
}
