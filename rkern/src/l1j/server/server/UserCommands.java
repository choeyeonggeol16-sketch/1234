package l1j.server.server;
import static l1j.server.server.model.character.skill.L1SkillId.BONE_BREAK;
import static l1j.server.server.model.character.skill.L1SkillId.BUYER_COOLTIME;
import static l1j.server.server.model.character.skill.L1SkillId.CURSE_PARALYZE;
import static l1j.server.server.model.character.skill.L1SkillId.DESPERADO;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.FOG_OF_SLEEPING;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.combat.statistics.chart.KdaChartScheduler;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_LetterList;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.FullStater;
import l1j.server.server.utils.SQLUtil;

public class UserCommands {
    private static final UserCommands INSTANCE = new UserCommands();
    private static final Logger LOG = Logger.getLogger(UserCommands.class.getName());
    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("yy/MM/dd", Locale.KOREA);
    boolean spawnTF = false;

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
                case "혈맹포인트" -> 혈맹포인트(pc);
                case "도움말", "help" -> SC_ShowHelp.send(pc, 1);
                case "나이" -> age(pc, param);
                case "혈파티", "혈맹파티" -> BloodParty(pc);
                case "party" -> BloodParty3(pc);
                case "멘트" -> setDropMessage(pc, param, false, true);
                case "ment" -> setDropMessage(pc, param, false, false);
                case "파티멘트" -> setDropMessage(pc, param, true, true);
                case "bpment" -> setDropMessage(pc, param, true, false);
                case "라우풀" -> Lawful(pc, param);
                case "라이트" -> setLight(pc, param, true);
                case "light" -> setLight(pc, param, false);
                case "시세" -> l1j.server.server.appcenter.AppCenterService.registerMarketSearch(pc, param);
                case "혈마크" -> setClanMark(pc, true);
                case "emblem" -> setClanMark(pc, false);
                case "차트" -> setChart(pc, param);
                case "구슬조회" -> L1NeigeSaveChar.commands(pc, param);
                case "텔렉풀기", "텔렉", ".", "텔." -> tell(pc);
                case "자동물약자동버프", "자동종료" ->
                        pc.sendPackets("기존 서버 자동 기능은 종료되었습니다. 플레이서포터를 사용하세요.");
                case "암호변경", "비번변경" -> changepassword(pc, param);
                default -> pc.sendPackets("명령어를 잘못 입력했습니다.");
            }
        } catch (RuntimeException failure) {
            // Never record the command parameter: it can contain credentials.
            LOG.log(Level.WARNING, "User command failed: characterId=" + pc.getId(), failure);
            pc.sendPackets("명령어 처리 중 오류가 발생했습니다.");
        }
    }

public static void WriteLetter22(L1PcInstance pc, String in4, int in2,
			String in5) {
		int nu1 = 949;
		String dTime = LETTER_DATE.format(LocalDate.now());
		String subject = "위탁물품판매완료";
		String content = "+ "
				+ in2
				+ " "
				+ in5
				+ "\n물품이 판매되었습니다. \n\n반드시 리스타트 후에 \n부가 아이템 창고에서\n수표를 회수하십시오.\n\n";
		String name = "위탁관리인";
		L1PcInstance target = L1World.getInstance().getPlayer(in4);
		LetterTable.getInstance().writeLetter(nu1, dTime, name, in4, 0,
				subject, content, target, () -> {
            if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			SC_SystemMessage.send(target, "\\fY물품이 판매되었습니다.");
			SC_SystemMessage.send(target, "\\fY리스 후 부가아이템창고에서 찾으실 수 있습니다.");
			SC_SkillSound.send(target, target.getId(), 1091);
			SC_ServerMessage.send(target, 428);
			SC_LetterList.sendListAsync(pc, 0, 20);
		}
        });
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

void AdSc(L1PcInstance pc) {
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

private void Lawful(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.sendPackets("\\f:PVP에 따른 라우풀 조절을 중지합니다.");
			pc.setLawfulStop(0);
			pc.killSkillEffectTimer(L1SkillId.라우풀고정);
		} else if (param.equalsIgnoreCase("켬")) {
			SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_Lawful, 622, "라우풀 성향 변화에 PVP를 제외하시겠습니까? (비용: 1천만 아데나)");
		} else {
			pc.sendPackets(".라우풀 [켬/끔] 입력 하시면 됩니다.");
			pc.sendPackets("이 모드는 라우풀 성향이 PVP에 영향을 받지 않도록 설정할 수 있습니다.");
		}
	}

