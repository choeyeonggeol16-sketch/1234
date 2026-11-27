import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CHAR_STAT;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.utils.StatContext;

/** Reset packet handlers, preview and persistence with three ready TCP fixture actors.
 * Compile with CioSyntheticPopulation; pass disposable JDBC URL, user, reference
 * schema. Only schema definitions are reused and all records are synthetic.
 */
public final class StatResetRegression {
    private static final String[] TABLES = {
        "accounts", "characters", "cached_transaction_receipts", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "character_quests", "character_level_gifts", "mapids"
    };
    private static final long BASE = packed(13, 9, 11, 9, 11, 13);
    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(3);
    private final Set<CUserSocket> used = Collections.newSetFromMap(new IdentityHashMap<>());
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final HikariDataSource data;

    private record Peer(Socket wire, CUserSocket socket, L1PcInstance pc) implements AutoCloseable {
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
        System.out.println("PASS stat reset: read-only preview, level points, 45/50 caps, elixir phase, final stats and SQL");
    }

    private void run() throws Exception {
        Config.CACHE_MAP_FILES = true;
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
                verifyCap(at89, 45);
                verifyCap(at90, 50);
                require(cache.flushAll(5_000L), "final snapshots committed");
                verifySql(elixir, new int[] {15, 9, 12, 10, 11, 13}, 3, 1);
                verifySql(at89, new int[] {45, 9, 11, 9, 11, 13}, 32, 0);
                verifySql(at90, new int[] {50, 9, 11, 9, 11, 13}, 37, 0);
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
        on(peer.socket, () -> {
            final L1PcInstance pc = peer.pc;
            final CharacterSnapshot oldImage = new CharacterSnapshot(pc);
            final int[] before = stats(pc);
            final int oldHp = pc.getCurrentHp(), oldMp = pc.getCurrentMp();
            final int oldBaseHp = pc.getBaseMaxHp(), oldBaseMp = pc.getBaseMaxMp();

            pc.SetSkillPrimaryStats(127L | (long) (-128 & 255) << 32, false);
            var current = new StatContext(0, 1);
            current.setStat(1, 14);
            require(current.resolve(pc) && current.getStats()[0] == 127 && current.getStats()[4] == 0,
                    "effective previews use real total caps");
            pc.SetSkillPrimaryStats(0, false);

            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, BASE);
            var preview = new StatContext(0, 1);
            preview.setStat(2, 18);
            require(preview.resolve(pc) && preview.getTargetType() == SC_CHAR_STAT.Wis
                    && preview.getStats()[0] == 13 && SC_CHAR_STAT.create(peer.socket, preview) != null,
                    "detached base values and normal preview packet");
            CS_Craft.handle(peer.socket, 228, 0, 1, 1 << 2, 18L << 16, 0L, null, null);
            require(Arrays.equals(stats(pc), before), "normal stat-preview handler leaves live attributes untouched");
            advanceTo(peer, 51);
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

            final long accepted = packed(15, 9, 12, 10, 11, 13);
            int token = pc.tryReserveItemTransfer();
            require(token != 0, "existing inventory work reserved");
            try {
                CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted);
                require(pc.rst != null && Arrays.equals(stats(pc), before),
                        "final selection waits behind the existing inventory gate");
            } finally { pc.endItemTransfer(token); }
            CS_ReturnStatus.handle(peer.socket, 3, 0, 0, accepted);
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
    }

    private void verifyCap(Peer peer, int cap) throws Exception {
        on(peer.socket, () -> {
            final L1PcInstance pc = peer.pc;
            CS_ReturnStatus.handle(peer.socket, 1, 0, 0, BASE);
            advanceTo(peer, 51);
            for (int spent = 13; spent < cap; spent++) step(peer, 1);
            final int level = pc.rst.level;
            step(peer, 1);
            require(pc.rst.level == level && pc.rst.basestr + pc.rst.str == cap && pc.GetStatStr() == 17,
                    "selecting a capped stat does not spend a point or advance the staged level");
            advanceTo(peer, pc.getHighLevel());
            CS_ReturnStatus.handle(peer.socket, 2, 8, 0, 0);
            require(pc.rst == null && pc.GetStatStr() == cap && pc.getBonusStats() == cap - 13,
                    "committed cap and actual allocation count");
        });
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
                "SELECT Str,Intel,Wis,Dex,Con,Cha,BonusStatus,ElixirStatus,ReturnStat,hp_mp_growth FROM characters WHERE objid=?")) {
            ps.setInt(1, peer.pc.getId());
            try (var row = ps.executeQuery()) {
                require(row.next(), "stored character found");
                for (int stat = 0; stat < expected.length; stat++)
                    require(row.getInt(stat + 1) == expected[stat], "stored stat " + stat);
                final HpMpGrowthHistory history = HpMpGrowthHistory.decode(row.getString(10));
                require(row.getInt(7) == levelPoints && row.getInt(8) == elixirs && row.getInt(9) == 0
                        && history != null && history.floorLevel() == 1 && history.known(peer.pc.getLevel()),
                        "coherent stored points, closure and HP/MP growth");
            }
        }
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
                pc.ResetPrimaryStats(17, 12, 16, 13, 14, 13);
                pc.addBaseMaxHp((short) 280);
                pc.addBaseMaxMp((short) 100);
                pc.setCurrentHp(90);
                pc.setCurrentMp(30);
                pc.setReturnStat(pc.getExp());
                published.complete(pc);
            });
            return new Peer(wire, socket, published.get(1L, TimeUnit.SECONDS));
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
