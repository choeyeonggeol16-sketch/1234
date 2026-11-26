package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.warehouse.Warehouse;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.model.inventory.CInventory;

public class L1DwarfForPackageInventory extends Warehouse {
    private static final long serialVersionUID = 1L;
    private final L1PcInstance owner;

    public L1DwarfForPackageInventory(L1PcInstance owner) {
        super("package");
        this.owner = owner;
    }

    @Override
    public String getName() {
        return owner.getAccountName();
    }

    @Override
    protected int getMax() {
        return Config.MAX_PERSONAL_WAREHOUSE_ITEM;
    }

    @Override
    public synchronized void loadItems() {
        assertWarehouseReadsCurrent(PACKAGE_ROWS);
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement query = con.prepareStatement(
                     "SELECT * FROM character_package_warehouse WHERE account_name=?")) {
            query.setString(1, getName());
            try (ResultSet rows = query.executeQuery()) {
                loadItemsFromSnapshot(rows);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Package warehouse load failed: " + getName(), failure);
        }
    }

    public synchronized void loadItemsFromSnapshot(ResultSet rows) throws SQLException {
        if (rows == null) return;
        installLoadedSnapshot(readDetachedSnapshot(rows));
    }

    /** Read detached items only; no warehouse/world mutation or JDBC writes. */
    public static java.util.List<L1ItemInstance> readDetachedSnapshot(ResultSet rows) throws SQLException {
        List<L1ItemInstance> loaded = new ArrayList<>();
        while (rows.next()) {
            int id = rows.getInt("id");
            int count = rows.getInt("count");
            L1Item template = CObjectDB.GetItem(rows.getInt("item_id"));
            if (template == null || count <= 0) throw new SQLException("Invalid package item: " + id);
            L1ItemInstance item = new L1ItemInstance(template, count);
            item.setId(id);
            item.setEnchantLevel(rows.getInt("enchantlvl"));
            item.setIdentified(rows.getInt("is_id") != 0);
            item.set_durability(rows.getInt("durability"));
            item.setChargeCount(rows.getInt("charge_count"));
            item.setRemainingTime(rows.getInt("remaining_time"));
            item.setLastUsed(rows.getTimestamp("last_used"));
            item.setBless(template.getBless());
            item.setAttrEnchantLevel(rows.getInt("attr_enchantlvl"));
            item.setSpecialEnchant(rows.getInt("special_enchant"));
            item.set_bless_level(rows.getInt("bless_level"));
            item.set_item_level(rows.getInt("item_level"));
            item.setHotel_Town(rows.getString("Hotel_Town"));
            item.setEndTime(rows.getTimestamp("end_time"));
            loaded.add(item);
        }
        // Package gifts may contain several adena rows. Keep their SQL identity
        // until withdrawal, where the normal inventory stack merge is atomic.
        return loaded;
    }

    @Override
    public synchronized void insertItem(L1ItemInstance item) {
        submitWarehouseInsert(PACKAGE_ROWS, item);
    }

    @Override
    public synchronized void updateItem(L1ItemInstance item) {
        submitWarehouseUpdate(PACKAGE_ROWS, item);
    }

    @Override
    public synchronized void deleteItem(L1ItemInstance item) {
        if (item == null || GetByServerID(item.getId()) != item)
            throw new IllegalArgumentException("Package item no longer owned");
        submitWarehouseDelete(PACKAGE_ROWS, item);
        _items.remove(item);
    }

	public static void present(String account, int itemid, int enchant,
			int count) throws Exception {

		L1Item temp = CObjectDB.GetItem(itemid);
		if (temp == null) {
			return;
		}

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

		L1Item temp = CObjectDB.GetItem(itemid);
		if (temp == null) {
			return;
		}

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

    private static void present(ArrayList<String> accounts, int itemId, int enchant, int count) throws SQLException {
        L1Item template = CObjectDB.GetItem(itemId);
        if (template == null || count <= 0 || count > CInventory.MAX_AMOUNT)
            throw new IllegalArgumentException("Invalid package item or count");
        final String sql = "INSERT INTO character_package_warehouse "
                + "(id,account_name,item_id,item_name,count,is_equipped,enchantlvl,is_id,durability,charge_count,remaining_time) "
                + "VALUES (?,?,?,?,?,0,?,0,0,0,0)";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement insert = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            try {
                for (String account : accounts) {
                    int rows = template.isStackable() ? 1 : count;
                    for (int i = 0; i < rows; i++) {
                        insert.setInt(1, IdFactory.getInstance().nextId());
                        insert.setString(2, account);
                        insert.setInt(3, itemId);
                        insert.setString(4, template.getName());
                        insert.setInt(5, template.isStackable() ? count : 1);
                        insert.setInt(6, enchant);
                        if (insert.executeUpdate() != 1) throw new SQLException("Package gift insert failed");
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException failure) {
                try { con.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
}
