package l1j.server.server.model.character.ItemSkill.Doll;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Immutable doll-proc configuration. File IO happens at start/reload only. */
public final class MagicDollCombatConfig {
    private static final Logger LOG = Logger.getLogger(MagicDollCombatConfig.class.getName());
    private static final Path FILE = Path.of("./config/MagicDollProc.properties");

    public record ExtraDollProc(int chance, int minDamage, int maxDamage, int effect) {}

    public record Settings(
            int enabled,
            int deathChance, int deathMin, int deathMax, int deathFx,
            int blackChance, int blackMin, int blackMax, int blackFx,
            int wolfChance, int wolfDamage, int wolfFx,
            int criticalMrMax, int criticalChance, int criticalMultiplierPercent,
            ExtraDollProc desel, ExtraDollProc hal, ExtraDollProc au, ExtraDollProc behe,
            ExtraDollProc ruler1, ExtraDollProc ruler1Daily,
            ExtraDollProc ruler2, ExtraDollProc ruler2Daily,
            ExtraDollProc ruler3, ExtraDollProc ruler3Daily,
            ExtraDollProc ruler4, ExtraDollProc ruler4Daily) {}

    private static volatile Settings current = new Settings(
            1, 10, 33, 54, 11660, 8, 20, 33, 11736,
            3, 15, 6319, 70, 10, 150,
            new ExtraDollProc(1, 20, 33, 15519), new ExtraDollProc(1, 20, 33, 13987),
            new ExtraDollProc(1, 20, 33, 2953), new ExtraDollProc(1, 20, 33, 12119),
            new ExtraDollProc(1, 20, 33, 13340), new ExtraDollProc(20, 20, 33, 13340),
            new ExtraDollProc(8, 20, 33, 13340), new ExtraDollProc(8, 20, 33, 13340),
            new ExtraDollProc(6, 20, 33, 13340), new ExtraDollProc(6, 20, 33, 13340),
            new ExtraDollProc(5, 20, 33, 13340), new ExtraDollProc(5, 20, 33, 13340));

