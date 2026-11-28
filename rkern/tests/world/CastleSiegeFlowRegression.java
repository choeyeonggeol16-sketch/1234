package l1j.server.server.model.world.siege;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.TIntObjectMap;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1CrownInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.character.animation.CSpriteTiming;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.character.skill.CSkillEffectActivation;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;

public final class CastleSiegeFlowRegression {
    private static final int KENT = L1CastleLocation.KENT_CASTLE_ID;
    private static final int GIRAN = L1CastleLocation.GIRAN_CASTLE_ID;
    private static final int ADEN = L1CastleLocation.ADEN_CASTLE_ID;
    private static final int X = L1CastleLocation.KENT_TOWER_X;
    private static final int Y = L1CastleLocation.KENT_TOWER_Y;
    private static final int TOWER = 81111;
    private static final int CROWN = 81125;

    public static void main(String[] args) throws Exception {
        var mapSetup = Class.forName("l1j.server.server.model.WorldVisibilityRegression")
                .getDeclaredMethod("installMapMetadata");
        mapSetup.setAccessible(true);
        mapSetup.invoke(null);
        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());
        Config.ALT_WAR_TIME_UNIT = Calendar.MINUTE;
        Config.ALT_WAR_TIME = 60;
        Config.ALT_WAR_INTERVAL_UNIT = Calendar.DAY_OF_MONTH;
        Config.ALT_WAR_INTERVAL = 7;
        Config.수성시간 = 10;
        Logger.getLogger(CacheD.class.getName()).setUseParentHandlers(false);

