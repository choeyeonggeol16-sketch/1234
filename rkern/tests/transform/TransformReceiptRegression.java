import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.loader.CSpriteTimingTable;
import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.category.CompanionCategoryInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.friendship.CompanionFriendShipBonus;
import l1j.server.server.model.character.companion.basic.potion.CompanionPotionInfo;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_ItemUse;
import l1j.server.server.model.network.core.cs.packet.CS_NpcAction;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

public final class TransformReceiptRegression {
    private static final int ITEM_OBJECT_ID = 91_100_001;
    private static final int BLESSED_OBJECT_ID = 91_100_002;
    private static final int CARD_SCROLL_OBJECT_ID = 91_100_003;
    private static final int BOOK_OBJECT_ID = 91_100_004;
    private final HikariDataSource db;
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private final CioSyntheticPopulation actors = new CioSyntheticPopulation(1);
    private CUserSocket session;
    private L1PcInstance pc;

    private TransformReceiptRegression(HikariDataSource db) {
        this.db = db;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected isolated Net.sql JDBC URL");
        var config = new HikariConfig();
        config.setJdbcUrl(args[0]);
        config.setUsername("root");
        config.setMaximumPoolSize(5);
        try (var db = new HikariDataSource(config)) {
            Field pool = field(DatabaseFactory.class, "DATABASE_POOL");
            pool.set(null, db);
            try {
                new TransformReceiptRegression(db).run();
            } finally {
                pool.set(null, null);
            }
        }
    }

