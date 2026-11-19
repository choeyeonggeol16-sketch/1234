#!/usr/bin/env python3
"""Compile actual extracted production mini-game methods under failure-injection stubs.
No server socket or database required. Requires Java 21.
"""
import pathlib, subprocess, tempfile, re, sys
ROOT=pathlib.Path(__file__).resolve().parents[3] # ZIP root
SOURCE=(pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'src/l1j/server/server/model')


def matched(s,pos,lc='{',rc='}'):
    assert s[pos]==lc
    dep=0;state='code';i=pos
    while i<len(s):
        c=s[i];d=s[i+1] if i+1<len(s) else ''
        if state=='code':
            if c=='"':state='str'
            elif c=="'":state='chr'
            elif c=='/' and d=='/':state='line';i+=1
            elif c=='/' and d=='*':state='block';i+=1
            elif c==lc:dep+=1
            elif c==rc:
                dep-=1
                if dep==0:return i
        elif state in ('str','chr'):
            if c=='\\':i+=1
            elif c==('"' if state=='str' else "'"):state='code'
        elif state=='line':
            if c=='\n':state='code'
        elif state=='block':
            if c=='*' and d=='/':state='code';i+=1
        i+=1
    raise ValueError('mismatched brace')


def method(src,name):
    m=re.search(r'(?m)^\s*(?:public|private|protected)\s+(?:(?:static|final)\s+)*(?:boolean|void|[\w.<>]+)\s+'+re.escape(name)+r'\s*\(',src)
    if not m:raise ValueError('missing production method '+name)
    a=src.find('{',m.end());return src[m.start():matched(src,a)+1]

COMMON='''package l1j.server.server.model;
import java.util.*;
public class L1PcInstance {
 final int mapId; final Inventory inventory=new Inventory();
 int teleports; boolean deathMatch,ghost;
 L1PcInstance(int mapId){this.mapId=mapId;}
 int getMapId(){return mapId;}
 int getHeading(){return 5;}
 Inventory getInventory(){return inventory;}
 void start_teleport(int x,int y,int map,int direction,int effect,boolean a,boolean b){teleports++;}
 boolean isGhost(){return ghost;}
 void DeathMatchEndGhost(){}
 void setDeathMatch(boolean b){deathMatch=b;}
 String getName(){return "T";}
}
class Inventory {
 final Map<Integer,Integer> amounts=new HashMap<>();
 void storeItem(int item,int count){amounts.merge(item,count,Integer::sum);}
}
class SC_ServerMessage {
 static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int id, Object... values){return b->{};}
 static void send(L1PcInstance pc,int id,Object... values){}
}
class SC_SystemMessage {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(String s){return b->{};}}
class SC_PacketBox {
 static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeMiniGameEndWriter(){return b->{};}
 static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeMiniGameTimeWriter(int n){return b->{};}
}
class SC_GameOver {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(){return b->{};}}
class SC_GameEnd {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(){return b->{};}}
class SC_GameStart {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(){return b->{};}}
class SC_GameRap {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int v){return b->{};}}
class SC_EtcPacket {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(){return b->{};}}
class SC_GameList {static boolean fail;static int calls;static void send(L1PcInstance pc,int idx) {calls++;if(fail)throw new IllegalStateException("list SEND");}}
class L1PolyMorph {static final int MORPH_BY_NPC=1; static void doPoly(L1PcInstance pc,int id,int t,int mode,boolean b) {}}
class L1ItemInstance {void setCount(int n){} String getLogName(){return "test";}}
class ItemTable {static ItemTable getInstance(){return new ItemTable();} L1ItemInstance createItem(int id){return new L1ItemInstance();}}
class L1World {
 static L1World active=new L1World();
 static L1World getInstance(){return active;}
 java.util.List<L1PcInstance> allPlayers=new ArrayList<>();
 java.util.List<L1PcInstance> getAllPlayers(){return allPlayers;}
}
'''
BROADCAST='''package l1j.server.server.model.network.core.broadcast;
import java.nio.ByteBuffer;
import l1j.server.server.model.L1PcInstance;
public class CSharedBroadcast {
 @FunctionalInterface public interface NativeWireWriter {void write(ByteBuffer b);}
 public static boolean failSend,failFanout;
 public static int attempts;
 public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc){
  if(shared==null || pc==null)return;
  attempts++;
  if(failSend)throw new IllegalStateException("injected native SEND failure");
 }
 public static void sendWrittenArray(L1PcInstance[] recipients,NativeWireWriter writer){
  if(failFanout)throw new IllegalStateException("injected fanout failure");
 }
 public static void sendWrittenArrayBestEffort(L1PcInstance[] recipients,NativeWireWriter writer){
  try{sendWrittenArray(recipients,writer);}catch(RuntimeException optionalFailure){}
 }
}
'''
BUFFER='''package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer implements AutoCloseable {
 public static boolean failOpen,failClose;
 public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter w,int sender,int lim){
  if(failOpen)throw new IllegalStateException("injected open failure");
 }
 @Override public void close(){if(failClose)throw new IllegalStateException("injected close failure");}
}
'''

