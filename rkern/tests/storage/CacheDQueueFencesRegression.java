// Run each regression mode in a fresh JVM.
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CItem;
import l1j.server.server.storage.cached.CPendingState;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.cached.CWareHouse;
import l1j.server.server.templates.L1EtcItem;

public class CacheDQueueFencesRegression {
    private static final AtomicInteger writes = new AtomicInteger();
    private static final int PROFILE_OWNERS = 5120, PROFILE_THREADS = 4;
    private static volatile IntConsumer onItemWrite;

    public static void main(String[] args) throws Exception {
        Field pool = field(DatabaseFactory.class, "DATABASE_POOL");
        pool.set(null, new HikariDataSource() {
            @Override public Connection getConnection() { return connect(); }
        });
        CacheD cache = CacheD.getInstance();
        check(cache.flushAll(1000), "schema probe completed");
        check(field(CacheD.class, "transactionStoreReady").getBoolean(cache), "receipt schema ready");
        switch (args[0]) {
            case "expedite" -> expedite(cache);
            case "quarantine" -> quarantine(cache);
            case "quarantine-chain" -> quarantineChain(cache);
            case "release" -> release(cache);
            case "reload" -> reload(cache);
            case "coalesce" -> coalesce(cache);
            case "coalesce-profile" -> profileQueuedItemSaves(cache);
            case "logout" -> logout(cache);
            case "autosave" -> autosave(cache);
            case "item-admission" -> itemAdmission(cache);
            default -> throw new IllegalArgumentException("unknown CacheD regression mode: " + args[0]);
        }
        System.out.println("PASS actual CacheD " + args[0]);
    }

    private static void itemAdmission(CacheD cache) throws Exception {
        int owner = 21111;
        L1EtcItem template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Fixture");
        L1ItemInstance detached = new L1ItemInstance(template, 1);
        detached.setId(44111);
        cache.registerLoadedItemOwner(owner, detached.getId());
        cache.requestCharacterItemOwnerRelease(owner);
        L1ItemInstance owned = new L1ItemInstance(template, 1);
        owned.setId(44112);
        cache.registerLoadedItemOwner(owner, owned.getId());

        boolean saveRejected = false;
        try { cache.saveItem(detached, 16L); }
        catch (RejectedExecutionException expected) { saveRejected = true; }
        int storesRejected = 0;
        for (int invalidOwner : new int[] {0, -1}) {
            try { cache.storeItem(invalidOwner, owned); }
            catch (IllegalArgumentException expected) { storesRejected++; }
        }
        check(cache.flushAll(250), "invalid ordinary item jobs must not block flush");
        check(saveRejected && storesRejected == 2, "reject unknown save owner and nonpositive store owners");
        check(!cache.isItemPersistencePending(detached.getId()) && !cache.isItemPersistencePending(owned.getId())
                && writes.get() == 0, "invalid item requests enqueue no SQL work");
        Object monitor = field(CacheD.class, "lock").get(cache);
        CWareHouse warehouse = (CWareHouse) field(CacheD.class, "warehouse").get(cache);
        synchronized (monitor) {
            check(warehouse.itemOwners.get(detached.getId()) == 0 && warehouse.itemOwners.get(owned.getId()) == owner,
                    "rejected item requests preserve existing ownership");
        }
    }

