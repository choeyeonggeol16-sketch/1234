package l1j.server.server.model.Warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Collections;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.monitor.Logger.WarehouseType;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;

public abstract class Warehouse extends L1Object {
	private static final long serialVersionUID = 1L;
	
	protected final List<L1ItemInstance> _items = new CopyOnWriteArrayList<L1ItemInstance>();
    private final List<L1ItemInstance> readOnlyItems = Collections.unmodifiableList(_items);
	private final String name;
    // An evicted warehouse must never accept writes from a caller retaining
    // an old reference. No extra registry, worker, or per-operation allocation.
    private volatile boolean retired;

    protected final void assertWarehouseActive() {
        if (retired) throw new IllegalStateException("Warehouse reference retired: " + name);
    }

    // Called under the warehouse mutation monitor by WarehouseList.
    final synchronized void retireWarehouse() {
        assertWarehouseActive();
        clearItems();
        retired = true;
    }

	public Warehouse(String n) {
		super();
		name = n;
	}
	
	public String getName() {
		return name;
	}

    // Keep the four existing CacheD SQL workers; no warehouse JDBC work runs
    // on the game/IO completion thread. All table names below are constants.
    protected static final int CLAN_ROWS = 1;
    protected static final int ELF_ROWS = 2;
    protected static final int SPECIAL_ROWS = 3;
    protected static final int SUPPLEMENT_ROWS = 4;

    private static final String[] ROW_TABLE = {
        "", "clan_warehouse", "character_elf_warehouse",
        "character_special_warehouse", "character_supplementary_service"
    };
    private static final String[] OWNER_COLUMN = {
        "", "clan_name", "account_name", "char_name", "account_name"
    };
    private static final String[] INSERT_SQL = {
        "",
        "INSERT INTO clan_warehouse SET id=?,clan_name=?,item_id=?,item_name=?,count=?,is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,last_used=?,attr_enchantlvl=?,special_enchant=?,package=?,bless_level=?,item_level=?,Hotel_Town=?,end_time=?",
        "INSERT INTO character_elf_warehouse SET id=?,account_name=?,item_id=?,item_name=?,count=?,is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,last_used=?,attr_enchantlvl=?,bless=?,special_enchant=?,bless_level=?,item_level=?,Hotel_Town=?,end_time=?",
        "INSERT INTO character_special_warehouse SET id=?,char_name=?,item_id=?,item_name=?,count=?,is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,last_used=?,attr_enchantlvl=?,bless=?,special_enchant=?,package=?,bless_level=?,item_level=?,Hotel_Town=?,end_time=?",
        "INSERT INTO character_supplementary_service SET id=?,account_name=?,item_id=?,item_name=?,count=?,is_equipped=0,enchantlvl=?,is_id=?,durability=?,charge_count=?,remaining_time=?,last_used=?,attr_enchantlvl=?,bless=?,special_enchant=?,package=?"
    };

    private static final class WarehouseRow {
        final String owner, itemName, hotel;
        final int id, itemId, count, enchant, identified, durability, charge, remaining;
        final int attr, bless, special, pack, blessLevel, itemLevel;
        final Timestamp lastUsed, endTime;

        WarehouseRow(String owner, L1ItemInstance item) {
            this.owner = Objects.requireNonNull(owner, "warehouse owner");
            id = item.getId();
            itemId = item.getItemId();
            itemName = item.getName();
            count = item.getCount();
            enchant = item.getEnchantLevel();
            identified = item.isIdentified() ? 1 : 0;
            durability = item.get_durability();
            charge = item.getChargeCount();
            remaining = item.getRemainingTime();
            attr = item.getAttrEnchantLevel();
            bless = item.getBless();
            special = item.getSpecialEnchant();
            pack = item.isPackage() ? 1 : 0;
            blessLevel = item.get_bless_level();
            itemLevel = item.get_item_level();
            hotel = item.getHotel_Town();
            lastUsed = copyTime(item.getLastUsed());
            endTime = copyTime(item.getEndTime());
        }
    }

    private static Timestamp copyTime(Timestamp time) {
        if (time == null) return null;
        Timestamp copy = new Timestamp(time.getTime());
        copy.setNanos(time.getNanos());
        return copy;
    }

    private int persistenceKey(int type) {
        // Account warehouses share the account FIFO with private warehouse.
        // Clan and special-owner namespaces are independent of account names.
        return CacheD.accountOrderKey(type == CLAN_ROWS ? "clan:" + getName()
                : type == SPECIAL_ROWS ? "special:" + getName() : getName());
    }

    public final int transferOrderKey() {
        if (this instanceof PrivateWarehouse) return CacheD.accountOrderKey(getName());
        if (this instanceof ClanWarehouse) return persistenceKey(CLAN_ROWS);
        if (this instanceof ElfWarehouse) return persistenceKey(ELF_ROWS);
        if (this instanceof SpecialWarehouse) return persistenceKey(SPECIAL_ROWS);
        if (this instanceof SupplementaryService) return persistenceKey(SUPPLEMENT_ROWS);
        throw new IllegalStateException("Unsupported warehouse type: " + getClass().getName());
    }

