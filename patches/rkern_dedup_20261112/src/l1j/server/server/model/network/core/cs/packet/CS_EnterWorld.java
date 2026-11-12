package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_UnityIcon;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_BookMarkLoad;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_CharPass;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;



import l1j.server.server.model.network.core.sc.packet.SC_ClanAttention;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.model.network.core.sc.packet.SC_SlotOpen;
import l1j.server.server.model.network.core.sc.packet.SC_Unknown1;
import java.sql.Connection;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJExpAmpSystem.MJExpAmplifierLoader;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.MJKDASystem.MJKDALoader;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJPassiveSkill.MJPassiveUserLoader;
import l1j.server.MJPassiveSkill.MJPassiveLoader;
import l1j.server.MJRaidSystem.MJRaidSpace;
import l1j.server.MJTemplate.MJEPcStatus;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.MJWarSystem.MJCastleWar;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.GMCommands;
import l1j.server.server.SkillCheck;
import l1j.server.server.Controller.BossController;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.datatables.CharacterSlotItemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.Getback;
import l1j.server.server.model.DungeonRandom;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanMatching;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1World;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistry;
import l1j.server.server.model.character.login.skill.BuffRestoreRegistryInit;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.tam.TamFruitUseService;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.Packet.SC_AddInventoryNoti;
import l1j.server.server.packets.Protocol.Packet.SC_AttendanceBonusGroupInfo;
import l1j.server.server.packets.Protocol.Packet.SC_AttendanceBonusInfoExtend;
import l1j.server.server.packets.Protocol.Packet.SC_AttendanceUserDataExtend;
import l1j.server.server.packets.Protocol.Packet.SC_MapID;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import l1j.server.server.packets.Protocol.Packet.SC_SlotChange;
import l1j.server.server.packets.Protocol.Packet.SC_WorldPutObjectNoti;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;
import l1j.server.server.model.network.core.sc.packet.SC_CharacterConfig;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.templates.L1GetBackRestart;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.CheckInitStat;
import l1j.server.swing.chocco;

import l1j.server.server.model.network.core.sc.packet.SC_Weather;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
public final class CS_EnterWorld {

	static class BuffInfo {
		public int skillId;
		public int remainTime;
		public int polyId;
	}

	private static Logger _log = Logger.getLogger(CS_EnterWorld.class.getName());

	private CS_EnterWorld() {
	}

	
	public static void handle(CIOClient client, String charName) {
		if (client == null || charName == null || charName.isEmpty()) {
			return;
		}

		try {
			if (Config.CHAR_PASSWORD
					&& client.getAccount().getCPW() != null
					&& client.getConnectionState() == ConnectionState.AUTHENTICATED) {

				client.setLastSelectedCharName(charName);
				SC_CharPass.send(client, SC_CharPass._비번입력창);
				return;
			}
		} catch (Exception e) {
			// 기존 readImpl()과 동일하게 인증 보조 분기 예외는 삼킨다.
		}

		doEnterWorld(charName, client);
	}

	private static void print_message(CIOClient clnt, String message, boolean is_kick) throws Exception {
		System.out.println("─────────────────────────────────");
		System.out.println(message);
		System.out.println("─────────────────────────────────");
		if (is_kick) {
			clnt.kick();
		}
	}
	
	
    private static final class EnterData {
        L1PcInstance pc;
        ArrayList<L1ItemInstance> items;
        CachedRowSet[] warehouseRows;
        SC_AttendanceUserDataExtend.LoginSnapshot attendance;
        int[] skills;
        List<Integer> mastery;
        List<BuffInfo> buffs;
        List<ExcludeRow> excludes;
        int mailCount;
        int[] slotItems;
        int[] passiveIds;
        List<DungeonTimeProgress<?>> dungeonProgress;
        long[] nBuffRows;
        long tamSnapshot;
        int[] questSteps;
        int[] levelGiftLevels;
        int[] lateralValues;
        BQSCharacterData bqs;
        ArrayList<L1BookMark> bookmarks;
        ArrayList<L1BookMark> templateOrbBookmarks;
        SC_CharacterConfig.Snapshot characterConfig;
        boolean blocked;
    }

