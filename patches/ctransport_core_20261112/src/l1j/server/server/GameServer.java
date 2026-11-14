package l1j.server.server;

import java.util.Collection;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.Server;
import l1j.server.DogFight.MJDogFightLoader;
import l1j.server.MJ3SEx.MJNpcSpeedData;
import l1j.server.MJ3SEx.MJSprBoundary;
import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.MJActionListener.ActionListenerLinkageLoader;
import l1j.server.MJActionListener.ActionListenerLoader;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJCompanion.MJCompanionLoader;
import l1j.server.MJDShopSystem.MJDShopStorage;
import l1j.server.MJDTSSystem.MJDTSLoader;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeLoadManager;
import l1j.server.MJEffectSystem.Loader.MJEffectModelLoader;
import l1j.server.MJExpRevision.MJFishingExpInfo;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.MJInstanceSystem.Loader.MJInstanceLoadManager;
import l1j.server.MJItemEnchantSystem.MJItemEnchantSystemLoadManager;
import l1j.server.MJItemSkillSystem.MJItemSkillModelLoader;
import l1j.server.MJKDASystem.MJKDALoadManager;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.MJServerMacroSystem.MJServerMacroLoader;
import l1j.server.MJTemplate.DateSchedulerModel.DateSchedulerLoader;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.Payment.MJPaymentCipher;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.TowerOfDominance.DominanceDataLoader;
import l1j.server.TowerOfDominance.BossController.DominanceTimeController;
import l1j.server.server.command.L1Commands;
import l1j.server.server.appcenter.AppCenterService;
import l1j.server.server.Controller.AuctionTimeController;
import l1j.server.server.Controller.BossController;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.Controller.HouseTaxTimeController;
import l1j.server.server.Controller.NpcChatTimeController;
import l1j.server.server.Controller.OneTimeController;
import l1j.server.server.Controller.PremiumTimeController;
import l1j.server.server.Controller.ShipTimeController;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.AlchemyProbability;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantList;
import l1j.server.server.datatables.ArmorSetTable;
import l1j.server.server.datatables.AuctionBoardTable;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanBuffTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.DropItemTable;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.IncreaseEinhasadMap;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.KeyTable;
import l1j.server.server.datatables.LightSpawnTable;
import l1j.server.server.datatables.MJAlchemyProbabilityBox;
import l1j.server.server.datatables.MJNpcMarkTable;
import l1j.server.server.datatables.MapUserTeleportTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.MobGroupTable;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.datatables.NpcCashShopSpawnTable;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.RaceTable;
import l1j.server.server.datatables.ReportTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.datatables.UBSpawnTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.WeaponEnchantList;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1ClanMatching;
import l1j.server.server.model.L1ClanRanking;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.character.scheduler.AutoSaveChaQuitCheckScheduler;
import l1j.server.server.model.character.scheduler.CharTamScheduler;
import l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.item.effects.ItemEffectBootstrap;
import l1j.server.server.model.item.function.L1HealingPotion;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.client.manager.NetworkClientCounter;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.NpcServer;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.model.trap.L1WorldTraps;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.Packet.SC_AlchemyDesignAck;
import l1j.server.server.packets.Protocol.Packet.SC_CraftListAllAck;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftLoader;
import l1j.server.server.storage.CacheD;
import l1j.server.server.utils.MJCommons;

public class GameServer {
	private static GameServer _instance;

	private GameServer() {
	}

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
		ThreadPool.getInstance();
		
		NetworkClientCounter.getInstance();
		NetworkSafeLoadManager.getInstance().load();
		// 나누기
		MJAlchemyProbabilityBox.getInstance().shuffleList();

		IdFactory.getInstance();
		L1Commands.reload();
		L1WorldMap.getInstance();
		SpriteInformationLoader.getInstance().loadSpriteInformation();

		MJFishingExpInfo.do_load();
		MJCommons.load();
		
