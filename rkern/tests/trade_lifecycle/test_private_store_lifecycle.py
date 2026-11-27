"""Run private-store owner and actual delayed play-info handler with controlled admissions."""
from pathlib import Path
import subprocess
import tempfile

source = Path(__file__).resolve().parents[2] / 'src'
server = 'l1j.server.server'
model = f'{server}.model'
shop = f'{model}.shop'
dynamic = f'{shop}.dynamic'
char = f'{model}.Instance'
net = f'{model}.network'
socket = f'{net}.core.connection'
thread = f'{net}.thread'
proto = f'{server}.packets.Protocol.MainServer.Proto'


def java_path(package, name):
    return Path(*package.split('.'), name + '.java')


pc_code = (source / java_path(char, 'L1PcInstance')).read_text(encoding='utf-8')
fields_start = pc_code.index('    private static final AtomicReferenceFieldUpdater<L1PcInstance, PlayerPrivateStore> PRIVATE_STORE')
fields_end = pc_code.index('\n', pc_code.index('private volatile PlayerPrivateStore privateStore;', fields_start))
methods_start = pc_code.index('    public PlayerPrivateStore.State privateStoreState()')
methods_end = pc_code.index('    /** Memory half of a receipt-committed private-shop operation.', methods_start)
pc_fields = pc_code[fields_start:fields_end]
pc_methods = pc_code[methods_start:methods_end].replace('@Override ', '')


def stub(package, name, body):
    return java_path(package, name), f'package {package};\n{body}'


fixtures = dict([
    stub(dynamic, 'DynamicShopItem', 'public final class DynamicShopItem { public int count = 5; }'),
    stub(dynamic, 'DynamicShopStorage', f'''
import {char}.L1PcInstance;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
public final class DynamicShopStorage {{
    public static boolean rejectOpen, rejectClose;
    public static int opens, closes;
    public static Runnable beforeOpen, beforeClose;
    public static void replacePlayerListings(L1PcInstance pc, ArrayList<DynamicShopItem> a, ArrayList<DynamicShopItem> b) {{
        if (beforeOpen != null) beforeOpen.run();
        if (rejectOpen) throw new RejectedExecutionException("open admission rejected");
        opens++;
    }}
    public static void enqueuePlayerListingRemoval(int id) {{
        if (beforeClose != null) beforeClose.run();
        if (rejectClose) throw new RejectedExecutionException("close admission rejected");
        closes++;
    }}
}}
'''),
    stub(char, 'L1PcInstance', f'''
import {dynamic}.*;
import {shop}.PlayerPrivateStore;
import {socket}.CUserSocket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
public final class L1PcInstance {{
{pc_fields}
    public boolean autoClan = true;
    public boolean busy;
    public int stamp = 1;
    public CUserSocket connection;
    public Runnable beforeReserve;
    public int getId() {{ return 1; }}
    public boolean isAutoClanjoin() {{ return autoClan; }}
    public int getSessionStamp() {{ return stamp; }}
    public CUserSocket getNetConnection() {{ return connection; }}
    public int tryReserveItemTransfer(CUserSocket socket, int oldStamp) {{
        if (beforeReserve != null) beforeReserve.run();
        if (busy || socket != connection || oldStamp != stamp) return 0;
        busy = true;
        return 1;
    }}
    public void endItemTransfer(int token) {{
        if (!busy || token != 1) throw new AssertionError("invalid item gate release");
        busy = false;
    }}
{pc_methods}
}}
'''),
    stub(socket, 'CUserSocket', f'''
import {char}.L1PcInstance;
public record CUserSocket(L1PcInstance getActiveChar) {{}}
'''),
    stub(thread, 'CIOThreadCallback', f'''
import {socket}.CUserSocket;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.concurrent.RejectedExecutionException;
public final class CIOThreadCallback {{
    private static final CIOThreadCallback INSTANCE = new CIOThreadCallback();
    public static CIOThreadCallback getInstance() {{ return INSTANCE; }}
    public final ArrayDeque<Event> events = new ArrayDeque<>();
    public boolean reject;
    public record Event(Runnable action, Consumer<Throwable> complete) {{
        public void run() {{
            Throwable failure = null;
            try {{ action.run(); }} catch (RejectedExecutionException rejected) {{ failure = rejected; }}
            complete.accept(failure);
        }}
        public void fail() {{ complete.accept(new RejectedExecutionException("owner admission rejected")); }}
    }}
    public boolean completeSession(CUserSocket client, long delay, Runnable action, Consumer<Throwable> complete) {{
        if (delay != 10_000L) throw new AssertionError("original session delay");
        if (reject) {{
            complete.accept(new RejectedExecutionException("scheduler rejection"));
            return false;
        }}
        events.add(new Event(action, complete));
        return true;
    }}
}}
'''),
    stub(proto, 'SC_USER_PLAY_INFO_NOTI', f'''
import {char}.L1PcInstance;
public final class SC_USER_PLAY_INFO_NOTI {{
    public static int sent;
    public static void send(L1PcInstance pc) {{ sent++; }}
}}
'''),
])

