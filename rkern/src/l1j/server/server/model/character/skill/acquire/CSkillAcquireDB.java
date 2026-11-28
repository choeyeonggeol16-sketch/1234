package l1j.server.server.model.character.skill.acquire;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Skills;

/** The L1 spell trainer and book rules indexed for the current definition snapshot. */
public final class CSkillAcquireDB {
    public enum Book {
        GENERAL(1, 80, "마법서 (", 224, -1),
        DARK_ELF(97, 241, "흑정령의 수정 (", 231, 29),
        ELF(129, 179, "정령의 수정 (", 224, -1),
        KNIGHT(87, 96, "기술서 (", 224, -1),
        PRINCE(113, 123, "마법서 (", 224, -1),
        DRAGON_KNIGHT(181, 199, "용기사의 서판(", 224, -1),
        ILLUSIONIST(201, 224, "기억의 수정(", 224, 28);

        final int first;
        final int last;
        final String prefix;
        public final int sound;
        public final int level29Slot;

        Book(int first, int last, String prefix, int sound, int level29Slot) {
            this.first = first;
            this.last = last;
            this.prefix = prefix;
            this.sound = sound;
            this.level29Slot = level29Slot;
        }
    }

    // The NPC's offer count and the per-tier purchase thresholds were independent.
    private static final int[] OFFER_COUNT = {16, 8, 23, 23, 16, 0, 0, 8};
    private static final int[][] MINIMUM_LEVEL = {
        {10, 20}, {50}, {8, 16, 24}, {4, 8, 12}, {12, 24}, {}, {}, {50}
    };
    private static final int[] BUY_PRICE = {100, 400, 900};
    private static final int[] EXCHANGE_PRICE = {10, 400, 900};

    private final CSkillDB definitions;
    private final Map<Book, Map<String, List<L1Skills>>> books = new EnumMap<>(Book.class);
    private final Map<Book, TIntObjectHashMap<IndexedBook>> booksByItemId = new EnumMap<>(Book.class);
    private final int npcDefinedMask;

    private record IndexedBook(String name, List<L1Skills> skills) {}

    public CSkillAcquireDB(CSkillDB definitions, Connection con) throws SQLException {
        this.definitions = definitions;
        int mask = 0;
        for (int skillId = 1; skillId <= 24; skillId++)
            if (definitions.getTemplate(skillId) != null) mask |= 1 << (skillId - 1);
        npcDefinedMask = mask;
        Book[] families = Book.values();
        for (Book book : families) {
            Map<String, List<L1Skills>> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (int skillId = book.first; skillId <= book.last; skillId++) {
                L1Skills definition = definitions.getTemplate(skillId);
                if (definition == null) continue;
                String key = book.prefix + definition.getName() + ")";
                names.computeIfAbsent(key, ignored -> new ArrayList<>(1)).add(definition);
            }
            names.replaceAll((key, matches) -> List.copyOf(matches));
            books.put(book, names);
            booksByItemId.put(book, new TIntObjectHashMap<>());
        }
        try (var query = con.prepareStatement("SELECT item_id,name FROM etcitem WHERE item_type='spellbook'");
             var rows = query.executeQuery()) {
            while (rows.next()) {
                int itemId = rows.getInt("item_id");
                String name = rows.getString("name");
                for (Book book : families) {
                    List<L1Skills> matches = findBook(book, name);
                    if (!matches.isEmpty()) booksByItemId.get(book).put(itemId, new IndexedBook(name, matches));
                }
            }
        }
    }

    public List<L1Skills> findBook(Book book, L1Item item) {
        IndexedBook indexed = booksByItemId.get(book).get(item.getItemId());
        // Item definitions can reload independently. Preserve the current item's legacy name rule.
        return indexed != null && indexed.name.equalsIgnoreCase(item.getName())
                ? indexed.skills : findBook(book, item.getName());
    }

    public List<L1Skills> findBook(Book book, String itemName) {
        return itemName == null ? List.of() : books.get(book).getOrDefault(itemName, List.of());
    }

    public L1Skills findWarriorBook(int itemId) {
        int skillId = switch (itemId) {
            case 210121 -> 225;
            case 210122 -> 226;
            case 210123 -> 228;
            case 210124 -> 229;
            case 210125 -> 230;
            case 3000094 -> 231;
            case 210127 -> 234;
            case 210128 -> 235;
            case 210126 -> 236;
            case 210129 -> 237;
            case 210130 -> 238;
            case 210131 -> 239;
            case 210132 -> 240;
            default -> 0;
        };
        return definitions.getTemplate(skillId);
    }

    public int npcOfferMask(int classId) {
        return classId >= 0 && classId < OFFER_COUNT.length
                ? ((1 << OFFER_COUNT[classId]) - 1) & npcDefinedMask : 0;
    }

    public int npcLearnMask(int classId, int level, boolean exchange) {
        int tierCount = 3;
        if (classId >= 0 && classId < MINIMUM_LEVEL.length && classId != 5 && classId != 6
                && !(exchange && classId == 7)) {
            int[] levels = MINIMUM_LEVEL[classId];
            tierCount = 0;
            while (tierCount < levels.length && level >= levels[tierCount]) tierCount++;
        }
        int upper = exchange ? 0x7fffff : 0xffffff;
        return ((1 << (tierCount * 8)) - 1) & upper & npcDefinedMask;
    }

    static int npcPrice(int mask, boolean exchange) {
        int[] prices = exchange ? EXCHANGE_PRICE : BUY_PRICE;
        return Integer.bitCount(mask & 255) * prices[0]
                + Integer.bitCount((mask >>> 8) & 255) * prices[1]
                + Integer.bitCount((mask >>> 16) & 255) * prices[2];
    }
}
