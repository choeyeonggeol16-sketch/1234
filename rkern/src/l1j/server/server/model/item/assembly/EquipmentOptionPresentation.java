package l1j.server.server.model.item.assembly;

import java.util.Arrays;

import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CSetItem;
import l1j.server.server.model.inventory.EquipmentEnchantOptions;
import l1j.server.server.model.inventory.EquipmentTraits;
import l1j.server.server.model.inventory.CUserEquipment;
import l1j.server.server.model.item.EquipmentBlessing;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.eKind;

/** Builds weapon, armor and accessory tooltips from the equipped stat definitions. */
final class EquipmentOptionPresentation {
    private enum Line {
        STR(8, 1, "STR"),
        DEX(9, 1, "DEX"),
        CON(10, 1, "CON"),
        WIS(11, 1, "WIS"),
        INT(12, 1, "INT"),
        CHARISMA(13, 1, "CHA", ItemStat.CHARISMA),
        HP(14, 2, "최대 HP", ItemStat.MAX_HP),
        MR(15, 2, "MR", ItemStat.MR),
        SP(17, 1, "SP", ItemStat.SP),
        BOW_HIT(24, 1, "원거리 명중", ItemStat.BOW_HIT),
        FIRE(27, 1, "불 저항"),
        WATER(28, 1, "물 저항"),
        WIND(29, 1, "바람 저항"),
        EARTH(30, 1, "땅 저항"),
        MP(32, 2, "최대 MP", ItemStat.MAX_MP),
        BOW_DAMAGE(35, 1, "원거리 대미지", ItemStat.BOW_DMG, ItemStat.BOW_DAMAGE_UP),
        EXP(36, 1, "경험치 증가"),
        HPR(37, 1, "HP 회복"),
        MPR(38, 1, "MP 회복", ItemStat.MPR),
        MAGIC_HIT(40, 1, "마법 명중", ItemStat.MAGIC_HIT),
        MELEE_DAMAGE(47, 1, "근거리 대미지", ItemStat.DMG_RATE, ItemStat.MELEE_DAMAGE_UP),
        HIT(48, 1, "근거리 명중", ItemStat.HIT),
        MAGIC_CRITICAL(50, 2, "마법 치명타", ItemStat.MAGIC_CRITICAL_RATE),
        PVP_DAMAGE(59, 1, "PVP 추가 대미지", ItemStat.PVP_DMG),
        PVP_REDUCTION(60, 1, "PVP 대미지 감소", ItemStat.PVP_DMG_RED),
        REDUCTION(63, 1, "대미지 감소", ItemStat.DMG_RED),
        MAGIC_DODGE(89, 4, "마법 회피", ItemStat.MAGIC_DODGE),
        WEIGHT(90, 2, "소지 무게 증가", ItemStat.WEIGHT),
        REDUCTION_IGNORE(97, 1, "대미지 감소 무시", ItemStat.DMG_RED_IGNORE),
        BOW_CRITICAL(99, 1, "원거리 치명타", ItemStat.BOW_CRITICAL_RATE),
        MELEE_CRITICAL(100, 1, "근거리 치명타", ItemStat.MELEE_CRITICAL_RATE),
        BLESS_EFFICIENCY(116, 2, "축복 소모 효율", ItemStat.BLESS_CONSUMO),
        ABILITY_RESIST(117, 1, "기술 내성", ItemStat.ABILITY_RESIS),
        SPIRIT_RESIST(118, 1, "정령 내성", ItemStat.SPIRIT_RESIS),
        DRAGON_RESIST(119, 1, "용언 내성", ItemStat.DRAGON_SPELL_RESIS),
        FEAR_RESIST(120, 1, "공포 내성", ItemStat.FEAR_RESIS),
        ALL_RESIST(121, 1, "전체 내성", ItemStat.ALL_RESIS),
        ABILITY_PIERCE(122, 1, "기술 적중", ItemStat.ABILITY_PLERCE),
        SPIRIT_PIERCE(123, 1, "정령 적중", ItemStat.SPIRIT_PLERCE),
        DRAGON_PIERCE(124, 1, "용언 적중", ItemStat.DRAGON_SPELL_PLERCE),
        FEAR_PIERCE(125, 1, "공포 적중", ItemStat.FEAR_PLERCE),
        ALL_PIERCE(126, 1, "전체 적중", ItemStat.ALL_PLERCE);

        final int opcode;
        final int width;
        final String label;
        final ItemStat[] bonuses;

        Line(int opcode, int width, String label, ItemStat... bonuses) {
            this.opcode = opcode;
            this.width = width;
            this.label = label;
            this.bonuses = bonuses;
        }

