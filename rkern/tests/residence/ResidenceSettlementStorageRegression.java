package l1j.server.server.model.world.residence;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

public final class ResidenceSettlementStorageRegression {
    public static void main(String[] args) throws Exception {
        Config.TIME_ZONE = "Asia/Seoul";
        Config.HOUSE_TAX_INTERVAL = 7;
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_residence_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password);
                var sql = admin.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                createFixture(sql);
                final var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(4);
                try (var data = new HikariDataSource(config)) {
                    final var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, data);
                    final var cache = CacheD.getInstance();
                    try {
                        check(cache.flushAll(5_000), "CacheD startup");
                        exercise(sql, cache);
                    } finally {
                        try { check(cache.shutdown(5_000), "CacheD shutdown"); }
                        finally {
                            CIOThreadCallback.getInstance().shutdown();
                            pool.set(null, null);
                        }
                    }
                }
            } finally {
                admin.setCatalog("mysql");
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS residence foreclosure, no-bid relist, rollback, winner ownership and one seller credit");
    }

    private static void createFixture(Statement sql) throws Exception {
        for (String table : new String[] {"character_warehouse", "clan_warehouse", "character_elf_warehouse",
                "character_special_warehouse", "character_supplementary_service", "character_items", "mapids"}) {
            sql.executeUpdate("CREATE TABLE " + table + "(id INT PRIMARY KEY) ENGINE=InnoDB");
        }
        sql.executeUpdate("CREATE TABLE characters (objid INT PRIMARY KEY) ENGINE=InnoDB");
        sql.executeUpdate("CREATE TABLE character_quests (char_id INT,quest_id INT,PRIMARY KEY(char_id,quest_id)) ENGINE=InnoDB");
        sql.executeUpdate("CREATE TABLE character_level_gifts (char_id INT,gift_level INT,PRIMARY KEY(char_id,gift_level)) ENGINE=InnoDB");
        sql.executeUpdate("CREATE TABLE cached_transaction_receipts (tx_id CHAR(36) PRIMARY KEY,kind VARCHAR(16),owner_a INT,owner_b INT) ENGINE=InnoDB");
        sql.executeUpdate("CREATE TABLE character_pending_credits (credit_id CHAR(36) PRIMARY KEY,char_id INT,amount INT,reason VARCHAR(40)) ENGINE=InnoDB");
        sql.executeUpdate("CREATE TABLE clan_data (clan_id INT PRIMARY KEY,leader_id INT,hashouse INT) ENGINE=InnoDB");
        sql.executeUpdate("""
                CREATE TABLE house (house_id INT PRIMARY KEY,house_name VARCHAR(45),house_area INT,
                  location VARCHAR(45),keeper_id INT,is_on_sale INT,is_purchase_basement INT,
                  tax_deadline DATETIME) ENGINE=InnoDB
                """);
        sql.executeUpdate("""
                CREATE TABLE board_auction (house_id INT PRIMARY KEY,house_name VARCHAR(45),house_area INT,
                  deadline DATETIME,price INT,location VARCHAR(45),old_owner VARCHAR(45),
                  old_owner_id INT,bidder VARCHAR(45),bidder_id INT) ENGINE=InnoDB
                """);
        sql.executeUpdate("""
                INSERT INTO house VALUES
                  (101,'Tax',2,'fixture',500,0,0,NOW()-INTERVAL 5 MINUTE),
                  (102,'Undated',2,'fixture',501,0,0,NULL),
                  (103,'Relist',2,'fixture',502,1,1,NULL),
                  (104,'Settlement',2,'fixture',503,1,1,NULL)
                """);
        sql.executeUpdate("""
                INSERT INTO board_auction VALUES
                  (103,'Relist',2,NOW()-INTERVAL 5 MINUTE,100000,'fixture','',0,'',0),
                  (104,'Settlement',2,NOW()-INTERVAL 5 MINUTE,200000,'fixture','Seller',841,'Buyer',842)
                """);
        sql.executeUpdate("INSERT INTO clan_data VALUES (1,801,101),(2,842,0),(3,841,104)");
    }

    private static void exercise(Statement sql, CacheD cache) throws Exception {
        final var taxClan = addClan(1, 801, 101);
        final var buyer = addClan(2, 842, 0);
        final var seller = addClan(3, 841, 104);
        final var houses = HouseTable.getInstance();
        final var auctions = AuctionBoardTable.getInstance();

        final var overdue = houses.getHouseTable(101);
        L1AuctionTransaction.foreclose(overdue, overdue.getTaxDeadline().getTimeInMillis());
        check(cache.flushAll(5_000), "foreclosure replay");
        check(taxClan.getHouseId() == 0 && overdue.isOnSale() && auctions.getAuctionBoardTable(101) != null
                && query(sql, "SELECT hashouse FROM clan_data WHERE clan_id=1") == 0, "foreclosure SQL and memory");
        check(houses.getHouseTable(102).getTaxDeadline() == null, "undated house remains unscheduled");

        final var relist = auctions.getAuctionBoardTable(103);
        final long relistDue = relist.getDeadline().getTimeInMillis();
        L1AuctionTransaction.expire(relist, relistDue);
        check(cache.flushAll(5_000), "relist replay");
        check(relist.getDeadline().getTimeInMillis() > relistDue
                && query(sql, "SELECT COUNT(*) FROM board_auction WHERE house_id=103 AND deadline>NOW()") == 1,
                "no-bid relist publishes the renewed deadline");

        final var board = auctions.getAuctionBoardTable(104);
        final long expiry = board.getDeadline().getTimeInMillis();
        L1AuctionTransaction.expire(board, expiry);
        check(cache.flushAll(5_000), "failed settlement releases the known-rollback receipt");
        check(auctions.getAuctionBoardTable(104) == board && buyer.getHouseId() == 0
                && query(sql, "SELECT hashouse FROM clan_data WHERE clan_id=2") == 0
                && query(sql, "SELECT COUNT(*) FROM character_pending_credits") == 0,
                "missing seller rolls every mutation back");

        sql.executeUpdate("INSERT INTO characters VALUES (841)");
        L1AuctionTransaction.expire(board, expiry);
        check(cache.flushAll(5_000), "retry replay");
        L1AuctionTransaction.expire(board, expiry);
        check(cache.flushAll(5_000), "duplicate stale expiry drained");
        check(buyer.getHouseId() == 104 && seller.getHouseId() == 0
                && auctions.getAuctionBoardTable(104) == null && !houses.getHouseTable(104).isOnSale()
                && query(sql, "SELECT hashouse FROM clan_data WHERE clan_id=2") == 104
                && query(sql, "SELECT COUNT(*) FROM board_auction WHERE house_id=104") == 0
                && query(sql, "SELECT COUNT(*) FROM character_pending_credits WHERE char_id=841 AND amount=180000") == 1,
                "winner ownership and exactly one durable seller credit");
    }

    private static L1Clan addClan(int id, int leader, int house) {
        final var clan = new L1Clan();
        clan.setClanId(id);
        clan.setClanName("Fixture" + id);
        clan.setLeaderId(leader);
        clan.setHouseId(house);
        CWorld.getInstance().storeClan(clan);
        return clan;
    }

    private static int query(Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            if (!rows.next()) throw new AssertionError("Missing row for fixture query");
            return rows.getInt(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
