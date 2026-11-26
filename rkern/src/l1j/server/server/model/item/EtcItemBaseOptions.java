package l1j.server.server.model.item;

import gnu.trove.map.hash.TIntObjectHashMap;

/** Per-template ETC options loaded before an item catalog is published. */
public final class EtcItemBaseOptions {
    public record Row(int hit, int damage, int minLevel, int maxLevel, int maxCharges, int delayTime, int foodVolume) {}

    private enum Field { hit, dmg, min_lvl, max_lvl, max_charge_count, delay_time, food_volume }

    private static final Row EMPTY = new Row(0, 0, 0, 0, 0, 0, 0);
    private static final int FIELD_COUNT = Field.values().length;
    private final TIntObjectHashMap<Row> rows = new TIntObjectHashMap<>(4500);
    private final String source;

    private EtcItemBaseOptions(String source) {
        this.source = source;
    }

    public static EtcItemBaseOptions load() {
        return ItemScript.load("etc-item-base.txt", "rkern.etc.base", (source, reader) -> {
            final var options = new EtcItemBaseOptions(source);
            ItemScript.readRows(source, reader, options::readRow);
            return options;
        });
    }

    public Row requireRow(int itemId) {
        final Row row = rows.get(itemId);
        if (row == null) throw new IllegalStateException(source + " is missing etcitem " + itemId);
        return row;
    }

    private void readRow(String[] fields) {
        final int id = Integer.parseInt(fields[0]);
        if (id <= 0 || rows.putIfAbsent(id, fields.length == 1 ? EMPTY : parseOptions(fields)) != null) {
            throw new IllegalArgumentException("Invalid or duplicate ETC item ID " + id);
        }
    }

    private static Row parseOptions(String[] fields) {
        final int[] values = new int[FIELD_COUNT];
        int seen = 0;
        for (int i = 1; i < fields.length; i++) {
            final String option = fields[i];
            final int equals = option.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("Expected option=value in " + option);
            final Field field = Field.valueOf(option.substring(0, equals));
            final int bit = 1 << field.ordinal();
            if ((seen & bit) != 0) throw new IllegalArgumentException("Duplicate option " + field);
            seen |= bit;
            values[field.ordinal()] = Integer.parseInt(option.substring(equals + 1));
        }
        return new Row(values[Field.hit.ordinal()], values[Field.dmg.ordinal()], values[Field.min_lvl.ordinal()],
                values[Field.max_lvl.ordinal()], values[Field.max_charge_count.ordinal()],
                values[Field.delay_time.ordinal()], values[Field.food_volume.ordinal()]);
    }
}
