package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.utils.MJCommons;

/**
 * SC_COMPANION_NAME_CHANGE_ACK (0x07D2) — 컴패니언 이름 변경 결과
 * wire: [1]=result enum(varint), [2]=new_name bytes(옵션, MS949)
 */
public final class SC_CompanionNameChangeAck extends ProtoPacket {

    public enum eResult {
        Success(0),
        SameNameExisted(1),
        SystemError(2);

        private final int value;
        eResult(int val) { value = val; }
        public int toInt() { return value; }

        public static eResult fromInt(int i) {
            switch (i) {
            case 0: return Success;
            case 1: return SameNameExisted;
            case 2: return SystemError;
            default: return null;
            }
        }
    }

    private eResult _result;
    private byte[] _newName;

    private SC_CompanionNameChangeAck() {}

    public static SC_CompanionNameChangeAck newInstance() {
        return new SC_CompanionNameChangeAck();
    }

    /** 구 시그니처 동일 — (String → MS949 bytes 변환 후 bytes 경로) */
    public static void send(CIOClient clnt, eResult result, String newName) {
        byte[] buff = null;
        if (!MJCommons.isNullOrEmpty(newName)) {
            try {
                buff = newName.getBytes("MS949");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        send_bytes(clnt, result, buff);
    }

    /** 구 시그니처 동일 — 핸들러가 원본 바이트 패스스루할 때 사용 */
    public static void send_bytes(CIOClient clnt, eResult result, byte[] newName) {
        if (clnt == null || result == null) return;
        eResult ackWire_result = null;
        byte[] ackWire_newName = null;


        { ackWire_result = result; }
        if (newName != null) {
            { ackWire_newName = newName; }
        }
        SC_CompanionNameChangeAck.sendFields(clnt.getActiveChar(), ackWire_result, ackWire_newName);
    }

    public eResult get_result()             { return _result; }
    public void set_result(eResult val)     { _result = val; }
    public boolean has_result()             { return _result != null; }
    public byte[] get_new_name()            { return _newName; }
    public void set_new_name(byte[] val)    { _newName = val; }
    public boolean has_new_name()           { return _newName != null; }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_NAME_CHANGE_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        if (_result != null) {
            w.enumV(1, _result.toInt());        // 구: writeEnum(1, ...)
        }
        if (_newName != null) {
            w.bytes(2, _newName);               // 구: writeBytes(2, ...)
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, eResult _result, byte[] _newName) {
        if (_result != null) {
                w.enumV(1, _result.toInt());
            }
            if (_newName != null) {
                w.bytes(2, _newName);
            }
    }

    public static boolean sendFields(L1PcInstance recipient, eResult _result, byte[] _newName) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _result, _newName);
    }

    public static boolean sendFields(CIOClient recipient, eResult _result, byte[] _newName) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_NAME_CHANGE_ACK, Proto.capacityBound(1024L + (_newName == null ? 0L : _newName.length)));
        if (writer == null) return false;
        try {
            writeFields(writer, _result, _newName);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(eResult _result, byte[] _newName) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _result, _newName);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_NAME_CHANGE_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        eResult proto22_result = null;
        byte[] proto22_newName = null;


        return sendFields(protoRecipient, proto22_result, proto22_newName);
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
