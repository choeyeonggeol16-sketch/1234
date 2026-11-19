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
    }

    private static final class Page {
        final Object[] objects = new Object[SLOT_COUNT];
        final int[] generations = new int[SLOT_COUNT];
    }

    private final PageGroup[] groups = new PageGroup[GROUP_COUNT];
    private final AtomicInteger generationSequence = new AtomicInteger(1);
    private final AtomicInteger objectCount = new AtomicInteger();
    private final CollectionView values = new CollectionView();

    // Identity mutations and their CWorld secondary-index updates share this
    // existing ID-page monitor. Unrelated pages never contend.
    Object slotLock(int objectId) {
        return ensurePage(objectId);
    }

    void install(L1Object object) {
        if (object == null) {
            throw new NullPointerException("object");
        }

        final int objectId = object.getId();
        final Page page = ensurePage(objectId);
        final int slot = slotIndex(objectId);

        synchronized (page) {
            final Object current =
                    OBJECT_HANDLE.getAcquire(page.objects, slot);

            if (current == object) {
                final int generation =
                        (int) INT_HANDLE.getAcquire(page.generations, slot);
                if (generation != 0) {
                    return;
                }
            }

            final boolean wasEmpty = current == null;
            
            OBJECT_HANDLE.setRelease(page.objects, slot, null);

            final int generation = nextGeneration();
            INT_HANDLE.setRelease(page.generations, slot, generation);

            OBJECT_HANDLE.setRelease(page.objects, slot, object);

            if (wasEmpty) {
                objectCount.incrementAndGet();
            }
        }
    }

    boolean remove(L1Object object) {
        if (object == null) {
            return false;
        }

        final Page page = pageIfPresent(object.getId());
        if (page == null) {
            return false;
        }

        final int slot = slotIndex(object.getId());

        synchronized (page) {
            final Object current =
                    OBJECT_HANDLE.getAcquire(page.objects, slot);

            if (current != object) {
                return false;
            }

            OBJECT_HANDLE.setRelease(page.objects, slot, null);
            INT_HANDLE.setRelease(page.generations, slot, 0);
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
        if (objectId <= 0) {
            return 0L;
        }

        final Page page = pageIfPresent(objectId);
        if (page == null) {
            return 0L;
        }

        final int slot = slotIndex(objectId);
        final Object first =
                OBJECT_HANDLE.getAcquire(page.objects, slot);

        if (first == null) {
            return 0L;
        }

        final int generation =
                (int) INT_HANDLE.getAcquire(page.generations, slot);

        if (generation == 0
                || first != OBJECT_HANDLE.getAcquire(page.objects, slot)) {
            return 0L;
        }

        return packSmartId(objectId, generation);
    }

    long smartIdOf(L1Object object) {
        if (object == null || object.getId() <= 0) {
            return 0L;
        }

        final int objectId = object.getId();
        final Page page = pageIfPresent(objectId);
        if (page == null) {
            return 0L;
        }

        final int slot = slotIndex(objectId);
        final Object first =
                OBJECT_HANDLE.getAcquire(page.objects, slot);

        if (first != object) {
            return 0L;
        }

        final int generation =
                (int) INT_HANDLE.getAcquire(page.generations, slot);

        if (generation == 0
                || OBJECT_HANDLE.getAcquire(page.objects, slot) != object) {
            return 0L;
        }

        return packSmartId(objectId, generation);
    }

    L1Object findBySmartId(long smartId) {
        if (smartId == 0L) {
            return null;
        }

        final int objectId = (int) smartId;
        final int expectedGeneration = (int) (smartId >>> 32);

        if (objectId <= 0 || expectedGeneration == 0) {
            return null;
        }

        final Page page = pageIfPresent(objectId);
        if (page == null) {
            return null;
        }

        final int slot = slotIndex(objectId);
        final Object first =
                OBJECT_HANDLE.getAcquire(page.objects, slot);

        if (first == null) {
            return null;
        }

        final int generation =
                (int) INT_HANDLE.getAcquire(page.generations, slot);

        if (generation != expectedGeneration) {
            return null;
        }

        final Object second =
                OBJECT_HANDLE.getAcquire(page.objects, slot);

        return first == second ? (L1Object) first : null;
    }

    // Private CWorld ticket identity, including negative inventory IDs.
    // Public SmartID methods intentionally keep their positive-ID contract.
    long worldIdentityOf(int objectId) {
        final Page page = pageIfPresent(objectId);
        if (page == null) return 0L;
        final int slot = slotIndex(objectId);
        final Object first = OBJECT_HANDLE.getAcquire(page.objects, slot);
        if (first == null) return 0L;
        final int generation = (int) INT_HANDLE.getAcquire(page.generations, slot);
        if (generation == 0 || first != OBJECT_HANDLE.getAcquire(page.objects, slot)) {
            return 0L;
        }
        return packSmartId(objectId, generation);
    }

    long worldIdentityOf(L1Object object) {
        if (object == null) return 0L;
        final int objectId = object.getId();
        final Page page = pageIfPresent(objectId);
        if (page == null) return 0L;
        final int slot = slotIndex(objectId);
        if (OBJECT_HANDLE.getAcquire(page.objects, slot) != object) return 0L;
        final int generation = (int) INT_HANDLE.getAcquire(page.generations, slot);
        if (generation == 0 || OBJECT_HANDLE.getAcquire(page.objects, slot) != object) {
            return 0L;
        }
        return packSmartId(objectId, generation);
    }

    L1Object findByWorldIdentity(long identity) {
        if (identity == 0L) return null;
        final int objectId = (int) identity;
        final int expectedGeneration = (int) (identity >>> 32);
        if (expectedGeneration == 0) return null;
        final Page page = pageIfPresent(objectId);
        if (page == null) return null;
        final int slot = slotIndex(objectId);
        final Object first = OBJECT_HANDLE.getAcquire(page.objects, slot);
        if (first == null) return null;
        if ((int) INT_HANDLE.getAcquire(page.generations, slot) != expectedGeneration) {
            return null;
        }
        return first == OBJECT_HANDLE.getAcquire(page.objects, slot)
                ? (L1Object) first : null;
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

    private Page ensurePage(int objectId) {
        final int pageNumber = objectId >>> SLOT_BITS;
        final int groupIndex = pageNumber >>> PAGE_BITS;
        final int pageIndex = pageNumber & PAGE_MASK;

        PageGroup group =
                (PageGroup) GROUP_HANDLE.getAcquire(groups, groupIndex);

        if (group == null) {
            final PageGroup created = new PageGroup();
            if (GROUP_HANDLE.compareAndSet(
                    groups, groupIndex, null, created)) {
                group = created;
            } else {
                group = (PageGroup) GROUP_HANDLE.getAcquire(
                        groups, groupIndex);
            }
        }

        Page page =
                (Page) PAGE_HANDLE.getAcquire(group.pages, pageIndex);

        if (page == null) {
            final Page created = new Page();
            if (PAGE_HANDLE.compareAndSet(
                    group.pages, pageIndex, null, created)) {
                page = created;
            } else {
                page = (Page) PAGE_HANDLE.getAcquire(
                        group.pages, pageIndex);
            }
        }

        return page;
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
