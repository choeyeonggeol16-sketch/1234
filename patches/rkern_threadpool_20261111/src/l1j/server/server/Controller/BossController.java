package l1j.server.server.Controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Boss;
import l1j.server.server.utils.SystemUtil;

/**
 * 보스 스폰 컨트롤러.
 */
public final class BossController {

    private static final Logger _log = Logger.getLogger(BossController.class.getName());


    /*
     * 기존 ThreadPool schedule 작업을 직접 cancel하지 않고
     * 버전이 다른 작업은 실행 시점에 폐기한다.
     */
    private static final AtomicInteger SCHEDULE_VERSION = new AtomicInteger();

    private BossController() {
    }

    public static void start() {
        final int version = SCHEDULE_VERSION.incrementAndGet();
        scheduleAll(version, "START");
    }

    /**
     * DB의 spawnlist_boss_hot을 다시 읽고
     * 기존 예약을 무효화한 뒤 새 시간으로 전부 재예약한다.
     */
    public static boolean reload() {
        try {
            // Only invalidate existing delayed callbacks after a complete,
            // successful DB reload. Failed reload leaves the old schedule live.
            BossMonsterSpawnList.init();
            final int version = SCHEDULE_VERSION.incrementAndGet();
            scheduleAll(version, "RELOAD");
            return true;
        } catch (Throwable t) {
            _log.log(Level.SEVERE, "[BossController] 보스 스케줄 리로드 실패", t);
            return false;
        }
    }

    private static void scheduleAll(final int version, final String reason) {
        final List<L1Boss> list = BossMonsterSpawnList.getList();

        if (list == null || list.isEmpty()) {
            _log.warning("[BossController] 등록된 보스 스케줄이 없습니다.");
            return;
        }

     //   int count = 0;

        for (final L1Boss boss : list) {
            if (boss == null || boss.getNpcId() <= 0) {
                continue;
            }
            scheduleNext(boss, version);
         //   count++;
        }

     //   _log.info(String.format("[BossController] %s 보스 스케줄 %d건 등록 / version=%d", reason, count, version));
    }

    /**
     * 다음 스폰 예약.
     */
    private static void scheduleNext(final L1Boss boss, final int version) {
        /*
         * 리로드 이전 객체가 재예약하려는 경우 차단.
         */
        if (version != SCHEDULE_VERSION.get()) {
            return;
        }

        final long delayMs = nextSpawnDelayMs(boss);

        if (delayMs < 0L) {
        //    _log.warning(String.format("[BossController] 스폰 시각 계산 실패 npcId=%d name=%s version=%d — 10분 후 재시도",
      //              boss.getNpcId(), boss.getMonName(), version));
//
            ThreadPool.getInstance().scheduleRequired(new Runnable() {
                @Override
                public void run() {
                    if (version != SCHEDULE_VERSION.get()) {
                        return;
                    }
                    scheduleNext(boss, version);
                }
            }, 600_000L);
            return;
        }

        long jitterMs = 0L;
        final int noneSpawn = boss.getnonespawntime();

        if (noneSpawn > 0) {
            final long maxJitterMs = (long) noneSpawn * 60_000L;
            jitterMs = ThreadLocalRandom.current().nextLong(1L, maxJitterMs + 1L);
        }

        final long finalDelay = delayMs + jitterMs;

        ThreadPool.getInstance().scheduleRequired(new Runnable() {
            @Override
            public void run() {
                /*
                 * DB reload 이후
                 * 예전 예약이면 여기서 끝.
                 */
                if (version != SCHEDULE_VERSION.get()) {
            //        _log.fine(String.format("[BossController] stale schedule skip npcId=%d version=%d current=%d",
            //                boss.getNpcId(), version, SCHEDULE_VERSION.get()));
                    return;
                }
                spawnAndReschedule(boss, version);
            }
        }, finalDelay);

        final long totalSec = finalDelay / 1000L;

    //    _log.info(String.format("[BossController] NEXT npcId=%d name=%s / %d분 %d초 후 / version=%d",
    //            boss.getNpcId(), boss.getMonName(), totalSec / 60L, totalSec % 60L, version));

        if (jitterMs > 0L) {
            notifyGm(String.format("\\f3[GM] \\f9[ %s ] \\f3%d분 %d초 후 \\f9등장합니다.",
                    boss.getMonName(), totalSec / 60L, totalSec % 60L));
        }
    }

    /**
     * 가장 가까운 다음 스폰 시간 계산.
     *
     * DB spawn_time 순서가 뒤죽박죽이어도
     * 실제 가장 가까운 미래 시간을 선택한다.
     */
    private static long nextSpawnDelayMs(final L1Boss boss) {
        final int[][] times = boss.getTime();

        if (times == null || times.length == 0) {
            return -1L;
        }

        final String[] yoil = boss.getYoil();

        if (yoil == null || yoil.length == 0) {
            return -1L;
        }

        final long now = System.currentTimeMillis();
        final Calendar day = Calendar.getInstance();

        for (int d = 0; d < 8; d++) {
            final String yoilToday = SystemUtil.getYoil(day.getTimeInMillis());
            boolean yoilOk = false;

            for (final String y : yoil) {
                if (y == null) {
                    continue;
                }
                if ("전체".equalsIgnoreCase(y.trim()) || yoilToday.equalsIgnoreCase(y.trim())) {
                    yoilOk = true;
                    break;
                }
            }

            if (yoilOk) {
                long bestDelay = Long.MAX_VALUE;

                for (final int[] time : times) {
                    if (time == null || time.length < 2) {
                        continue;
                    }

                    final int hour = time[0];
                    final int minute = time[1];

                    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                        continue;
                    }

                    final Calendar target = (Calendar) day.clone();
                    target.set(Calendar.HOUR_OF_DAY, hour);
                    target.set(Calendar.MINUTE, minute);
                    target.set(Calendar.SECOND, 0);
                    target.set(Calendar.MILLISECOND, 0);

                    final long delay = target.getTimeInMillis() - now;

                    if (delay > 0L && delay < bestDelay) {
                        bestDelay = delay;
                    }
                }

                if (bestDelay != Long.MAX_VALUE) {
                    return bestDelay;
                }
            }

            day.add(Calendar.DAY_OF_MONTH, 1);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
        }

