package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.ai.core.PathNodeWorld;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.animation.SpriteInformation;
import l1j.server.server.model.character.animation.loader.SpriteInformationLoader;
import l1j.server.server.model.character.combat.statistics.Kda;
import l1j.server.server.model.character.skill.CSkillInfo;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Lawful;
import l1j.server.server.model.network.core.sc.packet.SC_PinkName;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.character.effect.poison.PoisonEffect;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;
import l1j.server.server.shared.L1Type;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.IntRange;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPECIAL_RESISTANCE_NOTI;
import l1j.server.server.utils.eKind;
public class L1Character extends L1Object {
	private static final long serialVersionUID = 1L;
	// 캐릭터 기본
	private String _name;
	private String _title;
	private int _level;
	private int _exp;
	private volatile int _currentHp;
    private static final AtomicIntegerFieldUpdater<L1Character> CURRENT_HP =
            AtomicIntegerFieldUpdater.newUpdater(L1Character.class, "_currentHp");
	private volatile int _currentMp;
	private int _maxHp = 0;
	private int _trueMaxHp = 0;
	private int _maxMp = 0;
	private int _trueMaxMp = 0;
	private int _lawful;
	private int _karma;
	private int _MagicHit;
	// 상태
	private int _heading; // ● 방향 0. 좌상 1. 상 2. 우상 3. 오른쪽 4. 우하 5. 하 6. 좌하 7. 좌
	private int _moveSpeed; // ● 스피드 0. 통상 1. 헤이 파업 2. 슬로우
	private int _braveSpeed; // ● 치우침 이브 상태 0. 통상 1. 치우침 이브
	private volatile PoisonEffect _poison;
	private static final AtomicReferenceFieldUpdater<L1Character, PoisonEffect> POISON =
			AtomicReferenceFieldUpdater.newUpdater(L1Character.class, PoisonEffect.class, "_poison");
	private volatile boolean _paralyzed;
	private volatile boolean curseParalyzed;
	private volatile boolean poisonParalyzed;
	private volatile L1Paralysis _paralysis;
	private static final AtomicReferenceFieldUpdater<L1Character, L1Paralysis> PARALYSIS =
			AtomicReferenceFieldUpdater.newUpdater(L1Character.class, L1Paralysis.class, "_paralysis");
	private volatile boolean _isDead;

	protected Light light = null; // 캐릭터 주위 빛
	// CStats: original, permanent and equipment sources, shared by every creature.
	// Primitive byte lanes preserve the existing signed range and update order.
	byte m_OriginalStatStr, m_StatStr, m_AddedStatStr;
	byte m_OriginalStatDex, m_StatDex, m_AddedStatDex;
	byte m_OriginalStatInt, m_StatInt, m_AddedStatInt;
	byte m_OriginalStatWis, m_StatWis, m_AddedStatWis;
	byte m_OriginalStatCon, m_StatCon, m_AddedStatCon;
	byte m_OriginalStatCha, m_StatCha, m_AddedStatCha;
	volatile long m_SkillPrimaryStats;
	int m_nStatSp;
	volatile int m_nSkillSp;
	// BEGIN shared resistance sources (CCreature)
	private volatile boolean m_ResistanceDisposed;
	int m_nOrgMDefend;
	int m_nAddedMDefend;
	int m_nFireResist;
	int m_nWaterResist;
	int m_nWindResist;
	int m_nEarthResist;
	int m_nPvpDamageReduction;
	int m_nPvpDamage;
	int m_nFouDamage;
	int m_nAinEfficiency;
	int m_nPotionRecoveryAmount;
	int m_nPotionRecoveryRate;
	int m_nHealDefence;
	int m_nResistAbility;
	int m_nResistSpirit;
	int m_nResistDragon;
	int m_nResistFear;
	int m_nResistAll;
	int m_nPierceAbility;
	int m_nPierceSpirit;
	int m_nPierceDragon;
	int m_nPierceFear;
	int m_nPierceAll;
	volatile int m_nSkillMDefend;
	volatile int m_nSkillPvp;
	volatile long m_SkillSpecialResistance;
	volatile int m_nSkillSpecialPenalty;
	volatile int m_nSkillSpecialPierce;
	volatile long m_SkillElemental;
	// END shared resistance sources
	// NC-style defense parameters: source, skill modifier and effective AC.
	private int m_nBasePDefend;
	private int m_nAddedPDefend;
	private int m_nSkillPDefend;
	private volatile int m_nPDefend;
	
	private int _status;
	private int _Desperadolv = 0;
	// 대미지
	private int _dmgup = 0;
	private int _trueDmgup = 0;
	private int _bowDmgup = 0;
	private int _trueBowDmgup = 0;
	private int _hitup = 0;
	private int _trueHitup = 0;
	// Signed 16-bit values from low to high: melee hit, melee damage, ranged damage, ranged hit.
	private volatile long pumpedWeapon;
	// Signed 16-bit ranged critical, magic critical, magic hit, and weapon hit rate.
	private volatile long pumpedCombatRates;
	private int _bowHitup = 0;
	private int _trueBowHitup = 0;


	private int _missile_critical_rate = 0;
	private int _melee_critical_rate = 0;
	private int _magic_critical_rate = 0;
	private int _summonre = 0;

	private volatile SkillEffectContainer _skillEffects;
	private int retiredSkillHeartbeatGeneration;
	
	private transient volatile CKnownObjectContainer _knownContainer;
	private static final AtomicReferenceFieldUpdater<L1Character, CKnownObjectContainer> KNOWN_CONTAINER =
			AtomicReferenceFieldUpdater.newUpdater(L1Character.class, CKnownObjectContainer.class, "_knownContainer");
	private CKnownObjectContainer knownContainer(boolean create) {
		CKnownObjectContainer index = _knownContainer;
		if (index != null || !create) return index;
		final CKnownObjectContainer fresh = new CKnownObjectContainer();
		return KNOWN_CONTAINER.compareAndSet(this, null, fresh) ? fresh : _knownContainer;
	}

	public boolean isPassive(int passiveId) {
		return false;
	}

	public int getMagicHit() {
		return _MagicHit + (short) (pumpedCombatRates >>> 32);
	}

	protected final int getPumpedWeaponHitRate() {
		return (short) (pumpedCombatRates >>> 48);
	}
	
	public void addMagicHit(int i) {
		this._MagicHit += i;
	}

	public void dispose() {
        synchronized (this) {
            _ncPassingPos = null;
            _ncPassingNext = 0;
            _ncPassingCount = 0;
        }
        _ncAttackLeaderId = null;
		clearSkillEffectTimer();
		final CKnownObjectContainer known = KNOWN_CONTAINER.getAndSet(this, CKnownObjectContainer.CLOSED);
		if (known != null && known != CKnownObjectContainer.CLOSED) known.close();

		m_ResistanceDisposed = true;

		if (light != null) {
			light.dispose();
			light = null;
		}

		disposeShopInfo();
	}

	public L1Character() {
		_level = 1;
		light = new Light(this);
	}


    // NC CCreature passing-position ring and four directional attack leaders.
    // Allocate only when a creature actually moves or is chased. All movement
    // history reads/writes are protected by this character's monitor; leader
    // CAS uses an int-array VarHandle for cross-CIO owner safety.
    private static final int NC_PASSING_CAPACITY = 15;
    private static final VarHandle NC_LEADER_SLOT =
            MethodHandles.arrayElementVarHandle(int[].class);
    private transient long[] _ncPassingPos;
    private transient int _ncPassingNext;
    private transient int _ncPassingCount;
    private transient int _ncPassingMap = -1;
    private transient volatile int[] _ncAttackLeaderId;

    public final synchronized void ResetPassingPos(
            final int x, final int y, final int mapId) {
        if (_ncPassingPos != null) {
            _ncPassingNext = 0;
            _ncPassingCount = 0;
            _ncPassingMap = mapId;
        }
    }

    /**
     * Port of CCreature::AddPassingPos, with L1 two-dimensional tile
     * projection and the NC new-add straight-segment compression.
     * Invoked only after a PC/NPC movement has actually committed.
     */
    public final synchronized void AddPassingPos(final int fromX,
            final int fromY, final int toX, final int toY, final int mapId) {
        if (fromX == toX && fromY == toY) return;
        long[] passing = _ncPassingPos;
        if (passing == null) {
            passing = new long[NC_PASSING_CAPACITY];
            _ncPassingPos = passing;
        }
        final long from = PathNodeWorld.pack(fromX, fromY);
        final long to = PathNodeWorld.pack(toX, toY);
        final int previous = (_ncPassingNext + NC_PASSING_CAPACITY - 1)
                % NC_PASSING_CAPACITY;
        if (_ncPassingMap != mapId || _ncPassingCount == 0
                || passing[previous] != from) {
            // A teleport or an intervening relocation invalidates old paths.
            _ncPassingMap = mapId;
            _ncPassingNext = 1;
            _ncPassingCount = 1;
            passing[0] = from;
        }

        final int prev = (_ncPassingNext + NC_PASSING_CAPACITY - 1)
                % NC_PASSING_CAPACITY;
        final int older = (_ncPassingNext + NC_PASSING_CAPACITY - 2)
                % NC_PASSING_CAPACITY;
        if (_ncPassingCount >= 2) {
            final int olderX = PathNodeWorld.unpackX(passing[older]);
            final int olderY = PathNodeWorld.unpackY(passing[older]);
            final long dx = (long) fromX - toX;
            final long dy = (long) fromY - toY;
            // The original threshold is 1024 world units, or 64 L1 tiles.
            if (dx * dx + dy * dy < 64L * 64L) {
                final L1Map map = getMap();
                if (map != null && PathNodeWorld.moveStraight(
                        map, olderX, olderY, toX, toY)) {
                    passing[prev] = to;
                    return;
                }
            }
        }

        passing[_ncPassingNext] = to;
        _ncPassingNext = (_ncPassingNext + 1) % NC_PASSING_CAPACITY;
        if (_ncPassingCount < NC_PASSING_CAPACITY) ++_ncPassingCount;
    }