		CraftProbability.getInstance();
		CraftLoader.load();
		SC_CraftListAllAck.buildCaches();
		SC_CraftListAllAck.buildNpcCaches();
		AlchemyProbability.getInstance();
		SC_AlchemyDesignAck.load(); 
		MJAlchemyProbabilityBox.getInstance();	
		
		
		CharacterTable.getInstance().loadAllCharName();
		MJSprBoundary.do_load();
		// 온라인 상태 리셋트
		CharacterTable.clearOnlineStatus();
		// 게임 시간 시계
		L1GameTimeClock.init();
		// 현재 시간 시계
		RealTimeClock.init();
		
		KeyTable.initBossKey();

		ArmorEnchantList.getInstance(); // 방어구 인챈 정보 리스트
		WeaponEnchantList.getInstance(); // 무기 인챈 정보 리스트
		// 퀘스트 몬스터 시스템
	//	CharacterQuestMonsterTable.getInstance();

		// 정령의 돌 타임 컨트롤러
//		if (Config.ELEMENTAL_STONE_AMOUNT > 0) {
//			ElementalStoneGenerator elementalStoneGenerator = ElementalStoneGenerator.getInstance();
//			GeneralThreadPool.getInstance().scheduleAtFixedRate(elementalStoneGenerator, 0, ElementalStoneGenerator.SLEEP_TIME);
//		}
		/** 로그파일저장 **/
		LoggerInstance.getInstance();

		OneTimeController.start();
		
		AuctionBoardTable.getInstance();
		// 아지트 경매
		AuctionTimeController.getInstance();
		// 아지트 세금
		HouseTaxTimeController.getInstance();

		// 실행
		NpcChatTimeController.getInstance().start();

		BugRaceController.getInstance().start();

//		L1HauntedHouse hauntedHouse = L1HauntedHouse.getInstance();
//		ThreadPool.getInstance().execute(hauntedHouse);
//
//		L1Racing race = L1Racing.getInstance();
//		ThreadPool.getInstance().execute(race);

		NpcTable.getInstance();
		MJNpcSpeedData.do_load();

		if (!NpcTable.getInstance().isInitialized()) {
			throw new Exception("Could not initialize the npc table");
		}
        MobSkillTable.getInstance();
		l1j.server.IndunSystem.Gludio.GludioInstanceDungeon.getInstance().initialize();

		// 패키지상점
		NpcCashShopSpawnTable.getInstance();
		NpcCashShopSpawnTable.getInstance().Start();
		MJItemSkillModelLoader.getInstance();
		SpawnTable.getInstance();
		MobGroupTable.getInstance();
		SkillsTable.getInstance();
		PolyTable.getInstance();
		ItemTable.getInstance();
		ItemTable.getInstance().initRace();
		DropTable.getInstance();
		DropItemTable.getInstance();
		ShopTable.getInstance();
		NPCTalkDataTable.getInstance();
		L1World.getInstance();
		L1WorldTraps.getInstance();
		Dungeon.getInstance();
		NpcSpawnTable.getInstance();
		IpTable.getInstance();
		MapsTable.getInstance();
		UBSpawnTable.getInstance();
		PetTable.getInstance();
		ClanTable.getInstance();
		ClanBuffTable.getInstance();
		ClanTable.getInstance().createTutorialClan();
		
		L1CastleLocation.setCastleTaxRate(); 
		GetBackRestartTable.getInstance();
		DoorSpawnTable.getInstance();
		NpcActionTable.load();
		GMCommandsConfig.load();
		Getback.loadGetBack();
		PetTypeTable.load();
		L1TreasureBox.load();
		L1HealingPotion.load();
		DominanceDataLoader.getInstance();
		DominanceTimeController.getInstance();
		RaceTable.getInstance();
		ResolventTable.getInstance();
		FurnitureSpawnTable.getInstance();
		NpcChatTable.getInstance();
		LightSpawnTable.getInstance();
		Announcements.getInstance();
		WeaponAddDamage.getInstance();
		L1ClanMatching.getInstance().loadClanMatching();
		ArmorSetTable.getInstance();
		MapUserTeleportTable.getInstance();

