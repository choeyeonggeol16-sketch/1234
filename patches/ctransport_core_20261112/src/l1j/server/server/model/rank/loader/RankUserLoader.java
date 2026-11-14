package l1j.server.server.model.rank.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import LIN.SC_TOP_RANKER_NOTI;
import l1j.server.server.utils.SQLUtil;

public class RankUserLoader {
    private static volatile RankUserLoader _instance;
    public static RankUserLoader getInstance(){
        RankUserLoader current = _instance;
        if (current != null) return current;
        synchronized (RankUserLoader.class) {
            current = _instance;
            if (current == null) _instance = current = new RankUserLoader();
        }
        return current;
    }
    
    public void release() {
        if(_instance != null){
            _instance.dispose();
            _instance = null;
        }
    }
    
    public static void reload(){
        RankUserLoader tmp = _instance;
        _instance = new RankUserLoader();

        if(tmp != null){
            tmp.dispose();
            tmp = null;
        }
    }
    
    private ConcurrentHashMap<Integer, LIN.SC_TOP_RANKER_NOTI> _ranks;
    private ArrayList<LIN.SC_TOP_RANKER_NOTI> _dummys;
    private RankUserLoader(){
        Connection 			con		= null;
        PreparedStatement 	pstm 	= null;
        ResultSet 			rs 		= null;
        try{
            _dummys = new ArrayList<LIN.SC_TOP_RANKER_NOTI>(RankLoadManager.MRK_SYS_TOTAL_RANGE);
            for(int i=RankLoadManager.MRK_SYS_TOTAL_RANGE - 1; i>=0; --i)
                _dummys.add(LIN.SC_TOP_RANKER_NOTI.newDummyInstance());
            
            con = DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement("select * from characters where level>=? and Banned=?");
            pstm.setInt(1, RankLoadManager.MRK_SYS_MINLEVEL);
            pstm.setInt(2, 0);
            rs = pstm.executeQuery();
            int rows = SQLUtil.calcRows(rs);
            _ranks = new ConcurrentHashMap<Integer, LIN.SC_TOP_RANKER_NOTI>(Config.MAX_ONLINE_USERS > rows ? Config.MAX_ONLINE_USERS : rows);
            while(rs.next()){
                if(rs.getInt("AccessLevel") == Config.\u0047\u004D\u0043\u004F\u0044\u0045)
                    continue;
                
                if(isBanAccounts(rs.getString("account_name")))
                    continue;
                
                put(LIN.SC_TOP_RANKER_NOTI.newInstance(rs));
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            SQLUtil.close(rs, pstm, con);
        }
    }
    
    private boolean isBanAccounts(String login){
        Connection 			con		= null;
        PreparedStatement 	pstm 	= null;
        ResultSet 			rs 		= null;
        try{
            con						= DatabaseFactory.getInstance().getConnection();
            pstm					= con.prepareStatement("select Banned from accounts where login=?");			
            pstm.setString(1, login);
            rs						= pstm.executeQuery();
            if(rs.next())
                return rs.getInt("Banned") != 0;
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            SQLUtil.close(rs, pstm, con);
        }
        return false;
    }
    
    public LIN.SC_TOP_RANKER_NOTI create_user_information(L1PcInstance pc){
        LIN.SC_TOP_RANKER_NOTI noti = LIN.SC_TOP_RANKER_NOTI.newInstance(pc);
        put(noti);
        return noti;
    }
    
    public void onUser(L1PcInstance pc){
        if(pc == null || pc.isGm()){
            return;
        }
        
        LIN.SC_TOP_RANKER_NOTI noti = get(pc.getId());
    //    System.out.println("[Rank] onUser " + pc.getName());
        if(noti == null){
            if(pc.getLevel() < RankLoadManager.MRK_SYS_MINLEVEL)
                return;
            
            noti = create_user_information(pc);
            noti.onBuff();
        }else{
            noti.set_characterInstance(pc);
        }
        noti.syncBuff();
        if(!noti.class_ranker().name().equalsIgnoreCase(pc.getName()))
            noti.class_ranker().set_name(pc.getName());
        if(!noti.total_ranker().name().equalsIgnoreCase(pc.getName()))
            noti.total_ranker().set_name(pc.getName());
    }
    
    public void offUser(L1PcInstance pc){
        if(pc == null || pc.isGm())
            return;
        
        pc.getInventory().findAndRemoveItemId(RankLoadManager.MRK_TOPPROTECTION_ID);
        LIN.SC_TOP_RANKER_NOTI noti = get(pc.getId());
        if(noti != null)
            noti.set_characterInstance(null);
    }
    
    public void banUser(L1PcInstance pc){
        if(pc == null || pc.isGm())
            return;
        pc.getInventory().findAndRemoveItemId(RankLoadManager.MRK_TOPPROTECTION_ID);
        LIN.SC_TOP_RANKER_NOTI noti = remove(pc.getId());
        
        if(noti != null)
            noti.set_characterInstance(null);
    }
    
    public void removeUser(L1PcInstance pc){
        if(pc == null || pc.isGm())
            return;
        
        pc.getInventory().findAndRemoveItemId(RankLoadManager.MRK_TOPPROTECTION_ID);
        remove(pc.getId());
    
    }

    public void offBuff(){
        ThreadPool.getInstance().execute(new Runnable(){
            @Override
            public void run(){
                try{
                    createStream()
                    .filter((LIN.SC_TOP_RANKER_NOTI noti) -> noti.get_characterInstance() != null)
                    .forEach((LIN.SC_TOP_RANKER_NOTI noti) ->{
                        try{
                            noti.offBuff();
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    });
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }
    
    public Stream<LIN.SC_TOP_RANKER_NOTI> createStream(){
        return _ranks.values().size() >= 1024 ? _ranks.values().parallelStream() : _ranks.values().stream();
    }
    
    public LIN.SC_TOP_RANKER_NOTI get(int id){
        return _ranks.get(id);
    }
    
    public void put(LIN.SC_TOP_RANKER_NOTI rnk){
        _ranks.put(rnk.get_objectId(), rnk);
    }
    
    public LIN.SC_TOP_RANKER_NOTI remove(int id){
        return _ranks.remove(id);
    }
    
    public LIN.SC_TOP_RANKER_NOTI remove(L1PcInstance pc){
        return _ranks.remove(pc.getId());
    }
    
    public LIN.SC_TOP_RANKER_NOTI remove(LIN.SC_TOP_RANKER_NOTI rnk){
        return _ranks.remove(rnk.get_objectId());
    }
    
    public ArrayList<LIN.SC_TOP_RANKER_NOTI> createSortSnapshot(){
        ArrayList<LIN.SC_TOP_RANKER_NOTI> rankers = createSnapshot();
        if(rankers != null){
            rankers.addAll(_dummys);
            Collections.sort(rankers);
        }
        return rankers;
    }
    
    public ArrayList<LIN.SC_TOP_RANKER_NOTI> createSnapshot(){
        return _ranks.size() <= 0 ? null : new ArrayList<LIN.SC_TOP_RANKER_NOTI>(_ranks.values());		
    }
    
    public boolean isRankPoly(L1PcInstance pc){
        LIN.SC_TOP_RANKER_NOTI noti = get(pc.getId());
        if(noti == null)
            return false;
        
        return noti.total_ranker().rating() > 0 || noti.class_ranker().rank() <= 3;
    }
    
    public int getRankLevel(L1PcInstance pc){
        LIN.SC_TOP_RANKER_NOTI noti = get(pc.getId());
        return noti == null ? 0 :noti.total_ranker().rating();
    }
    
    public LIN.SC_TOP_RANKER_NOTI getRankNoti(L1PcInstance pc){
        LIN.SC_TOP_RANKER_NOTI noti = get(pc.getId());
        return noti;
    }
    
    public void dispose(){
        if(_ranks != null){
            _ranks.clear();
            _ranks = null;
        }
    }
}