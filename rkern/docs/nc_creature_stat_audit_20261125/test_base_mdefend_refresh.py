"""Extract production MR recalculation and WIS mutation paths; compare with direct L1 formula."""
from pathlib import Path
import re, tempfile, subprocess
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
creature=(model/"L1Character.java").read_text(encoding="utf-8")
pc=(model/"Instance/L1PcInstance.java").read_text(encoding="utf-8")
calc=(root/"src/l1j/server/server/utils/CalcStat.java").read_text(encoding="utf-8")
def method(src, signature):
    start=src.index(signature)
    end=src.index("{",start)+1
    depth=1
    while depth:
        depth+=(src[end]=="{")-(src[end]=="}")
        end+=1
    return src[start:end]
fields=creature[creature.index("// CStats: original,"):creature.index("// BEGIN shared resistance sources")]
methods=creature[creature.index("// CStats lane operations."):creature.index("// BEGIN direct primary-stat operations")]
methods+="\n".join(method(creature,s) for s in (
    "public synchronized void setLevel(", "public final void SetSkillPrimaryStats(",
    "public final void ResetPrimaryStats(", "public final void SetOrgMDefend(",
    "public final int GetOrgMDefend(", "public final void AddMDefend(",
    "public final int GetMDefend()", "private int CheckMDefendRange(", "public final void SetSkillMDefend("))
# GetMDefend reads the passive bit; fixture supplies the same state shape.
res=creature.split("// BEGIN shared resistance sources (CCreature)",1)[1].split("// END shared resistance sources",1)[0]
pcmethods="\n".join(method(pc,s) for s in ("public final void RefreshBaseMDefend(", "public void resetBaseMr()", "public void setType("))
assert "RefreshBaseMDefend();" in pcmethods
# Use actual CalcStat base table and formula.
base=re.search(r"(?:private|public)\s+static\s+final\s+int\[\]\s+BASE_MR\s*=\s*\{[^}]+\};",calc).group(0)
calcMethods=method(calc,"public static int 마법방어(")
stub="""
class L1Character {
  int _level=1; int getLevel(){return _level;} boolean passive;
  boolean isPassive(int id){return passive;}
  void NotifyStatBonusStr(){} void NotifyStatBonusDex(){} void NotifyStatBonusInt(){} void NotifyStatBonusCon(){} void NotifyStatBonusWis(){}
  """+fields+res+methods+"""
}
class L1PcInstance extends L1Character {
  int _type, packets;
  BaseParam m_BaseParam=new BaseParam();
  int getType(){return _type;}
  void RefreshBasePDefend(){}
  """+pcmethods+"""
}
class BaseParam {void invalidate(){}}
class SC_SPMR {static void send(L1PcInstance p){p.packets++;}}
class IntRange {static int ensure(int n,int lo,int hi){return Math.max(lo,Math.min(hi,n));}}
enum PassiveID {RESIST_ELEMENT; int toInt(){return 0;}}
class CalcStat {
  """+base+"""
  static int clampType(int n){return Math.max(0,Math.min(BASE_MR.length-1,n));}
  """+calcMethods+"""
}
public class BaseMrAudit {
 static int clamp(int n){return Math.max(0,Math.min(250,n));}
 static void check(L1PcInstance p,int bonus,int skill){
   int expected=clamp(CalcStat.마법방어(p.getType(),p.GetTotalStatWis())+p.getLevel()/2);
   if(p.GetOrgMDefend()!=expected||p.GetMDefend()!=clamp(expected+bonus+skill+(p.passive?5:0)))throw new AssertionError("MR "+expected+" vs "+p.GetOrgMDefend());
 }
 public static void main(String[] args){
   java.util.Random r=new java.util.Random(892);
   L1PcInstance p=new L1PcInstance();int bonus=0,skill=0;
   L1Character npc=new L1Character();npc.SetOrgMDefend(73);
   int count=0;
   for(int i=0;i<30000;i++){
     switch(i%9){
       case 0 -> p.setLevel(1+r.nextInt(125));
       case 1 -> p.setType(r.nextInt(8));
       case 2 -> p.SetStatWis(r.nextInt(128));
       case 3 -> p.AddAddedStatWis(r.nextInt(31)-15);
       case 4 -> p.SetSkillPrimaryStats(r.nextLong(),false);
       case 5 -> {int delta=r.nextInt(41)-20;bonus+=delta;p.AddMDefend(delta);}
       case 6 -> {skill=r.nextInt(81)-40;p.SetSkillMDefend(skill);}
       case 7 -> p.ResetPrimaryStats();
       case 8 -> p.passive=!p.passive;
     }
     check(p,bonus,skill);
     npc.setLevel(1+r.nextInt(125));npc.SetStatWis(r.nextInt(128));npc.AddAddedStatWis(r.nextInt(20));npc.SetSkillPrimaryStats(r.nextLong(),false);
     if(npc.GetOrgMDefend()!=73)throw new AssertionError("NPC template MR changed");
     if(p.packets!=0)throw new AssertionError("silent recalculation sent a packet");
     count++;
   }
   p.resetBaseMr();check(p,bonus,skill);
   if(p.packets!=1)throw new AssertionError("notification");
   System.out.println("PASS "+count+" WIS/level/class/skill/gear MR transitions and NPC isolation");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    f=Path(d)/"BaseMrAudit.java"
    f.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(f)],check=True)
    subprocess.run(["java","-cp",d,"BaseMrAudit"],check=True)
