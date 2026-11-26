package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.shared.L1Type;
import l1j.server.server.templates.L1Npc;

public class CNpcMaker {

    private static final Logger _log = Logger.getLogger(CNpcMaker.class.getName());

    /*
     * Local timer ids for the CIO-backed NpcMaker bridge.
     * These numeric ids are our implementation detail, not reference design ids.
     */
    public static final int TIMER_NPC_MAKER_WAKE = 1;
    public static final int TIMER_NPC_MAKER_RESPAWN = 2;

    private final L1Npc _template;

    private int _id;
    private String _location;
    private int _maximumCount;
    private int _npcid;
    private int _groupId;
    private int _locx;
    private int _locy;
    private int _randomx;
    private int _randomy;
    private int _locx1;
    private int _locy1;
    private int _locx2;
    private int _locy2;
    private int _heading;
    private int _minRespawnDelay;
    private int _maxRespawnDelay;

    private short _mapid;
    private boolean _respaenScreen;
    private int _movementDistance;
    private boolean _rest;
    private int _delayInterval;

    private int[] _homeX = null;
    private int[] _homeY = null;

    private String _name;

    /*
     * NpcMaker owner state.
     *
     * slot state/deadline/objectId/population counter는
     * init() 이후 해당 Maker의 CIOThread owner만 변경한다.
     *
     * 외부 death/decay thread는 owner state를 직접 쓰지 않는다.
     * slot별 primitive mailbox에 decay event만 publish하고
     * CIOThreadCallback의 owner shard를 wake한다.
     */
    private static final byte SLOT_EMPTY = 0;
    private static final byte SLOT_ACTIVE = 1;
    private static final byte SLOT_WAIT_RESPAWN = 2;
    private static final byte SLOT_SPAWNING = 3;

    private static final long DECAY_MAILBOX_EMPTY = 0L;
    private static final long DECAY_MAILBOX_CLAIMED = 1L;
    private static final long DECAY_MAILBOX_READY_BASE = 2L;

    /** 생성 실패 시 busy-loop를 피하면서 population을 다시 채우는 재시도 간격. */
    private static final long SPAWN_RETRY_MS = 1_000L;

    /** Maker 하나가 owner를 독점하지 않도록 한 번에 처리할 due slot 상한. */
    private static final int MAKER_STEP_BUDGET = 32;

    private static final AtomicIntegerFieldUpdater<CNpcMaker> NPC_MAKER_QUEUED =
            AtomicIntegerFieldUpdater.newUpdater(CNpcMaker.class, "_npcMakerQueued");

    private static final VarHandle SLOT_STATE_HANDLE =
            MethodHandles.arrayElementVarHandle(byte[].class);

    private static final VarHandle DECAY_MAILBOX_HANDLE =
            MethodHandles.arrayElementVarHandle(long[].class);

    @SuppressWarnings("unused")
    private volatile int _npcMakerQueued;

    /** owner-only slot state. 외부 thread는 acquire read만 한다. */
    private byte[] _spawnSlotState;
    private long[] _respawnAtMs;
    private int[] _respawnObjectId;

    /**
     * 외부 death thread -> owner primitive mailbox.
     *
     * mailbox:
     *   0 = EMPTY
     *   1 = producer가 slot을 claim하고 payload 기록 중
     *  >=2 = READY(objectId encoded)
     *
     * _decayAtMs는 READY release-store 전에 기록되므로 owner acquire-read에서 보인다.
     */
    private long[] _decayMailbox;
    private long[] _decayAtMs;

    /** 통계/검증용. write는 owner-only, 외부 조회를 위해 volatile. */
    private volatile int _activeSpawnCount;
    private volatile int _pendingRespawnCount;

    /**
     * CIO shared wheel timer generation.
     *
     * owner CIOThread만 변경한다. 새로운 decay wake가 들어오면 generation을
     * 전진시켜 이미 wheel에 남아 있는 이전 respawn task를 stale 처리한다.
     */
    private int _npcMakerTimerGeneration;

