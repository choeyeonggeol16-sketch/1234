package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Packet.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode;

/**
 * CS_ACHIEVEMENT_TELEPORT_REQ — 도감(업적) 텔레포트 요청
 * wire: [1]=achievement_id uint32 (required)
 */
public final class CS_ACHIEVEMENT_TELEPORT_REQ {

    private CS_ACHIEVEMENT_TELEPORT_REQ() {}

    public static void handleRequest(CIOClient client, ProtoReader r) {
        // ── 파싱 ──
        int achievementId = 0;
        boolean hasAchievementId = false;
        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case (1 << 3) | 0:              // field 1, varint
                    achievementId = r.varint32();
                    hasAchievementId = true;
                    break;
                default:
                    r.skip(tag & 7);          
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (!hasAchievementId) return;

        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            if (!BQSLoadManager.BQS_IS_ONUPDATE_BOOKS) {
            	SC_ACHIEVEMENT_TELEPORT_ACk.send(pc, achievementId, eResultCode.TELEPORT_FAIL);
                pc.sendPackets("현재 도감 시스템이 일시 중지 상태입니다.");
                return;
            }

            int criteriaId = BQSLoadManager.achievementIdToCriteriaId(achievementId);
            BQSInformation bqsInfo = BQSInformationLoader.getInstance().getInformation(criteriaId);
            if (bqsInfo == null) {
                System.out.println(String.format(
                        "[도감] 존재하지 않는 도감에 텔레포트를 시도했습니다. 캐릭터이름 : %s, 보상아이디 : %d, 북아이디 : %d",
                        pc.getName(), achievementId, criteriaId));
                SC_ACHIEVEMENT_TELEPORT_ACk.send(pc, achievementId, eResultCode.TELEPORT_FAIL);
                return;
            }
            SC_ACHIEVEMENT_TELEPORT_ACk.send(pc, achievementId, bqsInfo.doTeleport(pc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}