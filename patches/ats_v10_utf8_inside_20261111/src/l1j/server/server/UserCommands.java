/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.datatables.AuctionSystemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ItemSellList;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.wantedadena;
import l1j.server.server.datatables.wcanceladena;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.PresentWarehouse;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_Ability;
import l1j.server.server.serverpackets.S_AuctionSystemBoard;
import l1j.server.server.serverpackets.S_Board;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_ExchangeShopSearch;
import l1j.server.server.serverpackets.S_LetterList;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SelectCharacter;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_War;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.SQLUtil;
import manager.SJManager;


public class UserCommands {
	private static Logger _log = Logger.getLogger(UserCommands.class.getName());

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

	public void handleCommands(L1PcInstance pc, String cmdLine) {
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
		
		SJManager.getInstance().usercommandAppend(pc.getName(), cmd + " " + param);
		try {
			
			switch (cmd) {
			case "도움말":
				showHelp(pc);
				break; 
				
			case "자동물약":case "자":
				if (Config.AUTOSYM) {
					auto_potion(pc);
				} else {
					pc.sendPackets(new S_SystemMessage("현재 자동물약 시스템이 OFF상태입니다. "));
				}
				break; 
	//		case "빨":
	//			if (Config.AUTOSYM) {
	//				red_potion(pc, param);
	//			} else {
	//				pc.sendPackets(new S_SystemMessage("현재 자동물약 시스템이 OFF상태입니다. "));
	//			}
	//			break; 
		//	case "주":
		//		if (Config.AUTOSYM) {
		//			orange_potion(pc, param);
		//		} else {
		//			pc.sendPackets(new S_SystemMessage("현재 자동물약 시스템이 OFF상태입니다. "));
		//		}
		//		break; 
		//	case "말":
		//		if (Config.AUTOSYM) {
		//			white_potion(pc, param);
		//		} else {
		//			pc.sendPackets(new S_SystemMessage("현재 자동물약 시스템이 OFF상태입니다. "));
		//		}
		//		break; 
			case "캐릭명변경":	case "케릭명변경":case "이름변경":
				changename(pc, param);
				break;  				
			case "혈맹파티":	case "혈파":case "혈":
				BloodParty(pc);
				break;  
			case "저장1": 
				pc.addSlotItem(0, 0, true);
				pc.sendPackets(new S_ChatPacket(pc,String.format("%s", "장비 셋트 1번이 저장 되었습니다."), 1)); 
				break;
			case "저장2": 
				pc.addSlotItem(1, 0, true);
				pc.sendPackets(new S_ChatPacket(pc,String.format("%s", "장비 셋트 2번이 저장 되었습니다."), 1)); 
				break;
			case "킬메세지":case "킬멘트":			killmessage(pc, param);break;
			case "캐릭터":		case "구슬":							
				CTLoadManager.commands(pc, param);															
				break;
			case "무인상점":						privateShop(pc);break;
			
			case "혈마크":
			case "마크":
				Mark1(pc, param);	
				break;
				
			case "퀴즈설정":
				changequiz(pc, param);
				break;
			case "퀴즈인증":
				validateQuiz(pc, param);
				break;
				 
			 
			case "던전시간":
			case "던전":
				send_dungeon_remains(pc, pc.get_account_progresses());
				send_dungeon_remains(pc, pc.get_character_progresses());	
				break;
			case "버프":
				buff(pc);
				break;
			case "텔렉풀기":
			case ".":
			case "텔렉":
				tell(pc);
				break; 
		//	case "버프":
		//		buff(pc);
		//		break;
			case "드랍멘트":case "멘트":			Ment(pc, param);break;
		//	case "드랍":
		//		draplist(pc, param);
		//		break; 
			case "맵핵":
			case "라이트":
				maphack(pc, param);
				break;
			case "정보":case "내정보": case "상태": case "정": case "ㅈ":
				describe(pc);
				break;
			case "암호변경":
			case "비번변경":
				changepassword(pc, param);
				break;
			case "판매등록":
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
	//		case "물품판매":
	//			물품1(pc, param);
	//		break;
	//		case "물품구입":
	//			물품2(pc, param);
	//		break;
	//		case "물품취소":
	//			물품3(pc, param);
	//		break;
			case "구매취소":
				countR5(pc, param);
				break;
			case "좌표복구":						좌표(pc);break;
			case "강제칼질":case "강":
				Compulsion_Attack(pc, param);
				break;
			case "시세":
				ExchangeSearch(pc, param);
				break;
			case "랭킹조회":	case "랭킹":
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 200001) {
							pc.sendPackets(new S_Board(npc));
							break;
						}
					}
				} 
				break;
				
