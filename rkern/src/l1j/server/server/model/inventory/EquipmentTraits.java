package l1j.server.server.model.inventory;

import java.util.Arrays;
import java.util.TreeMap;

import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.inventory.EquipmentOptionScript.Section;

/** Special equipment capabilities attached to item definitions. */
public final class EquipmentTraits {
    enum Flag {
        INVISIBILITY, HALPHAS, MAP_BREATH_ICON, WATER_BREATHING, EXCLUSIVE_ABILITY,
        CHARGED_POLYMORPH, CLASS_DEPENDENT_BONUS, FIXED_ARMOR_AC, NONNEGATIVE_ENCHANT_AC
    }

    private enum Value {
        EQUIPPED_SKILL, ABILITY, EQUIP_MESSAGE, MORPH, ELF_MR, RUNE_TIER, RUNE_ACCURACY,
        RUNE_WIZARD_MP, RUNE_MAGIC_DR, SPELLS, MR_PER_ENCHANT
    }

    private static final EquipmentTraits EMPTY = new EquipmentTraits(0, new int[Value.values().length], 0);
    private static final int[] IDS;
    private static final EquipmentTraits[] DEFINITIONS;

    static {
        final TreeMap<Integer, Builder> builders = loadDefinitions();
        IDS = new int[builders.size()];
        DEFINITIONS = new EquipmentTraits[builders.size()];
        int index = 0;
        for (var entry : builders.entrySet()) {
            IDS[index] = entry.getKey();
            final Builder builder = entry.getValue();
            final EquipmentTraits definition = new EquipmentTraits(builder.flags, builder.values, builder.spells);
            if (definition.has(Flag.EXCLUSIVE_ABILITY) && definition.ability() == 0) {
                throw new IllegalArgumentException("Exclusive equipment has no ability " + entry.getKey());
            }
            DEFINITIONS[index++] = definition;
        }
    }

    static void initialize() {
        // Compile the traits before the server starts.
    }

    private static TreeMap<Integer, Builder> loadDefinitions() {
        final String definitions = EquipmentOptionScript.read(Section.TRAITS);
        try {
            return compile(definitions);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Invalid equipment traits in " + EquipmentOptionScript.source()
                    + " [TRAITS]: " + invalid.getMessage(), invalid);
        }
    }

    private static TreeMap<Integer, Builder> compile(String definitions) {
        final TreeMap<Integer, Builder> builders = new TreeMap<>();
        for (String row : definitions.split("\\R")) {
            if (row.isBlank()) continue;
            final String[] fields = row.split("\\s+");
            if (fields.length < 2) throw new IllegalArgumentException("Missing equipment traits in " + row);
            for (String selector : fields[0].split(",")) {
                final String[] ends = selector.split("\\.\\.");
                final int first = Integer.parseInt(ends[0]);
                final int last = ends.length == 1 ? first : Integer.parseInt(ends[1]);
                if (ends.length > 2 || first <= 0 || last < first) {
                    throw new IllegalArgumentException("Invalid equipment trait selector " + selector);
                }
                for (long id = first; id <= last; id++) {
                    final Builder builder = builders.computeIfAbsent((int) id, unused -> new Builder());
                    for (int i = 1; i < fields.length; i++) builder.add(fields[i]);
                }
            }
        }
        return builders;
    }

    private static final class Builder {
        private int flags;
        private int assigned;
        private long spells;
        private final int[] values = new int[Value.values().length];

        private void add(String field) {
            final int separator = field.indexOf('=');
            if (separator < 0) {
                final int mask = 1 << Flag.valueOf(field).ordinal();
                if ((flags & mask) != 0) throw new IllegalArgumentException("Duplicate equipment flag " + field);
                flags |= mask;
                return;
            }
            final Value key = Value.valueOf(field.substring(0, separator));
            final int mask = 1 << key.ordinal();
            if ((assigned & mask) != 0) throw new IllegalArgumentException("Duplicate equipment trait " + field);
            assigned |= mask;
            final String raw = field.substring(separator + 1);
            if (key == Value.SPELLS) {
                for (String spell : raw.split(",")) {
                    final int id = Integer.parseInt(spell);
                    if (id <= 0 || id > Long.SIZE || (spells & (1L << (id - 1))) != 0) {
                        throw new IllegalArgumentException("Invalid equipment spell " + spell);
                    }
                    spells |= 1L << (id - 1);
                }
            } else if (key == Value.EQUIPPED_SKILL) {
                values[key.ordinal()] = switch (raw) {
                    case "TELEPORT_RULER" -> L1SkillId.TELEPORT_RULER;
                    default -> throw new IllegalArgumentException("Unknown equipment skill " + raw);
                };
            } else {
                final int amount = Integer.parseInt(raw);
                if (amount <= 0) throw new IllegalArgumentException("Invalid equipment trait " + field);
                values[key.ordinal()] = amount;
            }
        }
    }

    private final int flags;
    private final int[] values;
    private final long spells;

    private EquipmentTraits(int flags, int[] values, long spells) {
        this.flags = flags;
        this.values = values;
        this.spells = spells;
    }

    public static EquipmentTraits forItem(int itemId) {
        final int index = Arrays.binarySearch(IDS, itemId);
        return index < 0 ? EMPTY : DEFINITIONS[index];
    }

    static boolean isAbilityEquipped(CUserInventory inventory, int ability) {
        if (ability == 0) return false;
        for (int i = 0; i < IDS.length; i++) {
            if (DEFINITIONS[i].ability() == ability && inventory.IsEquippedByClassIDCached(IDS[i])) return true;
        }
        return false;
    }

    boolean has(Flag flag) { return (flags & (1 << flag.ordinal())) != 0; }
    int ability() { return values[Value.ABILITY.ordinal()]; }
    int equippedSkill() { return values[Value.EQUIPPED_SKILL.ordinal()]; }
    int equipMessage() { return values[Value.EQUIP_MESSAGE.ordinal()]; }
    int morph() { return values[Value.MORPH.ordinal()]; }
    public int elfMr() { return values[Value.ELF_MR.ordinal()]; }
    int runeTier() { return values[Value.RUNE_TIER.ordinal()]; }
    int runeWizardMp() { return values[Value.RUNE_WIZARD_MP.ordinal()]; }
    long spells() { return spells; }
    public int runeAccuracy() { return values[Value.RUNE_ACCURACY.ordinal()]; }
    public int runeMagicDamageReduction() { return values[Value.RUNE_MAGIC_DR.ordinal()]; }
    public int mrPerEnchant() { return values[Value.MR_PER_ENCHANT.ordinal()]; }
}
