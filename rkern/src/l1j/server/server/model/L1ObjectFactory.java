package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;


final class L1ObjectFactory {

    private static final int SLOT_BITS = 12;
    private static final int PAGE_BITS = 10;

    private static final int SLOT_COUNT = 1 << SLOT_BITS;
    private static final int PAGE_COUNT = 1 << PAGE_BITS;
    private static final int SLOT_MASK = SLOT_COUNT - 1;
    private static final int PAGE_MASK = PAGE_COUNT - 1;
    private static final int GROUP_COUNT =
            1 << (32 - SLOT_BITS - PAGE_BITS);

    private static final VarHandle GROUP_HANDLE =
            MethodHandles.arrayElementVarHandle(PageGroup[].class);
    private static final VarHandle PAGE_HANDLE =
            MethodHandles.arrayElementVarHandle(Page[].class);
    private static final VarHandle OBJECT_HANDLE =
            MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle INT_HANDLE =
            MethodHandles.arrayElementVarHandle(int[].class);

    private static final class PageGroup {
        final Page[] pages = new Page[PAGE_COUNT];
        int pageCount;
    }

    private static final class Page {
        final Object[] objects = new Object[SLOT_COUNT];
        final int[] generations = new int[SLOT_COUNT];
        int size;
    }

    // A compound world mutation must keep its lock when its last slot disappears.
    private final Object[] slotLocks = locks(256);
    private final Object[] groupLocks = locks(32);
    private final PageGroup[] groups = new PageGroup[GROUP_COUNT];
    private final AtomicInteger generationSequence = new AtomicInteger(1);
    private final AtomicInteger objectCount = new AtomicInteger();
    private final CollectionView values = new CollectionView();

    private static Object[] locks(int count) {
        final Object[] locks = new Object[count];
        for (int i = 0; i < count; i++) locks[i] = new Object();
        return locks;
    }

    Object slotLock(int objectId) {
        final int page = objectId >>> SLOT_BITS;
        return slotLocks[(page ^ (page >>> 8) ^ (page >>> 16)) & (slotLocks.length - 1)];
    }

    void install(L1Object object) {
        if (object == null) throw new NullPointerException("object");
        final int id = object.getId();
        final int slot = slotIndex(id);
        synchronized (slotLock(id)) {
            Page page = pageIfPresent(id);
            if (page == null) page = createPage(id);
            final Object previous = OBJECT_HANDLE.getAcquire(page.objects, slot);
            if (previous == object) return;

            // Readers use the generation on both sides of the object read.
            OBJECT_HANDLE.setRelease(page.objects, slot, null);
            INT_HANDLE.setRelease(page.generations, slot, nextGeneration());
            OBJECT_HANDLE.setRelease(page.objects, slot, object);
            if (previous == null) {
                page.size++;
                objectCount.incrementAndGet();
            }
        }
    }

    boolean remove(L1Object object) {
        if (object == null) return false;
        final int id = object.getId();
        final int slot = slotIndex(id);
        synchronized (slotLock(id)) {
            final Page page = pageIfPresent(id);
            if (page == null || OBJECT_HANDLE.getAcquire(page.objects, slot) != object) return false;
            OBJECT_HANDLE.setRelease(page.objects, slot, null);
            INT_HANDLE.setRelease(page.generations, slot, 0);
            if (--page.size == 0) dropPage(id);
            objectCount.decrementAndGet();
            return true;
        }
    }

    L1Object find(int objectId) {
        final Page page = pageIfPresent(objectId);
        if (page == null) {
            return null;
        }

        return (L1Object) OBJECT_HANDLE.getAcquire(
                page.objects,
                slotIndex(objectId));
    }

    long smartIdOf(int objectId) {
        return objectId > 0 ? worldIdentityOf(objectId) : 0L;
    }

    long smartIdOf(L1Object object) {
        return object != null && object.getId() > 0 ? worldIdentityOf(object) : 0L;
    }

    L1Object findBySmartId(long identity) {
        return (int) identity > 0 ? findByWorldIdentity(identity) : null;
    }

    // World tickets also support signed inventory IDs, while public SmartIDs require positive IDs.
    long worldIdentityOf(int id) {
        return identityOf(id, null);
    }

    long worldIdentityOf(L1Object object) {
        return object == null ? 0L : identityOf(object.getId(), object);
    }

    private long identityOf(int id, L1Object expected) {
        final Page page = pageIfPresent(id);
        if (page == null) return 0L;
        final int slot = slotIndex(id);
        final int generation = (int) INT_HANDLE.getAcquire(page.generations, slot);
        if (generation == 0) return 0L;
        final Object current = OBJECT_HANDLE.getAcquire(page.objects, slot);
        if (current == null || (expected != null && current != expected)
                || (int) INT_HANDLE.getAcquire(page.generations, slot) != generation) return 0L;
        return packSmartId(id, generation);
    }

