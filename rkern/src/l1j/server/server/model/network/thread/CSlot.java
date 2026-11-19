package l1j.server.server.model.network.thread;

// NC CThreadLocalTimer::CSlot stores up to 1000 callbacks.
// Allocate backing references lazily, preserving memory at low load.
public final class CSlot {
    CThreadLocalTimer.Node[] entries = new CThreadLocalTimer.Node[16];
    int count;
    CSlot next;

    void add(CThreadLocalTimer.Node node) {
        if (count == entries.length) {
            final int capacity = Math.min(1000, entries.length << 1);
            final CThreadLocalTimer.Node[] expanded = new CThreadLocalTimer.Node[capacity];
            System.arraycopy(entries, 0, expanded, 0, count);
            entries = expanded;
        }
        entries[count++] = node;
    }

    void clear() {
        next = null;
        count = 0;
    }
}
