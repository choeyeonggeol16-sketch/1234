package l1j.server.server.packets.Protocol.Packet;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.session.CIOClient;
import java.util.LinkedList;
import java.util.List;

import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;
/**
 * 인벤토리 아이템 갱신 통지 (0x024D) — repeated ItemInfo(1)만.
 */
public final class SC_UPDATE_INVENTORY_NOTI extends ProtoPacket {

    private final List<ItemInfo> _items = new LinkedList<>();

    public SC_UPDATE_INVENTORY_NOTI add_item_info(ItemInfo item) {
        _items.add(item);
        return this;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_UPDATE_INVENTORY_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        for (ItemInfo item : _items) {
            w.msg(1);
            item.body(w);
            w.end();
        }
    }

    public static void send_companion_control_item_name(L1PcInstance pc, int itemObjectId, String classNpcNameId, int level) {
        String description = String.format("펫 목걸이 (%s LV %d)", classNpcNameId, level);
        SC_ItemName.send(pc, itemObjectId, description);
    }

    public static void send_companion_control_item(L1PcInstance pc, int itemObjectId, MJCompanionInstanceCache cache) {
        if (pc != null) sendCompanion(pc.getNetConnection(), itemObjectId, cache);
    }

    public static boolean sendCompanion(CIOClient client, int itemObjectId, MJCompanionInstanceCache cache) {
        boolean summoned = cache.get_is_summoned();
        boolean dead = cache.get_is_dead();
        boolean oblivion = cache.get_is_oblivion();
        int level = cache.get_level();
        String name = cache.get_name();
        int elixirs = cache.get_elixir_use_count();
        ProtoWriter writer = Proto.begin(client, ProtoMsg.SC_UPDATE_INVENTORY_NOTI,
                Proto.capacityBound(128L + (name == null ? 0L : 2L * name.length())));
        if (writer == null) return false;
        try {
            writer.msg(1).uint32(1, itemObjectId).msg(21)
                    .bool(1, oblivion).bool(2, dead).bool(3, summoned).uint32(5, level);
            if (name != null) writer.string(6, name);
            writer.uint32(7, elixirs).end().end();
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<ItemInfo> _items) {
        for (ItemInfo item : _items) {
                w.msg(1);
                item.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<ItemInfo> _items) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _items);
    }

    public static boolean sendFields(CIOClient recipient, List<ItemInfo> _items) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_UPDATE_INVENTORY_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _items);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<ItemInfo> _items) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _items);
        return writer.finishWithTrailer(ProtoMsg.SC_UPDATE_INVENTORY_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        List<ItemInfo> proto100_items = new LinkedList<>();

        return sendFields(protoRecipient, proto100_items);
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
