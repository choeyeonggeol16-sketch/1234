package l1j.server.server.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One transport lifetime, with ordered game delivery and byte budgets retained through write completion. */
public final class NettySession {

    private static final Logger LOG = Logger.getLogger(NettySession.class.getName());
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final long MAILBOX_STALL_NANOS = TimeUnit.SECONDS.toNanos(3);
    public final long generation = GENERATIONS.incrementAndGet();
    
    final CServer server;
    final Channel channel;
    final NetworkConfig config;
    final NetworkMetrics metrics;
    
    private final AdmissionController.Permit permit;
    private volatile SessionApplication app;
    private volatile SessionApplication.Framing framing = SessionApplication.Framing.lineage();
    
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean authenticated = new AtomicBoolean();
    private final AtomicBoolean retired = new AtomicBoolean();
    private final AtomicBoolean retireScheduled = new AtomicBoolean();
    
    private volatile CloseReason reason = CloseReason.PEER_CLOSED;
    private volatile boolean transportClosed;
    private volatile boolean readPaused;
    
    private final AtomicLong inBytes = new AtomicLong();
    private final AtomicLong outBytes = new AtomicLong();
    private final AtomicInteger inPackets = new AtomicInteger();
    private final AtomicInteger outPackets = new AtomicInteger();
    private final AtomicInteger controls = new AtomicInteger();
    
    private long rawBufferedBytes;
    private long partialStartedNs;
    private final long createdNs = System.nanoTime();
    private volatile long authStartedNs = createdNs;
    private long lastReadNs = createdNs;
    private volatile long lastWriteProgressNs = createdNs;
    private volatile long drainStartedNs;
    
    private ScheduledFuture<?> watchdog;
    private volatile ScheduledFuture<?> cleanupDelay;
    private volatile Runnable cleanupAction;
    private final AtomicBoolean cleanupSubmitted = new AtomicBoolean();
    
    private final Object outputLock = new Object();
    private final ConcurrentLinkedQueue<Outbound> output = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean writeScheduled = new AtomicBoolean();
    private final AtomicBoolean resumeScheduled = new AtomicBoolean();
    
    final Mailbox mailbox = new Mailbox();

    NettySession(CServer server, Channel channel, AdmissionController.Permit permit) {
        this.server = server;
        this.channel = channel;
        this.permit = permit;
        this.config = server.config;
        this.metrics = server.metrics;
    }

    void configure(SessionApplication app) {
        this.app = app;
        this.framing = app.framing();
    }

    SessionApplication.Framing framing() {
        return framing;
    }

    public int remotePort() {
        return ((InetSocketAddress) channel.remoteAddress()).getPort();
    }

    public String remoteIp() {
        return ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
    }

    public boolean isOpen() {
        return !isClosing() && channel.isActive();
    }

    public boolean isClosing() {
        return closing.get() || draining.get();
    }

    boolean authenticated() {
        return authenticated.get();
    }

    public void markAuthenticated() {
        authenticated.set(true);
    }

    public void markUnauthenticated() {
        authStartedNs = System.nanoTime();
        authenticated.set(false);
    }

    public long pendingInboundBytes() {
        return inBytes.get();
    }

    public long pendingOutboundBytes() {
        return outBytes.get();
    }

    void opened() throws Exception {
        if (app == null) {
            close(CloseReason.INITIALIZATION_ERROR);
            return;
        }
        
        byte[] hello = app.initialHandshake();
        if (hello.length != 0 && !enqueueOutput(hello, true)) {
            return;
        }
        
        app.onOpen();
        
        long step = Math.max(10, Math.min(1000, 
                Math.min(config.frameTimeoutMs, Math.min(config.authTimeoutMs, config.writeStallMs)) / 2));
                
        watchdog = channel.eventLoop().scheduleAtFixedRate(this::checkDeadlines, step, step, TimeUnit.MILLISECONDS);
    }