    public CNpcMaker(L1Npc mobTemplate) throws SecurityException, ClassNotFoundException {
        _template = mobTemplate;
        _npcid = mobTemplate != null ? mobTemplate.get_npcId() : 0;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public short getMapId() {
        return _mapid;
    }

    public void setMapId(short _mapid) {
        this._mapid = _mapid;
    }

    public boolean isRespawnScreen() {
        return _respaenScreen;
    }

    public void setRespawnScreen(boolean flag) {
        _respaenScreen = flag;
    }

    public int getMovementDistance() {
        return _movementDistance;
    }

    public void setMovementDistance(int i) {
        _movementDistance = i;
    }

    public int getAmount() {
        return _maximumCount;
    }

    public int getGroupId() {
        return _groupId;
    }

    public int getId() {
        return _id;
    }

    public String getLocation() {
        return _location;
    }

    public int getLocX() {
        return _locx;
    }

    public int getLocY() {
        return _locy;
    }

    public int getNpcId() {
        return _npcid;
    }

    public int getHeading() {
        return _heading;
    }

    public int getRandomx() {
        return _randomx;
    }

    public int getRandomy() {
        return _randomy;
    }

    public int getLocX1() {
        return _locx1;
    }

    public int getLocY1() {
        return _locy1;
    }

    public int getLocX2() {
        return _locx2;
    }

    public int getLocY2() {
        return _locy2;
    }

    public int getMinRespawnDelay() {
        return _minRespawnDelay;
    }

    public int getMaxRespawnDelay() {
        return _maxRespawnDelay;
    }

    public void setAmount(int amount) {
        _maximumCount = amount;
    }

    public void setId(int id) {
        _id = id;
    }

    public void setGroupId(int i) {
        _groupId = i;
    }

    public void setLocation(String location) {
        _location = location;
    }

    public void setLocX(int locx) {
        _locx = locx;
    }

    public void setLocY(int locy) {
        _locy = locy;
    }

    public void setNpcid(int npcid) {
        _npcid = npcid;
    }

    public void setHeading(int heading) {
        _heading = heading;
    }

    public void setRandomx(int randomx) {
        _randomx = randomx;
    }

    public void setRandomy(int randomy) {
        _randomy = randomy;
    }

    public void setLocX1(int locx1) {
        _locx1 = locx1;
    }

    public void setLocY1(int locy1) {
        _locy1 = locy1;
    }

    public void setLocX2(int locx2) {
        _locx2 = locx2;
    }

    public void setLocY2(int locy2) {
        _locy2 = locy2;
    }

    public void setMinRespawnDelay(int i) {
        _minRespawnDelay = i;
    }

    public void setMaxRespawnDelay(int i) {
        _maxRespawnDelay = i;
    }

    private long calcRespawnDelay() {
        long respawnDelay = (long) _minRespawnDelay * 1000L;
        if (_delayInterval > 0) {
            respawnDelay += (long) ThreadLocalRandom.current().nextInt(_delayInterval) * 1000L;
        }
        return respawnDelay;
    }

    /**
     * 기존 호출 계약 보존용 wrapper.
     *
     * death/decay thread에서는 primitive mailbox publish만 수행하고,
     * 실제 Maker state 변경/respawn은 고정 CIOThread owner가 직렬 처리한다.
     */
    public void executeSpawnTask(int spawnNumber, int objectId) {
        final long deathAtMs = nowMs();

        if (!npcMakerPublishDecay(spawnNumber, objectId, deathAtMs)) {
            return;
        }
        CIOThreadCallback.getInstance().wakeNpcMaker(this);
    }

    private boolean _initSpawn = false;
    private boolean _spawnHomePoint;
    
    public void init() {
        _delayInterval = _maxRespawnDelay - _minRespawnDelay;

        final int size = _maximumCount + 1;
        _spawnSlotState = new byte[size];
        _respawnAtMs = new long[size];
        _respawnObjectId = new int[size];
        _decayMailbox = new long[size];
        _decayAtMs = new long[size];
        _activeSpawnCount = 0;
        _pendingRespawnCount = 0;

        _initSpawn = true;
        if (Config.SPAWN_HOME_POINT && Config.SPAWN_HOME_POINT_COUNT <= getAmount()
                && Config.SPAWN_HOME_POINT_DELAY >= getMinRespawnDelay() && isAreaSpawn()) {
            _spawnHomePoint = true;
            _homeX = new int[size];
            _homeY = new int[size];
        }

        long nowMs = System.nanoTime() / 1_000_000L;
        boolean hasRetry = false;

        for (int spawnNumber = 1; spawnNumber <= _maximumCount; spawnNumber++) {
            SLOT_STATE_HANDLE.setRelease(
                    _spawnSlotState,
                    spawnNumber,
                    SLOT_SPAWNING);

            final boolean spawned = doSpawnInternal(spawnNumber, 0);
            if (spawned) {
                _activeSpawnCount++;
                SLOT_STATE_HANDLE.setRelease(
                        _spawnSlotState,
                        spawnNumber,
                        SLOT_ACTIVE);
            } else {
                _respawnAtMs[spawnNumber] = nowMs + SPAWN_RETRY_MS;
                _respawnObjectId[spawnNumber] = 0;
                _pendingRespawnCount++;
                SLOT_STATE_HANDLE.setRelease(
                        _spawnSlotState,
                        spawnNumber,
                        SLOT_WAIT_RESPAWN);
                hasRetry = true;
            }
        }

        _initSpawn = false;

        if (hasRetry) {
            CIOThreadCallback.getInstance().activateNpcMaker(this);
        }
    }

    protected void doSpawn(int spawnNumber) {
        doSpawn(spawnNumber, 0);
    }

    /** 기존 외부 호출 계약 보존. Maker 내부에서는 성공 여부가 필요한 doSpawnInternal을 사용한다. */
    public void doSpawn(int spawnNumber, int objectId) {
        doSpawnInternal(spawnNumber, objectId);
    }

    /**
     * 실제 NPC 생성 primitive.
     *
     * true  : 최소한 world object 등록까지 성공하여 slot을 ACTIVE로 볼 수 있음
     * false : world 등록 전에 실패. NpcMaker가 동일 slot을 재시도해야 함
     */
    private boolean doSpawnInternal(int spawnNumber, int objectId) {
        L1NpcInstance mob = null;
        boolean storedInWorld = false;
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        try {
            final short mapId = _mapid;
            int newlocx = getLocX();
            int newlocy = getLocY();
            int tryCount = 0;

            final L1Object obj = L1World.getInstance().findObject(objectId);
            if (obj != null && obj.instanceOf(L1Type.L1TYPE_MONSTER)) {
                final L1MonsterInstance old = (L1MonsterInstance) obj;
                if (!old.isDead()) {
                    old.deleteMe();
                }
            }

            mob = CObjectDB.CreateNPC(_template);
            if (mob == null) {
                return false;
            }

            mob.setHeading((_heading >= 0 && _heading <= 7) ? _heading : 0);

            final int npcId = mob.getNpcTemplate().get_npcId();
            if (npcId == 45488 && getMapId() == 9) {
                mob.setMap((short) (getMapId() + rnd.nextInt(2)));
            } else if (npcId == 45601 && getMapId() == 11) {
                mob.setMap((short) (getMapId() + rnd.nextInt(3)));
            } else {
                mob.setMap(getMapId());
            }

            mob.setMovementDistance(getMovementDistance());
            mob.setRest(isRest());

            while (tryCount <= 50) {
                if (isAreaSpawn()) {
                    if (!_initSpawn && _spawnHomePoint && _homeX != null
                            && spawnNumber < _homeX.length && _homeX[spawnNumber] != 0) {
                        final int hx = _homeX[spawnNumber];
                        final int hy = _homeY[spawnNumber];
                        newlocx = hx - Config.SPAWN_HOME_POINT_RANGE
                                + rnd.nextInt(Config.SPAWN_HOME_POINT_RANGE * 2 + 1);
                        newlocy = hy - Config.SPAWN_HOME_POINT_RANGE
                                + rnd.nextInt(Config.SPAWN_HOME_POINT_RANGE * 2 + 1);
                    } else {
                        final int rangeX = getLocX2() - getLocX1();
                        final int rangeY = getLocY2() - getLocY1();
                        newlocx = rnd.nextInt(rangeX > 0 ? rangeX : 1) + getLocX1();
                        newlocy = rnd.nextInt(rangeY > 0 ? rangeY : 1) + getLocY1();
                    }

                    if (tryCount > 49) {
                        newlocx = getLocX();
                        newlocy = getLocY();
                    }
                } else if (isRandomSpawn()) {
                    final int rx = getRandomx();
                    final int ry = getRandomy();
                    newlocx = getLocX() + (rx > 0 ? rnd.nextInt(rx * 2 + 1) - rx : 0);
                    newlocy = getLocY() + (ry > 0 ? rnd.nextInt(ry * 2 + 1) - ry : 0);
                } else {
                    newlocx = getLocX();
                    newlocy = getLocY();
                }

                mob.setX(newlocx);
                mob.setHomeX(newlocx);
                mob.setY(newlocy);
                mob.setHomeY(newlocy);

                final short spawnMapId = mob.getMapId();
                if (TileOccupancyNpc.getInstance().getOccupier(spawnMapId, newlocx, newlocy) != 0
                        || TileOccupancy.getInstance().getOccupier(spawnMapId, newlocx, newlocy) != 0) {
                    tryCount++;
                    continue;
                }

                if (!canSpawnHere(mob, newlocx, newlocy)) {
                    tryCount++;
                    continue;
                }
                break;
            }

            // A failed monster position search must not publish an invalid world object.
            // Fixed non-monster NPCs retain their existing placement behavior.
            if (tryCount > 50 && mob instanceof L1MonsterInstance) {
                return false;
            }
            mob.setId(objectId == 0 ? IdFactory.getInstance().nextId() : objectId);

            if (mob instanceof L1MonsterInstance) {
                ((L1MonsterInstance) mob).initHide();
            }

            mob.setSpawn(this);
            mob.setRespawn(true);
            mob.setSpawnNumber(spawnNumber);

            if (_spawnHomePoint && _homeX != null && spawnNumber > 0 && spawnNumber < _homeX.length
                    && (_initSpawn || _homeX[spawnNumber] == 0)) {
                _homeX[spawnNumber] = mob.getX();
                _homeY[spawnNumber] = mob.getY();
            }

            if (npcId == 45573 && mob.getMapId() == 2) {
                for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                    if (pc != null && pc.getMapId() == 2) {
                        pc.start_teleport(32664, 32797, 2, pc.getHeading(), 169, true, false);
                    }
                }
            }

            if (npcId == 5095) {
                SC_DoActionGFX.broadcast(mob, 4);
                mob.startTimedDelete(60 * 1000);  
            }

            L1World.getInstance().storeAndEnterWorld(mob);
            storedInWorld = true;

            if (mob instanceof L1MonsterInstance) {
                final L1MonsterInstance monster = (L1MonsterInstance) mob;
                if (!_initSpawn && monster.getHiddenStatus() == 0) {
                    monster.onNpcAI();
                }
            }

            if (getGroupId() != 0) {
                L1MobGroupSpawn.getInstance().doSpawn(
                        mob, getGroupId(), isRespawnScreen(), _initSpawn);
            }

            mob.getLight().turnOnOffLight();
            mob.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
            return true;

        } catch (Exception e) {
            System.out.println(String.format(
                    "스폰아이디 : %d, 엔피씨아이디 : %d", _id, _npcid));
            e.printStackTrace();

            /*
             * storeObject 이후 후처리에서 실패했다면 동일 objectId를 재생성하면
             * 중복 world object가 될 수 있다. 이미 world에 들어갔다면 ACTIVE로 취급한다.
             */
            return storedInWorld;
        }
    }

