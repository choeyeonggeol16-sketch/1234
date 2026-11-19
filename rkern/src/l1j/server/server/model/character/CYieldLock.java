package l1j.server.server.model.character;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class CYieldLock {
    private static final VarHandle OWNER;

    static {
        try {
            OWNER = MethodHandles.lookup().findVarHandle(CYieldLock.class, "owner", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile Thread owner;
    private int depth;

    public void lock() {
        final Thread current = Thread.currentThread();
        if (owner == current) {
            ++depth;
            return;
        }
        int spins = 0;
        while (!OWNER.compareAndSet(this, null, current)) {
            if ((++spins & 63) == 0) {
                Thread.yield();
            } else {
                Thread.onSpinWait();
            }
        }
        depth = 1;
    }

    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    public void unlock() {
        if (owner != Thread.currentThread() || depth == 0) {
            throw new IllegalMonitorStateException("CYieldLock not owned by current thread");
        }
        if (--depth == 0) {
            OWNER.setRelease(this, null);
        }
    }
}
