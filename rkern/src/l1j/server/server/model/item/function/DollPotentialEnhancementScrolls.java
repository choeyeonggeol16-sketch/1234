package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsumeTwoInput;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.ResolventItemFacade.useResolvent;

/** Item-specific action effects dispatched by item ID. */
final class DollPotentialEnhancementScrolls {
    private DollPotentialEnhancementScrolls() {}

    static ItemActionResult handleDollPotential(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        {
            if (!isOwnedScrollTarget(pc, item, targetItem)
                    || targetItem.getItemId() != 22208) return ItemActionResult.STOP;
            final String upgradeName = targetItem.getName();
            if (CommonUtil.random(100) < Config.방어구비법서확률) {
                UseDurableTwoInput(pc, item, targetItem,
                        922208, () -> {
                            try {
                                pc.sendPackets("강화에 성공하여 " + upgradeName + "이 강렬하게 빛납니다. ");
                            } catch (RuntimeException packetFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Committed DollPotentialEnhancementScrolls notice failed", packetFailure);
                            } finally {
                                ItemActionCompletion.finishCommittedConsumable(pc, item);
                            }
                        });
            } else {
                UseDurableConsumeTwoInput(pc, item, targetItem,
                        () -> {
                            try {
                                pc.sendPackets("강화에 실패하여 " + upgradeName + "이 증발 하였습니다. ");
                            } catch (RuntimeException packetFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Committed DollPotentialEnhancementScrolls notice failed", packetFailure);
                            } finally {
                                ItemActionCompletion.finishCommittedConsumable(pc, item);
                            }
                        });
            }
            return ItemActionResult.STOP;
        }
    }

}
