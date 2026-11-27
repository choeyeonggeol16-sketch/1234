package l1j.server.server.logd;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

import org.jctools.queues.MpscArrayQueue;

import l1j.server.server.logd.CLogRecord.FileKey;
import l1j.server.server.logd.CLogRecord.View;

final class CLogWriter {
    private static final int SEALED = Integer.MIN_VALUE;
    private static final int BATCH_SIZE = 256;
    private static final long IDLE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    @FunctionalInterface
    interface Sink {
        void append(FileKey file, CharSequence text) throws IOException;
    }

    record Diagnostics(int auditPending, int generalPending, long auditDropped, long generalDropped,
            long rejectedAfterClose, long captureFailures, long fileFailures, long unconfirmedWrites,
            long uiFailures, String lastFailure) {
        boolean hasLoss() {
            return auditDropped != 0 || generalDropped != 0 || rejectedAfterClose != 0
                    || captureFailures != 0 || unconfirmedWrites != 0;
        }
    }

    private final Sink sink;
    private final BiConsumer<View, String> ui;
    private final ZoneId zone;
    private final Lane audit;
    private final Lane general;

    // The sign bit seals admission while the lower bits count in-flight offers.
    private final AtomicInteger producers = new AtomicInteger();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final LongAdder rejectedAfterClose = new LongAdder();
    private final LongAdder captureFailures = new LongAdder();
    private final LongAdder fileFailures = new LongAdder();
    private final LongAdder unconfirmedWrites = new LongAdder();
    private final LongAdder uiFailures = new LongAdder();
    private final Thread worker;
    private volatile boolean abandon;
    private volatile String lastFailure;

    CLogWriter(Path root, int capacity, BiConsumer<View, String> ui) {
        this((file, text) -> appendFile(root, file, text), capacity, ZoneId.systemDefault(), ui);
    }

    CLogWriter(Sink sink, int capacity, ZoneId zone, BiConsumer<View, String> ui) {
        this.sink = sink;
        this.ui = ui;
        this.zone = zone;
        audit = new Lane(capacity);
        general = new Lane(capacity);
        worker = Thread.ofPlatform().daemon().name("LogD-Writer").unstarted(this::run);
        try {
            worker.start();
        } catch (SecurityException failure) {
            producers.set(SEALED);
            captureFailed(failure);
            stopped.countDown();
        }
    }

    void offer(CLogRecord event) {
        int active;
        do {
            active = producers.get();
            if (active < 0) {
                rejectedAfterClose.increment();
                return;
            }
        } while (!producers.compareAndSet(active, active + 1));
        try {
            Lane lane = event.kind().audit ? audit : general;
            if (!lane.queue.offer(event)) lane.dropped.increment();
        } finally {
            producers.decrementAndGet();
        }
    }

    void captureFailed(RuntimeException failure) {
        captureFailures.increment();
        lastFailure = failure.getClass().getName();
    }

    Diagnostics diagnostics() {
        return new Diagnostics(audit.pending(), general.pending(), audit.dropped.sum(), general.dropped.sum(),
                rejectedAfterClose.sum(), captureFailures.sum(), fileFailures.sum(), unconfirmedWrites.sum(),
                uiFailures.sum(), lastFailure);
    }

    boolean shutdown(long timeoutMillis) {
        producers.getAndUpdate(active -> active | SEALED);
        LockSupport.unpark(worker);
        try {
            if (stopped.await(timeoutMillis, TimeUnit.MILLISECONDS)) return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        abandon = true;
        LockSupport.unpark(worker);
        return false;
    }

    private void run() {
        try {
            while (true) {
                boolean closing = producers.get() < 0;
                if (abandon) {
                    if (producers.get() == SEALED) {
                        audit.discard();
                        general.discard();
                        return;
                    }
                    LockSupport.parkNanos(IDLE_NANOS);
                    continue;
                }
                boolean wroteAudit = audit.service(closing);
                boolean wroteGeneral = general.service(closing);
                if (producers.get() == SEALED && audit.isEmpty() && general.isEmpty()) return;
                if (!wroteAudit && !wroteGeneral) LockSupport.parkNanos(IDLE_NANOS);
            }
        } finally {
            producers.getAndUpdate(active -> active | SEALED);
            stopped.countDown();
        }
    }

    private final class Lane {
        private final MpscArrayQueue<CLogRecord> queue;
        private final Map<FileKey, Batch> files = new LinkedHashMap<>();
        private final LongAdder dropped = new LongAdder();
        private volatile int inFlight;
        private long retryAt;

        Lane(int capacity) {
            queue = new MpscArrayQueue<>(capacity);
        }

        int pending() {
            return queue.size() + inFlight;
        }

        boolean isEmpty() {
            return files.isEmpty() && queue.isEmpty();
        }

        void discard() {
            long count = inFlight;
            files.clear();
            inFlight = 0;
            while (queue.relaxedPoll() != null) count++;
            dropped.add(count);
        }

        boolean service(boolean closing) {
            if (abandon) return false;
            boolean retry = !files.isEmpty();
            if (retry && !closing && System.nanoTime() - retryAt < 0) return false;
            if (!retry) collect();
            if (files.isEmpty()) return false;
            for (Iterator<Map.Entry<FileKey, Batch>> it = files.entrySet().iterator(); it.hasNext() && !abandon;) {
                Map.Entry<FileKey, Batch> entry = it.next();
                Batch batch = entry.getValue();
                try {
                    sink.append(entry.getKey(), batch.text);
                } catch (IOException | RuntimeException failure) {
                    fileFailures.increment();
                    lastFailure = failure.toString();
                    if (!retry && failure instanceof IOException && !(failure instanceof UncertainAppend)) continue;
                    unconfirmedWrites.add(batch.count);
                }
                it.remove();
                inFlight -= batch.count;
            }
            if (!files.isEmpty()) retryAt = System.nanoTime() + RETRY_NANOS;
            return true;
        }

        private void collect() {
            for (int i = 0; i < BATCH_SIZE; i++) {
                CLogRecord event = queue.relaxedPoll();
                if (event == null) return;
                CLogRecord.Line line;
                try {
                    line = event.format(zone);
                } catch (RuntimeException failure) {
                    captureFailed(failure);
                    continue;
                }
                Batch batch = files.computeIfAbsent(line.file(), key -> new Batch());
                batch.text.append(line.text());
                batch.count++;
                inFlight++;
                if (line.view() != View.NONE) {
                    try {
                        ui.accept(line.view(), line.text());
                    } catch (RuntimeException failure) {
                        uiFailures.increment();
                        lastFailure = failure.toString();
                    }
                }
            }
        }
    }

    private static final class Batch {
        final StringBuilder text = new StringBuilder();
        int count;
    }

    private static void appendFile(Path root, FileKey file, CharSequence text) throws IOException {
        Path directory = root.resolve(file.date().toString());
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream stream = open(directory, file.name());
        try (stream) {
            stream.write(bytes);
        } catch (IOException failure) {
            // Retrying an append after a possible partial write can duplicate records.
            throw new UncertainAppend(failure);
        }
    }

    private static OutputStream open(Path directory, String name) throws IOException {
        Path path = directory.resolve(name);
        try {
            return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (NoSuchFileException missing) {
            Files.createDirectories(directory);
            return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static final class UncertainAppend extends IOException {
        private static final long serialVersionUID = 1L;

        UncertainAppend(IOException cause) {
            super(cause);
        }
    }
}
