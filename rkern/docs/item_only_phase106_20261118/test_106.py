#!/usr/bin/env python3
"""Test the actual three production auto-select classes with Java21 typed stubs.
Preserves ID mapping from Phase105 sources, tests busy gate + all selection cardinalities.
"""
from pathlib import Path
import re,subprocess,sys,tempfile
W=Path(__file__).resolve().parents[2]
prod=W/'src/l1j/server/server/model/item/Card'
old=W/'docs/item_only_phase106_20261118/baseline'
classes=['L1CardLPast','L1CardHPast','L1CardMyPast']
mappings=[]
for cls,lo,k in zip(classes,[101,1,201],[2,4,4]):
 a=(old/(cls+'.java')).read_text(encoding='utf-8')
 b=(prod/(cls+'.java')).read_text(encoding='utf-8')
 if cls=='L1CardMyPast':
  expected=[(getter,int(card)) for card,getter in re.findall(r'checkAndAdd\(eligibleCards, (\d+), acc\.(get\w+)\(\)\);',a)]
 else:
  expected=[(getter,int(card)) for getter,card in re.findall(r'if\s*\(acc\.(get\w+)\(\)\s*>=\s*2\)\s*(?:list|eligible)\.add\((\d+)\);',a)]
 actual=[(getter,int(card)) for getter,card in re.findall(r'if\s*\(acc\.(get\w+)\(\)\s*>=\s*2\)\s*eligible(?:Cards|Indices)\[eligibleCount\+\+\] = (\d+);',b)]
 if cls=='L1CardLPast':
  actual=[(getter,int(card)) for getter,card in re.findall(r'if\s*\(acc\.(get\w+)\(\)\s*>=\s*2\)\s*list\[count\+\+\] = (\d+);',b)]
 if cls=='L1CardHPast':
  actual=[(getter,int(card)) for getter,card in re.findall(r'if\s*\(acc\.(get\w+)\(\)\s*>=\s*2\)\s*eligible\[count\+\+\] = (\d+);',b)]
 assert expected==actual,(cls,len(expected),len(actual))
 assert len(expected)==(23 if cls==classes[0] else 36 if cls==classes[1] else 11)
 assert 'ThreadLocalRandom.current()' in b
 assert 'Collections.shuffle' not in b and 'List<' not in b and 'new ArrayList' not in b
 assert 'pc.isItemTransferBusy()' in b
 assert f'shufflePrefix(eligible' in b
 mappings.append((cls,expected,k))
 print('map parity',cls,len(expected),'card IDs')
