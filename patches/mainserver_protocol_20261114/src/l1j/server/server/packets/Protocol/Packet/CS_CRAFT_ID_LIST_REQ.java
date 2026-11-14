package l1j.server.server.packets.Protocol.Packet;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;

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

    public static void handleRequest(CIOClient client, ProtoReader r) {
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
            if (pc == null) return;

            L1Object obj = L1World.getInstance().findObject(npcId);
            if (obj == null || !obj.instanceOf(MJL1Type.L1TYPE_NPC)) {
                sendError(pc, RP_ERROR_INVALID_NPC_ID);
                return;
            }
            
            if (Math.abs(pc.getX() - obj.getX()) > 15 || Math.abs(pc.getY() - obj.getY()) > 15) {
                sendError(pc, RP_ERROR_OUT_OF_RANGE);
                return;
            }

            L1NpcInstance npc = (L1NpcInstance) obj;
            
            if (pc.getMapId() != npc.getMapId()) {
                sendError(pc, RP_ERROR_OUT_OF_RANGE);
            	return;
            }
            byte[] data = SC_CRAFT_LIST_ALL_ACK.getCraftIdListFromNpcId(npc.getNpcId());
            if (data == null) {
                return;
            }

            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, data);
        } catch (Exception e) {
            e.printStackTrace();
            if (pc != null)
                sendError(pc, RP_ERROR_UNKNOWN);
        }
    }

    /** 에러 응답 — eResult만 세팅 */
    private static void sendError(L1PcInstance pc, int resultCode) {
        ProtoWriter w = ProtoWriter.newInstance();
        w.enumV(1, resultCode);
        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, w.finishWithTrailer(ProtoMsg.SC_CRAFT_ID_LIST_ACK));
    }
}