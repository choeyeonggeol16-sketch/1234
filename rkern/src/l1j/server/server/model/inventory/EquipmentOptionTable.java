package l1j.server.server.model.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.templates.L1Item;

/** Compiles the equipment definitions once and resolves each item by a primitive-key lookup. */
final class EquipmentOptionTable {
    private static final int NOTIFY_SPMR = -1;
    private static final int NOTIFY_ER = -2;
    private static final int NOTIFY_AC = -3;
    private static final int OP_WIDTH = 3;
    private static final ItemStat[] STATS = ItemStat.values();
    private static final Profile EMPTY = new Profile(new int[0], new int[0], new int[0][]);

    private enum AccessoryKind {
        NON_ACCESSORY, NECKLACE, RING, BELT, EARRING, GRADE_3, OTHER_ACCESSORY;

        static AccessoryKind forItem(L1Item item) {
            final int type = item.getType();
            if (type < 8 || type > 12) return NON_ACCESSORY;
            final int grade = item.getGrade();
            if (grade >= 0 && grade <= 2) {
                return switch (type) {
                    case 8 -> NECKLACE;
                    case 9, 11 -> RING;
                    case 10 -> BELT;
                    case 12 -> EARRING;
                    default -> throw new IllegalArgumentException("Invalid accessory type " + type);
                };
            }
            if (grade == 5 && (type == 9 || type == 11)) return RING;
            return grade == 3 ? GRADE_3 : OTHER_ACCESSORY;
        }
    }

    static final EquipmentOptionTable WEAPON = load(EquipmentOptionData.WEAPON, false);
    static final EquipmentOptionTable ARMOR_PRIMARY = load(EquipmentOptionData.ARMOR_PRIMARY, false);
    static final EquipmentOptionTable ARMOR_SLOT = load(EquipmentOptionData.ARMOR_SLOT, false);
    static final EquipmentOptionTable ACCESSORY = load(EquipmentOptionData.ACCESSORY, true);
    static final EquipmentOptionTable ARMOR_DODGE = load(EquipmentOptionData.ARMOR_DODGE, false);
    static final EquipmentOptionTable ARMOR_CASTING = load(EquipmentOptionData.ARMOR_CASTING, false);

    private record Profile(int[] starts, int[] ends, int[][] steps) {
        void apply(L1PcInstance pc, int enchant, int sign, int[] changes) {
            final int match = Arrays.binarySearch(ends, enchant);
            final int index = match >= 0 ? match : -match - 1;
            if (index >= steps.length || enchant < starts[index]) return;
            final int[] ops = steps[index];
            for (int i = 0; i < ops.length; i += OP_WIDTH) {
                final int op = ops[i];
                if (op >= 0) {
                    EquipmentEnchantOptions.applyStat(pc, changes, STATS[op],
                            sign * (ops[i + 1] + enchant * ops[i + 2]));
                } else if (changes == null) {
                    switch (op) {
                        case NOTIFY_SPMR -> EquipmentEnchantOptions.sendSpMr(pc);
                        case NOTIFY_ER -> EquipmentEnchantOptions.sendEvasion(pc);
                        case NOTIFY_AC -> EquipmentEnchantOptions.sendArmorClass(pc);
                        default -> throw new IllegalStateException("Invalid equipment opcode " + op);
                    }
                }
            }
        }
    }

    private final int[] itemIds;
    private final Profile[][] byItem;
    private final Profile[] defaults;

    private EquipmentOptionTable(int[] itemIds, Profile[][] byItem, Profile[] defaults) {
        this.itemIds = itemIds;
        this.byItem = byItem;
        this.defaults = defaults;
    }

    void apply(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        apply(pc, item.getItemId(), 0, enchant, sign, changes);
    }

    void applyAccessory(L1PcInstance pc, L1Item item, int enchant, int sign, int[] changes) {
        final int kind = AccessoryKind.forItem(item).ordinal();
        apply(pc, item.getItemId(), kind * 2 + (item.getBless() == 0 ? 0 : 1), enchant, sign, changes);
    }

    private void apply(L1PcInstance pc, int itemId, int variant, int enchant, int sign, int[] changes) {
        final int index = Arrays.binarySearch(itemIds, itemId);
        final Profile specific = index >= 0 ? byItem[index][variant] : null;
        final Profile profile = specific != null ? specific : defaults[variant];
        if (profile != null) profile.apply(pc, enchant, sign, changes);
    }

