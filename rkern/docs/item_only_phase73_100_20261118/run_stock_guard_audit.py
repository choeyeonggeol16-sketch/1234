#!/usr/bin/env python3
"""Audit regression: selected card IDs must still have spare copies at commit entry.
Uses the production classes, Java 21, and a typed Account/Inventory harness.
"""
from pathlib import Path
import re,runpy,tempfile,subprocess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
base_script=(HERE/'run_normal_ab.py').read_text()
ns={'__file__':str(HERE/'run_normal_ab.py')}
exec(base_script.split('with tempfile.TemporaryDirectory(',1)[0],ns)
STUBS=ns['STUBS']; write=ns['write'];run=ns['run']
CARDS=ROOT/'src/l1j/server/server/model/item/Card'
MAPPING=[]
for cls,base in [('L1CardAlchemy1.java',101),('L1CardAlchemy2.java',1),('L1CardAlchemy3.java',201)]:
 src=(CARDS/cls).read_text()
 mapping={base+int(ix):m for ix,m in re.findall(r'_cardConsumers\[(\d+)\]\s*=\s*acc\s*->\s*acc\.set\w+\(acc\.(get\w+)\(\)\s*-\s*1\)',src)}
 check={int(ix):getter for ix,getter in re.findall(r'case (\d+) -> acc\.(get\w+)\(\);',src)}
 assert mapping==check,(cls,mapping,check)
 MAPPING.append(mapping)
MAIN='''import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.*;
import l1j.server.server.model.network.core.sc.packet.Trace;
import java.lang.reflect.*;
public class CardStockAudit {
 static final int[][] IDS={IDS};
 static final String[][] SETTERS={SETTERS};
 static final Class<?>[] TYPES={L1CardAlchemy1.class,L1CardAlchemy2.class,L1CardAlchemy3.class};
 static final String[] METHODS={"LegendAlchemy","HeroAlchemy1","MythAlchemy"};
 static int checks=0;
 static void requireFailure(int kind, Account acc)throws Exception {
   acc.events.setLength(0);Trace.EVENTS.setLength(0);
   L1PcInstance pc=new L1PcInstance(acc,kind,10);
   boolean result=(boolean)TYPES[kind].getMethod(METHODS[kind],L1PcInstance.class).invoke(null,pc);
   if(result || acc.events.toString().contains("consume:") || acc.events.toString().contains("store:") || acc.events.toString().contains("update"))
     throw new AssertionError("unsafe account debit: kind="+kind+" events="+acc.events+" result="+result);
   checks++;
 }
 public static void main(String[] args)throws Exception {
   for(int kind=0;kind<3;kind++) {
     for(int i=0;i<IDS[kind].length;i++) {
       Account a=new Account(10,kind);
       a.setInfo_card_a(IDS[kind][i]);
       // No accidental duplicate with the other selected slots: test one missing stock entry only.
       int b=a.getInfo_card_b(),c=a.getInfo_card_c(),d=a.getInfo_card_d();
       if(IDS[kind][i]==b||IDS[kind][i]==c||IDS[kind][i]==d)continue;
       Account.class.getMethod(SETTERS[kind][i],int.class).invoke(a,1);
       requireFailure(kind,a);
       Account.class.getMethod(SETTERS[kind][i],int.class).invoke(a,0);
       requireFailure(kind,a);
     }
     Account dupe=new Account(10,kind);
     dupe.setInfo_card_b(dupe.getInfo_card_a());
     requireFailure(kind,dupe);
   }
   System.out.println("PASS: stock count and duplicate selection guards, cases="+checks);
 }
}
'''
MAIN=MAIN.replace('{IDS}','new int[][]{'+','.join('new int[]{'+','.join(map(str,mp))+'}' for mp in MAPPING)+'}',1)
MAIN=MAIN.replace('{SETTERS}','new String[][]{'+','.join('new String[]{'+','.join('"set'+g[3:]+'"' for g in mp.values())+'}' for mp in MAPPING)+'}',1)
# replacements above deliberately only target the placeholder value; no per-test temporary arrays in production.
with tempfile.TemporaryDirectory(prefix='rkern_stock_audit_') as tmp:
 p=Path(tmp);src=p/'src'
 for n,content in STUBS.items():write(src,n,content)
 write(src,'CardStockAudit.java',MAIN)
 sourcefiles=[str(f) for f in src.rglob('*.java')]+[str(CARDS/f) for f in ['L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java']]
 out=p/'classes';out.mkdir()
 run(['javac','-encoding','UTF-8','-d',str(out),'--release','21',*sourcefiles])
 print(run(['java','-cp',str(out),'CardStockAudit']))
