package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_TOP_RANKER_REQ (0x0087) — 최상위 랭커 목록 요청
 *  1 class_id  int32 [required]
 *  2 version   int64 [required]
 */
public final class CS_TopRankerReq {
    private CS_TopRankerReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        int classId = 0;
        long version = 0;
        boolean hasClassId = false, hasVersion = false;

        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case 0x08:                       // field 1, varint
                    classId = r.varint32();
                    hasClassId = true;
                    break;
                case 0x10:                       // field 2, varint (int64)
                    version = r.varint64();
                    hasVersion = true;
                    break;
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[CS_TopRankerReq] parse fail: " + e);
            return;
        }

        // required 2개 — 누락 시 드랍 
        if (!hasClassId || !hasVersion) return;

        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        RankBusiness.getInstance().ack(pc, classId, version);
    }
}