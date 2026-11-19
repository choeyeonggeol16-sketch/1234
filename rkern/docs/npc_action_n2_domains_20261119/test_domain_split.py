"""Run: python test_domain_split.py <N0_N1_N2 Entry baseline ZIP> [extracted root].
Checks production bodies and call order; executes actual buy/local functions with typed doubles.
"""
from pathlib import Path
import re, zipfile, sys, subprocess, tempfile
from functools import lru_cache
root=Path(sys.argv[2]) if len(sys.argv)>2 else Path(__file__).resolve().parents[2]
rel='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(rel+'HtmlCmdHandler.java').decode('utf-8').replace('\r\n','\n')
def read(n):return (root/rel/(n+'.java')).read_text()
lex=re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
@lru_cache(maxsize=32)
def mask(s):return lex.sub(lambda m: ''.join('\n' if c=='\n' else ' ' for c in m.group()),s)
def token(s):
 # Ignore only whitespace and comments, keeping literal contents intact.
 out=[]; pos=0
 for m in lex.finditer(s):
  out.append(re.sub(r'\s+','',s[pos:m.start()]))
  if not m.group().startswith(('//','/*')):out.append(m.group())
  pos=m.end()
 out.append(re.sub(r'\s+','',s[pos:]));return ''.join(out)
def endbrace(s,at):
 m=mask(s); depth=0
 for i in range(at,len(s)):
  if m[i]=='{':depth+=1
  if m[i]=='}':
   depth-=1
   if depth==0:return i+1
 raise AssertionError(at)
@lru_cache(maxsize=16)
def methods(s):
 m=mask(s); result=[]
 for x in re.finditer(r'(?m)^[ \t]*(?:private|public|protected)?[ \t]*static\s+(?!final\b)[\w.\[\]<>?]+\s+([\w]+)\s*\(',m):
  a=x.start(); b=m.index('{',x.end()); e=endbrace(s,b);result.append((x.group(1),a,b,e))
 return result
def bodies(s,n):return [s[b:e] for nm,a,b,e in methods(s) if nm==n]
def method(s,n):
 a,b,e=next((a,b,e) for nm,a,b,e in methods(s) if nm==n);return s[a:e]
classes=['NpcLocalCommands','NpcCastleActions','NpcMarketCommands','NpcGameEventActions','NpcQuestTransactions']
cur=read('HtmlCmdHandler'); new={n:read(n) for n in classes}; new['HtmlCmdHandler']=cur
owners={}
for c,s in new.items():
 for n,_,_,_ in methods(s):
  if n in owners:assert owners[n]==c,n
  owners[n]=c
# Normalize only newly introduced qualifiers, not existing statements.
def norm(s):
 for c in new:s=re.sub(r'\b'+re.escape(c)+r'\.', '',s)
 return token(s)
old_names=set(n for n,*_ in methods(old))
moved=0
for n in old_names-{'handle'}:
 c=owners[n]; a=bodies(old,n); b=bodies(new[c],n)
 assert list(map(norm,a))==list(map(norm,b)),n
 if c!='HtmlCmdHandler':moved+=len(a)
print('All',len(methods(old))-1,'legacy helper bodies equivalent; moved',moved)
# Reconstruct entry method exact token order from four lifted blocks.
h=norm(bodies(cur,'handle')[0]); orig=norm(bodies(old,'handle')[0])
def segment(a,b):
 start=old.index(a);return old[start:old.index(b,start)]
buy=segment('\t\t\t\tint npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();\n\t\t\t\tif (pc.getInventory().getWeight100()', '\n\t\t\t} else if (s.equalsIgnoreCase("pssellps")')
tax=segment('            // Castle tax NPC screens:', '\n\t\t\tif (s.equalsIgnoreCase("mayer3"))')
target=segment('\t\t\tif (obj != null) {','\n\t\t\tif(obj.instanceOf(MJL1Type.L1TYPE_NPC))')
registered=segment('\t\t\tif(obj.instanceOf(MJL1Type.L1TYPE_NPC))','\n            // Castle tax NPC screens:')
for clazz,n,block,call,stop in [
 ('NpcMarketCommands','buy',buy,'if (buy(pc,obj,objid)) return;','true'),
 ('NpcCastleActions','sendTaxScreens',tax,'sendTaxScreens(pc,obj,s);',None),
 ('HtmlCmdHandler','dispatchTarget',target,'if (!dispatchTarget(pc,obj,objid,s)) return;','false'),
 ('HtmlCmdHandler','dispatchRegisteredAction',registered,'if (dispatchRegisteredAction(pc,obj,s,actionData)) return;','true')]:
 expected=block
 if stop is not None:
  expected=re.sub(r'\breturn\s*;', 'return '+stop+';', expected)+'\nreturn '+('false' if stop=='true' else 'true')+';'
 if n=='dispatchTarget':expected='L1PcInstance target;\n'+expected
 assert norm(bodies(new[clazz],n)[0])==norm('{'+expected+'}'),n
 assert h.count(norm(call))==1,n
 h=h.replace(norm(call),norm(block),1)
