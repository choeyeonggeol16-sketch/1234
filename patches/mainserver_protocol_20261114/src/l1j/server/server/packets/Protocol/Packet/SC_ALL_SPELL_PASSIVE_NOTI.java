package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import java.util.ArrayList;
import java.util.List;

import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_ALL_SPELL_PASSIVE_NOTI (0x0191) — 보유 패시브 전체 동기화
 *  1 passives repeated { 1 passive_id [required], 2 param }
 */
public final class SC_ALL_SPELL_PASSIVE_NOTI extends ProtoPacket {

    public static final class SpellPassiveData {
        private int _passiveId;
        private int _param;
        private boolean _hasParam;

        private SpellPassiveData() {}

        public static SpellPassiveData of(int passiveId){
            SpellPassiveData d = new SpellPassiveData();
            d._passiveId = passiveId;
            return d;
        }

        public static SpellPassiveData of(int passiveId, int param){
            SpellPassiveData d = of(passiveId);
            d._param = param;
            d._hasParam = true;
            return d;
        }

        public int get_passiveId(){ return _passiveId; }
        public int get_param(){ return _param; }

        void body(ProtoWriter w){
            w.int32(1, _passiveId);        // required — 항상
            if (_hasParam) w.int32(2, _param);
        }
    }

    private final List<SpellPassiveData> _passives = new ArrayList<>();

    public SC_ALL_SPELL_PASSIVE_NOTI() {}

    public SC_ALL_SPELL_PASSIVE_NOTI add(int passiveId){
        _passives.add(SpellPassiveData.of(passiveId));
        return this;
    }

    public SC_ALL_SPELL_PASSIVE_NOTI add(int passiveId, int param){
        _passives.add(SpellPassiveData.of(passiveId, param));
        return this;
    }

    public SC_ALL_SPELL_PASSIVE_NOTI add(SpellPassiveData data){
        _passives.add(data);
        return this;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ALL_SPELL_PASSIVE_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        for (SpellPassiveData d : _passives) {
            w.msg(1);
            d.body(w);
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<SpellPassiveData> _passives) {
        for (SpellPassiveData d : _passives) {
                w.msg(1);
                d.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<SpellPassiveData> _passives) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _passives);
    }

    public static boolean sendFields(CIOClient recipient, List<SpellPassiveData> _passives) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ALL_SPELL_PASSIVE_NOTI, Proto.capacityBound(128L + (_passives == null ? 0L : 64L * _passives.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _passives);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<SpellPassiveData> _passives) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _passives);
        return writer.finishWithTrailer(ProtoMsg.SC_ALL_SPELL_PASSIVE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        List<SpellPassiveData> proto12_passives = new ArrayList<>();


        return sendFields(protoRecipient, proto12_passives);
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }


    public static boolean sendPassives(L1PcInstance recipient,
            List<l1j.server.MJPassiveSkill.MJPassiveInfo> passives) {
        return sendPassives(recipient == null ? null : recipient.getNetConnection(), passives);
    }

    public static boolean sendPassives(CIOClient recipient,
            List<l1j.server.MJPassiveSkill.MJPassiveInfo> passives) {
        final int count = passives.size();
        ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ALL_SPELL_PASSIVE_NOTI,
                Proto.capacityBound(32L + 32L * count));
        if (writer == null) return false;
        try {
            for (int i = 0; i < count; i++) {
                int id = passives.get(i).getPassiveId();
                writer.msg(1).int32(1, id);
                if (id == l1j.server.MJPassiveSkill.MJPassiveID.ARMOR_GUARD.toInt()) writer.int32(2, 10);
                writer.end();
            }
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }
}
