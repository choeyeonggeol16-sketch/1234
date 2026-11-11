package l1j.server.server.model.Instance;

import java.util.List;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jctools.queues.MpscLinkedQueue;
import l1j.server.Config;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1MobSkillUse;
import l1j.server.server.model.L1NpcChatTimer;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.npc.ai.ActionSpriteMapper;
import l1j.server.server.model.npc.ai.action.CCreatureController;
import l1j.server.server.model.npc.ai.action.CGetItemAction;
import l1j.server.server.model.npc.ai.action.CMoveAction;
import l1j.server.server.model.npc.ai.action.AiRuntime;
import l1j.server.server.model.npc.ai.NpcServer;
import l1j.server.server.model.npc.ai.HateEvent;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.npc.ai.NpcDigestItems;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.NpcPotionPolicy;
import l1j.server.server.model.npc.ai.NpcRefineRecipes;
import l1j.server.server.model.npc.ai.NpcSpecials;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_MoveChar;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_ExplosionNoti;

import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1NpcChat;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CommonUtil;

public class L1NpcInstance extends L1Character { 
	
	private static final long serialVersionUID = 1L;

    public static final int HIDDEN_STATUS_NONE = 0;
    public static final int HIDDEN_STATUS_SINK = 1;
    public static final int HIDDEN_STATUS_FLY = 2;

    public static final int CHAT_TIMING_APPEARANCE = 0;
    public static final int CHAT_TIMING_DEAD = 1;
    public static final int CHAT_TIMING_HIDE = 2;
    public static final int CHAT_TIMING_GAME_TIME = 3;

    /** HateEvent 내부 SeeCreature sentinel. 실제 hate 값으로 사용하지 않는다. */
    public static final int HATE_EVENT_SIGHT = Integer.MIN_VALUE;
    public static final int HATE_EVENT_INVISIBILITY = Integer.MIN_VALUE + 1;

    /*
     * ATTACKED / CLAN_ATTACKED도 기존 pooled HateEvent를 그대로 사용한다.
     * 0x7D = direct ATTACKED, 0x7E = CLAN_ATTACKED, 하위 24bit = damage.
     * 일반 hate 값과 이벤트 입력을 섞지 않는다.
     */
    public static final int HATE_EVENT_DAMAGE_TAG_MASK = 0xFF000000;
    public static final int HATE_EVENT_DAMAGE_VALUE_MASK = 0x00FFFFFF;
    public static final int HATE_EVENT_ATTACKED_TAG = 0x7D000000;
    public static final int HATE_EVENT_CLAN_TAG = 0x7E000000;


    private static final int SEADUN_SHIP_ARRIVAL_Y = 32811;

    /** 지정 구역 이탈 시 귀환시키는 NPC (45912~45916) */
    private static final int RETURN_GUARD_ID_MIN = 45912;
    private static final int RETURN_GUARD_ID_MAX = 45916;

    /** 귀환 구역: 말성 인근 (맵 4) */
    private static final int RETURN_ZONE_MAP_ID = 4;
    private static final int RETURN_ZONE_MIN_X = 32591;
    private static final int RETURN_ZONE_MAX_X = 32644;
    private static final int RETURN_ZONE_MIN_Y = 32643;
    private static final int RETURN_ZONE_MAX_Y = 32688;


    private static final int[] DIR_DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIR_DY = {-1, -1, 0, 1, 1, 1, 0, -1};

    private L1Npc _npcTemplate;
    private L1Spawn _spawn;

    private int _spawnNumber;
    private int _petcost;

    /** NPC별 최소 몬스터 스킬 runtime. 정의/실행 객체는 소유하지 않는다. */
    public long _mobSkillSleepTime;
    public int[] _mobSkillUseCount;

    public boolean firstFound = true;

    private int _drainedMana = 0;

    private boolean _rest = false;
    private boolean _isResurrect;

    public int _randomMoveDistance = 0;
    public int _randomMoveDirection = 0;

    /** dMoveAround random destination adapter. 0/0 means no active random destination. */
    public int _desireMoveAroundTargetX = 0;
    public int _desireMoveAroundTargetY = 0;

    private boolean _aiRunning = false;
    private boolean _actived = false;
    private boolean _firstAttack = false;
    private long _sleep_time;
    public L1HateList _hateList = new L1HateList();
    public L1HateList _dropHateList = new L1HateList();
    
	protected L1Inventory _inventory = new L1Inventory();
	
	public volatile L1Character _target = null;
    public L1PcInstance _master = null;
    private volatile boolean _deathProcessing = false;
    private static final int TIMER_CORPSE_DELETE = 0x434F5250;
    public static final int TIMER_TIMED_DELETE = 0x54444C54;
    private int _corpseTimerGeneration;
    private int _corpseQueuedGeneration;
    private volatile boolean _corpseDeleteStarted;
    private L1MobGroupInfo _mobGroupInfo = null;
    private int _mobGroupId = 0;
    private int num; /* 버경 관련 */




    /** 이 몹이 다음 경로탐색을 시도 가능해지는 시각 (경로 실패 시 설정) */
    public long _pathRetryAfter = 0L;
    public  int _stuckCount = 0;
    public volatile long _nextAllowedTickMs = 0L;

    public volatile byte _aiAction = CCreatureController.ACTION_THINK;

    public volatile byte _pendingAiAction = CCreatureController.ACTION_NONE;

    public byte _moveCollision = 0;
    public byte _moveSide = -1;
    public byte _moveHeading = -1;
    public int _moveTargetId = 0;
    /** Last observed target XY packed as unsigned-16 X/Y; teleport rebase only. */
    public int _moveTargetPos = 0;
    public int _moveContactX = 0;
    public int _moveContactY = 0;

    /*
     * L1 lattice projection of the active CalcMidDest arrival point.
     * Native moves continuously toward an FVector arrival.  Keeping the
     * snapped arrival explicitly prevents timer-based whole-tile overshoot.
     */
    public int _moveSegmentX = 0;
    public int _moveSegmentY = 0;

    // L1 2-D container identity and target identity for native relative chase.
    public int _relativeTargetId = 0;
    public int _relativeMapId = -1;

    public int _waitTargetId = 0;
    public int _waitDistance = 0;
    public byte _waitPendingAction = CCreatureController.ACTION_NONE;

    /*
     * Native dPAttack() consecutive path-find failure counter.
     * Shared path-fail flag false => reset to 0.
     * Shared path-fail flag true => ++; fail when count > 30.
     */
    public int _pathFindFailCount = 0;

    private static final AtomicIntegerFieldUpdater<L1NpcInstance> AI_WAKE_QUEUED =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_aiWakeQueued");
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> AI_REGISTER_QUEUED =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_aiRegisterQueued");
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> AI_UNREGISTER_QUEUED =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_aiUnregisterQueued");

    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_ACTION_GENERATION =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcActionTimerGeneration");

    private static final AtomicReferenceFieldUpdater<L1NpcInstance, L1Character> NPC_CHASE_RETARGET =
            AtomicReferenceFieldUpdater.newUpdater(
                    L1NpcInstance.class, L1Character.class, "_npcChaseRetarget");

    private volatile int _aiRegisterQueued;
    private volatile int _aiUnregisterQueued;
    private volatile int _aiWakeQueued; 
    private volatile int _npcActionTimerGeneration;
    /** 1 while CIO owns the next action deadline. External AI wake must not bypass it. */
    private volatile int _npcActionCadenceOwned;
    // Emergency owner handoff uses the already coalesced AI wake ticket.
    // These primitives are allocated with the NPC, never per queued event.
    private volatile int _npcActionDeferredCancel;
    // ARM only: duplicate requests may coalesce without changing skill edits.
    // The existing NPC owner wake ticket delivers this when event leases are full.
    /** Bounded, non-blocking skill admission for callers that handle overload.
     * A false result leaves the requested mutation unapplied; no side effects
     * should be committed by the caller before admission succeeds.
     */
    public final boolean trySetSkillEffect(int skillId, int timeMillis) {
        return CIOThreadCallback.getInstance().trySubmitNpcSkillSet(this, skillId, timeMillis);
    }

    public final boolean tryRemoveSkillEffect(int skillId, boolean stop) {
        return CIOThreadCallback.getInstance().trySubmitNpcSkillRemove(this, skillId, stop);
    }

    public final boolean tryClearSkillEffect() {
        return CIOThreadCallback.getInstance().trySubmitNpcSkillClear(this);
    }

    public final boolean tryReplaceSkillEffect(int skillId, int timeMillis) {
        return CIOThreadCallback.getInstance().trySubmitNpcSkillReplace(this, skillId, timeMillis);
    }

    // Retired-NPC effect cleanup uses the existing coalesced owner wake,
    // not a bounded skill command lease. One primitive flag per NPC.
    private volatile int _npcSkillDeferredDestroyClear;
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_SKILL_DESTROY_CLEAR =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcSkillDeferredDestroyClear");

