package l1j.server.MJPassiveSkill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l1j.server.DatabaseFactory;
import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.BatchHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;
import LIN.SC_ADD_ACTIVE_SPELL_EX_NOTI;
import LIN.SC_ALL_SPELL_PASSIVE_NOTI;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;

public class MJPassiveUserLoader {

    public static void load(L1PcInstance pc){
        MJPassiveLoader loader = MJPassiveLoader.getInstance();
        Selector.exec("select * from passive_user_info where character_id=?", new SelectorHandler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                pstm.setInt(1, pc.getId());
            }

            @Override
            public void result(ResultSet rs) throws Exception {
                while(rs.next()){
                    int passiveId = rs.getInt("passive_id");
                    MJPassiveInfo pInfo = loader.fromPassiveId(passiveId);
                    if(pInfo == null)
                        continue;
                    
                    pc.addPassive(pInfo);
                }
            }
        });
        final ArrayList<MJPassiveInfo> passives = pc.getPassives();
        if(passives == null || passives.size() <= 0)
            return;
        
        LIN.SC_ALL_SPELL_PASSIVE_NOTI.sendPassives(pc, passives);
    }
    
    public static int[] readIds(int characterId) {
        int[] ids = new int[8];
        int count = 0;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "select passive_id from passive_user_info where character_id=?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (count == ids.length) {
                        int[] bigger = new int[ids.length << 1];
                        System.arraycopy(ids, 0, bigger, 0, count);
                        ids = bigger;
                    }
                    ids[count++] = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Passive DB read failed: " + characterId, e);
        }
        if (count == ids.length) return ids;
        int[] result = new int[count];
        System.arraycopy(ids, 0, result, 0, count);
        return result;
    }

    public static void applyLoaded(L1PcInstance pc, int[] ids) {
        if (ids == null) return;
        final MJPassiveLoader loader = MJPassiveLoader.getInstance();
        for (int i = 0; i < ids.length; i++) {
            MJPassiveInfo info = loader.fromPassiveId(ids[i]);
            if (info != null) pc.addPassive(info);
        }
        final ArrayList<MJPassiveInfo> passives = pc.getPassives();
        if (passives == null || passives.isEmpty()) return;
        LIN.SC_ALL_SPELL_PASSIVE_NOTI.sendPassives(pc, passives);
    }

    public static void store(L1PcInstance pc, MJPassiveInfo pInfo){
        final int characterId = pc.getId();
        final int passvieId = pInfo.getPassiveId();
        final String passiveName = pInfo.getPassiveName();
        Updator.exec("insert into passive_user_info set character_id=?, passive_id=?, passive_name=?", new Handler(){
            @Override
            public void handle(PreparedStatement pstm) throws Exception {
                int idx = 0;
                pstm.setInt(++idx, characterId);
                pstm.setInt(++idx, passvieId);
                pstm.setString(++idx, passiveName);
            }
        });
        if(passvieId == MJPassiveID.ARMOR_GUARD.toInt())
            LIN.SC_ADD_ACTIVE_SPELL_EX_NOTI.send(pc, pInfo.getPassiveId(), 10);
        else
            LIN.SC_ADD_ACTIVE_SPELL_EX_NOTI.send(pc, pInfo.getPassiveId(), 0);
    }
    
    public static void store(L1PcInstance pc){
        final ArrayList<MJPassiveInfo> passives = pc.getPassives();
        if(passives == null || passives.size() <= 0)
            return;
        
        final int characterId = pc.getId();
        Updator.batch("insert ignore into passive_user_info set character_id=?, passive_id=?, passive_name=?", new BatchHandler(){
            @Override
            public void handle(PreparedStatement pstm, int callNumber) throws Exception {
                MJPassiveInfo pInfo = passives.get(callNumber);
                int idx = 0;
                pstm.setInt(++idx, characterId);
                pstm.setInt(++idx, pInfo.getPassiveId());
                pstm.setString(++idx, pInfo.getPassiveName());
            }
        }, passives.size());
    }
}