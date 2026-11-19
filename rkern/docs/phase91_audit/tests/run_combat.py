#!/usr/bin/env python3
"""Compile exact MJCombatObserver production methods with stub sockets/failure injection."""
import pathlib,subprocess,tempfile,textwrap
ROOT=pathlib.Path(__file__).resolve().parents[3]
s=(ROOT/'src/l1j/server/MJCombatSystem/MJCombatObserver.java').read_text(encoding='utf8')
def method(head):
 i=s.index(head);j=s.index('{',i);n=1;k=j+1
 while n:
  if s[k]=='{':n+=1
  if s[k]=='}':n-=1
  k+=1
 return s[i:k]
methods='\n\n'.join(map(method,[
 'protected void dispose_users(){',
 'protected void on_closing(){',
 'private static void trySendCombatResultNotice(',
 'private static void closeCombatResultSource(',
 'private static void logCombatResultSendFailure('
]))
# Only substitute the enclosing harness class identifier for logger metadata.
methods=methods.replace('MJCombatObserver.class.getName()', 'Combat91.class.getName()')
code=textwrap.dedent('''\
package l1j.server.MJCombatSystem;
import java.util.*;
import java.nio.ByteBuffer;
import l1j.server.server.model.network.core.broadcast.*;
public class Combat91 {
  public static final int REWARDS_INDEX_WINNER_KEEP=0,REWARDS_INDEX_WINNER_DEAD=1,
    REWARDS_INDEX_LOSER_KEEP=2,REWARDS_INDEX_LOSER_DEAD=3;
  public static final List<String> events=new ArrayList<>();
  int _copyMapId=15;
  TeamKda[] _team_kda=new TeamKda[]{new TeamKda(1)};
  L1Party[] _parties=new L1Party[1];
  LinkedList<Integer> _team_q=new LinkedList<>();
  HashMap<Integer,Integer> _user_kda_dictionary=new HashMap<>();
  AbstractReward[][] _rewards=new AbstractReward[][]{{new AbstractReward()},{new AbstractReward()},{new AbstractReward()},{new AbstractReward()}};
  static final class Conn {boolean isConnected(){return true;}}
  public static final class L1PcInstance {
    public final int id,team;boolean active=true,dead=false;
    L1PcInstance(int id,int team){this.id=id;this.team=team;}
    Conn getNetConnection(){return new Conn();}boolean is_combat_field(){return active;}
    int get_current_combat_team_id(){return team;}int getId(){return id;}
    void set_is_non_action(boolean on){events.add("freeze:"+id);}
    boolean isDead(){return dead;}
    L1Party getParty(){return null;}
  }
  public static final class AbstractReward {}
  public static final class L1Party {void leaveMember(L1PcInstance pc){}}
  static final class TeamKda{final int id;TeamKda(int x){id=x;}int get_team_id(){return id;}}
  static final class UserKda{}
  static final class SC_NOTIFICATION_MESSAGE {
    final byte code;SC_NOTIFICATION_MESSAGE(int code){this.code=(byte)code;}
    byte[] encode(){return new byte[]{code};}
  }
  static final class MJCombatLoadManager {
    static final int COMBAT_QUIT_DELAY_SECOND=5;
    static SC_NOTIFICATION_MESSAGE[] create_result_notify(TeamKda win,UserKda[] ranks){
      return new SC_NOTIFICATION_MESSAGE[]{new SC_NOTIFICATION_MESSAGE(1),new SC_NOTIFICATION_MESSAGE(2),new SC_NOTIFICATION_MESSAGE(3)};
    }
  }
  static final class L1World {
    static L1World getInstance(){return new L1World();}
    Collection<L1PcInstance> getVisiblePlayers(int map){return people;}
  }
  static final class ThreadPool {
    static ThreadPool getInstance(){return new ThreadPool();}
    void scheduleRequired(Runnable r,long ms){events.add("schedule");}
  }
  static List<L1PcInstance> people=new ArrayList<>();
  TeamKda select_winner_kda(){return new TeamKda(1);}
  UserKda[] select_ranker_kda(){return new UserKda[3];}
  void do_rewards(AbstractReward[] rewards,L1PcInstance pc){events.add("reward:"+pc.id);}
  void leave(L1PcInstance pc){events.add("leave:"+pc.id);}
  /* METHODS */
  static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg+" "+events);}
  static void init(){
    events.clear();people=List.of(new L1PcInstance(1,1),new L1PcInstance(2,2));
    CSharedBroadcast.failOpen=-1;CSharedBroadcast.failSendId=-1;CSharedBroadcast.failCountdown=false;
    CAsyncSendConstBuffer.failClose=false;
  }
  public static void main(String[] args){
    java.util.logging.Logger.getLogger(Combat91.class.getName()).setLevel(java.util.logging.Level.OFF);
    init();new Combat91().on_closing();
    check(events.equals(List.of("freeze:1","send:1:1","send:3:1","reward:1",
      "freeze:2","send:2:2","send:3:2","reward:2")),"normal packet/reward FIFO order changed");
    init();CSharedBroadcast.failSendId=1;new Combat91().on_closing();
    check(events.contains("reward:1")&&events.contains("reward:2"),"failed recipient blocked rewards");
    check(events.contains("send:3:1"),"failed first message skipped later common result");
    init();CSharedBroadcast.failOpen=1;new Combat91().on_closing();
    check(events.contains("reward:1")&&events.contains("reward:2"),"failed source blocked rewards");
    init();CAsyncSendConstBuffer.failClose=true;new Combat91().on_closing();
    check(events.contains("reward:1")&&events.contains("reward:2"),"failed release blocked rewards");
    init();CSharedBroadcast.failCountdown=true;new Combat91().dispose_users();
    check(events.contains("schedule"),"failed countdown blocked evacuation scheduling");
    System.out.println("91 actual combat methods: 5 normal/source/send/release/countdown scenarios PASS");
  }
}
''').replace('/* METHODS */',methods)
stub=textwrap.dedent('''\
package l1j.server.server.model.network.core.broadcast;
import l1j.server.MJCombatSystem.Combat91;
import java.nio.ByteBuffer;
import java.util.Collection;
public class CSharedBroadcast {
  public interface NativeWireWriter {void write(ByteBuffer b);}
  public static int failOpen=-1,failSendId=-1;
  public static boolean failCountdown=false;
  public static CAsyncSendConstBuffer openRaw(byte[] data){
    if(data[0]==failOpen)throw new IllegalStateException("source failed");
    return new CAsyncSendConstBuffer(data[0]);
  }
  public static void sendRecipient(CAsyncSendConstBuffer src,Combat91.L1PcInstance pc){
    if(src==null)return;
    if(pc.id==failSendId && src.code!=3)throw new IllegalStateException("recipient failed");
    Combat91.events.add("send:"+src.code+":"+pc.id);
  }
  public static void sendWrittenRecipients(Collection<Combat91.L1PcInstance> people,NativeWireWriter write){
    if(failCountdown)throw new IllegalStateException("countdown failed");
    ByteBuffer b=ByteBuffer.allocate(8);b.position(2);write.write(b);
  }
  public static void sendWrittenRecipientsBestEffort(Collection<Combat91.L1PcInstance> people,NativeWireWriter write){
    try{ sendWrittenRecipients(people,write); }catch(RuntimeException optionalFailure){};
  }
}
''')
cb=textwrap.dedent('''\
package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer implements AutoCloseable {
  public final int code;public static boolean failClose=false;
  public CAsyncSendConstBuffer(int code){this.code=code;}
  public void close(){if(failClose)throw new IllegalStateException("release failed");}
}
''')
# Keep minimal stub for fully-qualified Opcodes referenced by exit path
opcode='package l1j.server.server; public class Opcodes {public static final int S_EVENT=10;}\n'
with tempfile.TemporaryDirectory() as td:
 b=pathlib.Path(td)
 for path,content in {
  'l1j/server/MJCombatSystem/Combat91.java':code,
  'l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java':stub,
  'l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java':cb,
  'l1j/server/server/Opcodes.java':opcode
 }.items():
  p=b/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 subprocess.run(['javac','--release','21','-d',td,*map(str,b.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',td,'l1j.server.MJCombatSystem.Combat91'],check=True)
