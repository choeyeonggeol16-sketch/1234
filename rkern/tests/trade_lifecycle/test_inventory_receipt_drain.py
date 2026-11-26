"""Check admitted inventory replay and its after-COMMIT effect during transport teardown."""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model/inventory"
owner = (src / "InventoryTransactionOwners.java").read_text(encoding="utf-8")
owner = owner[owner.index("final class InventoryTransactionOwners"):].replace(
    "final class InventoryTransactionOwners", "static final class InventoryTransactionOwners", 1)
transaction = (src / "L1InventoryTransaction.java").read_text(encoding="utf-8")
finish = transaction[transaction.index("    private void finishReplay("):
                     transaction.index("    /** The immutable row key")]
replay = transaction[transaction.index("    private void runCurrentOwners("):
                     transaction.index("    private void withOwnersLocked(")]
actor = (src.parent / "Instance/L1PcInstance.java").read_text(encoding="utf-8")
gate = actor[actor.index("    private volatile int itemTransferBusy;"):
             actor.index("    /** Failure callback for an old receipt")]
release = actor[actor.index("    public void endItemTransfer(int token)"):
                actor.index("    public boolean isTradeResolving()")]

fixture = r"""
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

public final class InventoryReceiptDrain {
    static int checks;
    static void check(boolean ok, String description) {
        checks++;
        if (!ok) throw new AssertionError(description);
    }
    static class CWorld {
        static final CWorld INSTANCE = new CWorld();
        final HashMap<Integer,L1PcInstance> actors = new HashMap<>();
        static CWorld getInstance() { return INSTANCE; }
        Object findObject(int id) { return actors.get(id); }
    }
    static class CUserSocket {
        boolean connected = true;
        L1PcInstance actor;
        boolean isConnected() { return connected; }
        boolean isRetiringItemTransfers() { return !connected; }
        L1PcInstance getActiveChar() { return actor; }
        void onItemTransferFinished(L1PcInstance pc) { }
    }
    static class L1PcInstance {
        final int id;
        int stamp = 1, quarantines;
        boolean logout, merchant;
        Object logoutOwner;
        CUserSocket socket = new CUserSocket();
        L1PcInstance(int id) {
            this.id = id;
            socket.actor = this;
            CWorld.INSTANCE.actors.put(id, this);
        }
        int getId() { return id; }
        int getSessionStamp() { return stamp; }
        CUserSocket getNetConnection() { return socket; }
        Object getInventory() { return this; }
        boolean isLogoutStarted() { return logout; }
        boolean isPrivateShop() { return merchant; }
        int getTradeID() { return 0; }
        boolean isTradeResolving() { return false; }
""" + gate + release + r"""
        void quarantineItemTransferSession(CUserSocket session, int generation) {
            if (isItemTransferSession(session, generation)) quarantines++;
        }
    }
    record OwnerJob(Runnable action, Consumer<Throwable> done) {
        void execute() {
            try { action.run(); }
            catch (Throwable failure) { done.accept(failure); return; }
            done.accept(null);
        }
    }
    static class CIOThreadCallback {
        static final CIOThreadCallback INSTANCE = new CIOThreadCallback();
        final ArrayDeque<OwnerJob> jobs = new ArrayDeque<>();
        boolean reject;
        static CIOThreadCallback getInstance() { return INSTANCE; }
        void completeWarehouseMove(L1PcInstance pc, int stamp, Runnable action, Consumer<Throwable> terminal) {
            if (reject || pc.stamp != stamp) terminal.accept(new IllegalStateException("owner unavailable"));
            else jobs.addLast(new OwnerJob(action, terminal));
        }
    }
    static class Move {
        Consumer<Throwable> continuation;
        int acknowledgements;
        void finishOwnerApply(Throwable failure) { acknowledgements++; }
        void finishOwnerApply(Throwable failure, Consumer<Throwable> afterReleased) {
            acknowledgements++;
            continuation = afterReleased;
        }
        void releaseBarrier(Throwable failure) {
            final var pending = continuation;
            continuation = null;
            pending.accept(failure);
        }
    }
    static class Receipt {
        final L1PcInstance primary;
        final InventoryTransactionOwners owners;
        final Move move = new Move();
        int failures, quarantines, effects;
        Runnable onEffect;
        Receipt(L1PcInstance primary, L1PcInstance... others) {
            this.primary = primary;
            owners = others.length == 0 ? new InventoryTransactionOwners(primary, null, false)
                    : others.length == 1 ? new InventoryTransactionOwners(primary, others[0], false)
                    : new InventoryTransactionOwners(primary, others);
        }
        void notifyTerminalFailure() { failures++; }
        void quarantine(Throwable failure) { quarantines++; owners.quarantine(); }
        void replayThenRelease() {
            runCurrentOwners(() -> {
                check(owners.beginContinuations(), "hold exact effect owners");
                owners.release();
            });
            finishReplay(() -> {
                check(Thread.holdsLock(primary.socket), "effect running under original actor monitor");
                if (onEffect != null) onEffect.run();
                effects++;
            }, null);
        }
""" + finish + replay + r"""
    }
""" + owner + r"""
    public static void main(String[] args) {
        final var main = new L1PcInstance(2);
        final var left = new L1PcInstance(1);
        final var right = new L1PcInstance(3);
        final var party = new Receipt(main, right, main, left);
        main.socket.connected = false;
        right.socket.connected = false;
        check(!party.owners.current(main) && !party.owners.current(right), "new preparation rejects closing transport");
        check(party.owners.get(0) == left && party.owners.currentForReplay(main) && party.owners.currentForReplay(right),
                "admitted party can replay exact draining owners in sorted order");
        right.stamp++;
        check(!party.owners.currentForReplay(right), "replay excludes replacement epoch");
        right.stamp--;
        final var otherWorldOwner = new L1PcInstance(3);
        check(!party.owners.currentForReplay(right), "replay excludes replaced world object");
        CWorld.INSTANCE.actors.put(3, right);
        party.replayThenRelease();
        check(party.move.acknowledgements == 1 && party.effects == 0 && main.hasItemTransferContinuation()
                && left.hasItemTransferContinuation() && right.hasItemTransferContinuation()
                && !main.ownsItemTransferToken(1) && !right.ownsItemTransferToken(1),
                "item gates released while every party member waits for CacheD worker");
        check(left.isItemTransferBusy() && left.tryReserveItemTransfer() == 0,
                "direct mutations and subsequent receipts defer to a queued continuation");
        party.onEffect = () -> check(main.tryReserveItemTransfer() == 0,
                "a closing socket cannot admit even the old effect's next receipt");
        party.move.releaseBarrier(null);
        check(party.effects == 0 && main.hasItemTransferContinuation(), "DB barrier release posts effect to CIO");
        CIOThreadCallback.INSTANCE.jobs.removeFirst().execute();
        check(party.effects == 1 && !main.hasItemTransferContinuation() && !left.hasItemTransferContinuation()
                && !right.hasItemTransferContinuation() && party.failures == 0,
                "committed effect runs exactly once before shutdown releases every actor");

        final var oldPartyLeader = new L1PcInstance(10);
        final var oldPartyMember = new L1PcInstance(11);
        final var lostParty = new Receipt(oldPartyLeader, oldPartyMember);
        lostParty.replayThenRelease();
        lostParty.move.releaseBarrier(null);
        oldPartyMember.stamp++;
        CIOThreadCallback.INSTANCE.jobs.removeFirst().execute();
        check(lostParty.effects == 0 && lostParty.failures == 1
                && !oldPartyLeader.hasItemTransferContinuation() && !oldPartyMember.hasItemTransferContinuation(),
                "stale party member suppresses effect and releases all old holds");

        final var merchant = new L1PcInstance(12);
        merchant.socket = null;
        merchant.merchant = true;
        final var customer = new Receipt(new L1PcInstance(13), merchant);
        customer.onEffect = () -> {
            check(!customer.primary.isItemTransferBusy(), "the active effect can use the released inventory");
            final int nested = customer.primary.tryReserveItemTransfer();
            check(nested != 0 && customer.primary.isItemTransferBusy(),
                    "current effect runner can admit its next receipt on a live socket");
            customer.primary.endItemTransfer(nested);
        };
        customer.replayThenRelease();
        customer.move.releaseBarrier(null);
        CIOThreadCallback.INSTANCE.jobs.removeFirst().execute();
        check(customer.effects == 1 && !merchant.hasItemTransferContinuation() && customer.failures == 0,
                "detached merchant remains eligible without a socket hold");

        final var replaced = new Receipt(new L1PcInstance(4));
        replaced.replayThenRelease();
        replaced.move.releaseBarrier(null);
        replaced.primary.socket.actor = otherWorldOwner;
        CIOThreadCallback.INSTANCE.jobs.removeFirst().execute();
        check(replaced.effects == 0 && replaced.failures == 1 && !replaced.primary.hasItemTransferContinuation()
                && replaced.primary.quarantines == 0, "stale effect releases only its old hold");

        final var rejected = new Receipt(new L1PcInstance(5));
        rejected.replayThenRelease();
        CIOThreadCallback.INSTANCE.reject = true;
        rejected.move.releaseBarrier(null);
        check(rejected.effects == 0 && rejected.failures == 1 && !rejected.primary.hasItemTransferContinuation()
                && rejected.primary.quarantines == 1, "rejected effect releases hold and quarantines affected actor");

        final var frozen = new Receipt(new L1PcInstance(6));
        frozen.replayThenRelease();
        frozen.move.releaseBarrier(new IllegalStateException("persistent owner frozen"));
        check(frozen.effects == 0 && frozen.failures == 1 && !frozen.primary.hasItemTransferContinuation(),
                "failed barrier suppresses effect and releases hold");
        System.out.println("PASS inventory receipt drain and after-release ownership (" + checks + " checks)");
    }
}
"""

with tempfile.TemporaryDirectory() as temp:
    java = Path(temp) / "InventoryReceiptDrain.java"
    java.write_text(fixture, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-cp", temp, "InventoryReceiptDrain"], check=True)