# Compile exact production source + minimal Java 21 typed fixture.
with tempfile.TemporaryDirectory(prefix='rk_card106_') as tmp:
 root=Path(tmp);sroot=root/'src'; out=root/'classes'
 def write(path,src):
  p=sroot/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src,encoding='utf-8');return p
 fields=sorted({getter[3:] for _,mapping,_ in mappings for getter,card in mapping})
 account='package l1j.server.server;\npublic class Account {\n'
 account+='\n'.join(f'  private int {field}; public int get{field}(){{return {field};}} public void set{field}(int i){{{field}=i;}}' for field in fields)+'\n'
 account+='\n'.join(f'  private int c{c}; public int getInfo_card_{c}(){{return c{c};}} public void setInfo_card_{c}(int i){{c{c}=i;}}' for c in 'abcd')+'\n}\n'
 sources=[write('l1j/server/server/Account.java',account)]
 sources.append(write('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import l1j.server.server.Account;
public class L1PcInstance {
 public Account account=new Account(); public boolean busy; public int uiCount; public int messages;
 public Account getAccount(){return account;} public boolean isItemTransferBusy(){return busy;}
}
'''))
 sources.append(write('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage { public static void send(L1PcInstance p,String s){p.messages++;}}
'''))
 for ui in 'ABC':
  sources.append(write(f'l1j/server/server/model/item/Card/L1Card{ui}.java',f'''package l1j.server.server.model.item.Card;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1Card{ui} {{ public static void cardlist(L1PcInstance p){{p.uiCount++;}} }}
'''))
 # Tests compile against same Java class signatures production uses.
 test='''package l1j.server.server.model.item.Card;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import java.util.*;
import java.lang.reflect.*;
public class AutoSelect106Test {
 static int tests;
 static void check(boolean b,String why){tests++;if(!b)throw new AssertionError(why);}
 static void select(int tier,L1PcInstance pc){switch(tier){case 0->L1CardLPast.L1PastSelct(pc);case 1->L1CardHPast.L1PastSelct(pc);default->L1CardMyPast.L1PastSelct(pc);}}
 static int[] selection(Account a,int size){return size==2?new int[]{a.getInfo_card_a(),a.getInfo_card_b()}:new int[]{a.getInfo_card_a(),a.getInfo_card_b(),a.getInfo_card_c(),a.getInfo_card_d()};}
 static void seed(Account a,String[] getters,int value)throws Exception{for(String getter:getters)a.getClass().getMethod("set"+getter.substring(3),int.class).invoke(a,value);}
 public static void main(String[] argv)throws Exception{
'''
 for tier,(cls,m,k) in enumerate(mappings):
  getters=[g for g,_ in m]; ids=[x for _,x in m]
  test+=f'''  {{final int tier={tier}; final int needed={k};
   String[] getters=new String[]{{{','.join('"'+x+'"' for x in getters)}}};
   int[] ids=new int[]{{{','.join(str(x) for x in ids)}}};
   // All eligible: every selection must use unique, in-domain IDs, and invoke the matching UI once.
   L1PcInstance pc=new L1PcInstance();seed(pc.account,getters,2);
   for(int i=0;i<1000;i++){{select(tier,pc);int[] picked=selection(pc.account,needed);
    check(pc.uiCount==i+1,"UI once");
    HashSet<Integer> seen=new HashSet<>();for(int card:picked){{seen.add(card);check(card>=ids[0] && card<=ids[ids.length-1],"card range");}}
    check(seen.size()==needed,"distinct cards");
   }}
   // Exactly k eligible: result must be that same set, not merely any in-range value.
   pc=new L1PcInstance();for(int j=0;j<needed;j++){{pc.account.getClass().getMethod("set"+getters[j].substring(3),int.class).invoke(pc.account,2);}}
   select(tier,pc);int[] picked=selection(pc.account,needed);HashSet<Integer> seen=new HashSet<>();
   for(int c:picked)seen.add(c);
   for(int j=0;j<needed;j++)check(seen.contains(ids[j]),"selected eligible only");
   // Too few or only one copy: old selections unchanged; proper error; no UI.
   pc=new L1PcInstance();seed(pc.account,getters,1);pc.account.setInfo_card_a(999);
   select(tier,pc);check(pc.account.getInfo_card_a()==999 && pc.uiCount==0 && pc.messages==1,"insufficient does not mutate");
   // Existing economy receipt gate blocks auto-selection even with all cards eligible.
   pc=new L1PcInstance();seed(pc.account,getters,2);pc.busy=true;pc.account.setInfo_card_a(888);
   select(tier,pc);check(pc.account.getInfo_card_a()==888 && pc.uiCount==0 && pc.messages==0,"busy does not mutate");
   pc=new L1PcInstance();pc.account=null;select(tier,pc);check(pc.uiCount==0,"missing account");
   select(tier,null);
  }}
'''
 test+='  System.out.println("PASS Phase106 real Java card selectors / "+tests+" checks");\n }\n}\n'
 sources.append(write('l1j/server/server/model/item/Card/AutoSelect106Test.java',test))
 sources.extend(prod/(cls+'.java') for cls in classes)
 r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out)]+list(map(str,sources)),capture_output=True,text=True,timeout=70)
 assert r.returncode==0,r.stderr[-4000:]
 r=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.Card.AutoSelect106Test'],capture_output=True,text=True,timeout=40)
 assert r.returncode==0,r.stderr[-4000:]
 print(r.stdout.strip())
# Previous phase deep transaction and selection-control tests, unchanged.
r=subprocess.run([sys.executable,str(W/'docs/item_only_phase105_20261118/test_105.py')],capture_output=True,text=True,timeout=180)
assert r.returncode==0,r.stderr[-3000:]+r.stdout[-3000:]
print('Phase105 + Phase104/103 regressions:',r.stdout[-700:].strip())
