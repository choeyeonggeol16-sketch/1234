package l1j.server.server.model.item.function;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;

public final class ElixirStatTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ElixirStatTransactions() {}

    public static void UseElixirAtomic(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.getReturnStat() != 0 || pc.getInventory().GetByServerID(item.getId()) != item)
            return;
        final int type = item.getItemId() - 40033;
        if (type < 0 || type > 5) return;
        final int level = pc.getLevel();
        final int allowed = level >= 80 ? 6 : level >= 75 ? 5 : level >= 70 ? 4
                : level >= 65 ? 3 : level >= 60 ? 2 : level >= 55 ? 1 : 0;
        final int beforeCount = pc.getElixirStats();
        if (allowed == 0 || beforeCount >= allowed || beforeCount >= 6) {
            SC_ServerMessage.send(pc, 4472);
            return;
        }
        final Stat stat = STATS[type];
        final int beforeStat = stat.value(pc);
        if (beforeStat >= 45) {
            SC_ServerMessage.send(pc, 481);
            return;
        }
        final int nextCount = beforeCount + 1;
        try {
            // The full character snapshot is ordered before the item receipt;
            // it must not overwrite the new stat and ElixirStatus afterward.
            if (!CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc))) {
                SC_SystemMessage.send(pc, "다른 저장 처리가 끝난 뒤 다시 시도해 주세요.");
                return;
            }
            try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                    l1j.server.server.model.inventory.L1InventoryTransaction.begin(pc, item.getId())) {
                if (pc.getReturnStat() != 0 || pc.getLevel() != level
                        || pc.getElixirStats() != beforeCount || stat.value(pc) != beforeStat
                        || !tx.debit(item, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> stat.persist(con, pc.getId(), level, beforeStat, beforeCount));
                tx.onFailure(() -> SC_SystemMessage.send(pc, "엘릭서 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(
                        () -> applyCommittedStat(pc, stat, beforeStat, beforeCount), () -> {
                    try {
                        pc.NotifyPermanentStat(stat.notice);
                        if (stat != Stat.STR && stat != Stat.DEX)
                            SC_OwnCharStatus2.send(pc);
                    }
                    catch (RuntimeException noticeFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed elixir stat notice failed", noticeFailure);
                    }
                    try { SC_ActionUI2.send(pc, SC_ActionUI2.Elixir, nextCount); }
                    catch (RuntimeException noticeFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed elixir UI notice failed", noticeFailure);
                    }
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Elixir transaction prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "엘릭서 사용을 처리하지 못했습니다.");
        }
    }
    enum Stat {
        STR("Str", SC_CHAR_STAT.Stat_Str), CON("Con", SC_CHAR_STAT.Stat_Con),
        DEX("Dex", SC_CHAR_STAT.Stat_Dex), INT("Intel", SC_CHAR_STAT.Stat_Int),
        WIS("Wis", SC_CHAR_STAT.Stat_Wis), CHA("Cha", SC_CHAR_STAT.Stat_Cha);

        private final String update;
        final int notice;

        Stat(String column, int notice) {
            this.notice = notice;
            this.update = "UPDATE characters SET " + column + "=?, ElixirStatus=? "
                    + "WHERE objid=? AND " + column + "=? AND ElixirStatus=? AND level=?";
        }

        int value(L1PcInstance pc) {
            return switch (this) {
                case STR -> pc.GetStatStr();
                case CON -> pc.GetStatCon();
                case DEX -> pc.GetStatDex();
                case INT -> pc.GetStatInt();
                case WIS -> pc.GetStatWis();
                case CHA -> pc.GetStatCha();
            };
        }

        void set(L1PcInstance pc, int value) {
            switch (this) {
                case STR -> pc.SetStatStr(value);
                case CON -> pc.SetStatCon(value);
                case DEX -> pc.SetStatDex(value);
                case INT -> pc.SetStatInt(value);
                case WIS -> pc.SetStatWis(value);
                case CHA -> pc.SetStatCha(value);
            }
        }

        void persist(Connection con, int ownerId, int level, int beforeStat, int beforeCount)
                throws SQLException {
            try (PreparedStatement ps = con.prepareStatement(update)) {
                ps.setInt(1, beforeStat + 1);
                ps.setInt(2, beforeCount + 1);
                ps.setInt(3, ownerId);
                ps.setInt(4, beforeStat);
                ps.setInt(5, beforeCount);
                ps.setInt(6, level);
                if (ps.executeUpdate() != 1)
                    throw new SQLException("Elixir stat preimage changed: " + ownerId);
            }
        }
    }

    private static final Stat[] STATS = Stat.values();

    /** Publish all SQL-backed fields before acknowledging the inventory receipt.
     * Character snapshots take the same lock and carry the published revision to CacheD. */
    static void applyCommittedStat(L1PcInstance pc, Stat stat, int beforeStat, int beforeCount) {
        pc.lockUserState();
        try {
            if (stat.value(pc) != beforeStat || pc.getElixirStats() != beforeCount)
                throw new IllegalStateException("Elixir live stat changed before receipt apply");
            stat.set(pc, beforeStat + 1);
            pc.setElixirStats(beforeCount + 1);
            pc.publishPermanentStatRevision();
        } finally {
            pc.unlockUserState();
        }
    }

}
