package l1j.server.server.model.character.combat.attack;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KeylinkBalanceConfig {
    private static final Logger LOG = Logger.getLogger(KeylinkBalanceConfig.class.getName());

    // Defender class factors, indexed by PC class type (0..7).
    // Reload publishes all eight primitive values together; combat only reads
    // this volatile reference, with no file IO or allocations per attack.
    private static volatile double[] keylinkClassFactors = {
            2.30, 2.40, 2.20, 2.00, 2.20, 2.40, 2.20, 2.40
    };
    private static final Path KEYLINK_BALANCE_FILE = Path.of("./config/KeylinkBalance.properties");

    static {
        try {
            reload();
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.WARNING, "KeylinkBalance.properties not loaded; using previous/default class factors", ex);
        }
    }

    /** Reload only defender class factors, retaining the last valid snapshot on failure. */
    public static synchronized void reload() throws IOException {
        final double[] next = new double[8];
        final boolean[] seen = new boolean[8];
        try (BufferedReader reader = Files.newBufferedReader(KEYLINK_BALANCE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                ++lineNumber;
                final String entry = line.trim();
                if (entry.isEmpty() || entry.charAt(0) == '#' || entry.charAt(0) == '!') continue;
                final int separator = entry.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("KeylinkBalance line " + lineNumber + " must be Key=Value");
                }
                final String key = entry.substring(0, separator).trim();
                final int type = switch (key) {
                    case "Prince" -> 0;
                    case "Knight" -> 1;
                    case "Elf" -> 2;
                    case "Wizard" -> 3;
                    case "DarkElf" -> 4;
                    case "DragonKnight" -> 5;
                    case "Illusionist" -> 6;
                    case "Warrior" -> 7;
                    default -> throw new IllegalArgumentException(
                            "Unknown KeylinkBalance key on line " + lineNumber + ": " + key);
                };
                if (seen[type]) {
                    throw new IllegalArgumentException("Duplicate KeylinkBalance key: " + key);
                }
                final String raw = entry.substring(separator + 1).trim();
                final double value;
                try {
                    value = Double.parseDouble(raw);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid KeylinkBalance " + key + ": " + raw, ex);
                }
                if (!Double.isFinite(value) || value < 0.0 || value > 10.0) {
                    throw new IllegalArgumentException(key + " must be between 0.0 and 10.0: " + raw);
                }
                next[type] = value;
                seen[type] = true;
            }
        }
        for (int i = 0; i < seen.length; ++i) {
            if (!seen[i]) throw new IllegalArgumentException("KeylinkBalance missing class type " + i);
        }
        keylinkClassFactors = next;
    }

    private KeylinkBalanceConfig() {}

    public static double classFactor(int type) {
        return keylinkClassFactors[type];
    }
}
