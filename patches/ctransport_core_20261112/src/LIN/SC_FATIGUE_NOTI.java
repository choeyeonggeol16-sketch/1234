package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * 그랑카인 시스템 
 * SC_FATIGUE_NOTI (0x014E) — 피로도/아인하사드 상태
 *  1 fatigue_status enum [required]  2 penalty_level int32 [required]
 *  3 combat_fatigue uint64 [required]
 */
public final class SC_FATIGUE_NOTI extends ProtoPacket {

    public enum FATIGUE_STATUS {
        OK(1), FAIL(2), NOT_USE(3);
        private final int v;
        FATIGUE_STATUS(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS _status;
    private final int _penaltyLevel;
    private final long _combatFatigue;

    public SC_FATIGUE_NOTI(LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS status, int penaltyLevel, long combatFatigue) {
        _status = status;
        _penaltyLevel = penaltyLevel;
        _combatFatigue = combatFatigue;
    }

    /** MJ offEinhasad 이관 — 아인하사드 해제 2단 전송 (penalty=1 → penalty=0) */
    public static void offEinhasad(L1PcInstance pc){
        LIN.SC_FATIGUE_NOTI.sendConstructed(pc, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS.OK, 1, 0);   // ein off
        LIN.SC_FATIGUE_NOTI.sendConstructed(pc, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS.OK, 0, 0);   // fatigue off
    }

    @Override
    public int msgId() { return ProtoMsg.SC_FATIGUE_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _status.toInt())
         .int32(2, _penaltyLevel)
         .uint64(3, _combatFatigue);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS _status, int _penaltyLevel, long _combatFatigue) {
        w.int32(1, _status.toInt()).int32(2, _penaltyLevel).uint64(3, _combatFatigue);
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS _status, int _penaltyLevel, long _combatFatigue) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _status, _penaltyLevel, _combatFatigue);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS _status, int _penaltyLevel, long _combatFatigue) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_FATIGUE_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _status, _penaltyLevel, _combatFatigue);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS _status, int _penaltyLevel, long _combatFatigue) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _status, _penaltyLevel, _combatFatigue);
        return writer.finishWithTrailer(ProtoMsg.SC_FATIGUE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS status, int penaltyLevel, long combatFatigue) {
        LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS proto32_status = null;
        int proto32_penaltyLevel = 0;
        long proto32_combatFatigue = 0;

            proto32_status = status;
            proto32_penaltyLevel = penaltyLevel;
            proto32_combatFatigue = combatFatigue;

        return sendFields(protoRecipient, proto32_status, proto32_penaltyLevel, proto32_combatFatigue);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS status, int penaltyLevel, long combatFatigue) { return sendCtor(protoRecipient, status, penaltyLevel, combatFatigue); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, LIN.SC_FATIGUE_NOTI.FATIGUE_STATUS status, int penaltyLevel, long combatFatigue) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), status, penaltyLevel, combatFatigue); }

}
