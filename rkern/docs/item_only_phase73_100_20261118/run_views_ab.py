from pathlib import Path
import tempfile
import re,subprocess,os,shutil
HERE=Path(__file__).resolve().parent
SRC=HERE.parents[1]/'src/l1j/server/server/model/item/Card'
TMP=tempfile.TemporaryDirectory(prefix='rkern_item_69_')
W=Path(TMP.name)
BASE=HERE/'baseline'
SOURCES=[f'L1Card{n}' for n in ['Step1','Step2','Step3','LegendList','HeroList']]
raw='\n'.join((BASE/f'{n}.java').read_text() for n in SOURCES)
names=sorted(set(re.findall(r'Account::(\w+)',raw)) | {'getmyth01'})
print('Account getter count:',len(names))
JAVAS='''package l1j.server.server;
public class Account {
 private int[] values = new int[NNNN];
 public void setCase(int c) { for (int i=0;i<values.length;i++){int[] x={0,1,2,3,4,127,128,129,255,1024,65535,-1}; values[i]=x[(c*7+i*13+(i/3))%x.length];} }
ZZZZ
}
'''.replace('NNNN',str(len(names))).replace('ZZZZ','\n'.join(' public int '+name+'() { return values['+str(i)+']; }' for i,name in enumerate(names)))
FAKES={
'l1j/server/server/Account.java':JAVAS,
'l1j/server/Config.java':'package l1j.server; public class Config {public static int legend_cost=12345,hero_cost=98765,myth_cost=135790;}',
'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;
import l1j.server.server.Account;
public class L1PcInstance {
 private Account acc; private int sex; private boolean dead;
 public L1PcInstance(Account a,int sex,boolean dead) {this.acc=a;this.sex=sex;this.dead=dead;}
 public Account getAccount(){return acc;} public int getId(){return 123456;} public boolean isDead(){return dead;} public int get_sex(){return sex;}
}
''',
'l1j/server/server/model/network/core/sc/packet/SC_NPCTalkReturn.java':'''package l1j.server.server.model.network.core.sc.packet;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_NPCTalkReturn {
 public static String output;
 public static volatile long sink;
 public static void sendList(L1PcInstance pc,int id,String html,List<String> args) {
   if (System.getProperty("benchmark")!=null) { long h=html.hashCode(); for(String s:args) h = h*31 + s.hashCode(); sink=h; return; }
   StringBuilder sb = new StringBuilder(); sb.append(id).append('|').append(html);
   for(String s:args)sb.append('|').append(s);
   output=sb.toString();
 }
 public static void send(L1PcInstance pc,int id,String html) {output=id+"|"+html;}
}
''',
'l1j/server/server/model/item/Card/CompareCardViews.java':'''package l1j.server.server.model.item.Card;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
public class CompareCardViews {
 static long sink;
 static void perform(int which,L1PcInstance pc){
 switch(which) {
 case 0 -> L1CardStep1.LegendAlchemyF(pc);
 case 1 -> L1CardStep2.LegendAlchemyF(pc);
 case 2 -> L1CardStep3.LegendAlchemyF(pc);
 case 3 -> L1CardLegendList.cardinfo(pc);
 case 4 -> L1CardLegendList.cardlistA(pc);
 case 5 -> L1CardLegendList.cardlistB(pc);
 case 6 -> L1CardLegendList.cardlistC(pc);
 case 7 -> L1CardLegendList.cardlist(pc);
 case 8 -> L1CardHeroList.cardinfo(pc);
 case 9 -> L1CardHeroList.cardlistA(pc);
 case 10 -> L1CardHeroList.cardlistB(pc);
 case 11 -> L1CardHeroList.cardlistC(pc);
 default -> throw new IllegalArgumentException();
 }
 }
 public static void main(String[] argv) {
 boolean benchmark = System.getProperty("benchmark")!=null;
 Account account=new Account();
 if(benchmark) {
  account.setCase(179); L1PcInstance pc=new L1PcInstance(account,1,false);
  for(int t=0;t<37500;t++) perform(t%5,pc);
  ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
  bean.setThreadAllocatedMemoryEnabled(true);
  long thread=Thread.currentThread().getId();
  long before=bean.getThreadAllocatedBytes(thread);
  for(int t=0;t<75000;t++) perform(t%5,pc);
  long after=bean.getThreadAllocatedBytes(thread);
  System.out.println("allocated bytes/call="+(after-before)/75000d+",sink="+SC_NPCTalkReturn.sink);
  return;
 }
 long count=0;
 for(int c=0;c<240;c++) {account.setCase(c);
 for(int sex=0;sex<2;sex++) {
 for(int dead=0;dead<2;dead++) {
  L1PcInstance pc=new L1PcInstance(account,sex,dead!=0);
  for(int which=0;which<12;which++){
   SC_NPCTalkReturn.output="NO_MESSAGE"; perform(which,pc);
   System.out.println(c+","+sex+","+dead+","+which+"="+SC_NPCTalkReturn.output);
   count++;
  }
 }
 }
 }
 SC_NPCTalkReturn.output="NO_MESSAGE";
 for(int which=0;which<12;which++){perform(which,null);System.out.println("null,"+which+"="+SC_NPCTalkReturn.output);}
 System.err.println("cases="+count);
 }
}
'''
}
for version in ('baseline','modified'):
 root=W/('typed_'+version)
 if root.exists():shutil.rmtree(root)
 for path,content in FAKES.items():
  p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 for name in SOURCES:
  p=root/'l1j/server/server/model/item/Card'/f'{name}.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(((BASE if version=='baseline' else SRC)/f'{name}.java').read_bytes())
 sources=[str(x) for x in root.rglob('*.java')]
 cp=subprocess.run(['javac','-encoding','UTF-8','-d',str(root/'classes'),*sources],capture_output=True,text=True)
 if cp.returncode:raise RuntimeError(version+' compile error: '+cp.stderr)
 print(version,'typed compile passed',len(sources),'files')
 cmd=['java','-cp',str(root/'classes'),'l1j.server.server.model.item.Card.CompareCardViews']
 with (W/f'{version}_outputs.txt').open('w') as out:
  cp=subprocess.run(cmd,stdout=out,stderr=subprocess.PIPE,text=True,timeout=60)
 if cp.returncode:raise RuntimeError(cp.stderr)
 print(version,cp.stderr.strip())
 for i in range(2):
  cp=subprocess.run(['java','-Dbenchmark=true','-cp',str(root/'classes'),'l1j.server.server.model.item.Card.CompareCardViews'],capture_output=True,text=True,timeout=60)
  print(version,'allocation trial',i,cp.stdout.strip())
assert (W/'baseline_outputs.txt').read_bytes()==(W/'modified_outputs.txt').read_bytes(), 'output differs'
print('PASS 11532 card-view scenarios exact original output')

TMP.cleanup()
