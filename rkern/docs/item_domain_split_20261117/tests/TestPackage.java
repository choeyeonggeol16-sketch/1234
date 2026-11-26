import l1j.server.server.model.item.function.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.datatables.ItemTable;
public class TestPackage {
 public static void main(String[] args){
  int[] items = new int[]{3000035,4100164,4100165,60036,60037,60038,60039,60040,60041,60042,60043,60044,60045,68078,68079,68080,68081,68082,68083,68084,68085,68086,68087,68088,68089,68090,68091,68092,7020,7021,3000508,4100080,714,715,716,717,721,722,777777};
  for(int itemId:items){for(int c=0;c<9;c++){
   L1PcInstance pc=new L1PcInstance(c,itemId);
   for(int mode=0;mode<5;mode++){
    L1InventoryTransaction.failAtGrant= mode==2 ? 2 : -1;
    L1InventoryTransaction.failDebit= mode==3;
    ItemTable.failAny=mode==1;
    if(mode==4) pc.valid=false;
    System.out.print("CASE:"+itemId+":"+c+":"+mode+" ");
    if(itemId==777777) ClassRewardPackages.clickItem(pc,itemId,pc.input);
    else if(java.util.Set.of(60036,60037,60038,60039,60040,60041,60042,60043,60044,60045,68078,68079,68080,68081,68082,68083,68084,68085,68086,68087,68088,68089,68090,68091,68092,3000035,4100164,4100165).contains(itemId)) ClassRewardPackages.clickItem(pc,itemId,pc.input);
    else LevelAndEquipmentPackages.clickItem(pc,itemId,pc.input);
    System.out.println(); pc.valid=true;
   }
  }}
 }
}