    L1Object findByWorldIdentity(long identity) {
        final int generation = (int) (identity >>> 32);
        if (generation == 0) return null;
        final int id = (int) identity;
        final Page page = pageIfPresent(id);
        if (page == null) return null;
        final int slot = slotIndex(id);
        if ((int) INT_HANDLE.getAcquire(page.generations, slot) != generation) return null;
        final Object current = OBJECT_HANDLE.getAcquire(page.objects, slot);
        return (int) INT_HANDLE.getAcquire(page.generations, slot) == generation ? (L1Object) current : null;
    }

    int size() {
        return objectCount.get();
    }

    Collection<L1Object> values() {
        return values;
    }

    private int nextGeneration() {
        int generation = generationSequence.incrementAndGet();
        if (generation == 0) {
            generation = generationSequence.incrementAndGet();
        }
        return generation;
    }

    // The caller owns the stable page stripe. Group locks protect only page table membership.
    private Page createPage(int id) {
        final int number = id >>> SLOT_BITS;
        final int groupIndex = number >>> PAGE_BITS;
        final Page page = new Page();
        synchronized (groupLocks[groupIndex & (groupLocks.length - 1)]) {
            PageGroup group = (PageGroup) GROUP_HANDLE.getAcquire(groups, groupIndex);
            if (group == null) {
                group = new PageGroup();
                GROUP_HANDLE.setRelease(groups, groupIndex, group);
            }
            PAGE_HANDLE.setRelease(group.pages, number & PAGE_MASK, page);
            group.pageCount++;
        }
        return page;
    }

    private void dropPage(int id) {
        final int number = id >>> SLOT_BITS;
        final int groupIndex = number >>> PAGE_BITS;
        synchronized (groupLocks[groupIndex & (groupLocks.length - 1)]) {
            final PageGroup group = (PageGroup) GROUP_HANDLE.getAcquire(groups, groupIndex);
            PAGE_HANDLE.setRelease(group.pages, number & PAGE_MASK, null);
            if (--group.pageCount == 0) GROUP_HANDLE.setRelease(groups, groupIndex, null);
        }
    }

    private Page pageIfPresent(int objectId) {
        final int pageNumber = objectId >>> SLOT_BITS;

        final PageGroup group =
                (PageGroup) GROUP_HANDLE.getAcquire(
                        groups,
                        pageNumber >>> PAGE_BITS);

        if (group == null) {
            return null;
        }

        return (Page) PAGE_HANDLE.getAcquire(
                group.pages,
                pageNumber & PAGE_MASK);
    }

    private static int slotIndex(int objectId) {
        return objectId & SLOT_MASK;
    }

    private static long packSmartId(int objectId, int generation) {
        return (Integer.toUnsignedLong(generation) << 32)
                | Integer.toUnsignedLong(objectId);
    }

    private final class CollectionView extends AbstractCollection<L1Object> {
        @Override
        public Iterator<L1Object> iterator() {
            return new ValueIterator();
        }

        @Override
        public int size() {
            return L1ObjectFactory.this.size();
        }
    }

    private final class ValueIterator implements Iterator<L1Object> {
        private int groupIndex;
        private int pageIndex;
        private int slotIndex;
        private Page currentPage;
        private L1Object next;

        ValueIterator() {
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public L1Object next() {
            final L1Object value = next;
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
                if (currentPage != null) {
                    while (slotIndex < SLOT_COUNT) {
                        final Object value =
                                OBJECT_HANDLE.getAcquire(
                                        currentPage.objects,
                                        slotIndex++);

                        if (value instanceof L1Object) {
                            next = (L1Object) value;
                            return;
                        }
                    }

                    currentPage = null;
                    slotIndex = 0;
                }

                while (groupIndex < GROUP_COUNT) {
                    final PageGroup group =
                            (PageGroup) GROUP_HANDLE.getAcquire(
                                    groups,
                                    groupIndex);

                    if (group == null) {
                        groupIndex++;
                        pageIndex = 0;
                        continue;
                    }

                    while (pageIndex < PAGE_COUNT) {
                        final Page page =
                                (Page) PAGE_HANDLE.getAcquire(
                                        group.pages,
                                        pageIndex++);

                        if (page != null) {
                            currentPage = page;
                            slotIndex = 0;
                            break;
                        }
                    }

                    if (currentPage != null) {
                        break;
                    }

                    groupIndex++;
                    pageIndex = 0;
                }

                if (currentPage == null) {
                    return;
                }
            }
        }
    }
}
