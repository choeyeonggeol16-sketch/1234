import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.HpMpGrowthHistory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Synthetic open-loop SQL/CacheD benchmark. Requires a disposable MariaDB service
 * and a reference schema containing the current Net DDL. Reference rows are never copied.
 * Set RKERN_BENCH_JDBC, RKERN_BENCH_USER, RKERN_BENCH_PASSWORD (optional), and
 * RKERN_BENCH_REFERENCE_SCHEMA. Arguments: offered operations/s and measured sample count.
 * Use -Drkern.bench.injectRowLock=false to repeat the same workload without the
 * intentional 150 ms row lock. Use -Drkern.bench.readAdmission=side to compare
 * independent SQL readers to the default CacheD login and world-load admissions.
 * The fixture creates and removes a random synthetic schema.
 */
public final class CacheDNetMixedLoad {
    static final int BASE = 1957200000;
    static final int OWNERS = 1200;
    static final int USERS = 64;
    static final int FIRST_BALANCE = 100_000;
    static final long DATE = Instant.parse("2026-11-26T00:00:00Z").toEpochMilli();
    static final int WARM_OPERATIONS = 800;
    static final int ITEMS_PER_OWNER = 16;
    static final int KEYS_PER_OWNER = 4;
    static final String[] KIND = {"receipt_disjoint", "receipt_conflict", "account", "character", "login_read", "inventory_read"};
    static final Set<String> EXPECTED_RECEIPTS = new HashSet<>();
    static final MySqlCharactersItemStorage ITEM_STORAGE = new MySqlCharactersItemStorage();
    static String encodedGrowth;
    static int readerCount;
    static int readOrdinal;
    static final String[] TABLES = {
        "cached_transaction_receipts", "accounts", "characters", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse", "character_supplementary_service",
        "character_quests", "character_level_gifts", "character_pending_credits", "item_key_boss", "etcitem", "weapon", "armor"
    };

    static HikariDataSource db;
    static CacheD cache;
    static final int[] expected = new int[OWNERS];
    static final long[] accountExpected = new long[USERS];
    static final String[] titleExpected = new String[USERS];
    static final L1PcInstance[] pcs = new L1PcInstance[USERS];
    static final ConcurrentLinkedQueue<Long> signalGaps = new ConcurrentLinkedQueue<>();
    static final AtomicLong lastSignal = new AtomicLong();
    static final AtomicReference<Throwable> failure = new AtomicReference<>();
    static final long seed = 20261126L;
    static final boolean INJECT_ROW_LOCK = Boolean.parseBoolean(System.getProperty("rkern.bench.injectRowLock", "true"));
    static final boolean NATIVE_READS = !"side".equals(System.getProperty("rkern.bench.readAdmission", "native"));

