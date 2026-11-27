package l1j.server.server.logd;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import l1j.server.server.datatables.MapsTable;

record CLogRecord(Kind kind, long timestamp, Object[] fields) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    enum View { NONE, SHOP, CLAN, GLOBAL, PARTY, WHISPER, ENCHANT, TRADE, WAREHOUSE, PICKUP }

    enum Kind {
        SHOP(true, "NPC상점.txt", View.SHOP, "[%s] 엔피씨 : %s, 캐릭명 : %s, 아이템 : %s, 갯수 : %d, 가격 : %d"),
        CLAN(false, "채팅.txt", View.CLAN, "%s\t혈맹(%s)\t[%s]\t%s"),
        CLAN_CHANNEL(false, "채팅.txt", View.CLAN, "%s\t혈맹\t[%s]\t%s"),
        GLOBAL(false, "채팅.txt", View.GLOBAL, "%s\t전체\t[%s]\t%s"),
        PARTY(false, "채팅.txt", View.PARTY, "%s\t파티\t[%s]\t%s"),
        WHISPER(false, "채팅.txt", View.WHISPER, "%s\t귓말\t[%s] -> [%s]\t%s"),
        COMMAND(true, "명령어.txt", View.NONE, "%s\t%s"),
        CONNECTION(true, "로그인.txt", View.NONE, "%s\t%s"),
        ERROR(false, "에러로그.txt", View.NONE, "%s\t%s"),
        ENCHANT(true, "인챈트.txt", View.ENCHANT, "%s\t%s:[%s]\t%s\t%s"),
        TRADE(true, "교환,시장.txt", View.TRADE, "%s\t%s\t%s:[%s]\t%s\t%s:[%s]"),
        CHECK_TRADE(true, "수표거래.txt", View.TRADE, "%s\t%s\t%s:[%s]\t%s\t%s:[%s]"),
        WAREHOUSE(true, "창고.txt", View.WAREHOUSE, "%s\t창고:%s\t%s:[%s]\t%s"),
        ITEM(true, "아이템로그.txt", View.NONE, "%s\t%s\t%s:[%s]\t%s\t[%s]"),
        PICKUP(true, "아이템로그.txt", View.PICKUP, "%s\t%s\t%s:[%s]\t%s\t[%s]"),
        LEVEL(true, "레벨업.txt", View.NONE, "%s\t%s:[%s]\tLevelUp %d"),
        CONSOLE(false, "CMD.txt", View.NONE, "");

        final boolean audit;
        final String filename;
        final View view;
        final String[] parts;

        Kind(boolean audit, String filename, View view, String format) {
            this.audit = audit;
            this.filename = filename;
            this.view = view;
            this.parts = format.split("%[sd]", -1);
        }
    }

    record Item(int objectId, int enchant, String name, boolean stackable, int count) {
        @Override
        public String toString() {
            StringBuilder text = new StringBuilder().append('[').append(objectId).append(']');
            if (enchant > 0) text.append('+');
            if (enchant != 0) text.append(enchant);
            text.append(singleLine(name));
            if (stackable) text.append('(').append(count).append(')');
            return text.toString();
        }
    }

    record MapName(short id) {
        @Override
        public String toString() {
            try {
                String name = MapsTable.getInstance().getMapName(id);
                if (name != null) return singleLine(name);
            } catch (RuntimeException ignored) {
                // Map data can be unavailable during startup or shutdown.
            }
            return Short.toString(id);
        }
    }

    record FileKey(LocalDate date, String name) {}
    record Line(FileKey file, View view, String text) {}

    Line format(ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(timestamp).atZone(zone);
        String text;
        if (kind == Kind.CONSOLE) {
            text = (String) fields[0];
        } else {
            StringBuilder result = new StringBuilder(128);
            result.append(kind.parts[0]).append(TIME.format(time)).append(kind.parts[1]);
            for (int i = 0; i < fields.length; i++) {
                Object field = fields[i];
                result.append(field instanceof String value ? singleLine(value) : field);
                result.append(kind.parts[i + 2]);
            }
            text = result.append("\r\n").toString();
        }
        return new Line(new FileKey(time.toLocalDate(), kind.filename), kind.view, text);
    }

    private static String singleLine(String value) {
        return value == null ? "null" : value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
