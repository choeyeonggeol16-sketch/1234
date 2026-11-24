package l1j.server.server.model.npc.ai.action;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1MobSkillUse;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.CNPCEvent;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.npc.ai.CNPCServer;

/**
 * NPC AI decision/event runtime.
 *
 * reference design split:
 * - this class owns event/hate drain and next-action selection only.
 * - CCreatureController/Action execution is submitted to the NPC's fixed CIO owner.
 * - active action cadence is no longer driven by AiEngine polling.
 *
 * Existing L1 target/action priority is intentionally preserved.
 */
public final class CNpcAI {

    private static final Logger LOG =
            Logger.getLogger(CNpcAI.class.getName());

    static final long BASE_TICK_MS = 100L;

    static final byte PRE_EXECUTE = 0;
    static final byte PRE_WAIT = 1;
    static final byte PRE_RETHINK = 2;
    static final byte PRE_STOP = 3;

    private CNpcAI() {
    }

    /** Base NPC keeps virtual hooks; decision and group policy live here. */
    public static boolean noTarget(final L1NpcInstance npc) {
        final L1Character master = npc.getMaster();
        if (master != null && master.getMapId() == npc.getMapId()
                && npc.getLocation().getTileLineDistance(master.getLocation()) > 2) {
            return npc.noTargetMaster();
        }
        if (!L1World.getInstance().hasRecognizePlayerFast(npc)) return true;
        if (master == null && npc.isMovable() && !npc.isRest()) {
            final L1MobGroupInfo group = npc.getMobGroupInfo();
            final L1NpcInstance leader = group == null ? null : group.getActiveLeader();
            if (group == null || leader == npc) {
                npc.noTargetNormal();
            } else {
                return npc.noTargetGroup(leader);
            }
        }
        return false;
    }

    private static boolean idleGroupFollower(final L1MonsterInstance monster) {
        // Preserve the L1 blocked-combat random-walk fallback.
        return monster.getTarget() == null
                && monster._desireBlockedAttackTargetId == 0
                && L1MobGroupInfo.isFollower(monster);
    }

