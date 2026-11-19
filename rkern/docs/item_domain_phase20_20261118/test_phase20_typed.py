#!/usr/bin/env python3
"""Java 21 original-vs-consolidated item action typed differential tests.
Run from any directory. Uses original production source snapshots in baseline/.
"""
from pathlib import Path
import tempfile, subprocess, os, shutil, re, sys

D=Path(__file__).resolve().parent
P=D.parents[1]/'src/l1j/server/server/model/item/function'
B=D/'baseline'
ENV=dict(os.environ, LANG='C.UTF-8', LC_ALL='C.UTF-8')
ROOT='l1j/server/server/'

def compile_run(label, javafiles, stubs, harness):
    outputs={}
    for side in ('original','modified'):
        with tempfile.TemporaryDirectory(prefix='rkern_phase20_'+re.sub(r'[^A-Za-z0-9]+','_',label)+'_') as tmp:
            tmp=Path(tmp);(tmp/'src').mkdir();(tmp/'classes').mkdir()
            def put(rel,s):
                f=tmp/'src'/rel;f.parent.mkdir(exist_ok=True,parents=True);f.write_text(s,encoding='utf8')
            for filename in javafiles:
                if side=='modified' and filename.startswith('SealedRuneLevel'): continue
                f=(B if side=='original' else P)/filename
                put(ROOT+'model/item/function/'+filename,f.read_text(encoding='utf8'))
            for rel,src in stubs.items():put(rel,src)
            put(ROOT+'model/item/function/Harness.java','package l1j.server.server.model.item.function;\n'+harness)
            cmd=['javac','--release','21','-encoding','UTF-8','-d',str(tmp/'classes')]+[str(f) for f in (tmp/'src').rglob('*.java')]
            p=subprocess.run(cmd,text=True,capture_output=True,env=ENV)
            assert p.returncode==0, f'{label} {side} compile failed:\n{p.stderr[:6000]}'
            p=subprocess.run(['java','-cp',str(tmp/'classes'),'l1j.server.server.model.item.function.Harness'],text=True,capture_output=True,env=ENV)
            assert p.returncode==0, f'{label} {side} run failed:\n{p.stderr[:6000]}'
            outputs[side]=p.stdout.strip()
    assert outputs['original']==outputs['modified'],f'{label}: DIFFERENT\n{outputs}'
    print('PASS',label,outputs['original'])

RUNE_STUBS={
 ROOT+'model/L1InventoryTransaction.java': 'package l1j.server.server.model; public class L1InventoryTransaction {}',
 ROOT+'model/Instance/L1PcInstance.java': '''package l1j.server.server.model.Instance;
public class L1PcInstance {
  public int flags, target; public boolean has;
  private final Inventory inv = new Inventory();
  public Inventory getInventory() { return inv; }
  public final class Inventory { public boolean checkItem(int itemId,int count){return has && target==itemId && count==1;} }
  public boolean isWarrior(){return (flags&1)!=0;} public boolean isKnight(){return (flags&2)!=0;}
  public boolean isDragonknight(){return (flags&4)!=0;} public boolean isCrown(){return (flags&8)!=0;}
  public boolean isWizard(){return (flags&16)!=0;} public boolean isBlackwizard(){return (flags&32)!=0;}
  public boolean isElf(){return (flags&64)!=0;} public boolean isDarkelf(){return (flags&128)!=0;}
}''',
 ROOT+'model/item/function/PackageRewardStaging.java': '''package l1j.server.server.model.item.function;
import java.util.ArrayList; import l1j.server.server.model.L1InventoryTransaction;
public class PackageRewardStaging {
  public static final StringBuilder log=new StringBuilder(); public static int calls, throwAt;
  public static void stageReward(L1InventoryTransaction tx, ArrayList<String> msg,int id,int count,int ench,int bless,int attr,boolean identified) {step("reward",msg,id,count,ench,bless,attr,identified);}
  public static void stageSealedReward(L1InventoryTransaction tx, ArrayList<String> msg,int id,int count,int ench,int bless,int attr,boolean identified) {step("sealed",msg,id,count,ench,bless,attr,identified);}
  private static void step(String tag,ArrayList<String> msg,int id,int count,int ench,int bless,int attr,boolean identified) {
    calls++;log.append(tag).append(':').append(id).append(',').append(count).append(',').append(ench).append(',').append(bless).append(',').append(attr).append(',').append(identified).append(';');
    if (throwAt==calls) throw new IllegalStateException("injected");msg.add("R"+id);
  }
}'''
}
RUNE_HARNESS='''import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import l1j.server.server.model.item.function.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
public class Harness {
static String hex(byte[] b) {StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
public static void main(String[] args) throws Exception {
MessageDigest md=MessageDigest.getInstance("SHA-256");
int[] ids={60036,60037,60038,60039,60040,60041,60042,60043,60044,60045,68078,68079,68080,68081,68082,68083,68084,68085,68086,68087,68088,68089,68090,68091,68092,0,70000};
L1PcInstance pc=new L1PcInstance();L1InventoryTransaction tx=new L1InventoryTransaction();int count=0;
for(int id:ids)for(int flags=0;flags<256;flags++)for(int available=0;available<2;available++)for(int throwAt=0;throwAt<=2;throwAt++) {
 pc.flags=flags;pc.target=id;pc.has=available!=0;
 PackageRewardStaging.log.setLength(0);PackageRewardStaging.calls=0;PackageRewardStaging.throwAt=throwAt;
 ArrayList<String> messages=new ArrayList<>();String exception="";
 try {RuneRewardPackages.stage(pc,id,tx,messages);}catch(IllegalStateException e){exception=e.getMessage();}
 String line=id+"/"+flags+"/"+available+"/"+throwAt+":"+PackageRewardStaging.log+":"+messages+":"+exception+"\\n";
 md.update(line.getBytes(StandardCharsets.UTF_8));count++;
}
System.out.println("cases="+count+" sha256="+hex(md.digest()));
}}
'''