    static {
        try {
            reload();
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.WARNING, "MagicDollProc.properties not loaded; keeping defaults", ex);
        }
    }

    private MagicDollCombatConfig() {}

    public static Settings get() { return current; }

    public static synchronized Settings reload() throws IOException {
        final Properties values = new Properties();
        final Set<String> seen = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String raw;
            int line = 0;
            while ((raw = reader.readLine()) != null) {
                ++line;
                final String s = raw.trim();
                if (s.isEmpty() || s.charAt(0) == '#' || s.charAt(0) == '!') continue;
                final int eq = s.indexOf('=');
                if (eq <= 0) throw new IllegalArgumentException("Doll proc line " + line + " must be Key=Value");
                final String key = s.substring(0, eq).trim();
                if (!known(key)) throw new IllegalArgumentException("Unknown doll proc key on line " + line + ": " + key);
                if (!seen.add(key)) throw new IllegalArgumentException("Duplicate doll proc key on line " + line + ": " + key);
                values.setProperty(key, s.substring(eq + 1).trim());
            }
        }
        final Settings next = new Settings(
                read(values, "Enabled", 1, 0, 1),
                read(values, "DeathKnightChance", 10, 0, 100),
                read(values, "DeathKnightMinDamage", 33, 0, 100000),
                read(values, "DeathKnightMaxDamage", 54, 0, 100000),
                read(values, "DeathKnightEffect", 11660, 0, 100000),
                read(values, "BlackElderChance", 8, 0, 100),
                read(values, "BlackElderMinDamage", 20, 0, 100000),
                read(values, "BlackElderMaxDamage", 33, 0, 100000),
                read(values, "BlackElderEffect", 11736, 0, 100000),
                read(values, "WerewolfChance", 3, 0, 100),
                read(values, "WerewolfDamage", 15, 0, 100000),
                read(values, "WerewolfEffect", 6319, 0, 100000),
                read(values, "CriticalMrMax", 70, 0, 1000),
                read(values, "CriticalChance", 10, 0, 100),
                read(values, "CriticalMultiplierPercent", 150, 100, 1000),
                extra(values, "Desel", 1, 15519),
                extra(values, "Hal", 1, 13987),
                extra(values, "Au", 1, 2953),
                extra(values, "Behe", 1, 12119),
                extra(values, "Ruler1", 1, 13340),
                extra(values, "Ruler1Daily", 20, 13340),
                extra(values, "Ruler2", 8, 13340),
                extra(values, "Ruler2Daily", 8, 13340),
                extra(values, "Ruler3", 6, 13340),
                extra(values, "Ruler3Daily", 6, 13340),
                extra(values, "Ruler4", 5, 13340),
                extra(values, "Ruler4Daily", 5, 13340));
        if (next.deathMin() > next.deathMax() || next.blackMin() > next.blackMax()) {
            throw new IllegalArgumentException("Doll proc minimum damage exceeds maximum");
        }
        current = next;
        return next;
    }

    private static ExtraDollProc extra(Properties values, String prefix,
            int chance, int effect) {
        final int lo = read(values, prefix + "MinDamage", 20, 0, 100000);
        final int hi = read(values, prefix + "MaxDamage", 33, 0, 100000);
        if (lo > hi) throw new IllegalArgumentException(prefix + "MinDamage > " + prefix + "MaxDamage");
        return new ExtraDollProc(
                read(values, prefix + "Chance", chance, 0, 100), lo, hi,
                read(values, prefix + "Effect", effect, 0, 1000000));
    }

    private static boolean known(String key) {
        return switch (key) {
            case "Enabled", "DeathKnightChance", "DeathKnightMinDamage", "DeathKnightMaxDamage",
                 "DeathKnightEffect", "BlackElderChance", "BlackElderMinDamage", "BlackElderMaxDamage",
                 "BlackElderEffect", "WerewolfChance", "WerewolfDamage", "WerewolfEffect",
                 "CriticalMrMax", "CriticalChance", "CriticalMultiplierPercent",
                 "DeselChance", "DeselMinDamage", "DeselMaxDamage", "DeselEffect",
                 "HalChance", "HalMinDamage", "HalMaxDamage", "HalEffect",
                 "AuChance", "AuMinDamage", "AuMaxDamage", "AuEffect",
                 "BeheChance", "BeheMinDamage", "BeheMaxDamage", "BeheEffect",
                 "Ruler1Chance", "Ruler1MinDamage", "Ruler1MaxDamage", "Ruler1Effect",
                 "Ruler1DailyChance", "Ruler1DailyMinDamage", "Ruler1DailyMaxDamage", "Ruler1DailyEffect",
                 "Ruler2Chance", "Ruler2MinDamage", "Ruler2MaxDamage", "Ruler2Effect",
                 "Ruler2DailyChance", "Ruler2DailyMinDamage", "Ruler2DailyMaxDamage", "Ruler2DailyEffect",
                 "Ruler3Chance", "Ruler3MinDamage", "Ruler3MaxDamage", "Ruler3Effect",
                 "Ruler3DailyChance", "Ruler3DailyMinDamage", "Ruler3DailyMaxDamage", "Ruler3DailyEffect",
                 "Ruler4Chance", "Ruler4MinDamage", "Ruler4MaxDamage", "Ruler4Effect",
                 "Ruler4DailyChance", "Ruler4DailyMinDamage", "Ruler4DailyMaxDamage", "Ruler4DailyEffect" -> true;
            default -> false;
        };
    }

    private static int read(Properties p, String key, int fallback, int min, int max) {
        final String raw = p.getProperty(key, Integer.toString(fallback)).trim();
        final int val;
        try { val = Integer.parseInt(raw); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid " + key + ": " + raw, ex); }
        if (val < min || val > max) throw new IllegalArgumentException(key + " must be " + min + ".." + max);
        return val;
    }
}
