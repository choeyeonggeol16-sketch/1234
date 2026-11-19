package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hashless int-key secondary registry.
 *
 * int key -> [8][8][8][8] radix path.
 * Only touched branches/leaves are allocated.
 *
 * Reads are lock-free acquire loads.
 * Writes use a stable striped monitor for each 256-key bucket, allowing
 * empty leaf arrays to be released without changing compound-lock identity.
 */
public final class L1IntRegistry<T> {

    private static final int RADIX = 256;
    private static final int MASK = 0xFF;

    // External compound operations (CWorld first-publication) must keep the
    // same monitor even after an empty leaf has been detached and recreated.
    // Shared fixed stripes avoid one permanent monitor per historical leaf.
    private static final Object[] WRITE_LOCKS = new Object[4096];
    static {
        for (int i = 0; i < WRITE_LOCKS.length; i++) WRITE_LOCKS[i] = new Object();
    }

    private static final VarHandle N1 =
            MethodHandles.arrayElementVarHandle(Node1[].class);
    private static final VarHandle N2 =
            MethodHandles.arrayElementVarHandle(Node2[].class);
    private static final VarHandle LEAF =
            MethodHandles.arrayElementVarHandle(Leaf[].class);
    private static final VarHandle VALUE =
            MethodHandles.arrayElementVarHandle(Object[].class);

    private static final class Node1 {
        final Node2[] next = new Node2[RADIX];
    }

    private static final class Node2 {
        final Leaf[] next = new Leaf[RADIX];
    }

    private static final class Leaf {
        final Object[] values = new Object[RADIX];
        // Only touched while holding this registry/key's stable stripe.
        int live;
    }

    private final Node1[] root = new Node1[RADIX];
    private final AtomicInteger size = new AtomicInteger();
    private final ValuesView values = new ValuesView();

    // The monitor is independent of leaf lifetime: pruning cannot break
    // compound lookup/create operations such as ground-inventory publication.
    Object lockForKey(int key) {
        int hash = (key >>> 8) ^ System.identityHashCode(this);
        hash ^= hash >>> 16;
        return WRITE_LOCKS[hash & (WRITE_LOCKS.length - 1)];
    }

    public T put(int key, T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        synchronized (lockForKey(key)) {
            final Leaf leaf = ensureLeaf(key);
            final int slot = key & MASK;
            @SuppressWarnings("unchecked")
            final T old = (T) VALUE.getAcquire(leaf.values, slot);
            VALUE.setRelease(leaf.values, slot, value);
            if (old == null) {
                leaf.live++;
                size.incrementAndGet();
            }
            return old;
        }
    }