    /** NC CCreature::GetPassingPosList, newest first, caller-owned scratch. */
    public final synchronized int GetPassingPosList(final long[] output) {
        if (output == null || output.length < NC_PASSING_CAPACITY
                || _ncPassingPos == null || _ncPassingCount == 0
                || _ncPassingMap != getMapId()) {
            return 0;
        }
        final int last = (_ncPassingNext + NC_PASSING_CAPACITY - 1)
                % NC_PASSING_CAPACITY;
        final long current = _ncPassingPos[last];
        // Do not surface pre-teleport history if relocation skipped Reset().
        if (Math.max(Math.abs(PathNodeWorld.unpackX(current) - getX()),
                     Math.abs(PathNodeWorld.unpackY(current) - getY())) > 1) {
            return 0;
        }
        int index = last;
        for (int i = 0; i < _ncPassingCount; i++) {
            output[i] = _ncPassingPos[index];
            index = (index + NC_PASSING_CAPACITY - 1) % NC_PASSING_CAPACITY;
        }
        return _ncPassingCount;
    }

    /** NC CCreature::GetMyPositionFrom, projected to L1 2D quadrants 1..4. */
    public final int GetMyPositionFrom(final L1Character other) {
        if (other == null) return 1;
        final double angle = Math.atan2(
                (double) getY() - other.getY(),
                (double) getX() - other.getX());
        final int pos = 2 - (int) (angle / Math.PI * -2.0d);
        return Math.max(1, Math.min(4, pos));
    }

    public final int GetAttackLeaderId(final int position) {
        final int[] leaders = _ncAttackLeaderId;
        return leaders != null && position >= 1 && position <= 4
                ? (int) NC_LEADER_SLOT.getVolatile(leaders, position) : 0;
    }

    /** NC CCreature::SetAttackLeaderRelated: one CAS per direction. */
    public final boolean SetAttackLeaderRelated(final L1NpcInstance actor,
            final int expectedId) {
        if (actor == null || actor.getId() == 0) return false;
        final int position = actor.GetMyPositionFrom(this);
        int[] leaders = _ncAttackLeaderId;
        if (leaders == null) {
            synchronized (this) {
                leaders = _ncAttackLeaderId;
                if (leaders == null) {
                    leaders = new int[5];
                    _ncAttackLeaderId = leaders;
                }
            }
        }
        return NC_LEADER_SLOT.compareAndSet(
                leaders, position, expectedId, actor.getId());
    }

    /** NC CCreature::AbandonAttackLeaderOf. */
    public final void AbandonAttackLeaderOf(final L1NpcInstance actor) {
        if (actor == null) return;
        final int[] leaders = _ncAttackLeaderId;
        if (leaders == null) return;
        final int id = actor.getId();
        for (int position = 1; position <= 4; position++) {
            NC_LEADER_SLOT.compareAndSet(leaders, position, id, 0);
        }
    }

    /** NC CCreature::IsCloserThan, L1 static sight and squared tile distance. */
    public final boolean IsCloserThan(final L1Character leader,
            final L1Character target) {
        if (leader == null) return true;
        if (target == null || getMapId() != target.getMapId()
                || leader.getMapId() != target.getMapId()) return false;
        final long dx = (long) getX() - target.getX();
        final long dy = (long) getY() - target.getY();
        final long bx = (long) leader.getX() - target.getX();
        final long by = (long) leader.getY() - target.getY();
        return dx * dx + dy * dy < bx * bx + by * by
                && PathNodeWorld.moveStraight(
                        getMap(), getX(), getY(), leader.getX(), leader.getY());
    }

    /**
     * NC CCreature::GetPassingPosOnSight projected to L1:
     * direct static sight, target's up-to-15 passing points, directional
     * attack leader's passing points, baked CPathNodeWorld fallback, then a
     * last visible target passing point. Physical motion still belongs to
     * SetMoving and WorldCollisionHash, never this static waypoint selector.
     */
    public final long GetPassingPosOnSight(final L1NpcInstance actor,
            final int destinationX, final int destinationY,
            final long[] targetPassing, final long[] leaderPassing) {
        if (actor == null || targetPassing == null || leaderPassing == null
                || actor.isDead() || isDead()
                || actor.getMapId() != getMapId()) {
            return PathNodeWorld.NO_PATH;
        }
        final L1Map map = actor.getMap();
        if (map == null || map.isNull()) return PathNodeWorld.NO_PATH;
        final int mapId = actor.getMapId();
        final int sx = actor.getX();
        final int sy = actor.getY();
        final int tx = getX();
        final int ty = getY();
        final int actorId = actor.getId();
        final WorldCollisionHash collision = WorldCollisionHash.getInstance();

        // NC's straight case does not need a graph search. A target's
        // occupied current tile is not a usable L1 movement destination.
        if (PathNodeWorld.moveStraight(
                map, sx, sy, destinationX, destinationY)
                && collision.canEnter(
                        (short) mapId, destinationX, destinationY, actorId)
                && (sx != destinationX || sy != destinationY)) {
            return PathNodeWorld.pack(destinationX, destinationY);
        }

        final int count = GetPassingPosList(targetPassing);
        for (int i = 0; i < count; i++) {
            final long point = targetPassing[i];
            final int x = PathNodeWorld.unpackX(point);
            final int y = PathNodeWorld.unpackY(point);
            if ((x != sx || y != sy) && map.isPassable(x, y)
                    && collision.canEnter((short) mapId, x, y, actorId)
                    && PathNodeWorld.moveStraight(map, sx, sy, x, y)
                    && PathNodeWorld.moveStraight(map, x, y, tx, ty)) {
                return point;
            }
        }

        int goalX = tx;
        int goalY = ty;
        if (actor instanceof L1MonsterInstance) {
            final int position = actor.GetMyPositionFrom(this);
            final int leaderId = GetAttackLeaderId(position);
            final L1Object leaderObject = leaderId == 0
                    ? null : L1World.getInstance().findObject(leaderId);
            if (leaderObject instanceof L1MonsterInstance
                    && leaderObject != actor) {
                final L1MonsterInstance leader = (L1MonsterInstance) leaderObject;
                if (!leader.isDead() && leader.getCurrentHp() > 0
                        && leader.isMovable()
                        && leader.getMapId() == mapId
                        && leader.getTarget() == this) {
                    final int n = leader.GetPassingPosList(leaderPassing);
                    for (int i = 0; i < n; i++) {
                        final long point = leaderPassing[i];
                        final int x = PathNodeWorld.unpackX(point);
                        final int y = PathNodeWorld.unpackY(point);
                        if ((x != sx || y != sy) && map.isPassable(x, y)
                                && collision.canEnter((short) mapId,
                                        x, y, actorId)
                                && PathNodeWorld.moveStraight(
                                        map, sx, sy, x, y)) {
                            return point;
                        }
                    }
                    if (n != 0) {
                        goalX = PathNodeWorld.unpackX(leaderPassing[0]);
                        goalY = PathNodeWorld.unpackY(leaderPassing[0]);
                    }
                }
            }
        }

        final long graphPoint = PathNodeWorld.getInstance().pathFind(
                mapId, sx, sy, goalX, goalY,
                PathNodeWorld.SEARCH_LIMIT_LONG);
        if (graphPoint != PathNodeWorld.NO_PATH) {
            return graphPoint;
        }
        // NC fallback after PFR_ERROR: a visible target passing position.
        for (int i = 0; i < count; i++) {
            final long point = targetPassing[i];
            final int x = PathNodeWorld.unpackX(point);
            final int y = PathNodeWorld.unpackY(point);
            if ((x != sx || y != sy) && map.isPassable(x, y)
                    && collision.canEnter((short) mapId, x, y, actorId)
                    && PathNodeWorld.moveStraight(map, sx, sy, x, y)) {
                return point;
            }
        }
        return PathNodeWorld.NO_PATH;
    }

	public double getCurrentHpPercent() {
		return (100D / (double) getMaxHp()) * (double) getCurrentHp();
	}

	public double getCurrentMpPercent() {
		return (100D / (double) getMaxMp()) * (double) getCurrentMp();
	}

	public int getLongLocation() {
		int pt = (getX() << 16) & 0xffff0000;
		pt |= (getY() & 0x0000ffff);
		return pt;
	}

	public int getLongLocationReverse() {
		int pt = (getY() << 16) & 0xffff0000;
		pt |= (getX() & 0x0000ffff);
		return pt;
	}

	private int _effectedDG = 0;
	private int _effectedER = 0;
	// ER in the low half, DG in the high half.
	private volatile int pumpedEvasion;

	public void addDg(int i) {
		_effectedDG += i;
	}
	

	public int getDg() {
		return _effectedDG + GetPDefendEvasion(m_nPDefend) + (pumpedEvasion >> 16);
	}



	private int getDefaultER() {
		return CalcStat.원거리회피(GetTotalStatDex());
	}

	private int getEffectedER() {
		return _effectedER + (short) pumpedEvasion;
	}

	public void addEffectedER(int i) {
		_effectedER += i;
	}

	public int getTotalER() {
		int er = 0;
		er += getDefaultER();
		er += getEffectedER();
		er += GetPDefendEvasion(m_nPDefend);
		if (er < 0) {
			er = 0;
		} else {
			if (hasSkillEffect(L1SkillId.STRIKER_GALE)) {
				er = er / 3;
			}
		}
		return er;
	}

	public void resurrect(int hp) {
	    if (!isDead()) {
	        return;
	    }

	    if (hp <= 0) {
	        hp = 1;
	    }

	    setCurrentHp(hp);
	    setDead(false);
	    setStatus(0);
	    L1PolyMorph.undoPoly(this);

	    for (L1PcInstance pc :
	            L1World.getInstance().getRecognizePlayer(this)) {

	        if (pc == null) {
	            continue;
	        }
	        pc.outOfSight(this);
	        if (pc.canSeeObject(this)) {
	            pc.inSight(this);
	        }
	    }
	}