    public static void main(String[] argv) throws Exception {
        int rate = Integer.parseInt(argv[0]);
        int count = Integer.parseInt(argv[1]);
        require(rate > 0 && count >= 100, "specify a positive rate and at least 100 measured operations");
        if (NATIVE_READS) readerCount = plannedReads(WARM_OPERATIONS, false) + plannedReads(count, true);
        String jdbc = Objects.requireNonNull(System.getenv("RKERN_BENCH_JDBC"), "RKERN_BENCH_JDBC");
        String user = Objects.requireNonNull(System.getenv("RKERN_BENCH_USER"), "RKERN_BENCH_USER");
        String password = System.getenv().getOrDefault("RKERN_BENCH_PASSWORD", "");
        String reference = Objects.requireNonNull(System.getenv("RKERN_BENCH_REFERENCE_SCHEMA"), "RKERN_BENCH_REFERENCE_SCHEMA");
        require(reference.matches("[0-9A-Za-z_]+"), "invalid reference schema name");
        String schema = "rkern_load_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        require(!schema.equals(reference), "reference cannot be the synthetic schema");
        Config.CACHE_MAP_FILES = true;
        Config.TIME_ZONE = "UTC";
        try (var admin = DriverManager.getConnection(jdbc, user, password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE `" + schema + "` DEFAULT CHARACTER SET utf8mb4");
            Path etc = null;
            Path equip = null;
            try {
                for (String table : TABLES) ddl.execute("CREATE TABLE `" + schema + "`.`" + table
                        + "` LIKE `" + reference + "`.`" + table + "`");
                etc = Files.createTempFile("rkern-load-etc", ".txt");
                equip = Files.createTempFile("rkern-load-equipment", ".txt");
                Files.writeString(etc, "40001\n80500\n");
                System.setProperty("rkern.etc.base", etc.toString());
                System.setProperty("rkern.equipment.base", equip.toString());
                var config = new HikariConfig();
                config.setJdbcUrl(jdbc);
                config.setUsername(user);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(8);
                config.setMinimumIdle(8);
                config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
                try (var source = new HikariDataSource(config)) {
                    db = source;
                    var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, source);
                    try (var conn = source.getConnection()) {
                        require(schema.equals(conn.getCatalog()), "pool selected only its scratch schema");
                    }
                    CTransaction.verifySchema();
                    seedDatabase();
                    require(CObjectDB.GetItem(80500) != null, "synthetic item prototypes loaded");
                    exercise(count, rate);
                }
            } finally {
                if (etc != null) Files.deleteIfExists(etc);
                if (equip != null) Files.deleteIfExists(equip);
                ddl.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    private static void exercise(int count, int rate) throws Exception {
        cache = CacheD.getInstance();
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final ExecutorService saves = Executors.newFixedThreadPool(8);
        final ExecutorService reads = Executors.newFixedThreadPool(4);
        final CServerTimer signal = CServerTimer.fixedDelaySignal(() -> {
            if (CIOThreadCallback.currentShardIndex() != 0) {
                failure.compareAndSet(null, new AssertionError("signal outside CIO"));
                return;
            }
            long now = System.nanoTime(), prior = lastSignal.getAndSet(now);
            if (prior != 0) signalGaps.add(now - prior);
        }, 1L, 50L);
        require(signal != null, "timer admitted");
        try {
            System.out.printf("PROFILE read_admission=%s warm_ops=%d reader_identities=%d%n",
                    NATIVE_READS ? "native" : "side", WARM_OPERATIONS, readerCount);
            run("warm", WARM_OPERATIONS, 400, false, saves, reads);
            signalGaps.clear();
            long deadlocksBefore = globalStatus("Innodb_deadlocks");
            long lockWaits = globalStatus("Innodb_row_lock_waits");
            long lockMillis = globalStatus("Innodb_row_lock_time");
            long selectCount = globalStatus("Com_select");
            long writeCount = globalStatus("Com_update");
            long commitCount = globalStatus("Com_commit");
            long optionCount = globalStatus("Com_set_option");
            run("mixed", count, rate, true, saves, reads);
            if (failure.get() != null) throw new AssertionError("worker failure", failure.get());
            require(cache.flushAll(10_000L), "all durable work flushed");
            require(cache.pendingAccounts() == 0 && cache.pendingCharacters() == 0 && cache.pendingItems() == 0,
                    "all pending counters drain");
            long readsDone = globalStatus("Com_select") - selectCount;
            long updatesDone = globalStatus("Com_update") - writeCount;
            long commitsDone = globalStatus("Com_commit") - commitCount;
            long optionsDone = globalStatus("Com_set_option") - optionCount;
            long waitsDone = globalStatus("Innodb_row_lock_waits") - lockWaits;
            long waitMillis = globalStatus("Innodb_row_lock_time") - lockMillis;
            for (int i = 0; i < USERS; i++) cache.requestCharacterItemOwnerRelease(actor(i));
            require(readOrdinal == readerCount, "all planned reader identities consumed exactly once");
            for (int i = 0; i < readerCount; i++) cache.requestCharacterItemOwnerRelease(readActor(i));
            verifyDurableState();
            verifyNoReadyRetention();
            stats("cio_signal_gap", signalGaps.stream().mapToLong(Long::longValue).toArray());
            require(signalGaps.size() >= 8, "CIO signals continued during locked SQL");
            long deadlocks = globalStatus("Innodb_deadlocks") - deadlocksBefore;
            require(deadlocks == 0, "no added InnoDB deadlocks");
            require(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "no JVM deadlock");
            System.out.printf("CHECKS exact_receipts=%d pending_db=%d pending_accounts=%d pending_characters=%d pending_items=%d innodb_deadlocks=%d jvm_deadlocks=0%n",
                    EXPECTED_RECEIPTS.size(), cache.pendingDbJobs(), cache.pendingAccounts(), cache.pendingCharacters(), cache.pendingItems(), deadlocks);
            System.out.printf("DB_WORK selects=%d updates=%d commits=%d set_options=%d row_lock_waits=%d row_lock_wait_ms=%d%n",
                    readsDone, updatesDone, commitsDone, optionsDone, waitsDone, waitMillis);
        } finally {
            signal.cancel();
            saves.shutdownNow();
            reads.shutdownNow();
            require(saves.awaitTermination(5, TimeUnit.SECONDS) && reads.awaitTermination(5, TimeUnit.SECONDS), "observers stopped");
            require(cache.shutdown(10_000L), "CacheD workers joined after durable drain");
            cio.shutdown();
            require(cio.awaitTermination(5L, TimeUnit.SECONDS), "CIO shards joined");
        }
    }

    static String name(int i) { return "cb_user" + i; }
    static int actor(int i) { return BASE + OWNERS + i; }
    static String readName(int i) { return "cb_reader" + i; }
    static int readActor(int i) { return actor(USERS) + i; }

    private static int operationKind(int ordinal, int draw, boolean measured) {
        if (measured && ordinal == 0) return 1;
        return draw < 37 ? 0 : draw < 50 ? 1 : draw < 62 ? 2 : draw < 74 ? 3 : draw < 86 ? 4 : 5;
    }

    private static int plannedReads(int count, boolean measured) {
        var rng = new SplittableRandom(seed + (measured ? 0 : 1));
        int readers = 0;
        for (int i = 0; i < count; i++) {
            if (operationKind(i, rng.nextInt(100), measured) >= 4) readers++;
            rng.nextInt(USERS);
        }
        return readers;
    }

    static void seedDatabase() throws Exception {
        byte[][] cells = new byte[8][8];
        for (byte[] row : cells) Arrays.fill(row, (byte) 3);
        var map = new L1V1Map(3, cells, 32, 32, false,true,true,true,true,true,true,true,true,true,true,false);
        Arrays.fill(expected, FIRST_BALANCE);
        try (var c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE bench_owner_state (owner_id INT PRIMARY KEY,balance INT NOT NULL) ENGINE=InnoDB");
            s.execute("INSERT INTO etcitem(item_id,name,name_id,item_type,use_type,material) VALUES "
                    + "(40001,'Fixture token','fixture_token','questitem','none','none'),"
                    + "(80500,'Fixture key','fixture_key','questitem','none','none')");
            s.execute("INSERT INTO character_items(id,char_id,item_id,item_name,count) SELECT 100000+seq,"
                    + actor(0) + "+(seq-1) DIV " + ITEMS_PER_OWNER + ",IF((seq-1) MOD " + ITEMS_PER_OWNER + "<"
                    + KEYS_PER_OWNER + ",80500,40001),'Synthetic load item',1 FROM seq_1_to_"
                    + ((USERS + readerCount) * ITEMS_PER_OWNER));
            s.execute("INSERT INTO item_key_boss(item_obj_id,key_id) SELECT id,601 FROM character_items WHERE item_id=80500");
            var growth = new HpMpGrowthHistory(40);
            for (int level = 41; level <= 80; level++) growth.record(level, 10 + level % 3, 4 + level % 2);
            encodedGrowth = growth.encode();
            s.execute("INSERT INTO bench_owner_state SELECT " + BASE + "+seq-1," + FIRST_BALANCE + " FROM seq_1_to_1200");
            try (var a = c.prepareStatement("INSERT INTO accounts(login,charslot,gamepassword,Shop_open_count) VALUES(?,8,0,0)");
                 var ch = c.prepareStatement("INSERT INTO characters (objid,char_name,account_name,Clanname,notes,ReturnStat) VALUES (?,?,?,'','',0)")) {
                for (int i = 0; i < USERS; i++) {
                    String name = name(i);
                    a.setString(1, name);
                    a.addBatch();
                    ch.setInt(1, actor(i));
                    ch.setString(2, name);
                    ch.setString(3, name);
                    ch.addBatch();
                    var pc = new L1PcInstance();
                    pc.setId(actor(i));
                    pc.setName(name);
                    pc.setAccountName(name);
                    pc.setMap(map);
                    pc.setX(35);
                    pc.setY(35);
                    pc.setTitle("");
                    pc.setClanname("");
                    pc.setClanMemberNotes("");
                    pc.setLevel(80);
                    pc.setFishingShopBuyTime_1(DATE);
                    pc.restoreHpMpGrowth(encodedGrowth);
                    pcs[i] = pc;
                    titleExpected[i] = "";
                }
                for (int i = 0; i < readerCount; i++) {
                    String name = readName(i);
                    a.setString(1, name);
                    a.addBatch();
                    ch.setInt(1, readActor(i));
                    ch.setString(2, name);
                    ch.setString(3, name);
                    ch.addBatch();
                }
                a.executeBatch();
                ch.executeBatch();
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void run(String label, int count, int rate, boolean blockSql, ExecutorService saves, ExecutorService reads) throws Exception {
        final var rng = new SplittableRandom(seed + (blockSql ? 0 : 1));
        final ConcurrentLinkedQueue<Long>[] latencies = new ConcurrentLinkedQueue[KIND.length];
        Arrays.setAll(latencies, i -> new ConcurrentLinkedQueue<>());
        final ConcurrentLinkedQueue<Long> receiptService = new ConcurrentLinkedQueue<>();
        final CountDownLatch done = new CountDownLatch(count);
        final long[] dueAt = new long[count], arrivedAt = new long[count], admittedAt = new long[count],
            workerAt = new long[count], mutationAt = new long[count], mutationDoneAt = new long[count],
            durableAt = new long[count], completedAt = new long[count];
        final int[] types = new int[count];
        int maxPending = 0;
        int maxCacheD = 0;
        int rejected = 0;
        final var failed = new AtomicInteger();
        final long period = 1_000_000_000L / rate;
        Thread release = null;
        boolean holdRow = blockSql && INJECT_ROW_LOCK;
        try (var held = holdRow ? db.getConnection() : null) {
            final long start = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(40);
            if (holdRow) {
                held.setAutoCommit(false);
                try (var lock = held.prepareStatement("SELECT balance FROM bench_owner_state WHERE owner_id=? FOR UPDATE")) {
                    lock.setInt(1, BASE);
                    try (var row = lock.executeQuery()) { require(row.next(), "held SQL row exists"); }
                }
                final long releaseAt = start + TimeUnit.MILLISECONDS.toNanos(150);
                release = Thread.ofPlatform().daemon().name("bench-lock-release").start(() -> {
                    awaitArrival(releaseAt);
                    try { held.rollback(); }
                    catch (SQLException e) { failure.compareAndSet(null, e); }
                });
            }
            for (int i = 0; i < count; i++) {
                final long due = start + i*period;
                final int ordinal = i;
                final int kind = operationKind(i, rng.nextInt(100), blockSql);
                final int user = rng.nextInt(USERS);
                types[ordinal] = kind;
                dueAt[ordinal] = due;
                awaitArrival(due);
                arrivedAt[ordinal] = System.nanoTime();
                final Runnable completion = () -> {
                    completedAt[ordinal] = System.nanoTime();
                    latencies[kind].add(completedAt[ordinal] - due);
                    done.countDown();
                };
                try {
                    if (kind <= 1) {
                        final int x = kind == 0 ? 1 + (i % 512) * 2 : 0;
                        final int y = kind == 0 ? x + 1 : 1025 + (i % 128);
                        --expected[x];
                        ++expected[y];
                        final String tx = new UUID(blockSql ? 49 : 17, 1L+i).toString();
                        require(EXPECTED_RECEIPTS.add(tx), "receipt identifier unique");
                        cache.submitDbJobOrdered(BASE+x, BASE+y, () -> {
                            final long serviceStart = workerAt[ordinal] = System.nanoTime();
                            try {
                                CTransaction.persistEconomy(tx, BASE+x, BASE+y, new CTransaction.Mutation[]{
                                    connection -> {
                                        mutationAt[ordinal] = System.nanoTime();
                                        try {
                                            transfer(connection, BASE+x, BASE+y);
                                        } finally {
                                            mutationDoneAt[ordinal] = System.nanoTime();
                                        }
                                    }
                                });
                                durableAt[ordinal] = System.nanoTime();
                            } catch (Throwable e) {
                                failed.incrementAndGet();
                                failure.compareAndSet(null, e);
                            } finally {
                                receiptService.add(System.nanoTime() - serviceStart);
                                completion.run();
                            }
                        });
                        admittedAt[ordinal] = System.nanoTime();
                    } else if (kind <= 3) {
                        final String name = name(user);
                        if (kind == 2) {
                            long time = DATE + (blockSql ? 60_000_000L : 0L) + 1_000L*ordinal;
                            cache.saveRaidBuff(name, time);
                            accountExpected[user] = time;
                        } else {
                            String title = (blockSql ? "measure-" : "warm-") + ordinal;
                            pcs[user].setTitle(title);
                            if (!cache.saveCharacter(new CharacterSnapshot(pcs[user]))) {
                                throw new RejectedExecutionException("fixture character snapshot not accepted");
                            }
                            titleExpected[user] = title;
                        }
                        admittedAt[ordinal] = System.nanoTime();
                        saves.execute(() -> {
                            workerAt[ordinal] = System.nanoTime();
                            try {
                                require(kind == 2 ? cache.flushAccount(name, 10_000L) : cache.flushCharacter(name, 10_000L),
                                        "save not durable: " + KIND[kind]);
                                durableAt[ordinal] = System.nanoTime();
                            } catch (Throwable e) {
                                failed.incrementAndGet();
                                failure.compareAndSet(null, e);
                            } finally {
                                completion.run();
                            }
                        });
                    } else {
                        final int reader = NATIVE_READS ? readOrdinal++ : user;
                        final String accountName = NATIVE_READS ? readName(reader) : name(reader);
                        final int characterId = NATIVE_READS ? readActor(reader) : actor(reader);
                        admittedAt[ordinal] = System.nanoTime();
                        Runnable read = () -> {
                            workerAt[ordinal] = System.nanoTime();
                            try {
                                if (kind == 4) verifyLoginRead(accountName);
                                else verifyInventoryRead(characterId);
                                durableAt[ordinal] = System.nanoTime();
                            } catch (Throwable e) {
                                failed.incrementAndGet();
                                failure.compareAndSet(null, e);
                            } finally {
                                completion.run();
                            }
                        };
                        if (!NATIVE_READS) reads.execute(read);
                        else if (kind == 4) cache.submitDbJobOrdered(accountName, read);
                        else cache.submitWarehouseLoginJob(characterId, CacheD.accountOrderKey(accountName),
                                CacheD.accountOrderKey("special:" + accountName), read);
                    }
                } catch (RejectedExecutionException rejectedAdmission) {
                    rejected++;
                    failure.compareAndSet(null, rejectedAdmission);
                    done.countDown();
                }
                maxPending = Math.max(maxPending, i + 1 - (count - (int) done.getCount()));
                if ((i & 31) == 0) maxCacheD = Math.max(maxCacheD, cache.pendingDbJobs());
            }
            require(done.await(15, TimeUnit.SECONDS), "timed out draining measured workload");
            System.out.printf("OUTCOMES %s attempted=%d admitted=%d successes=%d rejects=%d failures=%d%n",
                    label, count, count - rejected, count - rejected - failed.get(), rejected, failed.get());
            if (failure.get() != null) throw new AssertionError("load rejected/failed", failure.get());
            final long elapsed = System.nanoTime()-start;
            System.out.printf(Locale.ROOT,"PHASE %s operations=%d planned_rate=%d achieved_per_second=%.1f lock_hold_ms=%d%n",
                    label,count,rate,count/(elapsed/1_000_000_000.0),holdRow ? 150 : 0);
            for (int i=0; i<KIND.length; i++) stats(label+"_"+KIND[i], latencies[i].stream().mapToLong(Long::longValue).toArray());
            stats(label+"_receipt_worker", receiptService.stream().mapToLong(Long::longValue).toArray());
            System.out.printf("PENDING %s high_water_accepted_incomplete=%d sampled_cached_db=%d final=%d%n",
                    label, maxPending, maxCacheD, done.getCount());
            if (release != null) release.join(1_000);
            if (blockSql) {
                recordTrace(Path.of(System.getProperty("trace.file", "mixed.csv")), types, dueAt, arrivedAt,
                        admittedAt, workerAt, mutationAt, mutationDoneAt, durableAt, completedAt);
            }
        }
    }

    private static void verifyLoginRead(String name) {
        Account account = Account.load(name);
        require(account != null && name.equals(account.getName()), "login storage returned its account");
    }

    private static void verifyInventoryRead(int characterId) throws Exception {
        var items = ITEM_STORAGE.loadItems(characterId);
        require(items.size() == ITEMS_PER_OWNER, "complete inventory snapshot loaded");
        int keys = 0;
        for (var item : items) {
            require(item.getCount() == 1, "inventory quantity intact");
            if (item.getItemId() == 80500) {
                require(item.getKeyId() == 601, "boss key lookup intact");
                keys++;
            }
        }
        require(keys == KEYS_PER_OWNER, "all boss keys restored");
    }

    static final String[] STAGES = {"arrival","admission","queue","worker","pre_mutation","sql_mutation","post_mutation",
            "local_ack","total"};

    static void recordTrace(Path file, int[] kinds, long[] due, long[] arrived, long[] admitted, long[] worker,
            long[] begin, long[] finish, long[] durable, long[] complete) throws Exception {
        long[][] stages = new long[kinds.length][STAGES.length];
        try (var out = Files.newBufferedWriter(file)) {
            out.write("id,kind,scheduled_ns,actual_arrival_ns," + String.join(",", STAGES) + "\n");
            for (int i=0; i<kinds.length; i++) {
                long[] st = stages[i];
                st[0] = arrived[i] - due[i];
                st[1] = admitted[i] - arrived[i];
                st[2] = Math.max(0L, worker[i] - admitted[i]);
                st[3] = durable[i] - worker[i];
                st[4] = begin[i] == 0 ? 0 : begin[i] - worker[i];
                st[5] = begin[i] == 0 ? 0 : finish[i] - begin[i];
                st[6] = begin[i] == 0 ? 0 : durable[i] - finish[i];
                st[7] = complete[i] - durable[i];
                st[8] = complete[i] - due[i];
                for (long x : st) require(x >= 0, "stage timestamps in order");
                out.write(i + "," + KIND[kinds[i]] + "," + (due[i] - due[0]) + "," + (arrived[i] - due[0]));
                for (long x : st) out.write("," + x);
                out.write("\n");
            }
        }
        for (int k=0; k<KIND.length; k++) {
            final int kind = k;
            final int[] rows = java.util.stream.IntStream.range(0, kinds.length)
                    .filter(i -> kinds[i] == kind).boxed()
                    .sorted(java.util.Comparator.<Integer>comparingLong(i -> stages[i][8]).reversed())
                    .mapToInt(Integer::intValue).toArray();
            long serviceNanos = Arrays.stream(rows).mapToLong(i -> stages[i][3]).sum();
            long mutationNanos = Arrays.stream(rows).mapToLong(i -> stages[i][5]).sum();
            System.out.printf(Locale.ROOT, "SQL_WALL %s n=%d worker_total_ms=%.3f mutation_total_ms=%.3f%n",
                    KIND[k], rows.length, serviceNanos / 1e6, mutationNanos / 1e6);
            final int tail = Math.max(1,(int)Math.ceil(rows.length*.01));
            for (int j=0; j<STAGES.length; j++) {
                final int column = j;
                final long[] values = Arrays.stream(rows).mapToLong(i -> stages[i][column]).toArray();
                final long[] tailValues = Arrays.copyOf(values, tail);
                Arrays.sort(values);
                Arrays.sort(tailValues);
                if (j == 4 || j == 5 || j == 6) {
                    if (kind > 1) continue;
                }
                System.out.printf(Locale.ROOT,
                    "TAIL %s %s n=%d p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f tail_n=%d tail_median_ms=%.3f%n",
                    KIND[k], STAGES[j], values.length, q(values,.50), q(values,.95), q(values,.99),
                    tail, q(tailValues,.50));
            }
        }
    }

    static void transfer(Connection c, int x, int y) throws SQLException {
        try (PreparedStatement update = c.prepareStatement("UPDATE bench_owner_state SET balance=balance+? WHERE owner_id=?")) {
            update.setInt(1, -1);
            update.setInt(2, x);
            require(update.executeUpdate() == 1, "debit row exists");
            update.setInt(1, 1);
            update.setInt(2, y);
            require(update.executeUpdate() == 1, "credit row exists");
        }
    }

    static long scalar(String sql) throws SQLException {
        try (var c = db.getConnection(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
            require(r.next(), "scalar exists");
            return r.getLong(1);
        }
    }

    static long globalStatus(String variable) throws SQLException {
        try (var c = db.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name='" + variable + "'")) {
            require(r.next(), "MariaDB exposes " + variable);
            return r.getLong(2);
        }
    }

    static void verifyNoReadyRetention() throws ReflectiveOperationException {
        synchronized (field(cache, "lock")) {
            require((int) field(cache, "pendingDbJobs") == 0 && (int) field(cache, "activeDbJobs") == 0,
                    "no unfinished CacheD DB job after drain");
            require(((java.util.Map<?,?>) field(cache, "dbJobsByOwner")).isEmpty(),
                    "owner DB job index releases all entries");
            Object ready = field(cache, "ready");
            require(field(ready, "head") == null && field(ready, "tail") == null
                            && (int) field(ready, "count") == 0, "ready FIFO has no retained node");
            for (String name : new String[] {"owners","itemsById"})
                require(((java.util.Map<?,?>) field(ready, name)).isEmpty(), "ready lane index drained: " + name);
            require(((java.util.Map<?,?>) field(cache, "itemOwners")).isEmpty(), "synthetic logout released item owner index");
            for (String name : new String[] {"readyJobs","readyOther","sleeping"})
                require(((java.util.Collection<?>) field(ready, name)).isEmpty(), "candidate index drained: " + name);
        }
        System.out.println("RETENTION ready_fifo=0 ready_candidates=0 ready_lanes=0 cached_db_job_owners=0");
    }

    static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    static void verifyDurableState() throws SQLException {
        long receiptCount = 0;
        for (int i = 0; i < 1025; ++i) {
            if (i == 0 || (i & 1) != 0) receiptCount += FIRST_BALANCE - expected[i];
        }
        require(receiptCount == EXPECTED_RECEIPTS.size(), "expected debit counts and exact receipt IDs agree");
        Set<String> remaining = new HashSet<>(EXPECTED_RECEIPTS);
        try (var c = db.getConnection(); var s = c.createStatement();
             var rows = s.executeQuery("SELECT tx_id FROM cached_transaction_receipts")) {
            while (rows.next()) require(remaining.remove(rows.getString(1)), "unknown or duplicate persisted receipt");
        }
        require(remaining.isEmpty(), "every exact receipt durable once");
        require(scalar("SELECT COUNT(*) FROM (SELECT tx_id, LAG(tx_id) OVER "
                + "(PARTITION BY owner_a ORDER BY committed_at,tx_id) AS prior_receipt "
                + "FROM cached_transaction_receipts) ordered_receipts WHERE prior_receipt > tx_id") == 0,
                "same-owner receipts committed in admission order");
        try (var c = db.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT owner_id,balance FROM bench_owner_state ORDER BY owner_id")) {
            for (int x=0; x<OWNERS; x++) {
                require(r.next() && r.getInt(1) == BASE+x && r.getInt(2) == expected[x], "receipt balance per owner");
            }
            require(!r.next(), "no extra owner row");
        }
        try (var c = db.getConnection(); var a = c.prepareStatement("SELECT UNIX_TIMESTAMP(raid_buff)*1000 FROM accounts WHERE login=?");
             var ch = c.prepareStatement("SELECT Title,hp_mp_growth FROM characters WHERE objid=?")) {
            for (int i=0; i<USERS; i++) {
                a.setString(1, name(i));
                try (var row = a.executeQuery()) {
                    require(row.next() && row.getLong(1) == accountExpected[i], "final account write durable");
                }
                ch.setInt(1, actor(i));
                try (var row = ch.executeQuery()) {
                    require(row.next(), "character row exists");
                    final String growth = row.getString(2);
                    if (!titleExpected[i].isEmpty()) require(encodedGrowth.equals(growth), "growth ledger round-tripped");
                    final String actual = row.getString(1);
                    require(java.util.Objects.equals(actual, titleExpected[i]),
                            "final character snapshot durable at #" + i + ": expected '" + titleExpected[i]
                            + "', actual '" + actual + "'");
                }
            }
        }
    }

    static void awaitArrival(long due) {
        for (long left; (left=due-System.nanoTime())>0; ) LockSupport.parkNanos(left);
    }

    static void stats(String label, long[] values) {
        Arrays.sort(values);
        if (values.length == 0) throw new AssertionError("no samples: "+label);
        System.out.printf(Locale.ROOT, "LATENCY %s n=%d p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f max_ms=%.3f%n",
                label, values.length, q(values,.5), q(values,.95), q(values,.99), q(values,1));
    }

    static double q(long[] values,double p) { return values[(int)Math.ceil(p*values.length)-1]/1e6; }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
