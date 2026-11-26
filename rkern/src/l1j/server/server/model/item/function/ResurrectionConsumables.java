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
final class ResurrectionConsumables {
    private ResurrectionConsumables() {}

    static ItemActionResult handleTeleportCompanions(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int spellTargetId = use.spellTargetId();
        if(StadiumManager.getInstance().is_on_stadium(pc.getMapId())){
            pc.sendPackets("사용할 수 없는 지역입니다.");
            return ItemActionResult.STOP;
        }

        final L1Object candidate = CWorld.getInstance().findObject(spellTargetId);
        if (candidate instanceof L1Character resobject) {
            if (resobject != null) {
                if (resobject instanceof L1PcInstance) {
                    L1PcInstance target = (L1PcInstance) resobject;
                    if (pc.getId() == target.getId()) {
                        return ItemActionResult.STOP;
                    }

                    int castle_id = L1CastleLocation.getCastleIdByArea(pc);
                    if (castle_id != 0) {
                        SC_SystemMessage.send(pc, "사용할 수 없는 지역입니다.");
                        return ItemActionResult.STOP;
                    }

                    if (hasLivingOccupant(target)) {
                        SC_ServerMessage.send(pc, 592);
                        return ItemActionResult.STOP;
                    }
                    if (target.getCurrentHp() == 0 && target.isDead() == true) {
                        if (pc.getMap().isUseResurrection()) {
                            UseDurableConsume(pc, item, () -> {
                                try {
                                    // Do not publish a consent request to a disconnected or
                                    // replaced target after the durable receipt commits.
                                    if (CWorld.getInstance().findObject(target.getId()) != target
                                            || !target.isDead() || target.getCurrentHp() != 0) return;
                                    target.setTempID(pc.getId());
                                    try {
                                        if (itemId == 40089 || itemId == 30074)
                                            SC_MessageYN.send(target, 321, "");
                                        else if (itemId == 140089)
                                            SC_MessageYN.send(target, 322, "");
                                    } catch (RuntimeException ex) {
                                        CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                                "Resurrection consent packet failed after commit: " + pc.getId(), ex);
                                    }
                                } finally {
                                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                                }
                            });
                            return ItemActionResult.STOP;
                        } else {
                            return ItemActionResult.STOP;
                        }
                    }
                } else if (resobject instanceof L1NpcInstance) {
                    if (!(resobject instanceof L1TowerInstance)) {
                        L1NpcInstance npc = (L1NpcInstance) resobject;
                        int npcId = npc.getNpcId();
                        if (npcId == 0) {
                            pc.sendPackets("부활 할 수 없습니다.");
                            return ItemActionResult.STOP;
                        }

                        if (npc instanceof L1CompanionInstance) {
                            if (hasLivingOccupant(npc)) {
                                SC_ServerMessage.send(pc, 592);
                                return ItemActionResult.STOP;
                            }
                        } else if (npc.getNpcTemplate().isCantResurrect()) {
                            UseDurableConsume(pc, item,
                                    () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
                            return ItemActionResult.STOP;
                        }
                        if (npc.getCurrentHp() == 0 && npc.isDead()) {
                            UseDurableConsume(pc, item, () -> {
                                try {
                                    // Do not resurrect a re-used / already-alive NPC twice.
                                    if (CWorld.getInstance().findObject(npc.getId()) == npc
                                            && npc.getCurrentHp() == 0 && npc.isDead()) {
                                        npc.resurrect(npc.getMaxHp() / 4);
                                        npc.setResurrect(true);
                                        npc.occupyNpcTileNow();
                                    }
                                } finally {
                                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                                }
                            });
                            return ItemActionResult.STOP;
                        }
                    }
                }
            }
            // Legacy unsuccessful targets still consumed a scroll.
            UseDurableConsume(pc, item,
                    () -> ItemActionCompletion.finishCommittedConsumable(pc, item));
            return ItemActionResult.STOP;
        }

        return ItemActionResult.COMPLETE;
    }

    private static boolean hasLivingOccupant(L1Object target) {
        final int id = target.getId();
        final int map = target.getMapId();
        final var point = target.getLocation();
        for (L1PcInstance other : CWorld.getInstance().getAllPlayers()) {
            if (other != null && other.getId() != id && other.getMapId() == map
                    && point.isSamePoint(other.getLocation()) && !other.isDead())
                return true;
        }
        return false;
    }
}
