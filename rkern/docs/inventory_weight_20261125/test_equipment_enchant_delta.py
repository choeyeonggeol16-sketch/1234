"""Exercise the exact weapon option block and shared delta application with packet faults."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model"
source = (model / "EquipmentEnchantOptions.java").read_text(encoding="utf-8")
a = source.index("    static void applyStat(")
b = source.index("    static int accessoryHeal(", a)
methods = source[a:b]
a = source.index("        if (itemId == 123999) {")
b = source.index("        if (itemId == 61", a)
methods += "\n static void option(L1PcInstance pc,int itemId,int enchant,int sign,int[] changes){\n" + source[a:b] + "\n }\n"
slot = (model / "L1EquipmentSlot.java").read_text(encoding="utf-8")
assert "ItemBonusHooks.onEnchantChanged(_owner, equipment);" in slot
presenter = (model / "item/presentation/ItemPresentatorFactory.java").read_text(encoding="utf-8")
assert "pc.add_melee_critical_rate(enchant * signed);" not in presenter.split("case 123999:")[1]
harness = r'''
import java.util.Arrays;
public class EnchantAudit {
 enum ItemStat { MELEE_CRITICAL_RATE, FEAR_PLERCE, ABILITY_PLERCE, SPIRIT_PLERCE, EVASION, AC, MR, SP; void apply(L1PcInstance p,int v){p.stats[ordinal()]+=v;} }
 static final ItemStat[] STATS=ItemStat.values();
 static final ItemStat MELEE_CRITICAL_RATE=ItemStat.MELEE_CRITICAL_RATE, FEAR_PLERCE=ItemStat.FEAR_PLERCE, ABILITY_PLERCE=ItemStat.ABILITY_PLERCE, SPIRIT_PLERCE=ItemStat.SPIRIT_PLERCE, EVASION=ItemStat.EVASION, AC=ItemStat.AC, MR=ItemStat.MR, SP=ItemStat.SP;
 static class L1PcInstance {int[] stats=new int[STATS.length];int failed; int mask;int getTotalER(){return stats[EVASION.ordinal()];}}
 static class SC_PacketBox {static final int ER_UpDate=0;static void send(L1PcInstance p,int k,int n){if((p.mask&1)!=0)throw new IllegalStateException();}}
 static class SC_OwnCharAttrDef {static void send(L1PcInstance p){if((p.mask&2)!=0)throw new IllegalStateException();}}
 static void noticeFailed(L1PcInstance p,RuntimeException e){p.failed++;}
'''
checks = r'''
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){int n=0;for(int old=-10;old<=20;old++)for(int next=-10;next<=20;next++)for(int mask=0;mask<4;mask++){
   var p=new L1PcInstance(); p.mask=mask;option(p,123999,old,1,null);check(p.stats[0]==old&&p.stats[1]==3+old&&p.stats[2]==3+old&&p.stats[3]==3+old);
   int[] delta=new int[STATS.length];option(p,123999,old,-1,delta);option(p,123999,next,1,delta);delta[EVASION.ordinal()]=2;delta[AC.ordinal()]=-1;delta[MR.ordinal()]=3;
   check(applyDelta(p,delta));check(p.failed==Integer.bitCount(mask));check(p.stats[0]==next&&p.stats[1]==3+next&&p.stats[2]==3+next&&p.stats[3]==3+next);
   option(p,123999,next,-1,null);for(int i=0;i<4;i++)check(p.stats[i]==0);check(p.stats[4]==2&&p.stats[5]==-1&&p.stats[6]==3);n++;
 }System.out.println("PASS "+n+" equip/enchant/unequip + packet-fault cases");}
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "EnchantAudit.java"
    java.write_text(harness + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", work, "EnchantAudit"], check=True, timeout=30)
