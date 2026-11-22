package l1j.server.server;
import static l1j.server.server.model.character.skill.L1SkillId.BONE_BREAK;
import static l1j.server.server.model.character.skill.L1SkillId.CURSE_PARALYZE;
import static l1j.server.server.model.character.skill.L1SkillId.DESPERADO;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.FOG_OF_SLEEPING;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.combat.statistics.chart.KdaChartScheduler;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_War;

public class UserCommands {
    private static final UserCommands INSTANCE = new UserCommands();
    private static final Logger LOG = Logger.getLogger(UserCommands.class.getName());

    private UserCommands() {}

    public static UserCommands getInstance() {
        return INSTANCE;
    }
	
    public void handleCommands(L1PcInstance pc, String cmdLine) {
        if (pc == null || cmdLine == null) return;

        final StringTokenizer tokens = new StringTokenizer(cmdLine);
        if (!tokens.hasMoreTokens()) {
            pc.sendPackets("명령어를 잘못 입력했습니다.");
            return;
        }
        final String cmd = tokens.nextToken();
        String param = tokens.hasMoreTokens() ? tokens.nextToken() : "";
        if (tokens.hasMoreTokens()) {
            final StringBuilder buffer = new StringBuilder(param);
            do {
                buffer.append(' ').append(tokens.nextToken());
            } while (tokens.hasMoreTokens());
            param = buffer.toString();
        }

        try {
            switch (cmd) {
                case "혈맹포인트" -> showClanPoints(pc);
                case "도움말", "help" -> SC_ShowHelp.send(pc, 1);
                case "나이" -> setAge(pc, param);
                case "혈파티", "혈맹파티" -> inviteClanParty(pc, true);
                case "party" -> inviteClanParty(pc, false);
                case "멘트" -> setDropMessage(pc, param, false, true);
                case "ment" -> setDropMessage(pc, param, false, false);
                case "파티멘트" -> setDropMessage(pc, param, true, true);
                case "bpment" -> setDropMessage(pc, param, true, false);
                case "라이트" -> setLight(pc, param, true);
                case "light" -> setLight(pc, param, false);
                case "시세" -> l1j.server.server.appcenter.AppCenterService.registerMarketSearch(pc, param);
                case "혈마크" -> setClanMark(pc, true);
                case "emblem" -> setClanMark(pc, false);
                case "차트" -> setChart(pc, param);
                case "구슬조회" -> L1NeigeSaveChar.commands(pc, param);
                case "텔렉풀기", "텔렉", ".", "텔." -> recoverTeleportState(pc);
                case "자동물약자동버프", "자동종료" ->
                        pc.sendPackets("기존 서버 자동 기능은 종료되었습니다. 플레이서포터를 사용하세요.");
                case "암호변경", "비번변경" -> changePassword(pc, param);
                default -> pc.sendPackets("명령어를 잘못 입력했습니다.");
            }
        } catch (RuntimeException failure) {
            // Never record the command parameter: it can contain credentials.
            if (cmd.equals("암호변경") || cmd.equals("비번변경")) {
                LOG.warning("Password command failed: characterId=" + pc.getId()
                        + " exception=" + failure.getClass().getSimpleName());
            } else {
                LOG.log(Level.WARNING, "User command failed: characterId=" + pc.getId(), failure);
            }
            pc.sendPackets("명령어 처리 중 오류가 발생했습니다.");
        }
    }

    public static void setChart(L1PcInstance pc, String param) {
        if (pc == null || !KdaChartScheduler.isLoaded()) {
            if (pc != null) SC_SystemMessage.send(pc, "사용중이지 않습니다.");
            return;
        }
        final var kda = pc.getKDA();
        if (kda == null) {
            SC_SystemMessage.send(pc, "사용중이지 않습니다.");
            return;
        }
        if ("켬".equalsIgnoreCase(param)) {
            kda.isChartView = true;
            SC_SystemMessage.send(pc, "킬 랭킹 차트를 보입니다. 잠시 후 화면에 나타납니다.");
            KdaChartScheduler.getInstance().onLoginUser(pc);
        } else if ("끔".equalsIgnoreCase(param)) {
            kda.isChartView = false;
            SC_SystemMessage.send(pc, "킬 랭킹 차트를 숨깁니다. 리스 후 적용됩니다.");
        } else {
            SC_SystemMessage.send(pc, ".차트 [켬/끔] (왼쪽상단 PK차트 리스트)");
        }
    }
    private static void setClanMark(L1PcInstance pc, boolean korean) {
        final long now = System.currentTimeMillis() / 1000L;
        final long remaining = pc.getQuizTime() + 30L - now;
        if (remaining > 0L) {
            SC_ChatPacket.sendDirect(pc, pc, korean ? remaining + " 초 후 사용할 수 있습니다."
                    : "Please wait " + remaining + " more seconds.");
            return;
        }
        if (pc.isDead()) {
            SC_SystemMessage.send(pc, korean ? "죽은 상태에선 사용할 수 없습니다."
                    : "You cannot use this while dead.");
            return;
        }

        final boolean enabled = !pc.문장주시;
        final int action = enabled ? 1 : 3;
        pc.문장주시 = enabled;
        if (!korean) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, enabled
                    ? "Clan mark display has been enabled." : "Clan mark display has been disabled.");
        } else if (enabled) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "모든 혈맹의 마크를 표시하거나 종료하였습니다.");
        }
        final String ownClan = pc.getClanname();
        for (L1Clan clan : L1World.getInstance().getAllClans()) {
            if (clan != null) SC_War.send(pc, action, ownClan, clan.getClanName());
        }
        pc.setQuizTime(now);
    }

