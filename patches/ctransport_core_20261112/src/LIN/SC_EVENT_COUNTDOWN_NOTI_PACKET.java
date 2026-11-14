package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_EVENT_COUNTDOWN_NOTI (0x021C) — 이벤트 카운트다운
 *  1 remain_time uint32 [required]  2 event_desc bytes(MS949) [has]
 */
public final class SC_EVENT_COUNTDOWN_NOTI_PACKET extends ProtoPacket {

    private final int _remainTime;
    private final byte[] _eventDesc;

    private SC_EVENT_COUNTDOWN_NOTI_PACKET(int remainTime, byte[] eventDesc) {
        _remainTime = remainTime;
        _eventDesc = eventDesc;
    }

    /** MJ send() 이관  */
    public static void send(L1PcInstance pc, int remainSeconds, String desc){
        LIN.SC_EVENT_COUNTDOWN_NOTI_PACKET.sendCreate(pc, remainSeconds, desc);
    }

    /** MJ create() 이관 */
    public static LIN.SC_EVENT_COUNTDOWN_NOTI_PACKET create(int remainSeconds, String desc){
        byte[] descBytes = null;
        if (desc != null) {
            try {
                descBytes = desc.getBytes("MS949");
            } catch (java.io.UnsupportedEncodingException e) {
                descBytes = desc.getBytes();
            }
        }
        return new LIN.SC_EVENT_COUNTDOWN_NOTI_PACKET(remainSeconds, descBytes);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_EVENT_COUNTDOWN_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _remainTime);       // required — 항상
        if (_eventDesc != null)
            w.bytes(2, _eventDesc);     // has — desc 없으면 생략
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _remainTime, byte[] _eventDesc) {
        w.uint32(1, _remainTime);
            if (_eventDesc != null) w.bytes(2, _eventDesc);
    }

    public static boolean sendFields(L1PcInstance recipient, int _remainTime, byte[] _eventDesc) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _remainTime, _eventDesc);
    }

    public static boolean sendFields(CIOClient recipient, int _remainTime, byte[] _eventDesc) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_EVENT_COUNTDOWN_NOTI, Proto.capacityBound(1024L + (_eventDesc == null ? 0L : _eventDesc.length)));
        if (writer == null) return false;
        try {
            writeFields(writer, _remainTime, _eventDesc);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _remainTime, byte[] _eventDesc) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _remainTime, _eventDesc);
        return writer.finishWithTrailer(ProtoMsg.SC_EVENT_COUNTDOWN_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int remainTime, byte[] eventDesc) {
        int proto31_remainTime = 0;
        byte[] proto31_eventDesc = null;

            proto31_remainTime = remainTime;
            proto31_eventDesc = eventDesc;

        return sendFields(protoRecipient, proto31_remainTime, proto31_eventDesc);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int remainSeconds, String desc) {
        byte[] descBytes = null;
                if (desc != null) {
                    try {
                        descBytes = desc.getBytes("MS949");
                    } catch (java.io.UnsupportedEncodingException e) {
                        descBytes = desc.getBytes();
                    }
                }
                return sendCtor(protoRecipient, remainSeconds, descBytes);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int remainSeconds, String desc) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), remainSeconds, desc);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int remainTime, byte[] eventDesc) { return sendCtor(protoRecipient, remainTime, eventDesc); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int remainTime, byte[] eventDesc) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), remainTime, eventDesc); }

}
