package l1j.server.server.model.item.function;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.character.skill.L1SkillId;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_PUPLE;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_TOPAZ;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Commits a dragon gem debit and its account blessing increment in one receipt. */
public final class AinBlessingItems {
	private static final Logger LOG = Logger.getLogger(AinBlessingItems.class.getName());
	private static final int MAX_BLESS = 50000000;

	private AinBlessingItems() {}

	public static void consume(L1PcInstance pc, L1ItemInstance input, int delta,
			int maximumBefore, Runnable afterCommitted) {
		if (pc == null || input == null || delta <= 0 || maximumBefore < 0
				|| maximumBefore > MAX_BLESS || pc.getAccount() == null
				|| pc.getInventory().GetByServerID(input.getId()) != input) return;
		final Account account = pc.getAccount();
		final String login = account.getName();
		if (login == null || login.isEmpty()) return;
		if (account.getBlessOfAin() > maximumBefore) return;
		try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
				CacheD.accountOrderKey(login))) {
			if (!tx.debit(input, 1)) return;
			tx.requireTables("accounts");
			// Share the CacheD account owner's FIFO lane. An older account
			// snapshot must not overwrite a committed gem reward afterwards.
			tx.sql(con -> {
				try (PreparedStatement ps = con.prepareStatement(
						"UPDATE accounts SET bless_of_ain=LEAST(?, bless_of_ain+?) "
								+ "WHERE login=? AND bless_of_ain<=?")) {
					ps.setInt(1, MAX_BLESS);
					ps.setInt(2, delta);
					ps.setString(3, login);
					ps.setInt(4, maximumBefore);
					if (ps.executeUpdate() != 1) {
						// Emerald is still usable at the hard blessing cap:
						// its 30-minute skill effect is independent of the increment.
						// MySQL reports zero changed rows for a capped value.
						if (maximumBefore != MAX_BLESS || !atMaximum(con, login))
							throw new SQLException("Account blessing threshold changed or missing");
					}
				}
			});
			tx.onFailure(() -> SC_SystemMessage.send(pc,
					"아인하사드 축복 사용을 저장하지 못했습니다. 아이템을 확인해 주세요."));
			CDB.RequestUseItem(pc, tx, () -> {
				// Owner replay runs before the reserved account lane is released.
				// The ordinary CacheD account snapshot now contains this award
				// as well as any EXP changes that occurred during the DB job.
				account.addBlessOfAin(delta, pc);
				if (afterCommitted != null) afterCommitted.run();
			});
		} catch (RuntimeException failure) {
			LOG.log(Level.WARNING, "Dragon gem transaction prepare failed: " + pc.getId(), failure);
			SC_SystemMessage.send(pc, "아인하사드 축복 사용을 처리하지 못했습니다.");
		}
	}


	// Preserve the formerly fallthrough case 410139 (restricted to level < 82).
	// true skips the original per-use delay bookkeeping, as a direct return did.

	// Preserve the public item-use entrypoint for existing callers.
	public static boolean useGemCase(L1PcInstance pc, L1ItemInstance item, int itemId) {
		return AinBlessingGemConsumables.useGemCase(pc, item, itemId);
	}

	private static boolean atMaximum(java.sql.Connection con, String login) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT bless_of_ain FROM accounts WHERE login=? FOR UPDATE")) {
			ps.setString(1, login);
			try (ResultSet rows = ps.executeQuery()) {
				return rows.next() && rows.getInt(1) == MAX_BLESS && !rows.wasNull();
			}
		}
	}
}