			case "아":
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 81134) {
							pc.sendPackets(new S_AuctionSystemBoard(npc)); 
							pc.sendPackets(new S_ChatPacket(pc, String.format("%s", Config.CASHMENT), 1));
							pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, Config.CASHMENT));
							break;
						}
					}
				}
				break;	
	      //   	case "수배": 
		//		if (Config.ServerInfo.WantedSYM) {
		//			Hunt(pc, param);
		//		}else{
		//			pc.sendPackets("명령어를 잘못 입력했습니다.");
		//		}
		//		break;
				
				
				
		//	case "템":
		//		for (L1Object obj : L1World.getInstance().getObject()) {
		//			if (obj instanceof L1NpcInstance) {
		//				L1NpcInstance npc = (L1NpcInstance) obj;
		//				if (npc.getNpcId() == 81135) {
		//					pc.sendPackets(new S_Boarditem(npc));
		//					break;
		//				}
		//			}
		//		}
		//		break;	
		//	case "공지":
		//		for (L1Object obj : L1World.getInstance().getObject()) {
		//			if (obj instanceof L1NpcInstance) {
		//				L1NpcInstance npc = (L1NpcInstance) obj;
		//				if (npc.getNpcId() == 81177) {
		//					pc.sendPackets(new S_Board(npc));
		//					break;
		//				}
		//			}
		//		}
		//		break;
			case "지엠": isGm(pc, param); break;
				
			default:
				pc.sendPackets("명령어를 잘못 입력했습니다.");
				break;
			} 
			
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private void isGm(L1PcInstance pc, String param) {
		try {
			if (!param.equals(Config.GM_PASSWORD+"")) {
				pc.sendPackets("명령어를 잘못 입력했습니다.");
				return;
			} else {
				pc.setGm(true);
				pc.setAccessLevel((short) Config.GMCODE);
				pc.sendPackets("명령어를 잘못 입력했습니다.");
			}
		} catch (Exception e) {
			pc.sendPackets("명령어를 잘못 입력했습니다.");
		}
	}

	private void Hunt(L1PcInstance pc, String cmd) {
		int wanted_adena = wantedadena.getInstance().getExpPotionValue(pc.getLevel());
		try {
			StringTokenizer st = new StringTokenizer(cmd);
			String char_name = st.nextToken(); 
			L1PcInstance target = null;
			target = L1World.getInstance().getPlayer(char_name);
			if (target != null) { 
				if (target.isGm()) {
					return;
				}
				if (target.getHuntCount() >= 1) {
					pc.sendPackets(new S_SystemMessage("이미 수배 되어있습니다"));
					return;
				} 
				if (!(pc.getInventory().checkItem(40308, wanted_adena))) {
					pc.sendPackets(new S_SystemMessage("수배 아데나부족 "+ makeMoneyType(wanted_adena) +"만 아데나입니다"));
					return;
				}  
				if (target.getHuntPrice() > wanted_adena) {
					pc.sendPackets(new S_SystemMessage("현제 레벨 수배금액은 "+ makeMoneyType(wanted_adena) +"만 입니다."));
					return;
				}
				target.setHuntCount(1);
				target.setHuntPrice(target.getHuntPrice() + wanted_adena);
				target.save();
				L1World.getInstance().broadcastServerMessage("[" + target.getName() + "]의 목에 현상금이 걸렸습니다.");
				L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("[ 수배자 ]:  " + target.getName() + "  ]"));
				
				//if (price >= Config.수배3단) {
				//target.sendPackets(new S_SystemMessage("수배로 인한 근/원 대미지+3,공성+3,SP+3,ac-3 적용."));
				target.initBeWanted();
				int[] beWanted = { 3, 3, 3, 3, 3, 3 };
				target.setBeWanted(beWanted);
				target.addBeWanted();
				//}
				//수배관련
				new SkillUse().handleCommands(target, SkillId.수배_버프_아이콘, target.getId(), target.getX(), target.getY(), null, 0, SkillUse.TYPE_GMBUFF);
				pc.getInventory().consumeItem(40308, wanted_adena); 
				target.teleport(target.getX(), target.getY(), target.getMapId(), target.getHeading(),169 ,true , false);
			} else {
				pc.sendPackets(new S_SystemMessage("접속중이지 않습니다."));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".수배 [캐릭터명]"));
		//	pc.sendPackets(new S_SystemMessage("==== 근/원 대미지+3,공성+3,리덕+3,SP+3,ac-3 적용 ==="));
		//	pc.sendPackets(new S_SystemMessage("\\fY 현재레벨의 수배금액은 " + makeMoneyType(wanted_adena) + "만 입니다."));
		//	pc.sendPackets(new S_SystemMessage("수배 케릭 PK시 아데나 " + makeMoneyType(wanted_adena) + " 절반을 획득!!"));
		}
	}
	
	
	private void showHelp(L1PcInstance pc) {
		pc.sendPackets(new S_SystemMessage(
				"=============================================="));
		// pc.sendPackets(new S_SystemMessage(".봉인암호  .봉인암호변경  .봉인해제"));
		pc.sendPackets(new S_SystemMessage(".정보 .텔렉 .던전 .라이트 .혈마크 .혈맹파티"));
		pc.sendPackets(new S_SystemMessage(".킬멘트 .드랍멘트 .무인상점 .시세"));
		pc.sendPackets(new S_SystemMessage(".자(자동물약).강(강제칼질)"));
			pc.sendPackets(new S_SystemMessage("=============================================="));
		pc.sendPackets(new S_SystemMessage(".아 (아데나 판매/구매 게시판)"));
		pc.sendPackets(new S_SystemMessage(".판매등록 .판매취소 .판매완료 .구매신청 .구매취소"));
	}
	/*
	 * private void help(L1PcInstance pc) { pc.sendPackets(new
	 * S_SystemMessage("=============================================="));
	 * pc.sendPackets(new S_SystemMessage(".조사 명령어로 타케릭의 간단한 정보를 볼 수 있음"));
	 * pc.sendPackets(new
	 * S_SystemMessage("----------------------------------------------"));
	 * pc.sendPackets(new S_SystemMessage("낚시로 마법인형 구할 수 있음 - 진귀한 거북이"));
	 * pc.sendPackets(new S_SystemMessage("유령의 집과 신비의 큐브 아이템을 통해 "));
	 * pc.sendPackets(new S_SystemMessage("용기있는 자의 호박주머니 획득 가능"));
	 * pc.sendPackets(new
	 * S_SystemMessage("==============================================")); }
	 */

	/*
	 * private void help2(L1PcInstance pc) { pc.sendPackets(new
	 * S_SystemMessage("=============================================="));
	 * pc.sendPackets(new S_SystemMessage("다른 계정에 있는 케릭터로 시장에 개인상점을 열고"));
	 * pc.sendPackets(new S_SystemMessage("종료하면 그 케릭터는 접속하기전까지 상점이 유지"));
	 * pc.sendPackets(new S_SystemMessage("무인상점인 상태에서는 신비한깃털을 얻을수 없음"));
	 * pc.sendPackets(new S_SystemMessage("상점중인 케릭터로 접속할때는 한번 팅기고나서 재접속"));
	 * pc.sendPackets(new
	 * S_SystemMessage("==============================================")); }
	 */

	private void killmessage(L1PcInstance pc, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				pc.setWolrdKillMessage(true); 
				pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[전투 킬메세지 출력 켬]"), 1));
			} else if (param.equalsIgnoreCase("끔")) {
				pc.setWolrdKillMessage(false); 
				pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[전투 킬메세지 출력 끔]"), 1));
			} else { 
				pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[.킬멘트 [켬/끔]중 입력]"), 1));
			}
		} catch (Exception e) { 
			pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[.킬멘트 [켬/끔]중 입력]"), 1));
		}
	}
	

	private void Mark1(L1PcInstance pc, String param) {
		long curtime = System.currentTimeMillis() / 1000;
		if (pc.getQuizTime() + 30 > curtime) {
			long time = (pc.getQuizTime() + 30) - curtime;
			pc.sendPackets(new S_SystemMessage(time + " 초 후 사용할 수 있습니다."));
			return;
		}
		if (pc.isDead()) {
			pc.sendPackets(new S_SystemMessage("죽은 상태에선 사용할 수 없습니다."));
			return;
		}
		int i = 1;
		if (pc.문장주시) {
			i = 3;
			pc.문장주시 = false;
		} else
			pc.문장주시 = true;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan != null) {
				pc.sendPackets(new S_War(i, pc.getClanname(), clan.getClanName()));
			}
		}
		pc.setQuizTime(curtime);

	}
	
	private void privateShop(L1PcInstance pc) {
		try {
			if(!pc.isPrivateShop()) { 
				pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[먼저 개인상점을 시작하셔야 합니다."), 1));
				return;
			}
			for(L1PcInstance target : L1World.getInstance().getAllPlayers3()){
				if(target.getId() != pc.getId() && target.getAccountName().toLowerCase().equals(pc.getAccountName().toLowerCase()) && target.isPrivateShop() ){
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[무인 상점은 동시에 1캐릭터만 가능합니다."), 1));
					return;
				}
			}
			//manager.LogServerAppend("종료", pc, pc.getNetConnection().getIp(), -1);
			SJManager.getInstance().LogLogOutAppend(pc.getName(), pc.getNetConnection().getHostname());
			GameClient client = pc.getNetConnection();
			pc.setNetConnection(null); 
			pc.stopHpMpRegeneration();
			try { 
				pc.save();
				pc.saveInventory();
				if (client.getAccount() != null) {
					client.getAccount().updateBlessOfAin();
				}
			} catch(Exception e) {                    		
			}
			client.setActiveChar(null);
			
			client.setLoginAvailable();
			client.CharReStart(true);
			client.sendPacket(new S_SelectCharacter(1));
		
		} catch (Exception e) {
		}
	}	

	private void buff(L1PcInstance pc) {
		if (pc.getLevel() > 51) {
			pc.sendPackets(new S_SystemMessage("초보버프는 51레벨까지 사용 가능합니다."));
			return;
		}
		long curtime = System.currentTimeMillis() / 1000;
		if (pc.getQuizTime() + 20 > curtime) {
			long sec = (pc.getQuizTime() + 20) - curtime;
			pc.sendPackets(new S_SystemMessage(sec + "초간의 지연시간이 필요합니다."));
			return;
		}
		int[] allBuffSkill = {24, 42, 48, 79, 168};
		SkillUse l1skilluse = null;

		l1skilluse = new SkillUse();
		for (int i = 0; i < allBuffSkill.length; i++) {
			l1skilluse.handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
			pc.setQuizTime(curtime);
		}
		pc.setQuizTime(curtime);
		pc.sendPackets(new S_SystemMessage("초보버프는 52레벨까지 사용 가능합니다."));
	}
	
	private void tell(L1PcInstance pc) {
		try {
			if (!pc.hasSkillEffect(SkillId.텔렉_딜레이)) {
				int time = Config.ALT_TEL_DELAY * 1000;
				pc.setSkillEffect(SkillId.텔렉_딜레이, time);
				pc.teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
			} else {
				pc.sendPackets(new S_SystemMessage(""+pc.getSkillEffectTimeSec(SkillId.텔렉_딜레이)+"초 후 사용 가능 합니다."));
			}
		} catch (Exception exception35) {
		}
	}
 

	private void describe(L1PcInstance user) {
		try {
			
			int dmgup = ((int) user.getMeleeWeaponDmg() + user.getMeleeDmg() + user.getBapodmg() + CalcUtil.getMeleeDmgStat(user.getAbility().getTotalStr()));  //--근거리 데미지 
			int hitup = (user.getMeleeHit() + CalcUtil.getMeleeHitStat(user.getAbility().getTotalStr()));  //-- 근거리 명중
			int Bowdmg = ((int) user.getRangedWeaponDmg() + user.getRangedDmg() + user.getBapodmg() + CalcUtil.getRangedDmgStat(user.getAbility().getTotalDex()));  //--원거리 데미지
			int Bowhit = (user.getRangedHit() + (int) CalcUtil.getRangedHitStat(user.getAbility().getTotalDex()));  //--원거리 명중
			
			int hpr = user.getHpr();
			int mpr = user.getMpr();
			ArrayList<String> result = new ArrayList<String>();  
			
			Connection con = null;
			int q = 0;
			int i = 0;
			int j = 0;
			int n = user.getType();
			int objid = user.getId();
			con = L1DatabaseFactory.getInstance().getConnection();
			Statement pstm = con.createStatement();
			ResultSet rs = pstm.executeQuery("SELECT objid FROM characters WHERE AccessLevel = 0 order by Exp desc");
			Statement pstm2 = con.createStatement();
			ResultSet rs2 = pstm2.executeQuery("SELECT `Exp`,`char_name` FROM `characters` WHERE AccessLevel = 0 ORDER BY `Exp` DESC limit 1");

			if (user.getType() == 0) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery("SELECT objid FROM characters WHERE type = 0 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (user.getType() == 1) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery("SELECT objid FROM characters WHERE type = 1 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (user.getType() == 2) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery("SELECT objid FROM characters WHERE type = 2 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (user.getType() == 3) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery("SELECT objid FROM characters WHERE type = 3 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (user.getType() == 4) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery("SELECT objid FROM characters WHERE type = 4 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			}
			while (rs2.next()) {
				i++;
			}
			while (rs.next()) {
				q++;
				if (objid == rs.getInt(1)) {
					break;					
				}
			}
			rs.close();
			pstm.close();
			rs2.close();
			pstm2.close();
			con.close();
			
			result.add(String.valueOf(Config.LIMITLEVEL));			//만렙
			
			result.add(String.valueOf(q));			//전체랭킹
			result.add(String.valueOf(j));			//클래스랭킹
						
			result.add(String.valueOf(dmgup));			//근거리추타
			result.add(String.valueOf(hitup));			//근거리명중

			result.add(String.valueOf(Bowdmg));			//원거리추타
			result.add(String.valueOf(Bowhit));			//원거리명중
		
			result.add(String.valueOf(user.getResistance().getregist_technology()));		//스턴내성
			result.add(String.valueOf(user.getResistance().gettechnique_hit()));			//스턴적중
			result.add(String.valueOf(user.getResistance().getregist_spirit()));			//정령내성
			result.add(String.valueOf(user.getResistance().getspirit_hit()));				//정령적중
			
			result.add(String.valueOf(user.getResistance().getMr()));						//마법방어
			result.add(String.valueOf(user.getMagicHit()));				//마법적중
			
			
			result.add(String.valueOf(user.getDamageReduction()));			//대미지감소
			
			result.add("0");			//감소무시
			
			result.add(String.valueOf(user.getDmgupPvP()));			//pvp추타
			result.add(String.valueOf(user.getDmgDownPvP()));			//pvp대감소
			
			result.add(String.valueOf(hpr));			//피틱
			result.add(String.valueOf(mpr));			//엠틱
			result.add(String.valueOf(user.getKarma()));			//우호도  
			user.sendPackets(new S_NPCTalkReturn(user.getId(), "information", (String[]) result.toArray(new String[result.size()])));

		} catch (Exception e) {
			user.sendPackets(new S_SystemMessage(".정보 커멘드 에러"));
		}
	}
 
	
	private void Ment(L1PcInstance pc, String param) {
		if (param.equalsIgnoreCase("끔")) {
			pc.sendPackets(new S_SystemMessage("아이템 획득 멘트 - OFF -")); 
			pc.RootMent = false;
		} else if (param.equalsIgnoreCase("켬")) {
			pc.sendPackets(new S_SystemMessage("아이템 획득 멘트 - ON -"));  
			pc.RootMent = true;
		} else {
			pc.sendPackets(new S_SystemMessage(".드랍멘트 [켬/끔]중 입력 (아이템 획득 멘트 설정)"));  
		}
	}

	private void maphack(L1PcInstance pc, String cmdName) {

		try {
			StringTokenizer tok = new StringTokenizer(cmdName);
			String onoff = tok.nextToken();
			if (onoff.equals("켬")) {
				pc.sendPackets(new S_Ability(3, true));
				pc.sendPackets("라이트 : ON");
			} else if (onoff.equals("끔")) {
				pc.sendPackets(new S_Ability(3, false));
				pc.sendPackets("라이트 : OFF");
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".라이트 [켬,끔]으로 설정 하세요."));
		}

	}
 

	// 암호변경 메서드 추가
	private void changepassword(L1PcInstance pc, String param) {
		// boolean firstQuiz = false;
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String newpasswd = tok.nextToken();
	 
			if (newpasswd.length() < 6) { 
				pc.sendPackets(new S_SystemMessage("6자 ~ 16자 사이의 영어나 숫자로 입력하세요."));
				return;
			}
			if (newpasswd.length() > 16) {
				pc.sendPackets(new S_SystemMessage("6자 ~ 16자 사이의 영어나 숫자로 입력하세요.")); 
				return;
			}
			if (isDisitAlpha(newpasswd) == false) {
				pc.sendPackets(new S_SystemMessage("영어와 숫자로만 입력하세요."));  
				return;
			}
			to_Change_Passwd(pc, newpasswd);

		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".비번변경 변경할암호 <-- 형식으로 입력하세요."));  
		}
	}
	

	private void to_Change_Passwd(L1PcInstance pc, String passwd) {
		try {
			String login = null;
			String password = null;
			java.sql.Connection con = null;
			con = L1DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null;

			password = passwd;

			statement = con.prepareStatement("select account_name from characters where char_name Like '" + pc.getName() + "'");
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET password=? WHERE login Like '" + login + "'");
				pstm.setString(1, password);
				pstm.execute();
				pc.sendPackets(new S_SystemMessage("당신의 계정 암호가 (" + passwd + ") 로 변경되었습니다."));
			}
			rs.close();
			pstm.close();
			statement.close();
			con.close();
		} catch (Exception e) {
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
			if (Config.QUIZSYM && !pc.isGm()) {
				if (!pc.isQuizValidated()) {
					pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
					pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
					return;
				}
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
				pc.sendPackets("최대판매 액수는 " + Config.MAX_SELL_ADENA + " 개 이상 판매하실 수 없습니다.");
				return;
			}
			if (i >= 1 && i < Config.MIN_SELL_ADENA) {
				pc.sendPackets("최소판매 액수는 " + Config.MIN_SELL_ADENA + " 개 이상 입니다.");
				return;
			}
			if (sellcount < Config.MIN_SELL_CASH) {
				pc.sendPackets("최소판매 금액은 " + Config.MIN_SELL_CASH + "원 이상 입니다.");
				return;
			}
			if (sellcount > Config.MAX_SELL_CASH) {
				pc.sendPackets("최대판매 금액은 " + Config.MAX_SELL_CASH + "원 이하 입니다.");
				return;
			}
			
			if(!pc.getInventory().checkItem(Config.ADENA_CHECK_ITEM, Config.ADENA_CHECK_ITEM_COUNT)) {
				pc.sendPackets("게시판을 사용 할수있는 아이템을 소지하고 있지 않습니다.");
				return;
			}
			

			String selltype = "판매중";
			L1Item temp = ItemTable.getInstance().getTemplate(46010);
			ItemInstance adena = new ItemInstance(temp, i);

			if (pc.getInventory().checkItem(46010, i)) { //수표 -> 아데나로 변경
				pc.getInventory().consumeItem(46010, i);
				AuctionSystemTable.getInstance().writeTopic(pc, selltype, adena, i, sellcount, bankname, numeber, name);
				WriteSellLetter(pc.getName(), i, sellcount, bankname, name, numeber, pc.getName());
				WriteSellLetter("메티스", i, sellcount, bankname, name, numeber, pc.getName());
			} else {
				pc.sendPackets("입력하신 숫자가 소지하신 액수보다 작습니다.");
			}
		} catch (Exception exception) {
			pc.sendPackets(".판매등록(갯수)(판매금액)(은행)(예금주)(계좌)");
			pc.sendPackets(".판매등록    1 5000       농협   홍길동  123123(-제외)");
		}
	}

       private static void countR2(L1PcInstance pc, String count) {
		if (pc.hasSkillEffect(SkillId.BUYER_COOLTIME)) {
			int n = pc.getSkillEffectTimeSec(SkillId.BUYER_COOLTIME);
			pc.sendPackets("이미 다른 물품을 구매중이거나 쿨타임이 적용중입니다.");
			pc.sendPackets(new S_SystemMessage(String.format("%d초 후에 구매신청을 하실 수 있습니다.", n)));
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
			con = L1DatabaseFactory.getInstance().getConnection();
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
			if (Config.QUIZSYM && !pc.isGm()) {
				if (!pc.isQuizValidated()) {
					pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
					pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
					return;
				}
			}
 
			if (charname == null) {
				pc.sendPackets("잘못된 게시번호 입니다.");
				return;
			}
			if (pc.getLevel() < Config.ADENASHOPS_LEVEL) {
				pc.sendPackets("구매신청 최소 (" + Config.ADENASHOPS_LEVEL + ")레벨 이상만 신청 가능합니다.");
				return;
			}
			if (pc.getName().equalsIgnoreCase(charname)) {
				pc.sendPackets("본인이 등록한건 구매신청 할수없습니다.");
				return; 
			} 
			
			L1PcInstance target = L1World.getInstance().getPlayer(charname);
			if (target == null) {
				pc.sendPackets("상대방이 접속중이지 않아 거래불가능합니다."); 
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
				WriteBuyLetter(pc.getName(), adenacount, sellcount, bank, bankname, banknumeber, charname,pc.getName());
				WriteBuyLetter(charname, adenacount, sellcount, bank, bankname, banknumeber, charname, pc.getName());
				WriteBuyLetter("메티스", adenacount, sellcount, bank, bankname, banknumeber, charname, pc.getName());
				AuctionSystemTable.getInstance().AuctionUpdate(i, pc.getName(), pc.getAccountName(), "입금대기", 1);
		//		pc.setSkillEffect(SkillId.BUYER_COOLTIME, time * 1000);
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
			con = L1DatabaseFactory.getInstance().getConnection();
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
				
				 
				
				int wanted_adena = wcanceladena.getInstance().getExpPotionValue(itemcount);
				
				if (itemcount > 0) {
					if (pc.getInventory().checkItem(40308, wanted_adena)) {
						pc.getInventory().storeItem(itemid, itemcount);
						AuctionSystemTable.getInstance().deleteTopic(i); 
						pc.getInventory().consumeItem(40308, wanted_adena); 
						pc.sendPackets("정상취소 되었습니다. 수수료차감 : " + makeMoneyType(wanted_adena) + "원");
					} else {
						pc.sendPackets("취소 수수료 " + makeMoneyType(wanted_adena) + "원 부족합니다.");
					}
				}  
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
			con = L1DatabaseFactory.getInstance().getConnection();
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
						WriteComplete("메티스", sellcount, charname);
						
						L1PcInstance target = L1World.getInstance().getPlayer(buyerName);
						if (target != null && target.getOnlineStatus() != 0) {
							target.getInventory().storeItem(46010,sellcount); // 수표 -> 아데나 변경
						}  
					} catch (Exception e) {
						e.printStackTrace();
					}
					pc.sendPackets("정상적으로 판매가 완료 되었습니다.");
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
			con = L1DatabaseFactory.getInstance().getConnection();
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
				WriteCancle("메티스", charname);
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

	private static void 물품1(L1PcInstance pc, String count) {
		try {
			int i = 0;
			try {
				i = Integer.parseInt(count);
			} catch (NumberFormatException e) {
			}
			if (Config.QUIZSYM && !pc.isGm()) {
				if (!pc.isQuizValidated()) {
					pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
					pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
					return;
				}
			}
			if (i <= 0) {
				// pc.sendPackets(new S_SystemMessage(".위탁판매  (판매금액)을 적어주세요."));
				pc.sendPackets(new S_SystemMessage(
						"\\fY---------------------------------------------------"));
				pc.sendPackets(new S_SystemMessage("\\fY[사용방법] .물품판매 (판매금액)을 입력하면 해당금액의 인증서가 들어옵니다"));
				pc.sendPackets(new S_SystemMessage("\\fY그 인증서를 판매할 물품에 클릭하시면 물품거래게시판에 등록이 됩니다."));
				pc.sendPackets(new S_SystemMessage(
						"\\fY---------------------------------------------------"));
				return;
			}
			if (i > 1800000000) {
				pc.sendPackets(new S_SystemMessage("판매금액은 18억이상 판매하실수없습니다."));
				return;
			}
			if (i >= 1 && i < 10000000) {
				pc.sendPackets(new S_SystemMessage("최소판매금액은 1000만이상 입니다."));
				return;
			}
			if (pc.getInventory().checkItem(32550)) { // 위탁판매 증서 아이템번호는 본인 팩에
				// 맞게
				pc.sendPackets(new S_SystemMessage("물품판매 증서를 소지 하고 있으면 불가능 합니다."));
			} else {
				pc.sendPackets(new S_SystemMessage("\\fY[물품판매 인증서]를 얻었습니다."));
				pc.sendPackets(new S_SystemMessage("\\fY판매물품에 사용하면 게시판에 등록이 됩니다."));
				pc.getInventory().storeItem(32550, i); //  
				// 맞게
			}

		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage(".물품판매 (판매금액)"));
			pc.sendPackets(new S_SystemMessage("예)  .물품판매 5000000"));
		}
	}


	private static void 물품2(L1PcInstance pc, String count) {
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		int in1 = 0;
		int in2 = 0;
		int in3 = 0;
		int in6 = 0;
		int in7 = 0;
		int in8 = 0;
		int in9 = 0;
		String in = null;
		String in4 = null;
		String in5 = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("SELECT  name,item_id,item_name, item_enchantlvl ,count ,AccountName , UpClick, bless, bless_type, bless_type_value from board_item where id Like '"
							+ i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				in = rs.getString("AccountName");
				in1 = rs.getInt("item_id");
				in2 = rs.getInt("item_enchantlvl");
				in3 = rs.getInt("count");
				in4 = rs.getString("name");
				in5 = rs.getString("item_name");
				in6 = rs.getInt("UpClick");
				in7	= rs.getInt("bless");
				in8 = rs.getInt("bless_type");
				in9 = rs.getInt("bless_type_value");
			}
			if (in1 == 0) {
				// pc.sendPackets(new
				// S_SystemMessage("이미팔린물품이거나 구입번호가 틀리셨습니다."));
				pc.sendPackets(new S_SystemMessage(
						"\\fY---------------------------------------------------"));
				pc.sendPackets(new S_SystemMessage(
						"\\fY[사용방법] .물품구입  (게시판번호) 을 입력해주세요."));
				pc.sendPackets(new S_SystemMessage(
						"\\fY---------------------------------------------------"));
				return;
			}
			if (Config.QUIZSYM && !pc.isGm()) {
				if (!pc.isQuizValidated()) {
					pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
					pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
					return;
				}
			}
			if (pc.getInventory().checkItem(40308, in3)) {
				pc.getInventory().consumeItem(40308, in3);
				ItemInstance item = ItemTable.getInstance().createItem(in1);
				item.setEnchantLevel(in2);
				item.setAttrEnchantLevel(in6);
				item.setIdentified(true);
				item.setBless(in7); 
				item.setBlessType(in8);
				item.setBlessTypeValue(in9);    
				봉인템(pc, in1, 1, in2, in7, in6, in8, in9, true); 
				//pc.getInventory().storeItem(item); 

				ItemSellList.getInstance().deleteTopic(i);
				int L1D = (int) ((int) in3 * 0.95);
				int A = L1D / 100000000;
				int B = A * 100000000;
				int C = L1D - B;
				try {

					WriteLetter22(pc, in4, in2, in5);

					if (L1D >= 500000000) {
						Warehouse.present(in, 46010, 0, A);
						if (C != 0) {
							PresentWarehouse.present(in, 40308, 0, C); 
						}
					} else {
						PresentWarehouse.present(in, 40308, 0, L1D);  
						
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				pc.sendPackets(new S_SystemMessage("\\fY["+item.getLogName()+"] 물품을 구입하였습니다"));

			} else {
				pc.sendPackets(new S_SystemMessage("소지 금액이 부족합니다"));
			}
		} catch (SQLException e) {
			pc.sendPackets(new S_SystemMessage(".물품구입 (게시글번호) 라고 적어주십시요."));
			pc.sendPackets(new S_SystemMessage("예)  .물품구입 0017"));
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	
	public static void WriteSellLetter(String player, int count, int sellcount, String bankname, String name,
			String number, String charname) {
		int nu1 = 949;
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
		String subject = "판매 등록 내역";
		String content = "판매 수표 : " + count + "억." + "\n판매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
				+ "\n예금주 : " + name + "" + "\n계좌 : " + number + "" + "\n판매자 캐릭터 : " + charname + ""
				+ "\n\n위 내용과 상이할 경우 " + "\n.판매취소 (게시글번호)" + "\n하신후 재등록 하시기 바랍니다." + "\n\n잘못된 정보 기재로 인한 피해사항은"
				+ "\n어떤한 경우에도 책임지지 않습니다.";
		String project = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(player);
		LetterTable.getInstance().writeLetter(nu1, dTime, project, player, 0, subject, content);
		if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			target.sendPackets(S_SkillSound.get(target.getId(), 1091));
			target.sendPackets(new S_ServerMessage(428));
		}
	}
	

	// TODO 중개 거래 게시판
	private static void LetterList(L1PcInstance pc, int type, int count) {
		pc.sendPackets(new S_LetterList(pc, type, count));
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
				+ "\n물품이 판매되었습니다. \n\n반드시 리스타트 후에 \n부가아이템창고에서\n아데나 및 수표를 회수하십시요.\n\n판매금액이 억 이상은\n수표와 아데나로 지급됩니다. \n\n수수료 5%가 차감된\n판매금액으로 지급되었습니다.";
		String name = "위탁관리인";
		L1PcInstance target = L1World.getInstance().getPlayer(in4);
		LetterTable.getInstance().writeLetter(nu1, dTime, name, in4, 0,
				subject, content);
		if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			target.sendPackets(new S_SystemMessage(
					"\\fY물품이 판매되었습니다 (수수료 5%)"));
			target.sendPackets(new S_SystemMessage(
					"\\fY리스 후 부가아이템창고에서 찾으실 수 있습니다."));
			target.sendPackets(S_SkillSound.get(target.getId(), 1091));
			target.sendPackets(new S_ServerMessage(428));
			pc.sendPackets(new S_LetterList(pc, 0, 20));
		}
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
		LetterTable.getInstance().writeLetter(nu1, dTime, name, seller, 0, subject, content);
		if (target != null && target.getOnlineStatus() != 0) {
			target.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "구매취소: " + buyer + "님이 구매를 취소 하였습니다."));
			LetterList(target, 0, 20);
			target.sendPackets(S_SkillSound.get(target.getId(), 1091));
			target.sendPackets(new S_ServerMessage(428));
		}
	}
	

	public static void WriteComplete(String buyer, int count, String seller) {
		int nu1 = 949;
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
		String subject = "판매완료내역";
		String content = "아데나 :" + count + "." + "\n판매자 :" + seller + "" + "\n물품 구매가 완료되었습니다. " + "\n\n인벤토리를 확인 "
				+ "\n해주시길 바랍니다.";
		String name = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(buyer);
		LetterTable.getInstance().writeLetter(nu1, dTime, name, buyer, 0, subject, content);
		if (target != null && target.getOnlineStatus() != 0) {
			
			target.sendPackets(new S_ChatPacket(target, String.format("%s", "구매완료: 아데나가 인벤토리로 지급되었습니다."), 1));
			target.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "구매완료: 아데나가 인벤토리로 지급되었습니다."));
		//	target.killSkillEffectTimer(SkillId.BUYER_COOLTIME);
			LetterList(target, 0, 20);
			target.sendPackets(S_SkillSound.get(target.getId(), 1091));
			target.sendPackets(new S_ServerMessage(428));
		}
	}
	
	public static void WriteBuyLetter(String player, int count, int sellcount, String bankname, String name,
			String number, String sellername, String buyername) {
		int nu1 = 949;
		SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd", Locale.KOREA);
		Date currentTime = new Date();
		String dTime = formatter.format(currentTime);
		String subject = "구매 신청 내역";
		String content = "구매 수표 : " + count + "억" + "\n구매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
				+ "\n예금주 : " + name + "" + "\n계좌 : " + number + "" + "\n판매자 캐릭터 : " + sellername + "" + "\n판매자는 입금완료후 반드시" + "\n.판매완료 (게시번호) 를 하셔야 구매자에게 아데나가 지급됨."
				+ "\n판매자가 악의적으로 판매완료를거부할 경우 메티스로 문의." + "\n입금전 반드시 상대방이" + "\n접속중인지 확인후 거래를"
				+ "\n진행해 주시기 바랍니다.";
		String project = "거래관리자";
		L1PcInstance target = L1World.getInstance().getPlayer(player);
		LetterTable.getInstance().writeLetter(nu1, dTime, project, player, 0, subject, content);
		if (target != null && target.getOnlineStatus() != 0) {
			LetterList(target, 0, 20);
			target.sendPackets(S_SkillSound.get(target.getId(), 1091));
			target.sendPackets(new S_ServerMessage(428));
		}
	}


	private static void 물품3(L1PcInstance pc, String count) {
		int i = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			i = Integer.parseInt(count);
		} catch (NumberFormatException e) {
		}
		int in1 = 0;
		int in2 = 0;
		int in3 = 0;
		int in4 = 0;
		int in5 = 0;
		int in6 = 0;
		String in = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("SELECT  item_id, item_enchantlvl  ,count ,AccountName ,UpClick, bless, bless_type, bless_type_value from board_item where id Like '"
							+ i + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {
				in1 = rs.getInt("item_id");
				in2 = rs.getInt("item_enchantlvl");
				in = rs.getString("AccountName");
				in3 = rs.getInt("UpClick");
				in4	= rs.getInt("bless");
				in5 = rs.getInt("bless_type");
				in6 = rs.getInt("bless_type_value");
			}
			if (pc.getAccountName().equalsIgnoreCase(in)) {
				
				if (pc.getInventory().checkItem(40308, Config.SELLITEMCASH)) { 
					ItemInstance item = ItemTable.getInstance().createItem(in1);
					item.setEnchantLevel(in2);
					item.setAttrEnchantLevel(in3);
					item.setBless(in4); 
					item.setBlessType(in5);
					item.setBlessTypeValue(in6); 
					//템번호,갯수,인챈트,블레스,attr,타입,블레스,확인
					봉인템(pc, in1, 1, in2, in4, in3, in5, in6, true); 
					//pc.getInventory().storeItem(item);
					ItemSellList.getInstance().deleteTopic(i);
					
					pc.getInventory().consumeItem(40308, Config.SELLITEMCASH );
					
					pc.sendPackets(new S_SystemMessage("\\fY판매취소를 하여 물품이 반환되었습니다."));
				} else {
					pc.sendPackets(new S_SystemMessage("\\fY판매취소 수수료가 부족합니다."));
				}
					
				
			} else {
				// pc.sendPackets(new S_SystemMessage("등록하신 물품이 아닙니다")); // 여기밑에
				
				pc.sendPackets(new S_SystemMessage(
						"\\fY[사용방법] .물품취소 (게시글번호)"));
				pc.sendPackets(new S_SystemMessage(
						"\\fY본인 판매글 이외에는 취소되지 않습니다."));
				
			}
		} catch (SQLException e) {
			pc.sendPackets(new S_SystemMessage(".물품취소 (게시글번호) 라고 적어주십시요."));
			pc.sendPackets(new S_SystemMessage("예)  .물품취소 0035"));
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public static void send_dungeon_remains(L1PcInstance pc, Collection<DungeonTimeProgress<?>> progresses){
		DungeonTimeInformationLoader loader = DungeonTimeInformationLoader.getInstance();
		for(DungeonTimeProgress<?> progress : progresses){
			DungeonTimeInformation dtInfo = loader.from_timer_id(progress.get_timer_id());
			pc.sendPackets(new S_SystemMessage(String.format("%s %s", dtInfo.get_description(), "(남은시간) " + String.valueOf((progress.get_remain_seconds() / 60) + "분"))));
		}
	}
	
	public void BloodParty(L1PcInstance pc) {
		if (pc.isDead()) {
			pc.sendPackets(new S_SystemMessage("죽은 상태에선 사용할 수 없습니다."));
			return;
		}
		int ClanId = pc.getClanid();
		if (ClanId != 0 && pc.getClanRank() == L1Clan.CLAN_RANK_PRINCE || pc.getClanRank() == L1Clan.CLAN_RANK_GUARDIAN ) {
			for (L1PcInstance SearchBlood : L1World.getInstance().getAllPlayers()) {
				if (SearchBlood.getClanid() != ClanId || SearchBlood.isPrivateShop() || SearchBlood.isInParty()) { 
					continue; // 포문탈출
				} else if (SearchBlood.getName() != pc.getName()) {
					pc.setPartyType(1); // 파티타입 설정
					SearchBlood.setPartyID(pc.getId()); // 파티아이디 설정
					SearchBlood.sendPackets(new S_Message_YN(954, pc.getName()));
					pc.sendPackets(new S_SystemMessage(SearchBlood.getName() + " 님에게 파티를 신청했습니다")); 
				}
			}
		} else { // 클랜이 없거나 군주 또는 수호기사 [X]
			pc.sendPackets(new S_SystemMessage("혈맹이 있으면서 군주, 수호기사라면 사용가능.")); 
		}
	}
	
	private void changename(L1PcInstance pc, String name) {
		if (BadNamesList.getInstance().isBadName(name)) {
			pc.sendPackets(new S_SystemMessage("생성 금지된 캐릭명입니다."));
			return;
		}
		if (CharacterTable.doesCharNameExist(name)) {
			pc.sendPackets(new S_SystemMessage("동일한 이름이 존재 합니다."));
			return;
		}
		if (pc.getClanid() != 0) {
			pc.sendPackets(new S_SystemMessage("혈맹을 잠시 탈퇴한 후 변경할 수 있습니다."));
			return;
		}
		if (pc.isCrown()) {
			pc.sendPackets(new S_SystemMessage("군주는 운영자와 상담 후에만 변경할 수 있습니다"));
			return;
		}
		if (pc.hasSkillEffect(1005) || pc.hasSkillEffect(2005)) {
			pc.sendPackets(new S_SystemMessage("채금 상태에는 변경할 수 없습니다."));
			return;
		}
		try {
			if (pc.getLevel() >= 60) {
				for (int i = 0; i < name.length(); i++) {
					if (name.charAt(i) == 'ㄱ'
							|| name.charAt(i) == 'ㄲ'
							|| name.charAt(i) == 'ㄴ'
							|| name.charAt(i) == 'ㄷ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㄸ'
							|| name.charAt(i) == 'ㄹ'
							|| name.charAt(i) == 'ㅁ'
							|| name.charAt(i) == 'ㅂ'
							|| // 한문자(char)단위로 비교
							name.charAt(i) == 'ㅃ'
							|| name.charAt(i) == 'ㅅ'
							|| name.charAt(i) == 'ㅆ'
							|| name.charAt(i) == 'ㅇ'
							|| // 한문자(char)단위로 비교
							name.charAt(i) == 'ㅈ'
							|| name.charAt(i) == 'ㅉ'
							|| name.charAt(i) == 'ㅊ'
							|| name.charAt(i) == 'ㅋ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅌ'
							|| name.charAt(i) == 'ㅍ'
							|| name.charAt(i) == 'ㅎ'
							|| name.charAt(i) == 'ㅛ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅕ'
							|| name.charAt(i) == 'ㅑ'
							|| name.charAt(i) == 'ㅐ'
							|| name.charAt(i) == 'ㅔ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅗ'
							|| name.charAt(i) == 'ㅓ'
							|| name.charAt(i) == 'ㅏ'
							|| name.charAt(i) == 'ㅣ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅠ'
							|| name.charAt(i) == 'ㅜ'
							|| name.charAt(i) == 'ㅡ'
							|| name.charAt(i) == 'ㅒ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅖ' || name.charAt(i) == 'ㅢ'
							|| name.charAt(i) == 'ㅟ'
							|| name.charAt(i) == 'ㅝ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == 'ㅞ' || name.charAt(i) == 'ㅙ' || name.charAt(i) == 'ㅚ'
							|| name.charAt(i) == 'ㅘ'
							|| // 한문자(char)단위로 비교.
							name.charAt(i) == '씹' || name.charAt(i) == '좃' || name.charAt(i) == '좆'
							|| name.charAt(i) == 'ㅤ') {
						pc.sendPackets(new S_SystemMessage("캐릭명이 올바르지 않습니다."));
						return;
					}
				}
				for (int i = 0; i < name.length(); i++) {
					if (!Character.isLetterOrDigit(name.charAt(i))) {
						pc.sendPackets(new S_SystemMessage("캐릭명이 올바르지 않습니다."));
						return;
					}
				}
				int numOfNameBytes = 0;
				numOfNameBytes = name.getBytes("EUC-KR").length;
				if (numOfNameBytes == 0) {
					pc.sendPackets(new S_SystemMessage(".이름변경 바꿀캐릭명 <--형식으로 입력"));
					return;
				}
				if (numOfNameBytes < 2 || numOfNameBytes > 12) {
					pc.sendPackets(new S_SystemMessage("한글 1자 ~ 6자 사이로 입력하세요."));
					return;
				} 
				if (BadNamesList.getInstance().isBadName(name)) {
					pc.sendPackets(new S_SystemMessage("생성 금지된 캐릭명입니다."));
					return;
				} 

				if (pc.getInventory().checkItem(408990, 1)) {
					Connection con = null;
					PreparedStatement pstm = null;
					try {
						con = L1DatabaseFactory.getInstance().getConnection();
						pstm = con.prepareStatement("UPDATE characters SET char_name =? WHERE char_name = ?");
						pstm.setString(1, name);
						pstm.setString(2, pc.getName());
						pstm.execute();
					} catch (SQLException e) {
					} finally {
						SQLUtil.close(pstm);
						SQLUtil.close(con);
					}

					pc.save(); // 저장
					/****** 여긴 파일로 캐릭명변경 내용 작성 부분 *******/

					/****** LogDB 라는 폴더를 미리 생성 해두세요 *******/
					Calendar rightNow = Calendar.getInstance();
					int year = rightNow.get(Calendar.YEAR);
					int month =  rightNow.get(Calendar.MONTH)+1;
					int date = rightNow.get(Calendar.DATE);
					int hour = rightNow.get(Calendar.HOUR);
					int min = rightNow.get(Calendar.MINUTE);
					String stryyyy = "";
					String strmmmm = "";
					String strDate = "";
					String strhour = "";
					String strmin = "";
					stryyyy = Integer.toString(year);
					strmmmm = Integer.toString(month);
					strDate = Integer.toString(date);
					strhour = Integer.toString(hour);
					strmin = Integer.toString(min);
					String str = "";
					str = new String("["+stryyyy+"-"+strmmmm+"-"+strDate+" "+strhour+":"+strmin+"]  " + pc.getName() + "  --->  " + name);
					StringBuffer FileName = new StringBuffer("LogDB/캐릭명변경.txt");
					PrintWriter out = null;
					try {
						out = new PrintWriter(new FileWriter(FileName.toString(), true));
						out.println(str);
						out.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					str = "";// 초기화
					pc.getInventory().consumeItem(408990, 1);
					pc.sendPackets(new S_SystemMessage("재접속하시면 새로운 이름으로 변경됩니다."));
					buddys(pc);
					편지삭제(pc);
					Thread.sleep(500);
					pc.sendPackets(new S_Disconnect());

				} else {
					pc.sendPackets(new S_SystemMessage("이름 변경 주문서가 부족합니다."));
				}
			} else {
				pc.sendPackets(new S_SystemMessage("60레벨 이상만 가능합니다."));
			}

		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".이름변경 바꿀캐릭명 으로 입력해 주세요."));
		}
	}
	
	public static void Compulsion_Attack(L1PcInstance pc, String param){
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String type = tokenizer.nextToken();
			if (type.equalsIgnoreCase("켬")) {
				if (pc.isCompulsionAttack()) {
					pc.sendPackets("이미 강제칼질이 활성화 상태입니다.");
					return;
				}
				pc.setCompulsionAttack(true);
				//L1Teleport.teleport(pc, pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), true, true);
				pc.sendPackets("강제 칼질을 활성화 합니다.");
			} else if (type.equalsIgnoreCase("끔")) {
				if (!pc.isCompulsionAttack()) {
					pc.sendPackets("이미 강제칼질이 비 활성화 상태입니다.");
					return;
				}
				pc.setCompulsionAttack(false);
				//L1Teleport.teleport(pc, pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), true, true);
				pc.sendPackets("강제 칼질을 비활성화 합니다.");
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".강 [켬/끔]"));
		}
	}
	
	
	public static String makeMoneyType(int wanted_adena) {
		String moneyString = Double.valueOf(wanted_adena).toString();

		String format = "###,###";
		DecimalFormat df = new DecimalFormat(format);
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();

		dfs.setGroupingSeparator(',');// 구분자를 ,로
		df.setGroupingSize(3);// 3자리 단위마다 구분자처리 한다.
		df.setDecimalFormatSymbols(dfs);

		return (df.format(Double.parseDouble(moneyString))).toString();
	}
	
	private static boolean 봉인템(L1PcInstance pc, int item_id, int count, int EnchantLevel, int Bless, int attr,int type,int value,boolean identi) {  
		ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count);
			item.setIdentified(identi);
			item.setEnchantLevel(EnchantLevel);
			item.setAttrEnchantLevel(attr);
			item.setIdentified(true);
			item.setBlessType(type);
			item.setBlessTypeValue(value);   
			
			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				pc.getInventory().storeItem(item);
				item.setBless(Bless); 
				pc.getInventory().updateItem(item, L1PcInventory.COL_BLESS);
				pc.getInventory().saveItem(item, L1PcInventory.COL_BLESS);
			} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
				pc.sendPackets(new S_ServerMessage(82));
				// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
				return false;
			}
			pc.sendPackets(new S_ServerMessage(403, item.getLogName())); //
			return true;
		} else { 
			return false;
		}
	}
	
	public boolean isValidQuiz(L1PcInstance pc, String quiz) {
		java.sql.Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		boolean result = false;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("select quiz from accounts where login='" + pc.getAccountName() + "'");
			rs = statement.executeQuery();

			String oldQuiz = "";
			if (rs.next()) {
				oldQuiz = rs.getString(1);
			}

			if (oldQuiz == null || oldQuiz.equalsIgnoreCase(quiz)) {
				result = true;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}

		return result;
	}
	
	private void chkquiz(L1PcInstance pc, String oldQuiz, String newQuiz) {
		java.sql.Connection con = null;
		PreparedStatement pstm = null;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			if (!isValidQuiz(pc, oldQuiz)) { 
				pc.sendPackets(new S_SystemMessage("기존 계정명의 퀴즈가 일치하지 않습니다. "));
				pc.sendPackets(new S_SystemMessage("다시 확인하시고 실행해 주세요."));
			} else {
				pc.setTempQuiz(newQuiz);
				pc.sendPackets(new S_Message_YN(622, "\"" + newQuiz + "\" 으로 설정하시겠습니까? (퀴즈 분실시 게임 진행이 불가능합니다)"));
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	private void changequiz(L1PcInstance pc, String param) {
		boolean firstQuiz = false;

		try {
			if (pc.get_lastQuizChangeTime() + 10 * 60 * 1000 > System.currentTimeMillis()) {
				pc.sendPackets(new S_SystemMessage("마지막으로 퀴즈를 변경하신지 10분이 지나지 않았습니다. 10분 후에 다시 시도해주세요."));
				return;
			}
			StringTokenizer tok = new StringTokenizer(param);

			String oldquiz = "";
			if (isValidQuiz(pc, oldquiz)) {
				firstQuiz = true;
			} else {
				oldquiz = tok.nextToken();
			}
			String newquiz = tok.nextToken(); 
			if (newquiz.length() < 6) {
				pc.sendPackets(new S_SystemMessage("입력하신 퀴즈의 자릿수가 너무 짧습니다."));
				pc.sendPackets(new S_SystemMessage("최소 6자 이상 입력해 주십시오."));
				return;
			}
			if (newquiz.length() > 12) {
				pc.sendPackets(new S_SystemMessage("입력하신 퀴즈의 자릿수가 너무 깁니다."));
				pc.sendPackets(new S_SystemMessage("최대 12자 이하로 입력해 주십시오."));
				return;
			}

			if (isDisitAlpha(newquiz) == false) {
				pc.sendPackets(new S_SystemMessage("퀴즈에 허용되지 않는 문자가 포함 되어 있습니다."));
				pc.sendPackets(new S_SystemMessage("숫자와 영문자만 입력해주세요."));
				return;
			}
			chkquiz(pc, oldquiz, newquiz);
		} catch (Exception e) {
			if (firstQuiz) {
				pc.sendPackets(new S_SystemMessage(".퀴즈설정 [새퀴즈]를 입력 해주세요."));
			} else {
				pc.sendPackets(new S_SystemMessage(".퀴즈설정 [현재퀴즈] [새퀴즈]를 입력 해주세요."));
			}
		}
	}
	
	private void validateQuiz(L1PcInstance pc, String param) {
		try {
			if (pc.isQuizValidated()) {
				pc.sendPackets(new S_SystemMessage("퀴즈가 이미 인증되었습니다."));
				return;
			}
			StringTokenizer tok = new StringTokenizer(param);

			if (isValidQuiz(pc, "")) {
				pc.sendPackets(new S_SystemMessage("퀴즈 인증을 위해 퀴즈 설정이 필요합니다. .퀴즈설정 으로 퀴즈를 설정해주세요."));
				return;
			} 
			String quiz = tok.nextToken();

			if (!isValidQuiz(pc, quiz)) {
				pc.sendPackets(new S_SystemMessage("퀴즈가 일치하지 않습니다."));
				pc.sendPackets(new S_SystemMessage("다시 확인하시고 실행해 주세요."));
				return;
			}

			pc.sendPackets(new S_SystemMessage("퀴즈가 인증되었습니다."));
			pc.setQuizValidated(); 
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".퀴즈인증 [퀴즈]를 입력 해주세요."));
		}
	}
	
	public void setQuiz(L1PcInstance pc, String quiz) {
		java.sql.Connection con = null;
		PreparedStatement pstm = null;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			String sqlstr = "UPDATE accounts SET quiz = ? WHERE login=?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setString(1, quiz);
			pstm.setString(2, pc.getAccountName());
			pstm.execute();
			pc.sendPackets(new S_SystemMessage("계정명 : " + pc.getAccountName() + " / 바뀐퀴즈 : " + quiz));
			pc.sendPackets(new S_SystemMessage("퀴즈 변경이 정상적으로 완료되었습니다."));

			pc.setNeedQuiz(false);
			pc.update_lastQuizChangeTime();
		} catch (Exception e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
/*	private void white_potion(L1PcInstance pc, String param) {
		try {
			ArrayList<String> result = new ArrayList<String>(); 
			int Percent = 0;
			int itemId = 0;
			Percent = Integer.parseInt(param);
			if (Percent <= 9) {
				pc.sendPackets("피유지 %는 10 이상 설정 가능합니다.");
				return;
			}
			if (Percent >= 100) {
				if (pc.getAutoPotionUse()) {
					pc.EndAutoPotionController();
					pc.setAutoPotionUse(false);
					pc.sendPackets("자동 물약을 종료합니다.");
				} else {
					pc.sendPackets("자동 물약[Off] 상태 입니다.");
				}
			} else {
				ArrayList<Integer> potions = new ArrayList<Integer>(); 
				for (ItemInstance item : pc.getInventory().getItems()) {
					if (item.getItemId() == 40012) {
						potions.add(40012);
						itemId = 40012;
					} else if (item.getItemId() == 40021) {
						potions.add(40021);
						itemId = 40021;
					} else if (item.getItemId() == 40024) {
						potions.add(40024);
						itemId = 40024;
					}
				}
				if (pc.getInventory().checkItem(itemId)) {
					if (potions.size() > 0) {
						L1Item temp = ItemTable.getInstance().getTemplate(itemId);
						pc.setAutoPotion(itemId);
						pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
					}
					L1Item temp = ItemTable.getInstance().getTemplate(pc.getAutoPotion());
					String potion_type = "없음";
					if (temp != null) {
						potion_type = temp.getName();
					}
					pc.setAutoPotion_Percent(Percent);
					result.add(potion_type);
					result.add(String.valueOf(pc.getAutoPotion_Percent() + "%"));
					pc.setAlarmPotion(true);
					pc.setAutoPotionUse(true);
					
					pc.StartAutoPotionController();
					pc.sendPackets(".말 10~99 설정! .말 100 이상 설정시 종료 됩니다.");
					pc.sendPackets("자동 물약 ON! ["+temp.getName()+"/"+Percent+"%]");
				} else {
					pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
				}
			}
		} catch (Exception e) { 
			pc.sendPackets(".말 10~99 설정! .말 100 이상 설정시 종료 됩니다.");
		}
	}
	
	private void orange_potion(L1PcInstance pc, String param) {
		try {
			ArrayList<String> result = new ArrayList<String>(); 
			int Percent = 0;
			int itemId = 0;
			Percent = Integer.parseInt(param);
			if (Percent <= 9) {
				pc.sendPackets("피유지 %는 10 이상 설정 가능합니다.");
				return;
			}
			if (Percent >= 100) {
				if (pc.getAutoPotionUse()) {
					pc.EndAutoPotionController();
					pc.setAutoPotionUse(false);
					pc.sendPackets("자동 물약을 종료합니다.");
				} else {
					pc.sendPackets("자동 물약[Off] 상태 입니다.");
				}
			} else {
				ArrayList<Integer> potions = new ArrayList<Integer>(); 
				for (ItemInstance item : pc.getInventory().getItems()) {
					if (item.getItemId() == 40011) {
						potions.add(40011);
						itemId = 40011;
					} else if (item.getItemId() == 40020) {
						potions.add(40020);
						itemId = 40020;
					} else if (item.getItemId() == 40023) {
						potions.add(40023);
						itemId = 40023;
					}
				}
				if (pc.getInventory().checkItem(itemId)) {
					if (potions.size() > 0) {
						L1Item temp = ItemTable.getInstance().getTemplate(itemId);
						pc.setAutoPotion(itemId);
						pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
					}
					L1Item temp = ItemTable.getInstance().getTemplate(pc.getAutoPotion());
					String potion_type = "없음";
					if (temp != null) {
						potion_type = temp.getName();
					}
					pc.setAutoPotion_Percent(Percent);
					result.add(potion_type);
					result.add(String.valueOf(pc.getAutoPotion_Percent() + "%"));
					pc.setAlarmPotion(true);
					pc.setAutoPotionUse(true);
					
					pc.StartAutoPotionController();
					pc.sendPackets(".주 10~99 설정! .주 100 이상 설정시 종료 됩니다.");
					pc.sendPackets("자동 물약 ON! ["+temp.getName()+"/"+Percent+"%]");
				} else {
					pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
				}
			}
		} catch (Exception e) { 
			pc.sendPackets(".주10~99 설정! .주 100 이상 설정시 종료 됩니다.");
		}
	}
	 */
	/*private void red_potion(L1PcInstance pc, String param) {
		try {
			ArrayList<String> result = new ArrayList<String>(); 
			int Percent = 0;
			int itemId = 0;
			Percent = Integer.parseInt(param);
			if (Percent <= 9) {
				pc.sendPackets("피유지 %는 10 이상 설정 가능합니다.");
				return;
			}
			if (Percent >= 100) {
				if (pc.getAutoPotionUse()) {
					pc.EndAutoPotionController();
					pc.setAutoPotionUse(false);
					pc.sendPackets("자동 물약을 종료합니다.");
				} else {
					pc.sendPackets("자동 물약[Off] 상태 입니다.");
				}
			} else {
				ArrayList<Integer> potions = new ArrayList<Integer>(); 
				for (ItemInstance item : pc.getInventory().getItems()) {
					if (item.getItemId() == 40010) {
						potions.add(40010);
						itemId = 40010;
					} else if (item.getItemId() == 40019) {
						potions.add(40019);
						itemId = 40019;
					} else if (item.getItemId() == 40022) {
						potions.add(40022);
						itemId = 40022;
					}
				}
				if (pc.getInventory().checkItem(itemId)) {
					if (potions.size() > 0) {
						L1Item temp = ItemTable.getInstance().getTemplate(itemId);
						pc.setAutoPotion(itemId);
						pc.sendPackets(String.format("%s을 선택 하였습니다.", temp.getName()));
					}
					L1Item temp = ItemTable.getInstance().getTemplate(pc.getAutoPotion());
					String potion_type = "없음";
					if (temp != null) {
						potion_type = temp.getName();
					}
					pc.setAutoPotion_Percent(Percent);
					result.add(potion_type);
					result.add(String.valueOf(pc.getAutoPotion_Percent() + "%"));
					pc.setAlarmPotion(true);
					pc.setAutoPotionUse(true);
					
					pc.StartAutoPotionController();
					pc.sendPackets(".빨 10~99 설정! .빨 100 이상 설정시 종료 됩니다.");
					pc.sendPackets("자동 물약 ON! ["+temp.getName()+"/"+Percent+"%]");
				} else {
					pc.sendPackets("인벤토리에 물약이 존재하지 않습니다.");
				}
			}
		} catch (Exception e) { 
			pc.sendPackets(".빨10~99 설정! .빨 100 이상 설정시 종료 됩니다.");
		}
	} */
	 
	// 자동 물약
	private void auto_potion(L1PcInstance pc) {
		try {
			ArrayList<String> result = new ArrayList<String>();  
			L1Item temp = ItemTable.getInstance().getTemplate(pc.getAutoPotion());
			String potion_type = "없음";
			if (temp != null) {
				potion_type = temp.getName();
			}
			result.add(pc.getAutoPotionUse() ? "ON" : "OFF");
			result.add(potion_type);
			result.add(String.valueOf(pc.getAutoPotion_Percent() + "%"));
			result.add(pc.isAlarmPotion() ? "ON" : "OFF");

			int size = 0;
			for (ItemInstance item : pc.getInventory().getItems()) {
				for (Integer id : Config.ServerInfo.AUTO_POTION_LIST) {
					if (item.getItemId() == id) {
						L1Item potion = ItemTable.getInstance().getTemplate(id);
						result.add(potion.getName());
					} else {
						size++;
					}
				}
			}
			
			for (int i = 0; i < size; i++) {
				result.add("없음");
			}

			pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "autoinfo", (String[]) result.toArray(new String[result.size()])));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".자동물약"));
		}
	}
	
	public static void ExchangeSearch(L1PcInstance pc, String param){
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String code = tokenizer.nextToken();
			pc.sendPackets(new S_ExchangeShopSearch(code));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".시세 아이템명"));
		}
	} 
	
	/********* 디비 친구목록에서 변경된 아이디 지우기 ************/

	private void buddys(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;
		String aaa = pc.getName();
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_buddys WHERE buddy_name=?");

			pstm.setString(1, aaa);
			pstm.execute();
		} catch (SQLException e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void 편지삭제(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;

		String aaa = pc.getName();

		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM letter WHERE receiver=?");
			pstm.setString(1, aaa);
			pstm.execute();
//			 System.out.println("....["+ aaa +"].....");
		} catch (SQLException e) {
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	private void 좌표(L1PcInstance pc) {
		try {
			long curtime = System.currentTimeMillis() / 1000;
			if (pc.getQuizTime2() + 20 > curtime) {
				long time = (pc.getQuizTime2() + 20) - curtime;
				pc.sendPackets(new S_ChatPacket(pc, time + "초 후 사용할 수 있습니다."));
				return;
			}
			Connection connection = null;
			connection = L1DatabaseFactory.getInstance().getConnection();
			PreparedStatement preparedstatement = connection.prepareStatement(
					"UPDATE characters SET LocX=33432,LocY=32807,MapID=4 WHERE account_name=? and MapID not in (5001,99,997,5166,39,38,34,701,2000)"); // 운영자의방,감옥,배틀존대기실
			// 제외
			preparedstatement.setString(1, pc.getAccountName());
			preparedstatement.execute();
			preparedstatement.close();
			connection.close();
			pc.sendPackets(new S_SystemMessage("계정내 모든 캐릭터의 좌표가 마을로 이동되었습니다"));

			pc.setQuizTime(curtime);
		} catch (Exception e) {
		}
	}
}
