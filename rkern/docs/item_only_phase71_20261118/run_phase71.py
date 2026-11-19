#!/usr/bin/env python3
"""Java 21 A/B comparison using the actual three production synthesis classes and typed interface stubs.
No NPC/network/CacheD implementation is changed; these are verification fixtures only.
"""
from pathlib import Path
import re, tempfile, subprocess, sys
DOC = Path(__file__).resolve().parent
ROOT = DOC.parents[1]
SRC = ROOT/'src/l1j/server/server/model/item/Card'
BASE = DOC/'baseline'
FILES=['L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java']
all_src='\n'.join((SRC/f).read_text(encoding='utf-8') for f in FILES)
GETTERS=sorted(set(re.findall(r'\bacc\.(get\w+)\(',all_src)))
SETTERS=sorted(set(re.findall(r'\bacc\.(set\w+)\(',all_src)))
UPDATES=sorted(set(re.findall(r'\bacc\.(update\w+)\(',all_src)))
if not all(x in GETTERS for x in ['getInfo_card_a','getInfo_card_b','getInfo_card_c','getInfo_card_d']): raise AssertionError('selected-card accessors missing')

def run(cmd):
    r=subprocess.run(cmd,capture_output=True,text=True,timeout=100)
    if r.returncode: raise RuntimeError('COMMAND FAILED '+str(cmd)+'\nstdout:\n'+r.stdout+'\nstderr:\n'+r.stderr)
    return r.stdout.strip()

def write(root, name, src):
    dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(src,encoding='utf-8')

account='''package l1j.server.server;
import java.util.*;
public class Account {
 private final HashMap<String,Integer> values=new HashMap<>();
 public StringBuilder events=new StringBuilder();
 public Account(int seed,int kind) {
   for(String key:new String[]{"getInfo_card_a","getInfo_card_b","getInfo_card_c","getInfo_card_d"}) values.put(key,0);
   int[] ids = kind==0 ? new int[]{101,102} : kind==1 ? new int[]{1,2,3,4} : new int[]{201,202,203,204};
   for(int i=0;i<ids.length;i++) values.put("getInfo_card_"+(char)('a'+i), seed%11==3 ? 0 : ids[i]);
 }
 private int get(String name) {return values.getOrDefault(name, 2 + (Math.abs(name.hashCode()) % 5));}
 private void set(String name,int v){values.put("get"+name.substring(3),v); events.append(name).append('=').append(v).append(';');}
 private void update(String name){events.append(name).append(';');}
'''
account+='\n'.join(f' public int {name}() {{return get("{name}");}}' for name in GETTERS)+'\n'
account+='\n'.join(f' public void {name}(int v) {{set("{name}",v);}}' for name in SETTERS)+'\n'
account+='\n'.join(f' public void {name}() {{update("{name}");}}' for name in UPDATES)+'\n}'

