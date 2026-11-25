"""Verify the actual unified magic-defense entry path for all four creature pairs."""
from pathlib import Path
import tempfile, subprocess, re
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
source=(model/"L1Magic.java").read_text(encoding="utf-8")
def method(signature):
    start=source.index(signature)
    opening=source.index("{",start)
    depth=1
    end=opening+1
    while depth:
        depth+=(source[end]=="{")-(source[end]=="}")
        end+=1
    return source[start:end]
body=method("public int calcMrDefense(int dmg)")
assert "attacker.GetStatSp()" in body and "defender.GetMDefendBySkill()" in body
assert "_targetPc.getMr()" not in source
assert "_calcType == PC_PC && skillId == CANCELLATION && (cancellationMr = _targetPc.GetMDefendBySkill())" in source
for path, pattern in [
    ("Instance/L1PetInstance.java",r"pet\.addSp\("),
    ("L1WeaponSkill.java",r"pc\.getSp\("),
    ("item/ItemStat.java",r"\bCHARACTER_SP\b"),
    ("EquipmentEnchantOptions.java",r"\bCHARACTER_SP\b"),
]:
    assert not re.search(pattern,(model/path).read_text(encoding="utf-8")),path
stub="""
import java.util.concurrent.ThreadLocalRandom;
public class MagicAudit {
 static final int PC_PC=1,PC_NPC=2,NPC_PC=3,NPC_NPC=4;
 static class L1Character {
   final int sp,mr,level; int spReads,mrReads,levelReads;
   L1Character(int s,int m,int l){sp=s;mr=m;level=l;}
   int GetStatSp(){spReads++;return sp;}
   int GetMDefendBySkill(){mrReads++;return mr;}
   int getLevel(){levelReads++;return level;}
 }
 L1Character _pc,_npc,_targetPc,_targetNpc;
 int _calcType;
 """+body+"""
 public static void main(String[] args) {
   int count=0;
   for(int mode=1;mode<=4;mode++){
     for(int mr:new int[]{0,50,150,250}){
       for(int sp:new int[]{0,10,50,150}){
         MagicAudit a=new MagicAudit();a._calcType=mode;
         L1Character src=new L1Character(sp,3,80), dst=new L1Character(1,mr,90);
         if(mode==1||mode==2)a._pc=src;else a._npc=src;
         if(mode==1||mode==3)a._targetPc=dst;else a._targetNpc=dst;
         for(int i=0;i<50;i++){
           int result=a.calcMrDefense(1000);
           if(result<10||result>1050)throw new AssertionError(result);
         }
         if(src.spReads!=50||dst.mrReads!=50||src.levelReads!=50||dst.levelReads!=50)throw new AssertionError("wrong creature");
         count+=50;
       }
     }
   }
   System.out.println("PASS "+count+" magic-defense PC/NPC routing cases");
 }
}
"""
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"MagicAudit.java"
    p.write_text(stub,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",d,"MagicAudit"],check=True)
