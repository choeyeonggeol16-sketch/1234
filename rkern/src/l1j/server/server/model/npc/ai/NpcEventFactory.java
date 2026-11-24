package l1j.server.server.model.npc.ai;

import java.util.ArrayList;

import gnu.trove.map.hash.TIntIntHashMap;

import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1ScarecrowInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1World;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.model.npc.ai.action.CGetItemAction;

public final class NpcEventFactory {

    public static CNPCEvent CreateSeeCreatureEvent(L1NpcInstance npc, L1Character creature) {
        return CNPCEvent.Create(creature, L1NpcInstance.HATE_EVENT_SIGHT, npc.npcEventEpoch());
    }

    public static CNPCEvent CreateAttackedEvent(L1NpcInstance npc, L1Character creature, int damage) {
        return CNPCEvent.Create(creature, L1NpcInstance.HATE_EVENT_ATTACKED_TAG
                | Math.min(damage, L1NpcInstance.HATE_EVENT_DAMAGE_VALUE_MASK), npc.npcEventEpoch());
    }

    public static CNPCEvent CreateClanAttackedEvent(L1NpcInstance npc, L1Character creature, int damage) {
        return CNPCEvent.Create(creature, L1NpcInstance.HATE_EVENT_CLAN_TAG
                | Math.min(damage, L1NpcInstance.HATE_EVENT_DAMAGE_VALUE_MASK), npc.npcEventEpoch());
    }

    public static CNPCEvent CreateHateEvent(L1NpcInstance npc, L1Character creature, int hate) {
        return CNPCEvent.Create(creature, hate, npc.npcEventEpoch());
    }

    public static CNPCEvent CreateInvisibilityEvent(L1NpcInstance npc, L1Character creature) {
        return CNPCEvent.Create(creature, L1NpcInstance.HATE_EVENT_INVISIBILITY, npc.npcEventEpoch());
    }

    /** L1 flying-monster requests reuse the existing pooled NPC inbox. */
    public static void requestAirFood(final L1NpcInstance npc) {
        if (!CGetItemAction.canSearchFromAir(npc)) return;
        postAirEvent(npc, npc, L1NpcInstance.HATE_EVENT_AIR_FOOD);
    }

    public static void requestAirLanding(final L1NpcInstance npc, final L1PcInstance pc) {
        if (npc == null || pc == null || npc.isDead() || npc.isDestroyed()
                || npc.getCurrentHp() <= 0
                || npc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_FLY
                || npc.getMapId() != pc.getMapId()) return;
        postAirEvent(npc, pc, L1NpcInstance.HATE_EVENT_AIR_LAND);
    }

    private static void postAirEvent(final L1NpcInstance npc, final L1Character source,
            final int event) {
        CNPCServer.getInstance().HandleEvent(npc,
                CNPCEvent.Create(source, event, npc.npcEventEpoch()));
        if (!npc.isAiRunning()) npc.onNpcAI();
    }

    /*
     * owner-side primitive AI events.
     * 객체 event를 만들지 않고 L1NpcInstance field에 coalesce한다.
     */
    public static final int EVENT_BLOCKED_RETARGET = 1;

    private static final int BLOCKED_RETARGET_RANGE = 4;