public static void 할파스의신의(L1PcInstance pc, String param) {
		if (!(pc.getInventory().checkEquipped(123111) || pc.getInventory().checkEquipped(123112)
				|| pc.getInventory().checkEquipped(123113))) {
			pc.sendPackets("\\fY할파스의 갑옷을 착용하고 있지 않습니다.");
			return;
		} else {

			if (pc.hasSkillEffect(L1SkillId.할파스의신의)) {
				int n = pc.getSkillEffectTimeSec(L1SkillId.할파스의신의) / 60;
				SC_SystemMessage.send(pc, String.format("\\fY할파스의 신의 : %d분 후에 활성화됩니다.", n));
			} else {
				pc.sendPackets("\\fY할파스의 신의는 활성화 상태입니다.");

			}
		}
	}

	public static void doTarget(CommandArgs args) {
		try {
			String cmd = args.nextString();
			if (cmd.equalsIgnoreCase("켬")) {
				args.getOwner().setOnTargetEffect(true);
				args.notify("타겟팅 시스템이 활성화되었습니다.");
			} else if (cmd.equalsIgnoreCase("끔")) {
				args.getOwner().setOnTargetEffect(false);
				args.notify("타겟팅 시스템이 비활성화되었습니다.");
			} else if (cmd.equalsIgnoreCase("상태")) {
				args.notify(String.format("현재 타겟팅 시스템 상태 : %s", args.getOwner().isOnTargetEffect()));
			} else
				throw new Exception();
		} catch (Exception e) {
			args.notify(".타겟팅 [켬|끔|상태]");
		}
	}

