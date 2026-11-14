package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.utils.eKind;

/**
 * 특수 저항/관통 통지
 */
public final class SC_SpecialResistance extends ProtoPacket {

    private static final eKind[] KINDS = eKind.values();

    private final L1PcInstance _pc;

    public SC_SpecialResistance(L1PcInstance pc) {
        _pc = pc;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_SPECIAL_RESISTANCE_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        for (eKind e : eKind.values()) {
            final int v = _pc.getResistance().getSpecialResistance(e); 
            final eKind k = e;
            w.msg(1).enumV(1, k.value()).int32(2, v).end();
        }
        for (eKind e : eKind.values()) {
            final int v = _pc.getResistance().getSpecialPierce(e);
            final eKind k = e;
            w.msg(2).enumV(1, k.value()).int32(2, v).end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, L1PcInstance _pc) {
        for (eKind e : KINDS) {
                final int v = _pc.getResistance().getSpecialResistance(e);
                final eKind k = e;
                w.msg(1).enumV(1, k.value()).int32(2, v).end();
            }
            for (eKind e : KINDS) {
                final int v = _pc.getResistance().getSpecialPierce(e);
                final eKind k = e;
                w.msg(2).enumV(1, k.value()).int32(2, v).end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, L1PcInstance _pc) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _pc);
    }

    public static boolean sendFields(CIOClient recipient, L1PcInstance _pc) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SPECIAL_RESISTANCE_NOTI, 1024);
        if (writer == null) return false;
        try {
            writeFields(writer, _pc);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(L1PcInstance _pc) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _pc);
        return writer.finishWithTrailer(ProtoMsg.SC_SPECIAL_RESISTANCE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, L1PcInstance pc) {
        L1PcInstance proto63_pc = null;

            proto63_pc = pc;

        return sendFields(protoRecipient, proto63_pc);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, L1PcInstance pc) { return sendCtor(protoRecipient, pc); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, L1PcInstance pc) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), pc); }

}
