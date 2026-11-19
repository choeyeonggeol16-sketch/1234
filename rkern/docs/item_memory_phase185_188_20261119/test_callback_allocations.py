from pathlib import Path
import subprocess,tempfile
java='''import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.function.Consumer;
public class ErrorCallbackAllocations {
 static volatile Object sink;
 static class Move { Consumer<Throwable> onError; void onError(Consumer<Throwable> c){onError=c;} }
 static class Old {
   final Move move=new Move();
   Old(){move.onError(this::sqlFailed);}
   void sqlFailed(Throwable t) {}
 }
 static class New implements Consumer<Throwable> {
   final Move move=new Move();
   New(){move.onError(this);}
   public void accept(Throwable t) {}
 }
 static long allocated(Runnable run){
   ThreadMXBean b=(ThreadMXBean)ManagementFactory.getThreadMXBean();b.setThreadAllocatedMemoryEnabled(true);
   long t=Thread.currentThread().threadId(),start=b.getThreadAllocatedBytes(t);run.run();
   return b.getThreadAllocatedBytes(t)-start;
 }
 public static void main(String[] args){
   int n=600_000;
   Runnable a=()->{for(int i=0;i<n;i++)sink=new Old();};
   Runnable b=()->{for(int i=0;i<n;i++)sink=new New();};
   a.run();b.run();a.run();b.run();
   long old=allocated(a),optimized=allocated(b);
   System.out.printf("failure-receiver mock: old %.2f new %.2f saved %.2f B/transaction%n",(double)old/n,(double)optimized/n,(double)(old-optimized)/n);
 }
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d,'ErrorCallbackAllocations.java');p.write_text(java)
 x=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if x.returncode:print(x.stdout,x.stderr);raise SystemExit(x.returncode)
 x=subprocess.run(['java','-XX:-DoEscapeAnalysis','-cp',d,'ErrorCallbackAllocations'],capture_output=True,text=True)
 print(x.stdout,x.stderr);raise SystemExit(x.returncode)
