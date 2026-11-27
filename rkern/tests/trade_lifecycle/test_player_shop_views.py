"""Exercise real player-shop display rows and session publication with a controlled SEND boundary."""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / "src/l1j/server/server"


def section(file, first, last):
    text = (src / file).read_text(encoding="utf-8")
    return text[text.index(first):text.index(last)]


view = (src / "model/shop/PlayerShopView.java").read_text(encoding="utf-8")
view = view[view.index("public final class PlayerShopView"):].replace(
    "public final class PlayerShopView", "static final class PlayerShopView", 1)
socket = section("model/network/core/connection/CUserSocket.java",
                 "    private volatile PlayerShopView playerShopView;",
                 "    public NpcShopView getNpcShopView()")
writer = section("packets/Protocol/MainServer/Inventory/Proto/SC_PERSONAL_SHOP_ITEM_LIST_NOTI.java",
                 "    public static void do_send(L1PcInstance pc",
                 "    // ============================================================\n    // NPC 상점 - 구매")
wire_budget = section("packets/Protocol/MainServer/Inventory/Proto/SC_PERSONAL_SHOP_ITEM_LIST_NOTI.java",
                      "    private static final int PLAYER_SHOP_WIRE_OVERHEAD",
                      "    private ePersonalShopType type;")
close = section("model/network/core/cs/packet/CS_Result.java",
                "        if (size == 0 && (resultType == 0 || resultType == 1))",
                "\t\tL1Object findObject")

