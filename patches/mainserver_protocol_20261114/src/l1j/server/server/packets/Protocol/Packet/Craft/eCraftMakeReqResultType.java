package l1j.server.server.packets.Protocol.Packet.Craft;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Packet.SC_CRAFT_MAKE_ACK;

/**
 * 제작 결과 코드 — 완전 자체 포트.
 * sendCachedMessage는 SC_CRAFT_MAKE_ACK(자체 포트 ACK)로 전송.
 */
public enum eCraftMakeReqResultType {
    RP_SUCCESS(0),
    RP_FAILURE(1),
    RP_ERROR_NO_REQUIRED_ITEM(2),
    RP_ERROR_SUCCESS_COUNT_EXCEED(3),
    RP_ERROR_BLOCKED_CRAFT_ID(4),
    RP_ERROR_WEIGHT_OVER(5),
    RP_ERROR_INVEN_OVER(6),
    RP_ERROR_INVALID_NPC(7),
    RP_ERROR_NPC_OUT_OF_RANGE(8),
    RP_ERROR_CRAFT_DOES_NOT_EXIST(9),
    RP_ERROR_INVALID_INPUT(10),
    RP_ERROR_TOO_MANY_MAKE_REQ(11);

    private final int _value;

    eCraftMakeReqResultType(int val) {
        _value = val;
    }

    public int toInt() {
        return _value;
    }

    /** 에러코드 전송 — 결과 아이템 없는 ACK */
    public void sendCachedMessage(L1PcInstance pc) {
        SC_CRAFT_MAKE_ACK.send(pc, this, null);
    }
}