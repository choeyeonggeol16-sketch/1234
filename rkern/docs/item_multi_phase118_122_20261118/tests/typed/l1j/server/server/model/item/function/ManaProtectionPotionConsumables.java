package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Persist one potion debit before changing breath, mana, wisdom or blind status. */
final class ManaProtectionPotionConsumables {
    private ManaProtectionPotionConsumables() {}

    static boolean use(L1PcInstance pc, L1ItemInstance item, int itemId) {
        if (pc.hasSkillEffect(L1SkillId.DECAY_POTION)) {
            SC_ServerMessage.send(pc, 698);
            return true;
        }
        switch (itemId) {
        case 210115, 40032, 40041, 41344:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBlessOfEva(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 30083, 40015, 140015, 40736, 41142, 210114:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBluePotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 30089, 40016, 140016, 210113:
            if (!(pc.isWizard() || pc.isBlackwizard())) {
                SC_ServerMessage.send(pc, 79);
                return true;
            }
            UseDurableConsume(pc, item, () -> { PotionEffects.useWisdomPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 40025:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBlindPotion(pc); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        default:
            return false;
        }
    }
}