    private volatile int _npcSkillHeartbeatDeferredArm;
    // One active skill-expiry generation per NPC. Expiries are dispatched
    // through the existing coalesced owner wake without renting a corpse event.
    private volatile int _npcSkillHeartbeatDeferredTick;
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_SKILL_HEARTBEAT_DEFERRED_TICK =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcSkillHeartbeatDeferredTick");
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_SKILL_HEARTBEAT_DEFERRED_ARM =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcSkillHeartbeatDeferredArm");
    private volatile int _npcActionAdmissionCancelEpoch;
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_ACTION_ADMISSION_EPOCH =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcActionAdmissionCancelEpoch");
    private static final AtomicIntegerFieldUpdater<L1NpcInstance> NPC_ACTION_DEFERRED_CANCEL =
            AtomicIntegerFieldUpdater.newUpdater(L1NpcInstance.class, "_npcActionDeferredCancel");
    private long _npcActionDeferredDue;
    private int _npcActionDeferredGeneration;
    // Action cadence belongs to NpcServer's existing per-NPC owner wheel.
    // Only the NPC owner thread writes these two primitive fields.
    private long _npcControllerDueMs;
    private int _npcControllerDueGeneration;

    public final void npcControllerArm(long dueMs, int generation) {
        _npcControllerDueGeneration = generation;
        _npcControllerDueMs = dueMs;
    }

    public final void npcControllerDisarm() {
        _npcControllerDueMs = 0L;
    }

    public final long npcControllerDeadlineMs() {
        return _npcControllerDueMs;
    }

    public final int npcControllerDueGeneration() {
        return _npcControllerDueGeneration;
    }

    private volatile L1Character _npcChaseRetarget;
    /** 외부 event가 owner에게 action 재선택을 요청하는 coalesced hint. */
    private volatile int _aiThinkRequested;

    /*
     * owner-side primitive AI event state.
     * 현재는 MOVE body-block -> blocked retarget 사건만 사용한다.
     */
    public int _aiEventFlags;
    public int _aiBlockedTargetId;
    public int _aiBlockedType;
    private volatile long _timedDeleteDeadlineMs;
    private volatile int _timedDeleteGeneration;
    private long _aiEngineDeadlineMs;
    private int _aiEngineWheelBucket = -1;
    private L1NpcInstance _aiEngineWheelPrev;
    private L1NpcInstance _aiEngineWheelNext;
    public final NpcSpecials.Special _special;

    private final int _hprInterval;  
    private final int _hprPoint;
    private final int _mprInterval; 
    private final int _mprPoint;

    private long _nextHprAt = 0L;
    private long _nextMprAt = 0L;

    public L1NpcInstance(L1Npc template) {
        setStatus(0);
        setMoveSpeed(0);
        setDead(false);
        setRespawn(false);

        if (template != null) {
            _special = NpcSpecials.get(template.get_npcId());
            _hprInterval = template.get_hprinterval() != 0 ? template.get_hprinterval() : 0;
            _hprPoint    = Math.max(0, template.get_hpr());
            _mprInterval = template.get_mprinterval() != 0 ? template.get_mprinterval() : 0;
            _mprPoint    = Math.max(0, template.get_mpr());
            setting_template(template);
        } else {
            _special = null;
            _hprInterval = 0;
            _hprPoint = 0;
            _mprInterval = 0;
            _mprPoint = 0;
        }
    }

    private boolean _발묶임상태 = false;
    public boolean 발묶임상태() {
        return _발묶임상태;
    }
    public void set발묶임상태(boolean flag) {
        _발묶임상태 = flag;
    }

    private String Spawn_Location;

    public String getSpawnLocation() {// 하딘 시스템
        return Spawn_Location;
    }

    public void setSpawnLocation(String st) {
        Spawn_Location = st;
    }

    private double calcRandomVal(int seed, int ranval, double rate) {
        return rate * (ranval - seed);
    }

    protected void setting_template(L1Npc template) {
        _npcTemplate = template;
        double rate = 0;
        double diff = 0;

        setName(template.get_name());
        setNameId(template.get_nameid());
        int randomlevel = 0;
        int level = template.get_level();
        if (template.get_randomlevel() != 0) {
            randomlevel = ThreadLocalRandom.current().nextInt(template.get_randomlevel() - level + 1);
            int span = template.get_randomlevel() - level;
            diff = span;
            rate = span > 0 ? (double) randomlevel / span : 0D; 
            randomlevel += template.get_level();
            level = randomlevel;
        }
        setLevel(level);
        int ac = template.get_ac();
        if (template.get_randomac() != 0) {
            ac = (int) (ac + calcRandomVal(ac, template.get_randomac(), rate));
        }
        this.ac.setAc(ac);
        int bonus = template.get_randomlevel() == 0 ? 0 : (int) diff;
        ability.setStr(cap127(template.get_str() + bonus));
        ability.setCon(cap127(template.get_con() + bonus));
        ability.setDex(cap127(template.get_dex() + bonus));
        ability.setInt(cap127(template.get_int() + bonus));
        ability.setWis(cap127(template.get_wis() + bonus));
        resistance.setBaseMr(cap127(template.get_mr() + bonus));
        if (bonus != 0) {
            addHitup(bonus * 2);
            addDmgup(bonus * 2);
        }
        int hp = template.get_hp();
        if (template.get_randomhp() != 0) {
            hp = (int) (hp + calcRandomVal(hp, template.get_randomhp(), rate));
        }
        setMaxHp(hp);
        setCurrentHp(getMaxHp());

        int mp = template.get_mp();
        if (template.get_randommp() != 0) {
            mp = (int) (mp + calcRandomVal(mp, template.get_randommp(), rate));
        }
        setMaxMp(mp);
        setCurrentMp(mp);
        setAgro(template.is_agro());
        setAgrocoi(template.is_agrocoi());
        setAgrososc(template.is_agrososc());
        setCurrentSprite(template.get_gfxid());
        if (template.get_randomexp() == 0) {
            setExp(template.get_exp());
        } else {
            setExp(template.get_randomexp() + randomlevel);
        }
        int lawful = template.get_lawful();
        if (template.get_randomlawful() != 0) {
            lawful = (int) (lawful + calcRandomVal(lawful, template.get_randomlawful(), rate));
        }
        setLawful(lawful);
        setTempLawful(lawful);
        setPickupItem(template.is_picupitem());
        setKarma(template.getKarma());
        setLightSize(template.getLightSize());

        L1MobSkillUse.resetRuntime(this);
    }

    private static byte cap127(int v) {
        return (byte) Math.min(v, 127);
    }

    public final AtomicBoolean _castingSkill = new AtomicBoolean(false);

    public boolean tryBeginCast() {
        return _castingSkill.compareAndSet(false, true);
    }
    public void endCast() {
        _castingSkill.set(false);
        NpcServer.getInstance().wake(this);
    }

    protected void startAI() {
        final long now = System.currentTimeMillis();

        setAiRunning(true);
        _aiAction = CCreatureController.ACTION_THINK;
        _pendingAiAction = CCreatureController.ACTION_NONE;
        _aiEventFlags = 0;
        _aiBlockedTargetId = 0;
        _aiBlockedType = 0;
        _nextAllowedTickMs = now + 1000L;
        _aiEngineDeadlineMs = _nextAllowedTickMs;
        NpcServer.getInstance().register(this);
    }

    public void mobTeleport() {
        if (!(this instanceof L1MonsterInstance)) return;
        final int threshold = (getMaxHp() >= 100000) ? Config.Boss_Location : Config.Npc_Location;
        if (getLocation().getTileLineDistance(new Point(getHomeX(), getHomeY())) > threshold) {
            teleport(getHomeX(), getHomeY(), getHeading());
        }
    }

    public void TimerExpired(int timerId, int generation) {
        if (timerId != TIMER_TIMED_DELETE || _destroyed || _timedDeleteGeneration != generation) {
            return;
        }
        final long deadlineMs = _timedDeleteDeadlineMs;
        if (deadlineMs <= 0L || System.currentTimeMillis() < deadlineMs) {
            return;
        }
        _timedDeleteDeadlineMs = 0L;
        final long smartId = L1World.getInstance().smartIdOf(this);
        if (smartId != 0L && L1World.getInstance().findObjectBySmartId(smartId) == this) {
            deleteMe();
        }
    }

