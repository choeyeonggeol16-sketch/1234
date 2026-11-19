package l1j.server.server.Controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.L1UltimateBattle;

public final class UbTimeController implements Runnable {
    public static final int SLEEP_TIME = 15000;
    private static final Logger LOG = Logger.getLogger(UbTimeController.class.getName());

    private static final class Holder {
        static final UbTimeController INSTANCE = new UbTimeController();
    }

    public static UbTimeController getInstance() {
        return Holder.INSTANCE;
    }

    private UbTimeController() { }

    // Multiple scheduler/GM calls must not start the same UB twice.
    @Override
    public synchronized void run() {
        try {
            final var battles = UBTable.getInstance().getAllUb();
            if (battles == null) return;
            for (L1UltimateBattle ub : battles) {
                if (ub == null) continue;
                try {
                    if (ub.checkUbTime() && !ub.isActive()) {
                        ub.start();
                    }
                } catch (Exception failure) {
                    // A broken event must not suppress unrelated UB starts.
                    LOG.log(Level.WARNING, "UB time check failed", failure);
                }
            }
        } catch (Exception failure) {
            // A transient table lookup failure must not cancel a fixed-rate
            // scheduler by escaping the periodic Runnable.
            LOG.log(Level.WARNING, "UB time dispatch failed", failure);
        }
    }
}
