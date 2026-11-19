package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.무기축복;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.무기축복100;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.악세인챈;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.악세인챈100;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.방어구축복;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.방어구축복100;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.옵션부여;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.품질부여주문서;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.집행진화주문서;

/** Dedicated handler for nine special enhancement scrolls; original effects and consumption preserved. */
final class SpecialEnchantmentItems {
    private SpecialEnchantmentItems() {}

    static void use(L1PcInstance pc, L1ItemInstance item, L1ItemInstance targetItem, int itemId) {
        switch (itemId) {
            case 7010 -> 무기축복(pc, item, targetItem);
            case 7011 -> 무기축복100(pc, item, targetItem);
            case 8010 -> 악세인챈(pc, item, targetItem);
            case 8011 -> 악세인챈100(pc, item, targetItem);
            case 5922910 -> 방어구축복(pc, item, targetItem);
            case 5922911 -> 방어구축복100(pc, item, targetItem);
            case 622910 -> 옵션부여(pc, item, targetItem);
            case 633910 -> 품질부여주문서(pc, item, targetItem);
            case 885544 -> 집행진화주문서(pc, item, targetItem);
            default -> throw new IllegalArgumentException("Unknown special scroll: " + itemId);
        }
    }
}
