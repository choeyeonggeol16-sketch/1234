package l1j.server.server.model.item.function;
import java.util.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
public class CompareBoss {
  public static void main(String[] args) {
    Random r = new Random(68202618);
    String[] texts={null,"", " ", " , , ", ",,,", "1,2,3", "2147483647,2147483648,-2147483648", "\u0661,\uFF11,+4", "\u00a0,\u2000", "1,,3", "0,-1,000001,+3", "\u0000+12\t,3", "1 , , 2,", ",1,,", "-0,+0"};
    int checks=0;
    for(String text:texts) {
      if(!Arrays.equals(ClanRaidBossSelectionBaseline.parseBossIds(text),ClanRaidBossSelectionCandidate.parseBossIds(text))) throw new AssertionError("edge: "+text); checks++;
    }
    String chars="0123456789,+-x \r\n\t\u0000\u0020\u00a0\u0665\uFF13";
    for (int i=0;i<150000;i++) {
      StringBuilder b=new StringBuilder(); for(int j=0,n=r.nextInt(100);j<n;j++) b.append(chars.charAt(r.nextInt(chars.length())));
      String text=b.toString();
      int[] old=ClanRaidBossSelectionBaseline.parseBossIds(text), now=ClanRaidBossSelectionCandidate.parseBossIds(text);
      if (!Arrays.equals(old, now)) throw new AssertionError("random "+i+" "+text+" "+Arrays.toString(old)+" != "+Arrays.toString(now));
      checks++;
    }
    String unchanged = "42, 23,404,1,-9, 67, , 18";
    if(ClanRaidBossSelectionCandidate.configuredBossIds(unchanged)!=ClanRaidBossSelectionCandidate.configuredBossIds(new String(unchanged))) throw new AssertionError("cache not reused");
    if(!Arrays.equals(ClanRaidBossSelectionBaseline.configuredBossIds(unchanged),ClanRaidBossSelectionCandidate.configuredBossIds(unchanged)))throw new AssertionError("cache values");
    System.out.println("Boss CSV baseline/candidate comparisons="+checks+" PASS");
    ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long id=Thread.currentThread().threadId();
    String[] samples={"1,2,3,4,5,6,7,8,9,10,11,12", " 1, 2,broken,-1, 42, , 8"};
    for(String sample:samples) {
      for(int j=0;j<50000;j++){ClanRaidBossSelectionBaseline.parseBossIds(sample);ClanRaidBossSelectionCandidate.parseBossIds(sample);}
      long before=bean.getThreadAllocatedBytes(id);
      for(int j=0;j<100000;j++) ClanRaidBossSelectionBaseline.parseBossIds(sample);
      long middle=bean.getThreadAllocatedBytes(id);
      for(int j=0;j<100000;j++) ClanRaidBossSelectionCandidate.parseBossIds(sample);
      long after=bean.getThreadAllocatedBytes(id);
      System.out.println("Boss parse bytes/call: baseline="+(middle-before)/100000+" candidate="+(after-middle)/100000+" sample="+sample);
    }
  }
}
