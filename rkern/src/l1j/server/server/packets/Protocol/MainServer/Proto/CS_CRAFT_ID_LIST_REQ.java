package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

/**
 * CS_CRAFT_ID_LIST_REQ (0x0038)
 *  1 npc_id int32 [required]
 */
public final class CS_CRAFT_ID_LIST_REQ {

    /** eCraftIdListReqResultType  */
    private static final int RP_ERROR_INVALID_NPC_ID	= 1;
    private static final int RP_ERROR_OUT_OF_RANGE	= 2;
    private static final int RP_ERROR_UNKNOWN		= 9999;

    private CS_CRAFT_ID_LIST_REQ() {}

    public static void handleRequest(CUserSocket client, ProtoReader r) {
        // ── 파싱 ──
        int npcId = 0;
        while (!r.isAtEnd()) {
            int tag = r.tag();
            switch (tag >>> 3) {
            case 1:
                npcId = r.varint32();
                break;
            default:
                r.skip(tag & 7);
                break;
            }
        }

        // ── 실행 ──
        L1PcInstance pc = null;
        try {
            pc = client.getActiveChar();
            if (!CraftRequestAccess.active(client, pc)) return;
            L1NpcInstance npc = CraftRequestAccess.findNpc(npcId);
            if (npc == null || npc.getNpcTemplate() == null) {
                sendError(pc, RP_ERROR_INVALID_NPC_ID);
                return;
            }
            if (!CraftRequestAccess.inRange(pc, npc)) {
                sendError(pc, RP_ERROR_OUT_OF_RANGE);
                return;
            }
            byte[] data = SC_CRAFT_LIST_ALL_ACK.getCraftIdListFromNpcId(npc.getNpcId());
            if (data == null) {
                return;
            }

            l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, data);
        } catch (Exception e) {
            e.printStackTrace();
            if (pc != null)
                sendError(pc, RP_ERROR_UNKNOWN);
        }
    }

    /** 에러 응답 — eResult만 세팅 */
    private static void sendError(L1PcInstance pc, int resultCode) {
        final ProtoWriter w = Proto.begin(pc, ProtoMsg.SC_CRAFT_ID_LIST_ACK,
                8 + ProtoWriter.varintLenOf(resultCode));
        if (w == null) return;
        try {
            w.enumV(1, resultCode);
            Proto.commit(w);
        } finally {
            Proto.abort(w);
        }
    }
}