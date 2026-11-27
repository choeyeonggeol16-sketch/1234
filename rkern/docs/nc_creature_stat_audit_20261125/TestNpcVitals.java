import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillEffect;
import l1j.server.server.model.character.companion.basic.stat.CompanionStatEffect;
import l1j.server.server.templates.L1Npc;

/** Actual NPC and companion lifecycle with a JDBC boundary double for saved arguments. */
public final class TestNpcVitals {
    private static final Map<Integer, Integer> persisted = new HashMap<>();

    public static void main(String[] args) throws Exception {
        checkNpcPrototypeAndClamps();
        checkCompanionGrowthAndPersistence();
        System.out.println("PASS NPC prototype bounds, vital clamps and companion source persistence/reset");
    }

    private static void checkNpcPrototypeAndClamps() {
        var reversed = template(7120, 74, 1, 200, 80, 25);
        var npc = new TestNpc(reversed);
        check(npc.getLevel() == 74 && npc.GetStatWis() == 25, "reversed legacy range uses base level and stats");
        check(npc.getMaxHp() == 200 && npc.getCurrentHp() == 200 && npc.getMaxMp() == 130
                && npc.getCurrentMp() == 80, "ordinary spawn preserves template HP/MP and NPC WIS rule");
        npc.setCurrentHp(-2);
        npc.setCurrentMp(-2);
        check(npc.getCurrentHp() == 0 && npc.getCurrentMp() == 0, "negative current values clamp to zero");
        npc.setPumpedSkillModifiers(25, 30, 0, 0, 0L, 0L, 0, 0L, 0L, 0);
        npc.setCurrentHp(500);
        npc.setCurrentMp(500);
        check(npc.getCurrentHp() == 225 && npc.getCurrentMp() == 160, "positive vitality modifiers affect the cap");
        npc.setPumpedSkillModifiers(0, 0, 0, 0, 0L, 0L, 0, 0L, 0L, 0);
        check(npc.getCurrentHp() == 200 && npc.getCurrentMp() == 130, "expiry clips only over-cap vitality");
        npc.addMaxHp(10);
        npc.addMaxMp(10);
        check(npc.getCurrentHp() == 200 && npc.getCurrentMp() == 130, "raising the maximum does not heal or refill");

        var replacement = template(7121, 30, 32, 400, 120, 16);
        npc.replace(replacement);
        check(npc.getLevel() >= 30 && npc.getLevel() <= 32 && npc.GetStatWis() == 18,
                "valid random level retains inclusive span and legacy full-span stat bonus");
        check(npc.getNpcTemplate() == replacement && npc.getCurrentHp() == 400 && npc.getCurrentMp() == 120,
                "replacement installs only the target template's vitals");
        check(reversed.get_level() == 74 && reversed.get_hp() == 200 && reversed.get_mp() == 80,
                "shared prototype is unchanged");
        try {
            npc.replace(null);
            throw new AssertionError("missing target prototype must be rejected");
        } catch (IllegalArgumentException expected) {
            check(npc.getNpcTemplate() == replacement && npc.getCurrentHp() == 400,
                    "missing prototype cannot partially modify the old actor");
        }
    }

