package l1j.server.server.model;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Very small int-key map.
 *
 * Intended for per-character collections whose normal cardinality is 0~a few.
 * No hash table, no per-entry Node, and no stored Integer key objects.
 *
 * All operations are synchronized to preserve the cross-thread safety expected
 * from the ConcurrentHashMap instances this replaces.
 */
public final class L1SmallIntMap<V> extends AbstractMap<Integer, V> {

    private static final int[] EMPTY_KEYS = new int[0];
    private static final Object[] EMPTY_VALUES = new Object[0];

    private int[] keys;
    private Object[] values;
    private int size;

    private transient Set<Map.Entry<Integer, V>> entrySet;
    private transient Collection<V> valuesView;

    public L1SmallIntMap() {
        this(0);
    }

    public L1SmallIntMap(int initialCapacity) {
        if (initialCapacity <= 0) {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
        } else {
            keys = new int[initialCapacity];
            values = new Object[initialCapacity];
        }
    }

    public synchronized V put(int key, V value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        final int n = size;
        for (int i = 0; i < n; i++) {
            if (keys[i] == key) {
                @SuppressWarnings("unchecked")
                final V old = (V) values[i];
                values[i] = value;
                return old;
            }
        }

        ensureCapacity(n + 1);
        keys[n] = key;
        values[n] = value;
        size = n + 1;
        return null;
    }

    @Override
    public V put(Integer key, V value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return put(key.intValue(), value);
    }

    public synchronized V get(int key) {
        final int n = size;
        for (int i = 0; i < n; i++) {
            if (keys[i] == key) {
                @SuppressWarnings("unchecked")
                final V value = (V) values[i];
                return value;
            }
        }
        return null;
    }

    @Override
    public synchronized V get(Object key) {
        return key instanceof Integer ? get(((Integer) key).intValue()) : null;
    }

    public synchronized boolean containsKey(int key) {
        final int n = size;
        for (int i = 0; i < n; i++) {
            if (keys[i] == key) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return key instanceof Integer && containsKey(((Integer) key).intValue());
    }

    public synchronized V remove(int key) {
        final int n = size;
        for (int i = 0; i < n; i++) {
            if (keys[i] == key) {
                @SuppressWarnings("unchecked")
                final V old = (V) values[i];
                removeAt(i, n);
                return old;
            }
        }
        return null;
    }

    @Override
    public synchronized V remove(Object key) {
        return key instanceof Integer ? remove(((Integer) key).intValue()) : null;
    }

    public synchronized boolean remove(int key, V expected) {
        final int n = size;
        for (int i = 0; i < n; i++) {
            if (keys[i] == key && values[i] == expected) {
                removeAt(i, n);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        if (!(key instanceof Integer)) {
            return false;
        }

        final int k = ((Integer) key).intValue();
        final int n = size;
        for (int i = 0; i < n; i++) {
            final Object current = values[i];
            if (keys[i] == k && (current == value || (current != null && current.equals(value)))) {
                removeAt(i, n);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized int size() {
        return size;
    }

    @Override
    public synchronized boolean isEmpty() {
        return size == 0;
    }

    @Override
    public synchronized void clear() {
        for (int i = 0; i < size; i++) {
            values[i] = null;
        }
        size = 0;

        if (keys.length > 8) {
            keys = EMPTY_KEYS;
            values = EMPTY_VALUES;
        }
    }

    @Override
    public Collection<V> values() {
        Collection<V> view = valuesView;
        if (view == null) {
            view = new Values();
            valuesView = view;
        }
        return view;
    }

    @Override
    public Set<Map.Entry<Integer, V>> entrySet() {
        Set<Map.Entry<Integer, V>> set = entrySet;
        if (set == null) {
            set = new Entries();
            entrySet = set;
        }
        return set;
    }

    private void ensureCapacity(int needed) {
        final int current = keys.length;
        if (needed <= current) {
            return;
        }

        int next = current == 0 ? 2 : current << 1;
        if (next < needed) {
            next = needed;
        }

        final int[] newKeys = new int[next];
        final Object[] newValues = new Object[next];

        System.arraycopy(keys, 0, newKeys, 0, size);
        System.arraycopy(values, 0, newValues, 0, size);

        keys = newKeys;
        values = newValues;
    }

    private void removeAt(int index, int oldSize) {
        final int last = oldSize - 1;
        if (index != last) {
            keys[index] = keys[last];
            values[index] = values[last];
        }
        values[last] = null;
        size = last;
    }

    private final class Values extends AbstractCollection<V> {
        @Override
        public int size() {
            return L1SmallIntMap.this.size();
        }

        @Override
        public void clear() {
            L1SmallIntMap.this.clear();
        }

        @Override
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
    }

    private final class ValueIterator implements Iterator<V> {
        private int index;

        @Override
        public boolean hasNext() {
            synchronized (L1SmallIntMap.this) {
                return index < size;
            }
        }

        @Override
        public V next() {
            synchronized (L1SmallIntMap.this) {
                if (index >= size) {
                    throw new NoSuchElementException();
                }

                @SuppressWarnings("unchecked")
                final V value = (V) values[index++];
                return value;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class Entries extends AbstractSet<Map.Entry<Integer, V>> {
        @Override
        public int size() {
            return L1SmallIntMap.this.size();
        }

        @Override
        public void clear() {
            L1SmallIntMap.this.clear();
        }

        @Override
        public Iterator<Map.Entry<Integer, V>> iterator() {
            return new EntryIterator();
        }
    }

    private final class EntryIterator implements Iterator<Map.Entry<Integer, V>> {
        private int index;

        @Override
        public boolean hasNext() {
            synchronized (L1SmallIntMap.this) {
                return index < size;
            }
        }

        @Override
        public Map.Entry<Integer, V> next() {
            synchronized (L1SmallIntMap.this) {
                if (index >= size) {
                    throw new NoSuchElementException();
                }

                final int key = keys[index];
                @SuppressWarnings("unchecked")
                final V value = (V) values[index++];
                return new SimpleImmutableEntry<Integer, V>(key, value);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