    protected final void assertWarehouseReadsCurrent(int type) {
        assertWarehouseActive();
        // Never reload an older DB snapshot while asynchronous writes are pending.
        // This is a non-waiting boundary: callers must retry after persistence.
        if (!CacheD.getInstance().flushDbOwner(persistenceKey(type), 0L)) {
            throw new IllegalStateException("Warehouse writes pending: " + getName());
        }
    }

    protected final void submitWarehouseInsert(int type, L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || item.getId() <= 0 || item.getCount() <= 0) {
            throw new IllegalArgumentException("Invalid warehouse item");
        }
        final WarehouseRow row = new WarehouseRow(getName(), item);
        if (CacheD.stageWarehouseMoveSql(con -> insertWarehouseMove(con, type, row))) return;
        CacheD.getInstance().submitDbJobRetryOrdered(persistenceKey(type), () -> insertWarehouseRow(type, row));
    }

    protected final void submitWarehouseUpdate(int type, L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || item.getId() <= 0 || item.getCount() <= 0) {
            throw new IllegalArgumentException("Invalid warehouse quantity");
        }
        final String owner = getName();
        final int id = item.getId(), count = item.getCount();
        final int itemId = item.getItemId();
        final int previousCount = CacheD.warehouseMoveOriginalCount(id);
        if (CacheD.stageWarehouseMoveSql(con -> updateWarehouseMove(con, type, owner, id, itemId, previousCount, count))) return;
        CacheD.getInstance().submitDbJobRetryOrdered(persistenceKey(type), () -> {
            String sql = "UPDATE " + ROW_TABLE[type] + " SET count=? WHERE id=? AND " + OWNER_COLUMN[type] + "=?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, count);
                ps.setInt(2, id);
                ps.setString(3, owner);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement check = con.prepareStatement("SELECT count FROM " + ROW_TABLE[type]
                            + " WHERE id=? AND " + OWNER_COLUMN[type] + "=?")) {
                        check.setInt(1, id);
                        check.setString(2, owner);
                        try (ResultSet rs = check.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) != count) {
                                throw new SQLException("Warehouse UPDATE row missing or stale id=" + id);
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Warehouse UPDATE: " + ROW_TABLE[type] + " id=" + id, ex);
            }
        });
    }

    protected final void submitWarehouseDelete(int type, L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || item.getId() <= 0) throw new IllegalArgumentException("Invalid warehouse item");
        final String owner = getName();
        final int id = item.getId();
        final int itemId = item.getItemId();
        final int previousCount = CacheD.warehouseMoveOriginalCount(id);
        if (CacheD.stageWarehouseMoveSql(con -> deleteWarehouseMove(con, type, owner, id, itemId, previousCount))) return;
        CacheD.getInstance().submitDbJobRetryOrdered(persistenceKey(type), () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM " + ROW_TABLE[type]
                         + " WHERE id=? AND " + OWNER_COLUMN[type] + "=?")) {
                ps.setInt(1, id);
                ps.setString(2, owner);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement check = con.prepareStatement(
                            "SELECT " + OWNER_COLUMN[type] + " FROM " + ROW_TABLE[type] + " WHERE id=?")) {
                        check.setInt(1, id);
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) throw new SQLException("Warehouse DELETE owner mismatch id=" + id);
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Warehouse DELETE: " + ROW_TABLE[type] + " id=" + id, ex);
            }
        });
    }

    protected final void submitWarehouseDeleteAll(int type) {
        assertWarehouseActive();
        final String owner = getName();
        CacheD.getInstance().submitDbJobRetryOrdered(persistenceKey(type), () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM " + ROW_TABLE[type]
                         + " WHERE " + OWNER_COLUMN[type] + "=?")) {
                ps.setString(1, owner);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Warehouse DELETE ALL: " + ROW_TABLE[type]
                        + " owner=" + owner, ex);
            }
        });
    }

    // Transfer-only operations never obtain their own connection. Source and
    // destination run under CTransaction.persistWarehouseMove() on ONE MySQL tx.
    private static void insertWarehouseMove(Connection con, int type, WarehouseRow row)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_SQL[type])) {
            int p = 0;
            ps.setInt(++p, row.id);
            ps.setString(++p, row.owner);
            ps.setInt(++p, row.itemId);
            ps.setString(++p, row.itemName);
            ps.setInt(++p, row.count);
            ps.setInt(++p, row.enchant);
            ps.setInt(++p, row.identified);
            ps.setInt(++p, row.durability);
            ps.setInt(++p, row.charge);
            ps.setInt(++p, row.remaining);
            ps.setTimestamp(++p, row.lastUsed);
            ps.setInt(++p, row.attr);
            if (type != CLAN_ROWS) ps.setInt(++p, row.bless);
            ps.setInt(++p, row.special);
            if (type != ELF_ROWS) ps.setInt(++p, row.pack);
            if (type != SUPPLEMENT_ROWS) ps.setInt(++p, row.blessLevel);
            if (type != SUPPLEMENT_ROWS) {
                ps.setInt(++p, row.itemLevel);
                ps.setString(++p, row.hotel);
                ps.setTimestamp(++p, row.endTime);
            }
            if (ps.executeUpdate() != 1)
                throw new SQLException("Warehouse transfer INSERT failed id=" + row.id);
        }
    }

    private static void verifyWarehouseMoveRow(Connection con, int type, String owner,
                                                int id, int expectedItemId, int expectedCount) throws SQLException {
        if (expectedCount <= 0) throw new SQLException("Missing warehouse original count: " + id);
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_id,count FROM " + ROW_TABLE[type] + " WHERE id=? AND "
                + OWNER_COLUMN[type] + "=? FOR UPDATE")) {
            ps.setInt(1, id);
            ps.setString(2, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != expectedItemId || rs.getInt(2) != expectedCount)
                    throw new SQLException("Warehouse transfer source stale (owner/type/count): " + id);
            }
        }
    }

    private static void updateWarehouseMove(Connection con, int type, String owner,
                                            int id, int itemId, int before, int count) throws SQLException {
        verifyWarehouseMoveRow(con, type, owner, id, itemId, before);
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE " + ROW_TABLE[type] + " SET count=? WHERE id=? AND " + OWNER_COLUMN[type]
                + "=? AND item_id=? AND count=?")) {
            ps.setInt(1, count);
            ps.setInt(2, id);
            ps.setString(3, owner);
            ps.setInt(4, itemId);
            ps.setInt(5, before);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Warehouse move count/owner changed: " + id);
        }
    }

    private static void deleteWarehouseMove(Connection con, int type, String owner,
                                            int id, int itemId, int before) throws SQLException {
        verifyWarehouseMoveRow(con, type, owner, id, itemId, before);
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM " + ROW_TABLE[type] + " WHERE id=? AND " + OWNER_COLUMN[type] + "=?")) {
            ps.setInt(1, id);
            ps.setString(2, owner);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Warehouse move delete not applied: " + id);
        }
    }

    private static void insertWarehouseRow(int type, WarehouseRow row) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_SQL[type])) {
            int p = 0;
            ps.setInt(++p, row.id);
            ps.setString(++p, row.owner);
            ps.setInt(++p, row.itemId);
            ps.setString(++p, row.itemName);
            ps.setInt(++p, row.count);
            ps.setInt(++p, row.enchant);
            ps.setInt(++p, row.identified);
            ps.setInt(++p, row.durability);
            ps.setInt(++p, row.charge);
            ps.setInt(++p, row.remaining);
            ps.setTimestamp(++p, row.lastUsed);
            ps.setInt(++p, row.attr);
            if (type != CLAN_ROWS) ps.setInt(++p, row.bless);
            ps.setInt(++p, row.special);
            if (type != ELF_ROWS) ps.setInt(++p, row.pack);
            if (type != SUPPLEMENT_ROWS) ps.setInt(++p, row.blessLevel);
            if (type != SUPPLEMENT_ROWS) {
                ps.setInt(++p, row.itemLevel);
                ps.setString(++p, row.hotel);
                ps.setTimestamp(++p, row.endTime);
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            // An uncertain INSERT completion can leave the row committed.
            // Only acknowledge a retry when all stored columns match exactly.
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT * FROM " + ROW_TABLE[type] + " WHERE id=?")) {
                ps.setInt(1, row.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && row.owner.equals(rs.getString(OWNER_COLUMN[type]))
                            && row.itemId == rs.getInt("item_id")
                            && Objects.equals(row.itemName, rs.getString("item_name"))
                            && row.count == rs.getInt("count")
                            && row.enchant == rs.getInt("enchantlvl")
                            && row.identified == rs.getInt("is_id")
                            && row.durability == rs.getInt("durability")
                            && row.charge == rs.getInt("charge_count")
                            && row.remaining == rs.getInt("remaining_time")
                            && Objects.equals(row.lastUsed, rs.getTimestamp("last_used"))
                            && row.attr == rs.getInt("attr_enchantlvl")
                            && (type == CLAN_ROWS || row.bless == rs.getInt("bless"))
                            && row.special == rs.getInt("special_enchant")
                            && (type == ELF_ROWS || row.pack == rs.getInt("package"))
                            && (type == SUPPLEMENT_ROWS || row.blessLevel == rs.getInt("bless_level"))
                            && (type == SUPPLEMENT_ROWS || (row.itemLevel == rs.getInt("item_level")
                                && Objects.equals(row.hotel, rs.getString("Hotel_Town"))
                                && Objects.equals(row.endTime, rs.getTimestamp("end_time"))))) return;
                }
            } catch (SQLException checkFailed) {
                ex.addSuppressed(checkFailed);
            }
            throw new IllegalStateException("Warehouse INSERT unresolved: " + ROW_TABLE[type] + " id=" + row.id, ex);
        }
    }

    public static L1ItemInstance transferCopy(L1ItemInstance source, int id, int count, int charge) {
        if (source == null || id <= 0 || count <= 0) throw new IllegalArgumentException("Transfer snapshot");
        L1ItemInstance result = new L1ItemInstance();
        result.setId(id);
        result.setItem(source.getItem());
        result.setCount(count);
        result.setEnchantLevel(source.getEnchantLevel());
        result.setIdentified(source.isIdentified());
        result.set_durability(source.get_durability());
        result.setChargeCount(charge);
        result.setRemainingTime(source.getRemainingTime());
        result.setLastUsed(copyTime(source.getLastUsed()));
        result.setBless(source.getBless());
        result.setAttrEnchantLevel(source.getAttrEnchantLevel());
        result.setSpecialEnchant(source.getSpecialEnchant());
        result.setPackage(source.isPackage());
        result.set_bless_level(source.get_bless_level());
        result.set_bless_level2(source.get_bless_level2());
        result.set_item_level(source.get_item_level());
        result.setHotel_Town(source.getHotel_Town());
        result.setEndTime(copyTime(source.getEndTime()));
        return result;
    }

    // A split transfer needs a reserved ID, not a visible world object. The
    // world registration happens only after durable SQL and successful replay.
    public static L1ItemInstance allocateDetachedTransfer(L1ItemInstance source, int count) {
        return transferCopy(source, IdFactory.getInstance().nextId(), count, source.getChargeCount());
    }

    public static void recordCommittedMove(l1j.server.server.model.Instance.L1PcInstance pc,
                                           Warehouse target, boolean deposit,
                                           L1ItemInstance source, int count) {
        final WarehouseType type = target instanceof PrivateWarehouse ? WarehouseType.Private
                : target instanceof ClanWarehouse ? WarehouseType.Clan
                : target instanceof ElfWarehouse ? WarehouseType.Elf
                : target instanceof SupplementaryService ? WarehouseType.Private : null;
        if (type != null) {
            try { LoggerInstance.getInstance().addWarehouse(type, deposit, pc, source, count); }
            catch (RuntimeException loggingFailure) {
                java.util.logging.Logger.getLogger(Warehouse.class.getName())
                        .log(java.util.logging.Level.WARNING, "Warehouse committed audit log failed", loggingFailure);
            }
        }
    }

    public static L1ItemInstance characterTransferCopy(L1ItemInstance source,
            int id, int count, int charge) {
        L1ItemInstance row = transferCopy(source, id, count, charge);
        if (row.get_bless_level() > 0) row.setBless(0);
        return row;
    }

    public final void prepareTransferDelete(L1ItemInstance source) {
        assertWarehouseActive();
        if (source == null || getItem(source.getId()) != source)
            throw new IllegalStateException("Warehouse move stale source");
        if (this instanceof PrivateWarehouse) {
            ((PrivateWarehouse) this).preparePrivateTransferDelete(source);
        } else if (this instanceof ClanWarehouse) {
            submitWarehouseDelete(CLAN_ROWS, source);
        } else if (this instanceof ElfWarehouse) {
            submitWarehouseDelete(ELF_ROWS, source);
        } else if (this instanceof SpecialWarehouse) {
            submitWarehouseDelete(SPECIAL_ROWS, source);
        } else if (this instanceof SupplementaryService) {
            submitWarehouseDelete(SUPPLEMENT_ROWS, source);
        } else throw new IllegalStateException("Unsupported warehouse");
    }

    /** Publish a validated snapshot once; reconnect must not append duplicate IDs. */
    protected final synchronized void installLoadedSnapshot(List<L1ItemInstance> loaded) throws SQLException {
        assertWarehouseActive();
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (L1ItemInstance item : loaded) {
            if (item == null || item.getId() <= 0 || item.getCount() <= 0
                    || !ids.add(item.getId()))
                throw new SQLException("Invalid or duplicate warehouse snapshot: " + getName());
        }
        // A login SELECT itself owns a DB lane. Reject only atomic transfers
        // awaiting game replay, without treating the current read as a write.
        if (CacheD.getInstance().hasPendingOwnerApply(transferOrderKey()))
            throw new SQLException("Warehouse snapshot has pending writes: " + getName());
        for (L1ItemInstance previous : _items)
            L1World.getInstance().removeObject(previous);
        _items.clear();
        _items.addAll(loaded);
        for (L1ItemInstance item : loaded)
            L1World.getInstance().storeObject(item);
    }

	public abstract void loadItems(); 
	public abstract void deleteItem(L1ItemInstance item);
	public abstract void insertItem(L1ItemInstance item);
	public abstract void updateItem(L1ItemInstance findItem);
	protected abstract int getMax();
	
	public L1ItemInstance findItemId(int id) {
		for (L1ItemInstance item : _items) {
			if (item.getItem().getItemId() == id) {
				return item;
			}
		}
		return null;
	}

	public synchronized L1ItemInstance storeTradeItem(L1ItemInstance item) {
        assertWarehouseActive();
		if (item == null || item.getCount() <= 0 || checkAddItemToWarehouse(item, item.getCount()) != 0) {
			return null;
		}
		if (item.isStackable()) {
			L1ItemInstance findItem = findItemId(item.getItem().getItemId());
			if (findItem != null) {
				final int before = findItem.getCount();
				if (before < 0 || (long) before + item.getCount() > L1Inventory.MAX_AMOUNT) return null;
				findItem.setCount(before + item.getCount());
				try {
					updateItem(findItem);
				} catch (RuntimeException e) {
					findItem.setCount(before);
					throw e;
				}
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		insertItem(item);
        // The object now belongs to the warehouse, not its former PC.
        // Keeping _cha points later persistence at the wrong owner.
        item._cha = null;
		_items.add(item);
		return item;
	}

    private static boolean chargeMergeItem(int itemId) {
        switch (itemId) {
        case 40006: case 40007: case 40008: case 140006: case 140008:
        case 41401: case 810006: case 810007: return true;
        default: return false;
        }
    }

    private L1ItemInstance prepareWarehouseWithdrawal(L1ItemInstance item, int count,
            L1Inventory inventory, int feeItemId, int feeAmount, Timestamp withdrawalEndTime,
            Runnable onCommitted) {
        final l1j.server.server.model.L1PcInventory pcInventory =
                (l1j.server.server.model.L1PcInventory) inventory;
        final l1j.server.server.model.Instance.L1PcInstance pc = pcInventory.getOwner();
        final CIOClient session = pc.getNetConnection();
        if (session == null || !session.isConnected()) return null;
        final int stamp = pc.getSessionStamp();
        if (!pc.tryBeginItemTransfer()) return null;
        boolean submitted = false;
        try {
        final int sourceCount = item.getCount();
        final L1ItemInstance destStack = inventory.findItemId(item.getItemId());
        final boolean countMerge = item.isStackable() && destStack != null;
        final boolean chargeMerge = !item.isStackable() && chargeMergeItem(item.getItemId())
                && destStack != null;
        final int destCount = destStack == null ? 0 : destStack.getCount();
        final int destCharge = destStack == null ? 0 : destStack.getChargeCount();
        if (countMerge && (long) destCount + count > L1Inventory.MAX_AMOUNT) return null;
        if (chargeMerge && (long) destCharge + item.getChargeCount() > Integer.MAX_VALUE) return null;
        final L1ItemInstance fee = feeAmount == 0 ? null : inventory.findItemId(feeItemId);
        if (feeAmount != 0 && (fee == null || !fee.isStackable()
                || fee.getCount() < feeAmount)) return null;
        final int feeCount = fee == null ? 0 : fee.getCount();

        final CacheD.WarehouseMove reserved;
        try { reserved = CacheD.beginWarehouseMove(pc.getId(), transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reserved) {
            move.onFailure(() -> CIOThreadCallback.getInstance().completeSession(session, session::kick));
            move.expectOriginal(item);
            if (destStack != null && (countMerge || chargeMerge)) move.expectOriginal(destStack);
            if (fee != null) move.expectOriginal(fee);
            final L1ItemInstance carry;
            if (sourceCount == count) {
                carry = item;
                prepareTransferDelete(item);
            } else {
                carry = allocateDetachedTransfer(item, count);
                move.prepareCarry(carry);
                updateItem(transferCopy(item, item.getId(), sourceCount - count, item.getChargeCount()));
            }
            if (countMerge) {
                move.prepareCharacterWrite(transferCopy(destStack, destStack.getId(),
                        destCount + count, destCharge), false);
            } else if (chargeMerge) {
                move.prepareCharacterWrite(transferCopy(destStack, destStack.getId(),
                        destCount, destCharge + item.getChargeCount()), false);
            } else {
                L1ItemInstance row = characterTransferCopy(carry, carry.getId(),
                        carry.getCount(), carry.getChargeCount());
                if (withdrawalEndTime != null) row.setEndTime(copyTime(withdrawalEndTime));
                move.prepareCharacterWrite(row, true);
            }
            if (fee != null) {
                if (fee == destStack && countMerge) {
                    int resulting = destCount + count - feeAmount;
                    if (resulting <= 0) move.prepareCharacterDelete(fee);
                    else move.prepareCharacterWrite(transferCopy(fee, fee.getId(), resulting,
                            fee.getChargeCount()), false);
                } else if (feeCount == feeAmount) {
                    move.prepareCharacterDelete(fee);
                } else {
                    move.prepareCharacterWrite(transferCopy(fee, fee.getId(),
                            feeCount - feeAmount, fee.getChargeCount()), false);
                }
            }
            move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                    stamp, () -> {
                synchronized (session) {
                    if (!session.isConnected() || pc.getSessionStamp() != stamp
                            || pc.getNetConnection() != session || getItem(item.getId()) != item
                            || item.getCount() != sourceCount
                            || (destStack != null && (inventory.getItem(destStack.getId()) != destStack
                                   || destStack.getCount() != destCount
                                   || destStack.getChargeCount() != destCharge))
                            || (fee != null && (inventory.getItem(fee.getId()) != fee
                                   || fee.getCount() != feeCount)))
                        throw new IllegalStateException("Warehouse withdrawal preimage changed after DB COMMIT");
                    CacheD.applyCommittedWarehouseMove(move, () -> {
                        L1ItemInstance applied = tradePreparedItem(item, count, inventory,
                                feeItemId, feeAmount, withdrawalEndTime, null);
                        if (applied == null)
                            throw new IllegalStateException("Warehouse withdrawal replay failed");
                        if (move.replayCarry() == applied)
                            L1World.getInstance().storeObject(applied);
                    });
                    recordCommittedMove(pc, Warehouse.this, false, item, count);
                    pc.endItemTransfer();
                    // Defer the next selected item until CacheD releases this
                    // item's durable owner lanes; never recursively submit it here.
                    if (onCommitted != null) onCommitted.run();
                }
            }, move::finishOwnerApply));
            submitted = true;
            return item;
        }
        } finally {
            if (!submitted) pc.endItemTransfer();
        }
    }

    public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count, L1Inventory inventory) {
        return tradeItem(item, count, inventory, 0, 0);
    }

    public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count, L1Inventory inventory,
                                                  int feeItemId, int feeAmount) {
        return tradeItem(item, count, inventory, feeItemId, feeAmount, null);
    }

    public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count, L1Inventory inventory,
                                                  int feeItemId, int feeAmount, Runnable onCommitted) {
        return tradePreparedItem(item, count, inventory, feeItemId, feeAmount, null, onCommitted);
    }

    /** Supplementary timed items acquire their deadline in the committed character row. */
    public synchronized L1ItemInstance tradeSupplementaryItem(L1ItemInstance item, int count,
            L1Inventory inventory, Timestamp endTime) {
        return tradeSupplementaryItem(item, count, inventory, endTime, null);
    }

    public synchronized L1ItemInstance tradeSupplementaryItem(L1ItemInstance item, int count,
            L1Inventory inventory, Timestamp endTime, Runnable onCommitted) {
        if (!(this instanceof SupplementaryService)) return null;
        // Per-instance deadlines cannot be represented by a merged stack.
        if (endTime != null && item != null && item.isStackable()
                && inventory != null && inventory.findItemId(item.getItemId()) != null) return null;
        return tradePreparedItem(item, count, inventory, 0, 0, copyTime(endTime), onCommitted);
    }

    private synchronized L1ItemInstance tradePreparedItem(L1ItemInstance item, int count,
            L1Inventory inventory, int feeItemId, int feeAmount, Timestamp withdrawalEndTime,
            Runnable onCommitted) {
        assertWarehouseActive();
        if ((feeItemId == 0) != (feeAmount == 0) || feeAmount < 0) return null;
        if (feeAmount != 0 && feeItemId != 40308 && feeItemId != 40494) return null;
		if (item == null || inventory == null || getItem(item.getId()) != item) return null;
		if (item.getCount() <= 0 || count <= 0 || count > item.getCount()) return null;
		if (item.isEquipped()) return null;
        if (!item.isStackable() && count != 1) return null;
		if (inventory.checkAddItem(item, count) != L1Inventory.OK) return null;
		
        if (!(inventory instanceof l1j.server.server.model.L1PcInventory)) return null;
        final int characterId = ((l1j.server.server.model.L1PcInventory) inventory)
                .getOwner().getId();
        if (!CacheD.isWarehouseApplying())
            return prepareWarehouseWithdrawal(item, count, inventory, feeItemId, feeAmount,
                    withdrawalEndTime, onCommitted);
        final CacheD.WarehouseMove reservation;
        try { reservation = CacheD.beginWarehouseMove(characterId, transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reservation) {
        final CIOClient session = ((l1j.server.server.model.L1PcInventory) inventory)
                .getOwner().getNetConnection();
        if (session != null) move.onFailure(() ->
                CIOThreadCallback.getInstance().completeSession(session, session::kick));
        move.expectOriginal(item);
        L1ItemInstance destStack = inventory.findItemId(item.getItemId());
        if (destStack != null) move.expectOriginal(destStack);
        final int originalCount = item.getCount();
        final int destinationCount = destStack == null ? 0 : destStack.getCount();
        final int destinationCharge = destStack == null ? 0 : destStack.getChargeCount();
        final L1ItemInstance fee = feeAmount == 0 ? null : inventory.findItemId(feeItemId);
        if (feeAmount != 0 && (fee == null || fee.getCount() < feeAmount || !fee.isStackable()))
            return null;
        if (fee != null) move.expectOriginal(fee);
        int feeCountBeforeDebit = fee == null ? 0 : fee.getCount();
        boolean feeDebited = false;
        L1ItemInstance carryItem = null;
        try {
		
		if (item.getCount() == count) {
			deleteItem(item);
			carryItem = item;
		} 
		else {
			carryItem = move.replayCarry(item.getId());
            if (carryItem == null)
                throw new IllegalStateException("Warehouse withdrawal missing committed item ID");
			if (carryItem == null) return null;
			carryItem.setCount(count);
			carryItem.setEnchantLevel(item.getEnchantLevel());
			carryItem.setIdentified(item.isIdentified());
			carryItem.set_durability(item.get_durability());
			carryItem.setChargeCount(item.getChargeCount());
			carryItem.setRemainingTime(item.getRemainingTime());
			carryItem.setLastUsed(item.getLastUsed());
			carryItem.setBless(item.getBless());
			carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel());
            carryItem.setSpecialEnchant(item.getSpecialEnchant());
			/** 패키지상점 **/
			carryItem.setPackage(item.isPackage());
			carryItem.set_bless_level(item.get_bless_level());
            carryItem.set_bless_level2(item.get_bless_level2());
			carryItem.setEndTime(item.getEndTime());
			/**특수 인챈트 시스템**/
			carryItem.set_item_level(item.get_item_level());
			/**여관 리뉴얼**/
			carryItem.setHotel_Town(item.getHotel_Town());
			item.setCount(item.getCount() - count);
			updateItem(item);
		}
        L1ItemInstance stored = null;
        RuntimeException transferFailure = null;
        try {
            if (withdrawalEndTime != null) carryItem.setEndTime(copyTime(withdrawalEndTime));
            stored = inventory.storeTradeItem(carryItem);
        } catch (RuntimeException e) {
            transferFailure = e;
        }
        if (stored != null) {
            // Defer billing until the destination accepted the item, then
            // capture its SQL mutation alongside both item mutations.
            if (fee != null) {
                // Debit may throw after changing memory but before staging SQL.
                // Treat the fee as needing restoration until move.commit succeeds.
                feeCountBeforeDebit = fee.getCount();
                feeDebited = true;
                if (!inventory.debitWarehouseFee(fee, feeAmount))
                    throw new IllegalStateException("Warehouse fee unavailable during move");
            }
            move.commit();
            return stored;
        }

		// Destination refused the item. Restore the source's in-memory
		// ownership/count rather than silently consuming it.
		if (carryItem == item) {
			_items.add(item);
			insertItem(item);
		} else {
			item.setCount(item.getCount() + count);
			updateItem(item);
		}
        if (transferFailure != null) throw transferFailure;
		return null;
        } catch (RuntimeException failure) {
            // Each recovery step is independent: failure to restore billing
            // must not skip restoration of the destination and source items.
            if (feeDebited) {
                try { inventory.restoreWarehouseFee(fee, feeCountBeforeDebit); }
                catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
            }
            try {
                if (carryItem != null && inventory.getItem(carryItem.getId()) == carryItem) {
                    inventory.deleteItem(carryItem);
                } else if (destStack != null) {
                    final int excess = destStack.getCount() - destinationCount;
                    if (excess > 0) {
                        inventory.removeItem(destStack, excess);
                    } else if (destStack.getChargeCount() != destinationCharge) {
                        destStack.setChargeCount(destinationCharge);
                        inventory.updateItem(destStack);
                    }
                }
            } catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
            try {
                if (getItem(item.getId()) != item) {
                    item.setCount(originalCount);
                    item._cha = null;
                    insertItem(item);
                    _items.add(item);
                } else if (item.getCount() != originalCount) {
                    item.setCount(originalCount);
                    updateItem(item);
                }
            } catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
            throw failure;
        }
        } // warehouse move scope

	}
	
	public static void present(String account, int itemid, int enchant,
			int count) throws Exception {
		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null)
			return;

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();

			if (account.compareToIgnoreCase("*") == 0) {
				pstm = con.prepareStatement("SELECT * FROM accounts");
			} else {
				pstm = con
						.prepareStatement("SELECT * FROM accounts WHERE login=?");
				pstm.setString(1, account);
			}
			rs = pstm.executeQuery();

			ArrayList<String> accountList = new ArrayList<String>();
			while (rs.next()) {
				accountList.add(rs.getString("login"));
			}

			present(accountList, itemid, enchant, count);

		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

	}
	
	public static void present(int minlvl, int maxlvl, int itemid, int enchant,
			int count) throws Exception {
		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null)
			return;

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();

			pstm = con
					.prepareStatement("SELECT distinct(account_name) as account_name FROM characters WHERE level between ? and ?");
			pstm.setInt(1, minlvl);
			pstm.setInt(2, maxlvl);
			rs = pstm.executeQuery();

			ArrayList<String> accountList = new ArrayList<String>();
			while (rs.next()) {
				accountList.add(rs.getString("account_name"));
			}

			present(accountList, itemid, enchant, count);

		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

	}
	
	private static void present(ArrayList<String> accountList, int itemid,
			int enchant, int count) throws Exception {

		L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		if (temp == null) {
			throw new Exception("존재하지 않는 아이템 ID");
		}
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			con.setAutoCommit(false);
			L1ItemInstance item = null;
			for (String account : accountList) {
				if (temp.isStackable()) {
					item = ItemTable.getInstance().createItem(itemid);
					item.setEnchantLevel(enchant);
					item.setCount(count);

					pstm = con
							.prepareStatement("INSERT INTO character_supplementary_service SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?");
					pstm.setInt(1, item.getId());
					pstm.setString(2, account);
					pstm.setInt(3, item.getItemId());
					pstm.setString(4, item.getName());
					pstm.setInt(5, item.getCount());
					pstm.setInt(6, item.getEnchantLevel());
					pstm.setInt(7, item.isIdentified() ? 1 : 0);
					pstm.setInt(8, item.get_durability());
					pstm.setInt(9, item.getChargeCount());
					pstm.setInt(10, item.getRemainingTime());
					pstm.executeUpdate();
				} else {
					item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(enchant);

						pstm = con
								.prepareStatement("INSERT INTO character_supplementary_service SET id = ?, account_name = ?, item_id = ?, item_name = ?, count = ?, is_equipped=0, enchantlvl = ?, is_id = ?, durability = ?, charge_count = ?, remaining_time = ?");
						pstm.setInt(1, item.getId());
						pstm.setString(2, account);
						pstm.setInt(3, item.getItemId());
						pstm.setString(4, item.getName());
						pstm.setInt(5, item.getCount());
						pstm.setInt(6, item.getEnchantLevel());
						pstm.setInt(7, item.isIdentified() ? 1 : 0);
						pstm.setInt(8, item.get_durability());
						pstm.setInt(9, item.getChargeCount());
						pstm.setInt(10, item.getRemainingTime());
						pstm.executeUpdate();
					}
				}
			}

			con.commit();
			con.setAutoCommit(true);
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException ignore) {
				// ignore
			}
			e.printStackTrace();
			throw new Exception(".present 처리중에 에러가 발생했습니다.");
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public L1ItemInstance getItem(int objectId) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getId() == objectId) {
				return item;
			}
		}
		return null;
	}
	
	public synchronized void removeItem(L1ItemInstance item) {
        assertWarehouseActive();
        if (item == null || !_items.contains(item)) return;
        // Preserve in-memory ownership if admission to CacheD is rejected.
        deleteItem(item);
        _items.remove(item);
	}

	public List<L1ItemInstance> getItems() {
        assertWarehouseActive();
		return readOnlyItems;
	}

	public synchronized void clearItems() {
        assertWarehouseActive();
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			L1World.getInstance().removeObject(item);
		}
		_items.clear();
	}

	public L1ItemInstance[] findItemsId(int id) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<>(4);
		for (L1ItemInstance item : _items) {
			if (item.getItemId() == id) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}

	public boolean checkItem(int id, int count) {
		if (count == 0) return true;
		if (ItemTable.getInstance().getTemplate(id).isStackable()) {
			L1ItemInstance item = findItemId(id);
			if (item != null && item.getCount() >= count) {
				return true;
			}
		} 
		else {
			Object[] itemList = findItemsId(id);
			if (itemList.length >= count) {
				return true;
			}
		}
		return false;
	}

	public int getSize() {
		return _items.size();
	}

	public int checkAddItemToWarehouse(L1ItemInstance item, int count) {
        assertWarehouseActive();
		if (item == null) 						return -1;
		if (item.getCount() <= 0 || count <= 0) return -1;

		final int OK = 0, SIZE_OVER = 1;
		final int maxSize = getMax(), SIZE = getSize();
		if ( SIZE > maxSize || (SIZE == maxSize && (!item.isStackable() || !checkItem(item.getItem().getItemId(), 1))))  
			return SIZE_OVER;
	
		return OK;
	}
}
