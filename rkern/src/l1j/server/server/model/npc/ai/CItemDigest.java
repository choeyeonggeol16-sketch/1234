package l1j.server.server.model.npc.ai;

import java.util.Arrays;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** L1 digest policy, dispatched by the existing NPC object timer. */
public final class CItemDigest {
    public static final int TIMER_DIGEST = 0x44494753;
    private static final long RETRY_MS = 500L;
    private final L1NpcInstance npc;
    private L1ItemInstance[] items;
    private long[] deadlines;
    private int[] counts;
    private int[] floors;
    private int size;
    private int generation;
    private long armedDeadline;

    public CItemDigest(final L1NpcInstance npc) {
        this.npc = npc;
    }

    private boolean live() {
        return !npc.isDestroyed() && !npc.isDead() && !npc.isDeathProcessing()
                && npc.getCurrentHp() > 0;
    }

    /** Legacy callers keep one renewed deadline per item object. */
    public synchronized void Add(final L1ItemInstance item, final int digestSeconds) {
        if (item == null || digestSeconds <= 0 || !live()) return;
        for (int i = size - 1; i >= 0; --i) {
            if (items[i] == item) remove(i);
        }
        Add(item, item.getCount(), digestSeconds);
    }

    /** Record only the committed pickup quantity, not the merged stack. */
    public synchronized void Add(final L1ItemInstance item, final int acquiredCount,
            final int digestSeconds) {
        if (item == null || acquiredCount <= 0 || digestSeconds <= 0 || !live()) return;
        final CInventory inventory = npc.getInventory();
        if (inventory == null) return;
        final int current;
        synchronized (inventory) {
            if (!live() || npc.getInventory() != inventory
                    || inventory.GetByServerID(item.getId()) != item) return;
            current = item.getCount();
        }
        final int count = Math.min(acquiredCount, current);
        if (count <= 0) return;
        // Preserve the pre-existing part of a merged stack. Treat intervening
        // consumption conservatively as consumption of digestible units first.
        long pending = 0L;
        int floor = 0;
        for (int i = 0; i < size; ++i) {
            if (items[i] == item) {
                pending += counts[i];
                floor = Math.max(floor, floors[i]);
            }
        }
        floor = Math.max(floor, (int) Math.max(0L, current - count - pending));
        final long deadline = System.currentTimeMillis() + digestSeconds * 1000L;
        ensureCapacity();
        items[size] = item;
        deadlines[size] = deadline;
        counts[size] = count;
        floors[size] = floor;
        ++size;
        if (armedDeadline == 0L || deadline < armedDeadline) arm(deadline);
    }

    private void ensureCapacity() {
        if (items == null) {
            items = new L1ItemInstance[4];
            deadlines = new long[4];
            counts = new int[4];
            floors = new int[4];
        } else if (size == items.length) {
            final int next = size + (size >>> 1) + 1;
            items = Arrays.copyOf(items, next);
            deadlines = Arrays.copyOf(deadlines, next);
            counts = Arrays.copyOf(counts, next);
            floors = Arrays.copyOf(floors, next);
        }
    }

    private void remove(final int index) {
        final int last = --size;
        if (index != last) {
            items[index] = items[last];
            deadlines[index] = deadlines[last];
            counts[index] = counts[last];
            floors[index] = floors[last];
        }
        items[last] = null;
    }

    private void nextGeneration() {
        if (++generation == 0) ++generation;
    }

    private void arm(final long deadline) {
        nextGeneration();
        armedDeadline = deadline;
        CIOThreadCallback.getInstance().scheduleNpcTimer(npc, TIMER_DIGEST, generation,
                Math.max(1L, deadline - System.currentTimeMillis()));
    }

    private void armNext() {
        if (size == 0) {
            Clear();
            return;
        }
        long next = Long.MAX_VALUE;
        for (int i = 0; i < size; ++i) next = Math.min(next, deadlines[i]);
        arm(next);
    }

    /** Compatibility hook: no inventory mutation or expiry scan from callers. */
    public synchronized void CheckExpired() {
        if (size != 0 && armedDeadline == 0L && live()) armNext();
    }

    /** Invoked on the existing NPC owner, independently of active AI. */
    public synchronized void TimerExpired(final int timerGeneration) {
        if (timerGeneration != generation || armedDeadline == 0L) return;
        armedDeadline = 0L;
        final CInventory inventory = npc.getInventory();
        if (!live() || inventory == null) {
            Clear();
            return;
        }
        final long now = System.currentTimeMillis();
        try {
            for (int i = 0; i < size;) {
                if (deadlines[i] > now) {
                    ++i;
                    continue;
                }
                final L1ItemInstance item = items[i];
                try {
                    synchronized (inventory) {
                        if (live() && npc.getInventory() == inventory
                                && inventory.GetByServerID(item.getId()) == item) {
                            final int count = Math.min(counts[i],
                                    Math.max(0, item.getCount() - floors[i]));
                            if (count > 0) inventory.removeItem(item, count);
                        }
                    }
                } catch (IllegalStateException failure) {
                    if ("Item mutation refused while CacheD transfer pending".equals(failure.getMessage())) {
                        deadlines[i] = now + RETRY_MS;
                        ++i;
                        continue;
                    }
                    // Do not repeat a mutation whose outcome is unknown.
                    remove(i);
                    throw failure;
                } catch (RuntimeException | Error failure) {
                    remove(i);
                    throw failure;
                }
                remove(i);
            }
        } finally {
            if (live() && npc.getInventory() == inventory) armNext();
            else Clear();
        }
    }

    public synchronized void Clear() {
        nextGeneration();
        armedDeadline = 0L;
        size = 0;
        items = null;
        deadlines = null;
        counts = null;
        floors = null;
    }

    public synchronized boolean IsEmpty() {
        return size == 0;
    }
}
