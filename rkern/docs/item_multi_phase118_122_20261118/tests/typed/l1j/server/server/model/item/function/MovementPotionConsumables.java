package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Persist one potion debit before changing the movement/haste state. */
final class MovementPotionConsumables {
    private MovementPotionConsumables() {}

    static boolean use(L1PcInstance pc, L1ItemInstance item, int itemId) {
        // A blocked potion is neither debited nor charged delay bookkeeping.
        if (pc.hasSkillEffect(L1SkillId.DECAY_POTION)) {
            SC_ServerMessage.send(pc, 698);
            return true;
        }
        switch (itemId) {
        case 40030, 40013, 7006, 40018, 40039, 40040, 41338,
             41261, 41262, 41268, 41269, 41271, 41272, 41273,
             41342, 30067, 140013, 140018, 30158, 875640293:
            UseDurableConsume(pc, item, () -> { PotionEffects.useGreenPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 40014, 140014, 41415, 30073:
            if (!(pc.isKnight() || pc.isWarrior() || pc.isCrown())) return denied(pc);
            break;
        case 40068, 140068, 210110, 30076:
            if (!pc.isElf()) return denied(pc);
            break;
        case 712, 40031, 30075:
            if (!pc.isCrown()) return denied(pc);
            break;
        case 40733:
            break;
        case 713, 210036, 30077:
            if (!pc.isBlackwizard()) return denied(pc);
            UseDurableConsume(pc, item, () -> { PotionEffects.useFruit(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        default:
            return false;
        }
        UseDurableConsume(pc, item, () -> { PotionEffects.useBravePotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
        return true;
    }

    private static boolean denied(L1PcInstance pc) {
        SC_ServerMessage.send(pc, 79);
        return true;
    }
}
