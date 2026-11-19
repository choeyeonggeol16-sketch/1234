import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Allocated bytes for the scroll rejection string only (not a whole-server benchmark). */
public final class AllocationProbe21 {
    private static volatile String sink;
    private static long sample(ThreadMXBean mx, long tid, boolean format, int count) {
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i=0; i<count; i++) {
            int level = (i * 7) % 17;
            if (format) sink = String.format("문장은 +%d 이상 보호주문서로 인챈트할 수 없습니다.",level);
            else sink = "문장은 +" + level + " 이상 보호주문서로 인챈트할 수 없습니다.";
        }
        return mx.getThreadAllocatedBytes(tid) - before;
    }
    public static void main(String[] args) {
        Locale.setDefault(Locale.KOREAN);
        ThreadMXBean mx = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemorySupported()) { System.out.println("Thread allocation measurement unsupported"); return; }
        mx.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().threadId();
        for(int i=0;i<5;i++) {sample(mx,tid,true,10000);sample(mx,tid,false,10000);}
        int count=100000;
        long a=sample(mx,tid,true,count);
        long b=sample(mx,tid,false,count);
        System.out.printf(Locale.ROOT,"21 rejection-message allocation: %d calls, formatter=%d B (%.1f B/call), concat=%d B (%.1f B/call)%n",count,a,a/(double)count,b,b/(double)count);
    }
}
