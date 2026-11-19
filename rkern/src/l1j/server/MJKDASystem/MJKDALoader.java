package l1j.server.MJKDASystem;
/**********************************
 * 
 * MJ Kill Death Assist Object Loader.
 * made by mjsoft, 2017.
 *  
 **********************************/
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.MJKDASystem.Chart.MJKDAChartScheduler;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class MJKDALoader {
	private static MJKDALoader _instance;
	public static MJKDALoader getInstance(){
		if(_instance == null)
			_instance = new MJKDALoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.store();
			_instance.clear();
			_instance = null;
		}
	}
	
	private ConcurrentHashMap<Integer, MJKDA> _kdas;
	private MJKDALoader(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con		= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from tb_kda");
			rs		= pstm.executeQuery();
			_kdas 	= new ConcurrentHashMap<Integer, MJKDA>(SQLUtil.calcRows(rs) * 2);
			while(rs.next()){
				MJKDA kda 		= new MJKDA();
				kda.objid 		= rs.getInt("objid");
				kda.name		= rs.getString("name");
				kda.kill		= rs.getInt("killing");
				kda.death		= rs.getInt("death");
				kda.isChartView	= rs.getBoolean("is_chart");
				_kdas.put(kda.objid, kda);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public void getChangeName(int objid, String newName){
		MJKDA kda = _kdas.get(objid);
		if(kda != null){
			kda.name = newName;
			_kdas.put(kda.objid, kda);
		}
	}
	
	public ArrayList<MJKDA> createSnapshot(){
		return new ArrayList<MJKDA>(_kdas.values());
	}
	
	public void install(L1PcInstance pc, boolean isBot){
		if(pc == null)
			return;
		
		MJKDA kda = _kdas.get(pc.getId());
		if(kda == null){
			kda = new MJKDA();
			kda.objid 		= pc.getId();
			kda.name		= pc.getName();
			kda.kill 			= 0;
			kda.death		= 0;
			kda.isBot		= isBot;
			kda.isChartView = true;
			_kdas.put(kda.objid, kda);
		} else if (kda.name == null || !kda.name.equals(pc.getName())) {
			kda.name = pc.getName(); // [킬뎃 개명반영] 로그인 시 현재 캐릭명으로 갱신
		}
		pc.setKDA(kda);
		
		if(MJKDALoadManager.KDA_CHART_RUN && kda.isChartView)
			MJKDAChartScheduler.getInstance().onLoginUser(pc);
	}
	
    public static int orderKey() { return CacheD.accountOrderKey("$kda-store-global"); }
    private static final class Row {
        final int owner, kill, death;
        final String name;
        final boolean chart;
        Row(MJKDA kda) {
            owner=kda.objid; name=kda.name; kill=kda.kill; death=kda.death; chart=kda.isChartView;
        }
    }
    private static void persistRows(ArrayList<Row> rows) {
        try (Connection con=DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps=con.prepareStatement(
                        "INSERT INTO tb_kda SET objid=?,name=?,killing=?,death=?,is_chart=? "
                        + "ON DUPLICATE KEY UPDATE name=?,killing=?,death=?,is_chart=?")) {
                    for(Row row:rows) {
                        ps.setInt(1,row.owner); ps.setString(2,row.name); ps.setInt(3,row.kill); ps.setInt(4,row.death);
                        ps.setString(5,row.chart?"true":"false"); ps.setString(6,row.name);
                        ps.setInt(7,row.kill); ps.setInt(8,row.death); ps.setString(9,row.chart?"true":"false");
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                con.commit();
            } catch (SQLException|RuntimeException failure) {
                try { con.rollback(); } catch(SQLException rollback) {failure.addSuppressed(rollback);}
                throw failure;
            }
        } catch(SQLException failure) {throw new IllegalStateException("K/D snapshot save failed",failure);}
    }
    /** The same global CacheD lane also owns paid resets. Snapshot and enqueue
     * are under this monitor so an old image cannot enter behind a reset.
     */
    public synchronized void store() {
        if(_kdas==null || _kdas.isEmpty())return;
        ArrayList<Row> rows=new ArrayList<>();
        for(MJKDA kda:_kdas.values()) {
            if(kda==null || kda.isBot || CacheD.getInstance().hasPendingOwnerApply(kda.objid))continue;
            synchronized(kda) { rows.add(new Row(kda)); }
        }
        if(!rows.isEmpty())CacheD.getInstance().submitDbJobRetryOrdered(orderKey(),()->persistRows(rows));
    }
    /** Called only after the reset receipt's owner replay has adjusted counters.
     * The new immutable row is ordered after that receipt; no SQL runs here.
     */
    public synchronized void storeCommittedReset(MJKDA kda) {
        ArrayList<Row> rows=new ArrayList<>(1);
        synchronized(kda) {rows.add(new Row(kda));}
        CacheD.getInstance().submitDbJobRetryOrdered(kda.objid,orderKey(),()->persistRows(rows));
    }

	public void clear(){
		if(_kdas != null){
			_kdas.clear();
			_kdas = null;
		}
	}
}
