package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import l1j.server.MJCTSystem.MJCTItem;
import l1j.server.MJCTSystem.MJCTSpell;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

/**
 * 창고 아이템 목록 통지 (0x0408)
 */
public final class SC_WarehouseItemListNoti extends ProtoPacket {

    private static final int WH_CHUNK_MAX_ITEMS = 40;
    private static final int WH_CHUNK_MAX_BYTES = 12 * 1024;

    private Integer checker;              // 1 int32
    private Integer count;                // 2 int32
    private eWarehouseType warehouseType; // 3 enum
    private Integer price;                // 4 int32
    private Integer warehouseSize;        // 5 int32
    private final List<WarehouseItemInfo> items = new LinkedList<>();   // 6 (라이브)
    private final List<byte[]> rawItems = new LinkedList<>();           // 6 (선인코딩)
    private Boolean serialLast;           // 7 bool

    private SC_WarehouseItemListNoti() {}

    public static SC_WarehouseItemListNoti newInstance() { return new SC_WarehouseItemListNoti(); }

    public SC_WarehouseItemListNoti set_checker(int v)         { checker = v; return this; }
    public SC_WarehouseItemListNoti set_count(int v)           { count = v; return this; }
    public SC_WarehouseItemListNoti set_warehouse_type(eWarehouseType v) { warehouseType = v; return this; }
    public SC_WarehouseItemListNoti set_price(int v)           { price = v; return this; }
    public SC_WarehouseItemListNoti set_warehouse_size(int v)  { warehouseSize = v; return this; }
    public SC_WarehouseItemListNoti set_serial_last(boolean v) { serialLast = v; return this; }
    public SC_WarehouseItemListNoti add_item_list(WarehouseItemInfo v)   { items.add(v); return this; }
    public SC_WarehouseItemListNoti add_encoded_item(byte[] encodedBody){ rawItems.add(encodedBody); return this; }

    @Override
    public int msgId() { return ProtoMsg.SC_WAREHOUSE_ITEM_LIST_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (checker != null)       w.int32(1, checker);
        if (count != null)         w.int32(2, count);
        if (warehouseType != null) w.enumV(3, warehouseType.toInt());
        if (price != null)         w.int32(4, price);
        if (warehouseSize != null) w.int32(5, warehouseSize);
        for (WarehouseItemInfo wi : items) {
            w.msg(6);
            wi.body(w);
            w.end();
        }
        for (byte[] raw : rawItems) {
            w.bytes(6, raw);   // tag+varint+len+body = msg(6)/end()와 바이트 동일
        }
        if (serialLast != null) w.bool(7, serialLast);
    }

    // ============================================================
    // 1) 혈맹 창고
    // ============================================================
    public static void send_user_pledge_warehouse_items(L1PcInstance pc, int npc_object_id){
        if (pc.getInventory().getSize() >= 180){
            SC_ServerMessage.send(pc, 263);
            return;
        }
        if (pc.getClanRank() == L1Clan.수련) {
            SC_ServerMessage.send(pc, 728);
            return;
        }
        L1Clan clan = pc.getClan();
        if(clan == null || pc.getClanid() == 0){
            pc.sendPackets("혈맹창고를 사용하려면 혈맹이 있어야 합니다.");
            return;
        }
        ClanWarehouse whouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
        if(whouse.getSize() <= 0){
            SC_NPCTalkReturn.send(pc, npc_object_id, "noitemret", null);
            return;
        }
        if (!whouse.setWarehouseUsingChar(pc.getId(), 0)) {
            int id = whouse.getWarehouseUsingChar();
            L1Object prev_user = L1World.getInstance().findObject(id);
            if (prev_user instanceof L1PcInstance) {
                L1PcInstance using_pc = (L1PcInstance) prev_user;
                if (using_pc.getClan() == clan) {
                    pc.sendPackets(String.format("혈맹원 %s님이 혈맹창고를 사용중입니다.잠시 후 이용해 주세요.", using_pc.getName()));
                    return;
                }
            }
            if(!whouse.setWarehouseUsingChar(pc.getId(), id)){
                pc.sendPackets(String.format("혈맹원 %s님이 혈맹창고를 사용중입니다. 잠시 후 이용해 주세요.", whouse.getName()));
                return;
            }
        }
        sendWarehouseItemsChunked(pc, npc_object_id, castItemsSnapshot(whouse.getItems()),
                whouse.getSize(), 500, eWarehouseType.TRADE_RETRIEVE_PLEDGE);
    }

