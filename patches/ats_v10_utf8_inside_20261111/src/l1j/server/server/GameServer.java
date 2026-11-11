/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Collection;
import I1j.server.server.model.Thread.CharacterQuickCheckThread;
import l1j.server.ActionCodes.Loader.SpriteInformationLoader;
import l1j.server.ActionListener.ActionListenerLoader;
import l1j.server.BonusDropSystem.BonusDropSystemLoader;
import l1j.server.BonusItem.BonusItemLoader;
import l1j.server.BonusMaps.BonusMapTable;
import l1j.server.BugRace.BugRaceController;
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.DTSSystem.IODTSLoader;
import l1j.server.DogFightSystem.DogFigthSystemLoader;
import l1j.server.DungeonTimer.Loader.DungeonTimeLoadManager;
import l1j.server.ExchangeItem.ExchangeItemLoader;
import l1j.server.ExchangeListItem.ExchangeListItemLoader;
import l1j.server.ExpRevision.FishingExpInfo;
import l1j.server.FishingRodInfo.FishingRodInfoLoader;
import l1j.server.InvenBonusItem.InvenBonusItemLoader;
import l1j.server.ItemEnchantSystem.ItemEnchantSystemLoadManager;
import l1j.server.ItemExChangeSystem.ItemExChangeLoader;
import l1j.server.ItemSkill.Loader.ItemSkillModelLoader;
import l1j.server.MagicDollEventMonster.MagicDollEventMonsterLoader;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.ServerMacroSystem.ServerMacroLoader;
import l1j.server.SetItems.SetItemsLoader;
import l1j.server.Template.DateSchedulerModel.DateSchedulerLoader;
import l1j.server.Template.ObServer.CopyMapObservable;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.Controller.AuctionTimeController;
import l1j.server.server.Controller.BossController;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.Controller.HouseTaxTimeController;
import l1j.server.server.Controller.LoginController;
//import l1j.server.server.Controller.PremiumTimeController;
//import l1j.server.server.Controller.ShipTimeController;  // 잊혀진섬배
import l1j.server.server.Controller.OccultFeatherController;
import l1j.server.server.Controller.UserRankingController;
import l1j.server.server.Opcodes.ServerOpcodesTable;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.CastleTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.CraftListTable;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.DropItemTable;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.EtcItemBuffBeadTable;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemMessageBoxTable;
import l1j.server.server.datatables.ItemMessageTable;
import l1j.server.server.datatables.ItemSettingTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.L1DragonJewel;
import l1j.server.server.datatables.L1EnchantBonus;
import l1j.server.server.datatables.MapChangeTable;
import l1j.server.server.datatables.MapReturnTable;
import l1j.server.server.datatables.MapRingTelTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.MobGroupTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.datatables.NpcShopAdenTypeTable;
import l1j.server.server.datatables.NpcShopTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.datatables.WantedTelTable;
import l1j.server.server.datatables.WeaponBalanceTable;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.wantedadena;
import l1j.server.server.datatables.wcanceladena;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.ElementalStoneGenerator;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1DeleteItemOnGround;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.BlankScroll;
import l1j.server.server.model.item.BlessTypeEnchant;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.item.EnchantScroll;
import l1j.server.server.model.item.HealingPotion;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.item.MemorialStone;
import l1j.server.server.model.item.Oman_Teleport_Scroll;
import l1j.server.server.model.item.PolyPotion;
import l1j.server.server.model.item.SpellItem;
import l1j.server.server.model.item.SpellWand;
import l1j.server.server.model.item.TreasureBox;
import l1j.server.server.model.item.PolyBuffSystem;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.network.NetworkConfig;
import l1j.server.server.network.CServer;
import l1j.server.server.utils.SystemUtil;
import manager.SJManager;




public class GameServer {
	private int SELECT_THREAD_COUNT;
	private int _ioThreadIndex;
	private int chatlvl;
	
	private static GameServer _instance;
	private LoginController _loginController;
	private l1j.server.server.network.CServer _networkServer;

	private GameServer() {}

	public static GameServer getInstance() {
		if (_instance == null) {
			synchronized (GameServer.class) {
				if (_instance == null)
					_instance = new GameServer();
			}
		}
		return _instance;
	}

