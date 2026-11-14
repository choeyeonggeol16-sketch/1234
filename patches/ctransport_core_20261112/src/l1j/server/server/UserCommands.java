package l1j.server.server;
import l1j.server.server.model.network.core.sc.packet.SC_UserCommands17;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;


import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
import static l1j.server.server.model.character.skill.L1SkillId.BONE_BREAK;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.BUYER_COOLTIME;
import static l1j.server.server.model.character.skill.L1SkillId.CURSE_PARALYZE;
import static l1j.server.server.model.character.skill.L1SkillId.DESPERADO;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.FOG_OF_SLEEPING;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJKDASystem.Chart.MJKDAChartScheduler;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.datatables.AuctionSystemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.ItemTradeTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_LetterList;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_War;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.MJFullStater;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.TYUtil;


public class UserCommands{

	boolean spawnTF = false;  

	private static UserCommands _instance; 
	private UserCommands() {
	}

	public static UserCommands getInstance() {
		if (_instance == null) {
			_instance = new UserCommands(); 
		}
		return _instance;
	}
	
	public void handleCommands (L1PcInstance pc, String cmdLine) {
		if (pc == null) {
			return;
		}
		// System.out.println(cmdLine);
		StringTokenizer token = new StringTokenizer(cmdLine);
		// System.out.println(token.hasMoreTokens());
		String cmd = "";
		if (token.hasMoreTokens())
			cmd = token.nextToken();
		else
			cmd = cmdLine;
		String param = "";

		// System.out.println(cmd);

		while (token.hasMoreTokens()) {
			param = new StringBuilder(param).append(token.nextToken()).append(' ').toString();
		}
		param = param.trim();

		try {
			switch (cmd) {
//			 case "충전": 
//			 MJPaymentUserHandler.do_execute(new
//			 MJCommandArgs().setOwner(pc).setParam(param));
//			 break;
			
			case "탐":
			case "탐포인트":
				SC_SystemMessage.send(pc, "보유 탐 포인트: " + pc.getNetConnection().getAccount().getTamPoint() + " 개");
				break;
			case "엔코인":
				pc.sendPackets("보유 N코인:[\\aG"
						+ pc.getNetConnection().getAccount().Ncoin_point + "\\aA]");
				break;
			case "혈맹포인트":
				혈맹포인트(pc);
				break;
				/*
			case "무인낚시":								
				autofish(pc);
				break;
				*/
			case "도움말":
			case "help":
				SC_ShowHelp.send(pc, 1);
				//showHelp(pc);
				break;
//			case "결투":									do_lfc(pc, param); 																break;
/*아데나 거래 게시판
			case "판매등록":
				RPacketBox.send(pc, RPacketBox.GREEN_MESSAGE, "1억 이상의 아데나는 반드시 수표로 변경 후 거래하세요.");
				countR1(pc, param);
				break;
			case "구매신청":
				countR2(pc, param);
				break;				
			case "판매취소":
				countR3(pc, param);
				break;
			case "판매완료":
				countR4(pc, param);
				break;
				*/
			case "수배차단":
				수배차단(pc, param);
				break;
			case "물품판매":
				물품1(pc, param);
			break;

			case "물품구입":
				물품2(pc, param);
			break;
			case "물품취소":
				물품3(pc, param);
			break;
			case "구매취소":
				countR5(pc, param);
				break;
			case "외창":
				outsideChat(pc, param);
				break;			
//			case "복구":									복구(pc);																		break;
			case "나이":
				age(pc, param);
				break;
//			case "드랍":
//				템드랍(pc, param);
//				break;
//			case "\uB9E4\uC785\uC800\uC7A5":
//				AddAccount1(pc,param);
//				break;
//			case "\uB9E4\uC785\uC9C0\uAE09":
//				meep55(pc,param);
//				break;
//			case "후원지급":
//				royal555(pc,param);
//				break;
//			case "후원보상":
//				royal666(pc,param);
//				break;
			case "혈파티":
			case "혈맹파티":
				BloodParty(pc);
				break;
			case "party":
				BloodParty3(pc);
				break;
			case "ment":
				Ment3(pc, param);
				break;
			case "멘트":
				Ment(pc, param);
				break;
			case "수호성":
				Lineff(pc, param);
				break;
			case "라우풀":
				Lawful(pc, param);
				break;
			case "파티멘트":
				Ment1(pc, param);
				break;
			case "bpment":
				Ment4(pc, param);
				break;
			case "마스터멘트":
				master(pc, param);
				break;

			case "좌표복구":
				좌표복구(pc);
				break;
			case "라이트":
				maphack(pc, param);
				break;
			case "light":
				maphack3(pc, param);
				break;
            case "시세":
                l1j.server.server.appcenter.AppCenterService.registerMarketSearch(pc,param);
                break;
			// case "아덴시세": AdSc(pc); break;
			case "물품멘트":
				물품멘트(pc, param);
				break;
//			case "퀘스트멘트":
//				퀘스트멘트(pc, param);
//				break;				
			case "혈마크":
				clanMark(pc);
				break;
			case "emblem":
				clanMark3(pc);
			break;
//			case "타겟팅":									doTarget(new MJCommandArgs().setOwner(pc).setParam(param));						break;
//			case "스텟":									fullstat(pc, param);															break;
			case "차트":	
				setChart(pc, param);															
				break;
			case "구슬조회":
				L1NeigeSaveChar.commands(pc, param);
				break;
//			case "수배":
//				if (Config.수배작동유무) {
//					hunt(pc, param);
//				} else {
//					pc.sendPackets(new SC_SystemMessage("현재 " + cmd + " 명령어는 사용할 수 없습니다. "));
//				}
//				break;
			case "원격교환":
				if (Config.STANDBY_SERVER == false) {
				원격교환(pc, param, pc.getNetConnection());
				} else {
					SC_SystemMessage.send(pc, "오픈대기중에는 원격교환이 금지됩니다.");
				}
				break;
//			case "이름변경":
//				changename(pc, param);
//			break;
			case "텔렉풀기":
			case "텔렉":
			case ".": //텔렉
			case "텔.":
				tell(pc);
				break;
			case "자동물약자동버프":
            case "자동종료":
                pc.sendPackets("기존 서버 자동 기능은 종료되었습니다. 플레이서포터를 사용하세요.");
                break;
				
//			case "보스알림":								spawnNotifyOnOff(pc, param);													break;
			/*case "마일리지":
				if (pc != null) {
					pc.sendPackets("\\fH계정(\\f2" + pc.getAccountName() + "\\fH)에 적립된 마일리지:(\\aG"
							+ pc.getNetConnection().getAccount().Ncoin_point + "\\fH)원");
				}
				break;*/
//			case "엔코인선물":							giftNCoin(pc, param);																break;
//			case "봉인해제신청":							Sealedoff(pc, param);															break;
			case "고정":
			case "고정신청":
				phone(pc, param);
				break;
			//case "무인가입":						autoclanjoin(pc);break;
			case "암호변경":
			case "비번변경":
				changepassword(pc, param);
				break;
			case "체력바":
				monsterHpBar(pc, param);
				break;
			default:
				pc.sendPackets("명령어를 잘못 입력했습니다.");
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	


	public static void WriteLetter22(L1PcInstance pc, String in4, int in2,
			String in5) {
		int nu1 = 949;
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd",
				Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
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

	private static void 물품1(L1PcInstance pc, String count) {
		try {
			int i = 0;
			try {
				i = Integer.parseInt(count);
			} catch (NumberFormatException e) {
			}
			if (i <= 0) {
				// pc.sendPackets(new SC_SystemMessage(".위탁판매  (판매금액)을 적어주세요."));
				SC_SystemMessage.send(pc, "\\fY---------------------------------------------------");
				SC_SystemMessage.send(pc, "\\fY[사용방법] .물품판매 (판매금액)을 입력하면 해당금액의 인증서가 들어옵니다");
				SC_SystemMessage.send(pc, "\\fY그 인증서를 판매할 물품에 클릭하시면 물품거래게시판에 등록이 됩니다.");
				SC_SystemMessage.send(pc, "\\fY---------------------------------------------------");
				return;
			}
			if (i > 200) {
				SC_SystemMessage.send(pc, "판매금액은 20억이상 판매하실수없습니다."); 
				return;
			}
			if (i >= 0 && i < 1) {
				SC_SystemMessage.send(pc, "최소판매금액은 1000만이상 입니다.");
				return;
			}
			if (pc.getInventory().checkItem(474747)) { // 위탁판매 증서 아이템번호는 본인 팩에
														// 맞게
				SC_SystemMessage.send(pc, "물품판매 증서를 소지 하고 있으면 불가능 합니다.");
			} else {
				SC_SystemMessage.send(pc, "\\fY[물품판매 인증서]를 얻었습니다.");
				SC_SystemMessage.send(pc, "\\fY판매물품에 사용하면 게시판에 등록이 됩니다.");
				pc.getInventory().storeItem(474747, i); // 위탁판매 증서 아이템번호는 본인 팩에
															// 맞게
			}
			
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, ".물품판매 (판매수표갯수)");
			SC_SystemMessage.send(pc, "예)  .물품판매 5 (5천만)");
		}
	}

	private static void 물품2(L1PcInstance pc, String count) {
		ItemTradeTable.getInstance().buyTopic(pc, count, false);
	}

	private static void 물품3(L1PcInstance pc, String count) {
		ItemTradeTable.getInstance().cancelTopic(pc, count);
	}

	public static void setChart(L1PcInstance pc, String param) {
		boolean isOn;
		if (!MJKDAChartScheduler.isLoaded()) {
			SC_SystemMessage.send(pc, "사용중이지 않습니다.");
			return;
		}

		if (param.equalsIgnoreCase("켬")) {
			isOn = true;
			SC_SystemMessage.send(pc, "킬 랭킹 차트를 보입니다. 잠시 후 화면에 나타납니다.");
			MJKDAChartScheduler.getInstance().onLoginUser(pc);
		} else if (param.equalsIgnoreCase("끔")) {
			isOn = false;
			SC_SystemMessage.send(pc, "킬 랭킹 차트를 숨깁니다. 리스 후 적용됩니다.");
		} else {
			SC_SystemMessage.send(pc, ".차트 [켬/끔] (왼쪽상단 PK차트 리스트)");
			return;
		}
		if (pc.getKDA() != null)
			pc.getKDA().isChartView = isOn;
	}
	private void clanMark(L1PcInstance pc) {

		long curtime = System.currentTimeMillis() / 1000L;
		if (pc.getQuizTime() + 30L > curtime) {
			long time = pc.getQuizTime() + 30L - curtime;
			SC_ChatPacket.sendDirect(pc, pc, time + " 초 후 사용할 수 있습니다.");
			return;
		}
		if (pc.isDead()) {
			SC_SystemMessage.send(pc, "죽은 상태에선 사용할 수 없습니다.");
			return;
		}
		int i = 1;
		if (pc.문장주시) {
			i = 3;
			pc.문장주시 = false;
		} else {
			pc.문장주시 = true;
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "모든 혈맹의 마크를 표시하거나 종료하였습니다.");
		}
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan != null) {
				SC_War.send(pc, i, pc.getClanname(), clan.getClanName());
			}
		}
		pc.setQuizTime(curtime);
	}
	