STUBS={
 'l1j/server/server/Account.java':account,
 'l1j/server/Config.java':'''package l1j.server;
public final class Config {
 public static int legend_cost=900000, hero_cost=300000, myth_cost=1200000;
 public static int myth_per=50, myth_per_potion=10;
 public static int legend_per=50, legend_per_potion=10;
 public static int sole_per=50, sole_per_potion=10;
}''',
 'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;
import l1j.server.server.Account;
public class L1PcInstance {
 private final Account account;private final int kind,seed;private final Inventory inventory=new Inventory();
 public boolean 전설합성확률증가,영웅합성확률증가,신화합성확률증가;
 public L1PcInstance(Account a,int kind,int seed){this.account=a;this.kind=kind;this.seed=seed;
   this.전설합성확률증가=seed%2==0;this.영웅합성확률증가=seed%2==0;this.신화합성확률증가=seed%2==0;}
 public Account getAccount(){return account;} public int get_sex(){return seed%3==0?0:1;}
 public int getId(){return 10000+seed;} public boolean isGm(){return seed%11==1;}
 public Inventory getInventory(){return inventory;} public Object getLocation(){return this;}
 public GameMap getMap(){return new GameMap(seed);}
 public class Inventory {
  public boolean checkItem(int id,int count){return seed%11!=4;}
  public boolean consumeItem(int id,int count){account.events.append("consume:").append(id).append(':').append(count).append(';');return true;}
  public Object storeItem(int id,int count){account.events.append("store:").append(id).append(':').append(count).append(';');return null;}
 }
 public static class GameMap {
  private final int seed;GameMap(int seed){this.seed=seed;}
  public boolean isSafetyZone(Object p){return seed%11!=5;}
 }
}''',
 'l1j/server/server/model/network/core/sc/packet/Trace.java':'''package l1j.server.server.model.network.core.sc.packet;
public final class Trace {
 public static boolean ON=true;public static final StringBuilder EVENTS=new StringBuilder(256);
 public static void e(String x){if(ON)EVENTS.append(x).append(';');}
}''',
 'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage {public static void send(L1PcInstance p,String x){Trace.e("sys:"+x);}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ChatPacket {public static void sendDirect(L1PcInstance p,L1PcInstance s,String x,int t){Trace.e("chat:"+t+":"+x);}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_DisplayEffect.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_DisplayEffect {public static void send(L1PcInstance p,int x){Trace.e("display:"+x);}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_NPCTalkReturn.java':'''package l1j.server.server.model.network.core.sc.packet;
import java.util.List;import l1j.server.server.model.Instance.L1PcInstance;
public class SC_NPCTalkReturn {public static void sendList(L1PcInstance p,int id,String html,List<String> x){if(Trace.ON)Trace.e("talk:"+id+":"+html+":"+x);}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_PacketBox {public static final int GREEN_MESSAGE=22;public static void send(L1PcInstance p,int t,String x){Trace.e("box:"+t+":"+x);}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SkillSound {public static void send(L1PcInstance p,int id,int fx){Trace.e("sound:"+fx);}public static void broadcast(L1PcInstance p,int id,int fx){Trace.e("broadcast:"+fx);}}''',
 'l1j/server/server/utils/MessageUtil.java':'''package l1j.server.server.utils;
import l1j.server.server.model.network.core.sc.packet.Trace;
public class MessageUtil {public static void toAllPlayers(String x){Trace.e("all:"+x);} public static void greenMessageToAllPlayers(String x){Trace.e("green:"+x);}}''',
 'l1j/server/server/utils/DelaySender.java':'''package l1j.server.server.utils;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.network.core.sc.packet.Trace;
public class DelaySender {public static void sendGreen(L1PcInstance p,String x,long delay){Trace.e("delay:"+delay+":"+x);}}''',
 'l1j/server/server/ThreadPool.java':'''package l1j.server.server;
public class ThreadPool {private static final ThreadPool INSTANCE=new ThreadPool();public static ThreadPool getInstance(){return INSTANCE;}
 public void scheduleRequired(Runnable r,long delay){r.run();}}''',
}

MAIN=r'''import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Random;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.Trace;
import l1j.server.server.model.item.Card.*;
public final class Compare {
 static final Class<?>[] KINDS={L1CardAlchemy1.class,L1CardAlchemy2.class,L1CardAlchemy3.class};
 static final String[] START={"LegendAlchemy","HeroAlchemy1","MythAlchemy"};
 static volatile Object SINK;
 public static void main(String[] args)throws Exception {
  MessageDigest hash=MessageDigest.getInstance("SHA-256");
  int n=0;
  for(int kind=0;kind<3;kind++){
   Class<?> cls=KINDS[kind];Method start=cls.getDeclaredMethod(START[kind],L1PcInstance.class);
   Field rnd=cls.getDeclaredField("_random");rnd.setAccessible(true);Random random=(Random)rnd.get(null);
   for(int i=0;i<14000;i++){
    Config.myth_per=Config.legend_per=Config.sole_per=(i%5==0?-1:(i%5==1?0:(i%5==2?25:(i%5==3?70:101))));
    Account acc=new Account(i,kind);
    L1PcInstance pc=new L1PcInstance(acc,kind,i);
    Trace.EVENTS.setLength(0);Trace.ON=true;
    random.setSeed((long)i*131L+kind*971L+17L);
    boolean result=(boolean)start.invoke(null,pc);
    String t=kind+":"+i+":"+result+":"+acc.events+":"+Trace.EVENTS+":"+
       pc.전설합성확률증가+":"+pc.영웅합성확률증가+":"+pc.신화합성확률증가;
    hash.update(t.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);n++;
   }
  }
  System.out.println("AB_COUNT="+n+" SHA256="+HexFormat.of().formatHex(hash.digest()));
  // Direct reward selector allocation benchmark: actual private production methods, no packet stubs.
  Trace.ON=false;
  String[] methods={"getMythCard","getLegendCard","getLegendResult","getHeroResult"};
  int[] owner={0,0,1,1};
  ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  long thread=Thread.currentThread().threadId();
  for(int i=0;i<methods.length;i++){
   Class<?> cls=KINDS[owner[i]];
   Method m=i==0?cls.getDeclaredMethod(methods[i],int.class):cls.getDeclaredMethod(methods[i]);
   m.setAccessible(true);
   for(int j=0;j<40000;j++)SINK=call(m,i,j);
   long before=bean.getThreadAllocatedBytes(thread);
   for(int j=0;j<70000;j++)SINK=call(m,i,j);
   long bytes=bean.getThreadAllocatedBytes(thread)-before;
   System.out.printf(java.util.Locale.ROOT,"ALLOC_%s=%.3f%n",methods[i],bytes/70000.0);
  }
 }
 static Object call(Method m,int type,int j)throws Exception {
  if(type==0)return m.invoke(null,j&1);
  return m.invoke(null);
 }
}'''

def run_ab(output,src):
    root=output/'src'
    for name,code in STUBS.items():write(root,name,code)
    write(root,'Compare.java',MAIN)
    java=[str(p) for p in root.rglob('*.java')]+[str(src/name) for name in FILES]
    cls=output/'classes';cls.mkdir()
    run(['javac','-encoding','UTF-8','-source','21','-target','21','-d',str(cls)]+java)
    return run(['java','-cp',str(cls),'Compare'])

with tempfile.TemporaryDirectory(prefix='rkern_item_phase71_') as d:
    w=Path(d)
    old=run_ab(w/'baseline',BASE)
    new=run_ab(w/'optimized',SRC)
    print('BASELINE\n'+old+'\nOPTIMIZED\n'+new)
    assert old.splitlines()[0]==new.splitlines()[0], 'AB mismatch: reward, RNG, packet, account, inventory event log'
    a=dict(x.split('=',1) for x in old.splitlines()[1:]);b=dict(x.split('=',1) for x in new.splitlines()[1:])
    for key in a:
        orig=float(a[key]);updated=float(b[key]);decrease=(orig-updated)/orig*100
        print(key,'allocation reduction',round(decrease,2),'percent')
        assert updated<orig*.50, (key,orig,updated)
    print('PASS 42,000 Java21 end-to-end stub scenarios and allocation reduction for four production selectors')
