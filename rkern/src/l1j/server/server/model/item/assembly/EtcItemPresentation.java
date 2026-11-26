package l1j.server.server.model.item.assembly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.Attack.Helper.L1WeaponAttrEnchantHelper;
import l1j.server.server.model.item.ItemScript;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.function.CashScrollPotionEffects;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.ItemPresentOutStream;

/** Renders ETC item metadata and the effects shared with consumable handlers. */
final class EtcItemPresentation {
    private enum Phase { BEFORE_DOLL, BEFORE_POTENTIAL, DETAIL, BRIEF }

    private enum NumericOption {
        ARMOR_CLASS(ItemPresentOutStream::writeAddAc),
        DAMAGE(ItemPresentOutStream::writeAddDMG),
        EXP(ItemPresentOutStream::writeAddEXP),
        HP_RECOVERY(ItemPresentOutStream::writeAddHPPrecovery),
        MP_RECOVERY(ItemPresentOutStream::writeAddMPPrecovery),
        MAX_HP(ItemPresentOutStream::writeAddMaxHP, 65535),
        SP(ItemPresentOutStream::writeAddSP),
        CLASS_MASK(ItemPresentOutStream::writeClass),
        REDUCTION(ItemPresentOutStream::writeDMGdown),
        ALIGNMENT(ItemPresentOutStream::writeLawful),
        RANGED_DAMAGE(ItemPresentOutStream::writeLongDMG),
        RANGED_HIT(ItemPresentOutStream::writeLongHIT),
        MAGIC_HIT(ItemPresentOutStream::writeMagicHIT),
        MAX_MP(ItemPresentOutStream::writeMaxMP, 65535),
        PVP_DAMAGE(ItemPresentOutStream::writePVPAddDMG),
        PVP_REDUCTION(ItemPresentOutStream::writePVPAddDMGdown),
        MELEE_DAMAGE(ItemPresentOutStream::writeShortDMG),
        MELEE_HIT(ItemPresentOutStream::writeShortHIT),
        SKILL_STEP(ItemPresentOutStream::writeStep),
        USE_LEVEL(ItemPresentOutStream::writeUseLevel),
        CON(ItemPresentOutStream::writeaCON_Bu),
        DEX(ItemPresentOutStream::writeaDEX_Bu),
        INT(ItemPresentOutStream::writeaINT_Bu),
        STR(ItemPresentOutStream::writeaSTR_Bu),
        SPIRIT_RESIST(ItemPresentOutStream::writeaspirit_resis);

        private final ObjIntConsumer<ItemPresentOutStream> writer;
        private final int max;

        NumericOption(ObjIntConsumer<ItemPresentOutStream> writer) {
            this(writer, 255);
        }

        NumericOption(ObjIntConsumer<ItemPresentOutStream> writer, int max) {
            this.writer = writer;
            this.max = max;
        }

        private Action parse(String value) {
            final int amount = Integer.parseInt(value);
            if (amount < 0 || amount > max) {
                throw new IllegalArgumentException(name() + " must be between 0 and " + max);
            }
            return (itemId, out) -> writer.accept(out, amount);
        }
    }

    @FunctionalInterface
    private interface Action {
        void write(int itemId, ItemPresentOutStream out);
    }

    private record Rule(int classType, Action action) {}

    private static final Phase[] PHASES = Phase.values();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final TIntObjectHashMap<Rule[][]> DEFINITIONS = loadDefinitions();

    private EtcItemPresentation() {}

    static void initialize() {
        // Class initialization validates and compiles the tooltip file before game startup.
    }

    static boolean hasBrief(int itemId) {
        final Rule[][] rules = DEFINITIONS.get(itemId);
        return rules != null && rules[Phase.BRIEF.ordinal()] != null;
    }

    static void writeBrief(L1ItemInstance item, ItemPresentOutStream out) {
        writeRules(DEFINITIONS.get(item.getItemId()), Phase.BRIEF, item.getItemId(), classType(item._cha), out);
    }

    static void writeFull(L1ItemInstance item, L1PcInstance viewer, ItemPresentOutStream out) {
        final L1Item template = item.getItem();
        final int itemId = template.getItemId();
        final int classType = classType(viewer);
        final Rule[][] rules = DEFINITIONS.get(itemId);
        writeBase(item, out);
        writeRules(rules, Phase.BEFORE_DOLL, itemId, classType, out);
        DollOptionPresentation.write(out, itemId);
        writeRules(rules, Phase.BEFORE_POTENTIAL, itemId, classType, out);
        CashScrollPotionEffects.writeTooltip(itemId, out);
        DollOptionPresentation.writePotential(item, out);
        writeRules(rules, Phase.DETAIL, itemId, classType, out);
        CookingRegistry.writeItemOptions(out, itemId);

        if (template.getMinLevel() != 0) out.writeUseLevel(template.getMinLevel());
        if (template.getMaxLevel() != 0) out.writeLimitLevel(template.getMinLevel(), template.getMaxLevel());
        out.writeLongDMG(template.getDmgModifier());
        out.writeLongHIT(template.getHitModifier());
        out.writeAttrDmg(L1WeaponAttrEnchantHelper.arrowElementalBonus(itemId));
        if (item.isUndeadDmg()) {
            out.writeC(114);
            out.writeD(1);
        }
        out.writeC(130);
        out.writeD(template.isTradable() ? 7 : 6);
    }

