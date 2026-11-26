package l1j.server.server.model.item.assembly;

import java.io.IOException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.item.EquipmentBlessing;
import l1j.server.server.shared.stream.PresentationOutputStream;
import l1j.server.server.templates.L1Item;

/** Wire layout for weapons with custom option text. */
final class CCustomWeaponOptions {
    private CCustomWeaponOptions() {}

    static byte[] assemble(L1ItemInstance item) throws IOException {
        final PresentationOutputStream out = switch (item.getItemId()) {
            case 123777 -> assembleMelee(item, 40, 5, 22);
            case 123888 -> assembleMelee(item, 44, 5, 18);
            case 123666 -> assembleMagic(item, 25, 5, 22, true);
            case 123800 -> assembleRanged(item);
            case 123900 -> assembleDrainWeapon(item);
            case 123555 -> assembleMelee(item, 44, 5, 20);
            case 123444 -> assembleMelee(item, 36, 5, 21);
            case 123333 -> assembleMagic(item, 44, 2, 0, false);
            case 124111 -> assembleMelee(item, 41, 5, 19);
            case 123999 -> assembleReaperSword(item);
            default -> null;
        };
        if (out == null) return null;

        final String blessing = EquipmentBlessing.blessingDescription(item);
        if (blessing != null) out.write_text(blessing);
        final int quality = item.get_bless_level2();
        if (quality != 0) {
            out.write_text("품질: " + quality);
            out.write_text(EquipmentBlessing.qualityEffectDescription(item));
        }
        return out.toArray();
    }

    private static PresentationOutputStream assembleMelee(L1ItemInstance item, int damage,
            int enchantDamage, int hit) throws IOException {
        final L1Item template = item.getItem();
        final int enchant = item.getEnchantLevel();
        return weaponInfo(item)
                .write_str(template.get_addstr())
                .write_time(item)
                .write_text(damageAndHit(damage, enchantDamage, enchant, hit + enchant + enchant / 2))
                .write_text(criticalText(7, enchant))
                .write_weapon_level_options(item)
                .write_magic_name(item, template.getMagicName());
    }

    private static PresentationOutputStream assembleMagic(L1ItemInstance item, int damage,
            int enchantDamage, int hit, boolean addEnchantHit) throws IOException {
        final L1Item template = item.getItem();
        final int enchant = item.getEnchantLevel();
        final int totalHit = hit + (addEnchantHit ? enchant : 0) + enchant / 2;
        return weaponInfo(item)
                .write_str(template.get_addstr())
                .write_int(template.get_addint())
                .write_wis(template.get_addwis())
                .write_sp(template.get_addsp())
                .write_time(item)
                .write_text(damageAndHit(damage, enchantDamage, enchant, totalHit))
                .write_text(criticalText(7, enchant))
                .write_weapon_level_options(item)
                .write_magic_name(item, template.getMagicName());
    }

    private static PresentationOutputStream assembleRanged(L1ItemInstance item) throws IOException {
        final L1Item template = item.getItem();
        final int enchant = item.getEnchantLevel();
        return PresentationOutputStream.newInstance(256)
                .write_weapon_info1(template, item.getWeight())
                .write_weapon_etc_info1(template, item.get_durability())
                .write_dex(template.get_adddex())
                .write_time(item)
                .write_text(damageAndHit(39, 5, enchant, 18 + enchant + enchant / 2))
                .write_text(criticalText(10, enchant))
                .write_magic_name(item, template.getMagicName());
    }

    private static PresentationOutputStream assembleDrainWeapon(L1ItemInstance item) throws IOException {
        final int enchant = item.getEnchantLevel();
        return weaponInfo(item)
                .write_drain_hp()
                .write_time(item)
                .write_text(damageAndHit(36, 5, enchant, 20 + enchant + enchant / 2))
                .write_text(criticalText(7, enchant))
                .write_weapon_level_options(item)
                .write_magic_name(item, item.getItem().getMagicName());
    }

    private static PresentationOutputStream assembleReaperSword(L1ItemInstance item) throws IOException {
        final L1Item template = item.getItem();
        final int enchant = item.getEnchantLevel();
        return PresentationOutputStream.newInstance(256)
                .write_weapon_info(template, item.getWeight())
                .write_weapon_add_damage(enchant * 2, enchant * 2)
                .write_weapon_etc_info(template, item.get_durability())
                .write_str(template.get_addstr())
                .write_con(template.get_addcon())
                .write_short_hit(template.getHitModifier() + enchant / 2)
                .write_addsub_short_damage(template.getDmgModifier())
                .write_short_critical(template.get_melee_critical_probability() + enchant)
                .writeFearPierce(3 + enchant)
                .writeAbilityPierce(3 + enchant)
                .writeSpiritPierce(3 + enchant)
                .write_time(item)
                .write_weapon_level_options(item)
                .write_magic_name(item, template.getMagicName());
    }

    private static PresentationOutputStream weaponInfo(L1ItemInstance item) throws IOException {
        final L1Item template = item.getItem();
        return PresentationOutputStream.newInstance(256)
                .write_weapon_info(template, item.getWeight())
                .write_weapon_etc_info(template, item.get_durability());
    }

    private static String damageAndHit(int damage, int enchantDamage, int enchant, int hit) {
        final String damageText = enchant > 0 ? damage + "+" + (enchant * enchantDamage) : "+" + damage;
        return "대미지:" + damageText + "  명중:+" + hit;
    }

    private static String criticalText(int baseCritical, int enchant) {
        return "치명타:+" + (baseCritical + enchant * 2) + "%  이뮨 무시:" + (20 + enchant * 2) + "%";
    }
}
