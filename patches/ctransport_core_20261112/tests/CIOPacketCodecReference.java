package l1j.server.server.model.network.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Mandatory 2019-02-15 native protocol codec. One instance belongs to one
 * connection and guards its own native lifetime with synchronized methods.
 * The native DLL is always used; no JVM feature flag or silent Java fallback.
 * Windows x64: build CIOPacketCodec.dll from native/CIOPacketCodecReference.cpp.
 */
@SuppressWarnings("preview")
final class CIOPacketCodecReference implements AutoCloseable {
    static final int INCOMPLETE = -2;
    static final int EMPTY = -3;
    private static final int MAX_PLAINTEXT = 65535;

    private static final class API {
        static final Linker LINKER = Linker.nativeLinker();
        static final SymbolLookup SYMBOLS = openKnownCodec();
        static SymbolLookup openKnownCodec() {
            final Path path = Path.of("CIOPacketCodec.dll").toAbsolutePath();
            try {
                CTransportServer.requireKnownDll(path,
                        "60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9");
                return SymbolLookup.libraryLookup(path, Arena.global());
            } catch (java.io.IOException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
        static final MethodHandle VERSION = bind("CIOPC_Version",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
        // This checks presence and a valid unsigned version. Pinning an exact
        // ABI/feature set also requires the matching C++ sources and DLL build.
        static final long ABI_VERSION = verifyVersion();
        private static long verifyVersion() {
            try {
                final long version = Integer.toUnsignedLong((int) VERSION.invokeExact());
                // x64 Aden DLL supplied with this release returns 0x00010000.
                if (version != 0x00010000L)
                    throw new UnsatisfiedLinkError("Unsupported CIOPC ABI version: 0x"
                            + Long.toHexString(version) + ", expected 0x10000");
                return version;
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        static final MethodHandle CREATE = bind("CIOPC_Create",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle DESTROY = bind("CIOPC_Destroy",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        static final MethodHandle FEED = bind("CIOPC_Feed",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle NEXT = bind("CIOPC_Next",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle ENCRYPT = bind("CIOPC_Encrypt",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle PENDING = bind("CIOPC_Pending",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        private static MethodHandle bind(String symbol, FunctionDescriptor signature) {
            return LINKER.downcallHandle(SYMBOLS.find(symbol)
                    .orElseThrow(() -> new UnsatisfiedLinkError(symbol)), signature);
        }
    }

    private MemorySegment handle;
    private ByteBuffer plaintext = ByteBuffer.allocateDirect(4096)
            .order(ByteOrder.LITTLE_ENDIAN);

    CIOPacketCodecReference(long seed) {
        try {
            handle = (MemorySegment) API.CREATE.invokeExact((int) seed);
            if (handle.address() == 0) throw new OutOfMemoryError("native codec allocation");
        } catch (Throwable e) {
            throw new IllegalStateException("CIO native codec initialization failed", e);
        }
    }

    private void checkOpen() {
        if (handle == null) throw new IllegalStateException("CIO codec closed");
    }

    /** The input buffer's current position/limit define the bytes to feed. */
    synchronized void feed(ByteBuffer source) {
        checkOpen();
        if (!source.isDirect()) throw new IllegalArgumentException("native READ must be direct");
        final int size = source.remaining();
        if (size == 0) return;
        try {
            final int result = (int) API.FEED.invokeExact(handle,
                    MemorySegment.ofBuffer(source), size);
            if (result != size) throw new IllegalStateException("native feed returned " + result);
        } catch (Throwable e) {
            throw new IllegalStateException("CIO native feed failed", e);
        }
    }

    /** >0 decoded payload bytes, -2 incomplete, -3 empty; all other errors fatal. */
    synchronized int next() {
        checkOpen();
        try {
            for (;;) {
                plaintext.clear();
                final int capacity = plaintext.capacity();
                final int result = (int) API.NEXT.invokeExact(handle,
                        MemorySegment.ofBuffer(plaintext), capacity);
                if (result == -5 && capacity < MAX_PLAINTEXT) {
                    plaintext = ByteBuffer.allocateDirect(Math.min(MAX_PLAINTEXT, capacity * 2))
                            .order(ByteOrder.LITTLE_ENDIAN);
                    continue;
                }
                if (result > 0) {
                    plaintext.limit(result);
                    return result;
                }
                if (result == INCOMPLETE || result == EMPTY) return result;
                throw new IllegalStateException("native parser returned " + result);
            }
        } catch (Throwable e) {
            throw new IllegalStateException("CIO native next failed", e);
        }
    }

    /** Valid only until the next call to next() on this connection. */
    synchronized ByteBuffer plaintext() { checkOpen(); return plaintext; }

    synchronized int pending() {
        checkOpen();
        try { return (int) API.PENDING.invokeExact(handle); }
        catch (Throwable e) { throw new IllegalStateException("CIO native pending failed", e); }
    }

    /** Encrypt exactly the payload range, excluding the 2-byte length. */
    synchronized void encrypt(ByteBuffer buffer, int offset, int length) {
        checkOpen();
        if (!buffer.isDirect() || offset < 0 || length < 0
                || offset > buffer.capacity() - length)
            throw new IllegalArgumentException("invalid native SEND buffer");
        try {
            final ByteBuffer slice = buffer.duplicate();
            slice.clear();
            slice.position(offset).limit(offset + length);
            final int result = (int) API.ENCRYPT.invokeExact(handle,
                    MemorySegment.ofBuffer(slice), length);
            if (result != 0) throw new IllegalStateException("native encrypt returned " + result);
        } catch (Throwable e) {
            throw new IllegalStateException("CIO native encryption failed", e);
        }
    }

    @Override public synchronized void close() {
        final MemorySegment p = handle;
        handle = null;
        if (p == null) return;
        try { API.DESTROY.invokeExact(p); }
        catch (Throwable e) { throw new IllegalStateException("CIO native destroy failed", e); }
    }
}
