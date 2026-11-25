"""Compile real shared magic/critical stat accessors, preserving independent L1 formula lanes."""
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
fields="\n".join(re.search(r"[^\n]*\b"+n+r"\s*(?:=\s*0)?;",src).group(0) for n in (
    "m_nOrgMHit","m_nAddedMHit","m_nAddedMCriticalProb","pumpedCarryArmorAndAccuracy","m_SkillCombatRates",
    "m_nOrgRangedCriticalProb","m_nOrgMeleeCriticalProb","m_nOrgMCriticalProb"))
methods="\n".join(method(s) for s in (
    "public final void addBaseMagicHitUp(","public final int getBaseMagicHitUp(","public final int getTotalMagicHitup(",
    "public final void addBaseMagicCritical(","public final int getBaseMagicCritical(",
    "public int getMagicHit(","public void addMagicHit(","protected final int getPumpedWeaponHitRate(",
    "public int add_missile_critical_rate(","public int get_missile_critical_rate(",
    "public int add_melee_critical_rate(","public int get_melee_critical_rate(",
    "public int add_magic_critical_rate(","public int get_magic_critical_rate("))
assert "_originalMagicHit" not in pc and "_baseMagicHitup" not in pc and "_baseMagicCritical" not in pc
stub="""
class L1Character {
 """+fields+methods+"""
 void pump(long combat,long carry){m_SkillCombatRates=combat;pumpedCarryArmorAndAccuracy=carry;}
}
class L1PcInstance extends L1Character {
 int baseHit=7,baseCritical=3;
 int getBaseMagicHitByLevelClass(){return baseHit;}
 int getBaseMagicCriByLevelClass(){return baseCritical;}
}
class L1NpcInstance extends L1Character {}
public class CriticalAudit {
 public static void main(String[] args){
   int count=0;
   for(L1Character c:new L1Character[]{new L1PcInstance(),new L1NpcInstance()}){
     int directHit=0,gearHit=0,gearCritical=0,melee=0,ranged=0,magic=0;
     java.util.Random r=new java.util.Random(912);
     for(int i=0;i<50000;i++){
       int v=r.nextInt();
       switch(i%6){
         case 0->{directHit+=v;c.addMagicHit(v);}
         case 1->{gearHit+=v;c.addBaseMagicHitUp(v);}
         case 2->{gearCritical+=v;c.addBaseMagicCritical(v);}
         case 3->{melee+=v;if(c.add_melee_critical_rate(v)!=melee)throw new AssertionError();}
         case 4->{ranged+=v;if(c.add_missile_critical_rate(v)!=ranged)throw new AssertionError();}
         case 5->{magic+=v;if(c.add_magic_critical_rate(v)!=magic)throw new AssertionError();}
       }
       long a=i%2==0?0:r.nextLong(),b=i%3==0?0:r.nextLong();c.pump(a,b);
       int bh=c instanceof L1PcInstance?7:0,bc=c instanceof L1PcInstance?3:0;
       if(c.getMagicHit()!=directHit+(short)(a>>>32)||
          c.getBaseMagicHitUp()!=gearHit+(int)(b>>>32)||
          c.getTotalMagicHitup()!=bh+gearHit+(int)(b>>>32)||
          c.getBaseMagicCritical()!=bc+gearCritical||
          c.get_melee_critical_rate()!=melee||c.get_missile_critical_rate()!=ranged+(short)a||
          c.get_magic_critical_rate()!=magic+(short)(a>>>16)||
          c.getPumpedWeaponHitRate()!=(short)(a>>>48))throw new AssertionError("source mismatch");
       count++;
     }
   }
   System.out.println("PASS "+count+" independent magic hit and critical PC/NPC transitions");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"CriticalAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"CriticalAudit"],check=True)
