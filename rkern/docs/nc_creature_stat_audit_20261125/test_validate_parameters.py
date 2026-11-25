"""Check actual creature validation dispatch order and NPC defense isolation."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[2]
src=(root/"src/l1j/server/server/model/L1Character.java").read_text(encoding="utf-8")
start=src.index("protected static final int VB_HP =")
end=src.index("final byte ValidateStat(",start)
dispatch=src[start:end]
harness="""
class L1Character {
    final StringBuilder log=new StringBuilder();
    void clampCurrentHp(){log.append("H");}
    void clampCurrentMp(){log.append("M");}
""" + dispatch + """
}
class L1PcInstance extends L1Character {
    void RefreshBaseMDefend(){log.append("R");}
    void RefreshBasePDefend(){log.append("A");}
}
public class ValidateAudit {
    public static void main(String[] args){
        int checks=0;
        for(int mask=0;mask<16;mask++){
            for(boolean pc:new boolean[]{false,true}){
                L1Character c=pc?new L1PcInstance():new L1Character();
                String expected=((mask&1)!=0?"H":"")+((mask&2)!=0?"M":"")
                        +(pc&&(mask&4)!=0?"R":"")+(pc&&(mask&8)!=0?"A":"");
                for(int i=0;i<1000;i++){
                    c.log.setLength(0);
                    c.ValidateParameters(mask);
                    if(!expected.equals(c.log.toString()))throw new AssertionError(mask+": "+c.log);
                    checks++;
                }
            }
        }
        System.out.println("PASS "+checks+" validation group dispatches");
    }
}
"""
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)/"ValidateAudit.java"
    p.write_text(harness,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",tmp,"ValidateAudit"],check=True)
