package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_MY_RANKING_REQ (0x03FD) — 개인 랭킹 갱신 요청
 *  1 version  int64 (varint)  [required] — 클라가 갖고 있는 랭킹 버전
 */
public final class CS_MY_RANKING_REQ {
    private CS_MY_RANKING_REQ() {}

    public static void handleRequest(CIOClient client, ProtoReader r) {
        long version = 0;
        boolean hasVersion = false;

        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case 0x08:                       // field 1, varint
                    version = r.varint64();
                    hasVersion = true;
                    break;
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[CS_MY_RANKING_REQ] parse fail: " + e);
            return;
        }

        if (!hasVersion) return;

        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        RankBusiness.getInstance().ack_private_version(pc, version);
    }
}