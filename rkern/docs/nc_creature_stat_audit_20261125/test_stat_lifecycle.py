"""Directed lifecycle regressions using actual production methods."""
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
src=(model/"L1Character.java").read_text(encoding="utf-8")
npc=(model/"Instance/L1NpcInstance.java").read_text(encoding="utf-8")
rst=(model/"network/core/cs/packet/CS_ReturnStatus.java").read_text(encoding="utf-8")
def method(s,sig):
    i=s.index(sig); j=s.index("{",i)+1; depth=1
    while depth:
        depth+=(s[j]=="{")-(s[j]=="}"); j+=1
    return s[i:j]
fields=src[src.index("// CStats: original,"):src.index("// BEGIN shared resistance sources")]
lanes=src[src.index("// CStats lane operations."):src.index("// BEGIN direct primary-stat operations")]
vfields="\n".join(re.search(r"[^\n]*\b"+n+r"\s*;",src).group(0) for n in (
    "m_nBaseHP","m_nAddedHP","m_nMaxHP","m_nBaseMP","m_nAddedMP","m_nMaxMP","m_SkillHPMP",
    "m_nOrgMeleeDamage","m_nOrgMeleeHit","m_SkillWeapon"))
methods="\n".join(method(src,s) for s in (
    "public final void ResetPrimaryStats(","public final void SetSkillPrimaryStats(","private void NotifySkillPrimaryStats(","public void setPumpedSkillModifiers(",
    "public int getMaxHp(","public int getMaxMp(","private void clampCurrentHp(","private void clampCurrentMp(",
    "public void setMaxHp(","public void setMaxMp(","private void ValidateMaxHP(","private void ValidateMaxMP(",
    "public int getDmgup(","public void addDmgup(","public int getHitup(","public void addHitup("))
template=method(npc,"private void SetTemplateAttackBonus(")
assert "SetTemplateAttackBonus(bonus * 2);" in npc
assert "setAc(pc.GetBasePDefend())" not in rst
calc=(root/"src/l1j/server/server/utils/CPCParamDB.java").read_text(encoding="utf-8")
# Read the actual permanent CON/WIS bonus formula.
bonus=method(calc,"public static int pureHpMpBonus(")
stub="""
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
class L1Character {
 """+fields+vfields+lanes+methods+"""
 volatile int _currentHp,_currentMp;
 private static final AtomicIntegerFieldUpdater<L1Character> CURRENT_HP=AtomicIntegerFieldUpdater.newUpdater(L1Character.class,"_currentHp");
 private int pumpedEvasion; private long m_SkillCombatRates;
 void NotifyStatBonusStr(){} void NotifyStatBonusDex(){} void NotifyStatBonusInt(){} void NotifyStatBonusCon(){} void NotifyStatBonusWis(){}
 void setSkillAc(int v,boolean n){} void SetSkillMDefend(int v){} void SetSkillElemental(long v){} void SetSkillSp(int v){}
 void fill(){_currentHp=getMaxHp();_currentMp=getMaxMp();}
 void pump(int hp,int mp,long stats){setPumpedSkillModifiers(hp,mp,0,0,stats,0,0,0,0,0,false);}
}
class L1PcInstance extends L1Character {
 void RefreshBaseMDefend(){} void RefreshBasePDefend(){}
}
class L1NpcInstance extends L1Character {
 private int m_nTemplateAttackBonus;
 """+template+"""
 void transform(int b){SetTemplateAttackBonus(b);}
}
class IntRange {static int ensure(int n,int lo,int hi){return Math.clamp(n,lo,hi);}}
class CPCParamDB { """+bonus+""" }
public class LifecycleAudit {
 static void eq(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  L1PcInstance p=new L1PcInstance();p.setMaxHp(1000);p.setMaxMp(500);
  for(int i=0;i<10000;i++){
    p.SetStatCon(45);p.SetStatWis(45);p.fill();
    p.SetStatCon(24);p.SetStatWis(24);
    eq(p._currentHp,p.getMaxHp());eq(p._currentMp,p.getMaxMp());
    p.AddAddedStatDex(5);p.AddAddedStatWis(4);p.SetSkillPrimaryStats(3L<<8,false);
    p.ResetPrimaryStats(12,14,45,13,45,10);eq(p.GetAddedStatDex(),8);eq(p.GetAddedStatWis(),4);eq(p.GetStatCon(),45);eq(p.GetOriginalStatWis(),45);
    p.fill();p.ResetPrimaryStats(12,14,45,13,45,10);eq(p._currentHp,p.getMaxHp());eq(p._currentMp,p.getMaxMp());
    p.AddAddedStatDex(-5);p.AddAddedStatWis(-4);p.SetSkillPrimaryStats(0,false);
    eq(p.GetAddedStatDex(),0);eq(p.GetAddedStatWis(),0);p.ResetPrimaryStats(0,0,0,0,0,0);
  }
  L1NpcInstance n=new L1NpcInstance();n.setMaxHp(1000);n.setMaxMp(500);
  n.addDmgup(7);n.addHitup(9);
  for(int i=0;i<10000;i++){
    int v=i%13*2;n.transform(v);eq(n.getDmgup(),7+v);eq(n.getHitup(),9+v);
    n.transform(v);eq(n.getDmgup(),7+v);eq(n.getHitup(),9+v);
    n.transform(0);eq(n.getDmgup(),7);eq(n.getHitup(),9);
    n.SetStatWis(24);n.AddAddedStatWis(21);n.fill();
    n.AddAddedStatWis(-21);eq(n._currentMp,n.getMaxMp());
    n.pump(0,100,21L<<24);n.fill();
    n.pump(0,0,0);eq(n._currentMp,n.getMaxMp());
    n.SetStatWis(45);n.fill();n.SetStatWis(24);eq(n._currentMp,n.getMaxMp());
  }
  System.out.println("PASS 20000 stat reset, vital clamp and NPC template lifecycle iterations");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"LifecycleAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"LifecycleAudit"],check=True)
