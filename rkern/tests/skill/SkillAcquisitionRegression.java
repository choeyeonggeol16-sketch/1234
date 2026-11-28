import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.category.CompanionCategoryInfo;
import l1j.server.server.model.character.companion.basic.friendship.CompanionFriendShipBonus;
import l1j.server.server.model.character.companion.basic.potion.CompanionPotionInfo;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.acquire.CSkillAcquireDB.Book;
import l1j.server.server.model.character.skill.acquire.CSkillAcquisition;
import l1j.server.server.model.character.skill.acquire.CSkillBook;
import l1j.server.server.model.character.skill.acquire.CSkillMaster;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.classchange.ClassChange;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_BuySpell;
import l1j.server.server.model.network.core.cs.packet.CS_ExchangeSpell;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.CSkillAcquireStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Run against an isolated clean installation of Net.sql. This fixture writes synthetic records. */
public final class SkillAcquisitionRegression {
    private static final int OWNER = CioSyntheticPopulation.actorId(0);
    private static final int BOOK = 40170;
    private static final int CLASS_POTION = 51094;
    private static final int HELMET = 20014;
    private static final int FIRST_ITEM = 91_000_000;

    private final HikariDataSource db;
    private final CacheD cache = CacheD.getInstance();
    private final CIOThreadCallback cio = CIOThreadCallback.getInstance();
    private CUserSocket session;
    private L1PcInstance player;

    private SkillAcquisitionRegression(HikariDataSource db) { this.db = db; }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected isolated JDBC URL and test user");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(args[0]);
        config.setUsername(args[1]);
        config.setPassword(System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", ""));
        config.setMaximumPoolSize(5);
        try (HikariDataSource db = new HikariDataSource(config)) {
            field(DatabaseFactory.class, "DATABASE_POOL").set(null, db);
            try { new SkillAcquisitionRegression(db).run(); }
            finally { field(DatabaseFactory.class, "DATABASE_POOL").set(null, null); }
        }
    }

