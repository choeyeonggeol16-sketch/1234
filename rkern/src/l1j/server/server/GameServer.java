package l1j.server.server;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceEvents;
import java.util.Collection;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightLoader;
import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.Server;
import l1j.server.server.model.world.instance.dungeon.tower.bosscontroller.DominanceTimeController;
import l1j.server.server.model.world.instance.dungeon.tower.DominanceDataLoader;
import l1j.server.server.model.world.residence.CWorldAgitTimer;
import l1j.server.server.task.TaskLane;
import l1j.server.server.Controller.AuraController;
import l1j.server.server.model.npc.spawn.boss.BossController;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.model.npc.chat.NpcChatTimeController;
import l1j.server.server.Controller.OneTimeController;
import l1j.server.server.model.world.transport.ShipTimeController;
import l1j.server.server.appcenter.AppCenterService;
import l1j.server.server.auth.captcha.loader.CaptchaLoadManager;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.AlchemyProbability;
import l1j.server.server.datatables.AlchemyProbabilityBox;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantList;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.world.residence.AuctionBoardTable;
import l1j.server.server.model.npc.spawn.boss.BossMonsterSpawnList;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanBuffTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.item.drop.DropItemTable;
import l1j.server.server.model.item.drop.DropTable;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.model.world.residence.HouseTable;
import l1j.server.server.datatables.IncreaseEinhasadMap;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.KeyTable;
import l1j.server.server.datatables.LightSpawnTable;
import l1j.server.server.datatables.MapUserTeleportTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.npc.spawn.group.MobGroupTable;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.model.npc.dialog.NPCTalkDataTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.shop.NpcCashShopSpawnTable;
import l1j.server.server.model.npc.chat.NpcChatTable;
import l1j.server.server.datatables.NpcMarkTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.RaceTable;
import l1j.server.server.datatables.ReportTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.model.shop.ShopTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.datatables.UBSpawnTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.WeaponEnchantList;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Getback;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.rank.L1ClanRanking;
import l1j.server.server.model.world.residence.L1HouseLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.character.action.listener.ActionListenerLinkageLoader;
import l1j.server.server.model.character.action.listener.ActionListenerLoader;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.character.animation.SprBoundary;
import l1j.server.server.model.character.animation.loader.SpriteInformationLoader;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoadManager;
import l1j.server.server.model.character.companion.CompanionLoader;
import l1j.server.server.model.character.effect.action.loader.EffectModelLoader;
import l1j.server.server.model.character.experience.fishing.FishingExpInfo;
import l1j.server.server.model.character.scheduler.CInventoryExpiry;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.inventory.EquipmentEnchantOptions;
import l1j.server.server.model.item.EquipmentBlessing;
import l1j.server.server.model.item.assembly.CItemAssembler;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.item.doll.DollPotentialEffects;
import l1j.server.server.model.item.effects.ItemEffectBootstrap;
import l1j.server.server.model.item.enchant.ItemEnchantSystemLoadManager;
import l1j.server.server.model.item.function.L1HealingPotion;
import l1j.server.server.model.item.proc.ItemSkillModelLoader;
import l1j.server.server.model.item.teleport.designated.DesignatedTeleportLoader;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.model.shop.NpcShopManager;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;
import l1j.server.server.model.world.trap.L1WorldTraps;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeLoadManager;
import l1j.server.server.model.world.instance.inn.loader.InnHelperLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftLoader;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ALCHEMY_DESIGN_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CRAFT_LIST_ALL_ACK;
import l1j.server.server.shared.observer.CopyMapObservable;
import l1j.server.server.storage.CacheD;
import l1j.server.server.task.automation.ServerMacroLoader;
import l1j.server.server.task.calendar.DateSchedulerLoader;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.model.world.announcement.Announcements;
import l1j.server.server.model.world.announcement.CAnnounceTimer;

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
		loadGameData();
		System.gc();
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		startRuntimeServices();
	}

	private void loadGameData() throws Exception {
		EquipmentEnchantOptions.loadDefinitions();
		EquipmentBlessing.initialize();
		ItemEffectBootstrap.loadAll();
		CookingRegistry.initialize();
		DollRegistry.initialize();
		DollPotentialEffects.initialize();
		CItemAssembler.initialize();
		
		CPacketFilter.getInstance().load();
		// 나누기
		AlchemyProbabilityBox.getInstance().shuffleList();

		IdFactory.getInstance();
		BuilderCmdHandler.LoadBuilderCmdHandler();
		L1WorldMap.getInstance();
		SpriteInformationLoader.getInstance().loadSpriteInformation();

		FishingExpInfo.do_load();
		GameUtils.load();
		
		CraftProbability.getInstance();
		CraftLoader.load();
		SC_CRAFT_LIST_ALL_ACK.buildCaches();
		SC_CRAFT_LIST_ALL_ACK.buildNpcCaches();
		AlchemyProbability.getInstance();
		SC_ALCHEMY_DESIGN_ACK.load(); 
		AlchemyProbabilityBox.getInstance();	
		
		
		CharacterTable.getInstance().loadAllCharName();
		SprBoundary.do_load();
		// 온라인 상태 리셋트
		CharacterTable.clearOnlineStatus();
		// Loaders can read time and register listeners before callbacks start.
		L1GameTimeClock.prepare();
		RealTimeClock.prepare();
		
		KeyTable.initBossKey();

		ArmorEnchantList.getInstance(); // 방어구 인챈 정보 리스트
		WeaponEnchantList.getInstance(); // 무기 인챈 정보 리스트

		/** 로그파일저장 **/
		LoggerInstance.getInstance();

		AuctionBoardTable.getInstance();

//		L1HauntedHouse hauntedHouse = L1HauntedHouse.getInstance();
//
//		L1Racing race = L1Racing.getInstance();

		CObjectDB.LoadNPC();
		NpcSpeedData.do_load();

		if (!CObjectDB.LoadNPC().isInitialized()) {
			throw new Exception("Could not initialize the npc table");
		}
        MobSkillTable.getInstance();
		l1j.server.server.model.world.instance.dungeon.gludio.GludioInstanceDungeon.getInstance().initialize();

		// 패키지상점
		NpcCashShopSpawnTable.getInstance();
		NpcCashShopSpawnTable.getInstance().Start();
		ItemSkillModelLoader.getInstance();
		CNpcMakerDB.getInstance();
		MobGroupTable.getInstance();
		SkillsTable.getInstance();
		PolyTable.getInstance();
		CObjectDB.LoadItem().initRace();
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
		ClanTable.getInstance();
		ClanBuffTable.getInstance();
		ClanTable.getInstance().createTutorialClan();
		
		L1CastleLocation.setCastleTaxRate(); 
		GetBackRestartTable.getInstance();
		DoorSpawnTable.getInstance();
		NpcActionTable.load();
		BuilderCmdConfig.load();
		Getback.loadGetBack();
		L1TreasureBox.load();
		L1HealingPotion.load();
		DominanceDataLoader.getInstance();
		RaceTable.getInstance();
		ResolventTable.getInstance();
		FurnitureSpawnTable.getInstance();
		NpcChatTable.getInstance();
		LightSpawnTable.getInstance();
		Announcements.getInstance();
		WeaponAddDamage.getInstance();
		CObjectDB.LoadSetItem();
		CObjectDB.PostLoad();
		MapUserTeleportTable.getInstance();

		ArmorEnchantInformationTable.getInstance();
		WeaponEnchantInformationTable.getInstance();
		AccessoryEnchantInformationTable.getInstance();


		ReportTable.getInstance(); // 신고 테이블
		
		RankLoadManager.getInstance().load();

		KdaLoadManager.getInstance().load();


		NpcMarkTable.getInstance();

		/** 레이드 **/
		RaidLoadManager.getInstance().load();
		EffectModelLoader.initialize();

		CopyMapObservable.getInstance();
		InnHelperLoader.getInstance();

		if (Config.USE_ATTENDANCE_SYSTEM) {
			AttendanceLoadManager.getInstance().load();
		}

		BQSLoadManager.getInstance().run();

		DateSchedulerLoader.getInstance().run();
		ActionListenerLoader.getInstance();
		ActionListenerLinkageLoader.getInstance();

		CombatLoadManager.getInstance().load();
		CaptchaLoadManager.getInstance().load();
		ItemEnchantSystemLoadManager.getInstance().load();

		DesignatedTeleportLoader.getInstance();

		DungeonTimeLoadManager.getInstance().load();
		IncreaseEinhasadMap.getInstance();
		UserProtectMonsterTable.getInstance();

		PrideLoadManager.getInstance();
		
		MonsterParalyzeDelay.getInstance();

		CompanionLoader.getInstance();
		DogFightLoader.getInstance();
		
		CastleWarBusiness.getInstance().run();

		CollectionLoadManager.getInstance().load();
		
		HouseTable.getInstance();       // 캐시 로드
		L1HouseLocation.rebuildCaches();
		
		BossMonsterSpawnList.init();
	}

	private void startRuntimeServices() {
		CIOThreadCallback.getInstance();
		CNPCServer.getInstance().start();
		System.out.println("엔피시 서버 연결이 완료되었습니다.");

		OneTimeController.start();
		NpcChatTimeController.getInstance().start();
		BugRaceController.getInstance().start();
		ShipTimeController.getInstance().start();
		L1ClanRanking.getInstance().start();
		ServerMacroLoader.getInstance().start();
		DominanceTimeController.getInstance();
		BossController.start();
		CWorldAgitTimer.start();
		DogFightLoader.getInstance().startAutomaticReservation();
		AuraController.getInstance().start();
		CInventoryExpiry.getInstance().start();
		if (Config.Use_Show_Announcecycle) {
			CAnnounceTimer.getInstance().reload();
		}
		UBTable.getInstance().getUb(1).start();
		if (Config.AUTO_GM_SHOP) {
			NpcShopManager.getInstance().start();
		}
		L1GameTimeClock.start();
		RealTimeClock.start();
	}

    public void disconnectAllCharacters() {
        // Two phases: request close for every remaining session, then await all
        // completed PC logouts. This does NOT invoke pc.logout concurrently
        // with an in-progress packet or a previously submitted logout job.
        final L1PcInstance[] pcs = L1World.getInstance().getAllPlayers()
                .toArray(new L1PcInstance[0]);
        final l1j.server.server.model.network.core.connection.CUserSocket[] clients =
                new l1j.server.server.model.network.core.connection.CUserSocket[pcs.length];
        final long started = System.nanoTime();
        for (int i = 0; i < pcs.length; i++) {
            final L1PcInstance pc = pcs[i];
            if (pc == null || pc.noPlayerck2) continue;
            final l1j.server.server.model.network.core.connection.CUserSocket client = pc.getNetConnection();
            clients[i] = client;
            if (client != null && client != null)
                client.close();
        }
        for (int i = 0; i < pcs.length; i++) {
            final L1PcInstance pc = pcs[i];
            if (pc == null || pc.noPlayerck2) continue;
            final l1j.server.server.model.network.core.connection.CUserSocket client = clients[i];
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
        if (CIOThreadCallback.currentShardIndex() >= 0 || TaskLane.isWorkerThread()
                || CInstanceEvents.isEventThread()) {
            shutdownWithCountdown(0);
            return;
        }
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
	        PlaySupportWindow.stopIndependent();
	        CAnnounceTimer.getInstance().stop();
	        CWorldAgitTimer.stop();
	        DogFightLoader.getInstance().stopTimers();
	        UBTable.getInstance().getAllUb().forEach(battle -> battle.stop());
	        BossController.stop();
	        DominanceTimeController.stop();
	        AuraController.getInstance().stop();
	        ShipTimeController.getInstance().stop();
	        L1ClanRanking.getInstance().stop();
	        ServerMacroLoader.release();
	        NpcShopManager.getInstance().cancelRefill();
	        AppCenterService.stop();
	        OneTimeController.stop();
	        NpcChatTimeController.getInstance().stop();
	        L1CompanionInstance.do_store_companions();

            // The native transport is joined while CIO shard owners are still
            // alive. Never save or dispose a character while its RECV handler
            // can be executing on one of those owners.
            Server.stopLoginServer();
            disconnectAllCharacters();
            L1GameTimeClock.stop();
            RealTimeClock.stop();
            // Let accepted off-owner work finish before removing the world, its
            // owner threads, or the database that those callbacks may still use.
            CInstanceEvents.stop();
            CServerTimer.stopOwnerSignals();
            TaskLane.stopAll();
            while (!CInstanceEvents.awaitTermination(30L, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[Instance] Waiting for running events to finish");
            }
            while (!TaskLane.awaitAll(30L, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[TaskLane] Waiting for accepted work to finish");
            }
	        RaidLoadManager.getInstance().release();

	        DynamicShopStorage.clearProcess();

	        CollectionLoadManager.getInstance().release();

	        ItemSkillModelLoader.release();

	        KdaLoadManager.getInstance().release();

	        CPacketFilter.getInstance().release();


	        CInventoryExpiry.getInstance().stop();


	        BugRaceController.getInstance().stop();

	        CPacketFilter.getInstance().release();

	        RankUserLoader.getInstance().release();

	        LoggerInstance.getInstance().flushImmediately();
	        // Release delayed effect casts before closing their NPC owners.
	        EffectModelLoader.release();
	        CNPCServer npcServer = CNPCServer.getInstance();
	        npcServer.shutdown();
	        if (!npcServer.awaitTermination(30L, java.util.concurrent.TimeUnit.SECONDS))
	            throw new IllegalStateException("NPC owner termination exceeded 30 seconds");
	        Server.printShutdownStatus("엔피시 서버가 종료되었습니다.");

	        CIOThreadCallback.getInstance().shutdown();
            // No game owner may still be running a packet or actor timer when
            // the shared executor and CacheD are torn down.
            if (!CIOThreadCallback.getInstance().awaitTermination(
                    30L, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("CIO game shard termination exceeded 30 seconds");
	        TileOccupancyNpc.getInstance().destroy();
	        TileOccupancy.getInstance().destroy();

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
