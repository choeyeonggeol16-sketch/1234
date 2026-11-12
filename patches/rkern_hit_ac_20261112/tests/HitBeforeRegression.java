import l1j.server.server.model.character.Attack.Helper.*;
public class HitBeforeRegression {
 public static void main(String[] args)throws Exception {
  String which=args[0];double value;
  switch(which){
  case "floor":value=HitEvasionModel.calcHitChance(0,255,0,0,false,0);if(value>=.05)throw new AssertionError("forced 5% floor: "+value);break;
  case "ceiling":value=HitEvasionModel.calcHitChance(300,0,0,0,false,0);if(value<=.95)throw new AssertionError("forced 95% ceiling: "+value);break;
  case "low-ac":value=HitEvasionModel.calcHitChance(0,0,0,0,false,0);if(value>.61)throw new AssertionError("unbounded first-knot extrapolation: "+value);break;
  case "monotonic":
   double before=HitEvasionModel.calcHitChance(30,120,0,0,false,0),after=HitEvasionModel.calcHitChance(30,150,0,0,false,0);
   if(after>before)throw new AssertionError("stronger armor increased hit: "+before+" -> "+after);break;
  case "overflow":
   HitPathTest.Pc p=new HitPathTest.Pc();p.hitUp=Integer.MAX_VALUE;p.hitRate=Integer.MAX_VALUE;
   int h=L1AttackHelper.calcPcHitH(p,false,Integer.MAX_VALUE,Integer.MAX_VALUE,0);
   if(h!=300)throw new AssertionError("positive aggregate overflow changed H to "+h);break;
  default:throw new IllegalArgumentException(which);
  }
  System.out.println("PASS "+which);
 }
}
