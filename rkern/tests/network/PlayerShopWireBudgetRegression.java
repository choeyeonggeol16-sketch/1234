import java.util.ArrayList;
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

    private static void test(int tooltipBytes, boolean heavy) throws Exception {
        final Class<?> packet = SC_PERSONAL_SHOP_ITEM_LIST_NOTI.class;
        final var sizer = packet.getDeclaredMethod("playerShopRowWireBytes", TradeItemInfo.class);
        sizer.setAccessible(true);
        final var budgetField = packet.getDeclaredField("PLAYER_SHOP_ROW_BYTES");
        budgetField.setAccessible(true);
        final var item = ItemInfo.class.getDeclaredConstructor();
        item.setAccessible(true);
        final int totalBudget = budgetField.getInt(null);
        int remaining = totalBudget;
        TradeItemInfo overflow = null;
        final var rows = new ArrayList<TradeItemInfo>();
        for (int index = 0; index < 255; index++) {
            var details = item.newInstance().set_extra_description(new byte[tooltipBytes]);
            var row = TradeItemInfo.newInstance().set_index(index).set_count(2_000_000)
                    .set_price(2_000_001).set_trade_info(0).set_buy_count(2_000_000).set_item_info(details);
            final int measured = (int) sizer.invoke(null, row);
            if (measured > remaining) {
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
        final int capacity = CIOBufferPool.MAX_WIRE_BYTES - remaining;
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
            SC_PERSONAL_SHOP_ITEM_LIST_NOTI.writeFields(writer, 3L, type, seller, rows.size(), rows);
            finish.invoke(writer, true);
            check(buffer.position() == expected.length + CIOBufferPool.HEADER_BYTES, "length includes framing");
            for (int index = 0; index < expected.length; index++)
                check(lease.buffer().get(index + CIOBufferPool.HEADER_BYTES) == expected[index], "direct wire parity");
            System.out.printf("PASS rows=%d tooltip=%d reservedBytes=%d actualWireBytes=%d%n", rows.size(),
                    tooltipBytes, capacity, buffer.position());
        } finally {
            detach.invoke(writer);
            pool.release(lease);
        }
    }

    public static void main(String[] args) throws Exception {
        test(5, false);
        test(350, true);
    }
}
