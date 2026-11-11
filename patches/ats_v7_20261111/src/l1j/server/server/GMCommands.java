
package l1j.server.server;

import java.io.UnsupportedEncodingException; //## A112 암호변경 명령어 추가위해 임포트 
import java.lang.reflect.Constructor;
import java.security.MessageDigest; //## A112 암호변경 명령어 추가위해 임포트 
import java.security.NoSuchAlgorithmException; //#
import java.sql.Connection;
import java.sql.PreparedStatement; //## A83 검색 명령어 추가 위해 임포트 
import java.sql.ResultSet; //#
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import I1j.server.server.model.Thread.NpcDeleteTimer;
import javolution.util.FastTable;
import l1j.server.Base64; //## A112 암호변경 명령어 추가위해 임포트 
import l1j.server.L1DatabaseFactory; //## A83 검색 명령어 추가 위해 임포트 
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.DogFightSystem.DogFightCharacters;
import l1j.server.DogFightSystem.DogFightSystemSettings;
import l1j.server.DogFightSystem.DogFigthSystemLoader;
import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.DungeonTimer.Loader.DungeonTimeLoadManager;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.ItemEnchantSystem.ItemEnchantSystemLoadManager;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemAccountTable;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemCharacterTable;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemLoader;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.Template.Command.CommandArgs;
import l1j.server.Templates.L1Command;
import l1j.server.Templates.L1House;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.Controller.UserRankingController;
import l1j.server.server.command.L1Commands;
import l1j.server.server.command.executor.L1CommandExecutor;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ExpTable; //경험치복구
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.FurnitureInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.Instance.L1TrapInstance;
import l1j.server.server.model.Warehouse.PresentWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.serverpackets.S_Ability;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_Board;
import l1j.server.server.serverpackets.S_Chainfo; //## A87 캐릭터검사 추가 위해 임포트 
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_ExchangeShopSearch;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_Message_YN;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_Restart;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_War;
import l1j.server.server.serverpackets.S_Weather;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.SQLUtil;
import manager.SJManager;

public class GMCommands {
	private static Logger _log = Logger.getLogger(GMCommands.class.getName());

	boolean spawnTF = false;

	private static GMCommands _instance;

	private GMCommands() {
	}

	public static GMCommands getInstance() {
		if (_instance == null) {
			_instance = new GMCommands();
		}
		return _instance;
	}
	
	private String complementClassName(String className) {
		if (className.contains(".")) {
			return className;
		}
		return "l1j.server.server.command.executor." + className;
	}
	
