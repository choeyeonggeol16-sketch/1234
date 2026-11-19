#!/usr/bin/env python3
from pathlib import Path
import tempfile, subprocess, re, shutil
ROOT=Path(__file__).resolve().parents[2]; FUN=ROOT/'src/l1j/server/server/model/item/function'; D=ROOT/'docs/item_domain_phase13_20261118'
def run(files,main):
    with tempfile.TemporaryDirectory(prefix='rkern13_') as temp:
        tmp=Path(temp)
        for n,txt in files.items():
            p=tmp/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(txt,'utf-8')
        args=['javac','--release','21','-encoding','UTF-8','-d',temp,*map(str,tmp.rglob('*.java'))]
        p=subprocess.run(args,capture_output=True,text=True)
        if p.returncode:
            print(p.stdout+p.stderr);raise RuntimeError('compilation failed')
        p=subprocess.run(['java','-cp',temp,main],capture_output=True,text=True)
        if p.returncode:
            print(p.stdout+p.stderr);raise RuntimeError('run failed')
        print(p.stdout.strip())

pfx='l1j/server/server/model/item/function/'
def prod(*names):return {pfx+n+'.java':(FUN/(n+'.java')).read_text('utf-8') for n in names}
def legacy(name):
    src=(D/(name+'.java.baseline')).read_text('utf-8')
    src=src.replace('public final class '+name,'final class Legacy'+name,1).replace('private '+name+'()','private Legacy'+name+'()',1)
    return src

