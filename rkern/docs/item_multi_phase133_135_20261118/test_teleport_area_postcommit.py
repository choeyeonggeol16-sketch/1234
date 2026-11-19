#!/usr/bin/env python3
"""Compile actual Java21 production TeleportUseService, TeleportRandomTravel and
AreaDamageItems using typed collaborators; verify deferred commit, packet-failure
isolation, item tail, and unchanged skill-only teleport path."""
from pathlib import Path
import os, subprocess, tempfile, textwrap
ROOT=Path(os.environ.get('SOURCE_ROOT',Path(__file__).resolve().parents[2]))
PROD=ROOT/'src/l1j/server/server/model/item/function'
PKG='l1j/server/server/model/item/function/'
with tempfile.TemporaryDirectory(prefix='rkern134_teleport_area_') as td:
    base=Path(td)/'src'; classes=Path(td)/'classes';classes.mkdir()
    def put(path,code):
        p=base/path;p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(textwrap.dedent(code).strip()+'\n','utf-8')
    for n in ['TeleportUseService','TeleportRandomTravel','AreaDamageItems']:
        put(PKG+n+'.java',(PROD/(n+'.java')).read_text('utf-8'))
    put('l1j/server/Config.java','package l1j.server;public class Config {}')
    put('l1j/server/server/ActionCodes.java','package l1j.server.server;public class ActionCodes {public static final int ACTION_Damage=2;}')
    put('l1j/server/server/templates/L1BookMark.java','package l1j.server.server.templates;public class L1BookMark {}')
    put('l1j/server/server/packets/Protocol/MainServer/PlaySupport/ClientAutomationSession.java','package l1j.server.server.packets.Protocol.MainServer.PlaySupport;public class ClientAutomationSession {}')
    put('l1j/server/server/model/gametime/L1GameTimeClock.java','package l1j.server.server.model.gametime;public class L1GameTimeClock {}')
    put('l1j/server/server/model/character/skill/L1SkillId.java','''
package l1j.server.server.model.character.skill;
public class L1SkillId {public static final int MASS_TELEPORT=101,TELEPORT=102;}
''')
    put('l1j/server/server/model/map/L1Map.java','''
package l1j.server.server.model.map;
public class L1Map {public boolean isInMap(int x,int y){return true;}
public boolean isPassable(int x,int y){return true;}
public boolean isEscapable(){return true;}}
''')
    put('l1j/server/server/model/map/L1WorldMap.java','''
package l1j.server.server.model.map;
public class L1WorldMap {static final L1WorldMap singleton=new L1WorldMap();
public static L1WorldMap getInstance(){return singleton;}
public L1Map getMap(short id){return new L1Map();}}
''')
    put('l1j/server/server/model/L1Location.java','''
package l1j.server.server.model;
public class L1Location {public int x=333,y=444,map=1;
 public L1Location randomLocation(int range,boolean check){return this;}
 public int getX(){return x;}public int getY(){return y;}public int getMapId(){return map;}}
''')
    put('l1j/server/server/model/L1Object.java','''
package l1j.server.server.model;
public class L1Object {public int id=500;public int getId(){return id;}
 public int getX(){return 333;}public int getY(){return 444;}
 public L1Location getLocation(){return new L1Location();}}
''')
    put('l1j/server/server/model/Instance/L1ItemInstance.java','''
package l1j.server.server.model.Instance;
public class L1ItemInstance {public final int id=54,itemId;public int count=1;
public L1ItemInstance(int id){itemId=id;}public int getItemId(){return itemId;}
public int getId(){return id;}}
''')
    put('l1j/server/server/model/Instance/L1PcInstance.java','''
package l1j.server.server.model.Instance;
import l1j.server.server.model.*;import l1j.server.server.model.map.*;
public class L1PcInstance extends L1Object {
 public int mapId=1,level=80,teleports,barriers,moebius,end,count;
 public int clan=0;public boolean gm=false,failTeleport=false, invisible=false, shop=false;
 public L1Map getMap(){return new L1Map();}public int getMapId(){return mapId;}
 public int getLevel(){return level;}public boolean isInvisble(){return invisible;}
 public Object getNetConnection(){return this;}public int getHeading(){return 1;}
 public boolean getAutoHunt(){return false;}public boolean isGm(){return gm;}
 public int getClanid(){return clan;}public boolean isPrivateShop(){return shop;}
 public void start_teleport(int x,int y,int map,int h,int fx,boolean a,boolean b){
 teleports++;if(failTeleport)throw new IllegalStateException("teleport effect failed");}
 public void cancelAbsoluteBarrier(){barriers++;}public void cancelMoebius(){moebius++;}
 public void tpEndFinish(){end++;}public void sendPackets(String msg){count++;}
 public Inventory getInventory(){return new Inventory();}
 public static class Inventory {public boolean checkEquippedAtOnce(int[] ids){return false;}}
}
''')
    put('l1j/server/server/model/Instance/L1NpcInstance.java','''
package l1j.server.server.model.Instance;
import l1j.server.server.model.*;
public class L1NpcInstance extends L1Object {public int damage,status;
 public boolean isDead(){return false;}public void setStatus(int x){status=x;}
 public void receiveDamage(L1PcInstance pc,int dmg){damage+=dmg;}}
''')
    for n in ['L1MonsterInstance','L1DoorInstance','L1TowerInstance']:
        put('l1j/server/server/model/Instance/'+n+'.java',f'''
package l1j.server.server.model.Instance;
public class {n} extends L1NpcInstance {{}}
''')
    put('l1j/server/server/model/L1World.java','''
package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.*;
public class L1World {public static final L1World INSTANCE=new L1World();
public static L1World getInstance(){return INSTANCE;}
public L1Object target;public ArrayList<L1Object> nearby=new ArrayList<>();
public L1Object findObject(int id){return target!=null&&target.getId()==id?target:null;}
public java.util.List<L1Object> getVisiblePoint(L1Location loc,int radius){return nearby;}
public java.util.List<L1PcInstance> getVisiblePlayer(L1PcInstance p,int radius){return java.util.List.of();}
}
''')
    put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_ServerMessage {public static void send(L1PcInstance pc,int x){pc.count++;}}