    private static final class ExcludeRow {
        final int type;
        final String name;
        ExcludeRow(int type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    public static void doEnterWorld(String charName, CIOClient client) {
        if (client == null || charName == null || charName.isEmpty()
                || !client.isConnected() || client.getAccount() == null
                || client.getConnection() == null) return;
        final Account sessionAccount = client.getAccount();
        final L1World expectedWorld = L1World.getInstance();
        final l1j.server.server.templates.L1CharName known = CharacterTable.getInstance().getCharName(charName);
        final int charId = known == null ? 0 : known.getId();
        final int accountKey = CacheD.accountOrderKey(sessionAccount.getName());
        try {
            CacheD.getInstance().submitDbJobOrdered(charId, accountKey, () -> {
                final EnterData data;
                final Throwable failure;
                EnterData prepared = null;
                Throwable caught = null;
                try {
                    if (client.isConnected() && client.getAccount() == sessionAccount) {
                        prepared = loadEnterData(charName, sessionAccount);
                    }
                } catch (Throwable e) {
                    caught = e;
                }
                data = prepared;
                failure = caught;
                CIOThreadCallback.getInstance().completeSession(client,
                        () -> {
                            if (expectedWorld != L1World.getInstance())
                                throw new IllegalStateException("World replaced during login DB load");
                            completeEnterWorld(charName, client, sessionAccount, data, failure);
                        }, terminal -> {
                    // CachedRowSet is detached data, so discarding a rejected
                    // login needs no SQL or game-state mutation on this thread.
                    discardEnterData(data);
                    if (terminal != null) {
                        _log.log(Level.WARNING, "Enter owner completion failed: " + charName, terminal);
                        if (client.isConnected() && client.getAccount() == sessionAccount
                                && client.getActiveChar() == null) client.close();
                    }
                });
            });
        } catch (RuntimeException e) {
            _log.log(Level.SEVERE, "Enter DB request rejected: " + charName, e);
            client.kick();
        }
    }

    private static EnterData loadEnterData(String charName, Account sessionAccount) throws Exception {
        EnterData data = new EnterData();
        data.pc = L1PcInstance.load(charName);
        if (data.pc == null || data.pc.getAccountName() == null || !data.pc.getAccountName().equals(sessionAccount.getName())) return data;
        data.items = CharactersItemStorage.create().loadItems(data.pc.getId());
        // Bookmark metadata belongs to these detached item images. Its SQL
        // must finish before the CIO installs the inventory/world state.
        for (L1ItemInstance item : data.items) {
            if (item.getItemId() == 700023) L1ItemBookMark.bookmarItemkDB(data.pc, item);
            if (item.getItemId() == 700024 && data.templateOrbBookmarks == null)
                data.templateOrbBookmarks = L1BookMark.ShowBookmarkitem(null, 700024);
        }
        data.warehouseRows = readWarehouseRows(data.pc.getAccountName(), data.pc.getName());
        if (Config.USE_ATTENDANCE_SYSTEM && data.pc.getLevel() >= 50)
            data.attendance = SC_AttendanceUserDataExtend.readLoginSnapshot(data.pc.getAccountName());
        data.mastery = new ArrayList<>();
        data.skills = loadSkills(data.pc, data.mastery);
        data.buffs = loadBuff(data.pc);
        data.mailCount = CheckMail(data.pc);
        data.excludes = readExcludeList(data.pc.getId());
        data.slotItems = CharacterSlotItemTable.getInstance().loadCharSlots(data.pc.getId());
        data.blocked = GMCommands.isCharacterBlock(charName);
        data.passiveIds = MJPassiveUserLoader.readIds(data.pc.getId());
        data.dungeonProgress = DungeonTimeProgressLoader.readSnapshot(data.pc.getId(), data.pc.getAccountName());
        data.nBuffRows = readNBuffSnapshot(data.pc.getId(), sessionAccount.getAccountId());
        data.tamSnapshot = TamFruitUseService.readLoginSnapshot(data.pc.getAccountName());
        data.questSteps = L1Quest.readSnapshot(data.pc.getId());
        data.levelGiftLevels = L1PcInstance.readLevelGiftSnapshot(data.pc.getId());
        data.lateralValues = readLateralSnapshot(data.pc.getId());
        data.bqs = BQSCharacterDataLoader.readSnapshot(data.pc.getId());
        data.bqs.realloc_decks(data.pc);
        BQSCharacterDataLoader.storeCharacterBqs(data.bqs, false);
        data.bookmarks = L1BookMark.readSnapshot(data.pc.getId());
        if (Config.CHARACTER_CONFIG_IN_SERVER_SIDE)
            data.characterConfig = SC_CharacterConfig.load(data.pc.getId());
        MJPassiveLoader.getInstance();
        MJExpAmplifierLoader.getInstance();
        RankUserLoader.getInstance();
        DungeonRandom.getInstance();
        return data;
    }

    private static void discardEnterData(EnterData data) {
        if (data == null || data.warehouseRows == null) return;
        for (CachedRowSet row : data.warehouseRows)
            if (row != null) try { row.close(); } catch (SQLException ignored) {}
    }

    private static void completeEnterWorld(String charName, CIOClient client, Account account,
                                           EnterData data, Throwable failure) {
        try {
            if (!client.isConnected() || client.getAccount() != account
                    || client.getActiveChar() != null
                    || client.getConnectionState() != ConnectionState.AUTHENTICATED) return;
            if (failure != null) throw new IllegalStateException("Enter DB load failed", failure);
            if (data == null) return;
            L1PcInstance pc = validateEnterWorld(charName, client, data.pc, data.blocked);
            if (pc == null) return;
            pc.getQuest().applySnapshot(data.questSteps);
            pc.applyLevelGiftSnapshot(data.levelGiftLevels);
            applyEnterLocation(pc);
            l1j.server.server.model.network.core.DominationHybrid.applyLoginDestination(client, pc);
            registerToWorld(pc, client);
            sendEnterData(pc, client, data);
            postEnterProcess(pc, client, data);
            finalizeEnter(pc, client);
            pc.setCacheDSessionEpoch(CacheD.getInstance().registerLoadedCharacterSession(
                    pc.getAccountName(), pc.getName(), pc.getId()));
            client.publishGameSessionReady(pc);
            l1j.server.server.model.network.core.DominationHybrid.onEnterComplete(client, pc);
            // Repair level rewards after crash/relog, only after world/inventory
            // restoration and the session's CacheD owner lane are ready.
            pc.settleOutstandingLevelGifts();
        } catch (Exception e) {
            _log.log(Level.SEVERE, "doEnterWorld 실패: " + charName, e);
            try { client.kick(); } catch (Throwable ignore) {}
        }
    }

    private static L1PcInstance validateEnterWorld(String charName, CIOClient client, L1PcInstance pc, boolean blocked) throws Exception {
        if (blocked) {
            _log.info(String.format("압류 캐릭 접속 시도: %s", charName));
            SC_LoginResult.send(client, 52);
            return null;
        }

        if (pc == null) {
            print_message(client, String.format("캐릭터를 찾을 수 없습니다.(월드 접속) : %s(%s)", charName, client.getIp()), true);
            return null;
        }

        // 계정 로드 + 세션 정합
        Account account = client.getAccount();
        if (account == null || client.getAccount() == null || !pc.getAccountName().equals(client.getAccount().getName())) {
            _log.warning(String.format("계정 정합 실패: char=%s", charName));
            client.close();
            return null;
        }

        // 이미 활성 캐릭 보유한 클라 (중복 진입)
        if (client.getActiveChar() != null) {
            _log.info(String.format("중복 진입 차단: %s", client.getIp()));
            client.close();
            return null;
        }

        // 로그인 컨트롤러 정합
        CIOClient sessionClient = LoginController.getInstance().getClientByAccount(client.getAccountName());
        if (sessionClient != client) {
            _log.warning(String.format("세션 불일치 차단: %s", client.getIp()));
            client.close();
            return null;
        }

        // 월드 내 중복 캐릭 
        L1PcInstance other = L1World.getInstance().getPlayer(charName);
        if (other != null) {
            boolean exceptionCase = other.isPrivateShop() || other.isAutoClanjoin();
            if (!exceptionCase) {
                client.close();
                return null;
            }
        }
        // 소유 계정 일치
        if (!client.getAccountName().equals(pc.getAccountName())) {
            _log.warning(String.format("타계정 캐릭 접속 시도: char=%s", charName));
            client.close();
            return null;
        }
        // --- 치트/비정상 상태 체크 ---
        if (cheatChecks(pc, client)) {
            return null;   // 내부에서 kick/ban 처리됨
        }
        return pc;
    }
    private static boolean cheatChecks(L1PcInstance pc, CIOClient client) {
        // 렙다운
        if (!pc.isGm() && Config.LEVEL_DOWN_RANGE != 0
                && pc.getHighLevel() - pc.getLevel() >= Config.LEVEL_DOWN_RANGE) {
            _log.info(String.format("렙다운 초과: %s 계정=%s", pc.getName(), client.getAccountName()));
            client.close();
            return true;
        }
        // 방어구 버그
        if (pc.getAC().getAc() < -Config.ACLEVEL) {
            banLoginAccountAsync(pc.getAccountName(), SC_LoginResult.BANNED_REASON_HACK);
            client.close();
            _log.info("방어구버그 압류: " + pc.getName());
            return true;
        }
        // 레벨 버그
        if (pc.getLevel() > pc.getHighLevel()) {
            client.close();
            _log.info("레벨버그 추방: " + pc.getName());
            return true;
        }
        // 컨피그 레벨 캡
        if (pc.getLevel() >= Config.LIMITLEVEL && !pc.isGm()) {
            banLoginAccountAsync(pc.getAccountName(), SC_LoginResult.BANNED_REASON_HACK);
            client.close();
            _log.info("레벨캡 초과 압류: " + pc.getName());
            return true;
        }
        // 스탯 버그 (레벨별 상한)
        if (!pc.isGm()) {
            final int cap = (pc.getLevel() >= 90 || pc.getHighLevel() >= 90) ? 50 : 45;
            if (pc.getAbility().getCon() > cap || pc.getAbility().getStr() > cap
                    || pc.getAbility().getDex() > cap || pc.getAbility().getCha() > cap
                    || pc.getAbility().getInt() > cap || pc.getAbility().getWis() > cap) {
                스텟초기화(pc);
                 client.close();
                _log.info("스탯버그 초기화: " + pc.getName());
                return true;
            }
        }
        // 삭제 요청 캐릭
        if (pc.getType() < 0 || pc.getType() > 7) {
            client.close();
            _log.info("삭제요청 캐릭 접속 추방: " + pc.getName());
            return true;
        }
        return false;
    }
    
    private static void applyEnterLocation(L1PcInstance pc) {
        if (pc.is무인상점()) {
            pc.removeSkillEffect(L1SkillId.램페이지);
            pc.removeSkillEffect(L1SkillId.SHAPE_CHANGE);
            pc.set무인상점(false);
        }
        if (pc.getLevel() == 1) {
            pc.setExp(ExpTable.getExpByLevel(Config.Start_Char_Level));
        }
        L1GetBackRestart gbr = GetBackRestartTable.getInstance().getRestartInfo(pc.getMapId());
        if (gbr != null) {
            pc.setX(gbr.getLocX());
            pc.setY(gbr.getLocY());
            pc.setMap((short) gbr.getMapId());
        } else if (pc.getMapId() != 4 && pc.getMapId() != 3 && pc.getMapId() != 0
                && pc.getMapId() != 445 && pc.getMapId() != 63) {
            int[] loc = Getback.GetBack_Location(pc, true);
            pc.setX(loc[0]);
            pc.setY(loc[1]);
            pc.setMap((short) loc[2]);
        }
    }
    
    private static void registerToWorld(L1PcInstance pc, CIOClient client) {
        pc.set_instance_status(MJEPcStatus.WORLD);
        pc.setOnlineStatus(1);
        pc.create_captcha();
        CharacterTable.updateOnlineStatus(pc);
        L1World.getInstance().storeObject(pc);
        pc.setNetConnection(client);
        client.setActiveChar(pc);
        client.setConnectionState(ConnectionState.IN_GAME);
        SC_Unknown1.send(pc);
    }
    
    private static void sendEnterData(L1PcInstance pc, CIOClient client, EnterData data) {
        if (data.characterConfig != null) SC_CharacterConfig.send(pc, data.characterConfig);
        CharacterSlotItemTable.getInstance().applyCharSlots(pc, data.slotItems);
        MJKDALoader.getInstance().install(pc, false);
        int[] skillList = data.skills;
        if (skillList == null) skillList = new int[30];
        pc.lockUserState();
        try {
            for (int i = 0, n = data.mastery.size(); i < n; i++) {
                pc.setSkillMastery(data.mastery.get(i));
            }
        } finally {
            pc.unlockUserState();
        }
        SkillCheck.getInstance().AddSkill(pc.getId(), data.mastery);
        MJPassiveUserLoader.applyLoaded(pc, data.passiveIds);
        DungeonTimeProgressLoader.applySnapshot(pc, data.dungeonProgress);
        BQSCharacterDataLoader.applySnapshot(pc, data.bqs);
        sendSkillPacket(pc, skillList);
        MJCopyMapObservable.getInstance().resetPosition(pc);
        L1BookMark.applySnapshot(pc, data.bookmarks);
        SC_BookMarkLoad.send(pc);
        SC_OwnCharAttrDef.send(pc);
        SC_Weight.send(pc);
        SC_ActionUI2.send(pc, SC_ActionUI2.Elixir, pc.getElixirStats());
        MJCopyMapObservable.getInstance().resetPosition(pc);
        MJRaidSpace.getInstance().getBackPc(pc);
        MJInstanceSpace.getInstance().getBackPc(pc);
       
        // --- 성 전쟁 중 겟백 ---
        applyCastleGetBack(pc);

        L1Map l1map = pc.getMap();
        if (l1map == null || !l1map.isInMap(pc.getX(), pc.getY())) {
            MJCopyMapObservable.getInstance().resetAlwaysPositon(pc);
        }

        SC_OwnCharStatus.send(pc);
        SC_MapID.sendCreate(pc, pc.getMap().getBaseMapId(), pc.getMap().isUnderwater());
        MJExpAmplifierLoader.getInstance().set(pc);
        SC_Weather.send(pc, L1World.getInstance().getWeather());
        pc.sendClanMarks();
        SC_SPMR.send(pc);
        SC_Karma.send(pc);
        if (!SC_WorldPutObject.put(pc.getNetConnection(), pc)) {
            throw new IllegalStateException("Login character world packet not accepted: " + pc.getName());
        }
        L1World.getInstance().addVisibleObject(pc);
        pc.startObjectAutoUpdate();
        pc.beginGameTimeCarrier();
        clampHpMp(pc);
        CharacterTable.getInstance().restoreInventory(pc, data.items, data.warehouseRows);
        sendItemPacketChunked(pc);
        loadItems(pc, true, null);

        SC_PacketBox.send(pc, SC_PacketBox.INIT_DODGE, 0x0000);
        SC_PacketBox.send(pc, SC_PacketBox.DODGE, 0);
        pc.setCryOfSurvivalTime();
        SC_PacketBox.send(pc, SC_PacketBox.LOGIN_UNKNOWN3);
        SC_PacketBox.send(pc, SC_PacketBox.EMERALD_ICON, 0);
        SC_PacketBox.send(pc, SC_PacketBox.EMERALD_ICON, 1);
        
        safetyzone(pc);

        List<BuffInfo> buffList = data.buffs;
        processBuff(pc, buffList);


        // 기억의 구슬 목록 전송
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (item.getItemId() == 700024) {
                SC_PacketBox.send(pc, SC_PacketBox.BOOKMARK, item.getItemId(),
                        "$13719", data.templateOrbBookmarks);
            }
        }

        if (pc.getAccount().getphone() != null && pc.getAccount().getphone().length() >= 11) {
            보안버프(pc);
        }

        SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.UI4);
        SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.UI5);
        pc.sendVisualEffectAtLogin();
        pc.getLight().turnOnOffLight();
        SC_SPMR.send(pc);
        SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LOGIN);

        if (pc.isPassive(MJPassiveID.FOU_SLAYER_BRAVE.toInt())) {
            pc.포우브레이브(true);
        }

        // 존재 정합 최종 확인
        if (L1World.getInstance().getPlayer(pc.getName()) == null) {
            SC_SystemMessage.send(pc, "존재버그 강제종료! 재접속하세요");
            client.kick();
            return;
        }
        if (pc.getCurrentHp() > 0) {
            pc.setDead(false);
            pc.setStatus(0);
        } else {
            pc.setDead(true);
            pc.setStatus(ActionCodes.ACTION_Die);
        }

        // 미분배 스탯 안내
        if (pc.getLevel() >= 51 && pc.getLevel() - 50 > pc.getBonusStats()
                && pc.getAbility().getAmount() < 150) {
            SC_MessageYN.send(pc, 479, Integer.toString((pc.getLevel() - 50) - pc.getBonusStats()));
        }
        // 귀환 스탯 리셋 대기
        if (pc.getReturnStat() != 0) {
            if (pc.getWeapon() != null) {
                pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
            }
            for (L1ItemInstance armor : pc.getInventory().getItems()) {
                pc.getInventory().setEquipped(armor, false, false, false, false);
            }
            SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);
            try { pc.save(); } catch (Exception e) { _log.warning("귀환스탯 저장 실패: " + pc.getName()); }
        }

        SC_PacketBox.send(pc, SC_PacketBox.인벤저장);
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.퀘스트3, false);
        SC_NewCreateItem.sendTypeCk(pc, SC_NewCreateItem.퀘스트4, false);

        // One-time canonical scene reconciliation at login. The owner-local
        // spatial replica can lag canonical NPC/signboard publication; known
        // objects are not retransmitted, and failed sends remain retryable.
        for (L1Object visible : L1World.getInstance().getVisibleObjects(pc)) {
            if (visible != null) {
                pc.reconcileSight(visible);
            }
        }
    }
    
    private static void clampHpMp(L1PcInstance pc) {
        int maxHp = pc.getMaxHp(); int maxMp = pc.getMaxMp();
        if (pc.getCurrentHp() > maxHp) pc.setCurrentHp(maxHp);
        if (pc.getCurrentMp() > maxMp) pc.setCurrentMp(maxMp);
    }
    
    private static void postEnterProcess(L1PcInstance pc, CIOClient client, EnterData data) {
    	
        final int mailCount = data.mailCount;
        applyExcludeList(SpamTable.getInstance().getExcludeTable(pc.getId()), data.excludes);

        serchSummon(pc);
        MJCastleWarBusiness.getInstance().viewNowCastleWarState(pc);

        L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
        if (clan != null) {
            clan.updateClanMemberOnline(pc);
        }
        if (pc.getClanid() != 0) {
            if (clan != null) {
                if (clan.getBless() != 0) {
                	L1SkillUse.cast(pc, 504 + clan.getBless(), pc.getId(), pc.getX(),
                            pc.getY(), null, clan.getBuffTime()[clan.getBless() - 1], L1SkillUse.TYPE_LOGIN);
                	

                }
                SC_ActionUI.send(pc, clan.getClanName(), pc.getClanRank());
                SC_PacketBox.send(pc, SC_PacketBox.PLEDGE_EMBLEM_STATUS, pc.getClan().getEmblemStatus());
                if (clan.getGazeSize() != 0) {
                    SC_ClanAttention.sendList(pc, clan.getGazeSize(), clan.getGazeList());
                }
                if (pc.getClanid() == clan.getClanId()
                        && pc.getClanname().toLowerCase().equals(clan.getClanName().toLowerCase())) {
                    for (L1PcInstance member : clan.getOnlineClanMember()) {
                        if (member.getId() != pc.getId()) {
                            SC_ServerMessage.send(member, 843, pc.getName());
                        }
                    }
                    MJWar war = clan.getCurrentWar();
                    if (war != null) {
                        war.notifyEnenmy(pc);
                        if (war instanceof MJCastleWar) {
                            MJCastleWar cw = (MJCastleWar) war;
                            if (cw.isRun()) cw.onLordBuff(pc);
                        }
                    }
                } else {
                    pc.setClanid(0); pc.setClanname(""); pc.setClanRank(0);
                    pc.save();
                }
            } else {
                pc.setClanid(0); pc.setClanname(""); pc.setClanRank(0);
                pc.save();
            }
        }

        applyNBuffSnapshot(pc, data.nBuffRows);

        if (pc.getPartnerId() != 0) {
            L1PcInstance partner = (L1PcInstance) L1World.getInstance().findObject(pc.getPartnerId());
            if (partner != null && partner.getPartnerId() != 0
                    && pc.getPartnerId() == partner.getId() && partner.getPartnerId() == pc.getId()) {
                SC_ServerMessage.send(pc, 548);
                SC_ServerMessage.send(partner, 549);
            }
        }
        TamFruitUseService.applyLoginSnapshot(pc, data.tamSnapshot);
        pc.setSkillEffect(L1SkillId.SetBuff, 30 * 1000);

        pc.restoreBapoLevel();
        
        if (pc.getLevel() <= Config.Start_Char_Boho) {
            SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.Start_BUFF, 3804, true);
        } else {
            SC_InventoryIcon.icoEnd(pc, L1SkillId.Start_BUFF);
        }

        applyEinhasad(pc);
        applyDragonRaidBuff(pc, System.currentTimeMillis());

        pc.RenewStat();
        SC_Weight.send(pc);

        // 운영 GUI 
        final String name = pc.getName();
        SwingUtilities.invokeLater(() -> {
            try {
                chocco.count += 1;
                chocco.label2.setText(" " + chocco.count);
                chocco.userlist.add(name);
            } catch (Throwable ignore) {}
        });


        pc.getInventory().resetSlotTracking();
    	for (L1ItemInstance item : pc.getInventory().getItems()) {
    	    if (item != null && item.isEquipped()) {
    	        try {
    	            pc.getInventory().toSlotPacket(pc, item, true);
    	        } catch (Exception e) {
    	            e.printStackTrace();
    	        }
    	    }
    	}
       // ClanMatching(pc);

        // 이벤트 맵 만료 아이템 정리
        if (pc.getInventory().hasItemDirect(810006)) pc.getInventory().consumeItem(810006);
        if (pc.getInventory().hasItemDirect(810007)) pc.getInventory().consumeItem(810007);

        if (pc.getHeading() < 0 || pc.getHeading() > 7) pc.setHeading(0);

        SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RUNE, 1);

        // 이벤트 사냥터 시간 체크


        applyPcBangBuff(pc);
        
        if (pc.PC방_버프) {
            SC_ActionUI.send(pc, SC_ActionUI.PCBANG_SET, true);
        } else {
            SC_ActionUI.send(pc, SC_ActionUI.PCBANG_SET, false);
        }

        SC_SlotOpen.send(pc, SC_ReturnedStat.SUBTYPE_RING, computeSlotOpenStatus(pc));

        if (mailCount > 0) {
            SC_SkillSound.send(pc, pc.getId(), 1091);
            SC_ServerMessage.send(pc, 428);
        }
        pc.LoadCheckStatus();
        // 보스몬스터 이벤트 알람
        BossController.sendBossEventSnapshot(pc);
        SC_SlotChange.sendCreate(pc, SC_SlotChange.SLOT_CHANGE, pc);

        applySealScroll(pc);
        if (pc.getLevel() >= 90) pc.레벨버프();
        if (pc.getReturnStat() != 0) applyReturnStatReset(pc, true);

        applyLevelBuff(pc);

        notifySupplementaryWarehouse(pc);
        pc.add_lateral_damage(data.lateralValues[0]);
        pc.add_lateral_reduction(data.lateralValues[1]);
        pc.add_lateral_magic_rate(data.lateralValues[2]);
        pc.onWorldMove(pc.getMapId(), pc.getX(), pc.getY());
        
        if (Config.USE_ATTENDANCE_SYSTEM && pc.getLevel() >= 50) {
			SC_AttendanceBonusInfoExtend.send(pc);
			SC_AttendanceBonusGroupInfo.send(pc);
			SC_AttendanceUserDataExtend.applyLoginSnapshot(pc, data.attendance);
			SC_AttendanceUserDataExtend.send(pc);
        }
        if (pc.getLevel() >= 50) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, Config.GAME_SERVER_MENT);
        }
        LoggerInstance.getInstance().addConnection("접속 캐릭=" + client.getActiveChar().getName() + "	계정=" + client.getAccountName() + "	IP=" + client.getHostname());
        
        L1CardCollection.Join(pc);
    }

    private static void finalizeEnter(L1PcInstance pc, CIOClient client) {
        if (!CheckInitStat.CheckPcStat(pc)) {
            SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);
        }
    }
    
    private static void applyCastleGetBack(L1PcInstance pc) {
        int castle_id = L1CastleLocation.getCastleIdByArea(pc);
        if (pc.getMapId() == 66) {
            castle_id = 6;
        }
        if (0 < castle_id) {
            if (MJCastleWarBusiness.getInstance().isNowWar(castle_id)) {
                L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
                if (clan != null && clan.getCastleId() != castle_id) {
                    int[] loc = L1CastleLocation.getGetBackLoc(castle_id);
                    pc.setX(loc[0]);
                    pc.setY(loc[1]);
                    pc.setMap((short) loc[2]);
                } else if (pc.getMapId() == 4) {
                    int[] loc = L1CastleLocation.getGetBackLoc(castle_id);
                    pc.setX(loc[0]);
                    pc.setY(loc[1]);
                    pc.setMap((short) loc[2]);
                }
            }
        }
    }

    private static void applyEinhasad(L1PcInstance pc) {
        if (pc.getLevel() <= 5) return;

        // Only time after the last logout is offline recovery. Using the old
        // login time also counted the online interval already handled by the
        // character timer. Keep existing internal-unit increment/cap policies.
        final java.sql.Timestamp loggedOut = pc.getAccount().getLastLogOut();
        final long elapsed = loggedOut == null ? 0L
                : Math.max(0L, System.currentTimeMillis() - loggedOut.getTime());
        int einhasad = pc.getAccount().recoverBlessOfAin(elapsed / (15 * 60 * 1000L), 50000000);

        if (pc.getZoneType() == 1) {
            pc.startEinhasadTimer();
        }
        if (einhasad > 10000) {
        	SC_RestExpInfo.send(pc);
        }
    }

    private static void applyDragonRaidBuff(L1PcInstance pc, long sysTime) {
        if (pc.getNetConnection() == null || pc.getNetConnection().getAccount() == null) return;
        if (pc.getNetConnection().getAccount().getDragonRaid() == null) return;

        if (sysTime <= pc.getNetConnection().getAccount().getDragonRaid().getTime()) {
            long bloodTime = pc.getNetConnection().getAccount().getDragonRaid().getTime() - sysTime;
            pc.removeSkillEffect(L1SkillId.DRAGONRAID_BUFF);
            pc.setSkillEffect(L1SkillId.DRAGONRAID_BUFF, (int) bloodTime);
            SC_PacketBox.send(pc, SC_PacketBox.DRAGONRAID_BUFF, (int) bloodTime / 1000);
        }
    }

    private static void applyPcBangBuff(L1PcInstance pc) {
        if (pc.getAccount().getBuff_PC방() != null) {
            final long nowMs = System.currentTimeMillis();
            if (nowMs <= pc.getAccount().getBuff_PC방().getTime()) {
                long remain = pc.getAccount().getBuff_PC방().getTime() - nowMs;

                long sec = remain / 1000L;
                if (sec < 0) sec = 0;

                long d = sec / 86400L; sec %= 86400L;
                long h = sec / 3600L;  sec %= 3600L;
                long m = sec / 60L;    sec %= 60L;

                StringBuilder sb = new StringBuilder(128);
                sb.append("PC방 정량 잔여시간: ");
                if (d > 0) sb.append(d).append("일 ").append(h).append("시간 ").append(m).append("분 ").append(sec).append("초 남았습니다.");
                else if (h > 0) sb.append(h).append("시간 ").append(m).append("분 ").append(sec).append("초 남았습니다.");
                else if (m > 0) sb.append(m).append("분 ").append(sec).append("초 남았습니다.");
                else sb.append(sec).append("초 남았습니다.");

                SC_SystemMessage.send(pc, sb.toString());
                pc.setPC방_버프(true);
                SC_PacketBox.send(pc, SC_PacketBox.PC방버프, 1);
                return;
            }
        }
        pc.setPC방_버프(false);
    }

    private static int computeSlotOpenStatus(L1PcInstance pc) {
        int slotStatus = 0;
        if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT76))   slotStatus |= SC_ReturnedStat.OPEN_SLOT_LRING;
        if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT81))   slotStatus |= SC_ReturnedStat.OPEN_SLOT_RRING;
        if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT59))   slotStatus |= SC_ReturnedStat.OPEN_SLOT_EARRING;
        if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT_SHOULD)) slotStatus |= SC_ReturnedStat.OPEN_SLOT_SHOULD;
        if (pc.getQuest().isEnd(L1Quest.QUEST_SLOT_BADGE))  slotStatus |= SC_ReturnedStat.OPEN_SLOT_BADGE;
        return slotStatus;
    }

    private static void applySealScroll(L1PcInstance pc) {
        if (pc.getSealScrollTime() <= 0) return;

        int currentTime = (int) (System.currentTimeMillis() / 1000);
        if (pc.getSealScrollTime() < currentTime) {
            pc.getInventory().storeItem(50021, pc.getSealScrollCount());
            pc.setSealScrollTime(0);
            pc.setSealScrollCount(0);
            pc.save();
            SC_ChatPacket.sendDirect(pc, pc, "봉인해제 주문서가 지급되었습니다.", 1);
        } else {
            int remainMin = (pc.getSealScrollTime() - currentTime) / 60 + 1;
            int remainHour = remainMin / 60;
            remainMin -= remainHour * 60;
            int remainDay = remainHour / 24;
            remainHour -= remainDay * 24;
            SC_ChatPacket.sendDirect(pc, pc, String.format("봉인해제주문서 지급까지 %d일 %d시간 %d분 남았습니다.", remainDay, remainHour, remainMin), 1);
        }
    }

    private static void applyLevelBuff(L1PcInstance pc) {
        if (pc.getLevel() > 1 && pc.getLevel() <= Config.Lineage_Buff) {
            SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.HUNTER_BLESS3, 4126, true);
        } else {
            SC_InventoryIcon.icoEnd(pc, L1SkillId.HUNTER_BLESS3);
        }
    }

    private static void notifySupplementaryWarehouse(L1PcInstance pc) {
        SupplementaryService warehouse = WarehouseManager.getInstance()
                .getSupplementaryService(pc.getAccountName());
        if (warehouse.getSize() > 0) {
            SC_SystemMessage.send(pc, "부가 아이템 창고 : 수령하지 않은 아이템이 있습니다. 부가 아이템 창고 확인.");
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "부가 아이템 창고 : 수령하지 않은 아이템이 있습니다.\\aA 부가 아이템 창고 확인.");
        }
    }
    
    private static void banLoginAccountAsync(String account, int reason) {
        final String name = account;
        CacheD.getInstance().submitDbJobOrdered(CacheD.accountOrderKey(name), () -> Account.ban(name, reason));
    }

    private static CachedRowSet[] readWarehouseRows(String accountName, String charName) throws SQLException {
        final String[] sql = {
                "SELECT * FROM character_warehouse WHERE account_name = ?",
                "SELECT * FROM character_elf_warehouse WHERE account_name = ?",
                "SELECT * FROM character_package_warehouse WHERE account_name = ?",
                "SELECT * FROM character_supplementary_service WHERE account_name = ?",
                "SELECT * FROM character_special_warehouse WHERE char_name = ?"
        };
        final CachedRowSet[] rows = new CachedRowSet[5];
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            for (int i = 0; i < rows.length; i++) {
                try (PreparedStatement ps = con.prepareStatement(sql[i])) {
                    ps.setString(1, i == 4 ? charName : accountName);
                    try (ResultSet rs = ps.executeQuery()) {
                        rows[i] = copyWarehouseRows(rs);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            for (CachedRowSet row : rows) {
                if (row != null) try { row.close(); } catch (SQLException ignored) {}
            }
            throw e;
        }
        return rows;
    }

    private static CachedRowSet copyWarehouseRows(ResultSet rs) throws SQLException {
        final ResultSetMetaData sourceMeta = rs.getMetaData();
        final int count = sourceMeta.getColumnCount();
        final RowSetMetaDataImpl copyMeta = new RowSetMetaDataImpl();
        final boolean[] integral = new boolean[count + 1];
        copyMeta.setColumnCount(count);
        for (int i = 1; i <= count; i++) {
            final int type = sourceMeta.getColumnType(i);
            integral[i] = type == Types.BIT || type == Types.BOOLEAN || type == Types.TINYINT;
            copyMeta.setColumnName(i, sourceMeta.getColumnLabel(i));
            copyMeta.setColumnType(i, integral[i] ? Types.INTEGER : type);
            copyMeta.setNullable(i, sourceMeta.isNullable(i));
        }

        final CachedRowSet copy = RowSetProvider.newFactory().createCachedRowSet();
        try {
            copy.setMetaData(copyMeta);
            while (rs.next()) {
                copy.afterLast();
                copy.moveToInsertRow();
                for (int i = 1; i <= count; i++) {
                    if (integral[i]) {
                        final int value = rs.getInt(i);
                        if (rs.wasNull()) copy.updateNull(i);
                        else copy.updateInt(i, value);
                    } else {
                        final Object value = rs.getObject(i);
                        if (value == null) copy.updateNull(i);
                        else copy.updateObject(i, value);
                    }
                }
                copy.insertRow();
                copy.moveToCurrentRow();
            }
            copy.beforeFirst();
            return copy;
        } catch (SQLException | RuntimeException e) {
            try { copy.close(); } catch (SQLException ignored) {}
            throw e;
        }
    }

    private static void loadItems(L1PcInstance pc, boolean sendOption,
                                  List<L1ItemInstance> loadedItems) {
        if (sendOption) {
            pc.getInventory().sendOptioon();
            RankUserLoader.getInstance().onUser(pc);
        } else {
            CharacterTable.getInstance().restoreInventory(pc, loadedItems);
        }
    }

	private static void safetyzone(L1PcInstance pc) {
		if (pc.getZoneType() == 0) {
			if (pc.getSafetyZone() == true) {
				SC_ActionUI.send(pc, SC_ActionUI.SAFETYZONE, false);
				pc.setSafetyZone(false);
			}
		} else {
			if (pc.getSafetyZone() == false) {
				SC_ActionUI.send(pc, SC_ActionUI.SAFETYZONE, true);
				pc.setSafetyZone(true);
			}
		}
	}

	private static int CheckMail(L1PcInstance pc) throws SQLException {
		int count = 0;
		String sql = "SELECT count(*) as cnt FROM letter WHERE receiver = ? AND isCheck = 0";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, pc.getName());
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					count = rs.getInt("cnt");
				}
			}
		}

		return count;
	}

	private static int[] loadSkills(L1PcInstance pc, List<Integer> mastery) throws SQLException {
		int[] skillList = new int[30]; 

		String sql = "SELECT skill_id FROM character_skills WHERE char_obj_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, pc.getId());
			try (ResultSet rs = pstm.executeQuery()) {

				while (rs.next()) {
					int skillId = rs.getInt("skill_id");
					L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);

					if (l1skills != null && l1skills.getSkillLevel() > 0 && l1skills.getSkillLevel() <= 29) {
						if (skillId == 234) {
							skillList[l1skills.getSkillLevel()] |= l1skills.getId();
						} else {
							skillList[l1skills.getSkillLevel() - 1] |= l1skills.getId();
						}
					}

					mastery.add(skillId);
				}
			}
		}

		return skillList;
	}

	private static void sendSkillPacket(L1PcInstance pc, int[] skillList) {
		SC_AddSkill.send(pc, skillList[0], skillList[1], skillList[2], skillList[3], skillList[4],
				skillList[5], skillList[6], skillList[7], skillList[8], skillList[9], skillList[10], skillList[11],
				skillList[12], skillList[13], skillList[14], skillList[15], skillList[16], skillList[17], skillList[18],
				skillList[19], skillList[20], skillList[21], skillList[22], skillList[23], skillList[24], skillList[25],
				skillList[26], skillList[27], skillList[28], skillList[29], pc.getElfAttr());
	}

	private static void 보안버프(L1PcInstance pc) {
		pc.getAC().addAc(-1);
		SC_PacketBox.send(pc, pc, SC_PacketBox.ICON_SECURITY_SERVICES);
		SC_OwnCharStatus.send(pc);
	}

	private static void serchSummon(L1PcInstance pc) {
		try {
			for (L1SummonInstance summon : L1World.getInstance().getAllSummons()) {
				if (summon.getMaster().getId() == pc.getId()) {
					summon.setMaster(pc);
					pc.addPet(summon);
					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(summon)) {
						SC_WorldPutObjectNoti.sendNewInstance(visiblePc, summon, visiblePc);
					}
				}
			}
		} catch (Exception e) {
		}
	}


	private static List<BuffInfo> loadBuff(L1PcInstance pc) throws SQLException {
		List<BuffInfo> buffList = new ArrayList<>();
		String sql = "SELECT skill_id, remaining_time, poly_id FROM character_buff WHERE char_obj_id = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, pc.getId());
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					BuffInfo buffInfo = new BuffInfo();
					buffInfo.skillId = rs.getInt("skill_id");
					buffInfo.remainTime = rs.getInt("remaining_time");
					buffInfo.polyId = rs.getInt("poly_id");
					buffList.add(buffInfo);
				}
			}
		}

		return buffList;
	}

	private static void 스텟초기화(L1PcInstance pc) {
		try {
			L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(), null, 0,
					L1SkillUse.TYPE_LOGIN);
			

			if (pc.getWeapon() != null) {
				pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
			}

			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
			SC_OwnCharStatus2.send(pc);

			for (L1ItemInstance armor : pc.getInventory().getItems()) {
				for (int type = 0; type <= 12; type++) {
					if (armor != null) {
						pc.getInventory().setEquipped(armor, false, false, false, false);
					}
				}
			}
			pc.setReturnStat(pc.getExp());
			pc.save();
		} catch (Exception e) {
			System.out.println("스텟초기화 명령어 에러");
			e.printStackTrace();
		}
	}

	private static void processBuff(L1PcInstance pc, List<BuffInfo> buffList) {
	    int[] icon = new int[39];

	    BuffRestoreRegistryInit.init();
	    for (BuffInfo buffInfo : buffList) {

	        final int skillid = buffInfo.skillId;
	        int remaining_time = buffInfo.remainTime;

	        // =====================================================
	        // 요리 / 스프 / 특수 요리
	        // =====================================================
	        if (CookingRegistry.containsSkill(skillid) || skillid == 1541) {
	            if (CookingRegistry.restoreLogin(pc, skillid, remaining_time)) {
	                pc.setSkillEffect(skillid, remaining_time * 1000);
	                continue;
	            }
	        }
	        // =====================================================
	        // 일반 버프
	        // =====================================================
	        if (skillid == L1SkillId.SetBuff) {
	            remaining_time = 30;
	        }

	        BuffRestoreRegistry.find(skillid).restore(pc, skillid, buffInfo.polyId, remaining_time, icon);
	        pc.setSkillEffect(skillid, remaining_time * 1000);
	    }

	    SC_UnityIcon.send(pc, icon);
	}


    private static List<ExcludeRow> readExcludeList(int charId) throws SQLException {
        final ArrayList<ExcludeRow> rows = new ArrayList<>();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(
                     "SELECT type, exclude_name FROM character_exclude WHERE char_id = ?")) {
            pstm.setInt(1, charId);
            try (ResultSet rs = pstm.executeQuery()) {
                while (rs.next()) rows.add(new ExcludeRow(rs.getInt("type"), rs.getString("exclude_name")));
            }
        }
        return rows;
    }

    private static void applyExcludeList(L1ExcludingList exList, List<ExcludeRow> rows) {
        if (exList == null || rows == null) return;
        for (int i = 0, n = rows.size(); i < n; i++) {
            final ExcludeRow row = rows.get(i);
            if (!exList.contains(row.type, row.name)) exList.add(row.type, row.name);
        }
    }
    private static int[] readLateralSnapshot(int characterId) throws SQLException {
        final int[] values = new int[3];
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "select lateral_damage, lateral_reduction, lateral_magic_rate "
                     + "from tb_lateral_status where character_id=?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values[0] += rs.getInt(1);
                    values[1] += rs.getInt(2);
                    values[2] += rs.getInt(3);
                }
            }
        }
        return values;
    }

    private static long[] readNBuffSnapshot(int charId, int accountId) throws SQLException {
        final boolean byAccount = Config.ACCOUNT_N_BUFF;
        final String sql = byAccount
                ? "SELECT skill_id, char_id, expiration_time FROM character_tams WHERE skill_id BETWEEN ? AND ? AND account_id=?"
                : "SELECT skill_id, char_id, expiration_time FROM character_tams WHERE skill_id BETWEEN ? AND ? AND char_id=?";
        long[] values = new long[8];
        int count = 0;
        final long now = System.currentTimeMillis();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, 4075);
            ps.setInt(2, 4095);
            ps.setInt(3, byAccount ? accountId : charId);
            try (ResultSet rs = ps.executeQuery()) {
                PreparedStatement delete = null;
                try {
                    while (rs.next()) {
                        Timestamp expiry = rs.getTimestamp("expiration_time");
                        if (expiry == null) continue;
                        int skillId = rs.getInt("skill_id");
                        long expiresAt = expiry.getTime();
                        if (expiresAt <= now) {
                            if (delete == null) delete = con.prepareStatement(
                                    "DELETE FROM character_tams WHERE char_id=? AND skill_id=?");
                            delete.setInt(1, rs.getInt("char_id"));
                            delete.setInt(2, skillId);
                            delete.addBatch();
                        } else {
                            if (count == values.length) {
                                long[] bigger = new long[values.length << 1];
                                System.arraycopy(values, 0, bigger, 0, count);
                                values = bigger;
                            }
                            values[count++] = skillId;
                            values[count++] = expiresAt;
                        }
                    }
                    if (delete != null) delete.executeBatch();
                } finally {
                    if (delete != null) delete.close();
                }
            }
        }
        if (count == values.length) return values;
        long[] result = new long[count];
        System.arraycopy(values, 0, result, 0, count);
        return result;
    }

    private static void applyNBuffSnapshot(L1PcInstance pc, long[] rows) {
        if (rows == null) return;
        final long now = System.currentTimeMillis();
        for (int i = 0; i + 1 < rows.length; i += 2) {
            int skillId = (int) rows[i];
            if (pc.hasSkillEffect(skillId)) continue;
            long remaining = (rows[i + 1] - now) / 1000L;
            if (remaining <= 0L) continue;
            int seconds = (int) Math.min(remaining, Integer.MAX_VALUE / 1000L);
            L1SkillUse.cast(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, seconds, 1);
        }
    }

