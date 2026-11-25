package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

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

public final class ElixirStatTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ElixirStatTransactions() {}

    public static void UseElixirAtomic(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.getInventory().GetByServerID(item.getId()) != item)
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
        final int beforeStat;
        final String column;
        switch (type) {
        case 0: beforeStat = pc.GetStatStr(); column = "Str"; break;
        case 1: beforeStat = pc.GetStatCon(); column = "Con"; break;
        case 2: beforeStat = pc.GetStatDex(); column = "Dex"; break;
        case 3: beforeStat = pc.GetStatInt(); column = "Intel"; break;
        case 4: beforeStat = pc.GetStatWis(); column = "Wis"; break;
        case 5: beforeStat = pc.GetStatCha(); column = "Cha"; break;
        default: return;
        }
        if (beforeStat >= 45) {
            SC_ServerMessage.send(pc, 481);
            return;
        }
        final int nextStat = beforeStat + 1;
        final int nextCount = beforeCount + 1;
        try {
            // The full character snapshot is ordered before the item receipt;
            // it must not overwrite the new stat and ElixirStatus afterward.
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, item.getId())) {
                if (!tx.debit(item, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET " + column + "=?, ElixirStatus=? "
                            + "WHERE objid=? AND " + column + "=? AND ElixirStatus=? AND level=?")) {
                        ps.setInt(1, nextStat);
                        ps.setInt(2, nextCount);
                        ps.setInt(3, pc.getId());
                        ps.setInt(4, beforeStat);
                        ps.setInt(5, beforeCount);
                        ps.setInt(6, level);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("Elixir stat preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "엘릭서 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    final int liveStat;
                    switch (type) {
                    case 0: liveStat = pc.GetStatStr(); break;
                    case 1: liveStat = pc.GetStatCon(); break;
                    case 2: liveStat = pc.GetStatDex(); break;
                    case 3: liveStat = pc.GetStatInt(); break;
                    case 4: liveStat = pc.GetStatWis(); break;
                    default: liveStat = pc.GetStatCha(); break;
                    }
                    if (pc.getLevel() != level || pc.getElixirStats() != beforeCount
                            || liveStat != beforeStat)
                        throw new IllegalStateException("Elixir live stat changed before receipt apply");
                    switch (type) {
                    case 0: pc.AddStatStr(1); break;
                    case 1: pc.AddStatCon(1); break;
                    case 2: pc.AddStatDex(1); pc.resetBaseAc(); break;
                    case 3: pc.AddStatInt(1); break;
                    case 4: pc.AddStatWis(1); pc.resetBaseMr(); break;
                    case 5: pc.AddStatCha(1); break;
                    }
                    pc.setElixirStats(nextCount);
                    // Persist the committed stat before any client notification.
                    pc.save();
                    try { SC_OwnCharStatus2.send(pc); }
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
}
