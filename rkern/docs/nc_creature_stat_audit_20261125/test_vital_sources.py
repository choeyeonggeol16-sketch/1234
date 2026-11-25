"""Compare production HP/MP source transitions and regeneration against prior L1 rules."""
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
src=(root/"src/l1j/server/server/model/L1Character.java").read_text(encoding="utf-8")
def method(sig):
    i=src.index(sig); j=src.index("{",i)+1; depth=1
    while depth:
        depth+=(src[j]=="{")-(src[j]=="}"); j+=1
    return src[i:j]
names=("m_nBaseHP","m_nAddedHP","m_nMaxHP","m_nBaseMP","m_nAddedMP","m_nMaxMP","m_SkillHPMP","m_sOrgHPRegen","m_sOrgMPRegen","pumpedRegenAndWeaponRates")
fields="\n".join(re.search(r"[^\n]*\b"+n+r"\s*;",src).group(0) for n in names)
methods="\n".join(method(s) for s in (
    "public int getMaxHp(","public int getMaxMp(","public final int getBaseMaxHp(","public final int getBaseMaxMp(",
    "public final void addBaseMaxHp(","public final void addBaseMaxMp(","public void setMaxHp(","public void setMaxMp(",
    "public void addMaxHp(","public void addMaxMp(","private void ValidateMaxHP(","private void ValidateMaxMP(",
    "public final short getHpr(","public final short getMpr(","public final void addHpr(","public final void addMpr(",
    "public final void setPumpedRegenAndWeaponRates("))
stub="""
class L1Character {
 """+fields+methods+"""
 int _currentHp,_currentMp,con,wis;
 int GetStatCon(){return con;} int GetStatWis(){return wis;} int GetTotalStatWis(){return wis;}
 void clampCurrentHp(){_currentHp=Math.min(_currentHp,getMaxHp());}
 void pump(int hp,int mp){m_SkillHPMP=((long)mp<<32)|Integer.toUnsignedLong(hp);clampCurrentHp();_currentMp=Math.min(_currentMp,getMaxMp());}
}
class L1PcInstance extends L1Character {}
class L1NpcInstance extends L1Character {}
class IntRange {static int ensure(int n,int lo,int hi){return Math.clamp(n,lo,hi);}}
class CalcStat {static int pureHpMpBonus(int n){return n>=45?100:n>=35?50:n>=25?20:0;}}
public class VitalAudit {
 static class Old {
   int hp,mp,effHp,effMp,bh,bm,sh,sm,curHp,curMp,con,wis; short hpr,mpr;
   final boolean pc; Old(boolean p){pc=p;}
   int maxHp(){return (sh==0?effHp:Math.clamp((long)hp+sh,1,10000000))+(pc?CalcStat.pureHpMpBonus(con):0);}
   int maxMp(){return (sm==0?effMp:Math.clamp((long)mp+sm,0,10000000))+CalcStat.pureHpMpBonus(wis);}
   void setHp(int n){hp=n;effHp=Math.clamp(n,1,10000000);curHp=Math.min(curHp,maxHp());}
   void setMp(int n){mp=n;effMp=Math.clamp(n,0,10000000);curMp=Math.min(curMp,maxMp());}
   void baseHp(short n){int next=Math.clamp(bh+n,1,32767);setHp(hp+next-bh);bh=next;}
   void baseMp(int n){int next=Math.clamp(bm+n,0,32767);setMp(mp+next-bm);bm=next;}
 }
 public static void main(String[] args){
  int count=0;
  for(boolean pc:new boolean[]{false,true}){
   L1Character c=pc?new L1PcInstance():new L1NpcInstance();Old o=new Old(pc);
   java.util.Random r=new java.util.Random(140);
   for(int i=0;i<60000;i++){
    int v=r.nextInt(),op=i%9;
    switch(op){
     case 0->{c.setMaxHp(v);o.setHp(v);if(!pc)o.bh=v;}
     case 1->{c.setMaxMp(v);o.setMp(v);if(!pc)o.bm=v;}
     case 2->{c.addMaxHp(v);o.setHp(o.hp+v);}
     case 3->{c.addMaxMp(v);o.setMp(o.mp+v);}
     case 4->{if(pc){c.addBaseMaxHp((short)v);o.baseHp((short)v);}}
     case 5->{if(pc){c.addBaseMaxMp(v);o.baseMp(v);}}
     case 6->{int h=r.nextInt(),m=r.nextInt();c.pump(h,m);o.sh=h;o.sm=m;o.curHp=Math.min(o.curHp,o.maxHp());o.curMp=Math.min(o.curMp,o.maxMp());}
     case 7->{c.addHpr(v);o.hpr+=v;}
     case 8->{c.addMpr(v);o.mpr+=v;}
    }
    long regen=i%2==0?0:r.nextLong();c.setPumpedRegenAndWeaponRates(regen);
    if(c.getMaxHp()!=o.maxHp()||c.getMaxMp()!=o.maxMp()||c.getBaseMaxHp()!=o.bh||c.getBaseMaxMp()!=o.bm||
       c._currentHp!=o.curHp||c._currentMp!=o.curMp||c.getHpr()!=(short)Math.max(0,o.hpr+(short)regen)||
       c.getMpr()!=(short)Math.max(0,o.mpr+(short)(regen>>>16)))throw new AssertionError("vital source mismatch "+i);
    if(i%37==0){c._currentHp=o.curHp=o.maxHp();c._currentMp=o.curMp=o.maxMp();}
    if(i%53==0){c.con=o.con=r.nextInt(70);c.wis=o.wis=r.nextInt(70);}
    count++;
   }
  }
  System.out.println("PASS "+count+" PC/NPC vital source, growth and regeneration transitions");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"VitalAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"VitalAudit"],check=True)