	private void clanMark3(L1PcInstance pc) {

	    long curtime = System.currentTimeMillis() / 1000L;
	    
	    // 재사용 대기시간 체크 (Cooldown check)
	    if (pc.getQuizTime() + 30L > curtime) {
	        long time = pc.getQuizTime() + 30L - curtime;
	        SC_ChatPacket.sendDirect(pc, pc, "Please wait " + time + " more seconds.");
	        return;
	    }
	    
	    // 사망 체크 (Death check)
	    if (pc.isDead()) {
	        SC_SystemMessage.send(pc, "You cannot use this while dead.");
	        return;
	    }

	    int i = 1;
	    String message = "";

	    if (pc.문장주시) {
	        i = 3; 
	        pc.문장주시 = false;
	        message = "Clan mark display has been disabled.";
	    } else {
	        i = 1; 
	        pc.문장주시 = true;
	        message = "Clan mark display has been enabled.";
	    }

	    // 결과 메시지 전송
	    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);

	    // 월드의 모든 혈맹 마크 갱신
	    for (L1Clan clan : L1World.getInstance().getAllClans()) {
	        if (clan != null) {
	            SC_War.send(pc, i, pc.getClanname(), clan.getClanName());
	        }
	    }
	    
	    // 쿨타임 기록
	    pc.setQuizTime(curtime);
	}

	void AdSc(L1PcInstance pc) {
		pc.sendPackets("----------------------------------------------------");
		pc.sendPackets("아이템매니아: 초월 → 기타 → 게임머니= ㅍㅍ");
		pc.sendPackets("유저거래시세:(" + Config.Ad_Sc + ") 아데나 = 5,000원");
		pc.sendPackets("시세 이하 판매자/구매자 즉시 압류 조취");
		pc.sendPackets("----------------------------------------------------");
	}

	private void Ment(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.sendPackets("\\f:드랍멘트가 OFF 되었습니다.");
			pc.sendPackets("\\f:기능: 오토루팅템 멘트가 비활성화 되었습니다.");
			pc.RootMent = false;
		} else if (param.equalsIgnoreCase("켬")) {
			pc.sendPackets("\\f:드랍멘트가 ON 되었습니다.");
			pc.sendPackets("\\f:기능: 오토루팅템 멘트가 활성화 되었습니다.");
			pc.RootMent = true;
		} else {
			pc.sendPackets(".멘트 [켬/끔] 입력 하시면 됩니다.");
		}
	}
	
	private void Ment3(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("off")) {
			pc.sendPackets("\\f:dropment off");
			pc.RootMent = false;
		} else if (param.equalsIgnoreCase("on")) {
			pc.sendPackets("\\f:dropment on");
			pc.RootMent = true;
		} else {
			pc.sendPackets(".ment on/off");
		}
	}
	
	private void Lineff(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.sendPackets("\\f:수호성 이팩트가 비활성화 되었습니다.");
			pc.setLinMeff(2);
		} else if (param.equalsIgnoreCase("켬")) {
			pc.sendPackets("\\f:수호성 이팩트가 활성화 되었습니다.");
			pc.setLinMeff(1);
		} else {
			pc.sendPackets(".수호성 [켬/끔] 입력 하시면 됩니다.");
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
	private void master(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.마스터 = false;
			pc.sendPackets("카운터베리어 마스터 멘트 끔");
		} else if (param.equalsIgnoreCase("켬")) {
			pc.마스터 = true;
			pc.sendPackets("카운터베리어 마스터 멘트 켬");
		} else {
			pc.sendPackets(".마스터멘트 [켬/끔] 입력 하시면 됩니다.");
		}
	}


	private void Ment1(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.sendPackets("\\f:파티드랍멘트가 OFF 되었습니다.");
			
			pc.RootMent1 = false;
		} else if (param.equalsIgnoreCase("켬")) {
			pc.sendPackets("\\f:파티드랍멘트가 ON 되었습니다.");
			
			pc.RootMent1 = true;
		} else {
			pc.sendPackets(".파티멘트 [켬/끔] 입력 하시면 됩니다.");
			
		}
	}
	
	private void Ment4(L1PcInstance pc, String param) {
	    if (param.equalsIgnoreCase("off")) {
	        pc.sendPackets("\\f:Party drop announcements are now OFF.");
	        pc.RootMent1 = false;
	    } else if (param.equalsIgnoreCase("on")) {
	        pc.sendPackets("\\f:Party drop announcements are now ON.");
	        pc.RootMent1 = true;
	    } else {
	        pc.sendPackets("Usage: .bpment [on/off]");
	    }
	}
	


	
	  private void Sealedoff(L1PcInstance pc, String param) 
	  { try { StringTokenizer
	  tok = new StringTokenizer(param); 
	  String param1 = tok.nextToken(); 
	  int off = Integer.parseInt(param1); 
	  if (off > 10 || off < 0) { 
		  pc.sendPackets("해제 주문서는 [10]이상 신청불가능합니다.");
		  return;
	  } 
	  if (off == 0) {
	  pc.setSealScrollCount(0); pc.setSealScrollTime(0);
	  pc.sendPackets("해제주문서 신청이 초기화되었습니다."); 
	  } else {
		  int sealScrollTime = (int)(System.currentTimeMillis() / 1000) + 1 * 24 * 3600;
	  pc.setSealScrollTime(sealScrollTime);
	  pc.setSealScrollCount(off);
	  pc.sendPackets("해제 주문서 [" + off + "]장이 신청되었습니다.");
	  pc.sendPackets("오늘날짜로부터 [1]일 뒤에 자동지급 됩니다.");
	  pc.sendPackets("재신청할경우 [1]일 초기화 됩니다."); } 
	  pc.save(); } 
	  catch (Exception e) 
	  {
	  pc.sendPackets(".봉인해제신청 [신청할 장수]"); } }

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

	public static void doTarget(MJCommandArgs args) {
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
			
			CIOClient client = pc.getNetConnection();
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
		char[] acArray = s.toCharArray();
		for (char ac : acArray) {
			if (((ac >= 'A') && (ac <= 'z')) || ((ac >= 'a') && (ac <= 'z')))
				return true;
			if ((ac >= '0') && (ac <= '9'))
				return true;
			if ((ac >= 44032) && (ac <= 55203)) {
				return true;
			}
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
	public void 원격교환(L1PcInstance pc, String cmd, CIOClient clientthread) {
		try {
			StringTokenizer st = new StringTokenizer(cmd);
			String Trade_name = st.nextToken();
			L1PcInstance target = null;
			target = L1World.getInstance().getPlayer(Trade_name);
			clientthread.getActiveChar();
            if(pc.getIsChangeItem() || pc.getIsChangeItem1() ||
                    pc.getIsChangeItem2() || pc.getIsChangeItem3() ||
                    pc.getIsChangeItem4() || pc.getIsChangeItem5() || 
                    pc.getIsChangeItem6() || pc.getIsChangeItem7() ||
                    pc.getIsChangeItem8() || pc.getIsChangeItem9() ||
                    pc.getIsChangeItem10() || pc.getIsChangeItem11() ||
                    pc.getIsChangeItem12() || pc.getIsChangeItem13() ||
                    pc.getIsChangeItem14() || pc.getIsChangeItem15() ||
                    pc.getIsChangeItem16() || pc.getIsChangeItem17() ||
                    pc.getIsChangeItem18() || pc.getIsChangeItem19() ||
                    pc.getIsChangeItem20()     )
                 {
                     pc.sendPackets("변경권을 사용중이거나, 취소하셨으므로 리스후에 교환가능합니다.");
                     return;
                 }
                 if(target.getIsChangeItem() || target.getIsChangeItem1() ||
                 		target.getIsChangeItem2() || target.getIsChangeItem3() ||
                 		target.getIsChangeItem4() || target.getIsChangeItem5() ||
                 		target.getIsChangeItem6() || target.getIsChangeItem7() ||
                 		target.getIsChangeItem8() || target.getIsChangeItem9() ||
                 		target.getIsChangeItem10() || target.getIsChangeItem11() ||
                 		target.getIsChangeItem12() || target.getIsChangeItem13() ||
                 		target.getIsChangeItem14() || target.getIsChangeItem15() ||
                 		target.getIsChangeItem16() || target.getIsChangeItem17() ||
                 		target.getIsChangeItem18() || target.getIsChangeItem19() ||
                 		target.getIsChangeItem20()     )
                      {
                          pc.sendPackets("상대방이 변경권을 사용중이거나, 취소하셨으므로 리스후에 교환가능합니다.");
                          return;
                      }
			if (target != null) {
				if (target.isGm()) {
					SC_SystemMessage.send(pc, "\\f:운영자에게는 원격거래를 신청할 수 없습니다.");
					return;
				}
				if (target.isPrivateShop() || target.isFishing() || target.isDeathMatch() 
						 || target.isParalyzed() ) {
					SC_SystemMessage.send(pc, "\\f:상대방이 원격교환을 받을수 없는 상태입니다.");
					return;
				}
				if (Trade_name.equals(pc.getName())) {
					SC_SystemMessage.send(pc, "\\f:자신에게는 원격교환을 신청할 수 없습니다.");
					return;
				}
				if (target.getTradeID() > 0) {
					SC_SystemMessage.send(pc, "\\f:상대방은 현재 교환 중인 상태 입니다.");
					return;
				}
				int[] 원격맵 = TYUtil.GetIntTokenizeIntArray(Config.원격맵);
		        for (int i = 0; i < 원격맵.length; i++) {
		          if (pc.getMapId() == 원격맵[i]){
		        		SC_SystemMessage.send(pc, "\\f:해당 맵에서는 원격교환이 불가능합니다.");
						return;
					}
		        }
				
		        int[] 원격맵1 = TYUtil.GetIntTokenizeIntArray(Config.원격맵);
		        for (int i = 0; i < 원격맵1.length; i++) {
		          if (target.getMapId() == 원격맵1[i]){
		        	  SC_SystemMessage.send(pc, "\\f:해당 유저는 원격교환이 불가능한 곳에 있습니다.");
						return;
					}
		        }
		        
				if (pc.getAccountName().equalsIgnoreCase(target.getAccountName())) {
					SC_Disconnect.send(pc);
					SC_Disconnect.send(target);
					SC_SystemMessage.send(pc, "\\f:뭔데 ?");
					return;
				}
				if (!target.isParalyzed()) {
					pc.setTradeID(target.getId());
					target.setTradeID(pc.getId());
					SC_MessageYN.send(target, 252, pc.getName());
					SC_SystemMessage.send(pc, "\\f:" + target.getName() + "님에게 거래를 신청했습니다.");
				} else {
					SC_SystemMessage.send(pc, "\\f:상대방이 거래를 취소 하였습니다.");
				}
			} else {
				SC_SystemMessage.send(pc, "\\f:해당 유저는 접속해 있지 않습니다.");
				
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, "\\f:.원격교환 [캐릭터명]");
		}
	}
	private void hunt(L1PcInstance pc, String cmd) {
		int price = Config.수배금액;
		try{
			StringTokenizer tok = new StringTokenizer(cmd);
			String name = tok.nextToken();
			if(name == null || name.equals(""))
				throw new Exception();
			
			L1PcInstance target = L1World.getInstance().getPlayer(name);
			if(target == null){
				pc.sendPackets(String.format("%s 님을 찾을 수 없습니다.", name));
				return;
			}
			if(target.hasSkillEffect(L1SkillId.나루토감사캔디)){
				pc.sendPackets(String.format("%s 님은 수배 차단 중 입니다.", name));
				return;
			}
			if(target.hasSkillEffect(L1SkillId.USER_WANTED) || target.hasSkillEffect(L1SkillId.USER_WANTED1)){
				pc.sendPackets(String.format("%s 님은 이미 수배중입니다.", name));
				return;
			}
			if(target.isGm()){
				pc.sendPackets("운영자에게 수배를 걸수없습니다.");
				return;
			}
			if (!(pc.getInventory().checkItem(40308, price))) {
				pc.sendPackets("아데나가 부족합니다");
				return;
			}
			
			String message = String.format("\\fY%s 님께서 %s님에게 수배를 걸었습니다.", pc.getName(), target.getName());
			String message1 = String.format("\\fY수배범을 잡을 시 확률적으로 수배범 2단으로 승급됩니다.");
			pc.sendPackets(message);
			target.sendPackets(message);
			target.sendPackets(message1);
			//target.sendPackets("\\fY수배효과: 추타+3,명중+3,AC-3,SP+3");
			//L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\fY["+target.getName()+"]님이 수배범이 되었습니다."));
			//L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\fY[수배자]: "+pc.getName()));
			
			
			target.setSkillEffect(L1SkillId.USER_WANTED, -1);
			target.doWanted(true);
			pc.getInventory().consumeItem(40308, price);
		}catch(Exception e){
			pc.sendPackets("\\fY.수배 [캐릭터명] ("+Config.수배금액 / 10000 +"만)");
			pc.sendPackets("\\fY수배효과: 추타+3,명중+3,AC-3,SP+3");
			
		}
	}
	private void 퀘스트멘트(L1PcInstance pc, String param) {
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (pc.퀘스트멘트 == true) {
					SC_SystemMessage.send(pc, "이미 퀘스트멘트 활성화 상태입니다.");
					return;
				}
				
				pc.퀘스트멘트 = true;
				
				SC_SystemMessage.send(pc, "퀘스트몬스터를 잡을 때 마다 남은 마릿수가 채팅창에 출력됩니다");
				
			} else if (status.equalsIgnoreCase("끔")) {
				if (pc.퀘스트멘트 == false) {
					SC_SystemMessage.send(pc, "이미 퀘스트멘트는 비활성화상태입니다.");
					return;
				}
				
				pc.퀘스트멘트 = false;
				
				SC_SystemMessage.send(pc, "\\f:퀘스트멘트가 비활성화되었습니다.");
			
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(pc, ".퀘스트멘트 [켬/끔] 으로 입력하세요.");
			
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
	private void 물품멘트(L1PcInstance pc, String param) {
		int count = 0;
		int ccount = 0;
		
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (pc.물품멘트 == true) {
					SC_SystemMessage.send(pc, "이미 물품멘트 활성화 상태입니다.");
					return;
				}
				
				pc.물품멘트 = true;
				
				SC_SystemMessage.send(pc, "\\f:현 시간부로 거래게시판에 물품이 업로드 될 경우 채팅창에 출력됩니다.");
				
			} else if (status.equalsIgnoreCase("끔")) {
				if (pc.물품멘트 == false) {
					SC_SystemMessage.send(pc, "이미 물품멘트는 비활성화상태입니다.");
					return;
				}
				
				pc.물품멘트 = false;
				
				SC_SystemMessage.send(pc, "\\f:물품멘트가 비활성화되었습니다.");
			
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(pc, ".물품멘트 [켬/끔] 으로 입력하세요.");
			SC_SystemMessage.send(pc, "켬으로 할 시 거래게시판 물품이 업로드 됐을 경우 실시간으로 채팅창에 출력됩니다.");
		}
	}
	
	private void phone(L1PcInstance pc, String param) {
		try {
			long curtime = System.currentTimeMillis() / 1000;
			if (pc.getQuizTime() + 10 > curtime) {
				long sec = (pc.getQuizTime() + 10) - curtime;
				pc.sendPackets(sec + "초 후에 사용할 수 있습니다.");
				return;
			}

			StringTokenizer tok = new StringTokenizer(param);
			String phone = tok.nextToken();
			Account account = Account.load(pc.getAccountName());
			if (param.length() < 10) {
				pc.sendPackets("없는 번호입니다. 다시 입력해주세요.");
				pc.sendPackets("* 경고: 잘못된 정보 입력시 게임 이용에 제재를 받을 수 있습니다.");
				return;
			}
			if (param.length() > 11) {
				pc.sendPackets("잘못된 번호입니다. 다시 입력해주세요.");
				pc.sendPackets("* 경고: 잘못된 정보 입력시 게임 이용에 제재를 받을 수 있습니다.");
				return;
			}
			if (isDisitAlpha(phone) == false) {
				pc.sendPackets("숫자로만 입력하세요.");
				pc.sendPackets("* 경고: 잘못된 정보 입력시 게임 이용에 제재를 받을 수 있습니다.");
				return;
			}
			if (account.getphone() != null) {
				pc.sendPackets("이미 전화번호가 설정되어 있습니다.");
				pc.sendPackets("번호 변경시 메티스에게 편지로 연락처를 보내세요.");
				return;
			}

			account.setphone(phone);
			Account.updatePhone(account);
			pc.sendPackets(" " + phone + " 설정 완료. 초기화 시 문자발송됩니다.");
			보안버프(pc);
		} catch (Exception e) {
			pc.sendPackets("고정을 하시게 되시면 재오픈 또는 공지를 사전에 알수있습니다.");
		}
	}

	private static void 보안버프(L1PcInstance pc) {
		pc.getAC().addAc(-1);
		SC_PacketBox.send(pc, pc, SC_PacketBox.ICON_SECURITY_SERVICES);
		SC_OwnCharStatus.send(pc);
	}

	private void spawnNotifyOnOff(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				if (pc.isBossNotify()) {
					pc.sendPackets("보스알림:보스소환 및 알림이 이미 활성화 상태입니다.");
					return;
				}
				pc.setBossNotify(true);
				pc.sendPackets("보스알림:켬 (보스소환 및 알림이 실행 되었습니다)");
			} else if (on.equals("끔")) {
				if (!pc.isBossNotify()) {
					pc.sendPackets("보스알림:보스소환 및 알림이 활성화 상태가 아닙니다.");
					return;
				}
				pc.setBossNotify(false);
				pc.sendPackets("보스알림:끔 (보스소환 및 알림이 종료 되었습니다)");
			} else {
				pc.sendPackets("잘못된 요청입니다.");
			}
		} catch (Exception e) {
			pc.sendPackets(".보스알림 [켬, 끔]으로 설정하세요. 현재(" + (pc.isBossNotify() == true ? "켜짐" : "꺼짐") + ")입니다.");
		}
	}

	private void maphack(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (pc.getMapId() == 132) {
				SC_Ability.send(pc, 3, false);
				pc.sendPackets("현재 맵에서는 해당명령어를 사용할 수 없습니다.");
				return;
			}
			if (on.equalsIgnoreCase("켬")) {
				SC_Ability.send(pc, 3, true);
				pc.sendPackets("라이트: 켜짐");
			} else if (on.equals("끔")) {
				SC_Ability.send(pc, 3, false);
				pc.sendPackets("라이트: 꺼짐");
			}
		} catch (Exception e) {
			pc.sendPackets(".라이트 켬 또는 끔 으로 설정 해주시길 바랍니다.");
		}
	}
	
	private void maphack3(L1PcInstance pc, String param) {
	    try {
	        StringTokenizer st = new StringTokenizer(param);
	        if (!st.hasMoreTokens()) {
	            pc.sendPackets("Usage: .light [on/off]");
	            return;
	        }

	        String on = st.nextToken();
	        
	        // 특정 맵(132번) 제한 로직
	        if (pc.getMapId() == 132) {
	            SC_Ability.send(pc, 3, false);
	            pc.sendPackets("This command cannot be used in this map.");
	            return;
	        }

	        // 입력값 비교 (on/off)
	        if (on.equalsIgnoreCase("on")) {
	            SC_Ability.send(pc, 3, true);
	            pc.sendPackets("Light: ON");
	        } else if (on.equalsIgnoreCase("off")) {
	            SC_Ability.send(pc, 3, false);
	            pc.sendPackets("Light: OFF");
	        } else {
	            pc.sendPackets("Usage: .light [on/off]");
	        }
	    } catch (Exception e) {
	        pc.sendPackets("Usage: .light [on/off]");
	    }
	}


	/*
	 * private void 복구(L1PcInstance pc) { try { long curtime =
	 * System.currentTimeMillis() / 1000; if (pc.getQuizTime2() + 5 > curtime) {
	 * long time = (pc.getQuizTime2() + 5) - curtime; pc.sendPackets( time +
	 * "초 후 사용할 수 있습니다."); return; } Updator.
	 * exec("UPDATE characters SET LocX=33432,LocY=32807,MapID=4 WHERE account_name=? and MapID not in (38,5001,99,997,5166,39,34,701,2000)"
	 * , new Handler(){
	 * 
	 * @Override public void handle(PreparedStatement pstm) throws Exception {
	 * pstm.setString(1, pc.getAccountName()); } });
	 * pc.sendPackets("모든 케릭터의 좌표가 정상적으로 복구 되었습니다."); pc.setQuizTime(curtime); }
	 * catch (Exception e) { } }
	 */

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

	    if (pc.isDead()) {
	        pc.sendPackets("죽은 상태에선 사용할 수 없습니다.");
	        return;
	    }

	    L1Clan clan = pc.getClan();
	    if (clan == null) {
	        pc.sendPackets("혈맹에 가입된 상태가 아닙니다.");
	        return;
	    }

	    int rank = pc.getClanRank();
	    boolean hasAuthority = (rank == L1Clan.군주 || rank == L1Clan.부군주 || rank == L1Clan.수호);
	    
	    if (!hasAuthority) {
	        pc.sendPackets("혈맹의 군주, 부군주, 수호기사만 사용 가능합니다.");
	        return;
	    }

	    pc.setPartyType(1); // 자동분배 설정
	    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f:혈맹원들에게 혈맹파티를 신청하였습니다.");
	    pc.sendPackets("\\fQ자동분배는 한 화면에 파티원들이 있을 경우 분배됩니다.");

	    for (L1PcInstance member : clan.getOnlineClanMember()) {
	        
	        if (member.getId() == pc.getId() || member.isPrivateShop() || member.isInParty() || member.getAutoHunt()) {
	            continue;
	        }

	        member.setPartyID(pc.getId());
	        SC_MessageYN.send(member, 954, pc.getName()); 
	        
	        SC_PacketBox.send(member, SC_PacketBox.GREEN_MESSAGE, "\\f:귀하의 혈맹에서 혈맹파티 신청이 들어왔습니다 (Y/N)");
	        member.sendPackets("\\fQ자동분배는 한 화면에 파티원들이 있을 경우 분배됩니다.");
	    }
	}

	private void age(L1PcInstance pc, String cmd) {
		try {
			StringTokenizer tok = new StringTokenizer(cmd);
			String AGE = tok.nextToken();
			int AGEint = Integer.parseInt(AGE);
			if (AGEint > 59 || AGEint < 14) {
				pc.sendPackets("자신의 실제 나이로 설정하세요.");
				return;
			}
			pc.setAge(AGEint);
			pc.save();
			pc.sendPackets("명령어: 당신의 나이가 [" + AGEint + "] 설정되었습니다.");
		} catch (Exception e) {
			pc.sendPackets(".나이 숫자 형식으로 입력.(혈맹 채팅 시 표시됨)");
		}
	}
	
	public void BloodParty3(L1PcInstance pc) {

	    if (pc.isDead()) {
	        pc.sendPackets("You cannot use this while dead.");
	        return;
	    }

	    L1Clan clan = pc.getClan();
	    if (clan == null) {
	        pc.sendPackets("You are not currently in a clan.");
	        return;
	    }

	    int rank = pc.getClanRank();
	    // rank 값 비교는 내부 코드이므로 유지합니다.
	    boolean hasAuthority = (rank == L1Clan.군주 || rank == L1Clan.부군주 || rank == L1Clan.수호);
	    
	    if (!hasAuthority) {
	        pc.sendPackets("Only the Clan Prince, Vice-Prince, or Guardian can use this.");
	        return;
	    }

	    pc.setPartyType(1); // Auto-distribution
	    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f:Clan party invitation has been sent to online members.");
	    pc.sendPackets("\\fQAuto-loot only works when party members are on the same screen.");

	    for (L1PcInstance member : clan.getOnlineClanMember()) {
	        
	        if (member.getId() == pc.getId() || member.isPrivateShop() || member.isInParty()|| member.getAutoHunt()) {
	            continue;
	        }

	        member.setPartyID(pc.getId());
	        SC_MessageYN.send(member, 954, pc.getName()); 
	        
	        SC_PacketBox.send(member, SC_PacketBox.GREEN_MESSAGE, "\\f:You have received a clan party invitation. (Y/N)");
	        member.sendPackets("\\fQAuto-loot only works when party members are on the same screen.");
	    }
	}

	private static boolean isDisitAlpha(String str) {
		boolean check = true;
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i)) // 숫자가 아니라면
					&& !Character.isUpperCase(str.charAt(i)) // 대문자가 아니라면
					&& !Character.isLowerCase(str.charAt(i))) { // 소문자가 아니라면
				check = false;
				break; 
			}
		}
		return check;
	}

	private void changepassword(L1PcInstance pc, String param) {
		try {
			if (pc.get_lastPasswordChangeTime() + 10 * 60 * 1000 > System.currentTimeMillis()) {
				pc.sendPackets("암호를 변경하신지 10분이 지나지 않았습니다. 잠시후 다시 변경하세요.");
				return;
			}
			StringTokenizer tok = new StringTokenizer(param);
			String newpasswd = tok.nextToken();
			if (newpasswd.length() < 6) {
				pc.sendPackets("6자 ~ 16자 사이의 영어나 숫자로 입력하세요.");
				return;
			}
			if (newpasswd.length() > 16) {
				pc.sendPackets("6자 ~ 16자 사이의 영어나 숫자로 입력하세요.");
				return;
			}
			if (isDisitAlpha(newpasswd) == false) {
				pc.sendPackets("영어와 숫자로만 입력하세요.");
				return;
			}
			to_Change_Passwd(pc, newpasswd);

		} catch (Exception e) {
			pc.sendPackets(".암호변경 [변경할암호]로 입력 하세요.");
		}
	}


	private static void 케릭변경(L1PcInstance pc) {
		if (pc.isSkillMastery(91) && pc.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())) {
			pc.getInventory().storeItem(887799, 1);		
			pc.getInventory().storeItem(887700, 1);	
		} else if (pc.isSkillMastery(91)) {
			pc.getInventory().storeItem(887799, 1);
		} else if (pc.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())) {
			pc.getInventory().storeItem(887700, 1);
		} else if (pc.isSkillMastery(77)) {
			pc.getInventory().storeItem(887799, 1);
		} else if (pc.isSkillMastery(112) && pc.isPassive(MJPassiveID.ARMOR_BREAK_DESTINY.toInt())) {
			pc.getInventory().storeItem(887799, 1);		
			pc.getInventory().storeItem(887700, 1);	
		} else if (pc.isSkillMastery(112)) {
			pc.getInventory().storeItem(887799, 1);
		} else if (pc.isPassive(MJPassiveID.ARMOR_BREAK_DESTINY.toInt())) {
			pc.getInventory().storeItem(887700, 1);
		} else if (pc.isSkillMastery(230) && pc.isPassive(MJPassiveID.DESPERADO_ABSOLUTE.toInt())) {
			pc.getInventory().storeItem(887799, 1);		
			pc.getInventory().storeItem(887700, 1);	
		} else if (pc.isSkillMastery(230)) {
			pc.getInventory().storeItem(887799, 1);
		} else if (pc.isPassive(MJPassiveID.DESPERADO_ABSOLUTE.toInt())) {
			pc.getInventory().storeItem(887700, 1);
		} 
	}


	private void 수배차단(L1PcInstance pc, String param) {
		try {
			if (!(pc.getInventory().checkItem(40308, Config.수배금액))) {
				pc.sendPackets("아데나가 부족합니다");
				return;
			}
			if (param.equalsIgnoreCase("켬")) {
				SC_InventoryIcon.icoEnd(pc, L1SkillId.수배);
				pc.removeSkillEffect(L1SkillId.USER_WANTED);
				SC_InventoryIcon.icoEnd(pc, L1SkillId.수배1);
				pc.removeSkillEffect(L1SkillId.USER_WANTED1);
				pc.setSkillEffect(L1SkillId.나루토감사캔디, -1);
				SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.나루토감사캔디, 3257, true);
				pc.getInventory().consumeItem(40308, Config.수배금액); 
				SC_SystemMessage.send(pc, "\\f:▶수배 시스템을 OFF하였습니다. ◀");
			} else if (param.equalsIgnoreCase("끔")) {

				pc.removeSkillEffect(L1SkillId.나루토감사캔디);
				SC_InventoryIcon.icoEnd(pc, L1SkillId.나루토감사캔디);
				pc.getInventory().consumeItem(40308, Config.수배금액); 
				SC_SystemMessage.send(pc, "\\f:▶ 수배시스템을 ON하였습니다.◀");
			} else if(param.equalsIgnoreCase("")) {
				pc.sendPackets(".수배차단 켬/끔");
				pc.sendPackets("다른사람 및 본인은 수배를 걸지 못합니다.");
			}
		} catch (Exception eeeeeeee) {
			
		}
	}
	   private void 좌표복구(L1PcInstance pc){
	    
		Connection connection = null;
		PreparedStatement preparedstatement = null;
		Connection connection2 = null;
		PreparedStatement preparedstatement2 = null;
		ResultSet rs = null;
		try {
			connection2 = DatabaseFactory.getInstance().getConnection();
			preparedstatement2 = connection2.prepareStatement("SELECT * FROM characters WHERE account_name = ?");
			preparedstatement2.setString(1, pc.getAccountName());
			rs = preparedstatement2.executeQuery();
			while (rs.next()) {
				int objid = rs.getInt("objid");
				int mapid = rs.getInt("MapID");
				
				if (mapid != 99 && mapid != 6202 && mapid != 3 && mapid != 1284 && mapid != 34 && mapid != 1708
						&& mapid != 38 && mapid !=5001 && mapid != 997 && mapid !=  5166 && mapid !=  39 && mapid != 701 && mapid !=  2000) {
					try {
						connection = DatabaseFactory.getInstance().getConnection();
						preparedstatement = connection.prepareStatement("UPDATE characters SET LocX=33438, LocY=32805, MapID=4 WHERE objid = ?");
						preparedstatement.setInt(1, objid);
						preparedstatement.executeUpdate();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						SQLUtil.close(preparedstatement);
						SQLUtil.close(connection);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(preparedstatement2);
			SQLUtil.close(connection2);
		}
		SC_SystemMessage.send(pc, "모든 케릭터의 좌표가 정상적으로 복구 되었습니다.");
	    }
	    
	private void to_Change_Passwd(L1PcInstance pc, String passwd) {
		Selector.exec("select account_name from characters where char_name=?", new SelectorHandler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, pc.getName());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					final String login = rs.getString("account_name");
					Updator.exec("update accounts set password=? where login=?", new Handler() {
						@Override
						public void handle(PreparedStatement pstm) throws Exception {
							pstm.setString(1, passwd);
							pstm.setString(2, login);
						}
					});
				}
			}
		});
		pc.sendPackets(String.format("당신의 계정 암호가 (%s)로 변경되었습니다.", passwd));
	}

	// 패스워드 맞는지 여부 리턴
	public static boolean isPasswordTrue(String Password, String oldPassword) {
		String _rtnPwd = null;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		boolean result = false;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT password(?) as pwd");

			pstm.setString(1, oldPassword);
			rs = pstm.executeQuery();
			if (rs.next()) {
				_rtnPwd = rs.getString("pwd");
			}
			if (_rtnPwd.equals(Password)) { // 동일하다면
				result = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
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
			CIOClient client = pc.getNetConnection();
			pc.setNetConnection(null);
			client.setConnectionState(ConnectionState.OUT_GAME);
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

	private void outsideChat(L1PcInstance pc, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				pc.setOutSideChat(true);
			} else if (param.equalsIgnoreCase("끔")) {
				pc.setOutSideChat(false);
			} else if (param.equalsIgnoreCase("상태")) {

			} else
				throw new Exception();

			pc.sendPackets(String.format("외창 : %s", pc.isOutsideChat() ? "켜짐" : "꺼짐"));
		} catch (Exception e) {
			pc.sendPackets(".외창 [켬/끔/상태]");
		}
	}
	private void 계좌조회 (L1PcInstance pc) {
		try {			

			SC_UserCommands17.send(pc, 1);

				
					} catch (Exception exception) {
						pc.sendPackets("");

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
			MJFullStater.running(pc, s, Integer.parseInt(arr[1]));
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
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
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
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
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
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
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
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
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
	private void 혈맹포인트(L1PcInstance pc) {
		if (Config.혈맹포인트여부 == false ) {
			SC_SystemMessage.send(pc, "현재 혈맹포인트 시스템은 비활성화입니다.");
		      return;
		}
		
		if (Config.혈맹포인트조회) {
		if (!(pc.getClanRank() == 9 || pc.getClanRank() == 10))  { 
			SC_SystemMessage.send(pc, "군주 또는 수호기사만 조회가 가능합니다.");
		      return;
		}
		}
		if (pc.getClanid() != 0) {
		SC_SystemMessage.send(pc, "\\f:현재 혈맹포인트: ["+pc.getClan().getWarPoint()+"]");
		} else {
			SC_SystemMessage.send(pc, "혈맹에 가입되어 있지 않아 조회할 수 없습니다.");
		}
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
	private static void countR1(L1PcInstance pc, String count) {
		StringTokenizer st = new StringTokenizer(count);
		try {
			int i = 0;
			int sellcount = 0;
			String bankname = null;
			String name = null;
			String numeber = null;
			try {
				i = Integer.parseInt(st.nextToken());
				sellcount = Integer.parseInt(st.nextToken());
				bankname = st.nextToken();
				name = st.nextToken();
				numeber = st.nextToken();
			} catch (NumberFormatException e) {
			}

			if (i <= 0) {
				pc.sendPackets(".판매등록  (판매금액)을 적어주세요.");
				return;
			}
			if (pc.getLevel() < Config.ADENASHOP_LEVEL) {
				pc.sendPackets("판매등록 최소 (" + Config.ADENASHOP_LEVEL + ")레벨 이상만 등록 가능합니다.");
				return;
			}
			if (i > Config.MAX_SELL_ADENA) {
				pc.sendPackets("최대판매 수표는 " + Config.MAX_SELL_ADENA + " 장 이상 판매하실 수 없습니다.");
				return;
			}
			if (i >= 1 && i < Config.MIN_SELL_ADENA) {
				pc.sendPackets("최소판매 수표는 " + Config.MIN_SELL_ADENA + " 장 이상 입니다.");
				return;
			}
			if (sellcount < Config.MIN_SELL_CASH) {
				pc.sendPackets("최소판매금액은 " + Config.MIN_SELL_CASH + "원 이상 입니다.");
				return;
			}
			if (sellcount > Config.MAX_SELL_CASH) {
				pc.sendPackets("최대판매금액은 " + Config.MAX_SELL_CASH + "원 이하 입니다.");
				return;
			}

			String selltype = "판매중";
			L1Item temp = ItemTable.getInstance().getTemplate(600254); //원본은 1억 수표 400254
			L1ItemInstance adena = new L1ItemInstance(temp, i);

			if (pc.getInventory().checkItem(600254, i)) {//원본은 1억 수표 400254
				pc.getInventory().consumeItem(600254, i); //원본은 1억 수표 400254
				AuctionSystemTable.getInstance().writeTopic(pc, selltype, adena, i, sellcount, bankname, numeber, name);
				WriteSellLetter(pc.getName(), i, sellcount, bankname, name, numeber, pc.getName());
				//WriteSellLetter("메티스", i, sellcount, bankname, name, numeber, pc.getName());
			} else {
				pc.sendPackets("입력하신 숫자가 소지하신 수표보다 작습니다.");
			}
		} catch (Exception exception) {
			pc.sendPackets(".판매등록 (수표갯수) (판매금액) (은행명) (예금주) (계좌)");
			pc.sendPackets(".판매등록 70 5000 한국은행 홍길동 123123(-제외)");
			
		}
	}


	private static void countR2(L1PcInstance pc, String count) {
		if (pc.hasSkillEffect(BUYER_COOLTIME)) {
			int n = pc.getSkillEffectTimeSec(BUYER_COOLTIME);
			pc.sendPackets("이미 다른 물품을 구매중이거나 쿨타임이 적용중입니다.");
			SC_SystemMessage.send(pc, String.format("%d초 후에 구매신청을 하실 수 있습니다.", n));
			return;
		}
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		String charname = null;
		int adenacount = 0;
		int sellcount = 0;
		int status = 0;
		String bank = null;
		String banknumeber = null;
		String bankname = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT  id, name, count, sellcount, status, bank, banknumber, bankname from Auction where id Like '"
							+ i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				charname = rs.getString("name");
				adenacount = rs.getInt("count");
				sellcount = rs.getInt("sellcount");
				status = rs.getInt("status");
				bank = rs.getString("bank");
				banknumeber = rs.getString("banknumber");
				bankname = rs.getString("bankname");
			}

			if (charname == null) {
				pc.sendPackets("잘못된 게시번호 입니다.");
				return;
			}
			if (status == 1) {
				pc.sendPackets("이미 다른사람이 구매신청을 한 상태입니다.");
				return;
			}
			if (status == 2) {
				pc.sendPackets("이미 판매 완료된 물품입니다.");
				return;
			}

			try {
				int time = 600;
				WriteBuyLetter(pc.getName(), adenacount, sellcount, bank, bankname, banknumeber, charname,
						pc.getName());
				WriteBuyLetter(charname, adenacount, sellcount, bank, bankname, banknumeber, charname, pc.getName());
				//WriteBuyLetter("메티스", adenacount, sellcount, bank, bankname, banknumeber, charname, pc.getName());
				AuctionSystemTable.getInstance().AuctionUpdate(i, pc.getName(), pc.getAccountName(), "입금대기", 1);
				pc.setSkillEffect(BUYER_COOLTIME, time * 1000);
			} catch (Exception e) {
				e.printStackTrace();
			}

		} catch (SQLException e) {
			pc.sendPackets(".구매신청 (게시글번호)");
			pc.sendPackets(".구매신청 0017");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	private static void meep55(L1PcInstance pc, String login) {


		Connection con = null;
		PreparedStatement pstm = null;

		ResultSet rs = null;
	
		int itemcount = 0;
		String account = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT login, count from meep where login Like '" + login + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {

				itemcount = rs.getInt("count");
				account = rs.getString("login");
			} 
			if (account == null) {
				pc.sendPackets("저장된 매입정보가 없습니다.");
				return;
			}
			 int count = (int) ((itemcount*Config.개당지급) *((Config.지급율*1.0)/100)) ;
			 L1ItemInstance item = ItemTable.getInstance().createItem(Config.지급아이템);
			 String name = item.getItem().getName();
			if (pc.getAccountName().equalsIgnoreCase(account)) {
				if(count >= Config.최대지급) {
					pc.getInventory().storeItem(Config.지급아이템, Config.최대지급);
					pc.sendPackets("최대 지급 갯수를 초과하여 "+name+" "+Config.최대지급+"개 지급완료");
					AuctionSystemTable.getInstance().deleteTopic3(login);
				} else {
					pc.getInventory().storeItem(Config.지급아이템, count);
					AuctionSystemTable.getInstance().deleteTopic3(login);
					pc.sendPackets("증표 1개당 ["+name+" "+Config.개당지급+"] 개->"+Config.지급율+" % = 총지급율" );
					pc.sendPackets("지급율 : " + Config.지급율 + " %");
					pc.sendPackets("총 지급 갯수 : "+name+" "+count+" 개");
				}

			} else {
				pc.sendPackets("아이디가 다릅니다.");
			}
		} catch (SQLException e) {
			pc.sendPackets(".매입지급 [계정아이디] 라고 적어주십시요.");
			pc.sendPackets("예)  .매입지급 tor6157938");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	private void royal555(L1PcInstance pc, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String account1 = tok.nextToken();
			int count11 = Integer.parseInt(tok.nextToken());
			if(account1 == null) {
				pc.sendPackets("\\f4.후원지급 [입금자명] [금액](만단위) 라고 적어주십시요.");
				pc.sendPackets("예)  \\f4.후원지급 홍길동 10");
				return;
			}
			royal55(pc, account1, count11);
		} catch (Exception e) {
			pc.sendPackets("\\f4.후원지급 [입금자명] [금액](만단위) 라고 적어주십시요.");
			pc.sendPackets("예)  \\f4.후원지급 홍길동 10");
		}
	}
	private static void royal55(L1PcInstance pc, String account1, int count11) {
		

		Connection con = null;
		Connection con1 = null;
		Connection con2 = null;
		PreparedStatement pstm = null;
		PreparedStatement pstm1 = null; 
		PreparedStatement pstm2 = null; 

		ResultSet rs = null;
		ResultSet rs2 = null;
	
		int itemcount = 0;
		int no1 = 0;
		int count34 = 0;
		int item = 0;
		String account = null;
		String account33 = null;


		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT login, count from royal where login Like '" + account1 + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {

				itemcount = rs.getInt("count");
				account = rs.getString("login");

			}
			if (account == null) {
				pc.sendPackets("저장된 후원입금정보가 없습니다.");
				return;
			}

			 int count = itemcount * Config.개당지급갯수 ;

			if (account.equalsIgnoreCase(account1)) {
				if(count11==itemcount) {
					con2 = DatabaseFactory.getInstance().getConnection();
					pstm2 = con2.prepareStatement(
							"SELECT login, no, count from royal2 where login Like '" + account1 + "'");
					rs2 = pstm2.executeQuery();
					while (rs2.next()) {

						account33 = rs2.getString("login");
						no1 = rs2.getInt("no");
						count34 = rs2.getInt("count");

					}
					if (no1 >= 1 && no1 <=Config.서버차수) {
						pc.sendPackets("\\f4후원 보상이 존재 합니다. 보상부터 수령해 주세요.");
						pc.sendPackets("\\f4.후원보상");
						return;	
					}
					if (account1.equalsIgnoreCase(account33) && no1 == Config.서버차수+1) {	
					item = itemcount+count34;
						AuctionSystemTable.getInstance().AuctionUpdate33(item);
						pc.getInventory().storeItem(Config.지급템번호, count);
						pc.sendPackets("1만원당 => "+Config.개당지급갯수+"개");
						pc.sendPackets("\\f4총지급 : " + count+"개");
						return;						
					}
				con1 = DatabaseFactory.getInstance().getConnection();
				String sqlstr = "INSERT INTO royal2 SET login=?, count=?, account=?, name=?, no=?";
				pstm1 = con1.prepareStatement(sqlstr);
				pstm1.setString(1, account);
				pstm1.setInt(2, itemcount);
				pstm1.setString(3, pc.getAccountName());
				pstm1.setString(4, pc.getName());
				pstm1.setInt(5, Config.서버차수);
				pstm1.execute();
				pc.getInventory().storeItem(Config.지급템번호, count);
				AuctionSystemTable.getInstance().deleteTopic4(account1);
//				AuctionSystemTable.getInstance().AuctionUpdate11(pc.getAccountName(), pc.getName());
				pc.sendPackets("1만원당 => "+Config.개당지급갯수+"개");
				pc.sendPackets("\\f4총지급 : " + count+"개");
				} else {
					pc.sendPackets("\\f4입금자명과 입금액을 정확하게 입력하세요");
				}
			} else {
				pc.sendPackets("\\f4입금자명과 입금액을 정확하게 입력하세요");
			}
			
		} catch (SQLException e) {
			pc.sendPackets("\\f4.후원지급 [입금자명] [금액](만단위) 라고 적어주십시요.");
			pc.sendPackets("예)  \\f4.후원지급 홍길동 10");
		} finally {
			SQLUtil.close(rs, pstm, con);
			SQLUtil.close(rs2, pstm2, con2);
		}
	}
	private void royal666(L1PcInstance pc, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String account1 = tok.nextToken();
			String account2 = tok.nextToken();
			if(account1 == null) {
				pc.sendPackets("\\f4.후원지급 [입금자명] [금액](만단위) 라고 적어주십시요.");
				pc.sendPackets("예)  \\f4.후원지급 홍길동 10");
				return;
			}
			royal66(pc, account1, account2);
		} catch (Exception e) {
			pc.sendPackets("\\f4.후원보상 [전차계정명] [전차입금자명]");
			pc.sendPackets("예)  \\f4.후원보상 abc1234 홍길동");
		}
	}
	private static void royal66(L1PcInstance pc, String account1, String account2) {
		Connection con = null;
		Connection con2 = null;
		PreparedStatement pstm = null;
		PreparedStatement pstm2 = null; 
		ResultSet rs = null;
		ResultSet rs2 = null;
	
		int count34 = 0;
		int no1 = 0;
		String account = null;
		String account33 = null;


		try {

			con2 = DatabaseFactory.getInstance().getConnection();
			pstm2 = con2.prepareStatement(
					"SELECT login, no, count, account from royal2 where login Like '" + account2 + "'");
			rs2 = pstm2.executeQuery();
			while (rs2.next()) {

				account33 = rs2.getString("login");
				no1 = rs2.getInt("no");
				count34 = rs2.getInt("count");
				account = rs2.getString("account");

			}
			if (account == null) {
				pc.sendPackets("저장된 후원보상 정보가 없습니다.");
				return;
			}

			int count = (int) ((count34 * Config.개당지급갯수) * (Config.보상배율/100)) ;

			
			if (account1.equalsIgnoreCase(account) && account2.equalsIgnoreCase(account33) && Config.서버차수 == no1 ) {			
				pc.getInventory().storeItem(Config.지급템번호, count);
				AuctionSystemTable.getInstance().deleteTopic5(account2);
				pc.sendPackets("1만원당 => "+Config.개당지급갯수+"개 / "+Config.보상배율+"%");
				pc.sendPackets("총지급 : " + count+"개");
			} else {
				pc.sendPackets("저장된 후원보상 정보가 없습니다.");
			}
			
		} catch (SQLException e) {
			pc.sendPackets(".후원보상 [전차계정명] [전차입금자명]");
			pc.sendPackets("예)  .후원보상 abc1234 홍길동");
		} finally {
			SQLUtil.close(rs, pstm, con);
			SQLUtil.close(rs2, pstm2, con2);
		}
	}

	private static void countR3(L1PcInstance pc, String count) {
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		int itemid = 0;
		int itemcount = 0;
		String account = null;
		int status = 0;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT  item_id ,count ,AccountName ,status from Auction where id Like '" + i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				itemid = rs.getInt("item_id");
				itemcount = rs.getInt("count");
				account = rs.getString("AccountName");
				status = rs.getInt("status");
			}
			if (status == 1) {
				pc.sendPackets("입금 대기상태 에서는 판매취소가 불가능 합니다.");
				return;
			}
			if (status == 2) {
				pc.sendPackets("판매가 완료된 상태입니다.");
				return;
			}
			if (pc.getAccountName().equalsIgnoreCase(account) || pc.isGm()) {
				pc.getInventory().storeItem(itemid, itemcount);
				AuctionSystemTable.getInstance().deleteTopic(i);
				pc.sendPackets("정상취소 되었습니다");
			} else {
				pc.sendPackets("등록하신 물품이 아닙니다");
			}
		} catch (SQLException e) {
			pc.sendPackets(".판매취소 (게시글번호) 라고 적어주십시요.");
			pc.sendPackets("예)  .판매취소 0035");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	private static void countR4(L1PcInstance pc, String count) {
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		String charname = null;
		int sellcount = 0;
		int status = 0;
		String buyerName = null;
		String buyerAccountName = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT  name, count, status, buyername, buyerAccountName from Auction where id Like '" + i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				charname = rs.getString("name");
				sellcount = rs.getInt("count");
				status = rs.getInt("status");
				buyerName = rs.getString("buyername");
				buyerAccountName = rs.getString("buyerAccountName");
			}

			if (status == 1) {
				if (pc.getName().equalsIgnoreCase(charname) || pc.isGm()) {
					AuctionSystemTable.getInstance().AuctionComplete(i);
					try {
						WriteComplete(buyerName, sellcount, charname);
						//WriteComplete("메티스", sellcount, charname);
						Warehouse.present(buyerAccountName, 600254, 0, sellcount); //원본은 1억 수표 400254
					} catch (Exception e) {
						e.printStackTrace();
					}
					pc.sendPackets("정상 판매 완료 되었습니다.");
				} else {
					pc.sendPackets("당신은 해당글의 판매자가 아닙니다.");
				}
			} else {
				pc.sendPackets("아직 구매 신청자가 없습니다.");
			}
		} catch (SQLException e) {
			pc.sendPackets(".판매완료(게시글번호)");
			pc.sendPackets(".판매완료 0017");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	public void 장비검색(L1PcInstance pc, String param) {
		Connection c = null;
		PreparedStatement p = null;
		PreparedStatement p1 = null;
		ResultSet r = null;
		ResultSet r1 = null;
		try {
			StringTokenizer st = new StringTokenizer(param);
			String charname = st.nextToken();
		//	String type = st.nextToken();
			
			c = DatabaseFactory.getInstance().getConnection();
			
			if (charname.equalsIgnoreCase("메티스")) {
				SC_SystemMessage.send(pc, "\\fY검색할 수 없는 운영자입니다.");
				
				return;
			}
			
			String itemname;
			int searchCount = 0;

				try {
					
					// 캐릭 오브젝트 ID 검색 1=objid 2=charname
					p = c.prepareStatement("SELECT objid, char_name FROM characters WHERE char_name = '" + charname + "'");
					r = p.executeQuery();
					while(r.next()){
						SC_SystemMessage.send(pc, "\\fW** 검색 캐릭: " + charname + " **");
						L1PcInstance target = L1World.getInstance().getPlayer(charname);
						
						if (target != null) target.saveInventory();						
						// 캐릭 아이템 검색 1-itemid 2-인챈 3-착용 4-수량 5-이름 6-축복 7-속성
						p1 = c.prepareStatement("SELECT item_id,enchantlvl,is_equipped,count,item_name,bless,attr_enchantlvl " +
								"FROM character_items WHERE char_id = '" + r.getInt(1) + "' ORDER BY 3 DESC,2 DESC, 1 ASC");
						r1 = p1.executeQuery();				
						while(r1.next()){
							L1ItemInstance item = ItemTable.getInstance().createItem(r1.getInt(1));							
							if (item.getItem().getType2() == 1 || item.getItem().getType2() == 2) { // 무기 방어구만 표시
								itemname = getInvenItem(r1.getInt(1),r1.getInt(2),r1.getInt(3),r1.getInt(4),r1.getString(5),r1.getInt(6),r1.getInt(7));
								SC_SystemMessage.send(pc, "\\fU"+ ++searchCount +". " + itemname);
								itemname = "";
							}
						}
						SC_SystemMessage.send(pc, "\\fW** 총 "+searchCount+"건의 아이템이 검색 되었습니다 **");
					}					
				} catch (Exception e) {
					SC_SystemMessage.send(pc, "\\fW** [" + charname + "] 캐릭 검색 오류 **");
				}						
			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".장비검색 [캐릭명]");
		} finally {
			SQLUtil.close(r1);SQLUtil.close(p1);
			SQLUtil.close(r);SQLUtil.close(p);
			SQLUtil.close(c);
		}
	}
	private String getInvenItem(int itemid, int enchant, int equip, int count, String itemname, int bless, int attr){
		StringBuilder name = new StringBuilder();
		// +9 축복받은 실프의 흑왕도 (착용)		
		// 인챈
		if (enchant > 0) {
			name.append("+" + enchant + " ");
		} else if (enchant == 0) {
			name.append("");
		} else if (enchant < 0) {
			name.append(String.valueOf(enchant) + " ");
		}
		// 축복
		switch (bless) {
		case 0:name.append("축복받은 ");break;
		case 1:name.append("");break;		
		case 2:name.append("저주받은 ");break;
		default: break;
		}
		// 속성
		switch(attr){
		case 1:
			name.append("화령:1단 ");
			break;
		case 2:
			name.append("화령:2단 ");
			break;
		case 3:
			name.append("화령:3단 ");
			break;
		case 4:
			name.append("화령:4단 ");
			break;
		case 5:
			name.append("화령:5단 ");
			break;
		case 6:
			name.append("수령:1단 ");
			break;
		case 7:
			name.append("수령:2단 ");
			break;
		case 8:
			name.append("수령:3단 ");
			break;
		case 9:
			name.append("수령:4단 ");
			break;
		case 10:
			name.append("수령:5단 ");
			break;
		case 11:
			name.append("풍령:1단 ");
			break;
		case 12:
			name.append("풍령:2단 ");
			break;
		case 13:
			name.append("풍령:3단 ");
			break;
		case 14:
			name.append("풍령:4단 ");
			break;
		case 15:
			name.append("풍령:5단 ");
			break;
		case 16:
			name.append("지령:1단 ");
			break;
		case 17:
			name.append("지령:2단 ");
			break;
		case 18:
			name.append("지령:3단 ");
			break;
		case 19:
			name.append("지령:4단 ");
			break;
		case 20:
			name.append("지령:5단 ");
			break;
		default:
			break;
		}
		// 이름
		name.append(itemname + " ");
		// 착용여부
		if (equip == 1){
			name.append("(착용)");
		}
		switch (bless) {
		case 129:name.append("[봉인]");break;
		default: break;
		}
		// 카운트
		if (count > 1){
			name.append("(" + count + ")");
		}
		return name.toString();
	}

	
	private static void countR5(L1PcInstance pc, String count) {
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		int status = 0;
		String charname = null;
		String buyername = null;
		String buyeraccount = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT  name, status, buyername, buyerAccountName from Auction where id Like '" + i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				charname = rs.getString("name");
				status = rs.getInt("status");
				buyeraccount = rs.getString("buyerAccountName");
				buyername = rs.getString("buyername");
			}
			if (status == 0) {
				pc.sendPackets("구매 취소가 불가능한 상태입니다.");
				return;
			}
			if (status == 2) {
				pc.sendPackets("판매가 완료된 상태입니다.");
				return;
			}
			if (pc.getAccountName().equalsIgnoreCase(buyeraccount) || pc.isGm()) {
				AuctionSystemTable.getInstance().AuctionUpdate(i, "", "", "입금대기", 0);
				WriteCancle(buyername, charname);
				//WriteCancle("메티스", charname);
				pc.sendPackets("정상취소 되었습니다");
			} else {
				pc.sendPackets("등록하신 물품이 아닙니다");
			}
		} catch (SQLException e) {
			pc.sendPackets(".구매취소 (게시글번호) 라고 적어주십시요.");
			pc.sendPackets("예)  .구매취소 0035");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	// TODO 중개 거래 게시판

	/*
	 * private static void do_lfc(L1PcInstance pc, String param){ StringTokenizer
	 * token = new StringTokenizer(param); String name = token.nextToken();
	 * L1PcInstance target = L1World.getInstance().findpc(name); if(target == null){
	 * pc.sendPackets(String.format("%s님을 찾을 수 없습니다.", name)); } L1BoardPost bp =
	 * L1BoardPost.createLfc(name, "-", String.format("3 %s", pc.getName()));
	 * MJLFCCreator.registLfc(pc, 3);
	 * pc.sendPackets(String.format("%s님에게 결투를 신청했습니다.", name));
	 * pc.sendPackets(MJSurveySystemLoader.getInstance().registerSurvey(String.
	 * format("%s님이 결투를 신청했습니다.", pc.getName()), bp.getId() + 1000,
	 * MJSurveyFactory.createLFCSurvey(), 30 * 1000)); }
	 */

	public static void monsterHpBar(L1PcInstance pc, String param) {
		if (param.equals("켬") || param.equals("온")) {
			pc.setMonsterHpBar(true);
			SC_SystemMessage.send(pc, "몬스터 체력바 보기가 [활성화] 되었습니다.");
		} else if (param.equals("끔") || param.equals("오프")) {
			pc.setMonsterHpBar(false);
			SC_SystemMessage.send(pc, "몬스터 체력바 보기가 [비활성화] 되었습니다.");
		} else {
			SC_SystemMessage.send(pc, ".체력바 [켬/온/끔/오프] 라고 입력해 주세요.");
		}
	}
}