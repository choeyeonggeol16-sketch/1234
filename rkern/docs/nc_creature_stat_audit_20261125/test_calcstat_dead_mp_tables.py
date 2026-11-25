"""Compare the production CalcStat API against the exact pre-cleanup revision."""
from pathlib import Path
import re
import subprocess
import tempfile

root=Path(__file__).resolve().parents[2]
path=root/"src/l1j/server/server/utils/CalcStat.java"
old=subprocess.check_output(["git","show","2cdff1c07e6e6f2e32686ef03f15746dd95c4097:rkern/src/l1j/server/server/utils/CalcStat.java"],cwd=root).decode("utf-8")
new=path.read_text(encoding="utf-8")
dead=("MP_MIN","MP_RANGE","MP_MIN_LU","MP_MAX_LU","MpRule","StepRule",
      "buildIncreaseMp","buildMinMaxMp","applyMpRules","copyMpIncreaseTable","applyStepRules","clampWis","WIS_MAX")
for name in dead:
    assert not re.search(r"\b"+name+r"\b",new),name
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)
    (p/"OldCalcStat.java").write_text(old.replace("class CalcStat {","class OldCalcStat {").replace("private CalcStat()","private OldCalcStat()"),encoding="utf-8")
    (p/"CalcStat.java").write_text(new,encoding="utf-8")
    (p/"GrowthAudit.java").write_text(r"""
package l1j.server.server.utils;
import java.lang.reflect.*;
import java.util.*;
public class GrowthAudit {
    static int checks;
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
    public static void main(String[] args)throws Exception {
        int[] types={-2147483648,-1,0,1,2,3,4,5,6,7,8,2147483647};
        for(Method m:CalcStat.class.getDeclaredMethods()){
            if(!Modifier.isPublic(m.getModifiers())||!Modifier.isStatic(m.getModifiers()))continue;
            Class<?>[] params=m.getParameterTypes();
            Method baseline=OldCalcStat.class.getMethod(m.getName(),params);
            if(m.getName().equals("increaseHp")||m.getName().equals("increaseMp")){
                for(int t:types)for(int v=-128;v<=127;v++){
                    int u=v&255;
                    int a=((Number)m.invoke(null,t,(byte)v)).intValue();
                    int b=((Number)baseline.invoke(null,t,(byte)v)).intValue();
                    int lo=m.getName().equals("increaseHp")?CalcStat.PureHp(t,u):CalcStat.MinincreaseMp(t,u);
                    int hi=m.getName().equals("increaseHp")?lo+1:CalcStat.MaxincreaseMp(t,u);
                    check(a>=lo&&a<=hi&&b>=lo&&b<=hi,m.getName());
                }
                continue;
            }
            if(params.length==1)for(int i=-128;i<=255;i++)
                check(Objects.equals(m.invoke(null,i),baseline.invoke(null,i)),m.getName());
            else if(params.length==2)for(int t:types)for(int i=-128;i<=255;i++)
                check(Objects.equals(m.invoke(null,t,i),baseline.invoke(null,t,i)),m.getName());
            else if(params.length==3){
                for(int i=0;i<10000;i++){
                    Object[] v=params[1]==boolean.class?new Object[]{i%256-128,(i&1)!=0,i%128}:new Object[]{i%256,i%128-32,i%128};
                    check(Objects.equals(m.invoke(null,v),baseline.invoke(null,v)),m.getName());
                }
            }
            else throw new AssertionError(m.getName());
        }
        System.out.println("PASS "+checks+" public formula comparisons");
    }
}
""",encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,str(p/"OldCalcStat.java"),str(p/"CalcStat.java"),str(p/"GrowthAudit.java")],check=True)
    subprocess.run(["java","-cp",tmp,"l1j.server.server.utils.GrowthAudit"],check=True)
