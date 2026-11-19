package l1j.server.server.model.item.function;


import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.MJCommons;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;

import static l1j.server.server.model.item.function.CreatureItemActions.*;

public final class ElixirStatTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ElixirStatTransactions() {}

    public static void UseElixirAtomic(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.getInventory().getItem(item.getId()) != item)
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
        case 0: beforeStat = pc.getAbility().getStr(); column = "Str"; break;
        case 1: beforeStat = pc.getAbility().getCon(); column = "Con"; break;
        case 2: beforeStat = pc.getAbility().getDex(); column = "Dex"; break;
        case 3: beforeStat = pc.getAbility().getInt(); column = "Intel"; break;
        case 4: beforeStat = pc.getAbility().getWis(); column = "Wis"; break;
        case 5: beforeStat = pc.getAbility().getCha(); column = "Cha"; break;
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
                    case 0: liveStat = pc.getAbility().getStr(); break;
                    case 1: liveStat = pc.getAbility().getCon(); break;
                    case 2: liveStat = pc.getAbility().getDex(); break;
                    case 3: liveStat = pc.getAbility().getInt(); break;
                    case 4: liveStat = pc.getAbility().getWis(); break;
                    default: liveStat = pc.getAbility().getCha(); break;
                    }
                    if (pc.getLevel() != level || pc.getElixirStats() != beforeCount
                            || liveStat != beforeStat)
                        throw new IllegalStateException("Elixir live stat changed before receipt apply");
                    switch (type) {
                    case 0: pc.getAbility().addStr(1); break;
                    case 1: pc.getAbility().addCon(1); break;
                    case 2: pc.getAbility().addDex(1); pc.resetBaseAc(); break;
                    case 3: pc.getAbility().addInt(1); break;
                    case 4: pc.getAbility().addWis(1); pc.resetBaseMr(); break;
                    case 5: pc.getAbility().addCha(1); break;
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
