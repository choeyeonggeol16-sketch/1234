package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI (0x032D) — 도감 카드 처치 수량 갱신 통지.
 * 와이어: deck_index(f1, varint), card_index(f2, varint), amount(f3, varint)
 * 처치 경로(뜨거운 경로)에서 발신됨.
 */
public final class SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI extends ProtoPacket {

    private int _deckIndex;
    private int _cardIndex;
    private int _amount;

    private SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI() {}

    public static SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI newInstance() {
        return new SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI();
    }

    public static void send(L1PcInstance pc, int deckIndex, int cardIndex, int amount) {
        if (pc == null) return;

        int pWire_deckIndex = 0;
        int pWire_cardIndex = 0;
        int pWire_amount = 0;


        { pWire_deckIndex = deckIndex; }
        { pWire_cardIndex = cardIndex; }
        { pWire_amount = amount; }
        SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI.sendFields(pc, pWire_deckIndex, pWire_cardIndex, pWire_amount);
    }

    public int get_deck_index()          { return _deckIndex; }
    public void set_deck_index(int val)  { _deckIndex = val; }
    public int get_card_index()          { return _cardIndex; }
    public void set_card_index(int val)  { _cardIndex = val; }
    public int get_amount()              { return _amount; }
    public void set_amount(int val)      { _amount = val; }

    @Override
    public int msgId() { return ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _deckIndex);
        w.uint32(2, _cardIndex);
        w.uint32(3, _amount);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _deckIndex, int _cardIndex, int _amount) {
        w.uint32(1, _deckIndex);
            w.uint32(2, _cardIndex);
            w.uint32(3, _amount);
    }

    public static boolean sendFields(L1PcInstance recipient, int _deckIndex, int _cardIndex, int _amount) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _deckIndex, _cardIndex, _amount);
    }

    public static boolean sendFields(CIOClient recipient, int _deckIndex, int _cardIndex, int _amount) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _deckIndex, _cardIndex, _amount);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _deckIndex, int _cardIndex, int _amount) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _deckIndex, _cardIndex, _amount);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto44_deckIndex = 0;
        int proto44_cardIndex = 0;
        int proto44_amount = 0;


        return sendFields(protoRecipient, proto44_deckIndex, proto44_cardIndex, proto44_amount);
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
