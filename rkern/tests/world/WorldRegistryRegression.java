package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class WorldRegistryRegression {
    public static void main(String[] args) throws Exception {
        sparseBranches();
        concurrentClears();
        System.out.println("PASS sparse world registry reclamation, stable key locks, unsigned iteration and concurrent clear/count");
    }

    private static void sparseBranches() throws Exception {
        L1IntRegistry<Integer> registry = new L1IntRegistry<>();
        int survivor = 0x100;
        Object monitor = registry.lockForKey(survivor);
        registry.put(survivor, Integer.valueOf(survivor));
        for (int i = 0; i < 1024; i++) {
            int id = (i << 22) | (i << 9) | 23;
            registry.put(id, Integer.valueOf(id));
        }
        int seen = 0;
        int previous = 0;
        for (int id : registry.values()) {
            check(seen == 0 || Integer.compareUnsigned(previous, id) < 0, "unsigned order");
            previous = id;
            seen++;
        }
        check(seen == 1025 && registry.size() == seen, "initial sparse membership");
        for (int i = 0; i < 1024; i++) {
            int id = (i << 22) | (i << 9) | 23;
            check(registry.remove(id) == id, "sparse remove");
        }
        Integer value = registry.get(survivor);
        check(value != null && registry.size() == 1, "neighbor was lost during pruning");
        check(!registry.remove(survivor, -1) && registry.remove(survivor, value), "identity remove");
        check(registry.isEmpty() && !registry.values().iterator().hasNext(), "empty registry");
        // Count reachable data directories without depending on when a JVM runs GC.
        Field tree = L1IntRegistry.class.getDeclaredField("state");
        tree.setAccessible(true);
        Object generation = tree.get(registry);
        Field root = generation.getClass().getDeclaredField("root");
        root.setAccessible(true);
        check(root.get(generation) == null, "empty directories retained");
        registry.put(survivor, value);
        registry.clear();
        check(registry.lockForKey(survivor) == monitor, "key monitor changed across pruning or clear");
    }

    private static void concurrentClears() throws Exception {
        L1IntRegistry<Integer> registry = new L1IntRegistry<>();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] tasks = new Future<?>[5];
            for (int i = 0; i < 4; i++) {
                int offset = i << 8;
                tasks[i] = pool.submit(() -> {
                    start.await();
                    for (int n = 0; n < 16000; n++) {
                        int key = offset + (n & 127);
                        registry.put(key, Integer.valueOf(key));
                        if ((n & 3) == 0) registry.remove(key);
                    }
                    return null;
                });
            }
            tasks[4] = pool.submit(() -> {
                start.await();
                for (int n = 0; n < 4000; n++) registry.clear();
                return null;
            });
            start.countDown();
            for (Future<?> task : tasks) task.get(15, TimeUnit.SECONDS);
            int count = 0;
            for (int key = 0; key < 1024; key++) if (registry.get(key) != null) count++;
            int enumerated = 0;
            for (int key : registry.values()) {
                check(registry.get(key) == key, "unreachable value");
                enumerated++;
            }
            check(count == registry.size() && count == enumerated, "count after clear raced publication");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void check(boolean ok, String detail) {
        if (!ok) throw new AssertionError(detail);
    }
}
