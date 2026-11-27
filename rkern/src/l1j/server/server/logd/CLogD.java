package l1j.server.server.logd;

import java.nio.file.Path;

import javax.swing.JTextArea;

import l1j.server.Server;
import l1j.server.server.logd.CLogRecord.Kind;
import l1j.server.server.logd.CLogRecord.View;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.swing.ManagerLogBuffer;
import l1j.server.swing.chocco;

/** In-process, best-effort operational logging. Domain persistence is owned by CacheD. */
public final class CLogD {
    private static final int MAX_NAME = 128;
    private static final int MAX_TEXT = 2048;
    private static final int QUEUE_CAPACITY = 4096;
    private static final long SHUTDOWN_MILLIS = 3000;

    private static final class Holder {
        private static final CLogD INSTANCE = new CLogD();
    }

    public enum ChatType { Normal, Global, Clan, Alliance, Guardian, Party, Group, Shouting }
    public enum ItemActionType { Pickup, Drop, Delete, del, DeathDrop }
    public enum WarehouseType { Private, Clan, Package, Elf }

    private final CLogWriter writer = new CLogWriter(Path.of("logDB"), QUEUE_CAPACITY, CLogD::appendUi);

    private CLogD() {}

    public static CLogD getInstance() {
        return Holder.INSTANCE;
    }

    public void addShop(String itemName, int count, long price, String npc, String pc) {
        publish(Kind.SHOP, name(npc), name(pc), name(itemName), count, price);
    }

    public void addChat(ChatType type, L1PcInstance pc, String message) {
        if (pc == null || type == null) return;
        try {
            String character = name(pc.getName());
            String text = bound(message, MAX_TEXT);
            switch (type) {
                case Clan -> publish(Kind.CLAN, name(pc.getClanname()), character, text);
                case Alliance, Guardian -> publish(Kind.CLAN_CHANNEL, character, text);
                case Global, Normal, Shouting -> publish(Kind.GLOBAL, character, text);
                case Party, Group -> publish(Kind.PARTY, character, text);
            }
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addWhisper(L1PcInstance from, L1PcInstance to, String message) {
        if (from == null || to == null) return;
        try {
            publish(Kind.WHISPER, name(from.getName()), name(to.getName()), bound(message, MAX_TEXT));
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addCommand(String message) {
        publish(Kind.COMMAND, bound(message, MAX_TEXT));
    }

    public void addConnection(String message) {
        publish(Kind.CONNECTION, bound(message, MAX_TEXT));
    }

    public void addError(String message) {
        publish(Kind.ERROR, bound(message, MAX_TEXT));
    }

    public void addEnchant(L1PcInstance pc, L1ItemInstance item, boolean success) {
        if (pc == null || item == null) return;
        try {
            publish(Kind.ENCHANT, account(pc), name(pc.getName()), success ? "성공" : "실패", item(item, 1));
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addTrade(boolean success, L1PcInstance from, L1PcInstance to, L1ItemInstance item, int count) {
        trade(Kind.TRADE, success ? "OO완료OO" : "XX취소XX", from, to, item, count);
    }

    public void addCheckTrade(boolean success, L1PcInstance from, L1PcInstance to, L1ItemInstance item, int count) {
        trade(Kind.CHECK_TRADE, success ? "OO완료OO" : "XX취소XX", from, to, item, count);
    }

    public void addPrivateShop(boolean success, L1PcInstance from, L1PcInstance to, L1ItemInstance item, int count) {
        trade(Kind.TRADE, success ? "상점구매" : "상점취소", from, to, item, count);
    }

    private void trade(Kind kind, String result, L1PcInstance from, L1PcInstance to, L1ItemInstance item, int count) {
        if (from == null || to == null || item == null) return;
        try {
            publish(kind, result, account(from), name(from.getName()), item(item, count), account(to), name(to.getName()));
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addWarehouse(WarehouseType type, boolean put, L1PcInstance pc, L1ItemInstance item, int count) {
        if (pc == null || item == null) return;
        try {
            publish(Kind.WAREHOUSE, put ? "맡기기" : "찾기", account(pc), name(pc.getName()), item(item, count));
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addItemAction(ItemActionType type, L1PcInstance pc, L1ItemInstance item, int count) {
        if (pc == null || item == null || type == null) return;
        try {
            Kind kind = type == ItemActionType.Pickup ? Kind.PICKUP : Kind.ITEM;
            publish(kind, type.name(), account(pc), name(pc.getName()), item(item, count),
                    new CLogRecord.MapName(pc.getMapId()));
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addLevel(L1PcInstance pc, int level) {
        if (pc == null) return;
        try {
            publish(Kind.LEVEL, account(pc), name(pc.getName()), level);
        } catch (RuntimeException failure) {
            writer.captureFailed(failure);
        }
    }

    public void addCmd(String message) {
        if (message == null || message.isEmpty()) return;
        publish(Kind.CONSOLE, message.length() > MAX_TEXT
                ? bound(message, MAX_TEXT - 2) + "\r\n" : message);
    }

    private void publish(Kind kind, Object... fields) {
        writer.offer(new CLogRecord(kind, System.currentTimeMillis(), fields));
    }

    private static CLogRecord.Item item(L1ItemInstance item, int count) {
        return new CLogRecord.Item(item.getId(), item.getEnchantLevel(), name(item.getName()),
                item.isStackable(), count);
    }

    private static String account(L1PcInstance pc) {
        return name(pc.getAccountName());
    }

    private static String name(String value) {
        return bound(value, MAX_NAME);
    }

    private static String bound(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        int end = limit - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }

    private static void appendUi(View view, String line) {
        JTextArea area = switch (view) {
            case SHOP -> chocco.txtShop;
            case CLAN -> chocco.txtClanChat;
            case GLOBAL -> chocco.txtGlobalChat;
            case PARTY -> chocco.txtPartyChat;
            case WHISPER -> chocco.txtWhisper;
            case ENCHANT -> chocco.txtEnchant;
            case TRADE -> chocco.txtTrade;
            case WAREHOUSE -> chocco.txtWarehouse;
            case PICKUP -> chocco.txtPickup;
            case NONE -> null;
        };
        ManagerLogBuffer.append(area, line);
    }

    public String diagnostics() {
        return writer.diagnostics().toString();
    }

    public void shutdown() {
        boolean drained = writer.shutdown(SHUTDOWN_MILLIS);
        CLogWriter.Diagnostics status = writer.diagnostics();
        if (!drained || status.hasLoss()) {
            Server.printShutdownStatus("[LogD] " + (drained ? "" : "종료 대기 초과 ") + status);
        }
    }
}