CONFIG={
 'L1HauntedHouse': (['readyHauntedHouse','broadcast','countDown'],'''
 final java.util.ArrayList<L1PcInstance> members=new java.util.ArrayList<>();
 int status,count;
 static final int STATUS_NONE=0,STATUS_PLAYING=2;
 L1PcInstance[] getMembersArray(){return members.toArray(new L1PcInstance[0]);}
 int getMembersCount(){return members.size();}
 void removeMember(L1PcInstance pc){members.remove(pc);}
 void setHauntedHouseStatus(int n){status=n;}
 void setCount(int n){count=n;}
 private int _count;
 ''', '''
 static void verify(){
  for(int mode=0;mode<4;mode++){
   l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=(mode==0); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=(mode==1); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=(mode==2);
   L1HauntedHouse house=new L1HauntedHouse();L1PcInstance pc=new L1PcInstance(5140);house.members.add(pc);
   boolean result=house.readyHauntedHouse();
   check(!result && pc.inventory.amounts.getOrDefault(40308,0)==1000 && pc.teleports==1 && house.members.isEmpty() && house.status==0,"haunted refund/exit mode="+mode);
  }
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=true;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=false;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=false;
  L1HauntedHouse house=new L1HauntedHouse();house._count=3;house.members.add(new L1PcInstance(5140));
  check(!house.countDown() && house._count==2,"haunted countdown");
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=false;
  L1HauntedHouse empty=new L1HauntedHouse();empty.status=1;
  check(!empty.readyHauntedHouse() && empty.status==0,"haunted empty cancel");
 }
 ''',),
 'L1Racing': (['readyPetRacing','kickAllPlayers','endGame'],'''
 final java.util.ArrayList<L1PcInstance>[] lists=new java.util.ArrayList[18];
 L1Racing(){for(int j=0;j<18;j++)lists[j]=new java.util.ArrayList<>();}
 final int 일반=0;int status,_count;
 static final int STATUS_NONE=0,STATUS_PLAYING=2,STATUS_CLEANUP=3;
 int size(int id){return lists[id].size();}
 L1PcInstance[] toArray(int id){return lists[id].toArray(new L1PcInstance[0]);}
 int getMapId(){return 5143;}
 void removeMember(L1PcInstance pc){lists[0].remove(pc);}
 void clearMembers(){lists[0].clear();}
 void setGameStatus(int id){status=id;}
 int getGameStatus(){return status;}
 int Rnd(int size){return 0;}
 ''','''
 static void verify(){
  for(int mode=0;mode<4;mode++){
   l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=(mode==0); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=(mode==1); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=(mode==2);
   L1Racing racing=new L1Racing();L1PcInstance pc=new L1PcInstance(5143);racing.lists[0].add(pc);
   check(!racing.readyPetRacing() && pc.inventory.amounts.getOrDefault(40308,0)==1000 && pc.teleports==1 && racing.lists[0].isEmpty() && racing.status==0,"racing refund/exit mode="+mode);
  }
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=true;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=false;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=false;
  L1Racing racing=new L1Racing();L1PcInstance p=new L1PcInstance(5143), q=new L1PcInstance(5143);
  racing.lists[0].add(p);racing.lists[0].add(q);racing.kickAllPlayers();
  check(p.teleports==1 && q.teleports==1 && racing.lists[0].isEmpty(),"racing kick all");
  L1Racing finish=new L1Racing();finish.status=STATUS_PLAYING;finish._count=0;
  L1World.active.allPlayers.clear();L1World.active.allPlayers.add(p);
  finish.endGame();
  check(finish.status==STATUS_CLEANUP && finish._count==10,"racing finish state");
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=false;
  L1Racing empty=new L1Racing();empty.status=1;
  check(!empty.readyPetRacing() && empty.status==0,"racing empty cancel");
  L1Racing start=new L1Racing();start.lists[0].add(new L1PcInstance(5143));start.lists[0].add(new L1PcInstance(5143));
  SC_GameList.fail=true;start.readyPetRacing();SC_GameList.fail=false;
  check(SC_GameList.calls==2 && start.status==STATUS_PLAYING,"racing list failure isolation");
 }
 ''',),
 'L1DeathMatch': (['readyDeathMatch','sendMessage','endDeathMatch'],'''
 final java.util.ArrayList<L1PcInstance> members=new java.util.ArrayList<>();
 static int Count;
 L1PcInstance[] getMembersArray(){return members.toArray(new L1PcInstance[0]);}
 void removeMember(L1PcInstance pc){members.remove(pc);}
 void clearMembers(){members.clear();}
 ''','''
 static void verify(){
  for(int mode=0;mode<4;mode++){
   l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=(mode==0); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=(mode==1); l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=(mode==2);
   L1DeathMatch match=new L1DeathMatch();Count=0;L1PcInstance pc=new L1PcInstance(5153);match.members.add(pc);
   match.readyDeathMatch();
   check(pc.inventory.amounts.getOrDefault(40308,0)==1000 && pc.teleports==1 && match.members.isEmpty(),"deathmatch refund/exit mode="+mode);
  }
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=true;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=false;l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=false;l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failFanout=true;
  L1DeathMatch match=new L1DeathMatch();Count=2;L1PcInstance x=new L1PcInstance(5153),y=new L1PcInstance(5153);match.members.add(x);match.members.add(y);
  match.endDeathMatch();
  check(x.inventory.amounts.getOrDefault(410007,0)==1 && y.inventory.amounts.getOrDefault(410007,0)==1 && x.teleports==1 && y.teleports==1 && match.members.isEmpty() && Count==0,"deathmatch end reward/exit");
  l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=false;l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failFanout=false;
 }
 ''',)
}


