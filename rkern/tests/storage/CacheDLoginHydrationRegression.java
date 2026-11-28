import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CS_EnterWorld;
import l1j.server.server.templates.CItemBookmark;

/** Runs the detached login readers against a counting JDBC fixture. */
public final class CacheDLoginHydrationRegression {
    private static final int FIRST_ORB = 41_000;

    private static final class Fixture extends HikariDataSource {
        final List<String> reads = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();
        int opened;
        int closed;
        boolean failLastBatch;

        @Override public Connection getConnection() {
            opened++;
            return proxy(Connection.class, (method, args) -> {
                if (method.equals("prepareStatement")) return prepare((String) args[0]);
                if (method.equals("close")) closed++;
                if (method.equals("setAutoCommit") || method.equals("commit"))
                    throw new AssertionError("Login reads opened a transaction");
                return null;
            });
        }

        private PreparedStatement prepare(String sql) {
            final ArrayList<Object> bindings = new ArrayList<>();
            return proxy(PreparedStatement.class, (method, args) -> {
                if (method.equals("setInt") || method.equals("setString")) {
                    check((int) args[0] == bindings.size() + 1, "SQL bindings out of order");
                    bindings.add(args[1]);
                }
                if (method.equals("executeQuery")) {
                    reads.add(sql);
                    final var rows = result(sql, bindings);
                    final int[] current = {-1};
                    return proxy(ResultSet.class, (read, field) -> switch (read) {
                        case "next" -> ++current[0] < rows.size();
                        case "getInt" -> rows.get(current[0]).getOrDefault(field[0], 0);
                        case "getShort", "getString" -> rows.get(current[0]).get(field[0]);
                        default -> null;
                    });
                }
                return null;
            });
        }

        private List<Map<String, Object>> result(String sql, List<Object> bindings) throws SQLException {
            if (sql.contains("character_teleport_item")) {
                check(sql.endsWith("ORDER BY item_id,num_id,id"), "Stored orb row order changed");
                batchSizes.add(bindings.size());
                if (failLastBatch && bindings.size() == 1) throw new SQLException("Synthetic read failure");
                final ArrayList<Map<String, Object>> rows = new ArrayList<>();
                bindings.stream().mapToInt(value -> (int) value).sorted().filter(id -> id % 2 == 0)
                        .forEach(id -> {
                            rows.add(bookmark(id, 11, "first"));
                            rows.add(bookmark(id, 19, "second"));
                        });
                return rows;
            }
            if (sql.equals("SELECT * FROM skills"))
                return List.of(Map.of("skill_id", 234, "name", "fixture spell", "skill_level", 3, "id", 2));
            if (sql.startsWith("SELECT item_id,name FROM etcitem")) return List.of();
            if (sql.contains("FROM character_skills")) {
                check(bindings.equals(List.of(55)), "Wrong skill owner");
                return List.of(Map.of("skill_id", 234), Map.of("skill_id", 235));
            }
            if (sql.contains("FROM character_buff")) {
                check(bindings.equals(List.of(55)) && sql.contains("skill_id <> 24447"), "Buff policy changed");
                return List.of(Map.of("skill_id", 88, "remaining_time", 120, "poly_id", 21));
            }
            if (sql.contains("FROM letter")) {
                check(bindings.equals(List.of("fixture")) && sql.endsWith("LIMIT 1"), "Invalid unread lookup");
                return List.of(Map.of());
            }
            if (sql.contains("FROM character_exclude")) {
                check(bindings.equals(List.of(55)), "Wrong exclude owner");
                return List.of(Map.of("type", 2, "exclude_name", "other"));
            }
            throw new AssertionError("Unexpected read " + sql);
        }

        private static Map<String, Object> bookmark(int orb, int row, String name) {
            return Map.of("item_id", orb, "id", row, "speed_id", row, "name", name,
                    "locx", 33001, "locy", 33002, "mapid", (short) 4,
                    "randomX", 1, "randomY", 2);
        }

        void resetCounters() {
            opened = 0;
            closed = 0;
            reads.clear();
            batchSizes.clear();
        }
    }