	public int getCurrentHp() {
		return _currentHp;
	}

	public void setCurrentHp(int i) {
		if (i >= getMaxHp()) {
			i = getMaxHp();
		}
		if (i < 0)
			i = 0;

        CURRENT_HP.set(this, i);
    }

    protected final int changeHpAtomic(long delta) {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            if (oldHp <= 0) return -1;
            final long value = (long) oldHp + delta;
            final int maxHp = getMaxHp();
            final int next = value <= 0 ? 0 : value >= maxHp ? maxHp : (int) value;
            if (CURRENT_HP.compareAndSet(this, oldHp, next)) return next;
        }
    }

    // Existing primitive HP atomic state, with the previous value retained in
    // the high word for callers that must keep health-change side effects.
    // No boxing or allocation. A negative result means HP was already zero.
    protected final long changeHpAtomicPair(long delta) {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            if (oldHp <= 0) return -1L;
            final long value = (long) oldHp + delta;
            final int maxHp = getMaxHp();
            final int next = value <= 0 ? 0 : value >= maxHp ? maxHp : (int) value;
            if (CURRENT_HP.compareAndSet(this, oldHp, next))
                return ((long) oldHp << 32) | (next & 0xFFFFFFFFL);
        }
    }

	public int getCurrentMp() {
		return _currentMp;
	}

	public void setCurrentMp(int i) {
		if (i >= getMaxMp()) {
			i = getMaxMp();
		}
		if (i < 0)
			i = 0;
		
		_currentMp = i;
	}

	public boolean isSleeped() {
		return hasAnySkillEffect(L1SkillId.DARK_BLIND, L1SkillId.PHANTASM, L1SkillId.FOG_OF_SLEEPING);
	}

	public boolean isParalyzed() {
		return _paralyzed || curseParalyzed || poisonParalyzed;
	}

	public void setParalyzed(boolean paralyzed) {
		_paralyzed = paralyzed;
	}

	public void setCurseParalyzed(boolean paralyzed) {
		curseParalyzed = paralyzed;
	}

	public void setPoisonParalyzed(boolean paralyzed) {
		poisonParalyzed = paralyzed;
	}

	private boolean _thunderGrab;

	public boolean isThunderGrab() {
		return _thunderGrab;
	}

	public void setThunderGrab(boolean thunderGrab) {
		_thunderGrab = thunderGrab;
	}

	public L1Paralysis getParalysis() {
		return _paralysis;
	}

	/** Claim one curse-paralysis lifetime before sending it to the owner. */
	public boolean claimParalysis(L1Paralysis effect) {
		return effect != null && PARALYSIS.compareAndSet(this, null, effect);
	}

	/** A late timer must never clear a newer paralysis lifetime. */
	public boolean clearParalysisIfSame(L1Paralysis effect) {
		return effect != null && PARALYSIS.compareAndSet(this, effect, null);
	}

	public void setParalaysis(L1Paralysis p) {
		_paralysis = p;
	}

	public void cureParalaysis() {
		final L1Paralysis effect = _paralysis;
		if (effect != null) effect.cure();
	}

	

	

	
	
	

	

	

	

	
	
	/**
	 * 시야 검색 순회 나중에 브로드캐스트 다 바꿀것 전체 순회가 아니라 시야 순회라 부하 급감.
	 * 
	 */
    

	

	public int[] getFrontLoc() {
		int[] loc = new int[2];
		int x = getX();
		int y = getY();
		int heading = getHeading();
		switch (heading) {
		case 0: { y--; } break;
		case 1: { x++; y--; } break;
		case 2: { x++; } break;
		case 3: { x++; y++; } break;
		case 4: { y++; } break;
		case 5: { x--; y++; } break;
		case 6: { x--; } break;
		case 7: { x--; y--; } break;
		default: break;
		}
		loc[0] = x;
		loc[1] = y;
		return loc;
	}

	public int targetDirection(int tx, int ty) {
		float dis_x = Math.abs(getX() - tx);
		float dis_y = Math.abs(getY() - ty);
		float dis = Math.max(dis_x, dis_y);

		if (dis == 0)
			return getHeading();

		int avg_x = (int) Math.floor((dis_x / dis) + 0.59f);
		int avg_y = (int) Math.floor((dis_y / dis) + 0.59f);

		int dir_x = 0;
		int dir_y = 0;

		if (getX() < tx) dir_x = 1;
		if (getX() > tx) dir_x = -1;

		if (getY() < ty) dir_y = 1;
		if (getY() > ty) dir_y = -1;

		if (avg_x == 0) dir_x = 0;
		if (avg_y == 0) dir_y = 0;

		if (dir_x == 1 && dir_y == -1) return 1;
		if (dir_x == 1 && dir_y == 0) return 2;
		if (dir_x == 1 && dir_y == 1) return 3;
		if (dir_x == 0 && dir_y == 1) return 4;
		if (dir_x == -1 && dir_y == 1) return 5;
		if (dir_x == -1 && dir_y == 0) return 6;
		if (dir_x == -1 && dir_y == -1) return 7;
		if (dir_x == 0 && dir_y == -1) return 0;

		return getHeading();
	}

	public boolean glanceCheck(int targetX, int targetY) {
		if (this.getX() == targetX && this.getY() == targetY) {
			return true;
		}
		L1Map map = getMap();
		if (map == null) return false;
		return map.isRayTraceable(this.getX(), this.getY(), targetX, targetY);
	}

	public boolean isAttackPosition(int x, int y, int range) {
		if (range >= 7) {
			if (getLocation().getTileDistance(new Point(x, y)) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(new Point(x, y)) > range)
				return false;
		}
		return glanceCheck(x, y);
	}

	public boolean isAttackPosition(L1Character target, int range) {
		if (range >= 7) {
			if (getLocation().getTileDistance(target.getLocation()) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(target.getLocation()) > range)
				return false;
		}
		return glanceCheck(target.getX(), target.getY()) && target.glanceCheck(getX(), getY());
	}

	public CInventory getInventory() {
		return null;
	}

	private SkillEffectContainer skillEffects(boolean create) {
		SkillEffectContainer effects = _skillEffects;

		if (effects == null && create) {
			/*
			 * create=true is reached only from the character CIO owner path
			 * (or the no-owner bootstrap/teardown fallback).
			 */
			effects = new SkillEffectContainer(this, retiredSkillHeartbeatGeneration);
			_skillEffects = effects;
		}

		return effects;
	}

	public void setSkillEffect(int skillId, int timeMillis) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillSet(this, skillId, timeMillis);
	}

	public void removeSkillEffect(int skillId) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillRemove(this, skillId, true);
	}

	public void killSkillEffectTimer(int skillId) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillRemove(this, skillId, false);
	}

	public void clearSkillEffectTimer() {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillClear(this);
	}

	public boolean hasSkillEffect(int skillId) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.contains(skillId);
	}

	protected boolean hasAnySkillEffect(int first, int second, int third) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.containsAny(first, second, third);
	}

	public boolean hasSkillEffectMatching(java.util.function.IntPredicate matchesSkillId, int exceptSkillId) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.containsMatching(matchesSkillId, exceptSkillId);
	}

	public int getSkillEffectTimeSec(int skillId) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? -1 : effects.getRemainingTimeSec(skillId);
	}

	public void applySkillModifierFromOwner(int skillId, long captured) {
		skillEffects(true).applySkillModifier(skillId, captured);
	}

	public void applyPotentialModifierFromOwner(int hp, int mp, int mr, int sp, int er, int dg) {
		skillEffects(true).applyPotentialModifier(hp, mp, mr, sp, er, dg);
	}

	public boolean removeSkillModifierFromOwner(int skillId) {
		SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.removeSkillModifier(skillId, true);
	}

	public void validateSkillModifiersFromOwner() {
		SkillEffectContainer effects = skillEffects(false);
		if (effects != null) effects.validateSkillMod(true);
	}

	/**
	 * CIO owner-only mutation entry points.
	 */
	public void skillEffectSetFromOwner(int skillId, int timeMillis) {
		final SkillEffectContainer effects = skillEffects(true);

		if (effects.contains(skillId)) {
			// Widen before multiplying to keep this duration comparison safe
			// if the stored remaining-second range grows.
			final long remainingTimeMillis =
					(long) effects.getRemainingTimeSec(skillId) * 1000L;

			if (remainingTimeMillis >= 0
					&& (remainingTimeMillis < timeMillis || timeMillis == 0)) {

				if (skillId == L1SkillId.DESPERADO) {
					effects.remove(skillId, true);
				} else if (!CSkillInfo.isManaged(skillId)) {
					effects.remove(skillId, false);
				}

				effects.put(skillId, timeMillis);

				if (timeMillis > 0) {
					CIOThreadCallback.getInstance()
							.ensureCharacterSkillHeartbeatFromOwner(this);
				}
			}
			return;
		}

		effects.put(skillId, timeMillis);

		if (timeMillis > 0) {
			CIOThreadCallback.getInstance()
					.ensureCharacterSkillHeartbeatFromOwner(this);
		}
	}

	public void skillEffectRemoveFromOwner(int skillId, boolean stop) {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null && effects.remove(skillId, stop)
				&& (skillId == L1SkillId.INVISIBILITY
						|| skillId == L1SkillId.BLIND_HIDING)
				&& this instanceof L1PcInstance pc && !pc.isInvisble()) {
			NpcEventFactory.onPlayerVisibilityRestored(pc);
		}
	}

	public void skillEffectClearFromOwner() {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null) {
			retiredSkillHeartbeatGeneration = effects.clear();
			_skillEffects = null;
		}
	}

	public int skillEffectHeartbeatTryArm() {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? 0 : effects.tryArmHeartbeat();
	}

	public boolean skillEffectHeartbeatValid(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.isHeartbeatValid(generation);
	}

	public long skillEffectHeartbeatTick(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? 0L : effects.onHeartbeat(generation);
	}

	public void skillEffectHeartbeatRelease(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null) {
			effects.releaseHeartbeat(generation);
		}
	}


	private volatile long _nextSkillTimeNs;

	public boolean isSkillDelay() {
	    final long until = _nextSkillTimeNs;
	    return until != 0L && System.nanoTime() - until < 0L;
	}

	public void setNextSkillDelay(int delayMs) {
	    if (delayMs <= 0) {
	        return;
	    }
	    _nextSkillTimeNs = System.nanoTime() + (long) delayMs * 1_000_000L;
	}
	
	/** CAS claim is the only cross-owner poison admission step. */
	public boolean claimPoison(PoisonEffect poison) {
		return poison != null && POISON.compareAndSet(this, null, poison);
	}

	/** A stale expiration must never remove a newer poison. */
	public boolean clearPoisonIfSame(PoisonEffect poison) {
		return poison != null && POISON.compareAndSet(this, poison, null);
	}

	public void setPoison(PoisonEffect poison) {
		_poison = poison;
	}

	public void curePoison() {
		final PoisonEffect poison = _poison;
		if (poison != null) poison.cure();
	}

	public PoisonEffect getPoison() {
		return _poison;
	}

	public void setPoisonEffect(int effectId) {
		SC_Poison.broadcastFast(this, getId(), effectId);
	}

	public int getZoneType() {
		if (getMap().isSafetyZone(getLocation())) {
			if (getMapId() == 5153) {
				return -1;
			} else {
				return 1;
			}
		} else if (getMap().isCombatZone(getLocation())) {
			return -1;
		} else {
			return 0;
		}
	}

	public int getExp() {
		return _exp;
	}

	public void setExp(int exp) {
		_exp = exp;
	}

	public boolean knownsObject(L1Object obj) {
		final CKnownObjectContainer known = knownContainer(false);
		return known != null && known.contains(obj);
	}
	public Set<L1Object> getKnownObjects() {
		final CKnownObjectContainer known = knownContainer(false);
		return known == null ? Collections.emptySet() : known.objectsView();
	}
	public Set<L1PcInstance> getKnownPlayers() {
		final CKnownObjectContainer known = knownContainer(false);
		return known == null ? Collections.emptySet() : known.playersView();
	}
	/** Scoped recognition scan. Always release in finally on this thread. */
	public List<L1Object> borrowKnownObjects() {
		final CKnownObjectContainer index = knownContainer(false);
		return (index == null ? CKnownObjectContainer.CLOSED : index).borrowObjects();
	}

	public void releaseKnownObjects(List<L1Object> items) {
		CKnownObjectContainer.releaseObjects(items);
	}

	public List<L1PcInstance> borrowKnownPlayers() {
		final CKnownObjectContainer index = knownContainer(false);
		return (index == null ? CKnownObjectContainer.CLOSED : index).borrowPlayers();
	}

	public void releaseKnownPlayers(List<L1PcInstance> players) {
		CKnownObjectContainer.releasePlayers(players);
	}

	public L1Object[] knownObjectsSnapshot() {
		final CKnownObjectContainer known = knownContainer(false);
		return known == null ? CKnownObjectContainer.CLOSED.objectsSnapshot() : known.objectsSnapshot();
	}
	public L1PcInstance[] knownPlayersSnapshot() {
		final CKnownObjectContainer known = knownContainer(false);
		return known == null ? CKnownObjectContainer.CLOSED.playersSnapshot() : known.playersSnapshot();
	}
	public void addKnownObject(L1Object obj) {
		if (obj == null) return;
		knownContainer(true).add(obj);
	}
	public void removeKnownObject(L1Object obj) {
		final CKnownObjectContainer known = knownContainer(false);
		if (known != null) known.remove(obj);
	}
	public void removeAllKnownObjects() {
		final CKnownObjectContainer known = knownContainer(false);
		if (known != null) known.clear();
	}

	public String getName() {
		return _name;
	}

	public void setName(String s) {
		_name = s;
	}

	public int getLevel() {
		return _level;
	}

	public synchronized void setLevel(long level) {
		_level = (int) level;
		if (this instanceof L1PcInstance pc) {
			pc.RefreshBaseMDefend();
			pc.RefreshBasePDefend();
		}
	}

	private volatile long pumpedHpMp;

	public void setPumpedSkillModifiers(int hp, int mp, int ac, int mr,
			long primaryStats, long elementalResistance, int evasion, long weapon, long combatRates, int sp, boolean notify) {
		final long previous = pumpedHpMp;
		final long updated = ((long) mp << 32) | Integer.toUnsignedLong(hp);
		if (previous != updated) {
			pumpedHpMp = updated;
			if ((int) previous != hp) clampCurrentHp();
			if ((int) (previous >>> 32) != mp) {
                if (this instanceof l1j.server.server.model.Instance.L1NpcInstance) {
                    synchronized (this) {
                        _currentMp = Math.min(_currentMp, getMaxMp());
                    }
                } else {
                    _currentMp = Math.min(_currentMp, getMaxMp());
                }
            }
		}
		setSkillAc(ac, notify);
		SetSkillMDefend(mr);
		SetSkillPrimaryStats(primaryStats, notify);
		SetSkillElemental(elementalResistance);
		if (pumpedEvasion != evasion) pumpedEvasion = evasion;
		if (pumpedWeapon != weapon) pumpedWeapon = weapon;
		if (pumpedCombatRates != combatRates) pumpedCombatRates = combatRates;
		SetSkillSp(sp);
	}

	public int getMaxHp() {
		final int delta = (int) pumpedHpMp;
		int maxhp = delta == 0 ? _maxHp : Math.clamp((long) _trueMaxHp + delta, 1, 10_000_000);

        if (this instanceof L1PcInstance)
            maxhp += CalcStat.pureHpMpBonus(GetStatCon());
		return maxhp;
	}

	public void addMaxHp(int i) {
		setMaxHp(_trueMaxHp + i);
	}

	public void setMaxHp(int hp) {
		_trueMaxHp = hp;
		_maxHp = (int) IntRange.ensure(_trueMaxHp, 1, 10000000);
		clampCurrentHp();
	}

	private void clampCurrentHp() {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            final int next = Math.min(oldHp, getMaxHp());
            if (next == oldHp || CURRENT_HP.compareAndSet(this, oldHp, next)) break;
        }
	}

	public int getMaxMp() {
		final int delta = (int) (pumpedHpMp >>> 32);
		int maxmp = delta == 0 ? _maxMp : Math.clamp((long) _trueMaxMp + delta, 0, 10_000_000);

        int wis = this instanceof L1PcInstance ? GetStatWis() : GetTotalStatWis();
        maxmp += CalcStat.pureHpMpBonus(wis);
		return maxmp;
	}

	public void setMaxMp(int mp) {
		_trueMaxMp = mp;
		_maxMp = (int) IntRange.ensure(_trueMaxMp, 0, 10000000);
		_currentMp = Math.min(_currentMp, getMaxMp());
	}

	public void addMaxMp(int i) {
		setMaxMp(_trueMaxMp + i);
	}

	public void healHp(int pt) {
		setCurrentHp(getCurrentHp() + pt);
	}

	public int getDmgup() {
		final int effect = (short) (pumpedWeapon >>> 16);
		return effect == 0 ? _dmgup : Math.clamp((long) _trueDmgup + effect, -128, 127);
	}

	public void addDmgup(int i) {
		_trueDmgup += i;
		if (_trueDmgup >= 127) {
			_dmgup = 127;
		} else if (_trueDmgup <= -128) {
			_dmgup = -128;
		} else {
			_dmgup = _trueDmgup;
		}
	}

	public int getBowDmgup() {
		final int effect = (short) (pumpedWeapon >>> 32);
		return effect == 0 ? _bowDmgup : Math.clamp((long) _trueBowDmgup + effect, -128, 127);
	}

	public void addBowDmgup(int i) {
		_trueBowDmgup += i;
		if (_trueBowDmgup >= 127) {
			_bowDmgup = 127;
		} else if (_trueBowDmgup <= -128) {
			_bowDmgup = -128;
		} else {
			_bowDmgup = _trueBowDmgup;
		}
	}

	public int getHitup() {
		final int effect = (short) pumpedWeapon;
		return effect == 0 ? _hitup : Math.clamp((long) _trueHitup + effect, -128, 127);
	}

	public void addHitup(int i) {
		_trueHitup += i;
		if (_trueHitup >= 127) {
			_hitup = 127;
		} else if (_trueHitup <= -128) {
			_hitup = -128;
		} else {
			_hitup = _trueHitup;
		}
	}

	public int get_Summonre() {
		return _summonre;
	}

	public void set_Summonre(int sum) {
		_summonre = sum;
	}

	public int getBowHitup() {
		final int effect = (short) (pumpedWeapon >>> 48);
		return effect == 0 ? _bowHitup : Math.clamp((long) _trueBowHitup + effect, -128, 127);
	}

	public void addBowHitup(int i) {
		_trueBowHitup += i;
		if (_trueBowHitup >= 127) {
			_bowHitup = 127;
		} else if (_trueBowHitup <= -128) {
			_bowHitup = -128;
		} else {
			_bowHitup = _trueBowHitup;
		}
	}

	public int getMagicLevel() {
		return getLevel() / 4;
	}

	private long _doppeltime = 0;

	public long getDoppelTime() {
		return _doppeltime;
	}

	public void setDoppelTime(long l) {
		_doppeltime = l;
	}

	public boolean isDead() {
		return _isDead;
	}

	public void setDead(boolean flag) {
		_isDead = flag;
	}

	public int getStatus() {
		return _status;
	}

	private int _monCount;

	public int get_monCount() {
		return _monCount;
	}

	public void set_monCount(int i) {
		_monCount = i;
	}

	private int _waponexp;

	public int get_waponexp() {
		return _waponexp;
	}

	public void set_waponexp(int i) {
		_waponexp = i;
	}

	public void setStatus(int i) {
		_status = i;
	}

	public String getTitle() {
		return _title;
	}

	public void setTitle(String s) {
		_title = s;
	}

	public int getLawful() {
		return _lawful;
	}

	public void setLawful(int i) {
		_lawful = i;
	}

	public synchronized void addLawful(int i) {
		if (Config.STANDBY_SERVER)
			return;

		_lawful += i;
		if (_lawful > 32767) {
			_lawful = 32767;
		} else if (_lawful < -32768) {
			_lawful = -32768;
		}
	}

	public int getHeading() {
		return _heading;
	}

	public void setHeading(int i) {
		_heading = i;
	}

	public int getMoveSpeed() {
		return _moveSpeed;
	}

	public void setMoveSpeed(int i) {
		_moveSpeed = i;
	}

	public int getBraveSpeed() {
		return _braveSpeed;
	}

	public void setBraveSpeed(int i) {
		_braveSpeed = i;
	}

	public boolean isInvisble() {
		return (hasSkillEffect(L1SkillId.INVISIBILITY) || hasSkillEffect(L1SkillId.BLIND_HIDING));
	}

	public int getKarma() {
		return _karma;
	}

	public void setKarma(int karma) {
		_karma = karma;
	}

	private Kda _kda;

	public Kda getKDA() {
		return _kda;
	}

	public void setKDA(Kda kda) {
		_kda = kda;
	}

	private long _skilldelay2;

	public long getSkilldelay2() {
		return _skilldelay2;
	}

	public void setSkilldelay2(long skilldelay2) {
		_skilldelay2 = skilldelay2;
	}

	private int _buffnoch;

	public int getBuffnoch() {
		return _buffnoch;
	}

	public void setBuffnoch(int buffnoch) {
		_buffnoch = buffnoch;
	}

	public Light getLight() {
		return light;
	}

	// CStats lane operations. No new object, array or temporary allocation.
	final byte ValidateStat(int value, int minimum) {
		return (byte) IntRange.ensure(value, minimum, 127);
	}
	public final byte GetStatStr() { return m_StatStr; }
	public final byte GetOriginalStatStr() { return m_OriginalStatStr; }
	public final void SetStatStr(int value) { m_StatStr = ValidateStat(value, m_OriginalStatStr); }
	public final byte GetAddedStatStr() { return ValidateStat(m_AddedStatStr + (byte) (m_SkillPrimaryStats >>> 0), -128); }
	final void AddAddedStatStr(int value) { m_AddedStatStr = ValidateStat(m_AddedStatStr + value, -128); }
	public final byte GetTotalStatStr() { return ValidateStat(GetStatStr() + GetAddedStatStr(), 0); }
	public final byte GetStatDex() { return m_StatDex; }
	public final byte GetOriginalStatDex() { return m_OriginalStatDex; }
	public final void SetStatDex(int value) {
		m_StatDex = ValidateStat(value, m_OriginalStatDex);
		if (this instanceof L1PcInstance pc) pc.RefreshBasePDefend();
	}
	public final byte GetAddedStatDex() { return ValidateStat(m_AddedStatDex + (byte) (m_SkillPrimaryStats >>> 8), -128); }
	final void AddAddedStatDex(int value) {
		m_AddedStatDex = ValidateStat(m_AddedStatDex + value, -128);
		if (this instanceof L1PcInstance pc) pc.RefreshBasePDefend();
	}
	public final byte GetTotalStatDex() { return ValidateStat(GetStatDex() + GetAddedStatDex(), 0); }
	public final byte GetStatInt() { return m_StatInt; }
	public final byte GetOriginalStatInt() { return m_OriginalStatInt; }
	public final void SetStatInt(int value) { m_StatInt = ValidateStat(value, m_OriginalStatInt); }
	public final byte GetAddedStatInt() { return ValidateStat(m_AddedStatInt + (byte) (m_SkillPrimaryStats >>> 16), -128); }
	final void AddAddedStatInt(int value) { m_AddedStatInt = ValidateStat(m_AddedStatInt + value, -128); }
	public final byte GetTotalStatInt() { return ValidateStat(GetStatInt() + GetAddedStatInt(), 0); }
	public final byte GetStatWis() { return m_StatWis; }
	public final byte GetOriginalStatWis() { return m_OriginalStatWis; }
	public final void SetStatWis(int value) {
		m_StatWis = ValidateStat(value, m_OriginalStatWis);
		if (this instanceof L1PcInstance pc) pc.RefreshBaseMDefend();
	}
	public final byte GetAddedStatWis() { return ValidateStat(m_AddedStatWis + (byte) (m_SkillPrimaryStats >>> 24), -128); }
	final void AddAddedStatWis(int value) {
		m_AddedStatWis = ValidateStat(m_AddedStatWis + value, -128);
		if (this instanceof L1PcInstance pc) pc.RefreshBaseMDefend();
	}
	public final byte GetTotalStatWis() { return ValidateStat(GetStatWis() + GetAddedStatWis(), 0); }
	public final byte GetStatCon() { return m_StatCon; }
	public final byte GetOriginalStatCon() { return m_OriginalStatCon; }
	public final void SetStatCon(int value) { m_StatCon = ValidateStat(value, m_OriginalStatCon); }
	public final byte GetAddedStatCon() { return ValidateStat(m_AddedStatCon + (byte) (m_SkillPrimaryStats >>> 32), -128); }
	final void AddAddedStatCon(int value) { m_AddedStatCon = ValidateStat(m_AddedStatCon + value, -128); }
	public final byte GetTotalStatCon() { return ValidateStat(GetStatCon() + GetAddedStatCon(), 0); }
	public final byte GetStatCha() { return m_StatCha; }
	public final byte GetOriginalStatCha() { return m_OriginalStatCha; }
	public final void SetStatCha(int value) { m_StatCha = ValidateStat(value, m_OriginalStatCha); }
	public final byte GetAddedStatCha() { return ValidateStat(m_AddedStatCha + (byte) (m_SkillPrimaryStats >>> 40), -128); }
	final void AddAddedStatCha(int value) { m_AddedStatCha = ValidateStat(m_AddedStatCha + value, -128); }
	public final byte GetTotalStatCha() { return ValidateStat(GetStatCha() + GetAddedStatCha(), 0); }

	// BEGIN direct primary-stat operations
	public final void SetSkillPrimaryStats(long value, boolean notify) {
		final long changed = m_SkillPrimaryStats ^ value;
		if (changed == 0) return;
		m_SkillPrimaryStats = value;
		if (this instanceof L1PcInstance pc) {
			if ((changed & 0xff000000L) != 0) pc.RefreshBaseMDefend();
			if ((changed & 0x0000ff00L) != 0) pc.RefreshBasePDefend();
		}
		if (!notify) return;
		if ((changed & 0x000000ff) != 0) NotifyStatBonusStr();
		if ((changed & 0x0000ff00) != 0) NotifyStatBonusDex();
		if ((changed & 0xff00000000L) != 0) NotifyStatBonusCon();
		if ((changed & 0x00ff0000) != 0) NotifyStatBonusInt();
		if ((changed & 0xff000000L) != 0) NotifyStatBonusWis();
	}

	public final void ResetPrimaryStats() {
		m_StatStr = m_OriginalStatStr = m_AddedStatStr = 0;
		m_StatDex = m_OriginalStatDex = m_AddedStatDex = 0;
		m_StatCon = m_OriginalStatCon = m_AddedStatCon = 0;
		m_StatInt = m_OriginalStatInt = m_AddedStatInt = 0;
		m_StatWis = m_OriginalStatWis = m_AddedStatWis = 0;
		m_StatCha = m_OriginalStatCha = m_AddedStatCha = 0;
		if (this instanceof L1PcInstance pc) {
			pc.RefreshBaseMDefend();
			pc.RefreshBasePDefend();
		}
	}

	private byte CheckStatRange(int i) {
		if (i == 0)
			return 0;
		return CheckStatRange(i, 0);
	}

	private byte CheckStatRange(int i, int base) {
		return (byte) IntRange.ensure(i, 0 + base, 127);
	}

	public final int GetOriginalStatAmount() {
		return m_OriginalStatStr + m_OriginalStatCon + m_OriginalStatDex + m_OriginalStatCha + m_OriginalStatInt + m_OriginalStatWis;
	}

	public final int GetStatAmount() {
		return m_StatStr + m_StatCon + m_StatDex + m_StatCha + m_StatInt + m_StatWis;
	}

	public final void SetSkillSp(int value) {
		if (m_nSkillSp != value) m_nSkillSp = value;
	}

	public final void AddStatSp(int i) {
		m_nStatSp += i;
	}

	public final int GetStatSp() {
		return GetStatBaseSp() + m_nStatSp + m_nSkillSp;
	}

	public final int GetStatBaseSp() {
		return GetStatMagicLevel() + GetStatMagicBonus();
	}

	public final int GetStatMagicLevel() {
		if (this instanceof L1PcInstance)
			return ((L1PcInstance) this).getMagicLevel();
		return this.getLevel() / 4;
	}

	public final int GetStatMagicBonus() {
		int i = GetTotalStatInt();
		final int classType = (this instanceof L1PcInstance)
                ? ((L1PcInstance) this).getType() : 0;
        return CalcStat.마법보너스(classType, i);
		/*
		 * if (i <= 5) return -2; else if (i <= 8) return -1; else if (i <= 11)
		 * return 0; else if (i <= 14) return 1; else if (i <= 17) return 2;
		 * else if (i <= 24) return i - 15; else if (i <= 35) return 10; else if
		 * (i <= 42) return 11; else if (i <= 49) return 12; else if (i <= 50)
		 * return 13; else return i - 25;
		 */
	}

	public final void AddStatStr(int i) {
		SetStatStr(GetStatStr() + i);
		if (this instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) this;
            if (pc.getNetConnection() != null) {
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
                SC_Weight.send(pc);
                SC_CHAR_STAT.sendCreate(pc, pc, 2, SC_CHAR_STAT.Stat_Str);
                SC_OwnCharStatus2.send(pc);
            }
        }
	}

	public final void AddOriginalStatStr(int i) {
		SetOriginalStatStr(GetOriginalStatStr() + i);
	}

	public final void SetOriginalStatStr(int i) {
		byte newBaseStr = CheckStatRange(i);
		AddStatStr(newBaseStr - m_OriginalStatStr);
		m_OriginalStatStr = newBaseStr;
	}

	public final void AddStatBonusStr(int i) {
		AddAddedStatStr(i);
		NotifyStatBonusStr();
	}

	private void NotifyStatBonusStr() {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Str);
		SC_Weight.send(pc);
	}

	public final void AddStatCon(int i) {
		SetStatCon(GetStatCon() + i);
		if (this instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) this;
            if (pc.getNetConnection() != null) {
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
            	SC_Weight.send(pc);
                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Con);
            }
        }
	}

	public final void AddOriginalStatCon(int i) {
		SetOriginalStatCon(GetOriginalStatCon() + i);
	}

	public final void SetOriginalStatCon(int i) {
		byte newBaseCon = CheckStatRange(i);
		AddStatCon(newBaseCon - m_OriginalStatCon);
		m_OriginalStatCon = newBaseCon;
	}

	public final void AddStatBonusCon(int i) {
		AddAddedStatCon(i);
		NotifyStatBonusCon();
	}

	private void NotifyStatBonusCon() {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Con);
		SC_Weight.send(pc);
	}

	public final void AddStatDex(int i) {
		SetStatDex(GetStatDex() + i);
		if (this instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) this;
            if (pc.getNetConnection() != null) {
                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
                SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Dex);
                SC_OwnCharStatus2.send(pc);
                SC_OwnCharAttrDef.send(pc);

            }
        }
	}

	public final void AddOriginalStatDex(int i) {
		SetOriginalStatDex(GetOriginalStatDex() + i);
	}

	public final void SetOriginalStatDex(int i) {
		byte newBaseDex = CheckStatRange(i);
		AddStatDex(newBaseDex - m_OriginalStatDex);
		m_OriginalStatDex = newBaseDex;
	}

	public final void AddStatBonusDex(int i) {
		AddAddedStatDex(i);
		NotifyStatBonusDex();
	}

	private void NotifyStatBonusDex() {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Dex);
		SC_OwnCharAttrDef.send(pc);
	}

	public final void AddStatCha(int i) {
		SetStatCha(GetStatCha() + i);
	}

	public final void AddOriginalStatCha(int i) {
		SetOriginalStatCha(GetOriginalStatCha() + i);
	}

	public final void SetOriginalStatCha(int i) {
		byte newBaseCha = CheckStatRange(i);
		AddStatCha(newBaseCha - m_OriginalStatCha);
		m_OriginalStatCha = newBaseCha;
	}

	public final void AddStatBonusCha(int i) {
		AddAddedStatCha(i);
	}

	public final void AddStatInt(int i) {
		SetStatInt(GetStatInt() + i);
		 if (this instanceof L1PcInstance) {
	            L1PcInstance pc = (L1PcInstance) this;
	            if (pc.getNetConnection() != null) {
	                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
	                SC_SPMR.send(pc);
	                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Int);

	            }
	        }
	}

	public final void AddOriginalStatInt(int i) {
		SetOriginalStatInt(GetOriginalStatInt() + i);
	}

	public final void SetOriginalStatInt(int i) {
		byte newBaseInt = CheckStatRange(i);
		AddStatInt(newBaseInt - m_OriginalStatInt);
		m_OriginalStatInt = newBaseInt;
	}

	public final void AddStatBonusInt(int i) {
		AddAddedStatInt(i);
		NotifyStatBonusInt();
	}

	private void NotifyStatBonusInt() {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		SC_SPMR.send(pc);
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Int);
	}

	public final void AddStatWis(int i) {
		SetStatWis(GetStatWis() + i);
		 if (this instanceof L1PcInstance) {
	            L1PcInstance pc = (L1PcInstance) this;
	            if (pc.getNetConnection() != null) {
	                pc.resetBaseMr();
	                SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Wis);
	                SC_SPMR.send(pc);
	                SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);// 현재순수스탯
	            }
	        }
	}

	public final void AddOriginalStatWis(int i) {
		SetOriginalStatWis(GetOriginalStatWis() + i);
	}

	public final void SetOriginalStatWis(int i) {
		byte newBaseWis = CheckStatRange(i);
		AddStatWis(newBaseWis - m_OriginalStatWis);
		m_OriginalStatWis = newBaseWis;
	}

	public final void AddStatBonusWis(int i) {
		AddAddedStatWis(i);
		NotifyStatBonusWis();
	}

	private void NotifyStatBonusWis() {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		pc.resetBaseMr();
		SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Wis);
		SC_SPMR.send(pc);
	}

	public final int[] GetOriginalStatDiff(int[] value) {
		int[] returnValue = new int[6];
		returnValue[0] = GetOriginalStatStr() - value[0];
		returnValue[1] = GetOriginalStatDex() - value[1];
		returnValue[2] = GetOriginalStatCon() - value[2];
		returnValue[3] = GetOriginalStatWis() - value[3];
		returnValue[4] = GetOriginalStatCha() - value[4];
		returnValue[5] = GetOriginalStatInt() - value[5];

		return returnValue;
	}

	public final int[] GetMinStat(final int classId) {
		int[] minabllity = new int[6];
		// int minStr, minDex, minCon, minWis, minCha, minInt, remainStats;
		switch (classId) {
		case L1PcInstance.CLASSID_PRINCE:
		case L1PcInstance.CLASSID_PRINCESS:
			minabllity[0] = 13;
			minabllity[1] = 9;
			minabllity[2] = 11;
			minabllity[3] = 11;
			minabllity[4] = 13;
			minabllity[5] = 9;
			break;
		case L1PcInstance.CLASSID_KNIGHT_MALE:
		case L1PcInstance.CLASSID_KNIGHT_FEMALE:
			minabllity[0] = 16;
			minabllity[1] = 12;
			minabllity[2] = 16;
			minabllity[3] = 9;
			minabllity[4] = 10;
			minabllity[5] = 8;
			break;
		case L1PcInstance.CLASSID_WIZARD_MALE:
		case L1PcInstance.CLASSID_WIZARD_FEMALE:
			minabllity[0] = 8;
			minabllity[1] = 7;
			minabllity[2] = 12;
			minabllity[3] = 14;
			minabllity[4] = 8;
			minabllity[5] = 14;
			break;
		case L1PcInstance.CLASSID_ELF_MALE:
		case L1PcInstance.CLASSID_ELF_FEMALE:
			minabllity[0] = 10;
			minabllity[1] = 12;
			minabllity[2] = 12;
			minabllity[3] = 12;
			minabllity[4] = 9;
			minabllity[5] = 12;
			break;
		case L1PcInstance.CLASSID_DARK_ELF_MALE:
		case L1PcInstance.CLASSID_DARK_ELF_FEMALE:
			minabllity[0] = 15;
			minabllity[1] = 12;
			minabllity[2] = 12;
			minabllity[3] = 10;
			minabllity[4] = 8;
			minabllity[5] = 11;
			break;
		case L1PcInstance.CLASSID_DRAGONKNIGHT_MALE:
		case L1PcInstance.CLASSID_DRAGONKNIGHT_FEMALE:
			minabllity[0] = 13;
			minabllity[1] = 11;
			minabllity[2] = 14;
			minabllity[3] = 10;
			minabllity[4] = 8;
			minabllity[5] = 10;
			break;
		case L1PcInstance.CLASSID_BLACKWIZARD_MALE:
		case L1PcInstance.CLASSID_BLACKWIZARD_FEMALE:
			minabllity[0] = 9;
			minabllity[1] = 10;
			minabllity[2] = 12;
			minabllity[3] = 14;
			minabllity[4] = 8;
			minabllity[5] = 12;
			break;
		case L1PcInstance.CLASSID_전사_MALE:
		case L1PcInstance.CLASSID_전사_FEMALE:
			minabllity[0] = 16;
			minabllity[1] = 13;
			minabllity[2] = 16;
			minabllity[3] = 7;
			minabllity[4] = 9;
			minabllity[5] = 10;
			break;
		default:

		}
		minabllity = GetOriginalStatDiff(minabllity);
		/*
		 * for(int i =0 ; i<minabllity.length; i++){ System.out.println(minabllity[i]); }
		 */
		return minabllity;
	}
	// END direct primary-stat operations

	// BEGIN direct resistance operations
	public final boolean IsResistanceActive() { return !m_ResistanceDisposed; }
    public final void ResetResistanceStats() {
        m_nOrgMDefend = m_nAddedMDefend = 0;
        m_nFireResist = m_nWaterResist = m_nWindResist = m_nEarthResist = 0;
        m_nPvpDamageReduction =  m_nPvpDamage = 0;
        m_nAinEfficiency = 0;
        m_nFouDamage = 0;
        m_nPotionRecoveryAmount = m_nPotionRecoveryRate = m_nHealDefence = 0; // 초기화 시 함께 리셋
        m_nResistAbility = m_nResistSpirit = m_nResistDragon = m_nResistFear = m_nResistAll = 0;
        m_nPierceAbility = m_nPierceSpirit = m_nPierceDragon = m_nPierceFear = m_nPierceAll = 0;
    }

    private int CheckMDefendRange(int i, final int MIN) {
        return IntRange.ensure(i, MIN, 250);
    }

    private byte CheckResistanceRange(int i) {
        return (byte)IntRange.ensure(i, -128, 127);
    }

    private int GetResistSource(eKind kind) {
        return switch (kind) {
            case ABILITY -> m_nResistAbility;
            case SPIRIT -> m_nResistSpirit;
            case DRAGON_SPELL -> m_nResistDragon;
            case FEAR -> m_nResistFear;
            case ALL -> m_nResistAll;
        };
    }

    private int GetPierceSource(eKind kind) {
        return switch (kind) {
            case ABILITY -> m_nPierceAbility;
            case SPIRIT -> m_nPierceSpirit;
            case DRAGON_SPELL -> m_nPierceDragon;
            case FEAR -> m_nPierceFear;
            case ALL -> m_nPierceAll;
        };
    }

    public final void AddSpecialResistance(eKind kind, int value) {
        if (kind == null) return;
        switch (kind) {
            case ABILITY -> m_nResistAbility += value;
            case SPIRIT -> m_nResistSpirit += value;
            case DRAGON_SPELL -> m_nResistDragon += value;
            case FEAR -> m_nResistFear += value;
            case ALL -> m_nResistAll += value;
        }
        if (!m_ResistanceDisposed && this instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public final int GetSpecialResistance(eKind kind) {
        if (kind == null) return 0;
        final int shift = (kind.value() - 1) << 3;
        final int bonus = (int) (m_SkillSpecialResistance >>> shift) & 0xff;
        // Independent signed penalties must not wrap into an unsigned 255
        // or corrupt a neighboring byte of the positive resistance bitmap.
        final int penalty = kind.value() <= 4
                ? (byte) (m_nSkillSpecialPenalty >>> shift) : 0;
        return CheckResistanceRange(GetResistSource(kind) + bonus + penalty);
    }

    public final void AddSpecialPierce(eKind kind, int value) {
        if (kind == null) return;
        switch (kind) {
            case ABILITY -> m_nPierceAbility += value;
            case SPIRIT -> m_nPierceSpirit += value;
            case DRAGON_SPELL -> m_nPierceDragon += value;
            case FEAR -> m_nPierceFear += value;
            case ALL -> m_nPierceAll += value;
        }
        if (!m_ResistanceDisposed && this instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public final int GetSpecialPierce(eKind kind) {
        if (kind == null) return 0;
        final int value = kind.value();
        final int pumped = value <= 4
                ? (m_nSkillSpecialPierce >>> ((value - 1) << 3)) & 0xff : 0;
        final int shared = switch (kind) {
            case SPIRIT -> (int) ((m_SkillSpecialResistance >>> 40) & 0xffL);
            case ALL -> (int) ((m_SkillSpecialResistance >>> 48) & 0xffL);
            default -> 0;
        };
        return CheckResistanceRange(GetPierceSource(kind) + pumped + shared);
    }

    public final int GetMDefendBySkill() {
        int effectedMr = GetMDefend();

        if (this.hasSkillEffect(L1SkillId.ERASE_MAGIC))
            effectedMr /= 2; // 25% 원본4

        return effectedMr;
    }

    public final int GetAddedMDefend() { return CheckMDefendRange(m_nAddedMDefend, -m_nOrgMDefend); }

    public final void SetSkillMDefend(int value) { if (m_nSkillMDefend != value) m_nSkillMDefend = value; }

    public final int GetMDefend() {
        if(m_ResistanceDisposed || !this.isPassive(PassiveID.RESIST_ELEMENT.toInt()))
            return CheckMDefendRange(m_nOrgMDefend + m_nAddedMDefend + m_nSkillMDefend, 0);
        return CheckMDefendRange(m_nOrgMDefend + m_nAddedMDefend + m_nSkillMDefend + 5, 0);
    }

    public final int GetOrgMDefend() 		 { return m_nOrgMDefend; 										}

    public final void AddMDefend(int i) { m_nAddedMDefend += i; }

    public final void SetOrgMDefend(int i) { m_nOrgMDefend = CheckMDefendRange(i, 0); 				}

    public final void SetSkillCombatModifiers(int pvp, long specialResistance, boolean notify) {
        SetSkillCombatModifiers(pvp, specialResistance, 0, notify);
    }

    public final void SetSkillCombatModifiers(int pvp, long specialResistance, int penalty, boolean notify) {
        // Older callers did not own the extra pierce channels. Preserve their
        // active sources; only the five-argument owner publication clears them.
        SetSkillCombatModifiers(pvp, specialResistance, penalty, m_nSkillSpecialPierce, notify);
    }

    public final void SetSkillCombatModifiers(int pvp, long specialResistance, int penalty,
            int pierce, boolean notify) {
        if (m_nSkillPvp != pvp) m_nSkillPvp = pvp;
        if (m_SkillSpecialResistance == specialResistance && m_nSkillSpecialPenalty == penalty
                && m_nSkillSpecialPierce == pierce) return;
        m_SkillSpecialResistance = specialResistance;
        m_nSkillSpecialPenalty = penalty;
        m_nSkillSpecialPierce = pierce;
        if (notify && !m_ResistanceDisposed && this instanceof L1PcInstance pc) SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
    }

    public final int GetPvpDamageReduction() { return CheckResistanceRange(m_nPvpDamageReduction + (m_nSkillPvp >> 16)); }

    public final int GetPvpDamage() { return CheckResistanceRange(m_nPvpDamage + (short) m_nSkillPvp); }

    public final void SetSkillElemental(long value) { if (m_SkillElemental != value) m_SkillElemental = value; }

    public final int GetFireResist()  { return IncreaseElementalResistance(CheckResistanceRange(m_nFireResist + (short) (m_SkillElemental >>> 48))); }

    public final int GetWaterResist() { return IncreaseElementalResistance(CheckResistanceRange(m_nWaterResist + (short) m_SkillElemental)); }

    public final int GetWindResist()  { return IncreaseElementalResistance(CheckResistanceRange(m_nWindResist + (short) (m_SkillElemental >>> 16))); }

    public final int GetEarthResist() { return IncreaseElementalResistance(CheckResistanceRange(m_nEarthResist + (short) (m_SkillElemental >>> 32))); }

    private int IncreaseElementalResistance(int source_resist) {
        return m_ResistanceDisposed || !this.isPassive(PassiveID.RESIST_ELEMENT.toInt()) ?
                source_resist : source_resist + 5;
    }

    public final void AddFireResist(int i) { m_nFireResist += i; }

    public final void AddWaterResist(int i) { m_nWaterResist += i; }

    public final void AddWindResist(int i) { m_nWindResist += i; }

    public final void AddEarthResist(int i) { m_nEarthResist += i; }

    public final void AddPvpDamageReduction(int i) { m_nPvpDamageReduction += i; }

    public final void AddPvpDamage(int i) { m_nPvpDamage += i; }

    public final void AddAllNaturalResistance(int i) {
        AddFireResist(i);
        AddWaterResist(i);
        AddWindResist(i);
        AddEarthResist(i);
    }

    public final int GetMDefendAndConsumeErase(){
        int effectedMr = GetMDefend();
        if (this.hasSkillEffect(L1SkillId.ERASE_MAGIC)){
            this.removeSkillEffect(L1SkillId.ERASE_MAGIC);
            if (!m_ResistanceDisposed && this instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance) this), SC_PacketBox.UNLIMITED_ICON1, 55, false);
            effectedMr /= 2; // 25%
        }
        return effectedMr;
    }

    public final int GetAinEfficiency() {
        return m_nAinEfficiency;
    }

    public final void AddAinEfficiency(int i) {
        m_nAinEfficiency = CheckMDefendRange(m_nAinEfficiency + i, 0);
    }

    public final void SetAinEfficiency(int i) {
        m_nAinEfficiency = CheckMDefendRange(i, 0);
    }

    public final void AddFouDmgUp(int v) {
        m_nFouDamage += v;
        if (m_nFouDamage < 0) m_nFouDamage = 0;
    }

    public final int GetFouDmgUp() {
        return m_nFouDamage;
    }

    public final void AddPotionRecAmt(int v)  { m_nPotionRecoveryAmount += v; }

    public final int  GetPotionRecAmt()        { return m_nPotionRecoveryAmount; }

    public final void AddPotionRecRate(int v) { m_nPotionRecoveryRate += v; }

    public final int  GetPotionRecRate()      { return m_nPotionRecoveryRate; }

    public final void AddHealDefence(int v)   { m_nHealDefence += v; }

    public final int  GetHealDefence()        { return m_nHealDefence; }
	// END direct resistance operations

	// Direct defense operations.
	public final int getAc() {
		return m_nPDefend;
	}

	public final void addAc(int i) {
		m_nAddedPDefend += i;
		ValidatePDefend(true);
	}

	public final void setAc(int i) {
		m_nAddedPDefend = i - m_nBasePDefend;
		ValidatePDefend(true);
	}

	public final void setSkillAc(int value, boolean notify) {
		if (m_nSkillPDefend == value) return;
		m_nSkillPDefend = value;
		ValidatePDefend(notify);
	}

	public final int GetBasePDefend() { return m_nBasePDefend; }

	protected final void SetBasePDefend(int value, boolean notify) {
		if (m_nBasePDefend == value) return;
		m_nBasePDefend = value;
		ValidatePDefend(notify);
	}

	private static int GetPDefendEvasion(int ac) {
		final int decrease = -ac - 100;
		return decrease >= 0 ? decrease / 10 + 1 : 0;
	}

	private void ValidatePDefend(boolean notify) {
		final int previous = m_nPDefend;
		final int effective = IntRange.ensure(m_nBasePDefend + m_nAddedPDefend + m_nSkillPDefend, -999, 999);
		m_nPDefend = effective;
		if (notify && GetPDefendEvasion(previous) != GetPDefendEvasion(effective) && instanceOf(L1Type.L1TYPE_PC)) {
			SC_OwnCharAttrDef.send((L1PcInstance) this);
		}
	}

	public int getDesperadolv() {
		return _Desperadolv;
	}

	public void setDesperadolv(int Desperadolv) {
		_Desperadolv = Desperadolv;
	}

	private int 락구간상승;

	public int get락구간상승() {
		return 락구간상승;
	}

	public void set락구간상승(int i) {
		락구간상승 = i;
	}

	public void sendPetCtrlMenu(L1NpcInstance npc, boolean type) {
		if (npc instanceof L1PetInstance) {
			L1PetInstance pet = (L1PetInstance) npc;
			L1Character cha = pet.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, npc, type);
			}
		} else if (npc instanceof L1SummonInstance) {
			L1SummonInstance summon = (L1SummonInstance) npc;
			L1Character cha = summon.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, npc, type);
			}
		}
	}

	private volatile ArrayList<DynamicShopItem> _sellings;
	private volatile ArrayList<DynamicShopItem> _purchasings;

	public void disposeShopInfo() {
		disposeSellings();
		disposePurchasings();
	}

	private DynamicShopItem findDShopItem(ArrayList<DynamicShopItem> list, int objid) {
		if (list == null)
			return null;

		int size = list.size();
		DynamicShopItem item = null;
		for (int i = 0; i < size; i++) {
			item = list.get(i);
			if (item.objId == objid)
				return item;
		}
		return null;
	}

	public DynamicShopItem findSellings(int objid) {
		return findDShopItem(_sellings, objid);
	}

	public void updateSellings(int objid, int count) {
		DynamicShopItem item = findSellings(objid);
		if (item == null)
			return;

		if (item.count <= count) {
			_sellings.remove(item);
			DynamicShopStorage.deleteProcess(this, item.objId);
		} else {
			item.count -= count;
			DynamicShopStorage.updateProcess(this, item);
		}
	}

	public ArrayList<DynamicShopItem> getSellings() {
		return _sellings;
	}

	public void setSellings(ArrayList<DynamicShopItem> list) {
		_sellings = list;
	}

	public void addSellings(DynamicShopItem item) {
		if (_sellings == null)
			_sellings = new ArrayList<DynamicShopItem>(7);
		_sellings.add(item);
	}

	public void disposeSellings() {
		_sellings = null;
	}

	public DynamicShopItem findPurchasings(int objid) {
		return findDShopItem(_purchasings, objid);
	}

	public void updatePurchasings(int objid, int count) {
		DynamicShopItem item = findPurchasings(objid);
		if (item == null)
			return;

		if (item.count <= count) {
			_purchasings.remove(item);
			DynamicShopStorage.deleteProcess(this, item.objId);
		} else {
			item.count -= count;
			DynamicShopStorage.updateProcess(this, item);
		}
	}

    /** Memory half of a receipt-committed private-shop operation. SQL listing
     * quantities were checked and changed on the same connection as payment.
     */
    public void applyCommittedShopQuantity(boolean purchase, DynamicShopItem expected,
            int expectedCount, int amount) {
        final ArrayList<DynamicShopItem> list = purchase ? _purchasings : _sellings;
        if (list == null || expected == null || amount <= 0 || amount > expectedCount)
            throw new IllegalStateException("Invalid committed private shop quantity");
        synchronized (list) {
            if (!list.contains(expected) || expected.count != expectedCount)
                throw new IllegalStateException("Private shop preimage changed after commit");
            if (amount == expectedCount) list.remove(expected);
            else expected.count = expectedCount - amount;
        }
    }

	public ArrayList<DynamicShopItem> getPurchasings() {
		return _purchasings;
	}

	public void setPurchasings(ArrayList<DynamicShopItem> list) {
		_purchasings = list;
	}

	public void addPurchasings(DynamicShopItem item) {
		if (_purchasings == null)
			_purchasings = new ArrayList<DynamicShopItem>(7);
		    _purchasings.add(item);
	}

	public void disposePurchasings() {
		_purchasings = null;
	}


	public void sendPackets(CServerPacket pck, boolean clear) {
	}

	public void receiveDamage(L1Character attacker, int damage) {
	}

	public int Desperadolevel = 0;

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_CHARACTER : _instanceType;
	}

	public void sendPackets(CServerPacket sbp) {

	}

	protected SpriteInformation _currentSpriteInfo;

	public int getCurrentSpriteId() {
		return _currentSpriteInfo == null ? 1120 : _currentSpriteInfo.getSpriteId();
	}

	public SpriteInformation getCurrentSprite() {
		return _currentSpriteInfo;
	}

	public void setCurrentSprite(int spriteId) {
		if (!equalsCurrentSprite(spriteId))
			_currentSpriteInfo = SpriteInformationLoader.getInstance().get(spriteId);
	}

	public boolean equalsCurrentSprite(int compareSpriteId) {
		return getCurrentSpriteId() == compareSpriteId;
	}

	public long getCurrentSpriteInterval(EActionCodes actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}

	public long getCurrentSpriteInterval(int actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}

	public void sendShape(int poly) {
		SC_ChangeShape.send(this, getId(), poly, 0);
		SC_ChangeShape.broadcastFast(this, getId(), poly, 0);
	}

	public void set_missile_critical_rate(int rate) {
		_missile_critical_rate = rate;
	}

	public int add_missile_critical_rate(int rate) {
		return _missile_critical_rate += rate;
	}

	public int get_missile_critical_rate() {
		return _missile_critical_rate + (short) pumpedCombatRates;
	}

	public void set_melee_critical_rate(int rate) {
		_melee_critical_rate = rate;
	}

	public int get_melee_critical_rate() {
		return _melee_critical_rate;
	}

	public int get_final_burn_critical_rate() {
		if(!isPassive(PassiveID.FINAL_BURN.toInt()) || getCurrentHpPercent() > 70)
			return 0;
		int level = getLevel();
		int rate = 5;
		if(level == 50) 
			rate = 6;
		
		if(level > 50)
			rate += ((level - 50) / 2);
		if(rate > 10){
			rate = 10;
		}
		return rate;
	}
	
	public int add_melee_critical_rate(int rate) {
		return _melee_critical_rate += rate;
	}

	public void set_magic_critical_rate(int rate) {
		_magic_critical_rate = rate;
	}

	public int get_magic_critical_rate() {
		return _magic_critical_rate + (short) (pumpedCombatRates >>> 16);
	}

	public int add_magic_critical_rate(int rate) {
		return _magic_critical_rate += rate;
	}

	public void send_effect(int effect_id) {
		if (effect_id > 0) {
			long sound = SC_SkillSound.pack(getId(), effect_id);
			SC_SkillSound.sendPacked(this, sound);
			SC_SkillSound.broadcastFastPacked(this, sound);
		}
	}

	public void send_action(int action_id) {
		if (action_id > 0) {
			if (this instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) this;
				SC_DoActionGFX.send(
						pc.getNetConnection(),
						pc.getId(),
						action_id);
			}
			SC_DoActionGFX.broadcast(this, action_id);
		}
	}

	public void send_pink_name(int remain_seconds){
		SC_PinkName.send(this, getId(), remain_seconds);
		SC_PinkName.broadcastFast(this, getId(), remain_seconds);
	}
	
	public void send_lawful(){
		final int lawful = getLawful();
		SC_Lawful.send(this, getId(), lawful);
		SC_Lawful.broadcastFast(this, getId(), lawful);
	}

	private int _focus_wave_level;
	private int _last_focusing_target;

	public void set_focus_wave_level(int focus_wave_level) {
		_focus_wave_level = focus_wave_level;
	}

	public int get_focus_wave_level() {
		return _focus_wave_level;
	}

	public int inc_focus_wave_level() {
		return ++_focus_wave_level;
	}

	public int dec_focus_wave_level() {
		return --_focus_wave_level;
	}

	public void set_last_focusing_target(int last_focusing_target) {
		_last_focusing_target = last_focusing_target;
	}

	public int get_last_focusing_target() {
		return _last_focusing_target;
	}

	private static final int[] _elf_skill_braves = new int[] { 
		L1SkillId.DANCING_BLADES, L1SkillId.SAND_STORM, L1SkillId.HURRICANE, L1SkillId.FOCUS_WAVE, 
	};

	public void remove_elf_second_brave() {
		for (int skillId : _elf_skill_braves) {
			if (hasSkillEffect(skillId)) {
				removeSkillEffect(skillId);

			}
		}
	}

	private boolean _isbonusAdena;

	public boolean isBonusAdenaItem() {
		return _isbonusAdena;
	}

	public void setBounusAdenaItem(boolean flag) {
		_isbonusAdena = flag;
	}
	
	public boolean isHaste() {
		return (hasSkillEffect(L1SkillId.STATUS_HASTE) || hasSkillEffect(L1SkillId.HASTE)
				|| hasSkillEffect(L1SkillId.GREATER_HASTE) || getMoveSpeed() == 1);
	}

	
	private volatile long _tomahawkState;
	private static final AtomicLongFieldUpdater<L1Character> U_TOMAHAWK_STATE = AtomicLongFieldUpdater.newUpdater(
	                L1Character.class,
	                "_tomahawkState");

	/**
	 * 새 토마호크 시작 / 재시전.
	 * 항상 이전 active token과 다른 새 token을 발급한다.
	 */
	public long beginTomahawkState() {
	    while (true) {
	        final long old = U_TOMAHAWK_STATE.get(this);

	        final long next = ((old & 1L) != 0L) ? old + 2L : old + 1L;

	        if (U_TOMAHAWK_STATE.compareAndSet(this, old, next)) {
	            return next;
	        }
	    }
	}

	/**
	 * 이 node가 아직 현재 토마호크인지 확인.
	 */
	public boolean isTomahawkState(long token) {
	    return token != 0L
	            && U_TOMAHAWK_STATE.get(this) == token;
	}


	/**
	 * 정상 종료.
	 * 새 토마호크가 이미 들어왔다면 CAS 실패하므로
	 * 절대로 새 토마호크 상태를 지우지 않는다.
	 */
	public boolean finishTomahawkState(long token) {
	    if ((token & 1L) == 0L) {
	        return false;
	    }
	    return U_TOMAHAWK_STATE.compareAndSet(
	            this,
	            token,
	            token + 1L);
	}

	/**
	 * 외부 강제 취소.
	 */
	public boolean cancelTomahawkState() {
	    while (true) {
	        final long current =
	                U_TOMAHAWK_STATE.get(this);
	        if ((current & 1L) == 0L) {
	            return false;
	        }
	        if (U_TOMAHAWK_STATE.compareAndSet(this, current, current + 1L)) {
	            return true;
	        }
	    }
	}


	/**
	 * 현재 토마호크 활성 여부.
	 */
	public boolean hasTomahawkState() {
	    return (U_TOMAHAWK_STATE.get(this) & 1L) != 0L;
	}
}