void showTradePriceNotice(L1PcInstance pc) {
		pc.sendPackets("----------------------------------------------------");
		pc.sendPackets("아이템매니아: 초월 → 기타 → 게임머니= ㅍㅍ");
		pc.sendPackets("유저거래시세:(" + Config.Ad_Sc + ") 아데나 = 5,000원");
		pc.sendPackets("시세 이하 판매자/구매자 즉시 압류 조취");
		pc.sendPackets("----------------------------------------------------");
	}

    private static void setDropMessage(L1PcInstance pc, String param, boolean party, boolean korean) {
        final boolean enabled;
        if (param.equalsIgnoreCase(korean ? "켬" : "on")) {
            enabled = true;
        } else if (param.equalsIgnoreCase(korean ? "끔" : "off")) {
            enabled = false;
        } else {
            pc.sendPackets(party
                    ? (korean ? ".파티멘트 [켬/끔] 입력 하시면 됩니다." : "Usage: .bpment [on/off]")
                    : (korean ? ".멘트 [켬/끔] 입력 하시면 됩니다." : ".ment on/off"));
            return;
        }

        if (party) {
            pc.RootMent1 = enabled;
            pc.sendPackets(korean
                    ? (enabled ? "\\f:파티드랍멘트가 ON 되었습니다." : "\\f:파티드랍멘트가 OFF 되었습니다.")
                    : (enabled ? "\\f:Party drop announcements are now ON." : "\\f:Party drop announcements are now OFF."));
        } else {
            pc.RootMent = enabled;
            if (korean) {
                pc.sendPackets(enabled ? "\\f:드랍멘트가 ON 되었습니다." : "\\f:드랍멘트가 OFF 되었습니다.");
                pc.sendPackets(enabled ? "\\f:기능: 오토루팅템 멘트가 활성화 되었습니다."
                        : "\\f:기능: 오토루팅템 멘트가 비활성화 되었습니다.");
            } else {
                pc.sendPackets(enabled ? "\\f:dropment on" : "\\f:dropment off");
            }
        }
    }