    static void writeBase(L1ItemInstance item, ItemPresentOutStream out) {
        final L1Item template = item.getItem();
        switch (template.getType()) {
            case 2 -> out.writeLightRange(template.getLightRange());
            case 7 -> out.writeFoodVolume(template.getFoodVolume());
            case 0, 15 -> out.writeDMG(template.getDmgSmall(), template.getDmgLarge());
            default -> out.writeC(23);
        }
        out.writeC(template.getMaterial());
        out.writeD(item.getWeight());
    }

    private static int classType(L1PcInstance viewer) {
        return viewer == null ? -1 : viewer.getType();
    }

    private static void writeRules(Rule[][] rules, Phase phase, int itemId, int classType, ItemPresentOutStream out) {
        final Rule[] entries = rules == null ? null : rules[phase.ordinal()];
        if (entries == null) return;
        for (Rule entry : entries) {
            if (entry.classType == -1 || entry.classType == classType) entry.action.write(itemId, out);
        }
    }

    private static TIntObjectHashMap<Rule[][]> loadDefinitions() {
        return ItemScript.load("etc-item-tooltips.txt", "rkern.etc.tooltips", (source, reader) -> {
            final var profiles = new TIntObjectHashMap<EnumMap<Phase, List<Rule>>>();
            ItemScript.readRows(source, reader, row -> addRule(profiles, row));
            final var compiled = new TIntObjectHashMap<Rule[][]>(profiles.size());
            profiles.forEachEntry((id, phases) -> {
                final Rule[][] entries = new Rule[PHASES.length][];
                phases.forEach((phase, rules) -> entries[phase.ordinal()] = rules.toArray(Rule[]::new));
                compiled.put(id, entries);
                return true;
            });
            return compiled;
        });
    }

    private static void addRule(TIntObjectHashMap<EnumMap<Phase, List<Rule>>> profiles, String[] row) {
        if (row.length < 3) throw new IllegalArgumentException("Expected phase, item IDs and option");
        final Phase phase = Phase.valueOf(row[0]);
        final String[] selector = row[2].split("@", -1);
        if (selector.length > 2) throw new IllegalArgumentException("Invalid option selector " + row[2]);
        final int classType = selector.length == 1 ? -1 : Integer.parseInt(selector[1]);
        if (selector.length == 2 && (classType < 0 || classType > 7)) {
            throw new IllegalArgumentException("Invalid class " + classType);
        }
        final Rule rule = new Rule(classType, parseAction(selector[0], row));
        for (String entry : row[1].split(",", -1)) {
            final String[] ends = entry.split("\\.\\.", -1);
            if (ends.length > 2) throw new IllegalArgumentException("Invalid item ID range " + entry);
            final int first = Integer.parseInt(ends[0]);
            final int last = ends.length == 1 ? first : Integer.parseInt(ends[1]);
            if (first <= 0 || last < first) {
                throw new IllegalArgumentException("Invalid item ID range " + entry);
            }
            for (long id = first; id <= last; id++) {
                EnumMap<Phase, List<Rule>> phases = profiles.get((int) id);
                if (phases == null) {
                    phases = new EnumMap<>(Phase.class);
                    profiles.put((int) id, phases);
                }
                phases.computeIfAbsent(phase, unused -> new ArrayList<>()).add(rule);
            }
        }
    }

    private static Action parseAction(String option, String[] row) {
        if (option.equals("COOKING_DURATION")) {
            ItemScript.requireFields(row, 3);
            return (id, out) -> CookingRegistry.writeItemDuration(out, id);
        }
        ItemScript.requireFields(row, 4);
        if (option.equals("TEXT")) return textAction(row[3]);
        return NumericOption.valueOf(option).parse(row[3]);
    }

