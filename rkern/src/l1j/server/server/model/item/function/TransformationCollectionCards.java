package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAdd;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Item-specific action effects dispatched by item ID. */
final class TransformationCollectionCards {
    private static final java.util.logging.Logger ITEM_LOG =
            java.util.logging.Logger.getLogger(TransformationCollectionCards.class.getName());
    private TransformationCollectionCards() {}

    static ItemActionResult handleUnlockCollection(ItemActionContext use) {
        final L1PcInstance pc = use.player();
        final L1ItemInstance item = use.item();
        if (pc == null || item == null || item.getItemId() != 875640295)
            return ItemActionResult.COMPLETE;
        final l1j.server.server.Account account = pc.getAccount();
        if (account == null) return ItemActionResult.COMPLETE;
        final int before = account.getInfo_kir_poly();
        final int scoreBefore = account.getInfo_col_y();
        if (before < 0 || before == Integer.MAX_VALUE) {
            SC_SystemMessage.send(pc, "카드 컬렉션 수량을 확인할 수 없습니다.");
            return ItemActionResult.COMPLETE;
        }
        final String accountName = account.getName();
        // Keep the original unique-score assignment (=1), not an increment.
        // Both account fields and the card debit must commit in one receipt.
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                l1j.server.server.model.network.core.cs.packet.User.CheckAndSetPendingUseETCItem(
                        pc, l1j.server.server.storage.CacheD.accountOrderKey(accountName))) {
            // The account snapshot above precedes the inventory owner gate.
            // Reject a stale session/card count rather than submitting a
            // committed receipt which would later fail its memory preimage.
            if (pc.getAccount() != account || account.getInfo_kir_poly() != before
                    || account.getInfo_col_y() != scoreBefore) {
                SC_SystemMessage.send(pc, "유일 카드 등록 상태가 변경되었습니다. 다시 확인해 주세요.");
                return ItemActionResult.COMPLETE;
            }
            if (!tx.debit(item, 1)) {
                SC_SystemMessage.send(pc, "등록할 유일 카드를 보유하고 있지 않습니다.");
                return ItemActionResult.COMPLETE;
            }
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (java.sql.PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET info_kir_poly=info_kir_poly+1,info_col_y=1 "
                        + "WHERE login=? AND info_kir_poly=? AND info_col_y=?")) {
                    ps.setString(1, accountName);
                    ps.setInt(2, before);
                    ps.setInt(3, scoreBefore);
                    if (ps.executeUpdate() != 1)
                        throw new java.sql.SQLException("Unique card preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "유일 카드 등록 거래를 저장하지 못했습니다."));
            l1j.server.server.model.network.core.cs.packet.CDB.RequestUseItem(pc, tx, () -> {
                if (pc.getAccount() != account || account.getInfo_kir_poly() != before
                        || account.getInfo_col_y() != scoreBefore)
                    throw new IllegalStateException("Unique card memory/session preimage changed");
                account.setInfo_kir_poly(before + 1);
                account.setInfo_col_y(1);
                // Preserve stat bonus application failures as hard failures;
                // a failed chat notification cannot undo a committed unique card.
                L1CardCollection.solejoin(pc);
                try {
                    SC_ChatPacket.sendDirect(pc, pc,
                            "\\aD[유일]지배자 기르타스 (이)가 변신 컬렉션에 등록되었습니다.");
                } catch (RuntimeException packetFailure) {
                    ITEM_LOG.log(java.util.logging.Level.WARNING,
                            "Unique collection committed but notification failed: " + pc.getId(),
                            packetFailure);
                }
            });
        } catch (RuntimeException failure) {
            ITEM_LOG.log(java.util.logging.Level.WARNING,
                    "Unique collection card receipt prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "유일 카드 등록을 처리하지 못했습니다.");
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollection(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.myth(pc);
            return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup2(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.legend1(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup3(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.legend2(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup4(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.hero1(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup5(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.hero2(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup6(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.SkillTree(pc);
        return ItemActionResult.COMPLETE;
    }

}
