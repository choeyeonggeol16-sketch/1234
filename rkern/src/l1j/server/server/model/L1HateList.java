package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class L1HateList {
    // reference server HateInfoList::AddHateInfo (0x0044B39C): two independent int fields
    // at HateInfo +0x10 and +0x14. Keep one mapping per target.
    private final Map<L1Character, Long> _hateMap;

    private static final Comparator<Map.Entry<L1Character, Long>> FIRST_ASC =
            (left, right) -> Integer.compare(
                    first(left.getValue().longValue()),
                    first(right.getValue().longValue()));
    private static final Comparator<Map.Entry<L1Character, Long>> SECOND_ASC =
            (left, right) -> Integer.compare(
                    second(left.getValue().longValue()),
                    second(right.getValue().longValue()));

    public L1HateList() {
        _hateMap = new ConcurrentHashMap<L1Character, Long>();
    }

    private static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int first(long pair) {
        return (int) (pair >> 32);
    }

    private static int second(long pair) {
        return (int) pair;
    }

    // Original L1 API: legacy hate increments affect only the first metric.
    public void add(L1Character cha, int hate) {
        addHateInfo(cha, hate, 0, true);
    }

    // reference server HateInfoList::AddHateInfo (0x0044B39C): independent signed int
    // counters, with add/overwrite selected by the caller.
    public void addHateInfo(L1Character cha, int firstValue, int secondValue,
            boolean accumulate) {
        if (cha == null) return;
        final Long incoming = Long.valueOf(pack(firstValue, secondValue));
        if (!accumulate) {
            _hateMap.put(cha, incoming);
            return;
        }
        // Avoid an allocating capturing lambda on every NPC hate update.
        Long previous = _hateMap.putIfAbsent(cha, incoming);
        while (previous != null) {
            final long pair = previous.longValue();
            final Long next = Long.valueOf(pack(first(pair) + firstValue,
                    second(pair) + secondValue));
            if (_hateMap.replace(cha, previous, next)) return;
            previous = _hateMap.putIfAbsent(cha, incoming);
        }
    }

    public boolean containsKey(L1Character cha) {
        return cha != null && _hateMap.containsKey(cha);
    }

    public void remove(L1Character cha) {
        if (cha != null) _hateMap.remove(cha);
    }

    public void clear() {
        _hateMap.clear();
    }

    public boolean isEmpty() {
        return _hateMap.isEmpty();
    }

    // reference server CNPC::GetMaxHateInfo -> HateInfoList::GetMaxElement(mode):
    // mode 0 compares +0x10, mode 1 compares +0x14.
    public L1Character getMaxHateCharacter(int mode) {
        if (mode != 0 && mode != 1) return null;
        L1Character result = null;
        int max = Integer.MIN_VALUE;
        for (Map.Entry<L1Character, Long> e : _hateMap.entrySet()) {
            final L1Character target = e.getKey();
            final Long value = e.getValue();
            if (target.isDead() || target.getCurrentHp() <= 0) {
                _hateMap.remove(target, value);
                continue;
            }
            final long packed = value.longValue();
            final int metric = mode == 0 ? first(packed) : second(packed);
            if (result == null || metric > max) {
                max = metric;
                result = target;
            }
        }
        return result;
    }

    public L1Character getMaxHateCharacter() {
        return getMaxHateCharacter(0);
    }

    // reference server HateInfoList::GetMinElement(int): minimum of one hate metric.
    // This is NOT GetClosest; the native GetClosest receives FVector.
    public L1Character getMinHateCharacter(int mode) {
        if (mode != 0 && mode != 1) return null;
        L1Character result = null;
        int minimum = Integer.MAX_VALUE;
        for (Map.Entry<L1Character, Long> e : _hateMap.entrySet()) {
            final L1Character target = e.getKey();
            final Long value = e.getValue();
            if (target.isDead() || target.getCurrentHp() <= 0) {
                _hateMap.remove(target, value);
                continue;
            }
            final long packed = value.longValue();
            final int metric = mode == 0 ? first(packed) : second(packed);
            if (result == null || metric < minimum) {
                minimum = metric;
                result = target;
            }
        }
        return result;
    }

    // reference server HateInfoList::GetClosest(const FVector&): position-based selection.
    // The L2 Z/height rules are mapped to the L1 same-map 2D grid.
    // No allocation, sorting, or full distance square root in the scan.
    public L1Character getClosestHateCharacter(L1Character origin) {
        if (origin == null) return null;
        L1Character result = null;
        long bestDistanceSq = Long.MAX_VALUE;
        final int mapId = origin.getMapId();
        final int originX = origin.getX();
        final int originY = origin.getY();
        for (Map.Entry<L1Character, Long> e : _hateMap.entrySet()) {
            final L1Character target = e.getKey();
            final Long value = e.getValue();
            if (target.isDead() || target.getCurrentHp() <= 0) {
                _hateMap.remove(target, value);
                continue;
            }
            if (target == origin || target.getMapId() != mapId) continue;
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

    // reference server HateInfoList::GetNthElement(mode, index, ascending) (0x0044AE58):
    // mode 0 selects +0x10; mode 1 selects +0x14. The native code sorts
    // ascending, then indexes from the front or back according to the flag.
    // The index is clamped to the last available element.
    public L1Character getNthHateCharacter(int mode, int index,
            boolean ascending) {
        if ((mode != 0 && mode != 1) || index < 0) return null;
        final ArrayList<Map.Entry<L1Character, Long>> entries =
                new ArrayList<Map.Entry<L1Character, Long>>(_hateMap.size());
        for (Map.Entry<L1Character, Long> e : _hateMap.entrySet()) {
            final L1Character target = e.getKey();
            final Long value = e.getValue();
            if (target.isDead() || target.getCurrentHp() <= 0) {
                _hateMap.remove(target, value);
            } else {
                entries.add(e);
            }
        }
        if (entries.isEmpty()) return null;
        entries.sort(mode == 0 ? FIRST_ASC : SECOND_ASC);
        final int nth = Math.min(index, entries.size() - 1);
        return entries.get(ascending ? nth : entries.size() - 1 - nth).getKey();
    }

    public int getHate(L1Character cha) {
        final Long value = (cha == null) ? null : _hateMap.get(cha);
        return value == null ? 0 : first(value.longValue());
    }

    public int getHateMetric(L1Character cha, int mode) {
        if (mode != 0 && mode != 1) return 0;
        final Long value = (cha == null) ? null : _hateMap.get(cha);
        if (value == null) return 0;
        final long packed = value.longValue();
        return mode == 0 ? first(packed) : second(packed);
    }

    public ArrayList<L1Character> toTargetArrayList() {
        return new ArrayList<L1Character>(_hateMap.keySet());
    }

    public ArrayList<Integer> toHateArrayList() {
        final ArrayList<Integer> result = new ArrayList<Integer>(_hateMap.size());
        for (Long value : _hateMap.values()) {
            result.add(first(value.longValue()));
        }
        return result;
    }
}