public static void autofish(L1PcInstance pc) {
		try {
			if (!pc.isFishing()) {
				pc.sendPackets( "\\fY낚시를 실행 후 가능합니다.");
				return;
			}
			for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) {
				if (target.getId() != pc.getId() && target.getAccountName().toLowerCase().equals(pc.getAccountName().toLowerCase()) && target.isAutoFish()) {
					pc.sendPackets("\\f3귀하의 보조 캐릭터가 자동낚시 상태입니다.");
					return;
				}
			}
			
			CUserSocket client = pc.getNetConnection();
			pc.setNetConnection(null);
			pc.setAutoFish(true);
			try {
				pc.save();
				pc.saveInventory();
			} catch (Exception e) {
			}
			client.setActiveChar(null);
			SC_Unknown2.send(client, 1); // 리스버튼을 위한 구조변경 // Episode U

		} catch (Exception e) {
		}
	}
	
    public static boolean isAlphaNumeric(String s) {
        if (s == null) return false;
        for (int i = 0, length = s.length(); i < length; i++) {
            final char ch = s.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9') || (ch >= 44032 && ch <= 55203)) return true;
        }
        return false;
    }

	/********* 디비 친구목록에서 변경된 아이디 지우기 ************/
	public static void buddys(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;
		String aaa = pc.getName();
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_buddys WHERE buddy_name=?");

			pstm.setString(1, aaa);
			pstm.execute();
		} catch (SQLException e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public static void 편지삭제(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;

		String aaa = pc.getName();

		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM letter WHERE receiver=?");
			pstm.setString(1, aaa);
			pstm.execute();
			// System.out.println("....["+ aaa +"].....");
		} catch (SQLException e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

public static boolean isHpBarTarget(L1Object obj) {
		if (obj instanceof L1MonsterInstance) {
			return true;
		}
		if (obj instanceof L1PcInstance) {
			return true;
		}
		if (obj instanceof L1SummonInstance) {
			return true;
		}
		if (obj instanceof L1PetInstance) {
			return true;
		}
		if(obj instanceof L1CompanionInstance)
			return true;
		return false;
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

private void tell(L1PcInstance pc) {

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

    public void BloodParty(L1PcInstance pc) {
        inviteClanParty(pc, true);
    }

    public void BloodParty3(L1PcInstance pc) {
        inviteClanParty(pc, false);
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

    private static void age(L1PcInstance pc, String param) {
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

    private static void changepassword(L1PcInstance pc, String param) {
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

// 패스워드 맞는지 여부 리턴
    public static boolean isPasswordTrue(String password, String oldPassword) {
        if (password == null || oldPassword == null) return false;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = con.prepareStatement("SELECT password(?) as pwd")) {
            statement.setString(1, oldPassword);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && password.equals(result.getString("pwd"));
            }
        } catch (SQLException | RuntimeException failure) {
            LOG.warning("Password verification failed: " + failure.getClass().getSimpleName());
            return false;
        }
    }

public static void privateShop(L1PcInstance pc) {
		try {
			if (!pc.isPrivateShop()) {
				pc.sendPackets("개인상점 상태에서 사용이 가능합니다.");
				return;
			}
			for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) {
				if (target.getId() != pc.getId()
						&& target.getAccountName().toLowerCase().equals(pc.getAccountName().toLowerCase())
						&& target.isPrivateShop()) {
					pc.sendPackets("\\f3이미 당신의 보조 캐릭터가 무인상점 상태입니다.");
					pc.sendPackets("\\f3상점을 종료하시길 바랍니다. /상점");
					return;
				}
			}
			CUserSocket client = pc.getNetConnection();
			pc.setNetConnection(null);
			client.setConnectionState(CConnectionState.OUT_GAME);
			pc.set무인상점(true);
			try {
				pc.save();
				pc.saveInventory();
			} catch (Exception e) {
			}
			client.setActiveChar(null);
			SC_Unknown2.send(client, 1); // 리스버튼을 위한 구조변경 // Episode U

		} catch (Exception e) {
		}
	}

private String parseStat(String s) throws Exception {
		if (s.equalsIgnoreCase("힘"))
			return "str";
		else if (s.equalsIgnoreCase("덱스"))
			return "dex";
		else if (s.equalsIgnoreCase("콘"))
			return "con";
		else if (s.equalsIgnoreCase("인트"))
			return "int";
		else if (s.equalsIgnoreCase("위즈"))
			return "wis";
		else if (s.equalsIgnoreCase("카리"))
			return "cha";
		throw new Exception(s);
	}

	public void fullstat(L1PcInstance pc, String param) {
		try {
			String[] arr = param.split(" ");
			if (arr == null || arr.length < 2)
				throw new Exception();

			String s = parseStat(arr[0]);
			FullStater.running(pc, s, Integer.parseInt(arr[1]));
		} catch (Exception e) {
			pc.sendPackets(
					String.format(".스텟 [힘/덱스/콘/인트/위즈/카리] [올릴수] 남은스텟 %d", (pc.getLevel() - 50 - pc.getBonusStats())));
		}
	}

	// TODO 중개 거래 게시판
	private static void LetterList(L1PcInstance pc, int type, int count) {
		SC_LetterList.sendListAsync(pc, type, count);
	}

	public static void WriteCancle(String buyer, String seller) {
		int nu1 = 949;
		String dTime = LETTER_DATE.format(LocalDate.now());
		String subject = "구매취소내역";
		String content = "구매자 :" + buyer + "" + "\n\n상대방이 구매를 취소하엿습니다." + "\n입금 대기상태로 바뀝니다.";
		String name = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(seller);
		LetterTable.getInstance().writeLetter(nu1, dTime, name, seller, 0, subject, content, target, () -> {
            if (target != null && target.getOnlineStatus() != 0) {
			SC_PacketBox.send(target, SC_PacketBox.GREEN_MESSAGE, "구매취소: " + buyer + "님이 구매를 취소 하였습니다.");
			LetterList(target, 0, 20);
			SC_SkillSound.send(target, target.getId(), 1091);
			SC_ServerMessage.send(target, 428);
		}
        });
	}

	public static void WriteComplete(String buyer, int count, String seller) {
		int nu1 = 949;
		String dTime = LETTER_DATE.format(LocalDate.now());
		String subject = "판매완료내역";
		String content = "수표 :" + count + "개" + "\n판매자 :" + seller + "" + "\n물품 구매가 완료되었습니다. " + "\n\n반드시 리스타트 후에 "
				+ "\n부가 아이템 창고에서" + "\n아데나를 수령하십시요.";
		String name = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(buyer);
		LetterTable.getInstance().writeLetter(nu1, dTime, name, buyer, 0, subject, content, target, () -> {
            if (target != null && target.getOnlineStatus() != 0) {
			SC_PacketBox.send(target, SC_PacketBox.GREEN_MESSAGE, "판매완료: 아데나가 '부가아이템창고'로 지급 되었습니다(리스타트후 수령가능)");
			target.killSkillEffectTimer(BUYER_COOLTIME);
			LetterList(target, 0, 20);
			SC_SkillSound.send(target, target.getId(), 1091);
			SC_ServerMessage.send(target, 428);
		}
        });
	}

	public static void WriteBuyLetter(String player, int count, int sellcount, String bankname, String name,
			String number, String sellername, String buyername) {
		int nu1 = 949;
		String dTime = LETTER_DATE.format(LocalDate.now());
		String subject = "구매 신청 내역";
		String content = "구매 수표 : " + count + "개." + "\n구매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
				+ "\n예금주 : " + name + "" + "\n계좌번호 : " + number + "" + "\n판매자 캐릭터 : " + sellername + "" + "\n구매자 캐릭터 : "
				+ buyername + "" + "\n\n판매자는 입금완료후 반드시" + "\n.판매완료 (게시번호) 를 하셔야" + "\n구매자에게 아데나가 지급됩니다."
				+ "\n\n판매자가 악의적으로 판매완료를" + "\n거부할 경우 메티스로 문의." + "\n입금전 반드시 상대방이" + "\n접속중인지 확인후 거래를"
				+ "\n진행해 주시기 바랍니다.";
		String project = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(player);
		LetterTable.getInstance().writeLetter(nu1, dTime, project, player, 0, subject, content, target, () -> {
            if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			SC_SkillSound.send(target, target.getId(), 1091);
			SC_ServerMessage.send(target, 428);
		}
        });
	}

	public static void WriteSellLetter(String player, int count, int sellcount, String bankname, String name,
			String number, String charname) {
		int nu1 = 949;
		String dTime = LETTER_DATE.format(LocalDate.now());
		String subject = "판매 등록 내역";
		String content = "판매 수표 : " + count + "개." + "\n판매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
				+ "\n예금주 : " + name + "" + "\n계좌번호 : " + number + "" + "\n판매자 캐릭터 : " + charname + ""
				+ "\n\n위 내용과 상이할 경우 " + "\n.판매취소 (게시글번호)" + "\n하신후 재등록 하시기 바랍니다." + "\n\n잘못된 정보 기재로 인한 피해사항은"
				+ "\n어떤한 경우에도 책임지지 않습니다.";
		String project = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(player);
		LetterTable.getInstance().writeLetter(nu1, dTime, project, player, 0, subject, content, target, () -> {
            if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			SC_SkillSound.send(target, target.getId(), 1091);
			SC_ServerMessage.send(target, 428);
		}
        });
	}
    private static void 혈맹포인트(L1PcInstance pc) {
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
	public void 유저정보(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			L1PcInstance target = L1World.getInstance(). getPlayer(name);
			if (target == null) {
				SC_ServerMessage.send(pc, 73, name); // \f1%0은 게임을 하고 있지 않습니다.
				return;
			}		
			String typeName = null;
			switch(target.getType()){
			case 0:
				typeName = "군주";
				break;

			case 1:
				typeName = "기사";
				break;

			case 2:
				typeName = "요정";
				break;

			case 3:
				typeName = "마법사";
				break;

			case 4:
				typeName = "다크엘프";
				break;

			case 5:
				typeName = "용기사";
				break;

			case 6:
				typeName = "환술사";
				break;

			case 7:
				typeName = "전사";
				break;

			default:
				typeName = "????";
			}
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
			SC_SystemMessage.send(pc, "\\aD[ " + target.getName() + " ] 직업:" + typeName + ", 혈맹:" + target.getClanname());
			
SC_SystemMessage.send(pc, "\\aDBASE:(힘: " + target.getAbility().getBaseStr() + " / " + "덱: " + target.getAbility().getBaseDex() + " / " 
                        + "콘: " + target.getAbility().getBaseCon() + " / " + "인트: " + target.getAbility().getBaseInt() + " / " 
                        + "위즈: " + target.getAbility().getBaseWis() + " / " + "카리: " + target.getAbility().getBaseCha() + ')');
SC_SystemMessage.send(pc, "\\aHREAL:(힘: " + target.getAbility().getTotalStr() + " / " + "덱: " + target.getAbility().getTotalDex() + " / "
						+ "콘: " + target.getAbility().getTotalCon() + " / " + "인트: " + target.getAbility().getTotalInt() + " / "
						+ "위즈: " + target.getAbility().getTotalWis() + " / " + "카리: " + target.getAbility().getTotalCha() + ')');
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".유저정보 [캐릭명] 으로 입력.");
		}
	}

public void 장비검색(L1PcInstance pc, String param) {
        if (pc == null) return;
        if (param == null || param.isBlank()) {
            SC_SystemMessage.send(pc, ".장비검색 [캐릭명]");
            return;
        }
        final StringTokenizer tokens = new StringTokenizer(param);
        if (!tokens.hasMoreTokens()) {
            SC_SystemMessage.send(pc, ".장비검색 [캐릭명]");
            return;
        }
        final String charname = tokens.nextToken();
        if (charname.equalsIgnoreCase("메티스")) {
            SC_SystemMessage.send(pc, "\\fY검색할 수 없는 운영자입니다.");
            return;
        }

        // Read the persisted snapshot. Never instantiate or save inventory
        // objects simply to display equipment from the database.
        final String sql = """
                SELECT c.objid,i.item_id,i.enchantlvl,i.is_equipped,i.count,i.item_name,i.bless,i.attr_enchantlvl
                FROM characters c LEFT JOIN character_items i ON i.char_id=c.objid
                WHERE c.char_name=?
                ORDER BY i.is_equipped DESC,i.enchantlvl DESC,i.item_id ASC
                """;
        try (Connection db = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setString(1, charname);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭터를 찾을 수 없습니다 **");
                    return;
                }
                SC_SystemMessage.send(pc, "\\fW** 검색 캐릭: " + charname + " (DB 저장 기준) **");
                final ItemTable table = ItemTable.getInstance();
                int count = 0;
                do {
                    final int itemId = rows.getInt(2);
                    if (rows.wasNull()) continue;
                    final L1Item item = table.getTemplate(itemId);
                    if (item == null || (item.getType2() != 1 && item.getType2() != 2)) continue;
                    final String name = getInvenItem(itemId, rows.getInt(3), rows.getInt(4),
                            rows.getInt(5), rows.getString(6), rows.getInt(7), rows.getInt(8));
                    SC_SystemMessage.send(pc, "\\fU" + (++count) + ". " + name);
                } while (rows.next());
                SC_SystemMessage.send(pc, "\\fW** 총 " + count + "건의 아이템이 검색 되었습니다 **");
            }
        } catch (SQLException | RuntimeException failure) {
            LOG.log(Level.WARNING, "Equipment query failed: characterId=" + pc.getId(), failure);
            SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
        }
    }
    private static String getInvenItem(int itemid, int enchant, int equip, int count, String itemname, int bless, int attr) {
        final StringBuilder name = new StringBuilder(48);
        if (enchant > 0) name.append('+');
        if (enchant != 0) name.append(enchant).append(' ');
        if (bless == 0) name.append("축복받은 ");
        else if (bless == 2) name.append("저주받은 ");
        if (attr >= 1 && attr <= 20) {
            name.append(switch ((attr - 1) / 5) {
                case 0 -> "화령:";
                case 1 -> "수령:";
                case 2 -> "풍령:";
                default -> "지령:";
            }).append((attr - 1) % 5 + 1).append("단 ");
        }
        name.append(itemname).append(' ');
        if (equip == 1) name.append("(착용)");
        if (bless == 129) name.append("[봉인]");
        if (count > 1) name.append('(').append(count).append(')');
        return name.toString();
    }

// TODO 중개 거래 게시판

}