    /**
     * 스폰 위치 유효성 검사.
     * 몬스터는 맵 경계/통행 가능 여부 검사, NPC류는 제한 없이 통과.
     */
    private boolean canSpawnHere(L1NpcInstance mob, int x, int y) {
        if (mob == null) {
            return false;
        }
        final L1Map map = mob.getMap();
        if (map == null) {
            return false;
        }
        if (mob instanceof L1MonsterInstance) {
            if (!map.isInMap(x, y) || !map.isPassable(x, y)) {
                return false;
            }
        }
        return true;
    }

    public void setRest(boolean flag) {
        _rest = flag;
    }

    public boolean isRest() {
        return _rest;
    }

    private boolean isAreaSpawn() {
        return getLocX1() != 0 && getLocY1() != 0 && getLocX2() != 0 && getLocY2() != 0;
    }

    private boolean isRandomSpawn() {
        return getRandomx() != 0 || getRandomy() != 0;
    }
  
    public long calcRespawnDelayPublic() {
        return calcRespawnDelay();
    }

    // ---------------------------------------------------------------------
    // CIO-backed NpcMaker bridge.
    //
    // external thread:
    //   npcMakerPublishDecay() + CIOThreadCallback.wakeNpcMaker()
    //
    // owner CIOThread only:
    //   TimerExpired()
    //   npcMakerDrainDecayEvents()
    //   npcMakerStep()
    //   npcMakerRecover()
    // ---------------------------------------------------------------------

