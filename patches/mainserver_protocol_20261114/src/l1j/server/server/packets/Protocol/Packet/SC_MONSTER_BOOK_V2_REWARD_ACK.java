package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.MonsterBook.eMonsterBookV2RewardGrade;

/**
 * SC_MONSTER_BOOK_V2_REWARD_ACK (0x032C)
 * 와이어: deck_index(f1,varint) grade(f2,enum) result(f3,enum) reward_exp_amount(f4,varint,옵션)
 * eResultCode는 공용이라 MJ enum 재사용 (BQS 이관 시 이동).
 */
public final class SC_MONSTER_BOOK_V2_REWARD_ACK extends ProtoPacket {

    private int _deckIndex;
    private eMonsterBookV2RewardGrade _grade;
    private eResultCode _result;
    private int _rewardExpAmount;
    private boolean _hasRewardExp;

    private SC_MONSTER_BOOK_V2_REWARD_ACK() {}

    // ── MJ와 동일 시그니처 (호출부 import 교체만으로 호환) ──
    public static SC_MONSTER_BOOK_V2_REWARD_ACK newInstance() {
        return new SC_MONSTER_BOOK_V2_REWARD_ACK();
    }

    public static SC_MONSTER_BOOK_V2_REWARD_ACK newInstance(int deckIndex, eMonsterBookV2RewardGrade grade, eResultCode result) {
        final SC_MONSTER_BOOK_V2_REWARD_ACK p = newInstance();
        p.set_deck_index(deckIndex);
        p.set_grade(grade);
        p.set_result(result);
        return p;
    }

    public static void send(L1PcInstance pc, int deckIndex, eMonsterBookV2RewardGrade grade, eResultCode result) {
        if (pc == null) return;
        
        SC_MONSTER_BOOK_V2_REWARD_ACK.sendNewInstance(pc, deckIndex, grade, result);
    }

    public int get_deck_index()              { return _deckIndex; }
    public void set_deck_index(int val)      { _deckIndex = val; }
    public eMonsterBookV2RewardGrade get_grade()             { return _grade; }
    public void set_grade(eMonsterBookV2RewardGrade val)     { _grade = val; }
    public eResultCode get_result()          { return _result; }
    public void set_result(eResultCode val)  { _result = val; }
    public int get_reward_exp_amount()       { return _rewardExpAmount; }
    public void set_reward_exp_amount(int val) { _rewardExpAmount = val; _hasRewardExp = true; }
    public boolean has_reward_exp_amount()   { return _hasRewardExp; }

    @Override
    public int msgId() { return ProtoMsg.SC_MONSTER_BOOK_V2_REWARD_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _deckIndex);
        if (_grade != null)  w.enumV(2, _grade.toInt());   
        if (_result != null) w.enumV(3, _result.toInt());
        if (_hasRewardExp)   w.uint32(4, _rewardExpAmount);
    }
    
    public enum eResultCode{
		REWARD_SUCCESS(1),
		REWARD_FAIL(112),
		REWARD_FAIL_INVALID_INDEX(113),
		REWARD_FAIL_NOT_ENOUGH_AMOUNT(114),
		REWARD_FAIL_NOT_ENOUGH_ITEM(115);
		private int value;
		eResultCode(int val){
			value = val;
		}
		public int toInt(){
			return value;
		}
		public boolean equals(eResultCode v){
			return value == v.value;
		}
		public static eResultCode fromInt(int i){
			switch(i){
			case 1:
				return REWARD_SUCCESS;
			case 112:
				return REWARD_FAIL;
			case 113:
				return REWARD_FAIL_INVALID_INDEX;
			case 114:
				return REWARD_FAIL_NOT_ENOUGH_AMOUNT;
			case 115:
				return REWARD_FAIL_NOT_ENOUGH_ITEM;
			default:
				return null;
			}
		}
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, int _deckIndex, eMonsterBookV2RewardGrade _grade, eResultCode _result, int _rewardExpAmount,
            boolean _hasRewardExp) {
        w.uint32(1, _deckIndex);
            if (_grade != null) w.enumV(2, _grade.toInt());
            if (_result != null) w.enumV(3, _result.toInt());
            if (_hasRewardExp) w.uint32(4, _rewardExpAmount);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _deckIndex, eMonsterBookV2RewardGrade _grade, eResultCode _result,
            int _rewardExpAmount, boolean _hasRewardExp) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _deckIndex, _grade, _result, _rewardExpAmount, _hasRewardExp);
    }

    public static boolean sendFields(
            CIOClient recipient, int _deckIndex, eMonsterBookV2RewardGrade _grade, eResultCode _result,
            int _rewardExpAmount, boolean _hasRewardExp) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MONSTER_BOOK_V2_REWARD_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _deckIndex, _grade, _result, _rewardExpAmount, _hasRewardExp);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _deckIndex, eMonsterBookV2RewardGrade _grade, eResultCode _result, int _rewardExpAmount, boolean _hasRewardExp) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _deckIndex, _grade, _result, _rewardExpAmount, _hasRewardExp);
        return writer.finishWithTrailer(ProtoMsg.SC_MONSTER_BOOK_V2_REWARD_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto36_deckIndex = 0;
        eMonsterBookV2RewardGrade proto36_grade = null;
        eResultCode proto36_result = null;
        int proto36_rewardExpAmount = 0;
        boolean proto36_hasRewardExp = false;


        return sendFields(protoRecipient, proto36_deckIndex, proto36_grade, proto36_result, proto36_rewardExpAmount, proto36_hasRewardExp);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, int deckIndex, eMonsterBookV2RewardGrade grade, eResultCode result) {
        int pWire_deckIndex = 0;
                eMonsterBookV2RewardGrade pWire_grade = null;
                eResultCode pWire_result = null;
                int pWire_rewardExpAmount = 0;
                boolean pWire_hasRewardExp = false;


                { pWire_deckIndex = deckIndex; }
                { pWire_grade = grade; }
                { pWire_result = result; }
                return SC_MONSTER_BOOK_V2_REWARD_ACK.sendFields(
                        protoRecipient, pWire_deckIndex, pWire_grade, pWire_result, pWire_rewardExpAmount,
                        pWire_hasRewardExp);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, int deckIndex, eMonsterBookV2RewardGrade grade, eResultCode result) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), deckIndex, grade, result);
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
