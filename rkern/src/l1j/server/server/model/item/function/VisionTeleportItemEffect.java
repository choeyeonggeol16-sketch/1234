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

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Item-specific effect extracted without changing its original execution body. */
final class VisionTeleportItemEffect {
    private VisionTeleportItemEffect() {}
	
	public static void 비전텔레포트(L1PcInstance pc, int spellsc_x, int spellsc_y ) {
	int tx = spellsc_x;
	int ty = spellsc_y;
	if (!pc.glanceCheck(tx, ty) ) {
		
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY장애물로 인해 이동할 수 없습니다.");
		SC_SystemMessage.send(pc, "\\fY장애물로 인해 이동할 수 없습니다.");
	return;	
	}
	int diffLocX = Math.abs(pc.getX() - spellsc_x);
	int diffLocY = Math.abs(pc.getY() - spellsc_y);
	
	if (pc.isstop() || pc.hasSkillEffect(L1SkillId.SHOCK_STUN) || pc.hasSkillEffect(L1SkillId.DESPERADO) || pc.hasSkillEffect(L1SkillId.쉐도우제약)|| pc.hasSkillEffect(L1SkillId.이터디스) || pc.hasSkillEffect(L1SkillId.버닝샷) ) {
		SC_SystemMessage.send(pc, "\\fY사용할 수 없는 상태입니다.");
		return;
	}
	if (pc.isSkillDelay()) {
		SC_SystemMessage.send(pc, "\\f3스킬 딜레이중이므로 사용할 수 없습니다");
		return;
	}
	if (diffLocX > 7 || diffLocY > 7) {
		
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
		SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
		return;
	}
	if (pc.hasSkillEffect(L1SkillId.비전텔레포트)) {
		int n = pc.getSkillEffectTimeSec(L1SkillId.비전텔레포트);
		SC_SystemMessage.send(pc, "\\fY비전 텔레포트 대기시간 : " + n + "초 후에 사용가능합니다.");
		return;
	}
	if (pc.getCurrentMp() < Config.비전엠피) {
		SC_ServerMessage.send(pc, 278); // \f1MP가 부족해 마법을 사용할
		return;
	}
	if (pc.getZoneType() == 1) {
		SC_SystemMessage.send(pc, "\\fY세이프티존에서는 사용할 수 없습니다");
		return;
	}
	
	if (pc.getType() == 3 || pc.isGm()) {
	pc.setSkillEffect(L1SkillId.비전텔레포트, Config.비전대기시간 * 1000);
	pc.setCurrentMp(pc.getCurrentMp() - Config.비전엠피);
	pc.start_teleport(spellsc_x, spellsc_y, pc.getMapId(), pc.getHeading(), 169, false, pc.getAutoHunt());
	SC_EffectLocation.send(pc, pc.getX(), pc.getY(), 2235);
	SC_EffectLocation.broadcast(pc, pc.getX(), pc.getY(), 2235);
	} else {
		SC_SystemMessage.send(pc, "\\fY해당 스킬을 사용할 수 없는 클래스입니다.");
	}
	
}
}
