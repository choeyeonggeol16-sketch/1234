package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_WhoAmount;
import l1j.server.Config;
import l1j.server.server.command.executor.L1UserCalc;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_Who {

    private static final String[] CLASS_NAMES = {
            "군주",
            "기사",
            "요정",
            "마법사",
            "다크엘프",
            "용기사",
            "환술사",
            "전사"
    };

    private CS_Who() {}

    public static void handle(CIOClient client, String targetName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost()) {
            return;
        }

        final L1World world = L1World.getInstance();

        if (targetName != null && !targetName.isEmpty()) {
            final L1PcInstance target = world.getPlayer(targetName);

            if (target != null) {
                sendPlayerInfo(pc, target);
            }
        }

        int addUser = (int) (world.getAllPlayers().size() * Config.Number_Count + Config.Neige_Count);
        addUser += L1UserCalc.getClacUser();

        if (Config.Neige_Count_Pass == 2) {
            SC_WhoAmount.send(pc, String.valueOf(addUser));
        }
    }

    private static void sendPlayerInfo(L1PcInstance pc, L1PcInstance target) {

        /*
         * 일반 유저는 GM(운영자) 정보 조회 불가.
         */
        if (target.isGm() && !pc.isGm()) {
            pc.sendPackets("운영자의 정보는 알 수 없습니다.");
            return;
        }

        float win = 0;
        float lose = 0;

        if (target.getKDA() != null) {
            win = target.getKDA().kill;
            lose = target.getKDA().death;
        }

        final int type = target.getType();
        final String className = type >= 0 && type < CLASS_NAMES.length ? CLASS_NAMES[type] : "알수없음";

        String clanName = "\\fQ없음";
        final L1Clan clan = target.getClan();

        if (clan != null) {
            if (clan.getCastleId() != 0) {
                clanName = "\\aH" + target.getClanname() + " \\fH(성 소유)";
            } else {
                clanName = "\\aH" + target.getClanname();
            }
        }

        final float total = win + lose;
        final int winner = total > 0 ? (int) ((win * 100) / total) : 0;

        final boolean wanted1 = target.hasSkillEffect(L1SkillId.USER_WANTED);
        final boolean wanted2 = target.hasSkillEffect(L1SkillId.USER_WANTED1);
        final String wantedStatus;

        if (wanted1) {
            wantedStatus = "\\aH1단\\fQ";
        } else if (wanted2) {
            wantedStatus = "\\f32단\\fQ";
        } else {
            wantedStatus = "\\aH미적용";
        }

        final String lawfulStr = target.getLawful() > 0 ? "\\aJ(Lawful)" : "\\fY(Chaotic)";

        pc.sendPackets("\\fQ========= [" + target.getName() + "]님의 정보 " + lawfulStr + " \\fQ=========");
        pc.sendPackets("\\fQ클래스: \\aH" + className + "  \\fQ혈맹: " + clanName);
        pc.sendPackets("\\fQKILL: \\aH" + (int) win + "  \\fQDEATH: \\aH" + (int) lose 
                + "  \\fQ승률: \\aH" + winner + "%  \\fQ수배: " + wantedStatus);

        if (wanted1 || wanted2) {
            pc.sendPackets("\\fQ위치: \\aH" + MapsTable.getInstance().getMapName(target.getMapId()));
        }
    }
}