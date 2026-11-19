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

public final class DogFightEnhancementItems {
    private DogFightEnhancementItems() {}
	public static void 투견조작(L1PcInstance pc, int targetId, L1ItemInstance item) {
		SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
		SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
		L1Object target = L1World.getInstance().findObject(targetId);
		
		if (target != null && target instanceof MJDogFightInstance) {
			
			((MJDogFightInstance)target).set_min_damage(((MJDogFightInstance)target).get_min_damage() + Config.투견최소랜덤데미지);
			((MJDogFightInstance)target).set_max_damage(((MJDogFightInstance)target).get_max_damage() + Config.투견최대랜덤데미지);
			((MJDogFightInstance)target).set_attackdelay_reduce(((MJDogFightInstance)target).get_attackdelay_reduce() + Config.투견공속);
			SC_ChatPacket.sendDirect(pc, pc, "해당 투견의 [공격력: 최소범위 +"+Config.투견최소랜덤데미지+" / 최대범위 +"+Config.투견최대랜덤데미지+"] [공속: "+Config.투견공속+"] 증가하였습니다");
			
			pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
		} else {
			SC_ChatPacket.sendDirect(pc, pc, "투견에만 사용가능합니다.");
		}
	}
}
