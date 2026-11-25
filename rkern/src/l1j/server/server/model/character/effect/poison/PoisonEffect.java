package l1j.server.server.model.character.effect.poison;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/**
 * One target-owned poison lifetime. CIO owns deadlines; the existing PC
 * controller / NPC owner executes start, damage, phase changes and cure.
 * One effect instance is reused for every timer expiration.
 */
public abstract class PoisonEffect implements CIOThreadCallback.WheelTask {
    private static final Logger LOG = Logger.getLogger(PoisonEffect.class.getName());
    protected static final long KEEP = -1L;
    protected static final long STOP = -2L;
    private static final int[] IMMUNE_ITEMS = {
        20298, 20117, 22196, 22197, 22198, 22199, 123111, 123112, 123113
    };

    protected final L1Character target;
    private final int sessionStamp;
    private volatile boolean cureRequested;
    private volatile boolean stopped;
    private boolean started;

    protected PoisonEffect(L1Character target) {
        this.target = target;
        this.sessionStamp = target instanceof L1PcInstance pc ? pc.getSessionStamp() : 0;
    }

    public final L1Character target() {
        return target;
    }

    protected static boolean isValidTarget(L1Character target) {
        return target != null && target.getPoison() == null && canInfect(target);
    }

    private static boolean canInfect(L1Character target) {
        if (!(target instanceof L1PcInstance player)) return true;
        if (target.hasSkillEffect(L1SkillId.ICE_LANCE)
                || target.hasSkillEffect(L1SkillId.MOB_COCA)
                || target.hasSkillEffect(L1SkillId.MOB_BASILL)
                || target.hasSkillEffect(L1SkillId.EARTH_BIND)
                || player.hasSkillEffect(L1SkillId.VENOM_RESIST)) return false;
        for (int itemId : IMMUNE_ITEMS) {
            if (player.getInventory().IsEquippedByClassID(itemId)) return false;
        }
        return true;
    }

    protected static void sendMessageIfPlayer(L1Character target, int messageId) {
        if (target instanceof L1PcInstance player) SC_ServerMessage.send(player, messageId);
    }

    /** Reserve one poison slot before posting to the character owner. */
    protected final boolean install() {
        if (!isValidTarget(target) || !target.claimPoison(this)) return false;
        if (target instanceof L1PcInstance || target instanceof L1NpcInstance) {
            if (!CIOThreadCallback.getInstance().postPoisonOnOwner(this)
                    && !CIOThreadCallback.getInstance().schedulePoisonTick(this, 50L)) {
                target.clearPoisonIfSame(this);
                stopped = true;
                return false;
            }
        } else {
            // Test/base-character objects have no PC/NPC execution owner.
            onOwner();
        }
        return target.getPoison() == this;
    }

    protected abstract long onApply();
    protected long onTick() { return KEEP; }
    protected abstract void onCure();

    private boolean staleTarget() {
        if (target instanceof L1PcInstance pc) {
            return pc.getSessionStamp() != sessionStamp
                    || pc.getCreatureController() == null
                    || pc.getCreatureController().actor() != pc;
        }
        return target instanceof L1NpcInstance npc && npc.isDestroyed();
    }

    @Override
    public final void run() {
        if (isCancelled()) return;
        if (staleTarget()) {
            stopped = true;
            target.clearPoisonIfSame(this);
            return;
        }
        if (!CIOThreadCallback.getInstance().postPoisonOnOwner(this)
                && !CIOThreadCallback.getInstance().schedulePoisonTick(this, 50L)) {
            // No owner remains to execute cleanup; never keep a poison reserved.
            stopped = true;
            target.clearPoisonIfSame(this);
        }
    }

    @Override
    public final void runOnController(L1PcInstance pc) {
        if (target == pc) onOwner();
    }

    @Override
    public final void runOnNpc(L1NpcInstance npc) {
        if (target == npc) onOwner();
    }

    /** Invoked by the existing character owner, or by an ownerless test object. */
    public final void onOwner() {
        if (stopped || target.getPoison() != this) return;
        if (staleTarget()) {
            stopped = true;
            target.clearPoisonIfSame(this);
            return;
        }
        if (cureRequested) {
            finish();
            return;
        }
        try {
            final long next;
            if (!started) {
                if (!canInfect(target)) {
                    stopped = true;
                    target.clearPoisonIfSame(this);
                    return;
                }
                started = true;
                next = onApply();
            } else {
                next = onTick();
            }
            if (cureRequested || next == STOP) {
                finish();
            } else if (next > 0L && !CIOThreadCallback.getInstance().schedulePoisonTick(this, next)) {
                finish();
            }
        } catch (Throwable failure) {
            finish();
            LOG.log(Level.WARNING, "Poison owner callback failed", failure);
        }
    }

    private void finish() {
        if (stopped) return;
        stopped = true;
        try {
            if (started && target.getPoison() == this) onCure();
        } finally {
            target.clearPoisonIfSame(this);
        }
    }

    @Override
    public final boolean isCancelled() {
        return stopped || target.getPoison() != this;
    }

    /** Cure is posted to the same owner sequence as infection and damage. */
    public final void cure() {
        if (isCancelled() || cureRequested) return;
        cureRequested = true;
        if (!(target instanceof L1PcInstance || target instanceof L1NpcInstance)) {
            onOwner();
        } else if (!CIOThreadCallback.getInstance().postPoisonOnOwner(this)
                && !CIOThreadCallback.getInstance().schedulePoisonTick(this, 50L)) {
            stopped = true;
            target.clearPoisonIfSame(this);
        }
    }

    public abstract int getEffectId();
}
