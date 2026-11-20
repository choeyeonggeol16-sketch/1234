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

import l1j.server.server.model.network.core.connection.CUserSocket;
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

public final class ItemReceiptOperations {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ItemReceiptOperations() {}

    public static void UseDurableConsume(L1PcInstance pc, L1ItemInstance input, Runnable afterCommitted) {
        ItemTransactionActions.consume(pc, input, afterCommitted);
    }

    public static void UseDurableConsumeTwoInput(L1PcInstance pc, L1ItemInstance first,
            L1ItemInstance second, Runnable afterCommitted) {
        ItemTransactionActions.consumeTwo(pc, first, second, afterCommitted);
    }

    /** Atomic +1 enchant with its scroll debit. The target is a detached
     * prepared image until COMMIT; the owner applies equipped AC/MR only once
     * after receipt replay. No additional thread, queue or database connection.
     */
    public static void UseAtomicEnchantSuccess(L1PcInstance pc, L1ItemInstance scroll,
            L1ItemInstance target, CUserSocket client, Runnable afterCommitted) {
        if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, scroll, target)) return;
        try {
            // Flush older dirty values in FIFO order before preimage SQL checks.
            if (scroll.getRecordingColumns() != 0)
                pc.getInventory().saveItem(scroll, L1PcInventory.COL_SAVE_ALL);
            if (target.getRecordingColumns() != 0)
                pc.getInventory().saveItem(target, L1PcInventory.COL_SAVE_ALL);
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Enchant preimage save refused: " + pc.getId(), failure);
            ItemTransactionActions.failureNotice(pc, "강화 저장 준비에 실패했습니다.");
            return;
        }
        final int previousLevel = target.getEnchantLevel();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            tx.changeEnchantLevel(target, previousLevel, previousLevel + 1);
            tx.onFailure(() -> ItemTransactionActions.failureNotice(pc, "강화 결과가 저장되지 않았습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                // The scroll is already durably consumed; record its delay
                // before cosmetic effects, which may throw independently.
                try {
                    EnchantmentOutcomeEffects.SuccessEnchantCommitted(pc, target, client, 1);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                }
                if (afterCommitted != null) afterCommitted.run();
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Enchant receipt prepare failed: " + pc.getId(), failure);
            ItemTransactionActions.failureNotice(pc, "강화를 처리하지 못했습니다.");
        }
    }

    /** Non-destructive protection scrolls: success changes the target +1 in
     * the same receipt as scroll consumption; failure consumes only the scroll.
     * Existing probability checks and limit rules remain in the callers.
     */
    public static void UseProtectionEnchant(L1PcInstance pc, L1ItemInstance scroll,
            L1ItemInstance target, boolean success, boolean announceSuccess) {
        if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, scroll, target)) return;
        if (success) {
            UseAtomicEnchantSuccess(pc, scroll, target, pc.getNetConnection(),
                    announceSuccess ? () -> protectionNotice(pc,
                            "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.") : null);
        } else {
            UseDurableConsume(pc, scroll, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, scroll);
                protectionNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
            });
        }
    }

    private static void protectionNotice(L1PcInstance pc, String message) {
        try {
            SC_SystemMessage.send(pc, message);
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Protection enchant result notification failed", failure);
        }
    }

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId) {
		UseDurableExchange(pc, input, rewardId, null);
	}

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId,
			Runnable afterCommitted) {
		UseDurableExchange(pc, input, rewardId, 1, afterCommitted);
	}

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId,
			int rewardCount, Runnable afterCommitted) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(input, 1)) return;
			if (tx.grant(rewardId, rewardCount) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> ItemTransactionActions.failureNotice(pc, "아이템 교환이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item exchange prepare failed: " + pc.getId(), failure);
			ItemTransactionActions.failureNotice(pc, "아이템 교환을 처리하지 못했습니다.");
		}
	}

	public static void UseDurableTwoInput(L1PcInstance pc, L1ItemInstance catalyst,
			L1ItemInstance target, int rewardId, Runnable afterCommitted) {
		UseDurableTwoInput(pc, catalyst, target, rewardId, 1, afterCommitted);
	}

	static void UseDurableTwoInput(L1PcInstance pc, L1ItemInstance catalyst,
			L1ItemInstance target, int rewardId, int rewardCount, Runnable afterCommitted) {
		if (catalyst == null || target == null || catalyst == target || rewardCount <= 0)
			return;
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(catalyst, 1) || !tx.debit(target, 1)) {
				SC_SystemMessage.send(pc, "사용할 재료가 부족합니다.");
				return;
			}
			if (tx.grant(rewardId, rewardCount) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> ItemTransactionActions.failureNotice(pc, "아이템 교환이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Two-input item use failed: " + pc.getId() + " / " + rewardId, failure);
			ItemTransactionActions.failureNotice(pc, "아이템 사용을 처리하지 못했습니다.");
		}
	}
}
