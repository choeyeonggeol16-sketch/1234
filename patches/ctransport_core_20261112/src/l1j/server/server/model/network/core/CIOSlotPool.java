package l1j.server.server.model.network.core;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lock-free registered-slot allocator safe for arbitrary producer/owner threads.
 *
 * Each slot also carries a 16-bit non-zero generation so the same pool can be
 * used as a pooled-buffer lease token source without stale-slot aliasing after reuse.
 */
public final class CIOSlotPool {

    private static final int GENERATION_MASK = 0xFFFF;
    // One 64-bit bitmap word per nominal 64-byte cache line. Independent
    // completion workers otherwise bounce adjacent words in the same line.
    private static final int BITMAP_WORD_STRIDE = 8;

    private final int _slotSize;
    private final int _capacity;
    private final int _baseOffset;

    private final AtomicLongArray _freeBits;
    private final AtomicIntegerArray _generation;

    public CIOSlotPool(
            int slotSize,
            int capacity,
            int baseOffset) {

        if (slotSize <= 0) {
            throw new IllegalArgumentException("slotSize <= 0");
        }
        if (capacity <= 0 || capacity > 0x10000) {
            throw new IllegalArgumentException(
                    "capacity must be 1..65536");
        }
        if (baseOffset < 0) {
            throw new IllegalArgumentException("baseOffset < 0");
        }

        _slotSize = slotSize;
        _capacity = capacity;
        _baseOffset = baseOffset;

        final int words =
                (capacity + 63) >>> 6;

        _freeBits =
                new AtomicLongArray(words * BITMAP_WORD_STRIDE);

        for (int i = 0; i < words; i++) {
            _freeBits.set(i * BITMAP_WORD_STRIDE, -1L);
        }

        final int tailBits =
                capacity & 63;

        if (tailBits != 0) {
            _freeBits.set(
                    (words - 1) * BITMAP_WORD_STRIDE,
                    (1L << tailBits) - 1L);
        }

        _generation =
                new AtomicIntegerArray(capacity);

        for (int i = 0; i < capacity; i++) {
            _generation.set(i, 1);
        }

    }

    /**
     * @return local slot id, or -1 when the pool is exhausted.
     */
    public int acquire() {
        final int words =
                _freeBits.length() / BITMAP_WORD_STRIDE;

        /*
         * Spread producer threads across bitmap words. Starting every acquire
         * at word zero would turn the first cache line into a hot CAS point now
         * that this allocator backs the normal SEND path.
         */
        final int startWord =
                (int) (Thread.currentThread().threadId()
                        % words);

        for (int n = 0;
                n < words;
                n++) {

            int word =
                    startWord + n;

            if (word >= words) {
                word -= words;
            }

            long bits =
                    _freeBits.get(word * BITMAP_WORD_STRIDE);

            while (bits != 0L) {
                final int bit =
                        Long.numberOfTrailingZeros(bits);

                final long mask =
                        1L << bit;

                final long next =
                        bits & ~mask;

                if (_freeBits.compareAndSet(
                        word * BITMAP_WORD_STRIDE,
                        bits,
                        next)) {

                    final int slot =
                            (word << 6) + bit;

                    if (slot >= _capacity) {
                        throw new IllegalStateException(
                                "invalid free bit: " + slot);
                    }

                    return slot;
                }

                bits =
                        _freeBits.get(word * BITMAP_WORD_STRIDE);
            }
        }

        return -1;
    }

    public void release(int slot) {
        checkSlot(slot);
        release(slot, _generation.get(slot));
    }

