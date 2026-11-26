package l1j.server.server.model.item.assembly;

import java.util.Arrays;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.inventory.EquipmentEnchantOptions;
import l1j.server.server.model.inventory.EquipmentTraits;
import l1j.server.server.model.item.EquipmentBlessing;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.eKind;

/** Emits a single option record for the stats the equipment definitions supply. */
final class EquipmentOptionPresentation {
    private enum Line {
        HP(14, 2, ItemStat.MAX_HP),
        MR(15, 2, ItemStat.MR),
        SP(17, 1, ItemStat.SP),
        BOW_HIT(24, 1, ItemStat.BOW_HIT),
        MP(32, 2, ItemStat.MAX_MP),
        BOW_DAMAGE(35, 1, ItemStat.BOW_DMG, ItemStat.BOW_DAMAGE_UP),
        MPR(38, 1, ItemStat.MPR),
        MAGIC_HIT(40, 1, ItemStat.MAGIC_HIT),
        MELEE_DAMAGE(47, 1, ItemStat.DMG_RATE, ItemStat.MELEE_DAMAGE_UP),
        HIT(48, 1, ItemStat.HIT),
        MAGIC_CRITICAL(50, 2, ItemStat.MAGIC_CRITICAL_RATE),
        PVP_DAMAGE(59, 1, ItemStat.PVP_DMG),
        PVP_REDUCTION(60, 1, ItemStat.PVP_DMG_RED),
        REDUCTION(63, 1, ItemStat.DMG_RED),
        CHARISMA(13, 1, ItemStat.CHARISMA),
        MAGIC_DODGE(89, 4, ItemStat.MAGIC_DODGE),
        WEIGHT(90, 2, ItemStat.WEIGHT),
        REDUCTION_IGNORE(97, 1, ItemStat.DMG_RED_IGNORE),
        MELEE_CRITICAL(100, 1, ItemStat.MELEE_CRITICAL_RATE),
        BOW_CRITICAL(99, 1, ItemStat.BOW_CRITICAL_RATE),
        BLESS_EFFICIENCY(116, 2, ItemStat.BLESS_CONSUMO),
        ABILITY_RESIST(117, 1, ItemStat.ABILITY_RESIS),
        SPIRIT_RESIST(118, 1, ItemStat.SPIRIT_RESIS),
        DRAGON_RESIST(119, 1, ItemStat.DRAGON_SPELL_RESIS),
        FEAR_RESIST(120, 1, ItemStat.FEAR_RESIS),
        ALL_RESIST(121, 1, ItemStat.ALL_RESIS),
        ABILITY_PIERCE(122, 1, ItemStat.ABILITY_PLERCE),
        SPIRIT_PIERCE(123, 1, ItemStat.SPIRIT_PLERCE),
        DRAGON_PIERCE(124, 1, ItemStat.DRAGON_SPELL_PLERCE),
        FEAR_PIERCE(125, 1, ItemStat.FEAR_PLERCE),
        ALL_PIERCE(126, 1, ItemStat.ALL_PLERCE);

        final int opcode;
        final int width;
        final ItemStat[] sources;

        Line(int opcode, int width, ItemStat... sources) {
            this.opcode = opcode;
            this.width = width;
            this.sources = sources;
        }

