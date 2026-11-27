import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.ItemInfo;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_PERSONAL_SHOP_ITEM_LIST_NOTI.ePersonalShopType;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.TradeItemInfo;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoMsg;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;

public final class PlayerShopWireBudgetRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void test(int nameBytes, boolean heavy) throws Exception {
        final Class<?> packet = SC_PERSONAL_SHOP_ITEM_LIST_NOTI.class;
        final var writeRow = packet.getDeclaredMethod("writeRow", ProtoWriter.class, int.class, int.class,
                int.class, L1ItemInstance.class, boolean.class);
        writeRow.setAccessible(true);
        final var budgetField = packet.getDeclaredField("PLAYER_SHOP_ROW_BYTES");
        budgetField.setAccessible(true);
        var template = new L1EtcItem();
        template.setItemId(41_001);
        template.setName("wire fixture");
        String displayName = "x".repeat(nameBytes);
        var item = new L1ItemInstance(template) {
            @Override public String getViewName() { return displayName; }
        };
        item.setId(2_000_021);
        final int totalBudget = budgetField.getInt(null);
        int remaining = totalBudget;
        TradeItemInfo overflow = null;
        final var rows = new ArrayList<TradeItemInfo>();
        try (ProtoWriter encoded = ProtoWriter.borrow()) {
            for (int index = 0; index < 255; index++) {
                var details = ItemInfo.newInstance(item);
                var row = TradeItemInfo.newInstance().set_index(index).set_count(2_000_000)
                        .set_price(2_000_001).set_trade_info(0).set_item_info(details);
                boolean buying = (index & 1) != 0;
                if (buying) row.set_buy_count(2_000_000);
                int before = encoded.length();
                writeRow.invoke(null, encoded, index, 2_000_000, 2_000_001, item, buying);
                final int measured = encoded.length() - before;
                if (measured > remaining) {
                    encoded.rewindBody(before);
                    overflow = row;
                    break;
                }
                rows.add(row);
                remaining -= measured;
            }
            check(heavy ? (rows.size() > 127 && rows.size() < 255 && overflow != null) : rows.size() == 255,
                    "varint count and row limit");
            final var type = ePersonalShopType.TRADE_SELL;
            final int seller = Integer.MAX_VALUE;
            final byte[] expected = SC_PERSONAL_SHOP_ITEM_LIST_NOTI.encodeFields(3L, type, seller, rows.size(), rows);
            if (overflow != null) {
                final var tooLarge = new ArrayList<>(rows);
                tooLarge.add(overflow);
                final byte[] oversized = SC_PERSONAL_SHOP_ITEM_LIST_NOTI.encodeFields(3L, type, seller, tooLarge.size(), tooLarge);
                check(oversized.length + CIOBufferPool.HEADER_BYTES > CIOBufferPool.MAX_WIRE_BYTES,
                        "next heavy row would exceed actual frame cap");
            }
            final int capacity = expected.length + CIOBufferPool.HEADER_BYTES;
            final var pool = new CIOBufferPool(1, 1, 1, 1);
            final var lease = pool.acquireSend(capacity);
            final var direct = ProtoWriter.class.getDeclaredMethod("direct", Assemble.class);
            final var finish = ProtoWriter.class.getDeclaredMethod("finishDirect", boolean.class);
            final var detach = ProtoWriter.class.getDeclaredMethod("detachDirect");
            direct.setAccessible(true); finish.setAccessible(true); detach.setAccessible(true);
            final var buffer = lease.writer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI);
            final var writer = (ProtoWriter) direct.invoke(null, buffer);
            try {
                writer.enumV(1, type.toInt()).int32(2, seller).int32(3, rows.size()).appendBody(encoded);
                finish.invoke(writer, true);
                check(buffer.position() == expected.length + CIOBufferPool.HEADER_BYTES, "length includes framing");
                for (int index = 0; index < expected.length; index++)
                    check(lease.buffer().get(index + CIOBufferPool.HEADER_BYTES) == expected[index], "direct wire parity");
                System.out.printf("PASS rows=%d nameBytes=%d reservedBytes=%d actualWireBytes=%d%n", rows.size(),
                        nameBytes, capacity, buffer.position());
            } finally {
                detach.invoke(writer);
                pool.release(lease);
            }
        }
    }

    private static void itemParity(L1ItemInstance item) {
        ItemInfo previous = ItemInfo.newInstance(item);
        check(Arrays.equals(previous.encodeBody(), ItemInfo.encodeItemBody(item)), "full tooltip/card fields");
        try (ProtoWriter reference = ProtoWriter.borrow()) {
            reference.int32(1, item.getId()).msg(2);
            previous.set_extra_description(null).body(reference);
            reference.end();
            check(Arrays.equals(reference.bodyBytes(), ItemInfo.encodeWarehouseBody(item)), "warehouse field-19 omission");
        }
    }

    public static void main(String[] args) throws Exception {
        var companions = CompanionClassInfo.class.getDeclaredField("m_companions");
        companions.setAccessible(true);
        HashMap<String, CompanionClassInfo> catalog = new HashMap<>();
        companions.set(null, catalog);
        test(5, false);
        test(300, true);

        var template = new L1EtcItem();
        template.setItemId(729);
        template.setName("확인 주문서");
        var item = new L1ItemInstance(template) {
            @Override public String getViewName() { return "확인 주문서"; }
        };
        item.setId(15432);
        item.setIdentified(true);
        itemParity(item);
        var entry = CompanionClassInfo.class.getDeclaredConstructor();
        entry.setAccessible(true);
        catalog.put("test", entry.newInstance().set_item_id(template.getItemId()).set_class_id(128)
                .set_class_npc_name_id("강아지"));
        var caches = CompanionInstanceCache.class.getDeclaredField("m_caches");
        caches.setAccessible(true);
        var values = new ConcurrentHashMap<Integer, CompanionInstanceCache>();
        caches.set(null, values);
        itemParity(item);
        var cache = CompanionInstanceCache.class.getDeclaredConstructor();
        cache.setAccessible(true);
        values.put(item.getId(), cache.newInstance().set_level(8).set_name("콩이")
                .set_is_dead(true).set_is_oblivion(true).set_elixir_use_count(3));
        itemParity(item);
        System.out.println("PASS identified tooltip, warehouse omission and missing/cached companion cards");
    }
}
