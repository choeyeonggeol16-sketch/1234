"""Phase 115: type-compile production Java and exercise deterministic typed transaction doubles.
No actual SQL server or live server exercised.
"""
from pathlib import Path
import os, shutil, subprocess, tempfile, textwrap

root = Path(os.environ.get('SOURCE_ROOT', '/mnt/data/work115'))
production = root / 'src/l1j/server/server/model/item/function'
tmp = Path(tempfile.mkdtemp(prefix='rkern115_'))
src = tmp / 'src'
classes = tmp / 'classes'
classes.mkdir()

def put(name, contents):
    f=src/name
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(textwrap.dedent(contents).strip()+'\n', encoding='utf8')
for name in ('QuestProgressItems.java', 'TopRankerBlessingItems.java'):
    put(f'l1j/server/server/model/item/function/{name}', (production/name).read_text('utf8'))
put('l1j/server/server/model/Instance/L1ItemInstance.java', '''
 package l1j.server.server.model.Instance;
 public final class L1ItemInstance {
  private final int objectId, templateId; public int count=1, dirty=0;
  public L1ItemInstance(int oid,int tid){objectId=oid;templateId=tid;}
  public int getId(){return objectId;} public int getItemId(){return templateId;}
  public int getRecordingColumns(){return dirty;}
 }
''')
put('l1j/server/server/model/CUserInventory.java', '''
 package l1j.server.server.model;
 import l1j.server.server.model.Instance.L1ItemInstance;
 public class CUserInventory {
  public static final int COL_SAVE_ALL=0xFFFF;
  public L1ItemInstance current;
  public int itemSaves=0, removes=0;
  public L1ItemInstance getItem(int id){return current!=null && current.getId()==id?current:null;}
  public void saveItem(L1ItemInstance i,int flags){itemSaves++;}
  public void removeItem(L1ItemInstance i){removes++;}
 }
''')
put('l1j/server/server/model/Instance/L1PcInstance.java', '''
 package l1j.server.server.model.Instance;
 import java.sql.Timestamp;
 import l1j.server.server.model.CUserInventory;
 public class L1PcInstance {
  public int mark=60, saveCalls=0, skillMs=0, rank=1;
  public boolean failSave=false,failSkill=false,top=true;
  public Timestamp lastBless;
  public CUserInventory inventory=new CUserInventory();
  public CUserInventory getInventory(){return inventory;}
  public int getId(){return 2001;}
  public int getMark_count(){return mark;}
  public void setMark_count(int v){mark=v;}
  public void save(){saveCalls++;if(failSave)throw new IllegalStateException("save-admission");}
  public boolean is_top_ranker(){return top;}
  public int getRankLevel(){return rank;}
  public void sendPackets(String s){}
  public Timestamp getLastTopBless(){return lastBless;}
  public void setLastTopBless(Timestamp t){lastBless=t;}
  public boolean hasSkillEffect(int id){return skillMs>0;}
  public void setSkillEffect(int id,int ms){if(failSkill)throw new IllegalStateException("effect");skillMs=ms;}
 }
''')
put('l1j/server/server/storage/CacheD.java', '''
 package l1j.server.server.storage;
 import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
 public final class CacheD {
  private static final CacheD INSTANCE=new CacheD();
  public static int queued=0;
  public static CacheD getInstance(){return INSTANCE;}
  public void saveCharacter(CharacterSnapshot s){queued++;}
 }
''')
put('l1j/server/server/storage/mysql/MySqlCharacterStorage.java', '''
 package l1j.server.server.storage.mysql;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class MySqlCharacterStorage {
  public static final class CharacterSnapshot { public final int mark; public CharacterSnapshot(L1PcInstance p){mark=p.mark;} }
 }
''')
put('l1j/server/server/model/character/skill/L1SkillId.java', '''
 package l1j.server.server.model.character.skill;
 public final class L1SkillId {public static final int TOP_RANKER=33;}
''')
put('l1j/server/server/model/gametime/RealTimeClock.java', '''
 package l1j.server.server.model.gametime;
 import java.util.Calendar;
 public final class RealTimeClock {
  public static long now=1234567890000L;
  public static RealTimeClock getInstance(){return new RealTimeClock();}
  public Calendar getRealTimeCalendar(){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);return c;}
 }
''')
put('l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java', '''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_PacketBox {
  public static final int BOOKMARK_SIZE_PLUS_10=141;
  public static int sent=0, last=0;public static boolean fail=false;
  public static void send(L1PcInstance pc,int op,int value){sent++;last=value;if(fail)throw new IllegalStateException("packet");}
 }
''')
put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java', '''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_ServerMessage {public static int sent=0;public static void send(L1PcInstance pc,int msg){sent++;}}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java', '''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_SystemMessage {public static int sent=0;public static void send(L1PcInstance pc,String msg){sent++;}}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java', '''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class SC_SkillSound {
  public static boolean failSelf=false,failBroadcast=false;public static int self=0, broadcast=0;
  public static void send(L1PcInstance p,int id,int s){self++;if(failSelf)throw new IllegalStateException("self sound");}
  public static void broadcast(L1PcInstance p,int id,int s){broadcast++;if(failBroadcast)throw new IllegalStateException("broadcast");}
 }
