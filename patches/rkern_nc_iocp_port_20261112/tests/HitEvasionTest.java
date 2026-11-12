import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.character.Attack.Helper.HitEvasionModel;

public class HitEvasionTest {
 static volatile double sink;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static double hit(double h,int ac){return HitEvasionModel.calcHitChance(h,ac,0,0,false,0);}
 static Field field(String n)throws Exception{Field f=HitEvasionModel.class.getDeclaredField(n);f.setAccessible(true);return f;}
 static double[] centers=new double[256],spreads=new double[256],legacyCenters=new double[256];
 static void loadOriginalParameters()throws Exception {
  Object mu=field("MU_SPLINE").get(null),s=field("S_LERP").get(null);
  Method mv=mu.getClass().getDeclaredMethod("value",double.class),sv=s.getClass().getDeclaredMethod("value",double.class);
  mv.setAccessible(true);sv.setAccessible(true);
  for(int ac=0;ac<256;ac++){
   centers[ac]=(Double)mv.invoke(mu,(double)Math.max(64,ac));
   legacyCenters[ac]=(Double)mv.invoke(mu,(double)ac);
   spreads[ac]=Math.max(18,Math.min(120,(Double)sv.invoke(s,(double)ac)));
  }
 }
 static double sigmoid(double x){return 1/(1+Math.exp(-x));}
 static double legacy(double h,int ac){return Math.max(.05,Math.min(.95,sigmoid((h-legacyCenters[ac])/spreads[ac])));}
 static void envelopeOracle(){
  for(double h:new double[]{-10000,-30,0,.125,6,15,30,50,80,124,124.5,134,147,175,300,10000}){
   double minimum=Double.POSITIVE_INFINITY;
   for(int ac=0;ac<256;ac++){
    minimum=Math.min(minimum,(h-centers[ac])/spreads[ac]);
    check(Math.abs(hit(h,ac)-sigmoid(minimum))<1e-12,"independent exhaustive envelope oracle H="+h+" AC="+ac);
   }
  }
  check(hit(0,0)<.61&&hit(0,0)>.60,"low AC tangent explosion retained");
  check(hit(30,150)<=hit(30,120),"reported H30 AC120->150 reversal retained");
  System.out.println("PASS exhaustive weaker-armor oracle at 4096 points; H0/AC0 ~60.38%, H30/AC120 and AC150 ~6.3901%");
 }
 static void invariants(){
  long pairs=0;
  for(int q=-1200;q<=1600;q++){
   double h=q/4.0,previous=1;
   for(int ac=0;ac<256;ac++){
    double p=hit(h,ac);
    check(Double.isFinite(p)&&p>=0&&p<=1,"invalid probability");
    check(p<=previous+1e-12,"AC monotonicity H="+h+" AC="+ac);previous=p;
    check(p+1e-12>=hit(h-.25,ac),"H monotonicity H="+h+" AC="+ac);pairs++;
   }
  }
  for(int ac:new int[]{Integer.MIN_VALUE,-256,-255,-152,-64,-1,0,7,63,64,65,120,150,152,255,256,Integer.MAX_VALUE})
   check(Double.isFinite(hit(147,ac)),"AC int overflow");
  check(HitEvasionModel.defenseAc(Integer.MIN_VALUE)==255,"signed AC underflow");
  check(HitEvasionModel.defenseAc(Integer.MAX_VALUE)==0,"positive AC overflow");
  check(HitEvasionModel.defenseAc(7)==0&&HitEvasionModel.defenseAc(-7)==7,"positive AC became armor");
  check(hit(0,255)<.05&&hit(300,0)>.95,"5/95 policy retained");
  check(hit(-10000,255)==0&&hit(10000,0)==1,"finite model 0/100 endpoints");
  check(hit(Double.NaN,120)==0,"NaN H");
  check(hit(Double.NEGATIVE_INFINITY,120)==0&&hit(Double.POSITIVE_INFINITY,120)==1,"infinite H");
  for(int i=0;i<100000;i++){
   check(!HitEvasionModel.rollHit(0)&&!HitEvasionModel.rollHit(-1)&&!HitEvasionModel.rollHit(Double.NaN),"zero/invalid hit rolled true");
   check(HitEvasionModel.rollHit(1)&&HitEvasionModel.rollHit(2),"100% missed");
  }
  check(HitEvasionModel.rollHit(.999951)&&!HitEvasionModel.rollHit(.000049),"basis-point rounding boundary");
  int hits=0;for(int i=0;i<100000;i++)if(HitEvasionModel.rollHit(.5))hits++;
  check(hits>49000&&hits<51000,"50% roll distribution "+hits);
  System.out.println("PASS "+pairs+" AC/H grid points, finite probability and signed AC extremes; 100000 endpoint rolls, basis-point rounding and 50% distribution");
 }
 static void evasion(){
  double p=hit(147,140);
  check(Math.abs(HitEvasionModel.calcHitChance(147,140,10,90,false,0)-(p-.075))<1e-12,"melee DG/ER selection");
  check(Math.abs(HitEvasionModel.calcHitChance(147,140,90,10,true,0)-(p-.075))<1e-12,"ranged ER/DG selection");
  check(Math.abs(HitEvasionModel.calcHitChance(147,140,10,0,false,.1)-(p-.175))<1e-12,"Maan added twice");
  check(HitEvasionModel.calcHitChance(0,255,200,0,false,0)==0,"evasion zero endpoint retained minimum");
  System.out.println("PASS unchanged DG/ER routing, one Maan adjustment, evasion can reach 0%");
 }
 static void metrics(Path csv)throws Exception {
  List<String> lines=Files.readAllLines(csv);int[] actual=new int[33];
  for(int i=0;i<33;i++){String[] columns=lines.get(i+1).split(",");check(Integer.parseInt(columns[0])==120+i,"measurement AC order");actual[i]=Integer.parseInt(columns[1]);}
  for(int h:new int[]{134,147})for(boolean before:new boolean[]{true,false}){
   double sum=0,sq=0,slope=0;
   for(int i=0;i<33;i++){
    double p=before?legacy(h,120+i):hit(h,120+i),e=p-actual[i]/1000.0;
    sum+=e;sq+=e*e;slope+=(i-16)*p;
   }
   double denominator=0;for(int i=0;i<33;i++)denominator+=(i-16)*(i-16);
   System.out.printf(Locale.ROOT,"METRIC %s H=%d bias=%.6f pp RMSE=%.6f pp slope=%.6f pp/AC%n",before?"before":"after",h,sum/33*100,Math.sqrt(sq/33)*100,slope/denominator*100);
  }
  System.out.println("No H=-13 correction or JP2016 observed counts are present in production; displayed JP hit != verified RKern internal H.");
 }
 static void reload()throws Exception {
  double oldMin=HitEvasionModel.S_MIN,oldMax=HitEvasionModel.S_MAX;
  try {
   double a=hit(147,140);
   HitEvasionModel.S_MIN=80;HitEvasionModel.rebuildLut();double b=hit(147,140);
   check(Math.abs(a-b)>1e-3,"reload fixture ineffective");
   AtomicReference<Throwable> failure=new AtomicReference<>();List<Thread> threads=new ArrayList<>();
   for(int n=0;n<4;n++){
    Thread t=new Thread(()->{try{for(int i=0;i<100000;i++){double p=hit(147,140);check(Math.abs(p-a)<1e-12||Math.abs(p-b)<1e-12,"mixed reload snapshot");}}catch(Throwable e){failure.set(e);}});
    threads.add(t);t.start();
   }
   for(int i=0;i<30;i++){HitEvasionModel.S_MIN=(i%2==0?18:80);HitEvasionModel.rebuildLut();}
   for(Thread t:threads){t.join(10000);check(!t.isAlive(),"reload reader hung");}
   if(failure.get()!=null)throw new AssertionError(failure.get());
   double before=hit(147,140);HitEvasionModel.S_MIN=Double.NaN;
   try{HitEvasionModel.rebuildLut();throw new AssertionError("invalid reload accepted");}catch(IllegalArgumentException expected){}
   check(hit(147,140)==before,"invalid reload replaced active table");
  }finally{HitEvasionModel.S_MIN=oldMin;HitEvasionModel.S_MAX=oldMax;HitEvasionModel.rebuildLut();}
  System.out.println("PASS 400000 concurrent reads/30 reloads; one immutable table publication; rejected reload leaves active table intact");
 }
 static void hotPath()throws Exception {
  for(int i=0;i<200000;i++)sink+=hit(i%301,i%256);
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
  long tid=Thread.currentThread().threadId(),bytes=bean.getThreadAllocatedBytes(tid),start=System.nanoTime();double sum=0;
  for(int i=0;i<1000000;i++)sum+=hit(i%301,i%256);
  long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(tid)-bytes;sink=sum;
  System.out.printf(Locale.ROOT,"MEASURE warmed local calculation: %.2f ns/call, %d allocated bytes/1000000 calls; no live server load claim%n",elapsed/1000000.0,allocated);
  check(allocated<4096,"new per-hit heap allocations");
 }
 public static void main(String[] args)throws Exception {
  loadOriginalParameters();envelopeOracle();invariants();evasion();metrics(Path.of(args[0]));reload();hotPath();
  System.out.println("PASS production HitEvasionModel; low-AC boundary clamp and monotone envelope are conservative repairs, not a recovered official formula.");
 }
}
