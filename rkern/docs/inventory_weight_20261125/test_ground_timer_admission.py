"""Isolated JDK 21 regression for the production item generation and admission methods."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/Instance/L1ItemInstance.java").read_text(encoding="utf-8")
a = source.index("public void AddTimer(")
b = source.index("public void TimerExpired(", a)
methods = source[a:b].rsplit("/**", 1)[0]
a = source.index("public final boolean isItemTimerCurrent(")
b = source.index("private static long groundDeleteDelayMs()", a)
methods += "\n" + source[a:b]
harness = r'''
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class GroundAdmissionAudit {
    static final int TIMER_GROUND_DELETE = 1;
    private volatile int _groundDeleteTimerGeneration;
    private static final AtomicIntegerFieldUpdater<GroundAdmissionAudit> GROUND_DELETE_GENERATION =
            AtomicIntegerFieldUpdater.newUpdater(GroundAdmissionAudit.class,"_groundDeleteTimerGeneration");
    int getId(){return 100;}
    static class CIOThreadCallback {
        static final CIOThreadCallback INSTANCE = new CIOThreadCallback();
        static CIOThreadCallback getInstance(){return INSTANCE;}
        boolean accept = true; int last;
        boolean scheduleItemTimer(GroundAdmissionAudit item,int timerId,int generation,long delayMs){last=generation;return accept;}
    }
'''
checks = r'''
    static void check(boolean c){if(!c)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        var item = new GroundAdmissionAudit(); var cio=CIOThreadCallback.INSTANCE;
        item.AddTimer(1,1000); int token=cio.last; check(item.isItemTimerCurrent(1,token));
        item.CancelTimer(1); check(!item.isItemTimerCurrent(1,token));
        item.AddTimer(1,1000); check(item.isItemTimerCurrent(1,cio.last));
        cio.accept=false; boolean rejected=false;
        try{item.AddTimer(1,1000);}catch(RejectedExecutionException expected){rejected=true;}
        check(rejected);
        item._groundDeleteTimerGeneration=-1;
        check(item.nextGroundDeleteTimerGeneration()==1);
        check(!item.isItemTimerCurrent(1,0));check(!item.isItemTimerCurrent(2,1));
        Set<Integer> seen=ConcurrentHashMap.newKeySet();
        var pool=Executors.newFixedThreadPool(8); List<Future<?>> jobs=new ArrayList<>();
        for(int k=0;k<8;k++)jobs.add(pool.submit(()->{
            for(int j=0;j<10000;j++)check(seen.add(item.nextGroundDeleteTimerGeneration()));
        }));
        for(var j:jobs)j.get();pool.shutdown();
        check(seen.size()==80000);check(item._groundDeleteTimerGeneration==80001);
        System.out.println("PASS admission rejection, cancellation, wrap and 80000 concurrent generation increments");
    }
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "GroundAdmissionAudit.java"
    java.write_text(harness + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-cp", work, "GroundAdmissionAudit"], check=True, timeout=30)
