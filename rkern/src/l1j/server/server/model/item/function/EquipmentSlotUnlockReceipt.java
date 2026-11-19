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

/** Item-specific effect extracted without changing its original execution body. */
final class EquipmentSlotUnlockReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());
    private static final int[] SLOT_UNLOCK_QUESTS = {
        L1Quest.QUEST_SLOT59, L1Quest.QUEST_SLOT76, L1Quest.QUEST_SLOT81,
        L1Quest.QUEST_SLOT_SHOULD, L1Quest.QUEST_SLOT_BADGE
    };

    private EquipmentSlotUnlockReceipt() {}

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
