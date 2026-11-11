import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import l1j.server.server.model.SpellDamageFormula;
public class SpellFormulaTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int bOracle(int type,int wis,long mr) {
  BigInteger c=BigInteger.valueOf(SpellDamageFormula.classCoefficient100(type)-2L*(wis/2)).pow(10);
  BigInteger r=BigInteger.valueOf(mr).pow(3);
  int lo=0,hi=121;
  while(lo+1<hi){int m=(lo+hi)/2;if(BigInteger.valueOf(m).pow(10).multiply(r).compareTo(c)<=0)lo=m;else hi=m;}
  return lo;
 }
 static int full(int d,int stat,int er,int type,int wis,long mr,int md,int dr,boolean crit) {
  return SpellDamageFormula.calculate(d,SpellDamageFormula.coefficientA100(stat,0,0,er),
   SpellDamageFormula.coefficientB100(type,wis,mr),md,dr,0,crit);
 }
 public static void main(String[] args) {
  int[] er={-100,0,50,100},normal={44,38,36,33},critical={58,50,46,41};
  for(int i=0;i<er.length;i++){
   check(full(9,45,er[i],6,14,50,17,1,false)==normal[i],"normal golden "+er[i]);
   check(full(9,45,er[i],6,14,50,17,1,true)==critical[i],"critical golden "+er[i]);
  }
  check(full(9,12,100,6,14,50,0,0,false)==0,"ER100 base cancellation");
  check(full(9,12,100,6,14,50,8,1,false)==7,"MD 8 / DR 1 outside ER");
  check(full(9,12,100,6,14,50,13,5,false)==8,"MD 13 / DR 5 outside ER");
  System.out.println("PASS report normal/critical golden vectors and ER100 MD/DR cases");
  for(int stat=0;stat<=127;stat++)for(int bonus=-20;bonus<=100;bonus++)for(int e:new int[]{-150,-100,-99,0,50,99,100,150}){
   BigDecimal a=BigDecimal.ONE.add(new BigDecimal("0.09375").multiply(BigDecimal.valueOf(stat+bonus-12L)))
    .subtract(BigDecimal.valueOf(Math.max(-100,Math.min(100,e)),2));
   int expected=Math.max(0,a.movePointRight(2).setScale(0,RoundingMode.FLOOR).intValueExact());
   check(SpellDamageFormula.coefficientA100(stat,31+bonus,31,e)==expected,"A exact hundredths");
  }
  check(SpellDamageFormula.coefficientA100(45,50,50,0)==409,"MB/ML duplicated");
  System.out.println("PASS 123904 A-coefficient vectors against independent BigDecimal floor, SP subtraction, ER clamps");
  int[] factors={230,240,220,200,220,240,220,240};
  for(int t=0;t<8;t++)check(SpellDamageFormula.classCoefficient100(t)==factors[t],"defender class mapping");
  long[] mrs={10,11,19,20,49,50,99,100,101,200,300,1024,59049,100000000,Long.MAX_VALUE};
  int count=0;
  for(int t=0;t<8;t++)for(int wis=0;wis<=199;wis++)for(long mr:mrs)if(SpellDamageFormula.supports(t,wis,mr)) {
   check(SpellDamageFormula.coefficientB100(t,wis,mr)==bOracle(t,wis,mr),"B exact boundary t="+t+" wis="+wis+" mr="+mr);count++;
  }
  Random rng=new Random(2016);
  for(int i=0;i<10000;i++){
   int t=rng.nextInt(8),wis=rng.nextInt(200);long mr=10+rng.nextInt(Integer.MAX_VALUE-10);
   if(SpellDamageFormula.supports(t,wis,mr))check(SpellDamageFormula.coefficientB100(t,wis,mr)==bOracle(t,wis,mr),"B random oracle");
  }
  check(SpellDamageFormula.coefficientB100(1,24,59049)==8,"exact 3^10 boundary lost one hundredth");
  check(SpellDamageFormula.coefficientB100(1,0,10)==120,"research B>1 unexpectedly clamped");
  System.out.println("PASS "+count+" B boundary/class/WIS vectors and 10000 random vectors against BigInteger oracle");
  for(long mr:new long[]{-10,-1,0,1,8,9}){
   check(!SpellDamageFormula.supports(1,20,mr),"low MR admitted");
   try {SpellDamageFormula.coefficientB100(1,20,mr);throw new AssertionError("low MR not rejected");}catch(IllegalArgumentException expected){}
  }
  check(!SpellDamageFormula.supports(8,20,50)&&!SpellDamageFormula.supports(-1,20,50),"unknown class admitted");
  check(!SpellDamageFormula.supports(3,200,50),"nonpositive C admitted");
  check(SpellDamageFormula.coefficientB100(1,20,50)==SpellDamageFormula.coefficientB100(1,21,50),"odd WIS floor");
  check(SpellDamageFormula.calculate(Integer.MAX_VALUE,Integer.MAX_VALUE,120,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,true)==Integer.MAX_VALUE,"positive overflow saturation");
  check(SpellDamageFormula.calculate(0,0,0,0,Integer.MAX_VALUE,0,true)==0,"negative floor policy");
  check(SpellDamageFormula.calculate(3,100,63,0,0,0,true)==2,"critical after A before B floor changed");
  System.out.println("PASS low-MR/unknown-class exclusion, WIS floor, critical order and overflow bounds");
 }
}
