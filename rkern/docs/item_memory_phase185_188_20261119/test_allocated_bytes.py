from pathlib import Path
import re,subprocess,tempfile
old=(Path(__file__).resolve().parent/'baseline_184_L1InventoryTransaction.java').read_text()
new=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text()
def block(s,prefix):
 a=s.index(prefix);b=s.index('{',a);lvl=1;i=b+1
 while lvl:
  if s[i]=='{':lvl+=1
  elif s[i]=='}':lvl-=1
  i+=1
 return s[a:i]
def storage(s,inline):
 head='''private static final int INDEX_THRESHOLD=9;
'''
 head+=('private Object entries;' if inline else 'private Entry[] entries;')
 head+='private int entryCount;private TIntObjectHashMap<Entry> entryIndex;\n'
 if inline: head+=block(s,'    private Entry entryAt(int index)')+'\n'
 return head+block(s,'    private Entry findEntry(int rowId)')+'\n'+block(s,'    private void addEntry(Entry e)')
java='''import java.util.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
public class TxnAllocationCheck {
 static class Entry {final int rowId; Entry(int n){rowId=n;}}
 static class TIntObjectHashMap<T> {
  HashMap<Integer,T> m;
  TIntObjectHashMap(int cap){m=new HashMap<>(cap);}T get(int k){return m.get(k);}void put(int k,T v){m.put(k,v);}
 }
 static volatile Object sink;
 static class Old { %OLD% }
 static class New { %NEW% }
 static long bytes(Runnable r){
  ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
  bean.setThreadAllocatedMemoryEnabled(true);
  long id=Thread.currentThread().threadId();
  long start=bean.getThreadAllocatedBytes(id);r.run();return bean.getThreadAllocatedBytes(id)-start;
 }
 static int N=600_000;
 static void bench(String which,int count){
  Runnable old=()->{for(int i=0;i<N;i++){
   Old tx=new Old();for(int j=0;j<count;j++)tx.addEntry(new Entry(j+100));sink=tx;
  }};
  Runnable fresh=()->{for(int i=0;i<N;i++){
   New tx=new New();for(int j=0;j<count;j++)tx.addEntry(new Entry(j+100));sink=tx;
  }};
  old.run();fresh.run();old.run();fresh.run();
  long b=bytes(old),a=bytes(fresh);
  System.out.printf("rows=%d old=%.2f new=%.2f saved=%.2f bytes/transaction (JDK21, mock Entry)\\n",count,(double)b/N,(double)a/N,(double)(b-a)/N);
 }
 public static void main(String[]x){for(int c:new int[]{1,2,3,8,9})bench("allocation",c);}
}
'''.replace('%OLD%',storage(old,False)).replace('%NEW%',storage(new,True))
with tempfile.TemporaryDirectory() as d:
 p=Path(d,'TxnAllocationCheck.java');p.write_text(java)
 x=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if x.returncode:print(x.stdout,x.stderr);raise SystemExit(1)
 x=subprocess.run(['java','-XX:-DoEscapeAnalysis','-cp',d,'TxnAllocationCheck'],capture_output=True,text=True)
 print(x.stdout,x.stderr);raise SystemExit(x.returncode)
