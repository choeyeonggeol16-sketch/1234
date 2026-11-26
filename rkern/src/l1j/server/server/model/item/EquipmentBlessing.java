package l1j.server.server.model.item;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Blessing and quality values used by equipment effects and item tooltips. */
public final class EquipmentBlessing {
    private static final int QUALITY_UNITS_PER_PERCENT = 10;
    private static final int QUALITY_UNITS_PER_WHOLE = 100 * QUALITY_UNITS_PER_PERCENT;

    private record ArmorOption(ItemStat stat, int amount, String label) {
        private String description() {
            final int value = stat == ItemStat.AC ? -amount : amount;
            final String prefix = stat == ItemStat.MAX_HP || value < 0 ? "" : "+";
            return "\\aL" + label + " : " + prefix + value;
        }
    }

    private static final TIntObjectHashMap<ArmorOption> ARMOR_OPTIONS = ItemScript.load(
            "equipment-blessing.txt", "rkern.equipment.blessing", (source, reader) -> {
                final TIntObjectHashMap<ArmorOption> result = new TIntObjectHashMap<>();
                ItemScript.readRows(source, reader, row -> {
                    ItemScript.requireFields(row, 4);
                    final int level = Integer.parseInt(row[0]);
                    final int amount = Integer.parseInt(row[2]);
                    if (level <= 0 || amount == Integer.MIN_VALUE || row[3].isBlank()) {
                        throw new IllegalArgumentException("Invalid armor blessing level " + level);
                    }
                    final ArmorOption option = new ArmorOption(ItemStat.valueOf(row[1]), amount, row[3]);
                    if (result.putIfAbsent(level, option) != null) {
                        throw new IllegalArgumentException("Duplicate armor blessing level " + level);
                    }
                });
                return result;
            });

    public static void initialize() {
        // Compile the blessing options at startup.
    }

    private EquipmentBlessing() {}

    private static ArmorOption armorOption(int level) {
        return ARMOR_OPTIONS.get(level);
    }

    public static boolean applyArmor(L1PcInstance pc, L1ItemInstance item, int sign) {
        final int quality = item.get_bless_level2();
        if (quality > 0) ItemStat.MAX_HP.apply(pc, sign * quality);
        final ArmorOption option = armorOption(item.get_bless_level());
        if (option == null) return false;
        final ItemStat stat = option.stat();
        stat.apply(pc, sign * option.amount());
        return stat == ItemStat.SP || stat == ItemStat.MR;
    }

    private static boolean grantsWeaponSp(int type) {
        return type == 7 || type == 16 || type == 17;
    }

    public static int weaponSp(L1ItemInstance weapon) {
        return grantsWeaponSp(weapon.getItem().getType()) ? weapon.get_bless_level() : 0;
    }

    public static int weaponDamage(L1ItemInstance weapon) {
        return grantsWeaponSp(weapon.getItem().getType()) ? 0 : weapon.get_bless_level();
    }

    public static int applyWeaponQuality(int damage, int quality) {
        if (quality <= 0 || damage <= 0) return damage;
        final long bonus = (long) damage * quality / QUALITY_UNITS_PER_WHOLE;
        return (int) Math.min(Integer.MAX_VALUE, damage + bonus);
    }

    public static String blessingDescription(L1ItemInstance item) {
        final int level = item.get_bless_level();
        if (level == 0) return null;
        return switch (item.getItem().getType2()) {
            case 1 -> "축복" + level + "단: " + (grantsWeaponSp(item.getItem().getType()) ? "SP" : "DMG") + " +" + level;
            case 2 -> {
                final ArmorOption option = armorOption(level);
                yield option == null ? null : option.description();
            }
            default -> null;
        };
    }

    public static String qualityEffectDescription(L1ItemInstance item) {
        final int quality = item.get_bless_level2();
        return item.getItem().getType2() == 1
                ? "추댐: " + ((double) quality / QUALITY_UNITS_PER_PERCENT) + "%"
                : "추가HP: " + quality;
    }
}
