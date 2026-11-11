package l1j.server.AutoHuntSystem;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.network.NativeStepControl;
import l1j.server.server.serverpackets.S_SystemMessage;

/** All calls acquire the player's monitor, also used by Start/EndAutoController. */
public final class AtsUsageTimer implements Runnable {
    public static final class StartDenied extends Exception {
        private static final long serialVersionUID = 1L;
        StartDenied(String message) { super(message); }
    }
    private static final Logger LOG = Logger.getLogger(AtsUsageTimer.class.getName());
    private final L1PcInstance pc;
    private final AtsRules rules;
    private final AtsBudget budget;
    private boolean closed;
    private volatile long expiresNanos;
    private AtsUsageTimer(L1PcInstance pc, AtsRules rules, AtsBudget budget) {
        this.pc = pc; this.rules = rules; this.budget = budget;
    }
    public static AtsUsageTimer start(L1PcInstance pc) throws Exception {
        AtsRules rules = AtsRules.load();
        String reason = rules.rejection(pc.getLevel(), pc.getMapId());
        if (reason != null) throw new StartDenied(reason);
        AtsBudget budget = new AtsBudget(new AtsTimeStore(pc.getId(), rules.initialMillis));
        if (!budget.start(System::nanoTime)) throw new StartDenied("\uc790\ub3d9\uc0ac\ub0e5 \uc794\uc5ec \uc2dc\uac04\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.");
        AtsUsageTimer timer = new AtsUsageTimer(pc, rules, budget);
        long now = System.nanoTime();
        try { timer.expiresNanos = now + budget.remaining(now) * 1_000_000L; }
        catch (Exception failure) { budget.stop(System.nanoTime()); throw failure; }
        return timer;
    }
    public String blockReason() {
        String reason = rules.rejection(pc.getLevel(), pc.getMapId());
        if (reason == null && System.nanoTime() - expiresNanos >= 0) reason = "\uc790\ub3d9\uc0ac\ub0e5 \uc2dc\uac04\uc774 \ubaa8\ub450 \uc18c\uc9c4\ub418\uc5c8\uc2b5\ub2c8\ub2e4.";
        if (reason == null && !NativeStepControl.isBound(pc)) reason = "\uc790\ub3d9\uc0ac\ub0e5 \uc5f0\uacb0\uc774 \uc885\ub8cc\ub418\uc5c8\uc2b5\ub2c8\ub2e4.";
        return reason;
    }
    @Override public void run() {
        synchronized (pc) {
            if (closed || !pc.getAutoHunt()) return;
            try {
                String reason = rules.rejection(pc.getLevel(), pc.getMapId());
                if (reason == null && !NativeStepControl.isBound(pc)) reason = "\uc790\ub3d9\uc0ac\ub0e5 \uc5f0\uacb0\uc774 \uc885\ub8cc\ub418\uc5c8\uc2b5\ub2c8\ub2e4.";
                if (reason == null && pc.isDead()) reason = "\uce90\ub9ad\ud130\uac00 \uc0ac\ub9dd\ud558\uc5ec \uc790\ub3d9\uc0ac\ub0e5\uc744 \uc885\ub8cc\ud569\ub2c8\ub2e4.";
                long sampleNanos = System.nanoTime();
                long millis = budget.remaining(sampleNanos);
                if (reason == null && millis <= 0) reason = "\uc790\ub3d9\uc0ac\ub0e5 \uc2dc\uac04\uc774 \ubaa8\ub450 \uc18c\uc9c4\ub418\uc5c8\uc2b5\ub2c8\ub2e4.";
                if (reason != null) {
                    pc.sendPackets(new S_SystemMessage(reason)); pc.EndAutoController(); return;
                }
                expiresNanos = sampleNanos + millis * 1_000_000L;
                NativeStepControl.sendAts(pc, (millis + 999) / 1000, true);
            } catch (Exception failure) {
                LOG.log(Level.WARNING, "ATS timer failed; stopping auto hunt", failure);
                pc.sendPackets(new S_SystemMessage("\uc2dc\uac04 \uc815\ubcf4\ub97c \uc800\uc7a5\ud560 \uc218 \uc5c6\uc5b4 \uc790\ub3d9\uc0ac\ub0e5\uc744 \uc885\ub8cc\ud569\ub2c8\ub2e4."));
                pc.EndAutoController();
            }
        }
    }
    public void stop() {
        synchronized (pc) {
            if (closed) return; closed = true;
            try { budget.stop(System.nanoTime()); }
            catch (Exception failure) { LOG.log(Level.WARNING, "ATS unused reservation refund failed", failure); }
            try { NativeStepControl.sendAts(pc, 0, false); }
            catch (RuntimeException ignored) { /* HUD I/O must not interrupt movement/timer cleanup. */ }
        }
    }
}
