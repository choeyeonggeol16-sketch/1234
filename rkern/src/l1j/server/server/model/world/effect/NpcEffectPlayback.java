package l1j.server.server.model.world.effect;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.ObjIntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class NpcEffectPlayback implements Runnable {
    private static final Logger LOG = Logger.getLogger(NpcEffectPlayback.class.getName());

    private L1NpcInstance npc;
    private int remaining;
    private final int effectId;
    private final long delayMs;
    private final ObjIntConsumer<L1NpcInstance> broadcast;
    private final Runnable nextTick = this::submit;

    private NpcEffectPlayback(L1NpcInstance npc, int effectId, int count, long delayMs,
            ObjIntConsumer<L1NpcInstance> broadcast) {
        this.npc = npc;
        this.effectId = effectId;
        remaining = count;
        this.delayMs = Math.max(0L, delayMs);
        this.broadcast = broadcast;
    }

    static void action(L1NpcInstance npc, int actionId, int count, long delayMs) {
        if (npc != null && count > 0) {
            new NpcEffectPlayback(npc, actionId, count, delayMs, SC_DoActionGFX::broadcast).submit();
        }
    }

    static void sound(L1NpcInstance npc, int soundId, int count, long delayMs) {
        if (npc != null && count > 0) {
            new NpcEffectPlayback(npc, soundId, count, delayMs, SC_Sound::broadcast).submit();
        }
    }

    private void submit() {
        try {
            TaskLane.WORLD.execute(this);
        } catch (RejectedExecutionException rejected) {
            npc = null;
        }
    }

    @Override public void run() {
        final L1NpcInstance actor = npc;
        if (actor == null) return;
        try {
            broadcast.accept(actor, effectId);
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "NPC repeated effect failed", failure);
        }
        if (--remaining > 0 && TaskLane.WORLD.schedule(nextTick, delayMs) != null) return;
        npc = null;
    }
}
