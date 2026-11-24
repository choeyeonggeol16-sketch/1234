package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;

public class L1HateList {
    // One lazy open-addressed table per list. The monitor protects BOTH arrays
    // and both signed 32-bit metrics; no character/controller lock is acquired.
    private L1Character[] keys;
    private long[] values;
    private int size;

    private static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int first(long pair) { return (int) (pair >> 32); }
    private static int second(long pair) { return (int) pair; }
    private static int metric(long pair, int mode) {
        return mode == 0 ? first(pair) : second(pair);
    }

    private static int hash(L1Character key) {
        final int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    // Returns an occupied matching slot or the first empty slot. At least
    // one quarter of the table is empty, including after removals.
    private static int find(L1Character[] table, L1Character key) {
        final int mask = table.length - 1;
        int slot = hash(key) & mask;
        L1Character found;
        while ((found = table[slot]) != null && found != key && !key.equals(found)) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void resize(int capacity) {
        final L1Character[] newKeys = new L1Character[capacity];
        final long[] newValues = new long[capacity];
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                final L1Character key = keys[i];
                if (key != null) {
                    final int slot = find(newKeys, key);
                    newKeys[slot] = key;
                    newValues[slot] = values[i];
                }
            }
        }
        keys = newKeys;
        values = newValues;
    }

    public void add(L1Character cha, int hate) {
        addHateInfo(cha, hate, 0, true);
    }

    // HateInfoList::AddHateInfo: two independent signed int counters.
    // Repeated updates allocate neither entries nor boxed values.
    public synchronized void addHateInfo(L1Character cha, int firstValue, int secondValue,
            boolean accumulate) {
        addHateInfoInternal(cha, firstValue, secondValue, accumulate, false);
    }

    public synchronized void addDropContribution(L1Character cha, int hate, int damage) {
        addHateInfoInternal(cha, hate, damage, true, true);
    }

    private void addHateInfoInternal(L1Character cha, int firstValue, int secondValue,
            boolean accumulate, boolean saturateDamage) {
        if (cha == null) return;
        if (keys == null) resize(4);
        int slot = find(keys, cha);
        if (keys[slot] != null) {
            final long old = values[slot];
            values[slot] = accumulate
                    ? pack(first(old) + firstValue, saturateDamage
                            ? (int) Math.min(Integer.MAX_VALUE, (long) second(old) + secondValue)
                            : second(old) + secondValue)
                    : pack(firstValue, secondValue);
            return;
        }
        if (size >= keys.length - (keys.length >>> 2)) {
            if (keys.length >= (1 << 30)) throw new OutOfMemoryError("Hate table capacity");
            resize(keys.length << 1);
            slot = find(keys, cha);
        }
        keys[slot] = cha;
        values[slot] = pack(firstValue, secondValue);
        size++;
    }

    public synchronized boolean containsKey(L1Character cha) {
        return cha != null && keys != null && keys[find(keys, cha)] != null;
    }

    // Backward-shift deletion: no tombstones, no retained character references.
    private void removeAt(int hole) {
        final int mask = keys.length - 1;
        for (int scan = (hole + 1) & mask; keys[scan] != null; scan = (scan + 1) & mask) {
            final int home = hash(keys[scan]) & mask;
            if (((hole - home) & mask) < ((scan - home) & mask)) {
                keys[hole] = keys[scan];
                values[hole] = values[scan];
                hole = scan;
            }
        }
        keys[hole] = null;
        values[hole] = 0L;
        size--;
    }

    public synchronized void remove(L1Character cha) {
        if (cha == null || keys == null) return;
        final int slot = find(keys, cha);
        if (keys[slot] != null) removeAt(slot);
    }

    public synchronized void clear() {
        keys = null;
        values = null;
        size = 0;
    }

    public synchronized boolean isEmpty() { return size == 0; }