        int base(L1ItemInstance item, L1PcInstance owner, boolean ranged, long weaponBonus) {
            final L1Item template = item.getItem();
            return switch (this) {
                case STR -> template.get_addstr();
                case DEX -> template.get_adddex();
                case CON -> template.get_addcon();
                case WIS -> template.get_addwis();
                case INT -> template.get_addint();
                case CHARISMA -> template.get_addcha();
                case HP -> template.get_addhp();
                case MP -> template.get_addmp();
                case MR -> item.getMr() + (owner != null && owner.isElf()
                        ? EquipmentTraits.forItem(item.getItemId()).elfMr() : 0);
                case SP -> template.get_addsp();
                case HPR -> template.get_addhpr();
                case MPR -> template.get_addmpr();
                case HIT -> template.getHitRate() + (!ranged ? (int) (weaponBonus >> 32) : 0);
                case BOW_HIT -> template.getBowHitRate() + (ranged ? (int) (weaponBonus >> 32) : 0);
                case MELEE_DAMAGE -> template.getDmgRate() + (!ranged ? (int) weaponBonus : 0);
                case BOW_DAMAGE -> template.getBowDmgRate() + (ranged ? (int) weaponBonus : 0);
                case FIRE -> template.get_defense_fire();
                case WATER -> template.get_defense_water();
                case WIND -> template.get_defense_wind();
                case EARTH -> template.get_defense_earth();
                case EXP -> ItemBonusHooks.equipmentExperiencePercent(item);
                case REDUCTION -> template.get_damage_reduction();
                case WEIGHT -> template.getWeightReduction();
                case MAGIC_HIT -> template.getMagicHitup();
                case MAGIC_CRITICAL, MAGIC_DODGE, REDUCTION_IGNORE -> 0;
                case MELEE_CRITICAL -> template.get_melee_critical_probability();
                case BOW_CRITICAL -> template.get_missile_critical_probability();
                case PVP_DAMAGE -> template.get_regist_PVPweaponTotalDamage();
                case PVP_REDUCTION -> template.get_regist_calcPcDefense();
                case BLESS_EFFICIENCY -> template.get_addeinhasadper();
                case ABILITY_RESIST -> template.getSpecialStats().getResist(eKind.ABILITY);
                case SPIRIT_RESIST -> template.getSpecialStats().getResist(eKind.SPIRIT);
                case DRAGON_RESIST -> template.getSpecialStats().getResist(eKind.DRAGON_SPELL);
                case FEAR_RESIST -> template.getSpecialStats().getResist(eKind.FEAR);
                case ALL_RESIST -> template.getSpecialStats().getResist(eKind.ALL);
                case ABILITY_PIERCE -> template.getSpecialStats().getPierce(eKind.ABILITY);
                case SPIRIT_PIERCE -> template.getSpecialStats().getPierce(eKind.SPIRIT);
                case DRAGON_PIERCE -> template.getSpecialStats().getPierce(eKind.DRAGON_SPELL);
                case FEAR_PIERCE -> template.getSpecialStats().getPierce(eKind.FEAR);
                case ALL_PIERCE -> template.getSpecialStats().getPierce(eKind.ALL);
            };
        }

        void write(ItemPresentOutStream out, int value, boolean compact) {
            if (value == 0) return;
            final int limit = width == 1 ? 255 : width == 2 ? 65535 : Integer.MAX_VALUE;
            if (value > 0 && value <= limit) {
                out.writeNumericOption(opcode, value, width);
            } else if (!compact) {
                out.writeText(label + " " + signed(value));
            }
        }
    }

