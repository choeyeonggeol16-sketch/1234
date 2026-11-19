package l1j.server.server.model.item.Card;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
public class BenchPhase67 {
    static long measure(Runnable fn, int iters) {
        ThreadMXBean b=(ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!b.isThreadAllocatedMemoryEnabled())b.setThreadAllocatedMemoryEnabled(true);
        for(int i=0;i<25000;i++){fn.run();Trace.take();}
        long tid=Thread.currentThread().getId();
        long start=b.getThreadAllocatedBytes(tid);
        for(int i=0;i<iters;i++){fn.run();Trace.take();}
        return (b.getThreadAllocatedBytes(tid)-start)/iters;
    }
    public static void main(String[] args) {
        L1PcInstance p=new L1PcInstance();p.acc=new Account();p.sex=0;
        String[] keys={"getInfo_class_poly","getInfo_ki_poly","getInfo_el_poly","getInfo_wi_poly","getInfo_da_poly","getInfo_wa_poly","getInfo_drag_poly","getInfo_il_poly","getInfo_dsn_poly","getInfo_bapo_poly","getmyth01"};
        for(String k:keys)p.acc.m.put(k,2);
        int n=70000;
        System.out.println("Myth.cardinfo baseline B/call="+measure(()->OldMyth.cardinfo(p),n));
        System.out.println("Myth.cardinfo optimized B/call="+measure(()->NewMyth.cardinfo(p),n));
        System.out.println("Myth.autoSelect baseline B/call="+measure(()->OldPast.L1PastSelct(p),n));
        System.out.println("Myth.autoSelect optimized B/call="+measure(()->NewPast.L1PastSelct(p),n));
    }
}
