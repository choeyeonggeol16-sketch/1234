package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.CommonUtil;

/** Item-specific Utility effects; selected once by ItemActionDispatcher. */
/** Extracted PetAndSummonItems handlers; effect, consumption and transaction order unchanged. */
final class PetAndSummonItems {
    private static final int[] SUMMON_IDS = { 7320072, 7320073, 7320074, 7320075, 7320076, 7320077, 7320078, 7320079, 7320080,
                    7320081, 7320000 };

    private PetAndSummonItems() {}

    static ItemActionResult handlePlayRaceFlute(ItemActionContext use) {
        SC_SystemMessage.send(use.player(), "구형 펫 기능은 이용할 수 없습니다.");
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleSpecialFlute(ItemActionContext use) {
        SC_SystemMessage.send(use.player(), "구형 펫 기능은 이용할 수 없습니다.");
        return ItemActionResult.STOP;
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
        final L1Npc template = CObjectDB.GetNPC(
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
