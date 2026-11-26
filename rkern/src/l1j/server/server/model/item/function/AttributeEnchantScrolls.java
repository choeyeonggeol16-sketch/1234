package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.storage.CacheD;

/** Attribute conversion and guaranteed-grade scrolls: source debit + target update share one receipt. */
public final class AttributeEnchantScrolls {
    private static final Logger LOG = Logger.getLogger(AttributeEnchantScrolls.class.getName());

    private AttributeEnchantScrolls() {}

    public static void apply(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance weapon) {
        if (pc == null || scroll == null || weapon == null || scroll == weapon) return;
        final int scrollId = scroll.getItemId();
        final int current = weapon.getAttrEnchantLevel();
        // The packet handler has already verified scroll/weapon type and enchant eligibility.
        // Repeat the identity/equipment checks here, since this is the durable boundary.
        if (pc.getInventory().GetByServerID(scroll.getId()) != scroll
                || pc.getInventory().GetByServerID(weapon.getId()) != weapon
                || weapon.getItem() == null || weapon.getItem().getType2() != 1
                || weapon.isEquipped() || scroll.isEquipped() || current < 0 || current > 20) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final int result;
        final int message;
        final String notification;
        if (scrollId >= 560030 && scrollId <= 560033) {
            final int base = (scrollId - 560030) * 5;
            if (current == 0) {
                SC_ServerMessage.send(pc, 79);
                return;
            }
            if (current > base && current <= base + 5) {
                SC_ServerMessage.send(pc, 3319);
                return;
            }
            result = base + (current % 5 == 0 ? 5 : current % 5);
            message = current % 5 == 0 ? 3296 : 1410;
            notification = null;
        } else if (scrollId >= 310064 && scrollId <= 310068) {
            // The old 5th-grade scrolls refuse grades >= 5, except reset scroll 310068.
            if (scrollId != 310068 && current != 0 && current % 5 == 0) {
                SC_SystemMessage.send(pc, "더 이상 강화할 수 없습니다.");
                return;
            }
            switch (scrollId) {
            case 310064:
                result = 15;
                notification = "풍령의 속성 5단계가 스며들었습니다.";
                break;
            case 310065:
                result = 20;
                notification = "지령의 속성 5단계가 스며들었습니다.";
                break;
            case 310066:
                result = 10;
                notification = "수령의 속성 5단계가 스며들었습니다.";
                break;
            case 310067:
                result = 5;
                notification = "화령의 속성 5단계가 스며들었습니다.";
                break;
            default:
                result = 0;
                notification = "부여된 속성이 초기화 되었습니다.";
                break;
            }
            message = 0;
        } else {
            return;
        }
        final String weaponName = weapon.getLogName();
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            tx.change(weapon, image -> image.setAttrEnchantLevel(result));
            tx.onFailure(() -> SC_SystemMessage.send(pc, "속성 주문서 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                // The original inventory update also sent the enchant-update packet.
                // Receipt replay emits status/name/color, but not this packet.
                SC_PacketBox.send(pc, SC_PacketBox.ITEM_ENCHANT_UPDATE, weapon);
                if (message != 0) SC_ServerMessage.send(pc, message, weaponName);
                else SC_SystemMessage.send(pc, notification);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Attribute scroll failed: " + pc.getId() + " / " + scrollId,
                    failure);
            SC_SystemMessage.send(pc, "속성 주문서를 처리하지 못했습니다.");
        }
    }
}