    private void run() throws Exception {
        Config.CACHE_MAP_FILES = true;
        Config.MAX_ONLINE_USERS = 8;
        CompanionCategoryInfo.do_load();
        CompanionClassInfo.do_load();
        CompanionFriendShipBonus.do_load();
        CompanionBuffInfo.do_load();
        CompanionPotionInfo.do_load();
        check(CSpriteTimingTable.getInstance().reloadSprites(), "sprite timing startup");
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        int shards = cio.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(16, 32, 16, 16, shards);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, cio, config)) {
            try (var wire = new Socket()) {
                wire.connect(server.localAddress(), 4_000);
                wire.setSoTimeout(4_000);
                var input = new DataInputStream(wire.getInputStream());
                int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
                check(length == Opcodes.S_KEYBYTES.length + 2
                        && Arrays.equals(input.readNBytes(length - 2), Opcodes.S_KEYBYTES), "transport key");
                synchronized (server) {
                    session = ((Set<?>) field(CIOServer.class, "sockets").get(server)).stream()
                            .map(CUserSocket.class::cast).filter(CUserSocket::isConnected).findFirst().orElseThrow();
                }
                bind();
                actors.assertReady(server);
                verifyItemReceipts();
            }
            server.close();
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session workers drain");
            check(cache.flushAll(5_000), "CacheD retirement drained");
            actors.assertDrained(server);
        } finally {
            CSessionLifecycle.stopAccepting();
            boolean drained = cache.drainBeforeOwnerShutdown(5_000);
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS) && drained && cache.shutdown(5_000),
                    "shutdown after receipts");
        }
    }

    private void bind() throws Exception {
        var scroll = storeItem(40096, ITEM_OBJECT_ID, 3);
        var blessed = storeItem(140088, BLESSED_OBJECT_ID, 3);
        var cardScroll = storeItem(875640379, CARD_SCROLL_OBJECT_ID, 3);
        var book = storeItem(30060, BOOK_OBJECT_ID, 3);
        on(() -> {
            pc = actors.bind(session, 0);
            pc.setGm(true);
            pc.setType(3);
            pc.setClassId(734);
            pc.setCurrentSprite(734);
            pc.setMap(map(1));
            pc.setX(34);
            pc.setY(34);
            pc.setMaxHp(100);
            pc.setCurrentHp(100);
            pc.setLevel(80);
            var items = List.of(scroll, blessed, cardScroll, book);
            pc.getInventory().loadItems(items);
            for (var item : items)
                cache.registerLoadedItemOwner(pc.getId(), item.getId());
        });
    }

    private static L1ItemInstance storeItem(int itemId, int objectId, int count) throws SQLException {
        var item = new L1ItemInstance(CObjectDB.GetItem(itemId), count);
        item.setId(objectId);
        item.getLastStatus().updateAll();
        MySqlCharactersItemStorage.storeSnapshot(MySqlCharactersItemStorage.ItemSnapshot.capture(
                item, CioSyntheticPopulation.actorId(0)));
        return item;
    }

    private void verifyItemReceipts() throws Exception {
        use("unknown transform");
        check(storedCount() == 3 && pc.getTransformId() == 0, "unknown form does not debit");
        on(() -> pc.setMap(map(5302)));
        use("re cargo");
        check(storedCount() == 3 && pc.getTransformId() == 0, "blocked map does not debit");
        on(() -> pc.setMap(map(1)));
        execute("CREATE TRIGGER fixture_transform_debit BEFORE UPDATE ON character_items FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rollback transform debit'");
        try {
            use("re cargo");
            check(storedCount() == 3 && pc.getTransformId() == 0
                    && !pc.hasSkillEffect(L1SkillId.SHAPE_CHANGE), "receipt rollback preserves form and item");
        } finally {
            execute("DROP TRIGGER fixture_transform_debit");
        }
        use("re cargo");
        int remaining = storedCount();
        check(remaining == 2 && pc.getTransformId() == 8793 && pc.getCurrentSpriteId() == 8793,
                "durable debit publishes the successful form (count=" + remaining
                        + ", transform=" + pc.getTransformId() + ", sprite=" + pc.getCurrentSpriteId() + ")");
        use("");
        check(storedCount() == 1 && pc.getTransformId() == 0 && pc.getCurrentSpriteId() == 734,
                "durable cancellation restores class appearance");
        use(BLESSED_OBJECT_ID, null);
        check(storedCount(BLESSED_OBJECT_ID) == 3 && pc.getTransformId() == 0, "opening blessed list does not debit");
        use(BLESSED_OBJECT_ID, "re cargo");
        check(storedCount(BLESSED_OBJECT_ID) == 2 && pc.getTransformId() == 8793, "blessed scroll applies the named form");
        chooseCard("nenormal99");
        check(storedCount(CARD_SCROLL_OBJECT_ID) == 3 && pc.getTransformId() == 8793,
                "unsupported card selection keeps the item and previous form");
        chooseCard("nenormal01");
        check(storedCount(CARD_SCROLL_OBJECT_ID) == 2 && pc.getTransformId() == 21314,
                "paid normal card applies after receipt");
        chooseCard("nelowp01");
        check(storedCount(CARD_SCROLL_OBJECT_ID) == 1 && pc.getTransformId() == 21350 && pc.getMaxHp() == 300,
                "rare card replaces the normal form and keeps one ring bonus");
        use(BOOK_OBJECT_ID, null);
        check(pc.isMagicItem() && storedCount(BOOK_OBJECT_ID) == 3, "opening a book does not debit");
        chooseCard("unknown transform");
        check(pc.isMagicItem() && storedCount(BOOK_OBJECT_ID) == 3 && pc.getTransformId() == 21350,
                "invalid book selection leaves the paid menu active");
        chooseCard("re cargo");
        check(!pc.isMagicItem() && storedCount(BOOK_OBJECT_ID) == 2 && pc.getTransformId() == 8793,
                "book selection closes after receipt and transform");
        System.out.println("PASS normal and blessed scrolls, card and book receipts, rollback and durable apply/cancel");
    }

    private void use(String name) throws Exception {
        use(ITEM_OBJECT_ID, name);
    }

    private void use(int itemObjectId, String name) throws Exception {
        on(() -> CS_ItemUse.handle(session, itemObjectId, 0, 0, 0, name, 0));
        drainReceipt();
    }

    private void chooseCard(String name) throws Exception {
        on(() -> CS_NpcAction.handle(session, pc.getId(), name, null, null));
        drainReceipt();
    }

    private void drainReceipt() throws Exception {
        check(cache.flushAll(5_000), "receipt drained");
        on(() -> {});
    }

    private void on(Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        cio.completeOnShard(session.affinityShard(), action, failure -> {
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        result.get(5, TimeUnit.SECONDS);
    }

    private static L1V1Map map(int id) {
        return new L1V1Map(id, new byte[8][8], 32, 32,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private int storedCount() throws SQLException {
        return storedCount(ITEM_OBJECT_ID);
    }

    private int storedCount(int itemObjectId) throws SQLException {
        try (var connection = db.getConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT count FROM character_items WHERE id=" + itemObjectId)) {
            check(rows.next(), "scroll item row");
            return rows.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = db.getConnection(); var query = connection.createStatement()) {
            query.execute(sql);
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean ok, String detail) {
        if (!ok) throw new AssertionError(detail);
    }
}
