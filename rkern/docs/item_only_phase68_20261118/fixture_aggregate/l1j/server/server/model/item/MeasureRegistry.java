package l1j.server.server.model.item;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
public class MeasureRegistry {
  private static volatile long result;
  public static void main(String[] args){
    var old = new ItemEffectRegistryBaseline.Def();
    var cur = new ItemEffectRegistryCandidate.Def();
    ItemStat[] stats = {ItemStat.A,ItemStat.B,ItemStat.A,ItemStat.B};
    for(ItemStat stat:stats){old.add(-1,stat,2);cur.add(-1,stat,2);}
    old.buildIndex(); cur.buildIndex();
    for(int i=0;i<100000;i++){
       result+=old.aggregatedStatsFor(ItemEffectRegistryBaseline.TooltipSection.EFFECT,2,0).size();
       result+=cur.aggregatedStatsFor(ItemEffectRegistryCandidate.TooltipSection.EFFECT,2,0).size();
    }
    ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
    long id=Thread.currentThread().threadId();long a=bean.getThreadAllocatedBytes(id);
    for(int i=0;i<200000;i++)result+=old.aggregatedStatsFor(ItemEffectRegistryBaseline.TooltipSection.EFFECT,2,0).size();
    long b=bean.getThreadAllocatedBytes(id);
    for(int i=0;i<200000;i++)result+=cur.aggregatedStatsFor(ItemEffectRegistryCandidate.TooltipSection.EFFECT,2,0).size();
    long c=bean.getThreadAllocatedBytes(id);
    System.out.println("Tooltip bytes/call baseline="+(b-a)/200000+" candidate="+(c-b)/200000+" checksum="+result);
  }
}
