package l1j.server.server.model.item;
import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
public class CompareRegistry {
  public static void main(String[] args) {
    java.util.logging.Logger.getLogger(ItemEffectRegistryBaseline.class.getName()).setLevel(java.util.logging.Level.OFF);
    java.util.logging.Logger.getLogger(ItemEffectRegistryCandidate.class.getName()).setLevel(java.util.logging.Level.OFF);
    Random r = new Random(68202618);
    int checks=0;
    for (int set=0;set<450;set++) {
       var a=new ItemEffectRegistryBaseline.Def(); var b=new ItemEffectRegistryCandidate.Def();
       for(int i=0,n=r.nextInt(24);i<n;i++) {
          int clazz=r.nextInt(10)-2, enchant=r.nextInt(8)-1, value=r.nextInt(41)-20;
          ItemStat stat = r.nextInt(13)==0 ? null : ItemStat.values()[r.nextInt(ItemStat.values().length)];
          a.allBonuses().add(new ItemEffectRegistryBaseline.Bonus(clazz,enchant,stat,value));
          b.allBonuses().add(new ItemEffectRegistryCandidate.Bonus(clazz,enchant,stat,value));
       }
       a.buildIndex();b.buildIndex();
       for(int clazz=-2;clazz<=9;clazz++) for(int enchant=-2;enchant<=7;enchant++) {
          for (int section=0;section<3;section++) {
             var one=a.aggregatedStatsFor(ItemEffectRegistryBaseline.TooltipSection.values()[section],clazz,enchant);
             var two=b.aggregatedStatsFor(ItemEffectRegistryCandidate.TooltipSection.values()[section],clazz,enchant);
             if(!one.equals(two))throw new AssertionError("registry mismatch "+set+" "+clazz+" "+enchant+" "+one+" "+two);
             checks++;
          }
          var pc1=new L1PcInstance();var pc2=new L1PcInstance();
          a.apply(pc1,clazz,enchant,1); b.apply(pc2,clazz,enchant,1);
          if(pc1.value!=pc2.value)throw new AssertionError("apply mismatch");
          checks++;
       }
    }
    System.out.println("Registry baseline/candidate comparisons="+checks+" PASS");
  }
}
