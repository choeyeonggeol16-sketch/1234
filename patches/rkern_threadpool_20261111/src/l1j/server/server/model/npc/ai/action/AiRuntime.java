package l1j.server.server.model.npc.ai.action;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1MobSkillUse;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.npc.ai.NpcServer;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.HateEvent;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/**
 * NPC AI decision/event runtime.
 *
 * NC-style split:
 * - this class owns event/hate drain and next-action selection only.
 * - CCreatureController/Action execution is submitted to the NPC's fixed CIO owner.
 * - active action cadence is no longer driven by AiEngine polling.
 *
 * Existing L1 target/action priority is intentionally preserved.
 */
public final class AiRuntime {

    private static final Logger LOG =
            Logger.getLogger(AiRuntime.class.getName());

    static final long BASE_TICK_MS = 100L;

    static final byte PRE_EXECUTE = 0;
    static final byte PRE_WAIT = 1;
    static final byte PRE_RETHINK = 2;
    static final byte PRE_STOP = 3;

    private AiRuntime() {
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
                    drainHate(npc, nowMs);

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

            // Preserve the original AiRuntime deadline gate (including startAI).
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
     * The old AiRuntime ran these checks before every action tick. They remain
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

        if (npc.checkCondition()) {
            /*
             * Legacy process() returned without executing the controller and
             * waited getSleepTime() before the next THINK. Keep that delay.
             */
            CCreatureController.think(npc);
            return PRE_WAIT;
        }

        if (npc._special != null
                && npc._special.tick(npc)) {
            return PRE_WAIT;
        }

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

        // L1's home-return teleport happens in preflight, before MOVE_AROUND
        // takes its movement tick. A saved wander goal from the old location
        // must not survive this relocation or it will pull the NPC back out.
        final byte runningAction = ActionAccess.currentAction(npc);
        final boolean wasWandering = npc instanceof L1MonsterInstance
                && runningAction == CCreatureController.ACTION_MOVE_AROUND_NATIVE;
        final int beforeTeleportX = wasWandering ? npc.getX() : 0;
        final int beforeTeleportY = wasWandering ? npc.getY() : 0;
        final int beforeTeleportMap = wasWandering ? npc.getMapId() : 0;
        npc.mobTeleport();
        npc.onItemUse();

        if (wasWandering
                && (npc.getX() != beforeTeleportX
                        || npc.getY() != beforeTeleportY
                        || npc.getMapId() != beforeTeleportMap)) {
            // THINK -> transitionFrom() -> onInterrupted() clears the stale
            // MOVE_AROUND destination exactly once, on the same CIO owner.
            CCreatureController.think(npc);
            return PRE_RETHINK;
        }

        if (npc instanceof L1MonsterInstance
                && acceptsCombatRetarget(runningAction)) {

            final L1Character nextTarget = npc.npcChaseRetargetConsume();
            if (nextTarget != null
                    && nextTarget != npc._target
                    && !nextTarget.isDead()
                    && nextTarget.getCurrentHp() > 0
                    && nextTarget.getMapId() == npc.getMapId()
                    && (!nextTarget.isInvisble()
                            || npc.getNpcTemplate().is_agrocoi())
                    && ((L1MonsterInstance) npc)._desireBlockedAttackTargetId
                            != nextTarget.getId()) {
                npc._target = nextTarget;
                CCreatureController.think(npc);
                return PRE_RETHINK;
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
                    ActionAccess.currentAction(npc);

            if (currentAction
                    == CCreatureController.ACTION_MOVE_AROUND_NATIVE
                    && (!canMoveAround(npc)
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
                    ActionAccess.currentAction(npc);

            final boolean retailMoveAround =
                    npc instanceof L1MonsterInstance
                    && currentAction
                            == CCreatureController.ACTION_MOVE_AROUND_NATIVE
                    && CDesirePQ.isMoveAroundTop(
                            (L1MonsterInstance) npc,
                            nowMs);

            if (!retailMoveAround
                    && !CCreatureController.isCombatAction(
                            currentAction)) {

                CCreatureController.think(npc);
                return PRE_RETHINK;
            }

        } else if (CCreatureController.isCombatAction(
                ActionAccess.currentAction(npc))) {

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

        if (npc._special != null
                && npc._special.tick(npc)) {
            return PRE_WAIT;
        }

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

        npc.mobTeleport();
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
     * priority/order. Only the mutation point changes: AiRuntime submits a
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
                    // The old code could fall back to a stale _target here,
                    // even though the PQ no longer had a runnable ATTACK.
                    // Only take MOVE_AROUND if this NPC is allowed to roam.
                    if (moveAroundRunnable
                            && CDesirePQ.isMoveAroundTop(monster, nowMs)) {
                        CCreatureController.submit(
                                creature,
                                CCreatureController.ACTION_MOVE_AROUND_NATIVE);
                        return;
                    }
                } else {
                    creature._target = target;
                    CDesirePQ.leaveMoveAround(monster);
                }
            }
        }

        if (target != null) {
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

        creature.tickItemSearchCooldown();
        creature.checkTargetItem();

        if (creature.isPickupItem()) {
            if (ActionAccess.targetItem(creature) == null) {
                creature.searchTargetItem();
            }

            if (ActionAccess.targetItem(creature) != null) {
                CCreatureController.submit(
                        creature,
                        CCreatureController.ACTION_GET_ITEM);
                return;
            }
        }

        /*
         * Preserve subclass-specific noTarget() policy.
         * Guards must not fall through to generic ACTION_MOVE while idle.
         */
        if (creature instanceof L1CompanionInstance
                || creature instanceof L1GuardInstance
                || creature instanceof L1CastleGuardInstance) {
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

            final L1MobGroupInfo group =
                    creature.getMobGroupInfo();

            if (group != null
                    && !group.isLeader(creature)) {

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
        npc.setAiRunning(false);

        ThreadPool.getInstance().scheduleRequired(
                new DeathSyncTimer(npc),
                0);

        NpcServer.getInstance().unregister(npc);
    }

    private static boolean drainHate(
            final L1NpcInstance npc,
            final long nowMs) {

        HateEvent event;
        boolean changed = false;
        boolean realHateChanged = false;
        boolean attackDesireChanged = false;
        boolean sightDesireChanged = false;
        boolean interruptMoveAround = false;
        boolean invisibilityRetarget = false;

        /*
         * Native CNewAction owns its target for the lifetime of the action.
         * New desire/hate events may change the PQ while an action is running,
         * but they do not silently replace that action's target mid-tick.
         */
        final boolean actionOwnsTarget =
                npc.npcActionOwnsCadence()
                && CCreatureController.isCombatAction(
                        ActionAccess.currentAction(npc));

        // The retail seven-second lifetime gate belongs to CLAN_ATTACKED,
        // not to SEE_CREATURE. Never retain a one-shot sight event for 7s.
        while ((event = npc._hateQueue.poll()) != null) {
            try {
                final L1Character cha = event.cha;

                if (cha == null) {
                    continue;
                }

                final int eventValue = event.hate;

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

                if (eventValue == L1NpcInstance.HATE_EVENT_INVISIBILITY) {
                    if (!(npc instanceof L1MonsterInstance)
                            || cha != npc._target
                            || !cha.isInvisble()) {
                        continue;
                    }

                    final L1MonsterInstance monster =
                            (L1MonsterInstance) npc;
                    final L1Character alternate =
                            NpcEventFactory.findInvisibilityRetarget(
                                    monster, cha);

                    if (alternate == null
                            && monster.getNpcTemplate().is_agrocoi()) {
                        continue;
                    }

                    if (alternate != null
                            && !CDesirePQ.prioritizeMoveAroundAttacker(
                                    monster, alternate, nowMs)) {
                        continue;
                    }

                    CDesirePQ.removeAttack(monster, cha);
                    CDesirePQ.releaseBlockedAttack(monster);
                    npc._hateList.remove(cha);

                    if (alternate != null) {
                        if (!npc._hateList.containsKey(alternate)) {
                            npc._hateList.add(alternate, 0);
                        }
                        npc._target = alternate;
                    } else {
                        npc._target = null;
                        npc.checkTarget();
                    }

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
                        "[NpcAI] drainHate 연산 중 에러 발생 (NPC ID: "
                                + npc.getNpcId() + ")",
                        t);

            } finally {
                HateEvent.release(event);
            }
        }

        // A successful SEE is a new desire. If MOVE_AROUND owns the CIO
        // deadline (including its six-second rest), ask that owner to arbitrate
        // immediately. acceptFromAi() retains the wander action when its
        // desire is still top, so lower-priority sight never resets a rest.
        if (interruptMoveAround || invisibilityRetarget
                || (sightDesireChanged
                        && ActionAccess.currentAction(npc)
                                == CCreatureController.ACTION_MOVE_AROUND_NATIVE)) {
            CCreatureController.submit(
                    npc,
                    CCreatureController.ACTION_THINK);
        }

        if (attackDesireChanged && actionOwnsTarget
                && npc instanceof L1MonsterInstance) {
            final byte action = ActionAccess.currentAction(npc);
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
                || ActionAccess.currentAction(npc)
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