	public void initialize() throws Exception {
		System.out.println("┌────────────────────────────────────────────────────────────────────┐");
		System.out.println("\t\t\t3.1 Version Lineage Server 1체크\t\t");
		System.out.println("└────────────────────────────────────────────────────────────────────┘");
		double rateXp = Config.RATE_XP;
		double LA = Config.RATE_LA;
		double rateKarma = Config.RATE_KARMA;
		double rateDropItems = Config.RATE_DROP_ITEMS;
		double rateDropAdena = Config.RATE_DROP_ADENA;
		chatlvl = Config.GLOBAL_CHAT_LEVEL;

		SELECT_THREAD_COUNT = Config.SELECT_THREAD_COUNT;
		
		System.out.println("┌────────────────────────────────────────────────────────────────────┐");
		System.out.println("\t\t\t 경험치 :  " + (rateXp) + " 배\t\t");
		System.out.println("\t\t\t 성향:  " + (LA) + " 배\t\t");
		System.out.println("\t\t\t 우호도:  " + (rateKarma) + " 배\t\t");
		System.out.println("\t\t\t 아이템:  " + (rateDropItems) + " 배 \t\t");
		System.out.println("\t\t\t 아데나:  " + (rateDropAdena) + " 배\t\t");
		System.out.println("\t\t\t 채팅 가능:  " + (chatlvl) + " 레벨\t\t");
		int maxOnlineUsers = Config.MAX_ONLINE_USERS;
		System.out.println("\t\t\t 인원수:  " + (maxOnlineUsers) + " 명\t\t");
		System.out.println("└────────────────────────────────────────────────────────────────────┘");
		// 쓰레드 풀 로드
		GeneralThreadPool.getInstance();
		// 데이터베이스 팩토리 로드
		IdFactory.getInstance();
		// 월드맵 로드
		L1WorldMap.getInstance();
		// 월드 로드 
		L1World.getInstance();
		// 월드 트랩 로드 
		L1WorldTraps.getInstance();
		// 맵 테이블 로드
		MapsTable.getInstance();
		// 로그인 컨트롤러 로드
		_loginController = LoginController.getInstance();
		_loginController.setMaxAllowedOnlinePlayers(maxOnlineUsers);
		// 서버 옵코드 로드
		ServerOpcodesTable.getInstance();
		// 스포라이트 로드
		SpriteInformationLoader.getInstance().loadSpriteInformation(); 
		// 온라인 상태 리셋트
		CharacterTable.clearOnlineStatus();
		// 게임 시간 시계
		GameTimeClock.init();
		// 현재 시간 시계
		RealTimeClock.init();
		// 보스몬스터 스폰 리스트 
		BossMonsterSpawnList.init();
		// 경험치 테이블 로드
		ExpTable.getInstance().loadExp(false);
		// 로그 파일 저장 로드 
		LoggerInstance.getInstance();
		// 유저 랭킹 시스템 컨트롤러 로드 
		UserRankingController.getInstance();
		// 공성 비지니스 로드 
		CastleWarBusiness.getInstance().run();
		// 게임마스터 공지사항 로드
		ServerMacroLoader.getInstance().start(); 
		// 시간 스케쥴러 로드 
		DateSchedulerLoader.getInstance().run();
		// 던전 타임 매니저 로드 
		DungeonTimeLoadManager.getInstance().load();	
		// 정령의 돌 스폰 로드
		if (Config.ELEMENTAL_STONE_AMOUNT > 0) {
			GeneralThreadPool.getInstance().execute(ElementalStoneGenerator.getInstance());
		}
		// 캐릭터 종료 체크 쓰레드 로드 
		if (Config.CHARACTER_CHECK_SYSTEM) {
			CharacterQuickCheckThread.getInstance();
		} 	
		// 신비한 날개 깃털 로드 	
		if(Config.신비한_날개_깃털) {
			OccultFeatherController occultFeather = OccultFeatherController.getInstance();
			GeneralThreadPool.getInstance().scheduleAtFixedRate(occultFeather, 0, OccultFeatherController.SLEEP_TIME);
		}
		// 아지트 경매 타임 컨트롤러  로드
		AuctionTimeController auctionTime = AuctionTimeController.getInstance();
		GeneralThreadPool.getInstance().scheduleAtFixedRate(auctionTime, 0, AuctionTimeController.SLEEP_TIME);
		
		// 아지트 세금 타임 컨트롤러 로드
		HouseTaxTimeController houseTaxTime = HouseTaxTimeController.getInstance();
		GeneralThreadPool.getInstance().scheduleAtFixedRate(houseTaxTime, 0, HouseTaxTimeController.SLEEP_TIME);
		
		// 낚시 타임 컨트롤러 로드
		FishingTimeController fishingTime = FishingTimeController.getInstance();
		GeneralThreadPool.getInstance().scheduleAtFixedRate(fishingTime, 0, FishingTimeController.SLEEP_TIME);
		
		// 버그베어 경주 컨트롤러 로드
		BugRaceController bugRaceController = BugRaceController.getInstance();
		GeneralThreadPool.getInstance().execute(bugRaceController);
		
		
		// 엔피시 이벤트 스폰 컨트롤러  로드
		EventNpcSpawnCotroller EventNpcSpawn = EventNpcSpawnCotroller.getInstance();
		GeneralThreadPool.getInstance().scheduleAtFixedRate(EventNpcSpawn, 0, EventNpcSpawnCotroller.SLEEP_TIME);
		
		// 배타임 콘트롤러
	//	ShipTimeController shipTimeController = ShipTimeController.getInstance();
	//	GeneralThreadPool.getInstance().execute(shipTimeController);
		
		// 엔피시 테이블 로드
		NpcTable.getInstance();	
		// 레이드 로드
		Dragon_Raid_Load_Manager.getInstance().load();
		// 맵 카피 인스턴스 로드 
		CopyMapObservable.getInstance();
		// 그라운드 아이템 삭제 로드
		L1DeleteItemOnGround deleteitem = new L1DeleteItemOnGround();
		deleteitem.initialize();

		if (!NpcTable.getInstance().isInitialized()) {
			throw new Exception("Could not initialize the npc table");
		}
		// 엔피시 이벤트 스폰 테이블 로드 
		EventNpcSpawnTable.getInstance().loadEventNpc();
		// 낚시 경험치 로드 
		FishingExpInfo.do_load();
		// 스폰 테이블 로드
		SpawnTable.getInstance();
		// 엔피시 스폰 테이블
		NpcSpawnTable.getInstance();
		// 몬스터 그룹 테이블 로드
		MobGroupTable.getInstance();
		// 스킬 테이블 로드
		SkillsTable.getInstance();
		// 변신 테이블 로드 
		PolyTable.getInstance();
		// 아이템 테이블 로드 
		ItemTable.getInstance();
		// 드랍 테이블 로드 
		DropTable.getInstance();
		// 인챈트 주문서 시스템 로드(젤, 무기)
		EnchantScroll.getInstance();
		// 텔레포트 시스템 로드
		// 오만 텔레포트 주문서 로드
		Oman_Teleport_Scroll.getInstance();
		// 드랍 아이템 테이블 로드
		DropItemTable.getInstance();
		// 상점 테이블 로드
		ShopTable.getInstance();
		// 엔피시 액션 테이블 로드
		NPCTalkDataTable.getInstance();
		// 던전 구현 
		Dungeon.getInstance();
		// 클랜 테이블 로드 
		ClanTable.getInstance();
		// 아이피 테이블 로드
		IpTable.getInstance();
		// 펫 테이블 로드 
		PetTable.getInstance();
		// 성 테이블 로드 
		CastleTable.getInstance();
		// 성 위치 로드 
		L1CastleLocation.setCastleTaxRate();	
		// 리스타트 테이블 로드 
		GetBackRestartTable.getInstance();
		// 문 스폰 테이블 로드
		DoorSpawnTable.getInstance();
		// 무기 밸런스 테이블 로드 
		WeaponBalanceTable.getInstance();
		// 용해제 테이블 로드 
		ResolventTable.getInstance();
		// 가구 테이블 로드
		FurnitureSpawnTable.getInstance();
		// 엔피시 채팅 테이블 로드 
		NpcChatTable.getInstance();
		// 세트 아이템 로드 
		SetItemsLoader.getInstance();
		// 낚시 로드 
		FishingRodInfoLoader.getInstance();
		// 갯백 로드 
		Getback.loadGetBack();	
		// 아이템 스킬 모델 로드 
		ItemSkillModelLoader.getInstance();
		// 엔피시 상점 타입 테이블 로드 
		NpcShopAdenTypeTable.getInstance();
		// 기억 테이블 로드 
		MapReturnTable.getInstance();
		// 투견 시스템 로드 
		DogFigthSystemLoader.getInstance();	
		ItemTable.getInstance().initDogfight();
		ItemTable.getInstance().initRace();
		// 아이템 텔레포트 주문서 로드 
		IODTSLoader.getInstance();
		// 보너스 시스템 로드 
		BonusDropSystemLoader.getInstance();
		BonusMapTable.getInstance();
		BonusItemLoader.getInstance();	
		// 아이템 버프 테이블 로드 
		EtcItemBuffBeadTable.getInstance();
		// 엔피시 상점 테이블 로드 
		NpcShopTable.getInstance(); 
		// 맵 타입 테이블 로드 
		MapChangeTable.getInstance();
		// 보스 몬스터 스폰 리스트 로드
		BossController.getInstance();
		// 방어구 인챈트 시스템 로드 
		ArmorEnchantInformationTable.getInstance();
		// 무기 인챈트 시스템 로드
		WeaponEnchantInformationTable.getInstance();
		// 악세사리 인챈트 시스템 로드 
		AccessoryEnchantInformationTable.getInstance();
		// 순간 이동 지배 반지 테이블 로드
		MapRingTelTable.getInstance();
		// 수배 시스템 로드 
		WantedTelTable.getInstance();
		wantedadena.getInstance(); 
		wcanceladena.getInstance(); 
		// 제작 리스트 테이블 로드 
		CraftListTable.getInstance();
		// 마법인형 이벤트 몬스터 로드 
		MagicDollEventMonsterLoader.getInstance();
		// 교환 아이템 로드 
		ExchangeItemLoader.getInstance();
		ExchangeListItemLoader.getInstance();
		// 엔피시 액션 텔레포트 로드 
		ActionListenerLoader.getInstance();
		// 아이템 박스 메세지 로드 
		ItemMessageBoxTable.getInstance();
		ItemMessageTable.getInstance();		
		// 아이템 인챈트 셋팅 테이블 로드 (러쉬 확률)
		ItemSettingTable.getInstance();
		// 인벤토리 아이템 보너스 로드 
		InvenBonusItemLoader.getInstance();
		// 아이템 변경 로드 
		ItemExChangeLoader.getInstance();
		// 엔피시 액션 테이블 로드
		NpcActionTable.load();
		// 지엠 커멘드 컨피그 로드 
		GMCommandsConfig.load();
		// 펫 타입 테이블 로드 
		PetTypeTable.load();
		// 트래져 박스 로드 
		TreasureBox.load();
		// 힐링 포션 로드
		HealingPotion.load(); 
		// 요리 로드
		Cooking.load();
		// 변신 물약 로드 
		PolyPotion.load();
		// 마법 인형 로드 
		MagicDoll.load();
		// 마법 주문서 로드 
		BlankScroll.load();
		// 마법 막대 로드 
		SpellWand.load();
		// 마법아이템 로드
		SpellItem.load();
		// 드래곤 캐쉬 아이템 로드 
		L1DragonJewel.load();
		// 아이템 인챈트 러쉬 로드 
		L1EnchantBonus.load();
		// 아이템 축복 인챈트 로드 
		BlessTypeEnchant.load();
		// 영혼석 로드 
		MemorialStone.load();
		// 아이템 인챈트 시스템 매니저 로드 
		ItemEnchantSystemLoadManager.getInstance().load();
		// 캐릭터 교환 로드 
		CTLoadManager.getInstance().load();
		// 변신 버프 시스템 로드 
		PolyBuffSystem.load();

		System.gc();
		System.out.println("┌────────────────────────────────────────────────────────────────────┐");
		System.out.println("\t\t\t Server Loding Completed : --> ON\t\t");
		System.out.println("\t\t\t Server Port : " + Config.GAME_SERVER_PORT + "\t\t");
		System.out.println("\t\t\t Server Memory : " + SystemUtil.getUsedMemoryMB() + "M\t\t");
		System.out.println("└────────────────────────────────────────────────────────────────────┘");
		
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());