def check_src():
    results=[]
    with tempfile.TemporaryDirectory(prefix='game-notice92-') as d:
        out=pathlib.Path(d)
        common_path = out / 'l1j/server/server/model/Common.java'
        common_path.parent.mkdir(parents=True, exist_ok=True)
        player_type = COMMON[:COMMON.index('class Inventory {')]
        common_types = COMMON[COMMON.index('class Inventory {'):]
        (common_path.parent / 'L1PcInstance.java').write_text(player_type)
        common_path.write_text('package l1j.server.server.model;\nimport java.util.*;\n' + common_types)
        for cls, (methods,fields,tests) in CONFIG.items():
            src=(SOURCE/(cls+'.java')).read_text(encoding='utf8')
            helpers=[method(src,n) for n in ['openGameBroadcast','sendGameNotice','closeGameBroadcast'] if re.search(r'\b'+n+r'\s*\(',src)]
            body='\n'.join(method(src,n) for n in methods)
            hdr='package l1j.server.server.model;\nimport java.util.*;\n'
            text=(hdr+'public class '+cls+' {\nprivate static final java.util.logging.Logger GAME_NOTICE_LOG=java.util.logging.Logger.getLogger('+cls+'.class.getName());\n'+fields+'\n'+ '\n'.join(helpers)+'\n'+body+'\n'+tests+'\nstatic void check(boolean b,String m){if(!b)throw new AssertionError(m);}\n}\n')
            (common_path.parent/(cls+'.java')).write_text(text)
        bpath=out/'l1j/server/server/model/network/core/broadcast';bpath.mkdir(parents=True,exist_ok=True)
        (bpath/'CSharedBroadcast.java').write_text(BROADCAST);(bpath/'CAsyncSendConstBuffer.java').write_text(BUFFER)
        (common_path.parent/'Main.java').write_text('''package l1j.server.server.model; public class Main {public static void main(String[] args){L1HauntedHouse.verify();L1Racing.verify();L1DeathMatch.verify();System.out.println("92 production extracted mini-game failure injection PASS");}}''')
        outclasses=out/'classes';outclasses.mkdir()
        java=[str(f) for f in out.rglob('*.java')]
        res=subprocess.run(['javac','-encoding','UTF-8','-d',str(outclasses),*java],capture_output=True,text=True)
        if res.returncode:raise RuntimeError('JAVAC FAIL\n'+res.stderr)
        res=subprocess.run(['java','-cp',str(outclasses),'l1j.server.server.model.Main'],capture_output=True,text=True)
        if res.returncode:raise RuntimeError('TEST FAIL\n'+res.stderr)
        print(res.stdout.strip())
if __name__=='__main__':check_src()
