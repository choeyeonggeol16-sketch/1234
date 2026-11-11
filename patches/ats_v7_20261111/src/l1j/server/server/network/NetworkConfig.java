package l1j.server.server.network;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Validated, immutable transport limits. Sizes count queued AND in-flight data. */
public final class NetworkConfig {
    public final int ioThreads, gameThreads, authThreads, closeThreads, maxSessions, maxPerIp, maxFrameBytes;
    public final int inboundPackets, outboundPackets, batchPackets, batchBytes, controlTasks;
    public final int globalInboundPackets, globalOutboundPackets;
    public final long inboundBytes, outboundBytes, globalInboundBytes, globalOutboundBytes;
    public final long frameTimeoutMs, authTimeoutMs, readIdleMs, writeStallMs, gracefulCloseMs, shutdownMs;
    public final int writeLowWater, writeHighWater;

    public NetworkConfig(Properties p) {
        int cpus = Runtime.getRuntime().availableProcessors();
        ioThreads = integer(p, "ioThreads", Math.max(1, Math.min(4, cpus)), 1, 64);
        gameThreads = integer(p, "gameThreads", Math.max(2, Math.min(16, cpus)), 1, 128);
        authThreads = integer(p, "authThreads", 2, 1, 32);
        closeThreads = integer(p, "closeThreads", 2, 1, 32);
        maxSessions = integer(p, "maxSessions", 4096, 1, 100000);
        maxPerIp = integer(p, "maxPerIp", 16, 1, maxSessions);
        maxFrameBytes = integer(p, "maxFrameBytes", 65535, 6, 65535);
        inboundPackets = integer(p, "inboundPackets", 512, 1, 65536);
        // A visibility refresh/spawn burst can contain thousands of tiny packets.
        // 512 frames rejected even 34 KiB before the I/O loop could flush it.
        // Keep both byte budgets and the explicit packet cap: slow peers remain bounded.
        outboundPackets = integer(p, "outboundPackets", 8192, 1, 65536);
        globalInboundPackets = integer(p, "globalInboundPackets", 131072, 1, 10000000);
        globalOutboundPackets = integer(p, "globalOutboundPackets", 262144, 1, 10000000);
        controlTasks = integer(p, "controlTasks", 8, 1, 256);
        batchPackets = integer(p, "batchPackets", 32, 1, 4096);
        batchBytes = integer(p, "batchBytes", 65536, 1024, 4 * 1024 * 1024);
        inboundBytes = number(p, "inboundBytes", 256 * 1024L, maxFrameBytes, 64 * 1024 * 1024L);
        // Reserve room for the matching full-object visibility burst as well,
        // not only movement packets. Global admission still caps total retained data.
        outboundBytes = number(p, "outboundBytes", 2 * 1024 * 1024L, maxFrameBytes, 64 * 1024 * 1024L);
        globalInboundBytes = number(p, "globalInboundBytes", 64 * 1024 * 1024L, inboundBytes, 16L << 30);
        globalOutboundBytes = number(p, "globalOutboundBytes", 128 * 1024 * 1024L, outboundBytes, 16L << 30);
        writeLowWater = integer(p, "writeLowWater", 32768, 1, 16 * 1024 * 1024);
        writeHighWater = integer(p, "writeHighWater", 65536, writeLowWater + 1, 32 * 1024 * 1024);
        frameTimeoutMs = number(p, "frameTimeoutMs", 10000, 100, 300000);
        authTimeoutMs = number(p, "authTimeoutMs", 30000, 100, 600000);
        readIdleMs = number(p, "readIdleMs", 0, 0, 86400000);
        writeStallMs = number(p, "writeStallMs", 30000, 100, 300000);
        gracefulCloseMs = number(p, "gracefulCloseMs", 2000, 100, 30000);
        shutdownMs = number(p, "shutdownMs", 15000, 100, 300000);
    }

    public static NetworkConfig load(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.exists(path)) try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        return new NetworkConfig(p);
    }

    private static int integer(Properties p, String k, int d, int min, int max) {
        return (int) number(p, k, d, min, max);
    }
    private static long number(Properties p, String k, long d, long min, long max) {
        long n = Long.parseLong(p.getProperty(k, Long.toString(d)).trim());
        if (n < min || n > max) throw new IllegalArgumentException(k + " must be in " + min + ".." + max);
        return n;
    }
}
