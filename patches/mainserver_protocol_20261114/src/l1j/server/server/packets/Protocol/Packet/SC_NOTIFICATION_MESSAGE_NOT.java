package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * 화면 안내 메시지 통지 (0x0040)
 * 필드: suffileNumber(sint32), message(string), RGB(bytes), duration(sint32)
 * sint32는 zigzag 인코딩 — int32로 쓰면 값 틀어짐.
 */
public final class SC_NOTIFICATION_MESSAGE_NOT extends ProtoPacket {

    private final int _suffileNumber;
    private final String _message;
    private final byte[] _rgb;
    private final int _duration;

    private SC_NOTIFICATION_MESSAGE_NOT(int suffileNumber, String message, byte[] rgb, int duration) {
        _suffileNumber = suffileNumber;
        _message = message;
        _rgb = rgb;
        _duration = duration;
    }

    /** RGB를 byte[]로 직접 */
    public static SC_NOTIFICATION_MESSAGE_NOT of(int suffileNumber, String message, int duration, byte[] rgb) {
        return new SC_NOTIFICATION_MESSAGE_NOT(suffileNumber, message, rgb, duration);
    }

    /** RGB를 int 0xRRGGBB로 */
    public static SC_NOTIFICATION_MESSAGE_NOT of(int suffileNumber, String message, int duration, int rgb) {
        return of(suffileNumber, message, duration,
                new byte[] { (byte)(rgb >>> 16), (byte)(rgb >>> 8), (byte) rgb });
    }

    @Override
    public int msgId() { return ProtoMsg.SC_NOTIFICATION_MESSAGE_NOT; }

    @Override
    protected void body(ProtoWriter w) {
        w.sint32(1, _suffileNumber)
         .string(2, _message)
         .bytes(3, _rgb)
         .sint32(4, _duration);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _suffileNumber, String _message, byte[] _rgb, int _duration) {
        w.sint32(1, _suffileNumber).string(2, _message).bytes(3, _rgb).sint32(4, _duration);
    }

    public static boolean sendFields(L1PcInstance recipient, int _suffileNumber, String _message, byte[] _rgb, int _duration) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _suffileNumber, _message, _rgb, _duration);
    }

    public static boolean sendFields(CIOClient recipient, int _suffileNumber, String _message, byte[] _rgb, int _duration) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_NOTIFICATION_MESSAGE_NOT, Proto.capacityBound(1024L + (_message == null ? 0L : 2L * _message.length()) + (_rgb == null ? 0L : _rgb.length)));
        if (writer == null) return false;
        try {
            writeFields(writer, _suffileNumber, _message, _rgb, _duration);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _suffileNumber, String _message, byte[] _rgb, int _duration) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _suffileNumber, _message, _rgb, _duration);
        return writer.finishWithTrailer(ProtoMsg.SC_NOTIFICATION_MESSAGE_NOT);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int suffileNumber, String message, byte[] rgb, int duration) {
        int proto48_suffileNumber = 0;
        String proto48_message = null;
        byte[] proto48_rgb = null;
        int proto48_duration = 0;

            proto48_suffileNumber = suffileNumber;
            proto48_message = message;
            proto48_rgb = rgb;
            proto48_duration = duration;

        return sendFields(protoRecipient, proto48_suffileNumber, proto48_message, proto48_rgb, proto48_duration);
    }

    public static boolean sendOf(CIOClient protoRecipient, int suffileNumber, String message, int duration, byte[] rgb) {
        return sendCtor(protoRecipient, suffileNumber, message, rgb, duration);
    }

    public static boolean sendOf(L1PcInstance protoRecipient, int suffileNumber, String message, int duration, byte[] rgb) {
        return sendOf(protoRecipient == null ? null : protoRecipient.getNetConnection(), suffileNumber, message, duration, rgb);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int suffileNumber, String message, byte[] rgb, int duration) { return sendCtor(protoRecipient, suffileNumber, message, rgb, duration); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int suffileNumber, String message, byte[] rgb, int duration) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), suffileNumber, message, rgb, duration); }

}