    /** Snapshot before crossing threads. False means explicitly refused, never accepted-and-forgotten. */
    public boolean send(byte[] plaintext) {
        if (plaintext == null || plaintext.length < framing.minBodyBytes() || plaintext.length > framing.maxBodyBytes()) {
            close(CloseReason.INVALID_FRAME);
            return false;
        }
        return enqueueOutput(plaintext, false);
    }

    private boolean enqueueOutput(byte[] bytes, boolean raw) {
        long wire = raw ? bytes.length : bytes.length + (long) framing.prefixBytes();
        
        if (wire > config.maxFrameBytes) {
            close(CloseReason.INVALID_FRAME);
            return false;
        }
        
        String overloadLimit = null;
        
        synchronized (outputLock) {
            if (isClosing()) return false;
            
            if (outPackets.get() >= config.outboundPackets) {
                overloadLimit = "sessionPackets";
            } else if (!NetworkMetrics.reserve(outBytes, wire, config.outboundBytes)) {
                overloadLimit = "sessionBytes";
            } else if (!NetworkMetrics.reserve(metrics.outboundBytes, wire, config.globalOutboundBytes)) {
                NetworkMetrics.release(outBytes, wire);
                overloadLimit = "globalBytes";
            } else if (!NetworkMetrics.reserve(metrics.outboundPackets, 1, config.globalOutboundPackets)) {
                NetworkMetrics.release(outBytes, wire);
                NetworkMetrics.release(metrics.outboundBytes, wire);
                overloadLimit = "globalPackets";
            } else {
                boolean counted = false;
                try {
                    byte[] snapshot = bytes.clone();
                    if (outPackets.getAndIncrement() == 0) {
                        lastWriteProgressNs = System.nanoTime();
                    }
                    counted = true;
                    output.add(new Outbound(snapshot, wire, raw));
                } catch (Throwable allocationFailure) {
                    if (counted) outPackets.decrementAndGet();
                    NetworkMetrics.release(metrics.outboundPackets, 1);
                    NetworkMetrics.release(outBytes, wire);
                    NetworkMetrics.release(metrics.outboundBytes, wire);
                    throw allocationFailure;
                }
            }
        }
        
        if (overloadLimit != null) {
            if (metrics.allowOutputOverloadLog(System.nanoTime())) {
                LOG.warning("Outbound overload: session=" + generation
                        + " limit=" + overloadLimit
                        + " queuedPackets=" + outPackets.get() + "/" + config.outboundPackets
                        + " queuedBytes=" + outBytes.get() + "/" + config.outboundBytes
                        + " globalPackets=" + metrics.outboundPackets.get() + "/" + config.globalOutboundPackets
                        + " globalBytes=" + metrics.outboundBytes.get() + "/" + config.globalOutboundBytes);
            }
            close(CloseReason.OUTBOUND_OVERLOAD);
            return false;
        }
        
        requestWrite();
        return true;
    }

    private void requestWrite() {
        if (!writeScheduled.compareAndSet(false, true)) return;
        try {
            channel.eventLoop().execute(this::drainWrites);
        } catch (RuntimeException e) {
            writeScheduled.set(false);
            close(CloseReason.IO_ERROR);
        }
    }

    void writable() {
        if (channel.isWritable() && !closing.get()) {
            requestWrite();
        }
    }

