package l1j.server.server.storage;

import java.util.concurrent.TimeUnit;

import l1j.server.server.task.COwnedWork;

/** Captures periodic character and inventory snapshots for the CacheD write queue. */
public final class CCharacterSaveService {
    private static final COwnedWork CAPTURE = new COwnedWork("RKern-CharacterSave-",
            Math.clamp(Runtime.getRuntime().availableProcessors(), 2, 8), 8_192, 0);

    private CCharacterSaveService() {
    }

    public static void capture(Runnable snapshot) { CAPTURE.execute(snapshot); }

    public static void stopAccepting() { CAPTURE.stopAccepting(); }

    public static boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
        return CAPTURE.awaitDrained(timeout, unit);
    }
}
