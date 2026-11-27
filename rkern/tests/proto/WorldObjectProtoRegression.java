package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;

import com.zaxxer.hikari.HikariDataSource;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.NpcMarkTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillEffect;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.templates.L1Npc;

/** Run as a standalone JVM. Synthetic actors and an empty table require no server or database. */
public final class WorldObjectProtoRegression {
    public static void main(String[] args) throws Exception {
        loadEmptyNpcMarkTable();
        field(CompanionBuffInfo.class, "m_buffs").set(null, new HashMap<>());

        L1Npc template = new L1Npc();
        template.set_npcId(50001);
        template.set_name("동반자");
        template.set_nameid("$1234");
        template.set_level(16);
        template.set_hp(100);
        template.set_mp(10);
        template.set_gfxid(816);
        template.setLightSize(4);
        template.set_class_id(77);

        var constructor = L1CompanionInstance.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        L1CompanionInstance companion = constructor.newInstance();
        var copyTemplate = L1NpcInstance.class.getDeclaredMethod("CopyFrom", L1Npc.class);
        copyTemplate.setAccessible(true);
        copyTemplate.invoke(companion, template);

        var classConstructor = CompanionClassInfo.class.getDeclaredConstructor();
        classConstructor.setAccessible(true);
        field(L1CompanionInstance.class, "m_class_info").set(companion, classConstructor.newInstance().set_class_id(501));
        companion.setId(17001);
        companion.setName("한글이름".repeat(33));
        companion.set_master_name("동반자 주인");
        companion.setTitle("테스트 칭호");
        companion.setExplosionTime(713);
        field(L1CompanionInstance.class, "m_skill_effect").set(companion, CompanionSkillEffect.newInstance()
                .add_move_delay_reduce(0.0625).add_attack_delay_reduce(0.1875));
        companion.set_pvp_dmg_ratio(-13);

        byte[] expected = SC_WORLD_PUT_OBJECT_NOTI.newInstance(companion).encode();
        assertBytes(expected, encodeDirect(companion), "companion NPC direct payload");
        verifyCompanionField(expected, 0.0625, 0.1875, -13);
        try (var beforeRename = SC_WORLD_PUT_OBJECT_NOTI.openNpcBroadcast(companion)) {
            companion.setName("바뀐 이름");
            byte[] renamed = SC_WORLD_PUT_OBJECT_NOTI.newInstance(companion).encode();
            if (Arrays.equals(expected, renamed)) throw new AssertionError("rename needs different bytes");
            assertBytes(expected, readSnapshot(beforeRename), "earlier broadcast remains frozen");
            try (var afterRename = SC_WORLD_PUT_OBJECT_NOTI.openNpcBroadcast(companion)) {
                assertBytes(renamed, readSnapshot(afterRename), "rename broadcast payload");
                assertBytes(renamed, encodeDirect(companion), "after-rename direct payload");
            }
        }

        L1NpcInstance monster = new L1MonsterInstance(template);
        byte[] monsterLegacy = SC_WORLD_PUT_OBJECT_NOTI.newInstance(monster).encode();
        assertBytes(monsterLegacy, encodeDirect(monster), "monster optional NPC class payload");
        if (companionPayload(monsterLegacy) != null) throw new AssertionError("monster carried companion data");

        System.out.println("PASS world-object proto direct payload, companion doubles, and frozen rename");
    }

    private static byte[] encodeDirect(L1NpcInstance npc) throws Exception {
        var projection = SC_WORLD_PUT_OBJECT_NOTI.class.getDeclaredMethod("writeNpcForSend",
                CUserSocket.class, L1NpcInstance.class, ProtoWriter.class);
        projection.setAccessible(true);
        var pool = new CIOBufferPool(1, 1, 1, 1);
        var lease = pool.acquireSend(2048);
        Assemble frame = lease.writer();
        frame.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        frame.writeH(ProtoMsg.SC_WORLD_PUT_OBJECT_NOTI);
        ProtoWriter writer = ProtoWriter.direct(frame);
        try {
            if (!((boolean) projection.invoke(null, null, npc, writer))) throw new AssertionError("projection failed");
            writer.finishDirect(true);
            return bytesWritten(lease.buffer(), frame.position());
        } finally {
            writer.detachDirect();
            pool.release(lease);
        }
    }

    private static byte[] readSnapshot(CAsyncSendConstBuffer snapshot) throws Exception {
        var writer = (NativeWireWriter) field(CAsyncSendConstBuffer.class, "writer").get(snapshot);
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        buffer.position(CIOBufferPool.HEADER_BYTES);
        writer.write(buffer);
        return bytesWritten(buffer, buffer.position());
    }

    private static byte[] bytesWritten(ByteBuffer buffer, int end) {
        byte[] bytes = new byte[end - CIOBufferPool.HEADER_BYTES];
        buffer.get(CIOBufferPool.HEADER_BYTES, bytes);
        return bytes;
    }

    private static void verifyCompanionField(byte[] bytes, double move, double attack, int pvp) {
        byte[] field = companionPayload(bytes);
        if (field == null) throw new AssertionError("missing companion data");
        ByteBuffer fixed = ByteBuffer.wrap(field).order(ByteOrder.LITTLE_ENDIAN);
        if (fixed.get() != 9 || fixed.getDouble() != move || fixed.get() != 17 || fixed.getDouble() != attack
                || fixed.get() != 24) throw new AssertionError("companion fields and fixed64 order");
        var ratio = new ProtoReader(field, fixed.position(), fixed.remaining());
        if (ratio.varint64() != Integer.toUnsignedLong(pvp) || !ratio.isAtEnd())
            throw new AssertionError("companion uint32 PvP ratio");
    }

    private static byte[] companionPayload(byte[] bytes) {
        var body = new ProtoReader(bytes, 3, bytes.length - 5);
        for (int tag; (tag = body.next()) != 0;) {
            if (tag == (40 << 3 | 2)) return body.readBytes();
            body.skip(tag & 7);
        }
        return null;
    }

    private static void assertBytes(byte[] expected, byte[] actual, String name) {
        int mismatch = Arrays.mismatch(expected, actual);
        if (mismatch >= 0) throw new AssertionError(name + " differs at offset " + mismatch);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void loadEmptyNpcMarkTable() throws Exception {
        Field databasePool = field(DatabaseFactory.class, "DATABASE_POOL");
        try (var fixturePool = new HikariDataSource() {
            @Override
            public Connection getConnection() {
                return fake(Connection.class, "prepareStatement",
                        fake(PreparedStatement.class, "executeQuery", fake(ResultSet.class, "next", false)));
            }
        }) {
            databasePool.set(null, fixturePool);
            NpcMarkTable.getInstance();
        } finally {
            databasePool.set(null, null);
        }
    }

    private static <T> T fake(Class<T> type, String queryMethod, Object result) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getName().equals(queryMethod)) return result;
                    if (method.getName().equals("close")) return null;
                    throw new AssertionError("unexpected fixture SQL call " + method.getName());
                }));
    }
}