    private void drainWrites() {
        int packets = 0;
        int writtenBytes = 0;
        try {
            while (!closing.get() && channel.isWritable() && packets < config.batchPackets) {
                Outbound next = output.peek();
                if (next == null || (packets > 0 && writtenBytes + next.wireBytes > config.batchBytes)) {
                    break;
                }
                
                next = output.poll();
                if (next == null) continue;
                
                final Outbound message = next;
                ByteBuf buffer = null;
                
                try {
                    byte[] encoded = message.raw ? message.body : app.encode(message.body);
                    if (encoded.length != message.body.length) {
                        throw new IllegalStateException("Codec changed body length");
                    }
                    
                    buffer = channel.alloc().ioBuffer((int) message.wireBytes, (int) message.wireBytes);
                    
                    if (!message.raw) {
                        int length = framing.lengthIncludesPrefix() ? (int) message.wireBytes : encoded.length;
                        if (framing.prefixBytes() == 2) {
                            buffer.writeShortLE(length);
                        } else {
                            buffer.writeIntLE(length);
                        }
                    }
                    
                    buffer.writeBytes(encoded);
                    ChannelFuture write = channel.write(buffer);
                    buffer = null;
                    
                    write.addListener(f -> {
                        lastWriteProgressNs = System.nanoTime();
                        releaseOutbound(message);
                        if (!f.isSuccess() && !closing.get()) {
                            close(CloseReason.IO_ERROR);
                        }
                    });
                    
                    packets++;
                    writtenBytes += (int) message.wireBytes;
                    
                } catch (Exception failure) {
                    ReferenceCountUtil.safeRelease(buffer);
                    releaseOutbound(message);
                    close(CloseReason.CIPHER_ERROR);
                    break;
                }
            }
            
            if (packets != 0) {
                channel.flush();
                metrics.flushes.increment();
            }
            
        } finally {
            writeScheduled.set(false);
            if (!closing.get() && !output.isEmpty() && channel.isWritable()) {
                requestWrite();
            }
            maybeRetire();
        }
    }

    private void releaseOutbound(Outbound message) {
        if (!message.released.compareAndSet(false, true)) return;
        
        NetworkMetrics.release(outBytes, message.wireBytes);
        NetworkMetrics.release(metrics.outboundBytes, message.wireBytes);
        outPackets.decrementAndGet();
        metrics.outboundPackets.decrementAndGet();
        
        if (draining.get() && outPackets.get() == 0) {
            close(reason);
        }
        checkRetireLater();
    }

    boolean rawRead(int bytes) {
        if (isClosing()) return false;
        
        if (!NetworkMetrics.reserve(inBytes, bytes, config.inboundBytes)) {
            close(CloseReason.INBOUND_OVERLOAD);
            return false;
        }
        
        if (!NetworkMetrics.reserve(metrics.inboundBytes, bytes, config.globalInboundBytes)) {
            NetworkMetrics.release(inBytes, bytes);
            close(CloseReason.INBOUND_OVERLOAD);
            return false;
        }
        
        rawBufferedBytes += bytes;
        lastReadNs = System.nanoTime();
        
        // A high watermark below one maximum frame can strand a legal fragmented frame.
        if (inBytes.get() >= Math.max(config.maxFrameBytes, config.inboundBytes / 2) && !readPaused) {
            readPaused = true;
            channel.config().setAutoRead(false);
            metrics.readPauses.increment();
        }
        
        return true;
    }

    void partialFrame() {
        if (partialStartedNs == 0) {
            partialStartedNs = System.nanoTime();
        }
    }

    void frameComplete(byte[] encrypted, int wireBytes) throws Exception {
        partialStartedNs = 0;
        if (closing.get()) return;
        
        if (inPackets.get() >= config.inboundPackets || !NetworkMetrics.reserve(metrics.inboundPackets, 1, config.globalInboundPackets)) {
            close(CloseReason.INBOUND_OVERLOAD);
            return;
        }
        
        byte[] decoded;
        try {
            decoded = app.decode(encrypted);
        } catch (Exception failure) {
            metrics.inboundPackets.decrementAndGet();
            close(CloseReason.CIPHER_ERROR);
            return;
        }
        
        if (decoded == null || decoded.length != encrypted.length) {
            metrics.inboundPackets.decrementAndGet();
            close(CloseReason.CIPHER_ERROR);
            return;
        }
        
        rawBufferedBytes -= wireBytes;
        if (rawBufferedBytes < 0) {
            throw new IllegalStateException("Frame accounting underflow");
        }
        
        inPackets.incrementAndGet();
        mailbox.events.add(new Input(decoded, wireBytes));
        mailbox.schedule();
    }