    private static EquipmentOptionTable load(String[] chunks, boolean accessory) {
        final int variants = accessory ? AccessoryKind.values().length * 2 : 1;
        final TreeMap<Integer, Profile[]> byId = new TreeMap<>();
        final Profile[] defaults = new Profile[variants];
        for (String chunk : chunks) {
            String selector = null;
            final ArrayList<String> rows = new ArrayList<>();
            for (String line : chunk.split("\\R")) {
                final String row = line.trim();
                if (row.isEmpty()) continue;
                if (row.startsWith("@ ")) {
                    if (selector != null) addProfile(byId, defaults, selector, rows, accessory);
                    selector = row.substring(2);
                    rows.clear();
                } else {
                    if (selector == null) throw new IllegalArgumentException("Equipment row without item selector");
                    rows.add(row);
                }
            }
            if (selector != null) addProfile(byId, defaults, selector, rows, accessory);
        }
        final int[] ids = new int[byId.size()];
        final Profile[][] profiles = new Profile[byId.size()][];
        int index = 0;
        for (var entry : byId.entrySet()) {
            ids[index] = entry.getKey();
            profiles[index++] = entry.getValue();
        }
        return new EquipmentOptionTable(ids, profiles, defaults);
    }

    private static void addProfile(TreeMap<Integer, Profile[]> byId, Profile[] defaults,
            String selector, ArrayList<String> rows, boolean accessory) {
        final String[] fields = selector.split(" ");
        final int firstVariant;
        final int lastVariant;
        if (accessory && (fields.length == 2 || fields.length == 3)) {
            final int kind = AccessoryKind.valueOf(fields[1]).ordinal() * 2;
            if (fields.length == 2) {
                firstVariant = kind;
                lastVariant = kind + 1;
            } else {
                final int side = switch (fields[2]) {
                    case "BLESSED" -> 0;
                    case "OTHER" -> 1;
                    default -> throw new IllegalArgumentException("Invalid blessing selector " + selector);
                };
                firstVariant = lastVariant = kind + side;
            }
        } else if (!accessory && fields.length == 1) {
            firstVariant = lastVariant = 0;
        } else {
            throw new IllegalArgumentException("Invalid equipment selector " + selector);
        }

        final Profile profile = buildProfile(rows);
        for (String key : fields[0].split(",")) {
            final Profile[] variants;
            if (key.equals("*")) {
                variants = defaults;
            } else {
                final int id = Integer.parseInt(key);
                if (id <= 0) throw new IllegalArgumentException("Invalid equipment item ID " + key);
                variants = byId.computeIfAbsent(id, unused -> new Profile[defaults.length]);
            }
            for (int i = firstVariant; i <= lastVariant; i++) {
                if (variants[i] != null) throw new IllegalArgumentException("Duplicate equipment selector " + selector);
                variants[i] = profile;
            }
        }
    }

    private static Profile buildProfile(ArrayList<String> rows) {
        if (rows.isEmpty()) return EMPTY;
        final int[] starts = new int[rows.size()];
        final int[] ends = new int[rows.size()];
        final int[][] steps = new int[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            final String row = rows.get(i);
            final int separator = row.indexOf(' ');
            if (separator < 0) throw new IllegalArgumentException("Missing equipment effects in " + row);
            final String range = row.substring(0, separator);
            final int interval = range.indexOf("..");
            if (interval < 0) {
                starts[i] = ends[i] = Integer.parseInt(range);
            } else {
                final String first = range.substring(0, interval);
                final String last = range.substring(interval + 2);
                starts[i] = first.equals("*") ? Integer.MIN_VALUE : Integer.parseInt(first);
                ends[i] = last.equals("*") ? Integer.MAX_VALUE : Integer.parseInt(last);
            }
            if (starts[i] > ends[i] || (i > 0 && starts[i] <= ends[i - 1])) {
                throw new IllegalArgumentException("Overlapping or invalid equipment enchant range " + row);
            }
            steps[i] = parseSteps(row.substring(separator + 1).trim());
        }
        return new Profile(starts, ends, steps);
    }

    private static int[] parseSteps(String effects) {
        final String[] tokens = effects.split(", ");
        final int[] steps = new int[tokens.length * OP_WIDTH];
        for (int i = 0; i < tokens.length; i++) {
            final String token = tokens[i];
            final int offset = i * OP_WIDTH;
            if (token.startsWith("@")) {
                steps[offset] = switch (token) {
                    case "@SPMR" -> NOTIFY_SPMR;
                    case "@ER" -> NOTIFY_ER;
                    case "@AC" -> NOTIFY_AC;
                    default -> throw new IllegalArgumentException("Unknown equipment notification " + token);
                };
            } else {
                final String[] parts = token.split(":");
                if (parts.length < 2 || parts.length > 3) {
                    throw new IllegalArgumentException("Invalid equipment stat " + token);
                }
                steps[offset] = ItemStat.valueOf(parts[0]).ordinal();
                steps[offset + 1] = Integer.parseInt(parts[1]);
                if (parts.length == 3) steps[offset + 2] = Integer.parseInt(parts[2]);
            }
        }
        return steps;
    }
}
