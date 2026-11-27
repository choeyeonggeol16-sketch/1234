package l1j.server.server.model.npc.spawn.boss;

import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.npc.spawn.boss.CNpcRespawnStore.Entry;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.shared.GameRandom;

/**
 * 보스 스폰 컨트롤러.
 */
public final class CBossNpcMaker {

    private static final Logger _log = Logger.getLogger(CBossNpcMaker.class.getName());

    private static final int BOSS_LIFETIME_MS = 3_600_000;
    private static final CRespawnTimer TIMER = new CRespawnTimer(Clock.systemDefaultZone(), new CRespawnTimer.Store() {
        public Map<CBossSpawnKey, Entry> loadAll() throws SQLException {
            return CNpcRespawnStore.loadAll();
        }
        public boolean reconcile(Map<CBossSpawnKey, Entry> expected, Map<CBossSpawnKey, Entry> changes) throws SQLException {
            return CNpcRespawnStore.reconcile(expected, changes);
        }
        public boolean dispatch(CBossSpawnKey key, Entry expected, Entry next, BooleanSupplier publish) throws SQLException {
            return CNpcRespawnStore.dispatch(key, expected, next, publish);
        }
    }, (callback, delay) -> CWorldWork.scheduleRequiredOnWorldClock(callback, delay)::cancel,
            CBossNpcMaker::spawnForCalendar, CBossNpcMaker::notifySchedule);

    private static CServerTimer recoveryTimer;
    private CBossNpcMaker() {}

    public static synchronized void start() {
        if (recoveryTimer != null) return;
        recoveryTimer = CWorldWork.scheduleWithFixedDelay(TIMER::repairUnarmed, 60_000L, 60_000L);
        if (recoveryTimer == null) throw new IllegalStateException("Boss timer service unavailable");
        try { loadSchedule(); }
        catch (SQLException | RuntimeException e) {
            recoveryTimer.cancel();
            recoveryTimer = null;
            throw new IllegalStateException("Cannot start the boss calendar", e);
        }
    }

    public static synchronized void stop() {
        if (recoveryTimer != null) recoveryTimer.cancel();
        recoveryTimer = null;
        TIMER.stop();
        CBossNpcMakerDB.closeInvitations();
        BOSS_EVENTS.clear();
    }

    public static synchronized boolean reload() {
        if (recoveryTimer == null) return false;
        try {
            loadSchedule();
            return true;
        } catch (SQLException | RuntimeException e) {
            _log.log(Level.SEVERE, "Cannot reload boss schedule", e);
            return false;
        }
    }

    private static void loadSchedule() throws SQLException {
        var loaded = CBossNpcMakerDB.read();
        TIMER.replace(loaded.rows(), () -> CBossNpcMakerDB.install(loaded));
    }

    private static Runnable spawnForCalendar(CNpcSpawnDefine define) {
        L1MonsterInstance npc = publishBoss(define.getX(), define.getY(), (short) define.getMap(),
                define.getNpcId(), define.getRndLoc(), BOSS_LIFETIME_MS);
        if (npc == null) return null;
        return () -> announceBoss(define, npc, define.isMent(), define.isYn(), define.get_display_effect());
    }

    private static void notifySchedule(CNpcSpawnDefine define, long delayMs) {
        long seconds = Math.max(0L, delayMs) / 1000L;
        notifyGm(String.format("\\f3[GM] \\f9[ %s ] \\f3%d분 %d초 후 \\f9등장합니다.",
                define.getMonName(), seconds / 60L, seconds % 60L));
    }

    private static void notifyGm(final String msg) {
        CSharedBroadcast.sendWrittenAllGmsBestEffort(SC_SystemMessage.nativeWriter(msg));
    }

    // 1 여왕개미 2 샌드 웜 3 공성전 4는안뜸
    private static final int EVENT_BOSS_TYPE = 1;

    private static final class BossEvent {

        final L1NpcInstance npc;
        final int bossType;
        final int startTime;
        final int endTime;

