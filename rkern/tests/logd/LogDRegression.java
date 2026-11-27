package l1j.server.server.logd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.logd.CLogRecord.Kind;

public final class LogDRegression {
    private static final long TIME = Instant.parse("2026-11-27T23:59:59Z").toEpochMilli();

    public static void main(String[] args) throws Exception {
        legacyFiles();
        stallsAndSaturation();
        fileRetry();
        concurrentShutdown();
        System.out.println("PASS LogD files, queue saturation, retry and shutdown");
    }

    private static void legacyFiles() throws Exception {
        Path root = Files.createTempDirectory("rkern-logd-");
        try {
            CLogWriter writer = new CLogWriter(root, 8, (view, text) -> {});
            writer.offer(event(Kind.GLOBAL, "캐릭터", "안녕\r\n가짜 기록"));
            writer.offer(event(Kind.WAREHOUSE, "맡기기", "계정", "캐릭터",
                    new CLogRecord.Item(732, 5, "화살", true, 20)));
            writer.offer(new CLogRecord(Kind.CONSOLE, TIME + 1000, new Object[]{"서버 시작\r\n"}));
            // Small batches must reach disk while the server is running.
            Path chat = root.resolve(ZonedDateTime.ofInstant(Instant.ofEpochMilli(TIME),
                    ZoneId.systemDefault()).toLocalDate().toString()).resolve("채팅.txt");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((!Files.exists(chat) || Files.size(chat) == 0) && System.nanoTime() < deadline) Thread.sleep(10);
            check(Files.exists(chat) && Files.size(chat) != 0, "idle writer flushes a single event");
            check(writer.shutdown(3000), "file drain");
            String chatText = Files.readString(chat, StandardCharsets.UTF_8);
            check(chatText.endsWith("\t전체\t[캐릭터]\t안녕\\r\\n가짜 기록\r\n"), "legacy format and escaped text");
            String deposit = Files.readString(chat.resolveSibling("창고.txt"), StandardCharsets.UTF_8);
            check(deposit.endsWith("\t창고:맡기기\t계정:[캐릭터]\t[732]+5화살(20)\r\n"), "warehouse format");
            String nextDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(TIME + 1000),
                    ZoneId.systemDefault()).toLocalDate().toString();
            check(Files.readString(root.resolve(nextDate).resolve("CMD.txt"), StandardCharsets.UTF_8)
                    .equals("서버 시작\r\n"), "console format and event date");
            check(!writer.diagnostics().hasLoss(), "healthy write diagnostics");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void stallsAndSaturation() throws Exception {
        CountDownLatch diskEntered = new CountDownLatch(1);
        CountDownLatch diskRelease = new CountDownLatch(1);
        CLogWriter writer = new CLogWriter((file, text) -> {
            diskEntered.countDown();
            try {
                if (!diskRelease.await(10, TimeUnit.SECONDS)) throw new IOException("simulated stall timed out");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
        }, 8, ZoneOffset.UTC, (view, text) -> {});
        try {
            writer.offer(event(Kind.GLOBAL, "character", "first"));
            check(diskEntered.await(3, TimeUnit.SECONDS), "writer is in the disk sink");
            FutureTask<Void> publish = new FutureTask<>(() -> {
                for (int i = 0; i < 64; i++) writer.offer(event(Kind.GLOBAL, "character", "flood"));
                writer.offer(event(Kind.COMMAND, "gm command"));
                return null;
            });
            Thread.ofPlatform().daemon().start(publish);
            publish.get(2, TimeUnit.SECONDS);
            var status = writer.diagnostics();
            check(status.generalDropped() == 56 && status.generalPending() == 9, "bounded general backlog");
            check(status.auditDropped() == 0 && status.auditPending() == 1, "audit has separate capacity");
            check(!writer.shutdown(50), "shutdown returns with the sink still stalled");
            writer.offer(event(Kind.COMMAND, "after close"));
            check(writer.diagnostics().rejectedAfterClose() == 1, "sealed admission");
        } finally {
            diskRelease.countDown();
            check(writer.shutdown(3000), "daemon discards backlog after timed-out shutdown");
        }
        check(writer.diagnostics().auditPending() == 0 && writer.diagnostics().generalPending() == 0,
                "discard released queued records");
    }

    private static void fileRetry() {
        Map<String, Integer> attempts = new HashMap<>();
        CLogWriter writer = new CLogWriter((file, text) -> {
            int attempt = attempts.merge(file.name(), 1, Integer::sum);
            switch (file.name()) {
                case "명령어.txt" -> {
                    if (attempt == 1) throw new IOException("temporary open failure");
                }
                case "로그인.txt" -> throw new IOException("permanent open failure");
                default -> { }
            }
        }, 8, ZoneOffset.UTC, (view, text) -> { throw new IllegalStateException("UI unavailable"); });
        writer.offer(event(Kind.COMMAND, "command"));
        writer.offer(event(Kind.CONNECTION, "login"));
        writer.offer(event(Kind.GLOBAL, "character", "hello"));
        check(writer.shutdown(3000), "failed files stop retrying");
        check(attempts.equals(Map.of("명령어.txt", 2, "로그인.txt", 2, "채팅.txt", 1)), "healthy file was not replayed");
        var status = writer.diagnostics();
        check(status.fileFailures() == 3 && status.unconfirmedWrites() == 1 && status.uiFailures() == 1,
                "distinct failure counters");
    }

    private static void concurrentShutdown() throws Exception {
        AtomicInteger written = new AtomicInteger();
        CLogWriter writer = new CLogWriter((file, text) -> {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') written.incrementAndGet();
            }
        }, 32, ZoneOffset.UTC, (view, text) -> {});
        int threads = 4;
        int events = 1000;
        CountDownLatch started = new CountDownLatch(threads);
        Thread[] publishers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            publishers[i] = Thread.ofPlatform().daemon().start(() -> {
                writer.offer(event(Kind.CONNECTION, "login"));
                started.countDown();
                for (int j = 1; j < events; j++) writer.offer(event(Kind.CONNECTION, "login"));
            });
        }
        check(started.await(3, TimeUnit.SECONDS) && writer.shutdown(3000), "concurrent shutdown drains accepted work");
        for (Thread publisher : publishers) {
            publisher.join(3000);
            check(!publisher.isAlive(), "publisher completes");
        }
        var status = writer.diagnostics();
        check(written.get() >= threads && status.auditPending() == 0
                && written.get() + status.auditDropped() + status.rejectedAfterClose() == threads * events,
                "producer and writer agree after closing");
    }

    private static CLogRecord event(Kind kind, Object... fields) {
        return new CLogRecord(kind, TIME, fields);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