''')
    put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_SystemMessage {public static void send(L1PcInstance pc,String msg){pc.count++;}}
''')
    put('l1j/server/server/model/network/core/sc/packet/SC_DoActionGFX.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
public class SC_DoActionGFX {public static int calls,fail;
public static void send(Object conn,int id,int act){calls++;}
public static void broadcast(L1Object pc,int act){calls++;if(fail>0){fail--;throw new IllegalStateException("gfx IO fail");}}}
''')
    put('l1j/server/server/model/network/core/sc/packet/SC_UseAttackSkill.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_UseAttackSkill {public static int callsSend,callsBroadcast,failSend,failBroadcast;
public static void send(Object conn,L1PcInstance pc,int target,int effect,int x,int y,int z){
 callsSend++;if(failSend>0){failSend--;throw new IllegalStateException("attack IO fail");}}
public static void broadcast(L1PcInstance pc,int target,int effect,int x,int y,int z){
 callsBroadcast++;if(failBroadcast>0){failBroadcast--;throw new IllegalStateException("attack broadcast fail");}}}
''')
    put('l1j/server/server/model/network/core/sc/packet/SC_Paralysis.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_Paralysis {public static final int TYPE_TELEPORT_UNLOCK=1;
public static void send(L1PcInstance pc,int type,boolean on){}}
''')
    put(PKG+'TeleportItemRules.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class TeleportItemRules {public static boolean preCheckTeleportState(L1PcInstance pc){return true;}
public static boolean isValidBookmark(L1PcInstance pc,int x,int y,short mapId){return true;}
public static boolean canBypassMapRestrictions(L1PcInstance pc){return false;}
public static boolean canRandomTeleportHere(L1PcInstance pc){return true;}}
''')
    put(PKG+'TeleportClanBookmarkTravel.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.map.*;
public class TeleportClanBookmarkTravel {
public static void teleportClanMembersAroundBookmarkMass(L1PcInstance pc,int x,int y,short map,L1Map mapobj){}}
''')
    put(PKG+'CreatureItemActions.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class CreatureItemActions {public static final java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("rkern-area-test");
public static void cancelAbsoluteBarrier(L1PcInstance pc){pc.cancelAbsoluteBarrier();}}
''')
    put(PKG+'ItemActionCompletion.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionCompletion {public static int calls;
 public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance item){calls++;}}