    private static final Line[] LINES = Line.values();
    private static final int STAT_COUNT = ItemStat.values().length;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final int[] bonuses = new int[STAT_COUNT];
        boolean inUse;
    }

    private EquipmentOptionPresentation() {}

    static void write(L1ItemInstance item, L1PcInstance owner, ItemPresentOutStream out, boolean compact) {
        final Scratch cached = SCRATCH.get();
        final Scratch scratch = cached.inUse ? new Scratch() : cached;
        scratch.inUse = true;
        try {
            Arrays.fill(scratch.bonuses, 0);
            writeEquipment(item, owner, out, compact, scratch.bonuses);
        } finally {
            scratch.inUse = false;
        }
    }

    private static void writeEquipment(L1ItemInstance item, L1PcInstance owner, ItemPresentOutStream out,
            boolean compact, int[] bonuses) {
        final L1Item template = item.getItem();
        final boolean weapon = template.getType2() == 1;
        final int enchant = item.getEnchantLevel();
        EquipmentEnchantOptions.collectTooltipModifiers(item, owner, bonuses);
        final ItemEffectRegistry.Def registered = ItemEffectRegistry.get(item.getItemId());
        if (registered != null) {
            registered.collectModifiers(owner == null ? -1 : owner.getType(), enchant, bonuses);
        }

        if (!compact) out.writeText("\\aG[안전인챈:+" + Math.max(0, template.get_safeenchant()) + "]");
        writeHeader(item, out, weapon, enchant, bonuses[ItemStat.AC.ordinal()], compact);
        final boolean ranged = weapon && (template.getType1() == 20 || template.getType1() == 62);
        final long weaponBonus = weapon ? WeaponAddDamage.getInstance().getTemplateStatsPacked(template) : 0;
        for (Line line : LINES) {
            int value = line.base(item, owner, ranged, weaponBonus);
            for (ItemStat stat : line.bonuses) value += bonuses[stat.ordinal()];
            line.write(out, value, compact);
        }
        final int potion = bonuses[ItemStat.POTION_AMT.ordinal()];
        out.writePotionrecovery(potion, potion);
        out.writeHealDefence(bonuses[ItemStat.HEAL_DEF.ordinal()]);
        out.writeUseLevel(template.getMinLevel());
        if (template.getMaxLevel() > 0) out.writeLimitLevel(template.getMinLevel(), template.getMaxLevel());
        if (template.isHasteItem()) out.writeHaste();
        if (!compact) {
            writeDetails(item, out, registered, bonuses);
            CSetItem.writeTooltip(item, out);
        }
    }

    private static void writeHeader(L1ItemInstance item, ItemPresentOutStream out, boolean weapon,
            int enchant, int bonusAc, boolean compact) {
        final L1Item template = item.getItem();
        if (weapon) {
            out.writeDMG(template.getDmgSmall(), template.getDmgLarge());
        } else {
            out.writeC(19);
            out.writeC(-CUserEquipment.armorDefense(item, 0));
        }
        out.writeC(template.getMaterial());
        if (!weapon) out.writeH(-1);
        out.writeD(item.getWeight());

        final int actualAc = weapon ? 0 : CUserEquipment.armorDefense(item, 0)
                - CUserEquipment.armorDefense(item, enchant);
        final int defenseBonus = actualAc - bonusAc;
        if (weapon && enchant != 0) out.writeNumericOption(2, enchant, 1);
        if (defenseBonus > 0) out.writeAddAc(defenseBonus);
        else if (defenseBonus < 0 && !compact) out.writeText("추가 방어력 " + signed(defenseBonus));
        if (template.isTwohandedWeapon()) out.writeTwoHand();
        if (item.get_durability() != 0) out.writeDurability(item.get_durability());
        out.writeClass(classMask(template));
        out.writeC(130);
        out.writeD(template.isTradable() ? 7 : 6);
        if (item.isCanbeDmg()) {
            out.writeC(131);
            out.writeD(1);
        }
        out.writeC(132);
        out.writeD(3);
        if (weapon && item.isUndeadDmg()) {
            out.writeC(114);
            out.writeD(1);
        }
    }

    private static int classMask(L1Item item) {
        return (item.isUseRoyal() ? 1 : 0)
                | (item.isUseKnight() ? 2 : 0)
                | (item.isUseElf() ? 4 : 0)
                | (item.isUseMage() ? 8 : 0)
                | (item.isUseDarkelf() ? 16 : 0)
                | (item.isUseDragonKnight() ? 32 : 0)
                | (item.isUseBlackwizard() ? 64 : 0)
                | (item.isUse전사() ? 128 : 0);
    }

    private static void writeDetails(L1ItemInstance item, ItemPresentOutStream out,
            ItemEffectRegistry.Def registered, int[] bonuses) {
        out.writeMagic2(item.getItem().getMagicName());
        if (registered != null) {
            out.writeText(registered.getTooltipText());
            final String magic = registered.getMagicname();
            if (magic != null && !magic.equals(item.getItem().getMagicName())) out.writeMagic2(magic);
        }
        writeStatText(out, "기본 마법 치명타", bonuses[ItemStat.BASE_MAGIC_CRITICAL.ordinal()], "");
        writeStatText(out, "PVP 마법 대미지 감소", bonuses[ItemStat.PVP_MAGIC_DMG_RED.ordinal()], "%");
        writeStatText(out, "ER", bonuses[ItemStat.EVASION.ordinal()], "");
        out.writeText(EquipmentBlessing.blessingDescription(item));
        final int quality = item.get_bless_level2();
        if (quality != 0) {
            out.writeText("품질: " + quality);
            out.writeText(EquipmentBlessing.qualityEffectDescription(item));
        }
    }

    private static void writeStatText(ItemPresentOutStream out, String label, int value, String suffix) {
        if (value != 0) out.writeText(label + " " + signed(value) + suffix);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }
}