    // ============================================================
    // 2) 부가 창고
    // ============================================================
    public static void send_user_supplementary_service(L1PcInstance pc, int npc_object_id){
        if (pc.getInventory().getSize() >= 180){
            SC_ServerMessage.send(pc, 263);
            return;
        }
        SupplementaryService whouse = WarehouseManager.getInstance().getSupplementaryService(pc.getAccountName());
        if(whouse.getSize() <= 0){
            SC_ServerMessage.send(pc, 263);
            return;
        }
        sendWarehouseItemsChunked(pc, npc_object_id, castItemsSnapshot(whouse.getItems()),
                whouse.getSize(), 0x00, eWarehouseType.TRADE_RETRIEVE_CONTRACT);
    }

    // ============================================================
    // 3) 개인 창고
    // ============================================================
    public static void send_user_warehouse_items(L1PcInstance pc, int npc_object_id){
        if (pc.getInventory().getSize() >= 180){
            SC_ServerMessage.send(pc, 263);
            return;
        }
        PrivateWarehouse whouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
        if(whouse.getSize() <= 0){
            SC_NPCTalkReturn.send(pc, npc_object_id, "noitemret", null);
            return;
        }
        sendWarehouseItemsChunked(pc, npc_object_id, castItemsSnapshot(whouse.getItems()),
                whouse.getSize(), 300, eWarehouseType.TRADE_RETRIEVE);
    }

    // ============================================================
    // 청크 전송 
    // ============================================================
    private static List<L1ItemInstance> castItemsSnapshot(Iterable<?> src){
        ArrayList<L1ItemInstance> out = new ArrayList<>(256);
        try{
            if (src != null){
                for (Object o : src){
                    if (o instanceof L1ItemInstance){
                        out.add((L1ItemInstance)o);
                    }
                }
            }
        }catch(Throwable t){ }
        return out;
    }

    private static void sendWarehouseItemsChunked(L1PcInstance pc, int npc_object_id,
            List<L1ItemInstance> items, int totalSize, int price, eWarehouseType type){
        if (pc == null) return;
        if (items == null || items.isEmpty()) {
            sendEncodedChunk(pc, npc_object_id, price, 0, type, null, 0, true);
            return;
        }

        int idx = 0, sent = 0;
        final int n = items.size();
        int cntInChunk = 0, bytesInChunk = 0;

        byte[][] encodedChunk = new byte[WH_CHUNK_MAX_ITEMS][];

        while (idx < n){
            L1ItemInstance item = items.get(idx++);
            if (item == null || item.getItem() == null) continue;

            final byte[] encoded;
            try{
                encoded = ItemInfo.encodeWarehouseBody(item);
            }catch(Throwable t){
                try{
                    System.out.println("[WH] ItemInfo build fail: pc=" + pc.getName()
                            + " objId=" + item.getId() + " itemId=" + item.getItemId()
                            + " name=" + item.getName());
                }catch(Throwable ignore){}
                continue;
            }
            final int addBytes = 1 + ProtoWriter.varintLenOf(encoded.length) + encoded.length;

            if (addBytes >= WH_CHUNK_MAX_BYTES){
                if (cntInChunk > 0){
                    sendEncodedChunk(pc, npc_object_id, price, totalSize, type, encodedChunk, cntInChunk, false);
                    sent += cntInChunk;

                    cntInChunk = 0; bytesInChunk = 0;
                }
                encodedChunk[0] = encoded;
                sendEncodedChunk(pc, npc_object_id, price, totalSize, type, encodedChunk, 1, false);
                sent += 1;
                continue;
            }

            if (cntInChunk > 0 && (cntInChunk >= WH_CHUNK_MAX_ITEMS
                    || (bytesInChunk + addBytes) >= WH_CHUNK_MAX_BYTES)){
                sendEncodedChunk(pc, npc_object_id, price, totalSize, type, encodedChunk, cntInChunk, false);
                sent += cntInChunk;

                cntInChunk = 0; bytesInChunk = 0;
            }

            encodedChunk[cntInChunk] = encoded;
            cntInChunk++;
            bytesInChunk += addBytes;
        }

        if (cntInChunk > 0) {
            sendEncodedChunk(pc, npc_object_id, price, totalSize, type, encodedChunk, cntInChunk, true);
        } else {
            sendEncodedChunk(pc, npc_object_id, price, totalSize, type, null, 0, true);
        }
    }

    private static boolean sendEncodedChunk(L1PcInstance pc, int checker, int price, int totalSize,
            eWarehouseType type, byte[][] bodies, int count, boolean last) {
        return sendEncodedChunk(pc == null ? null : pc.getNetConnection(), checker, price,
                totalSize, type, bodies, count, last);
    }