    private static void checkCompanionGrowthAndPersistence() throws Exception {
        final var pool = field(DatabaseFactory.class, "DATABASE_POOL");
        try (var datasource = new HikariDataSource() {
            @Override public Connection getConnection() {
                return proxy(Connection.class, (connection, method, values) -> switch (method.getName()) {
                    case "close" -> null;
                    case "prepareStatement" -> savedStatement((String) values[0]);
                    default -> throw new AssertionError(method);
                });
            }
        }) {
            pool.set(null, datasource);
            final var constructor = L1CompanionInstance.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            final var companion = constructor.newInstance();
            final var copy = L1NpcInstance.class.getDeclaredMethod("CopyFrom", L1Npc.class);
            copy.setAccessible(true);
            copy.invoke(companion, template(8500171, 1, 0, 250, 60, 27));
            final var descriptor = CompanionClassInfo.class.getDeclaredConstructor();
            descriptor.setAccessible(true);
            field(L1CompanionInstance.class, "m_class_info").set(companion,
                    descriptor.newInstance().set_class_name("fixture"));
            companion.setName("Vital companion");
            companion.addMaxHp(25);
            companion.addMaxMp(10);
            companion.AddStatBonusStr(130);
            companion.AddStatBonusCon(8);
            companion.AddStatBonusInt(7);
            final long primaryBuff = 5L | (7L << 16) | (6L << 32);
            companion.setPumpedSkillModifiers(40, 50, 0, 0, primaryBuff, 0L, 0, 0L, 0L, 0);
            field(L1CompanionInstance.class, "m_skill_effect").set(companion,
                    CompanionSkillEffect.newInstance().add_max_hp(125));
            companion.setCurrentHp(500);
            companion.setCurrentMp(600);
            check(companion.getCurrentHp() == 440 && companion.getCurrentMp() == 170,
                    "learned and ordinary skill vitality and NPC WIS are effective");
            companion.update_instance();
            check(persisted.get(10) == 440 && persisted.get(11) == 170
                    && persisted.get(12) == 275 && persisted.get(13) == 70
                    && persisted.get(29) == 440 && persisted.get(30) == 170
                    && persisted.get(31) == 275 && persisted.get(32) == 70,
                    "both SQL branches save current vitals and base plus growth without effective bonuses");
            check(persisted.get(16) == 130 && persisted.get(17) == 8 && persisted.get(18) == 7
                    && persisted.get(35) == 130 && persisted.get(36) == 8 && persisted.get(37) == 7,
                    "SQL saves only allocated growth and preserves the full raw counter");

            field(CompanionStatEffect.class, "m_stat_effects").set(null,
                    new HashMap<>(Map.of(0, new HashMap<>(), 1, new HashMap<>(), 2, new HashMap<>())));
            field(CompanionBuffInfo.class, "m_buffs").set(null, new HashMap<>());
            companion.set_master(new L1PcInstance());
            companion.do_stats_initialized();
            check(companion.GetAddedStatStr() == 5 && companion.GetAddedStatCon() == 6 && companion.GetAddedStatInt() == 7,
                    "reset clears all allocated growth and retains independent active buffs");
            check(persisted.get(16) == 0 && persisted.get(17) == 0 && persisted.get(18) == 0,
                    "reset persists zero growth without promoting active primary modifiers");
            companion.update_skill_effect();
            check(companion.getMaxHp() == 315 && companion.getCurrentHp() == 315,
                    "rebuilding learned effects clamps current HP to the new maximum");
            companion.setPumpedSkillModifiers(0, 0, 0, 0, 0L, 0L, 0, 0L, 0L, 0);
            check(companion.getCurrentHp() == 275 && companion.getCurrentMp() == 120,
                    "ordinary buff expiry preserves permanent companion HP/MP growth");
        } finally {
            pool.set(null, null);
        }
    }

    private static PreparedStatement savedStatement(String sql) {
        check(sql.startsWith("insert into companion_instance set "), "fixture only permits companion save");
        persisted.clear();
        return proxy(PreparedStatement.class, (statement, method, values) -> switch (method.getName()) {
            case "setInt" -> { persisted.put((Integer) values[0], (Integer) values[1]); yield null; }
            case "close", "setString" -> null;
            case "execute" -> true;
            default -> throw new AssertionError(method);
        });
    }

    private static L1Npc template(int id, int level, int maxLevel, int hp, int mp, int wis) {
        var template = new L1Npc();
        template.set_npcId(id);
        template.set_level(level);
        template.set_randomlevel(maxLevel);
        template.set_hp(hp);
        template.set_mp(mp);
        template.set_wis((byte) wis);
        return template;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestNpc extends L1NpcInstance {
        TestNpc(L1Npc prototype) { super(prototype); }
        void replace(L1Npc prototype) { CopyFrom(prototype); }
    }
}