/** Consume only the light commands; other chat and commands retain their existing paths. */
    public static boolean handleLightCommand(L1PcInstance pc, String text) {
        if (pc == null || text == null || text.isEmpty() || text.charAt(0) != '.') return false;
        final boolean korean = text.startsWith(".라이트");
        final boolean english = text.startsWith(".light");
        if (!korean && !english) return false;
        final int end = korean ? ".라이트".length() : ".light".length();
        if (text.length() > end && !Character.isWhitespace(text.charAt(end))) return false;
        setLight(pc, text.substring(end).trim(), korean);
        return true;
    }

    private static void setLight(L1PcInstance pc, String param, boolean korean) {
        if (pc.getMapId() == 132) {
            SC_Ability.send(pc, 3, false);
            pc.sendPackets(korean ? "현재 맵에서는 해당명령어를 사용할 수 없습니다."
                    : "This command cannot be used in this map.");
            return;
        }

        final boolean enabled;
        if (korean ? param.equals("켬") : param.equalsIgnoreCase("on")) {
            enabled = true;
        } else if (korean ? param.equals("끔") : param.equalsIgnoreCase("off")) {
            enabled = false;
        } else {
            pc.sendPackets(korean ? ".라이트 [켬/끔]" : "Usage: .light [on/off]");
            return;
        }

        if (SC_Ability.send(pc, 3, enabled)) {
            pc.sendPackets(korean ? (enabled ? "라이트: 켜짐" : "라이트: 꺼짐")
                    : (enabled ? "Light: ON" : "Light: OFF"));
        }
    }

    private void recoverTeleportState(L1PcInstance pc) {

		long curtime = System.currentTimeMillis() / 1000;
		if (pc.getQuizTime2() + 5 > curtime) {
			long time = (pc.getQuizTime2() + 5) - curtime;
			pc.sendPackets(time + "초 후 사용할 수 있습니다.");
			return;
		}
		try {
			if (pc.getMapId() == 781) {
				if (pc.getLocation().getX() <= 32998 && pc.getLocation().getX() >= 32988
						&& pc.getLocation().getY() <= 32758 && pc.getLocation().getY() >= 32736) {
					pc.sendPackets("사용할 수 없는 장소입니다.");
					return;
				}
			}
			if (pc.hasSkillEffect(SHOCK_STUN) || pc.hasSkillEffect(DESPERADO) || pc.hasSkillEffect(L1SkillId.EMPIRE)
					|| pc.hasSkillEffect(EARTH_BIND) || pc.hasSkillEffect(CURSE_PARALYZE)
					|| pc.hasSkillEffect(ICE_LANCE) || pc.hasSkillEffect(FOG_OF_SLEEPING)
					|| pc.hasSkillEffect(BONE_BREAK)) {
				pc.sendPackets("현재 사용이 불가능한 상태 입니다.");
				return;
			}
			if (pc.getMapId() == 132) {
				SC_Ability.send(pc, 3, false);
			}
			/*
			 * if (CharPosUtil.getZoneType(pc) == 0 && castle_id != 0) { // 공성장 주변에서 불가능
			 * pc.sendPackets("사용할 수 없는 장소입니다."); return; }
			 */
			if (pc.isPinkName() || pc.isDead() || pc.isParalyzed() || pc.isSleeped() || pc.getMapId() == 800
					|| pc.getMapId() == 12150 || pc.getMapId() == 12154 || pc.getMapId() == 5302
					|| pc.getMapId() == 5153 || pc.getMapId() == 5490) {
				pc.sendPackets("사용할 수 없는 상태입니다.");
				return;
			}
			SC_PacketBox.send(pc, SC_PacketBox.공격가능거리, pc, pc.getWeapon());
			pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, true, false);
			pc.update_lastLocalTellTime();
			pc.setQuizTime2(curtime);
		} catch (Exception exception35) {
		}
	}

    private static void inviteClanParty(L1PcInstance pc, boolean korean) {
        if (pc == null) return;
        if (pc.isDead()) {
            pc.sendPackets(korean ? "죽은 상태에선 사용할 수 없습니다." : "You cannot use this while dead.");
            return;
        }
        final L1Clan clan = pc.getClan();
        if (clan == null) {
            pc.sendPackets(korean ? "혈맹에 가입된 상태가 아닙니다." : "You are not currently in a clan.");
            return;
        }
        final int rank = pc.getClanRank();
        if (rank != L1Clan.군주 && rank != L1Clan.부군주 && rank != L1Clan.수호) {
            pc.sendPackets(korean ? "혈맹의 군주, 부군주, 수호기사만 사용 가능합니다."
                    : "Only the Clan Prince, Vice-Prince, or Guardian can use this.");
            return;
        }

        final int inviterId = pc.getId();
        final String notice = korean ? "\\fQ자동분배는 한 화면에 파티원들이 있을 경우 분배됩니다."
                : "\\fQAuto-loot only works when party members are on the same screen.";
        pc.setPartyType(1);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, korean ? "\\f:혈맹원들에게 혈맹파티를 신청하였습니다."
                : "\\f:Clan party invitation has been sent to online members.");
        pc.sendPackets(notice);

        try (var shared = new CAsyncSendConstBuffer(SC_PacketBox.nativeGreenWriter(korean
                    ? "\\f:귀하의 혈맹에서 혈맹파티 신청이 들어왔습니다 (Y/N)"
                    : "\\f:You have received a clan party invitation. (Y/N)"), Integer.MIN_VALUE, Integer.MAX_VALUE);
             var ynShared = new CAsyncSendConstBuffer(SC_MessageYN.nativeWriter(954, pc.getName()),
                     Integer.MIN_VALUE, Integer.MAX_VALUE);
             var noticeShared = new CAsyncSendConstBuffer(SC_SystemMessage.nativeWriter(notice),
                     Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance member : clan.getOnlineClanMember()) {
                if (member == null || member.getId() == inviterId || member.isPrivateShop()
                        || member.isInParty() || member.getAutoHunt()) continue;
                final CUserSocket connection = member.getNetConnection();
                if (connection == null || !connection.isConnected()) continue;
                member.setPartyID(inviterId);
                CSharedBroadcast.sendRecipient(ynShared, member);
                CSharedBroadcast.sendRecipient(shared, member);
                CSharedBroadcast.sendRecipient(noticeShared, member);
            }
        }
    }

    private static void setAge(L1PcInstance pc, String param) {
        final int age;
        try {
            final int separator = param.indexOf(' ');
            age = Integer.parseInt(separator < 0 ? param : param.substring(0, separator));
        } catch (NumberFormatException failure) {
            pc.sendPackets(".나이 숫자 형식으로 입력.(혈맹 채팅 시 표시됨)");
            return;
        }
        if (age < 14 || age > 59) {
            pc.sendPackets("자신의 실제 나이로 설정하세요.");
            return;
        }
        final int previous = pc.getAge();
        if (previous != age) {
            pc.setAge(age);
            try {
                pc.save();
            } catch (RuntimeException failure) {
                pc.setAge(previous);
                LOG.log(Level.WARNING, "Age save admission failed: characterId=" + pc.getId(), failure);
                pc.sendPackets("나이 설정에 실패했습니다.");
                return;
            }
        }
        pc.sendPackets("명령어: 당신의 나이가 [" + age + "] 설정되었습니다.");
    }

    private static boolean isPasswordCharacters(String value) {
        for (int i = 0, n = value.length(); i < n; i++) {
            final char ch = value.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))) return false;
        }
        return true;
    }

    private static void changePassword(L1PcInstance pc, String param) {
        if (pc.get_lastPasswordChangeTime() + 600_000L > System.currentTimeMillis()) {
            pc.sendPackets("암호를 변경하신지 10분이 지나지 않았습니다. 잠시후 다시 변경하세요.");
            return;
        }
        if (param.isEmpty()) {
            pc.sendPackets(".암호변경 [변경할암호]로 입력 하세요.");
            return;
        }
        if (param.length() < 6 || param.length() > 16) {
            pc.sendPackets("6자 ~ 16자 사이의 영어나 숫자로 입력하세요.");
            return;
        }
        if (!isPasswordCharacters(param)) {
            pc.sendPackets("영어와 숫자로만 입력하세요.");
            return;
        }

        final CUserSocket connection = pc.getNetConnection();
        final Account account = connection == null ? null : connection.getAccount();
        if (account == null || !connection.isConnected() || connection.getActiveChar() != pc
                || !pc.getAccountName().equals(account.getName())) {
            pc.sendPackets("계정 정보를 확인할 수 없습니다.");
            return;
        }
        // Respect the existing account password representation and update only
        // the authenticated account. Publish the local password after SQL succeeds.
        if (param.equals(account.get_Password())) {
            pc.sendPackets("현재와 동일한 암호입니다.");
            return;
        }

        try (Connection db = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = db.prepareStatement(
                     "UPDATE accounts SET password=? WHERE login=?")) {
            statement.setString(1, param);
            statement.setString(2, account.getName());
            if (statement.executeUpdate() != 1) {
                pc.sendPackets("계정 암호 변경에 실패했습니다.");
                return;
            }
        } catch (SQLException | RuntimeException failure) {
            // JDBC exception messages may contain bound parameter values.
            LOG.warning("Password update failed: characterId=" + pc.getId()
                    + " exception=" + failure.getClass().getSimpleName());
            pc.sendPackets("계정 암호 변경에 실패했습니다.");
            return;
        }
        account.setPassword(param);
        pc.update_lastPasswordChangeTime();
        pc.sendPackets("계정 암호가 변경되었습니다.");
    }

    private static void showClanPoints(L1PcInstance pc) {
        if (!Config.혈맹포인트여부) {
            SC_SystemMessage.send(pc, "현재 혈맹포인트 시스템은 비활성화입니다.");
            return;
        }
        final L1Clan clan = pc.getClan();
        if (clan == null || pc.getClanid() == 0) {
            SC_SystemMessage.send(pc, "혈맹에 가입되어 있지 않아 조회할 수 없습니다.");
            return;
        }
        final int rank = pc.getClanRank();
        if (Config.혈맹포인트조회 && rank != 9 && rank != 10) {
            SC_SystemMessage.send(pc, "군주 또는 수호기사만 조회가 가능합니다.");
            return;
        }
        SC_SystemMessage.send(pc, "\\f:현재 혈맹포인트: [" + clan.getWarPoint() + "]");
    }

}