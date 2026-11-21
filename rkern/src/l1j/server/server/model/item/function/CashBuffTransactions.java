package l1j.server.server.model.item.function;


import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.CreatureItemActions.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.TransformationItems.*;

import java.sql.PreparedStatement;
import java.sql.Timestamp;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class CashBuffTransactions {
    private CashBuffTransactions() {}

	public static void 강화버프(L1PcInstance pc, int itemId, L1ItemInstance useItem) {
		if (itemId >= 600198 && itemId <= 600217) {
			int buffId = itemId - 596123;
            // Account DB expiry may exceed the skill timer's int-millisecond range.
            final long durationMillis = 86400000L * Math.max(1, Config.TIME_N_BUFF);
            final int activeSeconds = (int) (Math.min(durationMillis,
                    (long) Integer.MAX_VALUE) / 1000L);

			if (pc.hasSkillEffect(buffId)) {
				SC_SystemMessage.send(pc, "이미 적용중인 강화 버프입니다.");
				return;
			}

            int activeBuffs = 0;
            for (int skillId = 4075; skillId <= 4094; skillId++) {
                if (pc.hasSkillEffect(skillId)) activeBuffs++;
            }

            if (activeBuffs >= Config.LIMIT_N_BUFF) {
				SC_SystemMessage.send(pc, "강화 버프는 최대 " + Config.LIMIT_N_BUFF + "종류까지 받을 수 있습니다.");
				return;
			}
			
			if (itemId >= 600209 && itemId <= 600212) {
				for (int skillId = 4086; skillId <= 4089; skillId++) {
					if (pc.hasSkillEffect(skillId)) {
						SC_SystemMessage.send(pc, "완력,민첩,지식,지혜는 한가지만 사용가능합니다.");
						return;
					}
				}
			}
            final int characterId=pc.getId(),accountId=pc.getAccount().getAccountId(),saveBuffId=buffId;
            final String characterName=pc.getName();
            final Timestamp expiration=new Timestamp(System.currentTimeMillis()+durationMillis);
            try(l1j.server.server.model.L1InventoryTransaction tx=
                    User.CheckAndSetPendingUseETCItem(pc,CacheD.accountOrderKey("nshop:", characterId))) {
                if(!tx.debit(useItem,1))return;
                tx.requireTables("character_tams");
                tx.sql(con->{
                    try(PreparedStatement ps=con.prepareStatement(
                            "INSERT INTO character_tams SET account_id=?,char_id=?,char_name=?,skill_id=?,expiration_time=?,reserve=? "
                            +"ON DUPLICATE KEY UPDATE account_id=?,char_name=?,expiration_time=?")) {
                        ps.setInt(1,accountId);ps.setInt(2,characterId);ps.setString(3,characterName);
                        ps.setInt(4,saveBuffId);ps.setTimestamp(5,expiration);ps.setInt(6,0);
                        ps.setInt(7,accountId);ps.setString(8,characterName);ps.setTimestamp(9,expiration);ps.executeUpdate();
                    }
                });
                CDB.RequestUseItem(pc, tx, () -> {
                    try {
                        L1SkillUse.cast(pc, saveBuffId, pc.getId(), pc.getX(), pc.getY(),
                                null, activeSeconds, L1SkillUse.TYPE_GMBUFF);
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, useItem);
                    }
                });
            } catch(RuntimeException failure) {
                SC_SystemMessage.send(pc,"강화 버프를 저장하지 못했습니다. 아이템을 확인해 주세요.");
            }
        } else if (itemId == 600218) {
            final int characterId=pc.getId();
            try(l1j.server.server.model.L1InventoryTransaction tx=
                    User.CheckAndSetPendingUseETCItem(pc,CacheD.accountOrderKey("nshop:", characterId))) {
                if(!tx.debit(useItem,1))return;
                tx.requireTables("character_tams");
                tx.sql(con->{
                    try(PreparedStatement ps=con.prepareStatement("DELETE FROM character_tams WHERE char_id=? AND skill_id BETWEEN 4075 AND 4094")) {
                        ps.setInt(1,characterId);ps.executeUpdate();
                    }
                });
                CDB.RequestUseItem(pc, tx, () -> {
                    try {
                        for (int skill = 4075; skill <= 4094; skill++)
                            if (pc.hasSkillEffect(skill)) pc.removeSkillEffect(skill);
                        try {
                            SC_SystemMessage.send(pc, "모든 N샵 버프가 초기화되었습니다.");
                        } catch (RuntimeException outputFailure) {
                            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                    "Committed N-shop buff reset notice failed: " + pc.getId(), outputFailure);
                        }
                    } finally {
                        ItemActionCompletion.finishCommittedConsumable(pc, useItem);
                    }
                });
            } catch(RuntimeException failure) {
                SC_SystemMessage.send(pc,"강화 버프 초기화를 저장하지 못했습니다.");
            }
		}
	}
}
