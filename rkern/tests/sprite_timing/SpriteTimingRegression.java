package l1j.server.server.model.character.animation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import com.zaxxer.hikari.HikariDataSource;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.loader.CSpriteTimingTable;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.core.SkillDelayPolicy;
import l1j.server.server.templates.L1Skills;

public final class SpriteTimingRegression {
    private static final int DEFAULT = CSpriteTimingTable.DEFAULT_SPRITE_ID;
    private static final int NORMAL = 14500;
    private static final int SPECIAL = 12015;
    private static final Path XML = Path.of("data/xml/PolyFrame/polyframerate.xml");

    public static void main(String[] args) throws Exception {
        if (Files.exists(XML)) throw new IllegalArgumentException("Run from an isolated working directory.");
        Files.createDirectories(XML.getParent());
        Config.USE_ACTION_TIME_LOGGING = false;
        var factoryPool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        factoryPool.setAccessible(true);
        var oldPool = factoryPool.get(null);
        try (var pool = new SpriteDb()) {
            factoryPool.set(null, pool);
            assertPrecomputedArithmetic();
            assertReloads(pool);
            System.out.println("PASS SPR base times, aliases, PC/NPC overloads, status changes and concurrent reloads");
        } finally {
            factoryPool.set(null, oldPool);
            Files.deleteIfExists(XML);
        }
    }

    private static void assertPrecomputedArithmetic() {
        for (EActionCodes action : EActionCodes.values()) {
            for (int fps = 1; fps <= 60; fps++) {
                double frames = action.getAmount() + 0.125;
                double expected = frames * (1000.0 / fps);
                CActionTiming timing = CActionTiming.fromRate(action.toInt(), frames, fps);
                sameBits(expected, timing.baseMillis());
                CActionTiming levels = timing.withLevelRates(new Double[]{1000.0 / 19, 1000.0 / 31});
                sameBits(frames * (1000.0 / 19), levels.baseMillis(0));
                sameBits(frames * (1000.0 / 31), levels.baseMillis(1));
                sameBits(expected, levels.baseMillis(50));
            }
        }
    }

