package l1j.server.server.storage.mysql;

import l1j.server.server.datatables.CObjectDB;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.KeyTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.L1Item;

/**
 * character_items 저장소
 */
public class MySqlCharactersItemStorage extends CharactersItemStorage {

    private static final Logger _log = Logger.getLogger(MySqlCharactersItemStorage.class.getName());

    // ==================================================================
    // 읽기
    // ==================================================================

    @Override
    public ArrayList<L1ItemInstance> loadItems(int objId) throws Exception {
        CacheD cache = CacheD.getInstance();
        if (!cache.flushItemsByCharacter(objId, 30000L)) {
            throw new SQLException("CacheD item flush timeout before loadItems: " + objId);
        }
        l1j.server.server.model.L1PendingCredit.recoverBeforeLoad(objId);
        final String sql = "SELECT * FROM character_items WHERE char_id = ?";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                ArrayList<L1ItemInstance> items = new ArrayList<>();
                while (rs.next()) {
                    L1ItemInstance item = rowToItem(rs);
                    if (item != null) {
                        cache.registerLoadedItemOwner(objId, item.getId());
                        if (item.getItemId() == 80500) {
                            KeyTable.checkey(item);
                        }
                        items.add(item);
                    }
                }
                return items;
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "loadItems error for char_id: " + objId, e);
            throw e;
        }
    }

    /** RS 한 행 → 아이템. 컬럼 매핑이 이 프로젝트에 이 메서드 하나에만 존재함. */
    private L1ItemInstance rowToItem(ResultSet rs) throws SQLException {
        final int itemId = rs.getInt("item_id");
        L1Item template = CObjectDB.GetItem(itemId);
        if (template == null) {
            // A partial inventory load must not silently hide persisted items.
            throw new SQLException("Missing item template while loading character_items: " + itemId);
        }
        final int persistedCount = rs.getInt("count");
        if (persistedCount <= 0) {
            throw new SQLException("Invalid character_items count for item template " + itemId);
        }

        L1ItemInstance item = new L1ItemInstance();
        item.setId(rs.getInt("id"));
        item.setItem(template);
        item.setCount(persistedCount);
        item.setEquipped(rs.getInt("Is_equipped") != 0);
        item.setEnchantLevel(rs.getInt("enchantlvl"));
        item.setIdentified(rs.getInt("is_id") != 0);
        item.set_durability(rs.getInt("durability"));
        item.setChargeCount(rs.getInt("charge_count"));
        item.setRemainingTime(rs.getInt("remaining_time"));
        item.setLastUsed(rs.getTimestamp("last_used"));
        item.setBless(rs.getInt("bless"));
        item.setAttrEnchantLevel(rs.getInt("attr_enchantlvl"));
        item.setSpecialEnchant(rs.getInt("special_enchant"));
        item.setEndTime(rs.getTimestamp("end_time"));
        item.setPackage(rs.getInt("package") != 0);          // 패키지상점
        item.set_bless_level(rs.getInt("bless_level"));
        item.set_bless_level2(rs.getInt("bless_level2"));
        item.set_item_level(rs.getInt("item_level"));         // 특수 인챈트 시스템
        item.setHotel_Town(rs.getString("Hotel_Town"));       // 여관 리뉴얼
        item.getLastStatus().updateAll();
        return item;
    }

    // ==================================================================
    // 생성 / 삭제: CacheD 단일 실행 경로
    // ==================================================================

    /** Globally unique item IDs: never replace a conflicting owner's row. */
    private static final String STORE_SQL =
        "INSERT INTO character_items (id, item_id, char_id, item_name, count, is_equipped, "
        + "enchantlvl, is_id, durability, charge_count, remaining_time, last_used, bless, "
        + "attr_enchantlvl, special_enchant, end_time, buy_time, package, bless_level, "
        + "bless_level2, item_level, Hotel_Town) "
        + "VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?) ";

    public static final class ItemSnapshot {
        public final int id;
        public final int ownerId;
        public final int itemId;
        public final String itemName;
        public final int count, equipped, enchant, identified, durability, charge;
        public final int remaining, bless, attr, special, pack, blessLevel, blessLevel2, itemLevel;
        public final Timestamp lastUsed, endTime;
        public final String hotelTown;
        // SQL and retries use the captured values. A detached inventory need not
        // stay reachable for the optional in-memory acknowledgement.
        private final WeakReference<L1ItemInstance> source;

        private ItemSnapshot(int ownerId, L1ItemInstance item, boolean idOnly) {
            this(ownerId, item, idOnly, -1, -1);
        }

        private ItemSnapshot(int ownerId, L1ItemInstance item, boolean idOnly,
                             int projectedCount, int projectedCharge) {
            id = item.getId();
            this.ownerId = ownerId;
            source = idOnly ? null : new WeakReference<>(item);
            if (idOnly) {
                itemId=0; itemName=null; count=0; equipped=0; enchant=0; identified=0;
                durability=0; charge=0; remaining=0; bless=0; attr=0; special=0;
                pack=0; blessLevel=0; blessLevel2=0; itemLevel=0;
                lastUsed=null; endTime=null; hotelTown=null;
            } else {
                itemId = item.getItemId();
                itemName = item.getItem().getName();
                count = projectedCount >= 0 ? projectedCount : item.getCount();
                equipped = item.isEquipped() ? 1 : 0;
                enchant = item.getEnchantLevel();
                identified = item.isIdentified() ? 1 : 0;
                durability = item.get_durability();
                charge = projectedCharge >= 0 ? projectedCharge : item.getChargeCount();
                remaining = item.getRemainingTime();
                bless = item.getBless();
                attr = item.getAttrEnchantLevel();
                special = item.getSpecialEnchant();
                pack = item.isPackage() ? 1 : 0;
                blessLevel = item.get_bless_level();
                blessLevel2 = item.get_bless_level2();
                itemLevel = item.get_item_level();
                hotelTown = item.getHotel_Town();
                Timestamp lu = item.getLastUsed();
                Timestamp et = item.getEndTime();
                lastUsed = copyTime(lu);
                endTime = copyTime(et);
            }
        }

        public static ItemSnapshot capture(L1ItemInstance item, int ownerId) {
            return new ItemSnapshot(ownerId, item, false);
        }

        /** Compare the live preimage without allocating a snapshot or copying
         * timestamps. The captured SQL image remains immutable. Deliberately
         * matches the transaction's original persisted-field comparison; the
         * display name is not a mutable character_items precondition.
         */
        public boolean matchesCurrent(L1ItemInstance item, int expectedOwnerId) {
            return item != null && id == item.getId() && ownerId == expectedOwnerId
                    && itemId == item.getItemId() && count == item.getCount()
                    && equipped == (item.isEquipped() ? 1 : 0)
                    && enchant == item.getEnchantLevel() && identified == (item.isIdentified() ? 1 : 0)
                    && durability == item.get_durability() && charge == item.getChargeCount()
                    && remaining == item.getRemainingTime() && bless == item.getBless()
                    && attr == item.getAttrEnchantLevel() && special == item.getSpecialEnchant()
                    && pack == (item.isPackage() ? 1 : 0) && blessLevel == item.get_bless_level()
                    && blessLevel2 == item.get_bless_level2() && itemLevel == item.get_item_level()
                    && java.util.Objects.equals(lastUsed, item.getLastUsed())
                    && java.util.Objects.equals(endTime, item.getEndTime())
                    && java.util.Objects.equals(hotelTown, item.getHotel_Town());
        }
        /** Immutable SQL projection; never change the live item before COMMIT. */
        public static ItemSnapshot project(L1ItemInstance item, int ownerId,
                                           int finalCount, int finalCharge) {
            if (item == null || finalCount <= 0 || finalCharge < 0)
                throw new IllegalArgumentException("Invalid projected trade item");
            return new ItemSnapshot(ownerId, item, false, finalCount, finalCharge);
        }
        public static ItemSnapshot captureId(L1ItemInstance item) {
            return new ItemSnapshot(-1, item, true);
        }
        public static ItemSnapshot captureId(L1ItemInstance item, int ownerId) {
            if (ownerId <= 0) throw new IllegalArgumentException("Unknown delete owner");
            return new ItemSnapshot(ownerId, item, true);
        }

        public void finish(long mask) {
            L1ItemInstance item = source == null ? null : source.get();
            if (item == null) return;
            L1ItemInstance.LastStatus status = item.getLastStatus();
            synchronized (status) {
                // Record exactly what SQL committed, even if the live value
                // changed back while the worker was writing.
                if ((mask & 1L) != 0) status.itemId = itemId;
                if ((mask & 2L) != 0) status.count = count;
                if ((mask & 4L) != 0) status.isIdentified = identified != 0;
                if ((mask & 8L) != 0) status.isEquipped = equipped != 0;
                if ((mask & 16L) != 0) status.enchantLevel = enchant;
                if ((mask & 32L) != 0) status.durability = durability;
                if ((mask & 64L) != 0) status.chargeCount = charge;
                if ((mask & 128L) != 0) status.remainingTime = remaining;
                if ((mask & 256L) != 0) status.lastUsed = lastUsed;
                if ((mask & 512L) != 0) status.bless = bless;
                if ((mask & 1024L) != 0) status.attrenchantLevel = attr;
                if ((mask & 2048L) != 0) status.endTime = endTime;
                if ((mask & (1L << 12)) != 0) status._isPackage = pack != 0;
                if ((mask & (1L << 13)) != 0) status.bless_level = blessLevel;
                if ((mask & (1L << 14)) != 0) status.bless_level2 = blessLevel2;
                if ((mask & (1L << 15)) != 0) status.item_level = itemLevel;
                if ((mask & (1L << 16)) != 0) status.town_name = hotelTown;
                if ((mask & (1L << 17)) != 0) status.specialEnchant = special;
            }
        }
        private static Timestamp copyTime(Timestamp t) {
            if (t == null) return null;
            Timestamp copy = new Timestamp(t.getTime());
            copy.setNanos(t.getNanos());
            return copy;
        }
    }

    @Override
    public void storeItem(int objId, L1ItemInstance item) throws Exception {
        CacheD.getInstance().storeItem(objId, item);
    }

    @Override
    public void deleteItem(L1ItemInstance item) throws Exception {
        CacheD.getInstance().deleteItem(item);
    }

    public static void storeSnapshot(ItemSnapshot v) throws SQLException {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            storeSnapshot(con, v);
        }
    }

    private static final String STRICT_INSERT_SQL = STORE_SQL;

    private static void bindItemRow(PreparedStatement ps, ItemSnapshot v) throws SQLException {
                int i = 0;
                ps.setInt(++i, v.id);
                ps.setInt(++i, v.itemId);
                ps.setInt(++i, v.ownerId);
                ps.setString(++i, v.itemName);
                ps.setInt(++i, v.count);
                ps.setInt(++i, v.enchant);
                ps.setInt(++i, v.identified);
                ps.setInt(++i, v.durability);
                ps.setInt(++i, v.charge);
                ps.setInt(++i, v.remaining);
                ps.setTimestamp(++i, v.lastUsed);
                ps.setInt(++i, v.bless);
                ps.setInt(++i, v.attr);
                ps.setInt(++i, v.special);
                ps.setTimestamp(++i, v.endTime);
                ps.setInt(++i, v.pack);
                ps.setInt(++i, v.blessLevel);
                ps.setInt(++i, v.blessLevel2);
                ps.setInt(++i, v.itemLevel);
                ps.setString(++i, v.hotelTown);
    }

    public static void storeSnapshot(Connection con, ItemSnapshot v) throws SQLException {
        if (updateTradeSnapshotRow(con, v, v.ownerId)) return;
        // UPDATE can report zero for an unchanged row. Distinguish it from a
        // missing item and a different owner's unique ID before INSERT.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT char_id,item_id FROM character_items WHERE id=? FOR UPDATE")) {
            ps.setInt(1, v.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt(1) != v.ownerId || rs.getInt(2) != v.itemId)
                        throw new SQLException("Item ID belongs to another owner/type: " + v.id);
                    return; // same-owner/type no-op UPDATE
                }
            }
        }
        // A concurrent INSERT with this ID now fails PK validation rather
        // than ON DUPLICATE KEY silently stealing its owner and contents.
        insertTradeSnapshot(con, v);
    }

    /** A trade INSERT must never turn into an UPDATE of another owner's ID. */
    public static void insertTradeSnapshot(Connection con, ItemSnapshot v) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(STRICT_INSERT_SQL)) {
            bindItemRow(ps, v);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Trade item INSERT did not insert one row: " + v.id);
        }
    }

    /** Reassignment is conditional on the locked ORIGINAL owner, not the new one. */
    private static boolean updateTradeSnapshotRow(Connection con, ItemSnapshot v,
                                                   int previousOwner) throws SQLException {
        if (previousOwner <= 0 || v.ownerId <= 0 || v.id <= 0 || v.count <= 0)
            throw new SQLException("Invalid trade item owner/quantity");
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE character_items SET item_id=?,char_id=?,item_name=?,count=?,is_equipped=0,"
                + "enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,last_used=?,"
                + "bless=?,attr_enchantlvl=?,special_enchant=?,end_time=?,package=?,"
                + "bless_level=?,bless_level2=?,item_level=?,Hotel_Town=? "
                + "WHERE id=? AND char_id=? AND item_id=?")) {
            int i = 0;
            ps.setInt(++i, v.itemId);
            ps.setInt(++i, v.ownerId);
            ps.setString(++i, v.itemName);
            ps.setInt(++i, v.count);
            ps.setInt(++i, v.enchant);
            ps.setInt(++i, v.identified);
            ps.setInt(++i, v.durability);
            ps.setInt(++i, v.charge);
            ps.setInt(++i, v.remaining);
            ps.setTimestamp(++i, v.lastUsed);
            ps.setInt(++i, v.bless);
            ps.setInt(++i, v.attr);
            ps.setInt(++i, v.special);
            ps.setTimestamp(++i, v.endTime);
            ps.setInt(++i, v.pack);
            ps.setInt(++i, v.blessLevel);
            ps.setInt(++i, v.blessLevel2);
            ps.setInt(++i, v.itemLevel);
            ps.setString(++i, v.hotelTown);
            ps.setInt(++i, v.id);
            ps.setInt(++i, previousOwner);
            ps.setInt(++i, v.itemId);
            return ps.executeUpdate() == 1;
        }
    }

    public static void updateTradeSnapshot(Connection con, ItemSnapshot v,
                                           int previousOwner) throws SQLException {
        if (!updateTradeSnapshotRow(con, v, previousOwner))
            throw new SQLException("Trade item UPDATE owner/type changed: " + v.id);
    }

    /** Atomic mail-link item delete: owner and physical letter template. */
    public static void deleteLetterItemSnapshot(Connection con, int objectId,
                                                int ownerId, int templateId) throws SQLException {
        if (objectId <= 0 || ownerId <= 0 || templateId < 49016 || templateId > 49025)
            throw new SQLException("Invalid physical letter item delete");
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM character_items WHERE id=? AND char_id=? AND item_id=?")) {
            ps.setInt(1, objectId);
            ps.setInt(2, ownerId);
            ps.setInt(3, templateId);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Linked letter item owner/template changed: " + objectId);
        }
    }

    public static void deleteTradeSnapshot(Connection con, int itemId,
                                           int previousOwner) throws SQLException {
        if (itemId <= 0 || previousOwner <= 0)
            throw new SQLException("Invalid trade item DELETE");
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM character_items WHERE id=? AND char_id=?")) {
            ps.setInt(1, itemId);
            ps.setInt(2, previousOwner);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Trade item DELETE owner changed: " + itemId);
        }
    }

    public static void deleteSnapshot(ItemSnapshot v) throws SQLException {
        if (v == null || v.id <= 0 || v.ownerId <= 0)
            throw new SQLException("Invalid character item delete snapshot or owner");
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM character_items WHERE id=? AND char_id=?")) {
            ps.setInt(1, v.id);
            ps.setInt(2, v.ownerId);
            if (ps.executeUpdate() == 0) {
                // A repeated, already applied DELETE is harmless; a reused or
                // transferred ID is a stale command and must not be erased.
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT char_id FROM character_items WHERE id=?")) {
                    check.setInt(1, v.id);
                    try (ResultSet rows = check.executeQuery()) {
                        if (rows.next())
                            throw new SQLException("CacheD item delete owner changed: " + v.id);
                    }
                }
            }
        }
    }

    /** Ownerless deletion cannot distinguish a stale reference from a current
     * item. Keep binary/source compatibility, but never issue unsafe SQL.
     */
    @Deprecated
    public static void deleteSnapshot(Connection con, int itemId) throws SQLException {
        throw new SQLException("Character item DELETE requires an owner snapshot: " + itemId);
    }

    // ==================================================================
    // 갱신 — CacheD 상태 병합
    // ==================================================================

    private static final int ITEM_COLUMN_COUNT = 18;
    private static final long ALL_ITEM_COLUMNS = (1L << ITEM_COLUMN_COUNT) - 1;
    private static final String FULL_UPDATE = buildUpdateSql(ALL_ITEM_COLUMNS);
    private static final String[] SINGLE_COLUMN_UPDATES = singleColumnUpdates();

    private static String[] singleColumnUpdates() {
        String[] sql = new String[ITEM_COLUMN_COUNT];
        for (int bit = 0; bit < sql.length; bit++) sql[bit] = buildUpdateSql(1L << bit);
        return sql;
    }

    private static String updateSql(long mask) {
        if (mask == ALL_ITEM_COLUMNS) return FULL_UPDATE;
        if (mask > 0 && mask <= ALL_ITEM_COLUMNS && (mask & (mask - 1)) == 0)
            return SINGLE_COLUMN_UPDATES[Long.numberOfTrailingZeros(mask)];
        return buildUpdateSql(mask);
    }

    private static String buildUpdateSql(long mask) {
        StringBuilder sql = new StringBuilder(320).append("UPDATE character_items SET ");
        if ((mask & 1L) != 0) sql.append("item_id=?,");
        if ((mask & 2L) != 0) sql.append("count=?,");
        if ((mask & 4L) != 0) sql.append("is_id=?,");
        if ((mask & 8L) != 0) sql.append("is_equipped=?,");
        if ((mask & 16L) != 0) sql.append("enchantlvl=?,");
        if ((mask & 32L) != 0) sql.append("durability=?,");
        if ((mask & 64L) != 0) sql.append("charge_count=?,");
        if ((mask & 128L) != 0) sql.append("remaining_time=?,");
        if ((mask & 256L) != 0) sql.append("last_used=?,");
        if ((mask & 512L) != 0) sql.append("bless=?,");
        if ((mask & 1024L) != 0) sql.append("attr_enchantlvl=?,");
        if ((mask & 2048L) != 0) sql.append("end_time=?,");
        if ((mask & (1L << 12)) != 0) sql.append("package=?,");
        if ((mask & (1L << 13)) != 0) sql.append("bless_level=?,");
        if ((mask & (1L << 14)) != 0) sql.append("bless_level2=?,");
        if ((mask & (1L << 15)) != 0) sql.append("item_level=?,");
        if ((mask & (1L << 16)) != 0) sql.append("Hotel_Town=?,");
        if ((mask & (1L << 17)) != 0) sql.append("special_enchant=?,");
        sql.setLength(sql.length() - 1);
        // A stale item write must not modify a row that has changed owner.
        sql.append(" WHERE id=? AND char_id=?");
        return sql.toString();
    }

    /**
     * 배치 플러시용 부분 UPDATE — 마스크에 세팅된 컬럼만 갱신.
     * 호출자: CacheD.
     * 커넥션 획득/실행 지연 출력 — 안정화 후 제거 가능.
     */
    public void updateItemCombined(L1ItemInstance item, long mask) throws Exception {
        CacheD.getInstance().saveItem(item, mask);
    }

    public static void updateSnapshot(ItemSnapshot s, long mask) throws SQLException {
        if (mask == 0L) return;
        if (s == null || s.id <= 0 || s.ownerId <= 0 || s.itemId <= 0 || s.count <= 0) {
            throw new SQLException("Invalid character item update snapshot or owner");
        }
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(updateSql(mask))) {
            int i = 0;
            if ((mask & 1L) != 0) ps.setInt(++i, s.itemId);
            if ((mask & 2L) != 0) ps.setInt(++i, s.count);
            if ((mask & 4L) != 0) ps.setInt(++i, s.identified);
            if ((mask & 8L) != 0) ps.setInt(++i, s.equipped);
            if ((mask & 16L) != 0) ps.setInt(++i, s.enchant);
            if ((mask & 32L) != 0) ps.setInt(++i, s.durability);
            if ((mask & 64L) != 0) ps.setInt(++i, s.charge);
            if ((mask & 128L) != 0) ps.setInt(++i, s.remaining);
            if ((mask & 256L) != 0) ps.setTimestamp(++i, s.lastUsed);
            if ((mask & 512L) != 0) ps.setInt(++i, s.bless);
            if ((mask & 1024L) != 0) ps.setInt(++i, s.attr);
            if ((mask & 2048L) != 0) ps.setTimestamp(++i, s.endTime);
            if ((mask & (1L << 12)) != 0) ps.setInt(++i, s.pack);
            if ((mask & (1L << 13)) != 0) ps.setInt(++i, s.blessLevel);
            if ((mask & (1L << 14)) != 0) ps.setInt(++i, s.blessLevel2);
            if ((mask & (1L << 15)) != 0) ps.setInt(++i, s.itemLevel);
            if ((mask & (1L << 16)) != 0) ps.setString(++i, s.hotelTown);
            if ((mask & (1L << 17)) != 0) ps.setInt(++i, s.special);
            ps.setInt(++i, s.id);
            ps.setInt(++i, s.ownerId);
            if (ps.executeUpdate() == 0) {
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT 1 FROM character_items WHERE id=? AND char_id=?")) {
                    check.setInt(1, s.id);
                    check.setInt(2, s.ownerId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("CacheD item update target missing: " + s.id
                                    + " itemId=" + s.itemId + " ownerId=" + s.ownerId
                                    + " mask=" + mask);
                        }
                    }
                }
            }
        }
        s.finish(mask);
    }

    // ==================================================================
    // 인터페이스 호환용 — 전부 CacheD 위임
    // ==================================================================

    private static final long ALL = (1L << 18) - 1;
    @Override public void updateItemAll(L1ItemInstance item) throws Exception { updateItemCombined(item, ALL); }
    @Override public void updateItemId(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L); }
    @Override public void updateItemCount(L1ItemInstance item) throws Exception { updateItemCombined(item, 2L); }
    @Override public void updateItemIdentified(L1ItemInstance item) throws Exception { updateItemCombined(item, 4L); }
    @Override public void updateItemEquipped(L1ItemInstance item) throws Exception { updateItemCombined(item, 8L); }
    @Override public void updateItemEnchantLevel(L1ItemInstance item) throws Exception { updateItemCombined(item, 16L); }
    @Override public void updateItemDurability(L1ItemInstance item) throws Exception { updateItemCombined(item, 32L); }
    @Override public void updateItemChargeCount(L1ItemInstance item) throws Exception { updateItemCombined(item, 64L); }
    @Override public void updateItemRemainingTime(L1ItemInstance item) throws Exception { updateItemCombined(item, 128L); }
    @Override public void updateItemDelayEffect(L1ItemInstance item) throws Exception { updateItemCombined(item, 256L); }
    @Override public void updateItemBless(L1ItemInstance item) throws Exception { updateItemCombined(item, 512L); }
    @Override public void updateItemAttrEnchantLevel(L1ItemInstance item) throws Exception { updateItemCombined(item, 1024L); }
    @Override public void updateItemEndTime(L1ItemInstance item) throws Exception { updateItemCombined(item, 2048L); }
    @Override public void updateItemBlessLevel(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L << 13); }
    @Override public void updateItemBlessLevel2(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L << 14); }
    @Override public void updateItemLevel(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L << 15); }
    @Override public void updateHotelTowm(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L << 16); }
    @Override public void updateSpecialEnchant(L1ItemInstance item) throws Exception { updateItemCombined(item, 1L << 17); }

    // ==================================================================
    // 카운트
    // ==================================================================

    @Override
    public int getItemCount(int objId) throws Exception {
        if (!CacheD.getInstance().flushItemsByCharacter(objId, 30000L)) {
            throw new SQLException("CacheD scoped item flush timeout before getItemCount: " + objId);
        }
        final String sql = "SELECT COUNT(*) FROM character_items WHERE char_id = ?";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "getItemCount error for char_id: " + objId, e);
            throw e;
        }
    }
}
