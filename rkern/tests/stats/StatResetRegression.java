import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.HpMpGrowthHistory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_Craft;
import l1j.server.server.model.network.core.cs.packet.CS_ReturnStatus;
import l1j.server.server.model.network.core.sc.packet.SC_Chainfo;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.utils.CPCParamDB;
import l1j.server.server.utils.StatContext;

/** Reset packet handlers, encrypted status frames and persistence with three ready fixture actors.
 * Compile with CioSyntheticPopulation and SelectorTransportTest; pass disposable JDBC URL, user, reference
 * schema. Only schema definitions are reused and all records are synthetic.
 */
public final class StatResetRegression {
    private static final String[] TABLES = {
        "accounts", "characters", "cached_transaction_receipts", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "character_quests", "character_level_gifts", "mapids", "skills", "etcitem", "weapon", "armor",
        "tb_mjexpampsystem", "castle", "tb_dungeon_time_information"
    };
    private static final long BASE = packed(13, 9, 11, 9, 11, 13);
    private static final int[] BEFORE = {13, 9, 11, 19, 11, 13};
    private static final byte[] MARKER = {(byte) 0xfe, 0x19, 0x34, 0x7a, 0x17};
    private static final int STAT_DETAILS = 0xe3;
    private static final int STAT_REFRESH = 0xea;
    private static final Charset GAME_TEXT = Charset.forName("MS949");
    private static final String WARRIOR = "fixture_warrior";
    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(3);
    private final Set<CUserSocket> used = Collections.newSetFromMap(new IdentityHashMap<>());
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final HikariDataSource data;

