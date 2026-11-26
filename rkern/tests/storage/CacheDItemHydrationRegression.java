import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Real JDBC hydration in a disposable schema. The reference schema supplies only empty table shapes. */
public final class CacheDItemHydrationRegression {
    private static final String[] TABLES = {
        "cached_transaction_receipts", "character_items", "character_warehouse", "clan_warehouse",
        "character_elf_warehouse", "character_special_warehouse", "character_supplementary_service",
        "characters", "character_quests", "character_level_gifts", "etcitem", "weapon", "armor"
    };

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }

    private static void expectHydrationFailure(MySqlCharactersItemStorage storage, int owner, String reason)
            throws Exception {
        try {
            storage.loadItems(owner);
            throw new AssertionError("Hydration should fail: " + reason);
        } catch (SQLException failure) {
            if (!failure.getMessage().contains(reason)) throw failure;
        }
    }

    private static void exercise(Statement sql, String schema, String reference, CacheD cache) throws Exception {
        final var storage = new MySqlCharactersItemStorage();
        final Object lock = field(CacheD.class, "lock").get(cache);
        final var owners = (Int2IntOpenHashMap) field(CacheD.class, "itemOwners").get(cache);
        sql.executeUpdate("INSERT INTO etcitem(item_id,name,name_id,item_type,use_type,material) VALUES "
                + "(40001,'Fixture item','fixture_item','questitem','none','none'),"
                + "(80500,'Fixture key','fixture_key','questitem','none','none')");
        sql.executeUpdate("INSERT INTO character_items(id,item_id,char_id,item_name,count) VALUES "
                + "(100,40001,51,'valid first',1),(101,40001,51,'malformed second',0),"
                + "(200,40001,52,'normal item',1),(201,80500,52,'boss key',1)");

        // Match the production query and prove the valid row precedes the failure here.
        try (var rows = sql.executeQuery("SELECT * FROM character_items WHERE char_id = 51")) {
            check(rows.next() && rows.getInt("id") == 100 && rows.next() && rows.getInt("id") == 101,
                    "fixture's valid item must be read before the malformed row");
        }
        expectHydrationFailure(storage, 51, "Invalid character_items count");
        synchronized (lock) {
            check(!owners.containsKey(100) && !owners.containsKey(101),
                    "later malformed row published an earlier item owner");
        }

        sql.executeUpdate("UPDATE character_items SET count=2 WHERE id=101");
        try (var insert = sql.getConnection().prepareStatement(
                "INSERT INTO character_items(id,item_id,char_id,item_name,count) VALUES (?,40001,51,'extra',1)")) {
            for (int id = 102; id < 165; id++) {
                insert.setInt(1, id);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        final var hydrated = storage.loadItems(51);
        check(hydrated.size() == 65, "all 65 rows were hydrated");
        synchronized (lock) {
            for (int id = 100; id < 165; id++)
                check(owners.get(id) == 51, "owner missing across registration batch boundary: " + id);
        }

        expectHydrationFailure(storage, 52, "item_key_boss");
        synchronized (lock) {
            check(!owners.containsKey(200) && !owners.containsKey(201),
                    "key metadata failure published an item owner");
        }
        sql.execute("CREATE TABLE `" + schema + "`.item_key_boss LIKE `" + reference + "`.item_key_boss");
        sql.executeUpdate("INSERT INTO item_key_boss(item_obj_id,key_id) VALUES (201,601)");
        final var withKey = storage.loadItems(52);
        check(withKey.size() == 2 && withKey.stream().anyMatch(item -> item.getId() == 201 && item.getKeyId() == 601),
                "retry loads boss key metadata");
        synchronized (lock) {
            check(owners.get(200) == 52 && owners.get(201) == 52, "successful retry publishes both owners");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Pass the isolated MariaDB JDBC URL, user and reference schema");
        final String url = args[0], user = args[1], reference = args[2];
        final String schema = "rkern_hydrate_" + UUID.randomUUID().toString().replace("-", "");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final var etc = Files.createTempFile("rkern-hydrate-etc", ".txt");
        final var equipment = Files.createTempFile("rkern-hydrate-equipment", ".txt");
        try (var admin = DriverManager.getConnection(url, user, password); var sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE `" + schema + "` DEFAULT CHARACTER SET utf8mb4");
            try {
                admin.setCatalog(schema);
                for (String table : TABLES)
                    sql.execute("CREATE TABLE `" + schema + "`.`" + table + "` LIKE `" + reference + "`.`" + table + "`");
                Files.writeString(etc, "40001\n80500\n");
                System.setProperty("rkern.etc.base", etc.toString());
                System.setProperty("rkern.equipment.base", equipment.toString());

                final var config = new HikariConfig();
                config.setJdbcUrl(url);
                config.setUsername(user);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(3);
                config.setMinimumIdle(0);
                try (var data = new HikariDataSource(config)) {
                    final Field pool = field(DatabaseFactory.class, "DATABASE_POOL");
                    pool.set(null, data);
                    final CacheD cache = CacheD.getInstance();
                    try {
                        exercise(sql, schema, reference, cache);
                    } finally {
                        check(cache.shutdown(5000), "CacheD drained its fixture");
                        pool.set(null, null);
                    }
                }
            } finally {
                sql.execute("DROP DATABASE `" + schema + "`");
            }
        } finally {
            Files.deleteIfExists(etc);
            Files.deleteIfExists(equipment);
        }
        System.out.println("PASS actual item hydration: no partial owners, 65-row publication, boss key failure and retry");
    }
}
