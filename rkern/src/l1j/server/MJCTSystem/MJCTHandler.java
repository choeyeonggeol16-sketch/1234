package l1j.server.MJCTSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.Loader.MJCTSystemLoader;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Character marble ownership and inventory changes share one durable receipt. */
public final class MJCTHandler {
    private static final Logger LOG = Logger.getLogger(MJCTHandler.class.getName());
    private static final String ESCROW = "[MJCTSYSTEM]";
    private MJCTHandler() {}

    private static boolean admit(L1PcInstance pc, L1ItemInstance item, int expected) {
        if (pc == null || item == null || item.getItemId() != expected
                || pc.getInventory().getItem(item.getId()) != item || pc.getAccount() == null
                || pc.getAccountName() == null || ESCROW.equalsIgnoreCase(pc.getAccountName())) return false;
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "마을에서만 사용하실 수 있습니다.");
            return false;
        }
        return true;
    }

    public static boolean load(L1PcInstance pc, L1ItemInstance item) {
        if (!admit(pc, item, MJCTLoadManager.CTSYSTEM_LOAD_ID)
                || item.isStackable() || item.getCount() != 1) return false;
        final String account = pc.getAccountName();
        final var tam = pc.getAccount().getTamHandler();
        final int callerId = pc.getId(), marbleId = item.getId();
        final TradeResult result = new TradeResult();
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc, CacheD.accountOrderKey(account))) {
            if (!tx.debit(item, 1)) return false;
            tx.requireTables("accounts", "characters", "tb_mjct_mapping");
            tx.sql(con -> redeem(con, account, callerId, marbleId, result));
            tx.onFailure(() -> SC_SystemMessage.send(pc, result.message));
            tx.onDurableCommit(() -> {
                MJCTSystemLoader.removeCommitted(marbleId);
                tam.invalidateCache();
            });
            CDB.RequestUseItem(pc, tx, () -> {
                updateCharacterStatus(pc);
            });
            return true; // submitted, not yet committed
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Character marble redemption rejected", e);
            SC_SystemMessage.send(pc, result.message);
            return false;
        }
    }

    public static boolean store(L1PcInstance pc, L1ItemInstance item, int objid) {
        return store(pc, item, objid, null);
    }

    public static boolean store(L1PcInstance pc, L1ItemInstance item, int objid, Runnable completion) {
        if (!admit(pc, item, MJCTLoadManager.CTSYSTEM_STORE_ID)) return false;
        if (pc.getId() == objid) {
            SC_SystemMessage.send(pc, "자기 자신에게는 사용할 수 없습니다.");
            return false;
        }
        if (objid <= 0 || L1World.getInstance().findObject(objid) != null) {
            SC_SystemMessage.send(pc, MJCTLoadManager._invalidCharacter);
            return false;
        }
        final String account = pc.getAccountName();
        final var tam = pc.getAccount().getTamHandler();
        final int callerId = pc.getId();
        final TradeResult result = new TradeResult();
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc, CacheD.accountOrderKey(account))) {
            if (!tx.debit(item, 1)) return false;
            L1ItemInstance reward = tx.grant(MJCTLoadManager.CTSYSTEM_LOAD_ID, 1);
            if (reward == null || reward.isStackable()) return false;
            final int marbleId = reward.getId();
            tx.requireTables("accounts", "characters", "tam", "tb_mjct_mapping");
            tx.sql(con -> encase(con, account, callerId, objid, marbleId, result));
            tx.onFailure(() -> SC_SystemMessage.send(pc, result.message));
            tx.onDurableCommit(() -> {
                MJCTSystemLoader.publishCommitted(result.object);
                tam.invalidateCache();
            });
            CDB.RequestUseItem(pc, tx, () -> {
                // Only the stored character lost TAM; do not clear the caller's buff.
                L1ItemInstance live = pc.getInventory().getItem(marbleId);
                if (live != null) SC_ItemName.send(pc, live);
                if (completion != null) completion.run();
                updateCharacterStatus(pc);
            });
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Character marble creation rejected", e);
            SC_SystemMessage.send(pc, result.message);
            return false;
        }
    }

    private static final class TradeResult {
        String message = MJCTLoadManager._invalidCharacter;
        MJCTObject object;
    }

    // All helpers below execute on the existing receipt's DB worker. No direct
    // commit, connection acquisition or inventory mutation is allowed here.
    private static int lockAccount(Connection con, String account) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT charslot FROM accounts WHERE login=? FOR UPDATE")) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Character trade account missing");
                return rs.getInt(1);
            }
        }
    }

    private static int lockCharacters(Connection con, String account, int callerId) throws SQLException {
        int count = 0;
        boolean caller = false;
        try (PreparedStatement ps = con.prepareStatement("SELECT objid FROM characters WHERE account_name=? FOR UPDATE")) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { count++; caller |= rs.getInt(1) == callerId; }
            }
        }
        if (!caller) throw new SQLException("Character trade caller ownership changed");
        return count;
    }

    private static MJCTObject readCharacter(Connection con, String owner, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT char_name, Str, Dex, Con, Wis, Intel, Cha, Class, level, MaxHp, MaxMp, OnlineStatus "
                + "FROM characters WHERE account_name=? AND objid=? FOR UPDATE")) {
            ps.setString(1, owner);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("OnlineStatus") != 0) throw new SQLException("Character not offline/owned");
                MJCTObject o = new MJCTObject();
                o.charId = id;
                o.name = rs.getString("char_name");
                o.value1 = rs.getInt("Str"); o.dex = rs.getInt("Dex");
                o.con = rs.getInt("Con"); o.wis = rs.getInt("Wis");
                o.ints = rs.getInt("Intel"); o.cha = rs.getInt("Cha");
                o.classn = rs.getInt("Class"); o.level = rs.getInt("level");
                o.hp = rs.getInt("MaxHp"); o.mp = rs.getInt("MaxMp");
                return o;
            }
        }
    }

    private static void requireOffline(MJCTObject o) throws SQLException {
        // These existing CacheD DB-worker probes return false rather than wait
        // for outstanding writes. A recent logout must finish before boxing.
        if (o.name == null || L1World.getInstance().findObject(o.charId) != null
                || !CacheD.getInstance().flushCharacter(o.name, 0)
                || !CacheD.getInstance().flushItemsByCharacter(o.charId, 0))
            throw new SQLException("Character still active or persistence pending");
    }

    private static void moveCharacter(Connection con, String from, String to, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET account_name=?, TamEndTime=NULL WHERE objid=? AND account_name=? AND OnlineStatus=0")) {
            ps.setString(1, to); ps.setInt(2, id); ps.setString(3, from);
            requireOne(ps.executeUpdate());
        }
    }

    private static void encase(Connection con, String account, int callerId, int id, int marbleId,
            TradeResult result) throws SQLException {
        result.object = null;
        result.message = MJCTLoadManager._invalidCharacter;
        if (id == callerId) throw new SQLException("Cannot box caller");
        lockAccount(con, account);
        lockCharacters(con, account, callerId);
        MJCTObject o = readCharacter(con, account, id);
        requireOffline(o);
        if (o.value1 == 0 || o.dex == 0 || o.con == 0 || o.wis == 0 || o.ints == 0 || o.cha == 0
                || o.level == 0 || o.hp == 0 || o.mp == 0) throw new SQLException("Invalid character stats");
        o.marbleId = marbleId;
        try (PreparedStatement ps = con.prepareStatement("SELECT marble_id FROM tb_mjct_mapping WHERE char_id=? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new SQLException("Character already mapped");
            }
        }
        moveCharacter(con, account, ESCROW, id);
        // Preserve the legacy removal of the oldest single TAM reservation.
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tam WHERE objid=? ORDER BY id ASC LIMIT 1")) {
            ps.setInt(1, id); ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO tb_mjct_mapping "
                + "(marble_id,char_id,char_str,char_dex,char_con,char_wis,char_int,char_cha,char_potion,char_hp,char_mp,char_class,char_level,char_name) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, marbleId); ps.setInt(2, id); ps.setInt(3, o.value1); ps.setInt(4, o.dex);
            ps.setInt(5, o.con); ps.setInt(6, o.wis); ps.setInt(7, o.ints); ps.setInt(8, o.cha);
            ps.setInt(9, o.ele); ps.setInt(10, o.hp); ps.setInt(11, o.mp); ps.setInt(12, o.classn);
            ps.setInt(13, o.level); ps.setString(14, o.name);
            requireOne(ps.executeUpdate());
        }
        result.object = o;
    }

    private static void redeem(Connection con, String account, int callerId, int marbleId, TradeResult result)
            throws SQLException {
        result.message = MJCTLoadManager._invalidCharacter;
        int slots = lockAccount(con, account);
        int count = lockCharacters(con, account, callerId);
        if (count > 8 || count >= slots) {
            result.message = "캐릭터 슬롯이 부족합니다.";
            throw new SQLException("Character slots exhausted");
        }
        int id;
        try (PreparedStatement ps = con.prepareStatement("SELECT char_id FROM tb_mjct_mapping WHERE marble_id=? FOR UPDATE")) {
            ps.setInt(1, marbleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Marble mapping missing");
                id = rs.getInt(1);
                if (rs.next()) throw new SQLException("Duplicate marble mapping");
            }
        }
        MJCTObject o = readCharacter(con, ESCROW, id);
        requireOffline(o);
        moveCharacter(con, ESCROW, account, id);
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tb_mjct_mapping WHERE marble_id=? AND char_id=?")) {
            ps.setInt(1, marbleId); ps.setInt(2, id);
            requireOne(ps.executeUpdate());
        }
    }

    private static void requireOne(int count) throws SQLException {
        if (count != 1) throw new SQLException("Character trade row count: " + count);
    }

    private static void updateCharacterStatus(L1PcInstance pc) {
        CIOClient client = pc.getNetConnection();
        CS_CharSelect.restartProcess(pc);
        if (client != null) CS_CommonClick.sendCharAmountAndPacks(client);
    }
}