    private static void autosave(CacheD cache) throws Exception {
        final int ownerId = 21101;
        final L1PcInstance pc = new L1PcInstance();
        pc.setId(ownerId);
        pc.setName("fixture-autosave");
        pc.setCacheDSessionEpoch(cache.registerLoadedCharacterSession("fixture-account", pc.getName(), ownerId));
        final L1EtcItem template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Fixture");
        final L1ItemInstance transferred = addDirtyInventoryItem(cache, pc, template, 21111);
        final L1ItemInstance valid = addDirtyInventoryItem(cache, pc, template, 21112);
        // The immutable inventory membership may still contain the old object.
        cache.registerLoadedItemOwner(ownerId + 1, transferred.getId());
        try {
            pc.saveInventory();
            throw new AssertionError("legacy save should reject the former owner's first row");
        } catch (RejectedExecutionException expected) {
            check(writes.get() == 0 && valid.getRecordingColumns() != 0,
                    "legacy spillover skipped the independent item");
        }
        check(pc.saveInventoryIfReady(), "autosave covered transferred and valid rows");
        check(cache.flushItemsByCharacter(ownerId, 1000) && writes.get() == 1
                && valid.getRecordingColumns() == 0 && transferred.getRecordingColumns() != 0,
                "only the currently owned item was persisted");

        final Object timer = newInventorySaveTimer(pc);
        final Class<?> timerType = timer.getClass();
        final Method saveOnWorker = timerType.getDeclaredMethod("saveOnWorker");
        saveOnWorker.setAccessible(true);
        final Field next = field(timerType, "nextInvSaveNs");
        final Field failures = field(timerType, "invSaveFailures");
        valid.setCount(3);
        try (var ignored = CacheD.beginWarehouseMove(ownerId, ownerId + 2)) {
            // The real autosave work runs off the thread that prepares the receipt.
            final long due = System.nanoTime();
            next.setLong(timer, due - 1);
            final var attempt = new FutureTask<>(() -> {
                saveOnWorker.invoke(timer);
                return null;
            });
            Thread.ofVirtual().start(attempt);
            attempt.get(2L, TimeUnit.SECONDS);
            check(failures.getInt(timer) == 1 && next.getLong(timer) - due >= TimeUnit.SECONDS.toNanos(2)
                    && next.getLong(timer) - due < TimeUnit.SECONDS.toNanos(4)
                    && cache.pendingItems() == 0 && writes.get() == 1,
                    "unprepared receipt returned explicit deferral without queueing a stale image");
        }
        final long retry = System.nanoTime();
        next.setLong(timer, retry - 1);
        saveOnWorker.invoke(timer);
        check(failures.getInt(timer) == 0 && next.getLong(timer) - retry >= TimeUnit.SECONDS.toNanos(1200)
                && cache.flushItemsByCharacter(ownerId, 1000) && writes.get() == 2
                && valid.getRecordingColumns() == 0, "retry captured the item after the fence cleared");

        cache.deleteItem(valid);
        valid.setCount(4);
        check(pc.saveInventoryIfReady() && cache.flushItemsByCharacter(ownerId, 1000)
                && writes.get() == 3, "a deleted row did not enqueue an owner-zero update");

        final IllegalStateException brokenImage = new IllegalStateException("fixture item capture failed");
        final L1ItemInstance broken = new L1ItemInstance(template, 1) {
            @Override public int getRecordingColumns() { throw brokenImage; }
        };
        broken.setId(21113);
        pc.getInventory().getItems().add(broken);
        final L1ItemInstance later = addDirtyInventoryItem(cache, pc, template, 21114);
        try {
            pc.saveInventoryIfReady();
            throw new AssertionError("autosave hid a capture error");
        } catch (IllegalStateException expected) {
            check(expected == brokenImage, "autosave reported the original failed item");
        }
        check(cache.flushItemsByCharacter(ownerId, 1000) && writes.get() == 4
                && later.getRecordingColumns() == 0, "later independent dirty item persisted after a capture error");
    }

    private static Object newInventorySaveTimer(L1PcInstance pc) throws Exception {
        Config.AUTOSAVE_INTERVAL = 0;
        Config.AUTOSAVE_INTERVAL_INVENTORY = 1200;
        final String cio = "l1j.server.server.model.network.thread.CIOThreadCallback";
        final Class<?> handle = Class.forName(cio + "$TaskHandle");
        final var newHandle = handle.getDeclaredConstructor();
        newHandle.setAccessible(true);
        final var newTimer = Class.forName(cio + "$UserTimerTask").getDeclaredConstructor(
                L1PcInstance.class, handle, Class.forName(cio + "$CIOThread"));
        newTimer.setAccessible(true);
        return newTimer.newInstance(pc, newHandle.newInstance(), null);
    }

