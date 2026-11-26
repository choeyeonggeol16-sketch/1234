package l1j.server.server.model.inventory;

import static l1j.server.server.model.item.ItemStat.*;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.templates.L1Item;

/** Lineage 1 equipment options shared by equip, unequip, and enchant changes. */
public final class EquipmentEnchantOptions {
    private static final ItemStat[] STATS = ItemStat.values();

    private EquipmentEnchantOptions() {}

    public static void loadDefinitions() {
        try {
            EquipmentOptionTable.initialize();
            EquipmentTraits.initialize();
        } catch (ExceptionInInitializerError failure) {
            final Throwable cause = failure.getCause();
            throw new IllegalStateException("Unable to load equipment options: " + cause.getMessage(), cause);
        }
        NOTICE_LOG.info("Loaded equipment options from " + EquipmentOptionScript.source());
    }

    private static final java.util.logging.Logger NOTICE_LOG =
            java.util.logging.Logger.getLogger(EquipmentEnchantOptions.class.getName());

    private static void noticeFailed(L1PcInstance pc, RuntimeException notice) {
        NOTICE_LOG.log(java.util.logging.Level.WARNING,
                "Equipment option notice failed: " + pc.getId(), notice);
    }

    static int[] enchantDelta(L1PcInstance pc, L1Item item, int previousEnchant, int enchant) {
        final int[] changes = new int[STATS.length];
        collect(pc, item, previousEnchant, -1, changes);
        collect(pc, item, enchant, 1, changes);
        return changes;
    }

    public static void collectTooltipModifiers(L1ItemInstance item, L1PcInstance owner, int[] totals) {
        final L1Item template = item.getItem();
        collect(null, template, item.getEnchantLevel(), 1, totals);
        if (template.getType2() == 2) CUserEquipment.collectArmorTooltipModifiers(item, owner, totals);
    }

    private static void collect(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        if (item.getType2() == 1) {
            applyWeaponOptions(pc, item, enchant, sign, changes);
        } else if (item.getType2() == 2) {
            applyArmorPrimaryOptions(pc, item, enchant, sign, changes);
            EquipmentOptionTable.ARMOR_DODGE.apply(pc, item, enchant, sign, changes);
            EquipmentOptionTable.ARMOR_CASTING.apply(pc, item, enchant, sign, changes);
            applyArmorSlotOptions(pc, item, enchant, sign, changes);
        }
    }

    static void applyWeaponOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        EquipmentOptionTable.WEAPON.apply(pc, item, enchant, sign, changes);
    }

    static void applyArmorPrimaryOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        EquipmentOptionTable.ARMOR_PRIMARY.apply(pc, item, enchant, sign, changes);
    }

    static void applyArmorSlotOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        EquipmentOptionTable.ARMOR_SLOT.apply(pc, item, enchant, sign, changes);
        applyAccessoryOptions(pc, item, enchant, sign, changes);
    }

    private static void applyAccessoryOptions(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        EquipmentOptionTable.ACCESSORY.applyAccessory(pc, item, enchant, sign, changes);
    }

    static void applyStat(L1PcInstance pc, int[] changes, ItemStat stat, int value) {
        if (changes == null) stat.apply(pc, value);
        else changes[stat.ordinal()] += value;
    }

    static boolean applyDelta(L1PcInstance pc, int[] changes) {
        for (int i = 0; i < changes.length; i++) {
            if (changes[i] != 0) STATS[i].apply(pc, changes[i]);
        }
        if (changes[EVASION.ordinal()] != 0) sendEvasion(pc);
        if (changes[AC.ordinal()] != 0) sendArmorClass(pc);
        return changes[MR.ordinal()] != 0 || changes[SP.ordinal()] != 0;
    }

    static int accessoryHeal(L1Item item, int enchant) {
        final int type = item.getType();
        if (type != 8 && type != 12) return 0;
        final int grade = item.getGrade();
        if (grade < 0 || grade > 2) return 0;
        final int amount = switch (enchant) {
            case 5 -> 3;
            case 6 -> 5;
            case 7 -> 7;
            case 8 -> 9;
            case 9 -> 10;
            case 10 -> 11;
            default -> 0;
        };
        return type == 12 && amount > 0 ? amount - 1 : amount;
    }

    static void sendSpMr(L1PcInstance pc) {
        try { SC_SPMR.send(pc); }
        catch (RuntimeException notice) { noticeFailed(pc, notice); }
    }

    static void sendEvasion(L1PcInstance pc) {
        try { SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER()); }
        catch (RuntimeException notice) { noticeFailed(pc, notice); }
    }

    static void sendArmorClass(L1PcInstance pc) {
        try { SC_OwnCharAttrDef.send(pc); }
        catch (RuntimeException notice) { noticeFailed(pc, notice); }
    }
}