# Item bonus: compile extracted real production classes + full original side by side.
files=prod('ItemBonusHooks','ItemBonusPrimitiveMaps','DollTickBonuses','DollExperienceBonuses','EquipmentExperienceBonuses')
files[pfx+'LegacyItemBonusHooks.java']=legacy('ItemBonusHooks')
orig=(D/'ItemBonusHooks.java.baseline').read_text('utf-8')
constants=sorted(set(re.findall(r'L1DollInstance\.([\w\u0080-\uffff]+)',orig)))
files['l1j/server/server/model/Instance/L1DollInstance.java']='''package l1j.server.server.model.Instance;
public final class L1DollInstance {
'''+''.join('public static final int '+s+' = '+str(i+1)+';\n' for i,s in enumerate(constants))+'''
public int type,lv,_dollHpTickDelta,_dollMpTickDelta; public double _expBonusDeltaExp;
public int getDollType(){return type;}public int getDollLevel(){return lv;}
}'''
files['l1j/server/server/model/Instance/L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {
public int id,ench;public boolean equipped;public double _expBonusDeltaExp;
public int getItemId(){return id;}public int getEnchantLevel(){return ench;}public boolean isEquipped(){return equipped;}
}'''
files['l1j/server/server/model/item/Exp/ExpBonusState.java']='''package l1j.server.server.model.item.Exp;
public final class ExpBonusState {
public double doll,arka,ins1=1,ins2=1,ins3=1,ins4=1,ins5=1;
public void addDollDelta(double v){doll+=v;}public void addArkaDelta(double v){arka+=v;}
public void setIns1(double v){ins1=v;}public void setIns2(double v){ins2=v;}public void setIns3(double v){ins3=v;}public void setIns4(double v){ins4=v;}public void setIns5(double v){ins5=v;}
public String state(){return doll+":"+arka+":"+ins1+":"+ins2+":"+ins3+":"+ins4+":"+ins5;}
}'''
files['l1j/server/server/model/Instance/L1PcInstance.java']='''package l1j.server.server.model.Instance;
import l1j.server.server.model.item.Exp.ExpBonusState;
public final class L1PcInstance {
public final ExpBonusState st=new ExpBonusState();public int hp,mp;
public ExpBonusState expBonusState(){return st;}public void addDollHpRegenTick(int v){hp+=v;}public void addDollMpRegenTick(int v){mp+=v;}
public String state(){return st.state()+":"+hp+":"+mp;}
}'''
files[pfx+'Phase13BonusDifferential.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class Phase13BonusDifferential {
 static void eq(Object a,Object b,String what){if(!a.equals(b))throw new AssertionError(what+" "+a+" != "+b);}
 static void sameDoll(L1PcInstance a,L1PcInstance b,L1DollInstance da,L1DollInstance db){
 eq(a.state(),b.state(),"pc");eq(da._expBonusDeltaExp,db._expBonusDeltaExp,"doll exp");
 eq(da._dollHpTickDelta,db._dollHpTickDelta,"hp");eq(da._dollMpTickDelta,db._dollMpTickDelta,"mp");}
 public static void main(String[]args){int n=0;
 for(int type=0;type<240;type++)for(int lv=0;lv<135;lv+=3){
   L1DollInstance a=new L1DollInstance(),b=new L1DollInstance();a.type=b.type=type;a.lv=b.lv=lv;
   eq(LegacyItemBonusHooks.calcHpTick(a),ItemBonusHooks.calcHpTick(b),"calcHp");n++;
   eq(LegacyItemBonusHooks.calcMpTick(a),ItemBonusHooks.calcMpTick(b),"calcMp");n++;
   L1PcInstance pa=new L1PcInstance(),pb=new L1PcInstance();
   LegacyItemBonusHooks.onDollSpawned(pa,a);ItemBonusHooks.onDollSpawned(pb,b);sameDoll(pa,pb,a,b);n++;
   LegacyItemBonusHooks.onDollSpawned(pa,a);ItemBonusHooks.onDollSpawned(pb,b);sameDoll(pa,pb,a,b);n++;
   LegacyItemBonusHooks.onDollRemoved(pa,a);ItemBonusHooks.onDollRemoved(pb,b);sameDoll(pa,pb,a,b);n++;
   LegacyItemBonusHooks.onDollRemoved(pa,a);ItemBonusHooks.onDollRemoved(pb,b);sameDoll(pa,pb,a,b);n++;
 }
 int[] ids={900033,9961,555001,555002,555003,666001,666002,666003,900025,900184,900116,900130,900020,900093,900094,900095,900099,0,12,900001};
 for(int id:ids)for(int ench=-1;ench<=12;ench++){
  L1PcInstance pa=new L1PcInstance(),pb=new L1PcInstance();
  L1ItemInstance a=new L1ItemInstance(),b=new L1ItemInstance();a.id=b.id=id;a.ench=b.ench=ench;
  a.equipped=b.equipped=true;
  LegacyItemBonusHooks.onEquipChanged(pa,a,true);ItemBonusHooks.onEquipChanged(pb,b,true);eq(pa.state(),pb.state(),"equip");eq(a._expBonusDeltaExp,b._expBonusDeltaExp,"item");n++;
  a.ench++;b.ench++;LegacyItemBonusHooks.onEnchantChanged(pa,a);ItemBonusHooks.onEnchantChanged(pb,b);eq(pa.state(),pb.state(),"enchant");n++;
  LegacyItemBonusHooks.onEquipChanged(pa,a,false);ItemBonusHooks.onEquipChanged(pb,b,false);eq(pa.state(),pb.state(),"unequip");n++;
  a.equipped=b.equipped=false;
  LegacyItemBonusHooks.onEnchantChanged(pa,a);ItemBonusHooks.onEnchantChanged(pb,b);eq(pa.state(),pb.state(),"non-equipped");n++;
 }
 System.out.println("PASS bonus 5 real production classes typed; "+n+" original vs split lifecycle/calculation cases");
 }
}'''
run(files,'l1j.server.server.model.item.function.Phase13BonusDifferential')

