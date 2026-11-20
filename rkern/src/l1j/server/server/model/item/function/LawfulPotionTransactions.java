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

public final class LawfulPotionTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private LawfulPotionTransactions() {}

    public static void UseLawfulPotionAtomic(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || pc.getInventory().getItem(input.getId()) != input)
            return;
        final int itemId = input.getItemId();
        if (itemId != 490028 && itemId != 490029) return;
        // addLawful() refuses to act during standby. Do not consume the potion.
        if (Config.STANDBY_SERVER) return;
        final int before = pc.getLawful();
        if (itemId == 490028 && before >= 32767) {
            pc.sendPackets("현재 사용할수있는 조건이 아닙니다.");
            return;
        }
        if (itemId == 490029 && before <= -32768) {
            pc.sendPackets("현재 사용할수있는 조건이 아닙니다.");
            return;
        }
        if (before < -32768 || before > 32767) return;
        // Both legacy +70000 / -70000 operations always clamp to the limit.
        final int next = itemId == 490028 ? 32767 : -32768;
        final int characterId = pc.getId();
        try {
            // Flush the earlier character snapshot before the durable item+stat
            // receipt so a delayed save cannot restore the old lawful value.
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, input.getId())) {
                if (!tx.debit(input, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET Lawful=? WHERE objid=? AND Lawful=?")) {
                        ps.setInt(1, next);
                        ps.setInt(2, characterId);
                        ps.setInt(3, before);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("Lawful preimage changed: " + characterId);
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "성향 물약을 저장하지 못했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getLawful() != before)
                        throw new IllegalStateException("Lawful changed before receipt apply: " + characterId);
                    pc.setLawful(next);
                    pc.onChangeLawful();
                    pc.save();
                    try {
                        if (itemId == 490028) SC_ServerMessage.send(pc, 674);
                        else pc.sendPackets("영혼이 지저분해 지는것을 느낍니다.");
                    } catch (RuntimeException noticeFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed lawful potion notice failed", noticeFailure);
                    }
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Lawful potion prepare failed: " + characterId, failure);
            SC_SystemMessage.send(pc, "성향 물약 사용을 처리하지 못했습니다.");
        }
    }
}