for package, name in ((shop, 'PlayerPrivateStore'), (proto, 'CS_USER_PLAY_INFO_REQ')):
    path = java_path(package, name)
    fixtures[path] = (source / path).read_text(encoding='utf-8')

imports = f'''
import {char}.L1PcInstance;
import {dynamic}.*;
import {shop}.PlayerPrivateStore;
import {socket}.CUserSocket;
import {thread}.CIOThreadCallback;
import {proto}.*;
'''
fixtures[Path('StoreLifecycleCheck.java')] = imports + r'''
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public final class StoreLifecycleCheck {
    static final CIOThreadCallback TIMER = CIOThreadCallback.getInstance();
    static final byte[] CHAT = {1, 2}, FRESH_CHAT = {3, 4};

    static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    static L1PcInstance player() {
        var pc = new L1PcInstance();
        pc.connection = new CUserSocket(pc);
        return pc;
    }

    static ArrayList<DynamicShopItem> orders() {
        return new ArrayList<>(List.of(new DynamicShopItem()));
    }

    static void openReal(L1PcInstance pc, byte[] chat) { pc.openPrivateStore(orders(), orders(), chat); }
    static void request(L1PcInstance pc) { CS_USER_PLAY_INFO_REQ.handleRequest(pc.connection); }

    static CIOThreadCallback.Event pending(L1PcInstance pc) {
        request(pc);
        check(pc.isPrivateReady(), "play-info marks the current generation ready");
        return TIMER.events.remove();
    }

    static void rejected(Runnable task) {
        try { task.run(); } catch (RejectedExecutionException expected) { return; }
        throw new AssertionError("expected matching admission rejection");
    }

    static void assertSame(L1PcInstance pc, PlayerPrivateStore.State state) {
        check(pc.privateStoreState() == state && pc.getSellings() == state.sellings()
                && pc.getPurchasings() == state.purchasings() && pc.getShopChat() == state.chat(),
                "state, orders and chat remain bound to the exact opening");
    }

    static void ownerLifecycle() throws Exception {
        var field = L1PcInstance.class.getDeclaredField("privateStore");
        field.setAccessible(true);
        var pc = player();
        var initial = pc.privateStoreState();
        check(initial == player().privateStoreState() && initial.generation() == 0, "shared empty initial snapshot");
        request(pc);
        pc.setPrivateReady(true);
        pc.disposeShopInfo();
        check(TIMER.events.isEmpty() && !pc.closePrivateStore() && !pc.setPrivateReady(0, true)
                && field.get(pc) == null, "reads, play-info and closed writes allocate no owner");

        var sells = orders();
        var buys = orders();
        DynamicShopStorage.beforeOpen = () -> assertSame(pc, initial);
        pc.openPrivateStore(sells, buys, CHAT);
        DynamicShopStorage.beforeOpen = null;
        var holder = field.get(pc);
        var first = pc.privateStoreState();
        check(holder != null && first.open() && !first.displayOnly() && first.generation() == 1,
                "first real publication allocates one owner");
        check(first.sellings() == sells && first.purchasings() == buys && first.chat() == CHAT,
                "validated order lists and chat belong to this generation");
        check(pc.setPrivateReady(first.generation(), true), "ready exactly once");
        check(!pc.setPrivateReady(first.generation(), true), "duplicate ready suppressed");
        DynamicShopStorage.rejectOpen = true;
        rejected(() -> openReal(pc, FRESH_CHAT));
        DynamicShopStorage.rejectOpen = false;
        assertSame(pc, first);
        check(pc.isPrivateReady(), "a failed replacement did not touch the old ready bit");

        DynamicShopStorage.beforeClose = () -> assertSame(pc, first);
        check(pc.closePrivateStore(first.generation()), "matching real close accepted");
        DynamicShopStorage.beforeClose = null;
        var closed = pc.privateStoreState();
        check(!closed.open() && !pc.isPrivateReady() && closed.generation() == 2 && closed.sellings() == null
                && closed.purchasings() == null && closed.chat() == null, "retired owner keeps no chat, lists or readiness");
        check(sells.size() == 1 && buys.size() == 1, "stale readers retain untouched old list snapshots");
        openReal(pc, FRESH_CHAT);
        var next = pc.privateStoreState();
        check(field.get(pc) == holder && next.generation() == 3 && next.sellings() != sells
                && next.purchasings() != buys && !pc.isPrivateReady(), "reopen uses the same holder and fresh order identity");
        check(!pc.closePrivateStore(first.generation()) && !pc.setPrivateReady(first.generation(), false),
                "old generation cannot touch a real reopen");
        assertSame(pc, next);
        int removals = DynamicShopStorage.closes;
        pc.disposeShopInfo();
        pc.openDisplayPrivateStore(CHAT);
        var display = pc.privateStoreState();
        check(display.displayOnly() && display.sellings() == null && display.purchasings() == null,
                "display reopening carries chat only");
        check(pc.closePrivateStore(display.generation()) && DynamicShopStorage.closes == removals
                && field.get(pc) == holder && pc.getShopChat() == null, "display close and local disposal need no storage removal");
    }

    static void staleReopen(boolean displayOnly) {
        var pc = player();
        openReal(pc, CHAT);
        var event = pending(pc);
        request(pc);
        check(TIMER.events.isEmpty(), "duplicate play-info did not queue another timer");
        pc.closePrivateStore();
        if (displayOnly) pc.openDisplayPrivateStore(FRESH_CHAT); else openReal(pc, FRESH_CHAT);
        var next = pc.privateStoreState();
        pc.setPrivateReady(next.generation(), true);
        int removals = DynamicShopStorage.closes;
        event.run();
        event.fail();
        check(pc.isPrivateReady() && !pc.busy && DynamicShopStorage.closes == removals,
                "stale timer and late admission failure leave the new opening untouched");
        assertSame(pc, next);
    }

    static void interleavedClose() {
        var pc = player();
        pc.openDisplayPrivateStore(CHAT);
        var event = pending(pc);
        pc.beforeReserve = () -> {
            check(pc.closePrivateStore(), "old display closed while delayed event acquired the gate");
            openReal(pc, FRESH_CHAT);
        };
        int removals = DynamicShopStorage.closes;
        event.run();
        check(pc.isPrivateShop() && pc.getShopChat() == FRESH_CHAT && pc.getSellings() != null
                && !pc.busy && DynamicShopStorage.closes == removals,
                "conditional close rechecks generation after acquiring the gate");
    }

    static void recoverableRejection() {
        var pc = player();
        openReal(pc, CHAT);
        var current = pc.privateStoreState();
        int removals = DynamicShopStorage.closes;
        var busyEvent = pending(pc);
        pc.busy = true;
        busyEvent.run();
        check(!pc.isPrivateReady() && pc.busy && DynamicShopStorage.closes == removals,
                "busy timer clears only its ready bit and preserves the other operation gate");
        assertSame(pc, current);
        pc.busy = false;

        TIMER.reject = true;
        request(pc);
        TIMER.reject = false;
        check(!pc.isPrivateReady() && TIMER.events.isEmpty() && DynamicShopStorage.closes == removals,
                "scheduler rejection restores retry eligibility without closing");
        assertSame(pc, current);

        var dbEvent = pending(pc);
        DynamicShopStorage.rejectClose = true;
        dbEvent.run();
        DynamicShopStorage.rejectClose = false;
        check(!pc.isPrivateReady() && !pc.busy && DynamicShopStorage.closes == removals,
                "storage admission rejection preserves the store and releases its gate");
        assertSame(pc, current);

        pending(pc).run();
        check(!pc.isPrivateShop() && !pc.isPrivateReady() && !pc.busy && pc.getShopChat() == null
                && DynamicShopStorage.closes == removals + 1, "a retry closes the exact real generation once");

        pc.openDisplayPrivateStore(FRESH_CHAT);
        pending(pc).run();
        check(!pc.isPrivateShop() && pc.getShopChat() == null && DynamicShopStorage.closes == removals + 1,
                "matching delayed close also clears the display without SQL admission");
    }

    static void staleSessionFailure() {
        var pc = player();
        pc.openDisplayPrivateStore(CHAT);
        var event = pending(pc);
        var current = pc.privateStoreState();
        pc.connection = new CUserSocket(pc);
        pc.stamp++;
        event.fail();
        check(pc.isPrivateReady() && !pc.busy, "old socket failure does not clear replacement readiness");
        assertSame(pc, current);
    }

    public static void main(String[] args) throws Exception {
        ownerLifecycle();
        staleReopen(true);
        staleReopen(false);
        interleavedClose();
        recoverableRejection();
        staleSessionFailure();
        check(TIMER.events.isEmpty() && SC_USER_PLAY_INFO_NOTI.sent > 0, "no retained timer fixture and UI notices preserved");
        System.out.println("PASS lazy private-store owner, generation fences, list/chat cleanup; stale/busy/rejected delayed close and retry");
    }
}
'''

with tempfile.TemporaryDirectory(prefix='private-store-lifecycle-') as workspace:
    workspace = Path(workspace)
    files = []
    for relative, text in fixtures.items():
        path = workspace / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        files.append(path)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(workspace), *map(str, files)], check=True)
    subprocess.run(['java', '-ea', '-cp', str(workspace), 'StoreLifecycleCheck'], check=True)