    private void run() throws Exception {
        verifySkillDefinitionAndOwnership();
        Config.CACHE_MAP_FILES = true;
        Config.MAX_ONLINE_USERS = 8;
        CompanionCategoryInfo.do_load();
        CompanionClassInfo.do_load();
        CompanionFriendShipBonus.do_load();
        CompanionBuffInfo.do_load();
        CompanionPotionInfo.do_load();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        int shards = cio.bindNetworkIngress(config);
        var buffers = new CIOBufferPool(16, 32, 16, 16, shards);
        CioSyntheticPopulation actors = new CioSyntheticPopulation(1);
        try (var server = new CIOServer(address, 8, shards, 8, buffers, cio, config)) {
            try (var wire = new Socket()) {
                wire.connect(server.localAddress(), 4_000);
                wire.setSoTimeout(4_000);
                var input = new DataInputStream(wire.getInputStream());
                int length = input.readUnsignedByte() | (input.readUnsignedByte() << 8);
                check(length == Opcodes.S_KEYBYTES.length + 2
                        && Arrays.equals(input.readNBytes(length - 2), Opcodes.S_KEYBYTES), "CIO TCP key");
                synchronized (server) {
                    session = ((Set<?>) field(CIOServer.class, "sockets").get(server)).stream()
                            .map(CUserSocket.class::cast).filter(CUserSocket::isConnected).findFirst().orElseThrow();
                }
                bindPlayer(actors);
                actors.assertReady(server);
                verifyNpc();
                verifyBookAndEquipment();
                verifyGmAndClassChange();
                long restartDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                while ((!player.isLogoutComplete() || session.getActiveChar() != null)
                        && System.nanoTime() < restartDeadline) Thread.sleep(10);
                check(player.isLogoutComplete() && session.getActiveChar() == null,
                        "class change retires the original game session");
            }
            server.close();
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "transport released test session");
            check(cache.flushAll(5_000), "CacheD removed released owner");
            actors.assertDrained(server);
        } finally {
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session releases drained");
            boolean dbDrained = cache.drainBeforeOwnerShutdown(5_000);
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO stopped");
            check(dbDrained && cache.shutdown(5_000), "CacheD stopped");
        }
    }

    private void verifySkillDefinitionAndOwnership() throws Exception {
        CSkillDB definitions = CSkillDB.getInstance();
        var rules = definitions.acquireDB();
        try (Connection con = db.getConnection(); var sql = con.createStatement();
             ResultSet rows = sql.executeQuery("SELECT skill_id FROM skills")) {
            int count = 0;
            while (rows.next()) {
                check(definitions.getTemplate(rows.getInt(1)) != null, "missing valid real definition");
                count++;
            }
            check(count == 872, "all Net.sql definitions loaded");
        }
        String[] names = {"마법서 (힐)", "흑정령의 수정 (블라인드 하이딩)", "정령의 수정 (레지스트 매직)",
                "기술서 (쇼크 스턴)", "마법서 (트루 타겟)", "용기사의 서판(드래곤 스킨)", "기억의 수정(미러 이미지)"};
        int[] ids = {1, 97, 129, 87, 113, 181, 201};
        int i = 0;
        for (Book book : Book.values()) {
            int id = ids[i];
            check(rules.findBook(book, names[i++]).stream().anyMatch(s -> s.getSkillId() == id),
                    "book definition " + book);
        }
        check(rules.findWarriorBook(210121).getSkillId() == 225 && rules.findWarriorBook(0) == null,
                "warrior book ID index");
        check(rules.npcLearnMask(0, 9, false) == 0 && rules.npcLearnMask(0, 10, false) == 255
                && rules.npcLearnMask(0, 20, false) == 65535, "crown tier boundary");
        check(rules.npcLearnMask(2, 7, false) == 0 && rules.npcLearnMask(2, 8, false) == 255
                && rules.npcLearnMask(2, 16, false) == 65535 && rules.npcLearnMask(2, 24, false) == 0xffffff,
                "elf tier boundaries");
        check(rules.npcLearnMask(7, 49, false) == 0 && rules.npcLearnMask(7, 49, true) == 0x7fffff,
                "warrior route-specific legacy threshold");
        check(CSkillAcquisition.skillListPacket(new int[]{1, 234, 1_111_111})[29] == 2,
                "legacy skill 234 slot and orphan definition");

        L1Character creature = new L1Character();
        creature.RestoreAcquiredSkills(new int[] {234, 1, 234, 101065004});
        check(Arrays.equals(creature.CopyAcquiredSkills(), new int[] {1, 234, 101065004}), "ordered unique restore");
        check(!creature.AcquireSkill(1) && !creature.AcquireSkill(0) && creature.GetAcquiredSkillLevel(1) == 1,
                "idempotent acquire and L1 ownership level");
        try (var concurrency = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var write = concurrency.submit(() -> {
                for (int id = 1000; id < 1256; id++) creature.AcquireSkill(id);
            });
            var read = concurrency.submit(() -> {
                for (int n = 0; n < 10_000; n++) {
                    check(creature.HaveSkill(234) && !creature.HaveSkill(6666), "stable concurrent read");
                }
            });
            write.get(5, TimeUnit.SECONDS);
            read.get(5, TimeUnit.SECONDS);
        }
        check(creature.DeleteAcquireSkill(234) && !creature.DeleteAcquireSkill(234) && creature.HaveSkill(1255),
                "single delete and no concurrent lost grant");
        creature.DeleteAllAcquireSkills();
        check(creature.CopyAcquiredSkills().length == 0, "bulk delete");
        execute("RENAME TABLE etcitem TO skill_reload_hidden_item");
        try {
            check(!CSkillDB.reload() && CSkillDB.getInstance() == definitions,
                    "partial reload retained the old definition and book snapshot");
        } finally {
            execute("RENAME TABLE skill_reload_hidden_item TO etcitem");
        }
        System.out.println("PASS 872 Net.sql definitions, seven book families, warrior IDs, NPC gates, ownership and atomic reload");
    }

    private void bindPlayer(CioSyntheticPopulation actors) throws Exception {
        int exp = ExpTable.getExpByLevel(55);
        execute("INSERT INTO characters(objid,char_name,account_name,Clanname,notes,ReturnStat,level,HighLevel,Exp,Type,Class) VALUES("
                + OWNER + ",'cb_socket0','cb_socket0','','',0,55,55," + exp + ",3,734)");
        List<L1ItemInstance> items = List.of(item(L1ItemId.ADENA, 1000, 0), item(L1ItemId.PANS_LEATHER, 600, 1),
                item(BOOK, 2, 2), item(CLASS_POTION, 2, 3), item(HELMET, 1, 4));
        for (L1ItemInstance item : items)
            MySqlCharactersItemStorage.storeSnapshot(MySqlCharactersItemStorage.ItemSnapshot.capture(item, OWNER));
        on(() -> {
            player = actors.bind(session, 0);
            player.setExp(exp);
            player.setLevel(55);
            player.setHighLevel(55);
            player.setType(3);
            player.setClassId(734);
            byte[][] tiles = new byte[8][8];
            for (byte[] row : tiles) Arrays.fill(row, (byte) 0x10);
            var map = new L1V1Map(1, tiles, 32, 32, false, true, true, true, true, true, true, true, true, true, true, false);
            player.setMap(map);
            player.setX(34);
            player.setY(34);
            player.setMaxHp(30);
            player.setCurrentHp(20);
            player.getInventory().loadItems(items);
            for (L1ItemInstance item : items) cache.registerLoadedItemOwner(OWNER, item.getId());
        });
    }

    private static L1ItemInstance item(int id, int count, int offset) {
        var template = CObjectDB.GetItem(id);
        check(template != null, "fixture item exists: " + id);
        var item = new L1ItemInstance(template, count);
        item.setId(FIRST_ITEM + offset);
        item.getLastStatus().updateAll();
        return item;
    }

    private void verifyNpc() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch paused = new CountDownLatch(1);
        var held = submit(() -> {
            CS_BuySpell.handle(session, 1, 1, 0, 0);
            CS_BuySpell.handle(session, 1, 1, 0, 0);
            paused.countDown();
            try {
                if (!release.await(4, TimeUnit.SECONDS)) throw new AssertionError("CIO replay release");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        try {
            check(paused.await(3, TimeUnit.SECONDS), "owner paused after submitting NPC request");
            long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (itemCount(0) != 500 && System.nanoTime() < limit) Thread.sleep(10);
            check(itemCount(0) == 500 && Arrays.equals(storedSkills(), new int[] {1, 9}), "NPC batch durable debit and rows");
            check(!player.HaveSkill(1) && !player.HaveSkill(9), "CIO has not replayed or published success yet");
        } finally {
            release.countDown();
        }
        held.get(5, TimeUnit.SECONDS);
        drain();
        check(player.HaveSkill(1) && player.HaveSkill(9), "owner replay publishes purchased skills");
        on(() -> CS_BuySpell.handle(session, 1, 1, 0, 0));
        drain();
        check(itemCount(0) == 500, "known skills cannot repeatedly debit");
        execute("INSERT INTO character_skills(char_obj_id,skill_id,skill_name) VALUES(" + OWNER + ",2,'duplicate')");
        on(() -> CS_BuySpell.handle(session, 6, 0, 0, 0));
        drain();
        check(itemCount(0) == 500 && !player.HaveSkill(3) && !contains(storedSkills(), 3), "SQL duplicate rolls back cash and other grants");
        on(() -> CS_BuySpell.handle(session, 0, 0, 1, 0));
        drain();
        check(itemCount(0) == 500 && !player.HaveSkill(17), "insufficient adena");
        on(() -> CS_ExchangeSpell.handle(session, (1 << 4) | (1 << 5) | (1 << 9), false));
        drain();
        check(itemCount(1) == 180 && player.HaveSkill(5) && !player.HaveSkill(6) && player.HaveSkill(10),
                "Horun keeps existing first-tier repeat price, partial selection and higher-tier free grant");
        System.out.println("PASS NPC batch live replay, concurrent duplicates, database rollback, insufficient cost and Horun exchange");
    }

    private void verifyBookAndEquipment() throws Exception {
        var book = player.getInventory().GetByServerID(FIRST_ITEM + 2);
        int bookSkill = CSkillDB.getInstance().acquireDB().findBook(Book.GENERAL, book.getItem()).getLast().getSkillId();
        on(() -> CSkillBook.learn(player, book, Book.GENERAL));
        drain();
        check(itemCount(2) == 1 && player.HaveSkill(bookSkill) && contains(storedSkills(), bookSkill), "book consumed with skill row");
        on(() -> CSkillBook.learn(player, book, Book.GENERAL));
        drain();
        check(itemCount(2) == 1, "duplicate book is preserved");
        on(() -> {
            var helmet = player.getInventory().GetByServerID(FIRST_ITEM + 4);
            player.EquipItem(helmet);
            check(player.getEquipment().HasGrantedSkill(1), "helmet grants heal");
            player.UnequipItem(helmet);
            check(!player.getEquipment().HasGrantedSkill(1) && player.HaveSkill(1),
                    "removing helmet leaves overlapping learned spell");
        });
        check(!contains(storedSkills(), 19), "equipment skills absent from permanent ledger");
        System.out.println("PASS book commit, duplicate cost and equipment ownership overlap");
    }

    private void verifyGmAndClassChange() throws Exception {
        on(() -> CSkillMaster.Grant(player, player));
        drain();
        check(player.HaveSkill(80) && contains(storedSkills(), 80), "GM batch grant acknowledged");
        check(scalar("SELECT COUNT(*) FROM passive_user_info WHERE character_id=" + OWNER) > 0,
                "GM passive rows are part of the batch");
        var potion = player.getInventory().GetByServerID(FIRST_ITEM + 3);
        var helmet = player.getInventory().GetByServerID(FIRST_ITEM + 4);
        on(() -> {
            player.EquipItem(helmet);
            check(helmet.isEquipped(), "equipped before class change");
            player.getInventory().saveItem(helmet, l1j.server.server.model.inventory.CUserInventory.COL_EQUIPPED);
        });
        drain();
        check(scalar("SELECT is_equipped FROM character_items WHERE id=" + helmet.getId()) == 1,
                "equipment preimage recorded before class change");
        execute("INSERT INTO character_buff(char_obj_id,skill_id,remaining_time,poly_id) VALUES("
                + OWNER + "," + L1SkillId.프라이드 + ",0,0)");
        execute("CREATE TRIGGER skill_fixture_block_class BEFORE DELETE ON character_skills FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture rejects class cleanup'");
        try {
            on(() -> ClassChange.usePotion(player, potion));
            drain();
            check(itemCount(3) == 2 && player.getType() == 3 && player.HaveSkill(80), "class change rollback protects potion and skills");
            check(scalar("SELECT Type FROM characters WHERE objid=" + OWNER) == 3
                    && scalar("SELECT is_equipped FROM character_items WHERE id=" + helmet.getId()) == 1,
                    "class SQL rollback keeps equipped gear");
        } finally {
            execute("DROP TRIGGER skill_fixture_block_class");
        }
        on(() -> ClassChange.usePotion(player, potion));
        drain();
        check(itemCount(3) == 1 && player.getType() == 1 && player.CopyAcquiredSkills().length == 0,
                "class change publishes clear, new class and one potion debit");
        check(!helmet.isEquipped() && !player.getEquipment().HasGrantedSkill(1), "class change retires gear spells");
        check(storedSkills().length == 0 && scalar("SELECT Type FROM characters WHERE objid=" + OWNER) == 1
                && scalar("SELECT COUNT(*) FROM passive_user_info WHERE character_id=" + OWNER) == 0
                && scalar("SELECT COUNT(*) FROM character_buff WHERE char_obj_id=" + OWNER
                        + " AND skill_id=" + L1SkillId.프라이드) == 0
                && scalar("SELECT is_equipped FROM character_items WHERE id=" + helmet.getId()) == 0,
                "reconnect would restore the committed class, inventory and empty skills");
        System.out.println("PASS GM batched grant, active/passive class deletion, rollback and durable restore");
    }

    private void execute(String query) throws SQLException {
        try (var con = db.getConnection(); var sql = con.createStatement()) { sql.execute(query); }
    }

    private int scalar(String query) throws SQLException {
        try (var con = db.getConnection(); var sql = con.createStatement(); var rows = sql.executeQuery(query)) {
            check(rows.next(), "fixture expected SQL row");
            return rows.getInt(1);
        }
    }

    private int itemCount(int offset) throws SQLException {
        return scalar("SELECT count FROM character_items WHERE id=" + (FIRST_ITEM + offset));
    }

    private int[] storedSkills() throws SQLException {
        try (var con = db.getConnection()) { return CSkillAcquireStorage.load(con, OWNER); }
    }

    private void drain() throws Exception {
        check(cache.flushAll(5_000), "CacheD receipts and owner replay drained");
        on(() -> {});
    }

    private CompletableFuture<Void> submit(Runnable action) {
        var result = new CompletableFuture<Void>();
        cio.completeOnShard(session.affinityShard(), action, failure -> {
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        });
        return result;
    }

    private void on(Runnable action) throws Exception { submit(action).get(5, TimeUnit.SECONDS); }

    private static boolean contains(int[] sorted, int id) { return Arrays.binarySearch(sorted, id) >= 0; }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
