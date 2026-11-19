package l1j.server.server.storage.cached;

/** State of one pending durable change. Guard all mutations with CacheD's owner lock. */
public abstract class CPendingState {
    public long version;
    public long savedVersion;
    public long dueAt;
    public int failureCount;
    public boolean queued;
    public boolean writing;
    public boolean urgent;
    // CacheD owner-lock-only intrusive FIFO links. Cleared on dequeue.
    public CPendingState readyPrev;
    public CPendingState readyNext;
    public long readyOrder;
    public boolean readyIndexed;
    public int readyFirstOwner;
    public int readySecondOwner;
    public int readyItemId;
}