h=h.replace(norm('L1Object obj = L1World.getInstance().findObject(objid);'),norm('L1PcInstance target; L1Object obj = L1World.getInstance().findObject(objid);'),1)
assert h==orig,'main inverse comparison'
print('All four extracted blocks and full main dispatch reverse-token equivalence PASS')
# Actual old/new buy source executed with typed minimal test dependencies.
java=r'''
import java.util.*;
public class NpcBuyTest {
 static class L1Object {}
 static class L1NpcInstance extends L1Object {int id; String name; L1NpcInstance(int i,String n){id=i;name=n;} L1NpcInstance getNpcTemplate(){return this;} int get_npcId(){return id;} String get_name(){return name;}}
 static class L1PcInstance {int weight; boolean top; String trace=""; L1PcInstance(int w,boolean t){weight=w;top=t;} L1PcInstance getInventory(){return this;} int getWeight100(){return weight;} boolean is_top_ranker(){return top;}}
 static class MJDogFightSettings {static final int CASTER_NPC_ID=987654;}
 static class MJDogFightHistory {static MJDogFightHistory getInstance(){return new MJDogFightHistory();} void on_show_ticket_view(L1PcInstance pc,L1NpcInstance n){pc.trace+="dog:"+n.id;}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int n){p.trace+="msg:"+n;}}
 static class SC_PremiumShopSellList {static void send(L1PcInstance p,int n,int o){p.trace+="premium:"+n+":"+o;}}
 static class SC_PERSONAL_SHOP_ITEM_LIST_NOTI {enum ePersonalShopType {TRADE_BUY} static void do_send_for_npc(L1PcInstance p,int o,ePersonalShopType t){p.trace+="personal:"+o+":"+t;}}
 static class SC_ClassShop {static void send(L1PcInstance p,int o){p.trace+="class:"+o;}}
 static class SC_ShopSellList {static void send(int o,L1PcInstance p){p.trace+="normal:"+o;}}
 SELLONLY
 static void oldBuy(L1PcInstance pc,L1Object obj,int objid){
 OLD
 pc.trace+=";fallthrough";
 }
 NEW
 public static void main(String[] args){
  TreeSet<Integer> ids=new TreeSet<>(); for(int i: IDSET){ids.add(i-1);ids.add(i);ids.add(i+1);} ids.add(987654);ids.add(0);ids.add(-1);ids.add(Integer.MAX_VALUE);
  int cases=0; for(int id:ids)for(int weight:new int[]{0,89,90,91,100})for(boolean top:new boolean[]{true,false})for(String name:new String[]{"regular","에덴상단",null}){
   L1NpcInstance n=new L1NpcInstance(id,name);L1PcInstance a=new L1PcInstance(weight,top),b=new L1PcInstance(weight,top);
   oldBuy(a,n,42);if(!buy(b,n,42))b.trace+=";fallthrough";if(!a.trace.equals(b.trace))throw new AssertionError(id+":"+weight+":"+top+":"+name+":"+a.trace+":"+b.trace);cases++;
  } System.out.println("Buy dispatch cases="+cases+" PASS");
 }
}
'''.replace('SELLONLY',method(new['NpcMarketCommands'],'isNpcSellOnly')).replace('OLD',buy).replace('NEW',method(new['NpcMarketCommands'],'buy')).replace('IDSET','new int[]{'+','.join(sorted(set(re.findall(r'npcid\s*(?:==|>=|<=)\s*(\d+)',mask(buy)))))+'}')
# reuse the previous test's typed Java double harness, with current actual moved method.
prior=(root/'docs/npc_action_n0_n1_20261119/test_npc_entry.py').read_text(); harness=prior.split("java=r'''",1)[1].split("'''.replace",1)[0]
old_local=bodies(old,'dispatchLocalCommand')[0][1:-1]
new_local=bodies(new['NpcLocalCommands'],'dispatchLocalCommand')[0][1:-1]
# Both are boolean; adapt old mock to return true/false identically then record fallthrough.
harness=harness.replace('static void legacy(L1PcInstance pc,String s) {','static boolean legacy(L1PcInstance pc,String s) {').replace(' pc.trace.append("fallthrough;");\n }',' }',1).replace('legacy(a,cmd); if(!current(b,cmd))','if(!legacy(a,cmd)) a.trace.append("fallthrough;"); if(!current(b,cmd))').replace('LEGACY',old_local).replace('CURRENT',new_local)
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp)
 (t/'NpcBuyTest.java').write_text(java); (t/'NpcLocalTest.java').write_text(harness)
 subprocess.run(['javac','--release','21',str(t/'NpcBuyTest.java'),str(t/'NpcLocalTest.java')],check=True)
 for n in ('NpcBuyTest','NpcLocalTest'):subprocess.run(['java','-cp',tmp,n],check=True)
