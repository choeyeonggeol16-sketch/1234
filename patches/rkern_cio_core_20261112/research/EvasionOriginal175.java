import java.util.Locale;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.Attack.Helper.HitEvasionModel;

public class EvasionOriginal175 {
 static double evade(double hit){return (10000-(int)(hit*10000+.5))/100.0;}
 public static void main(String[] args){
  L1Character target=new L1Character();
  System.out.println("ac,ac_dg,curve_only_evade_pct,melee_with_ac_dg_evade_pct");
  for(int defense=0;defense<=250;defense+=10){
   target.getAC().setAc(-defense);
   int ac=Math.abs(target.getAC().getAc()),dg=target.getDg();
   double core=evade(HitEvasionModel.calcHitChance(175,ac,0,0,false,0));
   double melee=evade(HitEvasionModel.calcHitChance(175,ac,dg,0,false,0));
   System.out.printf(Locale.ROOT,"%d,%d,%.2f,%.2f%n",-defense,dg,core,melee);
  }
 }
}
