package l1j.server.server.model.character.Attack.Helper;
/** Test-only counters: production CAttackAction, substituted model boundary. */
public final class HitEvasionModel {
 public static int calculations,rolls,defenseCalls,ac,dg,er;public static double h,bonus;public static boolean ranged,result;
 public static void reset(boolean next){calculations=rolls=defenseCalls=0;result=next;}
 public static int defenseAc(int raw){defenseCalls++;return (int)Math.max(0L,Math.min(255L,-(long)raw));}
 public static double calcHitChance(double value,int defense,int dodge,int evade,boolean isRanged,double extra){
  calculations++;h=value;ac=defense;dg=dodge;er=evade;ranged=isRanged;bonus=extra;return .5;
 }
 public static boolean rollHit(double chance){rolls++;return result;}
}
