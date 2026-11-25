"""Exercise production CPCParamDB against independent L1 class/level formulas."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[2]
model=root/"src/l1j/server/server/model"
source=root/"src/l1j/server/server/utils/CPCParamDB.java"
pc=(model/"Instance/L1PcInstance.java").read_text(encoding="utf-8")
creature=(model/"L1Character.java").read_text(encoding="utf-8")
assert not (model/"character/Utill/PcBaseAbilityCache.java").exists()
assert "getBaseAbility()" not in pc and "getBaseCache()" not in pc
assert "private int GetBaseParam(int field)" in creature
harness=r'''
import l1j.server.server.utils.CPCParamDB;
import java.util.concurrent.atomic.AtomicReference;
public class BaseParamAudit {
 static final int[][] HIT={{4,6,20},{3,6,100},{5,3,16},{0,0,8},{3,4,24},{4,7,18},{5,8,12},{3,6,100}},
 DMG={{30,40,40},{10,40,40},{30,10,30},{40,80,10},{10,20,40},{10,40,40},{10,40,25},{10,40,40}},
 CRI={{20,30,80},{20,40,100},{24,16,30},{30,50,2},{10,20,30},{20,40,70},{30,50,20},{20,40,100}},
 ML={{15,2},{50,1},{12,7},{6,15},{15,2},{10,4},{8,11},{50,1}};
 static final int[] DR={4,2,4,4,3,3,3,3};
 static int at(int[][] a,int t,int x){return t<0||t>=a.length?0:a[t][x];}
 static int div(int n,int d){return d==0?0:n/d;}
 static void check(int field,int t,int l,int expect){
   int got=CPCParamDB.GetBaseParam(field,t,l);
   if(got!=expect)throw new AssertionError(field+" "+t+" "+l+" "+got+" != "+expect);
 }
 static void verify(int t,int l){
   int n=l>50?l-50:0,lv=l-60;
   check(CPCParamDB.BASE_PHIT,t,l,l+div(l,at(HIT,t,0)));
   check(CPCParamDB.BASE_RANGED_PHIT,t,l,l+div(l,at(HIT,t,1)));
   check(CPCParamDB.BASE_MHIT,t,l,div(l,at(HIT,t,2)));
   check(CPCParamDB.BASE_PATTACK,t,l,div(l,at(DMG,t,0)));
   check(CPCParamDB.BASE_RANGED_PATTACK,t,l,div(l,at(DMG,t,1)));
   check(CPCParamDB.BASE_MATTACK,t,l,div(l,at(DMG,t,2)));
   check(CPCParamDB.BASE_PCRITICAL,t,l,div(n,at(CRI,t,0)));
   check(CPCParamDB.BASE_RANGED_PCRITICAL,t,l,div(n,at(CRI,t,1)));
   check(CPCParamDB.BASE_MCRITICAL,t,l,div(n,at(CRI,t,2)));
   check(CPCParamDB.BASE_PVP_DR,t,l,lv<=0||t<0||t>=DR.length?0:lv/DR[t]);
   check(CPCParamDB.BASE_MAGIC_LEVEL,t,l,Math.min(div(l,at(ML,t,0)),at(ML,t,1)));
 }
 public static void main(String[] args)throws Exception{
   int count=0;
   for(int t=-1;t<=17;t++){
     for(int l=-128;l<=255;l++){verify(t,l);count++;}
     for(int l:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE}){verify(t,l);count++;}
   }
   var failure=new AtomicReference<Throwable>();
   Thread[] readers=new Thread[4];
   for(int j=0;j<readers.length;j++){
     final int offset=j;
     readers[j]=new Thread(()->{try{for(int i=0;i<100000;i++)verify((i+offset)&7,i&127);}catch(Throwable e){failure.set(e);}});
     readers[j].start();
   }
   for(Thread t:readers)t.join();
   if(failure.get()!=null)throw new AssertionError(failure.get());
   System.out.println("PASS "+count+" class/level cases and 400000 concurrent immutable row reads (11 parameters each)");
 }
}
'''
with tempfile.TemporaryDirectory() as d:
    f=Path(d)/"BaseParamAudit.java"
    f.write_text(harness,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-d",d,str(source),str(f)],check=True,timeout=60)
    subprocess.run(["java","-ea","-cp",d,"BaseParamAudit"],check=True,timeout=60)
