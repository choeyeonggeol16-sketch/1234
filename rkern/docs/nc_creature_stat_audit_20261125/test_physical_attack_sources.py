"""Exercise real shared melee/ranged damage and accuracy source methods."""
from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
creature=(model/"L1Character.java").read_text(encoding="utf-8")
pc=(model/"Instance/L1PcInstance.java").read_text(encoding="utf-8")
names=("Dmgup","BowDmgup","Hitup","BowHitup")
fields=("m_nOrgMeleeDamage","m_nOrgRangedDamage","m_nOrgMeleeHit","m_nOrgRangedHit","m_SkillWeapon")
def method(s):
    i=creature.index(s)
    end=creature.index("{",i)+1
    depth=1
    while depth:
        depth+=(creature[end]=="{")-(creature[end]=="}")
        end+=1
    return creature[i:end]
for n in names:
    assert "getBase"+n+"(" not in pc
    assert "_true"+n not in creature
fields="\n".join(re.search(r"[^\n]*\b"+n+r"\s*;",creature).group(0) for n in fields)
methods="\n".join(method("public "+typ+" "+prefix+n+"(") for n in names for typ,prefix in (("int","get"),("void","add")))
stub="""
class L1Character {
 """+fields+methods+"""
 void setSkill(long v){m_SkillWeapon=v;}
}
class L1PcInstance extends L1Character {}
class L1NpcInstance extends L1Character {}
public class WeaponStatAudit {
 static int clamp(long n){return (int)Math.max(-128,Math.min(127,n));}
 static void check(L1Character c,int[] org,long skill){
   if(c.getDmgup()!=clamp((long)org[0]+(short)(skill>>>16))||
      c.getBowDmgup()!=clamp((long)org[1]+(short)(skill>>>32))||
      c.getHitup()!=clamp((long)org[2]+(short)skill)||
      c.getBowHitup()!=clamp((long)org[3]+(short)(skill>>>48)))throw new AssertionError("source mismatch");
 }
 static void add(L1Character c,int n,int v){
    switch(n){case 0->c.addDmgup(v);case 1->c.addBowDmgup(v);case 2->c.addHitup(v);case 3->c.addBowHitup(v);}
 }
 public static void main(String[] args){
   int count=0;
   int[] edges={Integer.MIN_VALUE,-32768,-129,-128,-1,0,1,127,128,32767,Integer.MAX_VALUE};
   for(L1Character c:new L1Character[]{new L1PcInstance(),new L1NpcInstance()}){
     int[] org=new int[4];long skill=0;
     for(int i=0;i<4;i++)for(int x:edges)for(int y:edges){
       add(c,i,x);org[i]+=x;
       skill=(long)(y&65535)*0x0001000100010001L;c.setSkill(skill);
       check(c,org,skill);
       add(c,i,-x);org[i]-=x;check(c,org,skill);count+=2;
     }
     java.util.Random r=new java.util.Random(117);
     for(int j=0;j<50000;j++){
       int i=r.nextInt(4),v=r.nextInt();
       add(c,i,v);org[i]+=v;
       skill=j%2==0?0:r.nextLong();c.setSkill(skill);
       check(c,org,skill);count++;
     }
   }
   System.out.println("PASS "+count+" physical attack/accuracy PC/NPC transitions");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"WeaponStatAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"WeaponStatAudit"],check=True)
