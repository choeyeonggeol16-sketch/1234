from pathlib import Path
import tempfile,subprocess,os,re
D=Path(__file__).resolve().parent
ROOT=D.parents[1]
SRC21=D/'baseline/ItemEffectRegistry.java'
SRC30=ROOT/'src/l1j/server/server/model/item/ItemEffectRegistry.java'
assert SRC21.exists() and SRC30.exists()
STUB_PC='package l1j.server.server.model.Instance; public class L1PcInstance { public final StringBuilder log = new StringBuilder(); }'
STUB_STAT='''package l1j.server.server.model.item;
import l1j.server.server.model.Instance.L1PcInstance;
public enum ItemStat {
 AC(WType.AC), HP(WType.H), HIT(WType.B), HEAL(WType.HEAL), POTION(WType.POTION), SPELL(WType.B);
 public enum WType {AC,H,B,HEAL,POTION};
 public final WType wt;
 ItemStat(WType wt){this.wt=wt;}
 public void apply(L1PcInstance pc,int n) {pc.log.append(name()).append(':').append(n).append(';');}
}
'''
HARNESS='''package l1j.server.server.model.item;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import l1j.server.server.model.Instance.L1PcInstance;
public class Check {
 static void write(MessageDigest md,String text) {md.update(text.getBytes(StandardCharsets.UTF_8));}
 static String hex(byte[] b){StringBuilder x=new StringBuilder();for(byte c:b)x.append(Character.forDigit((c>>4)&15,16)).append(Character.forDigit(c&15,16));return x.toString();}
 public static void main(String[] args) throws Exception {
  final Random rng=new Random(12398765);final MessageDigest md=MessageDigest.getInstance("SHA-256");
  int applied=0; int immutableIndexCases=0; long size=0;
  for(int cfg=0;cfg<1800;cfg++) {
   ItemEffectRegistry.Def d = new ItemEffectRegistry.Def("A",cfg%2==0,"M");
   int count=cfg%23;
   for(int i=0;i<count;i++) {
    int klass = rng.nextInt(18)-7; if(klass < 0) klass = -1; // common or 0..10 invalid
    int enchant = rng.nextInt(8)-1;
    ItemStat stat=ItemStat.values()[rng.nextInt(ItemStat.values().length)];
    d.allBonuses().add(new ItemEffectRegistry.Bonus(klass,enchant,stat,rng.nextInt(200)-100));
   }
   d.buildIndex();
   for(int cls=-2;cls<10;cls++)for(int ench=-2;ench<10;ench++) {
    L1PcInstance pc=new L1PcInstance();d.apply(pc,cls,ench,(cls&1)==0?1:-1);
    write(md,pc.log.toString());
    for(ItemEffectRegistry.TooltipSection sec:ItemEffectRegistry.TooltipSection.values()) {
      write(md,d.aggregatedStatsFor(sec,cls,ench).toString());
    }
    write(md,Integer.toString(d.acLineValue(ench)));
    applied++;
   }
   // buildIndex is idempotent, and later bonuses without rebuilding remain invisible in old and new index.
   if (cfg%43==0) {d.buildIndex();L1PcInstance pc=new L1PcInstance();d.apply(pc,2,4,1);write(md,pc.log.toString());}
  }
  // Separate allocation probe: eight universal-only indexed Def registrations per iteration.
  com.sun.management.ThreadMXBean tb=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  long tid=Thread.currentThread().getId();
  if(tb.isThreadAllocatedMemorySupported() && !tb.isThreadAllocatedMemoryEnabled())tb.setThreadAllocatedMemoryEnabled(true);
  for(int warm=0;warm<18000;warm++) {
   ItemEffectRegistry.Def d = new ItemEffectRegistry.Def();
   d.add( -1,ItemStat.HIT, 2).add(-1,ItemStat.HP,3).addEnchant(5,ItemStat.AC,-1);
   d.buildIndex();size+=d.allBonuses().size();
  }
  long before=tb.getThreadAllocatedBytes(tid);
  int N=70000;
  for(int i=0;i<N;i++) {
   ItemEffectRegistry.Def d = new ItemEffectRegistry.Def();
   d.add(-1,ItemStat.HIT,2).add(-1,ItemStat.HP,3).addEnchant(5,ItemStat.AC,-1);
   d.buildIndex();size+=d.allBonuses().size();
  }
  long after=tb.getThreadAllocatedBytes(tid);
  System.out.println("DIFF count="+applied+" digest="+hex(md.digest()));
  System.out.println("ALLOCATION bytesPerDefinition="+((after-before)/(double)N)+" sink="+size);
 }
}
'''
def run(source):
 with tempfile.TemporaryDirectory(prefix='item30reg_') as d:
  d=Path(d)
  sources={'l1j/server/server/model/item/ItemEffectRegistry.java':source.read_text('utf8'),
    'l1j/server/server/model/item/ItemStat.java':STUB_STAT,
    'l1j/server/server/model/Instance/L1PcInstance.java':STUB_PC,
    'l1j/server/server/model/item/Check.java':HARNESS}
  names=[]
  for key,src in sources.items():
   out=d/'src'/key;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(src,encoding='utf8');names.append(str(out))
  cc=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(d/'bin')]+names,capture_output=True,text=True)
  assert cc.returncode==0,cc.stderr[:12000]
  rr=subprocess.run(['java','-Xmx512m','-cp',str(d/'bin'),'l1j.server.server.model.item.Check'],capture_output=True,text=True)
  assert rr.returncode==0,rr.stderr[:12000]
  return rr.stdout
before=run(SRC21);after=run(SRC30)
a=before.splitlines();b=after.splitlines()
assert a[0]==b[0],(a[0],b[0]);print('PASS registry results',a[0]);print('BASE',a[1]);print('AFTER',b[1])
