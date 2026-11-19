package l1j.server.server.model.BookQuest.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l1j.server.DatabaseFactory;
import java.sql.ResultSet;
import java.util.Collection;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;

public class BQSCharacterDataLoader {
    public static void in(L1PcInstance pc){
        loadCharacterBqs(pc)
        .realloc_decks(pc)
        .send_bqs_batch(pc)
        .send_decks_noti(pc);

    }
    
    public static BQSCharacterData readSnapshot(int characterId) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "select character_data from tb_mbook_characterInfo where character_id=?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return BQSCharacterData.newInstance(rs);
            }
        } catch (Exception e) {
            throw new IllegalStateException("BQS DB read failed: " + characterId, e);
        }
        BQSCharacterData data = BQSCharacterData.newInstance();
        data.set_object_id(characterId);
        return data;
    }

    public static void applySnapshot(L1PcInstance pc, BQSCharacterData data) {
        if (data == null) return;
        pc.setBqs(data);
        data.send_bqs_batch(pc, false).send_decks_noti(pc);
    }

    public static void out(L1PcInstance pc){
        BQSCharacterData bqs = pc.getBqs();
        if(bqs == null)
            return;
        
        storeCharacterBqs(bqs, true);
        pc.setBqs(null);
    }
    
    public static BQSCharacterData loadCharacterBqs(L1PcInstance pc){
        Selector.exec("select * from tb_mbook_characterInfo where character_id=?", new SelectorHandler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, pc.getId());
            }
            @Override
            public void result(ResultSet rs) throws Exception {
                if(rs.next()){
                    pc.setBqs(BQSCharacterData.newInstance(rs));
                }else{
                    BQSCharacterData bqs = BQSCharacterData.newInstance();
                    bqs.set_object_id(pc.getId());
                    pc.setBqs(bqs);
                }
            }
        });
        return pc.getBqs();
    }
    
    public static void storeCharacterBqs(BQSCharacterData bqs, boolean isDisposeBqs) {
        if (bqs == null) return;
        try {
            final int characterId = bqs.get_object_id();
            final byte[] bytes = bqs.save();
            CacheD.getInstance().submitDbJobOrdered(characterId, () -> {
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "insert into tb_mbook_characterInfo set character_id=?, character_data=? on duplicate key update character_data=?")) {
                    ps.setInt(1, characterId);
                    ps.setBytes(2, bytes);
                    ps.setBytes(3, bytes);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException("BQS DB save failed: " + characterId, e);
                }
            });
            if (isDisposeBqs) bqs.dispose();
        } catch (Exception e) {
            throw new IllegalStateException("BQS snapshot/save failed", e);
        }
    }

    public static void deleteCharactersBps(Collection<L1PcInstance> col){
        Updator.exec("truncate table tb_mbook_characterInfo", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {}
        });
        
        for(L1PcInstance pc : col){
            if(pc == null)
                continue;
            BQSCharacterData bqs = pc.getBqs();
            if(bqs == null)
                continue;
            
            bqs.dispose();
            pc.setBqs(null);
        }
    }
    
    public static void deleteCharacterBps(L1PcInstance pc, boolean isDisposeBps){
        deleteCharacterBps(pc.getId());
        if(isDisposeBps){
            BQSCharacterData bqs = pc.getBqs();
            if(bqs != null){
                bqs.dispose();
                pc.setBqs(null);
            }
        }
    }
    
    public static void deleteCharacterBps(int object_id) {
        CacheD.getInstance().submitDbJobOrdered(object_id, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "delete from tb_mbook_characterInfo where character_id=?")) {
                ps.setInt(1, object_id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("BQS DB delete failed: " + object_id, e);
            }
        });
    }

}