    private record Peer(Socket wire, DataInputStream input, SelectorTransportTest.ScalarClient cipher,
            CUserSocket socket, L1PcInstance pc) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { wire.close(); }
    }

    private StatResetRegression(HikariDataSource data) { this.data = data; }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Expected isolated JDBC URL, user and reference schema");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_stat_reset_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : TABLES)
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                final var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(5);
                try (var data = new HikariDataSource(config)) {
                    field(DatabaseFactory.class, "DATABASE_POOL").set(null, data);
                    try { new StatResetRegression(data).run(); }
                    finally { field(DatabaseFactory.class, "DATABASE_POOL").set(null, null); }
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS stat reset: both encrypted completion paths, live/login/SQL AC and stat parity, "
                + "45/50 caps, durable ordering, SQL rollback and retry");
    }

    private void run() throws Exception {
        Config.CACHE_MAP_FILES = true;
        Config.RATE_WEIGHT_LIMIT = 1;
        Config.TIME_ZONE = "UTC";
        l1j.server.server.model.gametime.CGameTime.prepare();
        Config.MAX_ONLINE_USERS = 8;
        CPacketFilter.getInstance().load();
        try (var con = data.getConnection();
                var rows = con.prepareStatement("INSERT INTO characters(objid,char_name,account_name,Clanname,notes,ReturnStat) "
                        + "VALUES(?,?,?,'','',0)")) {
            for (int seat = 0; seat < 3; seat++) {
                rows.setInt(1, CioSyntheticPopulation.actorId(seat));
                rows.setString(2, CioSyntheticPopulation.characterName(seat));
                rows.setString(3, CioSyntheticPopulation.login(seat));
                rows.addBatch();
            }
            rows.executeBatch();
        }
        require(cache.flushAll(5_000L), "CacheD schema probe drained");
        final var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        int shards = cio.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(8, 16, 8, 8, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, cio, config)) {
            try (var elixir = peer(server, 0, 55, 1); var at89 = peer(server, 1, 89, 0);
                    var at90 = peer(server, 2, 90, 0)) {
                actors.assertReady(server);
                verifyElixir(elixir);
                verifyCap(at89, 45, true);
                verifyCap(at90, 50, false);
                require(cache.flushAll(5_000L), "final snapshots committed");
                verifySql(elixir, new int[] {15, 9, 12, 10, 11, 13}, 3, 1);
                verifySql(at89, new int[] {45, 9, 11, 10, 11, 13}, 33, 0);
                verifySql(at90, new int[] {50, 9, 11, 9, 11, 13}, 37, 0);
                verifyCharacterInfo(elixir, elixir.pc.getName(), "군주",
                        elixir.pc.getCurrentHp(), elixir.pc.getCurrentMp(), elixir.pc.getAc());
                seedWarriorSummary();
                verifyCharacterInfo(at89, WARRIOR, "전사", 87, 34, -29);
            }
            server.close();
            actors.assertDrained(server);
        } finally {
            CSessionLifecycle.stopAccepting();
            require(CSessionLifecycle.awaitDrained(5L, TimeUnit.SECONDS), "session workers drained");
            final boolean drained = cache.drainBeforeOwnerShutdown(5_000L);
            cio.shutdown();
            require(cio.awaitTermination(5L, TimeUnit.SECONDS), "CIO owners stopped");
            require(drained && cache.shutdown(5_000L), "CacheD drained");
        }
    }

    private void verifyElixir(Peer peer) throws Exception {
        final L1PcInstance pc = peer.pc;
        final int oldAc = pc.getAc();
        final var oldImage = new CharacterSnapshot(pc);
        final long accepted = packed(15, 9, 12, 10, 11, 13);
        var draft = capture(peer, () -> {
            final int[] before = stats(pc);
            final int oldHp = pc.getCurrentHp(), oldMp = pc.getCurrentMp();
            final int oldBaseHp = pc.getBaseMaxHp(), oldBaseMp = pc.getBaseMaxMp();

            pc.SetSkillPrimaryStats(127L | (long) (-128 & 255) << 32, false);
            var current = new StatContext(0, 1);
            current.setStat(1, 14);
            require(current.resolve(pc) && current.getStats()[0] == 127 && current.getStats()[4] == 0,
                    "effective previews use real total caps");
            pc.SetSkillPrimaryStats(0, false);

            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, packed(13, 9, 11, 19, 11, 13));
            require(pc.rst == null && peer.socket.isConnected(),
                    "over-cap base spread cannot allocate a draft or disconnect the actor");
            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, BASE);
            var preview = new StatContext(0, 1);
            preview.setStat(2, 18);
            require(preview.resolve(pc) && preview.getTargetType() == SC_CHAR_STAT.Wis
                    && preview.getStats()[0] == 13 && SC_CHAR_STAT.create(peer.socket, preview) != null,
                    "detached base values and normal preview packet");
            CS_Craft.handle(peer.socket, 228, 0, 1, 1 << 2, 18L << 16, 0L, null, null);
            require(Arrays.equals(stats(pc), before), "normal stat-preview handler leaves live attributes untouched");
            advanceTo(peer, 51);
            final var progressed = pc.rst;
            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, BASE);
            require(pc.rst == progressed && pc.rst.level == 51,
                    "repeat start leaves the active allocation and growth intact");
            step(peer, 1);
            step(peer, 1);
            advanceTo(peer, 55);
            CS_ReturnStatus.handle(peer.socket, 2, 8, 3, 0);
            CS_ReturnStatus.handle(peer.socket, 2, 8, 3, 0);
            require(pc.rst.awaitingElixirs && pc.rst.allocatedLevelPoints() == 3,
                    "resending completion preserves the frozen level allocation");

            require(Arrays.equals(stats(pc), before) && pc.getBonusStats() == 1
                    && pc.getCurrentHp() == oldHp && pc.getCurrentMp() == oldMp
                    && pc.getBaseMaxHp() == oldBaseHp && pc.getBaseMaxMp() == oldBaseMp,
                    "preview, level simulation and elixir prompt leave live stats and vitals unchanged");

            final var elixirStage = pc.rst;
            CS_ReturnStatus.handle(peer.socket, 3, 0, 0, packed(15, 9, 11, 9, 11, 13));
            require(pc.rst == elixirStage && Arrays.equals(stats(pc), before),
                    "invalid elixir selection leaves stage and live stats untouched");
            int token = pc.tryReserveItemTransfer();
            require(token != 0, "existing inventory work reserved");
            try {
                CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted);
                require(pc.rst != null && Arrays.equals(stats(pc), before),
                        "final selection waits behind the existing inventory gate");
            } finally { pc.endItemTransfer(token); }
        });
        var initial = draft.stream().filter(frame -> returned(frame, SC_ReturnedStat.LEVELUP)).toList();
        require(!initial.isEmpty(), "staged level-up status frames received");
        var previewAc = initial.getLast();
        require((previewAc[2] & 255) == pc.getLevel() && (previewAc[3] & 255) == pc.getLevel()
                && (short) littleShort(previewAc, 8) == CPCParamDB.물리방어력(9) - pc.getLevel() / 6,
                "reset preview projects DEX-derived AC for the displayed level");

        try (Connection guard = data.getConnection()) {
            guard.setAutoCommit(false);
            try {
                try (var lock = guard.prepareStatement("SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                    lock.setInt(1, pc.getId());
                    try (var row = lock.executeQuery()) {
                        require(row.next(), "fixture row locked");
                    }
                }
                require(readPendingReturnStat(peer) != 0, "reset marker present on the locked row");
                var pending = capture(peer, () -> CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted));
                verifyNoCompletion(pending, "blocked elixir reset");
                on(peer.socket, () -> require(pc.rst != null && pc.getReturnStat() != 0
                        && pc.getAc() == oldAc && Arrays.equals(stats(pc), BEFORE),
                        "closed SQL row preserves current AC and all six stats"));
                require(readPendingReturnStat(peer) != 0 && receiptCount(peer) == 0,
                        "reset marker still durable and no receipt published before commit");
                verifyNoCompletion(capture(peer, () -> CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted)),
                        "duplicate while SQL owns completion");
            } finally { guard.rollback(); }
        }

        awaitReset(peer);
        final int[] expected = {15, 9, 12, 10, 11, 13};
        verifyFinalFrames(pc, capture(peer, () -> {}), expected, oldAc, "elixir");
        var duplicate = capture(peer, () -> {
            require(!cache.saveCharacter(oldImage), "pre-confirmation snapshot cannot undo the confirmed reset");
            require(pc.rst == null && pc.getReturnStat() == 0 && pc.getBonusStats() == 3 && pc.getElixirStats() == 1,
                    "validated final elixir allocation consumes the stage and preserves unspent level points");
            require(Arrays.equals(stats(pc), new int[] {15, 9, 12, 10, 11, 13})
                    && pc.getCurrentHp() == pc.getMaxHp() && pc.getCurrentMp() == pc.getMaxMp(),
                    "commit applies staged level and elixir values and restores vitals");
            CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted);
            require(pc.getBonusStats() == 3 && Arrays.equals(stats(pc), new int[] {15, 9, 12, 10, 11, 13}),
                    "a repeated final confirmation has no effect");
        });
        verifyNoCompletion(duplicate, "already-completed reset");
    }

    private void verifyCap(Peer peer, int cap, boolean injectFailure) throws Exception {
        final L1PcInstance pc = peer.pc;
        final int oldAc = pc.getAc();
        final int finalChoice = injectFailure ? 4 : 0;
        final int expectedPoints = cap - 13 + (injectFailure ? 1 : 0);
        capture(peer, () -> {
            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, BASE);
            advanceTo(peer, 51);
            for (int spent = 13; spent < cap; spent++) step(peer, 1);
            final int level = pc.rst.level;
            step(peer, 1);
            require(pc.rst.level == level && pc.rst.basestr + pc.rst.str == cap && pc.GetStatStr() == BEFORE[0],
                    "selecting a capped stat does not spend a point or advance the staged level");
            advanceTo(peer, pc.getHighLevel());
        });

        if (injectFailure) {
            try (var con = data.getConnection(); var ddl = con.createStatement()) {
                ddl.execute("CREATE TRIGGER reject_fixture_reset BEFORE UPDATE ON characters FOR EACH ROW "
                        + "BEGIN IF OLD.objid=" + pc.getId() + " AND OLD.ReturnStat<>0 AND NEW.ReturnStat=0 "
                        + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reset rejected'; END IF; END");
                try {
                    var failed = new ArrayList<>(capture(peer, () -> CS_ReturnStatus.handle(peer.socket, 2, 8, finalChoice, 0)));
                    require(cache.flushAll(5_000L), "rejected reset completed without queue residue");
                    failed.addAll(capture(peer, () -> require(pc.rst != null && pc.getReturnStat() != 0
                            && pc.rst.allocatedLevelPoints() == expectedPoints
                            && pc.rst.basedex + pc.rst.dex == 10
                            && pc.getAc() == oldAc && Arrays.equals(stats(pc), BEFORE),
                            "confirmed SQL failure preserves stage, AC and live stats")));
                    verifyNoCompletion(failed, "rejected direct reset");
                    require(readPendingReturnStat(peer) != 0 && receiptCount(peer) == 0,
                            "SQL rollback keeps reset marker and leaves no claimed receipt");
                } finally {
                    ddl.execute("DROP TRIGGER reject_fixture_reset");
                }
            }
        }
        var outgoing = new ArrayList<>(capture(peer, () -> CS_ReturnStatus.handle(peer.socket, 2, 8, finalChoice, 0)));
        awaitReset(peer);
        outgoing.addAll(capture(peer, () -> require(pc.rst == null && pc.GetStatStr() == cap
                && pc.getBonusStats() == expectedPoints, "committed cap and actual allocation count")));
        verifyFinalFrames(pc, outgoing, new int[]{cap, 9, 11, injectFailure ? 10 : 9, 11, 13}, oldAc, "direct " + cap);
    }

    private static void advanceTo(Peer peer, int target) {
        int budget = 100;
        while (peer.pc.rst != null && peer.pc.rst.level < target && budget-- > 0)
            step(peer, peer.pc.rst.level <= target - 10 && peer.pc.rst.level <= 40 ? 7 : 0);
        require(peer.pc.rst != null && peer.pc.rst.level == target, "stage reached level " + target);
    }

    private static void step(Peer peer, int choice) {
        CS_ReturnStatus.handle(peer.socket, 2, choice, 0, 0);
    }

    private void verifySql(Peer peer, int[] expected, int levelPoints, int elixirs) throws Exception {
        try (var con = data.getConnection(); var ps = con.prepareStatement(
                "SELECT Str,Intel,Wis,Dex,Con,Cha,BonusStatus,ElixirStatus,ReturnStat,hp_mp_growth,"
                        + "level,MaxHp,MaxMp,CurHp,CurMp,Ac FROM characters WHERE objid=?")) {
            ps.setInt(1, peer.pc.getId());
            try (var row = ps.executeQuery()) {
                require(row.next(), "stored character found");
                for (int stat = 0; stat < expected.length; stat++)
                    require(row.getInt(stat + 1) == expected[stat], "stored stat " + stat);
                final HpMpGrowthHistory history = HpMpGrowthHistory.decode(row.getString(10));
                require(row.getInt(7) == levelPoints && row.getInt(8) == elixirs && row.getInt(9) == 0
                        && history != null && history.floorLevel() == 1 && history.known(peer.pc.getLevel()),
                        "coherent stored points, closure and HP/MP growth");
                final int derivedAc = CPCParamDB.물리방어력(row.getInt(4)) - row.getInt(11) / 6;
                require(row.getInt(16) == derivedAc && derivedAc == peer.pc.getAc()
                        && row.getInt(12) == peer.pc.getBaseMaxHp() && row.getInt(13) == peer.pc.getBaseMaxMp()
                        && row.getInt(14) == peer.pc.getCurrentHp() && row.getInt(15) == peer.pc.getCurrentMp(),
                        "durable Ac column, DEX/level and vitals agree with the already-visible owner");
            }
        }
        require(receiptCount(peer) == 1, "exactly one durable reset receipt");
        var restored = new MySqlCharacterStorage().loadCharacter(peer.pc.getName());
        require(restored != null && Arrays.equals(stats(restored), expected)
                && restored.getAc() == peer.pc.getAc() && restored.GetBasePDefend() == peer.pc.GetBasePDefend()
                && restored.GetMDefend() == peer.pc.GetMDefend()
                && restored.getMaxHp() == peer.pc.getMaxHp() && restored.getMaxMp() == peer.pc.getMaxMp()
                && restored.getCurrentHp() == peer.pc.getCurrentHp() && restored.getCurrentMp() == peer.pc.getCurrentMp(),
                "fresh production login loader resolves the same AC, MR, stats and vitals");
    }

    private void seedWarriorSummary() throws Exception {
        try (var con = data.getConnection(); var row = con.prepareStatement(
                "INSERT INTO characters (objid,char_name,account_name,Clanname,notes,Type,Class,"
                        + "CurHp,CurMp,Ac,level,Exp,ReturnStat) VALUES (?,?,?,'','',7,12490,87,34,-29,55,?,0)")) {
            row.setInt(1, CioSyntheticPopulation.actorId(20));
            row.setString(2, WARRIOR);
            row.setString(3, WARRIOR);
            row.setInt(4, ExpTable.getExpByLevel(55));
            require(row.executeUpdate() == 1, "offline warrior summary fixture seeded");
        }
    }

    private void verifyCharacterInfo(Peer reader, String name, String characterClass, int hp, int mp, int ac)
            throws Exception {
        final int number = 0x743;
        var frames = new ArrayList<>(capture(reader, () -> require(SC_Chainfo.send(reader.pc, number, name),
                "DB-ordered character info request accepted")));
        require(cache.flushAll(5_000L), "character info storage lookup drained");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (true) {
            for (byte[] frame : frames) {
                if (!opcode(frame, Opcodes.S_BOARD_READ) || littleInt(frame, 1) != number) continue;
                String content = new String(frame, 5, frame.length - 5, GAME_TEXT);
                require(content.contains("클래스: " + characterClass)
                        && content.contains("HP:" + hp + "  MP:" + mp + " AC:" + ac),
                        "encrypted character-info board shows mapped class and current HP/MP/AC");
                return;
            }
            require(System.nanoTime() < deadline, "missing character information board for " + name);
            Thread.sleep(10);
            frames = new ArrayList<>(capture(reader, () -> {}));
        }
    }

    private int readPendingReturnStat(Peer peer) throws Exception {
        try (var con = data.getConnection(); var ps = con.prepareStatement(
                "SELECT ReturnStat FROM characters WHERE objid=?")) {
            ps.setInt(1, peer.pc.getId());
            try (var row = ps.executeQuery()) {
                require(row.next(), "current reset row present");
                return row.getInt(1);
            }
        }
    }

    private int receiptCount(Peer peer) throws Exception {
        try (var con = data.getConnection(); var ps = con.prepareStatement(
                "SELECT COUNT(*) FROM cached_transaction_receipts WHERE owner_a=? OR owner_b=?")) {
            ps.setInt(1, peer.pc.getId());
            ps.setInt(2, peer.pc.getId());
            try (var row = ps.executeQuery()) {
                require(row.next(), "fixture receipt count");
                return row.getInt(1);
            }
        }
    }

    private void awaitReset(Peer peer) throws Exception {
        require(cache.flushAll(5_000L), "reset durable write and owner application drained");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            final var done = new CompletableFuture<Boolean>();
            on(peer.socket, () -> done.complete(peer.pc.rst == null && peer.pc.getReturnStat() == 0));
            if (done.join()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("durable stat reset did not apply on the active owner");
    }

    private static void verifyNoCompletion(List<byte[]> frames, String step) {
        for (byte[] frame : frames) {
            require(!opcode(frame, Opcodes.S_STATUS) && !opcode(frame, Opcodes.S_ABILITY_SCORES)
                    && !opcode(frame, Opcodes.S_AC) && !extended(frame, STAT_REFRESH) && !resetComplete(frame),
                    step + " emitted a final stat/AC frame before a durable commit");
        }
    }

    private static void verifyFinalFrames(L1PcInstance pc, List<byte[]> frames, int[] expected,
            int oldAc, String step) {
        final int actualAc = CPCParamDB.물리방어력(expected[3]) - pc.getLevel() / 6;
        require(actualAc != oldAc && pc.getAc() == actualAc && pc.GetBasePDefend() == actualAc,
                step + " updates DEX-sensitive effective and base AC without relogin");
        final var defense = frames.stream().filter(frame -> opcode(frame, Opcodes.S_AC)).toList();
        require(!defense.isEmpty(), step + " sends attribute/defense");
        for (byte[] frame : defense) {
            require(littleInt(frame, 1) == actualAc && (frame[14] & 255) == (pc.getTotalER() & 255),
                    step + " sends post-reset AC and ER");
        }
        final var full = frames.stream().filter(frame -> opcode(frame, Opcodes.S_STATUS)).toList();
        require(!full.isEmpty(), step + " sends full own status");
        for (byte[] frame : full) {
            require(littleInt(frame, 24) == actualAc && Arrays.equals(frameStats(frame, 10), expected)
                    && littleShort(frame, 16) == pc.getCurrentHp() && littleShort(frame, 18) == pc.getMaxHp()
                    && littleShort(frame, 20) == pc.getCurrentMp() && littleShort(frame, 22) == pc.getMaxMp(),
                    step + " sends coherent own status, AC and vitals");
        }
        final var scores = frames.stream().filter(frame -> opcode(frame, Opcodes.S_ABILITY_SCORES)).toList();
        require(!scores.isEmpty() && Arrays.equals(frameStats(scores.getLast(), 1), expected),
                step + " refreshes compact ability scores");
        final var refreshes = frames.stream().filter(frame -> extended(frame, STAT_REFRESH)).toList();
        require(!refreshes.isEmpty(), step + " refreshes the stat sheet");
        for (byte[] frame : refreshes) require(Arrays.equals(statRefresh(frame), expected), step + " sheet stat values");
        var details = frames.stream().filter(frame -> extended(frame, STAT_DETAILS)).toList();
        require(details.size() >= 5, step + " sends all five detailed stat panels");
        var dex = details.stream().filter(frame -> frame.length > 6 && (frame[5] & 255) == (5 << 3 | 2))
                .findFirst().orElseThrow(() -> new AssertionError(step + " DEX stat panel missing"));
        final var body = ByteBuffer.wrap(dex, 6, dex.length - 6);
        final int length = readUnsignedVarint(body);
        body.limit(body.position() + length);
        final var fields = new int[5];
        for (int i = 0; i < fields.length; i++) {
            require(readUnsignedVarint(body) == (i + 1) << 3, step + " DEX field " + i);
            fields[i] = readUnsignedVarint(body);
        }
        require(fields[3] == CPCParamDB.물리방어력(expected[3])
                && fields[4] == CPCParamDB.원거리회피(expected[3]), step + " DEX panel AC/ER source values");
        require(frames.stream().filter(StatResetRegression::resetComplete).count() == 1,
                step + " closes the reset window once with zero remaining points");
    }

    private List<byte[]> capture(Peer peer, Runnable action) throws Exception {
        on(peer.socket, () -> {
            action.run();
            require(peer.socket.tryWritePacket(peer.socket, new CBytesPacket(MARKER)), "wire delimiter admitted");
        });
        final var frames = new ArrayList<byte[]>();
        for (int i = 0; i < 512; i++) {
            int length = peer.input.readUnsignedByte() | peer.input.readUnsignedByte() << 8;
            require(length > 2 && length <= 8192, "fixture frame length");
            byte[] encrypted = new byte[length - 2];
            peer.input.readFully(encrypted);
            byte[] frame = peer.cipher.fromServer(encrypted);
            if (Arrays.equals(frame, MARKER)) return frames;
            frames.add(frame);
        }
        throw new AssertionError("missing stat response delimiter");
    }

    private static boolean opcode(byte[] frame, int opcode) {
        return (frame[0] & 255) == opcode;
    }

    private static boolean returned(byte[] frame, int type) {
        return frame.length >= 3 && opcode(frame, Opcodes.S_VOICE_CHAT) && (frame[1] & 255) == type;
    }

    private static boolean resetComplete(byte[] frame) {
        return returned(frame, SC_ReturnedStat.END) && frame[2] == 0;
    }

    private static boolean extended(byte[] frame, int code) {
        return frame.length >= 5 && opcode(frame, Opcodes.S_EXTENDED_PROTOBUF)
                && (frame[1] & 255) == code && frame[2] == 1;
    }

    private static int littleInt(byte[] frame, int offset) {
        return ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).getInt(offset);
    }

    private static int littleShort(byte[] frame, int offset) {
        return (frame[offset] & 255) | (frame[offset + 1] & 255) << 8;
    }

    private static int[] frameStats(byte[] frame, int offset) {
        final var values = new int[6];
        for (int i = 0; i < values.length; i++) values[i] = frame[offset + i] & 255;
        return values;
    }

    private static int readUnsignedVarint(ByteBuffer data) {
        long value = 0;
        for (int shift = 0; shift < Long.SIZE; shift += 7) {
            int next = data.get() & 255;
            value |= (long) (next & 127) << shift;
            if ((next & 128) == 0) return (int) value;
        }
        throw new AssertionError("fixture protobuf varint too long");
    }

    private static int[] statRefresh(byte[] frame) {
        final var values = new int[6];
        int offset = 3;
        for (int i = 0; i < values.length; i++) {
            require((frame[offset++] & 255) == (i + 1) << 3, "ordered stat field " + i);
            require((frame[offset] & 128) == 0, "fixture expects bounded positive primary stat");
            values[i] = frame[offset++] & 255;
        }
        return values;
    }

    private Peer peer(CIOServer server, int seat, int level, int elixirs) throws Exception {
        final Socket wire = new Socket();
        try {
            wire.connect(server.localAddress(), 4_000);
            wire.setSoTimeout(4_000);
            final var input = new DataInputStream(wire.getInputStream());
            final int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
            require(length == Opcodes.S_KEYBYTES.length + 2
                    && Arrays.equals(input.readNBytes(length - 2), Opcodes.S_KEYBYTES), "TCP key received");
            final CUserSocket socket;
            synchronized (server) {
                socket = ((Set<?>) field(CIOServer.class, "sockets").get(server)).stream()
                        .filter(CUserSocket.class::isInstance).map(CUserSocket.class::cast)
                        .filter(candidate -> candidate.isConnected() && !used.contains(candidate)).findFirst().orElseThrow();
                used.add(socket);
            }
            final var published = new CompletableFuture<L1PcInstance>();
            on(socket, () -> {
                final L1PcInstance pc = actors.bind(socket, seat);
                pc.setClassId(L1PcInstance.CLASSID_PRINCE);
                pc.setType(0);
                pc.setExp(ExpTable.getExpByLevel(level));
                pc.setLevel(level);
                pc.setHighLevel(level);
                pc.setElixirStats(elixirs);
                pc.setBonusStats(1);
                pc.ResetPrimaryStats(13, 18, 11, 9, 11, 13);
                pc.SetStatDex(BEFORE[3]);
                pc.addBaseMaxHp((short) 280);
                pc.addBaseMaxMp((short) 100);
                pc.setCurrentHp(90);
                pc.setCurrentMp(30);
                pc.restoreHpMpGrowth(new HpMpGrowthHistory(level).encode());
                pc.setReturnStat(pc.getExp());
                published.complete(pc);
            });
            final L1PcInstance pc = published.get(1L, TimeUnit.SECONDS);
            // Seed the existing character's committed preimage before exercising gameplay handlers.
            new CharacterSnapshot(pc).write();
            return new Peer(wire, input, new SelectorTransportTest.ScalarClient(Opcodes.getSeed()), socket, pc);
        } catch (Exception | Error failure) {
            wire.close();
            throw failure;
        }
    }

    private void on(CUserSocket socket, Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        cio.completeOnShard(socket.affinityShard(), action, failure -> {
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        result.get(6L, TimeUnit.SECONDS);
    }

    private static int[] stats(L1PcInstance pc) {
        return new int[] {pc.GetStatStr(), pc.GetStatInt(), pc.GetStatWis(),
                pc.GetStatDex(), pc.GetStatCon(), pc.GetStatCha()};
    }

    private static long packed(int str, int intel, int wis, int dex, int con, int cha) {
        return str | (long) intel << 8 | (long) wis << 16 | (long) dex << 24 | (long) con << 32 | (long) cha << 40;
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
