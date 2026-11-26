package l1j.server.server.model.item.effects;

import java.util.LinkedHashMap;
import java.util.Map;

import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemScript;
import l1j.server.server.model.item.ItemStat;

public final class ItemEffectBootstrap {
    private ItemEffectBootstrap() {}

    public static void loadAll() {
        final String filename = "equipment-registered.txt";
        final Map<Integer, ItemEffectRegistry.Def> definitions =
                ItemScript.load(filename, "rkern.equipment.registered", (source, reader) -> {
                    final Map<Integer, ItemEffectRegistry.Def> result = new LinkedHashMap<>();
                    ItemScript.readRows(source, reader, new ItemScript.RowReader() {
                        private ItemEffectRegistry.Def definition;

                        @Override
                        public void read(String[] row) {
                            switch (row[0]) {
                                case "item" -> {
                                    ItemScript.requireFields(row, 5);
                                    final int id = Integer.parseInt(row[1]);
                                    if (id <= 0 || result.containsKey(id)) {
                                        throw new IllegalArgumentException("Invalid or duplicate item ID " + id);
                                    }
                                    if (!row[2].equals("true") && !row[2].equals("false")) {
                                        throw new IllegalArgumentException("Expected true or false, got " + row[2]);
                                    }
                                    definition = new ItemEffectRegistry.Def(
                                            optionalText(row[3]), Boolean.parseBoolean(row[2]), optionalText(row[4]));
                                    result.put(id, definition);
                                }
                                case "bonus" -> {
                                    ItemScript.requireFields(row, 5);
                                    final int classType = Integer.parseInt(row[1]);
                                    final int enchant = Integer.parseInt(row[2]);
                                    final ItemStat stat = ItemStat.valueOf(row[3]);
                                    final int value = Integer.parseInt(row[4]);
                                    if (definition == null || classType < -1 || classType > 7
                                            || enchant < -1 || (classType != -1 && enchant != -1)) {
                                        throw new IllegalArgumentException("Invalid class or enchant bonus");
                                    }
                                    if (enchant == -1) definition.add(classType, stat, value);
                                    else definition.addEnchant(enchant, stat, value);
                                }
                                default -> throw new IllegalArgumentException("Unknown option " + row[0]);
                            }
                        }
                    });
                    if (result.isEmpty()) throw new IllegalStateException(source + " has no item definitions");
                    return result;
                });
        definitions.forEach((id, def) -> ItemEffectRegistry.register(id, def, filename));
    }

    private static String optionalText(String text) {
        return text.equals("-") ? null : text;
    }
}