    private static L1ItemInstance addDirtyInventoryItem(CacheD cache, L1PcInstance pc, L1EtcItem template, int id) {
        final L1ItemInstance item = new L1ItemInstance(template, 1);
        item.setId(id);
        item._cha = pc;
        item.getLastStatus().updateAll();
        pc.getInventory().getItems().add(item);
        cache.registerLoadedItemOwner(pc.getId(), id);
        item.setCount(2);
        return item;
    }

    private static void logout(CacheD cache) throws Exception {
        Object lock = field(CacheD.class, "lock").get(cache);
        CountDownLatch aborted = new CountDownLatch(2);
        Runnable resume = () -> {
            check(!Thread.holdsLock(lock), "resume runs outside CacheD lock");
            aborted.countDown();
        };
        try (var reservation = CacheD.beginWarehouseMove(101, 102)) {
            check(cache.deferLogoutForOwnerApply(101, resume), "unprepared primary waits");
            check(cache.deferLogoutForOwnerApply(102, resume), "unprepared secondary waits");
            check(!cache.deferLogoutForOwnerApply(103, resume), "unrelated logout proceeds");
        }
        await(aborted);

        // Deliver ACK while the worker is still inside the receipt dispatch.
        // Follow-up effects must wait until both lanes can accept a new write.
        Class<?> type = Class.forName(CacheD.class.getName() + "$DbJob");
        Method submit = CacheD.class.getDeclaredMethod("submitDbJobInternal",
                int.class, int.class, Runnable.class, boolean.class, boolean.class);
        Method require = CacheD.class.getDeclaredMethod("requireOwnerApply", type);
        Method finish = CacheD.class.getDeclaredMethod("finishOwnerApply",
                type, Throwable.class, Consumer.class);
        for (Method method : new Method[]{submit, require, finish}) method.setAccessible(true);
        for (boolean fail : new boolean[]{false, true}) {
            int owner = fail ? 301 : 201;
            CountDownLatch acked = new CountDownLatch(1);
            CountDownLatch leaveWorker = new CountDownLatch(1);
            CountDownLatch observers = new CountDownLatch(2);
            AtomicReference<Object> job = new AtomicReference<>();
            AtomicReference<Throwable> bad = new AtomicReference<>();
            Runnable worker = () -> {
                try {
                    Consumer<Throwable> next = cause -> {
                        try {
                            check(!Thread.holdsLock(lock), "post-release effect outside CacheD lock");
                            check((cause != null) == fail, "effect receives actual final outcome");
                            check(cache.hasPendingOwnerApply(owner) == fail, "SQL barrier published before effect");
                            check(cache.isOwnerPersistenceQuarantined(owner) == fail, "correct quarantine");
                        } catch (Throwable error) { bad.set(error); }
                        finally { observers.countDown(); }
                    };
                    field(type, "commitConfirmed").setBoolean(job.get(), true);
                    finish.invoke(cache, job.get(), null, next);
                    acked.countDown();
                    await(leaveWorker);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                if (fail) throw new IllegalStateException("fixture failure after dispatch");
            };
            synchronized (lock) {
                job.set(submit.invoke(cache, owner, owner+1, worker, false, true));
                require.invoke(cache, job.get());
                check(cache.deferLogoutForOwnerApply(owner+1, observers::countDown),
                        "secondary logout waits for final outcome");
            }
            await(acked);
            check(observers.getCount() == 2 && cache.hasPendingOwnerApply(owner), "early ACK kept both callbacks");
            leaveWorker.countDown();
            await(observers);
            if (bad.get() != null) throw new AssertionError(bad.get());
            check(!cache.deferLogoutForOwnerApply(owner, resume), "retired or quarantined owner can detach");
        }
        check(field(CacheD.class, "logoutWaiters").get(cache) == null, "no retained logout listeners");
    }

    private static void expedite(CacheD cache) throws Exception {
        final int owner = 5051;
        final L1EtcItem template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Fixture");
        final L1ItemInstance item = new L1ItemInstance(template, 1);
        item.setId(44101);
        postpone(cache, owner, item, false);
        check(cache.flushItemsByCharacter(owner, 500), "foreground item flush bypasses healthy coalescing");
        check(writes.get() == 1, "healthy item image persisted exactly once");
        postpone(cache, owner, item, true);
        check(!cache.flushItemsByCharacter(owner, 60), "urgent flush respects failure backoff");
        check(writes.get() == 1 && cache.isItemPersistencePending(item.getId()),
                "deferred retry retains its image without extra write");
    }

    private static void postpone(CacheD cache, int owner, L1ItemInstance item, boolean retry) throws Exception {
        final Object lock = field(CacheD.class, "lock").get(cache);
        final Object ready = field(CacheD.class, "ready").get(cache);
        synchronized (lock) {
            cache.registerLoadedItemOwner(owner, item.getId());
            cache.saveItem(item, 16L);
            Int2ObjectOpenHashMap<?> items = (Int2ObjectOpenHashMap<?>) field(CacheD.class, "items").get(cache);
            CItem pending = (CItem) items.get(item.getId());
            invoke(ready, "detachForDeadlineChange", CPendingState.class, pending);
            pending.dueAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            if (retry) pending.failureCount = 1;
            invoke(ready, "refresh", CPendingState.class, pending);
        }
    }

    private static final class CountingReleases extends IntOpenHashSet {
        int traversals;

        @Override public IntIterator iterator() {
            traversals++;
            return super.iterator();
        }
    }

    private static void release(CacheD cache) throws Exception {
        Object lock = field(CacheD.class, "lock").get(cache);
        CWareHouse warehouse = (CWareHouse) field(CacheD.class, "warehouse").get(cache);
        CountingReleases waiters = new CountingReleases();
        L1EtcItem template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Synthetic");
        int previous = 321, current = 322;
        int held = 1024, writes = 256;
        synchronized (lock) {
            field(CWareHouse.class, "releasingOwnerIds").set(warehouse, waiters);
            for (int owner = 1_000; owner < 1_000 + held; owner++) {
                warehouse.addPendingId(owner, owner + 100_000);
                cache.requestCharacterItemOwnerRelease(owner);
            }
            final var reloaded = new ArrayList<L1ItemInstance>(writes);
            for (int id = 1; id <= writes; id++) {
                L1ItemInstance item = new L1ItemInstance(template, 1);
                item.setId(id);
                cache.registerLoadedItemOwner(previous, id);
                cache.saveItem(item, 16L);
                reloaded.add(item);
            }
            cache.registerLoadedItemOwners(current, reloaded);
            for (L1ItemInstance item : reloaded) cache.saveItem(item, 16L);
            cache.requestCharacterItemOwnerRelease(previous);
            cache.requestCharacterItemOwnerRelease(current);
        }
        check(cache.flushAll(5000), "known owner writes drained");
        synchronized (lock) {
            check(!waiters.contains(previous) && !waiters.contains(current), "release all former owners");
            check(waiters.size() == held, "unrelated pending owners retained");
            System.out.println("knownItemWrites=" + writes + " blockedLogouts=" + held
                    + " fullReleaseTraversals=" + waiters.traversals);
            check(waiters.traversals == 0, "known writes scanned unrelated logouts");

            // Simulate a legacy unknown-owner queue entry before an owner reload.
            // New ordinary admissions reject this entry in item-admission mode.
            L1ItemInstance unknown = new L1ItemInstance(template, 1);
            unknown.setId(700);
            Method capture = CacheD.class.getDeclaredMethod("captureItemSave", L1ItemInstance.class, int.class, long.class);
            capture.setAccessible(true);
            check((boolean) capture.invoke(cache, unknown, 0, 16L), "legacy unknown item staged");
            cache.requestCharacterItemOwnerRelease(800);
            check(waiters.contains(800), "unknown item must hold unrelated logout");
            cache.registerLoadedItemOwner(current, 700);
            cache.saveItem(unknown, 16L);
        }
        check(cache.flushAll(5000), "formerly unknown item drained");
        synchronized (lock) {
            check(!waiters.contains(800) && waiters.size() == held, "released final unknown-owner barrier");
            check(waiters.traversals == 1, "one global recheck for formerly unknown owner");
        }
    }

    /** Run in a fresh JVM with -Xms128m -Xmx128m -XX:ActiveProcessorCount=4.
     * Per-thread allocated bytes cover save admissions only; no SQL service is needed. */
    private static void profileQueuedItemSaves(CacheD cache) throws Exception {
        Object monitor = field(CacheD.class, "lock").get(cache);
        var held = (IntOpenHashSet) field(CacheD.class, "activeOwnerIds").get(cache);
        var template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("queued item profile");
        var items = new L1ItemInstance[PROFILE_OWNERS];
        try {
            synchronized (monitor) {
                for (int i = 0; i < items.length; i++) {
                    int owner = 200000 + i;
                    held.add(owner);
                    var item = new L1ItemInstance(template, 10);
                    item.setId(100000 + i);
                    item.setLastUsed(java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
                    items[i] = item;
                    cache.registerLoadedItemOwner(owner, item.getId());
                    cache.saveItem(item, 2L);
                }
            }
            measureQueuedItemSaves(cache, items, 4, 4);
            measureQueuedItemSaves(cache, items, 20, 4);
            measureQueuedItemSaves(cache, items, 20, 4);
            measureQueuedItemSaves(cache, items, 20, 1);
        } finally {
            synchronized (monitor) {
                for (int i = 0; i < items.length; i++) held.remove(200000 + i);
            }
            check(cache.flushAll(15000), "profile released all owners and drained");
        }
    }

    private static void measureQueuedItemSaves(CacheD cache, L1ItemInstance[] items, int passes, int changeEvery)
            throws Exception {
        var bean = java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
        var start = new java.util.concurrent.CyclicBarrier(PROFILE_THREADS + 1);
        var results = new ArrayList<java.util.concurrent.Future<long[]>>();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(PROFILE_THREADS)) {
            for (int worker = 0; worker < PROFILE_THREADS; worker++) {
                int from = worker * items.length / PROFILE_THREADS;
                int to = (worker + 1) * items.length / PROFILE_THREADS;
                results.add(workers.submit(() -> {
                    long[] elapsed = new long[passes * (to - from)];
                    int sample = 0;
                    start.await();
                    long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    for (int pass = 0; pass < passes; pass++) {
                        for (int i = from; i < to; i++) {
                            var item = items[i];
                            if ((pass + i) % changeEvery == 0) item.setCount(item.getCount() + 1);
                            long began = System.nanoTime();
                            cache.saveItem(item, 2L);
                            elapsed[sample++] = System.nanoTime() - began;
                        }
                    }
                    long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
                    java.util.Arrays.sort(elapsed);
                    return new long[] { allocated, elapsed[(int) Math.ceil(elapsed.length * 0.99) - 1] };
                }));
            }
            start.await();
            long began = System.nanoTime(), bytes = 0, threadP99 = 0;
            for (var result : results) {
                long[] values = result.get(20, TimeUnit.SECONDS);
                bytes += values[0];
                threadP99 += values[1];
            }
            int requests = passes * items.length;
            System.out.printf(java.util.Locale.ROOT,
                    "passes=%d changed=1/%d requests=%d admissions/s=%.0f bytes/admission=%.2f meanThreadP99us=%.2f%n",
                    passes, changeEvery, requests, requests * 1e9 / (System.nanoTime() - began),
                    (double) bytes / requests, threadP99 / 1000.0 / PROFILE_THREADS);
        }
    }

    @SuppressWarnings("unchecked")
    private static void coalesce(CacheD cache) throws Exception {
        final int id = 555, owner = 501;
        var template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Coalescing fixture");
        var item = new L1ItemInstance(template, 4);
        item.setId(id);
        item.setLastUsed(java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        Object monitor = field(CacheD.class, "lock").get(cache);
        var pending = (Int2ObjectOpenHashMap<CItem>) field(CacheD.class, "items").get(cache);
        synchronized (monitor) {
            cache.registerLoadedItemOwner(owner, id);
            cache.saveItem(item, 2L);
            CItem state = pending.get(id);
            var original = state.latest;
            long firstVersion = state.version, deadline = state.dueAt;
            cache.saveItem(item, 2L);
            check(state.latest == original && state.version == firstVersion, "covered duplicate skipped");
            cache.saveItem(item, 16L);
            check(state.latest == original && state.dirty == 18 && state.version > firstVersion,
                    "additional requested column retained");
            item.getLastUsed().setNanos(100);
            cache.saveItem(item, 2L);
            check(state.latest != original && original.lastUsed.getNanos() == 0, "mutable timestamp recaptured");
            var replacement = new L1ItemInstance(template, 4);
            replacement.setId(id);
            replacement.setLastUsed(item.getLastUsed());
            var prior = state.latest;
            cache.saveItem(replacement, 2L);
            check(state.latest != prior && state.latest.matchesSaveSource(replacement, owner),
                    "replacement object receives the acknowledgement");
            check(state.dueAt == deadline, "coalescing preserved the original deadline");
        }
        check(cache.flushAll(3000) && writes.get() == 1, "one coalesced update");
    }

    private static void reload(CacheD cache) throws Exception {
        int sqlOwner = 501, currentOwner = 502, unrelated = 503;
        L1EtcItem template = new L1EtcItem();
        template.setItemId(40001);
        template.setName("Reload fixture");
        L1ItemInstance item = new L1ItemInstance(template, 1);
        item.setId(555);
        CountDownLatch ownerActive = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        CountDownLatch writeEntered = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        AtomicInteger writtenOwner = new AtomicInteger();
        onItemWrite = owner -> {
            writtenOwner.set(owner);
            writeEntered.countDown();
            await(releaseWrite);
        };
        try {
            cache.submitDbJobOrdered(currentOwner, () -> {
                ownerActive.countDown();
                await(releaseOwner);
            });
            await(ownerActive);
            Object lock = field(CacheD.class, "lock").get(cache);
            Object ready = field(CacheD.class, "ready").get(cache);
            Method select = ready.getClass().getDeclaredMethod("select", long.class);
            select.setAccessible(true);
            synchronized (lock) {
                cache.registerLoadedItemOwner(sqlOwner, item.getId());
                cache.saveItem(item, 16L);
                cache.registerLoadedItemOwner(currentOwner, item.getId());
                Object candidate = select.invoke(ready, System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
                check(candidate == null, "delayed item must not bypass its newly active owner lane");
            }
            CountDownLatch oldOwnerSuccessor = new CountDownLatch(1);
            cache.submitDbJobOrdered(sqlOwner, oldOwnerSuccessor::countDown);
            CountDownLatch independent = new CountDownLatch(1);
            cache.submitDbJobOrdered(unrelated, independent::countDown);
            await(independent);
            check(writes.get() == 0 && oldOwnerSuccessor.getCount() == 1,
                    "the pending image holds its SQL owner lane before dispatch");
            check(!cache.flushItemsByCharacter(sqlOwner, 0) && !cache.flushItemsByCharacter(currentOwner, 0),
                    "both owners retain pending barriers after reload");

            releaseOwner.countDown();
            await(writeEntered);
            check(writtenOwner.get() == sqlOwner, "reload must not retarget the immutable SQL image");
            CountDownLatch newOwnerSuccessor = new CountDownLatch(1);
            cache.submitDbJobOrdered(currentOwner, newOwnerSuccessor::countDown);
            CountDownLatch independentDuringWrite = new CountDownLatch(1);
            cache.submitDbJobOrdered(unrelated, independentDuringWrite::countDown);
            await(independentDuringWrite);
            check(oldOwnerSuccessor.getCount() == 1 && newOwnerSuccessor.getCount() == 1,
                    "both owner lanes remain exclusive while the SQL write executes");
            releaseWrite.countDown();
            check(cache.flushAll(1000), "the item and both owner successors drain");
            check(writes.get() == 1 && oldOwnerSuccessor.getCount() == 0 && newOwnerSuccessor.getCount() == 0,
                    "both lanes release after the single item write");
        } finally {
            releaseOwner.countDown();
            releaseWrite.countDown();
        }
    }

    private static void quarantine(CacheD cache) throws Exception {
        int primary = 20001, secondary = 20002, extra = 20003, unrelated = 20099;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch quarantined = awaitQuarantineEvent();
        AtomicInteger affectedExecutions = new AtomicInteger();
        cache.submitWarehouseLoginJob(primary, secondary, extra, () -> {
            entered.countDown();
            await(release);
            throw new IllegalStateException(new CTransaction.OutcomeUnknownException("fixture", null));
        });
        await(entered);
        cache.submitDbJobOrdered(primary, affectedExecutions::incrementAndGet);
        release.countDown();
        await(quarantined);
        int accepted = 0;
        for (int i = 0; i < 2200; i++) {
            int lane = switch (i % 3) { case 0 -> primary; case 1 -> secondary; default -> extra; };
            try {
                cache.submitDbJobRetryOrdered(unrelated + 1, lane, affectedExecutions::incrementAndGet);
                accepted++;
            } catch (RejectedExecutionException expected) { }
        }
        check(accepted == 0, "quarantined owners consumed " + accepted + " shared queue slots");
        rejected(() -> cache.submitDbJobOrdered(primary, affectedExecutions::incrementAndGet));
        rejected(() -> cache.submitWarehouseLoginJob(unrelated + 1, unrelated + 2, extra,
                affectedExecutions::incrementAndGet));
        rejected(() -> CacheD.beginMailEconomyMove(unrelated + 1, secondary, new int[0]));
        rejected(() -> CacheD.beginMailEconomyMove(unrelated + 1, unrelated + 2, new int[] {extra}));
        AtomicInteger unrelatedExecutions = new AtomicInteger();
        cache.submitDbJobOrdered(unrelated, unrelatedExecutions::incrementAndGet);
        check(cache.flushDbOwner(unrelated, 1000) && unrelatedExecutions.get() == 1,
                "unrelated work completes after failed submission flood");
        check(!cache.flushDbOwner(primary, 0) && affectedExecutions.get() == 0,
                "quarantine and pre-admitted owner work remain fenced");
        Object monitor = field(CacheD.class, "lock").get(cache);
        synchronized (monitor) {
            check(field(CacheD.class, "pendingDbJobs").getInt(cache) == 2,
                    "rejections retain original work only");
        }
    }

    private static void quarantineChain(CacheD cache) throws Exception {
        int unresolved = 20501, linked = 20502, tail = 20503, independent = 20599;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch quarantined = awaitQuarantineEvent();
        AtomicInteger blockedExecutions = new AtomicInteger();
        cache.submitDbJobOrdered(unresolved, () -> {
            entered.countDown();
            await(release);
            throw new IllegalStateException(new CTransaction.OutcomeUnknownException("fixture", null));
        });
        try {
            await(entered);
            cache.submitDbJobOrdered(unresolved, linked, blockedExecutions::incrementAndGet);
            cache.submitDbJobOrdered(linked, tail, blockedExecutions::incrementAndGet);
        } finally {
            release.countDown();
        }
        await(quarantined);
        check(cache.isOwnerPersistenceQuarantined(unresolved) && !cache.isOwnerPersistenceQuarantined(linked)
                && !cache.isOwnerPersistenceQuarantined(tail), "quarantine stays scoped to the unresolved transaction");
        int accepted = 0;
        for (int i = 0; i < 2200; i++) {
            try {
                cache.submitDbJobRetryOrdered(tail, blockedExecutions::incrementAndGet);
                accepted++;
            } catch (RejectedExecutionException expected) { }
        }
        check(accepted == 0, "indirectly blocked admissions consumed " + accepted + " shared queue slots");
        rejected(() -> cache.submitDbJobOrdered(linked, blockedExecutions::incrementAndGet));
        rejected(() -> cache.submitWarehouseLoginJob(independent + 1, independent + 2, tail,
                blockedExecutions::incrementAndGet));
        rejected(() -> CacheD.beginMailEconomyMove(independent + 1, linked, new int[0]));
        CountDownLatch independentWrite = new CountDownLatch(1);
        cache.submitDbJobOrdered(independent, independentWrite::countDown);
        check(cache.flushDbOwner(independent, 1000) && independentWrite.getCount() == 0,
                "independent owners still progress");
        check(!cache.flushDbOwner(tail, 0) && blockedExecutions.get() == 0,
                "pre-admitted work stays in order behind the unresolved transaction");
        Object monitor = field(CacheD.class, "lock").get(cache);
        synchronized (monitor) {
            check(field(CacheD.class, "pendingDbJobs").getInt(cache) == 3,
                    "only unresolved and pre-admitted work remains");
        }
    }

    private static CountDownLatch awaitQuarantineEvent() {
        CountDownLatch quarantined = new CountDownLatch(1);
        Logger logger = Logger.getLogger(CacheD.class.getName());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord event) {
                if (event.getMessage().contains("QUARANTINED")) quarantined.countDown();
            }
            public void flush() {}
            public void close() {}
        });
        return quarantined;
    }