    /**
     * death/decay thread에서 호출.
     *
     * Maker owner state는 절대 변경하지 않는다.
     * ACTIVE 상태인지 acquire-read로 확인한 뒤 primitive mailbox에
     * objectId + death timestamp만 publish한다.
     */
    public final boolean npcMakerPublishDecay(
            final int spawnNumber,
            final int objectId,
            final long deathAtMs) {

        final byte[] states = _spawnSlotState;
        final long[] mailbox = _decayMailbox;
        final long[] decayAt = _decayAtMs;

        if (states == null
                || mailbox == null
                || decayAt == null
                || spawnNumber <= 0
                || spawnNumber >= states.length) {
            return false;
        }

        /*
         * doSpawnInternal()이 old.deleteMe()를 호출하는 경우 현재 slot은 SPAWNING이다.
         * 여기서 ACTIVE가 아니면 event 자체를 publish하지 않아 stale decay가
         * 새로 생성된 NPC를 다시 WAIT_RESPAWN으로 보내는 것을 막는다.
         */
        final byte state =
                (byte) SLOT_STATE_HANDLE.getAcquire(states, spawnNumber);

        if (state != SLOT_ACTIVE) {
            return false;
        }

        /*
         * 같은 slot에는 동시에 decay event 하나만 존재할 수 있다.
         * 0 -> CLAIMED를 성공한 producer만 payload를 쓴다.
         */
        if (!(boolean) DECAY_MAILBOX_HANDLE.compareAndSet(
                mailbox,
                spawnNumber,
                DECAY_MAILBOX_EMPTY,
                DECAY_MAILBOX_CLAIMED)) {
            return false;
        }

        decayAt[spawnNumber] = deathAtMs;

        /*
         * objectId 0도 유효하므로 +2 encoding.
         * 이 release-store가 decayAt write를 owner에게 함께 publish한다.
         */
        final long encodedObjectId =
                (((long) objectId) & 0xFFFF_FFFFL)
                        + DECAY_MAILBOX_READY_BASE;

        DECAY_MAILBOX_HANDLE.setRelease(
                mailbox,
                spawnNumber,
                encodedObjectId);

        return true;
    }

