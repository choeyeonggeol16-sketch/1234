package l1j.server.server.model;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariDataSource;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.utils.CPCParamDB;

public final class CreatureStatCrosscuttingRegression {
    private static final int[] CLASSES = {0, 61, 138, 734, 2786, 6658, 6671, 12490};
    private static final int[] LEVELS = {1, 50, 51, 89, 90, ExpTable.MAX_LEVEL};
    private static final int[] VALUES = {0, 1, 45, 50, 80, 127};
    private static final int[] AC_DIVISORS = {6, 6, 7, 8, 6, 6, 8, 6};
    private static long checksum = 1;

    public static void main(String[] args) throws Exception {
        fixtureMetadata();
        boundaries();
        equipmentAndEffects();
        publishAndReuse();
        System.out.printf("PASS creature numeric/equipment/effect/reader parity checksum=%016x%n", checksum);
        System.exit(0); // Production effect/timer owners can outlive this standalone fixture.
    }

    private static L1PcInstance player(int type, int level) {
        var pc = new L1PcInstance();
        pc.setClassId(CLASSES[type]);
        pc.setType(type);
        pc.setLevel(level);
        pc.ResetPrimaryStats(20, 20, 20, 20, 20, 20);
        pc.addBaseMaxHp((short) 400);
        pc.addBaseMaxMp(200);
        return pc;
    }

    private static void boundaries() {
        for (int type = 0; type < CLASSES.length; type++) {
            for (int level : LEVELS) {
                var pc = player(type, level);
                for (int i = 0; i < VALUES.length; i++) {
                    int str = VALUES[i];
                    int dex = VALUES[(i + 1) % VALUES.length];
                    int con = VALUES[(i + 2) % VALUES.length];
                    int intel = VALUES[(i + 3) % VALUES.length];
                    int wis = VALUES[(i + 4) % VALUES.length];
                    int cha = VALUES[(i + 5) % VALUES.length];
                    pc.ResetPrimaryStats(str, dex, con, intel, wis, cha);
                    for (int skill : new int[]{-128, -1, 0, 1, 127}) {
                        long raw = (skill & 255L) << ((i % 6) * 8);
                        pc.SetSkillPrimaryStats(raw, false);
                        int[] base = {str, dex, intel, wis, con, cha};
                        base[i] = Math.clamp(base[i] + skill, 0, 127);
                        same(base, primary(pc), "six primary lanes");
                        eq(CPCParamDB.물리방어력(base[1]) - level / AC_DIVISORS[type],
                                pc.GetBasePDefend(), "derived AC");
                        eq(Math.clamp(CPCParamDB.마법방어(type, base[3]) + level / 2, 0, 250),
                                pc.GetOrgMDefend(), "derived MR");
                        pc.setCurrentHp(pc.getMaxHp());
                        pc.setCurrentMp(pc.getMaxMp());
                        for (int value : snapshot(pc)) checksum = 31 * checksum + value;
                        require(pc.getCurrentHp() <= pc.getMaxHp() && pc.getCurrentMp() <= pc.getMaxMp(), "vital limits");
                    }
                    pc.SetSkillPrimaryStats(0, false);
                }
            }
        }
        System.out.println("PASS 1,440 boundary combinations across six stat lanes / eight classes / six levels");
    }

    private static void equipmentAndEffects() {
        for (int type = 0; type < CLASSES.length; type++) {
            var pc = player(type, 90);
            var template = new L1Armor();
            template.setItemId(1_931_001);
            template.setType2(2);
            template.setType(2);
            template.set_addstr((byte) 3);
            template.set_adddex((byte) 4);
            template.set_addwis((byte) 6);
            template.set_addint((byte) 5);
            template.set_addcon((byte) 2);
            template.set_addcha((byte) 1);
            template.set_addhp(100);
            template.set_addmp(50);
            template.set_addsp(3);
            template.set_ac(-10);
            var armor = new L1ItemInstance(template, 1);
            armor.setId(1_932_001 + type);
            var inventory = pc.getInventory();
            inventory.loadItems(List.of(armor));
            var equipment = pc.getEquipment();
            var effects = new SkillEffectContainer(pc);
            int[] baseline = snapshot(pc);
            for (int round = 0; round < 16; round++) {
                equipment.changeEquipment(armor, true, true, false);
                require(armor.isEquipped() && inventory.GetEquippedObjectCount() == 1, "real equipment bind");
                same(new int[]{23, 24, 25, 26, 22, 21}, primary(pc), "equipment applied");
                int[] equipped = snapshot(pc);
                effects.applySkillModifier(L1SkillId.힘3, 0);
                effects.applySkillModifier(L1SkillId.덱3, 0);
                effects.applySkillModifier(L1SkillId.위즈3, 0);
                effects.applySkillModifier(L1SkillId.hp300, 0);
                effects.applySkillModifier(L1SkillId.mp300, 0);
                effects.applySkillModifier(L1SkillId.sp5, 0);
                same(new int[]{26, 27, 25, 29, 22, 21}, primary(pc), "stacked equipment and skill");
                eq(equipped[6] + 300, pc.getMaxHp(), "stacked HP");
                eq(equipped[7] + 300, pc.getMaxMp(), "stacked MP");
                eq(equipped[10] + 5, pc.GetStatSp(), "stacked SP");
                for (int id : new int[]{L1SkillId.힘3, L1SkillId.덱3, L1SkillId.위즈3,
                        L1SkillId.hp300, L1SkillId.mp300, L1SkillId.sp5}) {
                    require(effects.removeSkillModifier(id, false), "remove active skill");
                }
                same(equipped, snapshot(pc), "gear retained on skill removal");
                equipment.changeEquipment(armor, false, true, false);
                require(!armor.isEquipped() && inventory.GetEquippedObjectCount() == 0, "real equipment unbind");
                same(baseline, snapshot(pc), "gear and effect state restored");
            }
        }
        System.out.println("PASS 128 production equipment bind + mixed effect + unbind cycles");
    }