		ArmorEnchantInformationTable.getInstance();
		WeaponEnchantInformationTable.getInstance();
		AccessoryEnchantInformationTable.getInstance();


		ShipTimeController.getInstance().start();//배표

		if (Config.Use_Show_Announcecycle) { // 자동 공지사항
			Announcecycle.getInstance();
		}
		ReportTable.getInstance(); // 신고 테이블
		
		L1ClanRanking.getInstance().start();
		
		RankLoadManager.getInstance().load();

		MJKDALoadManager.getInstance().load();
		// TODO 말하는 섬 퀘스트 저장
	//	CharacterQuestTable.getInstance();
	//	QuestInfoTable.getInstance();

		MJNpcMarkTable.getInstance();

		/** 레이드 **/
		MJRaidLoadManager.getInstance().load();
		MJEffectModelLoader.initialize();

		/** 2016.11.26 MJ 앱센터 LFC **/
		MJInstanceLoadManager.getInstance().load();

		MJServerMacroLoader.getInstance().start();

		MJCopyMapObservable.getInstance();
		MJINNHelperLoader.getInstance();

		if (Config.USE_ATTENDANCE_SYSTEM) {
			AttendanceLoadManager.getInstance().load();
		}

		BQSLoadManager.getInstance().run();

		DateSchedulerLoader.getInstance().run();
		ActionListenerLoader.getInstance();
		ActionListenerLinkageLoader.getInstance();

		MJCombatLoadManager.getInstance().load();
		MJCaptchaLoadManager.getInstance().load();
		MJItemEnchantSystemLoadManager.getInstance().load();

		MJDTSLoader.getInstance();

		DungeonTimeLoadManager.getInstance().load();
		//MJItemExChangeLoader.getInstance();
		IncreaseEinhasadMap.getInstance();
		UserProtectMonsterTable.getInstance();

		PrideLoadManager.getInstance();
		
		MonsterParalyzeDelay.getInstance();

		MJCompanionLoader.getInstance();
		MJPaymentCipher.getInstance();
		MJDogFightLoader.getInstance();
		
		MJCastleWarBusiness.getInstance().run();

		/** MJCTSystem **/
		MJCTLoadManager.getInstance().load();
		
		HouseTable.getInstance();       // 캐시 로드
		L1HouseLocation.rebuildCaches();
		
		// 아이템 정보 등록 처리 
		ItemEffectBootstrap.loadAll();
		
		// 보스몬스터 스폰 처리
		BossMonsterSpawnList.init();
		BossController.start();

		if (Config.깃털지급) {
			PremiumTimeController.getInstance().start();
		}

		// 캐릭터 탐 시스템  쓰레드 처리
		CharTamScheduler.getInstance().start();
		
		// 스킬 아우라 쓰레드 처리
		AuraController.getInstance().start();

		// 캐릭터 시간제 아이템 삭제 쓰레드 로직 처리 (/
		PcInventoryExpiryScheduler.getInstance().start();
		/** 서버관리 스레드 로직 처리 **/
		AutoSaveChaQuitCheckScheduler.getInstance().start();
		
		// CIOThreadCallback 각종 모니터 스레드 처리
		CIOThreadCallback.getInstance();
		
		UBTable.getInstance().getUb(1).start();
		

		if (Config.AUTO_GM_SHOP) {
			NpcShopManager.getInstance().start();
		}
		
//		
//		PathNodeWorld.getInstance().dumpPathNodes(
//		        4,
//		        32639,
//		        32872,
//		        24);
//		
		// ai용 맵데이터 읽기 처리
	//	PathNodeMaps.prepare();
		