        return -1L;
    }

    private static void spawnAndReschedule(final L1Boss boss, final int version) {
        /*
         * 리로드 전 예약이면 절대 스폰하지 않는다.
         */
        if (version != SCHEDULE_VERSION.get()) {
            return;
        }

        try {
            final L1MonsterInstance bossMon = boss_spawn(
                    boss, boss.getX(), boss.getY(), (short) boss.getMap(),
                    boss.getNpcId(), boss.getRndLoc(), 3600000, boss.isMent(),
                    boss.isYn(), boss.get_display_effect());

            if (bossMon == null) {
                _log.warning(String.format("[BossController] 스폰 실패 npcId=%d name=%s — 10분 후 재시도",
                        boss.getNpcId(), boss.getMonName()));

                ThreadPool.getInstance().scheduleRequired(new Runnable() {
                    @Override
                    public void run() {
                        if (version != SCHEDULE_VERSION.get()) {
                            return;
                        }
                        spawnAndReschedule(boss, version);
                    }
                }, 600_000L);
                return;
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "[BossController] 스폰 예외 npcId=" + boss.getNpcId(), e);
        }

        /*
         * 스폰 도중 reload가 됐으면
         * 구버전 객체로 다음 예약하지 않는다.
         */
        if (version == SCHEDULE_VERSION.get()) {
            scheduleNext(boss, version);
        }
    }

    private static void notifyGm(final String msg) {
        for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            if (pc != null && pc.isGm()) {
                SC_SystemMessage.send(pc, msg);
            }
        }
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
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
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
            final L1NpcInstance npc = L1World.getInstance().findNpcByNpcId(npcTemplateId);
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
        for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
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
    
    
    public static L1MonsterInstance boss_spawn(
            final L1Boss b, final int x, final int y, final short map,
            final int npcId, final int randomRange, final int timeMillisToDelete,
            final boolean ment, final boolean ynment, final int display_effect) {

        L1NpcInstance npc = null;

        try {
            npc = NpcTable.getInstance().newNpcInstance(npcId);

            if (npc == null) {
                _log.warning("[BossController] NPC template 없음 npcId=" + npcId);
                return null;
            }

            npc.setId(IdFactory.getInstance().nextId());
            npc.setMap(map);

            if (randomRange == 0) {
                npc.getLocation().set(x, y, map);
            } else {
                int tryCount = 0;
                do {
                    tryCount++;
                    npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
                    npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));

                    if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
                        break;
                    }
                } while (tryCount < 50);

                if (tryCount >= 50) {
                    npc.getLocation().set(x, y, map);
                    npc.getLocation().forward(5);
                }
            }

            if (npc.getNpcId() == 900007 || npc.getNpcId() == 900015
                    || npc.getNpcId() == 900036 || npc.getNpcId() == 900219) {
                for (final L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(npc)) {
                    npc.onPerceive(pc);
                    SC_DoActionGFX.send(pc.getNetConnection(), npc.getId(), ActionCodes.ACTION_AxeWalk);
                }
            }

            npc.setHomeX(npc.getX());
            npc.setHomeY(npc.getY());
            npc.setHeading(5);

            L1World.getInstance().storeAndEnterWorld(npc);

            sendBossEventSystem(npc);

            if (ment) {
                SC_SystemMessage.broadcast(b.getMentMessage());
                SC_PacketBox.broadcastGreen(b.getMentMessage());
            }

            if (ynment && !Config.STANDBY_SERVER) {
                if (display_effect > 0) {
                    
                    for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                        if (pc == null || pc.isPrivateShop() || !pc.isBossNotify()
                                || !pc.is_world() || !pc.getMap().isEscapable()) {
                            continue;
                        }
                        SC_DisplayEffect.send(pc, display_effect);
                        pc.setBossYN(npc.getNpcId());
                        SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_BOSS, 622, b.getYnMessage());
                    }
                }

                final int spawnedNpcId = npc.getNpcId();
                ThreadPool.getInstance().scheduleRequired(new Runnable() {
                    @Override
                    public void run() {
                        for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                            if (pc != null && pc.getBossYN() == spawnedNpcId) {
                                pc.setBossYN(0);
                            }
                        }
                    }
                }, 10000L);
            }

            npc.getLight().turnOnOffLight();
            npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);

            if (timeMillisToDelete > 0) {
            	npc.startTimedDelete(timeMillisToDelete);
            }

        } catch (Exception e) {
            _log.log(Level.WARNING, "[BossController] boss_spawn 오류 npcId=" + npcId, e);
        }

        return npc instanceof L1MonsterInstance ? (L1MonsterInstance) npc : null;
    }
}