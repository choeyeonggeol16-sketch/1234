package l1j.server.server.model.item.function;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;

/** Area-damage consumables. Inventory debit is durable before damage or packets. */
public final class AreaDamageItems {
    private AreaDamageItems() { }

    public static void use(L1PcInstance pc, L1ItemInstance input, int targetId, int itemId) {
        if (pc == null || input == null || input.getItemId() != itemId) return;
        final int map = pc.getMapId();
        switch (itemId) {
            case 30055:
                if (!((map >= 2101 && map <= 2201)
                        || (map >= 12152 && map <= 12200))) {
                    SC_SystemMessage.send(pc, "특정지역에서만 사용이 가능합니다.");
                    return;
                }
                break;
            case 3000425:
                if (!((map >= 1911 && map <= 1912) || map == 623 || map == 1 || map == 2)) {
                    SC_SystemMessage.send(pc, "해당맵에서만 사용가능 합니다.");
                    return;
                }
                if (pc.getLevel() <= 70) {
                    SC_SystemMessage.send(pc, "레벨 69까지만 사용가능 합니다.");
                    return;
                }
                break;
            case 711:
                if (!((map >= 1911 && map <= 1912) || map == 623)) {
                    SC_SystemMessage.send(pc, "해당맵에서만 사용가능 합니다.");
                    return;
                }
                if (pc.getLevel() > 75) {
                    SC_SystemMessage.send(pc, "레벨 74까지만 사용가능 합니다.");
                    return;
                }
                break;
            default:
                return;
        }
        if (pc.isInvisble()) return;
        // Capture the original target identity, not merely a potentially reused object ID.
        // The original code consumed even when the target ID could not be resolved.
        final L1Object target = L1World.getInstance().findObject(targetId);
        ItemReceiptOperations.UseDurableConsume(pc, input, () -> {
            try {
                CreatureItemActions.cancelAbsoluteBarrier(pc);
                if (target == null || L1World.getInstance().findObject(targetId) != target) return;
                final int damage = itemId == 30055 ? 250 : itemId == 3000425 ? 50 : 100;
                final int effect = itemId == 30055 ? 762 : itemId == 3000425 ? 5789 : 16060;
                for (L1Object object : L1World.getInstance().getVisiblePoint(target.getLocation(), 4)) {
                    if (object instanceof L1MonsterInstance) {
                        L1NpcInstance npc = (L1NpcInstance) object;
                        if (!npc.isDead() && npc.getId() != target.getId()) {
                            npc.setStatus(ActionCodes.ACTION_Damage);
                            try {
                                SC_DoActionGFX.broadcast(npc, 2);
                            } catch (RuntimeException notificationFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Area item action packet failed after commit", notificationFailure);
                            }
                        }
                        npc.receiveDamage(pc, damage);
                    } else if (itemId == 30055
                            && (object instanceof L1DoorInstance || object instanceof L1TowerInstance)) {
                        ((L1NpcInstance) object).receiveDamage(pc, damage);
                    }
                }
                try {
                    SC_UseAttackSkill.send(pc.getNetConnection(), pc, target.getId(), effect,
                            target.getX(), target.getY(), 18);
                } catch (RuntimeException notificationFailure) {
                    CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Area item attack notification failed after commit", notificationFailure);
                }
                try {
                    SC_UseAttackSkill.broadcast(pc, target.getId(), effect,
                            target.getX(), target.getY(), 18);
                } catch (RuntimeException notificationFailure) {
                    CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                            "Area item attack broadcast failed after commit", notificationFailure);
                }
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, input);
            }
        });
    }
}