    public static boolean sendEncodedChunk(CIOClient client, int checker, int price, int totalSize,
            eWarehouseType type, byte[][] bodies, int count, boolean last) {
        if (count < 0 || (count > 0 && (bodies == null || count > bodies.length)))
            throw new IllegalArgumentException("Invalid warehouse chunk range");
        long capacity = 128L;
        for (int i = 0; i < count; i++) capacity += 1L + ProtoWriter.varintLenOf(bodies[i].length) + bodies[i].length;
        ProtoWriter writer = Proto.begin(client, ProtoMsg.SC_WAREHOUSE_ITEM_LIST_NOTI, Proto.capacityBound(capacity));
        if (writer == null) return false;
        try {
            writer.int32(1, checker).int32(2, count);
            if (type != null) writer.enumV(3, type.toInt());
            writer.int32(4, price).int32(5, totalSize);
            for (int i = 0; i < count; i++) writer.bytes(6, bodies[i]);
            writer.bool(7, last);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    // ============================================================
    // CT / 디스플레이 생성
    // ============================================================
    public static SC_WarehouseItemListNoti create_ct_sp_info(ArrayDeque<MJCTSpell> spQ){
        SC_WarehouseItemListNoti noti = newInstance();
        noti.set_checker(1);
        int real_size = 0;

        L1ItemInstance item = ItemTable.getInstance().createItem(40005);
        if(spQ != null){
            while(!spQ.isEmpty()){
                MJCTSpell ct_spell = spQ.poll();
                item.setId(item.getId());
                item.setBless(item.getItem().getType2());
                item.setIdentified(true);
                item.setCount(1);
                try {
                    ItemInfo iInfo = ItemInfo.newInstance(item, item.getItem().getUseType(),
                            ct_spell.name.toString().getBytes("MS949"), item.getBless());
                    iInfo.set_icon_id(ct_spell.xicon);
                    WarehouseItemInfo wInfo = WarehouseItemInfo.newInstance()
                            .set_index(item.getId())
                            .set_item_info(iInfo);
                    noti.add_item_list(wInfo);
                    ++real_size;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        noti.set_count(real_size).set_price(0).set_serial_last(true)
            .set_warehouse_size(real_size).set_warehouse_type(eWarehouseType.TRADE_RETRIEVE_PLEDGE);
        return noti;
    }

    public static SC_WarehouseItemListNoti create_ct_item_info(ArrayDeque<MJCTItem> itemQ){
        SC_WarehouseItemListNoti noti = newInstance();
        noti.set_checker(1);
        int real_size = 0;

        if(itemQ != null){
            while(!itemQ.isEmpty()){
                MJCTItem ct_item = itemQ.poll();
                L1Item temp = ct_item.item;
                L1ItemInstance item = new L1ItemInstance();
                item.setItem(temp);
                item.setId(ct_item.id);
                item.setBless(ct_item.bless);
                item.setIdentified(ct_item.iden != 0);
                item.setCount(ct_item.count);
                temp.setGfxId(temp.getGfxId());
                try {
                    ItemInfo iInfo = ItemInfo.newInstance(item, temp.getUseType(),
                            ct_item.toString().getBytes("MS949"), item.getBless());
                    WarehouseItemInfo wInfo = WarehouseItemInfo.newInstance()
                            .set_index(item.getId())
                            .set_item_info(iInfo);
                    noti.add_item_list(wInfo);
                    ++real_size;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        noti.set_count(real_size).set_price(0).set_serial_last(true)
            .set_warehouse_size(real_size).set_warehouse_type(eWarehouseType.TRADE_RETRIEVE_PLEDGE);
        return noti;
    }

    public static SC_WarehouseItemListNoti newDisplayInstance(int object_id, int index, int start, int count, boolean is_serial_last){
        SC_WarehouseItemListNoti noti = newInstance();
        noti.set_checker(object_id);
        int real_size = 0;
        for(int i=0; i<count; ++i){
            L1Item temp = ItemTable.getInstance().getTemplate(40005);
            L1ItemInstance item = new L1ItemInstance();
            int real_number = start + i;
            int real_index = index + i;
            item.setId(real_index);
            item.setItem(temp);
            item.setBless(temp.getBless());
            item.setIdentified(false);
            item.setCount(real_number);
            temp.setGfxId(real_number);
            temp.setName(String.valueOf(real_number));
            temp.setNameId(String.valueOf(real_number));

            ItemInfo iInfo = ItemInfo.newInstance(item);
            WarehouseItemInfo wInfo = WarehouseItemInfo.newInstance()
                    .set_index(real_index)
                    .set_item_info(iInfo);
            noti.add_item_list(wInfo);
            ++real_size;
        }
        noti.set_count(real_size).set_price(300).set_serial_last(is_serial_last)
            .set_warehouse_size(real_size).set_warehouse_type(eWarehouseType.TRADE_RETRIEVE);
        return noti;
    }

    // ============================================================
    // 서브메시지
    // ============================================================
    public static final class WarehouseItemInfo {
        private Integer index;          // 1 int32
        private ItemInfo itemInfo;      // 2 message
        private byte[] rawItemBody;     // 2 (선인코딩)

        private WarehouseItemInfo() {}
        public static WarehouseItemInfo newInstance() { return new WarehouseItemInfo(); }

        public WarehouseItemInfo set_index(int v)              { index = v; return this; }
        public WarehouseItemInfo set_item_info(ItemInfo v)     { itemInfo = v; return this; }

        void body(ProtoWriter w) {
            if (index != null) w.int32(1, index);
            if (itemInfo != null) {
                w.msg(2);
                itemInfo.body(w);
                w.end();
            } else if (rawItemBody != null) {
                w.bytes(2, rawItemBody);
            }
        }

        /** 전체 본문 선인코딩 — 청킹 측정/재사용용 */
        public byte[] encodeBody() {
            ProtoWriter w = ProtoWriter.newInstance();
            body(w);
            return w.bodyBytes();
        }
    }

    public enum eWarehouseType{
        TRADE_RETRIEVE(3),
        TRADE_RETRIEVE_PLEDGE(5),
        TRADE_RETRIEVE_ELVEN(9),
        TRADE_RETRIEVE_CHAR(18),
        TRADE_RETRIEVE_CONTRACT(20);
        private final int value;
        eWarehouseType(int val){ value = val; }
        public int toInt(){ return value; }
        public static eWarehouseType fromInt(int i){
            switch(i){
            case 3:  return TRADE_RETRIEVE;
            case 5:  return TRADE_RETRIEVE_PLEDGE;
            case 9:  return TRADE_RETRIEVE_ELVEN;
            case 18: return TRADE_RETRIEVE_CHAR;
            case 20: return TRADE_RETRIEVE_CONTRACT;
            default: return null;
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, long presentMask, int checker, int count, eWarehouseType warehouseType, int price, int warehouseSize, List<WarehouseItemInfo> items, List<byte[]> rawItems, boolean serialLast) {
        if ((presentMask & (1L << 0)) != 0) w.int32(1, checker);
            if ((presentMask & (1L << 1)) != 0) w.int32(2, count);
            if (warehouseType != null) w.enumV(3, warehouseType.toInt());
            if ((presentMask & (1L << 2)) != 0) w.int32(4, price);
            if ((presentMask & (1L << 3)) != 0) w.int32(5, warehouseSize);
            for (WarehouseItemInfo wi : items) {
                w.msg(6);
                wi.body(w);
                w.end();
            }
            for (byte[] raw : rawItems) {
                w.bytes(6, raw);
            }
            if ((presentMask & (1L << 4)) != 0) w.bool(7, serialLast);
    }

    public static boolean sendFields(L1PcInstance recipient, long presentMask, int checker, int count, eWarehouseType warehouseType, int price, int warehouseSize, List<WarehouseItemInfo> items, List<byte[]> rawItems, boolean serialLast) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), presentMask, checker, count, warehouseType, price,
                warehouseSize, items, rawItems, serialLast);
    }

    public static boolean sendFields(CIOClient recipient, long presentMask, int checker, int count, eWarehouseType warehouseType, int price, int warehouseSize, List<WarehouseItemInfo> items, List<byte[]> rawItems, boolean serialLast) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_WAREHOUSE_ITEM_LIST_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, presentMask, checker, count, warehouseType, price, warehouseSize, items, rawItems, serialLast);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(long presentMask, int checker, int count, eWarehouseType warehouseType, int price, int warehouseSize, List<WarehouseItemInfo> items, List<byte[]> rawItems, boolean serialLast) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, presentMask, checker, count, warehouseType, price, warehouseSize, items, rawItems, serialLast);
        return writer.finishWithTrailer(ProtoMsg.SC_WAREHOUSE_ITEM_LIST_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        long proto102_present = 0L;
        int proto102_checker = 0;
        int proto102_count = 0;
        eWarehouseType proto102_warehouseType = null;
        int proto102_price = 0;
        int proto102_warehouseSize = 0;
        List<WarehouseItemInfo> proto102_items = new LinkedList<>();
        List<byte[]> proto102_rawItems = new LinkedList<>();
        boolean proto102_serialLast = false;


        return sendFields(
                protoRecipient, proto102_present, proto102_checker, proto102_count, proto102_warehouseType, proto102_price,
                proto102_warehouseSize, proto102_items, proto102_rawItems, proto102_serialLast);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