    /**
     * AiEngine owner entry.
     *
     * return > 0 : explicit AI-side deadline (condition/cast/freeze defer)
     * return <= 0: no AI polling; action side or external event will wake us.
     */
    public static long tick(
            final L1NpcInstance npc,
            final long nowMs) {

        if (npc == null) {
            return -1L;
        }

        try {
            final boolean targetChecked =
                    HandleEvent(npc, nowMs);

            if (notContinued(npc)) {
                stop(npc);
                return -1L;
            }

            /*
             * Active action cadence is owned by CIO. External hate/sight wakeups
             * are still drained above, but must never punch through the current
             * movement/attack/skill cooldown. The CIO action deadline will either
             * continue the action or wake AI for a new decision when due.
             */
            if (npc.npcActionOwnsCadence()) {
                return -1L;
            }

            // Preserve the original CNpcAI deadline gate (including startAI).
            if (nowMs < npc._nextAllowedTickMs) {
                return npc._nextAllowedTickMs;
            }

            if (npc._castingSkill.get()) {
                npc._nextAllowedTickMs = nowMs + 200L;
                return npc._nextAllowedTickMs;
            }

            final int paralysisTime = npc.getParalysisTime();
            if (paralysisTime > 0) {
                npc.setParalysisTime(0);
                npc.setParalyzed(false);
                npc._nextAllowedTickMs = nowMs + paralysisTime;
                return npc._nextAllowedTickMs;
            }

            if (npc.isParalyzed()
                    || npc.isSleeped()
                    || npc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {

                npc._nextAllowedTickMs = nowMs + 200L;
                return npc._nextAllowedTickMs;
            }

            final byte prep = prepareDecision(npc, targetChecked, nowMs);

            if (prep == PRE_STOP) {
                stop(npc);
                return -1L;
            }

            if (prep != PRE_EXECUTE) {
                final long sleep = Math.max(npc.getSleepTime(), BASE_TICK_MS);
                npc._nextAllowedTickMs = nowMs + sleep;
                return npc._nextAllowedTickMs;
            }

            selectAction(npc);

            return -1L;

        } catch (Throwable t) {
            LOG.log(
                    Level.WARNING,
                    "NpcAI 예외 — 재시도 예약. NPC: "
                            + npc.getNpcTemplate().get_npcId(),
                    t);

            return nowMs + 1000L;
        }
    }

    /**
     * CIO action timer preflight.
     *
     * The old CNpcAI ran these checks before every action tick. They remain
     * in the same order, but execute on the action owner so the original
     * semantics are not lost when action cadence leaves AiEngine.
     */
    public static byte beforeActionTick(
            final L1NpcInstance npc,
            final long nowMs) {

        if (npc == null || notContinued(npc)) {
            return PRE_STOP;
        }

        if (npc._castingSkill.get()) {
            npc.setSleepTime(200L);
            return PRE_WAIT;
        }

        final int paralysisTime = npc.getParalysisTime();
        if (paralysisTime > 0) {
            npc.setSleepTime(paralysisTime);
            npc.setParalysisTime(0);
            npc.setParalyzed(false);
            return PRE_WAIT;
        }

        if (npc.isParalyzed()
                || npc.isSleeped()
                || npc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {

            npc.setSleepTime(200L);
            return PRE_WAIT;
        }

        // Absolute return owns the current positional action until completion.
        if (dMoveTo.isReturningHome(npc)) return PRE_EXECUTE;

        if (npc.checkCondition()) {
            /*
             * Legacy process() returned without executing the controller and
             * waited getSleepTime() before the next THINK. Keep that delay.
             */
            CCreatureController.think(npc);
            return PRE_WAIT;
        }

        npc.ExecuteSpecialAction();

        if (npc instanceof L1SummonInstance) {
            final L1Character master = npc.getMaster();

            if (master != null
                    && master.get_Summonre() != 0) {

                npc.deleteMe();
                CCreatureController.set(
                        npc,
                        CCreatureController.ACTION_NOTHING);
                return PRE_STOP;
            }
        }

        // Legacy per-AI-tick default. Individual actions overwrite this.
        npc.setSleepTime(300L);
        npc.checkTarget();

        final byte runningAction = CCreatureController.currentAction(npc);
        if (npc.mobTeleport()) {
            // ReturnStartPoint submits on this owner and advances generation.
            // PRE_WAIT cannot execute or rearm the retired action.
            return PRE_WAIT;
        }
        npc.onItemUse();

        if (npc instanceof L1MonsterInstance
                && acceptsCombatRetarget(runningAction)) {

            final L1Character requested = npc.npcChaseRetargetConsume();
            if (requested != null) {
                /*
                 * NC picks the current CDesirePQ top when deciding an ATTACK.
                 * A queued retarget is only a wake-up signal: its candidate
                 * can lose priority, die, become invisible, or be removed
                 * before this CIO owner tick consumes the request.
                 *
                 * Re-arbitrate at the commit point instead of blindly
                 * installing the earlier snapshot. No additional queue
                 * or target scan: topAttackTarget owns native desire order.
                 */
                final L1MonsterInstance monster = (L1MonsterInstance) npc;
                final L1Character winner =
                        CDesirePQ.topAttackTarget(monster, nowMs);
                if (winner != null
                        && winner != npc._target
                        && !winner.isDead()
                        && winner.getCurrentHp() > 0
                        && winner.getMapId() == npc.getMapId()
                        && (!winner.isInvisble()
                                || npc.getNpcTemplate().is_agrocoi())
                        && monster._desireBlockedAttackTargetId
                                != winner.getId()) {
                    npc._target = winner;
                    CCreatureController.think(npc);
                    return PRE_RETHINK;
                }
            }
        }

        /*
         * Idle movement must die immediately when the NPC is not allowed to
         * roam or when no player is recognizing this monster anymore.
         *
         * This check belongs on the CIO action preflight, not only in
         * selectAction(): MOVE_AROUND owns its own cadence and otherwise never
         * returns to THINK after the last player leaves the map.
         */
        if (npc instanceof L1MonsterInstance) {
            final byte currentAction =
                    CCreatureController.currentAction(npc);

            if (currentAction
                    == CCreatureController.ACTION_MOVE_AROUND_NATIVE
                    && (!canMoveAround(npc)
                            || idleGroupFollower((L1MonsterInstance) npc)
                            || (npc._target == null
                                    && !L1World.getInstance()
                                            .hasRecognizePlayerFast(npc)))) {

                stopMoveAroundRuntime(
                        (L1MonsterInstance) npc);

                CCreatureController.think(npc);
                return PRE_RETHINK;
            }
        }

        if (npc._target != null) {
            if (npc.getHiddenStatus()
                    != L1NpcInstance.HIDDEN_STATUS_NONE) {

                CCreatureController.set(
                        npc,
                        CCreatureController.ACTION_NOTHING);
                return PRE_STOP;
            }

            final byte currentAction =
                    CCreatureController.currentAction(npc);

            final boolean retailMoveAround =
                    npc instanceof L1MonsterInstance
                    && currentAction
                            == CCreatureController.ACTION_MOVE_AROUND_NATIVE
                    && CDesirePQ.isMoveAroundTop(
                            (L1MonsterInstance) npc,
                            nowMs);

            final boolean fixedGuardHolding = CStandAction.isGuard(npc)
                    && currentAction == CCreatureController.ACTION_STAND
                    && !CStandAction.canInterruptGuardStand(npc);
            if (!retailMoveAround && !fixedGuardHolding
                    && !CCreatureController.isCombatAction(
                            currentAction)) {

                CCreatureController.think(npc);
                return PRE_RETHINK;
            }

        } else if (CCreatureController.isCombatAction(
                CCreatureController.currentAction(npc))) {

            CCreatureController.think(npc);
            return PRE_RETHINK;
        }

        return PRE_EXECUTE;
    }

    public static void stopFromController(final L1NpcInstance npc) {
        if (npc != null) {
            stop(npc);
        }
    }

    private static byte prepareDecision(
            final L1NpcInstance npc,
            final boolean targetChecked,
            final long nowMs) {

        if (npc.checkCondition()) {
            return PRE_WAIT;
        }

        npc.ExecuteSpecialAction();

        if (npc instanceof L1SummonInstance) {
            final L1Character master = npc.getMaster();

            if (master != null
                    && master.get_Summonre() != 0) {

                npc.deleteMe();
                return PRE_STOP;
            }
        }

        npc.setSleepTime(300L);

        if (!targetChecked) {
            npc.checkTarget();
        }

        if (npc.mobTeleport()) return PRE_WAIT;
        npc.onItemUse();

        if (npc._target != null
                && npc.getHiddenStatus()
                        != L1NpcInstance.HIDDEN_STATUS_NONE) {
            return PRE_STOP;
        }

        return PRE_EXECUTE;
    }

    /**
     * Existing CCreatureController.selectAction() policy moved here verbatim in
     * priority/order. Only the mutation point changes: CNpcAI submits a
     * primitive action id to the fixed CIO owner instead of executing it here.
     */
    private static void selectAction(final L1NpcInstance creature) {
        L1Character target = creature.getTarget();

        /*
         * Retail warrior desire dispatch.
         *
         * A target may deliberately stay in _target/_hateList while
         * MOVE_AROUND is the highest desire. Therefore target presence alone
         * must not force MOVE_TO_TARGET.
         */
        if (creature instanceof L1MonsterInstance) {
            final L1MonsterInstance monster =
                    (L1MonsterInstance) creature;

            final long nowMs =
                    System.currentTimeMillis();

            /*
             * Retail warrior NO_DESIRE -> MOVE_AROUND is only runnable for an
             * NPC that is physically allowed to roam and is currently observed.
             *
             * The old order created PS_MOVE_AROUND before the generic
             * isMovable()/isRest()/recognize checks, so stationary objects such
             * as the map-103 orbs could enter MOVE_AROUND_NATIVE.
             *
             * Do not destroy the desire PQ here: a stationary monster can still
             * own a valid ATTACK desire and attack from its fixed position.
             */
            final boolean moveAroundRunnable =
                    canMoveAround(monster)
                    && !idleGroupFollower(monster)
                    && L1World.getInstance()
                            .hasRecognizePlayerFast(monster);

            if (moveAroundRunnable) {
                CDesirePQ.onNoDesire(
                        monster,
                        nowMs);
            }

            byte desire =
                    CDesirePQ.isActive(monster)
                            ? CDesirePQ.topState(monster, nowMs)
                            : CDesirePQ.PS_IDLE;

            /*
             * A skill desire may expire or lose its target after selection.
             * Re-evaluate the heap after removing that exact key. The old
             * dispatch kept the stale PS_USE_SKILL value, then fell through
             * to the cached _target and could chase while MOVE_AROUND was
             * actually the next desire. No spell is executed in this loop.
             */
            int rejectedSkills = 0;
            while (desire == CDesirePQ.PS_USE_SKILL
                    && rejectedSkills < 64) {
                final int skillId = CDesirePQ.activeSkillId(monster);
                final L1Character skillTarget =
                        CDesirePQ.activeSkillTarget(monster);
                if (skillId > 0 && skillTarget != null) {
                    CCreatureController.submit(
                            creature, CCreatureController.ACTION_USE_SKILL);
                    return;
                }
                if (!CDesirePQ.removeUseSkillDesire(monster,
                        CDesirePQ.activeSkillTargetId(monster), skillId)) {
                    break;
                }
                rejectedSkills++;
                desire = CDesirePQ.topState(monster, nowMs);
            }
            if (desire == CDesirePQ.PS_USE_SKILL) {
                // Never execute an unrelated old target because a desire is
                // temporarily invalid. Let the existing owner re-evaluate.
                CCreatureController.submit(
                        creature, CCreatureController.ACTION_THINK);
                return;
            }

            if ((desire == CDesirePQ.PS_IDLE || desire == CDesirePQ.PS_MOVE_AROUND
                    || desire == CDesirePQ.PS_GET_ITEM)
                    && (monster.isPickupItem() || CGetItemAction.targetItem(monster) != null
                            || desire == CDesirePQ.PS_GET_ITEM)) {
                if (CGetItemAction.prepareMonsterItem(monster, nowMs)) {
                    CCreatureController.submit(monster, CCreatureController.ACTION_GET_ITEM);
                    return;
                }
                if (desire == CDesirePQ.PS_GET_ITEM) {
                    desire = CDesirePQ.topState(monster, nowMs);
                }
            }

            if (desire == CDesirePQ.PS_MOVE_AROUND) {
                if (moveAroundRunnable) {
                    CCreatureController.submit(
                            creature,
                            CCreatureController.ACTION_MOVE_AROUND_NATIVE);
                    return;
                }

                // An immobile / resting NPC must never enter random walk.
                // A stationary target can still be attacked through the
                // existing L1 action checks below.
                stopMoveAroundRuntime(monster);
            }

            if (desire == CDesirePQ.PS_ATTACK) {
                target = CDesirePQ.topAttackTarget(monster, nowMs);

                if (target == null) {
                    // NC cannot execute an ATTACK whose desire target has
                    // already disappeared. Never fall through to a stale
                    // L1 _target captured by the previous action.
                    if (moveAroundRunnable
                            && CDesirePQ.isMoveAroundTop(monster, nowMs)) {
                        CCreatureController.submit(
                                creature,
                                CCreatureController.ACTION_MOVE_AROUND_NATIVE);
                        return;
                    }
                    CCreatureController.submit(
                            creature,
                            CCreatureController.ACTION_THINK);
                    return;
                }
                creature._target = target;
                CDesirePQ.leaveMoveAround(monster);
            }
        }

        if (target != null) {
            // AMT_STAND for a physically fixed guard. Preserve the existing
            // L1 target and attack rules; do not submit an impossible chase.
            if (CStandAction.isGuard(creature) && !creature.isMovable()
                    && !creature.isAttackPosition(target, creature.getNpcTemplate().get_ranged())) {
                CCreatureController.submit(creature, CCreatureController.ACTION_STAND);
                return;
            }
            if (!creature.isActionable()
                    && creature.isMovable()) {

                CCreatureController.submit(
                        creature,
                        CCreatureController.ACTION_ESCAPE);
                return;
            }

            if (creature.isAttackPosition(
                    target,
                    creature.getNpcTemplate().get_ranged())) {

                CCreatureController.submit(
                        creature,
                        CCreatureController.ACTION_ATTACK);
                return;
            }

            CCreatureController.submit(
                    creature,
                    CCreatureController.ACTION_MOVE_TO_TARGET,
                    CCreatureController.ACTION_ATTACK);
            return;
        }

        // Monster candidates were already arbitrated against MOVE_AROUND.
        if (!(creature instanceof L1MonsterInstance)) {
            creature.tickItemSearchCooldown();
            creature.checkTargetItem();
        }

        if (!(creature instanceof L1MonsterInstance) && creature.isPickupItem()) {
            if (CGetItemAction.targetItem(creature) == null) {
                creature.searchTargetItem();
            }

            if (CGetItemAction.targetItem(creature) != null) {
                CCreatureController.submit(
                        creature,
                        CCreatureController.ACTION_GET_ITEM);
                return;
            }
        }

        // guard_stand NO_DESIRE -> MOVE_TO -> MOVE_TO_FINISHED -> DO_NOTHING.
        // Existing L1 guards are post guards; mobility is the existing template
        // flag, not a new DB setting or an invented patrol assignment.
        if (CStandAction.isGuard(creature)) {
            if (creature.isMovable() && !CStandAction.atHome(creature)) {
                if (dMoveTo.request(creature, creature.getHomeX(), creature.getHomeY())) {
                    return;
                }
            } else if (!L1World.getInstance().hasRecognizePlayerFast(creature)) {
                CCreatureController.submit(creature, CCreatureController.ACTION_NOTHING);
                return;
            }
            CCreatureController.submit(creature, CCreatureController.ACTION_STAND);
            return;
        }

        /*
         * NC DESIRE_PET_DEFAULT selects the owner's default action after
         * ATTACK / USE_SKILL have been considered. In L1 this is the
         * existing noTarget() virtual command hook on the CIO owner.
         *
         * Pet/Summon stay, extend and alert must not be preempted by the
         * generic FOLLOW_MASTER shortcut below. Dolls retain their 40-tile
         * teleport and owner-visibility policy; companions retain item mode.
         */
        if (creature instanceof L1CompanionInstance
                || creature instanceof L1PetInstance
                || creature instanceof L1SummonInstance
                || creature instanceof L1DollInstance) {
            CCreatureController.submit(
                    creature,
                    CCreatureController.ACTION_COMPANION);
            return;
        }

        final L1Character master = creature.getMaster();

        if (master != null
                && master.getMapId() == creature.getMapId()
                && creature.getLocation()
                        .getTileLineDistance(
                                master.getLocation()) > 2) {

            CCreatureController.submit(
                    creature,
                    CCreatureController.ACTION_FOLLOW_MASTER);
            return;
        }

        if (!L1World.getInstance()
                .hasRecognizePlayerFast(creature)) {

            CCreatureController.submit(
                    creature,
                    CCreatureController.ACTION_NOTHING);
            return;
        }

        if (master == null
                && creature.isMovable()
                && !creature.isRest()) {

            if (L1MobGroupInfo.isFollower(creature)) {

                CCreatureController.submit(
                        creature,
                        CCreatureController.ACTION_FOLLOW_GROUP);
                return;
            }

            CCreatureController.submit(
                    creature,
                    CCreatureController.ACTION_MOVE);
            return;
        }

        CCreatureController.submit(
                creature,
                CCreatureController.ACTION_STAND);
    }

    /**
     * True only when this NPC is allowed to perform idle roaming.
     *
     * isMovable=false covers fixed/non-moving NPCs.
     * isRest=true covers NPCs configured to stay in place while idle.
     */
    private static boolean canMoveAround(
            final L1NpcInstance npc) {

        return npc != null
                && npc.isMovable()
                && !npc.isRest();
    }

    /**
     * Clears only MOVE_AROUND physical/runtime state.
     * ATTACK desires and hate are intentionally preserved.
     */
    private static void stopMoveAroundRuntime(
            final L1MonsterInstance monster) {

        if (monster == null) {
            return;
        }

        SetMoving.clear(monster);
        CMoveToTargetWaitAction.clear(monster);
        CDesirePQ.leaveMoveAround(monster);
    }

    private static void stop(final L1NpcInstance npc) {
        L1MobSkillUse.resetAllSkillUseCount(npc);

        // Clear CIO-owner movement/action state before objectId can be reused.
        CIOThreadCallback.getInstance().cancelNpcAction(npc);
        // A living hidden NPC suspends its AI, never enters death polling.
        // Clear on its owner before publishing the stopped state.
        if (npc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE
                && !npc.isDead() && !npc.isDestroyed() && npc.getCurrentHp() > 0
                && !npc.isDeathProcessing()) {
            npc.allTargetClear();
            npc.setAiRunning(false);
            CNPCServer.getInstance().unregister(npc);
            if (npc.hasPendingHate() && !npc.isDead() && !npc.isDestroyed()) {
                npc.onNpcAI();
            }
            return;
        }
        npc.setAiRunning(false);

        ThreadPool.getInstance().scheduleRequired(
                new DeathSyncTimer(npc),
                0);

        CNPCServer.getInstance().unregister(npc);
    }

    /** NC ChangeNPCState split: L1 policy is selected by the caller;
     * the owner alone retires the current action and publishes the state.
     */
    private static boolean ChangeNPCState(final L1NpcInstance npc, final int event) {
        if (!(npc instanceof L1MonsterInstance)
                || npc.getMaxHp() / 3 <= npc.getCurrentHp()) {
            return false;
        }
        final int state;
        final int animation;
        final int status;
        switch (event) {
        case L1NpcInstance.HATE_EVENT_HIDE_SINK:
            state = L1NpcInstance.HIDDEN_STATUS_SINK;
            animation = ActionCodes.ACTION_Hide;
            status = 13;
            break;
        case L1NpcInstance.HATE_EVENT_HIDE_ANTHARAS:
            state = L1NpcInstance.HIDDEN_STATUS_SINK;
            animation = ActionCodes.ACTION_AntharasHide;
            status = 20;
            break;
        case L1NpcInstance.HATE_EVENT_HIDE_FLY:
            state = L1NpcInstance.HIDDEN_STATUS_FLY;
            animation = ActionCodes.ACTION_Moveup;
            status = 4;
            break;
        case L1NpcInstance.HATE_EVENT_HIDE_DRAGON_FLY:
            state = L1NpcInstance.HIDDEN_STATUS_FLY;
            animation = ActionCodes.ACTION_Moveup;
            status = 11;
            break;
        default:
            return false;
        }
        if (!npc.tryChangeHiddenStatus(L1NpcInstance.HIDDEN_STATUS_NONE, state)) {
            return false;
        }

        // Retire the previous action and disarm its wheel deadline before
        // allTargetClear resets its action ID.
        CIOThreadCallback.getInstance().cancelNpcAction(npc);
        npc.allTargetClear();
        npc.npcChaseRetargetConsume();
        SC_DoActionGFX.broadcast(npc, animation);
        npc.setStatus(status);
        SC_WorldPutObject.broadcastNpc(npc);
        return true;
    }

    static void broadcastAirLanding(final L1NpcInstance npc) {
        SC_DoActionGFX.broadcast(npc, ActionCodes.ACTION_Movedown);
        npc.setStatus(0);
        SC_WorldPutObject.broadcastNpc(npc);
        npc.startChat(L1NpcInstance.CHAT_TIMING_HIDE);
    }

    private static boolean HandleEvent(
            final L1NpcInstance npc,
            final long nowMs) {

        boolean changed = false;
        boolean realHateChanged = false;
        boolean attackDesireChanged = false;
        boolean sightDesireChanged = false;
        boolean interruptMoveAround = false;
        boolean invisibilityRetarget = false;
        boolean airFoodScanned = false;

        /*
         * Native CNewAction owns its target for the lifetime of the action.
         * New desire/hate events may change the PQ while an action is running,
         * but they do not silently replace that action's target mid-tick.
         */
        final boolean guardStanding = CStandAction.isGuard(npc)
                && CCreatureController.currentAction(npc) == CCreatureController.ACTION_STAND;
        final boolean actionOwnsTarget =
                npc.npcActionOwnsCadence()
                && CCreatureController.isCombatAction(
                        CCreatureController.currentAction(npc));

        // The retail seven-second lifetime gate belongs to CLAN_ATTACKED,
        // not to SEE_CREATURE. Never retain a one-shot sight event for 7s.
        CNPCEvent pending = null;
        while (pending != null || (pending = npc.TakeNpcEvents()) != null) {
            final CNPCEvent event = pending;
            pending = event.next;
            try {
                if (event.epoch != npc.npcEventEpoch() || npc.isDestroyed()
                        || dMoveTo.isReturningHome(npc)) continue;
                final L1Character cha = event.cha;

                if (cha == null) {
                    continue;
                }

                final int eventValue = event.value;

                if (eventValue >= L1NpcInstance.HATE_EVENT_HIDE_SINK
                        && eventValue <= L1NpcInstance.HATE_EVENT_HIDE_DRAGON_FLY) {
                    if (ChangeNPCState(npc, eventValue)) {
                        changed = false;
                        realHateChanged = false;
                        attackDesireChanged = false;
                        sightDesireChanged = false;
                        interruptMoveAround = false;
                        invisibilityRetarget = false;
                    }
                    continue;
                }

                if (eventValue == L1NpcInstance.HATE_EVENT_AIR_FOOD) {
                    // Coalesce repeated approaches within the existing owner drain.
                    if (!airFoodScanned) {
                        airFoodScanned = true;
                        CGetItemAction.searchItemFromAir(npc);
                    }
                    continue;
                }

                if (eventValue == L1NpcInstance.HATE_EVENT_AIR_LAND) {
                    final int hidden = npc.getHiddenStatus();
                    if (cha instanceof L1PcInstance pc && !pc.isDead()
                            && pc.getCurrentHp() > 0 && pc.getMapId() == npc.getMapId()
                            && !pc.hasSkillEffect(60) && !pc.hasSkillEffect(97)
                            && npc.getCurrentHp() == npc.getMaxHp()
                            && (hidden == L1NpcInstance.HIDDEN_STATUS_SINK
                                    || hidden == L1NpcInstance.HIDDEN_STATUS_FLY)
                            && npc.getLocation().getTileLineDistance(pc.getX(), pc.getY())
                                    <= (hidden == L1NpcInstance.HIDDEN_STATUS_SINK ? 2 : 1)
                            && npc.tryChangeHiddenStatus(hidden, L1NpcInstance.HIDDEN_STATUS_NONE)) {
                        if (hidden == L1NpcInstance.HIDDEN_STATUS_SINK) {
                            SC_DoActionGFX.broadcast(npc, ActionCodes.ACTION_Appear);
                            npc.setStatus(0);
                            SC_WorldPutObject.broadcastNpc(npc);
                        }
                        if (!pc.isGm()) {
                            npc._hateList.add(pc, 0);
                            npc._target = pc;
                            if (npc instanceof L1MonsterInstance) {
                                CDesirePQ.ensureAttackCandidate((L1MonsterInstance) npc, pc, nowMs);
                                attackDesireChanged = true;
                            }
                            changed = true;
                        }
                        if (hidden == L1NpcInstance.HIDDEN_STATUS_FLY) {
                            broadcastAirLanding(npc);
                        }
                    }
                    continue;
                }

                // Discard combat/sight events queued before a hide transition.
                if (npc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE) continue;

                if (eventValue
                        == L1NpcInstance.HATE_EVENT_SIGHT) {

                    if (!NpcEventFactory.isSightTargetCandidate(
                            npc,
                            cha,
                            nowMs)) {
                        continue;
                    }

                    if (!npc._hateList.containsKey(cha)) {
                        npc._hateList.add(cha, 0);
                    }

                    if (npc instanceof L1MonsterInstance) {
                        final L1MonsterInstance monster =
                                (L1MonsterInstance) npc;

                        CDesirePQ.addSightAttack(
                                monster,
                                cha,
                                nowMs);
                        sightDesireChanged = true;

                        final L1Character desireTarget =
                                CDesirePQ.topAttackTarget(
                                        monster,
                                        nowMs);

                        if (!actionOwnsTarget
                                && desireTarget != null) {
                            npc._target = desireTarget;
                        }

                    } else if (!actionOwnsTarget
                            && npc._target == null) {
                        npc._target = cha;
                    }

                    changed = true;
                    continue;
                }

                if (eventValue == L1NpcInstance.HATE_EVENT_GROUP_LINK) {
                    // Awareness is owner-only and never accumulates damage hate.
                    if (!npc._hateList.isEmpty() || cha == npc || cha.isDead()
                            || cha.getCurrentHp() <= 0 || cha.getMapId() != npc.getMapId()
                            || (cha instanceof L1NpcInstance helper && helper.isDestroyed())
                            || (cha.isInvisble() && !npc.getNpcTemplate().is_agrocoi())) {
                        continue;
                    }
                    if (npc instanceof L1MonsterInstance
                            && CDesirePQ.ensureAttackCandidate(
                                    (L1MonsterInstance) npc, cha, nowMs)) {
                        npc._hateList.add(cha, 0);
                        npc._dropHateList.add(cha, 0);
                        changed = true;
                        attackDesireChanged = true;
                        sightDesireChanged = true;
                    } else if (npc instanceof L1GuardianInstance) {
                        // Former Guardian.setLink wrote hate/checkTarget off-owner.
                        // Preserve its idle-only, non-drop-hate behavior.
                        npc._hateList.add(cha, 0);
                        changed = true;
                    }
                    continue;
                }

                if (eventValue == L1NpcInstance.HATE_EVENT_INVISIBILITY) {
                    if (!(npc instanceof L1MonsterInstance)
                            || cha != npc._target
                            || !cha.isInvisble()) {
                        continue;
                    }

                    final L1MonsterInstance monster =
                            (L1MonsterInstance) npc;

                    // NC keeps the target when this NPC can detect hidden
                    // creatures. A bystander must not trigger a target switch
                    // merely because it was present when invisibility began.
                    if (monster.getNpcTemplate().is_agrocoi()) {
                        continue;
                    }

                    // NC desire arbitration removes an invalid ATTACK key
                    // and selects the highest remaining admitted desire.
                    // Do not manufacture +200 hate for the nearest visible
                    // player or override the recorded desire priorities.
                    CDesirePQ.removeAttack(monster, cha);
                    npc._hateList.remove(cha);

                    final L1Character alternate =
                            CDesirePQ.GetTopDesire(monster, cha.getId());

                    if (alternate != null
                            && !npc._hateList.containsKey(alternate)) {
                        npc._hateList.add(alternate, 0);
                    }
                    npc._target = alternate;

                    changed = true;
                    invisibilityRetarget = true;
                    continue;
                }

                if (L1NpcInstance.isAttackHateEvent(
                        eventValue)) {

                    final int damage =
                            L1NpcInstance.eventDamage(
                                    eventValue);

                    if (npc instanceof L1MonsterInstance) {

                        /*
                         * warrior::ATTACKED
                         * damage/max_hp/0.05*100 (including damage=0)
                         */
                        CDesirePQ.addAttacked(
                                (L1MonsterInstance) npc,
                                cha,
                                damage,
                                nowMs);

                        attackDesireChanged = true;

                        if (interruptMoveAroundOnHit(npc, cha, nowMs)) {
                            interruptMoveAround = true;
                        }
                    }

                    int hate = damage;

                    if (!npc.isFirstAttack()
                            && hate != 0) {

                        hate += npc.getMaxHp() / 10;
                        npc.setFirstAttack(true);
                    }

                    npc._hateList.add(cha, hate);
                    npc._dropHateList.add(cha, hate);

                    changed = true;
                    realHateChanged = true;
                    continue;
                }

                if (L1NpcInstance.isClanHateEvent(
                        eventValue)) {

                    final int damage =
                            L1NpcInstance.eventDamage(
                                    eventValue);

                    if (npc instanceof L1MonsterInstance) {

                        if (CDesirePQ.addClanAttacked(
                                (L1MonsterInstance) npc,
                                cha,
                                damage,
                                nowMs)) {

                            if (!npc._hateList
                                    .containsKey(cha)) {

                                npc._hateList.add(
                                        cha,
                                        0);
                            }

                            changed = true;
                            attackDesireChanged = true;
                            sightDesireChanged = true;
                        }
                    }

                    continue;
                }

                int hate = eventValue;

                if (hate > 0
                        && interruptMoveAroundOnHit(npc, cha, nowMs)) {
                    interruptMoveAround = true;
                }

                if (!npc.isFirstAttack()
                        && hate != 0) {

                    hate += npc.getMaxHp() / 10;
                    npc.setFirstAttack(true);
                }

                npc._hateList.add(cha, hate);
                npc._dropHateList.add(cha, hate);

                changed = true;
                realHateChanged = true;

            } catch (Throwable t) {
                LOG.log(
                        Level.WARNING,
                        "[NpcAI] HandleEvent 연산 중 에러 발생 (NPC ID: "
                                + npc.getNpcId() + ")",
                        t);

            } finally {
                CNPCEvent.Release(event);
            }
        }

        // A successful SEE is a new desire. If MOVE_AROUND owns the CIO
        // deadline (including its six-second rest), ask that owner to arbitrate
        // immediately. acceptFromAi() retains the wander action when its
        // desire is still top, so lower-priority sight never resets a rest.
        if (interruptMoveAround || invisibilityRetarget
                || (sightDesireChanged
                        && CCreatureController.currentAction(npc)
                                == CCreatureController.ACTION_MOVE_AROUND_NATIVE)) {
            CCreatureController.submit(
                    npc,
                    CCreatureController.ACTION_THINK);
        }

        if (attackDesireChanged && actionOwnsTarget
                && npc instanceof L1MonsterInstance) {
            final byte action = CCreatureController.currentAction(npc);
            if (acceptsCombatRetarget(action)) {
                final L1Character winner =
                        CDesirePQ.topAttackTarget(
                                (L1MonsterInstance) npc,
                                nowMs);
                npc.npcChaseRetargetRequest(
                        winner != npc._target ? winner : null);
            }
        }

        if (changed && !actionOwnsTarget) {
            if (npc instanceof L1MonsterInstance
                    && CDesirePQ.isActive(
                            (L1MonsterInstance) npc)) {

                final L1Character desireTarget =
                        CDesirePQ.topAttackTarget(
                                (L1MonsterInstance) npc,
                                nowMs);

                if (desireTarget != null) {
                    npc._target = desireTarget;
                } else if (realHateChanged
                        && npc._target == null) {
                    npc._target =
                            npc._hateList
                                    .getMaxHateCharacter();
                }

            } else if (realHateChanged) {
                npc._target =
                        npc._hateList
                                .getMaxHateCharacter();
            }

            npc.checkTarget();
        }

        // A new admitted enemy must not wait for the guard's 40-second idle
        // timer. Reuse the owner generation/wake path, leaving active combat
        // and movement cadence unchanged.
        if (changed && guardStanding
                && (CCreatureController.currentAction(npc) != CCreatureController.ACTION_STAND
                    || CStandAction.canInterruptGuardStand(npc))) {
            CCreatureController.rethinkNow(npc);
        }
        return changed;
    }

    /** A running ATTACK needs the same desire-winner handoff as a chase.
     * Queue on the AI owner; consume at the next due CIO preflight so a hit
     * never grants an extra attack or replaces a captured target mid-action.
     * Casting and escape actions keep their existing targeting contract.
     */
    private static boolean acceptsCombatRetarget(final byte action) {
        return action == CCreatureController.ACTION_ATTACK
                || action == CCreatureController.ACTION_MOVE_TO_TARGET
                || action == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                || action == CCreatureController.ACTION_MOVE_TO_TARGET_WAIT;
    }


    private static boolean interruptMoveAroundOnHit(
            final L1NpcInstance npc,
            final L1Character attacker,
            final long nowMs) {

        if (!(npc instanceof L1MonsterInstance)
                || CCreatureController.currentAction(npc)
                        != CCreatureController.ACTION_MOVE_AROUND_NATIVE) {
            return false;
        }

        final L1MonsterInstance monster = (L1MonsterInstance) npc;
        if (!CDesirePQ.prioritizeMoveAroundAttacker(monster, attacker, nowMs)) {
            return false;
        }

        CDesirePQ.releaseBlockedAttack(monster);
        return true;
    }

    private static boolean notContinued(
            final L1NpcInstance npc) {

        return npc.isDestroyed()
                || npc.isDead()
                || npc.getCurrentHp() <= 0
                || npc.getHiddenStatus()
                        != L1NpcInstance.HIDDEN_STATUS_NONE;
    }

    private static final class DeathSyncTimer implements Runnable {

        private final L1NpcInstance npc;

        private DeathSyncTimer(final L1NpcInstance npc) {
            this.npc = npc;
        }

        private void schedule(final long delay) {
            ThreadPool.getInstance().scheduleRequired(this, delay);
        }

        @Override
        public void run() {
            if (npc.isDeathProcessing()) {
                schedule(npc.getSleepTime());
                return;
            }

            npc.allTargetClear();
            npc.setAiRunning(false);
        }
    }
}
