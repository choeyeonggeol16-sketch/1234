package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_MONSTER_BOOK_V2_TELEPORT_ACK (0x0330) — 도감 주간퀘스트 텔레포트 결과.
 * 와이어: deck_index(f1, varint), card_index(f2, varint), result(f3, enum)
 * eResultCode는 0/1/2/3 체계 — REWARD_ACK(1/112~115)와 체계가 달라 공용 재사용 불가, 자체 중첩.
 */
public final class SC_MONSTER_BOOK_V2_TELEPORT_ACK extends ProtoPacket {

    public enum eResultCode {
        TELEPORT_SUCCESS(0),
        TELEPORT_FAIL(1),
        TELEPORT_FAIL_NOT_ENOUGH_ADENA(2),
        TELEPORT_FAIL_WRONG_LOCATION(3);

        private final int value;
        eResultCode(int val) { value = val; }
        public int toInt() { return value; }

        public static LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode fromInt(int i) {
            switch (i) {
            case 0: return TELEPORT_SUCCESS;
            case 1: return TELEPORT_FAIL;
            case 2: return TELEPORT_FAIL_NOT_ENOUGH_ADENA;
            case 3: return TELEPORT_FAIL_WRONG_LOCATION;
            default: return null;
            }
        }

        /**
         * 구 SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode.modulation() 계승.
         * 도감 텔레포트 결과코드 → V2 텔레포트 결과코드 (매핑값 구와 1:1 동일)
         */
        public static LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode from(LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode r) {
            if (r == null) return TELEPORT_FAIL;
            switch (r.toInt()) {
            case 0: return TELEPORT_SUCCESS;
            case 2: return TELEPORT_FAIL_NOT_ENOUGH_ADENA;
            case 3: return TELEPORT_FAIL_WRONG_LOCATION;
            default: return TELEPORT_FAIL;
            }
        }
    }

    private int _deckIndex;
    private int _cardIndex;
    private LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode _result;

    private SC_MONSTER_BOOK_V2_TELEPORT_ACK() {}

    public static LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK newInstance() {
        return new LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK();
    }

    public static LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK newInstance(int deckIndex, int cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode result) {
        final LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK p = newInstance();
        p.set_deck_index(deckIndex);
        p.set_card_index(cardIndex);
        p.set_result(result);
        return p;
    }

    public static void send(L1PcInstance pc, int deckIndex, int cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode resultCode) {
        if (pc == null) return;
        LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.sendNewInstance(pc, deckIndex, cardIndex, resultCode);
    }

    public int deck_index()             { return _deckIndex; }
    public void set_deck_index(int val)     { _deckIndex = val; }
    public int card_index()             { return _cardIndex; }
    public void set_card_index(int val)     { _cardIndex = val; }
    public LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode result()         { return _result; }
    public void set_result(LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode val) { _result = val; }

    @Override
    public int msgId() { return ProtoMsg.SC_MONSTER_BOOK_V2_TELEPORT_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _deckIndex);
        w.uint32(2, _cardIndex);
        if (_result != null) w.enumV(3, _result.toInt());
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _deckIndex, int _cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode _result) {
        w.uint32(1, _deckIndex);
            w.uint32(2, _cardIndex);
            if (_result != null) w.enumV(3, _result.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, int _deckIndex, int _cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode _result) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _deckIndex, _cardIndex, _result);
    }

    public static boolean sendFields(CIOClient recipient, int _deckIndex, int _cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode _result) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_TELEPORT_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _deckIndex, _cardIndex, _result);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _deckIndex, int _cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode _result) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _deckIndex, _cardIndex, _result);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_TELEPORT_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto40_deckIndex = 0;
        int proto40_cardIndex = 0;
        LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode proto40_result = null;


        return sendFields(protoRecipient, proto40_deckIndex, proto40_cardIndex, proto40_result);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, int deckIndex, int cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode result) {
        int pWire_deckIndex = 0;
                int pWire_cardIndex = 0;
                LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode pWire_result = null;


                { pWire_deckIndex = deckIndex; }
                { pWire_cardIndex = cardIndex; }
                { pWire_result = result; }
                return LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.sendFields(protoRecipient, pWire_deckIndex, pWire_cardIndex, pWire_result);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, int deckIndex, int cardIndex, LIN.SC_MONSTER_BOOK_V2_TELEPORT_ACK.eResultCode result) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), deckIndex, cardIndex, result);
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