''')
put('l1j/server/server/model/item/function/ItemActionResult.java', '''
 package l1j.server.server.model.item.function;
 public enum ItemActionResult { COMPLETE,STOP }
''')
put('l1j/server/server/model/item/function/ItemActionContext.java', '''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemActionContext {
   private final L1PcInstance pc;private final L1ItemInstance item;
   public ItemActionContext(L1PcInstance p,L1ItemInstance i){pc=p;item=i;}
   public L1PcInstance player(){return pc;}public L1ItemInstance item(){return item;}
 }
''')
put('l1j/server/server/model/L1InventoryTransaction.java', '''
 package l1j.server.server.model;
 import java.sql.*;
 import java.lang.reflect.*;
 import l1j.server.server.model.Instance.*;
 public final class L1InventoryTransaction implements AutoCloseable {
  public interface SqlMutation {void apply(Connection c) throws SQLException;}
  public static L1InventoryTransaction pending;
  public static int dbMark=60, dbCount=1, submissions=0, errors=0, quarantines=0;
  public static boolean failDebit=false,failSql=false,failCommit=false,externalDbChange=false;
  final L1PcInstance pc; final L1ItemInstance input;
  boolean debit, required, done;
  SqlMutation mutation;Runnable callback,failure;
  private L1InventoryTransaction(L1PcInstance p,L1ItemInstance i){pc=p;input=i;}
  public static L1InventoryTransaction begin(L1PcInstance pc,int resource){
   if(pending!=null)throw new IllegalStateException("pending receipt");
   pending=new L1InventoryTransaction(pc,pc.getInventory().current);
   return pending;
  }
  public boolean debit(L1ItemInstance item,int count){
   if(failDebit||item!=input||count!=1||item.count<1)return false;
   debit=true;return true;
  }
  public void requireTables(String table){if(!table.equals("characters"))throw new AssertionError("table");required=true;}
  public void sql(SqlMutation m){mutation=m;}
  public void onFailure(Runnable r){failure=r;}
  public void commitAfterInventoryRelease(Runnable action){callback=action;submissions++;}
  public void finish() {
   if(pending!=this)throw new AssertionError("pending");
   pending=null;
   int oldMark=dbMark,oldCount=dbCount;
   if(failCommit){errors++;if(failure!=null)failure.run();return;}
   try {
    if(!debit||!required||mutation==null||callback==null)throw new SQLException("incomplete receipt");
    if(externalDbChange) dbMark+=5;
    if(failSql)throw new SQLException("SQL failed");
    mutation.apply(connection());
    if(dbCount<1)throw new SQLException("item shortage");
    dbCount--;input.count--;
   } catch(SQLException ex) {dbMark=oldMark;dbCount=oldCount;errors++;if(failure!=null)failure.run();return;}
   try {callback.run();} catch(RuntimeException ex){quarantines++;throw ex;}
  }
  public void close(){if(pending==this&&!done&&callback==null)pending=null;}
  private Connection connection(){
    InvocationHandler hc=(p,m,a)->{
      if(m.getName().equals("prepareStatement")){
        String sql=(String)a[0];
        if(!sql.equals("UPDATE characters SET Mark_Count=? WHERE objid=? AND Mark_Count=?"))throw new AssertionError(sql);
        int[] args=new int[4];
        InvocationHandler hp=(proxy,method,params)->{
          switch(method.getName()){
          case "setInt" -> {args[(Integer)params[0]]=(Integer)params[1];return null;}
          case "executeUpdate" -> {if(args[2]!=2001 || args[3]!=dbMark)return 0;dbMark=args[1];return 1;}
          case "close" -> {return null;}
          default -> {throw new UnsupportedOperationException(method.getName());}
          }
        };
        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},hp);
      }
      if(m.getName().equals("close"))return null;
      throw new UnsupportedOperationException(m.getName());
    };
    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},hc);
  }
 }
