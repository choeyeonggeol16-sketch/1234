package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class RaidUserTimeStore implements Runnable{
	private static Logger _log = Logger.getLogger(RaidUserTimeStore.class.getName());
	
	private ArrayList<L1PcInstance> _pcs;
	
	public RaidUserTimeStore(ArrayList<L1PcInstance> pcs){
		_pcs = pcs;
	}
	
	@Override
	public void run() {
		if(_pcs == null || _pcs.size() <= 0)
			return;
		
		Connection 			con		= null;
		PreparedStatement 	pstm	= null;
		L1PcInstance 		pc		= null;
		Account				acc		= null;
		try{
			con = DatabaseFactory.getInstance().getConnection();
			con.setAutoCommit(false);
			pstm	= con.prepareStatement("UPDATE accounts SET raid_buff=? WHERE login = ?");
			int idx	= 0;
			int size = _pcs.size();
			for(int i=0; i<size; i++){
				idx = 0;
				pc 	= _pcs.get(i);
				if(pc == null || pc.getNetConnection() == null)
					continue;
				
				acc = pc.getAccount();
				if(acc == null)
					continue;
				
				pstm.setTimestamp(++idx, acc.getDragonRaid());
				pstm.setString(++idx, acc.getName());
				pstm.addBatch();
				pstm.clearParameters();
			}
			pstm.executeBatch();
			pstm.clearBatch();
			con.commit();
			
		}catch(Exception e){
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			StringBuilder sb = new StringBuilder();
			sb.append("[ERROR - RaidUserTimeStore]...");
			if(pc !=null)
				sb.append(pc.getId());
			sb.append(" read error. \r\n").append(e.getLocalizedMessage());
			_log.log(Level.SEVERE, sb.toString(), e);
			System.out.println(sb.toString());
			e.printStackTrace();
		} finally {
			try {
				con.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
