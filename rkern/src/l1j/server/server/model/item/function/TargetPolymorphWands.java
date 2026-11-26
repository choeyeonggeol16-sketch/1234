package l1j.server.server.model.item.function;

import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.polyAction;

/** Item-specific action effects dispatched by item ID. */
final class TargetPolymorphWands {
    private TargetPolymorphWands() {}

    static ItemActionResult handleResurrectionScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        String text = use.text();
        int spellTargetId = use.spellTargetId();
        if (pc.getMapId() == 552 || pc.getMapId() == 555 || pc.getMapId() == 557 || pc.getMapId() == 558
                || pc.getMapId() == 779) {

            SC_ServerMessage.send(pc, 563);
        } else {
            SC_AttackStatus.sendAndBroadcast(pc, 0, ActionCodes.ACTION_Wand, false);
            int chargeCount = item.getChargeCount();
            int spriteId = pc.getCurrentSpriteId();
            if (chargeCount <= 0 && itemId != 40410 || spriteId == 6034 || spriteId == 6035) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            L1Object target = CWorld.getInstance().findObject(spellTargetId);
            if (spellTargetId == pc.getId() || (target != null && target instanceof L1Character)) {
                L1Character cha = spellTargetId == pc.getId() ? pc : (L1Character) target;
                if (itemId == 40008 || itemId == 140008) {
                    // Charged wands must commit the last-charge deletion (or
                    // charge decrement) before changing the target's form.
                    final int oldCharge = item.getChargeCount();
                    if (oldCharge <= 0) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                    try {
                        if (item.getRecordingColumns() != 0)
                            pc.getInventory().saveItem(item, CUserInventory.COL_SAVE_ALL);
                        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                                l1j.server.server.model.network.core.cs.packet.User.CheckAndSetPendingUseETCItem(pc,
                                l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
                            if (oldCharge == 1) {
                                // The old removeItem(item) removed the exhausted instance.
                                if (!tx.debit(item, item.getCount())) return ItemActionResult.STOP;
                            } else {
                                tx.changeChargeCount(item, oldCharge - 1);
                            }
                            tx.onFailure(() -> SC_SystemMessage.send(pc,
                                    "변신 지팡이 소모를 저장하지 못했습니다."));
                            l1j.server.server.model.network.core.cs.packet.CDB.RequestUseItem(pc, tx, () -> {
                                try {
                                    polyAction(pc, cha, itemId, text);
                                    cancelAbsoluteBarrier(pc);
                                } finally {
                                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                                }
                            });
                        }
                    } catch (RuntimeException ex) {
                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Polymorph wand receipt prepare failed: " + pc.getId(), ex);
                        SC_SystemMessage.send(pc, "변신 지팡이를 사용하지 못했습니다.");
                    }
                } else {
                    UseDurableConsume(pc, item, () -> {
                        try {
                            polyAction(pc, cha, itemId, text);
                            cancelAbsoluteBarrier(pc);
                        } finally {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                        }
                    });
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
        }
        return ItemActionResult.STOP;
    }}
