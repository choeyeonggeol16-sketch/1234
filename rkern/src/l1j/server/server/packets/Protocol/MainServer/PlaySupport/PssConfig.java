package l1j.server.server.packets.Protocol.MainServer.PlaySupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;

/** One immutable settings snapshot per reload; checks allocate nothing. */
public final class PssConfig {
    private static final Logger LOG = Logger.getLogger(PssConfig.class.getName());
    private static final Path FILE = Path.of("./config/PSS.properties");
    private static volatile Settings current = new Settings(0, 1, 99, true, new int[0], 9 * 60, 0L);
    private static boolean loaded;

    public record Settings(int dailySeconds, int minLevel, int maxLevel,
                           boolean gmOnly, int[] allowedMaps, int resetMinuteOfDay, long resetNotBeforeMs) {
        public String resetTimeText() {
            return "%02d:%02d".formatted(resetMinuteOfDay / 60, resetMinuteOfDay % 60);
        }
        public boolean permits(L1PcInstance pc) {
            if (pc == null || dailySeconds <= 0 || (gmOnly && !pc.isGm())) return false;
            int level = pc.getLevel();
            return level >= minLevel && level <= maxLevel
                    && (allowedMaps.length == 0 || Arrays.binarySearch(allowedMaps, pc.getMapId()) >= 0);
        }
    }

    static {
        try { reload(); }
        catch (IOException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "PSS config unavailable; disabled until valid config is loaded", e);
        }
    }

    private PssConfig() {}

    public static Settings get() { return current; }

    /** On invalid settings, retain the last good configuration. */
    public static synchronized Settings reload() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) { p.load(in); }
        int minutes = bounded(p, "DailyMinutes", 10, 0, 1440);
        int resetMinuteOfDay = resetMinuteOfDay(p);
        int minLevel = bounded(p, "MinLevel", 1, 1, 127);
        int maxLevel = bounded(p, "MaxLevel", 99, 1, 127);
        if (maxLevel < minLevel) throw new IllegalArgumentException("PSS MaxLevel < MinLevel");
        String gm = p.getProperty("GmOnly", "false").trim();
        if (!gm.equalsIgnoreCase("true") && !gm.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("PSS GmOnly must be true or false");
        String maps = p.getProperty("AllowedMaps", "").trim();
        int[] allowed;
        if (maps.isEmpty()) allowed = new int[0];
        else {
            String[] parts = maps.split(",", -1);
            allowed = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { allowed[i] = Integer.parseInt(parts[i].trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid PSS map: " + parts[i], e); }
                if (allowed[i] < 0 || allowed[i] > 65535)
                    throw new IllegalArgumentException("Invalid PSS map: " + allowed[i]);
            }
            Arrays.sort(allowed);
        }
        Settings previous = current;
        // A changed reset clock is effective at its NEXT boundary, never retroactively.
        long notBefore = !loaded || previous.resetMinuteOfDay() == resetMinuteOfDay
                ? previous.resetNotBeforeMs() : System.currentTimeMillis();
        Settings next = new Settings(minutes * 60, minLevel, maxLevel,
                Boolean.parseBoolean(gm), allowed, resetMinuteOfDay, notBefore);
        current = next;
        loaded = true;
        return next;
    }

    /** Read a strict 24-hour HH:mm reset time. Reload keeps the old snapshot on error. */
    private static int resetMinuteOfDay(Properties p) {
        String time = p.getProperty("ResetTime", "09:00").trim();
        if (time.length() != 5 || time.charAt(2) != ':'
                || !asciiDigit(time.charAt(0)) || !asciiDigit(time.charAt(1))
                || !asciiDigit(time.charAt(3)) || !asciiDigit(time.charAt(4)))
            throw new IllegalArgumentException("PSS ResetTime must be HH:mm (00:00 to 23:59)");
        int hour = (time.charAt(0) - '0') * 10 + time.charAt(1) - '0';
        int minute = (time.charAt(3) - '0') * 10 + time.charAt(4) - '0';
        if (hour >= 24 || minute >= 60)
            throw new IllegalArgumentException("PSS ResetTime must be HH:mm (00:00 to 23:59)");
        return hour * 60 + minute;
    }

    private static boolean asciiDigit(char c) { return c >= '0' && c <= '9'; }

    private static int bounded(Properties p, String key, int fallback, int min, int max) {
        final int n;
        try { n = Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid PSS " + key, e); }
        if (n < min || n > max)
            throw new IllegalArgumentException("PSS " + key + " must be between " + min + " and " + max);
        return n;
    }
}
