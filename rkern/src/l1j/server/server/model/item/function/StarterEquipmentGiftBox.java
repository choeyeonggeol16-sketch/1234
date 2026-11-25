package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

/** Dedicated item-use handler extracted from a mixed domain. */
final class StarterEquipmentGiftBox {
    private StarterEquipmentGiftBox() {}

    private static final Logger LOG = Logger.getLogger(StarterEquipmentGiftBox.class.getName());
    // Seven supported classes: crown, knight, elf, wizard, dark elf, dragon knight, illusionist.
    // Fixed rewards are retained once per JVM, rather than allocating arrays for each opening.
    private static final int[][] WEAPON = {
        { 1113, 169 },
        { 9, 62, 180 },
        { 9, 508 },
        { 509, 169 },
        { 507, 180 },
        { 62, 501, 615 },
        { 509, 504 },
    };
    private static final int[][] ARMOR = {
        { 20011, 21028, 21060, 20056, 20187, 20194, 20234 },
        { 20011, 21028, 21060, 20056, 20187, 20194, 20236, 22003 },
        { 20011, 21029, 21060, 20056, 20187, 20194, 22000 },
        { 20011, 22192, 21031, 20093, 20056, 20055, 20187, 20194, 20233, 20225, 22255 },
        { 20011, 21028, 21060, 20056, 20187, 20195, 20236, 22003 },
        { 20011, 21028, 21060, 20056, 20187, 20194, 20236, 22003 },
        { 20011, 21028, 21060, 20056, 20187, 20194, 20233, 22003 },
    };
    private static final int[][] ARMORENCHANT = {
        { 8, 8, 8, 8, 8, 8, 9 },
        { 8, 8, 8, 8, 8, 8, 9, 0 },
        { 8, 8, 8, 8, 8, 8, 0 },
        { 8, 8, 8, 0, 8, 7, 8, 8, 8, 5, 0 },
        { 8, 8, 8, 8, 8, 8, 9, 0 },
        { 8, 8, 8, 8, 8, 8, 9, 0 },
        { 8, 8, 8, 8, 8, 8, 8, 0 },
    };
    private static final int[][] ACCESSORY = {
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
        { 22231, 20257, 22228, 22228, 22225, 22225, 20288, 20317 },
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
        { 22230, 20422, 22228, 22228, 22226, 22226, 20288, 20317 },
    };
    private static final int[][] ACCESSORYENCHANT = {
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
        { 5, 0, 5, 5, 5, 5, 0, 0 },
    };
    private static final int[][] SPELLBOOK = {
        { 40226, 40227, 40228, 40229, 40230, 40231 },
        { 40164, 40165 },
        { 40232, 40233, 40234, 40235, 40236, 40237, 40238, 40239, 40240, 40241, 40242, 40244, 40170, 40171, 40172, 40173, 40174, 40175, 40176, 40177, 40178, 40179, 40180, 40181, 40182, 40183, 40184, 40185, 40186, 40187, 40188, 40189, 40190, 40191, 40192, 40193 },
        { 40170, 40171, 40172, 40173, 40174, 40175, 40176, 40177, 40178, 40179, 40180, 40181, 40182, 40183, 40184, 40185, 40186, 40187, 40188, 40189, 40190, 40191, 40192, 40193, 40197, 40224, 40213 },
        { 40265, 40266, 40267, 40269, 40271, 40272, 40273, 40274, 40275, 40278, 40279 },
        { 210021, 210022, 210023, 210024, 210026, 210027, 210028, 210029, 210030, 210031, 210032, 210033, 210034 },
        { 210000, 210001, 210002, 210003, 210005, 210006, 210007, 210008, 210009, 210010, 210011, 210012, 210013, 210015, 210016, 210017, 210018, 210019 },
    };
    private static final int[] DOLLS = { 500214, 500214, 210105, 210071, 500214, 500214, 500214 };