    private static void assertReloads(SpriteDb pool) throws Exception {
        var table = CSpriteTimingTable.getInstance();
        writeXml(50, 20, 40);
        check(table.reloadSprites() && CSpriteLevelBoundary.reload() && table.reloadSpellDelayInformation(), "initial loader");
        var actor = new Player();
        actor.setCurrentSprite(DEFAULT);
        check(actor.getCurrentSprite() != null, "default is initialized on a fresh actor");
        actor.setCurrentSprite(NORMAL);
        var old = actor.getCurrentSprite();
        check(actor.getCurrentSpriteInterval(EActionCodes.walk) == 500L, "PC level bucket");
        check(actor.getCurrentSpriteInterval(0) == 1000L, "compatibility int overload uses generic path");

        actor.haste = true;
        check(actor.getCurrentSpriteInterval(EActionCodes.walk) == (long)(500.0 * (1.0 / 1.33)), "haste applied immediately");
        actor.haste = false;
        actor.slow = true;
        check(actor.getCurrentSpriteInterval(EActionCodes.walk) == (long)(500.0 * 1.33), "expiry and slow use live state");
        actor.slow = false;
        check(actor.getCurrentSpriteInterval(EActionCodes.attack) == 800L, "unmodified attack interval");
        var spell = new L1Skills();
        spell.setTarget("attack");
        spell.setSkillId(15);
        Config.SPELL_NETWORK_TOLERANCE = 50;
        check(SkillDelayPolicy.calcGateMs(actor, spell) == 550, "directed spell interval");
        spell.setSkillId(18);
        check(SkillDelayPolicy.calcGateMs(actor, spell) == 300, "skill 18 uses non-directed interval");
        actor.haste = true;
        check(actor.getCurrentSpriteInterval(EActionCodes.attack) == (long)(800.0 * (1.0 / 1.33))
                && SkillDelayPolicy.calcGateMs(actor, spell) == (long)(350.0 * (1.0 / 1.33)) - 50,
                "attack and spell use the live haste effect");
        actor.haste = false;
        actor.setCurrentSprite(SPECIAL);
        check(actor.getCurrentSpriteInterval(EActionCodes.walk_bow) == 500L, "special sprite maps armed motion to basic");
        actor.setCurrentSprite(NORMAL);

        var npc = new L1Character();
        npc.setCurrentSprite(NORMAL);
        long firstAlias = npc.getCurrentSpriteInterval(EActionCodes.attack_kiringku);
        check(firstAlias == (long)(22.0 * (1000.0 / 24.0))
                && npc.getCurrentSpriteInterval(EActionCodes.attack_claw) == firstAlias,
                "numeric aliases share the first-use fallback");

        pool.fail = "FROM spr_action";
        writeXml(70, 10, 5);
        check(!table.reloadSprites() && actor.getCurrentSprite() == old, "failed SQL keeps prior generation");
        pool.fail = "";
        check(table.reloadSprites() && actor.getCurrentSprite() != old, "same sprite id refreshes after reload");
        check(actor.getCurrentSpriteInterval(EActionCodes.walk) == 2000L, "new rates and level indices published together");
        check((long) old.getInterval(actor, EActionCodes.walk) == 500L, "in-flight reader may still use previous SPR");

        pool.boundary = 99;
        check(CSpriteLevelBoundary.reload()
                && actor.getCurrentSpriteInterval(EActionCodes.walk) == 4000L, "boundary-only reload");
        pool.fail = "FROM spr_boundary";
        pool.boundary = 0;
        check(!CSpriteLevelBoundary.reload()
                && actor.getCurrentSpriteInterval(EActionCodes.walk) == 4000L, "failed boundary refresh retains overrides");
        pool.fail = "";
        pool.boundary = -1;
        check(CSpriteLevelBoundary.reload(), "restore level boundary");

        pool.spell = 792;
        check(table.getUseSpellInterval(actor, 18) == 640L, "SPR reload leaves spell data independent");
        check(table.reloadSpellDelayInformation() && table.getUseSpellInterval(actor, 18) == 792L, "spell-only refresh");
        pool.fail = "FROM tb_magicdelay";
        check(!table.reloadSpellDelayInformation() && table.getUseSpellInterval(actor, 18) == 792L, "old spell data survives failure");
        pool.fail = "";

        var readFailure = new AtomicReference<Throwable>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                for (int i = 0; i < 200_000; i++) {
                    long time = actor.getCurrentSpriteInterval(EActionCodes.walk);
                    check(time == 500L || time == 2000L, "reader must not mix SPR generations");
                }
            } catch (Throwable error) {
                readFailure.set(error);
            }
        });
        for (int i = 0; i < 12; i++) {
            if ((i & 1) == 0) writeXml(50, 20, 40);
            else writeXml(70, 10, 5);
            check(table.reloadSprites(), "concurrent reload");
        }
        worker.join();
        if (readFailure.get() != null) throw new AssertionError("concurrent timing lookup", readFailure.get());
    }

    private static void writeXml(int level, int fast, int slow) throws Exception {
        Files.writeString(XML, """
                <Configuration>
                    <Level range="0,%d"/>
                    <Target>14500</Target><Target>12015</Target>
                    <PolyFrameRate><Frame action="0" rate="%d,%d"/></PolyFrameRate>
                    <Sprite target="12015"/>
                    <PolyFrameException/>
                </Configuration>
                """.formatted(level, fast, slow));
    }

    private static void sameBits(double expected, double observed) {
        check(Double.doubleToLongBits(expected) == Double.doubleToLongBits(observed), "bit-exact frame arithmetic");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Player extends L1PcInstance {
        boolean haste;
        boolean slow;

        @Override
        public int getLevel() { return 55; }

        @Override
        public boolean isHaste() { return haste; }

        @Override
        public boolean hasSkillEffect(int skillId) { return skillId == L1SkillId.SLOW && slow; }
    }

    private static final class SpriteDb extends HikariDataSource {
        String fail = "";
        int boundary = -1;
        int spell = 640;

        @Override
        public Connection getConnection() {
            return proxy(Connection.class, (instance, method, args) -> {
                if ("prepareStatement".equals(method.getName())) return statement((String) args[0]);
                return closeOnly(method);
            });
        }

        private PreparedStatement statement(String query) {
            return proxy(PreparedStatement.class, (instance, method, args) -> {
                if ("executeQuery".equals(method.getName())) {
                    if (!fail.isEmpty() && query.contains(fail)) throw new SQLException("fixture read error");
                    if (query.contains("FROM spr_action")) return rows("spr_id act_id framecount framerate",
                            new int[]{NORMAL, 0, 20, 20}, new int[]{NORMAL, 1, 16, 20},
                            new int[]{NORMAL, 18, 12, 20}, new int[]{NORMAL, 19, 7, 20},
                            new int[]{SPECIAL, 0, 20, 20});
                    if (query.contains("FROM spr_info")) return rows("spr_id width height action_count",
                            new int[]{DEFAULT, 1, 1, 0}, new int[]{NORMAL, 1, 1, 1}, new int[]{SPECIAL, 1, 1, 1});
                    if (query.contains("FROM spr_boundary")) return boundary < 0 ? rows("sprite_id boundary_level")
                            : rows("sprite_id boundary_level", new int[]{NORMAL, boundary});
                    if (query.contains("FROM tb_magicdelay")) return rows("skill_id delay", new int[]{18, spell});
                    throw new AssertionError(query);
                }
                return closeOnly(method);
            });
        }

        private static Object closeOnly(Method method) {
            if ("close".equals(method.getName())) return null;
            throw new UnsupportedOperationException(method.getName());
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static CachedRowSet rows(String columns, int[]... values) throws SQLException {
            String[] names = columns.split(" ");
            var metadata = new RowSetMetaDataImpl();
            metadata.setColumnCount(names.length);
            for (int i = 0; i < names.length; i++) {
                metadata.setColumnName(i + 1, names[i]);
                metadata.setColumnType(i + 1, Types.INTEGER);
            }
            CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
            rows.setMetaData(metadata);
            for (int[] row : values) {
                rows.moveToInsertRow();
                for (int i = 0; i < row.length; i++) rows.updateInt(i + 1, row[i]);
                rows.insertRow();
                rows.moveToCurrentRow();
            }
            rows.beforeFirst();
            return rows;
        }
    }
}