	private boolean executeDatabaseCommand(L1PcInstance pc, String name, String arg) {
		try {
			L1Command command = L1Commands.get(name);
			if (command == null) {
				return false;
			}
			if (pc.getAccessLevel() < command.getLevel()) {
				pc.sendPackets(new S_ServerMessage(74, "커멘드 " + name));
				return true;
			}

			Class<?> cls = Class.forName(complementClassName(command.getExecutorClassName()));
			L1CommandExecutor exe = (L1CommandExecutor) cls.getMethod("getInstance").invoke(null);
			exe.execute(pc, name, arg);
			//manager.LogCommandAppend(pc.getName(), name, arg);
			SJManager.getInstance().GmAppend(pc.getName(), name, arg);
			/** 파일로그저장 **/ 
			//_log.info(pc.getName() + ": " + name + " " + arg);
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, "error gm command", e);
		}
		return false;
	}
	

	public void handleCommands(L1PcInstance gm, String cmdLine) {
		StringTokenizer token = new StringTokenizer(cmdLine);
		// 최초의 공백까지가 커맨드, 그 이후는 공백을 단락으로 한 파라미터로서 취급한다
		String cmd = "";
		if(token.hasMoreTokens()) cmd = token.nextToken();
		else cmd = cmdLine;
		String param = "";
		while (token.hasMoreTokens()) {
			param = new StringBuilder(param).append(token.nextToken()).append(' ').toString();
		}
		param = param.trim();
		
		try {
			// 일반 플레이어에 개방하는 커맨드는 여기에 쓴다

			if (gm.getAccessLevel() < 100) {
				gm.sendPackets(new S_ServerMessage(74, "커맨드 " + cmd)); // \f1%0은 사용할 수 없습니다.
				return;
			}

			// 모니터에 개방하는 커멘드는 여기에 쓴다

			if (gm.getAccessLevel() < 200) {
				gm.sendPackets(new S_ServerMessage(74, "커맨드 " + cmd)); // \f1%0은 사용할 수 없습니다.
				return;
			}
			
			if (executeDatabaseCommand(gm, cmd, param)) {
				if (!cmd.equalsIgnoreCase(".")) {
					_lastCmd = cmdLine;
				}
				return;
			}
			SJManager.getInstance().GmAppend(gm.getName(), cmd, param);

			switch (cmd) {
			case "공성시작":					castleWarStart(gm, param);break;
			case "공성종료":					stopWar(gm, param);break;
			
			case "서버저장":case "저장":				serversave(gm);break;
			case "감옥":					hold(gm, param);break;
			case "시세":
				ExchangeSearch(gm, param);
				break;
			case "자동물약":case "자":
				auto_potion(gm);
				break;
		    case "마크":     
		    Mark1(gm, param);	 break;
			case "퀴즈변경":				퀴즈변경(gm, param);break;
			case "혈마크":       
			case "던전시간":
			case "던전":
				send_dungeon_remains(gm, gm.get_account_progresses());
				send_dungeon_remains(gm, gm.get_character_progresses());	
				break;
			case "캐릭터":		case "구슬":							
				CTLoadManager.commands(gm, param);															
				break;
			case "교환":
				MultiTrade1(gm, param);
				break;
			case "레이드":
				Dragon_Raid_Load_Manager.commands(gm, param);
				break;
			case "보호모드":
			case "안전모드":
				setProtectionMode(gm, param);
				break;
			case "랭킹갱신":
				UserRankingController.isRenewal = true;
				gm.sendPackets(new S_SystemMessage("랭킹이 갱신 되었습니다."));
				break;
			case "저장1": 
				gm.addSlotItem(0, 0, true);
				gm.sendPackets(new S_ChatPacket(gm,String.format("%s", "장비 셋트 1번이 저장 되었습니다."), 1)); 
				break;
			case "저장2": 
				gm.addSlotItem(1, 0, true);
				gm.sendPackets(new S_ChatPacket(gm,String.format("%s", "장비 셋트 2번이 저장 되었습니다."), 1)); 
				break; 
				
			case "창고불가":				NoShopAndWare(gm, param);break;
			case "드랍불가":				NoDropItem(gm, param) ;break;
			case "교환불가":				NoTradable(gm, param) ;break;
			case "던전타이머":
				DungeonTimeLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));
				break;
			case "오픈대기":				standBy(gm, param);break;
			case "검":					chainfo(gm, param);break;
			case "랭킹조회":case "랭킹":
				for (L1Object obj : L1World.getInstance().getObject()) {
					if (obj instanceof L1NpcInstance) {
						L1NpcInstance npc = (L1NpcInstance) obj;
						if (npc.getNpcId() == 200001) {
							gm.sendPackets(new S_Board(npc));
							break;
						}
					}
				} 
				break;
			case "인챈트시스템":
				ItemEnchantSystemLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));
				break;
			case "기운축복":
				Blessleaf(gm, param);
				break;
			case "계정추가":				addaccount(gm, param);break;
			case "투견":
				DogFightSystem(gm, param);
				break;
			case "오토루팅":				autoloot(gm, param);break;
			case "자동사냥":
				if (gm._AutoController != null) {
					gm.EndAutoController();
					gm.sendPackets("자동 사냥을 종료합니다.");
				} else {
					gm.StartAutoController();
					gm.sendPackets("자동 사냥을 시작합니다.");
				}
				break;
			case "분포도":case "투견멘트": case "버경멘트":			DistributionMsg(gm, param);break;
			case "로그":
			case "접속": 
				Distributionlogin(gm, param);
				break;
			case "이팩트":
				effect(gm, param);
				break;
			case "텔":
				gm.teleport(gm.getX(), gm.getY(), gm.getMapId(), gm.getHeading(), 169, true, false);
				break;
			case "도움말":
				showHelp(gm);
				break;
			case "라이트":
				maphack(gm, param);
				break;
			case "이미지":
				spawn_images(gm, param);
				break;
			case "이미지삭제":
				this.get_delete_gfx(gm);
				break;
			case "인벤정리":
				InventoryArrangement(gm, param);
				break;
			case "인벤삭제":
				InventoryDelete(gm, param);
				break;
			case "유저인벤삭제":
				targetInventoryDelete(gm, param);
				break;
			case "가라":
				nocall(gm, param);
				break;
			case "렙작":
				levelup3(gm, param);
				break;
			case "경치복구":
				returnEXP(gm, param);
				break;
			case "서먼":
				summon(gm, param);
				break;
			case "청소":
				deleteItem();
				break;  
			case "스킬마스터":
				addSkill(gm);
				break;  
			case "파티소환":
				partyrecall(gm, param);
				break;
			case "이동":
				teleportTo(gm, param);
				break;
			case "위치":
				loc(gm);
				break;
			case "리스":
				restart(gm, param);
				break;
			case "아지트":
				GiveHouse(gm, param); // 포비: 아지트 지급
				break;
			case "킬":
				kill(gm, param);
				break; 
			case "아데나":
				adena(gm, param);
				break;
			case "출두":
				moveToChar(gm, param);
				break;
			case "고스폰":
				tospawn(gm, param);
				break;
			case "날씨":
				changeWeather(gm, param);
				break; 
			case "영구추방":
				powerkick(gm, param);
				break;
			case "추방":
				kick(gm, param);
				break;
			case "전체선물":
				allpresent(gm, param);
				break;
			case "부가선물":
				allPresent2(gm, param);
				break;
			case "부가개인선물":
				present1(gm, param);
				break; 
 
			case "계정압류":
				accbankick(gm, param);
				break;
			case "스킬보기":
				burf(gm, param);
				break;
			case "버프미":
				buff(gm, param, true);
				break;
			case "버프":
				buff(gm, param, false);
				break;
			case "올버프":
				allBuff(gm, param);
				break;
			case "몬스터":
				spawn(gm, param);
				break;
			case "몬스터배치":
				npcSpawn(gm, param, "mob");
				break;
			case "변신":
				polymorph(gm, param);
				break;
			case "세트아이템":
				makeItemSet(gm, param);
				break; 
			case "채금":
				chatng(gm, param);
				break;
			case "채팅":
				chat(gm, param);
				break;  
			case "바로종료":
				shutdownNow();
				break;
			case "종료취소":
				shutdownAbort();
				break;
			case "종료":
				shutdown(gm, param);
				break;
			case "리셋트랩":
				resetTrap();
				break;
			case "엔피씨그래픽":
				gfxId(gm, param);
				break; 
			case "액션":
				action(gm, param);
				break;
			case "밴아이피":
				banIp(gm, param);
				break; 
			case "감시":
				patrol(gm);
				break;
			case "킥":
				skick(gm, param);
				break; 
			case "피바":
				hpBar(gm, param);
				break;
			case "쇼트랩":
				showTraps(gm, param);
				break;
			case "리로드트랩":
				reloadTraps();
				break;
			case "뻥":
				popcon(gm, param);
				break;
			case "재실행":
				redo(gm, param);
				break;
			case "광역밴":
				LargeAreaIPBan(gm, param);
				break;
			case "실행":
				favorite(gm, param);
				break;
			case "정": case "ㅈ":
				describe(gm);
				break;
			case "지엠":
				gm(gm);
				break;
			case "콘":
				   checkicon(gm, param);
				break; 
			case "데미지체크":
				if (Config.ALT_ATKMSG) {
					Config.ALT_ATKMSG = false;
					gm.sendPackets(new S_SystemMessage("데미지체크 OFF"));
				} else {
					Config.ALT_ATKMSG = true;
					gm.sendPackets(new S_SystemMessage("데미지체크 ON"));
				}
				break;
			/*case "검색":
				searchDatabase(gm, param); // #
				break;*/
			case "캐릭터검사":
				chainfo(gm, param); // #
				break;
			case "암호변경":
				changePassword(gm, param); // #
				break;
			case "오브젝트":
				showObjectList(gm, param);
				break;
			case "레벨보상":case "레벨선물":
				LevelUpPresentSystem(gm, param);
				break;
			case "강제칼질": case "강":
				Compulsion_Attack(gm, param);
				break;
			case "상점추방":				ShopKick(gm, param);break;
			default:
				gm.sendPackets(new S_SystemMessage("커맨드 " + cmd + " 은(는) 존재하지 않습니다."));
				break;
			}

			if (!cmd.equalsIgnoreCase("재실행")) {
				_lastCmd = cmdLine;
			}

			//_log.info(gm.getName() + "이(가)." + cmdLine + "커맨드를 사용했습니다.");
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
 

	private void showHelp(L1PcInstance pc) {
		pc.sendPackets(new S_SystemMessage("-------------------<GM 명령어>--------------------"));
		pc.sendPackets(new S_SystemMessage(".공성시작 .공성종료 .서버저장 .감옥 .시세"));
		pc.sendPackets(new S_SystemMessage(".자동물약 .퀴즈변경 .던전시간 .상점추방"));
		pc.sendPackets(new S_SystemMessage(".구슬 .교환 .보호모드"));
		pc.sendPackets(new S_SystemMessage(".랭킹갱신 .저장1 .저장2 .창고불가 .드랍불가"));
		pc.sendPackets(new S_SystemMessage(".교환불가 .던전타이머 .검 .랭킹조회 or 랭킹"));
		pc.sendPackets(new S_SystemMessage(".인챈트시스템 .계정추가 .킬데스초기화"));
		pc.sendPackets(new S_SystemMessage(".킬데스 .투견 .오토루팅 .자동사냥 (안씀)"));
		pc.sendPackets(new S_SystemMessage(".분포도 .기운축복 .이미지 .청소 .출두 .변신"));
		pc.sendPackets(new S_SystemMessage(".로그 .이팩트 .텔 .도움말 .라이트"));
		pc.sendPackets(new S_SystemMessage(".이미지삭제 .인벤정리 .인벤삭제 .유저인벤삭제"));
		pc.sendPackets(new S_SystemMessage(".데스 .가라 .렙작 .경치복구 .서먼 .스킬마스터"));
		pc.sendPackets(new S_SystemMessage(".파티소환 .이동 .위치 .리스 .아지트 .킬 .아데나"));
		pc.sendPackets(new S_SystemMessage(".고스폰 .투명 .날씨 .영구추방 .추방 .전체선물"));
		pc.sendPackets(new S_SystemMessage(".부가선물 .부가개인선물 .계정압류 .스킬보기"));
		pc.sendPackets(new S_SystemMessage(".버프미 .버프 .올버프 .몬스터 .몬스터배치"));
		pc.sendPackets(new S_SystemMessage(".세트아이템 .채금 .채팅 .바로종료 .종료취소 .종료"));
		pc.sendPackets(new S_SystemMessage(".리셋트랩 .엔피씨그래픽 .액션 .밴아이피 .감시"));
		pc.sendPackets(new S_SystemMessage(".킥 .피바 .쇼트랩 .리로드트랩 .뻥 .재실행 .광역밴"));
		pc.sendPackets(new S_SystemMessage(".실행 .스킬비 .정 .지엠 .콘 .데미지체크 .캐릭터검사"));
		pc.sendPackets(new S_SystemMessage(".암호변경 .오브젝트 .레벨보상 .강제칼질 .상점추방"));
		pc.sendPackets(new S_SystemMessage("--------------------------------------------------"));
	}

	private void summon(L1PcInstance pc, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String nameid = tok.nextToken();
			int npcid = 0;
			try {
				npcid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				npcid = NpcTable.getInstance().findNpcIdByNameWithoutSpace(nameid);
				if (npcid == 0) {
					pc.sendPackets(new S_SystemMessage("해당 NPC가 발견되지 않았습니다."));
					return;
				}
			}
			int count = 1;
			if (tok.hasMoreTokens()) {
				count = Integer.parseInt(tok.nextToken());
			}
			L1Npc npc = NpcTable.getInstance().getTemplate(npcid);
			for (int i = 0; i < count; i++) {
				SummonInstance summonInst = new SummonInstance(npc, pc);
				summonInst.setPetcost(0);
			}
			nameid = NpcTable.getInstance().getTemplate(npcid).get_name();
			pc.sendPackets(new S_SystemMessage(nameid + "(ID:" + npcid + ") (" + count + ") 을(를) 소환했습니다."));
		} catch (Exception e) {
//			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			pc.sendPackets(new S_SystemMessage(".서먼 [npcid 또는 name] [출현수]를 입력 해주세요."));
		}
	}

	/** 포비: 아지트 */
	private void GiveHouse(L1PcInstance pc, String poby) {
		try {
			StringTokenizer st = new StringTokenizer(poby);
			String pobyname = st.nextToken();
			int pobyhouseid = Integer.parseInt(st.nextToken());

			L1PcInstance target = L1World.getInstance().getPlayer(pobyname);

			if (target != null) {
				if (target.getClanid() != 0) {
					L1Clan TargetClan = L1World.getInstance().getClan(target.getClanname());
					L1House pobyhouse = HouseTable.getInstance().getHouseTable(pobyhouseid);
					TargetClan.setHouseId(pobyhouseid);
					ClanTable.getInstance().updateClan(TargetClan);
					pc.sendPackets(new S_SystemMessage(
							target.getClanname() + " 혈맹에게 " + pobyhouse.getHouseName() + "번을 지급하였습니다."));
					for (L1PcInstance tc : TargetClan.getOnlineClanMember()) {
						tc.sendPackets(new S_SystemMessage("게임마스터로부터 " + pobyhouse.getHouseName() + "번을 지급 받았습니다."));
					}
				} else {
					pc.sendPackets(new S_SystemMessage(target.getName() + "님은 혈맹에 속해 있지 않습니다."));
				}
			} else {
				pc.sendPackets(new S_ServerMessage(73, pobyname));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage("\\fY.아지트 <지급할혈맹원> <아지트번호>"));
		}
	}

	private void addSkill(L1PcInstance pc) {
		try {
			int cnt = 0; // 루프 카운터
			String skill_name = ""; // 스킬명
			int skill_id = 0; // 스킬 ID

			int object_id = pc.getId(); // 캐릭터의 objectid를 취득
			S_SkillSound s = S_SkillSound.get(object_id, '\343');
			pc.sendPackets(s); // 마법 습득의 효과음을 울린다
			pc.broadcastPacket(s);

			if (pc.isCrown()) {
				pc.sendPackets(
						new S_AddSkill(255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0));
				for (cnt = 1; cnt <= 16; cnt++) // LV1~2 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				for (cnt = 113; cnt <= 120; cnt++) // 프리 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isKnight()) {
				pc.sendPackets(
						new S_AddSkill(255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
				for (cnt = 1; cnt <= 8; cnt++) // LV1 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				for (cnt = 87; cnt <= 91; cnt++) // 나이트 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isElf()) {
				pc.sendPackets(new S_AddSkill(255, 255, 127, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 127, 3, 255,
						255, 255, 255, 0, 0));
				for (cnt = 1; cnt <= 48; cnt++) // LV1~6 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				for (cnt = 129; cnt <= 176; cnt++) // 에르프 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isWizard()) {
				pc.sendPackets(new S_AddSkill(255, 255, 127, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0));
				for (cnt = 1; cnt <= 80; cnt++) // LV1~10 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isDarkelf()) {
				pc.sendPackets(
						new S_AddSkill(255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 127, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
				for (cnt = 1; cnt <= 16; cnt++) // LV1~2 마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				for (cnt = 97; cnt <= 111; cnt++) // DE마법
				{
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".스킬마스터 커맨드 에러"));
		}
	}

	private void loc(L1PcInstance pc) {
		try {
			int locx = pc.getX();
			int locy = pc.getY();
			short mapid = pc.getMapId();
			int gab = L1WorldMap.getInstance().getMap(mapid).getOriginalTile(locx, locy);
			pc.sendPackets(new S_SystemMessage((new StringBuilder()).append("좌표 (").append(locx).append(", ")
					.append(locy).append(", ").append(mapid).append(") ").append(gab).toString()));
			//System.out.println(map.isPassable(locx,locy));
		} catch (Exception e) {
		}
	}
	
	private void restart(L1PcInstance pc, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target != null) {
				restartnow(pc, target);
			} else {
				pc.sendPackets(new S_SystemMessage(".리스 캐릭명"));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".리스 캐릭명"));
		}
	}
	

	private void restartnow(L1PcInstance gm, L1PcInstance target) {
		try {
			target.sendPackets(new S_Restart(target.getId(), 1), true);
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}	
	

	private void adena(L1PcInstance pc, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			String para1 = stringtokenizer.nextToken();
			int count = Integer.parseInt(para1);

			ItemInstance adena = pc.getInventory().storeItem(ItemId.아데나, count);
			if (adena != null) {
				pc.sendPackets(
						new S_SystemMessage((new StringBuilder()).append(count).append("아데나를 생성했습니다.").toString()));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage((new StringBuilder()).append(".아데나 [액수]를 입력 해주세요.").toString()));
		}
	}
	
	private void standBy(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (Config.STANDBY_SERVER) {
					gm.sendPackets(new S_SystemMessage("이미 대기 상태로 돌입하였습니다."));
					return;
				}
				Config.STANDBY_SERVER = true;
				Config.RATE_XP = 0;//오픈대기때 경험치 강제 0으로만듬 
				L1World.getInstance().broadcastPacketToAll(
				new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "서버가 오픈대기에 돌입합니다. 일부 패킷이 차단되었습니다."));
				
				L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("서버가 오픈대기에 돌입합니다. 일부 패킷이 차단되었습니다."));
			
				
				
			} else if (status.equalsIgnoreCase("끔")) {
				if (!Config.STANDBY_SERVER) {
					gm.sendPackets(new S_SystemMessage("대기 상태가 아닙니다."));
					return;
				}
				Config.load();//기존컨피그 경험치 리로드시켜버림
				Config.STANDBY_SERVER = false;
				L1World.getInstance().broadcastPacketToAll(
						new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "대기 상태가 해지되고, 정상적인 플레이가 가능합니다."));
						
						L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("대기 상태가 해지되고, 정상적인 플레이가 가능합니다."));
					
			}
		} catch (Exception eee) {
			gm.sendPackets(new S_SystemMessage(".오픈대기 [켬/끔] 으로 입력하세요."));
			gm.sendPackets(new S_SystemMessage("켬 - 오픈대기 상태로 전환 | 끔 - 일반모드로 게임시작"));
		}
	}

	public void levelup3(L1PcInstance gm, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String user = tok.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(user);
			int level = Integer.parseInt(tok.nextToken());
			if (level == target.getLevel()) {
				return;
			}
			if (!IntRange.includes(level, 1, 99)) {
				gm.sendPackets(new S_SystemMessage("1-99의 범위에서 지정해 주세요"));
				return;
			}
			target.setExp(ExpTable.getExpByLevel(level));
			gm.sendPackets(new S_SystemMessage(target.getName() + "님의 레벨이 변경됨! .캐릭터검사 [케릭명]으로 확인요망"));
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".렙작 [케릭명] [레벨] 입력"));
		}
	}

	private void moveToChar(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);

			if (target != null) {
				gm.teleport(target.getX(), target.getY(), target.getMapId(), gm.getHeading(), 169, false, false);
				gm.sendPackets(new S_SystemMessage((new StringBuilder()).append(pcName).append(" 님에게 이동했습니다.").toString()));
			} else {
				gm.sendPackets(new S_SystemMessage((new StringBuilder()).append(pcName).append("님은 없습니다.").toString()));
			}
		} catch (Exception exception) {
			gm.sendPackets(new S_SystemMessage(".출두 [캐릭터명]을 입력 해주세요."));
		}
	}

	private void resetTrap() {
		L1WorldTraps.getInstance().resetAllTraps();
	}

	private void shutdownAbort() {
		GameServer.getInstance().abortShutdown();
	}

	private void shutdownNow() {
		GameServer.getInstance().shutdown();
	}

	private void shutdown(L1PcInstance gm, String params) {
		try {
			int sec = 0;
			StringTokenizer st = new StringTokenizer(params);
			if (st.hasMoreTokens()) {
				String param1 = st.nextToken();
				sec = Integer.parseInt(param1, 10);
			}
			if (sec < 5) {
				sec = 5;
			}
			GameServer.getInstance().shutdownWithCountdown(sec);
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".종료 [시간(초)]을 입력 해주세요."));
		}
	}

	private void npcSpawn(L1PcInstance gm, String param, String type) {
		String msg = null;

		try {
			int npcid = Integer.parseInt(param.trim());
			L1Npc template = NpcTable.getInstance().getTemplate(npcid);

			if (template == null) {
				msg = "해당하는 NPC가 발견되지 않았습니다.";
				return;
			}
			if (type.equals("mob")) {
				if (!template.getImpl().equals("L1Monster")) {
					msg = "지정한 NPC는 L1Monster가 아닙니다.";
					return;
				}
				SpawnTable.storeSpawn(gm, template);
			} else if (type.equals("npc")) {
				NpcSpawnTable.getInstance().storeSpawn(gm, template);
			}
			mobspawn(gm, npcid, 0, false);
			msg = new StringBuilder().append(template.get_name()).append(" (" + npcid + ") ").append("을(를) 추가했습니다.")
					.toString();
		} catch (Exception e) {
			msg = ".몬스터배치 또는 .엔피씨배치 [NPCID]라고 입력 해주세요.";
		} finally {
			if (msg != null) {
				gm.sendPackets(new S_SystemMessage(msg));
			}
		}
	}

	private void spawn(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String nameid = tok.nextToken();
			int count = 1;
			if (tok.hasMoreTokens()) {
				count = Integer.parseInt(tok.nextToken());
			}
			int randomrange = 0;
			if (tok.hasMoreTokens()) {
				randomrange = Integer.parseInt(tok.nextToken(), 10);
			}
			int npcid = 0;
			try {
				npcid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				npcid = NpcTable.getInstance().findNpcIdByNameWithoutSpace(nameid);
				if (npcid == 0) {
					gm.sendPackets(new S_SystemMessage("해당 NPC가 발견되지 않았습니다."));
					return;
				}
			}
			spawnTF = true; // .스폰 사용
			for (int k3 = 0; k3 < count; k3++) {
				mobspawn(gm, npcid, randomrange, false);
			}
			nameid = NpcTable.getInstance().getTemplate(npcid).get_name();
			gm.sendPackets(new S_SystemMessage(nameid + "(" + npcid + ") (" + count + ") 을(를) 소환했습니다. (범위:" + randomrange + ")"));
			spawnTF = false;
		} catch (Exception e) {
//			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			gm.sendPackets(new S_SystemMessage(".몬스터 [npcid 또는 name] [출연수] [범위]를 입력해 주세요."));
		}
	}

	private void changeWeather(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			String s27 = stringtokenizer.nextToken();
			int weather = Integer.parseInt(s27);
			L1World world = L1World.getInstance();
			world.setWeather(weather);
			L1World.getInstance().broadcastPacketToAll(new S_Weather(weather));
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".날씨 [0~3(눈), 16~19(비)]라고 입력 해주세요."));
		}
	}

	private void nocall(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();

			L1PcInstance target = null; // q
			target = L1World.getInstance().getPlayer(pcName);
			if (target != null) { // 타겟
				target.teleport(33440, 32795, (short) 4, target.getHeading(),169, true, false); // /
				// 가게될
				// 지점
				// (유저가떨어지는지점)
			} else {
				gm.sendPackets(new S_SystemMessage("접속중이지 않는 유저 ID 입니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".가라 (보낼케릭터명) 으로 입력해 주세요."));
		}
	}
	private void deleteItem() {
		for (L1Object l1object : L1World.getInstance().getObject()) {
			if (l1object instanceof ItemInstance) {
				ItemInstance l1iteminstance = (ItemInstance) l1object;
				if (l1iteminstance.getX() == 0 && l1iteminstance.getY() == 0) { // 지면상의 아이템은 아니고, 누군가의 소유물
					continue;
				}

				ArrayList<L1PcInstance> players = L1World.getInstance().getVisiblePlayer(l1iteminstance, 0);
				if (0 == players.size()) {
					L1Inventory groundInventory = L1World.getInstance().getInventory(l1iteminstance.getX(),
							l1iteminstance.getY(), l1iteminstance.getMapId());
					int itemId = l1iteminstance.getItem().getItemId();
					if (itemId == 40314 || itemId ==  40316) { // 펫 목걸이
						PetTable.getInstance().deletePet(l1iteminstance.getId());
					} else if (itemId >= 49016 && itemId <= 49025) { // 편지지
						LetterTable lettertable = new LetterTable();
						lettertable.deleteLetter(l1iteminstance.getId());
					} else if (itemId >= 41383 && itemId <= 41400) { // 가구
						if (l1object instanceof FurnitureInstance) {
							FurnitureInstance furniture = (FurnitureInstance) l1object;
							if (furniture.getItemObjId() == l1iteminstance.getId()) { // 이미 꺼내고 있는 가구
								FurnitureSpawnTable.getInstance().deleteFurniture(furniture);
							}
						}
					}
					groundInventory.deleteItem(l1iteminstance);
					L1World.getInstance().removeVisibleObject(l1iteminstance);
					L1World.getInstance().removeObject(l1iteminstance);
				}
			}
		}
		L1World.getInstance().broadcastServerMessage("월드맵 상의 아이템이 운영자에 의해 삭제되었습니다.");
	}

	private void popcon(L1PcInstance gm, String cmd) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(cmd);
			String s = stringtokenizer.nextToken();
			Config.setParameterValue("WhoisCount", s);
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".뻥 [숫자]을 입력 해주세요."));
		}
	} // 뻥튀기 명령어추가 by.닐라
 
	private void partyrecall(L1PcInstance pc, String pcName) {
		L1PcInstance target = L1World.getInstance().getPlayer(pcName);
		if (target != null) {
			L1Party party = target.getParty();
			if (party != null) {
				int x = pc.getX();
				int y = pc.getY() + 2;
				short map = pc.getMapId();
				L1PcInstance[] players = party.getMembers();
				for (L1PcInstance pc2 : players) {
					try {
						pc2.teleport(x, y, map, pc2.getHeading(),169, true, false);
						pc2.sendPackets(new S_SystemMessage("게임 마스터에게 소환되었습니다."));
					} catch (Exception e) {
						_log.log(Level.SEVERE, "", e);
					}
				}
			} else {
				pc.sendPackets(new S_SystemMessage("파티 멤버가 아닙니다."));
			}
		} else {
			pc.sendPackets(new S_SystemMessage("그러한 캐릭터는 없습니다."));
		}
	}
 
	private void polymorph(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			int polyid = Integer.parseInt(st.nextToken());

			L1PcInstance pc = L1World.getInstance().getPlayer(name);

			if (pc == null) {
				gm.sendPackets(new S_ServerMessage(73, name)); // \f1%0은 게임을 하고 있지 않습니다.
			} else {
				try {
					L1PolyMorph.doPoly(pc, polyid, 7200, L1PolyMorph.MORPH_BY_ITEMMAGIC);
				} catch (Exception exception) {
					gm.sendPackets(new S_SystemMessage(".변신 [캐릭터명] [그래픽 ID]를 입력 해주세요."));
				}
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".변신 [캐릭터명] [그래픽 ID]를 입력 해주세요."));
		}
	}

	private void LargeAreaIPBan(L1PcInstance pc, String param) { // 광역밴 추가
		try {
			StringTokenizer st = new StringTokenizer(param);

			String charName = st.nextToken();
			String banIp = "";

			L1PcInstance player = L1World.getInstance().getPlayer(charName);

			if (player != null) {
				banIp = player.getNetConnection().getIp();
				String[] banIpArr = banIp.split("\\.");
				IpTable iptable = IpTable.getInstance();
				pc.sendPackets(new S_SystemMessage("----------------------------------------------------"));
				Account.ban(player.getAccountName());
				player.logout();
				player.getNetConnection().kick();
				for (int i = 1; i <= 220; i++) {
					iptable.banIp(banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + "." + i);
				}

				pc.sendPackets(new S_SystemMessage("IP: " + banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + ".1~220광역 차단."));
				pc.sendPackets(new S_SystemMessage("----------------------------------------------------"));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".광역밴  [아이디]"));
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
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".경치복구 [캐릭터명]을 입력 해주세요."));
		}
	}

	private void chatng(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			int time = Integer.parseInt(st.nextToken());
			L1PcInstance pc = L1World.getInstance().getPlayer(name);

			if (pc != null) {
				pc.setSkillEffect(SkillId.상태_채팅_금지, time * 60 * 1000);
				pc.sendPackets(new S_SkillIconGFX(36, time * 60));
				pc.sendPackets(new S_ServerMessage(286, String.valueOf(time))); // \f3게임에 적합하지 않는 행동이기 (위해)때문에, 향후%0분간
																				// 채팅을 금지합니다.
				gm.sendPackets(new S_ServerMessage(287, name)); // %0의 채팅을 금지했습니다.
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".채금 [캐릭터명] [시간(분)]을 입력 해주세요."));
		}
	}

	private void chat(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			if (st.hasMoreTokens()) {
				String flag = st.nextToken();
				String msg;
				if (flag.compareToIgnoreCase("켬") == 0) {
					L1World.getInstance().set_worldChatElabled(true);
					msg = "월드 채팅을 유효하게 했습니다.";
				} else if (flag.compareToIgnoreCase("끔") == 0) {
					L1World.getInstance().set_worldChatElabled(false);
					msg = "월드 채팅을 정지했습니다.";
				} else {
					throw new Exception();
				}
				gm.sendPackets(new S_SystemMessage(msg));
			} else {
				String msg;
				if (L1World.getInstance().isWorldChatElabled()) {
					msg = "현재 월드 채팅은 유효합니다. .채팅 끔 으로 정지 하게 할 수 있습니다.";
				} else {
					msg = "현재 월드 채팅은 정지 하고 있습니다. .채팅 켬 으로 유효하게 할 수 있습니다.";
				}
				gm.sendPackets(new S_SystemMessage(msg));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".채팅 [켬 또는 끔] 이라고 입력 해주세요."));
		}
	}

	private void teleportTo(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int locx = Integer.parseInt(st.nextToken());
			int locy = Integer.parseInt(st.nextToken());
			short mapid;
			if (st.hasMoreTokens()) {
				mapid = Short.parseShort(st.nextToken());
			} else {
				mapid = pc.getMapId();
			}
			pc.teleport(locx, locy, mapid, pc.getHeading(), 169, false, false);
			pc.sendPackets(new S_SystemMessage("좌표 " + locx + ", " + locy + ", " + mapid + "로 이동했습니다"));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".이동 [X좌표] [Y좌표] [MAP ID]를 입력 해주세요."));
		}
	}

	private int _spawnId = 0;

	/** GM커맨드.고스폰 으로부터 불린다. 지정한 spawnid의 좌표에 이동한다. */
	private void tospawn(L1PcInstance gm, String param) {
		try {
			if (param.isEmpty() || param.equals("+")) {
				_spawnId++;
			} else if (param.equals("-")) {
				_spawnId--;
			} else {
				StringTokenizer st = new StringTokenizer(param);
				_spawnId = Integer.parseInt(st.nextToken());
			}
			L1Spawn spawn = NpcSpawnTable.getInstance().getTemplate(_spawnId);
			if (spawn == null) {
				spawn = SpawnTable.getInstance().getTemplate(_spawnId);
			}
			if (spawn != null) {
				gm.teleport(spawn.getLocX(), spawn.getLocY(), spawn.getMapId(), gm.getHeading(),169, false, false);
				gm.sendPackets(new S_SystemMessage("spawnid(" + _spawnId + ")의 위치로 이동 합니다."));
			} else {
				gm.sendPackets(new S_SystemMessage("spawnid(" + _spawnId + ")은(는) 발견되지 않았습니다."));
			}
		} catch (Exception exception) {
			gm.sendPackets(new S_SystemMessage("에러!  사용법:.고스폰 [spawnlist_npc 테이블의 id] [+,-]"));
		}
	}

	private void makeItemSet(L1PcInstance gm, String param) {
		try {
			String name = new StringTokenizer(param).nextToken();
			List<ItemSetItem> list = GMCommandsConfig.ITEM_SETS.get(name);
			if (list == null) {
				gm.sendPackets(new S_SystemMessage(name + " 은(는) 미등록된 세트 입니다."));
				return;
			}
			for (ItemSetItem item : list) {
				L1Item temp = ItemTable.getInstance().getTemplate(item.getId());
				if (!temp.isStackable() && 0 != item.getEnchant()) {
					for (int i = 0; i < item.getAmount(); i++) {
						ItemInstance inst = ItemTable.getInstance().createItem(item.getId());
						inst.setEnchantLevel(item.getEnchant());
						gm.getInventory().storeItem(inst);
					}
				} else {
					gm.getInventory().storeItem(item.getId(), item.getAmount());
				}
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".세트아이템 [세트명]을 입력 해주세요."));
		}
	}

	
 
	private void kill(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);

			if (target != null) {
				target.setCurrentHp(0);
				target.death(null);
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".킬 [캐릭터명]을 입력 해주세요."));
		}
	}
 

	private void kick(L1PcInstance gm, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);

			if (target != null) {
				gm.sendPackets(new S_SystemMessage((new StringBuilder()).append(target.getName()).append("님을 추방 했습니다.").toString()));
				target.sendPackets(new S_Disconnect());
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".추방 [캐릭터명]을 입력 해주세요."));
		}
	}

	private void skick(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);
			if (target != null) {
				gm.sendPackets(new S_SystemMessage(
						(new StringBuilder()).append(target.getName()).append("님을 추방 했습니다.").toString()));
				// SKT에 이동시킨다
				target.setX(33080);
				target.setY(33392);
				target.setMap((short) 4);
				target.sendPackets(new S_Disconnect());
				GameClient targetClient = target.getNetConnection();
				targetClient.kick();
				_log.warning("GM의 skick 커맨드에 의해(" + targetClient.getAccountName() + ":" + targetClient.getHostname()
						+ ")와의 접속을 강제 절단 했습니다.");
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".skick [캐릭터명]을 입력 해주세요."));
		}
	}

	private void powerkick(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);

			IpTable iptable = IpTable.getInstance();
			if (target != null) {
				iptable.banIp(target.getNetConnection().getIp()); // BAN 리스트에 IP를 더한다
				gm.sendPackets(new S_SystemMessage(
						(new StringBuilder()).append(target.getName()).append("님을 추방,  IP 주소를 차단 했습니다.").toString()));
				target.sendPackets(new S_Disconnect());
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".영구추방 [캐릭터명]을 입력 해주세요."));
		}
	}

	private void accbankick(L1PcInstance gm, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);

			if (target != null) {
				// 어카운트를 BAN 한다
				Account.ban(target.getAccountName());
				gm.sendPackets(new S_SystemMessage(target.getName() + "님을 추방, 계정을 압류 했습니다."));
				target.sendPackets(new S_Disconnect());
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".계정압류 [캐릭터명]을 입력 해주세요."));
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
				ItemInstance item = ItemTable.getInstance().createItem(itemid);
				if (target == null)
					continue;
				if (!target.isGhost() && !target.isPrivateShop()) {
					if (item.isStackable()) {
						item.setCount(count);
						item.setEnchantLevel(enchant);
						item.setIdentified(true);
						if (item != null) {
							if (target.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
								target.getInventory().storeItem(item);
							}
						}
						//target.sendPackets(new S_GMHtml("■전체 선물■: [" + target.getName() + "]","아이템: [" + item.getLogName() + "] 획득"));
						target.sendPackets(new S_SystemMessage("■전체 선물■: 아이템: [" + item.getLogName() + "] 획득"));
						target.sendPackets(S_SkillSound.get(target.getId(), 1091));// 비둘기액션
					} else {
						int createCount;
						for (createCount = 0; createCount < count; createCount++) {
							item = ItemTable.getInstance().createItem(itemid);
							item.setEnchantLevel(enchant);
							item.setIdentified(true);

							target.getInventory().storeItem(item);

						}
						if (createCount > 0) {
							//target.sendPackets(new S_GMHtml("■전체 선물■: [" + target.getName() + "]","아이템: [" + item.getLogName() + "] 획득"));
							target.sendPackets(new S_SystemMessage("■전체 선물■: 아이템: [" + item.getLogName() + "] 획득"));
							target.sendPackets(S_SkillSound.get(target.getId(), 1091));// 비둘기액션
						}
					}
				}
				}
		} catch (Exception exception) {
			gm.sendPackets(new S_SystemMessage(".전체선물 [템ID] [인첸] [갯수]"));
		}
	}
	

	private void present1(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			String nameid = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(name);
			boolean worldpc = true;

			if (target == null) {
				worldpc = false;
				target = CharacterTable.getInstance().restoreCharacter(name);
				if (target == null) {
					pc.sendPackets(new S_SystemMessage("존재하지 않는 캐릭터입니다."));
					return;
				}
				CharacterTable.getInstance().restoreInventory(target);
			}

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
			int bless = 0;
			if (st.hasMoreTokens()) {
				bless = Integer.parseInt(st.nextToken());
			}
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					pc.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않았습니다."));
					return;
				}
			}
			L1Item temp = ItemTable.getInstance().getTemplate(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					for (L1PcInstance _targetpc : L1World.getInstance().getAllPlayers()) {
						ItemInstance item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(0);
						item.setCount(count); 
						PresentWarehouse pwh = WarehouseManager.getInstance().getPresentWarehouse(target.getAccountName());

						pwh.storeTradeItem(item);
						_targetpc.sendPackets(S_SkillSound.get(_targetpc.getId(), 4856));

					}
					pc.sendPackets(new S_SystemMessage("\\aD " + temp.getName() + " 보냄"));
				} else {
					ItemInstance item = null;
					int createCount;
					for (L1PcInstance _targetpc : L1World.getInstance().getAllPlayers()) {
						for (createCount = 0; createCount < count; createCount++) {
							item = ItemTable.getInstance().createItem(itemid);
							item.setEnchantLevel(enchant);
							item.setAttrEnchantLevel(Attrenchant);
							if (bless == 129) {
								item.setBless(bless);
							}
							PresentWarehouse pwh = WarehouseManager.getInstance().getPresentWarehouse(target.getAccountName());
 

							pwh.storeTradeItem(item);
							_targetpc.sendPackets(S_SkillSound.get(_targetpc.getId(), 4856)); 
							target.sendPackets(new S_SystemMessage("부가 아이템 창고에 선물이 도착했습니다."));
						}
					}
					pc.sendPackets(new S_SystemMessage("\\aD " + temp.getName() + " 보냄"));
				}
			} else {
				pc.sendPackets(new S_SystemMessage("지정 ID의 아이템은 존재하지 않습니다."));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".[부가개인선물] [아디] [아이템ID] [갯수] [인챈] [속성] [봉인129]"));
		}
	}

	private void allPresent2(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			L1PcInstance pc = gm;
			StringTokenizer st = new StringTokenizer(param);
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
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					pc.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않았습니다."));
					return;
				}
			}
			L1Item temp = ItemTable.getInstance().getTemplate(itemid);
			if (temp != null) {
				for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
					if (target.getNetConnection() == null) {
						continue;
					}
					PresentGive(temp, itemid, count, enchant, target.getAccountName(), target);
					target.saveInventory();
					
				}
				pc.sendPackets(new S_SystemMessage(temp.getNameId() + "를 " + count + " 개 선물 했습니다. ", true));
			} else {
				pc.sendPackets(new S_SystemMessage("지정 ID의 아이템은 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".부가선물" + " [아이템ID] [갯수] [인첸트]로 입력해 주세요."));
		}
	}
 
	
	private void PresentGive(L1Item temp, int itemid, int count, int enchant, String accountname, L1PcInstance target) {
		PresentWarehouse pwh = WarehouseManager.getInstance().getPresentWarehouse(target.getAccountName());
		if (temp.isStackable()) {
			ItemInstance item = ItemTable.getInstance().createItem(itemid);
			item.setEnchantLevel(0);
			item.setCount(count);

			pwh.storeTradeItem(item);
			if (target != null) 
				target.sendPackets(new S_SystemMessage("부가 아이템 창고에 선물이 도착했습니다."));
		} else {
			ItemInstance item = null;
			int createCount;
			for (createCount = 0; createCount < count; createCount++) {
				item = ItemTable.getInstance().createItem(itemid);
				item.setEnchantLevel(enchant);
				pwh.storeTradeItem(item);
			}
			if (createCount > 0) {
				if (target != null)
					target.sendPackets(new S_SystemMessage("부가 아이템 창고에 선물이 도착했습니다."));
			}
		}
	}

	private void burf(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			int sprid = Integer.parseInt(stringtokenizer.nextToken());

			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				S_SkillSound s = S_SkillSound.get(pc.getId(), sprid);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".스킬보기 [castgfx]를 입력 해주세요."));
		}

	}

	private void buff(L1PcInstance gm, String args, boolean buffMe) {
		try {
			StringTokenizer tok = new StringTokenizer(args);
			int skillId = Integer.parseInt(tok.nextToken());
			int time = 0;
			if (tok.hasMoreTokens()) {
				time = Integer.parseInt(tok.nextToken());
			}

			L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);

			ArrayList<L1PcInstance> players = new ArrayList<L1PcInstance>();
			if (buffMe) {
				players.add(gm);
			} else {
				players = L1World.getInstance().getVisiblePlayer(gm);
			}
			if (skill.getTarget().equals("buff")) {
				for (L1PcInstance pc : players) {
					new SkillUse().handleCommands(gm, skillId, pc.getId(), pc.getX(), pc.getY(), null, time,
							SkillUse.TYPE_SPELLSC);
				}
			} else if (skill.getTarget().equals("none")) {
				for (L1PcInstance pc : players) {
					new SkillUse().handleCommands(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, time,
							SkillUse.TYPE_GMBUFF);
				}
			} else {
				gm.sendPackets(new S_SystemMessage("버프계의 스킬이 아닙니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".버프 또는 .버프미 [skillId] [지속시간(초)]을 입력 해주세요."));
		}
	}

	private void allBuff(L1PcInstance gm, String args) {
		int[] allBuffSkill = { SkillId.디크리즈_웨이트, SkillId.인챈트_덱스터리티, SkillId.메디테이션, SkillId.인챈트_마이티,
				SkillId.블레스_웨폰, SkillId.버서커스, SkillId.이뮨_투함, SkillId.어드밴스_스피릿, SkillId.리덕션_아머, SkillId.바운스_어택,
				SkillId.솔리드_캐리지, SkillId.인챈트_베놈, SkillId.버닝_스피릿츠, SkillId.베놈_레지스트, SkillId.더블_브레이크, SkillId.언케니_닷지, SkillId.드레스_이베이젼,
				SkillId.글루잉_오라, SkillId.브레이브_오라, SkillId.레지스트_매직, SkillId.클리어_마인드, SkillId.프로텍션_프롬_엘리멘탈, SkillId.아쿠아_프로텍트,
				SkillId.버닝_웨폰, SkillId.아이언_스킨, SkillId.엑조틱_바이탈라이즈, SkillId.워터_라이프, SkillId.엘리멘탈_파이어, SkillId.소울_오브_프레임,
				SkillId.어디셔널_파이어 };
		try {
			StringTokenizer st = new StringTokenizer(args);
			String name = st.nextToken();
			L1PcInstance pc = L1World.getInstance().getPlayer(name);
			if (pc == null) {
				gm.sendPackets(new S_ServerMessage(73, name)); // \f1 % 0은 게임을하지 않습니다.
				return;
			}

			for (int i = 0; i < allBuffSkill.length; i++) {
				L1Skills skill = SkillsTable.getInstance().getTemplate(allBuffSkill[i]);
				new SkillUse().handleCommands(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null,
						skill.getBuffDuration() * 1000, SkillUse.TYPE_GMBUFF);
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".올버프 캐릭터이름을 입력하세요"));
		}
	}

	private void mobspawn(L1PcInstance gm, int i, int randomrange, boolean isPineWand) {
		try {
			L1Npc l1npc = NpcTable.getInstance().getTemplate(i);
			if (l1npc != null) {
	
				try {
					String s = l1npc.getImpl();
					Constructor<?> constructor = Class.forName("l1j.server.server.model.Instance." + s + "Instance")
							.getConstructors()[0];
					Object aobj[] = { l1npc };
					L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(aobj);
					npc.setId(IdFactory.getInstance().nextId());
					npc.setMap(gm.getMapId());
					if (randomrange == 0) {
						if (gm.getHeading() == 0) {
							npc.setX(gm.getX());
							npc.setY(gm.getY() - 1);
						} else if (gm.getHeading() == 1) {
							npc.setX(gm.getX() + 1);
							npc.setY(gm.getY() - 1);
						} else if (gm.getHeading() == 2) {
							npc.setX(gm.getX() + 1);
							npc.setY(gm.getY());
						} else if (gm.getHeading() == 3) {
							npc.setX(gm.getX() + 1);
							npc.setY(gm.getY() + 1);
						} else if (gm.getHeading() == 4) {
							npc.setX(gm.getX());
							npc.setY(gm.getY() + 1);
						} else if (gm.getHeading() == 5) {
							npc.setX(gm.getX() - 1);
							npc.setY(gm.getY() + 1);
						} else if (gm.getHeading() == 6) {
							npc.setX(gm.getX() - 1);
							npc.setY(gm.getY());
						} else if (gm.getHeading() == 7) {
							npc.setX(gm.getX() - 1);
							npc.setY(gm.getY() - 1);
						}
					} else {
						int tryCount = 0;
						do {
							tryCount++;
							npc.setX(gm.getX() + (int) (Math.random() * randomrange)
									- (int) (Math.random() * randomrange));
							npc.setY(gm.getY() + (int) (Math.random() * randomrange)
									- (int) (Math.random() * randomrange));
							if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
								break;
							}
							Thread.sleep(1);
						} while (tryCount < 50);

						if (tryCount >= 50) {
							if (gm.getHeading() == 0) {
								npc.setX(gm.getX());
								npc.setY(gm.getY() - 1);
							} else if (gm.getHeading() == 1) {
								npc.setX(gm.getX() + 1);
								npc.setY(gm.getY() - 1);
							} else if (gm.getHeading() == 2) {
								npc.setX(gm.getX() + 1);
								npc.setY(gm.getY());
							} else if (gm.getHeading() == 3) {
								npc.setX(gm.getX() + 1);
								npc.setY(gm.getY() + 1);
							} else if (gm.getHeading() == 4) {
								npc.setX(gm.getX());
								npc.setY(gm.getY() + 1);
							} else if (gm.getHeading() == 5) {
								npc.setX(gm.getX() - 1);
								npc.setY(gm.getY() + 1);
							} else if (gm.getHeading() == 6) {
								npc.setX(gm.getX() - 1);
								npc.setY(gm.getY());
							} else if (gm.getHeading() == 7) {
								npc.setX(gm.getX() - 1);
								npc.setY(gm.getY() - 1);
							}
						}
					}

					npc.setHomeX(npc.getX());
					npc.setHomeY(npc.getY());
					npc.setHeading(gm.getHeading());

					L1World.getInstance().storeObject(npc);
					L1World.getInstance().addVisibleObject(npc);
					if (spawnTF == true) {
						L1Object object = L1World.getInstance().findObject(npc.getId());
						L1NpcInstance newnpc = (L1NpcInstance) object;
						newnpc.onNpcAI();
					}
					if (isPineWand) {
						NpcDeleteTimer timer = new NpcDeleteTimer(npc, 300000);
						timer.begin();
					}
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		} catch (Exception exception) {
		}
	}

	public void mobspawn(GameClient client, int i, int randomrange, boolean isPineWand) {
		mobspawn(client.getActiveChar(), i, randomrange, isPineWand);
	}

	private void gfxId(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int gfxid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			for (int i = 0; i < count; i++) {
				L1Npc l1npc = NpcTable.getInstance().getTemplate(45001);
				if (l1npc != null) {
					String s = l1npc.getImpl();
					Constructor<?> constructor = Class.forName("l1j.server.server.model.Instance." + s + "Instance")
							.getConstructors()[0];
					Object aobj[] = { l1npc };
					L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(aobj);
					npc.setId(IdFactory.getInstance().nextId());
					npc.setCurrentSprite(gfxid + i);
					npc.setCurrentSprite(0);
					npc.setNameId("");
					npc.setMap(gm.getMapId());
					npc.setX(gm.getX() + i * 2);
					npc.setY(gm.getY() + i * 2);
					npc.setHomeX(npc.getX());
					npc.setHomeY(npc.getY());
					npc.setHeading(4);

					L1World.getInstance().storeObject(npc);
					L1World.getInstance().addVisibleObject(npc);
				}
			}
		} catch (Exception exception) {
			gm.sendPackets(new S_SystemMessage(".엔피씨그래픽 [gfxid] [출현수]를 입력 해주세요."));
		}
	}
 

	private void action(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int actId = Integer.parseInt(st.nextToken(), 10);
			pc.sendPackets(S_DoActionGFX.get(pc.getId(), actId));
		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage(".액션 [actid]를 입력 해주세요."));
		}
	}

	private void banIp(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			// IP를 지정
			String s1 = stringtokenizer.nextToken();

			// add/del를 지정(하지 않아도 OK)
			String s2 = null;
			try {
				s2 = stringtokenizer.nextToken();
			} catch (Exception e) {
			}

			IpTable iptable = IpTable.getInstance();
			boolean isBanned = iptable.isBannedIp(s1);

			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (s1.equals(pc.getNetConnection().getIp())) {
					String msg = new StringBuilder().append("IP:").append(s1).append(" 로 접속중의 플레이어:")
							.append(pc.getName()).toString();
					gm.sendPackets(new S_SystemMessage(msg));
				}
			}

			if ("추가".equals(s2) && !isBanned) {
				iptable.banIp(s1); // BAN 리스트에 IP를 더한다
				String msg = new StringBuilder().append("IP:").append(s1).append(" 을(를) BAN IP에 등록했습니다.").toString();
				gm.sendPackets(new S_SystemMessage(msg));
			} else if ("삭제".equals(s2) && isBanned) {
				if (iptable.liftBanIp(s1)) { // BAN 리스트로부터 IP를 삭제한다
					String msg = new StringBuilder().append("IP:").append(s1).append(" 을(를) BAN IP로부터 삭제했습니다.")
							.toString();
					gm.sendPackets(new S_SystemMessage(msg));
				}
			} else {
				// BAN의 확인
				if (isBanned) {
					String msg = new StringBuilder().append("IP:").append(s1).append(" 은(는) BAN IP에 등록되어 있습니다.")
							.toString();
					gm.sendPackets(new S_SystemMessage(msg));
				} else {
					String msg = new StringBuilder().append("IP:").append(s1).append(" 은(는) BAN IP에 등록되어 있지 않습니다.")
							.toString();
					gm.sendPackets(new S_SystemMessage(msg));
				}
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".밴아이피 [IP 주소] [추가 또는 삭제]라고 입력 해주세요."));
		}
	}
 

	private void patrol(L1PcInstance gm) {
		gm.sendPackets(new S_PacketBox(S_PacketBox.CALL_SOMETHING));
	}
 

	public static boolean isHpBarTarget(L1Object obj) {
		if (obj instanceof MonsterInstance) {
			return true;
		}
		if (obj instanceof L1PcInstance) {
			return true;
		}
		if (obj instanceof SummonInstance) {
			return true;
		}
		if (obj instanceof PetInstance) {
			return true;
		}
		if(obj instanceof L1TowerInstance) {
			return true;
		}
		return false;
	}

	private void hpBar(L1PcInstance gm, String param) {
		if (param.equalsIgnoreCase("켬")) {
			gm.setSkillEffect(SkillId.GMSTATUS_HPBAR, 0);
			gm.sendPackets(new S_SystemMessage("HP 게이지를 볼 수 있습니다."));
		} else if (param.equalsIgnoreCase("끔")) {
			gm.removeSkillEffect(SkillId.GMSTATUS_HPBAR);
			gm.sendPackets(new S_SystemMessage("HP 게이지를 볼 수 없습니다."));

			for (L1Object obj : gm.getKnownObjects()) {
				gm.sendPackets(new S_HPMeter(obj.getId(), 0xFF));
			}
		} else {
			gm.sendPackets(new S_SystemMessage(".피바 [켬 또는 끔] 이라고 입력 해주세요."));
		}
	}

	private void reloadTraps() {
		L1WorldTraps.reloadTraps();
	}

	private void showTraps(L1PcInstance gm, String param) {
		if (param.equalsIgnoreCase("켬")) {
			gm.setSkillEffect(SkillId.GMSTATUS_SHOWTRAPS, 0);
			gm.sendPackets(new S_SystemMessage("트랩을 볼 수 있습니다."));
		} else if (param.equalsIgnoreCase("끔")) {
			gm.removeSkillEffect(SkillId.GMSTATUS_SHOWTRAPS);
			gm.sendPackets(new S_SystemMessage("트랩을 볼 수 없습니다."));

			for (L1Object obj : gm.getKnownObjects()) {
				if (obj instanceof L1TrapInstance) {
					gm.removeKnownObject(obj);
					gm.sendPackets(S_RemoveObject.get(obj), true);
				}
			}
		} else {
			gm.sendPackets(new S_SystemMessage(".쇼트랩 [켬 또는 끔] 이라고 입력 해주세요."));
		}
	}

	private String _lastCmd = "";

	private void redo(L1PcInstance gm, String param) {
		try {
			if (_lastCmd.isEmpty()) {
				gm.sendPackets(new S_SystemMessage("기억되어 있는 커맨드가 없습니다."));
				return;
			}
			if (param.isEmpty()) {
				gm.sendPackets(new S_SystemMessage("커맨드 " + _lastCmd + " 을(를) 재실행합니다."));
				handleCommands(gm, _lastCmd);
			} else {
				// 인수를 바꾸어 실행
				StringTokenizer token = new StringTokenizer(_lastCmd);
				String cmd = token.nextToken() + " " + param;
				gm.sendPackets(new S_SystemMessage("커맨드 " + cmd + " 을(를) 재실행합니다."));
				handleCommands(gm, cmd);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			gm.sendPackets(new S_SystemMessage(".재실행 커맨드 에러"));
		}
	}

	private String _faviCom = "";

	private void favorite(L1PcInstance gm, String param) {
		try {
			if (param.startsWith("추가")) {
				// 커맨드의 등록
				StringTokenizer st = new StringTokenizer(param);
				st.nextToken();
				if (!st.hasMoreTokens()) {
					gm.sendPackets(new S_SystemMessage("커맨드를 입력하지 않았습니다."));
					return;
				}
				StringBuilder cmd = new StringBuilder();
				String temp = st.nextToken(); // 커멘드 타입
				if (temp.equalsIgnoreCase("실행")) {
					gm.sendPackets(new S_SystemMessage("실행은 등록 할 수 없습니다."));
					return;
				}
				cmd.append(temp + " ");
				while (st.hasMoreTokens()) {
					cmd.append(st.nextToken() + " ");
				}
				_faviCom = cmd.toString().trim();
				gm.sendPackets(new S_SystemMessage(_faviCom + " 을(를) 등록했습니다."));
			} else if (param.startsWith("보기")) {
				gm.sendPackets(new S_SystemMessage("현재의 등록 커맨드 : " + _faviCom));
			} else if (_faviCom.isEmpty()) {
				gm.sendPackets(new S_SystemMessage("등록되어 있는 커맨드가 없습니다."));
			} else {
				StringBuilder cmd = new StringBuilder();
				StringTokenizer st = new StringTokenizer(param);
				StringTokenizer st2 = new StringTokenizer(_faviCom);
				while (st2.hasMoreTokens()) {
					String temp = st2.nextToken();
					if (temp.startsWith("%")) {
						cmd.append(st.nextToken() + " ");
					} else {
						cmd.append(temp + " ");
					}
				}
				while (st.hasMoreTokens()) {
					cmd.append(st.nextToken() + " ");
				}
				gm.sendPackets(new S_SystemMessage(cmd + " 를 실행합니다."));
				handleCommands(gm, cmd.toString());
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".실행 추가 [커맨드] " + "| .실행 [보기] | .실행 [인수] 라고 입력 해주세요."));
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	private void gm(L1PcInstance gm) {
		if (gm.isGm()) {
			gm.setGm(false);
			gm.sendPackets(new S_SystemMessage("setGm = false."));
		} else {
			gm.setGm(true);
			gm.sendPackets(new S_SystemMessage("setGm = true"));
		}
	}

	private void chainfo(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			String s = stringtokenizer.nextToken();
			gm.sendPackets(new S_Chainfo(1, s));
		} catch (Exception exception21) {
			gm.sendPackets(new S_SystemMessage(".검 [캐릭터명]을 입력 해주세요."));
		}
	}
	
	public static boolean IS_PROTECTION = false;

	private void setProtectionMode(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				IS_PROTECTION = true;
				L1World.getInstance().broadcastPacketToAll(
			new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "현재 보호모드로 인해, 페널티가 적용 되지 않습니다. 안심하시고 사냥하셔도 됩니다."));
			L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("현재 보호모드로 인해, 페널티가 적용 되지 않습니다. 안심하시고 사냥하셔도 됩니다."));
			
			} else if (param.equalsIgnoreCase("끔")) {
				IS_PROTECTION = false;	
				L1World.getInstance().broadcastPacketToAll(
				new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "잠시후 보호모드가 해지 됩니다. 페널티에 주의 하시길 바랍니다."));
				L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("잠시후 보호모드가 해지 됩니다. 페널티에 주의 하시길 바랍니다."));
				
			} else if (param.equalsIgnoreCase("상태")) {

			} else
				throw new Exception("");
			gm.sendPackets(String.format("현재 보호모드 상태 [%s짐]", IS_PROTECTION ? "켜" : "꺼"));
		} catch (Exception e) {
			gm.sendPackets(".보호모드 [켬/끔/상태]");
		}
	}

