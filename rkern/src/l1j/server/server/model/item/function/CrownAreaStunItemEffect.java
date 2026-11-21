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
final class CrownAreaStunItemEffect {
    private CrownAreaStunItemEffect() {}

    public static void 군주광역스턴(L1PcInstance pc) {
        if (pc.isCrown() || pc.isGm()) {
            if (pc.hasSkillEffect(L1SkillId.DELAY)) { // 딜레이
                SC_SystemMessage.send(pc, "아직 광역 스턴을 사용할 수 없습니다.");
                return;
            }
            if (pc.isInvisble()) {
                SC_SystemMessage.send(pc, "광역 스턴은 투명상태로 시전이 불가능합니다.");
                return;
            }
            if (pc.getMapId() == 800) {
                SC_SystemMessage.send(pc, "광역 스턴은 시장에서 시전이 불가능합니다.");
                return;
            }
            if (pc.getZoneType() == 1) {
                SC_SystemMessage.send(pc, "광역 스턴은 마을에서 시전이 불가능합니다.");
                return;
            }
            if (pc.getCurrentMp() < 30) {
                SC_ServerMessage.send(pc, 278); // \f1MP가 부족해 마법을 사용할
                // 수 있지 않습니다.
                return;
            }
            pc.setCurrentMp(pc.getCurrentMp() - 30);
            SC_SystemMessage.send(pc, "광역 스턴을 시전 합니다.");
            pc.setSkillEffect(L1SkillId.DELAY, 10 * 1000);
            SC_SkillIconGFX.send(pc, 74, 3);

            int actionId = ActionCodes.ACTION_SkillBuff;
            SC_DoActionGFX.send(
                    pc.getNetConnection(),
                    pc.getId(),
                    actionId);
            SC_DoActionGFX.broadcast(
                    pc,
                    actionId);

            // One RNG per owner-thread invocation, no per-target Random or
            // 5-element duration array. Duration and hit probability match the
            // original 5-way and 1..100 distributions exactly.
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(pc, 10)) {
                final int stunDuration = 2000 + 500 * random.nextInt(5);
                final int probability = random.nextInt(100) + 1;

                if (probability < 50) {
                    final int _shockStunDuration = stunDuration;
                    if (obj instanceof L1PcInstance) {
                        L1PcInstance target = (L1PcInstance) obj;
                        L1PinkName.onAction(target, pc);
                        if ((pc.getClanid() > 0 && (pc.getClanid() == target.getClanid())) || target.isGm()) {
                        } else {
                            L1Character cha = (L1Character) obj;

                            if (!cha.hasSkillEffect(SHOCK_STUN) && !cha.hasSkillEffect(L1SkillId.EMPIRE) && !cha.hasSkillEffect(EARTH_BIND) && !cha.hasSkillEffect(ICE_LANCE)) {
                                L1EffectSpawn.getInstance().spawnEffect(81162, _shockStunDuration, target.getX(), target.getY(), target.getMapId());
                                SC_Paralysis.send(target, SC_Paralysis.TYPE_STUN, true);
                                target.setSkillEffect(SHOCK_STUN, _shockStunDuration);
                                SC_SkillSound.send(target, target.getId(), 15101); // 스턴
                                SC_SkillSound.broadcast(target, target.getId(), 15101);
                            }
                        }
                    } else if (obj instanceof L1MonsterInstance || obj instanceof L1SummonInstance || obj instanceof L1PetInstance) {
                        L1NpcInstance targetnpc = (L1NpcInstance) obj;
                        if (!targetnpc.trySetSkillEffect(SHOCK_STUN, _shockStunDuration)) {
                            throw new java.util.concurrent.RejectedExecutionException("NPC SHOCK_STUN admission exhausted");
                        }
                        L1EffectSpawn.getInstance().spawnEffect(81162, _shockStunDuration, targetnpc.getX(),
                                targetnpc.getY(), targetnpc.getMapId());
                        SC_SkillSound.broadcast(targetnpc, obj.getId(), 15101);
                    }
                }
            }
        } else {
            SC_SystemMessage.send(pc, "해당 기술은 군주만 시전이 가능합니다.");
        }
        System.currentTimeMillis();
        return;
    }
}