    private static final int[] BLOCK_DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] BLOCK_DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final byte[] BLOCK_ORDER =
            { 0, -1, 1, -2, 2, -3, 3, 4 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    /* 테스트 끝나면 false. 길막 사건 자체만 찍으므로 tick spam은 없다. */
    private static final boolean DEBUG_BLOCKED_RETARGET = false;

	private static final boolean PREFER_CHAOS = true;

	private static final int[][] CLASS_GFX = {
			{ 0, 1 },
			{ 48, 61 },
			{ 37, 138 },
			{ 734, 1186 },
			{ 2786, 2796 }
	};

	private static final int F_NONE       = 0;
	private static final int F_PRIEST     = 1;
	private static final int F_BALROG     = 2;
	private static final int F_ISLE_GUARD = 3;
	private static final int F_ISLE_MON   = 4;
	private static final int F_GATE_A     = 5;

	/*
	 * Integer boxing 제거.
	 * 존재하지 않는 key의 기본값 0 == F_NONE.
	 */
	private static final TIntIntHashMap FACTION = new TIntIntHashMap(64);

	private static final TIntIntHashMap DUEL = new TIntIntHashMap(16);

	private static final TIntIntHashMap IMMEDIATE = new TIntIntHashMap(32);

	/*
	 * NPC↔NPC relation event fast gate.
	 * 일반 몬스터는 이 map에 없으므로 이동/스폰 때 주변 NPC query 자체를 하지 않는다.
	 */
	private static final TIntIntHashMap NPC_RELATION = new TIntIntHashMap(96);


	/*
	 * NpcAiTicker worker별로 사실상 하나씩 생성되어 계속 재사용.
	 * 조회할 때마다 ArrayList 만드는 GC 제거.
	 */
	private static final ThreadLocal<ArrayList<L1Object>> OBJECT_BUFFER = ThreadLocal.withInitial(() -> new ArrayList<>(64));

    /*
     * L2Server retail:
     *   m_nClanHelpRange default = 500 world units
     *   candidate collection -> exact distance gate -> clan-help relation table
     *
     * L1 does not expose the original world-unit scale nor reference server's per-template
     * clan-help relation list.  12 tiles is the single cross-engine scale
     * adapter; the event topology itself remains retail-style.
     */
    private static final int RETAIL_CLAN_HELP_RANGE_WORLD = 500;
    private static final int CLAN_HELP_RANGE_TILES = 12;
    private static final int CLAN_HELP_RANGE_SQ =
            CLAN_HELP_RANGE_TILES * CLAN_HELP_RANGE_TILES;


	static {
		setFaction(F_PRIEST,
				45570, 45571, 45582, 45587, 45605, 45685);

		setFaction(F_BALROG,
				45391, 45450, 45482, 45569,
				45579, 45315, 45647);

		setFaction(F_ISLE_GUARD,
				7310130, 7310131, 7310132, 7310133);

		setFactionRange(F_ISLE_MON, 5000119, 5000126);
		setFactionRange(F_GATE_A, 5100000, 5100016);

		setDuel(50000004, 50000003);
		setDuel(50000006, 50000007);

		setDuel(50000008, 50000009);
		setDuel(50000009, 50000008);

		setDuel(50000010, 50000011);
		setDuel(50000011, 50000010);

		setDuel(50000012, 50000013);
		setDuel(50000013, 50000012);

		setDuel(50000014, 50000015);
		setDuel(50000015, 50000014);

		for (int id = 7200008; id <= 7200020; id++) {
			setImmediate(id, 7200003);
		}

		setImmediate(14905, 7200003);
		setImmediate(14907, 7200003);
	}


	private NpcEventFactory() {
	}


	private static void setFaction(int faction, int... ids) {
		for (int id : ids) {
			FACTION.put(id, faction);
			NPC_RELATION.put(id, 1);
		}
	}


	private static void setFactionRange(
			int faction,
			int from,
			int to) {

		for (int id = from; id <= to; id++) {
			FACTION.put(id, faction);
			NPC_RELATION.put(id, 1);
		}
	}


	private static void setDuel(int npcId, int targetId) {
		DUEL.put(npcId, targetId);
		NPC_RELATION.put(npcId, 1);
		NPC_RELATION.put(targetId, 1);
	}

	private static void setImmediate(int npcId, int targetId) {
		IMMEDIATE.put(npcId, targetId);
		NPC_RELATION.put(npcId, 1);
		NPC_RELATION.put(targetId, 1);
	}

	public static boolean needsNpcRelationEvent(L1NpcInstance npc) {
		return npc != null
				&& npc.getNpcTemplate() != null
				&& NPC_RELATION.containsKey(npc.getNpcId());
	}

	private static int factionOf(int npcId) {
		return FACTION.get(npcId);
	}


	private static int specialTargetId(int npcId) {
		int target = IMMEDIATE.get(npcId);

		if (target == 0) {
			target = DUEL.get(npcId);
		}

		return target;
	}



	/**
	 * NC식 SEE_CREATURE 진입점.
	 *
	 * 외부/world thread에서는 target/hate를 직접 변경하지 않는다.
	 * candidate만 CNPCEvent inbox에 싣고 실제 판정/반영은 AI owner에서 한다.
	 */
	public static void seeCreature(
			L1NpcInstance npc,
			L1Character creature) {

		if (npc == null || creature == null) {
			return;
		}

		npc.seeCreature(creature);
	}

	/**
	 * 실제 movement lane을 몸으로 막은 PC/Pet/Summon/Companion을 찾는다.
	 *
	 * 이건 일반 타겟 검색이 아니다. 실제 occupancy body-block 예외만 처리한다.
	 * 반환된 대상은 다음 DesirePQ arbitration에서 우선되도록 승격한다.
	 */
	public static L1Character addBlockedBodyAttackDesire(
			L1MonsterInstance npc,
			L1Character currentTarget,
			int blockedX,
			int blockedY,
			long nowMs) {

		if (npc == null
				|| currentTarget == null
				|| currentTarget != npc._target) {
			return null;
		}

		final int sx = npc.getX();
		final int sy = npc.getY();
		final int dx = blockedX - sx;
		final int dy = blockedY - sy;

		L1Character blocker =
				findBlockedPcAt(
						npc,
						currentTarget,
						blockedX,
						blockedY,
						nowMs);

		if (blocker == null
				&& Math.abs(dx) == 1
				&& Math.abs(dy) == 1) {

			blocker =
					findBlockedPcAt(
							npc,
							currentTarget,
							sx + dx,
							sy,
							nowMs);

			if (blocker == null) {
				blocker =
						findBlockedPcAt(
								npc,
								currentTarget,
								sx,
								sy + dy,
								nowMs);
			}
		}

		if (blocker == null) {
			blocker =
					findBlockedNpcBodyAt(
							npc,
							currentTarget,
							blockedX,
							blockedY);

			if (blocker == null
					&& Math.abs(dx) == 1
					&& Math.abs(dy) == 1) {

				blocker =
						findBlockedNpcBodyAt(
								npc,
								currentTarget,
								sx + dx,
								sy);

				if (blocker == null) {
					blocker =
							findBlockedNpcBodyAt(
									npc,
									currentTarget,
									sx,
									sy + dy);
				}
			}
		}

		if (blocker == null) {
			return null;
		}

        // A physical body can be unattackable (notably an invisible pet,
        // summon, or companion).  Only register it as hate if the identical
        // target-validation path accepted the ATTACK desire.
        if (!CDesirePQ.promotePhysicalBlocker(
                npc, blocker, nowMs)) {
            return null;
        }
        if (!npc._hateList.containsKey(blocker)) {
            npc._hateList.add(blocker, 0);
        }
        return blocker;
	}

	/**
	 * 길이 막혔을 때 기존 DesirePQ에 없던 '뒤의 PC'도 인식한다.
	 *
	 * world scan은 하지 않는다. 이미 이 NPC가 알고 있는 object만 한 번 순회한다.
	 * PC만 대상이며 GM/GM-invis/ghost/shop/dead/invisible-not-detectable은 제외한다.
	 */
	public static L1Character addBlockedPlayerAttackDesire(
			L1MonsterInstance npc,
			L1Character currentTarget,
			long nowMs) {

		if (npc == null || currentTarget == null) {
			return null;
		}

		L1PcInstance best = null;
		int bestDist = Integer.MAX_VALUE;
		int bestId = Integer.MAX_VALUE;

		for (L1Object obj : npc.getKnownObjects()) {
			if (!(obj instanceof L1PcInstance)) {
				continue;
			}

			final L1PcInstance pc =
					(L1PcInstance) obj;

			if (pc == currentTarget
					|| !isBlockedPlayerCandidate(
							npc,
							pc)) {
				continue;
			}

			final int dist =
					npc.getLocation()
							.getTileLineDistance(
									pc.getLocation());

			final int id =
					pc.getId();

			if (dist > bestDist
					|| (dist == bestDist
							&& id >= bestId)) {
				continue;
			}

			best = pc;
			bestDist = dist;
			bestId = id;
		}

		if (best == null) {
			return null;
		}

		if (!npc._hateList.containsKey(best)) {
			npc._hateList.add(best, 0);
		}

		CDesirePQ.ensureAttackCandidate(
				npc,
				best,
				nowMs);

		return best;
	}

	private static boolean isBlockedPlayerCandidate(
			L1MonsterInstance npc,
			L1PcInstance pc) {

		if (npc == null
				|| pc == null
				|| pc.getMapId() != npc.getMapId()
				|| pc.isDead()
				|| pc.getCurrentHp() <= 0
			//	|| pc.isGm()
			//	|| pc.isGmInvis()
				|| pc.isGhost()
				|| pc.isPrivateShop()) {

			return false;
		}

		if (pc.isInvisble()
				&& (npc.getNpcTemplate() == null
						|| !npc.getNpcTemplate()
								.is_agrocoi())) {

			return false;
		}

		return true;
	}

	private static L1Character findBlockedPcAt(
			L1MonsterInstance npc,
			L1Character currentTarget,
			int x,
			int y,
			long nowMs) {

		final int occupierId =
				COLLISION.getPcOccupier(
						(short) npc.getMapId(),
						x,
						y);

		if (occupierId == 0
				|| occupierId == npc.getId()
				|| occupierId == currentTarget.getId()) {

			return null;
		}

		final L1Object obj =
				L1World.getInstance()
						.findObject(occupierId);

		if (!(obj instanceof L1PcInstance)) {
			return null;
		}

		final L1PcInstance pc =
				(L1PcInstance) obj;

		return isBlockedPlayerCandidate(
				npc,
				pc)
				? pc
				: null;
	}

	private static L1Character findBlockedNpcBodyAt(
			L1MonsterInstance npc,
			L1Character currentTarget,
			int x,
			int y) {

		final int occupierId =
				COLLISION.getNpcOccupier(
						(short) npc.getMapId(),
						x,
						y);

		if (occupierId == 0
				|| occupierId == npc.getId()
				|| occupierId == currentTarget.getId()) {

			return null;
		}

		final L1Object obj =
				L1World.getInstance()
						.findObject(occupierId);

		if (!(obj instanceof L1PetInstance)
				&& !(obj instanceof L1SummonInstance)
				&& !(obj instanceof L1CompanionInstance)) {

			return null;
		}

		final L1Character blocker =
				(L1Character) obj;

		if (blocker.isDead()
				|| blocker.getCurrentHp() <= 0
				|| blocker.getMapId()
						!= npc.getMapId()) {

			return null;
		}

		return blocker;
	}

	/**
	 * MOVE_TO_TARGET이 실제 creature body에 막혔다는 사건만 기록한다.
	 *
	 * 여기서는 target/hate를 변경하지 않는다.
	 * producer는 AI owner의 MOVE action이므로 별도 queue/object가 필요 없다.
	 */
	public static boolean queueBlockedRetarget(
			L1NpcInstance npc,
			L1Character currentTarget,
			int blockType) {

		if (!(npc instanceof L1MonsterInstance)
				|| currentTarget == null
				|| currentTarget != npc._target) {
			return false;
		}

		if (blockType != WorldCollisionHash.MOVE_PC
				&& blockType != WorldCollisionHash.MOVE_NPC) {
			return false;
		}

		npc._aiBlockedTargetId = currentTarget.getId();
		npc._aiBlockedType = blockType;
		npc._aiEventFlags |= EVENT_BLOCKED_RETARGET;

		if (DEBUG_BLOCKED_RETARGET) {
			System.out.println(
					"[NPC-BLOCK-EVENT] QUEUE"
					+ " npc=" + npc.getId()
					+ " current=" + currentTarget.getId()
					+ " block=" + blockName(blockType));
		}

		return true;
	}

	/**
	 * CNpcAI owner-side consume.
	 *
	 * old target이 아직 그대로일 때만 blocked-retarget을 적용한다.
	 * stale event가 새 hate target을 덮어쓰지 않게 target id를 검증한다.
	 */
	public static boolean consumeBlockedRetarget(
			L1NpcInstance npc,
			int blockedTargetId,
			int blockType,
			long nowMs) {

		if (!(npc instanceof L1MonsterInstance)
				|| blockedTargetId == 0) {
			return false;
		}

		if (blockType != WorldCollisionHash.MOVE_PC
				&& blockType != WorldCollisionHash.MOVE_NPC) {
			return false;
		}

		final L1Character current = npc._target;

		if (current == null
				|| current.getId() != blockedTargetId
				|| current.isDead()
				|| current.getCurrentHp() <= 0
				|| current.getMapId() != npc.getMapId()) {
			return false;
		}

		final L1MonsterInstance monster =
				(L1MonsterInstance) npc;

		/*
		 * 1순위:
		 * 실제로 지금 진행을 막고 있는 body를 collision occupancy에서 직접 찾는다.
		 * 길막 사건인데 주변 knownObjects를 다시 뒤지는 게 아니라,
		 * 사건의 원인 객체를 먼저 AI 판단 대상으로 올린다.
		 */
		L1Character candidate =
				findActualBlockerCandidate(
						monster,
						current,
						blockType,
						nowMs);

		/*
		 * 실제 blocker가 공격 불가 대상이면 기존 retained/known 후보에서
		 * 가까운 대체 타겟을 한 번 더 찾는다.
		 */
		if (candidate == null) {
			candidate =
					findBlockedRetargetCandidate(
							monster,
							current,
							BLOCKED_RETARGET_RANGE,
							nowMs);
		}

		if (candidate == null) {
			if (DEBUG_BLOCKED_RETARGET) {
				System.out.println(
						"[NPC-BLOCK-EVENT] MISS"
						+ " npc=" + npc.getId()
						+ " current=" + current.getId()
						+ " block=" + blockName(blockType));
			}
			return false;
		}

		if (!npc._hateList.containsKey(candidate)) {
			npc._hateList.add(candidate, 0);
		}

		npc._target = candidate;

		if (DEBUG_BLOCKED_RETARGET) {
			System.out.println(
					"[NPC-BLOCK-EVENT] SWITCH"
					+ " npc=" + npc.getId()
					+ " old=" + current.getId()
					+ " new=" + candidate.getId()
					+ " type=" + candidate.getClass().getSimpleName()
					+ " block=" + blockName(blockType));
		}

		return true;
	}

	/**
	 * 예전에 CMoveToTargetAction 안에 있던 blocked retarget 후보 판정을
	 * AI 판단 영역으로 이동한 것.
	 *
	 * 현재 target은 제외하고 PC/Pet/Summon/Companion 중
	 * range 안의 가장 가까운 visible 후보를 고른다.
	 *
	 * world scan 없음. knownObjects 1회 순회.
	 */
	/**
	 * 실제 길막 body를 우선 찾는다.
	 *
	 * MOVE_PC  -> TileOccupancy의 PC occupier id
	 * MOVE_NPC -> TileOccupancyNpc의 NPC occupier id
	 *
	 * 현재 target 방향을 우선으로 8방향 bounded probe.
	 * 객체/컬렉션 생성 없음.
	 */
	private static L1Character findActualBlockerCandidate(
			L1MonsterInstance npc,
			L1Character current,
			int blockType,
			long nowMs) {

		if (npc == null
				|| current == null) {
			return null;
		}

		final int base =
				npc.targetDirection(
						current.getX(),
						current.getY());

		if (base < 0 || base > 7) {
			return null;
		}

		final short mapId =
				(short) npc.getMapId();

		final int sx = npc.getX();
		final int sy = npc.getY();
		final int selfId = npc.getId();

		for (int i = 0; i < BLOCK_ORDER.length; i++) {
			final int heading =
					(base + BLOCK_ORDER[i]) & 7;

			final int x =
					sx + BLOCK_DX[heading];

			final int y =
					sy + BLOCK_DY[heading];

			final int occupierId;

			if (blockType == WorldCollisionHash.MOVE_PC) {
				occupierId =
						COLLISION.getPcOccupier(
								mapId, x, y);
			} else {
				occupierId =
						COLLISION.getNpcOccupier(
								mapId, x, y);
			}

			if (occupierId == 0
					|| occupierId == selfId
					|| occupierId == current.getId()) {
				continue;
			}

			final L1Object obj =
					L1World.getInstance()
							.findObject(occupierId);

			if (!(obj instanceof L1Character)) {
				continue;
			}

			final L1Character blocker =
					(L1Character) obj;

			final boolean eligible =
					isBlockedRetargetCandidate(
							npc,
							blocker,
							nowMs);

			if (DEBUG_BLOCKED_RETARGET) {
				System.out.println(
						"[NPC-BLOCK-EVENT] BLOCKER"
						+ " npc=" + npc.getId()
						+ " current=" + current.getId()
						+ " blocker=" + blocker.getId()
						+ " type="
						+ blocker.getClass().getSimpleName()
						+ " eligible=" + eligible
						+ " block=" + blockName(blockType));
			}

			if (eligible) {
				return blocker;
			}
		}

		return null;
	}

	/**
	 * blocker 자체가 공격 불가였을 때의 fallback.
	 *
	 * Monster는 이미 SEE_CREATURE owner drain에서 공격 가능한 후보를
	 * _hateList에 0 hate로 retained 하고 있다.
	 *
	 * 따라서 BLOCKED_RETARGET에서 knownObjects를 다시 뒤지지 않는다.
	 * 현재 target만 제외하고 retained target 후보 중 가장 가까운 visible 대상을 고른다.
	 */
	private static L1Character findBlockedRetargetCandidate(
	        L1MonsterInstance npc,
	        L1Character current,
	        int range,
	        long nowMs) {

	    if (npc._hateList.isEmpty()) {
	        return null;
	    }

	    final ArrayList<L1Character> retained =
	            npc._hateList.toTargetArrayList();

	    L1Character best = null;
	    int bestDist = Integer.MAX_VALUE;
	    int bestId = Integer.MAX_VALUE;

	    for (int i = 0, size = retained.size(); i < size; i++) {
	        final L1Character cha = retained.get(i);

	        if (cha == null
	                || cha == current
	                || cha == npc
	                || !isBlockedRetargetCandidate(
	                        npc,
	                        cha,
	                        nowMs)) {
	            continue;
	        }

	        final int dist =
	                npc.getLocation()
	                        .getTileLineDistance(
	                                cha.getLocation());

	        if (dist > range) {
	            continue;
	        }

	        final int id = cha.getId();

	        if (dist > bestDist
	                || (dist == bestDist
	                        && id >= bestId)) {
	            continue;
	        }

	        best = cha;
	        bestDist = dist;
	        bestId = id;
	    }

	    if (DEBUG_BLOCKED_RETARGET) {
	        System.out.println(
	                "[NPC-BLOCK-EVENT] RETAINED"
	                + " npc=" + npc.getId()
	                + " current=" + current.getId()
	                + " size=" + retained.size()
	                + " best=" + (best != null ? best.getId() : 0)
	                + " dist=" + (best != null ? bestDist : -1));
	    }

	    return best;
	}

	/**
	 * BLOCKED_RETARGET 전용 공격 가능 판정.
	 *
	 * PC:
	 * - 이미 hate 후보면 허용
	 * - 아니면 기존 player sight 규칙 통과 시 허용
	 *
	 * Pet/Summon/Companion:
	 * - 실제 body blocker면 살아있는 개체를 허용
	 *
	 * 일반 NPC:
	 * - faction/duel/immediate 등 기존 NPC 적대 규칙을 그대로 사용
	 */
	private static boolean isBlockedRetargetCandidate(
			L1MonsterInstance npc,
			L1Character target,
			long nowMs) {

		if (npc == null
				|| target == null
				|| target == npc
				|| target.getMapId() != npc.getMapId()
				|| target.isDead()
				|| target.getCurrentHp() <= 0) {
			return false;
		}

        // Retained hate is not a bypass around visibility.  An invisible
        // target may remain in hate after it disappears, but cannot become
        // a new blocked-retarget choice without the monster's COI detection.
        if (target.isInvisble()
                && (npc.getNpcTemplate() == null
                    || !npc.getNpcTemplate().is_agrocoi())) {
            return false;
        }

		if (target instanceof L1PcInstance) {
			final L1PcInstance pc =
					(L1PcInstance) target;

//			if (pc.isGm()
//					|| pc.isGhost()
//					|| pc.isGmInvis()
//					|| pc.isPrivateShop()) {
//				return false;
//			}

			if (npc._hateList.containsKey(pc)) {
				return true;
			}

			return isPlayerTargetCandidate(
					npc,
					pc,
					nowMs);
		}

		if (target instanceof L1PetInstance
				|| target instanceof L1SummonInstance
				|| target instanceof L1CompanionInstance) {
			return true;
		}

		if (target instanceof L1NpcInstance) {
			return isNpcTargetCandidate(
					npc,
					(L1NpcInstance) target);
		}

		return false;
	}

	private static String blockName(final int blockType) {
		if (blockType == WorldCollisionHash.MOVE_PC) {
			return "PC";
		}
		if (blockType == WorldCollisionHash.MOVE_NPC) {
			return "NPC";
		}
		if (blockType == WorldCollisionHash.MOVE_RESERVED) {
			return "RESERVED";
		}
		if (blockType == WorldCollisionHash.MOVE_CLEAR) {
			return "CLEAR";
		}
		return Integer.toString(blockType);
	}


	/**
	 * searchTarget polling을 제거한 NPC 종류.
	 * 다음 subclass를 event-only로 옮길 때 여기만 추가하면 CNpcAI도 같이 따라간다.
	 */
	public static boolean usesEventTargeting(L1NpcInstance npc) {
		return npc instanceof L1MonsterInstance
				|| npc instanceof L1GuardInstance
				|| npc instanceof L1CastleGuardInstance
				|| npc instanceof L1GuardianInstance;
	}

	/**
	 * SEE_CREATURE owner-side 공통 후보 판정.
	 */
	public static boolean isSightTargetCandidate(
			L1NpcInstance npc,
			L1Character target,
			long nowMs) {

		if (npc instanceof L1MonsterInstance) {
			return isSightTargetCandidate(
					(L1MonsterInstance) npc,
					target,
					nowMs);
		}

		if (npc instanceof L1GuardInstance) {
			return ((L1GuardInstance) npc)
					.isSightTargetCandidate(target);
		}

		if (npc instanceof L1CastleGuardInstance) {
			return ((L1CastleGuardInstance) npc)
					.isSightTargetCandidate(target);
		}

		if (npc instanceof L1GuardianInstance) {
			return ((L1GuardianInstance) npc)
					.isSightTargetCandidate(target);
		}

		return false;
	}


    /** Pooled awareness event; admission and hate/PQ writes are owner-only. */
    public static void onGroupLink(final L1MonsterInstance helper,
            final L1Character attacker) {
        if (helper == null || attacker == null || helper == attacker
                || helper.isDead() || helper.isDestroyed()
                || helper.getCurrentHp() <= 0
                || helper.getMapId() != attacker.getMapId()) return;
        CNPCServer.getInstance().HandleEvent(helper, CNPCEvent.Create(attacker,
                L1NpcInstance.HATE_EVENT_GROUP_LINK, helper.npcEventEpoch()));
        helper.onNpcAI();
    }

    /** L1 mana-damage awareness, distinct from damage-scaled CLAN_ATTACKED. */
    public static void searchLink(final L1NpcInstance source,
            final L1PcInstance attacker, final int family) {
        if (source == null || attacker == null || source.isDestroyed()) return;
        if (family > 0) {
            final ArrayList<L1Object> nearby = OBJECT_BUFFER.get();
            try {
                L1World.getInstance().getClusterGrid().queryNearby(
                        source, CLAN_HELP_RANGE_TILES, nearby);
                for (int i = 0, n = nearby.size(); i < n; i++) {
                    final L1Object obj = nearby.get(i);
                    if (!(obj instanceof L1NpcInstance)) continue;
                    final L1NpcInstance helper = (L1NpcInstance) obj;
                    if (helper == source || helper.isDestroyed() || helper.isDead()
                            || helper.getCurrentHp() <= 0
                            || helper.getMapId() != source.getMapId()
                            || source.getLocation().getTileLineDistance(helper.getLocation())
                                    > CLAN_HELP_RANGE_TILES) continue;
                    final L1Npc t = helper.getNpcTemplate();
                    if (t != null && t.get_family() == family && t.get_agrofamily() > 0) {
                        helper.setLink(attacker);
                    }
                }
            } finally {
                nearby.clear();
            }
        }
        onPartyAttacked(source, attacker);
    }

    /**
     * Retail L2Server clan-help dispatch adapted to L1 template data.
     *
     * Native path:
     *   attacked NPC
     *     -> local spatial candidates
     *     -> clan_help_range
     *     -> precomputed clan-help relation table
     *     -> per-helper CLAN_ATTACKED event
     *
     * Important: this is NOT a broadcast over attacker's known objects.
     */
    public static void onClanAttacked(
            final L1MonsterInstance source,
            final L1Character attacker,
            final int damage) {

        if (source == null
                || attacker == null
                || damage <= 0
                || source.isDead()
                || source.getCurrentHp() <= 0) {
            return;
        }

        final L1Npc sourceTemplate =
                source.getNpcTemplate();

        if (sourceTemplate == null
                || sourceTemplate.get_family() <= 0) {
            return;
        }

        final ArrayList<L1Object> nearby =
                OBJECT_BUFFER.get();

        L1World.getInstance()
                .getClusterGrid()
                .queryNearby(
                        source,
                        CLAN_HELP_RANGE_TILES,
                        nearby);

        final int sx = source.getX();
        final int sy = source.getY();
        final int mapId = source.getMapId();

        try {
            for (int i = 0, size = nearby.size();
                    i < size;
                    i++) {

                final L1Object obj =
                        nearby.get(i);

                if (!(obj instanceof L1MonsterInstance)) {
                    continue;
                }

                final L1MonsterInstance helper =
                        (L1MonsterInstance) obj;

                if (helper == source
                        || helper.isDead()
                        || helper.getCurrentHp() <= 0
                        || helper.getMapId() != mapId) {
                    continue;
                }

                final int dx =
                        helper.getX() - sx;
                final int dy =
                        helper.getY() - sy;

                if ((long) dx * dx + (long) dy * dy
                        > CLAN_HELP_RANGE_SQ) {
                    continue;
                }

                if (!isClanHelpRelation(
                        sourceTemplate,
                        helper.getNpcTemplate())) {
                    continue;
                }

                /*
                 * Per-helper event.  CNpcAI owner drains this and applies
                 * warrior::CLAN_ATTACKED desire math.
                 */
                helper.setClanHate(
                        attacker,
                        damage);

                helper.onNpcAI();
            }

        } finally {
            nearby.clear();
        }
    }

    /**
     * L1 mobGroup is kept as the closest equivalent of reference server's explicit
     * party/private list, but it is deliberately NOT converted into
     * CLAN_ATTACKED.
     */
    public static void onPartyAttacked(
            final L1NpcInstance source,
            final L1Character attacker) {

        if (source == null || attacker == null) {
            return;
        }

        final L1MobGroupInfo group = source.getMobGroupInfo();
        final int groupId = source.getMobGroupId();

        if (group == null && groupId == 0) {
            return;
        }

        final ArrayList<L1Object> nearby =
                OBJECT_BUFFER.get();

        L1World.getInstance()
                .getClusterGrid()
                .queryNearby(
                        source,
                        -1,
                        nearby);

        try {
            for (int i = 0, size = nearby.size();
                    i < size;
                    i++) {

                final L1Object obj =
                        nearby.get(i);

                if (!(obj instanceof L1MonsterInstance)) {
                    continue;
                }

                final L1MonsterInstance helper =
                        (L1MonsterInstance) obj;

                if (helper == source
                        || helper.isDead()
                        || helper.getCurrentHp() <= 0
                        || helper.getMapId()
                                != source.getMapId()
                        || helper.isDestroyed()
                        || (group != null
                                ? helper.getMobGroupInfo() != group
                                : helper.getMobGroupId() != groupId)) {
                    continue;
                }

                /*
                 * Preserve existing L1 group-link contract as a separate
                 * zero-hate awareness event.  No CLAN_ATTACKED multiplier.
                 */
                helper.setLink(attacker);
            }

        } finally {
            nearby.clear();
        }
    }

    /**
     * reference server has a precomputed per-template clan-help relation matrix.
     * L1 only has family/agrofamily, so use the conservative subset that is
     * actually representable: helper opted into agro-family behavior AND
     * helper/source share the same non-zero family.
     *
     * This intentionally removes the legacy "agrofamily > 1 => help anyone"
     * broadcast behavior.
     */
    private static boolean isClanHelpRelation(
            final L1Npc source,
            final L1Npc helper) {

        if (source == null || helper == null) {
            return false;
        }

        final int family =
                source.get_family();

        return family > 0
                && helper.get_family() == family
                && helper.get_agrofamily() > 0;
    }

    public static int retailClanHelpRangeWorld() {
        return RETAIL_CLAN_HELP_RANGE_WORLD;
    }


	public static void onPlayerInvisibility(final L1PcInstance pc) {
		if (pc == null || !pc.isInvisble()) {
			return;
		}

		for (L1Object obj : pc.getKnownObjects()) {
			if (!(obj instanceof L1MonsterInstance)) {
				continue;
			}

			final L1MonsterInstance monster = (L1MonsterInstance) obj;
			if (monster._target != pc
					|| monster.isDead()
					|| monster.isDestroyed()
					|| monster.getMapId() != pc.getMapId()) {
				continue;
			}

			CNPCServer.getInstance().HandleEvent(monster,
					CreateInvisibilityEvent(monster, pc));
		}
	}

	/**
	 * Re-issue the one-shot SEE event only when invisibility really ends.
	 * Existing AOI known-object membership does not call onPerceive() twice.
	 * No periodic scan or off-owner NPC state mutation is introduced.
	 */
	public static void onPlayerVisibilityRestored(final L1PcInstance pc) {
		if (pc == null || pc.isInvisble() || pc.isGmInvis()
				|| pc.isGhost() || pc.isDead()
				|| pc.isPrivateShop() || pc.getCurrentHp() <= 0) {
			return;
		}

		for (L1Object obj : pc.getKnownObjects()) {
			if (!(obj instanceof L1MonsterInstance)) {
				continue;
			}
			final L1MonsterInstance monster = (L1MonsterInstance) obj;
			if (monster.isDestroyed() || monster.isDead()
					|| monster.getCurrentHp() <= 0
					|| monster.getMapId() != pc.getMapId()
					|| !monster.getLocation().isInScreen(pc.getLocation())) {
				continue;
			}
			onSeePlayer(monster, pc);
		}
	}

	public static L1PcInstance findInvisibilityRetarget(
			final L1MonsterInstance monster,
			final L1Character oldTarget) {

		if (monster == null || oldTarget == null) {
			return null;
		}

		L1PcInstance best = null;
		int bestDist = Integer.MAX_VALUE;
		int bestId = Integer.MAX_VALUE;

		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(monster)) {
			if (pc == null || pc == oldTarget) {
				continue;
			}

			if (pc.getMapId() != monster.getMapId()
					|| pc.isDead()
					|| pc.getCurrentHp() <= 0
					|| pc.isInvisble()
					|| pc.isGhost()
					|| pc.isPrivateShop()
					|| !monster.getLocation().isInScreen(pc.getLocation())) {
				continue;
			}

			final int dist = monster.getLocation()
					.getTileLineDistance(pc.getLocation());
			final int id = pc.getId();
			if (dist < bestDist || (dist == bestDist && id < bestId)) {
				best = pc;
				bestDist = dist;
				bestId = id;
			}
		}

		return best;
	}

	/**
	 * Player -> Monster SeeCreature event.
	 * 이미 AOI가 건네준 단일 PC만 판정한다. 주변 player scan 없음.
	 */
	public static void onSeePlayer(
			L1MonsterInstance npc,
			L1PcInstance pc) {

		if (npc == null || pc == null) {
			return;
		}

		/*
		 * Most ordinary monsters enter warrior_aggressive::SEE_CREATURE only
		 * when their DB aggro flag is set. Other legacy L1 aggro conditions
		 * (shape, sprite, chaotic player or a special map) remain exceptions.
		 * Evaluate those rules before dropping the one-shot AOI event.
		 */
		final L1Npc template = npc.getNpcTemplate();
		if (template == null) {
			return;
		}

		// Ordinary passive monsters still do not start pre-emptive attacks.
		// Their exceptional L1 rules (shape/class/chaotic/map) must not be
		// suppressed before the owner-side sight candidate check, however.
		if (!template.is_agro()
				&& !isPlayerTargetCandidate(npc, pc,
						System.currentTimeMillis())) {
			return;
		}

		seeCreature(npc, pc);
	}

	/**
	 * NPC spawn/move event.
	 *
	 * 일반 NPC/몬스터는 NPC_RELATION fast gate에서 즉시 끝난다.
	 * 실제 주변 query는 faction/duel/immediate 관계에 참가하는 소수 NPC만 수행한다.
	 * 양방향을 한 번에 검사하므로 moved가 공격자든 피공격자든 관계를 놓치지 않는다.
	 */
	public static void onNpcWorldEvent(L1NpcInstance moved) {
		if (moved == null
				|| moved.isDead()
				|| moved.getCurrentHp() <= 0) {
			return;
		}

		final boolean relationEvent =
				needsNpcRelationEvent(moved);

		/*
		 * L1GuardInstance의 기존 searchTarget()은 map 1708에서
		 * L1MonsterInstance만 찾았다.
		 *
		 * 이제 guard/monster의 spawn/move world event에서만
		 * 주변 관계를 확인한다.
		 */
		final boolean guardEvent =
				moved.getMapId() == 1708
				&& (moved instanceof L1GuardInstance
						|| moved instanceof L1MonsterInstance);

		if (!relationEvent && !guardEvent) {
			return;
		}

		final ArrayList<L1Object> nearby = OBJECT_BUFFER.get();
		L1World.getInstance().getClusterGrid().queryNearby(moved, -1, nearby);

		try {
			for (int i = 0, size = nearby.size(); i < size; i++) {
				final L1Object obj = nearby.get(i);

				if (obj == null
						|| obj == moved
						|| !(obj instanceof L1NpcInstance)) {
					continue;
				}

				final L1NpcInstance other =
						(L1NpcInstance) obj;

				if (other.getMapId() != moved.getMapId()) {
					continue;
				}

				/*
				 * Guard ↔ Monster SeeCreature.
				 *
				 * 외부/world thread에서는 target을 직접 바꾸지 않는다.
				 * seeCreature() -> CNPCEvent -> AI owner에서 반영한다.
				 */
				if (guardEvent) {
					if (moved instanceof L1GuardInstance
							&& other instanceof L1MonsterInstance) {

						final L1GuardInstance guard =
								(L1GuardInstance) moved;

						if (guard._target == null) {
							guard.seeCreature(
									(L1MonsterInstance) other);
						}

					} else if (moved instanceof L1MonsterInstance
							&& other instanceof L1GuardInstance) {

						final L1GuardInstance guard =
								(L1GuardInstance) other;

						if (guard._target == null) {
							guard.seeCreature(
									(L1MonsterInstance) moved);
						}
					}
				}

				/*
				 * 아래는 기존 Monster faction/duel/immediate 관계.
				 */
				if (!relationEvent
						|| !needsNpcRelationEvent(other)
						|| other.isDead()
						|| other.getCurrentHp() <= 0) {
					continue;
				}

				if (moved instanceof L1MonsterInstance) {
					final L1MonsterInstance attacker =
							(L1MonsterInstance) moved;

					if (isNpcTargetCandidate(attacker, other)) {
						attacker.seeCreature(other);
					}
				}

				if (other instanceof L1MonsterInstance) {
					final L1MonsterInstance attacker =
							(L1MonsterInstance) other;

					if (isNpcTargetCandidate(attacker, moved)) {
						attacker.seeCreature(moved);
					}
				}
			}
		} finally {
			nearby.clear();
		}
	}

	/** 단일 NPC candidate 판정. 주변 검색 없음. */
	public static boolean isNpcTargetCandidate(
			L1MonsterInstance npc,
			L1NpcInstance target) {

		if (npc == null || target == null || npc == target) {
			return false;
		}
		if (npc.getMapId() != target.getMapId()
				|| target.isDead()
				|| target.getCurrentHp() <= 0) {
			return false;
		}

		final int myId = npc.getNpcId();
		final int otherId = target.getNpcId();
		final int want = specialTargetId(myId);

		/* IMMEDIATE / DUEL은 기존 searchTarget과 동일하게 최우선 exclusive target. */
		if (want != 0) {
			return otherId == want;
		}

		final int myFaction = factionOf(myId);
		if (myFaction == F_NONE) {
			return false;
		}

		final int otherFaction = factionOf(otherId);
		if (otherFaction == F_NONE || otherFaction == myFaction) {
			return false;
		}

		/* GATE_A는 Scarecrow 포함 기존 규칙을 그대로 허용. */
		if (myFaction == F_GATE_A) {
			return target instanceof L1MonsterInstance
					|| target instanceof L1ScarecrowInstance;
		}

		/* 일반 faction enemy는 Monster만. */
		if (!(target instanceof L1MonsterInstance)) {
			return false;
		}

		return ((L1MonsterInstance) target).getHiddenStatus() == 0;
	}

	/** owner drain에서 SEE sentinel을 검증할 때 사용. */
	public static boolean isSightTargetCandidate(
			L1MonsterInstance npc,
			L1Character target,
			long nowMs) {

		if (target instanceof L1PcInstance) {
			return isPlayerTargetCandidate(
					npc,
					(L1PcInstance) target,
					nowMs);
		}

		if (target instanceof L1NpcInstance) {
			return isNpcTargetCandidate(
					npc,
					(L1NpcInstance) target);
		}

		return false;
	}

	/**
	 * zero-hate SeeCreature 후보끼리만 비교한다.
	 * 실제 hate가 생긴 target은 CNpcAI에서 이 비교 자체를 하지 않는다.
	 */
	public static boolean isBetterSightTarget(
			L1MonsterInstance npc,
			L1Character candidate,
			L1Character current) {

		if (candidate == null) {
			return false;
		}
		if (current == null) {
			return true;
		}
		if (candidate == current) {
			return false;
		}

		final boolean candidateNpc = candidate instanceof L1NpcInstance;
		final boolean currentNpc = current instanceof L1NpcInstance;

		/* 기존 searchTarget(): NPC target을 PC보다 먼저 찾았다. */
		if (candidateNpc != currentNpc) {
			return candidateNpc;
		}

		if (candidateNpc) {
			final int cr = npcTargetRank(npc, (L1NpcInstance) candidate);
			final int rr = npcTargetRank(npc, (L1NpcInstance) current);
			if (cr != rr) {
				return cr > rr;
			}
		} else if (candidate instanceof L1PcInstance
				&& current instanceof L1PcInstance) {
			final L1PcInstance a = (L1PcInstance) candidate;
			final L1PcInstance b = (L1PcInstance) current;
			final int al = a.getLawful();
			final int bl = b.getLawful();

			if (PREFER_CHAOS
					&& (al < 0 || bl < 0)
					&& al != bl) {
				return al < bl;
			}
		}

		final int ad = npc.getLocation().getTileLineDistance(candidate.getLocation());
		final int bd = npc.getLocation().getTileLineDistance(current.getLocation());
		return ad < bd;
	}

	private static int npcTargetRank(
			L1MonsterInstance npc,
			L1NpcInstance target) {

		final int want = specialTargetId(npc.getNpcId());
		if (want != 0) {
			return target.getNpcId() == want ? 3 : 0;
		}

		final int myFaction = factionOf(npc.getNpcId());
		if (myFaction == F_NONE) {
			return 0;
		}

		final int otherFaction = factionOf(target.getNpcId());
		if (otherFaction == F_NONE || otherFaction == myFaction) {
			return 0;
		}

		return myFaction == F_GATE_A ? 2 : 1;
	}

	/*
	 * 특수 타겟 + 진영 적대를 주변 NPC 1회 스캔으로 처리.
	 * 우선순위:
	 * 1. IMMEDIATE / DUEL
	 * 2. GATE_A 적대
	 * 3. 일반 진영 적대
	 */
	public static L1NpcInstance findNpcTarget(L1MonsterInstance npc) {
		final int myId = npc.getNpcId();
		final int want = specialTargetId(myId);
		final int myFaction = factionOf(myId);

		final boolean gateScan =
				myFaction == F_GATE_A;

		final boolean factionScan =
				myFaction != F_NONE;

		if (want == 0 && !gateScan && !factionScan) {
			return null;
		}

		final ArrayList<L1Object> nearby = OBJECT_BUFFER.get();

		L1World.getInstance().getClusterGrid().queryNearby(npc, -1, nearby);

		L1MonsterInstance factionTarget = null;

		try {
			for (int i = 0, size = nearby.size(); i < size; i++) {
				final L1Object obj = nearby.get(i);

				if (!(obj instanceof L1MonsterInstance)
						&& !(obj instanceof L1ScarecrowInstance)) {
					continue;
				}

				final L1NpcInstance target =
						(L1NpcInstance) obj;

				if (target.isDead()) {
					continue;
				}

				final int otherId =
						target.getNpcTemplate().get_npcId();

				if (want != 0 && otherId == want) {
					return target;
				}

				if (gateScan) {
					final int otherFaction =
							factionOf(otherId);

					if (otherFaction != F_NONE
							&& otherFaction != myFaction) {

						return target;
					}
				}

				/*
				 * 일반 faction enemy는 Monster만 가능.
				 */
				if (!(target instanceof L1MonsterInstance)) {
					continue;
				}

				final L1MonsterInstance mon =
						(L1MonsterInstance) target;

				if (mon.getHiddenStatus() != 0) {
					continue;
				}

				final int otherFaction =
						factionOf(otherId);

				if (otherFaction != F_NONE
						&& otherFaction != myFaction
						&& factionTarget == null) {
					factionTarget = mon;
				}
			}

			return factionTarget;

		} finally {
			nearby.clear();
		}
	}



	/**
	 * SeeCreature 전용 단일 PC 판정.
	 *
	 * 주변을 검색하지 않는다.
	 * onPerceive()로 이미 들어온 PC 한 명만 기존 선공 규칙으로 검사한다.
	 *
	 * 중요:
	 * - _hateList / _target은 여기서 건드리지 않는다.
	 * - 외부 thread에서 호출되어도 NPC owner state를 직접 변경하지 않는다.
	 * - 실제 target 반영은 setHate() -> CNpcAI owner drain에서 수행한다.
	 */
	public static boolean isPlayerTargetCandidate(
			L1MonsterInstance npc,
			L1PcInstance pc,
			long now) {

		if (npc == null || pc == null) {
			return false;
		}

		if (npc.getMap().getBaseMapId() == 1936) {
			return false;
		}

		if (pc.getMapId() != npc.getMapId()
				|| pc.getCurrentHp() <= 0
				|| pc.isDead()
//				|| pc.isGm()
//				|| pc.isGmInvis()
				|| pc.isGhost()
				|| pc.isPrivateShop()) {
			return false;
		}

		final L1Npc t = npc.getNpcTemplate();
		if (t == null || (pc.isInvisble() && !t.is_agrocoi())) {
			return false;
		}

		final int mapId = npc.getMapId();
		final boolean specialMap =
				mapId == 88
				|| mapId == 98
				|| mapId == 92
				|| mapId == 91
				|| mapId == 95;

		final int karma = t.getKarma();
		final int npcId = t.get_npcId();

		final boolean agro = t.is_agro();
        if (agro) {
            final int movementRadius = npc.getMovementDistance();
            if (movementRadius > 0
                    && Math.max(Math.abs(npc.getX() - npc.getHomeX()),
                            Math.abs(npc.getY() - npc.getHomeY())) > movementRadius) {
                return false;
            }
        }
		final boolean agrososc = t.is_agrososc();
		final boolean agrocoi = t.is_agrocoi();

		final int gfx1 = t.is_agrogfxid1();
		final int gfx2 = t.is_agrogfxid2();

		final boolean noAgroRule =
				!agro
				&& !agrososc
				&& gfx1 < 0
				&& gfx2 < 0;

		final boolean bypassAgroRule =
				specialMap
				&& (!pc.isInvisble() || agrocoi);

		if (!bypassAgroRule) {
			if ((karma < 0 && pc.getKarmaLevel() >= 1)
					|| (karma > 0 && pc.getKarmaLevel() <= -1)) {
				return false;
			}

			final int sprite = pc.getCurrentSpriteId();

			if ((sprite == 6034 && karma < 0)
					|| (sprite == 6035
							&& (karma > 0
									|| npcId == 46070
									|| npcId == 46072))) {
				return false;
			}

			if (noAgroRule) {
				if (pc.getLawful() >= -1000) {
					return false;
				}
			} else {
				if (pc.isInvisble() && !agrocoi) {
					return false;
				}

				boolean targetable =
						pc.hasSkillEffect(L1SkillId.SHAPE_CHANGE)
						? agrososc
						: agro;

				if (!targetable) {
					targetable = matchClassGfx(gfx1, sprite);
				}

				if (!targetable) {
					targetable = matchClassGfx(gfx2, sprite);
				}

				if (!targetable) {
					return false;
				}
			}
		}


		return true;
	}
	
	private static final int GUARD_HELP_RANGE = 12;
	private static final int GUARD_HELP_RANGE_SQ =
	        GUARD_HELP_RANGE * GUARD_HELP_RANGE;

	public static void onGuardAttacked(
	        final L1NpcInstance source,
	        final L1Character attacker) {

	    if (source == null
	            || attacker == null
	            || source.isDead()
	            || source.getCurrentHp() <= 0) {
	        return;
	    }

	    final boolean townGuard =
	            source instanceof L1GuardInstance;

	    final boolean castleGuard =
	            source instanceof L1CastleGuardInstance;

	    if (!townGuard && !castleGuard) {
	        return;
	    }

	    final ArrayList<L1Object> nearby =
	            OBJECT_BUFFER.get();

	    L1World.getInstance()
	            .getClusterGrid()
	            .queryNearby(
	                    source,
	                    GUARD_HELP_RANGE,
	                    nearby);

	    final int sx = source.getX();
	    final int sy = source.getY();
	    final int mapId = source.getMapId();

	    try {
	        for (int i = 0, size = nearby.size();
	                i < size;
	                i++) {

	            final L1Object obj = nearby.get(i);

	            if (!(obj instanceof L1NpcInstance)
	                    || obj == source) {
	                continue;
	            }

	            /*
	             * 마을 경비는 마을 경비끼리,
	             * 성 경비는 성 경비끼리만 지원.
	             */
	            if (townGuard) {
	                if (!(obj instanceof L1GuardInstance)) {
	                    continue;
	                }
	            } else {
	                if (!(obj instanceof L1CastleGuardInstance)) {
	                    continue;
	                }
	            }

	            final L1NpcInstance helper =
	                    (L1NpcInstance) obj;

	            if (helper.isDead()
	                    || helper.getCurrentHp() <= 0
	                    || helper.getMapId() != mapId) {
	                continue;
	            }

	            final int dx = helper.getX() - sx;
	            final int dy = helper.getY() - sy;

	            if ((long) dx * dx + (long) dy * dy
	                    > GUARD_HELP_RANGE_SQ) {
	                continue;
	            }

	            /*
	             * 직접 _target 수정 금지.
	             * helper AI owner에게 공격자 hate event만 전달.
	             */
	            helper.setHate(attacker, 1);
	            helper.onNpcAI();
	        }
	    } finally {
	        nearby.clear();
	    }
	}


	private static boolean matchClassGfx(
			int gfx,
			int sprite) {

		if (gfx < 0) {
			return false;
		}

		if (gfx < CLASS_GFX.length) {
			return CLASS_GFX[gfx][0] == sprite
					|| CLASS_GFX[gfx][1] == sprite;
		}

		return gfx == sprite;
	}


}