//## A112 암호변경 추가 
	/* 암호 변경 소스 - 당신 */
	// 입력받은 암호의 인코딩 메소드 - Account.java 참조.
	private static String encodePassword(String rawPassword)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {
		byte buf[] = rawPassword.getBytes("UTF-8");
		buf = MessageDigest.getInstance("SHA").digest(buf);

		return Base64.encodeBytes(buf);
	}

	/* 실제 암호 변경 메소드 */
	private void to_Change_Passwd(L1PcInstance gm, L1PcInstance pc, String passwd) {
		try {
			String login = null;
			String password = null;
			java.sql.Connection con = null;
			con = L1DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null;

			password = encodePassword(passwd);

			statement = con.prepareStatement(
					"select account_name from characters where char_name Like '" + pc.getName() + "'");
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET password=? WHERE login Like '" + login + "'");
				pstm.setString(1, password);
				pstm.execute();
				gm.sendPackets(new S_SystemMessage("-암호 변경정보- Account:[" + login + "] Password:[" + passwd + "]"));
				gm.sendPackets(new S_SystemMessage(pc.getName() + "의 암호 변경이 성공적으로 완료되었습니다."));
				pc.sendPackets(new S_SystemMessage("귀하의 계정 정보가 갱신 되었습니다."));
			}
			rs.close();
			pstm.close();
			statement.close();
			con.close();
		} catch (Exception e) {
		}
	}

	/* 입력받은 암호에 한글이 포함되지 않았는지 확인해 주는 메소드 */
	/* 실제로 암호가 한글로 바뀌어버리면 클라이언트에서는 입력할 방법이 없다. */
	private static boolean isDisitAlpha(String str) {
		boolean check = true;
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i)) // 숫자가 아니라면
					&& Character.isLetterOrDigit(str.charAt(i)) // 특수문자라면
					&& !Character.isUpperCase(str.charAt(i)) // 대문자가 아니라면
					&& !Character.isLowerCase(str.charAt(i))) { // 소문자가 아니라면
				check = false;
				break;
			}
		}
		return check;
	}

	/* 암호 변경에 필요한 변수를 입력받는다. */
	private void changePassword(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (passwd.length() < 4) {
				gm.sendPackets(new S_SystemMessage("입력하신 암호의 자릿수가 너무 짧습니다."));
				gm.sendPackets(new S_SystemMessage("최소 4자 이상 입력해 주십시오."));
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets(new S_SystemMessage("입력하신 암호의 자릿수가 너무 깁니다."));
				gm.sendPackets(new S_SystemMessage("최대 12자 이하로 입력해 주십시오."));
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets(new S_SystemMessage("암호에 허용되지 않는 문자가 포함 되어 있습니다."));
				return;
			}

			L1PcInstance target = L1World.getInstance().getPlayer(user);
			if (target != null) {
				to_Change_Passwd(gm, target, passwd);
			} else {
				gm.sendPackets(new S_SystemMessage("그런 이름을 가진 캐릭터는 없습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".암호변경 [캐릭터명] [변경할 암호]를 입력 해주세요."));
		}
	}
//## A112 암호변경 추가 
	
	// 인벤삭제
	private void InventoryDelete(L1PcInstance pc, String param) {
		try {
			for (ItemInstance item : pc.getInventory().getItems()) {
				if (!item.isEquipped()) {
					pc.getInventory().removeItem(item, item.getCount());	
				}				
			}
		} catch (Exception e) {
			pc.sendPackets(".인벤삭제");
		}
	}
	
	// 인벤정리
	private void InventoryArrangement(L1PcInstance pc, String param) {
		try {
			FastTable<ItemInstance> controll = new FastTable<ItemInstance>();
			FastTable<ItemInstance> eq_weapon = new FastTable<ItemInstance>();
			FastTable<ItemInstance> eq_armor = new FastTable<ItemInstance>();
			FastTable<ItemInstance> weapon = new FastTable<ItemInstance>();
			FastTable<ItemInstance> armor = new FastTable<ItemInstance>();
			FastTable<ItemInstance> etcitem = new FastTable<ItemInstance>();
			for (ItemInstance item : pc.getInventory().getItems()) {
				if (item.getItem().getType2() == 0) {
					if (item.getItemId() == 49027)
						controll.add(item);
					else
						etcitem.add(item);
				} else if (item.getItem().getType2() == 1) {
					if (item.isEquipped())
						eq_weapon.add(item);
					else
						weapon.add(item);
				} else if (item.getItem().getType2() == 2) {
					if (item.isEquipped())
						eq_armor.add(item);
					else
						armor.add(item);
				}
				
				pc.getInventory().removeItem(item, item.getCount());
			}
			
			if (eq_weapon != null) {
				for (ItemInstance weapon_item : eq_weapon) {
					pc.getInventory().storeItem(weapon_item);
					pc.getInventory().setEquipped(weapon_item, true);
				}
			}
			
			if (eq_armor != null) {
				for (ItemInstance armor_item : eq_armor) {
					pc.getInventory().storeItem(armor_item);
					pc.getInventory().setEquipped(armor_item, true);
				}
			}
			
			if (etcitem != null) {
				for (ItemInstance etc_item : etcitem) {
					pc.getInventory().storeItem(etc_item);
				}
			}
			
			if (weapon != null) {
				for (ItemInstance weapon_item : weapon) {
					pc.getInventory().storeItem(weapon_item);
				}
			}
			
			if (armor != null) {
				for (ItemInstance armor_item : armor) {
					pc.getInventory().storeItem(armor_item);
				}
			}
			
			if (controll != null) {
				for (ItemInstance control_item : controll) {
					pc.getInventory().storeItem(control_item);
				}
			}
			
			pc.save();
			pc.saveInventory();
		} catch (Exception e) {
			pc.sendPackets(".인벤정리");
		}
	}

	private void targetInventoryDelete(L1PcInstance user, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String char_name = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(char_name);
			for (ItemInstance item : user.getInventory().getItems()) {
				if (!item.isEquipped()) {
					target.getInventory().removeItem(item, item.getCount());
				}
			}
		} catch (Exception e) {
			user.sendPackets(".유저인벤삭제 [접속중인 캐릭명] 입력.");
		}
	}
	
	private FastTable<L1NpcInstance> list = new FastTable<L1NpcInstance>();

	public void add_list(L1NpcInstance npc) {
		synchronized (list) {
			if (!list.contains(npc))
				list.add(npc);
		}
	}
	
	private void spawn_images(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int gfxid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			int loc_x = pc.getX();
			int loc_y = pc.getY();
			
			for (int i = 0; i < count; i++) {
				L1Npc l1npc = NpcTable.getInstance().getTemplate(45001);
				if (l1npc != null) {
					String s = l1npc.getImpl();
					Constructor<?> constructor = Class.forName("l1j.server.server.model.Instance." + s + "Instance").getConstructors()[0];
					Object aobj[] = { l1npc };
					L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(aobj);
					npc.setId(IdFactory.getInstance().nextId());
					npc.setCurrentSprite(gfxid + i);
					npc.setNameId("" + (gfxid + i) + "");
					npc.setMap(pc.getMapId());
					
					int k = i % 5;
					if (k == 0 && i > 0) {
						loc_x -= 4;
						loc_y += 4;
					}
					npc.setX(loc_x + k * 2);
					npc.setY(loc_y + k * 2);
					
					npc.setHomeX(npc.getX());
					npc.setHomeY(npc.getY());
					npc.setHeading(5);
					npc.setStatus(0);
					L1World.getInstance().storeObject(npc);
					L1World.getInstance().addVisibleObject(npc);
					
					GMCommands.getInstance().add_list(npc);
				}
			}
		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage("이미지" + " [id] [출현시키는 수]로 입력해 주세요. "));
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
	
	private void maphack(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				gm.sendPackets(new S_Ability(3, true));
				gm.sendPackets("라이트 : ON");
			} else if (on.equals("끔")) {
				gm.sendPackets(new S_Ability(3, false));
				gm.sendPackets("라이트 : OFF");
			}
		} catch (Exception e) {
			gm.sendPackets(".라이트 [켬,끔]으로 설정 하세요.");
		}
	}
	
	private void effect(L1PcInstance pc, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			int sprid = Integer.parseInt(stringtokenizer.nextToken());
			S_SkillSound s = S_SkillSound.get(pc.getId(), sprid);
			pc.sendPackets(s);
			pc.broadcastPacket(s);
		} catch (Exception e) {
			pc.sendPackets(".이팩트 [숫자] 라고 입력해 주세요.");
		}
	}
	

	public static void LevelUpPresentSystem(L1PcInstance gm, String param){
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);		
			String first_code = tokenizer.nextToken();
			if (first_code.equalsIgnoreCase("리로드")) {
				LevelUpPresentSystemLoader.getInstance().reload();
				gm.sendPackets("레벨보상 아이템 목록을 리로드 합니다.");
			} else if (first_code.equalsIgnoreCase("초기화")) {
				String second_code = tokenizer.nextToken();
				if (second_code.equalsIgnoreCase("계정")) {
					LevelUpPresentSystemAccountTable.reload();
					gm.sendPackets("레벨보상 아이템에 대한 모든 보상내역을 초기화 합니다. (계정)");
				} else if (second_code.equalsIgnoreCase("캐릭터")) {
					LevelUpPresentSystemCharacterTable.reload();
					gm.sendPackets("레벨보상 아이템에 대한 모든 보상내역을 초기화 합니다. (캐릭터)");
				} else {
					gm.sendPackets(".레벨보상 초기화 계정/캐릭터");
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".레벨보상 [리로드/초기화 (계정/캐릭터)]");
		}
	}
	

	
	public void showObjectList(L1PcInstance gm, String param) {
		StringBuilder sb = new StringBuilder(256);
		for (L1Object obj : gm.getKnownObjects()) {
			if (obj instanceof L1NpcInstance) {
				L1NpcInstance c = (L1NpcInstance) obj;
				sb.append(c.getName() + "("+c.getNpcId()+")").append(" ").append(obj.getX()).append(" ").append(obj.getY()).append("\n");
			}
			
			if (obj instanceof L1Character) {
				L1Character c = (L1Character) obj;
				sb.append(c.getName()).append(" ").append(obj.getX()).append(" ").append(obj.getY()).append("\n");
			}
		}

		gm.sendPackets(sb.toString());
	}
	
	private void autoloot(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String type = tok.nextToken();
			if (type.equalsIgnoreCase("리로드")) {
				AutoLoot.getInstance().reload();
				gm.sendPackets(new S_SystemMessage("오토루팅 설정이 리로드 되었습니다."));
			} else if (type.equalsIgnoreCase("검색")) {
				java.sql.Connection con = null;
				PreparedStatement pstm = null;
				ResultSet rs = null;

				String nameid = tok.nextToken();
				try {
					con = L1DatabaseFactory.getInstance().getConnection();
					String strQry;
					strQry = " Select e.item_id, e.name from etcitem e, autoloot l where l.item_id = e.item_id and name Like '%"
							+ nameid + "%' ";
					strQry += " union all "
							+ " Select w.item_id, w.name from weapon w, autoloot l where l.item_id = w.item_id and name Like '%"
							+ nameid + "%' ";
					strQry += " union all "
							+ " Select a.item_id, a.name from armor a, autoloot l where l.item_id = a.item_id and name Like '%"
							+ nameid + "%' ";
					pstm = con.prepareStatement(strQry);
					rs = pstm.executeQuery();
					while (rs.next()) {
						gm.sendPackets(new S_SystemMessage("[" + rs.getString("item_id") + "] " + rs.getString("name")));
					}
				} catch (Exception e) {
				} finally {
					rs.close();
					pstm.close();
					con.close();
				}
			} else {
				String nameid = tok.nextToken();
				int itemid = 0;
				try {
					itemid = Integer.parseInt(nameid);
				} catch (NumberFormatException e) {
					itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
					if (itemid == 0) {
						gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
						return;
					}
				}

				L1Item temp = ItemTable.getInstance().getTemplate(itemid);
				if (temp == null) {
					gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
					return;
				}
				if (type.equalsIgnoreCase("추가")) {
					if (AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets(new S_SystemMessage("이미 오토루팅 목록에 있습니다."));
						return;
					}
					AutoLoot.getInstance().storeId(itemid);
					gm.sendPackets(new S_SystemMessage("오토루팅 항목에 추가 했습니다."));
				} else if (type.equalsIgnoreCase("삭제")) {
					if (!AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets(new S_SystemMessage("오토루팅 항목에 해당 아이템이 없습니다."));
						return;
					}
					gm.sendPackets(new S_SystemMessage("오토루팅 항목에서 삭제 했습니다."));
					AutoLoot.getInstance().deleteId(itemid);
				}
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".오토루팅 리로드"));
			gm.sendPackets(new S_SystemMessage(".오토루팅 추가|삭제 itemid|name"));
			gm.sendPackets(new S_SystemMessage(".오토루팅 검색 name"));
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
	
	private void addaccount(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (user.length() < 4) {
				gm.sendPackets(new S_SystemMessage("입력하신 계정명의 자릿수가 너무 짧습니다."));
				gm.sendPackets(new S_SystemMessage("최소 4자 이상 입력해 주십시오."));
				return;
			}
			if (passwd.length() < 4) {
				gm.sendPackets(new S_SystemMessage("입력하신 암호의 자릿수가 너무 짧습니다."));
				gm.sendPackets(new S_SystemMessage("최소 4자 이상 입력해 주십시오."));
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets(new S_SystemMessage("입력하신 암호의 자릿수가 너무 깁니다."));
				gm.sendPackets(new S_SystemMessage("최대 12자 이하로 입력해 주십시오."));
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets(new S_SystemMessage("암호에 허용되지 않는 문자가 포함 되어 있습니다."));
				return;
			}
			AddAccount(gm, user, passwd, "127.0.0.1", "127.0.0.1");
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".계정추가 [계정명] [암호] 를 입력 해주세요."));
		}
	}
	
	private void AddAccount(L1PcInstance gm, String account, String passwd, String Ip, String Host) {
		try {
			String login = null;
			String password = null;
			java.sql.Connection con = null;
			con = L1DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			PreparedStatement pstm = null;

			password = passwd;

			statement = con.prepareStatement("select * from accounts where login Like '" + account + "'");
			ResultSet rs = statement.executeQuery();

			if (rs.next())
				login = rs.getString(1);
			if (login != null) {
				gm.sendPackets(new S_SystemMessage("이미 계정이 있습니다."));
				return;
			} else {
				String sqlstr = "INSERT INTO accounts SET login=?,password=?,lastactive=?,access_level=?,ip=?,host=?,banned=?,charslot=?,mjct_charname=?";
				pstm = con.prepareStatement(sqlstr);
				pstm.setString(1, account);
				pstm.setString(2, password);
				pstm.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
				pstm.setInt(4, 0);
				pstm.setString(5, Ip);
				pstm.setString(6, Host);
				pstm.setInt(7, 0);
				pstm.setInt(8, 6);
				pstm.setString(9, "");
				pstm.execute();
				gm.sendPackets(new S_SystemMessage("계정 추가가 완료되었습니다."));
			}

			rs.close();
			pstm.close();
			statement.close();
			con.close();
		} catch (Exception e) {
		}
	}
	
	public static void DogFightSystem(L1PcInstance gm, String param){
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String Chat = tokenizer.nextToken();
			if (Chat.equalsIgnoreCase("리로드")) {
				DogFightSystemSettings.DogfightInfoLoad();
				DogFightCharacters.do_load();
				gm.sendPackets("투견 시스템 정보를 리로드 합니다.");
			} else if (Chat.equalsIgnoreCase("켬")) {
				DogFigthSystemLoader.getInstance().do_run_game();
				gm.sendPackets("투견 시스템을 작동합니다.");
			} else if (Chat.equalsIgnoreCase("끔")) {
				DogFigthSystemLoader.getInstance().close_game();
				gm.sendPackets("투견 시스템을 종료 합니다.(안전을 위해 이번판 까지만 작동하고 종료됩니다.)");
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".투견 [리로드/켬/끔]"));
		}
	} 
	private void NoShopAndWare(L1PcInstance gm, String param) {
		 try {
		  StringTokenizer tok = new StringTokenizer(param);
		  String type = tok.nextToken();
		  if (type.equalsIgnoreCase("리로드")) {
		   NoShopAndWare.getInstance().reload();
		   gm.sendPackets(new S_SystemMessage("창고불가 설정이 리로드 되었습니다."));
		  } else if (type.equalsIgnoreCase("검색")) {
		   java.sql.Connection con = null;
		   PreparedStatement pstm = null;
		   ResultSet rs = null;
		   String nameid = tok.nextToken();
		   try {
		    con = L1DatabaseFactory.getInstance().getConnection();
		    String strQry;
		    strQry = " Select e.item_id, e.name from etcitem e, NoShopAndWare l where l.item_id = e.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select w.item_id, w.name from weapon w, NoShopAndWare l where l.item_id = w.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select a.item_id, a.name from armor a, NoShopAndWare l where l.item_id = a.item_id and name Like '%" + nameid + "%' ";
		    pstm = con.prepareStatement(strQry);
		    rs = pstm.executeQuery();
		    while (rs.next()) {
		     gm.sendPackets(new S_SystemMessage("[" + rs.getString("item_id") + "] " + rs.getString("name")));
		    }
		   } catch (Exception e) {
		   } finally {
		    rs.close();
		    pstm.close();
		    con.close();
		   }
		  } else {
		   String nameid = tok.nextToken();
		   int itemid = 0;
		   try {
		    itemid = Integer.parseInt(nameid);
		   } catch (NumberFormatException e) {
		    itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
		    if (itemid == 0) {
		     gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		     return;
		    }
		   }
		   L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		   if (temp == null) {
		    gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		    return;
		   }
		   if (type.equalsIgnoreCase("추가")) {
		    if (NoShopAndWare.getInstance().isNoShopAndWare(itemid)) {
		     gm.sendPackets(new S_SystemMessage("이미 창고불가 목록에 있습니다."));
		     return;
		    }
		    NoShopAndWare.getInstance().storeId(itemid);
		    gm.sendPackets(new S_SystemMessage("창고불가 항목에 추가 했습니다."));
		   } else if (type.equalsIgnoreCase("삭제")) {
		    if (!NoShopAndWare.getInstance().isNoShopAndWare(itemid)) {
		     gm.sendPackets(new S_SystemMessage("창고불가 항목에 해당 아이템이 없습니다."));
		     return;
		    }
		    gm.sendPackets(new S_SystemMessage("창고불가 항목에서 삭제 했습니다."));
		    NoShopAndWare.getInstance().deleteId(itemid);
		   }
		  }
		 } catch (Exception e) {
		  gm.sendPackets(new S_SystemMessage(".창고불가 리로드"));
		  gm.sendPackets(new S_SystemMessage(".창고불가 추가|삭제 itemid|name"));

		  gm.sendPackets(new S_SystemMessage(".창고불가 검색 name"));
		 }
		}
	private void NoDropItem(L1PcInstance gm, String param){
		 try {
		  StringTokenizer tok = new StringTokenizer(param);
		  String type = tok.nextToken();
		  if (type.equalsIgnoreCase("리로드")) {
		   NoDropItem.getInstance().reload();
		   gm.sendPackets(new S_SystemMessage("드랍불가 설정이 리로드 되었습니다."));
		  } else if (type.equalsIgnoreCase("검색")) {
		   java.sql.Connection con = null;
		   PreparedStatement pstm = null;
		   ResultSet rs = null;
		   String nameid = tok.nextToken();
		   try {
		    con = L1DatabaseFactory.getInstance().getConnection();
		    String strQry;
		    strQry = " Select e.item_id, e.name from etcitem e, NoDropItem l where l.item_id = e.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select w.item_id, w.name from weapon w, NoDropItem l where l.item_id = w.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select a.item_id, a.name from armor a, NoDropItem l where l.item_id = a.item_id and name Like '%" + nameid + "%' ";
		    pstm = con.prepareStatement(strQry);
		    rs = pstm.executeQuery();
		    while (rs.next()) {
		     gm.sendPackets(new S_SystemMessage("[" + rs.getString("item_id") + "] " + rs.getString("name")));
		    }
		   } catch (Exception e) {
		   } finally {
		    rs.close();
		    pstm.close();
		    con.close();
		   }
		  } else {
		   String nameid = tok.nextToken();
		   int itemid = 0;
		   try {
		    itemid = Integer.parseInt(nameid);
		   } catch (NumberFormatException e) {
		    itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
		    if (itemid == 0) {
		     gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		     return;
		    }
		   }
		   L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		   if (temp == null) {
		    gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		    return;
		   }
		   if (type.equalsIgnoreCase("추가")) {
		    if (NoDropItem.getInstance().isNoDropItem(itemid)) {
		     gm.sendPackets(new S_SystemMessage("이미 드랍불가 목록에 있습니다."));
		     return;
		    }
		    NoDropItem.getInstance().storeId(itemid);
		    gm.sendPackets(new S_SystemMessage("드랍불가 항목에 추가 했습니다."));
		   } else if (type.equalsIgnoreCase("삭제")) {
		    if (!NoDropItem.getInstance().isNoDropItem(itemid)) {
		     gm.sendPackets(new S_SystemMessage("드랍불가 항목에 해당 아이템이 없습니다."));
		     return;
		    }
		    gm.sendPackets(new S_SystemMessage("드랍불가 항목에서 삭제 했습니다."));
		    NoDropItem.getInstance().deleteId(itemid);
		   }
		  }
		 } catch (Exception e) {
		  gm.sendPackets(new S_SystemMessage(".드랍불가 리로드"));
		  gm.sendPackets(new S_SystemMessage(".드랍불가 추가|삭제 itemid|name"));

		  gm.sendPackets(new S_SystemMessage(".드랍불가 검색 name"));
		 }
		}
	private void NoTradable(L1PcInstance gm, String param){
		 try {
		  StringTokenizer tok = new StringTokenizer(param);
		  String type = tok.nextToken();
		  if (type.equalsIgnoreCase("리로드")) {
		   NoTradable.getInstance().reload();
		   gm.sendPackets(new S_SystemMessage("드랍불가 설정이 리로드 되었습니다."));
		  } else if (type.equalsIgnoreCase("검색")) {
		   java.sql.Connection con = null;
		   PreparedStatement pstm = null;
		   ResultSet rs = null;
		   String nameid = tok.nextToken();
		   try {
		    con = L1DatabaseFactory.getInstance().getConnection();
		    String strQry;
		    strQry = " Select e.item_id, e.name from etcitem e, NoTradable l where l.item_id = e.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select w.item_id, w.name from weapon w, NoTradable l where l.item_id = w.item_id and name Like '%" + nameid + "%' ";
		    strQry += " union all " + " Select a.item_id, a.name from armor a, NoTradable l where l.item_id = a.item_id and name Like '%" + nameid + "%' ";
		    pstm = con.prepareStatement(strQry);
		    rs = pstm.executeQuery();
		    while (rs.next()) {
		     gm.sendPackets(new S_SystemMessage("[" + rs.getString("item_id") + "] " + rs.getString("name")));
		    }
		   } catch (Exception e) {
		   } finally {
		    rs.close();
		    pstm.close();
		    con.close();
		   }
		  } else {
		   String nameid = tok.nextToken();
		   int itemid = 0;
		   try {
		    itemid = Integer.parseInt(nameid);
		   } catch (NumberFormatException e) {
		    itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
		    if (itemid == 0) {
		     gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		     return;
		    }
		   }
		   L1Item temp = ItemTable.getInstance().getTemplate(itemid);
		   if (temp == null) {
		    gm.sendPackets(new S_SystemMessage("해당 아이템이 발견되지 않습니다. "));
		    return;
		   }
		   if (type.equalsIgnoreCase("추가")) {
		    if (NoTradable.getInstance().isNoTradable(itemid)) {
		     gm.sendPackets(new S_SystemMessage("이미 교환불가 목록에 있습니다."));
		     return;
		    }
		    NoTradable.getInstance().storeId(itemid);
		    gm.sendPackets(new S_SystemMessage("교환불가 항목에 추가 했습니다."));
		   } else if (type.equalsIgnoreCase("삭제")) {
		    if (!NoTradable.getInstance().isNoTradable(itemid)) {
		     gm.sendPackets(new S_SystemMessage("교환불가 항목에 해당 아이템이 없습니다."));
		     return;
		    }
		    gm.sendPackets(new S_SystemMessage("교환불가 항목에서 삭제 했습니다."));
		    NoTradable.getInstance().deleteId(itemid);
		   }
		  }
		 } catch (Exception e) {
		  gm.sendPackets(new S_SystemMessage(".교환불가 리로드"));
		  gm.sendPackets(new S_SystemMessage(".교환불가 추가|삭제 itemid|name"));

		  gm.sendPackets(new S_SystemMessage(".교환불가 검색 name"));
		 }
		}

	private void DistributionMsg(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				if (gm.isDistributionMsg() == true) {
					gm.sendPackets(new S_SystemMessage("이미 작동중입니다."));
					return;
				}
				gm.setDistributionMsg(true);
				gm.sendPackets(new S_SystemMessage("투견 분포도메세지 출력 켬"));
			} else if (param.equalsIgnoreCase("끔")) {
				if (gm.isDistributionMsg() != true) {
					gm.sendPackets(new S_SystemMessage("작동중이아닙니다."));
					return;
				}
				gm.setDistributionMsg(false);
				gm.sendPackets(new S_SystemMessage("투견 분포도메세지 출력 끔"));
			} else {
				gm.sendPackets(new S_SystemMessage(".분포도 [켬/끔]중 입력"));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".분포도 [켬/끔]중 입력"));
		}
	}
	
	private void Distributionlogin(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				if (gm.isDistributionlogin() == true) {
					gm.sendPackets(new S_SystemMessage("이미 작동중입니다."));
					return;
				}
				gm.setDistributionlogin(true);
				gm.sendPackets(new S_SystemMessage("유저접속로그 출력 켬"));
			} else if (param.equalsIgnoreCase("끔")) {
				if (gm.isDistributionlogin() != true) {
					gm.sendPackets(new S_SystemMessage("작동중이아닙니다."));
					return;
				}
				gm.setDistributionlogin(false);
				gm.sendPackets(new S_SystemMessage("유저접속로그 출력 끔"));
			} else {
				gm.sendPackets(new S_SystemMessage(".로그 [켬/끔]중 입력"));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".로그 [켬/끔]중 입력"));
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
			pc.sendPackets(new S_SystemMessage(".강제칼질 [켬/끔]"));
		}
	}
	
	/** 아이콘 확인 (개태) **/
    public void checkicon(L1PcInstance pc, String param) {
        try {
            StringTokenizer st = new StringTokenizer(param);
            String type = st.nextToken();
            if (type.equalsIgnoreCase("지속")) {
                try {
                    String select = st.nextToken();
                    int i = Integer.parseInt(st.nextToken());
                    int j = 0;
                    try {
                        j = Integer.parseInt(st.nextToken());
                    } catch (Exception exception) {
                        j = i;
                    }
                    if (select.equalsIgnoreCase("표현")) {
                        for (int k = i; k < j + 1; k++) {
                            pc.sendPackets(new S_SystemMessage("\\aA아이콘 지속출력 번호 : [\\aG" + k + "\\aA]"));
                            pc.sendPackets(new S_PacketBox(S_PacketBox.UNLIMITED_ICON, k, true));
                        }
                    } else if (select.equalsIgnoreCase("삭제")) {
                        for (int k = i; k < j + 1; k++) {
                            pc.sendPackets(new S_SystemMessage("\\aA아이콘 삭제 번호 : [\\aG" + k + "\\aA]"));
                            pc.sendPackets(new S_PacketBox(S_PacketBox.UNLIMITED_ICON, k, false));
                        }
                    } else {
                        pc.sendPackets(new S_SystemMessage("\\aI.아이콘 [지속] [표현 or 삭제] [i 또는 i~j]"));
                    }
                } catch (Exception exception) {
                    pc.sendPackets(new S_SystemMessage("\\aI.아이콘 [지속] [표현 or 삭제] [i 또는 i~j]"));
                }
            } else if (type.equalsIgnoreCase("연속")) {
                try {
                    int i = Integer.parseInt(st.nextToken());
                    int j = 0;
                    try {
                        j = Integer.parseInt(st.nextToken());
                    } catch (Exception exception) {
                        j = i;
                    }
                    for (int k = i; k < j + 1; k++) {
                        pc.sendPackets(new S_SystemMessage("\\aA아이콘 연속출력 번호:[\\aG" + k + "\\aA]"));
                        pc.sendPackets(new S_PacketBox(S_PacketBox.UNLIMITED_ICON, k, true));
                        Thread.sleep(2000);
                        pc.sendPackets(new S_PacketBox(S_PacketBox.UNLIMITED_ICON, k, false));
                    }
                } catch (Exception exception) {
                    pc.sendPackets(new S_SystemMessage("\\aL.아이콘 [연속] [i 또는 i~j]"));
                }
            } else {
                pc.sendPackets(new S_SystemMessage("\\aI.아이콘 [지속] [표현 or 삭제] [i 또는 i~j]"));
                pc.sendPackets(new S_SystemMessage("\\aL.아이콘 [연속] [i 또는 i~j]"));
            }
        } catch (Exception exception) {
            pc.sendPackets(new S_SystemMessage("\\aI.아이콘 [지속] [표현 or 삭제] [i 또는 i~j]"));
            pc.sendPackets(new S_SystemMessage("\\aL.아이콘 [연속] [i 또는 i~j]"));
        }
    }
    
	public static void send_dungeon_remains(L1PcInstance gm, Collection<DungeonTimeProgress<?>> progresses){
		DungeonTimeInformationLoader loader = DungeonTimeInformationLoader.getInstance();
		for(DungeonTimeProgress<?> progress : progresses){
			DungeonTimeInformation dtInfo = loader.from_timer_id(progress.get_timer_id());
			gm.sendPackets(new S_SystemMessage(String.format("%s %s", dtInfo.get_description(), "(남은시간) " + String.valueOf((progress.get_remain_seconds() / 60) + "분"))));
		}
	}
	
	public static void MultiTrade1(L1PcInstance pc, String arg) {
		L1PcInstance target = L1World.getInstance().getPlayer(arg);
		try {
			if (CheckPc1(pc, arg))
				return;
			if (target != null) {
				if (!target.isParalyzed()) {
					pc.setTradeID(target.getId());
					target.setTradeID(pc.getId());
					target.sendPackets(new S_Message_YN(252, pc.getName()));
					pc.sendPackets(new S_SystemMessage("" + target.getName() + " 님에게 교환을 요청하였습니다."));
				}
			} else {
				pc.sendPackets(new S_SystemMessage("상대방이 접속중이 아닙니다."));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".교환 캐릭명 으로 입력."));
		}
	}
	
	private void 퀴즈변경(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String newquiz = tok.nextToken();

			if (newquiz.length() < 4) {
				gm.sendPackets(new S_SystemMessage("입력하신 퀴즈의 자릿수가 너무 짧습니다."));
				gm.sendPackets(new S_SystemMessage("최소 4자 이상 입력해 주십시오."));
				return;
			}

			if (newquiz.length() > 12) {
				gm.sendPackets(new S_SystemMessage("입력하신 퀴즈의 자릿수가 너무 깁니다."));
				gm.sendPackets(new S_SystemMessage("최대 12자 이하로 입력해 주십시오."));
				return;
			}

			if (isDisitAlpha(newquiz) == false) {
				gm.sendPackets(new S_SystemMessage("암호에 허용되지 않는 문자가 포함되었습니다."));
				return;
			}
			L1PcInstance target = L1World.getInstance().getPlayer(user);
			if (target != null) {
				퀴즈체인지(gm, target, newquiz);
			} else {
				if (!퀴즈체인지(gm, user, newquiz))
					gm.sendPackets(new S_SystemMessage("그런 이름을 가진 캐릭터는 없습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".퀴즈변경 [캐릭명] [암호]"));
		}
	}
	
	private void 퀴즈체인지(L1PcInstance gm, L1PcInstance pc, String newquiz) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String quiz = null;
			con = L1DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement("select account_name from characters where char_name Like '" + pc.getName() + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				gm.sendPackets(new S_SystemMessage("퀴즈변경계정: [" + login + "] 퀴즈: [" + quiz + "]"));
				gm.sendPackets(new S_SystemMessage(pc.getName() + " 님의 퀴즈변경 완료."));
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
			con = L1DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement("select account_name from characters where char_name Like '" + name + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				pc.sendPackets(new S_SystemMessage("퀴즈변경계정: [" + login + "] 암호: [" + quiz + "]"));
				pc.sendPackets(new S_SystemMessage("해당 캐릭터 퀴즈변경 완료. (미접속중)"));
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

	private static boolean CheckPc1(L1PcInstance pc, String arg) {
		L1PcInstance target = L1World.getInstance().getPlayer(arg);
		if (pc.isGhost())
			return true;
		int castle_id = L1CastleLocation.getCastleIdByArea(pc);
		if (castle_id != 0)
			return true;// 무한몸빵방지,공성존에선 원격교환불가
		int itemId = 0;
		if (itemId >= 40010 && itemId <= 40043)
			return true;// 물약류 원격교환불가.

		if (pc.getOnlineStatus() == 0)
			return true;
		if (pc.getOnlineStatus() != 1)
			return true;
		if (!pc.isGm() && pc.isInvisble()) {
			pc.sendPackets(new S_ServerMessage(334));
			return true;
		}
		if (pc.getAccountName().equalsIgnoreCase(target.getAccountName())) {
			pc.sendPackets(new S_Disconnect());
			target.sendPackets(new S_Disconnect());
			return true;
		}
		if (pc.getId() == target.getId()) {
			pc.getNetConnection().kick();
			pc.getNetConnection().close();
			target.getNetConnection().kick();
			target.getNetConnection().close();
			return true;
		} else if (pc.getId() != target.getId()) {
			if (pc.getAccountName().equalsIgnoreCase(target.getAccountName())) {
				if (!target.isPrivateShop()) {
					pc.getNetConnection().kick();
					pc.getNetConnection().close();
					target.getNetConnection().kick();
					target.getNetConnection().close();
					return true;
				}
			}
		}
		return false;
	}
	
	private void ShopKick(L1PcInstance gm, String param) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(param);
			if (target != null) {
				gm.sendPackets(new S_SystemMessage((new StringBuilder()).append(target.getName()).append("님을 추방 했습니다.").toString()));
				GameServer.disconnectChar(target);
			} else {
				gm.sendPackets(new S_SystemMessage("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다."));
			}
		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".상점추방 캐릭명"));
		}
	}
	
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
	
	public static void ExchangeSearch(L1PcInstance pc, String param){
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String code = tokenizer.nextToken();
			pc.sendPackets(new S_ExchangeShopSearch(code));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".시세 아이템명"));
		}
	} 
	
	
	private void describe(L1PcInstance gm) {
		try {

			int dmgup = ((int) gm.getMeleeWeaponDmg() + gm.getMeleeDmg() + gm.getBapodmg() + CalcUtil.getMeleeDmgStat(gm.getAbility().getTotalStr()));  //--근거리 데미지 
			int hitup = (gm.getMeleeHit() + CalcUtil.getMeleeHitStat(gm.getAbility().getTotalStr()));  //-- 근거리 명중
			int Bowdmg = ((int) gm.getRangedWeaponDmg() + gm.getRangedDmg() + gm.getBapodmg() + CalcUtil.getRangedDmgStat(gm.getAbility().getTotalDex()));  //--원거리 데미지
			int Bowhit = (gm.getRangedHit() + (int) CalcUtil.getRangedHitStat(gm.getAbility().getTotalDex()));  //--원거리 명중
			
			int hpr = gm.getHpr();
			int mpr = gm.getMpr();
			ArrayList<String> result = new ArrayList<String>();  
			
			Connection con = null;
			int q = 0;
			int i = 0;
			int j = 0;
			int n = gm.getType();
			int objid = gm.getId();
			con = L1DatabaseFactory.getInstance().getConnection();
			Statement pstm = con.createStatement();
			ResultSet rs = pstm.executeQuery("SELECT objid FROM characters WHERE AccessLevel = 0 order by Exp desc");
			Statement pstm2 = con.createStatement();
			ResultSet rs2 = pstm2.executeQuery(
					"SELECT `Exp`,`char_name` FROM `characters` WHERE AccessLevel = 0 ORDER BY `Exp` DESC limit 1");

			if (gm.getType() == 0) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery(
						"SELECT objid FROM characters WHERE type = 0 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (gm.getType() == 1) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery(
						"SELECT objid FROM characters WHERE type = 1 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (gm.getType() == 2) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery(
						"SELECT objid FROM characters WHERE type = 2 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (gm.getType() == 3) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery(
						"SELECT objid FROM characters WHERE type = 3 and AccessLevel = 0 order by Exp desc");
				while (rs3.next()) {
					j++;
					if (objid == rs3.getInt(1))
						break;
				}
				rs3.close();
				pstm3.close();
			} else if (gm.getType() == 4) {
				Statement pstm3 = con.createStatement();
				ResultSet rs3 = pstm3.executeQuery(
						"SELECT objid FROM characters WHERE type = 4 and AccessLevel = 0 order by Exp desc");
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
				if (objid == rs.getInt(1))
					break;
			}
			rs.close();
			pstm.close();
			rs2.close();
			pstm2.close();
			con.close();
			
			result.add(String.valueOf(Config.LIMITLEVEL));			//만렙
			
			result.add(String.valueOf(0));			//전체랭킹
			result.add(String.valueOf(0));			//클래스랭킹
						
			result.add(String.valueOf(dmgup));			//근거리추타
			result.add(String.valueOf(hitup));			//근거리명중

			result.add(String.valueOf(Bowdmg));			//원거리추타
			result.add(String.valueOf(Bowhit));			//원거리명중
			
			result.add(String.valueOf(gm.getResistance().getregist_technology()));		//스턴내성
			result.add(String.valueOf(gm.getResistance().gettechnique_hit()));			//스턴적중
			result.add(String.valueOf(gm.getResistance().getregist_spirit()));			//정령내성
			result.add(String.valueOf(gm.getResistance().getspirit_hit()));				//정령적중
			
			result.add(String.valueOf(gm.getResistance().getMr()));						//마법방어
			result.add(String.valueOf(gm.getMagicHit()));				//마법적중
			
			
			result.add(String.valueOf(gm.getDamageReduction()));			//대미지감소
			
			result.add("0");			//감소무시
			
			result.add(String.valueOf(gm.getDmgupPvP()));			//pvp추타
			result.add(String.valueOf(gm.getDmgDownPvP()));			//pvp대감소
			
			result.add(String.valueOf(hpr));			//피틱
			result.add(String.valueOf(mpr));			//엠틱
			result.add(String.valueOf(gm.getKarma()));			//우호도  
			gm.sendPackets(new S_NPCTalkReturn(gm.getId(), "information", (String[]) result.toArray(new String[result.size()])));

		} catch (Exception e) {
			gm.sendPackets(new S_SystemMessage(".정보 커멘드 에러"));
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
	private void stopWar(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			CastleWar war = CastleWarBusiness.getInstance().findWar(name);
			if (war == null) {
				throw new Exception();
			}
			war.close();
		} catch (Exception e) {
			gm.sendPackets(".공성종료 [성이름두글자]");
		}
	}
	

	private void serversave(L1PcInstance pc) {
		Saveserver();// 서버세이브 메소드 선언
		pc.sendPackets(new S_SystemMessage("서버저장이 완료되었습니다."));
	}

	/** 서버저장* */
	private void Saveserver() {
		/** 전체플레이어를 호출* */
		Collection<L1PcInstance> list = null;
		list = L1World.getInstance().getAllPlayers();
		for (L1PcInstance player : list) {
			if (player == null) continue;
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
	
    private void hold(L1PcInstance gm, String pcName) {
        try {
            L1PcInstance target = L1World.getInstance().getPlayer(pcName);
            if (target != null) {
                holdnow(gm, target);
            } else {
                gm.sendPackets(new S_SystemMessage("그런 캐릭터는 없습니다."));
            }
        } catch (Exception e) {
            gm.sendPackets(new S_SystemMessage(".감옥 캐릭명 으로 입력해주세요."));
        }
    }
    private void holdnow(L1PcInstance gm, L1PcInstance target) {
        try { 
        	target.teleport(32736, 32799, (short) 34, target.getHeading(), 169, false, false);
            gm.sendPackets(new S_SystemMessage((new StringBuilder()).append(target.getName()).append(" 님 감옥으로 이동됨.").toString()));
            target.sendPackets(new S_SystemMessage("감옥에 감금 되었습니다."));
        } catch (Exception e) {
            _log.log(Level.SEVERE, "", e);
        }
    }
 
}
