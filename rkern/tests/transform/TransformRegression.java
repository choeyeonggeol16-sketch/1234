import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.character.animation.loader.CSpriteTimingTable;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.model.character.transform.CTransformCombatData;
import l1j.server.server.model.character.transform.CTransformDB;
import l1j.server.server.model.character.transform.CTransformForModeChange;
import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.cs.packet.CS_EnterWorld;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Weapon;

/** Runs on an isolated clean install of Net.sql. */
public final class TransformRegression {
    private static final int FIRST = 6862;
    private static final int SECOND = 8793;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected isolated Net.sql JDBC URL");
        var config = new HikariConfig();
        config.setJdbcUrl(args[0]);
        config.setUsername("root");
        config.setMaximumPoolSize(5);
        try (var db = new HikariDataSource(config)) {
            var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
            pool.setAccessible(true);
            pool.set(null, db);
            try {
                Config.CACHE_MAP_FILES = true;
                check(CSpriteTimingTable.getInstance().reloadSprites(), "load actual sprite timing definitions");
                verifyDefinitions(db);
                verifyPlayers();
                verifyLoginAndResurrection();
                verifyNpc();
            } finally {
                pool.set(null, null);
            }
        }
    }

    private static void verifyDefinitions(HikariDataSource db) throws SQLException {
        var snapshot = CTransformDB.getInstance();
        int count = 0;
        try (var connection = db.getConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT DISTINCT polyid FROM polymorphs")) {
            while (rows.next()) {
                check(snapshot.find(rows.getInt(1)) != null, "every persisted transform sprite is indexed");
                count++;
            }
        }
        check(count > 700 && snapshot.find("re cargo").getPolyId() == SECOND, "legacy name and sprite indices");
        check(CTransform.IsKnownSprite(FIRST) && CTransform.IsKnownSprite(SECOND)
                && !CTransform.IsKnownSprite(Integer.MAX_VALUE), "exact atlas identity");
        try (var connection = db.getConnection(); var query = connection.createStatement()) {
            query.execute("RENAME TABLE polymorphs TO transform_fixture_hidden");
            try {
                check(!CTransformDB.reload() && CTransformDB.getInstance() == snapshot, "failed reload retains definitions");
            } finally {
                query.execute("RENAME TABLE transform_fixture_hidden TO polymorphs");
            }
        }
        check(CTransformDB.reload() && CTransformDB.getInstance() != snapshot, "valid reload publishes a new snapshot");
        System.out.println("PASS " + count + " transform sprite definitions, aliases and reload rollback");
    }

    private static L1V1Map map(int id) {
        return new L1V1Map(id, new byte[8][8], 32, 32,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static void verifyPlayers() {
        var pc = new L1PcInstance();
        pc.setMap(map(1));
        pc.setType(5);
        pc.setClassId(L1PcInstance.CLASSID_DRAGONKNIGHT_MALE);
        pc.setCurrentSprite(pc.getClassId());
        pc.setMaxHp(1000);
        pc.setCurrentHp(1000);
        pc.SetStatStr(15);
        pc.SetStatDex(12);
        pc.SetStatCon(14);
        pc.SetStatWis(10);
        pc.SetStatCha(9);
        pc.SetStatInt(11);
        int baseHp = pc.getMaxHp();
        int[] baseStats = stats(pc);

        check(pc.Transform(FIRST, 20, TransformType.GM, true), "apply known transform");
        check(pc.getTransformId() == FIRST && pc.getCurrentSpriteId() == FIRST
                && pc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) == 0, "PC keeps indefinite transform");
        check(pc.getMaxHp() == baseHp + 200, "ring adds 200 HP");
        long transform = pc.getTransformKey();
        check(!pc.Transform(Integer.MAX_VALUE, 20, TransformType.GM, false)
                && pc.getTransformKey() == transform && pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER),
                "unknown sprite preserves the current timer and modifiers");
        pc.setMap(map(5302));
        check(!pc.Transform(SECOND, 20, TransformType.GM, false) && pc.getTransformKey() == transform,
                "map restrictions preserve active form");
        pc.setMap(map(1));

        for (int attempt = 0; attempt < 1_000; attempt++) {
            check(pc.Transform(FIRST, 20, TransformType.GM, true), "repeat transform");
            check(pc.Transform(SECOND, 20, TransformType.GM, true), "replace transform");
            check(pc.getMaxHp() == baseHp + 200, "ring HP is idempotent");
            for (int stat = 0; stat < baseStats.length; stat++)
                check(stats(pc)[stat] == baseStats[stat] + 1, "ring applies each of the six stats once");
            pc.CancelTransform();
            pc.CancelTransform();
            check(pc.getMaxHp() == baseHp && java.util.Arrays.equals(stats(pc), baseStats),
                    "double cancellation restores HP and all six stats");
        }

        check(pc.Transform(FIRST, 20, TransformType.GM, true), "ring before independent bonuses");
        pc.addMaxHp(15);
        pc.AddStatBonusStr(2);
        pc.CancelTransform();
        check(pc.getMaxHp() == baseHp + 15 && pc.GetTotalStatStr() == baseStats[0] + 2,
                "cancelling ring retains unrelated HP and stat bonuses");

        var template = new L1Weapon();
        template.setItemId(979_000);
        template.setName("fixture chainsword");
        template.setType2(1);
        template.setType(18);
        template.setType1(50);
        var item = new L1ItemInstance(template, 1);
        item.setId(71_234);
        pc.getInventory().loadItems(java.util.List.of(item));
        pc.EquipItem(item);
        check(item.isEquipped() && pc.getCurrentWeapon() == 83, "equipped chainsword uses the native action");
        check(pc.Transform(7155, 20, TransformType.GM, false) && item.isEquipped()
                && pc.getCurrentWeapon() == 50, "compatible combat form retains the equipped weapon");
        long compatible = pc.getTransformKey();
        check(!pc.Transform(FIRST, 20, TransformType.GM, false, item)
                && item.isEquipped() && pc.getTransformKey() == compatible,
                "transform cannot remove its own source weapon");
        check(pc.Transform(FIRST, 20, TransformType.GM, false) && !item.isEquipped()
                && pc.getCurrentWeapon() == 0, "incompatible weapon is removed before publishing the appearance");
        pc.CancelTransform();
        var other = new L1PcInstance();
        other.setClassId(L1PcInstance.CLASSID_KNIGHT_MALE);
        check(CTransformCombatData.weaponAction(pc, item) == 83 && CTransformCombatData.weaponAction(other, item) == 50,
                "native chainsword action belongs to the dragon knight");
        check(pc.Transform(FIRST, 20, TransformType.GM, false)
                && CTransformCombatData.weaponAction(pc, item) == 50 && template.getType1() == 50,
                "transformed chain action does not mutate the shared weapon");
        int shopSprite = CTransformForModeChange.FindShopSprite(1);
        check(shopSprite == 11479 && CTransformForModeChange.FindShopSprite(0) == 0
                && CTransformForModeChange.FindShopSprite(9) == 0, "validate shop sprite indices");
        CTransformForModeChange.SetTransformData(pc, shopSprite);
        check(pc.getTransformId() == FIRST && pc.getCurrentSpriteId() == shopSprite,
                "shop form does not overwrite the active transform");
        check(pc.snapshotSkillEffects(id -> id == L1SkillId.SHAPE_CHANGE, L1SkillId.SHAPE_CHANGE, 0)
                .spriteId() == FIRST, "login snapshot preserves the combat form under a shop overlay");
        CTransformForModeChange.CancelTransform(pc);
        check(pc.getCurrentSpriteId() == FIRST, "shop exit restores the active transform");
        pc.skillEffectRemoveFromOwner(L1SkillId.SHAPE_CHANGE, true);
        check(pc.getTransformId() == 0 && pc.getCurrentSpriteId() == pc.getClassId(), "skill removal cancels the form");
        System.out.println("PASS failed transitions, 1,000 ring cycles, chainsword identity and shop restoration");
    }

    private static int[] stats(L1PcInstance pc) {
        return new int[] {pc.GetTotalStatStr(), pc.GetTotalStatDex(), pc.GetTotalStatCon(),
                pc.GetTotalStatWis(), pc.GetTotalStatCha(), pc.GetTotalStatInt()};
    }

    private static void verifyLoginAndResurrection() throws Exception {
        var pc = new L1PcInstance();
        pc.setMap(map(1));
        pc.setClassId(L1PcInstance.CLASSID_KNIGHT_MALE);
        pc.setCurrentSprite(pc.getClassId());
        pc.setMaxHp(500);
        pc.setCurrentHp(500);
        var restore = CS_EnterWorld.class.getDeclaredMethod("processBuff", L1PcInstance.class, java.util.List.class);
        restore.setAccessible(true);
        var plain = buffRow(L1SkillId.SHAPE_CHANGE, FIRST);
        var ring = buffRow(L1SkillId.POLY_RING_MASTER, FIRST);
        restore.invoke(null, pc, java.util.List.of(ring, plain));
        check(pc.getTransformId() == FIRST && pc.getMaxHp() == 700
                && pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER), "ring restore is independent of saved row order");
        pc.setDead(true);
        long previous = pc.getTransformKey();
        check(!pc.Transform(SECOND, 20, TransformType.GM, false)
                && pc.getTransformKey() == previous, "dead character cannot replace its current form");
        pc.resurrect(200);
        check(!pc.isDead() && pc.getTransformId() == 0 && pc.getMaxHp() == 500
                && pc.getCurrentSpriteId() == pc.getClassId(), "resurrection clears the transform once");

        var invalid = buffRow(L1SkillId.POLY_RING_MASTER, Integer.MAX_VALUE);
        restore.invoke(null, pc, java.util.List.of(invalid, plain));
        check(pc.getTransformId() == FIRST && pc.getMaxHp() == 500
                && !pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER), "invalid ring row falls back to valid plain form");
        pc.CancelTransform();
        restore.invoke(null, pc, java.util.List.of(invalid));
        check(pc.getTransformId() == 0 && !pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER),
                "invalid stored form cannot create a phantom effect");
        System.out.println("PASS login restore order, invalid stored effects and resurrection");
    }

    private static Object buffRow(int skill, int sprite) throws ReflectiveOperationException {
        var rowType = Class.forName(CS_EnterWorld.class.getName() + "$BuffInfo");
        var ctor = rowType.getDeclaredConstructor();
        ctor.setAccessible(true);
        var row = ctor.newInstance();
        var skillId = rowType.getDeclaredField("skillId");
        skillId.setAccessible(true);
        skillId.setInt(row, skill);
        var polyId = rowType.getDeclaredField("polyId");
        polyId.setAccessible(true);
        polyId.setInt(row, sprite);
        return row;
    }

    private static void verifyNpc() throws Exception {
        var cio = CIOThreadCallback.getInstance();
        var engine = CNPCServer.getInstance();
        var config = new CIOConfig(new InetSocketAddress("127.0.0.1", 0));
        config.packetExecutorShardCount = 2;
        NpcSpeedData.do_load();
        var template = new L1Npc();
        template.set_npcId(100);
        template.set_name("transform fixture");
        template.set_gfxid(FIRST);
        template.set_hp(100);
        var npc = new L1MonsterInstance(template);
        npc.setId(40_412);
        npc.setMap(map(1));
        npc.setCurrentSprite(FIRST);
        try {
            CWorld.getInstance().storeObject(npc);
            engine.start();
            cio.bindNetworkIngress(config);
            check(npc.Transform(SECOND, 1, TransformType.NPC, false), "NPC owner accepts transform");
            await(() -> npc.getTransformId() == SECOND && npc.getCurrentSpriteId() == SECOND, "NPC shape published");
            check(npc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) == 1, "NPC retains a finite duration");
            check(npc.Transform(FIRST, 0, TransformType.NPC, false), "NPC replaces timed transform");
            await(() -> npc.getTransformId() == FIRST && npc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) == 0,
                    "replacement retires previous timer");
            Thread.sleep(1200);
            check(npc.getTransformId() == FIRST, "old expiry leaves replacement alone");
            npc.CancelTransform();
            await(() -> npc.getTransformId() == 0 && !npc.hasSkillEffect(L1SkillId.SHAPE_CHANGE), "NPC cancellation");
            check(npc.Transform(SECOND, 1, TransformType.NPC, false), "NPC owner accepts finite transform after cancel");
            await(() -> npc.getTransformId() == SECOND, "finite form installed before expiry");
            await(() -> npc.getTransformId() == 0 && npc.getCurrentSpriteId() == FIRST,
                    "finite timer restores the base sprite");
            check(npc.Transform(FIRST, 0, TransformType.NPC, false), "NPC owner accepts an indefinite form");
            await(() -> npc.getTransformId() == FIRST, "indefinite form installed");
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO shut down with NPC owner still running");
            check(npc.Transform(SECOND, 1, TransformType.NPC, false), "NPC owner accepts finite attempt");
            var settled = new java.util.concurrent.CountDownLatch(1);
            check(cio.postCharacterEffectOnOwner(npc, new CIOThreadCallback.WheelTask() {
                public boolean isCancelled() { return false; }
                public void run() { settled.countDown(); }
            }) && settled.await(3, TimeUnit.SECONDS), "owner processed failed heartbeat admission");
            check(npc.getTransformId() == FIRST && npc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) == 0,
                    "scheduler refusal retains indefinite shape and timer");
            engine.shutdown();
            check(!npc.Transform(SECOND, 1, TransformType.NPC, false) && npc.getCurrentSpriteId() == FIRST,
                    "NPC queue refusal has no visual side effect");
            System.out.println("PASS NPC owner admission, finite/indefinite durations and old timer replacement");
        } finally {
            CWorld.getInstance().removeObject(npc);
            engine.shutdown();
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO shutdown");
        }
    }

    private static void await(java.util.function.BooleanSupplier ready, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(ready.getAsBoolean(), message);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
