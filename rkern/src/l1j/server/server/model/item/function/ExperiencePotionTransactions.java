package l1j.server.server.model.item.function;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.CommonUtil;


import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import static l1j.server.server.model.item.function.EnchantBoxRollTables.*;
import static l1j.server.server.model.item.function.ClassGiftBundleTable.*;

final class ExperiencePotionTransactions {
    private ExperiencePotionTransactions() {}

    static void useLevelExpPotion(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 111556
                || pc.getInventory().GetByServerID(item.getId()) != item)
            return;
        final int level = pc.getLevel();
        if (Config.STANDBY_SERVER || level <= 0 || level >= Config.LIMITLEVEL
                || level >= ExpTable.MAX_LEVEL) {
            SC_SystemMessage.send(pc, "지금 레벨 구간에서는 사용할 수 없습니다.");
            return;
        }
        final int beforeExp = pc.getExp();
        final long calculated;
        if (level < 51) {
            final long level51 = (long) ExpTable.getExpByLevel(51) - 1L;
            calculated = level51 + level51 / 100L;
        } else {
            calculated = (long) ExpTable.getExpByLevel(level + 1) + 99L;
        }
        final int afterExp = (int) Math.min(ExpTable.MAX_EXP, calculated);
        if (afterExp <= beforeExp) return;
        final int difference = afterExp - beforeExp;
        try {
            // Same pre-receipt character snapshot ordering as the existing
            // experience-recovery path. No JDBC runs on the game owner.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
                if (!tx.debit(item, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE characters SET Exp=? WHERE objid=? AND level=? AND Exp=?")) {
                        update.setInt(1, afterExp);
                        update.setInt(2, pc.getId());
                        update.setInt(3, level);
                        update.setInt(4, beforeExp);
                        if (update.executeUpdate() != 1)
                            throw new SQLException("Level EXP potion preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "경험치 물약 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getExp() != beforeExp || pc.getLevel() != level)
                        throw new IllegalStateException("Level EXP potion live preimage changed");
                    pc.addExp(difference);
                });
            }
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "경험치 물약 사용을 확정하지 못했습니다.");
        }
    }

    static void useFixedExpPotion(L1PcInstance pc, L1ItemInstance item, int expReward) {
        if (pc == null || item == null || expReward <= 0
                || (item.getItemId() != 700000 && item.getItemId() != 4100074
                    && item.getItemId() != 4100075)
                || pc.getInventory().GetByServerID(item.getId()) != item) return;
        final int level = pc.getLevel();
        if (Config.STANDBY_SERVER || level <= 0 || level >= Config.LIMITLEVEL
                || level >= ExpTable.MAX_LEVEL) {
            SC_SystemMessage.send(pc, "지금 레벨 구간에서는 사용할 수 없습니다.");
            return;
        }
        // Re-evaluate the requested potion's access rule on the same CIO owner.
        if ((item.getItemId() == 700000 && level > Config.ExpPo2)
                || (item.getItemId() == 4100074 && level > Config.ExpPo)
                || (item.getItemId() == 4100075 && level < Config.ExpPo1)) return;
        final int beforeExp = pc.getExp();
        final int afterExp = (int) Math.min((long) ExpTable.MAX_EXP,
                (long) beforeExp + (long) expReward);
        if (afterExp <= beforeExp) return;
        final int difference = afterExp - beforeExp;
        try {
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
                if (!tx.debit(item, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE characters SET Exp=? WHERE objid=? AND level=? AND Exp=?")) {
                        update.setInt(1, afterExp);
                        update.setInt(2, pc.getId());
                        update.setInt(3, level);
                        update.setInt(4, beforeExp);
                        if (update.executeUpdate() != 1)
                            throw new SQLException("Fixed EXP potion preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "경험치 물약 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getExp() != beforeExp || pc.getLevel() != level)
                        throw new IllegalStateException("Fixed EXP potion live preimage changed");
                    pc.addExp(difference);
                });
            }
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "경험치 물약 사용을 확정하지 못했습니다.");
        }
    }

}