    private void releaseInput(Input input) {
        NetworkMetrics.release(inBytes, input.wireBytes);
        NetworkMetrics.release(metrics.inboundBytes, input.wireBytes);
        inPackets.decrementAndGet();
        metrics.inboundPackets.decrementAndGet();
        resumeReadLater();
        checkRetireLater();
    }

    private void resumeReadLater() {
        if (!readPaused || isClosing() || !resumeScheduled.compareAndSet(false, true)) return;
        
        try {
            channel.eventLoop().execute(() -> {
                resumeScheduled.set(false);
                if (!isClosing() && readPaused && inBytes.get() < Math.max(config.maxFrameBytes, config.inboundBytes / 4)) {
                    try {
                        channel.config().setAutoRead(true);
                        readPaused = false;
                        metrics.readResumes.increment();
                    } catch (RuntimeException failure) {
                        close(CloseReason.IO_ERROR);
                    }
                }
                maybeRetire();
            });
        } catch (RuntimeException e) {
            resumeScheduled.set(false);
            close(CloseReason.IO_ERROR);
        }
    }

    /** Bounded serial game control events, used by the four legacy per-session timers. */
    public boolean executeOnOwner(Runnable task) {
        if (task == null || isClosing()) return false;
        
        for (;;) {
            int n = controls.get();
            if (n >= config.controlTasks) return false;
            if (controls.compareAndSet(n, n + 1)) break;
        }
        
        if (isClosing()) {
            controls.decrementAndGet();
            checkRetireLater();
            return false;
        }
        
        mailbox.events.add(new Control(task));
        mailbox.schedule();
        return true;
    }

    /** Seal output, drain accepted frames, and force close at a finite deadline. */
    public void closeGracefully(CloseReason why) {
        synchronized (outputLock) {
            if (closing.get() || !draining.compareAndSet(false, true)) return;
            reason = why;
            drainStartedNs = System.nanoTime();
        }
        
        channel.eventLoop().execute(() -> {
            channel.config().setAutoRead(false);
            if (outPackets.get() == 0) {
                close(why);
            } else {
                requestWrite();
            }
        });
    }

    public void close(CloseReason why) {
        if (why == CloseReason.SERVER_SHUTDOWN) {
            forceCleanup();
        }
        
        if (!closing.compareAndSet(false, true)) return;
        
        reason = why;
        try {
            channel.eventLoop().execute(channel::close);
        } catch (RuntimeException e) {
            channel.close();
        }
    }

    void inactive() {
        if (transportClosed) return;
        
        transportClosed = true;
        closing.set(true);
        
        if (watchdog != null) {
            watchdog.cancel(false);
            watchdog = null;
        }
        
        long unframed = rawBufferedBytes;
        rawBufferedBytes = 0;
        
        if (unframed != 0) {
            NetworkMetrics.release(inBytes, unframed);
            NetworkMetrics.release(metrics.inboundBytes, unframed);
        }
        
        synchronized (outputLock) {
            Outbound o;
            while ((o = output.poll()) != null) {
                releaseOutbound(o);
            }
        }
        
        metrics.closed(reason);
        mailbox.terminal = true;
        mailbox.schedule();
        maybeRetire();
    }

