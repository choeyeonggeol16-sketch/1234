package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_Serchdrop2;

import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.MJCommons;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;

import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;


import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;

import static l1j.server.server.model.item.function.CreatureItemActions.*;

public final class PcRoomPassTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private PcRoomPassTransactions() {}

	public static void 피씨방코인(L1PcInstance pc, int itemId, L1ItemInstance useItem, int day) {
        if (pc == null || useItem == null || useItem.getItemId() != itemId || day <= 0
                || pc.PC방_버프 || pc.getInventory().getItem(useItem.getId()) != useItem) return;
        final long now = System.currentTimeMillis();
        final Timestamp expiration = new Timestamp(now + 86400000L * day + 10000L);
        final String accountName = pc.getAccount().getName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("pcroom:", pc.getId()))) {
            if (!tx.debit(useItem, 1)) return;
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET BUFF_PCROOM_Time=? WHERE login=? "
                        + "AND (BUFF_PCROOM_Time IS NULL OR BUFF_PCROOM_Time<=?)")) {
                    ps.setTimestamp(1, expiration);
                    ps.setString(2, accountName);
                    ps.setTimestamp(3, new Timestamp(now));
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("PC-room access active or account not found");
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "PC방 이용권 저장에 실패했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                pc.getAccount().setBuff_PC방(expiration);
                pc.setPC방_버프(true);
                SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 293, true);
                SC_ActionUI.send(pc, SC_ActionUI.PCBANG_SET, true);
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,
                        "PC방 이용 시간: " + day + "일 동안 PC방 혜택이 적용 됩니다.");
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "PC-room ticket prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "PC방 이용권 처리를 시작하지 못했습니다.");
        }
    }
}
