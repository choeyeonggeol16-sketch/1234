package l1j.server.server.model.item.Card;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;
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

    /** A capture-free getter: callers share one static method reference per tier. */
    @FunctionalInterface
    interface CardCount {
        int get(Account account, int cardId);
    }


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

    private static boolean validCard(int index, String[] columns,
            Consumer<Account>[] debitCallbacks) {
        return index >= 0 && index < columns.length &&
                index < debitCallbacks.length && columns[index] != null &&
                debitCallbacks[index] != null;
    }

    static boolean submit(L1PcInstance pc, Account account, int cost, int resultItemId,
            int firstId, String[] columns, Consumer<Account>[] debitCallbacks,
            CardCount balance, int cardA, int cardB, int cardC, int cardD,
            int selectedCount, Runnable afterCommit) {
        if (pc == null || account == null || pc.getAccount() != account || cost <= 0
                || resultItemId <= 0 || (selectedCount != 2 && selectedCount != 4)) return false;
        if (columns == null || debitCallbacks == null || balance == null || afterCommit == null)
            return false;
        // Validate the four primitive IDs directly. Synthesis does not need to
        // allocate temporary card/count arrays on every click.
        if (cardA == cardB || (selectedCount == 4 &&
                (cardA == cardC || cardA == cardD || cardB == cardC ||
                 cardB == cardD || cardC == cardD))) return false;
        final int indexA = cardA - firstId;
        final int indexB = cardB - firstId;
        final int indexC = cardC - firstId;
        final int indexD = cardD - firstId;
        if (!validCard(indexA, columns, debitCallbacks) ||
                !validCard(indexB, columns, debitCallbacks) ||
                (selectedCount == 4 && (!validCard(indexC, columns, debitCallbacks) ||
                                         !validCard(indexD, columns, debitCallbacks)))) return false;
        final int countA = balance.get(account, cardA);
        final int countB = balance.get(account, cardB);
        final int countC = selectedCount == 4 ? balance.get(account, cardC) : 0;
        final int countD = selectedCount == 4 ? balance.get(account, cardD) : 0;
        if (countA <= 1 || countB <= 1 ||
                (selectedCount == 4 && (countC <= 1 || countD <= 1))) return false;
        // The selection is transient Account state. Snapshot both unused slots
        // in a two-card synthesis as well: a prior four-card menu may have
        // left values there. Do not discard another session's later selection
        // silently after this receipt commits.
        final int selectionC = selectedCount == 4 ? cardC : account.getInfo_card_c();
        final int selectionD = selectedCount == 4 ? cardD : account.getInfo_card_d();
        if (account.getInfo_card_a() != cardA || account.getInfo_card_b() != cardB
                || account.getInfo_card_c() != selectionC
                || account.getInfo_card_d() != selectionD) return false;
        // Column identifiers come exclusively from immutable source-owned tables.
        // User input contributes only prepared-statement numeric values.
        final StringBuilder statement = new StringBuilder(260);
        statement.append("UPDATE accounts SET ");
        for (int i = 0; i < selectedCount; i++) {
            final String col = columns[switch (i) {
                case 0 -> indexA;
                case 1 -> indexB;
                case 2 -> indexC;
                default -> indexD;
            }];
            if (!validColumn(col)) return false;
            statement.append(col).append('=').append(col).append("-1,");
        }
        // Always clear all four persisted slots, even for two-card legend
        // synthesis. A stale fourth slot otherwise reappears on relogin.
        statement.append("info_card_a=0,info_card_b=0,info_card_c=0,info_card_d=0");
        statement.append(" WHERE login=?");
        for (int i = 0; i < selectedCount; i++)
            statement.append(" AND ").append(columns[switch (i) {
                case 0 -> indexA;
                case 1 -> indexB;
                case 2 -> indexC;
                default -> indexD;
            }]).append("=?");
        final String sql = statement.toString();
        final String accountName = account.getName();

        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey(accountName))) {
            // The selection and balances above were read before the inventory
            // owner gate was acquired. A menu action or another account session
            // could have changed them in that window. Never start a receipt
            // using a stale set of cards, even if the later SQL CAS would fail.
            if (pc.getAccount() != account
                    || account.getInfo_card_a() != cardA
                    || account.getInfo_card_b() != cardB
                    || account.getInfo_card_c() != selectionC
                    || account.getInfo_card_d() != selectionD
                    || balance.get(account, cardA) != countA
                    || balance.get(account, cardB) != countB
                    || (selectedCount == 4 &&
                        (balance.get(account, cardC) != countC
                         || balance.get(account, cardD) != countD))) {
                SC_SystemMessage.send(pc, "카드 선택 또는 수량이 변경되었습니다. 다시 확인해 주세요.");
                return false;
            }
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
                    ps.setInt(2, countA);
                    ps.setInt(3, countB);
                    if (selectedCount == 4) {
                        ps.setInt(4, countC);
                        ps.setInt(5, countD);
                    }
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Card synthesis account preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "카드 합성 거래를 저장하지 못했습니다. 재료와 카드를 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (pc.getAccount() != account)
                    throw new IllegalStateException("Card synthesis account session changed");
                // Selection is a transient UI hint, not part of the committed
                // economy. A different account session may have chosen new cards
                // while this receipt was in flight. The inventory and account
                // counters are already durable; quarantining on UI-only changes
                // would strand a successfully committed synthesis. Keep the
                // economic preimage guard below, but preserve newer selections.
                if (balance.get(account, cardA) != countA ||
                        balance.get(account, cardB) != countB ||
                        (selectedCount == 4 &&
                            (balance.get(account, cardC) != countC ||
                             balance.get(account, cardD) != countD)))
                    throw new IllegalStateException("Card synthesis memory preimage changed");
                debitCallbacks[indexA].accept(account);
                debitCallbacks[indexB].accept(account);
                if (selectedCount == 4) {
                    debitCallbacks[indexC].accept(account);
                    debitCallbacks[indexD].accept(account);
                }
                // The receipt clears persisted selection slots. Clear the
                // live slots only if the player has not selected another set
                // in the meantime. Later selection edits are never overwritten.
                if (account.getInfo_card_a() == cardA && account.getInfo_card_b() == cardB
                        && account.getInfo_card_c() == selectionC
                        && account.getInfo_card_d() == selectionD) {
                    account.setInfo_card_a(0);
                    account.setInfo_card_b(0);
                    account.setInfo_card_c(0);
                    account.setInfo_card_d(0);
                }
                // Only the UI/effects/notice callback remains. The durable
                // item grant and account counter reconciliation above have
                // completed successfully. An optional packet/broadcast failure
                // must not quarantine a committed economy receipt.
                try {
                    afterCommit.run();
                } catch (RuntimeException effectFailure) {
                    LOG.log(Level.WARNING,
                            "Card synthesis committed but presentation failed: " + pc.getId(),
                            effectFailure);
                }
            });
            return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Card synthesis receipt prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "카드 합성 거래를 시작하지 못했습니다.");
            return false;
        }
    }
}
