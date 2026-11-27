import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.DatabaseFactory;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.CSkillEffectActivation;
import l1j.server.server.model.item.doll.DollPotentialEffects;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class TestExternalStatSources {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void verifyComaRewards() throws InterruptedException {
        final var pc = new L1PcInstance();
        pc.ResetPrimaryStats(15, 15, 15, 15, 15, 15);
        final int ac = pc.getAc(), sp = pc.GetStatSp(), hit = pc.getHitRate();
        final var owner = CIOThreadCallback.getInstance();
        require(owner.submitComaReward(pc, false), "small reward admitted");
        require(pc.getSkillEffectTimeSec(L1SkillId.COMA_A) == 3600 && pc.GetTotalStatStr() == 20
                && pc.GetTotalStatDex() == 20 && pc.GetTotalStatCon() == 16
                && pc.getHitRate() == hit + 3, "small reward applies its skill and timer");
        int smallAc = pc.getAc();
        require(owner.submitComaReward(pc, false) && pc.GetTotalStatStr() == 20, "refresh is idempotent");
        require(owner.submitComaReward(pc, true) && !pc.hasSkillEffect(L1SkillId.COMA_A)
                && pc.getSkillEffectTimeSec(L1SkillId.COMA_B) == 7200 && pc.GetTotalStatCon() == 18
                && pc.getAc() == smallAc - 5 && pc.GetStatSp() == sp + 1 && pc.getHitRate() == hit + 5,
                "large reward replaces small modifiers");
        pc.removeSkillEffect(L1SkillId.COMA_B);
        require(pc.GetTotalStatStr() == 15 && pc.GetTotalStatDex() == 15 && pc.GetTotalStatCon() == 15
                && pc.getAc() == ac && pc.GetStatSp() == sp && pc.getHitRate() == hit, "dispel restores all sources");

        CSkillEffectActivation.startComaRewardFromOwner(pc, L1SkillId.COMA_A, 1);
        final int generation = pc.skillEffectHeartbeatTryArm();
        require(generation != 0, "fixture owns timer");
        Thread.sleep(1050);
        pc.skillEffectHeartbeatTick(generation);
        require(!pc.hasSkillEffect(L1SkillId.COMA_A) && pc.GetTotalStatStr() == 15
                && pc.getAc() == ac && pc.getHitRate() == hit, "natural expiration restores sources");
    }

    private static void verifyDollPotential() throws Exception {
        final Path file = Files.createTempFile("rkern-doll-stat", ".txt");
        try {
            Files.writeString(file, "1\tSTR=-128\tDEX=130\tCON=-128\tINT=130\tWIS=-128\ttext=stat\n");
            System.setProperty("rkern.doll.potential", file.toString());
            final var pc = new L1PcInstance();
            pc.ResetPrimaryStats(20, 20, 20, 20, 20, 20);
            DollPotentialEffects.apply(pc, 1, 1);
            require(pc.GetAddedStatStr() == -128 && pc.GetAddedStatDex() == 127
                    && pc.GetAddedStatCon() == -128 && pc.GetAddedStatInt() == 127
                    && pc.GetAddedStatWis() == -128, "editable values project without sign wrap");
            DollPotentialEffects.apply(pc, 1, -1);
            require(pc.GetTotalStatStr() == 20 && pc.GetTotalStatDex() == 20 && pc.GetTotalStatCon() == 20
                    && pc.GetTotalStatInt() == 20 && pc.GetTotalStatWis() == 20, "doll removal reverses configured deltas");
        } finally {
            System.clearProperty("rkern.doll.potential");
            Files.deleteIfExists(file);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].matches("[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Expected a scratch JDBC URL, user and reference schema");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        String schema = "rkern_stat_effect_" + UUID.randomUUID().toString().replace("-", "");
        try (var con = DriverManager.getConnection(args[0], args[1], password); var ddl = con.createStatement()) {
            ddl.execute("CREATE DATABASE " + schema);
            try {
                for (String table : new String[] {"mapids", "skills"}) {
                    ddl.execute("CREATE TABLE " + schema + "." + table + " LIKE `" + args[2] + "`." + table);
                }
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                try (var pool = new HikariDataSource(config)) {
                    var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);
                    try {
                        verifyComaRewards();
                        verifyDollPotential();
                    } finally {
                        var owner = CIOThreadCallback.getInstance();
                        owner.shutdown();
                        require(owner.awaitTermination(5, TimeUnit.SECONDS), "CIO workers stopped");
                        field.set(null, null);
                    }
                }
            } finally {
                ddl.execute("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS paid Coma grant, refresh, replace, dispel, expiration and doll script round trip");
    }
}
