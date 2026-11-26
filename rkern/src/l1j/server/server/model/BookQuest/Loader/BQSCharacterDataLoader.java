package l1j.server.server.model.BookQuest.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.MonsterBookV2Info.DeckT;
import l1j.server.server.packets.Protocol.MainServer.Proto.MonsterBook.eMonsterBookV2DeckState;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.storage.sql.handler.SelectorHandler;

public class BQSCharacterDataLoader {
    private static final String LOCK_SNAPSHOT =
            "SELECT character_data FROM tb_mbook_characterInfo WHERE character_id=? FOR UPDATE";
    private static final String UPDATE_SNAPSHOT =
            "UPDATE tb_mbook_characterInfo SET character_data=? WHERE character_id=?";
    private static final String INSERT_SNAPSHOT =
            "INSERT INTO tb_mbook_characterInfo (character_id, character_data) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE character_id=character_id";

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
            CacheD.getInstance().submitDbJobOrdered(characterId, () -> saveSnapshot(characterId, bytes));
            if (isDisposeBqs) bqs.dispose();
        } catch (Exception e) {
            throw new IllegalStateException("BQS snapshot/save failed", e);
        }
    }

    public static void stageWeeklyReward(L1InventoryTransaction tx, int characterId, long version, int deckIndex) {
        if (characterId <= 0 || version <= 0 || deckIndex < 0)
            throw new IllegalArgumentException("Invalid weekly Monster Book claim");
        tx.requireTables("tb_mbook_characterInfo");
        tx.sql(con -> {
            final BQSCharacterData saved = lockSnapshot(con, characterId);
            if (saved == null || saved.get_object_id() != characterId || saved.get_decks_version() != version)
                throw new SQLException("Weekly Monster Book snapshot changed: " + characterId);
            final DeckT deck = saved.get_deck(deckIndex);
            if (deck == null || deck.get_state() != eMonsterBookV2DeckState.DS_COMPLETED)
                throw new SQLException("Weekly Monster Book deck is not claimable: " + characterId);
            deck.set_state(eMonsterBookV2DeckState.DS_REWARDED);
            if (updateSnapshot(con, characterId, saved.save()) != 1)
                throw new SQLException("Weekly Monster Book claim was not persisted: " + characterId);
        });
    }

    private static BQSCharacterData lockSnapshot(Connection con, int characterId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(LOCK_SNAPSHOT)) {
            ps.setInt(1, characterId);
            try (ResultSet rows = ps.executeQuery()) {
                return rows.next() ? BQSCharacterData.load(rows.getBytes(1)) : null;
            }
        }
    }

    private static int updateSnapshot(Connection con, int characterId, byte[] bytes) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(UPDATE_SNAPSHOT)) {
            ps.setBytes(1, bytes);
            ps.setInt(2, characterId);
            return ps.executeUpdate();
        }
    }

    private static void saveSnapshot(int characterId, byte[] bytes) {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                final BQSCharacterData incoming = BQSCharacterData.load(bytes);
                if (!incoming.has_object_id() || incoming.get_object_id() != characterId)
                    throw new SQLException("Invalid Monster Book snapshot owner: " + characterId);
                // Establish the key first. Locking a missing row before
                // INSERT could deadlock other first-time character saves.
                try (PreparedStatement ps = con.prepareStatement(INSERT_SNAPSHOT)) {
                    ps.setInt(1, characterId);
                    ps.setBytes(2, bytes);
                    ps.executeUpdate();
                }
                final BQSCharacterData saved = lockSnapshot(con, characterId);
                if (saved == null || (saved.has_object_id() && saved.get_object_id() != characterId))
                    throw new SQLException("Monster Book snapshot owner changed: " + characterId);
                final long storedVersion = saved.get_decks_version();
                final long incomingVersion = incoming.get_decks_version();
                if (incomingVersion >= storedVersion) {
                    if (incomingVersion == storedVersion && preserveWeeklyClaims(saved, incoming))
                        bytes = incoming.save();
                    updateSnapshot(con, characterId, bytes);
                }
                con.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    con.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw new IllegalStateException("BQS DB save failed: " + characterId, failure);
        }
    }

    private static boolean preserveWeeklyClaims(BQSCharacterData saved, BQSCharacterData incoming) throws SQLException {
        if (!saved.has_decks()) return false;
        boolean changed = false;
        for (int index = 0; index < saved.get_decks().size(); index++) {
            final DeckT rewarded = saved.get_deck(index);
            if (rewarded == null || rewarded.get_state() != eMonsterBookV2DeckState.DS_REWARDED) continue;
            final DeckT deck = incoming.get_deck(index);
            if (deck == null) throw new SQLException("Monster Book snapshot loses a rewarded deck: " + index);
            if (deck.get_state() != eMonsterBookV2DeckState.DS_REWARDED) {
                deck.set_state(eMonsterBookV2DeckState.DS_REWARDED);
                changed = true;
            }
        }
        return changed;
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
