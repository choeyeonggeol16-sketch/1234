package l1j.server.server.model.item.function;
import java.util.List;import java.util.ArrayList;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public class TestPhase33 {
 static long checks;
 static void check(boolean pass,String detail){checks++;if(!pass)throw new AssertionError(detail);}
 interface Call {boolean invoke(L1PcInstance pc);}
 static void runCase(String mode,int template, int quantity, int enchant,int expectedEnchant, boolean identified,
    Call baseline,Call improved) {
  String[] variants={"fresh","merged","reject","throwCheck","throwStore"};
  for(String v:variants){
   List<String> outputs=new ArrayList<>();
   for(int old=0;old<2;old++){
    L1World.getInstance().clear();SC_ServerMessage.events.clear();
    L1PcInstance pc=new L1PcInstance();
    var inv=pc.getInventory();
    if(v.equals("merged")){
     inv.stack=true;L1ItemInstance existing=new L1ItemInstance(3,template);
     existing.setCount(7);inv.entries.put(3,existing);L1World.getInstance().storeObject(existing);
    }
    if(v.equals("reject"))inv.reject=1;
    if(v.equals("throwCheck"))inv.failCheck=true;
    if(v.equals("throwStore"))inv.failStore=true;
    boolean result=false;String error="";
    try {result=(old==0?baseline:improved).invoke(pc);}catch(Exception x){error=x.getMessage();}
    check(inv.checkCalls==1,mode+":"+v+" check calls");
    int expectedWorld=(v.equals("fresh")?1:v.equals("merged")?1:0);
    if(old==1)check(L1World.getInstance().size()==expectedWorld,mode+":"+v+" orphan world object");
    else if(!v.equals("fresh"))check(L1World.getInstance().size()>expectedWorld,mode+":"+v+" legacy precondition");
    if(v.equals("fresh")){
      check(inv.entries.size()==1,mode+" expected insert");
      var item=inv.entries.values().iterator().next();
      check(item.getEnchantLevel()==expectedEnchant,mode+" enchant");
      check(item.isIdentified()==identified,mode+" identified");
      check(item.getCount()==quantity,mode+" count");
      if(mode.equals("sealed")){check(item.getBless()==7,"sealed bless");check(inv.saveCalls==1 && inv.updateCalls==1,"sealed bless persisted");}
    }
    if(v.equals("merged")){check(inv.entries.size()==1,"merged item count");check(inv.entries.get(3).getCount()==7+quantity,"merged count");}
    outputs.add(result+"|"+error+"|"+SC_ServerMessage.events+"|"+inv.entries.size()+"|"+inv.storeCalls+"|"+inv.updateCalls+"|"+inv.saveCalls);
   }
   check(outputs.get(0).equals(outputs.get(1)),mode+":"+v+" behavior changed: "+outputs);
  }
 }
 public static void main(String[] args){
  for(int template:new int[]{5,100,10005})for(int qty:new int[]{1,2,50}){
    runCase("normal",template,qty,0,0,true,
       pc->ItemCreationBaseline.createNewItem(pc,template,qty),
       pc->ItemCreationItems.createNewItem(pc,template,qty));
    runCase("GM",template,qty,0,0,true,
       pc->ItemCreationBaseline.createGMItem(pc,template,qty),
       pc->ItemCreationItems.createGMItem(pc,template,qty));
    for(int enchant:new int[]{-1,0,5,9})
      runCase("enchant",template,qty,enchant,enchant,true,
       pc->ItemCreationBaseline.createNewItem2(pc,template,qty,enchant),
       pc->ItemCreationItems.createNewItem2(pc,template,qty,enchant));
    runCase("sealed",template,qty,4,4,true,
       pc->SealedEquipmentBaseline.UseSealedItem(pc,template,qty,4,7,2,false),
       pc->SealedEquipmentRewards.UseSealedItem(pc,template,qty,4,7,2,false));
  }
  L1PcInstance pc=new L1PcInstance();
  check(!ItemCreationItems.createNewItem(pc,1,0),"zero amount must not send successful grant");
  check(!ItemCreationItems.createNewItem(pc,-1,1),"invalid template must fail");
  System.out.println("Phase33 typed production + legacy execution comparison passed: "+checks+" checks");
 }
}