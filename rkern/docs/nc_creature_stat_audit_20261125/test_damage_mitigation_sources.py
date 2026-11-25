"""Real common creature mitigation lanes: compare equipment and skill deltas."""
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
src=(model/"L1Character.java").read_text(encoding="utf-8")
pc=(model/"Instance/L1PcInstance.java").read_text(encoding="utf-8")
def method(sig):
    i=src.index(sig); j=src.index("{",i)+1; depth=1
    while depth:
        depth+=(src[j]=="{")-(src[j]=="}"); j+=1
    return src[i:j]
names=("m_nArmorDamageReduction","m_nDamageReductionIgnore","m_nDamageReduction","m_nPvpMagicDamageReduction","m_nSkillPvpMagicDamageReduction","pumpedCarryArmorAndAccuracy")
fields="\n".join(re.search(r"[^\n]*\b"+n+r"\s*;",src).group(0) for n in names)
methods="\n".join(method("public final "+t+" "+n+"(") for t,n in (
    ("int","getDamageReductionByArmor"),("void","addDamageReductionByArmor"),
    ("int","getDamageReductionIgnore"),("void","addDamageReductionIgnore"),
    ("int","getDamageReduction"),("void","addDamageReduction"),
    ("int","getPVPMagicDamageReduction"),("int","addPVPMagicDamageReduction"),
    ("void","setPumpedPvPMagicDamageReduction")))
for n in ("_damageReductionByArmor","_damageReductionIgnore","_DamageReduction","_PVPMagicDamageReduction","pumpedPvPMagicDamageReduction"):
    assert not re.search(r"\b"+n+r"\b",pc),n
stub="""
class L1Character {
 """+fields+methods+"""
 void pump(long v){pumpedCarryArmorAndAccuracy=v;}
}
class L1PcInstance extends L1Character {}
class L1NpcInstance extends L1Character {}
public class MitigationAudit {
 public static void main(String[] args){
   int count=0;
   for(L1Character c:new L1Character[]{new L1PcInstance(),new L1NpcInstance()}){
     int armor=0,ignore=0,normal=0,pvp=0,skill=0;
     java.util.Random r=new java.util.Random(130);
     for(int i=0;i<50000;i++){
       int v=r.nextInt();
       switch(i%5){
         case 0->{armor+=v;c.addDamageReductionByArmor(v);}
         case 1->{ignore+=v;c.addDamageReductionIgnore(v);}
         case 2->{normal+=v;c.addDamageReduction(v);}
         case 3->{pvp+=v;if(c.addPVPMagicDamageReduction(v)!=pvp)throw new AssertionError("return");}
         case 4->{skill=v;c.setPumpedPvPMagicDamageReduction(v);}
       }
       long a=i%2==0?0:r.nextLong();c.pump(a);
       if(c.getDamageReductionByArmor()!=armor+(short)(a>>>16)||
          c.getDamageReductionIgnore()!=ignore||c.getDamageReduction()!=normal||
          c.getPVPMagicDamageReduction()!=pvp+skill)throw new AssertionError("source mismatch");
       count++;
     }
     c.setPumpedPvPMagicDamageReduction(0);c.pump(0);
     if(c.getDamageReductionByArmor()!=armor||c.getPVPMagicDamageReduction()!=pvp)throw new AssertionError("skill clear");
   }
   System.out.println("PASS "+count+" PC/NPC damage mitigation transitions");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"MitigationAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"MitigationAudit"],check=True)
