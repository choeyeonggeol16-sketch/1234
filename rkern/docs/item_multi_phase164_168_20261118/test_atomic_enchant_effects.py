#!/usr/bin/env python3
"""Java21 typed test of production committed-enchant effect path and legacy compatibility."""
from pathlib import Path
import tempfile,subprocess,sys
root=Path(sys.argv[1]).resolve()
script=(root/'docs/item_multi_phase155_158_20261118/check_enchant.py').read_text()
needle=" files=list(r.rglob('*.java'))+[prod]"
assert needle in script
new='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class TestHarness {
 static int cases;static void check(boolean c,String msg){cases++;if(!c)throw new AssertionError(msg+" "+cases);}
 public static void main(String[]args){
   for(int i=0;i<25;i++){
     L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();
     item.data.type2=2;item.data.id=20011;item.data.type=4;
     item.equipped=true;item.lv=2;pc.inv.saveFails=true;pc.inv.packetFailMode=(i%2==0)?1:0;
     EnchantmentOutcomeEffects.SuccessEnchantCommitted(pc,item,null,1);
     check(item.lv==2,"receipt level stays exactly once");
     check(pc.inv.saves==0,"no second db save");
     check(pc.inv.updates==0,"receipt replay already sent item update");
     check(pc.getAC().val==-1,"equipped AC applied once");
     check(pc.getResistance().mr==1,"equipped MR applied once");
   }
   L1PcInstance legacy=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();
   item.data.type2=1;item.lv=1;
   EnchantmentOutcomeEffects.SuccessEnchant(legacy,item,null,1);
   check(item.lv==2,"legacy success unchanged");check(legacy.inv.saves==1,"legacy persists once");
   System.out.println("ENCHANT_COMMITTED_EFFECT_TYPED_CASES="+cases+" PASS");
 }
}'''
insert=" add('l1j/server/server/model/item/function/TestHarness.java',"+repr(new)+")\n"
modified=script.replace(needle,insert+needle,1)
with tempfile.NamedTemporaryFile('w',suffix='.py',encoding='utf8',delete=False) as f:
 f.write(modified);p=Path(f.name)
try:
 c=subprocess.run([sys.executable,str(p),str(root/'src')],capture_output=True,text=True,timeout=60)
 if c.returncode:raise AssertionError(c.stdout+c.stderr)
 print(c.stdout.strip())
finally:p.unlink()
