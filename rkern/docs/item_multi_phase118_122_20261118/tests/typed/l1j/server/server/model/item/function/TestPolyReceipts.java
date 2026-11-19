package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class TestPolyReceipts {
 static int checked;
 static void check(boolean b,String m){checked++;if(!b)throw new AssertionError(m);}
 static void finish(){ItemReceiptOperations.finish(true);}
 public static void main(String[] args){
  int[] fixed={4100284,4100285,4100286,4100287,4100288,4100289,4200100,4200400,5922901,41143,41144,41145,30057,30058,30059,8000,8001,8002};
  for(int id:fixed){
   L1PcInstance p=new L1PcInstance();p.level=82;L1ItemInstance item=new L1ItemInstance();
   check(TransformationScrollItems.handleFixedPolyPotion(new ItemActionContext(p,item,id))==ItemActionResult.STOP,"fixed admitted");
   check(item.count==1,"fixed premature consume");int n=PotionEffects.morphCount;finish();
   check(item.count==0 && PotionEffects.morphCount==n+1,"fixed commit effect");
   p=new L1PcInstance();p.level=83;item=new L1ItemInstance();
   check(TransformationScrollItems.handleFixedPolyPotion(new ItemActionContext(p,item,id))==ItemActionResult.STOP,"fixed level denial");
   check(item.count==1 && ItemReceiptOperations.receipt==null,"fixed denial no receipt");
  }
  for(int id:new int[]{210097,210098,210099,210100,210101,210102,210103,210116,210117}){
   L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();int n=PotionEffects.morphCount;
   check(TransformationScrollItems.handleLevelPolyScroll(new ItemActionContext(p,item,id))==ItemActionResult.STOP,"level admitted");
   check(item.count==1,"level premature debit");ItemReceiptOperations.finish(false);
   check(item.count==1 && PotionEffects.morphCount==n,"level rollback");
   check(TransformationScrollItems.handleLevelPolyScroll(new ItemActionContext(p,item,id))==ItemActionResult.STOP,"level retry");
   finish();check(item.count==0 && PotionEffects.morphCount==n+1,"level commit");
  }
  for(int map:new int[]{3,7783,12152,12149,12154,12358,12153,12146,12147,12148,12258,12150,12257}){
   L1PcInstance p=new L1PcInstance();p.mapId=map;L1ItemInstance item=new L1ItemInstance();
   check(TransformationScrollItems.handleTrainingPolyScroll(new ItemActionContext(p,item,42655))==ItemActionResult.STOP,"training map permitted");
   ItemReceiptOperations.finish(false);check(item.count==1,"training rollback");
  }
  {L1PcInstance p=new L1PcInstance();p.mapId=1;L1ItemInstance item=new L1ItemInstance();
   check(TransformationScrollItems.handleTrainingPolyScroll(new ItemActionContext(p,item,42655))==ItemActionResult.STOP,"training map denied");
   check(ItemReceiptOperations.receipt==null,"training denied receipt");}
  {L1PcInstance p=new L1PcInstance();p.level=83;L1ItemInstance item=new L1ItemInstance();
   check(TransformationScrollItems.handleTrainingPolyScroll(new ItemActionContext(p,item,42655))==ItemActionResult.STOP,"training level denied");
   check(ItemReceiptOperations.receipt==null,"training level receipt");}
  System.out.println("PASS phase121 polymorph production type+receipt assertions="+checked);
 }
}
