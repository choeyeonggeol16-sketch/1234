package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_ClanName;

import l1j.server.Config;
import l1j.server.server.BadNamesList;
import l1j.server.server.datatables.ClanBuffTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_QuestTalkIsland;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1QuestInfo;

public final class CS_CreatePledge {

    private static final String FORBIDDEN = 
            "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
          + "ㅛㅕㅑㅐㅔㅗㅓㅏ║ㅠㅜㅡㅒㅖㅢㅟㅝㅞㅙㅚㅘ"
          + "씹좃좆ㅤ";

    private CS_CreatePledge() {}

    public static void handle(CIOClient client, String clanName, int nameBytes) {

        if (client == null || clanName == null || clanName.isEmpty()) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost()) {
            return;
        }

        if (isInvalidName(clanName, nameBytes)) {
            SC_SystemMessage.send(pc, "잘못된 혈맹 이름입니다.");
            return;
        }

        /*
         * 군주만 생성 가능.
         */
        if (!pc.isCrown()) {
            SC_ServerMessage.send(pc, 85);
            return;
        }

        /*
         * 이미 혈맹이 있거나, 군주 최소 레벨 미달.
         */
        if (pc.getClanid() != 0 || pc.getLevel() < Config.Crown_Level) {
            SC_SystemMessage.send(pc, "이미 혈맹이 있거나 " + Config.Crown_Level + "레벨 이상만 창설할 수 있습니다.");
            return;
        }

        /*
         * 생성 비용.
         */
        if (!pc.getInventory().checkItem(L1ItemId.ADENA, 10000)) {
            SC_ServerMessage.send(pc, 337, "$4");
            return;
        }

        /*
         * 동일 이름 검사.
         */
        if (L1World.getInstance().findClan(clanName) != null) {
            SC_ServerMessage.send(pc, 99);
            return;
        }

        final ClanTable clanTable = ClanTable.getInstance();
        clanTable.createClanPaid(pc, clanName, clan -> {
            SC_ServerMessage.send(pc, 84, clanName);
            SC_ClanName.send(pc, pc, clan.getEmblemId(), pc.getClanRank());
            SC_ActionUI.send(pc, clan.getClanName(), pc.getClanRank());
            SC_PacketBox.send(pc, SC_PacketBox.PLEDGE_EMBLEM_STATUS, clan.getEmblemStatus());

            pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);

            /*
             * 말하는 섬 퀘스트 처리.
             */
            if (pc.getClanid() != 0) {
                final L1QuestInfo info = pc.getQuestList(272);

                if (info != null && info.end_time == 0) {
                    /*
                     * 기존 코드는 clanId를 넣고 1보다 크면 바로 1로 축약했습니다.
                     *
                     * 결과적으로 clanId != 0이면 항상 1이므로
                     * 바로 1을 기록해도 의미론적(semantics)으로 동일합니다.
                     */
                    info.ck[0] = 1;
                    SC_QuestTalkIsland.sendUpdate(pc, 272, info);
                }
            }

            /*
             * 혈맹 버프 초기화.
             */
            clan.setEinhasadBlessBuff(0);
            clan.setBuffFirst(ClanBuffTable.getRandomBuff(clan));
            clan.setBuffSecond(ClanBuffTable.getRandomBuff(clan));
            clan.setBuffThird(ClanBuffTable.getRandomBuff(clan));

            l1j.server.server.model.network.core.sc.packet.SC_EinhasadClanBuff.send(pc, pc);
            clanTable.updateClan(clan);
            clanTable.persistInitialBuffs(clan);
        });
    }

    private static boolean isInvalidName(String name, int nameBytes) {

        if (name == null || name.isEmpty() || nameBytes <= 0) {
            return true;
        }

        /*
         * 한글·영문·숫자로 된 정상 이름은 허용하고, 공백·특수문자와
         * 금지 문자 및 금칙어는 거부합니다.
         *
         * 종전 코드는 이름 전체가 Letter/Digit로 구성되면 오히려
         * 거부하는 반전 조건 때문에 정상적인 혈맹 생성이 불가능했습니다.
         */
        boolean alphaNumeric = true;
        final int length = name.length();

        for (int i = 0; i < length; i++) {
            final char c = name.charAt(i);

            if (FORBIDDEN.indexOf(c) >= 0) {
                return true;
            }

            if (alphaNumeric && !Character.isLetterOrDigit(c)) {
                alphaNumeric = false;
            }
        }

        if (!alphaNumeric) {
            return true;
        }

        /*
         * 기존 방식:
         *
         * int numOfNameBytes = name.getBytes("MS949").length;
         *
         * if (5 < (numOfNameBytes - name.length()) || 12 < numOfNameBytes)
         *
         * FastPath에서 직접 전달받은 실제 MS949 byte 수로 동일하게 판정합니다.
         */
        if ((nameBytes - length) > 5 || nameBytes > 12) {
            return true;
        }

        return BadNamesList.getInstance().isBadName(name);
    }
}