package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Logger;

import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import l1j.server.server.Account;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

final class ItemUseDurableGoods {
    private static final Logger _log = Logger.getLogger(CItemUse.class.getName());
    private ItemUseDurableGoods() {}

    /** Keep the item debit and the account point increment in one receipt.
     * Account snapshot writers are gated until the owner applies the receipt.
     */
    static void useTamBoxAtomic(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null || pc.getNetConnection() == null) return;
        final Account account = pc.getNetConnection().getAccount();
        if (account == null || !account.tryBeginTamAward()) return;
        boolean submitted = false;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(account.getName()))) {
            if (!tx.debit(box, 1)) return;
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET Tam_Point=Tam_Point+100000 "
                        + "WHERE login=? AND Tam_Point BETWEEN 0 AND 2147383647")) {
                    ps.setString(1, account.getName());
                    if (ps.executeUpdate() != 1) throw new SQLException("Tam award account missing or overflow");
                }
            });
            tx.onFailure(() -> {
                account.endTamAward();
                account.updateTam();
                SC_SystemMessage.send(pc, "탐 상자 사용을 저장하지 못했습니다.");
            });
            CDB.RequestUseItem(pc, tx, () -> {
                account.addTamPoint(100000);
                account.endTamAward();
                account.updateTam();
                SC_NewCreateItem.sendTamPoint(pc, pc.getNetConnection());
                SC_SystemMessage.send(pc, "탐 10만개를 얻었습니다.");
            });
            submitted = true;
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Tam box prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "탐 상자를 처리하지 못했습니다.");
        } finally {
            if (!submitted) account.endTamAward();
        }
    }

    /** Scroll consumption and all eligible inventory conversions share one
     * receipt. No items (or the scroll) are changed until the receipt commits.
     */
    static void useUncurseScrollAtomic(L1PcInstance pc, L1ItemInstance scroll, int scrollId) {
        if (pc == null || scroll == null) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (item == scroll || item.getItem().getBless() != 2) continue;
                if (!item.isEquipped() && (scrollId == 40119 || scrollId == 40097)) continue;
                L1Item normal = ItemTable.getInstance().getTemplate(item.getItemId() - 200000);
                if (normal == null) continue;
                if (normal.getType2() != item.getItem().getType2()
                        || normal.getType() != item.getItem().getType()
                        || normal.getType1() != item.getItem().getType1()) continue;
                if (item.getBless() < 1) continue;
                if (normal.isStackable() && pc.getInventory().checkItem(normal.getItemId())
                        && !item.isEquipped()) {
                    int amount = item.getCount();
                    if (!tx.debit(item, amount) || tx.grant(normal.getItemId(), amount) == null)
                        throw new IllegalStateException("Cursed stack conversion failed");
                } else {
                    tx.uncursedTemplate(item, normal);
                }
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "저주 해제 아이템 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> SC_ServerMessage.send(pc, 155));
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Uncurse scroll prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "저주 해제를 처리하지 못했습니다.");
        }
    }


    static void useDurableLanternOil(L1PcInstance pc, L1ItemInstance oil) {
        if (pc == null || oil == null || pc.getInventory().getItem(oil.getId()) != oil) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(oil, 1)) return;
            L1ItemInstance found = null;
            for (L1ItemInstance candidate : pc.getInventory().getItems()) {
                if (candidate != null && (candidate.getItemId() == 40002 || candidate.getItemId() == 7005)) {
                    found = candidate;
                    break;
                }
            }
            if (found != null) tx.changeRemainingTime(found, oil.getItem().getLightFuel());
            final L1ItemInstance lamp = found;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "랜턴오일 사용 내역을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (lamp != null) {
                    SC_ItemName.send(pc, lamp);
                    SC_ServerMessage.send(pc, 230);
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Lantern oil prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "랜턴오일을 사용하지 못했습니다.");
        }
    }

    // The class gift and its package must share the same CacheD receipt.
    static void useHeroPackageAtomic(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || input.getItemId() != 753
                || pc.getInventory().getItem(input.getId()) != input) return;
        if (pc.getInventory().getSize() > 120) {
            SC_SystemMessage.send(pc, "소지하고 있는 아이템이 너무 많습니다.");
            return;
        }
        if (pc.getInventory().getWeight100() > 82) {
            SC_SystemMessage.send(pc, "소지품이 너무 무거워서 사용 할 수 없습니다.");
            return;
        }
        final int gift = pc.isWarrior() ? 832
                : pc.isKnight() ? 834
                : pc.isDragonknight() ? 838
                : pc.isCrown() ? 833
                : pc.isWizard() ? 836
                : pc.isBlackwizard() ? 839
                : pc.isElf() ? 835
                : pc.isDarkelf() ? 837 : 0;
        if (gift == 0) return;
        final L1Item definition = ItemTable.getInstance().getTemplate(gift);
        if (definition == null) {
            SC_SystemMessage.send(pc, "영웅 패키지 보상을 찾을 수 없습니다.");
            return;
        }
        // createItem() publishes in L1World; prepare a detached gift instead.
        final L1ItemInstance reward = new L1ItemInstance(definition, 1);
        reward.setId(l1j.server.server.IdFactory.getInstance().nextId());
        reward.setBless(1);
        reward.setIdentified(true);
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            final L1ItemInstance prepared = tx.grant(reward);
            if (prepared == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "영웅 패키지 지급을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, () -> SC_ServerMessage.send(pc, 403, prepared.getLogName()));
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Hero package transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "영웅 패키지 사용을 처리하지 못했습니다.");
        }
    }

    // accounts.charslot and character_items debit must be one durable COMMIT.
    static void useCharacterSlotExpansionAtomic(CIOClient client,
            L1PcInstance pc, L1ItemInstance input) {
        if (client == null || client.getAccount() == null || pc == null || input == null
                || input.getItemId() != 210083
                || pc.getInventory().getItem(input.getId()) != input) return;
        final int before = client.getAccount().getCharSlot();
        if (before >= 8) {
            SC_SystemMessage.send(pc, "캐릭터 슬롯이 이미 가득찼습니다.");
            return;
        }
        if (before < 0) return;
        final int after = before + 1;
        final String accountName = client.getAccount().getName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(accountName))) {
            if (!tx.debit(input, 1)) return;
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET charslot=? WHERE login=? AND charslot=? AND charslot<8")) {
                    ps.setInt(1, after);
                    ps.setString(2, accountName);
                    ps.setInt(3, before);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Character slot preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "캐릭터 슬롯 확장을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                client.getAccount().setCharSlot(after);
                pc.getAccount().is_changed_slot(true);
                SC_SystemMessage.send(pc, "캐릭터 슬롯 확장 완료(완전히 접속종료후 적용됨)");
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Character slot transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "캐릭터 슬롯 확장을 처리하지 못했습니다.");
        }
    }

    private static final int[] SLOT_UNLOCK_QUESTS = {
        L1Quest.QUEST_SLOT59, L1Quest.QUEST_SLOT76, L1Quest.QUEST_SLOT81,
        L1Quest.QUEST_SLOT_SHOULD, L1Quest.QUEST_SLOT_BADGE
    };

    /** The item debit and every quest slot flag share one CacheD receipt. */
    static void useSlotUnlockAtomic(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.getInventory().getItem(item.getId()) != item)
            return;
        final int id = item.getItemId();
        final int selected = id == 4100203 ? 31
                : id >= 4100140 && id <= 4100144 ? 1 << (id - 4100140) : 0;
        if (selected == 0) return;

        // The full unlock ticket can be used on a partially opened character.
        // Only the still-closed slots are written; an already fully opened
        // character must not lose a ticket for no benefit.
        int missing = 0;
        for (int i = 0; i < SLOT_UNLOCK_QUESTS.length; i++) {
            final int bit = 1 << i;
            if ((selected & bit) != 0 && !pc.getQuest().isEnd(SLOT_UNLOCK_QUESTS[i]))
                missing |= bit;
        }
        if (missing == 0) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final int missingMask = missing;
        final int charId = pc.getId();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:" + charId))) {
            if (!tx.debit(item, 1)) return;
            tx.requireTables("character_quests");
            tx.sql(con -> {
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE");
                     PreparedStatement write = con.prepareStatement(
                        "INSERT INTO character_quests (char_id,quest_id,quest_step) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE quest_step=VALUES(quest_step)")) {
                    for (int i = 0; i < SLOT_UNLOCK_QUESTS.length; i++) {
                        if ((missingMask & (1 << i)) == 0) continue;
                        final int questId = SLOT_UNLOCK_QUESTS[i];
                        lock.setInt(1, charId);
                        lock.setInt(2, questId);
                        try (ResultSet rows = lock.executeQuery()) {
                            if (rows.next()) {
                                final int step = rows.getInt(1);
                                if (rows.wasNull() || step == L1Quest.QUEST_END)
                                    throw new SQLException("Slot quest changed before debit: " + charId + "/" + questId);
                            }
                        }
                        write.setInt(1, charId);
                        write.setInt(2, questId);
                        write.setInt(3, L1Quest.QUEST_END);
                        write.executeUpdate();
                    }
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "슬롯 개방을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                for (int i = 0; i < SLOT_UNLOCK_QUESTS.length; i++) {
                    if ((missingMask & (1 << i)) != 0)
                        pc.getQuest().applyCommittedStepIfLoaded(SLOT_UNLOCK_QUESTS[i], L1Quest.QUEST_END);
                }
                // Client display follows the same five slot packet formats.
                if ((selected & 1) != 0) {
                    SC_ReturnedStat.sendSlot(pc, 67, 1, 16);
                    SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                }
                if ((selected & 2) != 0) {
                    SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                    SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 1);
                }
                if ((selected & 4) != 0) {
                    SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                    SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, 2);
                }
                if ((selected & 8) != 0) {
                    SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                    SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING,
                            SC_ReturnedStat.OPEN_SLOT_SHOULD);
                }
                if ((selected & 16) != 0) {
                    SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING,
                            SC_ReturnedStat.OPEN_SLOT_BADGE);
                    SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                }
                final int sound = id == 4100203 || id == 4100143 ? 12358
                        : id == 4100140 ? 12004 : id == 4100144 ? 12360 : 12003;
                SC_SkillSound.send(pc, pc.getId(), sound);
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Slot unlock transaction prepare failed: " + charId, failure);
            SC_SystemMessage.send(pc, "슬롯 개방을 처리하지 못했습니다.");
        }
    }

}
