#!/usr/bin/env python3
"""Compile exact production error-handling methods with deterministic fault injection."""
import pathlib, subprocess, tempfile, textwrap
ROOT=pathlib.Path(__file__).resolve().parents[3]
SRC=ROOT/'src'

def extract(java, declaration):
    s=java.read_text(encoding='utf8')
    start=s.index(declaration)
    op=s.index('{',start)
    depth=1;i=op+1
    while depth:
        if s[i]=='{': depth+=1
        if s[i]=='}': depth-=1
        i+=1
    return s[start:i]

gludio=extract(SRC/'l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java', 'private void finish(Room r,String reason) {')
clan=extract(SRC/'l1j/server/server/model/network/core/cs/packet/CS_LeavePledge.java', 'private static void leaveClanBoss(')

java=textwrap.dedent('''\
package l1j.server.IndunSystem.Gludio;
import java.util.*;
import java.util.logging.*;
import java.io.File;
import l1j.server.server.model.Instance.L1PcInstance;
public class FailureRegression91 {
  static final Logger LOG = Logger.getLogger("FailureRegression91");
  public static final List<String> events = new ArrayList<>();
  static boolean failNotice, failTeleFirst;
  static int cleanupCount;
  static void check(boolean b,String msg) {if(!b)throw new AssertionError(msg+": "+events);}
  static class Room {boolean ended; int id=33,mapId=730; List<Member> members=new ArrayList<>();}
  static class Member {L1PcInstance pc; int charId; Member(int id,int map){charId=id;pc=new L1PcInstance(id,map);}}
  void dissolve(Room r) { r.ended=true;events.add("dissolve"); }
  void notice(Room r,String reason) {
    events.add("notice");if(failNotice)throw new IllegalStateException("notice failed");
  }
  void scheduleCleanup(Room r,long delay) {cleanupCount++;events.add("cleanup");}
  /* HERE_FINISH */
  static class L1Clan {
    static class ClanMember {String name;ClanMember(String n){name=n;}}
    List<ClanMember> members=new ArrayList<>();
    List<ClanMember> getClanMemberList(){return members;}
    int getCastleId(){return 0;}int getHouseId(){return 0;}
    Object getCurrentWar(){return null;}int getAlliance(){return 0;}
    int getClanId(){return 42;}
  }
  static class L1SkillId {static final int 무인혈맹=1;}
  static class SC_PacketBox {
    static final int GREEN_MESSAGE=1;
    static void send(L1PcInstance pc,int type,String msg){}
  }
  static class SC_ServerMessage {
    static void send(L1PcInstance pc,int type){}
    static Object nativeWriter(int type,String a,String b,String c){return new Object();}
  }
  static class ServerMessage {
    static final int HAVING_NEST_OF_CLAN=0,CANNOT_BREAK_CLAN=1,CANNOT_BREAK_CLAN_HAVING_FRIENDS=2;
  }
  static class ClanTable {
    static ClanTable getInstance(){return new ClanTable();}
    void deleteClanWithMembers(L1Clan clan){events.add("dbCommit");}
  }
  static class L1World {
    static L1World getInstance(){return new L1World();}
    L1PcInstance getPlayer(String name){
      return switch(name) {case "boss"->boss;case "a"->a;case "b"->b;default->null;};
    }
  }
  static class L1ClanMatching {
    static L1ClanMatching getInstance(){return new L1ClanMatching();}
    void deleteClanMatching(L1PcInstance pc){events.add("matchmaking");}
  }
  static L1PcInstance boss, a, b;
  /* HERE_CLAN */
  static void reset() {
    events.clear();cleanupCount=0;failNotice=false;failTeleFirst=false;
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failId=-1;
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failOpen=false;
    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=false;
    GludioDungeonPackets.failEncode=false;
  }
  static void runGludio(boolean noticeFail,boolean sourceFail,boolean encodeFail,boolean sendFail,boolean teleportFail,boolean closeFail) {
    reset();failNotice=noticeFail;
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failOpen=sourceFail;
    GludioDungeonPackets.failEncode=encodeFail;
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failId=sendFail?1:-1;
    l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=closeFail;
    Room r=new Room();r.members.add(new Member(1,730));r.members.add(new Member(2,730));r.members.add(new Member(3,731));
    r.members.get(0).pc.failTeleport=teleportFail;
    new FailureRegression91().finish(r,"done");
    check(r.ended,"room not ended");check(cleanupCount==1,"cleanup not scheduled once");
    check(events.contains("teleport:2"),"later member not teleported");
    check(events.indexOf("cleanup")>events.indexOf("teleport:2"),"cleanup precedes teleport");
    if(!sourceFail&&!encodeFail&&!sendFail)check(events.indexOf("status:1")<events.indexOf("teleport:1"),"status/teleport order");
    check(!events.contains("teleport:3"),"off-map member teleported");
  }
  static void runClan(boolean fail) throws Exception {
    reset();boss=new L1PcInstance(10,730);boss.name="boss";boss.clanName="clan";
    a=new L1PcInstance(11,730);a.name="a";b=new L1PcInstance(12,730);b.name="b";
    L1Clan clan=new L1Clan();clan.members.add(new L1Clan.ClanMember("boss"));
    clan.members.add(new L1Clan.ClanMember("a"));clan.members.add(new L1Clan.ClanMember("b"));
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failId=fail?11:-1;
    leaveClanBoss(clan,boss);
    check(events.get(0).equals("dbCommit"),"DB commit order changed");
    check(events.contains("clear:11")&&events.contains("clear:12"),"member cleanup skipped after send failure");
    check(events.indexOf("clear:11")<events.indexOf("status:12"),"member order changed");
    check(events.contains("matchmaking"),"matchmaking cleanup skipped");
  }
  public static void main(String[] args) throws Exception {
    LOG.setLevel(Level.OFF);
    runGludio(false,false,false,false,false,false);
    runGludio(true,false,false,false,false,false);
    runGludio(false,true,false,false,false,false);
    runGludio(false,false,true,false,false,false);
    runGludio(false,false,false,true,false,false);
    runGludio(false,false,false,false,true,false);
    runGludio(false,false,false,false,false,true);
    runClan(false);runClan(true);
    System.out.println("91 actual extracted production methods: 7 dungeon + 2 clan failure scenarios PASS");
  }
}
''').replace('/* HERE_FINISH */',gludio).replace('/* HERE_CLAN */',clan)

