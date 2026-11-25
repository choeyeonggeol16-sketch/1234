"""Compile the actual common defense methods and compare their old semantics."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model"
creature = (model / "L1Character.java").read_text(encoding="utf-8")
assert not (model / "AC.java").exists()
assert "extends L1Object implements AC" not in creature
assert "new AC(" not in creature
assert "protected final AC ac = this;" not in creature
# Catch residual allocations outside the inspected PC/NPC source paths.
for source in (root / "src").rglob("*.java"):
    assert not re.search(r"\bnew\s+(?:l1j\.server\.server\.model\.)?AC\s*\(", source.read_text(encoding="utf-8", errors="replace")), str(source)

def method(signature):
    start = creature.index(signature)
    opening = creature.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (creature[end] == "{") - (creature[end] == "}")
        end += 1
    return creature[start:end]

methods = "\n".join(method(s) for s in (
    "public final int getAc()", "public final void addAc(",
    "public final void setAc(", "public final void setSkillAc(", "private void ValidatePDefend(",
    "public final int GetBasePDefend(", "protected final void SetBasePDefend(", "protected final void InitPDefend(", "private static int GetPDefendEvasion(",
))
fields = "\n".join(re.search(r"[^\n]*\b" + n + r"\s*;", creature).group(0)
                   for n in ("m_nBasePDefend", "m_nAddedPDefend", "m_nSkillPDefend", "m_nPDefend"))
stub = """
class L1Character {
    """ + fields + """
    int packets;
    int dg(){return GetPDefendEvasion(getAc());}
    int er(){return GetPDefendEvasion(getAc());}
    public boolean instanceOf(int n) {return this instanceof L1PcInstance;}
    """ + methods + """
}
class L1PcInstance extends L1Character {}
class L1Type {static final int L1TYPE_PC=1;}
class IntRange {static int ensure(int n,int lo,int hi){return Math.max(lo,Math.min(hi,n));}}
class SC_OwnCharAttrDef {static void send(L1PcInstance pc){pc.packets++;}}
"""
test = r"""
public class DefenseAudit {
    static class OldAC {
        int source, skill, value, dg, er, packets;
        final boolean pc;
        OldAC(boolean pc){this.pc=pc;}
        void setAc(int i){source=i;update(true);}
        void addAc(int i){setAc(source+i);}
        void setSkillAc(int i,boolean notify){if(skill==i)return;skill=i;update(notify);}
        void update(boolean notify){
            value=IntRange.ensure(source+skill,-999,999);
            int decreaseAC=-value-100;
            int effect=decreaseAC>=0?(decreaseAC/10)+1:0;
            boolean changed=dg!=effect||er!=effect;
            dg=er=effect;
            if(notify&&changed&&pc)packets++;
        }
    }
    static void check(OldAC old,L1Character c){
        if(old.value!=c.getAc()||old.dg!=c.dg()||old.er!=c.er()||old.packets!=c.packets
                )throw new AssertionError();
    }
    public static void main(String[] args){
        int count=0;
        int[] edges={Integer.MIN_VALUE,-10000,-1000,-999,-111,-110,-101,-100,-99,-1,0,1,99,100,999,1000,10000,Integer.MAX_VALUE};
        for(boolean pc:new boolean[]{false,true}){
            L1Character c=pc?new L1PcInstance():new L1Character();OldAC old=new OldAC(pc);
            for(int a:edges)for(int b:edges)for(boolean notify:new boolean[]{false,true}){
                old.setAc(a);c.setAc(a);check(old,c);
                old.setSkillAc(b,notify);c.setSkillAc(b,notify);check(old,c);
                old.addAc(b);c.addAc(b);check(old,c);
                old.setSkillAc(b,notify);c.setSkillAc(b,notify);check(old,c);
                count+=4;
            }
            java.util.Random rnd=new java.util.Random(391);
            for(int i=0;i<100000;i++){
                int value=rnd.nextInt();boolean notify=rnd.nextBoolean();
                switch(rnd.nextInt(3)){
                    case 0 -> {old.setAc(value);c.setAc(value);}
                    case 1 -> {old.addAc(value);c.addAc(value);}
                    default -> {old.setSkillAc(value,notify);c.setSkillAc(value,notify);}
                }
                check(old,c);count++;
            }
        }
        for(boolean pc:new boolean[]{false,true}){
            L1Character c=pc?new L1PcInstance():new L1Character(); OldAC old=new OldAC(pc);
            java.util.Random rng=new java.util.Random(182);
            int base=0;
            for(int i=0;i<20000;i++){
                int v=rng.nextInt(1200)-600;
                boolean notify=rng.nextBoolean();
                switch(i%5){
                    case 0 -> {c.SetBasePDefend(v,false);old.source+=v-base;base=v;old.update(false);}
                    case 1 -> {c.addAc(v);old.addAc(v);}
                    case 2 -> {c.setSkillAc(v,notify);old.setSkillAc(v,notify);}
                    case 3 -> {c.setAc(v);old.setAc(v);}
                    case 4 -> {c.InitPDefend(v);old.source=v;base=v;old.update(false);}
                }
                check(old,c);
                if(c.GetBasePDefend()!=base)throw new AssertionError("base lost");
                count++;
            }
        }
        System.out.println("PASS "+count+" PC/NPC defense transition comparisons");
    }
}
"""
with tempfile.TemporaryDirectory() as d:
    work=Path(d)
    (work / "DefenseAudit.java").write_text(stub+test, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "DefenseAudit.java"], cwd=d, check=True, timeout=60)
    subprocess.run(["java", "-ea", "-cp", d, "DefenseAudit"], cwd=d, check=True, timeout=60)