    private static void publishAndReuse() throws Exception {
        var pc = player(0, 90);
        var effects = new SkillEffectContainer(pc);
        var started = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> {
                started.countDown();
                require(started.await(5, TimeUnit.SECONDS), "reader startup");
                for (int i = 0; i < 10_000; i++) {
                    effects.applySkillModifier(L1SkillId.힘3, 0);
                    effects.applySkillModifier(L1SkillId.힘3, 0);
                    require(effects.removeSkillModifier(L1SkillId.힘3, false), "owner clears one reapplication");
                }
                return null;
            });
            var reader = pool.submit(() -> {
                started.countDown();
                require(started.await(5, TimeUnit.SECONDS), "writer startup");
                for (int i = 0; i < 100_000; i++) {
                    int strength = pc.GetTotalStatStr();
                    require(strength == 20 || strength == 23, "only published skill totals");
                    eq(20, pc.GetTotalStatDex(), "no neighboring lane contamination");
                }
                return null;
            });
            try {
                writer.get(15, TimeUnit.SECONDS);
                reader.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                throw new AssertionError("cross-thread stat publication", failure.getCause());
            }
        }
        eq(20, pc.GetTotalStatStr(), "publication restored");
        eq(0, pc.GetAddedStatStr(), "no remaining bonus");
        System.out.println("PASS one effect owner / concurrent readers / duplicate application / cleanup");
    }

    // Only empty static catalogs are substituted; all inventory, modifiers and equipment are production objects.
    static void fixtureMetadata() throws ReflectiveOperationException {
        var fixture = new HikariDataSource() {
            @Override public Connection getConnection() {
                return proxy(Connection.class, (method, args) -> switch (method) {
                    case "prepareStatement" -> {
                        require(List.of("SELECT * FROM mapids", "SELECT * FROM armor_set").contains(args[0]),
                                "unexpected fixture metadata query " + args[0]);
                        yield proxy(PreparedStatement.class, (statement, unused) -> switch (statement) {
                            case "executeQuery" -> proxy(ResultSet.class, (result, ignored) -> switch (result) {
                                case "next" -> false;
                                case "close" -> null;
                                default -> throw new AssertionError(result);
                            });
                            case "close" -> null;
                            default -> throw new AssertionError(statement);
                        });
                    }
                    case "close" -> null;
                    default -> throw new AssertionError(method);
                });
            }
        };
        var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        pool.setAccessible(true);
        pool.set(null, fixture);
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (target, method, args) -> calls.apply(method.getName(), args)));
    }

    private static int[] primary(L1PcInstance pc) {
        return new int[]{pc.GetTotalStatStr(), pc.GetTotalStatDex(), pc.GetTotalStatInt(),
                pc.GetTotalStatWis(), pc.GetTotalStatCon(), pc.GetTotalStatCha()};
    }

    private static int[] snapshot(L1PcInstance pc) {
        var result = Arrays.copyOf(primary(pc), 17);
        result[6] = pc.getMaxHp();
        result[7] = pc.getMaxMp();
        result[8] = pc.getAc();
        result[9] = pc.GetMDefend();
        result[10] = pc.GetStatSp();
        result[11] = pc.getMaxWeight();
        result[12] = pc.getTotalER();
        result[13] = pc.getMpr();
        result[14] = pc.getHpr();
        result[15] = pc.GetStatPAttack(false);
        result[16] = pc.GetStatPAttack(true);
        return result;
    }

    private static void same(int[] expected, int[] actual, String field) {
        if (!Arrays.equals(expected, actual))
            throw new AssertionError(field + " expected " + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual));
    }

    private static void eq(int expected, int actual, String field) {
        if (expected != actual) throw new AssertionError(field + " expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String field) {
        if (!condition) throw new AssertionError(field);
    }
}