    /**
     * owner thread 전용.
     * 외부 thread가 publish한 decay mailbox를 owner state로 commit한다.
     *
     * ACTIVE -> WAIT_RESPAWN
     */
    public final void npcMakerDrainDecayEvents() {
        final long[] mailbox = _decayMailbox;
        final long[] decayAt = _decayAtMs;

        if (mailbox == null || decayAt == null || _spawnSlotState == null) {
            return;
        }

        final int limit =
                Math.min(_maximumCount, mailbox.length - 1);

        for (int i = 1; i <= limit; i++) {
            final long encoded =
                    (long) DECAY_MAILBOX_HANDLE.getAcquire(mailbox, i);

            /*
             * EMPTY 또는 producer가 payload를 기록 중인 CLAIMED는 건드리지 않는다.
             * CLAIMED producer는 READY publish 후 owner를 wake한다.
             */
            if (encoded < DECAY_MAILBOX_READY_BASE) {
                continue;
            }

            if (_spawnSlotState[i] == SLOT_ACTIVE) {
                final int objectId =
                        (int) (encoded - DECAY_MAILBOX_READY_BASE);

                final long deathAtMs = decayAt[i];

                _respawnObjectId[i] = objectId;
                _respawnAtMs[i] =
                        deathAtMs + calcRespawnDelay();

                if (_activeSpawnCount > 0) {
                    _activeSpawnCount--;
                }
                _pendingRespawnCount++;

                /*
                 * owner state payload를 먼저 기록하고 WAIT_RESPAWN을 release publish.
                 * 외부 thread는 state를 쓰지 않는다.
                 */
                SLOT_STATE_HANDLE.setRelease(
                        _spawnSlotState,
                        i,
                        SLOT_WAIT_RESPAWN);
            }

            decayAt[i] = 0L;

            /*
             * state commit 이후 mailbox를 비운다.
             * 이 전까지 duplicate producer는 같은 slot을 claim할 수 없다.
             */
            DECAY_MAILBOX_HANDLE.setRelease(
                    mailbox,
                    i,
                    DECAY_MAILBOX_EMPTY);
        }
    }

