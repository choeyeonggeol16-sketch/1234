package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1Quest;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 신규 유저 지원 및 레벨 점핑 아이템 처리 핸들러
 */
public class StarterSupportHandler {

    private static final StarterSupportHandler instance = new StarterSupportHandler();
    public static StarterSupportHandler getInstance() { return instance; }

    private StarterSupportHandler() {}

    /** Starter settlement: source debit, optional box rewards, EXP and quest
     * progress share one CacheD receipt. No blocking JDBC on the owner thread.
     */
    public void handle(L1PcInstance pc, int itemId, L1ItemInstance item) {
        if (pc == null || item == null) return;
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_ChatPacket.sendDirect(pc, pc, "이 아이템은 마을에서만 사용 할 수 있습니다.");
            return;
        }
        if (pc.getInventory().getItem(item.getId()) != item || item.getItemId() != itemId) return;

        final int configuredLevel = Config.STARTER_REWARD_LEVEL;
        if (configuredLevel < 1 || configuredLevel > ExpTable.MAX_LEVEL) {
            SC_SystemMessage.send(pc, "지원 레벨 설정을 확인해 주세요.");
            return;
        }
        final int minExp = pc.getLevel() < configuredLevel
                ? ExpTable.getExpByLevel(configuredLevel) : pc.getExp();
        final int durableExp = Math.max(pc.getExp(), minExp);
        final int characterId = pc.getId();
        final Runnable afterCommitted = () -> finishSupport(pc, durableExp);
        L1TreasureBox supplyBox = L1TreasureBox.get(itemId);
        if (supplyBox != null) {
            supplyBox.openAtomic(pc, item,
                    tx -> stageSupportSettlement(tx, characterId, durableExp),
                    afterCommitted);
        } else {
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(
                    pc, CacheD.accountOrderKey("item-use:", characterId))) {
                if (!tx.debit(item, 1)) return;
                stageSupportSettlement(tx, characterId, durableExp);
                tx.onFailure(() -> SC_SystemMessage.send(pc, "지원 아이템 사용을 저장하지 못했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    try { afterCommitted.run(); }
                    catch (RuntimeException failure) {
                        java.util.logging.Logger.getLogger(StarterSupportHandler.class.getName())
                                .log(java.util.logging.Level.SEVERE, "Committed support replay failed", failure);
                        throw failure; // quarantine; EXP and quest are already durable
                    }
                });
            } catch (RuntimeException failure) {
                SC_SystemMessage.send(pc, "지원 아이템 사용을 확정하지 못했습니다.");
            }
        }
    }

    private static void stageSupportSettlement(L1InventoryTransaction tx, int characterId, int exp) {
        tx.requireTables("characters", "character_quests");
        tx.sql(con -> {
            // Lock and verify the character row on the DB worker. A normal
            // UPDATE may return zero when the desired values already match.
            try (PreparedStatement lock = con.prepareStatement(
                    "SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                lock.setInt(1, characterId);
                try (java.sql.ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Missing starter character: " + characterId);
                }
            }
            try (PreparedStatement update = con.prepareStatement(
                    // Persist EXP, but do NOT advance the stored level yet.
                    // loadCharacter().refresh() must still see the old level and
                    // execute the game's HP/MP/stat levelUp recovery after a crash.
                    "UPDATE characters SET Exp=GREATEST(Exp, ?) WHERE objid=?")) {
                update.setInt(1, exp);
                update.setInt(2, characterId);
                update.executeUpdate();
            }
        });
        tx.sql(con -> {
            try (PreparedStatement update = con.prepareStatement(
                    "INSERT INTO character_quests (char_id,quest_id,quest_step) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE quest_step=VALUES(quest_step)")) {
                update.setInt(1, characterId);
                update.setInt(2, L1Quest.QUEST_FIRSTQUEST);
                update.setInt(3, L1Quest.QUEST_END);
                // MySQL may report zero affected rows for an already-ended quest.
                // The idempotent UPSERT is still successful in that case.
                update.executeUpdate();
            }
        });
    }

    private void finishSupport(L1PcInstance pc, int exp) {
        // EXP and quest are durable, but the level-up growth and level/highLevel
        // writes still belong to the existing character levelUp path. If the
        // process dies here, loadCharacter().refresh() detects the old DB level
        // and applies the missing growth from the committed EXP on next login.
        pc.getQuest().applyCommittedStepIfLoaded(L1Quest.QUEST_FIRSTQUEST, L1Quest.QUEST_END);
        if (pc.getExp() < exp) {
            pc.setExp(exp);
            pc.onChangeExp();
        }
        fullRecovery(pc);
        SC_OwnCharStatus.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.퀘스트, false);
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.퀘스트2, false);
    }

    /** HP/MP 전체 회복 및 관련 패킷 전송 */
    private void fullRecovery(L1PcInstance pc) {
        pc.setCurrentHp(pc.getBaseMaxHp());
        pc.setCurrentMp(pc.getBaseMaxMp());
        
        SC_SPMR.send(pc);
        SC_OwnCharStatus.send(pc); // 전체 갱신
    }

}
