import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillEffect;
import l1j.server.server.templates.L1Npc;

public final class CompanionStatStorageRegression {
    private static final int TEMPLATE = 8500171, CONTROL = 62001, OBJECT = 62002;
    private static final String CLASS = "fixture companion";

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Expected a scratch JDBC URL, user and reference schema");
        String jdbc = args[0], user = args[1], reference = args[2];
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        String schema = "rkern_companion_" + UUID.randomUUID().toString().substring(0, 8);
        try (var admin = DriverManager.getConnection(jdbc, user, password); var sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE `" + schema + "`");
            try {
                for (String table : new String[]{"companion_instance", "npc", "npc_born"})
                    sql.execute("CREATE TABLE `" + schema + "`.`" + table + "` LIKE `" + reference + "`.`" + table + "`");
                sql.executeUpdate("INSERT INTO `" + schema + "`.npc SELECT * FROM `" + reference + "`.npc WHERE npcid=" + TEMPLATE);
                check(sql.executeUpdate("UPDATE `" + schema + "`.npc SET lvl=1,hp=250,mp=60,wis=27,randomlevel=0 WHERE npcid=" + TEMPLATE) == 1,
                        "fixture copies and specializes one real companion template");
                var config = new HikariConfig();
                config.setJdbcUrl(jdbc);
                config.setUsername(user);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(2);
                try (var datasource = new HikariDataSource(config)) {
                    field(DatabaseFactory.class, "DATABASE_POOL").set(null, datasource);
                    verifyRoundTrip(datasource);
                    verifyLethalFallback();
                } finally {
                    field(DatabaseFactory.class, "DATABASE_POOL").set(null, null);
                }
            } finally {
                sql.execute("DROP DATABASE `" + schema + "`");
            }
        }
        System.out.println("PASS real MariaDB companion save/load/save and missing NPC morph death fallback");
    }

    private static void verifyRoundTrip(HikariDataSource datasource) throws Exception {
        var descriptor = CompanionClassInfo.class.getDeclaredConstructor();
        descriptor.setAccessible(true);
        var info = descriptor.newInstance().set_class_name(CLASS).set_npc_id(TEMPLATE);
        field(CompanionClassInfo.class, "m_companions").set(null, new HashMap<>(Map.of(CLASS, info)));
        var constructor = L1CompanionInstance.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        L1CompanionInstance companion = constructor.newInstance();
        companion.set_class_info(info);
        companion.set_control_object_id(CONTROL);
        companion.setId(OBJECT);
        companion.setName("Fixture companion");
        companion.addMaxHp(25);
        companion.addMaxMp(10);
        companion.AddStatBonusStr(9);
        companion.AddStatBonusCon(8);
        companion.AddStatBonusInt(7);
        applyTemporary(companion);
        companion.setCurrentHp(440);
        companion.setCurrentMp(170);
        var read = L1CompanionInstance.class.getDeclaredMethod("from_database", L1PcInstance.class, int.class);
        read.setAccessible(true);
        for (int cycle = 0; cycle < 3; cycle++) {
            companion.update_instance();
            try (var con = datasource.getConnection(); var sql = con.createStatement();
                 var rows = sql.executeQuery("SELECT current_hp,current_mp,max_hp,max_mp,str,con,intel FROM companion_instance WHERE control_object_id=" + CONTROL)) {
                check(rows.next() && rows.getInt(1) == 440 && rows.getInt(2) == 170
                        && rows.getInt(3) == 275 && rows.getInt(4) == 70
                        && rows.getInt(5) == 9 && rows.getInt(6) == 8 && rows.getInt(7) == 7 && !rows.next(),
                        "actual durable row retains exact current values and intrinsic growth without temporary bonus");
            }
            var loaded = read.invoke(null, new L1PcInstance(), CONTROL);
            companion = (L1CompanionInstance) field(loaded.getClass(), "companion").get(loaded);
            check(companion != null && companion.getMaxHp() == 275 && companion.getMaxMp() == 120,
                    "production load installs intrinsic values and adds WIS once");
            applyTemporary(companion);
            var restore = loaded.getClass().getDeclaredMethod("restoreVitals");
            restore.setAccessible(true);
            restore.invoke(loaded);
            check(companion.getCurrentHp() == 440 && companion.getCurrentMp() == 170
                    && companion.GetAddedStatStr() == 14 && companion.GetAddedStatCon() == 14
                    && companion.GetAddedStatInt() == 14, "deferred restore preserves current values and temporary stats apply once");
        }
        companion.update_skill_effect();
        companion.setPumpedSkillModifiers(0, 0, 0, 0, 0L, 0L, 0, 0L, 0L, 0);
        check(companion.getCurrentHp() == 275 && companion.getCurrentMp() == 120,
                "temporary values can expire after the real DB round trip");
    }

    private static void applyTemporary(L1CompanionInstance companion) throws Exception {
        companion.setPumpedSkillModifiers(40, 50, 0, 0, 5L | (7L << 16) | (6L << 32), 0L, 0, 0L, 0L, 0);
        field(L1CompanionInstance.class, "m_skill_effect").set(companion, CompanionSkillEffect.newInstance().add_max_hp(125));
    }

    private static void verifyLethalFallback() {
        var template = new L1Npc();
        template.set_npcId(62099);
        template.set_level(1);
        template.set_hp(10);
        template.setTransformId(Integer.MAX_VALUE);
        var npc = new TestMonster(template);
        npc.receiveDamage(new L1EffectInstance(null), 10);
        check(npc.deaths == 1 && npc.getCurrentHp() == 0 && npc.getNpcTemplate() == template,
                "missing transform target runs normal death callback without clearing the old template");
    }

    private static final class TestMonster extends L1MonsterInstance {
        int deaths;
        TestMonster(L1Npc template) { super(template); }
        @Override public void onNpcAI() { }
        @Override public void onDeath(L1Character attacker) { deaths++; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
