import java.util.Locale;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.Attack.Helper.HitEvasionModel;

/** Read-only calculation using the published production classes. */
public class EvasionByAc {
 static double evade(double hit){return (10000-(int)(hit*10000+.5))/100.0;}
 public static void main(String[] args){
  L1Character target=new L1Character();target.getAbility().setBaseDex(25);
  System.out.println("ac,internal_h,ac_dg,total_er_at_dex25,curve_only_evade_pct,melee_with_ac_dg_evade_pct,ranged_dex25_evade_pct,melee_gain_per_10ac_pp");
  for(int h:new int[]{0,30,124,134,147,175}){
   double previous=Double.NaN;
   for(int ac=0;ac<=250;ac+=10){
    target.getAC().setAc(-ac);
    int modelAc=HitEvasionModel.defenseAc(target.getAC().getAc()),dg=target.getDg(),er=target.getTotalER();
    double core=evade(HitEvasionModel.calcHitChance(h,modelAc,0,0,false,0));
    double melee=evade(HitEvasionModel.calcHitChance(h,modelAc,dg,er,false,0));
    double ranged=evade(HitEvasionModel.calcHitChance(h,modelAc,dg,er,true,0));
    System.out.printf(Locale.ROOT,"%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f%n",-ac,h,dg,er,core,melee,ranged,Double.isNaN(previous)?0:melee-previous);
    previous=melee;
   }
  }
 }
}
