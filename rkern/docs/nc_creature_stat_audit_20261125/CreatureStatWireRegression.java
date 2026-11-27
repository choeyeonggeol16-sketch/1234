import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import l1j.server.Config;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** Compile with SelectorTransportTest. Run in a fresh working directory. */
public final class CreatureStatWireRegression {
    private static final String CHARACTER = "StatWireFixture";
    private static final String ACCOUNT = "stat_wire_fixture";
    private static final int OWNER = 1_935_051;
    private static final String[] TABLES = {"characters", "character_quests", "character_level_gifts", "mapids",
            "cached_transaction_receipts", "character_items", "character_warehouse", "clan_warehouse",
            "character_elf_warehouse", "character_special_warehouse", "character_supplementary_service"};
    private static final byte[] MARKER = {(byte) 0xfe, 0x19, 0x34, 0x7a, 0x17};
    private final CUserSocket session;
    private final DataInputStream input;
    private final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

    private CreatureStatWireRegression(CUserSocket session, DataInputStream input) {
        this.session = session;
        this.input = input;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("durable")) {
            runDurableFixture(args[1], args[2], args[3]);
            return;
        }
        check(args.length == 1 && (args[0].equals("baseline") || args[0].equals("candidate")),
                "historical baseline/candidate, or durable with isolated JDBC URL, user and DDL schema");
        runWire(args[0].equals("candidate"), false);
    }

    private static void runDurableFixture(String url, String user, String reference) throws Exception {
        check(reference.matches("[A-Za-z0-9_]+"), "isolated DDL reference schema");
        String catalog = "rkern_stat_wire_" + UUID.randomUUID().toString().replace("-", "");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        try (var admin = DriverManager.getConnection(url, user, password); var sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE " + catalog);
            try {
                for (String table : TABLES)
                    sql.execute("CREATE TABLE " + catalog + "." + table + " LIKE " + reference + "." + table);
                admin.setCatalog(catalog);
                sql.execute("INSERT INTO characters(account_name,objid,char_name,Clanname,notes,ReturnStat,level,HighLevel,"
                        + "Exp,Type,Class,MaxHp,CurHp,MaxMp,CurMp,BaseStr,BaseCon,BaseDex,BaseIntel,BaseWis,BaseCha,"
                        + "Str,Con,Dex,Intel,Wis,Cha,BonusStatus,ElixirStatus) VALUES('" + ACCOUNT + "',"
                        + OWNER + ",'" + CHARACTER + "','','',0,50,50," + ExpTable.getExpByLevel(50)
                        + ",0,0,500,500,300,300,25,25,25,25,25,25,25,25,25,25,25,25,0,0)");
                try (var pool = new HikariDataSource()) {
                    pool.setJdbcUrl(url);
                    pool.setUsername(user);
                    pool.setPassword(password);
                    pool.setCatalog(catalog);
                    pool.setMaximumPoolSize(3);
                    var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);
                    try {
                        check(CacheD.getInstance().flushAll(5_000L), "stat receipt schema ready");
                        runWire(true, true);
                    } finally {
                        check(CacheD.getInstance().shutdown(5_000L), "CacheD drained");
                    }
                }
            } finally {
                sql.execute("DROP DATABASE " + catalog);
            }
        }
    }

    private static void runWire(boolean candidate, boolean durable) throws Exception {
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), """
                DisruptorShardCount=2
                NetworkClientPermission=8
                NetworkAddress2Account=8
                UsePreLoginTimeout=false
                """);
        Config.TIME_ZONE = "UTC";
        Config.CACHE_MAP_FILES = true;
        l1j.server.server.model.gametime.L1GameTimeClock.prepare();
        Config.MAX_ONLINE_USERS = 8;
        Config.RATE_WEIGHT_LIMIT = 1;
        CPacketFilter.getInstance().load();
        var owner = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        int shards = owner.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(8, 24, 8, 8, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, owner, config);
             var wire = new Socket()) {
            wire.setSoTimeout(5000);
            wire.connect(server.localAddress(), 5000);
            var input = new DataInputStream(wire.getInputStream());
            check(Arrays.equals(frame(input), Opcodes.S_KEYBYTES), "production initial key");
            new CreatureStatWireRegression(accepted(server), input).exercise(candidate, durable);
        } finally {
            owner.shutdown();
            check(owner.awaitTermination(5, TimeUnit.SECONDS), "owner drained");
        }
        System.out.println("PASS production encrypted permanent-stat and compound effects notifications");
    }

    private void exercise(boolean candidate, boolean durable) throws Exception {
        var pc = onOwner(() -> {
            var player = new FixturePlayer();
            player.setId(OWNER);
            player.setName(CHARACTER);
            player.setTitle("");
            player.setClanname("");
            player.setClanMemberNotes("");
            player.setClassId(0);
            player.setType(0);
            player.ResetPrimaryStats(25, 25, 25, 25, 25, 25);
            player.setLevel(durable ? 50 : 90);
            if (durable) {
                player.setExp(ExpTable.getExpByLevel(51));
                player.setHighLevel(51);
                player.addBaseMaxHp((short) 500);
                player.addBaseMaxMp(300);
                player.applyHpMpLevelTransition(50, 51);
                player.setLevel(51);
                player.setCurrentHp(500);
                player.setCurrentMp(300);
            }
            player.setOnlineStatus(1);
            var account = new Account();
            account.setAccountId(1_935_050);
            account.setName(ACCOUNT);
            account.setValid(true);
            player.setAccountName(account.getName());
            session.setAccount(account);
            player.setNetConnection(session);
            session.setActiveChar(player);
            session.setConnectionState(CConnectionState.IN_GAME);
            session.publishGameSessionReady(player);
            check(session.ownsReadyGameSession(), "fixture owns a ready production connection");
            if (durable) {
                CWorld.getInstance().storeObject(player);
                player.setCacheDSessionEpoch(CacheD.getInstance().registerLoadedCharacterSession(ACCOUNT, CHARACTER, OWNER));
            }
            return player;
        });
        try {
            CharacterSnapshot beforeInvestment = null;
            if (durable) {
                beforeInvestment = onOwner(() -> {
                    int token = pc.tryReserveItemTransfer();
                    check(token != 0, "reserve a competing item transaction");
                    try {
                        check(!pc.onStat("str") && pc.GetStatStr() == 25 && pc.getBonusStats() == 0,
                                "pending item action blocks ability investment");
                    } finally {
                        pc.endItemTransfer(token);
                    }
                    return new CharacterSnapshot(pc);
                });
            }
            for (String name : List.of("str", "dex", "con", "int", "wis", "cha")) {
                var packets = durable ? captureCommittedInvestment(pc, name)
                        : capture(() -> check(pc.onStat(name), "invest " + name));
                if (durable && name.equals("str")) {
                    var persisted = new MySqlCharacterStorage().loadCharacter(CHARACTER);
                    eq(51, persisted.getLevel(), "first unsaved level was persisted");
                    eq(26, persisted.GetStatStr(), "first stat durable before packets");
                    eq(1, persisted.getBonusStats(), "first point durable before packets");
                    onOwner(() -> {
                        pc.setExp(ExpTable.getExpByLevel(90));
                        pc.applyHpMpLevelTransition(51, 90);
                        pc.setLevel(90);
                        pc.setHighLevel(90);
                        return null;
                    });
                }
                if (durable && name.equals("dex")) {
                    var persisted = new MySqlCharacterStorage().loadCharacter(CHARACTER);
                    eq(26, persisted.GetStatDex(), "DEX durable before packets");
                    try (var con = DatabaseFactory.getInstance().getConnection()) {
                        try (var sql = con.createStatement();
                             var row = sql.executeQuery("SELECT Ac FROM characters WHERE objid=" + OWNER)) {
                            check(row.next(), "persisted DEX row");
                            eq(pc.getAc(), row.getInt(1), "DEX receipt stores final AC");
                        }
                    }
                }
                int expectedStatus = !candidate && (name.equals("str") || name.equals("dex")) ? 2 : 1;
                eq(expectedStatus, matching(packets, CreatureStatWireRegression::status).size(), name + " status");
                var scores = matching(packets, CreatureStatWireRegression::status).getLast();
                int[] observed = {scores[1], scores[2], scores[3], scores[4], scores[5], scores[6]};
                int[] expected = {pc.GetTotalStatStr(), pc.GetTotalStatInt(), pc.GetTotalStatWis(),
                        pc.GetTotalStatDex(), pc.GetTotalStatCon(), pc.GetTotalStatCha()};
                check(Arrays.equals(expected, observed), "live ability scores for " + name);
                int expectedWeight = name.equals("str") || name.equals("con") ? (candidate ? 1 : 2) : 0;
                eq(expectedWeight, matching(packets, CreatureStatWireRegression::weight).size(), name + " weight");
                eq(name.equals("dex") ? (candidate ? 1 : 2) : 0,
                        matching(packets, CreatureStatWireRegression::defense).size(), name + " defense");
                var detail = matching(packets, p -> extended(p, 0xe3));
                eq(name.equals("cha") ? 0 : 1, detail.size(), name + " detail");
                if (!detail.isEmpty())
                    eq(name.equals("str") ? 2 : 8, detail.getFirst()[4] & 255, name + " detail setting");
            }
            eq(durable ? 0 : 6, pc.saves, "ordinary save is not the durable ack");
            eq(6, pc.getBonusStats(), "six points spent");
            if (durable) {
                final CacheD cache = CacheD.getInstance();
                cache.publishKarmaReceipt(OWNER, pc.getCacheDSessionEpoch(), beforeInvestment.characterReceiptRevision);
                check(!cache.saveCharacter(beforeInvestment), "late pre-investment snapshot rejected");
                check(cache.saveCharacter(onOwner(() -> new CharacterSnapshot(pc))) && cache.flushCharacter(CHARACTER, 5_000L),
                        "fresh snapshot remains persistable");
                var restored = new MySqlCharacterStorage().loadCharacter(CHARACTER);
                eq(6, restored.getBonusStats(), "six points after reload");
                check(Arrays.equals(new int[]{26,26,26,26,26,26}, new int[]{restored.GetStatStr(),
                        restored.GetStatDex(), restored.GetStatCon(), restored.GetStatInt(),
                        restored.GetStatWis(), restored.GetStatCha()}), "six pure stats after reload");
            }
            System.out.println("PASS six permanent investment details and exact status/weight/defense counts");

            var effects = new SkillEffectContainer(pc);
            int plainSp = pc.GetStatSp();
            int plainAc = pc.getAc();
            var applied = capture(() -> effects.applySkillModifier(L1SkillId.COMA_B, 0));
            eq(plainSp + 1, pc.GetStatSp(), "compound skill SP applied");
            check(pc.getAc() <= plainAc - 8, "compound AC includes skill armor and DEX");
            eq(candidate ? 1 : 2, matching(applied, CreatureStatWireRegression::weight).size(),
                    "compound weight count");
            verifyFinalFrames(pc, applied, candidate, "compound apply");
            var duplicate = capture(() -> effects.applySkillModifier(L1SkillId.COMA_B, 0));
            eq(0, duplicate.size(), "identical skill does not re-notify");
            var removed = capture(() -> check(effects.removeSkillModifier(L1SkillId.COMA_B, true), "compound remove"));
            eq(plainSp, pc.GetStatSp(), "compound skill SP restored");
            eq(plainAc, pc.getAc(), "compound AC restored");
            verifyFinalFrames(pc, removed, candidate, "compound remove");
            capture(() -> effects.applySkillModifier(L1SkillId.COMA_B, 0));
            var silent = capture(() -> check(effects.removeSkillModifier(L1SkillId.COMA_B, false), "silent remove"));
            if (candidate) eq(0, silent.size(), "silent recomputation emits no state packets");
            eq(plainSp, pc.GetStatSp(), "silent removal updates server");
            eq(plainAc, pc.getAc(), "silent removal updates defense");
            System.out.printf("PASS compound apply/remove/unchanged/silent packets=%d/%d/%d/%d%n",
                    applied.size(), removed.size(), duplicate.size(), silent.size());

            // This live effect changes INT and SP together, without a separate SP packet in its cast hook.
            final int intellect = pc.GetTotalStatInt();
            var iceApplied = capture(() -> effects.applySkillModifier(L1SkillId.지식빙수, (6L << 32) | 7));
            eq(intellect + 1, pc.GetTotalStatInt(), "ice effect INT");
            eq(7, pc.GetStatSp() - pc.GetStatBaseSp(), "ice effect SP");
            var iceRemoved = capture(() -> check(effects.removeSkillModifier(L1SkillId.지식빙수, true), "remove ice effect"));
            eq(intellect, pc.GetTotalStatInt(), "ice effect INT restored");
            eq(0, pc.GetStatSp() - pc.GetStatBaseSp(), "ice effect SP restored");
            eq(candidate ? 7 : 0, sentMagicBonus(iceApplied), "ice skill apply SP packet");
            eq(candidate ? 0 : 7, sentMagicBonus(iceRemoved), "ice skill removal SP packet");
            System.out.printf("PASS compound INT/SP wire %d -> %d (%s)%n",
                    sentMagicBonus(iceApplied), sentMagicBonus(iceRemoved), candidate ? "fixed" : "baseline stale");
            var clan = capture(() -> effects.applySkillModifier(L1SkillId.혈맹버프올스텟, 0));
            eq(candidate ? 1 : 2, matching(clan, CreatureStatWireRegression::weight).size(), "clan buff weight");
            var clanMagic = matching(clan, packet -> opcode(packet, Opcodes.S_MAGIC_STATUS));
            eq(candidate ? 1 : 2, clanMagic.size(), "clan buff deduplicates magic status");
            for (byte[] magic : clanMagic) eq(0, magic[1] & 255, "clan buff SP payload");
            capture(() -> check(effects.removeSkillModifier(L1SkillId.혈맹버프올스텟, true), "remove clan buff"));
            if (durable) {
                int before = pc.GetStatCha(), spent = pc.getBonusStats();
                onOwner(() -> {
                    l1j.server.server.utils.FullStater.running(pc, "cha", 2);
                    return null;
                });
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < until && onOwner(pc::getBonusStats) < spent + 2) Thread.sleep(10);
                check(CacheD.getInstance().flushAll(5_000), "batch receipts retired");
                var fresh = new MySqlCharacterStorage().loadCharacter(CHARACTER);
                eq(before + 2, fresh.GetStatCha(), "two committed CHA allocations");
                eq(spent + 2, fresh.getBonusStats(), "two durable batch points");
                var frames = capture(() -> {});
                eq(2, matching(frames, CreatureStatWireRegression::status).size(), "two post-commit status updates");
                System.out.println("PASS two-point FullStater batch with production session and SQL");
            }
        } finally {
            onOwner(() -> {
                session.setActiveChar(null);
                if (durable) CWorld.getInstance().removeObject(pc);
                pc.setNetConnection(null);
                session.setAccount(null);
                return null;
            });
        }
    }

    private static void verifyFinalFrames(L1PcInstance pc, List<byte[]> packets, boolean candidate, String phase) {
        var defense = matching(packets, CreatureStatWireRegression::defense);
        check(!defense.isEmpty(), phase + " emits defense");
        if (candidate) eq(1, defense.size(), phase + " final defense only");
        byte[] last = defense.getLast();
        eq(pc.getAc(), ByteBuffer.wrap(last).order(ByteOrder.LITTLE_ENDIAN).getInt(1), phase + " final AC");
        eq(pc.getTotalER() & 255, last[14] & 255, phase + " final ER");

    }

    private static int sentMagicBonus(List<byte[]> packets) {
        var magic = matching(packets, p -> opcode(p, Opcodes.S_MAGIC_STATUS));
        eq(1, magic.size(), "one final SP notification");
        return magic.getFirst()[1] & 255;
    }

    private List<byte[]> captureCommittedInvestment(L1PcInstance pc, String stat) throws Exception {
        // Historical modes compile against the pre-receipt server, too.
        var submit = L1PcInstance.class.getMethod("onStat", CUserSocket.class, int.class,
                String.class, Runnable.class, Runnable.class);
        var completed = new CompletableFuture<Void>();
        onOwner(() -> {
            Runnable success = () -> {
                try {
                    writeDelimiter();
                    completed.complete(null);
                } catch (RuntimeException | Error failure) {
                    completed.completeExceptionally(failure);
                }
            };
            Runnable rollback = () -> completed.completeExceptionally(new AssertionError("investment rolled back"));
            check(Boolean.TRUE.equals(submit.invoke(pc, session, pc.getSessionStamp(), stat, success, rollback)),
                    "admit durable investment " + stat);
            return null;
        });
        completed.get(5, TimeUnit.SECONDS);
        return readCapture();
    }

    private List<byte[]> capture(Action action) throws Exception {
        onOwner(() -> {
            action.run();
            writeDelimiter();
            return null;
        });
        return readCapture();
    }

    private void writeDelimiter() {
        check(session.tryWritePacket(session, new CBytesPacket(MARKER)), "test completion delimiter");
    }

    private List<byte[]> readCapture() throws Exception {
        var messages = new ArrayList<byte[]>();
        for (int i = 0; i < 64; i++) {
            byte[] payload = cipher.fromServer(frame(input));
            if (Arrays.equals(payload, MARKER)) return messages;
            messages.add(payload);
        }
        throw new AssertionError("missing capture delimiter");
    }

    private <T> T onOwner(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        check(CIOThreadCallback.getInstance().scheduleNetworkTimeout(session.affinityShard(),
                new CIOThreadCallback.WheelTask() {
                    @Override public boolean isCancelled() { return result.isCancelled(); }
                    @Override public void run() {
                        try { result.complete(task.call()); }
                        catch (Exception | Error failure) { result.completeExceptionally(failure); }
                    }
                }, 1), "schedule on real socket owner");
        return result.get(5, TimeUnit.SECONDS);
    }

    private static CUserSocket accepted(CIOServer server) throws ReflectiveOperationException {
        var field = CIOServer.class.getDeclaredField("sockets");
        field.setAccessible(true);
        synchronized (server) {
            for (Object peer : (Set<?>) field.get(server)) {
                if (peer instanceof CUserSocket socket && socket.isConnected()) return socket;
            }
        }
        throw new AssertionError("accepted socket missing");
    }

    private static byte[] frame(DataInputStream input) throws Exception {
        int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
        check(length > 2 && length <= 1024, "test frame length " + length);
        byte[] data = new byte[length - 2];
        input.readFully(data);
        return data;
    }

    private static List<byte[]> matching(List<byte[]> frames, Predicate<byte[]> predicate) {
        return frames.stream().filter(predicate).toList();
    }

    private static boolean opcode(byte[] frame, int opcode) { return (frame[0] & 255) == opcode; }
    private static boolean status(byte[] frame) { return opcode(frame, Opcodes.S_ABILITY_SCORES); }
    private static boolean defense(byte[] frame) { return opcode(frame, Opcodes.S_AC); }
    private static boolean weight(byte[] frame) { return extended(frame, 0xe5); }

    private static boolean extended(byte[] frame, int code) {
        return frame.length > 4 && opcode(frame, Opcodes.S_EXTENDED_PROTOBUF)
                && (frame[1] & 255) == code && frame[2] == 1;
    }

    private static void eq(int expected, int actual, String message) {
        check(expected == actual, message + " expected " + expected + " but got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }

    private static final class FixturePlayer extends L1PcInstance {
        int saves;
        @Override public void save() { saves++; }
    }
}