    public T get(int key) {
        final Leaf leaf = leafIfPresent(key);
        if (leaf == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final T value = (T) VALUE.getAcquire(leaf.values, key & MASK);
        return value;
    }

    public boolean containsKey(int key) {
        return get(key) != null;
    }

    public T get(Integer key) {
        return key == null ? null : get(key.intValue());
    }

    public T put(Integer key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return put(key.intValue(), value);
    }

    public boolean containsKey(Integer key) {
        return key != null && containsKey(key.intValue());
    }

    public T remove(Integer key) {
        return key == null ? null : remove(key.intValue());
    }


    public T remove(int key) {
        synchronized (lockForKey(key)) {
            final Leaf leaf = leafIfPresent(key);
            if (leaf == null) return null;
            final int slot = key & MASK;
            @SuppressWarnings("unchecked")
            final T old = (T) VALUE.getAcquire(leaf.values, slot);
            if (old == null) return null;
            VALUE.setRelease(leaf.values, slot, null);
            leaf.live--;
            size.decrementAndGet();
            if (leaf.live == 0) detachEmptyLeaf(key, leaf);
            return old;
        }
    }

    public boolean remove(int key, T expected) {
        if (expected == null) {
            return false;
        }

        synchronized (lockForKey(key)) {
            final Leaf leaf = leafIfPresent(key);
            if (leaf == null) return false;
            final int slot = key & MASK;
            if (VALUE.getAcquire(leaf.values, slot) != expected) return false;
            VALUE.setRelease(leaf.values, slot, null);
            leaf.live--;
            size.decrementAndGet();
            if (leaf.live == 0) detachEmptyLeaf(key, leaf);
            return true;
        }
    }

    public int size() {
        return size.get();
    }

    public boolean isEmpty() {
        return size.get() == 0;
    }

    public Collection<T> values() {
        return values;
    }

    public void clear() {
        synchronized (root) {
            for (int i = 0; i < RADIX; i++) {
                N1.setRelease(root, i, null);
            }
            size.set(0);
        }
    }

    private void detachEmptyLeaf(int key, Leaf leaf) {
        // All writers to this 256-key leaf share the same stable monitor.
        // Leave parent nodes installed: removing those would require a second
        // coordination protocol with writers in other leaf stripes.
        final Node1 n1 = (Node1) N1.getAcquire(root, key >>> 24);
        if (n1 == null) return;
        final Node2 n2 = (Node2) N2.getAcquire(n1.next, (key >>> 16) & MASK);
        if (n2 != null) LEAF.compareAndSet(n2.next, (key >>> 8) & MASK, leaf, null);
    }

    private Leaf ensureLeaf(int key) {
        final int a = key >>> 24;
        final int b = (key >>> 16) & MASK;
        final int c = (key >>> 8) & MASK;

        Node1 n1 = (Node1) N1.getAcquire(root, a);
        if (n1 == null) {
            final Node1 created = new Node1();
            if (N1.compareAndSet(root, a, null, created)) {
                n1 = created;
            } else {
                n1 = (Node1) N1.getAcquire(root, a);
            }
        }

        Node2 n2 = (Node2) N2.getAcquire(n1.next, b);
        if (n2 == null) {
            final Node2 created = new Node2();
            if (N2.compareAndSet(n1.next, b, null, created)) {
                n2 = created;
            } else {
                n2 = (Node2) N2.getAcquire(n1.next, b);
            }
        }

        Leaf leaf = (Leaf) LEAF.getAcquire(n2.next, c);
        if (leaf == null) {
            final Leaf created = new Leaf();
            if (LEAF.compareAndSet(n2.next, c, null, created)) {
                leaf = created;
            } else {
                leaf = (Leaf) LEAF.getAcquire(n2.next, c);
            }
        }

        return leaf;
    }

    private Leaf leafIfPresent(int key) {
        final Node1 n1 = (Node1) N1.getAcquire(root, key >>> 24);
        if (n1 == null) {
            return null;
        }

        final Node2 n2 = (Node2) N2.getAcquire(
                n1.next, (key >>> 16) & MASK);
        if (n2 == null) {
            return null;
        }

        return (Leaf) LEAF.getAcquire(
                n2.next, (key >>> 8) & MASK);
    }

    private final class ValuesView extends AbstractCollection<T> {
        @Override
        public Iterator<T> iterator() {
            return new ValuesIterator();
        }

        @Override
        public int size() {
            return L1IntRegistry.this.size();
        }
    }

    private final class ValuesIterator implements Iterator<T> {
        private int a, b, c, d;
        private Node1 n1;
        private Node2 n2;
        private Leaf leaf;
        private T next;

        ValuesIterator() {
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            final T value = next;
            if (value == null) {
                throw new NoSuchElementException();
            }
            advance();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            next = null;

            for (;;) {
                if (leaf != null) {
                    while (d < RADIX) {
                        @SuppressWarnings("unchecked")
                        final T v = (T) VALUE.getAcquire(leaf.values, d++);
                        if (v != null) {
                            next = v;
                            return;
                        }
                    }
                    leaf = null;
                    d = 0;
                }

                if (n2 != null) {
                    while (c < RADIX) {
                        final Leaf v = (Leaf) LEAF.getAcquire(n2.next, c++);
                        if (v != null) {
                            leaf = v;
                            break;
                        }
                    }
                    if (leaf != null) {
                        continue;
                    }
                    n2 = null;
                    c = 0;
                }

                if (n1 != null) {
                    while (b < RADIX) {
                        final Node2 v = (Node2) N2.getAcquire(n1.next, b++);
                        if (v != null) {
                            n2 = v;
                            break;
                        }
                    }
                    if (n2 != null) {
                        continue;
                    }
                    n1 = null;
                    b = 0;
                }

                while (a < RADIX) {
                    final Node1 v = (Node1) N1.getAcquire(root, a++);
                    if (v != null) {
                        n1 = v;
                        break;
                    }
                }

                if (n1 == null) {
                    return;
                }
            }
        }
    }
}
