#!/usr/bin/env python3
"""Compile three production Java item handlers with Java 21 typed deterministic stubs.
Checks receipt-before-effect, rollback, postcommit completion, and output failure.
"""
from pathlib import Path
import subprocess, tempfile, textwrap, os
root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else Path(__file__).resolve().parents[2]
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern127_typed_') as temp:
 base=Path(temp)/'src'; classes=Path(temp)/'classes';classes.mkdir()
 def put(rel,code):
  p=base/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(code).strip()+'\n',encoding='utf-8')
 for name in ['DurableBuffScrollItems','DominationTowerReturnHandler','TelBook']:
  put('l1j/server/server/model/item/function/'+name+'.java',(prod/(name+'.java')).read_text('utf-8'))
 put('l1j/server/server/model/Instance/L1ItemInstance.java','''
 package l1j.server.server.model.Instance;
 public final class L1ItemInstance {
  public int id=3, itemId=560025, count=1;
  public int getId(){return id;}public int getItemId(){return itemId;}
 }
 ''')
 put('l1j/server/server/model/Instance/L1PcInstance.java','''
 package l1j.server.server.model.Instance;
 import java.util.*;
 public class L1PcInstance {
  public int id=30,mapId=4,x=5,y=6,teleports,barriers,removed,setEffects;
  public boolean gm,blocked,dead,slept,paralyzed;
  public MapObj map=new MapObj();public Inventory inv=new Inventory();public Ability ability=new Ability();
  public Set<Integer> effects=new HashSet<>();
  public int getId(){return id;}public int getMapId(){return mapId;}public int getHeading(){return 4;}
  public int getX(){return x;}public int getY(){return y;}public boolean getAutoHunt(){return false;}
  public boolean isParalyzed(){return paralyzed;}public boolean isSleeped(){return slept;}
  public boolean isDead(){return dead;}public boolean isGm(){return gm;}
  public MapObj getMap(){return map;}public Inventory getInventory(){return inv;}
  public Ability getAbility(){return ability;}
  public boolean hasSkillEffect(int id){return effects.contains(id);}
  public void removeSkillEffect(int id){removed++;effects.remove(id);}
  public void setSkillEffect(int id,int dur){setEffects++;effects.add(id);}
  public void start_teleport(int x,int y,int map,int heading,int effect,boolean b1,boolean b2){teleports++;}
  public void cancelAbsoluteBarrier(){barriers++;}
  public static final class MapObj{public boolean escapable=true;public boolean isEscapable(){return escapable;}}
  public static final class Inventory{public L1ItemInstance live;
   public L1ItemInstance getItem(int id){return live!=null&&live.id==id?live:null;}}
  public static final class Ability{public int wis;public void addAddedWis(byte x){wis+=x;}}
 }
 ''')
 put('l1j/server/server/model/character/skill/L1SkillId.java','''
 package l1j.server.server.model.character.skill;
 public final class L1SkillId {
  public static final int BONE_BREAK=1,EARTH_BIND=2,ICE_LANCE=3,SHOCK_STUN=4,THUNDER_GRAB=5;
  public static final int EMPIRE=6,이터디스=7,쉐도우제약=8,버닝샷=9;
  public static final int God_buff=10,BLESSED_ARMOR=11,루돌프의버프=12;
 }
 ''')
 put('l1j/server/server/model/character/skill/L1SkillUse.java','''
 package l1j.server.server.model.character.skill;
 import l1j.server.server.model.Instance.*;
 public final class L1SkillUse {public static final int TYPE_GMBUFF=0;public static int casts;public static boolean fail;
  public static void cast(L1PcInstance pc,int skill,int id,int x,int y,Object obj,int sec,int type){casts++;if(fail)throw new IllegalStateException("cast failed");pc.setSkillEffect(skill,sec);}
 }
 ''')
 put('l1j/server/server/model/Getback.java','''
 package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public final class Getback {public static int[] GetBack_Location(L1PcInstance p,boolean b){return new int[]{5,6,4};}}
 ''')
 put('l1j/server/server/model/network/core/sc/packet/SC_Paralysis.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public final class SC_Paralysis {public static final int TYPE_TELEPORT_UNLOCK=1;
  public static void send(L1PcInstance p,int id,boolean flag) {}}
 ''')
 put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public final class SC_ServerMessage {public static void send(L1PcInstance p,int id) {}}
 ''')
 put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public final class SC_SystemMessage {public static void send(L1PcInstance p,String msg) {}}
 ''')
 put('l1j/server/server/model/network/core/sc/packet/SC_InventoryIcon.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public final class SC_InventoryIcon {public static boolean fail;public static int calls;
  public static void icoNew(L1PcInstance p,int id,int gfx,int sec,boolean b){calls++;if(fail)throw new RuntimeException("icon");}}
 ''')
 put('l1j/server/server/model/item/function/CreatureItemActions.java','''
 package l1j.server.server.model.item.function;
 public final class CreatureItemActions {public static final java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("test-items");}
 ''')
 put('l1j/server/server/model/item/function/ItemActionCompletion.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemActionCompletion {public static int count;
  public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance item){count++;}}
 ''')
 put('l1j/server/server/model/item/function/ItemReceiptOperations.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemReceiptOperations {public static Runnable pending;public static L1ItemInstance item;
  public static int admissions;
  public static void UseDurableConsume(L1PcInstance p,L1ItemInstance i,Runnable cb){admissions++;item=i;pending=cb;}
  public static void commit(){if(pending==null)throw new AssertionError("no receipt");Runnable r=pending;pending=null;item.count--;r.run();}
  public static void rollback(){pending=null;}
 }
 ''')
 put('l1j/server/server/model/item/function/Test127.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
 import l1j.server.server.model.character.skill.*;
 public final class Test127 {
  static int count;static void check(boolean v,String x){count++;if(!v)throw new AssertionError(x);}
  static void reset(){ItemReceiptOperations.pending=null;ItemReceiptOperations.admissions=0;ItemActionCompletion.count=0;L1SkillUse.casts=0;L1SkillUse.fail=false;SC_InventoryIcon.fail=false;SC_InventoryIcon.calls=0;}
  public static void main(String[] args){
   java.util.logging.Logger.getLogger("test-items").setLevel(java.util.logging.Level.OFF);
   for(int id:new int[]{560025,560027,560029}){
    reset();L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();item.itemId=id;p.inv.live=item;
    TelBook.clickItem(p,id,1,item);
    check(ItemReceiptOperations.admissions==1,"book commit admission");
    check(p.teleports==0&&ItemActionCompletion.count==0,"book precommit effect or tail");
    ItemReceiptOperations.commit();check(p.teleports==1&&ItemActionCompletion.count==1&&item.count==0,"book commit effect and tail");
    reset();p=new L1PcInstance();item=new L1ItemInstance();item.itemId=id;p.inv.live=item;TelBook.clickItem(p,id,1,item);
    ItemReceiptOperations.rollback();check(p.teleports==0&&ItemActionCompletion.count==0&&item.count==1,"book rollback");
   }
   reset();L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance();item.itemId=560028;p.inv.live=item;
   TelBook.clickItem(p,560028,1,item);
   check(ItemReceiptOperations.admissions==0&&p.teleports==1&&ItemActionCompletion.count==0,"nonconsumable talisman immediate");
   reset();p=new L1PcInstance();p.mapId=12853;item=new L1ItemInstance();p.inv.live=item;
   check(DominationTowerReturnHandler.getInstance().handleReturn(p,item),"tower return accepted");
   check(p.teleports==0&&ItemActionCompletion.count==0,"tower no early completion");
   ItemReceiptOperations.commit();check(p.teleports==1&&ItemActionCompletion.count==1,"tower committed completion");
   for(int id:new int[]{65648,40879,773355}){
    reset();p=new L1PcInstance();item=new L1ItemInstance();item.itemId=id;
    DurableBuffScrollItems.use(p,item,id);
    check(ItemReceiptOperations.admissions==1&&ItemActionCompletion.count==0&&p.setEffects==0,"buff precommit");
    ItemReceiptOperations.commit();check(item.count==0&&ItemActionCompletion.count==1,"buff commit tail");
    check(p.setEffects==1,"buff effect");
    reset();p=new L1PcInstance();item=new L1ItemInstance();item.itemId=id;DurableBuffScrollItems.use(p,item,id);
    ItemReceiptOperations.rollback();check(item.count==1&&p.setEffects==0&&ItemActionCompletion.count==0,"buff rollback");
   }
   reset();SC_InventoryIcon.fail=true;p=new L1PcInstance();item=new L1ItemInstance();
   DurableBuffScrollItems.use(p,item,773355);ItemReceiptOperations.commit();
   check(ItemActionCompletion.count==1&&p.ability.wis==1&&p.setEffects==1,"icon error must not drop buff or tail");
   reset();L1SkillUse.fail=true;p=new L1PcInstance();item=new L1ItemInstance();DurableBuffScrollItems.use(p,item,65648);
   boolean failed=false;try{ItemReceiptOperations.commit();}catch(IllegalStateException e){failed=true;}
   check(failed&&ItemActionCompletion.count==1,"hard skill failure must propagate but tail commit");
   System.out.println("PHASE127_TYPED_ASSERTIONS="+count);
  }
 }
 ''')
 files=[str(f) for f in base.rglob('*.java')]
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*files],text=True,capture_output=True)
 if p.returncode: print(p.stderr[-10000:]);raise SystemExit(p.returncode)
 p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Test127'],text=True,capture_output=True)
 print(p.stdout,end='')
 if p.returncode: print(p.stderr[-10000:]);raise SystemExit(p.returncode)
