package l1j.server.server.model.item.Card;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/**
 * Card synthesis economy boundary. Account card decrements, adena debit and
 * reward grant are one CacheD/InnoDB receipt. No account object is changed
 * until that receipt has committed and the inventory owner has been released.
 * No new worker, queue, or long-lived transaction state is introduced.
 */
final class CardAlchemyReceipt {
    private static final Logger LOG = Logger.getLogger(CardAlchemyReceipt.class.getName());

    private CardAlchemyReceipt() {}

    // Avoid Pattern compilation in item-use path; column names are source-owned.
    private static boolean validColumn(String name) {
        if (name == null || name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) continue;
            if (i > 0 && c >= '0' && c <= '9') continue;
            return false;
        }
        return true;
    }

    static boolean submit(L1PcInstance pc, Account account, int cost, int resultItemId,
            int firstId, String[] columns, Consumer<Account>[] debitCallbacks,
            IntUnaryOperator balance, int cardA, int cardB, int cardC, int cardD,
            int selectedCount, Runnable afterCommit) {
        if (pc == null || account == null || pc.getAccount() != account || cost <= 0
                || resultItemId <= 0 || (selectedCount != 2 && selectedCount != 4)) return false;
        final int[] cards = selectedCount == 2
                ? new int[]{cardA, cardB} : new int[]{cardA, cardB, cardC, cardD};
        final int[] counts = new int[selectedCount];
        for (int i = 0; i < selectedCount; i++) {
            final int index = cards[i] - firstId;
            if (index < 0 || index >= columns.length || index >= debitCallbacks.length
                    || columns[index] == null || debitCallbacks[index] == null) return false;
            for (int j = 0; j < i; j++) if (cards[i] == cards[j]) return false;
            counts[i] = balance.applyAsInt(cards[i]);
            if (counts[i] <= 1) return false;
        }
        // Column identifiers come exclusively from immutable source-owned tables.
        // User input contributes only prepared-statement numeric values.
        final StringBuilder statement = new StringBuilder(260);
        statement.append("UPDATE accounts SET ");
        for (int i = 0; i < selectedCount; i++) {
            final String col = columns[cards[i] - firstId];
            if (!validColumn(col)) return false;
            statement.append(col).append('=').append(col).append("-1,");
        }
        statement.append("info_card_a=0,info_card_b=0,info_card_c=0");
        if (selectedCount == 4) statement.append(",info_card_d=0");
        statement.append(" WHERE login=?");
        for (int i = 0; i < selectedCount; i++)
            statement.append(" AND ").append(columns[cards[i] - firstId]).append("=?");
        final String sql = statement.toString();
        final String accountName = account.getName();

        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey(accountName))) {
            if (!tx.debit(40308, cost)) {
                SC_SystemMessage.send(pc, "합성에 필요한 아데나가 부족합니다.");
                return false;
            }
            if (tx.grant(resultItemId, 1) == null) {
                SC_SystemMessage.send(pc, "합성 보상을 지급할 공간이나 아이템 데이터가 없습니다.");
                return false;
            }
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, accountName);
                    for (int i = 0; i < selectedCount; i++) ps.setInt(i + 2, counts[i]);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Card synthesis account preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "카드 합성 거래를 저장하지 못했습니다. 재료와 카드를 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (pc.getAccount() != account)
                    throw new IllegalStateException("Card synthesis account session changed");
                for (int i = 0; i < selectedCount; i++) {
                    if (balance.applyAsInt(cards[i]) != counts[i])
                        throw new IllegalStateException("Card synthesis memory preimage changed");
                }
                for (int i = 0; i < selectedCount; i++)
                    debitCallbacks[cards[i] - firstId].accept(account);
                account.setInfo_card_a(0);
                account.setInfo_card_b(0);
                account.setInfo_card_c(0);
                if (selectedCount == 4) account.setInfo_card_d(0);
                afterCommit.run();
            });
            return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Card synthesis receipt prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "카드 합성 거래를 시작하지 못했습니다.");
            return false;
        }
    }
}