    private void checkDeadlines() {
        if (closing.get()) return;
        long now = System.nanoTime();

        // Recovery is event-loop owned; only trigger when the byte budget is genuinely low.
        // Normally releaseInput() schedules this immediately. This catches a missed wake-up.
        if (readPaused && inBytes.get() < Math.max(config.maxFrameBytes, config.inboundBytes / 4)
                && !resumeScheduled.get()) {
            metrics.readResumeRecoveries.increment();
            resumeReadLater();
        }
        mailbox.reportStall(now);

        if (draining.get()) {
            if (elapsed(now, drainStartedNs, config.gracefulCloseMs)) {
                close(reason);
            }
            return;
        }
        
        if (partialStartedNs != 0 && elapsed(now, partialStartedNs, config.frameTimeoutMs)) {
            close(CloseReason.FRAME_TIMEOUT);
        } else if (!authenticated.get() && elapsed(now, authStartedNs, config.authTimeoutMs)) {
            close(CloseReason.AUTH_TIMEOUT);
        } else if (config.readIdleMs > 0 && elapsed(now, lastReadNs, config.readIdleMs)) {
            close(CloseReason.READ_IDLE);
        } else if (outPackets.get() > 0 && elapsed(now, lastWriteProgressNs, config.writeStallMs)) {
            close(CloseReason.WRITE_STALL);
        }
    }

    private static boolean elapsed(long now, long before, long ms) {
        return now - before >= TimeUnit.MILLISECONDS.toNanos(ms);
    }

    private void checkRetireLater() {
        if (!transportClosed || !mailbox.terminalDone || !retireScheduled.compareAndSet(false, true)) return;
        
        try {
            channel.eventLoop().execute(() -> {
                retireScheduled.set(false);
                maybeRetire();
            });
        } catch (RuntimeException e) {
            retireScheduled.set(false);
            maybeRetire();
        }
    }

    private void prepareCleanup(Runnable action) {
        cleanupAction = action;
        long delay = 0;
        try {
            if (app != null) {
                delay = Math.max(0, Math.min(5000, app.closeDelayMillis()));
            }
        } catch (Exception failure) {
            LOG.log(Level.WARNING, "Close delay calculation failed", failure);
        }
        
        if (delay == 0 || server.isStopping()) {
            forceCleanup();
        } else {
            cleanupDelay = channel.eventLoop().schedule(this::forceCleanup, delay, TimeUnit.MILLISECONDS);
            if (server.isStopping()) {
                forceCleanup();
            }
        }
    }

    private void forceCleanup() {
        Runnable action = cleanupAction;
        if (action == null || !cleanupSubmitted.compareAndSet(false, true)) return;
        
        ScheduledFuture<?> delayed = cleanupDelay;
        if (delayed != null) delayed.cancel(false);
        
        server.dispatcher.cleanup(() -> {
            try {
                action.run();
            } finally {
                cleanupAction = null;
                cleanupDelay = null;
            }
        });
    }

    private void maybeRetire() {
        if (transportClosed && mailbox.terminalDone && !mailbox.scheduled.get()
                && !writeScheduled.get() && !resumeScheduled.get()
                && inPackets.get() == 0 && outPackets.get() == 0 && controls.get() == 0
                && inBytes.get() == 0 && outBytes.get() == 0 && retired.compareAndSet(false, true)) {
            app = null;
            permit.release();
            server.retired(this);
        }
    }

    final class Mailbox {
        
        boolean ownerAuthenticated() {
            return authenticated();
        }
        
        final ConcurrentLinkedQueue<Object> events = new ConcurrentLinkedQueue<>();
        final AtomicBoolean scheduled = new AtomicBoolean();
        volatile boolean terminal;
        volatile boolean terminalDone;
        final AtomicBoolean terminalScheduled = new AtomicBoolean();
        volatile long queuedAtNs;
        volatile long runningAtNs;

        void schedule() {
            if (!scheduled.compareAndSet(false, true)) return;
            queuedAtNs = System.nanoTime();
            try {
                server.dispatcher.schedule(this);
            } catch (RuntimeException failure) {
                // A rejected dispatch must never leave scheduled=true with accepted input.
                queuedAtNs = 0;
                scheduled.set(false);
                metrics.mailboxDispatchFailures.increment();
                LOG.log(Level.SEVERE, "Mailbox dispatch rejected; closing session " + generation, failure);
                close(CloseReason.IO_ERROR);
            }
        }

