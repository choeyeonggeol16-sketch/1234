"""Exercise production stat application with injected packet failures."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/inventory/CUserEquipment.java").read_text(encoding="utf-8")
a = source.index("private void applyStatBonus(")
b = source.index("private int applyArmorVitalBonus(", a)
method = source[a:b]
harness = r'''
public class EquipmentAudit {
 static class Owner {int str,con,dex,intel,wis,cha;void AddStatBonusStr(byte v){str+=v;}void AddStatBonusCon(byte v){con+=v;}void AddStatBonusDex(byte v){dex+=v;}void AddStatBonusInt(byte v){intel+=v;}void AddStatBonusWis(byte v){wis+=v;}void AddStatBonusCha(byte v){cha+=v;}int[] stat=new int[5];int missile,melee;boolean elf;void add_missile_critical_rate(int n){missile+=n;}void add_melee_critical_rate(int n){melee+=n;}void resetBaseMr(){}boolean isElf(){return elf;}}
 enum ItemStat {BLESS_CONSUMO,MAX_HP,MAX_MP,MR,SP;void apply(Owner p,int n){p.stat[ordinal()]+=n;}}
 static class L1Item {int id,sp;int get_addeinhasadper(){return 1;}int get_addhp(){return 2;}int get_addmp(){return 3;}int get_addstr(){return 4;}int get_addcon(){return 5;}int get_adddex(){return 6;}int get_addint(){return 7;}int get_addwis(){return 8;}int get_addcha(){return 9;}int get_missile_critical_probability(){return 10;}int get_melee_critical_probability(){return 11;}int getItemId(){return id;}int get_addsp(){return sp;}}
 static class L1ItemInstance {int mr;int getMr(){return mr;}}
 record EquipmentTraits(int elfMr) {static EquipmentTraits forItem(int id){return new EquipmentTraits(id==20236?5:0);}}
 static class SC_SPMR {static int mask,calls;static void send(Owner p){if((mask&(1<<calls++))!=0)throw new IllegalStateException("send");}}
 final Owner _owner=new Owner();int failures;void equipmentNoticeFailed(RuntimeException e){failures++;}
'''
checks = r'''
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){int n=0;for(int mr:new int[]{0,4,-3})for(int sp:new int[]{0,2,-2})for(int id:new int[]{1,20236})for(boolean elf:new boolean[]{false,true})for(int mask=0;mask<16;mask++){
  EquipmentAudit a=new EquipmentAudit();L1Item i=new L1Item();i.id=id;i.sp=sp;L1ItemInstance e=new L1ItemInstance();e.mr=mr;a._owner.elf=elf;SC_SPMR.mask=mask;SC_SPMR.calls=0;a.applyStatBonus(e,i,1);int m=mr+(id==20236&&elf?5:0);check(java.util.Arrays.equals(a._owner.stat,new int[]{1,2,3,m,sp}));a.applyStatBonus(e,i,-1);check(java.util.Arrays.equals(a._owner.stat,new int[5]));Owner b=a._owner;check(b.str==0&&b.con==0&&b.dex==0&&b.intel==0&&b.wis==0&&b.cha==0&&a._owner.missile==0&&a._owner.melee==0);check(a.failures==Integer.bitCount(mask&((1<<SC_SPMR.calls)-1)));n++;
 }System.out.println("PASS "+n+" stat-bonus apply/remove cases with send failures");}
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "EquipmentAudit.java"
    java.write_text(harness + method + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-cp", work, "EquipmentAudit"], check=True, timeout=30)
