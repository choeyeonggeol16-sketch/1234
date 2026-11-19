#!/usr/bin/env python3
"""Exercise the exact card answer arms extracted from production CS_Attr.java,
using phase103's typed real L1CardAdd and simulated durable receipts.
No production network runtime or MySQL server is substituted for this test.
"""
from pathlib import Path
import re, subprocess, sys
W=Path(__file__).resolve().parents[2]
packet=W/'src/l1j/server/server/model/network/core/cs/packet/CS_Attr.java'
code=packet.read_text(encoding='utf-8')
begin=code.index('case MSGCODE_622_Card2:')
end=code.index('case MSGCODE_622_LFC:',begin)
block=code[begin:end]
expected={
  'MSGCODE_622_Card2': 'L1CardAdd.legend1(pc)',
  'MSGCODE_622_Card3': 'L1CardAdd.myth(pc)',
  'MSGCODE_622_Card': 'L1CardAdd.hero1(pc)',
  'MSGCODE_622_Tree': 'L1CardAdd.SkillTree(pc)',
}
assert len(re.findall(r'\bcase MSGCODE_622_',block))==4
assert block.count('pc.setCollecItem(null)')==4
for marker,call in expected.items():
    assert re.search(rf'case {marker}:\s+if \(c == MSGCODE_YES\) {re.escape(call)};\s+else pc.setCollecItem\(null\);\s+return;', block), marker
for bad in ('removeItem(','.updateLegend(','.updateMyth(','.updateHero(','.updateTree(','.setInfo_col_', '.setTree_'):
    assert bad not in block, bad
assert 'L1CardCollection.Join(pc)' not in block
assert 'import l1j.server.server.model.item.Card.L1CardAdd;' in code

# Build the existing 103 real-source typed fixture and its baseline tests.
r=subprocess.run([sys.executable,str(W/'docs/item_only_phase103_20261118/test_103.py')],capture_output=True,text=True)
print('baseline 103:',r.returncode,r.stdout.strip()[-460:])
assert r.returncode==0, r.stderr[-4000:]+r.stdout[-4000:]
fixture=W/'docs/item_only_phase103_20261118/fixture_work'
src=fixture/'src/l1j/server/server/model/network/core/cs/packet/CSAttrCardOnly104.java'
src.parent.mkdir(parents=True,exist_ok=True)
src.write_text('''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAdd;
public final class CSAttrCardOnly104 {
    public static final int MSGCODE_622_Card2=8, MSGCODE_622_Card3=9, MSGCODE_622_Card=7, MSGCODE_622_Tree=10;
    public static final int MSGCODE_YES=1;
    public static void answer(L1PcInstance pc,int msgIdx,int c) {
      switch(msgIdx) {
'''+block+'''
      default: return;
      }
    }
}
''',encoding='utf-8')

test=fixture/'src/l1j/server/server/model/network/core/cs/packet/CardAnswer104Test.java'
test.write_text('''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.L1InventoryTransaction;
public class CardAnswer104Test {
 static int checked;
 static void ok(boolean x,String m){checked++; if(!x) throw new AssertionError(m);}
 static int value(L1PcInstance p,int code){return switch(code){
 case 8->p.acc.getInfo_ds_poly();case 9->p.acc.getInfo_class_poly();
 case 7->p.acc.getInfo_heaven_poly();case 10->p.acc.getTree_iceq();default->-1;};}
 static int score(L1PcInstance p,int code){return switch(code){
 case 8->p.acc.getInfo_col_l();case 9->p.acc.getInfo_col_m();
 case 7->p.acc.getInfo_col_h();case 10->0;default->-1;};}
 static int id(int code){return switch(code){case 8->875640306;case 9->875640296;case 7->875640315;case 10->875640331;default->0;};}
 public static void main(String[] args){
  for(int code:new int[]{8,9,7,10}){
   // YES: no item removal or account mutation until durable owner completion.
   L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(id(code));p.selected=item;
   CSAttrCardOnly104.answer(p,code,1);
   L1InventoryTransaction tx=L1InventoryTransaction.last;
   ok(p.selected==null,"confirmed pointer must clear");
   ok(tx.item==item && tx.after!=null && tx.pending,"confirmed uses receipt, not direct inventory remove");
   ok(item.count==1 && value(p,code)==0 && score(p,code)==0,"no early mutation");
   tx.finish(true);
   ok(item.count==0 && value(p,code)==1,"successful durable commit");
   ok(score(p,code)==(code==10?0:1),"first registration score");
   ok(p.bonuses>0 && p.messages.size()==1,"post-commit only effects");
   // SQL failure: no debit, account mutation or bonus.
   p=new L1PcInstance();item=new L1ItemInstance(id(code));p.selected=item;
   CSAttrCardOnly104.answer(p,code,1);tx=L1InventoryTransaction.last;tx.finish(false);
   ok(item.count==1 && value(p,code)==0 && score(p,code)==0,"rollback when accounts CAS fails");
   ok(p.bonuses==0 && p.selected==null,"no bonus / clear on SQL failure");
   // Refuse, invalid c, absent pointer: never submit any receipt.
   for(int answer:new int[]{0,2,255}){
    p=new L1PcInstance();p.selected=new L1ItemInstance(id(code));item=p.selected;
    L1InventoryTransaction prior=L1InventoryTransaction.last;
    CSAttrCardOnly104.answer(p,code,answer);
    ok(p.selected==null && item.count==1 && p.bonuses==0,"refusal does not debit");
    ok(L1InventoryTransaction.last==prior,"refusal does not submit receipt");
   }
   p=new L1PcInstance();L1InventoryTransaction prior=L1InventoryTransaction.last;
   CSAttrCardOnly104.answer(p,code,1);
   ok(p.selected==null && L1InventoryTransaction.last==prior,"null selection safe");
   // A malicious wrong-category packet cannot register any selected item.
   p=new L1PcInstance();item=new L1ItemInstance(id(code));p.selected=item;
   int wrong=code==10?8:code==8?9:8;
   prior=L1InventoryTransaction.last;
   CSAttrCardOnly104.answer(p,wrong,1);
   ok(p.selected==null && item.count==1 && L1InventoryTransaction.last==prior,"cross-category rejected");
  }
  // Repeated tree receipt is rejected before any debit.
  L1PcInstance p=new L1PcInstance();p.acc.setTree_iceq(1);
  p.selected=new L1ItemInstance(875640331);
  L1InventoryTransaction prior=L1InventoryTransaction.last;
  CSAttrCardOnly104.answer(p,10,1);
  ok(p.selected==null && p.acc.getTree_iceq()==1 && L1InventoryTransaction.last==prior,"tree duplicate rejected");
  System.out.println("PASS 104: "+checked+" assertions / 4 packet answer codes / SQL failure, cancel, spoof, duplicate");
 }
}
''',encoding='utf-8')
cls=fixture/'classes'
cp=str(cls)
cmd=['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',cp,str(src),str(test)]
r=subprocess.run(cmd,capture_output=True,text=True)
print('packet answer arm compile:',r.returncode, r.stdout[-1000:]+r.stderr[-2000:]);assert r.returncode==0
r=subprocess.run(['java','-cp',cp,'l1j.server.server.model.network.core.cs.packet.CardAnswer104Test'],capture_output=True,text=True)
print('packet reply regression:',r.returncode,r.stdout.strip(),r.stderr[-2000:]);assert r.returncode==0
print('Legacy direct-debit/account-save arms removed, all 103 tests preserved')
