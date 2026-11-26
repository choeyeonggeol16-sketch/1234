"""Check the actual stale-session gate and fallback when CIO cannot admit a quarantine kick."""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model"


def section(path, start, end):
    source = (src / path).read_text(encoding="utf-8")
    return source[source.index(start):source.index(end)]


socket = section("network/core/connection/CUserSocket.java", "    public void closeIfCurrentCharacter(",
                 "    private void finishClose(boolean graceful)")
actor = section("Instance/L1PcInstance.java", "    public boolean isItemTransferSession(",
                "    public boolean ownsItemTransferToken(")
quarantine = section("Instance/L1PcInstance.java", "    public void quarantineItemTransferSession(",
                     "    public boolean tryBeginItemTransfer()")

fixture = r"""
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

public final class ReceiptQuarantineFallback {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static class CUserSocket {
        static final AtomicIntegerFieldUpdater<CUserSocket> CLOSED =
                AtomicIntegerFieldUpdater.newUpdater(CUserSocket.class, "closed");
        volatile int closed;
        boolean _gameSessionReady = true;
        L1PcInstance actor;
        int closes, kicks;
        boolean isConnected() { return closed == 0; }
        boolean isRetiringItemTransfers() { return closed != 0; }
        void onItemTransferFinished(L1PcInstance pc) { }
        L1PcInstance getActiveChar() { return actor; }
        synchronized void setActor(L1PcInstance actor) { this.actor = actor; }
        void kick() { kicks++; closed = 1; }
        void finishClose(boolean graceful) {
            check(!Thread.holdsLock(this), "normal fallback tears down transport after claiming");
            check(!graceful, "fallback must not wait for a frame it cannot send");
            closes++;
        }
""" + socket + r"""
    }
    static class L1PcInstance {
        int stamp = 1;
        boolean logout, forcedItemTransferRetirement;
        CUserSocket socket;
        L1PcInstance(CUserSocket socket) { this.socket = socket; socket.setActor(this); }
        int getSessionStamp() { return stamp; }
        CUserSocket getNetConnection() { return socket; }
        boolean isLogoutStarted() { return logout; }
""" + actor + quarantine + r"""
    }
    static class CIOThreadCallback {
        static final CIOThreadCallback INSTANCE = new CIOThreadCallback();
        Consumer<Throwable> terminal;
        int mode;
        static CIOThreadCallback getInstance() { return INSTANCE; }
        boolean completeSession(CUserSocket socket, Runnable task, Consumer<Throwable> terminal) {
            if (mode == 0) { task.run(); terminal.accept(null); return true; }
            if (mode == 1) { terminal.accept(new RejectedExecutionException()); return false; }
            if (mode == 2) { this.terminal = terminal; return true; }
            throw new RejectedExecutionException();
        }
    }
    public static void main(String[] args) throws Exception {
        final var owner = CIOThreadCallback.INSTANCE;
        final var live = new CUserSocket();
        new L1PcInstance(live).quarantineItemTransferSession(live, 1);
        check(live.kicks == 1 && live.closes == 0, "accepted CIO path keeps normal kick");

        owner.mode = 1;
        final var rejected = new CUserSocket();
        final var quarantined = new L1PcInstance(rejected);
        quarantined.quarantineItemTransferSession(rejected, 1);
        quarantined.quarantineItemTransferSession(rejected, 1);
        check(!rejected.isConnected() && !rejected._gameSessionReady && rejected.closes == 1,
                "same connection closed exactly once on rejection");

        owner.mode = 2;
        final var replaced = new CUserSocket();
        final var old = new L1PcInstance(replaced);
        old.quarantineItemTransferSession(replaced, 1);
        new L1PcInstance(replaced);
        owner.terminal.accept(new RejectedExecutionException());
        check(replaced.isConnected() && replaced.closes == 0, "old receipt cannot close replacement actor");

        final var reused = new CUserSocket();
        final var actor = new L1PcInstance(reused);
        actor.quarantineItemTransferSession(reused, 1);
        actor.stamp++;
        owner.terminal.accept(new RejectedExecutionException());
        check(reused.isConnected() && reused.closes == 0, "old receipt cannot close a new epoch of same actor");

        final var concurrent = new CUserSocket();
        new L1PcInstance(concurrent).quarantineItemTransferSession(concurrent, 1);
        final Thread fallback = new Thread(() -> owner.terminal.accept(new RejectedExecutionException()));
        synchronized (concurrent) {
            fallback.start();
            final long deadline = System.nanoTime() + 2_000_000_000L;
            while (fallback.getState() == Thread.State.RUNNABLE && System.nanoTime() < deadline)
                Thread.sleep(1);
            check(fallback.getState() == Thread.State.BLOCKED, "fallback waits for socket publication boundary");
            new L1PcInstance(concurrent);
        }
        fallback.join(2000);
        check(!fallback.isAlive() && concurrent.isConnected(), "concurrent identity change prevents stale closure");

        owner.mode = 3;
        final var dispatchFailed = new CUserSocket();
        new L1PcInstance(dispatchFailed).quarantineItemTransferSession(dispatchFailed, 1);
        check(!dispatchFailed.isConnected() && dispatchFailed.closes == 1, "throwing CIO dispatcher also closes exact owner");
        System.out.println("PASS receipt quarantine rejection, dispatch exception, and stale identity");
    }
}
"""

with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / "ReceiptQuarantineFallback.java"
    source.write_text(fixture, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(source)], check=True)
    subprocess.run(["java", "-cp", temp, "ReceiptQuarantineFallback"], check=True)
