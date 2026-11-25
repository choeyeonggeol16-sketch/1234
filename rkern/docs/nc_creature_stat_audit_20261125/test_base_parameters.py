"""Compile the actual class/level parameter cache and stress snapshot publication."""
from pathlib import Path
import tempfile
import subprocess

root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model"
source = model / "character/Utill/PcBaseAbilityCache.java"
pc = (model / "Instance/L1PcInstance.java").read_text(encoding="utf-8")
assert pc.count("new PcBaseAbilityCache()") == 1
assert "_baseCache" not in pc and "_pcBaseAbilityCache" not in pc
stub = """package l1j.server.server.model.Instance;
public class L1PcInstance {
 public volatile int level, type;
 public int getLevel(){return level;}
 public int getType(){return type;}
}"""
harness = r'''
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.Utill.PcBaseAbilityCache;
import l1j.server.server.model.character.Utill.PcBaseAbilityCache.BaseAbility;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
public class BaseParamAudit {
 static final int[][] HIT={{4,6,20},{3,6,100},{5,3,16},{0,0,8},{3,4,24},{4,7,18},{5,8,12},{3,6,100}},
 DMG={{30,40,40},{10,40,40},{30,10,30},{40,80,10},{10,20,40},{10,40,40},{10,40,25},{10,40,40}},
 CRI={{20,30,80},{20,40,100},{24,16,30},{30,50,2},{10,20,30},{20,40,70},{30,50,20},{20,40,100}},
 ML={{15,2},{50,1},{12,7},{6,15},{15,2},{10,4},{8,11},{50,1}};
 static final int[] DR={4,2,4,4,3,3,3,3};
 static final Field LEVEL, TYPE;
 static {try {LEVEL=BaseAbility.class.getDeclaredField("level");TYPE=BaseAbility.class.getDeclaredField("type");LEVEL.setAccessible(true);TYPE.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static int at(int[][] a,int t,int x){return t<0||t>=a.length?0:a[t][x];}
 static int div(int n,int d){return d==0?0:n/d;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void verify(BaseAbility b)throws Exception{
   int t=TYPE.getInt(b),l=LEVEL.getInt(b),n=Math.max(0,l-50);
   check(b.get근거리명중률()==l+div(l,at(HIT,t,0)));
   check(b.get원거리명중률()==l+div(l,at(HIT,t,1)));
   check(b.get마법적중()==div(l,at(HIT,t,2)));
   check(b.get근거리대미지()==div(l,at(DMG,t,0)));
   check(b.get원거리대미지()==div(l,at(DMG,t,1)));
   check(b.get마법대미지()==div(l,at(DMG,t,2)));
   check(b.get근거리치명률()==div(n,at(CRI,t,0)));
   check(b.get원거리치명률()==div(n,at(CRI,t,1)));
   check(b.get마법치명타()==div(n,at(CRI,t,2)));
   check(b.getPVP대미지리덕션()==(l<=60||t<0||t>=DR.length?0:(l-60)/DR[t]));
   check(b.get매직레벨()==Math.min(div(l,at(ML,t,0)),at(ML,t,1)));
 }
 public static void main(String[] args)throws Exception{
   var c=new PcBaseAbilityCache();var p=new L1PcInstance();int count=0;
   for(int t=-1;t<=17;t++)for(int l=0;l<=255;l++){
    p.type=t;p.level=l;var a=c.ValidateBaseData(p);check(TYPE.getInt(a)==t&&LEVEL.getInt(a)==l);verify(a);
    check(a==c.ValidateBaseData(p));c.invalidate();var b=c.ValidateBaseData(p);check(b!=a&&b==c.get());verify(b);count++;
   }
   var failure=new AtomicReference<Throwable>();
   Thread writer=new Thread(()->{try{for(int i=0;i<100000;i++){p.level=i&255;p.type=i&7;c.invalidate();c.refresh(p);}}catch(Throwable e){failure.set(e);}});
   Thread[] readers=new Thread[4];for(int j=0;j<readers.length;j++){readers[j]=new Thread(()->{try{for(int i=0;i<100000;i++)verify(c.ValidateBaseData(p));}catch(Throwable e){failure.set(e);}});readers[j].start();}
   writer.start();writer.join();for(Thread t:readers)t.join();if(failure.get()!=null)throw new AssertionError(failure.get());
   System.out.println("PASS "+count+" class/level cases and 400000 concurrent snapshot reads");
 }
}
'''
with tempfile.TemporaryDirectory() as d:
    work = Path(d)
    stub_file = work / "L1PcInstance.java"
    test_file = work / "BaseParamAudit.java"
    stub_file.write_text(stub, encoding="utf-8")
    test_file.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", d, str(source), str(stub_file), str(test_file)], check=True, timeout=60)
    subprocess.run(["java", "-ea", "-cp", d, "BaseParamAudit"], check=True, timeout=60)
