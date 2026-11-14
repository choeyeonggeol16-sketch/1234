package LIN;

import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_NOTIFICATION_MESSAGE (0x013C) — 화면 알림 메시지
 *  1 pos enum [required], 2 desc string [required], 3 messageRGB bytes [required],
 *  4 duration int32 [required], 5 serverno int32 [has]
 */
public final class SC_NOTIFICATION_MESSAGE extends ProtoPacket {

    public enum display_position {
        SCREEN_TOP(1), SCREEN_MIDDLE(2), SCREEN_BOTTOM(3);
        private final int v;
        display_position(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final int _pos;
    private final String _desc;
    private final byte[] _messageRGB;
    private final int _duration;
    private final int _serverno;
    private final boolean _hasServerno;

    private SC_NOTIFICATION_MESSAGE(int pos, String desc, byte[] messageRGB,
            int duration, int serverno, boolean hasServerno) {
        _pos = pos;
        _desc = desc;
        _messageRGB = messageRGB;
        _duration = duration;
        _serverno = serverno;
        _hasServerno = hasServerno;
    }

    /** MJ newInstance(message, rgb, duration) — screen_top 고정, serverno 미사용 */
    public static LIN.SC_NOTIFICATION_MESSAGE of(String message, MJSimpleRgb rgb, int duration){
        return new LIN.SC_NOTIFICATION_MESSAGE(
                LIN.SC_NOTIFICATION_MESSAGE.display_position.SCREEN_TOP.toInt(), message, rgb.get_bytes(), duration, 0, false);
    }

    public static LIN.SC_NOTIFICATION_MESSAGE of(String message, MJSimpleRgb rgb){
        return of(message, rgb, 60);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_NOTIFICATION_MESSAGE; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _pos)
         .string(2, _desc)
         .bytes(3, _messageRGB)
         .int32(4, _duration);
        if (_hasServerno)
            w.int32(5, _serverno);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _pos, String _desc, byte[] _messageRGB, int _duration, int _serverno, boolean _hasServerno) {
        w.int32(1, _pos).string(2, _desc).bytes(3, _messageRGB).int32(4, _duration);
            if (_hasServerno) w.int32(5, _serverno);
    }

    public static boolean sendFields(L1PcInstance recipient, int _pos, String _desc, byte[] _messageRGB, int _duration, int _serverno, boolean _hasServerno) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _pos, _desc, _messageRGB, _duration, _serverno, _hasServerno);
    }

    public static boolean sendFields(CIOClient recipient, int _pos, String _desc, byte[] _messageRGB, int _duration, int _serverno, boolean _hasServerno) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_NOTIFICATION_MESSAGE, Proto.capacityBound(1024L + (_desc == null ? 0L : 2L * _desc.length()) + (_messageRGB == null ? 0L : _messageRGB.length)));
        if (writer == null) return false;
        try {
            writeFields(writer, _pos, _desc, _messageRGB, _duration, _serverno, _hasServerno);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _pos, String _desc, byte[] _messageRGB, int _duration, int _serverno, boolean _hasServerno) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _pos, _desc, _messageRGB, _duration, _serverno, _hasServerno);
        return writer.finishWithTrailer(ProtoMsg.SC_NOTIFICATION_MESSAGE);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int pos, String desc, byte[] messageRGB, int duration, int serverno, boolean hasServerno) {
        int proto47_pos = 0;
        String proto47_desc = null;
        byte[] proto47_messageRGB = null;
        int proto47_duration = 0;
        int proto47_serverno = 0;
        boolean proto47_hasServerno = false;

            proto47_pos = pos;
            proto47_desc = desc;
            proto47_messageRGB = messageRGB;
            proto47_duration = duration;
            proto47_serverno = serverno;
            proto47_hasServerno = hasServerno;

        return sendFields(protoRecipient, proto47_pos, proto47_desc, proto47_messageRGB, proto47_duration, proto47_serverno, proto47_hasServerno);
    }

    public static boolean sendOf(CIOClient protoRecipient, String message, MJSimpleRgb rgb, int duration) {
        return sendCtor(protoRecipient, LIN.SC_NOTIFICATION_MESSAGE.display_position.SCREEN_TOP.toInt(), message, rgb.get_bytes(), duration, 0, false);
    }

    public static boolean sendOf(L1PcInstance protoRecipient, String message, MJSimpleRgb rgb, int duration) {
        return sendOf(protoRecipient == null ? null : protoRecipient.getNetConnection(), message, rgb, duration);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int pos, String desc, byte[] messageRGB, int duration, int serverno,
            boolean hasServerno) { return sendCtor(protoRecipient, pos, desc, messageRGB, duration, serverno, hasServerno); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int pos, String desc, byte[] messageRGB, int duration, int serverno,
            boolean hasServerno) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), pos, desc, messageRGB, duration, serverno, hasServerno); }

}