public static void loadNBuff(L1PcInstance pc) {
		boolean isAccountBuff = Config.ACCOUNT_N_BUFF;
		String sql = isAccountBuff ?
				"SELECT * FROM character_tams WHERE skill_id BETWEEN ? AND ? AND account_id=?" :
					"SELECT * FROM character_tams WHERE skill_id BETWEEN ? AND ? AND char_id=?";

		long now = System.currentTimeMillis();

		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, 4075);
			pstm.setInt(2, 4095);
			pstm.setInt(3, isAccountBuff ? pc.getAccount().getAccountId() : pc.getId());

			try (ResultSet rs = pstm.executeQuery()) {
				PreparedStatement delPs = null;  
				try {
					while (rs.next()) {
						int skillId = rs.getInt("skill_id");
						int rowCharId = rs.getInt("char_id");
						Timestamp expirationTime = rs.getTimestamp("expiration_time");

						if (expirationTime == null) {
							continue;
						}
						if (expirationTime.getTime() <= now) {
							if (delPs == null) {
								delPs = con.prepareStatement("DELETE FROM character_tams WHERE char_id=? AND skill_id=?");
							}
							delPs.setInt(1, rowCharId);
							delPs.setInt(2, skillId);
							delPs.addBatch();
							continue;
						}
						if (pc.hasSkillEffect(skillId)) {
							continue;
						}
						// Preserve a positive skill lifetime even for persisted 30-day N-shop buffs.
                        // The current skill API takes int milliseconds internally.
                        long remainingSeconds = (expirationTime.getTime() - now) / 1000L;
                        if (remainingSeconds <= 0L) continue;
                        int time = (int) Math.min(remainingSeconds, Integer.MAX_VALUE / 1000L);
                        L1SkillUse.cast(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, time, 1);
					}
					if (delPs != null) {
						delPs.executeBatch();  
					}
				} finally {
					if (delPs != null) delPs.close();
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "loadNBuff error [PC: " + pc.getName() + "]", e);
		}
	}

	private static final int INVENTORY_NOTI_CHUNK = 40;
	private static final int INVENTORY_NOTI_MAX_BYTES = 16 * 1024;

	private static void sendItemPacketChunked(L1PcInstance pc) {
	    if (pc == null) return;

	    List<L1ItemInstance> items = pc.getInventory().getItems();
	    if (items == null || items.isEmpty()) return;

	    final L1ItemInstance[] snap = items.toArray(new L1ItemInstance[0]);
	    final int total = snap.length;
	    if (total <= 0) return;

	    final boolean ON_START = true;

	    final byte[][] encoded = new byte[total][];
	    for (int i = 0; i < total; i++) {
	        L1ItemInstance item = snap[i];
	        if (item == null || item.getItem() == null) continue;
	        try {
	            encoded[i] = ItemInfo.encodeItemBody(item);
	        } catch (Throwable t) {
	            _log.log(Level.WARNING, "[INV] ItemInfo encode failed: pc=" + pc.getName()
	                + " objId=" + safeObjId(item) + " itemId=" + safeItemId(item) + " name=" + safeName(item), t);
	        }
	    }

	    int sentTotal = 0;
	    int chunkNo = 0;
	    int cntInChunk = 0;
	    int bytesInChunk = 0;
	    L1ItemInstance lastItem = null;

	    int chunkStart = 0;

	    for (int i = 0; i < total; i++) {
	        L1ItemInstance item = snap[i];
	        final byte[] body = encoded[i];
	        if (body == null) continue;

	        final int addBytes = ItemInfo.encodedWireSize(body);

	        // 단일 아이템이 청크 한도 초과 → 독립 전송
	        if (addBytes >= INVENTORY_NOTI_MAX_BYTES) {
	            if (cntInChunk > 0) {
	                flushInvChunk(pc, encoded, chunkStart, i, ++chunkNo, cntInChunk, bytesInChunk, sentTotal, total, lastItem);
	                sentTotal += cntInChunk;
	                chunkStart = i;
	                cntInChunk = 0;
	                bytesInChunk = 0;
	                lastItem = null;
	            }

	            try {
	                SC_AddInventoryNoti.sendEncodedItem(pc, body, ON_START, pc.getId());
	            } catch (Throwable t) {
	                _log.log(Level.SEVERE, "[INV] SINGLE send failed: pc=" + pc.getName()
	                    + " addBytes=" + addBytes
	                    + " objId=" + safeObjId(item)
	                    + " itemId=" + safeItemId(item)
	                    + " cnt=" + safeCount(item)
	                    + " ench=" + safeEnchant(item)
	                    + " attr=" + safeAttr(item)
	                    + " bless=" + safeBless(item)
	                    + " name=" + safeName(item), t);
	                return;
	            }
	            sentTotal += 1;
	            chunkStart = i + 1;
	            continue;
	        }

	        // 청크 한도 도달 → flush 후 새 청크
	        if (cntInChunk > 0 && (cntInChunk >= INVENTORY_NOTI_CHUNK || (bytesInChunk + addBytes) >= INVENTORY_NOTI_MAX_BYTES)) {
	            flushInvChunk(pc, encoded, chunkStart, i, ++chunkNo, cntInChunk, bytesInChunk, sentTotal, total, lastItem);
	            sentTotal += cntInChunk;
	            chunkStart = i;
	            cntInChunk = 0;
	            bytesInChunk = 0;
	            lastItem = null;
	        }

	        // The encoded snapshot already owns this entry; no per-chunk list node.
	        cntInChunk++;
	        bytesInChunk += addBytes;
	        lastItem = item;
	    }

	    if (cntInChunk > 0) {
	        flushInvChunk(pc, encoded, chunkStart, total, ++chunkNo, cntInChunk, bytesInChunk, sentTotal, total, lastItem);
	        sentTotal += cntInChunk;
	    }
	}

	private static void flushInvChunk(L1PcInstance pc,
	        byte[][] encoded, int from, int to,
	        int chunkNo,
	        int cntInChunk,
	        int bytesInChunk,
	        int sentBefore,
	        int total,
	        L1ItemInstance lastItem) {
	    try {
            SC_AddInventoryNoti.sendEncoded(pc, encoded, from, to, true, pc.getId());
	    } catch (Throwable t) {
	        _log.log(Level.SEVERE, "[INV] send failed on chunk#" + chunkNo
	                + " pc=" + (pc != null ? pc.getName() : "null") + "(" + (pc != null ? pc.getId() : 0) + ")"
	                + " cnt=" + cntInChunk
	                + " bytes=" + bytesInChunk
	                + " progress=" + sentBefore + "/" + total
	                + " last=" + (lastItem != null
	                ? ("objId=" + safeObjId(lastItem)
	                + " itemId=" + safeItemId(lastItem)
	                + " cnt=" + safeCount(lastItem)
	                + " ench=" + safeEnchant(lastItem)
	                + " attr=" + safeAttr(lastItem)
	                + " bless=" + safeBless(lastItem)
	                + " name=" + safeName(lastItem))
	                        : "null")
	                , t);
	        throw t;
	    }
	}

	private static void applyReturnStatReset(L1PcInstance pc, boolean doSave) {
		try {
			
			L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_LOGIN);

			if (pc.getWeapon() != null) {
				pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
			}
			for (L1ItemInstance armor : pc.getInventory().getItems()) {
				if (armor != null && armor.isEquipped()) {
					pc.getInventory().setEquipped(armor, false, false, false, false);
				}
			}
			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);

			if (doSave) {
				try { pc.save(); } catch (Exception e) { System.out.println("스텟초기화에러"); }
			}
		} catch (Throwable t) {
			System.out.println("[ReturnStatReset] fail char=" + pc.getName());
			t.printStackTrace();
		}
	}



	private static int safeObjId(L1ItemInstance it) { try { return it != null ? it.getId() : 0; } catch (Throwable t) { return 0; } }
	private static int safeItemId(L1ItemInstance it) { try { return it != null ? it.getItemId() : 0; } catch (Throwable t) { return 0; } }
	private static int safeCount(L1ItemInstance it) { try { return it != null ? it.getCount() : 0; } catch (Throwable t) { return 0; } }
	private static int safeEnchant(L1ItemInstance it) { try { return it != null ? it.getEnchantLevel() : 0; } catch (Throwable t) { return 0; } }
	private static int safeAttr(L1ItemInstance it) { try { return it != null ? it.getAttrEnchantLevel() : 0; } catch (Throwable t) { return 0; } }
	private static int safeBless(L1ItemInstance it) { try { return it != null ? it.getBless() : 0; } catch (Throwable t) { return 0; } }
	private static String safeName(L1ItemInstance it) { try { return it != null ? String.valueOf(it.getName()) : "null"; } catch (Throwable t) { return "err"; } }
}
