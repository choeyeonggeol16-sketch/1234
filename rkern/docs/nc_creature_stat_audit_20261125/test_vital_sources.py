"""Compare ordinary L1 vitality transitions and explicit widened-sum boundaries."""
from pathlib import Path
import os, re, subprocess, tempfile
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
    "public void addMaxHp(","public void addMaxMp(","private void ValidateMaxHP(","private void ValidateMaxMP(","private void clampCurrentMp(",
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
class CPCParamDB {static int pureHpMpBonus(int n){return n>=45?100:n>=35?50:n>=25?20:0;}}
public class VitalAudit {
 static class Rules {
   int effHp,effMp,bh,bm,ah,am,sh,sm,curHp,curMp,con,wis; short hpr,mpr;
   final boolean pc, wide;
   Rules(boolean pc, boolean wide){this.pc=pc;this.wide=wide;}
   long sum(int base,int added){return wide?(long)base+added:base+added;}
   int maxHp(){return (sh==0?effHp:Math.clamp(sum(bh,ah)+sh,1,10000000))+(pc?CPCParamDB.pureHpMpBonus(con):0);}
   int maxMp(){return (sm==0?effMp:Math.clamp(sum(bm,am)+sm,0,10000000))+CPCParamDB.pureHpMpBonus(wis);}
   void refreshHp(){effHp=Math.clamp(sum(bh,ah),1,10000000);curHp=Math.min(curHp,maxHp());}
   void refreshMp(){effMp=Math.clamp(sum(bm,am),0,10000000);curMp=Math.min(curMp,maxMp());}
   void setHp(int n){if(pc)ah=n-bh;else{bh=n;ah=0;}refreshHp();}
   void setMp(int n){if(pc)am=n-bm;else{bm=n;am=0;}refreshMp();}
   void baseHp(short n){bh=Math.clamp(bh+n,1,32767);refreshHp();}
   void baseMp(int n){bm=Math.clamp(bm+n,0,32767);refreshMp();}
 }
 static void checkCombinedOverflow() {
   L1Character c=new L1NpcInstance();
   Rules old=new Rules(false,false);
   int nearLimit=Integer.MAX_VALUE-15;
   c.setMaxHp(nearLimit); c.setMaxMp(nearLimit); old.setHp(nearLimit); old.setMp(nearLimit);
   c.addMaxHp(20); c.addMaxMp(20); old.ah+=20; old.am+=20; old.refreshHp(); old.refreshMp();
   if(old.maxHp()!=1||old.maxMp()!=0||c.getMaxHp()!=10_000_000||c.getMaxMp()!=10_000_000)
     throw new AssertionError("positive base/added sum wrapping must no longer hit the minimum");
   nearLimit=Integer.MIN_VALUE+15;
   c.setMaxHp(nearLimit); c.setMaxMp(nearLimit); old.setHp(nearLimit); old.setMp(nearLimit);
   c.addMaxHp(-20); c.addMaxMp(-20); old.ah-=20; old.am-=20; old.refreshHp(); old.refreshMp();
   c.pump(5,5);old.sh=old.sm=5;
   if(old.maxHp()!=10_000_000||old.maxMp()!=10_000_000||c.getMaxHp()!=1||c.getMaxMp()!=0)
     throw new AssertionError("negative base/added sum wrapping must no longer inflate the skill cap");
 }
 public static void main(String[] args){
  int count=0;
  checkCombinedOverflow();
  for(boolean wide:new boolean[]{false,true}) for(boolean pc:new boolean[]{false,true}){
   L1Character c=pc?new L1PcInstance():new L1NpcInstance();Rules o=new Rules(pc,wide);
   java.util.Random r=new java.util.Random(140);
   for(int i=0;i<60000;i++){
    int v=wide?r.nextInt():r.nextInt(-2000,2001),op=i%9;
    switch(op){
     case 0->{c.setMaxHp(v);o.setHp(v);}
     case 1->{c.setMaxMp(v);o.setMp(v);}
     case 2->{c.addMaxHp(v);o.ah+=v;o.refreshHp();}
     case 3->{c.addMaxMp(v);o.am+=v;o.refreshMp();}
     case 4->{if(pc){c.addBaseMaxHp((short)v);o.baseHp((short)v);}}
     case 5->{if(pc){c.addBaseMaxMp(v);o.baseMp(v);}}
     case 6->{int h=wide?r.nextInt():r.nextInt(-2000,2001),m=wide?r.nextInt():r.nextInt(-2000,2001);
       c.pump(h,m);o.sh=h;o.sm=m;o.curHp=Math.min(o.curHp,o.maxHp());o.curMp=Math.min(o.curMp,o.maxMp());}
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
    subprocess.run([os.environ.get("JAVAC","javac"),"-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"VitalAudit"],check=True)
