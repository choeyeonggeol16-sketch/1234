package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.UserCommands;
import l1j.server.server.GMCommands;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;

public class ItemTradeTable {

	private static final Logger _log = Logger.getLogger(ItemTradeTable.class.getName());

	private volatile static ItemTradeTable _instance;

	private ItemTradeTable() {
	}

	public static synchronized ItemTradeTable getInstance() {
		if (_instance == null) {
			_instance = new ItemTradeTable();
		}
		return _instance;
	}

	/** Stage the listing in the SAME CacheD transaction as the two item debits.
	 * No DB access runs on the game owner thread. Do not call this after commit.
	 * A unique AUTO_INCREMENT id is required; a missing migration fails closed.
	 */
	public void stageTopic(L1InventoryTransaction tx, L1PcInstance player,
			String date, L1ItemInstance item, int count) {
		if (tx == null || player == null || item == null || count <= 0)
			throw new IllegalArgumentException("Invalid consignment listing");
		final String name = player.getName();
		final String account = player.getAccountName();
		final int itemId = item.getItemId();
		final String itemName = item.getName();
		final int enchant = item.getEnchantLevel();
		final int attr = item.getAttrEnchantLevel();
		final int bless = item.get_bless_level();
		final int level = item.get_item_level();
		final String listingDate = date;
		tx.sql(con -> {
			// Old MAX(id)+1 could race another seller; never allocate an id in Java.
			try (PreparedStatement check = con.prepareStatement(
					"SELECT EXTRA,COLUMN_KEY FROM information_schema.COLUMNS "
					+ "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_item' AND COLUMN_NAME='id'");
				 ResultSet columns = check.executeQuery()) {
				if (!columns.next())
					throw new SQLException("Missing board_item.id column");
				final String extra = columns.getString(1);
				if (extra == null || !extra.toLowerCase(Locale.ROOT).contains("auto_increment")
						|| !"PRI".equalsIgnoreCase(columns.getString(2)))
					throw new SQLException("Consignment requires board_item.id AUTO_INCREMENT PRIMARY KEY");
			}
			try (PreparedStatement ps = con.prepareStatement(
					"INSERT INTO board_item (name,item_id,item_name,item_enchantlvl,count,AccountName,date,"
					+ "UpClick,Blesslevel,item_level) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
				ps.setString(1,name);
				ps.setInt(2,itemId);
				ps.setString(3,itemName);
				ps.setInt(4,enchant);
				ps.setInt(5,count);
				ps.setString(6,account);
				ps.setString(7,listingDate);
				ps.setInt(8,attr);
				ps.setInt(9,bless);
				ps.setInt(10,level);
				if (ps.executeUpdate() != 1)
					throw new SQLException("Consignment listing insert did not persist one row");
			}
		});
	}


    private static final class Listing {
        final int id, itemId, enchant, price, attr, blessLevel, itemLevel;
        final String seller, sellerCharacter, itemName;
        Listing(int id, ResultSet row) throws SQLException {
            this.id = id;
            itemId = row.getInt("item_id");
            enchant = row.getInt("item_enchantlvl");
            price = row.getInt("count");
            attr = row.getInt("UpClick");
            blessLevel = row.getInt("Blesslevel");
            itemLevel = row.getInt("item_level");
            seller = row.getString("AccountName");
            sellerCharacter = row.getString("name");
            itemName = row.getString("item_name");
        }
        boolean matches(ResultSet r) throws SQLException {
            return r.getInt("item_id") == itemId
                    && r.getInt("item_enchantlvl") == enchant
                    && r.getInt("count") == price
                    && r.getInt("UpClick") == attr
                    && r.getInt("Blesslevel") == blessLevel
                    && r.getInt("item_level") == itemLevel
                    && Objects.equals(seller, r.getString("AccountName"))
                    && Objects.equals(sellerCharacter, r.getString("name"))
                    && Objects.equals(itemName, r.getString("item_name"));
        }
    }

    /** Command entry: the initial board read uses an EXISTING CacheD worker,
     * and the inventory preparation returns to the original CIO owner.
     * All irreversible mutations are committed together by L1InventoryTransaction.
     */
    public void buyTopic(L1PcInstance buyer, String argument, boolean gmCurrency) {
        transactTopic(buyer, argument, gmCurrency, false);
    }

    public void cancelTopic(L1PcInstance seller, String argument) {
        transactTopic(seller, argument, false, true);
    }

    private void transactTopic(L1PcInstance actor, String argument, boolean gmCurrency, boolean cancel) {
        final int id;
        try { id = Integer.parseInt(argument.trim()); }
        catch (RuntimeException e) {
            SC_SystemMessage.send(actor, cancel ? "사용법: .물품취소 (게시글번호)" : "사용법: .물품구입 (게시글번호)");
            return;
        }
        if (id <= 0) {
            SC_SystemMessage.send(actor, "올바른 게시글번호를 입력해 주세요.");
            return;
        }
        final int stamp = actor.getSessionStamp();
        final CIOClient session = actor.getNetConnection();
        if (session == null || !session.isConnected() || session.getActiveChar() != actor) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(actor.getId(), () -> {
                Listing listing = null;
                SQLException error = null;
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "SELECT name,item_id,item_name,item_enchantlvl,count,AccountName,"
                             + "UpClick,Blesslevel,item_level FROM board_item WHERE id=?")) {
                    ps.setInt(1, id);
                    try (ResultSet rows = ps.executeQuery()) {
                        if (rows.next()) listing = new Listing(id, rows);
                    }
                } catch (SQLException failure) { error = failure; }
                final Listing found = listing;
                final SQLException readError = error;
                CIOThreadCallback.getInstance().completeWarehouseMove(actor, stamp, () -> {
                    if (actor.getSessionStamp() != stamp || actor.getNetConnection() != session
                            || !session.isConnected() || session.getActiveChar() != actor) return;
                    if (readError != null) {
                        _log.log(Level.WARNING, "Consignment board read failed", readError);
                        SC_SystemMessage.send(actor, "위탁 게시판을 조회하지 못했습니다.");
                    } else if (found == null || found.itemId <= 0 || found.price <= 0
                            || found.seller == null || found.seller.isEmpty()) {
                        SC_SystemMessage.send(actor, "판매글이 없거나 이미 처리되었습니다.");
                    } else {
                        prepareTopic(actor, found, gmCurrency, cancel);
                    }
                }, failure -> {
                    if (failure != null) _log.log(Level.WARNING, "Consignment owner callback", failure);
                });
            });
        } catch (RuntimeException busy) {
            _log.log(Level.FINE, "Consignment board read admission rejected", busy);
            SC_SystemMessage.send(actor, "위탁 거래가 혼잡합니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static L1ItemInstance detachedItem(int template, int count) {
        L1Item definition = ItemTable.getInstance().getTemplate(template);
        if (definition == null || count <= 0) throw new IllegalArgumentException("Missing item template/quantity: " + template);
        L1ItemInstance item = new L1ItemInstance();
        item.setId(IdFactory.getInstance().nextId());
        item.setItem(definition);
        item.setCount(count);
        item.setBless(definition.getBless());
        item.setIdentified(true);
        return item;
    }

    private static void prepareTopic(L1PcInstance actor, Listing listing, boolean gmCurrency, boolean cancel) {
        if (cancel && !actor.getAccountName().equalsIgnoreCase(listing.seller)) {
            SC_SystemMessage.send(actor, "본인 판매글만 취소할 수 있습니다.");
            return;
        }
        final L1ItemInstance reward;
        try {
            reward = detachedItem(listing.itemId, 1);
            reward.setEnchantLevel(listing.enchant);
            reward.setAttrEnchantLevel(listing.attr);
            reward.set_bless_level(listing.blessLevel);
            reward.set_item_level(listing.itemLevel);
        } catch (RuntimeException invalid) {
            _log.log(Level.WARNING, "Invalid consignment reward item listing=" + listing.id, invalid);
            SC_SystemMessage.send(actor, "판매 아이템 정보가 올바르지 않습니다.");
            return;
        }
        // Reserve the seller account's existing warehouse DB lane, not a new queue.
        // This prevents a seller's warehouse snapshot racing an uncommitted payout.
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(actor,
                CacheD.accountOrderKey(listing.seller))) {
            if (!cancel && !tx.debit(gmCurrency ? 40308 : 600254, listing.price)) {
                SC_SystemMessage.send(actor, "소지 수표가 부족합니다.");
                return;
            }
            if (tx.grant(reward) == null) {
                SC_SystemMessage.send(actor, "인벤토리 공간 또는 아이템 정보가 부족합니다.");
                return;
            }
            tx.requireTables("board_item");
            final L1ItemInstance payoutA;
            final L1ItemInstance payoutB;
            if (cancel) {
                payoutA = payoutB = null;
            } else if (gmCurrency) {
                int sale = (int) (listing.price * 0.95d);
                int unit = sale / 100000000;
                int rest = sale - unit * 100000000;
                payoutA = unit > 0 ? detachedItem(400254, unit) : null;
                payoutB = rest > 0 ? detachedItem(40308, rest) : null;
            } else {
                payoutA = detachedItem(600254, listing.price);
                payoutB = null;
            }
            if (!cancel) tx.requireTables("character_supplementary_service");
            tx.sql(con -> {
                // Pre-read is NOT authorization. Lock and compare the current row
                // INSIDE the same transaction that changes the currency and reward.
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT name,item_id,item_name,item_enchantlvl,count,AccountName,"
                        + "UpClick,Blesslevel,item_level FROM board_item WHERE id=? FOR UPDATE")) {
                    lock.setInt(1, listing.id);
                    try (ResultSet r = lock.executeQuery()) {
                        if (!r.next() || !listing.matches(r))
                            throw new SQLException("Consignment listing already claimed/changed: " + listing.id);
                    }
                }
                if (cancel && !actor.getAccountName().equalsIgnoreCase(listing.seller))
                    throw new SQLException("Consignment cancel owner mismatch: " + listing.id);
                if (!cancel) {
                    try (PreparedStatement account = con.prepareStatement(
                            "SELECT login FROM accounts WHERE login=?")) {
                        account.setString(1, listing.seller);
                        try (ResultSet r = account.executeQuery()) {
                            if (!r.next()) throw new SQLException("Consignment seller account missing: " + listing.id);
                        }
                    }
                    insertPayout(con, listing.seller, payoutA);
                    if (payoutB != null) insertPayout(con, listing.seller, payoutB);
                }
                try (PreparedStatement remove = con.prepareStatement("DELETE FROM board_item WHERE id=?")) {
                    remove.setInt(1, listing.id);
                    if (remove.executeUpdate() != 1)
                        throw new SQLException("Consignment listing claim failed: " + listing.id);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(actor,
                    "위탁 거래가 확정되지 않았습니다. 아이템을 확인해 주세요."));
            tx.commit(() -> {
                // This callback is on actor's CIO owner AFTER durable COMMIT and
                // buyer/seller-cancel inventory replay. NEVER re-run a failed sale.
                if (cancel) {
                    SC_SystemMessage.send(actor, "판매 취소가 완료되어 물품이 반환되었습니다.");
                } else {
                    SC_SystemMessage.send(actor, "[" + reward.getLogName() + "] 물품을 구입하였습니다.");
                    try {
                        if (gmCurrency) GMCommands.WriteLetter22(actor, listing.sellerCharacter,
                                listing.enchant, listing.itemName);
                        else UserCommands.WriteLetter22(actor, listing.sellerCharacter,
                                listing.enchant, listing.itemName);
                    } catch (RuntimeException mailFailure) {
                        // Letter is notification only; money and goods are
                        // already committed. Never reverse/retry the purchase.
                        _log.log(Level.WARNING, "Committed consignment notification failed id=" + listing.id, mailFailure);
                    }
                }
            });
        } catch (RuntimeException busy) {
            _log.log(Level.WARNING, "Consignment preparation rejected id=" + listing.id, busy);
            SC_SystemMessage.send(actor, "위탁 거래를 준비하지 못했습니다. 다시 조회해 주세요.");
        }
    }

    private static void insertPayout(Connection con, String seller, L1ItemInstance item) throws SQLException {
        if (item == null) return;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO character_supplementary_service "
                + "(id,account_name,item_id,item_name,count,is_equipped,enchantlvl,is_id,"
                + "durability,charge_count,remaining_time) VALUES (?,?,?,?,?,0,?,?,?,?,?)")) {
            ps.setInt(1, item.getId());
            ps.setString(2, seller);
            ps.setInt(3, item.getItemId());
            ps.setString(4, item.getName());
            ps.setInt(5, item.getCount());
            ps.setInt(6, item.getEnchantLevel());
            ps.setInt(7, item.isIdentified() ? 1 : 0);
            ps.setInt(8, item.get_durability());
            ps.setInt(9, item.getChargeCount());
            ps.setInt(10, item.getRemainingTime());
            if (ps.executeUpdate() != 1) throw new SQLException("Consignment payout insert failed");
        }
    }

	public void boardclear() {
		String sql = "DELETE FROM board_item";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.execute();
		} catch (SQLException e) {
			_log.info("게시판 리셋 에러4!");
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	public void deleteTopic(int number) {
		String sql = "DELETE FROM board_item WHERE id = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, number);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "물품거래 deleteTopic error", e);
		}
	}
}