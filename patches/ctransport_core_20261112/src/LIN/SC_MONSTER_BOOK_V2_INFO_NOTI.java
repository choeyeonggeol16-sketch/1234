package LIN;

import LIN.MonsterBookV2Info;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_MONSTER_BOOK_V2_INFO_NOTI (0x032A) — 몬스터북 V2 전체 정보 통지.
 * 와이어: info(f1, LEN 서브메시지 → MonsterBookV2Info)
 * ⚠ info 필드번호 1 가정 — generated readFrom에서 case 0x0000000A(=field 1) 확인.
 *   틀리면 body()의 1만 고치면 끝.
 */
public final class SC_MONSTER_BOOK_V2_INFO_NOTI extends ProtoPacket {

    private LIN.MonsterBookV2Info _info;

    private SC_MONSTER_BOOK_V2_INFO_NOTI() {}

    public static LIN.SC_MONSTER_BOOK_V2_INFO_NOTI newInstance() {
        return new LIN.SC_MONSTER_BOOK_V2_INFO_NOTI();
    }

    public static void send(L1PcInstance pc, LIN.MonsterBookV2Info info) {
        if (pc == null || info == null) return;
        LIN.MonsterBookV2Info pWire_info = null;


        { pWire_info = info; }
        LIN.SC_MONSTER_BOOK_V2_INFO_NOTI.sendFields(pc, pWire_info);
    }

    public LIN.MonsterBookV2Info info()          { return _info; }
    public void set_info(LIN.MonsterBookV2Info val)  { _info = val; }

    @Override
    public int msgId() { return ProtoMsg.SC_MONSTER_BOOK_V2_INFO_NOTI; }  

    @Override
    protected void body(ProtoWriter w) {
        if (_info == null) return;
        w.msg(1);
        _info.saveBody(w);
        w.end();
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.MonsterBookV2Info _info) {
        if (_info == null) return;
            w.msg(1);
            _info.saveBody(w);
            w.end();
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.MonsterBookV2Info _info) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _info);
    }

    public static boolean sendFields(CIOClient recipient, LIN.MonsterBookV2Info _info) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_INFO_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _info);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.MonsterBookV2Info _info) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _info);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_INFO_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        LIN.MonsterBookV2Info proto35_info = null;


        return sendFields(protoRecipient, proto35_info);
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
