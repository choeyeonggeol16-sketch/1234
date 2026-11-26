package l1j.server.server.model.item;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Blessing and quality values used by equipment effects and item tooltips. */
public final class EquipmentBlessing {
    private static final int QUALITY_UNITS_PER_PERCENT = 10;
    private static final int QUALITY_UNITS_PER_WHOLE = 100 * QUALITY_UNITS_PER_PERCENT;

    private record ArmorOption(ItemStat stat, int amount, String label) {
        private String description() {
            final String prefix = stat == ItemStat.MAX_HP ? "" : "+";
            return "\\aL" + label + " : " + prefix + Math.abs(amount);
        }
    }

    private static final ArmorOption[] ARMOR_OPTIONS = {
        null,
        new ArmorOption(ItemStat.MAX_HP, 30, "최대HP"),
        new ArmorOption(ItemStat.MAX_HP, 20, "최대HP"),
        new ArmorOption(ItemStat.MAX_HP, 10, "최대HP"),
        new ArmorOption(ItemStat.MAX_HP, 30, "최대HP"),
        new ArmorOption(ItemStat.MAX_HP, 40, "최대HP"),
        new ArmorOption(ItemStat.MAX_HP, 50, "최대HP"),
        new ArmorOption(ItemStat.DMG_RED, 1, "리덕션"),
        new ArmorOption(ItemStat.DMG_RED, 2, "리덕션"),
        null,
        new ArmorOption(ItemStat.ABILITY_PLERCE, 1, "기술적중"),
        new ArmorOption(ItemStat.FEAR_PLERCE, 1, "공포적중"),
        new ArmorOption(ItemStat.DRAGON_SPELL_PLERCE, 1, "용언적중"),
        new ArmorOption(ItemStat.SPIRIT_PLERCE, 1, "정령적중"),
        new ArmorOption(ItemStat.MAGIC_HIT, 1, "마법적중"),
        new ArmorOption(ItemStat.ABILITY_RESIS, 1, "기술내성"),
        new ArmorOption(ItemStat.FEAR_RESIS, 1, "공포내성"),
        new ArmorOption(ItemStat.DRAGON_SPELL_RESIS, 1, "용언내성"),
        new ArmorOption(ItemStat.SPIRIT_RESIS, 1, "정령내성"),
        new ArmorOption(ItemStat.DMG_RATE, 1, "근거리대미지"),
        new ArmorOption(ItemStat.BOW_DMG, 1, "원거리대미지"),
        new ArmorOption(ItemStat.HIT, 1, "근거리명중"),
        new ArmorOption(ItemStat.BOW_HIT, 1, "원거리명중"),
        new ArmorOption(ItemStat.DMG_RED, 1, "리덕션"),
        new ArmorOption(ItemStat.AC, -1, "추가방어력"),
        new ArmorOption(ItemStat.MR, 3, "MR"),
        new ArmorOption(ItemStat.SP, 1, "SP")
    };

    private EquipmentBlessing() {}

    private static ArmorOption armorOption(int level) {
        return level > 0 && level < ARMOR_OPTIONS.length ? ARMOR_OPTIONS[level] : null;
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
