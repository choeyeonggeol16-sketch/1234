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

/** Item-specific effect extracted without changing its original execution body. */
final class MagicShieldItemEffect {
    private MagicShieldItemEffect() {}
	public static boolean 매직실드(L1PcInstance pc) {
		
		
		if (pc.getType() == 2 && pc.getLevel() >= 85) {
		if (pc.hasSkillEffect(L1SkillId.매직실드대기)) {
			
			int n = pc.getSkillEffectTimeSec(L1SkillId.매직실드대기);
			SC_SystemMessage.send(pc, "\\fY매직실드대기 대기시간 : " + n + "초 후에 사용가능합니다.");
			return false;
		}

		if (MJCommons.isNonAction(pc)) {
			return false;
		}

		
		if (pc.isInvisble()) {
			SC_SystemMessage.send(pc, "매직실드는 투명상태로 시전이 불가능합니다.");
			return false;
		}

		if (!pc.getInventory().checkItem(40319, 5)) {
			SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
			return false;
		}
		if (pc.getCurrentMp() < 20) {
			SC_ServerMessage.send(pc, 278); // \f1MP가 부족해 마법을 사용할
			return false;
		}
		
		if (!pc.getInventory().tryConsumeCombatReagent(40319, 5)) return false;
		if (pc.hasSkillEffect(L1SkillId.매직실드1)) {
			pc.removeSkillEffect(L1SkillId.매직실드1);
		}
		if (pc.hasSkillEffect(L1SkillId.매직실드2)) {
			pc.removeSkillEffect(L1SkillId.매직실드2);
		}
		
		pc.setSkillEffect(L1SkillId.매직실드대기, Config.매직실드대기 * 1000);
		pc.setSkillEffect(L1SkillId.매직실드1, Config.매직실드지속 * 1000);
		pc.setSkillEffect(L1SkillId.매직실드2, Config.매직실드지속 * 1000);
		SC_InventoryIcon.icoNew(pc, L1SkillId.매직실드2, 3156, Config.매직실드지속, true);
		pc.setCurrentMp(pc.getCurrentMp() - 20);
		SC_SkillSound.send(pc, pc.getId(), 14102);
		SC_SkillSound.broadcast(pc, pc.getId(), 14102);
		
		L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(),
				pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
        return true;

	} else {
		SC_SystemMessage.send(pc, "사용할 수 없는 클래스이거나 적정레벨이 아닙니다.");
        return false;
	}
	}
}