        void reportStall(long now) {
            if (!scheduled.get()) return;
            long runningSince = runningAtNs;
            long waitingSince = queuedAtNs;
            boolean running = runningSince != 0;
            long since = running ? runningSince : waitingSince;
            if (since == 0 || now - since < MAILBOX_STALL_NANOS) return;
            if (running) metrics.mailboxRunStalls.increment();
            else metrics.mailboxWaitStalls.increment();
            if (metrics.allowStallLog(now)) {
                LOG.warning("Mailbox stalled: session=" + generation
                        + " state=" + (running ? "RUNNING" : "QUEUED")
                        + " ageMs=" + TimeUnit.NANOSECONDS.toMillis(now - since)
                        + " inboundPackets=" + inPackets.get()
                        + " inboundBytes=" + inBytes.get()
                        + " controls=" + controls.get()
                        + " readPaused=" + readPaused
                        + " autoRead=" + channel.config().isAutoRead()
                        + " gameQueued=" + server.dispatcher.gameQueued()
                        + " gameRunning=" + server.dispatcher.gameRunning()
                        + " authQueued=" + server.dispatcher.authQueued()
                        + " authRunning=" + server.dispatcher.authRunning());
            }
        }

        void runBatch(boolean dispatchedAuthenticated) {
            int handled = 0;
            long start = System.nanoTime();
            long queuedSince = queuedAtNs;
            runningAtNs = start;
            queuedAtNs = 0;
            if (queuedSince != 0) NetworkMetrics.observeMax(metrics.mailboxMaxWaitNanos, start - queuedSince);

            try {
                metrics.gameBatches.increment();
                while (handled < config.batchPackets && (handled == 0 || System.nanoTime() - start < 1_000_000L)) {
                    
                    if (terminal) {
                        Object pending;
                        while ((pending = events.poll()) != null) {
                            discard(pending);
                        }
                        
                        if (terminalScheduled.compareAndSet(false, true)) {
                            prepareCleanup(() -> {
                                try {
                                    if (app != null) app.onClosed(reason);
                                } catch (Throwable failure) {
                                    LOG.log(Level.SEVERE, "Session close callback failed", failure);
                                } finally {
                                    terminalDone = true;
                                    checkRetireLater();
                                }
                            });
                        }
                        return;
                    }
                    
                    // Authentication/return-to-login changes lanes at a packet boundary.
                    if (ownerAuthenticated() != dispatchedAuthenticated) break;
                    
                    Object e = events.poll();
                    if (e == null) break;
                    
                    try {
                        if (e instanceof Input input) {
                            if (!isClosing()) {
                                app.onPacket(input.body);
                                metrics.processed.increment();
                            }
                        } else if (e instanceof Control c && !isClosing()) {
                            c.task.run();
                        }
                    } catch (Throwable failure) {
                        metrics.handlerErrors.increment();
                        close(CloseReason.HANDLER_ERROR);
                        LOG.log(Level.WARNING, "Session handler failed; closing this session", failure);
                    } finally {
                        discard(e);
                    }
                    
                    handled++;
                }
            } finally {
                NetworkMetrics.observeMax(metrics.mailboxMaxRunNanos, System.nanoTime() - start);
                runningAtNs = 0;
                scheduled.set(false);
                if (!events.isEmpty() || (terminal && !terminalScheduled.get())) {
                    schedule();
                } else if (terminalDone) {
                    checkRetireLater();
                }
            }
        }
        
        private void discard(Object e) {
            if (e instanceof Input input) {
                releaseInput(input);
            } else {
                controls.decrementAndGet();
            }
        }
    }

    private record Input(byte[] body, int wireBytes) { }
    
    private record Control(Runnable task) { }
    
    private static final class Outbound {
        final byte[] body;
        final long wireBytes;
        final boolean raw;
        final AtomicBoolean released = new AtomicBoolean();

        Outbound(byte[] body, long wireBytes, boolean raw) {
            this.body = body;
            this.wireBytes = wireBytes;
            this.raw = raw;
        }
    }
}
