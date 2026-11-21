package l1j.server.server.model.item.function;

import static l1j.server.server.model.item.function.CreatureItemActions.ITEM_USE2_LOG;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;
import static l1j.server.server.model.item.function.PetWithdrawalItems.withdrawPet;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class EngagementRingItemUse {
    private EngagementRingItemUse() {}

    static ItemActionResult handleEngagementRing(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            L1PcInstance partner = null;
            boolean partner_stat = false;

            int chargeCount = item.getChargeCount();
            if (pc.getPartnerId() != 0) {
                partner = (L1PcInstance) L1World.getInstance().findObject(pc.getPartnerId());
                if (chargeCount > 0) {
                    if (partner != null && partner.getPartnerId() != 0 && pc.getPartnerId() == partner.getId()
                            && partner.getPartnerId() == pc.getId()) {
                        partner_stat = true;
                        item.setChargeCount(item.getChargeCount() - 1);
                        pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
                    }
                }
            } else {
                SC_ServerMessage.send(pc, 662);

                return ItemActionResult.STOP;
            }

            if (partner_stat) {
                boolean castle_area = L1CastleLocation.checkInAllWarArea(

                        partner.getX(), partner.getY(), partner.getMapId());
                if (castle_area == true || partner.isDead() || partner.getMapId() == 603
                        || partner.getMapId() == 255 || partner.getMapId() == 777 || partner.getMapId() == 778
                        || partner.getMapId() == 39 || partner.getMapId() == 5167 || partner.getMapId() == 5153
                        || partner.getMapId() == 5001 || (partner.getMapId() > 190 && partner.getMapId() < 201)
                        || (partner.getMapId() > 255 && partner.getMapId() < 260) || partner.getMapId() == 23
                        || partner.getMapId() == 5153 || partner.getMapId() == 5001 || partner.getMapId() == 24
                        || (partner.getMapId() > 239 && partner.getMapId() < 244)
                        || (partner.getMapId() > 247 && partner.getMapId() < 252)
                        || (partner.getMapId() > 280 && partner.getMapId() < 289)
                        || (partner.getMapId() > 1 && partner.getMapId() < 2)
                        || (partner.getMapId() > 1700 && partner.getMapId() < 1712)) {
                    SC_SystemMessage.send(pc, "당신의 파트너는 죽어있거나 갈 수 없는 곳에 있습니다.");
                }
            } else if (item.getChargeCount() > 0) {
                SC_ServerMessage.send(pc, 546);

            } else {
                SC_ServerMessage.send(pc, 79);

            }
        }
            return ItemActionResult.COMPLETE;
    }
}