		System.gc();
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		NpcServer.getInstance();
		System.out.println("엔피시 서버 연결이 완료되었습니다.");
		AppCenterService.start();
	}

    public void disconnectAllCharacters() {
        // Two phases: request close for every remaining session, then await all
        // completed PC logouts. This does NOT invoke pc.logout concurrently
        // with an in-progress packet or a previously submitted logout job.
        final L1PcInstance[] pcs = L1World.getInstance().getAllPlayers()
                .toArray(new L1PcInstance[0]);
        final l1j.server.server.model.network.core.session.CIOClient[] clients =
                new l1j.server.server.model.network.core.session.CIOClient[pcs.length];
        final long started = System.nanoTime();
        for (int i = 0; i < pcs.length; i++) {
            final L1PcInstance pc = pcs[i];
            if (pc == null || pc.noPlayerck2) continue;
            final l1j.server.server.model.network.core.session.CIOClient client = pc.getNetConnection();
            clients[i] = client;
            if (client != null && client.getConnection() != null)
                client.getConnection().close();
        }
        for (int i = 0; i < pcs.length; i++) {
            final L1PcInstance pc = pcs[i];
            if (pc == null || pc.noPlayerck2) continue;
            final l1j.server.server.model.network.core.session.CIOClient client = clients[i];
            if (client != null) {
                while (!client.isLogoutComplete()) {
                    if (System.nanoTime() - started >= 30_000_000_000L)
                        throw new IllegalStateException("CIO logout drain incomplete after 30s; PC=" + pc.getId());
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("CIO logout drain interrupted");
                    }
                }
            }
            // Private-shop/auto-clan connections intentionally skip ordinary
            // disconnect logout; a complete server shutdown must still close
            // them after all packet owners have been released.
            if (!pc.isLogoutStarted()) pc.logout();
            while (!pc.isLogoutComplete()) {
                if (System.nanoTime() - started >= 30_000_000_000L)
                    throw new IllegalStateException("PC logout teardown incomplete after 30s; PC=" + pc.getId());
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("PC logout teardown interrupted");
                }
            }
        }
    }

	/**
	 * 온라인중의 플레이어에 대해서 kick , 캐릭터 정보의 보존을 한다.
	 */
	public static void disconnectChar(L1PcInstance pc) {
		if (pc.getNetConnection() != null) {
			pc.getNetConnection().kick();
		}
		pc.logout();
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
				world.broadcastServerMessage("잠시 후, 서버를 종료 합니다.");
				world.broadcastServerMessage("안전한 장소에서 로그아웃 해 주세요");
				while (0 < secondsCount) {
					if (secondsCount <= 30) {
						System.out.println("게임이 " + secondsCount + "초 후에 종료 됩니다. 게임을 중단해 주세요.");
						world.broadcastServerMessage("게임이 " + secondsCount + "초 후에 종료 됩니다. 게임을 중단해 주세요.");
					} else {
						if (secondsCount % 60 == 0) {
							System.out.println("게임이 " + secondsCount / 60 + "분 후에 종료 됩니다.");
							world.broadcastServerMessage("게임이 " + secondsCount / 60 + "분 후에 종료 됩니다.");
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
			return;
		}
		_shutdownThread = new ServerShutdownThread(secondsCount);
		_shutdownThread.start();
		Collection<L1PcInstance> pcList = L1World.getInstance().getAllPlayers();
		for (L1PcInstance pc : pcList) {
			if (pc == null || pc.noPlayerck2)
				continue;
			pc.서버다운중 = true;
		}
	}

    private final Object resourceShutdownLock = new Object();
    private boolean resourcesShutdown;
    private int resourceShutdownStatus;

    public void shutdown() {
        final int status = shutdownResources();
        System.exit(status);
    }

    /** The JVM hook must return instead of calling System.exit recursively. */
    public void shutdownFromHook() {
        shutdownResources();
    }

    private int shutdownResources() {
        synchronized (resourceShutdownLock) {
            if (!resourcesShutdown) {
                resourceShutdownStatus = shutdownResourcesOnce();
                resourcesShutdown = true;
            }
            return resourceShutdownStatus;
        }
    }

    private int shutdownResourcesOnce() {
	    Throwable fatal = null;
	    try {
	        Server.stopNewConnections();
	        AppCenterService.stop();
	        OneTimeController.stop();
	        NpcChatTimeController.getInstance().stop();
	        L1CompanionInstance.do_store_companions();

            // The native transport is joined while CIO shard owners are still
            // alive. Never save or dispose a character while its RECV handler
            // can be executing on one of those owners.
            Server.stopLoginServer();
            disconnectAllCharacters();
	        MJRaidLoadManager.getInstance().release();

	        MJInstanceLoadManager.getInstance().release();

	        MJDShopStorage.clearProcess();

	        MJCTLoadManager.getInstance().release();

	        MJItemSkillModelLoader.release();

	        MJKDALoadManager.getInstance().release();

	        NetworkSafeLoadManager.getInstance().release();

	        CharTamScheduler.getInstance().stop();

	        PcInventoryExpiryScheduler.getInstance().stop();

	        AutoSaveChaQuitCheckScheduler.getInstance().stop();

	        PremiumTimeController.getInstance().stop();
	        BugRaceController.getInstance().stop();

	        NetworkSafeLoadManager.getInstance().release();

	        RankUserLoader.getInstance().release();

	        NetworkClientCounter.getInstance().release();
	        LoggerInstance.getInstance().flushImmediately();
	        TileOccupancyNpc.getInstance().destroy();
	        TileOccupancy.getInstance().destroy();

	        // Release any delayed MJEffect cast before NPC owner/shard shutdown.
        MJEffectModelLoader.release();
        NpcServer.getInstance().shutdown();
	        l1j.server.Server.printShutdownStatus("엔피시 서버가 종료되었습니다.");

	        CIOThreadCallback.getInstance().shutdown();
            // No game owner may still be running a packet or actor timer when
            // the shared executor and CacheD are torn down.
            if (!CIOThreadCallback.getInstance().awaitTermination(
                    30L, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("CIO game shard termination exceeded 30 seconds");

	        // Cancel clock futures before destroying the shared scheduler;
	        // stale periodic callbacks must not retain listeners/game objects.
	        L1GameTimeClock.stop();
	        RealTimeClock.stop();
            // Keep storage alive until every accepted instant job has finished.
            ThreadPool pool = ThreadPool.getInstance();
            while (!pool.shutdown(30L, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[ThreadPool] Waiting for accepted tasks to finish before storage shutdown");
                if (Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("Interrupted while waiting for ThreadPool shutdown");
            }

	        CacheD cache = CacheD.getInstance();
	        while (!cache.shutdown(30_000L)) {
	            System.err.println("[CacheD] 저장 대기: accounts=" + cache.pendingAccounts()
	                    + ", characters=" + cache.pendingCharacters()
	                    + ", items=" + cache.pendingItems());
	            Throwable failure = cache.lastFailure();
	            if (failure != null) {
	                failure.printStackTrace();
	            }
	            LoggerInstance.getInstance().flushImmediately();
	            try {
	                Thread.sleep(5000L);
	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	            }
	        }

	       Server.printShutdownStatus("게임서버가 종료되었습니다.");
	        DatabaseFactory.getInstance().shutdown();

	    } catch (Throwable t) {
	        fatal = t;
	        t.printStackTrace();
	        LoggerInstance.getInstance().flushImmediately();
	    } finally {
	        LoggerInstance.getInstance().flushImmediately();
	        if (fatal != null) {
	            System.err.println("=== 셧다운 중 치명 오류 — 5초 후 종료 (로그 확인용) ===");
	            try {
	                Thread.sleep(5000);
	            } catch (InterruptedException ignore) {
	                Thread.currentThread().interrupt();
	            }
	        }
	    }
        return fatal != null ? 1 : 0;
	}


	public synchronized void abortShutdown() {
		if (_shutdownThread == null) {
			return;
		}

		_shutdownThread.interrupt();
		_shutdownThread = null;
	}
}