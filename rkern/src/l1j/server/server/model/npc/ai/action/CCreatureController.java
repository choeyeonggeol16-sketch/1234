package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.npc.ai.NpcServer;

/**
 * NPC creature action controller.
 *
 * reference design ownership split:
 * - AiRuntime chooses the next primitive action id.
 * - this controller executes only on the NPC's fixed CIO owner.
 * - current/pending state stays on L1NpcInstance; no per-NPC controller object.
 * - action cadence is driven by CIO shared wheel, not AiEngine polling.
 */
public final class CCreatureController {

    public static final byte ACTION_NONE           = -1;
    public static final byte ACTION_THINK          = 0;
    public static final byte ACTION_STAND          = 1;
    public static final byte ACTION_MOVE           = 2;
    public static final byte ACTION_FOLLOW_MASTER  = 3;
    public static final byte ACTION_FOLLOW_GROUP   = 4;
    public static final byte ACTION_GET_ITEM       = 5;
    public static final byte ACTION_MOVE_TO_TARGET = 6;
    public static final byte ACTION_ATTACK         = 7;
    public static final byte ACTION_ESCAPE         = 8;
    public static final byte ACTION_NOTHING        = 9;
    public static final byte ACTION_COMPANION      = 10;
    public static final byte ACTION_MOVE_TO_TARGET_WAIT = 11;
    public static final byte ACTION_MOVE_AROUND_NATIVE  = 12;
    public static final byte ACTION_USE_SKILL = 13;
    public static final byte ACTION_MOVE_TO_TARGET_RELATIVE = 14;

    public static final byte RESULT_RUNNING = 0;
    public static final byte RESULT_DONE    = 1;
    public static final byte RESULT_BLOCKED = 2;
    public static final byte RESULT_INVALID = 3;
    public static final byte RESULT_STOP    = 4;

    private static final long BASE_TICK_MS = 100L;

    private CCreatureController() {
    }

    // ---------------------------------------------------------------------
    // AI -> CIO request entry
    // ---------------------------------------------------------------------

    public static void submit(
            final L1NpcInstance creature,
            final byte action) {

        submit(creature, action, ACTION_NONE);
    }

    public static void submit(
            final L1NpcInstance creature,
            final byte action,
            final byte pendingAction) {

        if (creature == null) {
            return;
        }

        /*
         * Claim the current action deadline before crossing threads. This keeps
         * a second external AI wake from submitting another immediate action
         * before the CIO owner has consumed this command.
         */
        creature.npcActionClaimCadence();

        CIOThreadCallback.getInstance()
                .submitNpcAction(
                        creature,
                        action,
                        pendingAction);
    }

    /**
     * CIO owner only. Applies an AI decision and immediately performs the same
     * first action tick the old AiRuntime -> CCreatureController.tick path did.
     */
    public static void acceptFromAi(
            final L1NpcInstance creature,
            final byte action,
            final byte pendingAction) {

        if (creature == null) {
            return;
        }
        if (!creature.aiEngineIsRunning()) {
            creature.npcActionReleaseCadence();
            return;
        }

        if (action == ACTION_THINK
                && ActionAccess.currentAction(creature)
                        == ACTION_MOVE_AROUND_NATIVE) {
            // A THINK wake does not necessarily mean a higher desire won.
            // Retain the in-flight random segment/rest when MOVE_AROUND is
            // still the highest runnable desire. Otherwise its 6-second
            // deadline gets silently replaced by a new immediate walk.
            if (creature instanceof L1MonsterInstance) {
                final L1MonsterInstance monster =
                        (L1MonsterInstance) creature;
                if (CDesirePQ.isActive(monster)
                        && CDesirePQ.isMoveAroundTop(
                                monster, System.currentTimeMillis())) {
                    return;
                }
            }
            creature.npcActionInvalidateGeneration();
            CMoveAroundAction.onEnd(creature, RESULT_DONE);
            creature.npcActionReleaseCadence();
            creature._nextAllowedTickMs = System.currentTimeMillis();
            NpcServer.getInstance().wake(creature);
            return;
        }

        final int generation = creature.npcActionNextGeneration();

        applySubmittedAction(
                creature,
                action,
                pendingAction);

        runOwnerTick(
                creature,
                generation,
                System.currentTimeMillis(),
                false);
    }