TOWER_STUBS={
 ROOT+'model/Instance/L1ItemInstance.java':'package l1j.server.server.model.Instance; public class L1ItemInstance {}',
 ROOT+'model/character/skill/L1SkillId.java': 'package l1j.server.server.model.character.skill; public class L1SkillId {public static final int 쉐도우제약=1, 이터디스=2, 버닝샷=4;}',
 ROOT+'model/Instance/L1PcInstance.java': '''package l1j.server.server.model.Instance;
import l1j.server.server.model.item.function.Log;
public class L1PcInstance {
 public int mapId, skills,heading=5;public boolean gm,escapable,auto,paralyzed,sleeped,dead;
 public int getMapId(){return mapId;} public Map getMap(){return new Map();}
 public final class Map {public boolean isEscapable(){return escapable;}}
 public boolean isGm(){return gm;} public boolean isParalyzed(){return paralyzed;}
 public boolean isSleeped(){return sleeped;} public boolean isDead(){return dead;}
 public boolean hasSkillEffect(int id){return (skills&id)!=0;}
 public int getHeading(){return heading;} public boolean getAutoHunt(){return auto;}
 public void start_teleport(int x,int y,int m,int heading,int eff,boolean b,boolean hunt) {Log.s.append("tp:").append(x).append(',').append(y).append(',').append(m).append(',').append(heading).append(',').append(eff).append(',').append(b).append(',').append(hunt).append(';');}
 public void cancelAbsoluteBarrier(){Log.s.append("barrier;");}
}''',
 ROOT+'model/item/function/Log.java':'package l1j.server.server.model.item.function; public class Log {public static final StringBuilder s=new StringBuilder();}',
 ROOT+'model/Getback.java': '''package l1j.server.server.model;
import l1j.server.server.model.Instance.L1PcInstance; import l1j.server.server.model.item.function.Log;
public class Getback {public static boolean returnNull=false;
public static int[] GetBack_Location(L1PcInstance pc,boolean flag) {Log.s.append("getback:").append(flag).append(';');return returnNull?null:new int[]{10000,20000,4};}
}''',
 ROOT+'model/network/core/sc/packet/SC_Paralysis.java': '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_Paralysis {public static final int TYPE_TELEPORT_UNLOCK=98;
public static void send(L1PcInstance pc,int t,boolean val){Log.s.append("paralyze:").append(t).append(',').append(val).append(';');}}
''',
 ROOT+'model/network/core/sc/packet/SC_ServerMessage.java': '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_ServerMessage {public static void send(L1PcInstance pc,int msg){Log.s.append("server:").append(msg).append(';');}}
''',
 ROOT+'model/network/core/sc/packet/SC_SystemMessage.java': '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.item.function.Log;