# Ice buffs: compile actual extracted classes and full original; compare durable-consume callbacks
# and every observable skill/message/status/cancellation event in order.
files=prod('IceBuffItem','IceBuffSingleUse','IceBuffAllUse','IceBuffEffects')
files[pfx+'LegacyIceBuffItem.java']=legacy('IceBuffItem')
orig=(D/'IceBuffItem.java.baseline').read_text('utf-8')
skills=sorted(set(re.findall(r'L1SkillId\.([\w\u0080-\uffff]+)',orig)))
files['l1j/server/server/model/character/skill/L1SkillId.java']='''package l1j.server.server.model.character.skill;
public final class L1SkillId {'''+''.join('public static final int '+s+' = '+str(i+500)+';\n' for i,s in enumerate(skills))+'''}'''
files['l1j/server/server/model/character/skill/L1SkillUse.java']='''package l1j.server.server.model.character.skill;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1SkillUse {public static final int TYPE_GMBUFF=1;
public static void cast(L1PcInstance p,int sk,int id,int x,int y,Object obj,int sec,int mode){p.log("CAST:"+sk+":"+mode);}}
'''
files['l1j/server/server/model/Instance/L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public class L1ItemInstance {public int id,type1;public L1ItemInstance(int id,int type1){this.id=id;this.type1=type1;}
public int getItemId(){return id;}public L1ItemInstance getItem(){return this;}public int getType1(){return type1;}}
'''
files['l1j/server/server/model/Instance/L1PcInstance.java']='''package l1j.server.server.model.Instance;
import java.util.*;
public final class L1PcInstance {
 public final StringBuilder events=new StringBuilder();public Set<Integer> effects=new HashSet<>();
 public boolean elf,wizard;public int attr,weaponType=-1,buffnoch,maxmp=100;
 public void log(String s){events.append(s).append(';');}
 public L1ItemInstance getWeapon(){return weaponType<0?null:new L1ItemInstance(999,weaponType);}
 public void sendPackets(String s){log("MSG:"+s);}public int getId(){return 123;}public int getX(){return 77;}public int getY(){return 88;}
 public void setBuffnoch(int n){buffnoch=n;log("BuffNo:"+n);}public boolean isElf(){return elf;} public int getElfAttr(){return attr;}
 public boolean isWizard(){return wizard;}public Ability getAbility(){return new Ability(this);}public Resistance getResistance(){return new Resistance(this);}
 public static final class Ability {final L1PcInstance p;Ability(L1PcInstance p){this.p=p;}
 public void addSp(int n){p.log("SP:"+n);}public void addAddedStr(int n){p.log("STR:"+n);}public void addAddedDex(byte n){p.log("DEX:"+n);}public void addAddedInt(int n){p.log("INT:"+n);}}
 public static final class Resistance {final L1PcInstance p;Resistance(L1PcInstance p){this.p=p;}public void addcalcPcDefense(int n){p.log("DEF:"+n);}}
 public void addMaxMp(int n){maxmp+=n;log("MaxMP:"+n);}public void addBaseMagicHitUp(int n){log("MHIT:"+n);}public void addDmgRate(int n){log("DmgRate:"+n);}
 public void addHitup(int n){log("Hit:"+n);}public void addDmgup(int n){log("Dmg:"+n);}public void addBowDmgup(int n){log("BowDmg:"+n);}public void addBowHitup(int n){log("BowHit:"+n);}
 public void curePoison(){log("Cure");}public void setSkillEffect(int s,int t){effects.add(s);log("SET:"+s+":"+t);}
 public boolean hasSkillEffect(int s){log("HAS:"+s);return effects.contains(s);}public void removeSkillEffect(int s){effects.remove(s);log("REM:"+s);}
 public int getTotalER(){return 5;}public int getCurrentMp(){return 16;}public int getMaxMp(){return maxmp;}
}'''
files[pfx+'ItemReceiptOperations.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class ItemReceiptOperations {public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable callback){pc.log("RECEIPT:"+item.getItemId());callback.run();pc.log("RECEIPT_END");}}
'''
sys='l1j/server/server/model/network/core/sc/packet/'
files[sys+'SC_SPMR.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SPMR {public static void send(L1PcInstance p){p.log("SC_SPMR");}}
'''
files[sys+'SC_MpUpdate.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_MpUpdate {public static void send(L1PcInstance p,int cur,int max){p.log("MPUP:"+cur+":"+max);}}
'''
files[sys+'SC_SkillSound.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SkillSound {public static long pack(int a,int b){return (((long)a)<<32)|b;}
public static void sendPacked(L1PcInstance p,long v){p.log("SOUNDSEND:"+v);}public static void broadcastPacked(L1PcInstance p,long v){p.log("SOUNDBROAD:"+v);}}
'''
files[sys+'SC_OwnCharStatus.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_OwnCharStatus {public static void send(L1PcInstance p){p.log("OWNCHAR");}}
'''
files[sys+'SC_PacketBox.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_PacketBox {public static final int ER_UpDate=33;public static void send(L1PcInstance p,int box,int value){p.log("BOX:"+box+":"+value);}}
'''
files[pfx+'Phase13IceDifferential.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.skill.L1SkillId;
public final class Phase13IceDifferential {
 public static void main(String[]args){int n=0;
 int[] ids={60208,60209,60210,4100040,10000,0};
 for(int id:ids)for(int weapon:new int[]{-1,1,20,5})for(int cls=0;cls<5;cls++)for(int eff=0;eff<8;eff++){
 L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
 a.elf=b.elf=cls==1||cls==2;a.wizard=b.wizard=cls==3;
 a.attr=b.attr=cls==2?2:1;a.weaponType=b.weaponType=weapon;
 for(int x=0;x<12;x++)if(((x+eff)&3)==0){a.effects.add(500+x);b.effects.add(500+x);}
 L1ItemInstance item=new L1ItemInstance(id,0);
 boolean ar=LegacyIceBuffItem.use(a,item),br=IceBuffItem.use(b,item);
 if(ar!=br||!a.events.toString().equals(b.events.toString())||!a.effects.equals(b.effects)||a.buffnoch!=b.buffnoch||a.maxmp!=b.maxmp)
 throw new AssertionError("id="+id+" weapon="+weapon+" cls="+cls+" eff="+eff+" before="+a.events+" now="+b.events);
 n++;}
 if(LegacyIceBuffItem.use(null,null)!=IceBuffItem.use(null,null))throw new AssertionError("null");
 System.out.println("PASS ice 4 actual production classes typed and "+n+" original-vs-new item use cases");
 }
}'''
run(files,'l1j.server.server.model.item.function.Phase13IceDifferential')

# Teleport: original class and split rules/travel production classes type-compiled against
# the same mock world. Compare map restrictions, GM/PSS, bookmark and item/callback paths.
files=prod('TeleportUseService','TeleportItemRules','TeleportRandomTravel','TeleportClanBookmarkTravel')
files[pfx+'LegacyTeleportUseService.java']=legacy('TeleportUseService')
orig=(D/'TeleportUseService.java.baseline').read_text('utf-8')
skills=sorted(set(re.findall(r'L1SkillId\.([\w\u0080-\uffff]+)',orig)))
files['l1j/server/server/model/character/skill/L1SkillId.java']='''package l1j.server.server.model.character.skill;
public final class L1SkillId {'''+''.join('public static final int '+s+' = '+str(i+100)+';\n' for i,s in enumerate(skills))+'''}'''
files['l1j/server/Config.java']='''package l1j.server;
public final class Config {public static boolean PRISONTEL,PRISONTELS;}'''
files['l1j/server/server/packets/Protocol/MainServer/PlaySupport/ClientAutomationSession.java']='''package l1j.server.server.packets.Protocol.MainServer.PlaySupport;
public enum ClientAutomationSession {SERVER,CLIENT}'''
files['l1j/server/server/templates/L1BookMark.java']='''package l1j.server.server.templates;
public final class L1BookMark {private final int x,y;private final short m;public L1BookMark(int x,int y,short m){this.x=x;this.y=y;this.m=m;}
public int getLocX(){return x;}public int getLocY(){return y;}public short getMapId(){return m;}}'''
files['l1j/server/server/model/L1Location.java']='''package l1j.server.server.model;
public final class L1Location {private final int x,y,map;
public L1Location(int x,int y,int m){this.x=x;this.y=y;this.map=m;}
public L1Location randomLocation(int r,boolean ok){return new L1Location(x+5,y+7,map);}
public int getX(){return x;}public int getY(){return y;}public int getMapId(){return map;}}'''
files['l1j/server/server/model/L1World.java']='''package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.L1PcInstance;
public final class L1World {private static final L1World INSTANCE=new L1World();public static L1World getInstance(){return INSTANCE;}
public Iterable<L1PcInstance> getVisiblePlayer(L1PcInstance p,int r){return Collections.emptyList();}}'''
files['l1j/server/server/model/map/L1Map.java']='''package l1j.server.server.model.map;
public final class L1Map {public boolean escapable=true,teleportable=true,ruler,passable=true;
public boolean isEscapable(){return escapable;}public boolean isTeleportable(){return teleportable;}
public boolean isRuler(){return ruler;}public boolean isInMap(int x,int y){return true;}
public boolean isPassable(int x,int y){return passable;}}'''
files['l1j/server/server/model/map/L1WorldMap.java']='''package l1j.server.server.model.map;
public final class L1WorldMap {private static final L1WorldMap INSTANCE=new L1WorldMap();public static L1Map map;
public static L1WorldMap getInstance(){return INSTANCE;}public L1Map getMap(int mapId){return map;}}
'''
files['l1j/server/server/model/gametime/L1GameTimeClock.java']='''package l1j.server.server.model.gametime;
public final class L1GameTimeClock {private static final L1GameTimeClock INSTANCE=new L1GameTimeClock();public static long seconds;
public static L1GameTimeClock getInstance(){return INSTANCE;}public GameTime getGameTime(){return new GameTime();}
public static final class GameTime {public long getSeconds(){return seconds;}}}'''
files['l1j/server/server/model/Instance/L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {public final int id;public L1ItemInstance(int id){this.id=id;}public int getItemId(){return id;}}'''
files['l1j/server/server/model/Instance/L1PcInstance.java']='''package l1j.server.server.model.Instance;
import java.util.*;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;
public final class L1PcInstance {
 public int mapId=1,id=11,clanId,heading;public boolean gm,shop;
 public boolean[] status=new boolean[1024];public L1Map map=new L1Map();public StringBuilder events=new StringBuilder();
 public Inventory inv=new Inventory();public L1BookMark[] bookmarks=new L1BookMark[0];
 public Net conn; public void log(String v){events.append(v).append(';');}
 public boolean isGm(){return gm;}public Net getNetConnection(){return conn;}
 public boolean hasSkillEffect(int s){return status[s];}
 public void tpEndFinish(){log("TPEND");}
 public L1BookMark[] getBookMarkArray(){return bookmarks;}
 public L1Map getMap(){return map;}public int getMapId(){return mapId;}
 public Inventory getInventory(){return inv;}
 public L1Location getLocation(){return new L1Location(101,202,mapId);}
 public int getHeading(){return heading;}
 public int getId(){return id;}public int getX(){return 101;}public int getY(){return 202;}
 public void start_teleport(int x,int y,short m,int h,int effect,boolean b,boolean c){log("TP:"+x+":"+y+":"+m+":"+h+":"+effect+":"+b+":"+c);}
 public void cancelAbsoluteBarrier(){log("BARRIER");}public void cancelMoebius(){log("MOEBIUS");}
 public int getClanid(){return clanId;}public boolean isPrivateShop(){return shop;}
 public static final class Net {public boolean rules;public ClientAutomationSession session;
 public boolean requiresPlayerTeleportRules(){return rules;}public ClientAutomationSession getAutomationSession(){return session;}}
 public static final class Inventory {public final HashSet<Integer> items=new HashSet<>();public boolean precise;
 public L1ItemInstance findItemId(int id){return items.contains(id)?new L1ItemInstance(id):null;}
 public boolean checkItem(int id){return items.contains(id);}
 public boolean checkEquippedAtOnce(int[] ids){return precise;}}
}'''
files[pfx+'ItemReceiptOperations.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class ItemReceiptOperations {public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable callback){pc.log("RECEIPT:"+item.getItemId());callback.run();pc.log("RECEIPT_END");}}'''
sys='l1j/server/server/model/network/core/sc/packet/'
files[sys+'SC_Paralysis.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_Paralysis {public static final int TYPE_TELEPORT_UNLOCK=42;public static void send(L1PcInstance p,int t,boolean b){p.log("PARALYSIS:"+t+":"+b);}}'''
files[sys+'SC_ServerMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance p,int m){p.log("SERVERMSG:"+m);}}'''
files[sys+'SC_SystemMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SystemMessage {public static void send(L1PcInstance p,String m){p.log("SYS:"+m);}}'''
files[sys+'SC_DoActionGFX.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_DoActionGFX {public static void send(L1PcInstance.Net n,int id,int act){}
public static void broadcast(L1PcInstance p,int act){p.log("GFX:"+act);}}'''
files[pfx+'Phase13TeleportDifferential.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.map.*;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.Config;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;
import java.lang.reflect.*;
public final class Phase13TeleportDifferential {
 static boolean baseline(String name,Class<?>[]types,Object...args){try{Method m=LegacyTeleportUseService.class.getDeclaredMethod(name,types);m.setAccessible(true);return (Boolean)m.invoke(null,args);}catch(Exception x){throw new AssertionError(x);}}
 static void eq(Object a,Object b,String where){if(!a.equals(b))throw new AssertionError(where+" "+a+" != "+b);}
 static L1PcInstance init(int map,int variant,int flags){
  L1PcInstance p=new L1PcInstance();p.mapId=map;p.gm=(flags&1)!=0;
  p.map.escapable=(flags&2)!=0;p.map.teleportable=(flags&4)!=0;p.map.ruler=(flags&8)!=0;
  p.map.passable=false;
  if((flags&16)!=0){p.conn=new L1PcInstance.Net();p.conn.rules=(flags&32)!=0;p.conn.session=(flags&64)!=0?ClientAutomationSession.SERVER:ClientAutomationSession.CLIENT;}
  if((flags&128)!=0){p.inv.items.add(900111);p.inv.precise=true;}
  if((flags&256)!=0)p.inv.items.add(560028);
  if((flags&512)!=0)p.inv.items.add(4100135);
  if((flags&1024)!=0)p.inv.items.add(830022+Math.max(0,map-101));
  if((flags&2048)!=0)p.inv.items.add(830022+Math.max(0,map-12852));
  L1WorldMap.map=p.map;
  p.bookmarks=new L1BookMark[]{new L1BookMark(77,88,(short)map)};
  if(variant==1)p.status[L1SkillId.이터디스]=true;
  if(variant==2)p.status[L1SkillId.쉐도우제약]=true;
  if(variant==3)p.status[L1SkillId.버닝샷]=true;
  return p;
 }
 public static void main(String[]args){int n=0;
 int[] maps={1,54,101,105,110,12852,12859,12861,1708,1709,8000};
 for(int map:maps)for(int flags=0;flags<4096;flags+=37)for(int v=0;v<4;v++){
   L1PcInstance a=init(map,v,flags),b=init(map,v,flags);
   Config.PRISONTEL=(flags&2)!=0;Config.PRISONTELS=(flags&8)!=0;
   L1GameTimeClock.seconds=(flags&16)!=0?21*3600:12*3600;
   boolean ar=baseline("canRandomTeleportHere",new Class[]{L1PcInstance.class},a);
   boolean br=TeleportItemRules.canRandomTeleportHere(b);
   eq(ar,br,"map="+map+" flags="+flags);eq(a.events.toString(),b.events.toString(),"map log");n++;
   a=init(map,v,flags);b=init(map,v,flags);
   ar=baseline("preCheckTeleportState",new Class[]{L1PcInstance.class},a);
   br=TeleportItemRules.preCheckTeleportState(b);
   eq(ar,br,"precheck");eq(a.events.toString(),b.events.toString(),"precheck log");n++;
   a=init(map,v,flags);b=init(map,v,flags);
   for(int x:new int[]{0,77,90}){
    ar=baseline("isValidBookmark",new Class[]{L1PcInstance.class,int.class,int.class,short.class},a,x,88,(short)map);
    br=TeleportItemRules.isValidBookmark(b,x,88,(short)map);
    eq(ar,br,"bookmark");n++;
   }
   a=init(map,v,flags);b=init(map,v,flags);
   ar=LegacyTeleportUseService.canBypassMapRestrictions(a);
   br=TeleportUseService.canBypassMapRestrictions(b);
   eq(ar,br,"gm");n++;
 }
 // Public item entrypoint: valid bookmarks, invalid bookmarks and no bookmark.
 for(int map:new int[]{1,54,101,12852,1708})for(int flags=0;flags<4096;flags+=137)for(int v=0;v<4;v++)for(int itemId:new int[]{40100,40101})for(int mode=0;mode<4;mode++){
  L1PcInstance a=init(map,v,flags),b=init(map,v,flags);
  Config.PRISONTEL=(flags&2)!=0;Config.PRISONTELS=(flags&8)!=0;L1GameTimeClock.seconds=21*3600;
  int x=mode==0?0:mode==1?77:90;
  L1ItemInstance ia=mode==3?new L1ItemInstance(itemId):null;
  LegacyTeleportUseService.clickItem(a,itemId,x,88,(short)map,ia);
  TeleportUseService.clickItem(b,itemId,x,88,(short)map,ia);
  eq(a.events.toString(),b.events.toString(),"item public map="+map+" flags="+flags+" mode="+mode);n++;
 }
 // Mass and regular skill paths: no party; map passable=false makes random offsets fall back to same coordinate.
 for(int map:new int[]{1,54,101,12852})for(int flags=0;flags<4096;flags+=147)for(int v=0;v<4;v++)for(int skill:new int[]{L1SkillId.TELEPORT,L1SkillId.MASS_TELEPORT})for(int mode=0;mode<3;mode++){
  L1PcInstance a=init(map,v,flags),b=init(map,v,flags);
  Config.PRISONTEL=(flags&2)!=0;Config.PRISONTELS=(flags&8)!=0;L1GameTimeClock.seconds=21*3600;
  int x=mode==0?0:77;
  LegacyTeleportUseService.handleSkillTeleport(a,skill,x,88,(short)map);
  TeleportUseService.handleSkillTeleport(b,skill,x,88,(short)map);
  eq(a.events.toString(),b.events.toString(),"skill public map="+map+" flags="+flags+" mode="+mode);n++;
 }
 System.out.println("PASS teleport 4 actual production classes typed; "+n+" original-vs-new map/bookmark/skill/item cases");
 }
}'''
run(files,'l1j.server.server.model.item.function.Phase13TeleportDifferential')
