"""Check all live parameter lookup APIs against the exact baseline implementation."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
path = root / "src/l1j/server/server/utils/CPCParamDB.java"
old = subprocess.check_output(["git", "show", "8412df03bd5d173291db4cc9590fad56be534f07:rkern/src/l1j/server/server/utils/CalcStat.java"], cwd=root).decode("utf-8")
new = path.read_text(encoding="utf-8")
assert "tableGet80(" not in new and "tableGet45(" not in new
assert "private static int GetParamBonus(" in new
with tempfile.TemporaryDirectory() as tmp:
    p = Path(tmp)
    (p / "OldCalcStat.java").write_text(old.replace("class CalcStat {", "class OldCalcStat {").replace("private CalcStat()", "private OldCalcStat()"), encoding="utf-8")
    (p / "CPCParamDB.java").write_text(new, encoding="utf-8")
    (p / "ParamAudit.java").write_text(r"""
package l1j.server.server.utils;
import java.lang.reflect.Method;
public class ParamAudit {
    public static void main(String[] args) throws Exception {
        String[] names={"근거리대미지","근거리명중","원거리대미지","원거리명중","마법대미지","원거리회피","물리방어력","엠소모감소","엠회복틱","엠피물약회복","피회복틱"};
        long count=0;
        for(String name:names){
            Method a=OldCalcStat.class.getMethod(name,int.class), b=CPCParamDB.class.getMethod(name,int.class);
            for(int v=-512;v<=512;v++){
                if(!a.invoke(null,v).equals(b.invoke(null,v)))throw new AssertionError(name+" "+v);
                count++;
            }
            for(int v:new int[]{Integer.MIN_VALUE,Integer.MIN_VALUE+1,Integer.MAX_VALUE-1,Integer.MAX_VALUE}){
                if(!a.invoke(null,v).equals(b.invoke(null,v)))throw new AssertionError(name+" "+v);
                count++;
            }
        }
        System.out.println("PASS "+count+" parameter lookups");
    }
}
""", encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,str(p/"OldCalcStat.java"),str(p/"CPCParamDB.java"),str(p/"ParamAudit.java")],check=True)
    subprocess.run(["java","-cp",tmp,"l1j.server.server.utils.ParamAudit"],check=True)
