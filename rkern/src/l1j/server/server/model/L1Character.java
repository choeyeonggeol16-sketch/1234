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
import l1j.server.server.shared.L1Type;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CPCParamDB;
import l1j.server.server.utils.IntRange;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPECIAL_RESISTANCE_NOTI;
import l1j.server.server.utils.eKind;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1PolyMorph;
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
    private static final AtomicIntegerFieldUpdater<L1Character> CURRENT_MP =
            AtomicIntegerFieldUpdater.newUpdater(L1Character.class, "_currentMp");


	// Common creature base, non-skill additions and cached effective limits.
	private int m_nBaseHP;
	private int m_nAddedHP;
	private int m_nMaxHP;
	private int m_nBaseMP;
	private int m_nAddedMP;
	private int m_nMaxMP;
	private int _lawful;
	private int _karma;
	private int m_nOrgMHit;
	private int m_nAddedMHit;
	private int m_nAddedMCriticalProb;
	// Signed 16-bit carry limit and armor reduction; signed 32-bit base magic hit.
	protected volatile long pumpedCarryArmorAndAccuracy;
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

	private volatile int lightRadius;
	private volatile boolean disposed;
	// CStats: original, permanent and equipment sources, shared by every creature.
	// Primitive byte lanes preserve the existing signed range and update order.
	byte m_OriginalStatStr, m_StatStr;
	byte m_OriginalStatDex, m_StatDex;
	byte m_OriginalStatInt, m_StatInt;
	byte m_OriginalStatWis, m_StatWis;
	byte m_OriginalStatCon, m_StatCon;
	byte m_OriginalStatCha, m_StatCha;
	// Keep exact non-skill sums. Equipment and world aura apply on different owners.
	private volatile int m_AddedStatStr, m_AddedStatDex, m_AddedStatInt;
	private volatile int m_AddedStatWis, m_AddedStatCon, m_AddedStatCha;
	private static final java.util.concurrent.atomic.AtomicIntegerFieldUpdater<L1Character>
			ADDED_STAT_STR = statAccumulator("m_AddedStatStr"),
			ADDED_STAT_DEX = statAccumulator("m_AddedStatDex"),
			ADDED_STAT_INT = statAccumulator("m_AddedStatInt"),
			ADDED_STAT_WIS = statAccumulator("m_AddedStatWis"),
			ADDED_STAT_CON = statAccumulator("m_AddedStatCon"),
			ADDED_STAT_CHA = statAccumulator("m_AddedStatCha");

	private static java.util.concurrent.atomic.AtomicIntegerFieldUpdater<L1Character> statAccumulator(String field) {
		return java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater(L1Character.class, field);
	}
	volatile long m_SkillPrimaryStats;
	int m_nStatSp;
	volatile int m_nSkillSp;
	// BEGIN shared resistance sources (CCreature)
	volatile int m_nOrgMDefend;
	volatile int m_nAddedMDefend;
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
	private volatile int m_nBasePDefend;
	private int m_nAddedPDefend;
	private volatile int m_nSkillPDefend;
	private volatile int m_nPDefend;
	
	private int _status;
	// 대미지
	private int m_nOrgMeleeDamage;
	private int m_nOrgRangedDamage;
	private int m_nOrgMeleeHit;
	// Signed 16-bit values from low to high: melee hit, melee damage, ranged damage, ranged hit.
	private volatile long m_SkillWeapon;
	// Signed 16-bit ranged critical, magic critical, magic hit, and weapon hit rate.
	private volatile long m_SkillCombatRates;
	private int m_nOrgRangedHit;


	private int m_nOrgRangedCriticalProb = 0;
	private int m_nOrgMeleeCriticalProb = 0;
	private int m_nOrgMCriticalProb = 0;
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

	// Keep the two L1 magic channels separate: configured class/gear
	// bonuses and direct combat modifiers have different formula consumers.
	public final void addBaseMagicHitUp(int i) {
		m_nAddedMHit += i;
	}

	public final int getBaseMagicHitUp() {
		return m_nAddedMHit + (int) (pumpedCarryArmorAndAccuracy >>> 32);
	}

	public final int getTotalMagicHitup() {
		final int base = this instanceof L1PcInstance pc ? pc.getBaseMagicHitByLevelClass() : 0;
		return base + getBaseMagicHitUp();
	}

	public final void addBaseMagicCritical(int i) {
		m_nAddedMCriticalProb += i;
	}

	public final int getBaseMagicCritical() {
		final int base = this instanceof L1PcInstance pc ? pc.getBaseMagicCriByLevelClass() : 0;
		return base + m_nAddedMCriticalProb;
	}

	public int getMagicHit() {
		return m_nOrgMHit + (short) (m_SkillCombatRates >>> 32);
	}

	protected final int getPumpedWeaponHitRate() {
		return (short) (m_SkillCombatRates >>> 48);
	}
	
	public void addMagicHit(int i) {
		this.m_nOrgMHit += i;
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

		disposed = true;
		disposeShopInfo();
	}

	public L1Character() {
		_level = 1;
	}


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

    public final void AbandonAttackLeaderOf(final L1NpcInstance actor) {
        if (actor == null) return;
        final int[] leaders = _ncAttackLeaderId;
        if (leaders == null) return;
        final int id = actor.getId();
        for (int position = 1; position <= 4; position++) {
            NC_LEADER_SLOT.compareAndSet(leaders, position, id, 0);
        }
    }

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
                    ? null : CWorld.getInstance().findObject(leaderId);
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



	// Intrinsic L1 stat contributions, shared by PC and NPC. Equipment,
	// class/level, target-dependent effects and random rolls remain with their owners.
	// Class/level parameters are immutable shared rows in CPCParamDB.
	// NPCs retain their template-owned values; they do not receive PC class bonuses.
	private int GetBaseParam(int field) {
		return this instanceof L1PcInstance pc
				? CPCParamDB.GetBaseParam(field, pc.getType(), getLevel()) : 0;
	}
	public final int getBaseMeleeDmgByLevelClass() { return GetBaseParam(CPCParamDB.BASE_PATTACK); }
	public final int getBaseBowDmgByLevelClass() { return GetBaseParam(CPCParamDB.BASE_RANGED_PATTACK); }
	public final int getBaseMagicDmgByLevelClass() { return GetBaseParam(CPCParamDB.BASE_MATTACK); }
	public final int getBaseMeleeHitByLevelClass() { return GetBaseParam(CPCParamDB.BASE_PHIT); }
	public final int getBaseBowHitByLevelClass() { return GetBaseParam(CPCParamDB.BASE_RANGED_PHIT); }
	public final int getBaseMagicHitByLevelClass() { return GetBaseParam(CPCParamDB.BASE_MHIT); }
	public final int getBaseMeleeCriByLevelClass() { return GetBaseParam(CPCParamDB.BASE_PCRITICAL); }
	public final int getBaseBowCriByLevelClass() { return GetBaseParam(CPCParamDB.BASE_RANGED_PCRITICAL); }
	public final int getBaseMagicCriByLevelClass() { return GetBaseParam(CPCParamDB.BASE_MCRITICAL); }
	public final int getBasePvpDrByLevelClass() { return GetBaseParam(CPCParamDB.BASE_PVP_DR); }
	public final int getBaseMLByLevelClass() { return GetBaseParam(CPCParamDB.BASE_MAGIC_LEVEL); }

	public final int GetStatPAttack(boolean ranged) {
		return ranged ? CPCParamDB.원거리대미지(GetTotalStatDex())
				: CPCParamDB.근거리대미지(GetTotalStatStr());
	}

	public final int GetStatMCriticalProb() {
		return CPCParamDB.마법치명타(GetTotalStatInt());
	}

	public final int GetStatCarryWeight() {
		return CPCParamDB.최대소지무게(GetTotalStatStr(), GetTotalStatCon());
	}

	public final int GetStatPDefend() {
		return CPCParamDB.물리방어력(GetTotalStatDex());
	}

	public final int GetStatMDefend(int classType) {
		return CPCParamDB.마법방어(classType, GetTotalStatWis());
	}

	public final int GetStatHPRegen() {
		return CPCParamDB.피회복틱(GetStatCon());
	}

	public final int GetStatMPRegen() {
		return CPCParamDB.naturalMpRegen(getLevel(), GetTotalStatWis(), GetStatWis());
	}

	private int getDefaultER() {
		return CPCParamDB.원거리회피(GetTotalStatDex());
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
	            CWorld.getInstance().getRecognizePlayer(this)) {

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
        CURRENT_HP.set(this, Math.max(0, Math.min(i, getMaxHp())));
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
        _currentMp = Math.max(0, Math.min(i, getMaxMp()));
	}

    protected final void restoreLoginMpOverflow(int amount) {
        int oldMp, next;
        do {
            oldMp = _currentMp;
            next = Math.clamp((long) oldMp + amount, 0, getMaxMp());
        } while (oldMp != next && !CURRENT_MP.compareAndSet(this, oldMp, next));
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
		ValidateParameters(VB_MDEFEND | VB_PDEFEND);
	}

	private short m_sOrgHPRegen;
	private short m_sOrgMPRegen;
	// Signed 16-bit HP regen, MP regen, weapon damage rate, Impact probability.
	protected volatile long pumpedRegenAndWeaponRates;

	public final void setPumpedRegenAndWeaponRates(long value) {
		if (pumpedRegenAndWeaponRates != value) pumpedRegenAndWeaponRates = value;
	}

	public final short getHpr() {
		return (short) Math.max(0, m_sOrgHPRegen + (short) pumpedRegenAndWeaponRates);
	}

	public final void addHpr(int i) {
		m_sOrgHPRegen += i;
	}

	public final short getMpr() {
		return (short) Math.max(0, m_sOrgMPRegen + (short) (pumpedRegenAndWeaponRates >>> 16));
	}

	public final void addMpr(int i) {
		m_sOrgMPRegen += i;
	}

	private volatile long m_SkillHPMP;

	public final int setPumpedSkillModifiers(int hp, int mp, int ac, int mr,
			long primaryStats, long elementalResistance, int evasion, long weapon, long combatRates, int sp) {
		final long previous = m_SkillHPMP;
		final long updated = ((long) mp << 32) | Integer.toUnsignedLong(hp);
		if (previous != updated) m_SkillHPMP = updated;
		if ((int) previous != hp) clampCurrentHp();
		final long changedPrimary = m_SkillPrimaryStats ^ primaryStats;
		SetSkillPrimaryStats(primaryStats, false);
		if ((int) (previous >>> 32) != mp) clampCurrentMp();
		final boolean defenseChanged = ApplySkillAc(ac);
		SetSkillMDefend(mr);
		SetSkillElemental(elementalResistance);
		if (pumpedEvasion != evasion) pumpedEvasion = evasion;
		if (m_SkillWeapon != weapon) m_SkillWeapon = weapon;
		if (m_SkillCombatRates != combatRates) m_SkillCombatRates = combatRates;
		SetSkillSp(sp);
		final int defense = defenseChanged ? NOTIFY_DEFENSE : 0;
		return ChangedPrimaryStats(changedPrimary) | defense;
	}

	public final void NotifyPumpedSkillModifiers(int changedStats, boolean specialChanged, boolean carryChanged) {
		if (!(this instanceof L1PcInstance pc)) return;
		NotifyStatBonuses(changedStats);
		if (specialChanged && !disposed) SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
		if (carryChanged && (changedStats & (NOTIFY_STR | NOTIFY_CON)) == 0) SC_Weight.send(pc);
	}

    public record StatResetVitals(int hp, int mp) {}

    /** Project the normal maximums from a detached reset while preserving active bonuses. */
    public final StatResetVitals projectStatResetVitals(int baseHp, int baseMp, int con, int wis) {
        final long skill = m_SkillHPMP;
        final int hp = Math.clamp((long) baseHp + m_nAddedHP + (int) skill, 1, 10_000_000)
                + CPCParamDB.pureHpMpBonus(con);
        final int mp = Math.clamp((long) baseMp + m_nAddedMP + (int) (skill >>> 32), 0, 10_000_000)
                + CPCParamDB.pureHpMpBonus(wis);
        return new StatResetVitals(hp, mp);
    }

	public int getMaxHp() {
		final int delta = (int) m_SkillHPMP;
		int maxhp = delta == 0 ? m_nMaxHP : Math.clamp((long) m_nBaseHP + m_nAddedHP + delta, 1, 10_000_000);

        if (this instanceof L1PcInstance)
            maxhp += CPCParamDB.pureHpMpBonus(GetStatCon());
		return maxhp;
	}

	public final int getBaseMaxHp() {
		return m_nBaseHP;
	}

	/** Includes permanent companion growth, before transient skill and stat bonuses. */
	protected final int getBaseAndAddedMaxHp() {
		return m_nMaxHP;
	}

	public final void addBaseMaxHp(short i) {
		m_nBaseHP = Math.max(1, Math.min(32767, m_nBaseHP + i));
		ValidateMaxHP();
	}

	public void addMaxHp(int i) {
		m_nAddedHP += i;
		ValidateMaxHP();
	}

	public void setMaxHp(int hp) {
		if (this instanceof L1PcInstance) {
			m_nAddedHP = hp - m_nBaseHP;
		} else {
			m_nBaseHP = hp;
			m_nAddedHP = 0;
		}
		ValidateMaxHP();
	}

	private void ValidateMaxHP() {
		m_nMaxHP = Math.clamp((long) m_nBaseHP + m_nAddedHP, 1, 10_000_000);
		clampCurrentHp();
	}

	protected final void clampCurrentHp() {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            final int next = Math.min(oldHp, getMaxHp());
            if (next == oldHp || CURRENT_HP.compareAndSet(this, oldHp, next)) break;
        }
	}

	public int getMaxMp() {
		final int delta = (int) (m_SkillHPMP >>> 32);
		int maxmp = delta == 0 ? m_nMaxMP : Math.clamp((long) m_nBaseMP + m_nAddedMP + delta, 0, 10_000_000);

        int wis = this instanceof L1PcInstance ? GetStatWis() : GetTotalStatWis();
        maxmp += CPCParamDB.pureHpMpBonus(wis);
		return maxmp;
	}

	public final int getBaseMaxMp() {
		return m_nBaseMP;
	}

	protected final int getBaseAndAddedMaxMp() {
		return m_nMaxMP;
	}

	public final void addBaseMaxMp(int i) {
		m_nBaseMP = Math.clamp(m_nBaseMP + i, 0, 32767);
		ValidateMaxMP();
	}

	public void setMaxMp(int mp) {
		if (this instanceof L1PcInstance) {
			m_nAddedMP = mp - m_nBaseMP;
		} else {
			m_nBaseMP = mp;
			m_nAddedMP = 0;
		}
		ValidateMaxMP();
	}

	public void addMaxMp(int i) {
		m_nAddedMP += i;
		ValidateMaxMP();
	}

	private void ValidateMaxMP() {
		m_nMaxMP = Math.clamp((long) m_nBaseMP + m_nAddedMP, 0, 10_000_000);
		clampCurrentMp();
	}

	private void clampCurrentMp() {
		if (this instanceof L1NpcInstance) {
			synchronized (this) {
				_currentMp = Math.min(_currentMp, getMaxMp());
			}
		} else {
			_currentMp = Math.min(_currentMp, getMaxMp());
		}
	}

	public void healHp(int pt) {
		setCurrentHp(getCurrentHp() + pt);
	}

	public int getDmgup() {
		return Math.clamp((long) m_nOrgMeleeDamage + (short) (m_SkillWeapon >>> 16), -128, 127);
	}

	public void addDmgup(int i) {
		m_nOrgMeleeDamage += i;
	}

	public int getBowDmgup() {
		return Math.clamp((long) m_nOrgRangedDamage + (short) (m_SkillWeapon >>> 32), -128, 127);
	}

	public void addBowDmgup(int i) {
		m_nOrgRangedDamage += i;
	}

	public int getHitup() {
		return Math.clamp((long) m_nOrgMeleeHit + (short) m_SkillWeapon, -128, 127);
	}

	public void addHitup(int i) {
		m_nOrgMeleeHit += i;
	}

	public int get_Summonre() {
		return _summonre;
	}

	public void set_Summonre(int sum) {
		_summonre = sum;
	}

	public int getBowHitup() {
		return Math.clamp((long) m_nOrgRangedHit + (short) (m_SkillWeapon >>> 48), -128, 127);
	}

	public void addBowHitup(int i) {
		m_nOrgRangedHit += i;
	}

	public int getMagicLevel() {
		return getLevel() / 4;
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

	private int _buffnoch;

	public int getBuffnoch() {
		return _buffnoch;
	}

	public void setBuffnoch(int buffnoch) {
		_buffnoch = buffnoch;
	}

	public final int getOwnLightSize() {
		return disposed ? 0 : lightRadius;
	}

	public final int getVisibleLightSize() {
		return disposed || isInvisble() ? 0 : lightRadius;
	}

	public final boolean refreshLight() {
		if (disposed) return false;
		final int radius = CLight.calculate(this);
		if (disposed) return false;
		CLight.send(this, radius);
		lightRadius = radius;
		return true;
	}

	// CStats lane operations. No new object, array or temporary allocation.
	// L1 validation groups: preserve existing vital clamp and defense ownership.
	// These are local masks, not the L2 protocol flag values.
	protected static final int VB_HP = 1;
	protected static final int VB_MP = 2;
	protected static final int VB_MDEFEND = 4;
	protected static final int VB_PDEFEND = 8;

	protected final void ValidateParameters(int parameter) {
		if ((parameter & VB_HP) != 0) clampCurrentHp();
		if ((parameter & VB_MP) != 0) clampCurrentMp();
		if ((parameter & (VB_MDEFEND | VB_PDEFEND)) != 0 && this instanceof L1PcInstance pc) {
			if ((parameter & VB_MDEFEND) != 0) pc.RefreshBaseMDefend();
			if ((parameter & VB_PDEFEND) != 0) pc.RefreshBasePDefend();
		}
	}

	final byte ValidateStat(int value, int minimum) {
		return (byte) IntRange.ensure(value, minimum, 127);
	}
	protected final int getNonSkillAddedStatStr() { return m_AddedStatStr; }
	protected final int getNonSkillAddedStatCon() { return m_AddedStatCon; }
	protected final int getNonSkillAddedStatInt() { return m_AddedStatInt; }

	private byte effectiveAddedStat(int nonSkill, int shift) {
		// Project each source to its existing signed byte boundary, then combine.
		return ValidateStat(ValidateStat(nonSkill, -128) + (byte) (m_SkillPrimaryStats >>> shift), -128);
	}
	public final byte GetStatStr() { return m_StatStr; }
	public final byte GetOriginalStatStr() { return m_OriginalStatStr; }
	public final void SetStatStr(int value) { m_StatStr = ValidateStat(value, m_OriginalStatStr); }
	public final byte GetAddedStatStr() { return effectiveAddedStat(m_AddedStatStr, 0); }
	final void AddAddedStatStr(int value) { ADDED_STAT_STR.getAndAdd(this, value); }
	public final byte GetTotalStatStr() { return ValidateStat(GetStatStr() + GetAddedStatStr(), 0); }
	public final byte GetStatDex() { return m_StatDex; }
	public final byte GetOriginalStatDex() { return m_OriginalStatDex; }
	public final void SetStatDex(int value) {
		m_StatDex = ValidateStat(value, m_OriginalStatDex);
		ValidateParameters(VB_PDEFEND);
	}
	public final byte GetAddedStatDex() { return effectiveAddedStat(m_AddedStatDex, 8); }
	final void AddAddedStatDex(int value) {
		ADDED_STAT_DEX.getAndAdd(this, value);
		ValidateParameters(VB_PDEFEND);
	}
	public final byte GetTotalStatDex() { return ValidateStat(GetStatDex() + GetAddedStatDex(), 0); }
	public final byte GetStatInt() { return m_StatInt; }
	public final byte GetOriginalStatInt() { return m_OriginalStatInt; }
	public final void SetStatInt(int value) { m_StatInt = ValidateStat(value, m_OriginalStatInt); }
	public final byte GetAddedStatInt() { return effectiveAddedStat(m_AddedStatInt, 16); }
	final void AddAddedStatInt(int value) { ADDED_STAT_INT.getAndAdd(this, value); }
	public final byte GetTotalStatInt() { return ValidateStat(GetStatInt() + GetAddedStatInt(), 0); }
	public final byte GetStatWis() { return m_StatWis; }
	public final byte GetOriginalStatWis() { return m_OriginalStatWis; }
	public final void SetStatWis(int value) {
		final int previous = this instanceof L1PcInstance ? GetStatWis() : GetTotalStatWis();
		m_StatWis = ValidateStat(value, m_OriginalStatWis);
		if (this instanceof L1PcInstance) {
			ValidateParameters(VB_MDEFEND);
			if (GetStatWis() < previous) ValidateParameters(VB_MP);
		} else if (GetTotalStatWis() < previous) {
			ValidateParameters(VB_MP);
		}
	}
	public final byte GetAddedStatWis() { return effectiveAddedStat(m_AddedStatWis, 24); }
	final void AddAddedStatWis(int value) {
		final int previous = GetTotalStatWis();
		ADDED_STAT_WIS.getAndAdd(this, value);
		if (this instanceof L1PcInstance) ValidateParameters(VB_MDEFEND);
		else if (GetTotalStatWis() < previous) ValidateParameters(VB_MP);
	}
	public final byte GetTotalStatWis() { return ValidateStat(GetStatWis() + GetAddedStatWis(), 0); }
	public final byte GetStatCon() { return m_StatCon; }
	public final byte GetOriginalStatCon() { return m_OriginalStatCon; }
	public final void SetStatCon(int value) {
		final byte previous = m_StatCon;
		m_StatCon = ValidateStat(value, m_OriginalStatCon);
		if (m_StatCon < previous && this instanceof L1PcInstance) ValidateParameters(VB_HP);
	}
	public final byte GetAddedStatCon() { return effectiveAddedStat(m_AddedStatCon, 32); }
	final void AddAddedStatCon(int value) { ADDED_STAT_CON.getAndAdd(this, value); }
	public final byte GetTotalStatCon() { return ValidateStat(GetStatCon() + GetAddedStatCon(), 0); }
	public final byte GetStatCha() { return m_StatCha; }
	public final byte GetOriginalStatCha() { return m_OriginalStatCha; }
	public final void SetStatCha(int value) { m_StatCha = ValidateStat(value, m_OriginalStatCha); }
	public final byte GetAddedStatCha() { return effectiveAddedStat(m_AddedStatCha, 40); }
	final void AddAddedStatCha(int value) { ADDED_STAT_CHA.getAndAdd(this, value); }
	public final byte GetTotalStatCha() { return ValidateStat(GetStatCha() + GetAddedStatCha(), 0); }

	// BEGIN direct primary-stat operations
	public final void SetSkillPrimaryStats(long value, boolean notify) {
		final long changed = m_SkillPrimaryStats ^ value;
		if (changed == 0) return;
		final int previousWis = GetTotalStatWis();
		m_SkillPrimaryStats = value;
		int parameter = 0;
		if ((changed & 0xff000000L) != 0) {
			if (this instanceof L1PcInstance) parameter |= VB_MDEFEND;
			else if (GetTotalStatWis() < previousWis) parameter |= VB_MP;
		}
		if ((changed & 0x0000ff00L) != 0) parameter |= VB_PDEFEND;
		if (parameter != 0) ValidateParameters(parameter);
		if (notify) NotifySkillPrimaryStats(changed);
	}

	private static final int NOTIFY_STR = 1, NOTIFY_DEX = 2, NOTIFY_CON = 4, NOTIFY_INT = 8,
			NOTIFY_WIS = 16, NOTIFY_DEFENSE = 32, NOTIFY_MAGIC = 64;

	private static int ChangedPrimaryStats(long changed) {
		int notifications = 0;
		if ((changed & 0x000000ffL) != 0) notifications |= NOTIFY_STR;
		if ((changed & 0x0000ff00L) != 0) notifications |= NOTIFY_DEX;
		if ((changed & 0xff00000000L) != 0) notifications |= NOTIFY_CON;
		if ((changed & 0x00ff0000L) != 0) notifications |= NOTIFY_INT;
		if ((changed & 0xff000000L) != 0) notifications |= NOTIFY_WIS;
		return notifications;
	}

	private void NotifySkillPrimaryStats(long changed) {
		NotifyStatBonuses(ChangedPrimaryStats(changed));
	}

	/** Applies one equipment source and sends each dependent display once. */
	public final void AddStatBonuses(int str, int dex, int con, int intelligence, int wis, int cha,
			boolean magicChanged) {
		int notifications = magicChanged ? NOTIFY_MAGIC : 0;
		if (str != 0) {
			AddAddedStatStr(str);
			notifications |= NOTIFY_STR;
		}
		if (dex != 0) {
			AddAddedStatDex(dex);
			notifications |= NOTIFY_DEX;
		}
		if (con != 0) {
			AddAddedStatCon(con);
			notifications |= NOTIFY_CON;
		}
		if (intelligence != 0) {
			AddAddedStatInt(intelligence);
			notifications |= NOTIFY_INT;
		}
		if (wis != 0) {
			AddAddedStatWis(wis);
			notifications |= NOTIFY_WIS;
		}
		if (cha != 0) AddAddedStatCha(cha);
		NotifyStatBonuses(notifications);
	}

	private void NotifyStatBonuses(int changed) {
		if (changed == 0 || !(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		if ((changed & (NOTIFY_DEFENSE | NOTIFY_DEX)) == NOTIFY_DEFENSE) SC_OwnCharAttrDef.send(pc);
		if ((changed & NOTIFY_STR) != 0) {
			SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Str);
			SC_Weight.send(pc);
		}
		if ((changed & NOTIFY_DEX) != 0) {
			SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
			SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Dex);
			SC_OwnCharAttrDef.send(pc);
		}
		if ((changed & NOTIFY_CON) != 0) {
			SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Con);
			if ((changed & NOTIFY_STR) == 0) SC_Weight.send(pc);
		}
		if ((changed & NOTIFY_INT) != 0) {
			SC_SPMR.send(pc);
			SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Int);
		}
		if ((changed & NOTIFY_WIS) != 0) SC_CHAR_STAT.sendCreate(pc, pc, 8, SC_CHAR_STAT.Stat_Wis);
		if ((changed & NOTIFY_INT) == 0 && (changed & (NOTIFY_WIS | NOTIFY_MAGIC)) != 0) SC_SPMR.send(pc);
	}

	// Stat redistribution resets permanent allocations only; equipment and
	// skill sources remain owned by their original apply/remove paths.
	public final void ResetPrimaryStats(int str, int dex, int con, int intel, int wis, int cha) {
		m_StatStr = m_OriginalStatStr = ValidateStat(str, 0);
		m_StatDex = m_OriginalStatDex = ValidateStat(dex, 0);
		m_StatCon = m_OriginalStatCon = ValidateStat(con, 0);
		m_StatInt = m_OriginalStatInt = ValidateStat(intel, 0);
		m_StatWis = m_OriginalStatWis = ValidateStat(wis, 0);
		m_StatCha = m_OriginalStatCha = ValidateStat(cha, 0);
		ValidateParameters(VB_HP | VB_MP | VB_MDEFEND | VB_PDEFEND);
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
        return CPCParamDB.마법보너스(classType, i);
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
		NotifyPermanentStat(SC_CHAR_STAT.Stat_Str);
	}

	public final void AddOriginalStatStr(int i) {
		SetOriginalStatStr(GetOriginalStatStr() + i);
	}

	public final void SetOriginalStatStr(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatStr;
		m_OriginalStatStr = newBase;
		AddStatStr(delta);
	}

	public final void AddStatBonusStr(int i) {
		AddAddedStatStr(i);
		NotifyStatBonusStr();
	}

	private void NotifyStatBonusStr() {
		NotifyStatBonuses(NOTIFY_STR);
	}

	public final void AddStatCon(int i) {
		SetStatCon(GetStatCon() + i);
		NotifyPermanentStat(SC_CHAR_STAT.Stat_Con);
	}

	public final void AddOriginalStatCon(int i) {
		SetOriginalStatCon(GetOriginalStatCon() + i);
	}

	public final void SetOriginalStatCon(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatCon;
		m_OriginalStatCon = newBase;
		AddStatCon(delta);
	}

	public final void AddStatBonusCon(int i) {
		AddAddedStatCon(i);
		NotifyStatBonusCon();
	}

	private void NotifyStatBonusCon() {
		NotifyStatBonuses(NOTIFY_CON);
	}

	public final void AddStatDex(int i) {
		SetStatDex(GetStatDex() + i);
		NotifyPermanentStat(SC_CHAR_STAT.Stat_Dex);
	}

	public final void AddOriginalStatDex(int i) {
		SetOriginalStatDex(GetOriginalStatDex() + i);
	}

	public final void SetOriginalStatDex(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatDex;
		m_OriginalStatDex = newBase;
		AddStatDex(delta);
	}

	public final void AddStatBonusDex(int i) {
		AddAddedStatDex(i);
		NotifyStatBonusDex();
	}

	private void NotifyStatBonusDex() {
		NotifyStatBonuses(NOTIFY_DEX);
	}

	public final void AddStatCha(int i) {
		SetStatCha(GetStatCha() + i);
	}

	public final void AddOriginalStatCha(int i) {
		SetOriginalStatCha(GetOriginalStatCha() + i);
	}

	public final void SetOriginalStatCha(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatCha;
		m_OriginalStatCha = newBase;
		AddStatCha(delta);
	}

	public final void AddStatBonusCha(int i) {
		AddAddedStatCha(i);
	}

	public final void AddStatInt(int i) {
		SetStatInt(GetStatInt() + i);
		NotifyPermanentStat(SC_CHAR_STAT.Stat_Int);
	}

	public final void AddOriginalStatInt(int i) {
		SetOriginalStatInt(GetOriginalStatInt() + i);
	}

	public final void SetOriginalStatInt(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatInt;
		m_OriginalStatInt = newBase;
		AddStatInt(delta);
	}

	public final void AddStatBonusInt(int i) {
		AddAddedStatInt(i);
		NotifyStatBonusInt();
	}

	private void NotifyStatBonusInt() {
		NotifyStatBonuses(NOTIFY_INT);
	}

	public final void AddStatWis(int i) {
		SetStatWis(GetStatWis() + i);
		NotifyPermanentStat(SC_CHAR_STAT.Stat_Wis);
	}

	public final void AddOriginalStatWis(int i) {
		SetOriginalStatWis(GetOriginalStatWis() + i);
	}

	public final void SetOriginalStatWis(int i) {
		final byte newBase = CheckStatRange(i);
		final int delta = newBase - m_OriginalStatWis;
		m_OriginalStatWis = newBase;
		AddStatWis(delta);
	}

	public final void AddStatBonusWis(int i) {
		AddAddedStatWis(i);
		NotifyStatBonusWis();
	}

	private void NotifyStatBonusWis() {
		NotifyStatBonuses(NOTIFY_WIS);
	}

	/** Publishes the committed permanent stat using its original packet mode and order. */
	public final void NotifyPermanentStat(int statType) {
		if (!(this instanceof L1PcInstance pc) || pc.getNetConnection() == null) return;
		switch (statType) {
		case SC_CHAR_STAT.Stat_Str:
			SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
			SC_Weight.send(pc);
			SC_CHAR_STAT.sendCreate(pc, pc, 2, statType);
			SC_OwnCharStatus2.send(pc);
			break;
		case SC_CHAR_STAT.Stat_Dex:
			SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
			SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
			SC_CHAR_STAT.sendCreate(pc, pc, 8, statType);
			SC_OwnCharStatus2.send(pc);
			SC_OwnCharAttrDef.send(pc);
			break;
		case SC_CHAR_STAT.Stat_Con:
			SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
			SC_Weight.send(pc);
			SC_CHAR_STAT.sendCreate(pc, pc, 8, statType);
			break;
		case SC_CHAR_STAT.Stat_Int:
			SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
			SC_SPMR.send(pc);
			SC_CHAR_STAT.sendCreate(pc, pc, 8, statType);
			break;
		case SC_CHAR_STAT.Stat_Wis:
			SC_CHAR_STAT.sendCreate(pc, pc, 8, statType);
			SC_SPMR.send(pc);
			SC_CHAR_STAT.sendCreate(pc, pc, SC_CHAR_STAT.STAT_REFRESH);
			break;
		default:
			break;
		}
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
	public final boolean IsResistanceActive() { return !disposed; }
    public final synchronized void ResetResistanceStats() {
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
        if (!disposed && this instanceof L1PcInstance pc) {
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
        if (!disposed && this instanceof L1PcInstance pc) {
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
        if(disposed || !this.isPassive(PassiveID.RESIST_ELEMENT.toInt()))
            return CheckMDefendRange(m_nOrgMDefend + m_nAddedMDefend + m_nSkillMDefend, 0);
        return CheckMDefendRange(m_nOrgMDefend + m_nAddedMDefend + m_nSkillMDefend + 5, 0);
    }

    public final int GetOrgMDefend() 		 { return m_nOrgMDefend; 										}

    public final synchronized void AddMDefend(int i) { m_nAddedMDefend += i; }

    public final synchronized void SetOrgMDefend(int i) { m_nOrgMDefend = CheckMDefendRange(i, 0); 				}

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
        if (ApplySkillCombatModifiers(pvp, specialResistance, penalty, pierce)
                && notify && !disposed && this instanceof L1PcInstance pc)
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
    }

    public final boolean ApplySkillCombatModifiers(int pvp, long specialResistance, int penalty, int pierce) {
        if (m_nSkillPvp != pvp) m_nSkillPvp = pvp;
        if (m_SkillSpecialResistance == specialResistance && m_nSkillSpecialPenalty == penalty
                && m_nSkillSpecialPierce == pierce) return false;
        m_SkillSpecialResistance = specialResistance;
        m_nSkillSpecialPenalty = penalty;
        m_nSkillSpecialPierce = pierce;
        return true;
    }

    public final int GetPvpDamageReduction() { return CheckResistanceRange(m_nPvpDamageReduction + (m_nSkillPvp >> 16)); }

    public final int GetPvpDamage() { return CheckResistanceRange(m_nPvpDamage + (short) m_nSkillPvp); }

    public final void SetSkillElemental(long value) { if (m_SkillElemental != value) m_SkillElemental = value; }

    public final int GetFireResist()  { return IncreaseElementalResistance(CheckResistanceRange(m_nFireResist + (short) (m_SkillElemental >>> 48))); }

    public final int GetWaterResist() { return IncreaseElementalResistance(CheckResistanceRange(m_nWaterResist + (short) m_SkillElemental)); }

    public final int GetWindResist()  { return IncreaseElementalResistance(CheckResistanceRange(m_nWindResist + (short) (m_SkillElemental >>> 16))); }

    public final int GetEarthResist() { return IncreaseElementalResistance(CheckResistanceRange(m_nEarthResist + (short) (m_SkillElemental >>> 32))); }

    private int IncreaseElementalResistance(int source_resist) {
        return disposed || !this.isPassive(PassiveID.RESIST_ELEMENT.toInt()) ?
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
            if (!disposed && this instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance) this), SC_PacketBox.UNLIMITED_ICON1, 55, false);
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

	private int m_nArmorDamageReduction;
	private int m_nDamageReductionIgnore;
	private int m_nDamageReduction;
	public final int getDamageReductionByArmor() {
		return m_nArmorDamageReduction + (short) (pumpedCarryArmorAndAccuracy >>> 16);
	}

	public final void addDamageReductionByArmor(int i) {
		m_nArmorDamageReduction += i;
	}

	public final int getDamageReductionIgnore() {
		return m_nDamageReductionIgnore;
	}

	public final void addDamageReductionIgnore(int i) {
		m_nDamageReductionIgnore += i;
	}

	private int m_nPvpMagicDamageReduction;
	private volatile int m_nSkillPvpMagicDamageReduction;

	public final void setPumpedPvPMagicDamageReduction(int value) {
		if (m_nSkillPvpMagicDamageReduction != value) m_nSkillPvpMagicDamageReduction = value;
	}

	public final int getPVPMagicDamageReduction() { // PVP마법대미지 감소
		return m_nPvpMagicDamageReduction + m_nSkillPvpMagicDamageReduction;
	}

	public final int addPVPMagicDamageReduction(int i) {
		return m_nPvpMagicDamageReduction += i;
	}

	public final int getDamageReduction() {
		return m_nDamageReduction;
	}

	public final void addDamageReduction(int i) {
		m_nDamageReduction += i;
	}

	// Direct defense operations.
	public final int getAc() {
		return m_nPDefend;
	}

	public final void addAc(int i) {
		final boolean evasionChanged;
		synchronized (this) {
			m_nAddedPDefend += i;
			evasionChanged = ValidatePDefend();
		}
		if (evasionChanged) NotifyPDefend();
	}

	public final void setAc(int i) {
		final boolean evasionChanged;
		synchronized (this) {
			m_nAddedPDefend = i - m_nBasePDefend;
			evasionChanged = ValidatePDefend();
		}
		if (evasionChanged) NotifyPDefend();
	}

	public final void setSkillAc(int value, boolean notify) {
		if (ApplySkillAc(value) && notify) NotifyPDefend();
	}

	private boolean ApplySkillAc(int value) {
		if (m_nSkillPDefend == value) return false;
		synchronized (this) {
			if (m_nSkillPDefend == value) return false;
			m_nSkillPDefend = value;
			return ValidatePDefend();
		}
	}

	public final int GetBasePDefend() { return m_nBasePDefend; }

	// Copy resolved NPC prototype parameters into the existing creature-owned fields.
	// The ordinary setters retain their validation and modifier semantics.
	protected final void CopyBaseParameters(int ac, int str, int con, int dex,
			int intelligence, int wis, int mr) {
		InitPDefend(ac);
		SetStatStr(str);
		SetStatCon(con);
		SetStatDex(dex);
		SetStatInt(intelligence);
		SetStatWis(wis);
		SetOrgMDefend(mr);
	}

	protected final synchronized void InitPDefend(int value) {
		m_nBasePDefend = value;
		m_nAddedPDefend = 0;
		ValidatePDefend();
	}

	protected final void SetBasePDefend(int value, boolean notify) {
		final boolean evasionChanged;
		synchronized (this) {
			if (m_nBasePDefend == value) return;
			m_nBasePDefend = value;
			evasionChanged = ValidatePDefend();
		}
		if (notify && evasionChanged) NotifyPDefend();
	}

	private static int GetPDefendEvasion(int ac) {
		final int decrease = -ac - 100;
		return decrease >= 0 ? decrease / 10 + 1 : 0;
	}

	/** Every AC source update and its effective projection hold this monitor. */
    protected final int effectiveAcFromBase(int baseAc) {
        return IntRange.ensure(baseAc + m_nAddedPDefend + m_nSkillPDefend, -999, 999);
    }

	private boolean ValidatePDefend() {
		final int previous = m_nPDefend;
		final int effective = effectiveAcFromBase(m_nBasePDefend);
		m_nPDefend = effective;
		return GetPDefendEvasion(previous) != GetPDefendEvasion(effective);
	}

	private void NotifyPDefend() {
		if (instanceOf(L1Type.L1TYPE_PC)) SC_OwnCharAttrDef.send((L1PcInstance) this);
	}

	public void sendPetCtrlMenu(L1NpcInstance npc, boolean type) {
		if (npc instanceof L1SummonInstance) {
			L1SummonInstance summon = (L1SummonInstance) npc;
			L1Character cha = summon.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, npc, type);
			}
		}
	}

    public void disposeShopInfo() { }

    public ArrayList<DynamicShopItem> getSellings() { return null; }

    public ArrayList<DynamicShopItem> getPurchasings() { return null; }


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


	public int add_missile_critical_rate(int rate) {
		return m_nOrgRangedCriticalProb += rate;
	}

	public int get_missile_critical_rate() {
		return m_nOrgRangedCriticalProb + (short) m_SkillCombatRates;
	}


	public int get_melee_critical_rate() {
		return m_nOrgMeleeCriticalProb;
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
		return m_nOrgMeleeCriticalProb += rate;
	}


	public int get_magic_critical_rate() {
		return m_nOrgMCriticalProb + (short) (m_SkillCombatRates >>> 16);
	}

	public int add_magic_critical_rate(int rate) {
		return m_nOrgMCriticalProb += rate;
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
}
