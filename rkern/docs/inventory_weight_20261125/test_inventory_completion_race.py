"""Compile the actual receipt completion methods and force session changes during lock acquisition."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/L1InventoryTransaction.java").read_text(encoding="utf-8")
a = source.index("private void post(Runnable action,Consumer<Throwable> done)")
b = source.index("@Override public void accept(Throwable failure)", a)
methods = source[a:b]
harness = r'''
import java.util.concurrent.*;
import java.util.function.Consumer;
public class CompletionAudit {
 static class L1PcInstance { volatile boolean current=true; }
 static class Owners {
  final L1PcInstance[] pcs; final CountDownLatch entering=new CountDownLatch(1);
  Owners(int n){pcs=new L1PcInstance[n];for(int i=0;i<n;i++)pcs[i]=new L1PcInstance();}
  int size(){return pcs.length;} L1PcInstance get(int i){return pcs[i];}
  int stamp(L1PcInstance p){return 1;} boolean current(L1PcInstance p){return p.current;}
  Object monitor(L1PcInstance p){if(p==pcs[pcs.length-1])entering.countDown();return p;}
 }
 static class CIOThreadCallback {
  static CIOThreadCallback getInstance(){return new CIOThreadCallback();}
  void completeWarehouseMove(L1PcInstance pc,int stamp,Runnable action,Consumer<Throwable> done){try{action.run();done.accept(null);}catch(Throwable e){done.accept(e);}}
 }
 final Owners owners; final L1PcInstance primary;
 CompletionAudit(int n){owners=new Owners(n);primary=owners.get(0);}
'''
checks = r'''
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  int cases=0;
  for(int n=1;n<=8;n++)for(int stale=-1;stale<n;stale++)for(int rep=0;rep<8;rep++){
   var a=new CompletionAudit(n);var called=new java.util.concurrent.atomic.AtomicInteger();var completion=new java.util.concurrent.atomic.AtomicInteger();var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
   Thread th=new Thread(()->a.post(()->{for(var p:a.owners.pcs)check(Thread.holdsLock(p));called.incrementAndGet();},e->{error.set(e);completion.incrementAndGet();}));
   synchronized(a.owners.get(n-1)){th.start();check(a.owners.entering.await(2,TimeUnit.SECONDS));if(stale>=0)a.owners.get(stale).current=false;}
   th.join(2000);check(!th.isAlive()&&completion.get()==1&&called.get()==(stale<0?1:0));check((error.get()==null)==(stale<0));cases++;
  }System.out.println("PASS "+cases+" completion / owner-monitor session race cases");
 }
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "CompletionAudit.java"
    java.write_text(harness + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", work, "CompletionAudit"], check=True, timeout=30)