        BossEvent(
                L1NpcInstance npc,
                int bossType,
                int startTime,
                int endTime) {

            this.npc = npc;
            this.bossType = bossType;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static final ConcurrentHashMap<Integer, BossEvent> BOSS_EVENTS = new ConcurrentHashMap<>();

    // Enable only after the running 2019 client confirms that it displays the two rows.
    private static volatile boolean shopAlarmsEnabled;
    private static volatile SC_ActionUI.ShopEntry adenShop = new SC_ActionUI.ShopEntry(
            SC_ActionUI.EVENT_ADEN_SHOP, "아덴 상점", null);
    private static volatile SC_ActionUI.ShopEntry generalShop = new SC_ActionUI.ShopEntry(
            SC_ActionUI.EVENT_GENERAL_SHOP, "잡화 상점", null);

    public static void handleShopAlarmCommand(L1PcInstance gm, String param) {
        if (gm == null) return;
        final String[] args = (param == null ? "" : param.trim()).split("\\s+");
        final String action = args.length == 0 ? "" : args[0];
        if ("검사".equals(action)) {
            SC_ActionUI.sendEvents(gm, SC_ActionUI.EVENT_SYSTEM, getBossEventSnapshot(), adenShop, generalShop);
            SC_SystemMessage.send(gm, "[이벤트상점] 미리보기 전송 (GM만)");
            return;
        }
        if ("켬".equals(action)) {
            shopAlarmsEnabled = true;
            broadcastBossEventSnapshot();
            SC_SystemMessage.send(gm, "[이벤트상점] 모든 접속자에게 알림 전송 활성화");
            return;
        }
        if ("끔".equals(action)) {
            shopAlarmsEnabled = false;
            for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                if (pc != null) SC_ActionUI.removeShopEvents(pc);
            }
            broadcastBossEventSnapshot();
            SC_SystemMessage.send(gm, "[이벤트상점] 상점 알림 삭제 및 전송 비활성화");
            return;
        }
        if (("아덴".equals(action) || "잡화".equals(action)) && args.length == 2) {
            final int npcTemplateId;
            try {
                npcTemplateId = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                SC_SystemMessage.send(gm, "[이벤트상점] NPC 템플릿 번호를 입력하세요.");
                return;
            }
            final L1NpcInstance npc = CWorld.getInstance().findNpcByNpcId(npcTemplateId);
            if (npc == null || npc.isDestroyed() || npc.isDead()) {
                SC_SystemMessage.send(gm, "[이벤트상점] 월드에 살아 있는 NPC를 찾지 못했습니다: " + npcTemplateId);
                return;
            }
            if ("아덴".equals(action)) {
                adenShop = new SC_ActionUI.ShopEntry(SC_ActionUI.EVENT_ADEN_SHOP, "아덴 상점", npc);
            } else {
                generalShop = new SC_ActionUI.ShopEntry(SC_ActionUI.EVENT_GENERAL_SHOP, "잡화 상점", npc);
            }
            SC_ActionUI.sendEvents(gm, SC_ActionUI.EVENT_SYSTEM, getBossEventSnapshot(), adenShop, generalShop);
            if (shopAlarmsEnabled) broadcastBossEventSnapshot();
            SC_SystemMessage.send(gm, "[이벤트상점] NPC 연결 및 알림 갱신 완료: " + npcTemplateId);
            return;
        }
        SC_SystemMessage.send(gm, "[이벤트상점] 검사 | 켬 | 끔 | 아덴 NPC번호 | 잡화 NPC번호");
    }

    private static List<SC_ActionUI.EventEntry> getBossEventSnapshot() {
        final List<SC_ActionUI.EventEntry> list = new ArrayList<>(BOSS_EVENTS.size());
        final int now = (int) (System.currentTimeMillis() / 1000L);

        for (final var entry : BOSS_EVENTS.entrySet()) {
            final BossEvent event = entry.getValue();
            if (event == null) {
                continue;
            }
            if (event.npc == null || event.npc.isDead()
                    || event.npc.isDestroyed() || event.endTime <= now) {
                BOSS_EVENTS.remove(entry.getKey(), event);
                continue;
            }
            list.add(new SC_ActionUI.EventEntry(event.npc, event.bossType, event.startTime, event.endTime));
        }
        return list;
    }

    private static void broadcastBossEventSnapshot() {
        final List<SC_ActionUI.EventEntry> snapshot = getBossEventSnapshot();
        final SC_ActionUI.ShopEntry aden = shopAlarmsEnabled ? adenShop : null;
        final SC_ActionUI.ShopEntry general = shopAlarmsEnabled ? generalShop : null;
        for (final L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
            if (pc != null) {
                SC_ActionUI.sendEvents(pc, SC_ActionUI.EVENT_SYSTEM, snapshot, aden, general);
            }
        }
    }

    private static void sendBossEventSystem(
            final L1NpcInstance npc) {

        if (npc == null || Config.STANDBY_SERVER) {
            return;
        }

        final int startTime = (int) (System.currentTimeMillis() / 1000L);

        final int endTime = startTime + 300;

        BOSS_EVENTS.put(npc.getId(), new BossEvent(npc, EVENT_BOSS_TYPE, startTime, endTime));

        broadcastBossEventSnapshot();
    }

    public static void sendBossEventSnapshot(final L1PcInstance pc) {

        if (pc == null) {
            return;
        }
        SC_ActionUI.sendEvents(pc, SC_ActionUI.EVENT_SYSTEM, getBossEventSnapshot(),
                shopAlarmsEnabled ? adenShop : null, shopAlarmsEnabled ? generalShop : null);
    }