    public final boolean npcMakerTryQueue() {
        return NPC_MAKER_QUEUED.compareAndSet(this, 0, 1);
    }

    public final void npcMakerClearQueued() {
        _npcMakerQueued = 0;
    }

    /** 현재 Maker가 가진 가장 빠른 WAIT_RESPAWN deadline. owner thread 전용. */
    public final long npcMakerNextDeadlineMs() {
        if (_spawnSlotState == null) {
            return -1L;
        }

        long next = Long.MAX_VALUE;

        for (int i = 1; i <= _maximumCount; i++) {
            if (_spawnSlotState[i] != SLOT_WAIT_RESPAWN) {
                continue;
            }

            final long deadline = _respawnAtMs[i];
            if (deadline > 0L && deadline < next) {
                next = deadline;
            }
        }

        return next == Long.MAX_VALUE ? -1L : next;
    }

    /**
     * 해당 Maker의 CIOThread owner에서만 호출한다.
     *
     * WAIT_RESPAWN -> SPAWNING -> ACTIVE
     *                            -> WAIT_RESPAWN(retry)
     */
    public final long npcMakerStep(final long nowMs) {
        long currentNow = nowMs;
        int processed = 0;
        int scanFrom = 1;

        while (processed < MAKER_STEP_BUDGET) {
            int spawnNumber = 0;
            int objectId = 0;

            if (_spawnSlotState == null) {
                return -1L;
            }

            for (int i = scanFrom; i <= _maximumCount; i++) {
                if (_spawnSlotState[i] != SLOT_WAIT_RESPAWN) {
                    continue;
                }

                final long deadline = _respawnAtMs[i];

                if (deadline <= currentNow) {
                    spawnNumber = i;
                    scanFrom = i + 1;
                    objectId = _respawnObjectId[i];

                    SLOT_STATE_HANDLE.setRelease(
                            _spawnSlotState,
                            i,
                            SLOT_SPAWNING);
                    break;
                }

            }

            if (spawnNumber == 0) {
                break;
            }

            boolean spawned = false;
            Throwable fatal = null;

            try {
                spawned = doSpawnInternal(spawnNumber, objectId);
            } catch (Throwable t) {
                fatal = t;
            }

            currentNow = System.nanoTime() / 1_000_000L;

            if (_spawnSlotState[spawnNumber] == SLOT_SPAWNING) {
                if (spawned) {
                    _respawnAtMs[spawnNumber] = 0L;
                    _respawnObjectId[spawnNumber] = 0;

                    _activeSpawnCount++;
                    if (_pendingRespawnCount > 0) {
                        _pendingRespawnCount--;
                    }

                    SLOT_STATE_HANDLE.setRelease(
                            _spawnSlotState,
                            spawnNumber,
                            SLOT_ACTIVE);
                } else {
                    _respawnAtMs[spawnNumber] =
                            currentNow + SPAWN_RETRY_MS;

                    // objectId는 동일 slot 재시도를 위해 그대로 유지.
                    SLOT_STATE_HANDLE.setRelease(
                            _spawnSlotState,
                            spawnNumber,
                            SLOT_WAIT_RESPAWN);
                }
            }

            if (fatal != null) {
                if (fatal instanceof Error) {
                    throw (Error) fatal;
                }
                if (fatal instanceof RuntimeException) {
                    throw (RuntimeException) fatal;
                }
                throw new RuntimeException(fatal);
            }

            processed++;
        }

        /*
         * due slot이 더 있어도 한 Maker가 owner를 독점하지 않는다.
         * CIO shared wheel의 다음 tick으로 넘겨 다른 owner work에 실행권을 돌려준다.
         */
        final long next = npcMakerNextDeadlineMs();

        if (next <= 0L) {
            return -1L;
        }

        return next <= currentNow ? currentNow + 1L : next;
    }