''')
put('l1j/server/server/model/item/function/ItemReceiptOperations.java', '''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemReceiptOperations {
  public static Runnable pending;public static int admitted=0;
  public static void UseDurableConsume(L1PcInstance p,L1ItemInstance i,Runnable r){pending=r;admitted++;}
  public static void finish(boolean ok){Runnable r=pending;pending=null;if(ok&&r!=null)r.run();}
 }
''')
put('l1j/server/server/model/item/function/Regression115.java', r'''
 package l1j.server.server.model.item.function;
 import java.sql.Timestamp;
 import l1j.server.server.model.*;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 import l1j.server.server.model.gametime.RealTimeClock;
 public final class Regression115 {
  static int asserts=0;
  static void ok(boolean value,String label){asserts++;if(!value)throw new AssertionError(label);}
  static L1PcInstance pc(int mark){
    L1PcInstance p=new L1PcInstance();p.mark=mark;p.inventory.current=new L1ItemInstance(11,700022);
    L1InventoryTransaction.pending=null;L1InventoryTransaction.dbMark=mark;L1InventoryTransaction.dbCount=1;
    L1InventoryTransaction.failDebit=false;L1InventoryTransaction.failSql=false;
    L1InventoryTransaction.failCommit=false;L1InventoryTransaction.externalDbChange=false;
    SC_PacketBox.fail=false;return p;
  }
  static void questSuccess() {
   for(int mark:new int[]{0,30,60,70,80,90,95}) {
    L1PcInstance p=pc(mark);
    int before=L1InventoryTransaction.submissions;
    QuestProgressItems.handleMarkReward(new ItemActionContext(p,p.inventory.current));
    ok(p.mark==mark,"no mutation precommit");
    ok(L1InventoryTransaction.dbCount==1,"no item deletion precommit");
    ok(L1InventoryTransaction.submissions==before+1,"one receipt");
    L1InventoryTransaction.pending.finish();
    ok(p.mark==mark+10,"memory mark");
    ok(L1InventoryTransaction.dbMark==mark+10,"DB mark");
    ok(L1InventoryTransaction.dbCount==0,"DB token count");
    ok(p.saveCalls==1,"character save scheduled");
    ok(SC_PacketBox.last==mark+10,"packet count");
   }
  }
  static void noDepleteOnFailure() {
   for(int which=0;which<4;which++) {
    L1PcInstance p=pc(60);
    if(which==0)L1InventoryTransaction.failSql=true;
    if(which==1)L1InventoryTransaction.failCommit=true;
    if(which==2)L1InventoryTransaction.externalDbChange=true;
    if(which==3)L1InventoryTransaction.failDebit=true;
    QuestProgressItems.handleMarkReward(new ItemActionContext(p,p.inventory.current));
    if(which!=3)L1InventoryTransaction.pending.finish();
    ok(p.mark==60,"failure memory stable "+which);
    ok(L1InventoryTransaction.dbMark==60,"failure DB mark stable "+which);
    ok(L1InventoryTransaction.dbCount==1,"failure DB count stable "+which);
    ok(p.saveCalls==0,"failure no save "+which);
   }
  }
  static void noItemWhenCapOrNotOwned() {
   for(int mark:new int[]{100,110,200}) {
    L1PcInstance p=pc(mark);
    QuestProgressItems.handleMarkReward(new ItemActionContext(p,p.inventory.current));
    ok(L1InventoryTransaction.pending==null,"cap no receipt");
    ok(p.mark==mark,"cap stable");
    ok(L1InventoryTransaction.dbCount==1,"cap item exists");
   }
   L1PcInstance p=pc(60);
   QuestProgressItems.handleMarkReward(new ItemActionContext(p,new L1ItemInstance(22,700022)));
   ok(L1InventoryTransaction.pending==null,"stale item no receipt");
   QuestProgressItems.handleMarkReward(new ItemActionContext(p,new L1ItemInstance(11,1)));
   ok(L1InventoryTransaction.pending==null,"incorrect ID no receipt");
  }
  static void postCommitOutputFailure() {
   for(int flags=0;flags<4;flags++) {
    L1PcInstance p=pc(60);
    p.failSave=(flags&1)!=0;SC_PacketBox.fail=(flags&2)!=0;
    QuestProgressItems.handleMarkReward(new ItemActionContext(p,p.inventory.current));
    L1InventoryTransaction.pending.finish();
    ok(p.mark==70,"mark retained after output failure");
    ok(L1InventoryTransaction.dbMark==70,"DB retained after output failure");
    ok(L1InventoryTransaction.dbCount==0,"debit retained after output failure");
    ok(p.saveCalls==1,"save attempted");
   }
  }
  static void postCommitLiveConflict() {
    L1PcInstance p=pc(60);
    QuestProgressItems.handleMarkReward(new ItemActionContext(p,p.inventory.current));
    p.mark=80;
    int before=L1InventoryTransaction.quarantines;
    boolean threw=false;
    try{L1InventoryTransaction.pending.finish();}catch(IllegalStateException expected){threw=true;}
    ok(threw,"live memory conflict detected");
    ok(L1InventoryTransaction.quarantines==before+1,"conflict still protected");
    ok(p.mark==80,"no conflicting memory overwrite");
    ok(L1InventoryTransaction.dbMark==70,"DB already committed");
  }
  static void topBlessing() {
    for(int flags=0;flags<8;flags++) {
      L1PcInstance p=pc(60);p.inventory.current=new L1ItemInstance(55,55558);
      p.failSave=(flags&1)!=0;
      SC_SkillSound.failSelf=(flags&2)!=0;
      SC_SkillSound.failBroadcast=(flags&4)!=0;
      ItemReceiptOperations.pending=null;
      TopRankerBlessingItems.정상의가호2(p,p.inventory.current);
      ok(ItemReceiptOperations.pending!=null,"blessing submitted");
      ok(p.skillMs==0&&p.lastBless==null,"blessing not applied before commit");
      ItemReceiptOperations.finish(true);
      ok(p.skillMs==600000,"blessing applied with output failure");
      ok(p.lastBless!=null&&p.lastBless.getTime()==RealTimeClock.now,"cooldown applied with output failure");
      ok(p.saveCalls==1,"top ranker save attempted");
      ItemReceiptOperations.pending=null;
      TopRankerBlessingItems.정상의가호2(p,p.inventory.current);
      ok(ItemReceiptOperations.pending==null,"cooldown prevents second use");
    }
    SC_SkillSound.failSelf=false;SC_SkillSound.failBroadcast=false;
    L1PcInstance p=pc(60);p.inventory.current=new L1ItemInstance(55,55558);
    TopRankerBlessingItems.정상의가호2(p,p.inventory.current);
    ItemReceiptOperations.finish(false);
    ok(p.skillMs==0&&p.lastBless==null,"rollback no effect or cooldown");
    p=pc(60);p.inventory.current=new L1ItemInstance(55,55558);p.failSkill=true;
    TopRankerBlessingItems.정상의가호2(p,p.inventory.current);
    boolean threw=false;
    try{ItemReceiptOperations.finish(true);}catch(IllegalStateException expected){threw=true;}
    ok(threw,"gameplay error propagated");
    ok(p.lastBless==null,"no cooldown without buff");
  }
  public static void main(String[] args){
    questSuccess();noDepleteOnFailure();noItemWhenCapOrNotOwned();
    postCommitOutputFailure();postCommitLiveConflict();topBlessing();
    System.out.println("PHASE115_PASS typed production classes; checks="+asserts);
  }
 }
''')
try:
    files=[str(x) for x in src.rglob('*.java')]
    javac=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes)]+files,capture_output=True,text=True,timeout=75)
    if javac.returncode:
        print(javac.stdout+javac.stderr)
        raise SystemExit(javac.returncode)
    result=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Regression115'],capture_output=True,text=True,timeout=75)
    print('JAVA_COMPILE_OK files=',len(files))
    print(result.stdout)
    if result.returncode:
        print(result.stderr)
        raise SystemExit(result.returncode)
    print('PHASE115_TEST_OK (typed doubles, no live DB)')
finally:
    shutil.rmtree(tmp, ignore_errors=True)
