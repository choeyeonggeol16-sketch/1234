package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_BOX_ATTR_CHANGE_NOTI_PACKET (0x0081) — 맵 이벤트 박스 속성 변경
 *  1 world_number int32 [required]
 *  2 box { 1 sx, 2 sy, 3 ex, 4 ey int32 } [required]
 *  3 attribute    int32 [required]
 */
public final class SC_BOX_ATTR_CHANGE_NOTI_PACKET extends ProtoPacket {

    public static final class Box {
        private int _sx, _sy, _ex, _ey;

        private Box() {}

        /** MJ create_line_boxes 이관 — 사각 테두리 4변 박스 생성 */
        public static Box[] createLineBoxes(int left, int top, int right, int bottom){
            Box[] boxes = new Box[4];

            boxes[0] = of(left, top, right, top + 1);        // 상변
            boxes[1] = of(right - 1, top, right, bottom);    // 우변
            boxes[2] = of(left, bottom - 1, right, bottom);  // 하변
            boxes[3] = of(left, top, left + 1, bottom);      // 좌변
            return boxes;
        }

        public static Box of(int sx, int sy, int ex, int ey){
            Box b = new Box();
            b._sx = sx; b._sy = sy; b._ex = ex; b._ey = ey;
            return b;
        }

        public int get_sx(){ return _sx; }
        public int get_sy(){ return _sy; }
        public int get_ex(){ return _ex; }
        public int get_ey(){ return _ey; }

        void body(ProtoWriter w){
            w.int32(1, _sx)
             .int32(2, _sy)
             .int32(3, _ex)
             .int32(4, _ey);
        }
    }

    private final int _worldNumber;
    private final Box _box;
    private final int _attribute;

    private SC_BOX_ATTR_CHANGE_NOTI_PACKET(int worldNumber, Box box, int attribute){
        _worldNumber = worldNumber;
        _box = box;
        _attribute = attribute;
    }

    /** MJ create_box 이관 — 반환 타입이 ProtoOutputStream → byte[] 로 변경됨 */
    public static byte[] createBox(int mapId, Box box, int attribute){
        return new SC_BOX_ATTR_CHANGE_NOTI_PACKET(mapId, box, attribute).encode();
    }

    @Override
    public int msgId() { return ProtoMsg.SC_BOX_ATTR_CHANGE_NOTI_PACKET; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _worldNumber);
        w.msg(2);
        _box.body(w);
        w.end();
        w.int32(3, _attribute);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _worldNumber, Box _box, int _attribute) {
        w.int32(1, _worldNumber);
            w.msg(2);
            _box.body(w);
            w.end();
            w.int32(3, _attribute);
    }

    public static boolean sendFields(L1PcInstance recipient, int _worldNumber, Box _box, int _attribute) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _worldNumber, _box, _attribute);
    }

    public static boolean sendFields(CIOClient recipient, int _worldNumber, Box _box, int _attribute) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_BOX_ATTR_CHANGE_NOTI_PACKET, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _worldNumber, _box, _attribute);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _worldNumber, Box _box, int _attribute) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _worldNumber, _box, _attribute);
        return writer.finishWithTrailer(ProtoMsg.SC_BOX_ATTR_CHANGE_NOTI_PACKET);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int worldNumber, Box box, int attribute) {
        int proto18_worldNumber = 0;
        Box proto18_box = null;
        int proto18_attribute = 0;

            proto18_worldNumber = worldNumber;
            proto18_box = box;
            proto18_attribute = attribute;

        return sendFields(protoRecipient, proto18_worldNumber, proto18_box, proto18_attribute);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int worldNumber, Box box, int attribute) { return sendCtor(protoRecipient, worldNumber, box, attribute); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int worldNumber, Box box, int attribute) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), worldNumber, box, attribute); }

}