sock=textwrap.dedent('''\
package l1j.server.server.model.Instance;
import l1j.server.IndunSystem.Gludio.FailureRegression91;
public class L1PcInstance {
  public final int id;private final int map;
  public String name="",clanName="";public boolean failTeleport;
  public L1PcInstance(int id,int map){this.id=id;this.map=map;}
  public int getId(){return id;}public int getMapId(){return map;}
  public void start_teleport(int x,int y,int map,int heading,int gfx,boolean a,boolean b){
    FailureRegression91.events.add("teleport:"+id);if(failTeleport)throw new IllegalStateException("teleport failed");
  }
  public String getName(){return name;}public String getClanname(){return clanName;}
  public boolean hasSkillEffect(int id){return false;}
  public void sendPackets(String msg){}
  public void ClearPlayerClanData(Object clan){FailureRegression91.events.add("clear:"+id);}
}
''')
shared=textwrap.dedent('''\
package l1j.server.server.model.network.core.broadcast;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.IndunSystem.Gludio.FailureRegression91;
public class CSharedBroadcast {
  public static int failId=-1;public static boolean failOpen=false;
  public static CAsyncSendConstBuffer openRaw(byte[] bytes){
    if(failOpen)throw new IllegalStateException("pooled source unavailable");
    return new CAsyncSendConstBuffer();
  }
  public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc){
    if(shared==null)return;
    if(pc.id==failId)throw new IllegalStateException("send failed");
    FailureRegression91.events.add("status:"+pc.id);
  }
}
''')
cb=textwrap.dedent('''\
package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer implements AutoCloseable {
  public static boolean failClose=false;
  public CAsyncSendConstBuffer(){}
  public CAsyncSendConstBuffer(Object writer,int sender,int limit){}
  public void close(){if(failClose)throw new IllegalStateException("release failed");}
}
''')
proto=textwrap.dedent('''\
package l1j.server.IndunSystem.Gludio;
public class GludioDungeonPackets {
  static boolean failEncode;
  static byte[] encodeArenaPlayStatus(int seconds,boolean started){
    if(failEncode)throw new IllegalStateException("encode failed");return new byte[]{1};
  }
}
''')

with tempfile.TemporaryDirectory() as td:
    base=pathlib.Path(td)
    for rel,txt in {
        'l1j/server/IndunSystem/Gludio/FailureRegression91.java':java,
        'l1j/server/IndunSystem/Gludio/GludioDungeonPackets.java':proto,
        'l1j/server/server/model/Instance/L1PcInstance.java':sock,
        'l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java':shared,
        'l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java':cb,
    }.items():
        p=base/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(txt,encoding='utf8')
    files=[str(x) for x in base.rglob('*.java')]
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(base),*files],check=True)
    subprocess.run(['java','-cp',str(base),'l1j.server.IndunSystem.Gludio.FailureRegression91'],check=True)