public class SC_SystemMessage {public static void send(L1PcInstance pc,String msg){Log.s.append("system:").append(msg).append(';');}}
''',
 ROOT+'model/item/function/ItemReceiptOperations.java':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.Instance.L1ItemInstance;
public class ItemReceiptOperations {public static boolean runCallback=true;
public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable r) {Log.s.append("consume;");if (runCallback) r.run();}}
'''
}
TOWER_HARNESS='''import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
import l1j.server.server.model.item.function.*;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.Getback;
public class Harness {
static String hex(byte[] b) {StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
public static void main(String[] args)throws Exception {
MessageDigest md=MessageDigest.getInstance("SHA-256"); int count=0;
int[] maps={-1,0,100,101,12851,12852,12853,12854,12855,12856,12857,12858,12859,12860,12861,12862,12863,13005,99999};
for(int map:maps) for(int skill=0;skill<8;skill++)for(int flags=0;flags<32;flags++){
 L1PcInstance pc=new L1PcInstance();pc.mapId=map;pc.skills=skill;pc.gm=(flags&1)!=0;pc.escapable=(flags&2)!=0;pc.auto=(flags&4)!=0;
 Getback.returnNull=(flags&8)!=0;ItemReceiptOperations.runCallback=(flags&16)==0;
 Log.s.setLength(0);
 boolean ok=DominationTowerReturnHandler.getInstance().handleReturn(pc,new L1ItemInstance());
 String line=map+"/"+skill+"/"+flags+":"+ok+":"+Log.s+"\\n";
 md.update(line.getBytes(StandardCharsets.UTF_8));count++;
}
for(int id=830000;id<=830040;id++)for(int skills=0;skills<8;skills++)for(int flags=0;flags<16;flags++){
 L1PcInstance pc=new L1PcInstance();pc.skills=skills;pc.paralyzed=(flags&1)!=0;pc.sleeped=(flags&2)!=0;pc.dead=(flags&4)!=0;pc.escapable=(flags&8)!=0;
 Log.s.setLength(0);
 OmanAmuletHandler.getInstance().clickItem(pc,id);
 String line=id+"/"+skills+"/"+flags+":"+Log.s+"\\n";
 md.update(line.getBytes(StandardCharsets.UTF_8));count++;
}
System.out.println("cases="+count+" sha256="+hex(md.digest()));
}}
'''

NEAR_STUBS={
 ROOT+'types/Point.java': '''package l1j.server.server.types;
public class Point {
 public static long calls;public final int x,y;
 public Point(int x,int y){this.x=x;this.y=y;}
 public boolean isInScreen(Point p){return Math.max(Math.abs(x-p.x),Math.abs(y-p.y))<=15;}
 public boolean isSamePoint(Point p){return x==p.x&&y==p.y;}
 public int getTileLineDistance(Point p){calls++;return Math.max(Math.abs(x-p.x),Math.abs(y-p.y));}
}''',
 ROOT+'model/L1Object.java': '''package l1j.server.server.model;
import l1j.server.server.types.Point;
public class L1Object {public int map;public Point p;public L1Object(int map,Point p){this.map=map;this.p=p;}
public int getMapId(){return map;} public Point getLocation(){return p;}}
''',
 ROOT+'model/Instance/L1PcInstance.java': '''package l1j.server.server.model.Instance;
import l1j.server.server.model.L1Object;import l1j.server.server.types.Point;
public class L1PcInstance extends L1Object {public L1PcInstance(int map,Point p){super(map,p);}}
''',
 ROOT+'model/Instance/L1NpcInstance.java': '''package l1j.server.server.model.Instance;
import l1j.server.server.model.L1Object;import l1j.server.server.types.Point;
public class L1NpcInstance extends L1Object {public L1NpcInstance(int map,Point p){super(map,p);}}
''',
 ROOT+'model/L1World.java': '''package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.*;
public class L1World {public static final L1World W=new L1World();public static L1World getInstance(){return W;}
public final ArrayList<L1PcInstance> players=new ArrayList<>();public final ArrayList<L1NpcInstance> npcs=new ArrayList<>();
public Collection<L1PcInstance> getAllPlayers(){return players;} public Collection<L1NpcInstance> getAllNpc(){return npcs;}}
''',
 ROOT+'model/network/core/sc/packet/SC_PacketBox.java': '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_PacketBox {public static int count;public static final int GREEN_MESSAGE=9;
public static void send(L1PcInstance pc,int t,String s){count++;}}
''',
 ROOT+'model/network/core/sc/packet/SC_SystemMessage.java': '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage {public static int count;
public static void send(L1PcInstance pc,String s){count++;}}
''',
}

def extract_method(src,name):
    m=re.search(r'public static ArrayList<[^>]+>\s+'+name+r'\(',src)
    assert m,name
    i=src.index('{',m.end())+1;depth=1
    while depth:
        if src[i]=='{':depth+=1
        elif src[i]=='}':depth-=1
        i+=1
    return src[m.start():i]
