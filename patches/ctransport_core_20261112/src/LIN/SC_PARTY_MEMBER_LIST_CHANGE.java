package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_PARTY_MEMBER_LIST_CHANGE (0x0338)
 *  1 leader_name, 2 out_user, 3 goto_inter_server_user, 4 new_user(PartyMember)
 */
public final class SC_PARTY_MEMBER_LIST_CHANGE extends ProtoPacket {

    private final String _leaderName, _outUser, _gotoInterServerUser;
    private final LIN.SC_PARTY_MEMBER_LIST.Member _newUser;

    private SC_PARTY_MEMBER_LIST_CHANGE(String leaderName, String outUser,
            String gotoInterServerUser, LIN.SC_PARTY_MEMBER_LIST.Member newUser) {
        _leaderName = leaderName;
        _outUser = outUser;
        _gotoInterServerUser = gotoInterServerUser;
        _newUser = newUser;
    }

    public static LIN.SC_PARTY_MEMBER_LIST_CHANGE ofNewMember(LIN.SC_PARTY_MEMBER_LIST.Member m) {
        return new LIN.SC_PARTY_MEMBER_LIST_CHANGE(null, null, null, m);
    }

    public static LIN.SC_PARTY_MEMBER_LIST_CHANGE ofOutUser(String name) {
        return new LIN.SC_PARTY_MEMBER_LIST_CHANGE(null, name, null, null);
    }
    
    public static LIN.SC_PARTY_MEMBER_LIST_CHANGE ofLeaderChange(String newLeaderName) {
        return new LIN.SC_PARTY_MEMBER_LIST_CHANGE(newLeaderName, null, null, null);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_MEMBER_LIST_CHANGE; }

    @Override
    protected void body(ProtoWriter w) {
        if (_leaderName != null)          w.string(1, _leaderName);
        if (_outUser != null)             w.string(2, _outUser);
        if (_gotoInterServerUser != null) w.string(3, _gotoInterServerUser);
        if (_newUser != null) {
            w.msg(4);
            _newUser.body(w);
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, String _leaderName, String _outUser, String _gotoInterServerUser, LIN.SC_PARTY_MEMBER_LIST.Member _newUser) {
        if (_leaderName != null) w.string(1, _leaderName);
            if (_outUser != null) w.string(2, _outUser);
            if (_gotoInterServerUser != null) w.string(3, _gotoInterServerUser);
            if (_newUser != null) {
                w.msg(4);
                _newUser.body(w);
                w.end();
            }
    }

    public static boolean sendFields(
            L1PcInstance recipient, String _leaderName, String _outUser, String _gotoInterServerUser,
            LIN.SC_PARTY_MEMBER_LIST.Member _newUser) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _leaderName, _outUser, _gotoInterServerUser, _newUser);
    }

    public static boolean sendFields(
            CIOClient recipient, String _leaderName, String _outUser, String _gotoInterServerUser,
            LIN.SC_PARTY_MEMBER_LIST.Member _newUser) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_MEMBER_LIST_CHANGE, Proto.capacityBound(512L + (_leaderName == null ? 0L : 2L * _leaderName.length()) + (_outUser == null ? 0L : 2L * _outUser.length()) + (_gotoInterServerUser == null ? 0L : 2L * _gotoInterServerUser.length()) + (_newUser == null || _newUser.name == null ? 0L : 2L * _newUser.name.length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _leaderName, _outUser, _gotoInterServerUser, _newUser);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(String _leaderName, String _outUser, String _gotoInterServerUser, LIN.SC_PARTY_MEMBER_LIST.Member _newUser) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _leaderName, _outUser, _gotoInterServerUser, _newUser);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_MEMBER_LIST_CHANGE);
    }

    private static boolean sendCtor(
            CIOClient protoRecipient, String leaderName, String outUser, String gotoInterServerUser,
            LIN.SC_PARTY_MEMBER_LIST.Member newUser) {
        String proto50_leaderName = null;
        String proto50_outUser = null;
        String proto50_gotoInterServerUser = null;
        LIN.SC_PARTY_MEMBER_LIST.Member proto50_newUser = null;

            proto50_leaderName = leaderName;
            proto50_outUser = outUser;
            proto50_gotoInterServerUser = gotoInterServerUser;
            proto50_newUser = newUser;

        return sendFields(protoRecipient, proto50_leaderName, proto50_outUser, proto50_gotoInterServerUser, proto50_newUser);
    }

    public static boolean sendOfNewMember(CIOClient protoRecipient, LIN.SC_PARTY_MEMBER_LIST.Member m) {
        return sendCtor(protoRecipient, null, null, null, m);
    }

    public static boolean sendOfNewMember(L1PcInstance protoRecipient, LIN.SC_PARTY_MEMBER_LIST.Member m) {
        return sendOfNewMember(protoRecipient == null ? null : protoRecipient.getNetConnection(), m);
    }

    public static boolean sendOfOutUser(CIOClient protoRecipient, String name) {
        return sendCtor(protoRecipient, null, name, null, null);
    }

    public static boolean sendOfOutUser(L1PcInstance protoRecipient, String name) {
        return sendOfOutUser(protoRecipient == null ? null : protoRecipient.getNetConnection(), name);
    }

    public static boolean sendOfLeaderChange(CIOClient protoRecipient, String newLeaderName) {
        return sendCtor(protoRecipient, newLeaderName, null, null, null);
    }

    public static boolean sendOfLeaderChange(L1PcInstance protoRecipient, String newLeaderName) {
        return sendOfLeaderChange(protoRecipient == null ? null : protoRecipient.getNetConnection(), newLeaderName);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, String leaderName, String outUser, String gotoInterServerUser,
            LIN.SC_PARTY_MEMBER_LIST.Member newUser) { return sendCtor(protoRecipient, leaderName, outUser, gotoInterServerUser, newUser); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, String leaderName, String outUser, String gotoInterServerUser,
            LIN.SC_PARTY_MEMBER_LIST.Member newUser) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), leaderName, outUser, gotoInterServerUser, newUser); }

}
