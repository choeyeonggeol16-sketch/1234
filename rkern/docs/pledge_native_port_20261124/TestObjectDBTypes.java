package l1j.server.server.model;

import java.util.Locale;
import java.util.function.ToIntFunction;
import l1j.server.server.datatables.CObjectDB.ItemData;

/** 기존 DB 타입 번호, 별칭, 대소문자, 숫자 입력 및 예외 동작을 검증한다. */
public final class TestObjectDBTypes {
    private static int checks;
    private static void check(ToIntFunction<String> parser, String input, int expected) {
        int actual = parser.applyAsInt(input);
        if (actual != expected)
            throw new AssertionError(input + ": " + actual + " != " + expected);
        checks++;
    }
    private static void table(ToIntFunction<String> parser, String[] keys, int[] expected) {
        if (keys.length != expected.length) throw new AssertionError("table length");
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            check(parser, key, expected[i]);
            check(parser, key.toUpperCase(Locale.ROOT), expected[i]);
            check(parser, key.toLowerCase(Locale.ROOT), expected[i]);
            StringBuilder mixed = new StringBuilder(key);
            for (int j = 0; j < mixed.length(); j += 2)
                mixed.setCharAt(j, Character.toUpperCase(mixed.charAt(j)));
            check(parser, mixed.toString(), expected[i]);
            for (char special : new char[]{'İ', 'ı', 'ſ', 'K'}) {
                for (int j = 0; j < key.length(); j++) {
                    if (!String.valueOf(key.charAt(j)).equalsIgnoreCase(String.valueOf(special))) continue;
                    check(parser, key.substring(0, j) + special + key.substring(j + 1), expected[i]);
                }
            }
        }
        try { parser.applyAsInt(null); throw new AssertionError("null accepted"); }
        catch (NullPointerException expectedFailure) { checks++; }
    }
    public static void main(String[] args) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            table(ItemData::parseWeaponId, new String[]{"sword", "dagger", "tohandsword", "bow", "spear", "blunt", "staff", "throwingknife", "arrow", "gauntlet", "claw", "edoryu", "singlebow", "singlespear", "tohandblunt", "tohandstaff", "keyring", "chainsword"},
                    new int[]{4, 46, 50, 20, 24, 11, 40, 2922, 66, 62, 58, 54, 20, 24, 11, 40, 58, 83});
            table(ItemData::parseWeaponType, new String[]{"sword", "dagger", "tohandsword", "bow", "spear", "blunt", "staff", "throwingknife", "arrow", "gauntlet", "claw", "edoryu", "singlebow", "singlespear", "tohandblunt", "tohandstaff", "keyring", "chainsword"},
                    new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18});
            table(ItemData::parseArmorType, new String[]{"none", "helm", "armor", "T", "cloak", "glove", "boots", "shield", "amulet", "ring", "belt", "ring2", "earring", "garder", "ron", "pair", "ron2", "should", "badge"},
                    new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 28, 29, 30});
            table(ItemData::parseEtcType, new String[]{"arrow", "wand", "light", "gem", "totem", "firecracker", "potion", "food", "scroll", "questitem", "spellbook", "petitem", "other", "material", "event", "sting", "treasure_box", "teleport_scroll"},
                    new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
            table(ItemData::parseMaterial, new String[]{"none", "liquid", "web", "vegetation", "animalmatter", "paper", "cloth", "leather", "wood", "bone", "dragonscale", "iron", "steel", "copper", "silver", "gold", "platinum", "mithril", "blackmithril", "glass", "gemstone", "mineral", "oriharukon"},
                    new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22});
            table(ItemData::parseUseType, new String[]{"none", "normal", "weapon", "armor", "spell_long", "ntele", "identify", "res", "teleport", "letter", "letter_w", "choice", "instrument", "sosc", "spell_short", "T", "cloak", "glove", "boots", "helm", "ring", "amulet", "shield", "garder", "dai", "zel", "blank", "btele", "spell_buff", "ccard", "ccard_w", "vcard", "vcard_w", "wcard", "wcard_w", "belt", "spell_long2", "earring", "fishing_rod", "ron", "acczel", "healing", "tam", "ron2", "pair", "magic_doll", "should", "petp", "badge", "JJ"},
                    new int[]{-1, 0, 1, 2, 5, 6, 7, 8, 9, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 39, 40, 42, 44, 46, 51, 68, 74, 70, 73, 75, 77, 78, 79});
            check(ItemData::parseWeaponId, "unknown", 0);
            check(ItemData::parseWeaponType, "unknown", 0);
            check(ItemData::parseArmorType, "unknown", 0);
            check(ItemData::parseEtcType, "unknown", -1);
            check(ItemData::parseMaterial, "unknown", 0);
            for (String value : new String[]{"0","-1","+42","0007","2147483647","-2147483648","１２"})
                check(ItemData::parseUseType, value, Integer.parseInt(value));
            for (String value : new String[]{"unknown"," 7","2147483648","", "7x"}) {
                try { ItemData.parseUseType(value); throw new AssertionError("invalid numeric type accepted: " + value); }
                catch (NumberFormatException expectedFailure) { checks++; }
            }
            System.out.println("PASS ObjectDB type parsers: " + checks + " checks");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