		NetworkConfig network =
                    NetworkConfig.load(Path.of("config/network.properties"));
            _networkServer = new CServer(network, GameClient::new,
                    ip -> !IpTable.getInstance().isBannedIp(ip));
            try {
                _networkServer.start(new InetSocketAddress(Config.GAME_SERVER_PORT));
                // Advertisement listener is unused; only start the game transport.
            } catch (Exception failure) {
                _networkServer.close();
                throw failure;
            }

	}

	
	

	/**
	 * 온라인중의 플레이어 모두에 대해서 kick, 캐릭터 정보의 보존을 한다.
	 */
	public void disconnectAllCharacters() {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            if (pc == null || pc.noPlayerck2) continue;
            try {
                if (pc.getNetConnection() != null) pc.getNetConnection().kick();
                else pc.logout();
            } catch (Exception failure) { failure.printStackTrace(); }
        }
    }
    public void shutdownNetwork() {
        // No advertisement listener is started by this server.
        if (_networkServer != null) _networkServer.close();
    }
    public String networkMetrics() {
        return _networkServer == null ? "not started" : _networkServer.snapshot().toString();
    }

	public int saveAllCharInfo() {
		// exception 발생하면 -1 리턴, 아니면 저장한 인원 수 리턴
		int cnt = 0;
		try {
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (pc.noPlayerck2)
					continue;
				cnt++;
				pc.save();
				pc.saveInventory();
			}
		} catch (Exception e) {
			return -1;
		}

		return cnt;
	}

	/**
	 * 온라인중의 플레이어에 대해서 kick , 캐릭터 정보의 보존을 한다.
	 */
	public static void disconnectChar(L1PcInstance pc) {
        if (pc.getNetConnection() != null) pc.getNetConnection().kick();
        else pc.logout();
    }

	public static void disconnectChar(String name) {
		L1PcInstance pc = L1World.getInstance().getPlayer(name);

		if (pc != null) {
			disconnectChar(pc);
		}
	}

	private class ServerShutdownThread extends Thread {
		private final int _secondsCount;

		public ServerShutdownThread(int secondsCount) {
			_secondsCount = secondsCount;
		}

		@Override
		public void run() {
			L1World world = L1World.getInstance();
			try {
				int secondsCount = _secondsCount;
				world.broadcastServerMessage("------------------------------------");
				world.broadcastServerMessage("잠시 후 시스템 및 DB 안정화를 위한");
				world.broadcastServerMessage("리니지 정기점검이 진행될 예정입니다.");
				world.broadcastServerMessage("서버 다운 이전에 안전한 장소에서");
				world.broadcastServerMessage("게임을 종료하여 주시길 바랍니다.");
				world.broadcastServerMessage("더욱 쾌적하고 원활한 게임 환경으로");
				world.broadcastServerMessage("찾아뵐 수 있도록 최선의 노력을 다하겠습니다.");
				world.broadcastServerMessage("------------------------------------");
				while (0 < secondsCount) {
					if (secondsCount <= 30) {
						world.broadcastServerMessage("게임이 " + secondsCount + "초 후에 종료 됩니다. 게임을 중단해 주세요.");
					} else {
						if (secondsCount % 60 == 0) {
							world.broadcastServerMessage("게임이" + secondsCount / 60 + "분 후에 종료 됩니다.");
						}
					}
					Thread.sleep(1000);
					secondsCount--;
				}
				shutdown();
			} catch (InterruptedException e) {
				world.broadcastServerMessage("서버 종료가 중단되었습니다. 서버는 정상 가동중입니다.");
				return;
			}
		}
	}

	private ServerShutdownThread _shutdownThread = null;

	public synchronized void shutdownWithCountdown(int secondsCount) {
		if (_shutdownThread != null) {
			// 이미 슛다운 요구를 하고 있다
			// TODO 에러 통지가 필요할지도 모른다
			return;
		}
		_shutdownThread = new ServerShutdownThread(secondsCount);
		_shutdownThread.start();
	}

	public void shutdown() {
		CTLoadManager.getInstance().release();	
		ItemSkillModelLoader.release();
		disconnectAllCharacters();

		try {
			LoggerInstance.getInstance().flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			SJManager.display.syncExec(new Runnable() {
				public void run() {
					try {
						SJManager.savelog();
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("LinAllManager(세이브 로그) error check GameServer.java");
					}
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("LinAllManager(디스플레이 체크) error check GameServer.java");
		}

		System.exit(0);
	}

	public synchronized void abortShutdown() {
		if (_shutdownThread == null) {
			return;
		}
		_shutdownThread.interrupt();
		_shutdownThread = null;
	}
}