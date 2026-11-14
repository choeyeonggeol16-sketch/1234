package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.MonsterBookV2Info;

/**
 * SC_MONSTER_BOOK_V2_INFO_NOTI (0x032A) — 몬스터북 V2 전체 정보 통지.
 * 와이어: info(f1, LEN 서브메시지 → MonsterBookV2Info)
 * ⚠ info 필드번호 1 가정 — generated readFrom에서 case 0x0000000A(=field 1) 확인.
 *   틀리면 body()의 1만 고치면 끝.
 */
public final class SC_MonsterBookV2InfoNoti extends ProtoPacket {

    private MonsterBookV2Info _info;

    private SC_MonsterBookV2InfoNoti() {}

    public static SC_MonsterBookV2InfoNoti newInstance() {
        return new SC_MonsterBookV2InfoNoti();
    }

    public static void send(L1PcInstance pc, MonsterBookV2Info info) {
        if (pc == null || info == null) return;
        MonsterBookV2Info pWire_info = null;


        { pWire_info = info; }
        SC_MonsterBookV2InfoNoti.sendFields(pc, pWire_info);
    }

    public MonsterBookV2Info get_info()          { return _info; }
    public void set_info(MonsterBookV2Info val)  { _info = val; }

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
    public static void writeFields(ProtoWriter w, MonsterBookV2Info _info) {
        if (_info == null) return;
            w.msg(1);
            _info.saveBody(w);
            w.end();
    }

    public static boolean sendFields(L1PcInstance recipient, MonsterBookV2Info _info) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _info);
    }

    public static boolean sendFields(CIOClient recipient, MonsterBookV2Info _info) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_INFO_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _info);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(MonsterBookV2Info _info) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _info);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_INFO_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        MonsterBookV2Info proto35_info = null;


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