    public final boolean handleCorpseDeleteTimer(int timerId, int generation) {
        if (timerId != TIMER_CORPSE_DELETE) {
            return false;
        }

        synchronized (this) {
            if (generation != _corpseTimerGeneration || (generation & 1) == 0
                    || !isDead() || _destroyed || _corpseDeleteStarted) {
                return true;
            }
            if (_corpseQueuedGeneration == generation) {
                return true;
            }

            final long smartId = L1World.getInstance().smartIdOf(this);
            if (smartId == 0L) {
                return true;
            }

            _corpseQueuedGeneration = generation;
            if (NpcServer.getInstance().enqueueCorpse(this, generation, smartId)) {
                return true;
            }
            _corpseQueuedGeneration = 0;
        }

        CIOThreadCallback.getInstance().scheduleNpcTimer(
                this, TIMER_CORPSE_DELETE, generation, 250L);
        return true;
    }

    public final void processCorpseDelete(int generation, long smartId) {
        synchronized (this) {
            if (_corpseQueuedGeneration != generation
                    || generation != _corpseTimerGeneration
                    || (generation & 1) == 0 || !isDead()
                    || _destroyed || _corpseDeleteStarted
                    || L1World.getInstance().findObjectBySmartId(smartId) != this) {
                return;
            }

            _corpseQueuedGeneration = 0;
            ++_corpseTimerGeneration;
            _corpseDeleteStarted = true;
        }

        try {
            deleteMe();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void onItemUse() {}

 //   public void searchTarget() {}

    public boolean checkCondition() {
        if (getNpcId() == 707026) {
            int p = (int) (((double) getCurrentHp() / (double) getMaxHp()) * 100D);
            if (p > 90) {
                setSleepTime(200);
                return true; 
            }
        }
        return false;
    }

    public void checkTarget() {
        while (true) {
            if (isValidTarget(_target)) {
                return;
            }

            if (_target != null) {
                if (isTemporaryLinkedSightLoss(_target)) {
                    clearTargetReferenceOnly();
                    return;
                }

                // checkTarget() can run in CIO preflight while MOVE_AROUND
                // owns the current action. Discarding a dead/stale enemy must
                // not reset the ongoing 3..6-tile walk or its 6-second rest.
                // Explicit tagertClear()/allTargetClear() retain their normal
                // full action-reset contract outside this validation path.
                if (_aiAction == CCreatureController.ACTION_MOVE_AROUND_NATIVE
                        && this instanceof L1MonsterInstance) {
                    CDesirePQ.removeAttack((L1MonsterInstance) this, _target);
                    _hateList.remove(_target);
                    _target = null;
                } else {
                    tagertClear();
                }
            }

            if (_hateList.isEmpty()) {
                return;
            }

            _target = _hateList.getMaxHateCharacter();
        }
    }

    private boolean isTemporaryLinkedSightLoss(
            final L1Character target) {

        if (target == null) {
            return false;
        }
        if (this instanceof L1MonsterInstance
                && CDesirePQ.isActive(
                        (L1MonsterInstance) this)) {
            return false;
        }

        return NpcEventFactory.usesEventTargeting(this)
                && target.getMapId() == getMapId()
                && !target.isDead()
                && target.getCurrentHp() > 0
                && _hateList.containsKey(target)
                && _hateList.getHate(target) <= 0
                && !getLocation()
                        .isInScreen(
                                target.getLocation());
    }

    private void clearTargetReferenceOnly() {
        _target = null;
        clearNativeMoveState();
        _aiEventFlags = 0;
        _aiBlockedTargetId = 0;
        _aiBlockedType = 0;
        _pendingAiAction =
                CCreatureController.ACTION_NONE;
        _aiAction =
                CCreatureController.ACTION_THINK;
    }

    /**
     * 타겟 유효성 판정 
     */
    private boolean isValidTarget(L1Character t) {
        if (t == null) return false;
        if (t.getMapId() != getMapId()) return false;
        if (t.isDead()) return false;
        if (t.getCurrentHp() <= 0) return false;

        if (!(this instanceof L1MonsterInstance
                && CDesirePQ.isActive(
                        (L1MonsterInstance) this))
                && NpcEventFactory.usesEventTargeting(this)
                && _hateList.containsKey(t)
                && _hateList.getHate(t) <= 0
                && !getLocation()
                        .isInScreen(
                                t.getLocation())) {

            return false;
        }

        if (t.isInvisble()
                && !getNpcTemplate().is_agrocoi()) {
            return false;
        }

        if (t instanceof L1SummonInstance
                && ((L1SummonInstance) t).isDestroyed()) {
            return false;
        }

        if (t instanceof L1PetInstance
                && ((L1PetInstance) t).isDestroyed()) {
            return false;
        }

        if (t instanceof L1CompanionInstance
                && ((L1CompanionInstance) t).isDestroyed()) {
            return false;
        }

        return true;
    }

    /**
     * 타겟 action용 virtual hook.
     * subclass override 계약은 유지하고 기본 dispatch만 Controller로 넘긴다.
     */
    public void onTarget() {
        setActived(true);
        _targetItem = null;

        final L1Character target = _target;
        if (target == null) {
            CCreatureController.think(this);
            return;
        }

        if (isSleeped()
                && getLocation().getTileLineDistance(target.getLocation()) > 1) {
            if (_target == target) {
                tagertClear();
            }
            return;
        }

        if (isNoTargetNpc()) {
            if (_target == target) {
                tagertClear();
            }
            return;
        }
        CCreatureController.executeTargetAction(this, target);
    }

    private boolean isNoTargetNpc() {
        final int id = getNpcId();
        return id >= 7320012 && id <= 7320017;
    }
    

    protected void onDeath(L1Character lastAttacker) {
    }

    public final MpscLinkedQueue<HateEvent> _hateQueue = new MpscLinkedQueue<>();

    /**
     * SeeCreature event. 외부/world thread는 candidate만 queue에 싣고 실제 target 변경은 AI owner가 수행한다.
     */
    public final void seeCreature(L1Character cha) {
        if (cha == null
                || cha.getId() == getId()
                || _destroyed
                || isDead()
                || getCurrentHp() <= 0) {
            return;
        }

        if (!isAiRunning()) {
            onNpcAI();
        }

        _hateQueue.offer(HateEvent.obtain(cha, HATE_EVENT_SIGHT));
        NpcServer.getInstance().wake(this);
    }

    public void setHate(L1Character cha, int hate) {
        if (cha == null || cha.getId() == getId()) {
            return;
        }
        _hateQueue.offer(HateEvent.obtain(cha, hate));
        
        NpcServer.getInstance().wake(this);
    }

    /**
     * Retail ATTACKED event.
     * 일반 setHate()와 분리해 실제 receiveDamage 입력만 Desire에 반영한다.
     */
    public final void setAttackHate(
            L1Character cha,
            int damage) {

        if (cha == null
                || cha.getId() == getId()
                || damage < 0) {
            return;
        }

        final int encoded =
                HATE_EVENT_ATTACKED_TAG
                        | (Math.min(
                                damage,
                                HATE_EVENT_DAMAGE_VALUE_MASK)
                                & HATE_EVENT_DAMAGE_VALUE_MASK);

        _hateQueue.offer(HateEvent.obtain(cha, encoded));
        NpcServer.getInstance().wake(this);
    }

    /**
     * Retail CLAN_ATTACKED event.
     * damage를 pooled HateEvent의 tagged int에 싣고 AI owner에서 해석한다.
     */
    public final void setClanHate(
            L1Character cha,
            int damage) {

        if (cha == null
                || cha.getId() == getId()
                || damage <= 0) {
            return;
        }

        final int encoded =
                HATE_EVENT_CLAN_TAG
                        | (Math.min(
                                damage,
                                HATE_EVENT_DAMAGE_VALUE_MASK)
                                & HATE_EVENT_DAMAGE_VALUE_MASK);

        _hateQueue.offer(HateEvent.obtain(cha, encoded));
        NpcServer.getInstance().wake(this);
    }

    public static boolean isAttackHateEvent(final int value) {
        return (value & HATE_EVENT_DAMAGE_TAG_MASK)
                == HATE_EVENT_ATTACKED_TAG;
    }

    public static boolean isClanHateEvent(final int value) {
        return (value & HATE_EVENT_DAMAGE_TAG_MASK)
                == HATE_EVENT_CLAN_TAG;
    }

    public static int eventDamage(final int value) {
        return value & HATE_EVENT_DAMAGE_VALUE_MASK;
    }

    public boolean hasPendingHate() {
        return !_hateQueue.isEmpty();
    }

    public void setLink(L1Character cha) {
    }

    /**
     * damage-aware clan/group link entry.
     * Base NPC keeps the legacy no-op contract; monsters override it.
     */
    public void setLink(
            L1Character cha,
            int damage) {
        setLink(cha);
    }

    public void serchLink(
            L1PcInstance targetPlayer,
            int family) {

        if (targetPlayer == null) {
            return;
        }

        final int myGroupId =
                getMobGroupId();

        for (L1Object knownObject :
                targetPlayer.getKnownObjects()) {

            if (!(knownObject instanceof L1NpcInstance)) {
                continue;
            }

            final L1NpcInstance npc =
                    (L1NpcInstance) knownObject;

            if (npc == this
                    || npc.isDead()
                    || npc.getCurrentHp() <= 0
                    || npc.getMapId() != getMapId()) {
                continue;
            }

            final L1Npc template =
                    npc.getNpcTemplate();

            if (template == null) {
                continue;
            }
            
            boolean linked =
                    family > 0
                    && template.get_family() == family
                    && template.get_agrofamily() > 0
                    && getLocation()
                            .getTileLineDistance(
                                    npc.getLocation()) <= 12;

            if (!linked
                    && myGroupId != 0
                    && myGroupId == npc.getMobGroupId()) {

                linked = true;
            }

            if (linked) {
                npc.setLink(targetPlayer);
            }
        }
    }

    /**
     * Compatibility overload kept for external callers.
     * Generic link no longer transports CLAN_ATTACKED damage.
     */
    public void serchLink(
            L1PcInstance targetPlayer,
            int family,
            int damage) {

        serchLink(
                targetPlayer,
                family);
    }

    public void attackTarget(L1Character target) {
        if (target == null) return;

        if (isMasterTeleporting(target)) return;

        if (isMasterTeleporting(this)) return;

        if (target instanceof L1NpcInstance) {
            L1NpcInstance npc = (L1NpcInstance) target;
            if (npc.getHiddenStatus() != HIDDEN_STATUS_NONE) {
                allTargetClear();
                return;
            }
        }
        l1j.server.server.model.character.action.CAttackAction.attackNpcTarget(this, target);
        setSleepTime(getCurrentSpriteInterval(ActionSpriteMapper.forAttack(this)));
    }


    private boolean isMasterTeleporting(L1Character cha) {
        if (cha == null) return false;

        //  직접 PC인 경우
        if (cha instanceof L1PcInstance) {
            return ((L1PcInstance) cha).get_teleport();
        }
        //  펫, 소환수, 컴패니언, 서포트인 경우 마스터 확인
        L1Character master = null;
        if (cha instanceof L1PetInstance) {
            master = ((L1PetInstance) cha).getMaster();
        } else if (cha instanceof L1SummonInstance) {
            master = ((L1SummonInstance) cha).getMaster();
        } else if (cha instanceof L1CompanionInstance) {
            master = ((L1CompanionInstance) cha).get_master();
        } else if (cha instanceof L1SupportInstance) {
            master = ((L1SupportInstance) cha).getMaster();
        }

        if (master instanceof L1PcInstance) {
            return ((L1PcInstance) master).get_teleport();
        }

        return false;
    }


    public volatile L1ItemInstance _targetItem = null;
    public  int _itemSearchCooldown = 0;

    /** GetItemAction용 기존 public API/virtual hook 보존. */
    public void tickItemSearchCooldown() {
        CGetItemAction.tickSearchCooldown(this);
    }

    /** GetItemAction용 기존 public API/virtual hook 보존. */
    public void checkTargetItem() {
        CGetItemAction.checkTarget(this);
    }

    /** GetItemAction용 기존 public API/virtual hook 보존. */
    public void searchTargetItem() {
        CGetItemAction.searchTarget(this);
    }

    public void searchItemFromAir() {
        L1ItemInstance found = null;
        for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(this, 4)) {
            if (obj instanceof L1GroundInventory) {
                final L1GroundInventory gi = (L1GroundInventory) obj;
                if (gi.getSize() <= 0) continue;
                for (L1ItemInstance item : gi.getItems()) {
                    if (item == null) continue;
                    final int type = item.getItem().getType();
                    if (type == 6 || type == 7) { 
                        found = item;
                        break;
                    }
                }
            }
            if (found != null) break;
        }
        if (found == null) {
            return; 
        }
        if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
            setHiddenStatus(HIDDEN_STATUS_NONE);
            SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Movedown);
            setStatus(0);
            SC_WorldPutObject.broadcastNpc(this);
            onNpcAI();
            startChat(CHAT_TIMING_HIDE);
        }
        _targetItem = found;
    }

    /**
     * 기존 subclass override 계약을 보존하는 virtual hook.
     * 기본 item 접근/획득 구현은 GetItemAction이 담당한다.
     */
    public void onTargetItem() {
        CGetItemAction.onTargetItem(this);
    }

    /**
     * 기존 public/override 계약을 보존한다.
     * 실제 ground inventory transfer는 GetItemAction이 담당한다.
     */
    public void pickupTargetItem(L1ItemInstance targetItem) {
        CGetItemAction.pickup(this, targetItem);
    }

    public boolean noTarget() {
        if (_master != null && _master.getMapId() == getMapId() && getLocation().getTileLineDistance(_master.getLocation()) > 2) {
            return noTargetMaster();
        }    
        if (!L1World.getInstance().hasRecognizePlayerFast(this)) {
            return true;
        }   
        if (_master == null && isMovable() && !isRest()) {
            L1MobGroupInfo mobGroupInfo = getMobGroupInfo();
            if (mobGroupInfo == null || mobGroupInfo.isLeader(this)) {
                noTargetNormal();
            } else {
                return noTargetGroup(mobGroupInfo.getLeader());
            }
        }
        return false;
    }

    /**
     * 기존 subclass override 계약을 보존하는 virtual hook.
     * 기본 구현만 MoveAction으로 빠진다.
     */
    public boolean noTargetGroup(L1NpcInstance leader) {
        return CMoveAction.followGroup(this, leader);
    }

    /**
     * 기존 subclass override 계약을 보존하는 virtual hook.
     * 기본 wander 구현은 MoveAction이 담당한다.
     */
    public void noTargetNormal() {
        CMoveAction.move(this);
    }

    /**
     * 기존 subclass override 계약을 보존하는 virtual hook.
     * 기본 master follow 구현은 MoveAction이 담당한다.
     */
    public boolean noTargetMaster() {
        return CMoveAction.followMaster(this);
    }

    public void onFinalAction(L1PcInstance pc, String s) {
    }

    public final void clearNativeMoveState() {
        _moveCollision = 0;
        _moveSide = -1;
        _moveHeading = -1;
        _moveTargetId = 0;
        _moveTargetPos = 0;
        _moveContactX = 0;
        _moveContactY = 0;
        _moveSegmentX = 0;
        _moveSegmentY = 0;

        _relativeTargetId = 0;
        _relativeMapId = -1;
        _waitTargetId = 0;
        _waitDistance = 0;
        _waitPendingAction = CCreatureController.ACTION_NONE;

        _pathFindFailCount = 0;

        _desireMoveAroundTargetX = 0;
        _desireMoveAroundTargetY = 0;
    }

    public void tagertClear() {
        if (_target != null) {
            if (this instanceof L1MonsterInstance) {
                CDesirePQ.removeAttack(
                        (L1MonsterInstance) this,
                        _target);
            }
            _hateList.remove(_target);
        }
        _target = null;
        clearNativeMoveState();
        _aiEventFlags = 0;
        _aiBlockedTargetId = 0;
        _aiBlockedType = 0;
        _pendingAiAction = CCreatureController.ACTION_NONE;
        _aiAction = CCreatureController.ACTION_THINK;
    }

    public void targetRemove(L1Character target) {
        if (this instanceof L1MonsterInstance) {
            CDesirePQ.removeAttack(
                    (L1MonsterInstance) this,
                    target);
        }
        _hateList.remove(target);
        if (_target != null && _target.equals(target)) {
            _target = null;
            _aiEventFlags = 0;
            _aiBlockedTargetId = 0;
            _aiBlockedType = 0;
            // Removing an obsolete hate entry must not interrupt a random
            // segment/rest already owned by MOVE_AROUND. Full target resets
            // remain unchanged for ATTACK/CHASE and explicit clear-all.
            if (_aiAction != CCreatureController.ACTION_MOVE_AROUND_NATIVE
                    || !(this instanceof L1MonsterInstance)) {
                clearNativeMoveState();
                _pendingAiAction = CCreatureController.ACTION_NONE;
                _aiAction = CCreatureController.ACTION_THINK;
            }
        }
    }

    public void allTargetClear() {
        if (this instanceof L1MonsterInstance) {
            CDesirePQ.clear(
                    (L1MonsterInstance) this);
        }
        _hateList.clear();
        _dropHateList.clear();
        _target = null;
        _targetItem = null;
        clearNativeMoveState();
        _aiEventFlags = 0;
        _aiBlockedTargetId = 0;
        _aiBlockedType = 0;
        _pendingAiAction = CCreatureController.ACTION_NONE;
        _aiAction = CCreatureController.ACTION_THINK;
    }

    public void setMaster(L1PcInstance cha) {
        _master = cha;
        if (_aiRunning) {
            NpcServer.getInstance().wake(this);
        }
    }

    public L1Character getMaster() {
        return _master;
    }

    public void onNpcAI() {
    }

    public void refineItem() {
        if (getExp() == 0) {
            return;
        }
        final NpcRefineRecipes.Recipe[] recipes = NpcRefineRecipes.get(_npcTemplate.get_npcId());
        if (recipes == null) {
            return;
        }
        for (NpcRefineRecipes.Recipe r : recipes) {
            NpcRefineRecipes.tryRefine(this, getInventory(), r);
        }
    }

    public L1HateList getHateList() {
        return _hateList;
    }

    private int _paralysisTime = 0;

    public void setParalysisTime(int ptime) {
        _paralysisTime = ptime;
    }

    public int getParalysisTime() {
        return _paralysisTime;
    }

    private boolean _pickupItem;

    public boolean isPickupItem() {
        return _pickupItem;
    }

    public void setPickupItem(boolean flag) {
        _pickupItem = flag;
    }

    @Override
	public L1Inventory getInventory() {
		return _inventory;
	}

    public void setInventory(L1Inventory inventory) {
		_inventory = inventory;
    }

    public L1Npc getNpcTemplate() {
        return _npcTemplate;
    }

    public int getNpcId() {
        return _npcTemplate.get_npcId();
    }

    public void setPetcost(int i) {
        _petcost = i;
    }

    public int getPetcost() {
        return _petcost;
    }

    public void setSpawn(L1Spawn spawn) {
        _spawn = spawn;
    }

    public L1Spawn getSpawn() {
        return _spawn;
    }

    public void setSpawnNumber(int number) {
        _spawnNumber = number;
    }

    public int getSpawnNumber() {
        return _spawnNumber;
    }

    public void onDecay(boolean isReuseId) {
        int id = isReuseId ? getId() : 0;
        _spawn.executeSpawnTask(_spawnNumber, id);
    }

    public int PASS = 1;

    @Override
    public void onPerceive(L1PcInstance perceivedFrom) {
        if (perceivedFrom == null) {
            return;
        }
        if (SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this)) {
            perceivedFrom.addKnownObject(this);
        }
        onNpcAI();
        NpcServer.getInstance().wake(this);
    }

    @Override
    public void dispose() {
        l1j.server.MJEffectSystem.Realize.OnEffectThread.cancelNpc(this);
        _mobGroupInfo = null;
        _inventory = null;

        if (getNpcTemplate().get_npcId() != 8500138) {
            L1MobSkillUse.dispose(this);
        }

        _spawn = null;
        super.dispose();
    }

    public void deleteMe() {
        l1j.server.MJEffectSystem.Realize.OnEffectThread.cancelNpc(this);
        synchronized (this) {
            if (_destroyed) {
                return;
            }
            _destroyed = true;
            _corpseDeleteStarted = true;
            _corpseQueuedGeneration = 0;
            if ((_corpseTimerGeneration & 1) != 0) {
                ++_corpseTimerGeneration;
            }
        }
        L1Inventory inv = _inventory;
        if (inv != null) {
            inv.clearItems();
        }
        allTargetClear();
        
        final List<L1PcInstance> players = L1World.getInstance().getVisiblePlayerFast(this);

        L1World.getInstance().leaveAndRemoveObject(this);

        if (!players.isEmpty()) {
        	for (L1PcInstance pc : players) {
        		if (pc != null) {
        			pc.removeKnownObject(this);
        			SC_RemoveObject.send(pc.getNetConnection(), this);
        		}
        	}
        }
        removeAllKnownObjects();
        L1MobGroupInfo mobGroupInfo = getMobGroupInfo();
        if (mobGroupInfo == null) {
            if (isReSpawn()) {
                onDecay(true);
            }
        } else {
            if (mobGroupInfo.removeMember(this) == 0) {
                setMobGroupInfo(null);
                if (isReSpawn()) {
                    onDecay(false);
                }
            }
        }
        dispose();
    }

    public void NpcDie() {
        try {
            setDeathProcessing(true);
            setCurrentHp(0);
            setDead(true);
            setDeathProcessing(false);
            setExp(0);
            setKarma(0);
            setLawful(0);
            allTargetClear();
            deleteMe();
        } catch (Exception e) {
        }
    }
    
    public void ReceiveManaDamage(L1Character attacker, int damageMp) {
    }

    public void receiveCounterBarrierDamage(L1Character attacker, int damage) {
        receiveDamage(attacker, damage);
    }

    public void receiveDamage(L1Character attacker, int damage) {
    }
    public void onGetItem(L1ItemInstance item) {
        refineItem();
        getInventory().shuffle();
        if (getNpcTemplate().get_digestitem() > 0) {
            setDigestItem(item);
        }
    }
    
    public void approachPlayer(L1PcInstance pc) {
        if (pc == null)
            return;
        if (pc.hasSkillEffect(60) || pc.hasSkillEffect(97)) {
            return;
        }
        if (getHiddenStatus() == HIDDEN_STATUS_SINK) {
            if (getCurrentHp() == getMaxHp()) {
                if (pc.getLocation().getTileLineDistance(this.getLocation()) <= 2) {
                    appearOnGround(pc);
                }
            }
        } else if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
            if (getCurrentHp() == getMaxHp()) {
                if (pc.getLocation().getTileLineDistance(this.getLocation()) <= 1) {
                    appearOnGround(pc);
                }
            } else {
                searchItemFromAir();
            }
        }

    }

    public void appearOnGround(L1PcInstance pc) {
        if (pc == null)
            return;
        if (getHiddenStatus() == HIDDEN_STATUS_SINK) {
            setHiddenStatus(HIDDEN_STATUS_NONE);
            SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Appear);
            setStatus(0);
            SC_WorldPutObject.broadcastNpc(this);
            if (!pc.hasSkillEffect(60) && !pc.hasSkillEffect(97) && !pc.isGm()) {
                _hateList.add(pc, 0);
                _target = pc;
            }
            onNpcAI();
        } else if (getHiddenStatus() == HIDDEN_STATUS_FLY) {
            setHiddenStatus(HIDDEN_STATUS_NONE);
            SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_Movedown);
            setStatus(0);
            SC_WorldPutObject.broadcastNpc(this);
            if (!pc.hasSkillEffect(60) && !pc.hasSkillEffect(97) && !pc.isGm()) {
                _hateList.add(pc, 0);
                _target = pc;
            }
            onNpcAI();
            startChat(CHAT_TIMING_HIDE);
        }
    }

    protected void on_moved(){}

    public long setDirectionMove(int dir) { 
        if (dir < 0 || dir > 7 || isDead() || _destroyed) {
            return getCurrentSpriteInterval(EActionCodes.walk);
        }

        long bornInterval = getNpcTemplate().doProbabilityBornNpc(this);
        if (bornInterval > 0) {
            return bornInterval;
        }

        final long walkInterval = getCurrentSpriteInterval(EActionCodes.walk);

        final int fromMap = getMapId();
        final int fromX = getX();
        final int fromY = getY();
        final int toX = fromX + DIR_DX[dir];
        final int toY = fromY + DIR_DY[dir];

        setHeading(dir);

        if (!reserveNpcDest(toX, toY)) {
            return walkInterval;
        }

        boolean moved = false;
        try {
            synchronized (this) {
                if (isDead() || _destroyed
                        || getMapId() != fromMap || getX() != fromX || getY() != fromY) {
                    return walkInterval;
                }
                setX(toX);
                setY(toY);

                if (!commitNpcMove(fromX, fromY, toX, toY)) {
                    setX(fromX);
                    setY(fromY);
                    return walkInterval;
                }
                moved = true;
                L1World.getInstance().moveTo(this, fromMap, fromX, fromY);
            }
            NpcEventFactory.onNpcWorldEvent(this);
            SC_MoveChar.broadcast(this);
            if (handleSeadunShipIfArrived()) {
                return dir;
            }
            teleportHomeIfTooFar();
            teleportHomeIfEscapedZone();
        } finally {
            if (!moved) {
                cancelNpcDestReserve(toX, toY);
            }
        }
        return walkInterval;
    }

    /** 씨던 호선 NPC — 배 운항 특수 처리 대상 */
    private static final boolean isSeadunShip(int npcId) {
        return npcId == 91234 || npcId == 91236 || npcId == 91237;
    }

    private boolean handleSeadunShipIfArrived() {
        if (!isSeadunShip(getNpcId())) {
            return false;
        }
        if (getY() == SEADUN_SHIP_ARRIVAL_Y) {
            Config.seadunship = 0;
        }
        return true;
    }

    /** 이동 반경 제한 NPC(경비/성경비/상인/몬스터)가 집에서 너무 멀어지면 귀환 */
    private void teleportHomeIfTooFar() {
        if (getMovementDistance() <= 0) {
            return;
        }
        if (!(this instanceof L1GuardInstance)
                && !(this instanceof L1CastleGuardInstance)
                && !(this instanceof L1MerchantInstance)
                && !(this instanceof L1MonsterInstance)) {
            return;
        }
        if (getLocation().getLineDistance(new Point(getHomeX(), getHomeY())) > getMovementDistance()) {
            teleport(getHomeX(), getHomeY(), getHeading());
        }
    }

    /** 지정 구역 담당 NPC(45912~45916)가 구역을 이탈하면 귀환 */
    private void teleportHomeIfEscapedZone() {
        final int npcId = getNpcTemplate().get_npcId();
        if (npcId < RETURN_GUARD_ID_MIN || npcId > RETURN_GUARD_ID_MAX) {
            return;
        }
        final boolean inZone = getMapId() == RETURN_ZONE_MAP_ID
                && getX() >= RETURN_ZONE_MIN_X && getX() <= RETURN_ZONE_MAX_X
                && getY() >= RETURN_ZONE_MIN_Y && getY() <= RETURN_ZONE_MAX_Y;

                if (inZone) {
                    teleport(getHomeX(), getHomeY(), getHeading());
                }
    }

    public int moveDirection(int x, int y) {
        return CMoveAction.direction(this, x, y);
    }

    public int moveDirection(int x, int y, double d) {
        return CMoveAction.direction(this, x, y, d);
    }


    public int targetReverseDirection(int tx, int ty) {
        int dir = targetDirection(tx, ty);
        dir += 4;
        if (dir > 7) {
            dir -= 8;
        }
        return dir;
    }

    public void useItem(int type, int chance) {
        NpcPotionPolicy.tryUseItem(this, type, chance);
    }

    public boolean nearTeleport(int nx, int ny) {
        final int ox = nx;        
        final int oy = ny;

        final int rdir = ThreadLocalRandom.current().nextInt(8);
        final TileOccupancyNpc occNpc = TileOccupancyNpc.getInstance();
        final TileOccupancy occPc = TileOccupancy.getInstance();

        for (int i = 0; i < 8; i++) {
            final int dir = (rdir + i) & 7;
            final int tx = ox + DIR_DX[dir];
            final int ty = oy + DIR_DY[dir];

            if (!getMap().isPassable(tx, ty)) continue;
            if (occNpc.getOccupier(getMapId(), tx, ty) != 0) continue;
            if (occPc.getOccupier(getMapId(), tx, ty) != 0) continue;

            int face = dir + 4;
            if (face > 7) face -= 8;

            teleport(tx, ty, face);
            setCurrentMp(getCurrentMp() - 10);
            return true;
        }
        return false;
    }

    public void teleport(int nx, int ny, int dir) {
    	for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) {
    		if (pc == null) {
    			continue;
    		}
    		SC_SkillSound.send(pc, getId(), 169);
    		SC_RemoveObject.send(pc.getNetConnection(), this);

    		pc.removeKnownObject(this);
    	}

        final int oldX = getX();
        final int oldY = getY();
        final int oldMapId = getMapId();

        L1World.getInstance().relocateVisibleObject(this, nx, ny, oldMapId);
        setHeading(dir);
        NpcEventFactory.onNpcWorldEvent(this);
        teleportNpcOcc(oldMapId, oldX, oldY, oldMapId, nx, ny);
    }

    // ----------From L1Character-------------
    private String _nameId;
    private boolean _Agro;
    private boolean _Agrocoi;
    private boolean _Agrososc;
    private int _homeX;
    private int _homeY;
    private int _homeRnd;
    private boolean _reSpawn;
    private int _lightSize;
    private boolean _weaponBreaked;
    private int _hiddenStatus;
    private int _movementDistance = 0;
    private int _tempLawful = 0;

    public String getNameId() {
        return _nameId;
    }

    public void setNameId(String s) {
        _nameId = s;
    }

    public boolean isAgro() {
        return _Agro;
    }

    public void setAgro(boolean flag) {
        _Agro = flag;
    }

    public boolean isAgrocoi() {
        return _Agrocoi;
    }

    public void setAgrocoi(boolean flag) {
        _Agrocoi = flag;
    }

    public boolean isAgrososc() {
        return _Agrososc;
    }

    public void setAgrososc(boolean flag) {
        _Agrososc = flag;
    }

    public int getHomeX() {
        return _homeX;
    }

    public void setHomeX(int i) {
        _homeX = i;
    }

    public int getHomeY() {
        return _homeY;
    }

    public void setHomeY(int i) {
        _homeY = i;
    }

    public boolean isReSpawn() {
        return _reSpawn;
    }

    public void setRespawn(boolean flag) {
        _reSpawn = flag;
    }

    public int getLightSize() {
        return _lightSize;
    }

    public void setLightSize(int i) {
        _lightSize = i;
    }

    public boolean isWeaponBreaked() {
        return _weaponBreaked;
    }

    public void setWeaponBreaked(boolean flag) {
        _weaponBreaked = flag;
    }

    public int getHiddenStatus() {
        return _hiddenStatus;
    }

    public void setHiddenStatus(int i) {
        _hiddenStatus = i;
    }

    public int getMovementDistance() {
        return _movementDistance;
    }

    public void setMovementDistance(int i) {
        _movementDistance = i;
    }

    public int getTempLawful() {
        return _tempLawful;
    }

    public void setTempLawful(int i) {
        _tempLawful = i;
    }

    public void setAiRunning(boolean aiRunning) {
        _aiRunning = aiRunning;
        if (!aiRunning) {
            npcActionInvalidateGeneration();
            npcActionReleaseCadence();
        }
    }

    protected boolean isAiRunning() {
        return _aiRunning;
    }

    protected void setActived(boolean actived) {
        _actived = actived;
    }

    protected boolean isActived() {
        return _actived;
    }

    public void setFirstAttack(boolean firstAttack) {
        _firstAttack = firstAttack;
    }

    public boolean isFirstAttack() {
        return _firstAttack;
    }

    public void setSleepTime(long sleep_time) {
        _sleep_time = sleep_time;
    }

    public long getSleepTime() {
        return _sleep_time;
    }

    public final int getAiActionState() {
        return _aiAction;
    }

    // ---------------------------------------------------------------------
    // NpcAiEngine bridge. Wheel mutation is performed only by the shard owner.
    // ---------------------------------------------------------------------
    // No more than one outstanding register/unregister ticket of each type
    // per NPC. Deadline/state are read when the owner drains the ticket.
    public final boolean aiEngineTryQueueRegister() {
        return AI_REGISTER_QUEUED.compareAndSet(this, 0, 1);
    }

    public final void aiEngineClearQueuedRegister() {
        _aiRegisterQueued = 0;
    }

    public final boolean aiEngineTryQueueUnregister() {
        return AI_UNREGISTER_QUEUED.compareAndSet(this, 0, 1);
    }

    public final void aiEngineClearQueuedUnregister() {
        _aiUnregisterQueued = 0;
    }

    public final boolean aiEngineTryQueueWake() {
        // 외부 producer는 AI decision만 요청한다. action state는 CIO owner만 바꾼다.
        _aiThinkRequested = 1;
        return AI_WAKE_QUEUED.compareAndSet(this, 0, 1);
    }

    public final void startTimedDelete(int delayMs) {
        if (delayMs > 0) {
            NpcServer.getInstance().scheduleDelete(this, delayMs);
        }
    }

    public final long npcDeleteDeadlineMs() {
        return _timedDeleteDeadlineMs;
    }

    public final void npcDeleteDeadlineMs(long deadlineMs) {
        ++_timedDeleteGeneration;
        _timedDeleteDeadlineMs = deadlineMs;
    }

    public final int npcDeleteGeneration() {
        return _timedDeleteGeneration;
    }

    public final boolean aiEngineIsRunning() {
        return _aiRunning && !_destroyed && !isDead();
    }

    public final int npcActionNextGeneration() {
        int next = NPC_ACTION_GENERATION.incrementAndGet(this);
        if (next == 0) {
            next = NPC_ACTION_GENERATION.incrementAndGet(this);
        }
        return next;
    }

    public final int npcActionGeneration() {
        return _npcActionTimerGeneration;
    }

    public final void npcActionInvalidateGeneration() {
        npcActionNextGeneration();
    }

    public final int npcActionAdmissionEpoch() {
        return _npcActionAdmissionCancelEpoch;
    }

    public final void npcSkillRequestDestroyClear() {
        NPC_SKILL_DESTROY_CLEAR.set(this, 1);
    }

    public final boolean npcSkillConsumeDestroyClear() {
        return NPC_SKILL_DESTROY_CLEAR.getAndSet(this, 0) != 0;
    }

    public final void npcSkillHeartbeatRequestDeferredArm() {
        NPC_SKILL_HEARTBEAT_DEFERRED_ARM.set(this, 1);
    }

    public final boolean npcSkillHeartbeatConsumeDeferredArm() {
        return NPC_SKILL_HEARTBEAT_DEFERRED_ARM.getAndSet(this, 0) != 0;
    }

    public final void npcSkillHeartbeatClearDeferredArm() {
        NPC_SKILL_HEARTBEAT_DEFERRED_ARM.set(this, 0);
    }

    public final void npcSkillHeartbeatRequestDeferredTick(int generation) {
        // Only one generation may be live. An out-of-order stale CIO timer
        // must never overwrite a newer expiration already published.
        if (generation == 0) return;
        for (;;) {
            if (!skillEffectHeartbeatValid(generation)) return;
            final int pending = NPC_SKILL_HEARTBEAT_DEFERRED_TICK.get(this);
            if (pending == generation) return;
            if (pending != 0 && skillEffectHeartbeatValid(pending)) return;
            if (NPC_SKILL_HEARTBEAT_DEFERRED_TICK.compareAndSet(this,
                    pending, generation)) return;
        }
    }

    public final int npcSkillHeartbeatConsumeDeferredTick() {
        return NPC_SKILL_HEARTBEAT_DEFERRED_TICK.getAndSet(this, 0);
    }

    public final void npcSkillHeartbeatClearDeferredTick() {
        NPC_SKILL_HEARTBEAT_DEFERRED_TICK.set(this, 0);
    }

    public final void npcActionRequestDeferredCancel() {
        NPC_ACTION_ADMISSION_EPOCH.incrementAndGet(this);
        _npcActionDeferredCancel = 1;
    }

    public final boolean npcActionConsumeDeferredCancel() {
        return NPC_ACTION_DEFERRED_CANCEL.getAndSet(this, 0) != 0;
    }

    public final synchronized void npcActionDeferArm(int generation, long deadlineMs) {
        // An older producer must not overwrite a newer timer that already
        // reached the owner fallback while the event pool was saturated.
        if (generation == 0 || generation != npcActionGeneration()) return;
        _npcActionDeferredGeneration = generation;
        _npcActionDeferredDue = deadlineMs;
    }

    // Called by the NPC owner in the coalesced wake drain.
    public final synchronized void npcActionApplyDeferredArm() {
        final int generation = _npcActionDeferredGeneration;
        if (generation == 0) return;
        final long deadline = _npcActionDeferredDue;
        _npcActionDeferredGeneration = 0;
        _npcActionDeferredDue = 0L;
        if (generation == npcActionGeneration() && aiEngineIsRunning()) {
            npcControllerArm(deadline, generation);
        }
    }

    public final synchronized void npcActionClearDeferredCommands() {
        _npcActionDeferredCancel = 0;
        _npcActionDeferredGeneration = 0;
        _npcActionDeferredDue = 0L;
    }

    public final void npcActionClaimCadence() {
        _npcActionCadenceOwned = 1;
    }

    public final void npcActionReleaseCadence() {
        _npcActionCadenceOwned = 0;
    }

    public final boolean npcActionOwnsCadence() {
        return _npcActionCadenceOwned != 0;
    }

    public final void npcChaseRetargetRequest(final L1Character target) {
        _npcChaseRetarget = target;
    }

    public final L1Character npcChaseRetargetConsume() {
        return NPC_CHASE_RETARGET.getAndSet(this, null);
    }

    public final long aiEngineStep(long nowMs) {
        if (!_aiRunning || _destroyed || isDead()) {
            return -1L;
        }

        if (_aiThinkRequested != 0) {
            _aiThinkRequested = 0;
        }
        return AiRuntime.tick(this, nowMs);
    }

    public final long aiEngineDeadlineMs() {
        return _aiEngineDeadlineMs;
    }

    public final void aiEngineDeadlineMs(long deadlineMs) {
        _aiEngineDeadlineMs = deadlineMs;
    }

    public final int aiEngineWheelBucket() {
        return _aiEngineWheelBucket;
    }

    public final void aiEngineWheelBucket(int bucket) {
        _aiEngineWheelBucket = bucket;
    }

    public final L1NpcInstance aiEngineWheelPrev() {
        return _aiEngineWheelPrev;
    }

    public final void aiEngineWheelPrev(L1NpcInstance prev) {
        _aiEngineWheelPrev = prev;
    }

    public final L1NpcInstance aiEngineWheelNext() {
        return _aiEngineWheelNext;
    }

    public final void aiEngineWheelNext(L1NpcInstance next) {
        _aiEngineWheelNext = next;
    }

    @Override
    public void healHp(int amount) {
        if (amount > 0) applyNpcHpDelta(amount);
    }

    public final int applyNpcHpDelta(long delta) {
        if (_destroyed || isDead()) return -1;
        final int hp = changeHpAtomic(delta);
        if (hp >= 0) onNpcAtomicHpChanged(hp);
        return hp;
    }

    protected void onNpcAtomicHpChanged(int hp) {
    }

    public void setDeathProcessing(boolean deathProcessing) {
        _deathProcessing = deathProcessing;
    }

    public boolean isDeathProcessing() {
        return _deathProcessing;
    }

    public int drainMana(int drain) {
        if (_drainedMana >= Config.MANA_DRAIN_LIMIT_PER_NPC) {
            return 0;
        }
        int result = Math.min(drain, getCurrentMp());
        if (_drainedMana + result > Config.MANA_DRAIN_LIMIT_PER_NPC) {
            result = Config.MANA_DRAIN_LIMIT_PER_NPC - _drainedMana;
        }
        _drainedMana += result;
        return result;
    }

    public volatile boolean _destroyed = false;

    public boolean isDestroyed() {
        return _destroyed;
    }

    protected void transform(int transformId) {
        int transformGfxId = getNpcTemplate().getTransformGfxId();
        if (transformGfxId != 0) {
            SC_SkillSound.broadcast(this, getId(), transformGfxId);
        }
        L1Npc npcTemplate = NpcTable.getInstance().getTemplate(transformId);
        setting_template(npcTemplate);
        sendShape(getCurrentSpriteId());
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) {
            if (pc != null) {
                onPerceive(pc);
            }
        }
    }

    public void setRest(boolean _rest) {
        this._rest = _rest;
    }

    public boolean isRest() {
        return _rest;
    }

    public boolean isResurrect() {
        return _isResurrect;
    }

    public void setResurrect(boolean flag) {
        _isResurrect = flag;
    }

    @Override
    public synchronized void resurrect(int hp) {
        if (_destroyed || _corpseDeleteStarted) {
            return;
        }
        if ((_corpseTimerGeneration & 1) != 0) {
            ++_corpseTimerGeneration;
        }
        _corpseQueuedGeneration = 0;
        super.resurrect(hp);        
        L1SkillUse.cast(
                null,
                L1SkillId.CANCELLATION,
                 getId(), getX(), getY(),
                null,
                0,
                L1SkillUse.TYPE_LOGIN, this);
    }

    protected void startDeleteTimer() {
        final int generation;
        synchronized (this) {
            if (_destroyed || _corpseDeleteStarted || !isDead()
                    || (_corpseTimerGeneration & 1) != 0) {
                return;
            }
            generation = ++_corpseTimerGeneration;
        }

        final long delayMs = Math.max(1L, (long) MapsTable.getInstance().get_monster_respawn_seconds(getMapId()));
        CIOThreadCallback.getInstance().scheduleNpcTimer(this, TIMER_CORPSE_DELETE, generation, delayMs);
    }

    private volatile NpcDigestItems _digestItems;

    public void setDigestItem(L1ItemInstance item) {
        if (item == null || getNpcTemplate().get_digestitem() <= 0) return;

        NpcDigestItems d = _digestItems;
        if (d == null) {
            d = new NpcDigestItems(this);
            _digestItems = d;
        }
        d.add(item, getNpcTemplate().get_digestitem());
    }

    public void checkDigestExpiry() {
        final NpcDigestItems d = _digestItems;
        if (d != null) {
            d.checkExpired();
        }
    }

    public boolean isInMobGroup() {
        return getMobGroupInfo() != null;
    }

    public L1MobGroupInfo getMobGroupInfo() {
        return _mobGroupInfo;
    }

    public void setMobGroupInfo(L1MobGroupInfo m) {
        _mobGroupInfo = m;
    }

    public int getMobGroupId() {
        return _mobGroupId;
    }

    public void setMobGroupId(int i) {
        _mobGroupId = i;
    }

    public void startChat(int chatTiming) {
        int npcId = this.getNpcTemplate().get_npcId();
        final NpcChatTable table = NpcChatTable.getInstance();

        L1NpcChat npcChat;
        switch (chatTiming) {
        case CHAT_TIMING_APPEARANCE: npcChat = table.getTemplateAppearance(npcId); break;
        case CHAT_TIMING_DEAD:       npcChat = table.getTemplateDead(npcId); break;
        case CHAT_TIMING_HIDE:       npcChat = table.getTemplateHide(npcId); break;
        case CHAT_TIMING_GAME_TIME:  npcChat = table.getTemplateGameTime(npcId); break;
        default: return; 
        }
        if (npcChat == null) return;

        final L1NpcChatTimer timer = npcChat.isRepeat()
                ? new L1NpcChatTimer(this, npcChat, npcChat.getRepeatInterval())
                        : new L1NpcChatTimer(this, npcChat);

                timer.startChat(npcChat.getStartDelayTime());
    }

    public void set_num(int num) {
        this.num = num;
    }

    public int get_num() {
        return num;
    }

    private long boss_time;

    public long get_boss_time() {
        return boss_time;
    }

    private int boss_yn;
    private String yn_msg;

    private int boss_onoff;
    private String subnpc;
    private int boss_effect;
    private String subtitle;

    public String getSubTitle() {
        return subtitle;
    }

    public void setSubTitle(String s) {
        subtitle = s;
    }

    public String getSubNpc() {
        return subnpc;
    }

    public void setSubNpc(String s) {
        subnpc = s;
    }

    public int get_boss_onoff() {
        return boss_onoff;
    }

    public void set_boss_onoff(int i) {
        boss_onoff = i;
    }

    public String get_yn_msg() {
        return yn_msg;
    }

    public void set_yn_msg(String s) {
        yn_msg = s;
    }



    public int getHomeRnd() {
        return _homeRnd;
    }

    public int get_boss_yn() {
        return boss_yn;
    }

    public int get_boss_effect() {
        return boss_effect;
    }

    public void set_boss_effect(int i)
    {
        boss_effect = i;
    }
    public void set_boss_yn(int i)
    {
        this.boss_yn = i;
    }

    public void setHomeRnd(int i)
    {
        this._homeRnd = i;
    }

    public void set_boss_time(long l) {
        this.boss_time = l;
    }
    private String boss_name;
    public String get_boss_name()
    {
        return boss_name;
    }

    public void set_boss_name(String s)
    {
        boss_name = s;
    }

    private int boss_type;

    public int get_boss_type() {
        return boss_type;
    }

    public void set_boss_type(int i) {
        this.boss_type = i;
    }

    private int boss_hour;

    public int get_boss_hour() {
        return boss_hour;
    }

    public void set_boss_hour(int i) {
        this.boss_hour = CommonUtil.get_current(i, 0, 24);
    }

    private int boss_minute;

    public int get_boss_minute() {
        return boss_minute;
    }

    public void set_boss_minute(int i) {
        this.boss_minute = CommonUtil.get_current(i, 0, 60);
    }

    private String boss_msg;

    public String get_boss_msg() {
        return boss_msg;
    }

    public void set_boss_msg(String s) {
        this.boss_msg = s;
    }

    public void deleteRe() {
        synchronized (this) {
            if (_destroyed) {
                return;
            }
            _destroyed = true;
            _corpseDeleteStarted = true;
            _corpseQueuedGeneration = 0;
            if ((_corpseTimerGeneration & 1) != 0) {
                ++_corpseTimerGeneration;
            }
        }

        L1Inventory inv = _inventory;
        if (inv != null) {
            inv.clearItems();
        }
        allTargetClear();
        _master = null;

        L1World.getInstance().leaveAndRemoveObject(this);
        List<L1PcInstance> players =
        		L1World.getInstance().getRecognizePlayer(this);

        if (players.size() > 0) {
        	for (L1PcInstance pc : players) {
        		if (pc != null) {
        			pc.removeKnownObject(this);

        			SC_RemoveObject.send(
        					pc.getNetConnection(),
        					this);
        		}
        	}
        }
        removeAllKnownObjects();
        // onDecay(false);

    }

    public L1NpcInstance CastleTarget = null;

    public long skillDelayTime = 0;
    public int castgfx = 0;
    public long isMentTime = 0;

    private boolean _isPassObject = true;

    public boolean isPassObject() {
        return _isPassObject;
    }

    public void setPassObject(boolean b) {
        _isPassObject = b;
    }

    private long _explosion_remain_time;

    public long getExplosionTime() {
        return _explosion_remain_time;
    }

    public void setExplosionTime(long l) {
        _explosion_remain_time = l;
    }

    public void startExplosionTime(long l) {
        setExplosionTime(l);
        ThreadPool.getInstance().scheduleRequired(new ExplosionCounter(this), 1000);
    }

    public class ExplosionCounter implements Runnable {
        private L1NpcInstance _npc;
        private boolean _isSending;

        public ExplosionCounter(L1NpcInstance npc) {
            _npc = npc;
            _isSending = false;
        }

        @Override
        public void run() {
            try {
                _explosion_remain_time -= 1000;
                if (!_isSending && _explosion_remain_time <= 30000) {
                    _isSending = true;
                    SC_ExplosionNoti.broadcast(_npc, _npc.getId(), _explosion_remain_time);
                }
            } catch (Exception e) {

            } finally {
                if (_explosion_remain_time <= 0 || isDead())
                    _explosion_remain_time = 0;
                else
                    ThreadPool.getInstance().scheduleRequired(this, 1000);
            }
        }
    }

    private String _shopName;
    public String getShopName() {
        return _shopName;
    }
    public void setShopName(String name) {
        _shopName = name;
    }

    public boolean isMovable() {
        return _npcTemplate.isMovable();
    }
    public boolean isActionable() {
        return _npcTemplate.isActionable();
    }
    public boolean isDynamic() {
        return _npcTemplate.isDynamic();
    }

    private short occMapId() {
        return (short) getMapId();
    }

    public void occupyNpcTileNow() {
        if (!shouldUseNpcOccupancy()) {
            return;
        }
        TileOccupancyNpc.getInstance().tryOccupyNow(occMapId(), getX(), getY(), getId());
    }

    public void releaseNpcTileNow() {
        if (!shouldUseNpcOccupancy()) {
            return;
        }
        TileOccupancyNpc.getInstance().releaseNow(occMapId(), getX(), getY(), getId());
    }

    private boolean reserveNpcDest(int x, int y) {
        if (isPassableNpc() || isDead() || _destroyed) {
            return true;
        }
        return TileOccupancyNpc.getInstance().canMoveTo(occMapId(), x, y, getId(), 140L);
    }

    private void cancelNpcDestReserve(int x, int y) {
        if (isPassableNpc()) {
            return;
        }
        TileOccupancyNpc.getInstance().cancelMove(occMapId(), x, y, getId());
    }

    private boolean commitNpcMove(int sx, int sy, int dx, int dy) {
        if (isPassableNpc()) {
            return true;
        }
        return TileOccupancyNpc.getInstance().commitMove(occMapId(), sx, sy, dx, dy, getId());
    }

    public boolean teleportNpcOcc(int oldMapId, int oldX, int oldY, int newMapId, int newX, int newY) {
        if (isPassableNpc()) {
            return true;
        }
        return TileOccupancyNpc.getInstance().teleportRelaxed(
                (short) oldMapId, oldX, oldY, (short) newMapId, newX, newY, getId());
    }

    public static final Class<?>[] OCCUPANCY_CLASSES = {
            L1MonsterInstance.class, L1PetInstance.class, L1SummonInstance.class,  L1CastleGuardInstance.class, L1GuardianInstance.class, L1GuardInstance.class,
            L1MerchantInstance.class, L1TeleporterInstance.class, L1DwarfInstance.class, L1CompanionInstance.class, L1PeopleInstance.class
    };
    
    public boolean shouldUseNpcOccupancy() {
        if (isPassableNpc()) {
            return false;
        }
        for (final Class<?> c : OCCUPANCY_CLASSES) {
            if (c.isInstance(this)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPassableNpc() {
        return this instanceof L1DollInstance;
    }

    public void tickRegeneration(long nowMs) {
        if (_destroyed || isDead()) return;

        if (_hprInterval > 0 && nowMs >= _nextHprAt) {
            _nextHprAt = nowMs + _hprInterval;
            final int hp = getCurrentHp();
            if (hp > 0 && hp < getMaxHp()) {
                applyNpcHpDelta(_hprPoint);
            }
        }

        if (_mprInterval > 0 && nowMs >= _nextMprAt) {
            _nextMprAt = nowMs + _mprInterval;
            final int mp = getCurrentMp();
            if (mp < getMaxMp()) {
                setCurrentMp(mp + _mprPoint);
            }
        }
    }
    
    public final void aiEngineClearQueuedWake() {
        _aiWakeQueued = 0;
    }

    public L1Character getTarget() { return _target; }

    private static int _instanceType = -1;

    @Override
    public int getL1Type() {
        return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_NPC : _instanceType;
    }

}
