package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanJoin;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeCharName;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_IdentifyDesc;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Extracted PetAndSummonItems handlers; effect, consumption and transaction order unchanged. */
final class PetAndSummonItems {
    private static final int[] SUMMON_IDS = { 7320072, 7320073, 7320074, 7320075, 7320076, 7320077, 7320078, 7320079, 7320080,
                    7320081, 7320000 };

    private PetAndSummonItems() {}

    static ItemActionResult handlePlayRaceFlute(ItemActionContext use) {
        final L1PcInstance pc = use.player();
        final int petAmuletId = use.itemObjectId();
        final L1ItemInstance amulet = use.item();
        // The object id in this use request must refer to the selected amulet.
        if (pc.getInventory().getItem(petAmuletId) != amulet)
            return ItemActionResult.STOP;
        final L1ItemInstance flute = pc.getInventory().findItemId(41160);
        if (flute == null || flute.getCount() <= 0) {
            SC_ServerMessage.send(pc, 79);
            return ItemActionResult.STOP;
        }
        if (!PetWithdrawalItems.canWithdrawPet(pc, petAmuletId))
            return ItemActionResult.STOP;

        // The flute is the consumed material, not the selected pet amulet.
        // Reserve its exact instance and debit durably before creating a world pet.
        ItemReceiptOperations.UseDurableConsume(pc, flute, () -> {
            try {
                // A pet amulet can be moved or replaced while the flute debit
                // is in flight. Never spawn against a stale item reference.
                if (pc.getInventory().getItem(petAmuletId) == amulet)
                    withdrawPet(pc, petAmuletId);
            } finally {
                // The amulet itself survives. Its normal item-use delay belongs
                // after the flute debit, never to the asynchronous submit path.
                ItemActionCompletion.finishCommittedConsumable(pc, amulet);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleSpecialFlute(ItemActionContext use) {
        L1PcInstance pc = use.player();
        Object[] petList = pc.getPetList().values().toArray();
        for (Object petObject : petList) {
            if (petObject instanceof L1PetInstance) {
                L1PetInstance pet = (L1PetInstance) petObject;
                pet.call();
            }
        }
        try {
            SC_Sound.send(pc, 437);
        } catch (RuntimeException packetFailure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Pet call sound packet failed", packetFailure);
        }
        try {
            SC_Sound.broadcast(pc, 437);
        } catch (RuntimeException packetFailure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Pet call broadcast sound failed", packetFailure);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSummonMonster(ItemActionContext use) {
        final L1PcInstance pc = use.player();
        final L1ItemInstance item = use.item();
        if (!pc.getMap().isRecallPets() || pc.isInWarArea()) {
            SC_ServerMessage.send(pc, 79);
            return ItemActionResult.STOP;
        }
        if (!pc.getPetList().isEmpty()) {
            SC_SystemMessage.send(pc, "더 이상 소환이 불가능 합니다.");
            return ItemActionResult.STOP;
        }
        final L1Npc template = NpcTable.getInstance().getTemplate(
                SUMMON_IDS[CommonUtil.random(3)]);
        if (template == null) {
            SC_ServerMessage.send(pc, 79);
            return ItemActionResult.STOP;
        }
        // World effects are not reversible SQL rows. Do not create a summon
        // until the consumable debit has durably committed and been replayed.
        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
            try {
                L1SummonInstance summon = new L1SummonInstance(template, pc);
                summon.setPetcost(0);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

}
