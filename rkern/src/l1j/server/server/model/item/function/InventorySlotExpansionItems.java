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

import l1j.server.server.model.network.core.session.CUserSession;
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

public final class InventorySlotExpansionItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private InventorySlotExpansionItems() {}

    public static void UseSpecialSizeExpansion(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || input.getItemId() != 89136
                || pc.getInventory().getItem(input.getId()) != input) return;
        final int before = pc.get_SpecialSize();
        if (before == 40) {
            SC_ServerMessage.send(pc, 1622);
            return;
        }
        if (before != 0 && before != 20) return;
        final int next = before + 20;
        final int level = pc.getLevel();
        try {
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, input.getId())) {
                if (!tx.debit(input, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET SpecialSize=? "
                            + "WHERE objid=? AND SpecialSize=? AND level=?")) {
                        ps.setInt(1, next);
                        ps.setInt(2, pc.getId());
                        ps.setInt(3, before);
                        ps.setInt(4, level);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("SpecialSize preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "슬롯 확장 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.get_SpecialSize() != before || pc.getLevel() != level)
                        throw new IllegalStateException("SpecialSize changed before receipt apply");
                    pc.set_SpecialSize(next);
                    // The receipt has committed the capacity. Do not let a client
                    // notice prevent the character snapshot from being queued.
                    pc.save();
                    try { SC_ServerMessage.send(pc, 1624, Integer.toString(next)); }
                    catch (RuntimeException noticeFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed special-slot expansion notice failed", noticeFailure);
                    }
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "SpecialSize expansion prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "슬롯 확장을 처리하지 못했습니다.");
        }
    }
}
