package l1j.server.server.model.character.Attack.Helper;

import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attack skill chances live only in SkillChance.properties.
 * One immutable snapshot is swapped after a fully validated reload.
 * No file IO, locks, parsing or allocation on the attack path.
 */
public final class AttackSkillChanceConfig {
    private static final Logger LOG = Logger.getLogger(AttackSkillChanceConfig.class.getName());
    private static final Path FILE = Path.of("./config/SkillChance.properties");

    public record Settings(
            int doubleBreakChance, int doubleBreakBonusStartLevel, int doubleBreakBonusStepLevel,
            int doubleBreakDestinyStartLevel, int doubleBreakDestinyPerLevel,
            int burningChance,
            int blowAttackStartLevel, int blowAttackBaseChance, int blowAttackPerLevel,
            int cycloneStartLevel, int cycloneBaseChance, int cyclonePerLevel,
            int crashChance, int furyChance,
            int counterBarrierChance, int mortalBodyChance, int infernoChance,
            int titanRockChance, int titanBlitzChance, int titanMagicChance,
            int maanEvadeChance, int maanDamageChance,
            int absoluteBladeOneHandCap, int absoluteBladeTwoHandCap,
            int weakExposeBaseChance) {}

    // Default values preserve the prior external rates except Mortal Body (22%
    // from src22). Operations may override every value in SkillChance.properties.
    private static volatile Settings current = new Settings(
            1, 65, 5, 80, 1,
            33, 75, 5, 1, 85, 5, 1,
            10, 2, 20, 22, 20,
            30, 30, 30,
            10, 10, 8, 16, 13);

    static {
        try {
            reload();
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.WARNING, "SkillChance.properties not loaded; keeping defaults", ex);
        }
    }

    private AttackSkillChanceConfig() {}

    public static Settings get() { return current; }

    /** A failed reload must not expose any partially applied combat settings. */
    public static synchronized Settings reload() throws IOException {
        // Strict key=value parsing prevents java.util.Properties from silently
        // accepting a duplicate key and replacing its earlier chance.
        // Parsing runs only on reload, never in the combat hot path.
        final Properties props = new Properties();
        final Set<String> seen = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String raw;
            int line = 0;
            while ((raw = reader.readLine()) != null) {
                ++line;
                final String entry = raw.trim();
                if (entry.isEmpty() || entry.charAt(0) == '#' || entry.charAt(0) == '!') continue;
                final int separator = entry.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("SkillChance line " + line + " must use Key=Value");
                }
                final String key = entry.substring(0, separator).trim();
                if (!knownKey(key)) {
                    throw new IllegalArgumentException("Unknown SkillChance key on line " + line + ": " + key);
                }
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Duplicate SkillChance key on line " + line + ": " + key);
                }
                props.setProperty(key, entry.substring(separator + 1).trim());
            }
        }
        final Settings next = new Settings(
                number(props,"DoubleBreakChance",1,0,100),
                number(props,"DoubleBreakBonusStartLevel",65,1,127),
                number(props,"DoubleBreakBonusStepLevel",5,1,127),
                number(props,"DoubleBreakDestinyStartLevel",80,1,127),
                number(props,"DoubleBreakDestinyPerLevel",1,0,100),
                number(props,"BurningChance",33,0,100),
                number(props,"BlowAttackStartLevel",75,1,127),
                number(props,"BlowAttackBaseChance",5,0,100),
                number(props,"BlowAttackPerLevel",1,0,100),
                number(props,"CycloneStartLevel",85,1,127),
                number(props,"CycloneBaseChance",5,0,100),
                number(props,"CyclonePerLevel",1,0,100),
                number(props,"CrashChance",10,0,100),
                number(props,"FuryChance",2,0,100),
                number(props,"CounterBarrierChance",20,0,100),
                number(props,"MortalBodyChance",22,0,100),
                number(props,"InfernoChance",20,0,100),
                number(props,"TitanRockChance",30,0,100),
                number(props,"TitanBlitzChance",30,0,100),
                number(props,"TitanMagicChance",30,0,100),
                number(props,"MaanEvadeChance",10,0,100),
                number(props,"MaanDamageChance",10,0,100),
                number(props,"AbsoluteBladeOneHandCap",8,0,100),
                number(props,"AbsoluteBladeTwoHandCap",16,0,100),
                number(props,"WeakExposeBaseChance",13,0,100));
        current = next;
        return next;
    }

    private static boolean knownKey(String key) {
        return switch (key) {
            case "DoubleBreakChance", "DoubleBreakBonusStartLevel", "DoubleBreakBonusStepLevel",
                 "DoubleBreakDestinyStartLevel", "DoubleBreakDestinyPerLevel", "BurningChance",
                 "BlowAttackStartLevel", "BlowAttackBaseChance", "BlowAttackPerLevel",
                 "CycloneStartLevel", "CycloneBaseChance", "CyclonePerLevel", "CrashChance",
                 "FuryChance", "CounterBarrierChance", "MortalBodyChance", "InfernoChance",
                 "TitanRockChance", "TitanBlitzChance", "TitanMagicChance", "MaanEvadeChance",
                 "MaanDamageChance", "AbsoluteBladeOneHandCap", "AbsoluteBladeTwoHandCap",
                 "WeakExposeBaseChance" -> true;
            default -> false;
        };
    }

    private static int number(Properties props, String key, int fallback, int min, int max) {
        final String raw = props.getProperty(key, Integer.toString(fallback)).trim();
        final int value;
        try { value = Integer.parseInt(raw); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, ex);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}