    private interface Call { Object run(String method, Object[] args) throws Throwable; }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.run(method.getName(), args)));
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }

    private static L1ItemInstance orb(int id) {
        final var item = new L1ItemInstance();
        item.setId(id);
        final var stale = new CItemBookmark();
        stale.setName("stale");
        item.getBookMark().add(stale);
        return item;
    }

    private static void testOrbs(Fixture fixture) {
        CItemBookmark.loadSavedOrbSlots(List.of());
        check(fixture.opened == 0, "Empty inventory borrowed a connection");
        final var orbs = new ArrayList<L1ItemInstance>();
        for (int i = 64; i >= 0; i--) orbs.add(orb(FIRST_ORB + i));
        CItemBookmark.loadSavedOrbSlots(orbs);
        check(fixture.opened == 1 && fixture.closed == 1 && fixture.batchSizes.equals(List.of(64, 1)),
                "65 orbs did not use one checkout and two statements");
        for (L1ItemInstance orb : orbs) {
            final var slots = orb.getBookMark();
            if (orb.getId() % 2 != 0) {
                check(slots.isEmpty(), "Orb with no DB rows kept stale data");
                continue;
            }
            check(slots.size() == 2, "Orb rows mixed");
            for (int i = 0; i < slots.size(); i++) {
                final CItemBookmark slot = slots.get(i);
                check(slot.getCharId() == orb.getId() && slot.getNumId() == i && slot.getTemp_id() == i,
                        "Orb identity or slot numbering changed");
                check(slot.getId() == (i == 0 ? 11 : 19) && slot.getMapId() == 4,
                        "Orb payload or row order changed");
            }
        }

        fixture.resetCounters();
        fixture.failLastBatch = true;
        orbs.set(64, orb(FIRST_ORB));
        CItemBookmark.loadSavedOrbSlots(orbs);
        check(orbs.get(64).getBookMark().size() == 1 && orbs.get(64).getBookMark().getFirst().getName().equals("stale"),
                "Failed read replaced a stored orb snapshot");
        check(fixture.closed == 1, "Failed batch leaked checkout");
        fixture.failLastBatch = false;
    }

    private static void testProfile(Fixture fixture) throws Exception {
        CSkillDB.getInstance();
        fixture.resetCounters();

        final Class<?> dataClass = Class.forName(CS_EnterWorld.class.getName() + "$EnterData");
        final var constructor = dataClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object data = constructor.newInstance();
        final L1PcInstance pc = new L1PcInstance();
        pc.setId(55);
        pc.setName("fixture");
        field(dataClass, "pc").set(data, pc);
        final var load = CS_EnterWorld.class.getDeclaredMethod("readLoginSkillsAndInbox", dataClass);
        load.setAccessible(true);
        load.invoke(null, data);
        check(fixture.opened == 1 && fixture.closed == 1 && fixture.reads.size() == 4,
                "Profile readers did not share one checkout for four reads");
        check(fixture.reads.get(0).contains("character_skills") && fixture.reads.get(1).contains("character_buff")
                && fixture.reads.get(2).contains("letter") && fixture.reads.get(3).contains("character_exclude"),
                "Profile read sequence changed");
        check(java.util.Arrays.equals((int[]) field(dataClass, "acquiredSkills").get(data), new int[] {234, 235}), "Skill IDs lost");
        check(((int[]) field(dataClass, "skills").get(data))[3] == 2, "Login skill layout changed");
        check(field(dataClass, "unreadMail").getBoolean(data), "Unread notification lost");
        check(((List<?>) field(dataClass, "buffs").get(data)).size() == 1
                && ((List<?>) field(dataClass, "excludes").get(data)).size() == 1, "Profile data lost");
    }

    public static void main(String[] args) throws Exception {
        try (Fixture fixture = new Fixture()) {
            final Field pool = field(DatabaseFactory.class, "DATABASE_POOL");
            final Object previous = pool.get(null);
            pool.set(null, fixture);
            try {
                testOrbs(fixture);
                testProfile(fixture);
            } finally {
                pool.set(null, previous);
            }
        }
        System.out.println("PASS login hydration order and payload, orbs 65→2 statements, profile 4→1 checkout");
    }
}
