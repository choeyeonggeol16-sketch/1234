package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.WeaponTransformationTables.*;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class WeaponTransformationReceipt {
    private WeaponTransformationReceipt() {}

    static void useWeaponTransformation(L1PcInstance pc, L1ItemInstance scroll,
            L1ItemInstance weapon) {
        if (pc == null || scroll == null || weapon == null || scroll == weapon
                || pc.getInventory().GetByServerID(scroll.getId()) != scroll
                || pc.getInventory().GetByServerID(weapon.getId()) != weapon) return;
        final int scrollId = scroll.getItemId();
        final int weaponId = weapon.getItemId();
        final int[] rewards;
        if (scrollId == 698) {
            if (weaponId != 40346 && weaponId != 40370 && weaponId != 40362 && weaponId != 40354) {
                SC_ServerMessage.send(pc, 79);
                return;
            }
            rewards = TRANSFORM_WEAPON_698;
        } else if (scrollId == 776) {
            if (weaponId != 3000088 && weaponId != 3000352 && weaponId != 755
                    && weaponId != 756 && weaponId != 757 && weaponId != 745 && weaponId != 746) {
                SC_ServerMessage.send(pc, 79);
                return;
            }
            rewards = TRANSFORM_WEAPON_776;
        } else {
            return;
        }
        if (weapon.getBless() >= 128 || weapon.getCount() != 1 || weapon.isEquipped()) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, scroll.getId())) {
            if (!tx.debit(scroll, 1) || !tx.debit(weapon, 1)) {
                SC_ServerMessage.send(pc, 79);
                return;
            }
            final int resultId = rewards[ThreadLocalRandom.current().nextInt(rewards.length)];
            final L1Item template = CObjectDB.LoadItem().getTemplate(resultId);
            if (template == null) {
                SC_SystemMessage.send(pc, "무기 변환 결과 아이템 설정을 확인해 주세요.");
                return;
            }
            final L1ItemInstance image = new L1ItemInstance(template, 1);
            image.setId(IdFactory.getInstance().nextId());
            image.setBless(template.getBless());
            image.setIdentified(true);
            image.setEnchantLevel(weapon.getEnchantLevel());
            final L1ItemInstance reward = tx.grant(image);
            if (reward == null) {
                SC_SystemMessage.send(pc, "변환된 무기를 소지할 수 없습니다.");
                return;
            }
            final String name = reward.getLogName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "무기 변환이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_SystemMessage.send(pc, name + " 을 얻었습니다."));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 무기 변환을 완료할 수 없습니다.");
        }
    }
}
