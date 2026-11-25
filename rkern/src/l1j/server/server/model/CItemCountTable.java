package l1j.server.server.model;

import java.io.Serializable;

/** Packed int key/count table. Zero count is absent; no per-entry objects.
 * Monitor protects probes, deletion and resize; no callbacks under the monitor.
 */
final class CItemCountTable implements Serializable {
    private static final long serialVersionUID = 1L;
    private long[] rows;
    private int size;

    private static int slot(long[] a, int key) {
        int h = key * 0x9E3779B9;
        h ^= h >>> 16;
        final int mask = a.length - 1;
        int i = h & mask;
        while (a[i] != 0 && (int) (a[i] >>> 32) != key) i = (i + 1) & mask;
        return i;
    }

    private static long pack(int key, int count) {
        return ((long) key << 32) | (count & 0xffffffffL);
    }

    private int getLocked(int key) {
        return rows == null ? 0 : (int) rows[slot(rows, key)];
    }

    synchronized int getOrDefault(int key, int fallback) {
        final int value = getLocked(key);
        return value == 0 ? fallback : value;
    }

    /** Count equipped instances, not distinct template IDs. */
    synchronized long countAll() {
        long count = 0;
        if (rows != null) {
            for (long row : rows) count += (int) row;
        }
        return count;
    }

    synchronized void put(int key, int count) { putLocked(key, count); }

    synchronized void add(int key, int amount) {
        final long next = (long) getLocked(key) + amount;
        if (amount < 0 || next < 0 || next > CInventory.MAX_AMOUNT)
            throw new IllegalArgumentException("Inventory count overflow: " + key);
        putLocked(key, (int) next);
    }

    synchronized void subtract(int key, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative inventory decrement");
        final int remaining = getLocked(key) - amount;
        putLocked(key, remaining > 0 ? remaining : 0);
    }

    synchronized void remove(int key) { putLocked(key, 0); }

    /** Drop the packed backing storage when its owning inventory is retired. */
    synchronized void clear() {
        rows = null;
        size = 0;
    }

    private void putLocked(int key, int count) {
        if (count < 0 || count > CInventory.MAX_AMOUNT)
            throw new IllegalArgumentException("Invalid inventory count: " + key);
        if (rows == null) {
            if (count == 0) return;
            rows = new long[8];
        }
        int i = slot(rows, key);
        if (rows[i] != 0) {
            if (count == 0) erase(i);
            else rows[i] = pack(key, count);
            return;
        }
        if (count == 0) return;
        if (size >= rows.length / 2) {
            final long[] old = rows;
            final long[] next = new long[old.length << 1];
            for (long entry : old)
                if (entry != 0) next[slot(next, (int) (entry >>> 32))] = entry;
            rows = next;
            i = slot(rows, key);
        }
        rows[i] = pack(key, count);
        size++;
    }

    private void erase(int index) {
        final long[] a = rows;
        final int mask = a.length - 1;
        a[index] = 0;
        size--;
        for (int i = (index + 1) & mask; a[i] != 0; i = (i + 1) & mask) {
            final long entry = a[i];
            a[i] = 0;
            a[slot(a, (int) (entry >>> 32))] = entry;
        }
        if (size == 0) rows = null;
    }
}
