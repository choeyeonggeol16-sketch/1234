package l1j.server.server.Opcodes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class ServerOpcodesTable {
	private static Logger _log = Logger.getLogger(ServerOpcodesTable.class.getName());
	private static ServerOpcodesTable _instance;
	private static List<ServerOpcodes> list = new ArrayList<ServerOpcodes>();

	public static ServerOpcodesTable getInstance() {
		if (_instance == null) {
			_instance = new ServerOpcodesTable();
		}
		return _instance;
	}

	private ServerOpcodesTable() {
		load();
	}

	private void load() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM server_opcodes");
			rs = pstm.executeQuery();
			while (rs.next()) {
				ServerOpcodes so = new ServerOpcodes();
				so.setUid(rs.getInt("uid"));
				so.setType(rs.getString("type").equalsIgnoreCase("client") ? ServerOpcodes.TYPE.Client : ServerOpcodes.TYPE.Server);
				so.setName(rs.getString("name"));
				so.setNameOp(rs.getString("name_op"));
				so.setOp(rs.getInt("now_op"));				
				list.add(so);
			}
			Opcodes.init();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public static int ClientOpcodeFind(int value){
		for(ServerOpcodes so : list) {
			if(so.getUid()==value && ServerOpcodes.TYPE.Client==so.getType()){
				return so.getOp();
			}
		}
		return 255;
	}
	
	public static int ServerOpcodeFind(int value){
		for(ServerOpcodes so : list) {
			if(so.getUid()==value && ServerOpcodes.TYPE.Server==so.getType()){
				return so.getOp();
			}
		}
		return 255;
	}
	
	public static int getSize(){
		return list.size();
	}
}
