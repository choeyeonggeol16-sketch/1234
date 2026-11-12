import java.lang.reflect.*;
import l1j.server.server.model.character.Attack.Helper.HitEvasionModel;
public class HitBaseline {
 public static void main(String[] args)throws Exception {
  for(String n:new String[]{"MU_KNOT","S_KNOT"}) {
   Field f=HitEvasionModel.class.getDeclaredField(n);f.setAccessible(true);
   System.out.println(n+"="+java.util.Arrays.toString((double[])f.get(null)));
  }
  for(int h:new int[]{0,6,15,30,50,124,134,147,175,300}) {
   System.out.print("H="+h);
   for(int ac:new int[]{0,7,63,64,80,102,120,140,150,152,255})
    System.out.printf(" AC%d=%.8f",ac,HitEvasionModel.calcHitChance(h,ac,0,0,false,0));
   System.out.println();
  }
  int acIncreases=0,hitDecreases=0;
  for(int h=0;h<=300;h++)for(int ac=1;ac<=255;ac++)
   if(HitEvasionModel.calcHitChance(h,ac,0,0,false,0)>HitEvasionModel.calcHitChance(h,ac-1,0,0,false,0)+1e-12)acIncreases++;
  for(int h=1;h<=300;h++)for(int ac=0;ac<=255;ac++)
   if(HitEvasionModel.calcHitChance(h,ac,0,0,false,0)<HitEvasionModel.calcHitChance(h-1,ac,0,0,false,0)-1e-12)hitDecreases++;
  System.out.println("AC monotonicity violations="+acIncreases+", H violations="+hitDecreases);
 }
}