fixture = r"""
import java.util.*;

public final class PlayerShopViewsFixture {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static class CWorld {
        static final CWorld WORLD = new CWorld();
        final HashMap<Integer,L1PcInstance> objects = new HashMap<>();
        final HashMap<Long,L1PcInstance> generations = new HashMap<>();
        static CWorld getInstance() { return WORLD; }
        Object findObject(int id) { return objects.get(id); }
        Object findObjectBySmartId(long id) { return generations.get(id); }
        long smartIdOf(L1PcInstance actor) { return objects.get(actor.id) == actor ? actor.identity : 0; }
        void install(L1PcInstance actor) {
            final var old = objects.put(actor.id, actor);
            if (old != null) generations.remove(old.identity);
            generations.put(actor.identity, actor);
        }
    }
    static class CUserSocket {
        L1PcInstance active;
        boolean ready = true;
        boolean ownsReadyGameSession() { return ready && active != null && active.socket == this; }
        L1PcInstance getActiveChar() { return active; }
""" + socket + r"""
    }
    static class NpcShopView {}
    static class L1PcInstance {
        final int id;
        final long identity;
        int stamp = 1, version = 1;
        final ArrayList<DynamicShopItem> orders = new ArrayList<>();
        ArrayList<DynamicShopItem> sales;
        final Inventory inventory = new Inventory();
        CUserSocket socket = new CUserSocket();
        boolean selling, gm;
        L1PcInstance(int id, long identity) {
            this.id = id;
            this.identity = identity;
            socket.active = this;
            CWorld.WORLD.install(this);
        }
        int getId() { return id; }
        int getSessionStamp() { return stamp; }
        int getPrivateShopVersion() { return version; }
        boolean isPrivateShop() { return selling; }
        boolean isGm() { return gm; }
        ArrayList<DynamicShopItem> getPurchasings() { return orders; }
        ArrayList<DynamicShopItem> getSellings() { return sales; }
        CUserSocket getNetConnection() { return socket; }
        Inventory getInventory() { return inventory; }
    }
    static class Inventory {
        final ArrayList<L1ItemInstance> items = new ArrayList<>();
        L1ItemInstance[] GetUnequippedItemListByClassIDs(int[] classIds) {
            return items.stream().filter(item -> Arrays.stream(classIds).anyMatch(id -> id == item.itemClass))
                    .toArray(L1ItemInstance[]::new);
        }
        L1ItemInstance GetByServerID(int id) {
            return items.stream().filter(item -> item.id == id).findFirst().orElse(null);
        }
    }
    static class NoTradable {
        static boolean blocked;
        static boolean blocksPlayerTransfer(boolean gm, int id) { return blocked && !gm; }
    }
    record L1ItemInstance(int id, int itemClass, int count, boolean eligible) {
        int getId() { return id; }
        int getItemId() { return itemClass; }
        int getCount() { return count; }
    }
    static class DynamicShopItem {
        int itemId = 12, objId = 700, price = 50, count = 4;
        boolean isPurchase = true;
    }
    static class PlayerShopTransactions {
        static int eligibilityChecks;
        static boolean canReach(L1PcInstance viewer, L1PcInstance shop) {
            return viewer != shop && shop.isPrivateShop();
        }
        static boolean canTransfer(L1PcInstance viewer, L1ItemInstance item, DynamicShopItem listing,
                int count, long now) {
            eligibilityChecks++;
            return item.eligible && item.itemClass == listing.itemId && count > 0;
        }
    }
    record ItemInfo(int id) {
        static Runnable beforePack;
        static void writeItem(ProtoWriter w, L1ItemInstance item) {
            if (beforePack != null) beforePack.run();
            w.current.item = new ItemInfo(item.id);
        }
    }
    static class CIOBufferPool {
        static final int HEADER_BYTES = 2, MAX_WIRE_BYTES = 65535;
    }
    static class TradeItemInfo {
        static int bodySize = 20;
        int index, count, price, buyCount;
        ItemInfo item;
    }
    static class ProtoWriter implements AutoCloseable {
        int length, depth;
        TradeItemInfo current;
        final List<TradeItemInfo> rows = new ArrayList<>();
        static ProtoWriter borrow() { return new ProtoWriter(); }
        ProtoWriter msg(int field) {
            if (depth++ == 0) current = new TradeItemInfo();
            return this;
        }
        ProtoWriter int32(int field, int value) {
            if (field == 1) current.index = value;
            if (field == 2) current.count = value;
            if (field == 3) current.price = value;
            if (field == 6) current.buyCount = value;
            return this;
        }
        ProtoWriter end() {
            if (--depth == 0) { rows.add(current); length += TradeItemInfo.bodySize + 3; }
            return this;
        }
        int length() { return length; }
        void rewindBody(int before) { rows.removeLast(); length = before; }
        public void close() {}
    }
    enum ePersonalShopType { TRADE_BUY, TRADE_SELL }
    static class Writer {
        static boolean accepted = true;
        static CUserSocket sentTo;
        static List<TradeItemInfo> last;
        static Runnable afterSend;
        static boolean sendRows(CUserSocket client, ePersonalShopType type, int shopId, int size, ProtoWriter encoded) {
            List<TradeItemInfo> items = encoded.rows;
            check(Thread.holdsLock(client), "viewer must be held through SEND admission");
            final L1PcInstance shop = CWorld.WORLD.objects.get(shopId);
            final var listings = type == ePersonalShopType.TRADE_SELL ? shop.orders : shop.sales;
            check(listings == null || !Thread.holdsLock(listings), "must not send while holding merchant list monitor");
            check(size == items.size() && encoded.length() <= PLAYER_SHOP_ROW_BYTES,
                    "wire header and row budget");
            sentTo = client;
            last = items;
            if (afterSend != null) afterSend.run();
            return accepted;
        }
""" + wire_budget + writer + r"""
    }
""" + view + r"""
    static void close(CUserSocket client, L1PcInstance pc, int npcObjectId, int resultType, int size) {
""" + close + r"""
    }
    static void show(L1PcInstance viewer, L1PcInstance shop) {
        viewer.socket.clearPlayerShopView(viewer.socket.getPlayerShopView());
        Writer.do_send(viewer, shop.id, ePersonalShopType.TRADE_SELL);
    }
    public static void main(String[] args) {
        final var viewer = new L1PcInstance(10, 3010);
        final var shop = new L1PcInstance(20, 3020);
        shop.selling = true;
        shop.orders.add(new DynamicShopItem());
        viewer.inventory.items.addAll(List.of(new L1ItemInstance(201, 12, 7, true),
                new L1ItemInstance(202, 12, 8, false), new L1ItemInstance(203, 12, 2, true)));
        show(viewer, shop);
        final var socket = viewer.socket;
        final var view = socket.getPlayerShopView();
        check(Writer.sentTo == socket && Writer.last.size() == 2 && view.size() == 2,
                "one order displays two matching physical items on the captured session");
        check(PlayerShopTransactions.eligibilityChecks == 3, "writer uses settlement item eligibility");
        for (int index = 0; index < 2; index++) {
            final var row = Writer.last.get(index);
            check(row.index == index && row.item.id == view.customerItemId(index)
                    && row.price == view.price(index) && row.count == view.count(index)
                    && row.buyCount == row.count && view.listingId(index) == 700, "wire row/view identity divergence");
        }
        check(view.customerItemId(0) == 201 && view.customerItemId(1) == 203, "inventory order preserved");
        check(view.currentFor(socket, viewer) && view.matches(shop, true), "view requires live actor and merchant");
        close(socket, viewer, shop.id, 0, 0);
        check(socket.getPlayerShopView() == view, "closing opposite direction leaves view");
        close(socket, viewer, 999, 1, 0);
        check(socket.getPlayerShopView() == view, "closing another shop leaves view");
        close(socket, viewer, shop.id, 1, 0);
        check(socket.getPlayerShopView() == null, "matching close clears view");

        final var sale = new DynamicShopItem();
        sale.isPurchase = false;
        shop.sales = new ArrayList<>(List.of(sale));
        shop.inventory.items.add(new L1ItemInstance(sale.objId, 12, 3, true));
        Writer.do_send(viewer, shop.id, ePersonalShopType.TRADE_BUY);
        final var buyView = socket.getPlayerShopView();
        check(buyView.matches(shop, false) && buyView.size() == 1 && buyView.customerItemId(0) == 0
                && Writer.last.getFirst().item.id == sale.objId && Writer.last.getFirst().count == 3,
                "sale page keeps merchant-owned item, quantity, and direction");

        for (int n = 0; n < 270; n++) viewer.inventory.items.add(new L1ItemInstance(1000 + n, 12, 1, true));
        show(viewer, shop);
        final var many = socket.getPlayerShopView();
        check(many.size() == 255 && Writer.last.get(254).index == 254, "cap counts actual compact rows");
        TradeItemInfo.bodySize = 20_000;
        show(viewer, shop);
        final var heavy = socket.getPlayerShopView();
        check(heavy.size() == 3 && Writer.last.get(2).index == 2
                && Writer.last.get(2).item.id == heavy.customerItemId(2),
                "oversized list stops before byte limit and keeps wire indices");
        TradeItemInfo.bodySize = 20;
        socket.clearPlayerShopView(view);
        check(socket.getPlayerShopView() == heavy, "expired compare-and-clear leaves newer view");
        shop.version++;
        check(!many.matches(shop, true), "reopening shop invalidates previous generation");
        viewer.stamp++;
        check(!many.currentFor(socket, viewer), "viewer rebind invalidates previous view");

        Writer.accepted = false;
        show(viewer, shop);
        check(socket.getPlayerShopView() == null, "failed SEND publishes no view");
        Writer.accepted = true;
        Writer.afterSend = () -> socket.ready = false;
        show(viewer, shop);
        check(socket.getPlayerShopView() == null, "session closed during SEND cannot receive the view");
        Writer.afterSend = null;
        socket.ready = true;
        shop.orders.clear();
        show(viewer, shop);
        check(Writer.last.isEmpty() && socket.getPlayerShopView() == null, "empty list responds without a view");
        shop.orders.add(new DynamicShopItem());
        NoTradable.blocked = true;
        viewer.gm = true;
        show(viewer, shop);
        check(Writer.last.isEmpty() && socket.getPlayerShopView() == null, "restricted non-GM buy order stays hidden");
        NoTradable.blocked = false;
        Writer.sentTo = null;
        final var replacement = new CUserSocket();
        ItemInfo.beforePack = () -> { viewer.stamp++; viewer.socket = replacement; replacement.active = viewer; };
        show(viewer, shop);
        check(Writer.sentTo == null && socket.getPlayerShopView() == null && replacement.getPlayerShopView() == null,
                "session replaced during encoding receives no list or view");
        final var recycledShop = new L1PcInstance(shop.id, 4020);
        recycledShop.selling = true;
        check(!many.matches(shop, true) && !many.matches(recycledShop, true), "recycled merchant ID cannot reuse a view");
        System.out.println("PASS player shop compact rows, matching close, and exact viewer publication");
    }
}
"""

with tempfile.TemporaryDirectory() as temp:
    main = Path(temp) / "PlayerShopViewsFixture.java"
    main.write_text(fixture, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(main)], check=True)
    subprocess.run(["java", "-cp", temp, "PlayerShopViewsFixture"], check=True)