    // Recheck the current slot after a deletion because a later key may have
    // shifted into it. Run before ranking so every surviving entry is visited once.
    private void purgeDead() {
        for (int i = 0; keys != null && i < keys.length && size != 0;) {
            final L1Character target = keys[i];
            if (target != null && (target.isDead() || target.getCurrentHp() <= 0)) {
                removeAt(i);
            } else {
                i++;
            }
        }
    }

    private synchronized L1Character getExtreme(int mode, boolean maximum) {
        if (mode != 0 && mode != 1) return null;
        purgeDead();
        if (size == 0) return null;
        L1Character result = null;
        int best = 0;
        for (int i = 0; i < keys.length; i++) {
            final L1Character target = keys[i];
            if (target == null) continue;
            final int value = metric(values[i], mode);
            if (result == null || (maximum ? value > best : value < best)) {
                result = target;
                best = value;
            }
        }
        return result;
    }

    public L1Character getMaxHateCharacter(int mode) { return getExtreme(mode, true); }
    public L1Character getMaxHateCharacter() { return getMaxHateCharacter(0); }
    public L1Character getMinHateCharacter(int mode) { return getExtreme(mode, false); }

    public synchronized L1Character getClosestHateCharacter(L1Character origin) {
        if (origin == null) return null;
        purgeDead();
        if (size == 0) return null;
        L1Character result = null;
        long bestDistanceSq = Long.MAX_VALUE;
        final int mapId = origin.getMapId();
        final int originX = origin.getX();
        final int originY = origin.getY();
        for (L1Character target : keys) {
            if (target == null || target == origin || target.getMapId() != mapId) continue;
            final long dx = (long) target.getX() - originX;
            final long dy = (long) target.getY() - originY;
            final long distanceSq = dx * dx + dy * dy;
            if (result == null || distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                result = target;
            }
        }
        return result;
    }

    // Cold ranking path: sort signed metric + slot index, not boxed Map.Entry objects.
    public synchronized L1Character getNthHateCharacter(int mode, int index, boolean ascending) {
        if ((mode != 0 && mode != 1) || index < 0) return null;
        purgeDead();
        if (size == 0) return null;
        final long[] ranks = new long[size];
        int count = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) ranks[count++] = pack(metric(values[i], mode), i);
        }
        Arrays.sort(ranks);
        final int nth = Math.min(index, size - 1);
        return keys[second(ranks[ascending ? nth : size - 1 - nth])];
    }

    public int getHate(L1Character cha) { return getHateMetric(cha, 0); }

    public synchronized int getHateMetric(L1Character cha, int mode) {
        if ((mode != 0 && mode != 1) || cha == null || keys == null) return 0;
        final int slot = find(keys, cha);
        return keys[slot] == null ? 0 : metric(values[slot], mode);
    }

    // Preserve legacy APIs; callers needing paired rewards must use one snapshot.
    public synchronized ArrayList<L1Character> toTargetArrayList() {
        final ArrayList<L1Character> result = new ArrayList<>(size);
        if (size != 0) for (L1Character key : keys) if (key != null) result.add(key);
        return result;
    }

    public synchronized ArrayList<Integer> toHateArrayList() {
        final ArrayList<Integer> result = new ArrayList<>(size);
        if (size != 0) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != null) result.add(first(values[i]));
            }
        }
        return result;
    }

    public void copySnapshotTo(ArrayList<L1Character> targets, ArrayList<Integer> hates) {
        copySnapshotTo(targets, hates, null);
    }

    public synchronized void copySnapshotTo(ArrayList<L1Character> targets, ArrayList<Integer> hates, ArrayList<Integer> damages) {
        targets.clear();
        hates.clear();
        targets.ensureCapacity(size);
        hates.ensureCapacity(size);
        if (damages != null) { damages.clear(); damages.ensureCapacity(size); }
        if (size == 0) return;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) {
                targets.add(keys[i]);
                hates.add(first(values[i]));
                if (damages != null) damages.add(second(values[i]));
            }
        }
    }
}
