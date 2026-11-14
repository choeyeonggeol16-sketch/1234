package l1j.server.server.model.network.core.cs.packet;

import java.io.File;

import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ClanAttention;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_PledgeWatch {

    private CS_PledgeWatch() {
    }

    public static void handle(
            CIOClient client,
            int data,
            String targetClanName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        switch (data) {

        /*
         * 문장 주시 혈맹 추가.
         */
        case 0:
            addWatch(
                    pc,
                    targetClanName);
            return;

        /*
         * 문장 주시 삭제.
         */
        case 1:
            removeWatch(
                    pc,
                    targetClanName);
            return;

        /*
         * 목록 조회.
         *
         * 원본에서도 아무 작업 없음.
         */
        case 2:
        default:
            return;
        }
    }

    private static void addWatch(
            L1PcInstance pc,
            String targetClanName) {

        if (targetClanName == null
                || targetClanName.isEmpty()) {

            return;
        }

        final String pcClanName =
                pc.getClanname();

        final L1World world =
                L1World.getInstance();

        final L1Clan clan =
                world.findClan(
                        pcClanName);

        if (clan == null) {

            SC_SystemMessage.send(pc, "\\aG먼저 혈맹을 창설하시길 바랍니다.");

            return;
        }

        if (pcClanName.equalsIgnoreCase(
                targetClanName)) {

            SC_SystemMessage.send(pc, "\\aG자신의 클랜에 주시를 할 수 없습니다.");

            return;
        }

        /*
         * 원본과 동일하게 직접 순회.
         */
        for (String gazeName :
                clan.getGazeList()) {

            if (gazeName.equalsIgnoreCase(
                    targetClanName)) {

                SC_SystemMessage.send(pc, "\\aG이미 상대혈맹과 주시를 하고 있습니다.");

                return;
            }
        }

        if (clan.getGazeList().size() >= 5) {

            SC_SystemMessage.send(pc, "\\aG문장주시는 최대 5개 혈맹에만 가능합니다.");

            return;
        }

        final L1Clan targetClan =
                world.findClan(
                        targetClanName);

        if (targetClan == null) {

            SC_SystemMessage.send(pc, "\\aG상대혈맹이 존재하지 않습니다.");

            return;
        }

        /*
         * 원본 파일 검사 semantics 그대로.
         */
        final String emblemDir =
                System.getProperty("user.dir")
                        + "/emblem/";

        File file =
                new File(
                        emblemDir
                                + clan.getEmblemId());

        if (!file.exists()) {

            SC_SystemMessage.send(pc, "혈마크 없이는 문장주시를 요청 할 수 없습니다.");

            return;
        }

        file =
                new File(
                        emblemDir
                                + targetClan.getEmblemId());

        if (!file.exists()) {

            SC_SystemMessage.send(pc, "상대혈맹에 혈마크가 없습니다.");

            return;
        }

        final L1PcInstance target =
                world.getPlayer(
                        targetClan.getLeaderName());

        if (target == null) {

            SC_ServerMessage.send(pc, 3349);

            return;
        }

        SC_SystemMessage.send(pc, "혈맹 주시: 요청중입니다. 기다려주세요.");

        target.setTempID(
                pc.getId());

        SC_MessageYN.send(target, 3348,
                        pc.getClanname());
    }

    private static void removeWatch(
            L1PcInstance pc,
            String targetClanName) {

        if (targetClanName == null
                || targetClanName.isEmpty()) {

            return;
        }

        if (!pc.isCrown()) {

            SC_SystemMessage.send(pc, "\\aG군주만이 문장주시를 해제할 수 있습니다.");

            return;
        }

        final L1World world =
                L1World.getInstance();

        final L1Clan clan =
                world.findClan(
                        pc.getClanname());

        if (clan == null) {
            return;
        }

        final L1Clan targetClan =
                world.findClan(
                        targetClanName);

        if (targetClan == null) {

            SC_SystemMessage.send(pc, "\\aG상대혈맹이 존재하지 않습니다.");

            return;
        }

        /*
         * 상호 주시 제거.
         */
        clan.removeGazelist(
                targetClan.getClanName());

        targetClan.removeGazelist(
                clan.getClanName());

        /*
         * 현재 혈맹 실시간 갱신.
         */
        for (L1PcInstance member :
                clan.getOnlineClanMember()) {

            SC_ClanAttention.sendList(member, 
                            clan.getGazeSize(),
                            clan.getGazeList());
        }

        /*
         * 상대 혈맹 실시간 갱신.
         */
        for (L1PcInstance member :
                targetClan.getOnlineClanMember()) {

            SC_ClanAttention.sendList(member, 
                            targetClan.getGazeSize(),
                            targetClan.getGazeList());
        }
    }
}