    /**
     * CIO shared-wheel callback. Stale generations are ignored in O(1).
     */
    public static void TimerExpired(
            final L1NpcInstance creature,
            final int generation) {

        if (creature == null
                || generation != creature.npcActionGeneration()
                || !creature.aiEngineIsRunning()) {
            return;
        }

        if (ActionAccess.currentAction(creature) == ACTION_THINK) {
            creature.npcActionReleaseCadence();
            creature._nextAllowedTickMs = System.currentTimeMillis();
            NpcServer.getInstance().wake(creature);
            return;
        }

        runOwnerTick(
                creature,
                generation,
                System.currentTimeMillis(),
                true);
    }

    /** Compatibility entry. Execution only; no action selection here. */
    public static boolean tick(
            final L1NpcInstance creature,
            final long nowMs) {

        if (creature == null) {
            return true;
        }

        return executeAction(creature, nowMs);
    }

    private static void runOwnerTick(
            final L1NpcInstance creature,
            final int generation,
            final long nowMs,
            final boolean runLegacyPreflight) {

        if (creature == null
                || generation != creature.npcActionGeneration()
                || !creature.aiEngineIsRunning()) {
            return;
        }

        if (SetMoving.debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-OWNER] t=" + nowMs
                    + " actor=" + creature.getId()
                    + " action=" + ActionAccess.currentAction(creature)
                    + " preflight=" + runLegacyPreflight
                    + " sleepBefore=" + creature.getSleepTime()
                    + " pos=" + creature.getX() + "," + creature.getY());
        }

        if (runLegacyPreflight) {
            final byte pre = AiRuntime.beforeActionTick(creature, nowMs);

            if (pre == AiRuntime.PRE_STOP) {
                creature.npcActionInvalidateGeneration();
                AiRuntime.stopFromController(creature);
                return;
            }

            if (pre == AiRuntime.PRE_WAIT) {
                armNext(creature, generation, nowMs);
                return;
            }

            if (pre == AiRuntime.PRE_RETHINK) {
                creature.npcActionReleaseCadence();
                creature._nextAllowedTickMs = nowMs;
                NpcServer.getInstance().wake(creature);
                return;
            }
        } else {
            // First action tick still respects async cast/freeze/paralysis gates.
            if (creature._castingSkill.get()) {
                creature.setSleepTime(200L);
                armNext(creature, generation, nowMs);
                return;
            }

            final int paralysisTime = creature.getParalysisTime();
            if (paralysisTime > 0) {
                creature.setSleepTime(paralysisTime);
                creature.setParalysisTime(0);
                creature.setParalyzed(false);
                armNext(creature, generation, nowMs);
                return;
            }

            if (creature.isParalyzed()
                    || creature.isSleeped()
                    || creature.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {

                creature.setSleepTime(200L);
                armNext(creature, generation, nowMs);
                return;
            }
        }

        if (ActionAccess.currentAction(creature) == ACTION_THINK) {
            armNext(creature, generation, nowMs);
            return;
        }

        final boolean stop = executeAction(creature, nowMs);

        if (stop) {
            creature.npcActionInvalidateGeneration();
            creature.npcActionReleaseCadence();
            AiRuntime.stopFromController(creature);
            return;
        }

        armNext(creature, generation, nowMs);
    }

    private static void armNext(
            final L1NpcInstance creature,
            final int generation,
            final long nowMs) {

        if (creature == null
                || generation != creature.npcActionGeneration()
                || !creature.aiEngineIsRunning()) {
            return;
        }

        final long delay =
                Math.max(
                        creature.getSleepTime(),
                        BASE_TICK_MS);

        if (SetMoving.debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-SCHED] t=" + nowMs
                    + " actor=" + creature.getId()
                    + " action=" + ActionAccess.currentAction(creature)
                    + " sleep=" + creature.getSleepTime()
                    + " delay=" + delay
                    + " pos=" + creature.getX() + "," + creature.getY());
        }

        // Keep the legacy visible deadline for event-side cooldown gating.
        creature._nextAllowedTickMs = nowMs + delay;

        CIOThreadCallback.getInstance()
                .scheduleNpcActionTimer(
                        creature,
                        generation,
                        delay);
    }

    private static void applySubmittedAction(
            final L1NpcInstance creature,
            final byte action,
            final byte pendingAction) {

        switch (action) {
        case ACTION_USE_SKILL:
            set(creature, ACTION_USE_SKILL);
            return;

        case ACTION_ATTACK:
            CAttackAction.set(creature);
            return;

        case ACTION_MOVE_TO_TARGET:
            CMoveToTargetAction.set(
                    creature,
                    pendingAction);
            return;

        case ACTION_MOVE_TO_TARGET_RELATIVE:
            CMoveToTargetRelativeAction.set(creature, creature.getTarget(), pendingAction);
            return;

        case ACTION_MOVE_TO_TARGET_WAIT:
            CMoveToTargetWaitAction.set(
                    creature,
                    creature.getTarget(),
                    Math.max(1, creature.getNpcTemplate().get_ranged()),
                    pendingAction);
            return;

        case ACTION_MOVE_AROUND_NATIVE:
            CMoveAroundAction.set(creature);
            return;

        case ACTION_ESCAPE:
            CEscapeAction.set(creature);
            return;

        case ACTION_GET_ITEM:
            CGetItemAction.set(creature);
            return;

        case ACTION_FOLLOW_MASTER:
        case ACTION_FOLLOW_GROUP:
        case ACTION_MOVE:
            CMoveAction.set(creature, action);
            return;

        case ACTION_STAND:
            CStandAction.set(creature);
            return;

        case ACTION_NOTHING:
            CNothingAction.set(creature);
            return;

        case ACTION_COMPANION:
            set(creature, ACTION_COMPANION);
            return;

        case ACTION_THINK:
        default:
            think(creature);
            return;
        }
    }

    // ---------------------------------------------------------------------
    // Owner-side action execution
    // ---------------------------------------------------------------------

    private static boolean executeAction(
            final L1NpcInstance creature,
            final long nowMs) {

        switch (ActionAccess.currentAction(creature)) {
        case ACTION_ATTACK:
        case ACTION_MOVE_TO_TARGET:
        case ACTION_MOVE_TO_TARGET_RELATIVE:
        case ACTION_MOVE_TO_TARGET_WAIT:
        case ACTION_ESCAPE:
            creature.onTarget();
            return false;

        case ACTION_USE_SKILL:
            CSkillAction.onDesireTick(creature);
            return false;

        case ACTION_MOVE_AROUND_NATIVE: {
            final byte result =
                    CMoveAroundAction.onTick(creature);

            if (result != RESULT_RUNNING) {
                CMoveAroundAction.onEnd(
                        creature,
                        result);
            }
            return false;
        }

        case ACTION_GET_ITEM: {
            final byte result = CGetItemAction.onTick(creature);

            if (result != RESULT_RUNNING) {
                CGetItemAction.onEnd(
                        creature,
                        result);
            }
            return false;
        }

        case ACTION_FOLLOW_MASTER:
        case ACTION_FOLLOW_GROUP:
        case ACTION_MOVE: {
            final byte current = ActionAccess.currentAction(creature);
            final byte result = CMoveAction.onTick(
                    creature,
                    current);

            if (result != RESULT_RUNNING) {
                CMoveAction.onEnd(
                        creature,
                        current,
                        result);
            }

            return result == RESULT_INVALID;
        }

        case ACTION_STAND: {
            final byte result = CStandAction.onTick(creature);

            if (result != RESULT_RUNNING) {
                CStandAction.onEnd(
                        creature,
                        result);
            }
            return false;
        }

        case ACTION_NOTHING: {
            final byte result = CNothingAction.onTick(creature);

            if (result != RESULT_RUNNING) {
                CNothingAction.onEnd(
                        creature,
                        result);
            }

            return result == RESULT_STOP
                    || result == RESULT_INVALID;
        }

        case ACTION_COMPANION:
            /*
             * Preserve the original virtual contract exactly, but execute it
             * on the CIO owner so its movement/item side effects are serialized
             * with every other NPC action.
             */
            if (creature.noTarget()) {

                /*
                 * Guard reaction race:
                 *
                 * Guard / CastleGuard can receive damage while this idle
                 * ACTION_COMPANION tick is about to transition into
                 * CNothingAction(STOP). receiveDamage() has already queued the
                 * attacker in the hate queue, but STOP here would unregister
                 * the AI before that queued hate is drained.
                 *
                 * Do not change ordinary NPC/monster idle behavior. Only the
                 * two guard classes cancel this STOP when a hate event is
                 * already pending, then return to THINK so AiRuntime drains it
                 * and selects MOVE_TO_TARGET / ATTACK normally.
                 */
                if ((creature instanceof L1GuardInstance
                        || creature instanceof L1CastleGuardInstance)
                        && creature.hasPendingHate()) {

                    think(creature);
                    return false;
                }

                CNothingAction.set(creature);

                final byte result = CNothingAction.onTick(creature);

                /*
                 * Close the same race once more after CNothingAction was
                 * selected. This covers damage queued between noTarget() and
                 * the STOP result without changing the guard's normal idle
                 * STOP semantics when there is no event.
                 */
                if ((creature instanceof L1GuardInstance
                        || creature instanceof L1CastleGuardInstance)
                        && creature.hasPendingHate()) {

                    think(creature);
                    return false;
                }

                if (result != RESULT_RUNNING) {
                    CNothingAction.onEnd(creature, result);
                }

                return result == RESULT_STOP
                        || result == RESULT_INVALID;
            }

            think(creature);
            return false;

        case ACTION_THINK:
        default:
            return false;
        }
    }

    public static void executeTargetAction(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null || target == null) {
            think(creature);
            return;
        }
        // The first CIO tick skips legacy preflight. Apply the same actor
        // gate before any CHASE/ESCAPE/ATTACK ability or movement side effect.
        if (creature.isDead()
                || creature.getCurrentHp() <= 0
                || creature.getHiddenStatus()
                        != L1NpcInstance.HIDDEN_STATUS_NONE) {
            think(creature);
            return;
        }

        // A target may have been replaced after onTarget() captured it.
        // Never run a combat/movement primitive against the obsolete target.
        // The current owner action and its pending choice are left intact;
        // the next owner tick will consume the current target.
        if (creature.getTarget() != target) {
            return;
        }

        final byte action = ActionAccess.currentAction(creature);

        if (action == ACTION_ESCAPE) {
            executeEscapeAction(creature, target);
            return;
        }

        if (!creature.isActionable() && creature.isMovable()) {
            CEscapeAction.set(creature);
            executeEscapeAction(creature, target);
            return;
        }

        if (action == ACTION_ATTACK) {
            executeAttackAction(creature, target);
            return;
        }

        if (action == ACTION_MOVE_TO_TARGET) {
            executeMoveToTargetAction(creature, target, true);
            return;
        }

        if (action == ACTION_MOVE_TO_TARGET_RELATIVE) {
            CMoveToTargetRelativeAction.tick(creature, target);
            return;
        }

        if (action == ACTION_MOVE_TO_TARGET_WAIT) {
            executeMoveToTargetWaitAction(creature, target);
            return;
        }

        CMoveToTargetAction.set(creature, ACTION_ATTACK);
        executeMoveToTargetAction(creature, target, true);
    }

    private static void executeEscapeAction(
            final L1NpcInstance creature,
            final L1Character target) {

        final byte result = CEscapeAction.onTick(creature, target);

        if (result != RESULT_RUNNING) {
            CEscapeAction.onEnd(
                    creature,
                    target,
                    result);
        }
    }

    private static void executeAttackAction(
            final L1NpcInstance creature,
            final L1Character target) {

        final byte result = CAttackAction.onTick(creature, target);

        if (result != RESULT_RUNNING) {
            CAttackAction.onEnd(
                    creature,
                    target,
                    result);
        }
    }

    private static void executeMoveToTargetAction(
            final L1NpcInstance creature,
            final L1Character target,
            final boolean tryCombatAbility) {

        final byte result = CMoveToTargetAction.onTick(
                creature,
                target,
                tryCombatAbility);

        if (result != RESULT_RUNNING) {
            CMoveToTargetAction.onEnd(
                    creature,
                    target,
                    result);
        }
    }

    private static void executeMoveToTargetWaitAction(
            final L1NpcInstance creature,
            final L1Character target) {

        final byte result =
                CMoveToTargetWaitAction.onTick(
                        creature,
                        target);

        if (result != RESULT_RUNNING) {
            CMoveToTargetWaitAction.onEnd(
                    creature,
                    target,
                    result);
        }
    }

    public static void set(
            final L1NpcInstance creature,
            final byte action) {

        if (creature == null) {
            return;
        }

        transitionFrom(creature, action);
        ActionAccess.currentAction(creature, action);
        ActionAccess.pendingAction(creature, ACTION_NONE);
    }

    public static void set(
            final L1NpcInstance creature,
            final byte action,
            final byte pendingAction) {

        if (creature == null) {
            return;
        }

        transitionFrom(creature, action);
        ActionAccess.currentAction(creature, action);
        ActionAccess.pendingAction(creature, pendingAction);
    }

    private static void transitionFrom(
            final L1NpcInstance creature,
            final byte nextAction) {

        final byte previous = ActionAccess.currentAction(creature);
        if (previous == nextAction) {
            return;
        }
        if (previous == ACTION_MOVE_TO_TARGET
                || previous == ACTION_MOVE_TO_TARGET_RELATIVE) {
            // WAIT retains movement path state, but never an attack-slot claim.
            CMoveToTargetAction.onInterrupted(
                    creature,
                    nextAction == ACTION_MOVE_TO_TARGET_WAIT);
            if (previous == ACTION_MOVE_TO_TARGET_RELATIVE) {
                CMoveToTargetRelativeAction.clear(creature);
            }
        } else if (previous == ACTION_MOVE_AROUND_NATIVE) {
            // Preserve the reserved ring hand-off to the new chase action.
            CMoveAroundAction.onInterrupted(creature);
        } else if (previous == ACTION_MOVE_TO_TARGET_WAIT) {
            CMoveToTargetWaitAction.clear(creature);
        }
    }

    public static void doPendingAction(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        final byte pending = ActionAccess.pendingAction(creature);

        if (pending != ACTION_NONE) {
            ActionAccess.pendingAction(creature, ACTION_NONE);

            if (pending == ACTION_ATTACK) {
                CAttackAction.set(creature);
            } else {
                set(creature, pending);
            }
            return;
        }

        think(creature);
    }

    public static void think(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        transitionFrom(creature, ACTION_THINK);
        ActionAccess.pendingAction(creature, ACTION_NONE);
        ActionAccess.currentAction(creature, ACTION_THINK);
    }


    /**
     * An already-admitted alternate ATTACK can become runnable while the
     * current chase is blocked. Retire the old CIO action generation before
     * waking the existing NPC desire owner; a pending old timer must not
     * re-arm the blocked chase or postpone the new target decision.
     */
    public static void rethinkNow(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }
        think(creature);
        creature.npcActionInvalidateGeneration();
        creature.npcActionReleaseCadence();
        creature._nextAllowedTickMs = System.currentTimeMillis();
        NpcServer.getInstance().wake(creature);
    }

    /**
     * AI stop/delete bridge. Must run on the fixed CIO owner so ThreadLocal
     * movement intent/path state is cleared on the same thread that created it.
     */
    public static void cancelFromAi(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        creature.npcActionInvalidateGeneration();
        creature.npcActionReleaseCadence();

        CMoveToTargetAction.clearBlocked(creature);
        CMoveToTargetWaitAction.clear(creature);
        CMoveToTargetRelativeAction.clear(creature);
        // Cancellation does not invoke transitionFrom(). The normal exit
        // from a random walk does, so mirror its cleanup here before this
        // NPC can be reactivated or have its object id reused.
        if (ActionAccess.currentAction(creature) == ACTION_MOVE_AROUND_NATIVE) {
            CMoveAroundAction.onInterrupted(creature);
        } else {
            SetMoving.clear(creature);
        }
        ActionAccess.pendingAction(creature, ACTION_NONE);
        ActionAccess.currentAction(creature, ACTION_THINK);
    }

    public static boolean isCombatAction(final byte action) {
        return action == ACTION_MOVE_TO_TARGET
                || action == ACTION_MOVE_TO_TARGET_RELATIVE
                || action == ACTION_MOVE_TO_TARGET_WAIT
                || action == ACTION_ATTACK
                || action == ACTION_USE_SKILL
                || action == ACTION_ESCAPE;
    }
}
