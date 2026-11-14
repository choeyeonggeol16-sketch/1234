package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_SIEGE_INJURY_TIME_NOT (0x004C) — 공성 남은시간/수성혈맹 알림
 *  1 siege_kind   enum: 1=수성(DEFENCE), 2=공성(ATTACK)  [required]
 *  2 remain_second sint32 (zigzag)                      [required]
 *  3 pledge_name   string (MS949)
 */
public final class SC_SIEGE_INJURY_TIME_NOIT extends ProtoPacket {

    public enum SIEGE_KIND {
        DEFENCE(1),
        ATTACK(2);

        private final int v;
        SIEGE_KIND(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND _kind;
    private final int _remainSecond;
    private final String _pledgeName;

    public SC_SIEGE_INJURY_TIME_NOIT(LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND kind, int remainSecond, String pledgeName) {
        _kind = kind;
        _remainSecond = remainSecond;
        _pledgeName = pledgeName;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_SIEGE_INJURY_TIME_NOT; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _kind.toInt())
         .sint32(2, _remainSecond)      
         .string(3, _pledgeName);     
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND _kind, int _remainSecond, String _pledgeName) {
        w.int32(1, _kind.toInt()).sint32(2, _remainSecond).string(3, _pledgeName);
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND _kind, int _remainSecond, String _pledgeName) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _kind, _remainSecond, _pledgeName);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND _kind, int _remainSecond, String _pledgeName) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SIEGE_INJURY_TIME_NOT, Proto.capacityBound(1024L + (_pledgeName == null ? 0L : 2L * _pledgeName.length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _kind, _remainSecond, _pledgeName);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND _kind, int _remainSecond, String _pledgeName) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _kind, _remainSecond, _pledgeName);
        return writer.finishWithTrailer(ProtoMsg.SC_SIEGE_INJURY_TIME_NOT);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND kind, int remainSecond, String pledgeName) {
        LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND proto58_kind = null;
        int proto58_remainSecond = 0;
        String proto58_pledgeName = null;

            proto58_kind = kind;
            proto58_remainSecond = remainSecond;
            proto58_pledgeName = pledgeName;

        return sendFields(protoRecipient, proto58_kind, proto58_remainSecond, proto58_pledgeName);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND kind, int remainSecond, String pledgeName) { return sendCtor(protoRecipient, kind, remainSecond, pledgeName); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND kind, int remainSecond, String pledgeName) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), kind, remainSecond, pledgeName); }

}
