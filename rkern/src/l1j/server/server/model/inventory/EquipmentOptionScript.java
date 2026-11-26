package l1j.server.server.model.inventory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import l1j.server.server.model.item.ItemScript;

/** Reads the same script format from an operator file or the bundled defaults. */
final class EquipmentOptionScript {
    private static final Loaded LOADED = ItemScript.load(
            "equipment-options.txt", "rkern.equipment.options", EquipmentOptionScript::parse);

    enum Section {
        WEAPON, ARMOR_PRIMARY, ARMOR_SLOT, ACCESSORY, ARMOR_DODGE, ARMOR_CASTING, TRAITS
    }

    private record Loaded(String source, Map<Section, String> sections) {}

    private EquipmentOptionScript() {}

    static String read(Section section) {
        return LOADED.sections().get(section);
    }

    static String source() {
        return LOADED.source();
    }

    private static Loaded parse(String source, BufferedReader reader) throws IOException {
        final EnumMap<Section, StringBuilder> sections = new EnumMap<>(Section.class);
        StringBuilder body = null;
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
            final String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) continue;
            if (text.startsWith("[") && text.endsWith("]")) {
                final Section section;
                try {
                    section = Section.valueOf(text.substring(1, text.length() - 1));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalStateException(source + ":" + lineNumber + " unknown section " + text, invalid);
                }
                body = new StringBuilder();
                if (sections.putIfAbsent(section, body) != null) {
                    throw new IllegalStateException(source + ":" + lineNumber + " duplicate section " + text);
                }
            } else {
                if (body == null) {
                    throw new IllegalStateException(source + ":" + lineNumber + " option outside a section");
                }
                body.append(text).append('\n');
            }
        }
        final EnumMap<Section, String> parsed = new EnumMap<>(Section.class);
        for (Section section : Section.values()) {
            final StringBuilder content = sections.get(section);
            if (content == null) {
                throw new IllegalStateException(source + " missing equipment options section " + section);
            }
            parsed.put(section, content.toString());
        }
        return new Loaded(source, Map.copyOf(parsed));
    }
}
