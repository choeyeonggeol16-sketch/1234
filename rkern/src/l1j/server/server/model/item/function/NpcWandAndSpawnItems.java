package l1j.server.server.model.item.function;


import l1j.server.server.datatables.CObjectDB;
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

import java.lang.reflect.Constructor;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;

public final class NpcWandAndSpawnItems {
    private NpcWandAndSpawnItems() {}
	public static void useNpcSpownWand(L1PcInstance pc, int targetId, L1ItemInstance item) {
        if (pc == null || item == null || pc.getInventory().GetByServerID(item.getId()) != item
                || item.getChargeCount() <= 0) return;
        // Resolve the target and its executable constructor BEFORE any debit.
        // An invalid NPC implementation must not consume a wand charge.
        final L1Npc template = CObjectDB.GetNPC(targetId);
        if (template == null) return;
        final Constructor<?> constructor;
        try {
            constructor = Class.forName("l1j.server.server.model.Instance."
                    + template.getImpl() + "Instance").getConstructors()[0];
        } catch (ReflectiveOperationException | RuntimeException invalidTemplate) {
            java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName())
                    .log(java.util.logging.Level.WARNING, "NPC wand template is not instantiable", invalidTemplate);
            return;
        }
        try {
            // Retire previous legacy dirty-column saves ahead of the receipt's
            // DB preimage SELECT, not after reserving the economy owner lane.
            if (item.getRecordingColumns() != 0)
                pc.getInventory().saveItem(item, CUserInventory.COL_SAVE_ALL);
        } catch (RuntimeException saveFailure) {
            java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName())
                    .log(java.util.logging.Level.WARNING, "NPC wand prior state flush failed", saveFailure);
            return;
        }
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc, CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (pc.getInventory().GetByServerID(item.getId()) != item) return;
            final int chargeCount = item.getChargeCount();
            if (chargeCount <= 0) return;
            if (chargeCount == 1) {
                // The old removal deleted the entire wand, not one stack unit.
                if (!tx.debit(item, item.getCount())) return;
            } else {
                tx.changeChargeCount(item, chargeCount - 1);
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "소환 지팡이 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                try {
                    L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(template);
                    npc.setId(IdFactory.getInstance().nextId());
                    npc.setMap(pc.getMapId());
                    npc.setX(pc.getX());
                    npc.setY(pc.getY());
                    npc.setHomeX(pc.getX());
                    npc.setHomeY(pc.getY());
                    npc.setHeading(2);
                    L1World.getInstance().storeAndEnterWorld(npc);
                    npc.startTimedDelete(60000);
                } catch (Exception spawnFailure) {
                    // A committed debit must not be retried or silently refunded
                    // if a world spawn fails. Let the existing committed-effect
                    // failure handler isolate this inconsistent result.
                    throw new IllegalStateException("NPC wand spawn failed after committed charge", spawnFailure);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
                try {
                    SC_AttackStatus.sendAndBroadcast(pc, 0, ActionCodes.ACTION_Wand, false);
                } catch (RuntimeException packetFailure) {
                    java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName())
                            .log(java.util.logging.Level.WARNING,
                                    "NPC wand visual packet failed after spawn", packetFailure);
                }
            });
        } catch (RuntimeException failure) {
            java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName())
                    .log(java.util.logging.Level.WARNING, "NPC wand transaction admission failed", failure);
            try { SC_SystemMessage.send(pc, "소환 지팡이를 처리하지 못했습니다."); }
            catch (RuntimeException notificationFailure) {
                java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName())
                        .log(java.util.logging.Level.WARNING, "NPC wand failure notification failed", notificationFailure);
            }
        }
    }

	public static void spawn(int x, int y, short MapId, int Heading, int npcId, int randomRange,
			int roomnumber) {
		try {// 타입이란부분에 월래2였는데 얼음여왕은 1을 죠 타입을따라가보면
			L1NpcInstance npc = CObjectDB.CreateNPC(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			if (randomRange == 0) {
				npc.getLocation().set(x, y, MapId);
				npc.getLocation().forward(Heading);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);
				if (tryCount >= 50) {
					npc.getLocation().forward(Heading);
				}
			}
			if (npc instanceof L1DoorInstance) {
				if (npc.getNpcId() == 5147) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32816);
					((L1DoorInstance) npc).setRightEdgeLocation(32821);
					((L1DoorInstance) npc).setDirection(1);
				} else if (npc.getNpcId() == 5148) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32804);
					((L1DoorInstance) npc).setRightEdgeLocation(32809);
					((L1DoorInstance) npc).setDirection(1);
				} else if (npc.getNpcId() == 5149) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32853);
					((L1DoorInstance) npc).setRightEdgeLocation(32858);
					((L1DoorInstance) npc).setDirection(1);
				} else if (npc.getNpcId() == 5150) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32760);
					((L1DoorInstance) npc).setRightEdgeLocation(32764);
					((L1DoorInstance) npc).setDirection(0);
				} else if (npc.getNpcId() == 5151) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32918);
					((L1DoorInstance) npc).setRightEdgeLocation(32923);
					((L1DoorInstance) npc).setDirection(1);
				}
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(Heading);

			L1World.getInstance().storeAndEnterWorld(npc);


			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