    static L1MonsterInstance publishBoss(int x, int y, short map, int npcId,
            int range, int deleteMs) {
        L1NpcInstance created = null;
        try {
            created = CObjectDB.CreateNPC(npcId);
            if (!(created instanceof L1MonsterInstance monster)) {
                if (created != null) created.dispose();
                _log.warning("Invalid boss monster template npcId=" + npcId);
                return null;
            }
            monster.setId(IdFactory.getInstance().nextId());
            monster.setMap(map);
            return enterBoss(monster, x, y, range, deleteMs);
        } catch (RuntimeException failure) {
            if (created instanceof L1MonsterInstance monster) discardFailedSpawn(monster, failure);
            _log.log(Level.WARNING, "Boss creation failed npcId=" + npcId, failure);
            return null;
        }
    }

    static L1MonsterInstance enterBoss(L1MonsterInstance npc, int x, int y, int range, int lifetimeMs) {
        final int npcId = npc.getNpcId();
        try {
            if (!placeBoss(npc, x, y, range))
                throw new IllegalStateException("No boss spawn position at " + x + "," + y + " map=" + npc.getMapId());
            npc.setHomeX(npc.getX());
            npc.setHomeY(npc.getY());
            npc.setHeading(5);
            CWorld.getInstance().storeAndEnterWorld(npc);
            if (lifetimeMs > 0) {
                npc.startTimedDelete(lifetimeMs);
                if (npc.npcDeleteDeadlineMs() == 0 && !npc.isDead() && !npc.isDestroyed())
                    throw new IllegalStateException("Boss deletion timer was not admitted");
            }
        } catch (RuntimeException failure) {
            discardFailedSpawn(npc, failure);
            _log.log(Level.WARNING, "Boss publication failed npcId=" + npcId, failure);
            return null;
        }
        return npc;
    }

    static void announceBoss(CNpcSpawnDefine b, L1MonsterInstance npc, boolean ment, boolean ynment, int display_effect) {
        final int npcId = npc.getNpcId();
        try {
            announceSpawn(npc, b, ment, ynment, display_effect);
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Boss announcement failed npcId=" + npcId, failure);
        }
        try {
            npc.refreshLight();
            npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Boss appearance failed npcId=" + npcId, failure);
        }
    }

    static boolean placeBoss(L1MonsterInstance npc, int x, int y, int range) {
        final var location = npc.getLocation();
        final var map = location.getMap();
        location.setX(x);
        location.setY(y);
        // Fixed definitions may deliberately stand on a non-walkable tile.
        if (range == 0) return map.isInMap(location);
        if (range < 0) return false;
        for (int i = 0; i < 50; i++) {
            location.setX(x + GameRandom.nextInt(range) - GameRandom.nextInt(range));
            location.setY(y + GameRandom.nextInt(range) - GameRandom.nextInt(range));
            if (map.isInMap(location) && map.isPassable(location)) return true;
        }
        location.setX(x);
        location.setY(y);
        location.forward(5);
        if (map.isInMap(location) && map.isPassable(location)) return true;
        location.setX(x);
        location.setY(y);
        return map.isInMap(location) && map.isPassable(location);
    }

    private static void discardFailedSpawn(L1MonsterInstance npc, RuntimeException failure) {
        final CWorld world = CWorld.getInstance();
        try {
            npc.deleteMe();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
        // Entry installs the ID before regeneration and visibility; an exception
        // from entry or from normal NPC cleanup can leave that identity installed.
        try {
            if (world.findObject(npc.getId()) == npc) world.leaveAndRemoveObject(npc);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static void announceSpawn(L1MonsterInstance npc, CNpcSpawnDefine define, boolean announce,
            boolean invite, int displayEffect) {
        int npcId = npc.getNpcId();
        if (npcId == 900007 || npcId == 900015 || npcId == 900036 || npcId == 900219) {
            try (var action = new CAsyncSendConstBuffer(SC_DoActionGFX.nativeWriter(npc.getId(),
                    ActionCodes.ACTION_AxeWalk), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (L1PcInstance pc : CWorld.getInstance().getVisiblePlayerFast(npc)) {
                    npc.onPerceive(pc);
                    CSharedBroadcast.sendRecipientBestEffort(action, pc);
                }
            } catch (RuntimeException failure) {
                _log.log(Level.WARNING, "NPC spawn/action visual send failed", failure);
            }
        }
        sendBossEventSystem(npc);
        if (announce) {
            SC_SystemMessage.broadcast(define.getMentMessage());
            SC_PacketBox.broadcastGreen(define.getMentMessage());
        }
        if (invite) BossTeleportInvites.announce(define, npc, displayEffect);
    }
}
