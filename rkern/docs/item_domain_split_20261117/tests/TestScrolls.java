package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class TestScrolls {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
   L1PcInstance pc=new L1PcInstance(1,7010);
   int[] ids={7010,7011,8010,8011,5922910,5922911,622910,633910,885544};
   for(int i=0;i<ids.length;i++){
     L1PcInstance.events.setLength(0);
     L1ItemInstance scroll=new L1ItemInstance(new l1j.server.server.templates.L1Item(ids[i]),1);
     L1ItemInstance target=new L1ItemInstance(new l1j.server.server.templates.L1Item(777),1);
     SpecialEnchantmentItems.use(pc,scroll,target,ids[i]);
     String expected="ENCH:"+i+":"+ids[i]+":777|";
     check(L1PcInstance.events.toString().equals(expected));
   }
   int[] buffs={65648,40879,773355};
   for(int itemId:buffs){
     L1PcInstance.events.setLength(0);
     L1ItemInstance item=new L1ItemInstance(new l1j.server.server.templates.L1Item(itemId),1);
     DurableBuffScrollItems.use(pc,item,itemId);
     String result=L1PcInstance.events.toString();
     check(result.startsWith("DEBIT:"+itemId+"|HAS:"));
     check(result.endsWith("DONE|"));
     if(itemId==773355) check(result.contains("SET:102:3600000|") && result.contains("WIS:1|") && result.contains("ICON:102:5087:3600:true|"));
     else check(result.contains("REMOVE:") && result.contains("CAST:"));
   }
   for(int itemId:buffs){
     pc.skillActive=false;L1PcInstance.events.setLength(0);
     var it=new L1ItemInstance(new l1j.server.server.templates.L1Item(itemId),1);
     DurableBuffScrollItems.use(pc,it,itemId);
     check(!L1PcInstance.events.toString().contains("REMOVE:"));
   }
   try {SpecialEnchantmentItems.use(pc,null,null,123);throw new AssertionError();}
   catch (IllegalArgumentException ok){}
   try {DurableBuffScrollItems.use(pc,null,123);throw new AssertionError();}
   catch (IllegalArgumentException ok){}
   System.out.println("PASS 9 enchant scroll + 3 durable skill item actions, active/inactive status, ordering");
 }
}
