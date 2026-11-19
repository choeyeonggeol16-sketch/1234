package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Dedicated durable-item skill effects, keeping debit and effect order unchanged. */
final class DurableBuffScrollItems {
    private DurableBuffScrollItems() {}

    static void use(L1PcInstance pc, L1ItemInstance item, int itemId) {
        switch (itemId) {
            case 65648 -> UseDurableConsume(pc, item, () -> {
                try {
                    if (pc.hasSkillEffect(L1SkillId.God_buff)) pc.removeSkillEffect(L1SkillId.God_buff);
                    L1SkillUse.cast(pc, L1SkillId.God_buff, pc.getId(), pc.getX(), pc.getY(),
                            null, 0, L1SkillUse.TYPE_GMBUFF);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            case 40879 -> UseDurableConsume(pc, item, () -> {
                try {
                    if (pc.hasSkillEffect(L1SkillId.BLESSED_ARMOR)) pc.removeSkillEffect(L1SkillId.BLESSED_ARMOR);
                    L1SkillUse.cast(pc, L1SkillId.BLESSED_ARMOR, pc.getId(), pc.getX(), pc.getY(),
                            null, 0, L1SkillUse.TYPE_GMBUFF);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            case 773355 -> UseDurableConsume(pc, item, () -> {
                try {
                    if (pc.hasSkillEffect(L1SkillId.루돌프의버프)) pc.removeSkillEffect(L1SkillId.루돌프의버프);
                    pc.setSkillEffect(L1SkillId.루돌프의버프, 3600 * 1000);
                    pc.getAbility().addAddedWis((byte) 1);
                    try {
                        SC_InventoryIcon.icoNew(pc, L1SkillId.루돌프의버프, 5087, 3600, true);
                    } catch (RuntimeException outputFailure) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Committed Rudolph buff icon failed: " + pc.getId(), outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            default -> throw new IllegalArgumentException("Unknown buff scroll: " + itemId);
        }
    }

}