        int base(L1ItemInstance item, L1Item template) {
            return switch (this) {
                case HP -> template.get_addhp();
                case MP -> template.get_addmp();
                case MR -> item.getMr() + (item._cha != null && item._cha.isElf()
                        ? EquipmentTraits.forItem(item.getItemId()).elfMr() : 0);
                case SP -> template.get_addsp() + (template.getType2() == 1 ? EquipmentBlessing.weaponSp(item) : 0);
                case MPR -> template.get_addmpr();
                case HIT -> template.getHitRate() + (isMeleeWeapon(template) ? template.getHitModifier() : 0);
                case BOW_HIT -> template.getBowHitRate() + (isRangedWeapon(template) ? template.getHitModifier() : 0);
                case MELEE_DAMAGE -> template.getDmgRate() + (!isRangedWeapon(template) ? template.getDmgModifier() : 0);
                case BOW_DAMAGE -> template.getBowDmgRate() + (isRangedWeapon(template) ? template.getDmgModifier() : 0);
                case REDUCTION -> template.get_damage_reduction();
                case WEIGHT -> template.getWeightReduction();
                case MAGIC_DODGE, REDUCTION_IGNORE -> 0;
                case MAGIC_HIT -> template.getMagicHitup();
                case MAGIC_CRITICAL -> 0;
                case MELEE_CRITICAL -> template.get_melee_critical_probability();
                case BOW_CRITICAL -> template.get_missile_critical_probability();
                case PVP_DAMAGE -> template.get_regist_PVPweaponTotalDamage();
                case PVP_REDUCTION -> template.get_regist_calcPcDefense();
                case CHARISMA -> template.get_addcha();
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
    }

    private static final Line[] LINES = Line.values();
    private static final int ABSENT = Integer.MIN_VALUE;
    private static final int STAT_COUNT = ItemStat.values().length;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final int[] bonuses = new int[STAT_COUNT + (STAT_COUNT + 31) / 32];
        final int[] totals = new int[127];
        boolean inUse;
    }

    private EquipmentOptionPresentation() {}

    static void write(L1ItemInstance item, ItemPresentOutStream out, int type, int id, int grade) throws Exception {
        final Scratch cached = SCRATCH.get();
        final Scratch scratch = cached.inUse ? new Scratch() : cached;
        scratch.inUse = true;
        try {
            final L1Item template = item.getItem();
            final int enchant = item.getEnchantLevel();
            Arrays.fill(scratch.bonuses, 0);
            Arrays.fill(scratch.totals, ABSENT);
            EquipmentEnchantOptions.collectTooltipModifiers(template, enchant, scratch.bonuses);
            final ItemEffectRegistry.Def registry = ItemEffectRegistry.get(id);
            final int playerClass = item._cha == null ? -1 : item._cha.getType();
            for (Line line : LINES) {
                int additional = 0;
                boolean defined = false;
                for (ItemStat stat : line.sources) {
                    final int ordinal = stat.ordinal();
                    final int registered = registry == null ? 0 : registry.statValue(stat, playerClass, enchant);
                    additional += scratch.bonuses[ordinal] + registered;
                    defined |= registered != 0 || (scratch.bonuses[STAT_COUNT + (ordinal >>> 5)]
                            & (1 << (ordinal & 31))) != 0;
                }
                if (!defined) continue;
                scratch.totals[line.opcode] = line.base(item, template) + additional;
            }
            if (type == 2 && scratch.totals[Line.MELEE_DAMAGE.opcode] != ABSENT) {
                // Armor and snapper effects historically also used the generic damage record.
                scratch.totals[6] = scratch.totals[Line.MELEE_DAMAGE.opcode];
            }
            out.replaceEquipmentOptions(scratch.totals);
            CItemEnchantOption.AssembleEquipmentItem(item, out, type, id, grade, scratch.totals);
        } finally {
            out.replaceEquipmentOptions(null);
            scratch.inUse = false;
        }
    }

    static void writeTotals(ItemPresentOutStream out, int[] totals, boolean snapper) {
        out.replaceEquipmentOptions(null);
        for (Line line : LINES) {
            final int total = totals[line.opcode];
            if (total <= 0) continue;
            final int opcode = snapper && line == Line.MELEE_DAMAGE ? 6 : line.opcode;
            final int limit = switch (line.width) {
                case 1 -> 255;
                case 2 -> 65535;
                default -> Integer.MAX_VALUE;
            };
            out.writeNumericOption(opcode, Math.min(total, limit), line.width);
        }
    }

    private static boolean isRangedWeapon(L1Item item) {
        return item.getType2() == 1 && item.getType1() == 20;
    }

    private static boolean isMeleeWeapon(L1Item item) {
        return item.getType2() == 1 && !isRangedWeapon(item);
    }
}