''')
    put(PKG+'ItemReceiptOperations.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemReceiptOperations {public static Runnable pending;public static int admitted;
 public static L1ItemInstance input;
 public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable callback){
 pending=callback;input=item;admitted++;}
 public static void commit(){if(pending==null)throw new AssertionError("no pending receipt");
 Runnable run=pending;pending=null;input.count--;run.run();}
 public static void rollback(){pending=null;}
}
''')
    put(PKG+'TestTeleportArea.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
public class TestTeleportArea {
 static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 static void reset(){ItemReceiptOperations.pending=null;ItemReceiptOperations.admitted=0;
 ItemActionCompletion.calls=0;SC_DoActionGFX.calls=0;SC_DoActionGFX.fail=0;
 SC_UseAttackSkill.callsSend=0;SC_UseAttackSkill.callsBroadcast=0;
 SC_UseAttackSkill.failSend=0;SC_UseAttackSkill.failBroadcast=0;
 L1World.INSTANCE.target=null;L1World.INSTANCE.nearby.clear();}
 static void checkTeleport(boolean bookmarked,boolean exception){
  reset();L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(40099);
  p.failTeleport=exception;
  TeleportUseService.clickItem(p,40099,bookmarked?100:0,bookmarked?100:0,(short)1,item);
  ok(ItemReceiptOperations.admitted==1,"receipt staged for teleport");
  ok(p.teleports==0&&ItemActionCompletion.calls==0,"no precommit effect/tail");
  try {ItemReceiptOperations.commit();if(exception)throw new AssertionError("expected effect exception");}
  catch(IllegalStateException ex){if(!exception)throw ex;}
  ok(p.teleports==1,"teleport attempted after commit");
  ok(ItemActionCompletion.calls==1,"teleport item tail exactly once");
  ok(p.barriers==1&&p.moebius==1,"teleport cleanup still attempted");
 }
 public static void main(String[] args){
  CreatureItemActions.ITEM_USE2_LOG.setLevel(java.util.logging.Level.OFF);
  for(boolean b:new boolean[]{true,false})for(boolean failed:new boolean[]{false,true})checkTeleport(b,failed);
  reset();L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(40099);
  TeleportUseService.clickItem(p,40099,50,50,(short)1,item);
  ItemReceiptOperations.rollback();ok(p.teleports==0&&ItemActionCompletion.calls==0&&item.count==1,"rollback no teleport");
  reset();p=new L1PcInstance();p.mapId=1;
  TeleportUseService.handleSkillTeleport(p,102,100,100,(short)1);
  ok(p.teleports==1&&ItemReceiptOperations.admitted==0&&ItemActionCompletion.calls==0,"skill-only teleport unchanged");
  for(boolean failPackets:new boolean[]{false,true}){
   reset();p=new L1PcInstance();p.mapId=2101;item=new L1ItemInstance(30055);
   L1MonsterInstance target=new L1MonsterInstance();target.id=600;
   L1MonsterInstance nearby=new L1MonsterInstance();nearby.id=601;
   L1World.INSTANCE.target=target;L1World.INSTANCE.nearby.add(nearby);
   if(failPackets){SC_DoActionGFX.fail=1;SC_UseAttackSkill.failSend=1;SC_UseAttackSkill.failBroadcast=1;}
   AreaDamageItems.use(p,item,600,30055);
   ok(ItemReceiptOperations.admitted==1&&nearby.damage==0,"area damage before commit blocked");
   ok(ItemActionCompletion.calls==0,"area no early tail");
   ItemReceiptOperations.commit();
   ok(nearby.damage==250,"combat damage still applied after commit");
   ok(ItemActionCompletion.calls==1,"area item tail committed");
   ok(SC_UseAttackSkill.callsSend==1&&SC_UseAttackSkill.callsBroadcast==1,"both packets attempted");
  }
  reset();p=new L1PcInstance();p.mapId=2101;item=new L1ItemInstance(30055);
  AreaDamageItems.use(p,item,1234,30055);
  ItemReceiptOperations.commit();ok(ItemActionCompletion.calls==1,"invalid target still finalizes committed use");
  System.out.println("TELEPORT_AREA_JAVA21_ASSERTIONS="+checks);
 }
}
''')
    source_files=[str(p) for p in base.rglob('*.java')]
    r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*source_files],
                     capture_output=True,text=True)
    if r.returncode:
        print(r.stdout,r.stderr[-14000:]);raise SystemExit('typed Java21 compile failed')
    r=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TestTeleportArea'],
                     capture_output=True,text=True)
    print(r.stdout,end='')
    if r.returncode:
        print(r.stderr[-14000:]);raise SystemExit('teleport area test failed')
