import java.util.Locale;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;

/** Local allocation proxy: not a production-game benchmark. */
public final class FormattingAllocationProbe19 {
    private static volatile String sink;

    private static long run(ThreadMXBean bean, long thread, boolean formatter, int count) {
        final long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < count; i++) {
            int cooldown = (i * 17) % 700;
            if (formatter) {
                sink = String.format("\fY버서크 대기시간 : %d초 후에 사용가능합니다.", cooldown);
            } else {
                sink = "\fY버서크 대기시간 : " + cooldown + "초 후에 사용가능합니다.";
            }
        }
        return bean.getThreadAllocatedBytes(thread) - before;
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.KOREAN);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            System.out.println("Thread allocation measurement unsupported");
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().threadId();
        for (int i=0; i<5; i++) {
            run(bean, tid, true, 10000);
            run(bean, tid, false, 10000);
        }
        final int count = 100000;
        long original = run(bean, tid, true, count);
        long updated = run(bean, tid, false, count);
        System.out.printf(Locale.ROOT, "Korean cooldown format allocation (100,000 calls): old=%d bytes (%.1f B/call), new=%d bytes (%.1f B/call)%n",original,original/(double)count,updated,updated/(double)count);
    }
}