        Field poolField = field(DatabaseFactory.class, "DATABASE_POOL");
        try (var data = args.length > 1 ? storageFixture(args[1]) : new FixtureDatabase()) {
            poolField.set(null, data);
            CWorld world = CWorld.getInstance();
            L1WorldMap maps = L1WorldMap.getInstance();
            var kent = terrain(L1CastleLocation.KENT_TOWER_MAP);
            var other = terrain(701);
            maps.addMap(kent);
            maps.addMap(other);
            installTemplates();
            CCastleSiegeManager manager = CCastleSiegeManager.getInstance();
            CCastle castle = createSiege(KENT);
            CCastle second = createSiege(GIRAN);
            CCastle aden = createSiege(ADEN);
            field(CCastleSiegeManager.class, "_castleWars")
                    .set(manager, Map.of(KENT, castle, GIRAN, second, ADEN, aden));
            try {
                String scenario = args.length == 0 ? "all" : args[0];
                if ("all".equals(scenario) || "concurrent".equals(scenario)) {
                    simultaneousDeclarations(castle, second);
                }
                if ("all".equals(scenario) || "range".equals(scenario)) {
                    wrongMapCannotClaim(world, kent, other, castle);
                }
                if ("all".equals(scenario) || "zones".equals(scenario)) {
                    switchingSieges(world, kent, other, castle, second);
                }
                if ("all".equals(scenario) || "tower-reveal".equals(scenario)) {
                    guardianTowerReveals(world, kent, other, castle);
                }
                if ("all".equals(scenario) || "capture".equals(scenario)) {
                    unownedCastleCanBeTaken(world, kent, manager, castle, data, args.length > 1);
                }
            } finally {
                manager.dispose();
                CInstanceWork.stopAccepting();
                check(CInstanceWork.awaitDrained(3, TimeUnit.SECONDS), "instance workers drain");
                CacheD.getInstance().shutdown(2_000);
                CIOThreadCallback.getInstance().shutdown();
                check(CIOThreadCallback.getInstance().awaitTermination(3, TimeUnit.SECONDS), "CIO workers drain");
                CWorldWork.stopAccepting();
                check(CWorldWork.awaitDrained(3, TimeUnit.SECONDS), "world workers drain");
                for (var object : world.snapshotZoneOccupants(kent)) world.leaveAndRemoveObject(object);
                maps.removeMap(kent);
                maps.removeMap(other);
            }
        } finally {
            poolField.set(null, null);
        }
    }

    private static void simultaneousDeclarations(CCastle first, CCastle second) throws Exception {
        var clan = clan(901, 1901);
        ReentrantLock firstLock = (ReentrantLock) first._warLock;
        ReentrantLock secondLock = (ReentrantLock) second._warLock;
        var a = new FutureTask<>(() -> first.proclaim(clan));
        var b = new FutureTask<>(() -> second.proclaim(clan));
        firstLock.lock();
        secondLock.lock();
        try {
            Thread one = Thread.ofPlatform().daemon().start(a);
            Thread two = Thread.ofPlatform().daemon().start(b);
            await(() -> firstLock.hasQueuedThread(one) && secondLock.hasQueuedThread(two),
                    "both declarations reached the individual castle locks");
        } finally {
            firstLock.unlock();
            secondLock.unlock();
        }
        try {
            CCastle left = a.get(2, TimeUnit.SECONDS);
            CCastle right = b.get(2, TimeUnit.SECONDS);
            check((left == null) != (right == null), "only one of two simultaneous declarations succeeds");
            CCastle winner = left != null ? left : right;
            check(clan.getCurrentWar() == winner
                    && (first.getOffenseClan(clan.getClanId()) != null) != (second.getOffenseClan(clan.getClanId()) != null),
                    "the clan backlink and one attacker list agree");
            System.out.println("PASS competing castle declarations and attacker backlink");
        } finally {
            first.removeOffenseClan(clan);
            second.removeOffenseClan(clan);
        }
    }

    private static void wrongMapCannotClaim(CWorld world, L1V1Map kent, L1V1Map other, CCastle castle) throws Exception {
        var clan = clan(902, 1902);
        world.storeClan(clan);
        var king = king(clan, other);
        var crown = new L1CrownInstance(npc(CROWN, "L1Crown"));
        crown.setId(2902);
        crown.getLocation().set(X, Y, kent);
        world.storeAndEnterWorld(crown);
        try {
            check(castle.proclaim(clan) == castle, "king's clan is registered");
            crown.onAction(king);
            check(clan.getCastleId() == 0 && world.findObject(crown.getId()) == crown,
                    "a king at the same coordinates in another map cannot claim the crown");
            king.getLocation().set(X + 2, Y, kent);
            crown.onAction(king);
            check(clan.getCastleId() == 0, "a king two tiles away cannot claim the crown");
            System.out.println("PASS crown map identity and range");
        } finally {
            world.leaveAndRemoveObject(crown);
            castle.removeOffenseClan(clan);
            world.removeClan(clan);
        }
    }

    private static void unownedCastleCanBeTaken(CWorld world, L1V1Map kent, CCastleSiegeManager manager,
            CCastle castle, HikariDataSource data, boolean durable) throws Exception {
        var clan = clan(903, 1903);
        world.storeClan(clan);
        var king = king(clan, kent);
        var tower = new L1TowerInstance(npc(TOWER, "L1Tower"));
        tower.setId(2903);
        tower.getLocation().set(X, Y, kent);
        world.storeAndEnterWorld(tower);
        try {
            check(castle.getDefenseClan() == null && castle.IsUnderSiege(), "unowned and active castle fixture");
            tower.receiveDamage(king, 20);
            check(tower.getCurrentHp() == 100, "unregistered clan cannot damage the tower");
            check(castle.proclaim(clan) == castle, "clan declares a siege on the unowned castle");
            tower.receiveDamage(king, 40);
            check(tower.getCurrentHp() == 60, "registered attackers can damage the unowned tower");
            tower.receiveDamage(king, 60);
            check(tower.isDead(), "tower was destroyed");
            var crowns = world.getNpcsByTemplateId(CROWN);
            check(crowns.size() == 1 && crowns.getFirst() instanceof L1CrownInstance, "one crown spawned");
            var crown = (L1CrownInstance) crowns.getFirst();
            tower.receiveDamage(king, 500);
            check(world.getNpcsByTemplateId(CROWN).size() == 1, "repeated tower damage cannot spawn a second crown");
            if (durable) {
                check(CacheD.getInstance().flushAll(3_000), "ready receipt schema");
                try (var con = data.getConnection(); var sql = con.createStatement()) {
                    sql.executeUpdate("INSERT INTO castle VALUES (" + KENT + ",'Kent',NOW(),0,0)");
                }
                crown.onAction(king);
                check(CacheD.getInstance().flushAll(3_000), "failed capture drained");
                check(world.findObject(crown.getId()) == crown && clan.getCastleId() == 0,
                        "missing winner rolls back and preserves crown");
                try (var con = data.getConnection(); var sql = con.createStatement()) {
                    sql.executeUpdate("INSERT INTO clan_data VALUES (903,0)");
                }
            }
            crown.onAction(king);
            if (!durable) {
                check(clan.getCastleId() == 0 && world.findObject(crown.getId()) == crown,
                        "castle capture requires durable receipt storage");
                System.out.println("PASS siege capture fails closed without receipt storage");
                return;
            }
            crown.onAction(king);
            check(CacheD.getInstance().flushAll(3_000), "receive and publish capture");
            try (var con = data.getConnection(); var sql = con.createStatement();
                    var rows = sql.executeQuery("SELECT hascastle FROM clan_data WHERE clan_id=903")) {
                check(rows.next() && rows.getInt(1) == KENT, "one durable castle owner");
            }
            check(castle.getDefenseClan() == clan && clan.getCastleId() == KENT
                    && clan.getCurrentWar() == castle && castle.getOffenseClan(clan.getClanId()) == null,
                    "capturing king becomes the defender and its attack membership ends");
            check(world.getNpcsByTemplateId(CROWN).isEmpty() && castle.IsUnderSiege(),
                    "the captured crown disappears and the siege continues");
            long start = castle.getNextStartMillis();
            castle.requestEndSiege();
            Field phase = field(CCastle.class, "_state");
            await(() -> {
                try {
                    return phase.get(castle) == CastleSiegeState.IDLE && castle.getNextStartMillis() > start
                            && world.getNpcsByTemplateId(TOWER).size() == 1;
                } catch (IllegalAccessException failure) {
                    throw new AssertionError(failure);
                }
            }, "end restores tower and schedules next siege");
            check(manager.get(KENT) == castle && castle.getDefenseClan() == clan, "owner survives the end");
            System.out.println("PASS unowned siege declaration, tower damage, crown spawn, capture and close");
        } finally {
            world.leaveAndRemoveObject(tower);
            world.removeClan(clan);
        }
    }

    private static void switchingSieges(CWorld world, L1V1Map field, L1V1Map other, CCastle first, CCastle second) {
        var clan = clan(904, 1904);
        world.storeClan(clan);
        var player = new BuffObserver();
        player.setClanid(clan.getClanId());
        try {
            check(second.proclaim(clan) == second, "clan participates in the second siege");
            player.getLocation().set(X, Y, field);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.siegeZoneWarId == first.getId(), "enters the first siege zone");
            player.getLocation().set(L1CastleLocation.GIRAN_TOWER_X, L1CastleLocation.GIRAN_TOWER_Y, field);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.siegeZoneWarId == second.getId(), "direct movement refreshes to the second siege");
            first.offLordBuff(player);
            check(player.buffRemovals == 0, "another active siege cannot remove this participant's buff");
            second.offLordBuff(player);
            check(player.buffRemovals == 1, "the participant's siege can remove its buff");
            player.getLocation().set(X, Y, other);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.siegeZoneWarId == 0, "leaving both sieges clears the zone");
            System.out.println("PASS switching active siege zones and preserving the other siege's buff");
        } finally {
            second.removeOffenseClan(clan);
            world.removeClan(clan);
        }
    }

    private static final class BuffObserver extends L1PcInstance {
        int buffRemovals;

        @Override
        public void removeSkillEffect(int skillId) {
            if (skillId == L1SkillId.주군의버프) buffRemovals++;
            else super.removeSkillEffect(skillId);
        }
    }

    private static void guardianTowerReveals(CWorld world, L1V1Map field, L1V1Map other, CCastle castle)
            throws Exception {
        var tower = new L1TowerInstance(npc(TOWER, "L1Tower"));
        tower.setId(2910);
        tower.getLocation().set(X, Y, field);
        world.storeAndEnterWorld(tower);
        var player = new L1PcInstance();
        player.setId(1910);
        player.setName("Tower watcher");
        player.getLocation().set(X + 4, Y + 4, field);
        world.storeAndEnterWorld(player);
        L1TowerInstance subtower = null;
        try {
            player.skillEffectSetFromOwner(L1SkillId.INVISIBILITY, 0);
            player.skillEffectSetFromOwner(L1SkillId.BLIND_HIDING, 0);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.isInvisble(), "invisibility remains four tiles from the tower");
            player.getLocation().set(X + 3, Y + 3, field);
            world.moveTo(player, field.getId(), X + 4, Y + 4);
            CCastleSiegeManager.updateSiegeZone(player);
            check(!player.hasSkillEffect(L1SkillId.INVISIBILITY) && !player.hasSkillEffect(L1SkillId.BLIND_HIDING),
                    "a walking step to the three-tile boundary removes both skills within the same siege");

            var start = CSkillEffectActivation.class.getDeclaredMethod("startInvisibility",
                    L1PcInstance.class, int.class, int.class, int.class, int.class, boolean.class);
            start.setAccessible(true);
            start.invoke(null, player, L1SkillId.INVISIBILITY, 10, 0, 0, false);
            start.invoke(null, player, L1SkillId.BLIND_HIDING, 10, 0, 0, false);
            player.invisibleItem();
            check(!player.isInvisble(), "spells and invisible armor do not reactivate by a live siege tower");

            check(world.relocateVisibleObject(player, X, Y, other), "move to the other map");
            player.invisibleItem();
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.isInvisble(), "a tower on another map does not reveal the character");
            check(world.relocateVisibleObject(player, X, Y, field), "teleport to the tower map");
            CCastleSiegeManager.updateSiegeZone(player);
            check(!player.isInvisble(), "teleport arrival inside the range removes invisibility");

            player.setGmInvis(true);
            player.skillEffectSetFromOwner(L1SkillId.INVISIBILITY, 0);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.isGmInvis() && player.isInvisble(), "admin invisibility is preserved");
            player.setGmInvis(false);
            player.delInvis();

            Field phase = field(CCastle.class, "_state");
            phase.set(castle, CastleSiegeState.READY);
            player.invisibleItem();
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.isInvisble(), "a live tower outside the active siege does not reveal");
            phase.set(castle, CastleSiegeState.RUN);
            tower.setDead(true);
            CCastleSiegeManager.updateSiegeZone(player);
            check(player.isInvisble(), "a destroyed tower does not reveal");

            subtower = new L1TowerInstance(npc(81190, "L1Tower"));
            subtower.setId(2911);
            int sx = L1CastleLocation.ADEN_SUB_TOWER1_X;
            int sy = L1CastleLocation.ADEN_SUB_TOWER1_Y;
            subtower.getLocation().set(sx, sy, field);
            world.storeAndEnterWorld(subtower);
            check(world.relocateVisibleObject(player, sx + 3, sy, field), "move to the Aden subtower");
            CCastleSiegeManager.updateSiegeZone(player);
            check(!player.isInvisble(), "a live Aden subtower reveals");
            System.out.println("PASS guardian tower invisibility on walking, teleport arrival, skills and armor");
        } finally {
            world.leaveAndRemoveObject(player);
            if (subtower != null) world.leaveAndRemoveObject(subtower);
            world.leaveAndRemoveObject(tower);
        }
    }

    private static CCastle createSiege(int id) throws Exception {
        Calendar start = CGameTime.getRealTimeCalendar();
        start.add(Calendar.MINUTE, -1);
        var entry = new CastleSiegeTable.Entry(id, "Castle " + id, new CastleSiegeSchedule(start), 10, 0);
        CCastle castle = WarFactory.createCastleSiege(null, entry);
        field(CCastle.class, "_state").set(castle, CastleSiegeState.RUN);
        return castle;
    }

    private static L1V1Map terrain(int id) {
        return new L1V1Map(id, new byte[1056][680], X - 10, L1CastleLocation.GIRAN_TOWER_Y - 10,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static L1Npc npc(int id, String impl) {
        var npc = new L1Npc();
        npc.set_npcId(id);
        npc.set_name("Test " + id);
        npc.set_nameid(npc.get_name());
        npc.set_hp(100);
        npc.setImpl(impl);
        return npc;
    }

    @SuppressWarnings("unchecked")
    private static void installTemplates() throws Exception {
        var data = CObjectDB.LoadNPC();
        var templates = (TIntObjectMap<L1Npc>) field(data.getClass(), "_npcs").get(data);
        templates.put(TOWER, npc(TOWER, "L1Tower"));
        templates.put(CROWN, npc(CROWN, "L1Crown"));
        templates.put(81158, npc(81158, "L1Door"));
    }

    private static L1Clan clan(int id, int leader) {
        var clan = new L1Clan();
        clan.setClanId(id);
        clan.setClanName("Clan " + id);
        clan.setLeaderId(leader);
        return clan;
    }

    private static L1PcInstance king(L1Clan clan, L1V1Map map) throws Exception {
        var king = new L1PcInstance();
        king.setId(clan.getLeaderId());
        king.setName("King " + clan.getLeaderId());
        king.setClanid(clan.getClanId());
        king.setClassId(L1PcInstance.CLASSID_PRINCE);
        var sprite = CSpriteTiming.class.getDeclaredConstructor(int.class, int.class, int.class,
                int.class, int[].class, List.class);
        sprite.setAccessible(true);
        field(L1Character.class, "currentSpriteTiming").set(king,
                sprite.newInstance(0, 1, 1, 0, new int[128], List.of()));
        king.getLocation().set(X, Y, map);
        check(king.isCrown() && king.getCurrentSpriteId() == 0 && king.getClan() == clan, "valid king fixture");
        return king;
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }

    private static void await(BooleanSupplier condition, String label) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(condition.getAsBoolean(), label);
    }

    private static HikariDataSource storageFixture(String url) throws SQLException {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("root");
        config.setPassword(System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", ""));
        var data = new HikariDataSource(config);
        try (var con = data.getConnection(); var sql = con.createStatement()) {
            for (String table : new String[] {"character_warehouse", "clan_warehouse", "character_elf_warehouse",
                    "character_special_warehouse", "character_supplementary_service", "character_items",
                    "character_package_warehouse", "character_teleport"}) {
                sql.executeUpdate("CREATE TABLE " + table + "(id INT PRIMARY KEY) ENGINE=InnoDB");
            }
            sql.executeUpdate("CREATE TABLE characters (objid INT PRIMARY KEY,ClanID INT,char_name VARCHAR(32),ClanRank INT,"
                    + "level INT,notes VARCHAR(32),Type INT) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE companion_instance (object_id INT PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE letter (item_object_id INT PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE npc (npcid INT PRIMARY KEY, family VARCHAR(45)) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE npc_born (id INT PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE spawnlist_door (id INT PRIMARY KEY) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE clan_data (clan_id INT PRIMARY KEY, hascastle INT) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE castle (castle_id INT PRIMARY KEY,name VARCHAR(45),war_time DATETIME,"
                    + "tax_rate INT,public_money INT) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE cached_transaction_receipts (tx_id CHAR(36) PRIMARY KEY,kind VARCHAR(16),"
                    + "owner_a INT,owner_b INT) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE character_quests (char_id INT,quest_id INT,PRIMARY KEY(char_id,quest_id)) ENGINE=InnoDB");
            sql.executeUpdate("CREATE TABLE character_level_gifts (char_id INT,gift_level INT,"
                    + "PRIMARY KEY(char_id,gift_level)) ENGINE=InnoDB");
        } catch (SQLException failure) {
            data.close();
            throw failure;
        }
        return data;
    }

    private static final class FixtureDatabase extends HikariDataSource {
        @Override
        public Connection getConnection() {
            return fixture(Connection.class, method -> switch (method) {
                case "prepareStatement" -> fixture(PreparedStatement.class, operation -> switch (operation) {
                    case "executeQuery" -> fixture(ResultSet.class, result -> switch (result) {
                        case "next" -> false;
                        case "getMetaData" -> fixture(ResultSetMetaData.class, column -> switch (column) {
                            case "getColumnCount" -> 1;
                            case "getColumnLabel" -> "class_id";
                            default -> throw new IllegalStateException("Unexpected metadata call: " + column);
                        });
                        case "close" -> null;
                        default -> throw new IllegalStateException("Unexpected row call: " + result);
                    });
                    case "executeUpdate" -> 1;
                    case "setInt", "setString", "setTimestamp", "setObject", "close" -> null;
                    default -> throw new IllegalStateException("Unexpected statement call: " + operation);
                });
                case "close" -> null;
                default -> throw new IllegalStateException("Unexpected connection call: " + method);
            });
        }
    }

    private static <T> T fixture(Class<T> type, Function<String, Object> methods) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, parameters) -> {
                    if (method.getName().equals("getMetaData") && type == Connection.class) {
                        throw new SQLException("Fixture has no receipt schema");
                    }
                    return methods.apply(method.getName());
                }));
    }
}
