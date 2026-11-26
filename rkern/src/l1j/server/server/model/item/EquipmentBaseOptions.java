package l1j.server.server.model.item;

import gnu.trove.map.hash.TIntObjectHashMap;

/** Supplies base option values while the weapon and armor templates are loaded. */
public final class EquipmentBaseOptions {
    public enum Field {
        dmg_small, dmg_large, safenchant, hitmodifier, dmgmodifier,
        add_str, add_dex, add_con, add_int, add_wis, add_cha, add_hp, add_mp, add_hpr, add_mpr, add_sp,
        m_def, double_dmg_chance, magicdmgmodifier, canbedmg, min_lvl, max_lvl, haste_item, max_use_time,
        Magic_name, isEndedTimeMessage, missile_critical_probability, melee_critical_probability,
        ac, add_einhasadper, weight_reduction, damage_reduction, hit_rate, dmg_rate, bow_hit_rate,
        bow_dmg_rate, defense_earth, defense_water, defense_wind, defense_fire, ability_resis, ability_pierce,
        spirit_resis, spirit_pierce, dragonS_resis, dragonS_pierce, fear_resis, fear_pierce, all_resis,
        all_pierce, PVPcalcPcDefense, PVPweaponTotalDamage, magic_hit_up;

        private boolean narrow() {
            return switch (this) {
                case add_str, add_dex, add_con, add_int, add_wis, add_cha -> true;
                default -> false;
            };
        }

        private boolean usableOn(boolean weapon) {
            return switch (this) {
                case dmg_small, dmg_large, hitmodifier, dmgmodifier, double_dmg_chance, magicdmgmodifier, canbedmg -> weapon;
                case ac, add_einhasadper, weight_reduction, damage_reduction, hit_rate, dmg_rate, bow_hit_rate,
                     bow_dmg_rate, defense_earth, defense_water, defense_wind, defense_fire, ability_resis, ability_pierce,
                     spirit_resis, spirit_pierce, dragonS_resis, dragonS_pierce, fear_resis, fear_pierce, all_resis,
                     all_pierce, PVPcalcPcDefense, PVPweaponTotalDamage, magic_hit_up -> !weapon;
                default -> true;
            };
        }
    }

    public static final class Row {
        private final int[] values;
        private final String magicName;

        private Row(int[] values, String magicName) {
            this.values = values;
            this.magicName = magicName;
        }

        public int integer(Field field) {
            return values[field.ordinal()];
        }

        public byte signedByte(Field field) {
            return (byte) integer(field);
        }

        public boolean bool(Field field) {
            return integer(field) != 0;
        }

        public String magicName() {
            return magicName;
        }
    }

    private static final int FIELD_COUNT = Field.values().length;
    private final TIntObjectHashMap<Row> weapons = new TIntObjectHashMap<>(600);
    private final TIntObjectHashMap<Row> armors = new TIntObjectHashMap<>(1250);
    private final String source;

    private EquipmentBaseOptions(String source) {
        this.source = source;
    }

    public static EquipmentBaseOptions load() {
        return ItemScript.load("equipment-base.txt", "rkern.equipment.base", (source, reader) -> {
            final EquipmentBaseOptions definitions = new EquipmentBaseOptions(source);
            ItemScript.readRows(source, reader, definitions::add);
            return definitions;
        });
    }

    public Row requireRow(boolean weapon, int itemId) {
        final Row entry = (weapon ? weapons : armors).get(itemId);
        if (entry == null) {
            throw new IllegalStateException(source + " is missing " + (weapon ? "weapon" : "armor")
                    + " item " + itemId);
        }
        return entry;
    }

    private void add(String[] row) {
        if (row.length < 2) throw new IllegalArgumentException("Missing equipment item ID");
        final boolean weapon = switch (row[0]) {
            case "weapon" -> true;
            case "armor" -> false;
            default -> throw new IllegalArgumentException("Unknown equipment type " + row[0]);
        };
        final int id = Integer.parseInt(row[1]);
        if (id <= 0) throw new IllegalArgumentException("Invalid equipment item ID " + id);
        long seen = 0;
        final int[] values = new int[FIELD_COUNT];
        String magicName = null;
        for (int i = 2; i < row.length; i++) {
            final int equals = row[i].indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("Expected option=value in " + row[i]);
            final Field field = Field.valueOf(row[i].substring(0, equals));
            final long bit = 1L << field.ordinal();
            if (!field.usableOn(weapon) || (seen & bit) != 0) {
                throw new IllegalArgumentException("Invalid or duplicate " + field + " on item " + id);
            }
            seen |= bit;
            final String raw = row[i].substring(equals + 1);
            if (field == Field.Magic_name) {
                magicName = unescape(raw);
            } else {
                final int value = Integer.parseInt(raw);
                if (field.narrow() && (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)) {
                    throw new IllegalArgumentException(field + " outside signed byte range on item " + id);
                }
                values[field.ordinal()] = value;
            }
        }
        if ((weapon ? weapons : armors).putIfAbsent(id, new Row(values, magicName)) != null) {
            throw new IllegalArgumentException("Duplicate equipment base row " + id);
        }
    }

    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) return value;
        final StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch != '\\') {
                decoded.append(ch);
                continue;
            }
            if (++i == value.length()) throw new IllegalArgumentException("Incomplete Magic_name escape");
            decoded.append(switch (value.charAt(i)) {
                case '\\' -> '\\';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                default -> throw new IllegalArgumentException("Unknown Magic_name escape " + value.charAt(i));
            });
        }
        return decoded.toString();
    }
}