    private static Connection connect() {
        return proxy(Connection.class, (obj, method, args) -> switch (method.getName()) {
            case "close" -> null;
            case "prepareStatement" -> statement((String) args[0]);
            default -> throw new AssertionError(method);
        });
    }

    private static PreparedStatement statement(String sql) {
        ArrayList<String> tables = new ArrayList<>();
        int[] boundOwner = new int[1];
        return proxy(PreparedStatement.class, (obj, method, args) -> switch (method.getName()) {
            case "close" -> null;
            // The synthetic enchant-only update binds owner last.
            case "setInt" -> { boundOwner[0] = (Integer) args[1]; yield null; }
            case "setTimestamp" -> null;
            case "setString" -> { tables.add((String) args[1]); yield null; }
            case "executeQuery" -> rows(sql.contains("information_schema.TABLES") ? tables : new ArrayList<>(java.util.List.of("fixture")));
            case "executeUpdate" -> {
                IntConsumer callback = onItemWrite;
                if (callback != null) callback.accept(boundOwner[0]);
                writes.incrementAndGet();
                yield 1;
            }
            default -> throw new AssertionError(method);
        });
    }

    private static ResultSet rows(ArrayList<String> tables) {
        AtomicInteger row = new AtomicInteger(-1);
        return proxy(ResultSet.class, (obj, method, args) -> switch (method.getName()) {
            case "close" -> null;
            case "next" -> row.incrementAndGet() < tables.size();
            case "getString" -> (Integer) args[0] == 1 ? tables.get(row.get()) : "InnoDB";
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(Object target, String name, Class<?> argType, Object arg) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, argType);
        method.setAccessible(true);
        method.invoke(target, arg);
    }

    private static void rejected(Runnable action) {
        try { action.run(); }
        catch (RejectedExecutionException expected) { return; }
        throw new AssertionError("expected owner quarantine rejection");
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(2, TimeUnit.SECONDS), "latch tripped"); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