NEAR_HARNESS='''import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.item.function.*;
import l1j.server.server.types.Point;import l1j.server.server.model.network.core.sc.packet.*;
public class Harness {
static String hex(byte[] b) {StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
public static void main(String[] args)throws Exception {
MessageDigest md=MessageDigest.getInstance("SHA-256");int count=0;long totalDistance=0;L1World w=L1World.getInstance();
L1PcInstance pc=new L1PcInstance(5,new Point(0,0));
for(int radius=-2;radius<=30;radius++)for(int mode=0;mode<70;mode++){
 w.players.clear();w.npcs.clear();
 for(int i=0;i<50;i++){
    int map=(i%7==0)?3:5;
    w.players.add(new L1PcInstance(map,new Point((i*9+mode)%70-30,(i*5+mode*2)%70-30)));
    w.npcs.add(new L1NpcInstance(map,new Point((i*4+mode)%70-30,(i*3+mode*4)%70-30)));
 }
 SC_PacketBox.count=0;SC_SystemMessage.count=0;Point.calls=0;
 var result=NearbyItemTargetQueries.get어드밴스(pc,pc,radius);
 int messagesA=SC_PacketBox.count,messagesB=SC_SystemMessage.count;
 long distance=Point.calls;totalDistance+=distance;
 var npcs=NearbyItemTargetQueries.get어드밴스1(pc,radius);
 String line=radius+"/"+mode+":"+result.size()+":"+npcs.size()+":"+messagesA+":"+messagesB+"\\n";
 md.update(line.getBytes(StandardCharsets.UTF_8));count++;
 if (radius>0 && result.size()==0 && distance<=0)throw new AssertionError("distance not exercised");
}
System.out.println("cases="+count+" sha256="+hex(md.digest()));
System.out.println("distance_calls="+totalDistance);
}}
'''

def nearby_run():
    old=(B/'NearbyItemTargetQueries.java').read_text();new=(P/'NearbyItemTargetQueries.java').read_text()
    outputs={}
    for side,src in [('original',old),('modified',new)]:
        files={'NearbyItemTargetQueries.java':src}
        code='''package l1j.server.server.model.item.function;
import java.util.*;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
import l1j.server.server.types.Point;import l1j.server.server.model.network.core.sc.packet.*;
public final class NearbyItemTargetQueries {private NearbyItemTargetQueries(){}
'''+extract_method(src,'get어드밴스1')+'\n'+extract_method(src,'get어드밴스')+'\n}\n'
        with tempfile.TemporaryDirectory(prefix='rkern_phase20_near_') as temp:
            temp=Path(temp);source=temp/'src';source.mkdir();classes=temp/'classes';classes.mkdir()
            def put(rel,s):
                p=source/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf8')
            put(ROOT+'model/item/function/NearbyItemTargetQueries.java',code)
            for rel,s in NEAR_STUBS.items():put(rel,s)
            put('Harness.java',NEAR_HARNESS)
            p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+[str(f) for f in source.rglob('*.java')],capture_output=True,text=True,env=ENV)
            assert p.returncode==0,p.stderr
            p=subprocess.run(['java','-cp',str(classes),'Harness'],capture_output=True,text=True,env=ENV)
            assert p.returncode==0,p.stderr
            outputs[side]=p.stdout.strip()
    original=outputs['original'].splitlines(); modified=outputs['modified'].splitlines()
    assert original[0]==modified[0],outputs
    old_calls=int(original[1].split('=')[1]);new_calls=int(modified[1].split('=')[1])
    assert new_calls < old_calls,(old_calls,new_calls)
    print('PASS nearby',original[0],f'distance_calls {old_calls} -> {new_calls}')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('area',nargs='?',default='all',choices=['all','rune','tower','nearby'])
    args=parser.parse_args()
    if args.area in ('all','rune'):
        compile_run('rune rewards (class masks, inventory status, injected exceptions)',
            ['RuneRewardPackages.java']+['SealedRuneLevel'+str(n)+'Rewards.java' for n in (55,70,80,85,90)],RUNE_STUBS,RUNE_HARNESS)
    if args.area in ('all','tower'):
        compile_run('tower/oman travel paths', ['DominationTowerReturnHandler.java','OmanAmuletHandler.java'],TOWER_STUBS,TOWER_HARNESS)
    if args.area in ('all','nearby'): nearby_run()