    private static Action textAction(String source) {
        final String text = source.replace("\\s", " ");
        if (text.isEmpty()) throw new IllegalArgumentException("Empty tooltip text");
        final var matcher = PLACEHOLDER.matcher(text);
        if (!matcher.find()) {
            if (text.contains("${")) throw new IllegalArgumentException("Invalid tooltip placeholder " + text);
            return (id, out) -> out.writeText(text);
        }

        final List<String> literals = new ArrayList<>();
        final List<Supplier<String>> values = new ArrayList<>();
        int end = 0;
        do {
            final String literal = text.substring(end, matcher.start());
            if (literal.contains("${")) throw new IllegalArgumentException("Invalid tooltip placeholder " + text);
            literals.add(literal);
            values.add(configValue(matcher.group(1)));
            end = matcher.end();
        } while (matcher.find());
        final String suffix = text.substring(end);
        if (suffix.contains("${")) throw new IllegalArgumentException("Invalid tooltip placeholder " + text);

        return (id, out) -> {
            final var rendered = new StringBuilder(text.length());
            for (int i = 0; i < values.size(); i++) {
                rendered.append(literals.get(i)).append(values.get(i).get());
            }
            out.writeText(rendered.append(suffix).toString());
        };
    }

    private static Supplier<String> configValue(String key) {
        return switch (key) {
            case "고급갯수" -> () -> String.valueOf(Config.고급갯수);
            case "데몰리션대기" -> () -> String.valueOf(Config.데몰리션대기);
            case "데몰리션엠피" -> () -> String.valueOf(Config.데몰리션엠피);
            case "리버레이션대기" -> () -> String.valueOf(Config.리버레이션대기);
            case "매스이뮨대기시간" -> () -> String.valueOf(Config.매스이뮨대기시간);
            case "매스이뮨마돌" -> () -> String.valueOf(Config.매스이뮨마돌);
            case "매스이뮨엠피" -> () -> String.valueOf(Config.매스이뮨엠피);
            case "매직실드대기" -> () -> String.valueOf(Config.매직실드대기);
            case "버닝샷대기" -> () -> String.valueOf(Config.버닝샷대기);
            case "버서크대기시간" -> () -> String.valueOf(Config.버서크대기시간);
            case "버서크시간" -> () -> String.valueOf(Config.버서크시간);
            case "비전대기시간" -> () -> String.valueOf(Config.비전대기시간);
            case "비전엠피" -> () -> String.valueOf(Config.비전엠피);
            case "쉐도우대기" -> () -> String.valueOf(Config.쉐도우대기);
            case "악세사리" -> () -> String.valueOf(Config.악세사리);
            case "어드밴스대기" -> () -> String.valueOf(Config.어드밴스대기);
            case "어드밴스레벨" -> () -> String.valueOf(Config.어드밴스레벨);
            case "어벤저대기" -> () -> String.valueOf(Config.어벤저대기);
            case "엘븐스트라이크대기" -> () -> String.valueOf(Config.엘븐스트라이크대기);
            case "영웅갯수" -> () -> String.valueOf(Config.영웅갯수);
            case "영웅유물" -> () -> String.valueOf(Config.영웅유물);
            case "이뮨투함블레이드군주피" -> () -> String.valueOf(Config.이뮨투함블레이드군주피);
            case "이뮨투함블레이드기사피" -> () -> String.valueOf(Config.이뮨투함블레이드기사피);
            case "이뮨투함블레이드다엘피" -> () -> String.valueOf(Config.이뮨투함블레이드다엘피);
            case "이뮨투함블레이드대기" -> () -> String.valueOf(Config.이뮨투함블레이드대기);
            case "이뮨투함블레이드레벨" -> () -> String.valueOf(Config.이뮨투함블레이드레벨);
            case "이뮨투함블레이드법사피" -> () -> String.valueOf(Config.이뮨투함블레이드법사피);
            case "이뮨투함블레이드요정피" -> () -> String.valueOf(Config.이뮨투함블레이드요정피);
            case "이뮨투함블레이드용기사피" -> () -> String.valueOf(Config.이뮨투함블레이드용기사피);
            case "이뮨투함블레이드전사피" -> () -> String.valueOf(Config.이뮨투함블레이드전사피);
            case "이뮨투함블레이드환술피" -> () -> String.valueOf(Config.이뮨투함블레이드환술피);
            case "이터니티대기" -> () -> String.valueOf(Config.이터니티대기);
            case "일반갯수" -> () -> String.valueOf(Config.일반갯수);
            case "전설갯수" -> () -> String.valueOf(Config.전설갯수);
            case "포텐셜대기시간" -> () -> String.valueOf(Config.포텐셜대기시간);
            case "프라임대기시간" -> () -> String.valueOf(Config.프라임대기시간);
            case "혈맹버프지속시간" -> () -> String.valueOf(Config.혈맹버프지속시간);
            case "혈맹버프회복" -> () -> String.valueOf(Config.혈맹버프회복);
            case "혈맹포인트최대" -> () -> String.valueOf(Config.혈맹포인트최대);
            case "혈맹포인트최소" -> () -> String.valueOf(Config.혈맹포인트최소);
            case "희귀갯수" -> () -> String.valueOf(Config.희귀갯수);
            default -> throw new IllegalArgumentException("Unknown tooltip config key " + key);
        };
    }
}