    public void release(
            int slot,
            int expectedGeneration) {

        checkSlot(slot);

        final int word =
                slot >>> 6;

        final long mask =
                1L << (slot & 63);

        if ((_freeBits.get(word * BITMAP_WORD_STRIDE) & mask) != 0L) {
            throw new IllegalStateException(
                    "RConcurrentSlot double release: "
                            + slot);
        }

        final int currentGeneration =
                _generation.get(slot);

        if (currentGeneration != expectedGeneration) {
            throw new IllegalStateException(
                    "RConcurrentSlot stale generation: slot="
                            + slot
                            + ", expected="
                            + expectedGeneration
                            + ", current="
                            + currentGeneration);
        }

        int nextGeneration =
                (currentGeneration + 1)
                        & GENERATION_MASK;

        if (nextGeneration == 0) {
            nextGeneration = 1;
        }

        if (!_generation.compareAndSet(
                slot,
                currentGeneration,
                nextGeneration)) {

            throw new IllegalStateException(
                    "RConcurrentSlot concurrent release: "
                            + slot);
        }

        for (;;) {
            final long bits =
                    _freeBits.get(word * BITMAP_WORD_STRIDE);

            if ((bits & mask) != 0L) {
                throw new IllegalStateException(
                        "RConcurrentSlot double release: "
                                + slot);
            }

            if (_freeBits.compareAndSet(
                    word * BITMAP_WORD_STRIDE,
                    bits,
                    bits | mask)) {

                return;
            }
        }
    }

    /**
     * Fast release for a caller that has already atomically revoked its lease.
     * CIOBufferEx.release() gates exactly one releaser with ACTIVE.getAndSet.
     * Unlike the public release(), there cannot be a competing owner of this
     * generation, so a second generation CAS is redundant. The bitmap CAS is
     * still necessary because independent slots share a bitmap word.
     */
    void releaseOwnedLease(int slot, int expectedGeneration) {
        checkSlot(slot);
        final int word = (slot >>> 6) * BITMAP_WORD_STRIDE;
        final long mask = 1L << (slot & 63);
        // The bitmap is checked in the publication CAS loop below.  A
        // separate volatile bitmap read here is redundant for the ACTIVE-
        // gated lease owner, and adds work on every SEND buffer return.
        final int generation = _generation.get(slot);
        if (generation != expectedGeneration)
            throw new IllegalStateException("RConcurrentSlot stale generation: slot="
                    + slot + ", expected=" + expectedGeneration + ", current=" + generation);

        int next = (generation + 1) & GENERATION_MASK;
        if (next == 0) next = 1;
        // The successful lease ACTIVE.getAndSet(0) is the single-releaser gate.
        // Publish the new generation before returning the slot via bitmap CAS.
        _generation.lazySet(slot, next);
        for (;;) {
            final long bits = _freeBits.get(word);
            if ((bits & mask) != 0L)
                throw new IllegalStateException("RConcurrentSlot double release: " + slot);
            if (_freeBits.compareAndSet(word, bits, bits | mask)) return;
        }
    }

    public boolean validate(
            int slot,
            int generation) {

        if (slot < 0
                || slot >= _capacity
                || generation <= 0) {

            return false;
        }

        final int word =
                slot >>> 6;

        final long mask =
                1L << (slot & 63);

        return (_freeBits.get(word * BITMAP_WORD_STRIDE) & mask) == 0L
                && _generation.get(slot) == generation;
    }

    public int generation(int slot) {
        checkSlot(slot);
        return _generation.get(slot);
    }

    public int offset(int slot) {
        checkSlot(slot);

        return _baseOffset
                + slot * _slotSize;
    }

    public int slotSize() {
        return _slotSize;
    }

    public int capacity() {
        return _capacity;
    }

    public int freeCount() {
        int free = 0;

        for (int i = 0;
                i < _freeBits.length();
                i += BITMAP_WORD_STRIDE) {

            free +=
                    Long.bitCount(
                            _freeBits.get(i));
        }

        return free;
    }

    public int usedCount() {
        return _capacity
                - freeCount();
    }

    private void checkSlot(int slot) {
        if (slot < 0
                || slot >= _capacity) {

            throw new IndexOutOfBoundsException(
                    "slot=" + slot
                            + ", capacity="
                            + _capacity);
        }
    }
}
