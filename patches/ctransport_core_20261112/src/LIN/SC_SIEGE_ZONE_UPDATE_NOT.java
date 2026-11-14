package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_SIEGE_ZONE_UPDATE_NOT (0x0042) — 공성존 전환 알림
 *  1 siege_zone_kind: 1=BEGIN, 2=END  [required]
 */
public final class SC_SIEGE_ZONE_UPDATE_NOT extends ProtoPacket {

    public enum SIEGE_ZONE_KIND {
        BEGIN(1),
        END(2);

        private final int v;
        SIEGE_ZONE_KIND(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND _kind;

    public SC_SIEGE_ZONE_UPDATE_NOT(LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND kind) {
        _kind = kind;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_SIEGE_ZONE_UPDATE_NOT; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _kind.toInt());
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND _kind) {
        w.int32(1, _kind.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND _kind) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _kind);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND _kind) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SIEGE_ZONE_UPDATE_NOT, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _kind);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND _kind) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _kind);
        return writer.finishWithTrailer(ProtoMsg.SC_SIEGE_ZONE_UPDATE_NOT);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND kind) {
        LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND proto59_kind = null;

            proto59_kind = kind;

        return sendFields(protoRecipient, proto59_kind);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND kind) { return sendCtor(protoRecipient, kind); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND kind) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), kind); }

}
