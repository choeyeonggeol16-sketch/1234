package l1j.server.server.model.item.function;
import java.util.*;import l1j.server.server.model.Instance.*;
public class CompareConsignment {
 static int cases;
 static void compare(int mode){
   var a=make(mode); var b=make(mode);
   var saleA=new L1ItemInstance(); var saleB=new L1ItemInstance();
   var ticketA=new L1ItemInstance(); var ticketB=new L1ItemInstance();
   ticketA.item.itemId=474747;ticketB.item.itemId=474747;
   if(mode==1){saleA.item.tradable=false;saleB.item.tradable=false;}
   if(mode==2){saleA.durability=1;saleB.durability=1;}
   if(mode==3){saleA.equipped=true;saleB.equipped=true;}
   if(mode==4){saleA.bless=4;saleB.bless=4;}
   if(mode==5){saleA.endTime=new Object();saleB.endTime=new Object();}
   if(mode==6){saleA.item.maxUseTime=1;saleB.item.maxUseTime=1;}
   if(mode==7){a.inventory.ticketCount=0;b.inventory.ticketCount=0;}
   if(mode==8){saleA.item.type2=-1;saleB.item.type2=-1;}
   if(mode==9){saleA.item.type2=0; saleB.item.type2=0;saleA.item.type=10;saleB.item.type=10;}
   if(mode==10){saleA.item.type2=0;saleB.item.type2=0;saleA.item.type=2;saleB.item.type=2;saleA.item.itemId=447016;saleB.item.itemId=447016;}
   if(mode==11){saleA.item.type2=1;saleB.item.type2=1;saleA.item.itemId=875640331;saleB.item.itemId=875640331;}
   BaselineConsignmentItemActions.register(a,ticketA,saleA);
   CandidateConsignmentItemActions.register(b,ticketB,saleB);
   if(!a.log.equals(b.log))throw new AssertionError("mode="+mode+" "+a.log+" != "+b.log);
   cases++;
 }
 static L1PcInstance make(int mode){return new L1PcInstance();}
 public static void main(String[] args){for(int i=0;i<100;i++)for(int m=0;m<12;m++)compare(m);System.out.println("Consignment production classes type-compiled and outcomes="+cases+" PASS");}
}
