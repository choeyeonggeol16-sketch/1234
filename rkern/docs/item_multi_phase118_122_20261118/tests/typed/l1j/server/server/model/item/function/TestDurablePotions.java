package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.Faults;
import l1j.server.server.model.character.skill.L1SkillId;
public class TestDurablePotions {
 static int tests;
 static void chk(boolean b,String msg){tests++; if(!b) throw new AssertionError(msg);}
 static L1PcInstance pc(){L1PcInstance p=new L1PcInstance();p.knight=true;p.wizard=true;p.elf=true;p.crown=true;p.darkwizard=true;return p;}
 public static void main(String[] args) {
  java.util.logging.Logger.getLogger(MovementPotionEffects.class.getName()).setLevel(java.util.logging.Level.OFF);
  java.util.logging.Logger.getLogger(ManaPotionEffects.class.getName()).setLevel(java.util.logging.Level.OFF);
  java.util.logging.Logger.getLogger(ProtectivePotionEffects.class.getName()).setLevel(java.util.logging.Level.OFF);
  int[] move={40030,40013,7006,40018,40039,40040,41338,41261,41262,41268,41269,41271,41272,41273,41342,30067,140013,140018,30158,875640293,40014,140014,41415,30073,40068,140068,210110,30076,712,40031,30075,40733,713,210036,30077};
  int[] mana={210115,40032,40041,41344,30083,40015,140015,40736,41142,210114,30089,40016,140016,210113,40025};
  for(int mask=0;mask<2048;mask++) {
   Faults.failMask=mask;
   for(int id:move) {
    L1PcInstance p=pc();L1ItemInstance item=new L1ItemInstance();int before=Faults.calls;
    chk(MovementPotionConsumables.use(p,item,id),"movement accepted "+id);
    chk(item.count==1,"debit before commit");
    chk(ItemReceiptOperations.receipt!=null,"missing movement receipt");
    if ((mask&1)==0 && id==40013) chk(!p.hasSkillEffect(L1SkillId.STATUS_HASTE),"movement effect before commit");
    if(mask%3==0){ItemReceiptOperations.finish(false);chk(item.count==1,"rollback consumed");chk(p.buffs.isEmpty(),"rollback effect");}
    else {ItemReceiptOperations.finish(true);chk(item.count==0 && p.delayCount==1,"commit not debited or missing item delay");}
   }
   for(int id:mana) {
    L1PcInstance p=pc();L1ItemInstance item=new L1ItemInstance();
    chk(ManaProtectionPotionConsumables.use(p,item,id),"mana accepted "+id);
    chk(item.count==1 && ItemReceiptOperations.receipt!=null,"mana before commit");
    if(mask%3==0) {ItemReceiptOperations.finish(false);chk(item.count==1,"mana rollback consumed");chk(p.buffs.isEmpty(),"mana rollback effect");}
    else {ItemReceiptOperations.finish(true);chk(item.count==0 && p.delayCount==1,"mana commit");
      if(id==40015)chk(p.hasSkillEffect(L1SkillId.STATUS_BLUE_POTION),"blue buff dropped by packet");
      if(id==40032)chk(p.hasSkillEffect(L1SkillId.STATUS_UNDERWATER_BREATH),"breath buff dropped by packet");
      if(id==40016)chk(p.hasSkillEffect(L1SkillId.STATUS_WISDOM_POTION),"wisdom buff dropped by packet");
      if(id==40025)chk(p.hasSkillEffect(L1SkillId.CURSE_BLIND),"blind buff dropped by packet");
    }
   }
  }
  // Denial must never submit a debit, including fallback from class restrictions.
  Faults.failMask=0;
  for(int id:move){L1PcInstance p=pc();p.decay=true;L1ItemInstance i=new L1ItemInstance();chk(MovementPotionConsumables.use(p,i,id),"decay movement");chk(ItemReceiptOperations.receipt==null,"decay receipt");}
  for(int id:mana){L1PcInstance p=pc();p.decay=true;L1ItemInstance i=new L1ItemInstance();chk(ManaProtectionPotionConsumables.use(p,i,id),"decay mana");chk(ItemReceiptOperations.receipt==null,"decay receipt");}
  for(int id:new int[]{40014,40068,712,713}) {
   L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();
   chk(MovementPotionConsumables.use(p,i,id),"class restriction movement");chk(ItemReceiptOperations.receipt==null,"class restriction movement receipt");
  }
  {L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();chk(ManaProtectionPotionConsumables.use(p,i,40016),"class restriction mana");chk(ItemReceiptOperations.receipt==null,"class restriction mana receipt");}
  // Completion and lastUsed are not allowed before durable commit.
  {L1PcInstance p=pc();L1ItemInstance item=new L1ItemInstance();item.count=2;item.template.delayEffect=30;
   chk(MovementPotionConsumables.use(p,item,40013),"stack pending");
   chk(p.delayCount==0 && item.lastUsed==null && p.getInventory().saves==0,"stack completed before commit");
   ItemReceiptOperations.finish(false);
   chk(item.count==2 && p.delayCount==0 && item.lastUsed==null,"stack rollback changed delay");
   chk(MovementPotionConsumables.use(p,item,40013),"stack retry");
   ItemReceiptOperations.finish(true);
   chk(item.count==1 && p.delayCount==1 && item.lastUsed!=null && p.getInventory().saves==1,"stack committed timestamp missing");
  }
  {L1PcInstance p=pc();L1ItemInstance item=new L1ItemInstance();item.template.delayEffect=30;
   chk(ManaProtectionPotionConsumables.use(p,item,40015),"last unit pending");
   ItemReceiptOperations.finish(true);
   chk(item.count==0 && item.lastUsed==null && p.getInventory().saves==0 && p.delayCount==1,"last unit incorrectly saved");
  }
  System.out.println("PASS typed production potions; assertions="+tests+"; IDs="+(move.length+mana.length));
 }
}
