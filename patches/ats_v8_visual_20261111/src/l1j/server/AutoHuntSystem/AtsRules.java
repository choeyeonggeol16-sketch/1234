package l1j.server.AutoHuntSystem;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Read on each start. Running sessions retain their start-time rule snapshot. */
public final class AtsRules {
    public static final long MAX_MILLIS = 31_536_000_000L;
    public final long initialMillis;
    public final int minLevel, maxLevel;
    public final boolean enabled;
    private final Set<Integer> allowedMaps = new HashSet<>();
    public static AtsRules load() throws Exception {
        Path path = Paths.get("config", "autohunt_ats.properties");
        Properties p = new Properties();
        // Missing configuration must not silently grant time or ignore map limits.
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        return new AtsRules(p);
    }
    public AtsRules(Properties p) {
        String flag = p.getProperty("Enabled", "true").trim();
        if (!flag.equals("true") && !flag.equals("false")) throw new IllegalArgumentException("Enabled must be true/false");
        enabled = Boolean.parseBoolean(flag);
        long seconds = Long.parseLong(p.getProperty("InitialSeconds", "86400").trim());
        if (seconds < 0 || seconds > MAX_MILLIS / 1000) throw new IllegalArgumentException("InitialSeconds range");
        initialMillis = seconds * 1000;
        minLevel = Integer.parseInt(p.getProperty("MinLevel", "1").trim());
        maxLevel = Integer.parseInt(p.getProperty("MaxLevel", "0").trim());
        if (minLevel < 1 || maxLevel < 0 || (maxLevel != 0 && maxLevel < minLevel))
            throw new IllegalArgumentException("ATS level range");
        String maps = p.getProperty("AllowedMaps", "").trim();
        if (!maps.isEmpty()) for (String token : maps.split(",", -1)) {
            int map = Integer.parseInt(token.trim());
            if (map < 0 || map > 32767) throw new IllegalArgumentException("ATS map range");
            allowedMaps.add(map);
        }
    }
    public String rejection(int level, int map) {
        if (!enabled) return "\ud604\uc7ac \uc790\ub3d9\uc0ac\ub0e5\uc744 \uc774\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.";
        if (level < minLevel) return "\uc790\ub3d9\uc0ac\ub0e5\uc740 " + minLevel + "\ub808\ubca8\ubd80\ud130 \uc774\uc6a9\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.";
        if (maxLevel != 0 && level > maxLevel) return "\uc790\ub3d9\uc0ac\ub0e5\uc740 " + maxLevel + "\ub808\ubca8\uae4c\uc9c0 \uc774\uc6a9\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.";
        if (!allowedMaps.isEmpty() && !allowedMaps.contains(map)) return "\uc790\ub3d9\uc0ac\ub0e5\uc774 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc740 \ub9f5\uc785\ub2c8\ub2e4.";
        return null;
    }
}
