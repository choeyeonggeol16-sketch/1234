import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
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

/** Compile with SelectorTransportTest. Run in a fresh working directory. */
public final class CreatureStatWireRegression {
    private static final byte[] MARKER = {(byte) 0xfe, 0x19, 0x34, 0x7a, 0x17};
    private final CUserSocket session;
    private final DataInputStream input;
    private final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

    private CreatureStatWireRegression(CUserSocket session, DataInputStream input) {
        this.session = session;
        this.input = input;
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 1 && (args[0].equals("baseline") || args[0].equals("candidate")), "choose runtime");
        boolean candidate = args[0].equals("candidate");
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), """
                DisruptorShardCount=2
                NetworkClientPermission=8
                NetworkAddress2Account=8
                UsePreLoginTimeout=false
                """);
        Config.TIME_ZONE = "UTC";
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
            new CreatureStatWireRegression(accepted(server), input).exercise(candidate);
        } finally {
            owner.shutdown();
            check(owner.awaitTermination(5, TimeUnit.SECONDS), "owner drained");
        }
        System.out.println("PASS production encrypted permanent-stat and compound effects notifications");
    }

    private void exercise(boolean candidate) throws Exception {
        var pc = onOwner(() -> {
            var player = new FixturePlayer();
            player.setId(1_935_051);
            player.setClassId(0);
            player.setType(0);
            player.setLevel(90);
            player.ResetPrimaryStats(25, 25, 25, 25, 25, 25);
            player.setOnlineStatus(1);
            var account = new Account();
            account.setAccountId(1_935_050);
            account.setName("stat_wire_fixture");
            account.setValid(true);
            player.setAccountName(account.getName());
            session.setAccount(account);
            player.setNetConnection(session);
            session.setActiveChar(player);
            session.setConnectionState(CConnectionState.IN_GAME);
            session.publishGameSessionReady(player);
            check(session.ownsReadyGameSession(), "fixture owns a ready production connection");
            return player;
        });
        try {
            for (String name : List.of("str", "dex", "con", "int", "wis", "cha")) {
                var packets = capture(() -> check(pc.onStat(name), "invest " + name));
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
            eq(6, pc.saves, "one character save request per investment");
            eq(6, pc.getBonusStats(), "six points spent");
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
        } finally {
            onOwner(() -> {
                session.setActiveChar(null);
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

    private List<byte[]> capture(Action action) throws Exception {
        onOwner(() -> {
            action.run();
            check(session.tryWritePacket(session, new CBytesPacket(MARKER)), "test completion delimiter");
            return null;
        });
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