    private static boolean grant(L1InventoryTransaction tx, ArrayList<L1ItemInstance> announcements,
            int itemId, int count, int enchant, int bless, int attr, boolean identified) {
        final L1Item template = CObjectDB.LoadItem().getTemplate(itemId);
        if (template == null || count <= 0) return false;
        // createItem() publishes the item to L1World before COMMIT. Build a detached image.
        final L1ItemInstance item = new L1ItemInstance(template, count);
        item.setId(IdFactory.getInstance().nextId());
        item.setCount(count);
        item.setIdentified(identified);
        item.setEnchantLevel(enchant);
        item.setAttrEnchantLevel(attr);
        item.setBless(bless);
        final L1ItemInstance granted = tx.grant(item);
        if (granted == null) return false;
        announcements.add(granted);
        return true;
    }


    public static void openStarterKit(L1PcInstance pc, L1ItemInstance source) {
        if (pc == null || source == null || source.getItemId() != 30071
                || pc.getInventory().GetByServerID(source.getId()) != source) return;
        final int clazz = pc.isCrown() ? 0 : pc.isKnight() ? 1 : pc.isElf() ? 2
                : pc.isWizard() ? 3 : pc.isDarkelf() ? 4
                : pc.isDragonknight() ? 5 : pc.isBlackwizard() ? 6 : -1;
        if (clazz < 0) {
            SC_SystemMessage.send(pc, "해당 직업은 장비 지급함을 사용할 수 없습니다.");
            return;
        }
        final int[] Weapon = WEAPON[clazz];
        final int[] Armor = ARMOR[clazz];
        final int[] ArmorEnchant = ARMORENCHANT[clazz];
        final int[] Accessory = ACCESSORY[clazz];
        final int[] AccessoryEnchant = ACCESSORYENCHANT[clazz];
        final int[] SpellBook = SPELLBOOK[clazz];
        final int MagicDoll = DOLLS[clazz];
        if (Weapon == null || Armor == null || ArmorEnchant == null
                || Accessory == null || AccessoryEnchant == null || SpellBook == null
                || Armor.length != ArmorEnchant.length
                || Accessory.length != AccessoryEnchant.length || MagicDoll == 0) {
            SC_SystemMessage.send(pc, "해당 직업은 장비 지급함을 사용할 수 없습니다.");
            return;
        }
        final ArrayList<L1ItemInstance> rewards = new ArrayList<>(
                Weapon.length + Armor.length + Accessory.length + SpellBook.length + 3);
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(source, 1)) return;
					for (int i = 0; i < Weapon.length; i++) { // 무기
						if (!grant(tx, rewards, Weapon[i], 1, 9, 129, 3, true)) return;
					}
					for (int i = 0; i < Armor.length; i++) { // 방어구
						if (!grant(tx, rewards, Armor[i], 1, ArmorEnchant[i], 129, 0, true)) return;
					}
					for (int i = 0; i < Accessory.length; i++) { // 악세사리
						if (!grant(tx, rewards, Accessory[i], 1, AccessoryEnchant[i], 129, 0, true)) return;
					}
					for (int i = 0; i < SpellBook.length; i++) { // 법서
						if (!grant(tx, rewards, SpellBook[i], 1, 0, 1, 0, false)) return;
					}
					if (!grant(tx, rewards, MagicDoll, 1, 0, 129, 0, false)) return;
					if (!grant(tx, rewards, 30072, 200, 0, 129, 0, false)) return; // 단테스의
					// 유물
					// 주머니
					if (!grant(tx, rewards, 40308, 2000000, 0, 1, 0, false)) return; // 아데나

            tx.onFailure(() -> SC_SystemMessage.send(pc, "장비 지급함 저장에 실패했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, source);
                RuntimeException firstNotificationFailure = null;
                for (int i = 0; i < rewards.size(); i++) {
                    final L1ItemInstance reward = rewards.get(i);
                    try {
                        if (pc.getInventory().GetByServerID(reward.getId()) != null)
                            SC_ServerMessage.send(pc, 403, reward.getLogName());
                    } catch (RuntimeException notificationFailure) {
                        // A disconnected client cannot undo a committed reward.
                        // Still attempt the remaining rewards' notifications.
                        if (firstNotificationFailure == null)
                            firstNotificationFailure = notificationFailure;
                    }
                }
                if (firstNotificationFailure != null)
                    LOG.log(Level.WARNING, "Starter kit notification failed after commit",
                            firstNotificationFailure);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Starter kit transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "장비 지급함을 처리하지 못했습니다.");
        }
    }
}
