package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.MonsterBookV2Info;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.eMonsterBookV2DeckState;

/**
 * SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI (0x032E) — 도감 덱 상태 갱신 통지.
 * 와이어: deck_index(f1, varint), state(f2, enum)
 */
public final class SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI extends ProtoPacket {

    private int _deckIndex;
    private eMonsterBookV2DeckState _state;

    private SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI() {}

    public static SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI newInstance() {
        return new SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI();
    }

    public static void send(L1PcInstance pc, MonsterBookV2Info.DeckT deck) {
        if (pc == null || deck == null) return;
        
        int pWire_deckIndex = 0;
        eMonsterBookV2DeckState pWire_state = null;


        { pWire_deckIndex = (deck.get_index()); }
        { pWire_state = (deck.get_state()); }
        SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI.sendFields(pc, pWire_deckIndex, pWire_state);
    }

    public int get_deck_index()                { return _deckIndex; }
    public void set_deck_index(int val)        { _deckIndex = val; }
    public eMonsterBookV2DeckState get_state() { return _state; }
    public void set_state(eMonsterBookV2DeckState val) { _state = val; }

    @Override
    public int msgId() { return ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _deckIndex);
        if (_state != null) w.enumV(2, _state.toInt());
    }

  


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _deckIndex, eMonsterBookV2DeckState _state) {
        w.uint32(1, _deckIndex);
            if (_state != null) w.enumV(2, _state.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, int _deckIndex, eMonsterBookV2DeckState _state) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _deckIndex, _state);
    }

    public static boolean sendFields(CIOClient recipient, int _deckIndex, eMonsterBookV2DeckState _state) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _deckIndex, _state);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _deckIndex, eMonsterBookV2DeckState _state) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _deckIndex, _state);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto45_deckIndex = 0;
        eMonsterBookV2DeckState proto45_state = null;


        return sendFields(protoRecipient, proto45_deckIndex, proto45_state);
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