    /** owner 예외 후 SPAWNING으로 남은 slot을 WAIT_RESPAWN으로 복구한다. */
    public final long npcMakerRecover(final long nowMs) {
        if (_spawnSlotState == null) {
            return -1L;
        }

        long next = Long.MAX_VALUE;

        for (int i = 1; i <= _maximumCount; i++) {
            if (_spawnSlotState[i] == SLOT_SPAWNING) {
                _respawnAtMs[i] =
                        nowMs + SPAWN_RETRY_MS;

                SLOT_STATE_HANDLE.setRelease(
                        _spawnSlotState,
                        i,
                        SLOT_WAIT_RESPAWN);
            }

            if (_spawnSlotState[i] == SLOT_WAIT_RESPAWN) {
                final long deadline = _respawnAtMs[i];
                if (deadline > 0L && deadline < next) {
                    next = deadline;
                }
            }
        }

        return next == Long.MAX_VALUE ? -1L : next;
    }

    public final int npcMakerActiveCount() {
        return _activeSpawnCount;
    }

    public final int npcMakerPendingCount() {
        return _pendingRespawnCount;
    }

    /**
     * CIO raw timer callback.
     *
     * WAKE:
     *   외부 producer가 publish한 decay mailbox를 owner state로 commit하고
     *   기존 respawn timer generation을 무효화한 뒤 가장 빠른 deadline을 다시 건다.
     *
     * RESPAWN:
     *   generation이 현재 값과 일치할 때만 due slot을 처리한다.
     */
    public final void TimerExpired(final int timerId, final int generation) {
        if (timerId == TIMER_NPC_MAKER_WAKE) {
            /*
             * 먼저 queued flag를 내린다.
             * 이후 새 decay가 들어오면 owner 처리 중에도 다음 wake를 다시 publish할 수 있다.
             */
            npcMakerClearQueued();

            /*
             * 기존 CIO wheel respawn node는 물리적으로 찾지 않는다.
             * generation만 전진시켜 나중에 발화하면 O(1) stale 처리한다.
             */
            nextNpcMakerTimerGeneration();

        } else if (timerId == TIMER_NPC_MAKER_RESPAWN) {
            if (generation != _npcMakerTimerGeneration) {
                return;
            }

        } else {
            return;
        }

        final long nowMs = nowMs();
        long nextDeadline;

        try {
            /*
             * timer 만료 직전에 새 decay가 publish됐어도 한 owner에서 함께 commit한다.
             */
            npcMakerDrainDecayEvents();

            /*
             * due slot은 최대 MAKER_STEP_BUDGET까지만 처리한다.
             * 더 남아 있으면 npcMakerStep()이 near-future deadline을 반환하여
             * CIO에 제어권을 돌려준 뒤 다시 실행된다.
             */
            nextDeadline = npcMakerStep(nowMs);

        } catch (Throwable t) {
            /*
             * SPAWNING으로 남은 slot을 WAIT_RESPAWN으로 되돌리고 재시도한다.
             */
            nextDeadline = npcMakerRecover(nowMs());

            _log.log(
                    Level.WARNING,
                    "[CIO NpcMaker] respawn exception spawnId="
                            + getId()
                            + " npcId="
                            + getNpcId(),
                    t);
        }

        if (nextDeadline > 0L) {
            AddTimer(nextDeadline);
        }
    }

    private void AddTimer(final long deadlineMs) {
        if (deadlineMs <= 0L) {
            return;
        }

        final long delayMs =
                Math.max(
                        1L,
                        deadlineMs - nowMs());

        final int generation =
                nextNpcMakerTimerGeneration();

        CIOThreadCallback.getInstance().scheduleNpcMakerTimer(
                this,
                TIMER_NPC_MAKER_RESPAWN,
                generation,
                delayMs);
    }

    private int nextNpcMakerTimerGeneration() {
        int next = _npcMakerTimerGeneration + 1;

        if (next == 0) {
            next = 1;
        }

        _npcMakerTimerGeneration = next;
        return next;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

}