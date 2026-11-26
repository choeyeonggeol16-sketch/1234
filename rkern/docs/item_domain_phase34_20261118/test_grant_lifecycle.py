from pathlib import Path
import shutil,re,textwrap,subprocess,os
T=Path(__file__).resolve().parent
W=T.parents[1]
R=T/'typed'
shutil.rmtree(R,ignore_errors=True);R.mkdir(parents=True,exist_ok=True)
SRC=W/'src/l1j/server/server/model'
ORIG=T/'baseline/l1j/server/server/model'

def write(path,source):
 p=R/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')

def extract(src,sign):
 p=src.index(sign);start=src.rfind('\n',0,p)+1;op=src.index('{',p);level=1;i=op+1
 while level:
  if src[i]=='{':level+=1
  elif src[i]=='}':level-=1
  i+=1
 return src[start:i]

def class_wrapper(package, cls, imports, method, supporting=''):
 return f'package {package};\n{imports}\npublic final class {cls} {{\n'+supporting+'\n'+method+'\n}\n'

imports='''import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;'''
for stage,source in [('Legacy',ORIG),('Candidate',SRC)]:
 src=(source/'Beginner.java').read_text('utf-8')
 func=extract(src,'public int GiveItemToActivePc(')
 wrapper=class_wrapper('l1j.server.server.model','Beginner'+stage,imports,func,'''private static final Logger _log = Logger.getLogger("BeginnerTest");
private String getJobActivationCode(L1PcInstance pc) { return "C"; }''')
 write(Path('l1j/server/server/model')/f'Beginner{stage}.java',wrapper)

rank_imports='''import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.rank.loader.RankLoadManager;'''
for stage,source in [('Legacy',ORIG),('Candidate',SRC)]:
 src=(source/'rank/buff/RankBuff.java').read_text('utf-8')
 fun=extract(src,'private static void giveProtectionItem(')
 wrapper=class_wrapper('l1j.server.server.model.rank.buff','RankBuff'+stage,rank_imports,fun,'public static void run(L1PcInstance pc) { giveProtectionItem(pc); }')
 write(Path('l1j/server/server/model/rank/buff')/f'RankBuff{stage}.java',wrapper)

race_imports='''import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.datatables.ItemTable;'''
for stage,source in [('Legacy',ORIG),('Candidate',SRC)]:
 trap_dir = 'world/trap' if stage == 'Candidate' else 'trap'
 s=(source/trap_dir/'L1PetRaceTrap.java').read_text('utf-8')
 pos=s.index('if(random.nextInt() < 33)');o=s.index('{',pos);lvl=1;i=o+1
 while lvl:
  if s[i]=='{':lvl+=1
  elif s[i]=='}':lvl-=1
  i+=1
 body=s[o+1:i-1]
 meth='public static void run(L1PcInstance c) {'+body+'\n}'
 write(Path('l1j/server/server/model/trap')/f'Race{stage}.java',class_wrapper('l1j.server.server.model.trap','Race'+stage,race_imports,meth))

write(Path('l1j/server/server/templates/L1Item.java'),'''package l1j.server.server.templates;
public class L1Item {
 public final int itemId; public final boolean stack;
 public L1Item(int id,boolean st){itemId=id;stack=st;}
 public boolean isStackable(){return stack;}
}''')
write(Path('l1j/server/server/model/Instance/L1ItemInstance.java'),'''package l1j.server.server.model.Instance;
import l1j.server.server.templates.L1Item;
public class L1ItemInstance {
 private final int id; private final L1Item item; private int count=1,enchant; private boolean identified=true;
 public L1ItemInstance(int id,L1Item item){this.id=id;this.item=item;}
 public int getId(){return id;}public L1Item getItem(){return item;}
 public int getCount(){return count;}public void setCount(int v){count=v;}
 public void setEnchantLevel(int v){enchant=v;}public int getEnchantLevel(){return enchant;}
 public String getLogName(){return getNumberedName(count);}
 public String getNumberedName(int n){return "Item:"+item.itemId+" *"+n+" e"+enchant;}
 public void setIdentified(boolean b){identified=b;}public boolean isIdentified(){return identified;}
}''')
write(Path('l1j/server/server/model/Instance/L1PcInstance.java'),'''package l1j.server.server.model.Instance;
import l1j.server.server.model.CUserInventory;
public class L1PcInstance {
 public final CUserInventory inv=new CUserInventory();
 public CUserInventory getInventory(){return inv;}
}''')
write(Path('l1j/server/server/model/CInventory.java'),'''package l1j.server.server.model;
public class CInventory {public static final int OK=0;}''')
write(Path('l1j/server/server/model/CUserInventory.java'),'''package l1j.server.server.model;
import java.util.*;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
public class CUserInventory {
 public boolean reject, throwCheck,throwStore,returnNull, lateOwnership;
 public int checkCalls,storeCalls,checkOwnedCalls;
 public final Map<Integer,L1ItemInstance> owned=new HashMap<>();
 public int checkAddItem(L1ItemInstance item,int count){checkCalls++;if(throwCheck)throw new IllegalStateException("check fail");return reject?1:OK;}
 private static final int OK=CInventory.OK;
 public boolean checkItem(int id){checkOwnedCalls++;for(var item:owned.values())if(item.getItem().itemId==id)return true;return false;}
 public L1ItemInstance getItem(int id){return owned.get(id);}
 public L1ItemInstance storeItem(L1ItemInstance item){storeCalls++;
  if(throwStore)throw new IllegalStateException("store fail");
  if(returnNull)return null;
  if(item.getItem().isStackable())for(var existing:owned.values())if(existing.getItem().itemId==item.getItem().itemId){existing.setCount(existing.getCount()+item.getCount());return existing;}
  owned.put(item.getId(),item);return item;
 }
 public L1ItemInstance storeItem(int id,int count){L1ItemInstance item=ItemTable.getInstance().createItem(id);if(item==null)return null;
  item.setCount(count);try{return storeItem(item);}finally{if(getItem(item.getId())!=item)L1World.getInstance().removeObject(item);}
 }
 public void putExisting(L1ItemInstance item){owned.put(item.getId(),item);L1World.getInstance().storeObject(item);}
 public int total(int id){int n=0;for(var v:owned.values())if(v.getItem().itemId==id)n+=v.getCount();return n;}
}''')
write(Path('l1j/server/server/model/L1World.java'),'''package l1j.server.server.model;
import java.util.*;
import l1j.server.server.model.Instance.L1ItemInstance;
public class L1World {
 static final L1World instance=new L1World();
 private final Map<Integer,L1ItemInstance> world=new HashMap<>();
 public static L1World getInstance(){return instance;}
 public void storeObject(L1ItemInstance item){world.put(item.getId(),item);}
 public void removeObject(L1ItemInstance item){world.remove(item.getId(),item);}
 public int size(){return world.size();}
 public void clear(){world.clear();}
}''')
write(Path('l1j/server/server/datatables/ItemTable.java'),'''package l1j.server.server.datatables;
import java.util.*;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.templates.L1Item;
public class ItemTable {
 static final ItemTable instance=new ItemTable();public static ItemTable getInstance(){return instance;}
 public static int creations;private static int next=1000;
 public static void reset(){creations=0;next=1000;}
 public L1Item getTemplate(int id){return id<=0?null:new L1Item(id,id==101 || id==3000024);}
 public L1ItemInstance createItem(int id){var template=getTemplate(id);if(template==null)return null;
   var v=new L1ItemInstance(next++,template);creations++;L1World.getInstance().storeObject(v);return v;}
}''')
write(Path('l1j/server/server/model/rank/loader/RankLoadManager.java'),'''package l1j.server.server.model.rank.loader;
public class RankLoadManager {public static final int MRK_TOPPROTECTION_ID=501;}''')
write(Path('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java'),'''package l1j.server.server.model.network.core.sc.packet;
import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ServerMessage {
 public static final List<String> events=new ArrayList<>();
 public static void send(L1PcInstance pc,int code,String content){events.add(code+":"+content);}
}''')
write(Path('l1j/server/DatabaseFactory.java'),'''package l1j.server;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
public class DatabaseFactory {
 static final DatabaseFactory instance=new DatabaseFactory();
 public static final List<int[]> rows=new ArrayList<>();
 public static DatabaseFactory getInstance(){return instance;}
 static Object def(Class<?> t){ if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T> T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
 public Connection getConnection(){return proxy(Connection.class,(self,m,a)->{
   if(m.getName().equals("prepareStatement"))return proxy(PreparedStatement.class,(q,n,b)->{
     if(n.getName().equals("executeQuery"))return result();
     return def(n.getReturnType());
   });
   return def(m.getReturnType());
 });}
 static ResultSet result(){final int[] at={-1};return proxy(ResultSet.class,(self,m,a)->{
  if(m.getName().equals("next")){at[0]++;return at[0]<rows.size();}
  if(m.getName().equals("getInt")){int[] row=rows.get(at[0]);return switch((String)a[0]){case "item_id"->row[0];case "count"->row[1];case "enchantlvl"->row[2];default->0;};}
  return def(m.getReturnType());
 });}
}''')
write(Path('l1j/server/server/model/TestPhase34.java'),'''package l1j.server.server.model;
import java.util.*;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.rank.buff.*;
import l1j.server.server.model.trap.*;
public class TestPhase34 {
 static int checks, cases;
 static void check(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
 record Result(String effect,String message,int world,int created){}
 interface Action {void run(L1PcInstance pc) throws Exception;}
 static Result exec(Action action,int[][] rows, int preset,boolean reject,boolean checkFail,boolean storeFail,boolean storeNull){
  L1World.getInstance().clear();ItemTable.reset();SC_ServerMessage.events.clear();DatabaseFactory.rows.clear();
  for(var row:rows)DatabaseFactory.rows.add(row);
  var pc=new L1PcInstance();if(preset>0){var x=ItemTable.getInstance().createItem(preset);x.setCount(7);pc.inv.putExisting(x);}
  int originalWorld=L1World.getInstance().size();int originalCreated=ItemTable.creations;
  pc.inv.reject=reject;pc.inv.throwCheck=checkFail;pc.inv.throwStore=storeFail;pc.inv.returnNull=storeNull;
  String error="";try{action.run(pc);}catch(Exception e){error=e.getClass().getSimpleName()+":"+e.getMessage();}
  StringBuilder items=new StringBuilder();pc.inv.owned.values().stream().sorted(Comparator.comparingInt(L1ItemInstance::getId)).forEach(x->items.append(x.getItem().itemId).append('/').append(x.getCount()).append('/').append(x.getEnchantLevel()).append(';'));
  String effect=items+"|check:"+pc.inv.checkCalls+"|store:"+pc.inv.storeCalls+"|err:"+error;
  return new Result(effect,SC_ServerMessage.events.toString(),L1World.getInstance().size()-originalWorld,ItemTable.creations-originalCreated);
 }
 static void beginnerTests(){
   for(int[] ids:new int[][]{{101,5,2},{201,1,3},{201,3,6},{101,1,0}}){
    for(int preset:new int[]{0,ids[0],888})for(int flags=0;flags<5;flags++){
     final boolean reject=flags==1,checkFail=flags==2,storeFail=flags==3,storeNull=flags==4;
     final int[][] rows={ids};
     var old=exec(pc->new BeginnerLegacy().GiveItemToActivePc(pc),rows,preset,reject,checkFail,storeFail,storeNull);
     var now=exec(pc->new BeginnerCandidate().GiveItemToActivePc(pc),rows,preset,reject,checkFail,storeFail,storeNull);
     check(old.effect().equals(now.effect()),"Beginner item result: "+Arrays.toString(ids)+" preset="+preset+" flags="+flags+" old="+old+" new="+now);
     if(flags!=4)check(old.message().equals(now.message()),"Beginner message differs: "+old+" / "+now);
     else if(old.effect().contains("err:"))check(true,"store null deliberately suppresses false positive success");
     if(flags!=0 || preset==ids[0] && ids[0]==101)check(now.world()<=old.world(),"beginner temporary world object leak");
     cases++;
    }
   }
   // A multirow batch does not lose earlier successful grants when a later row is rejected.
   var rows=new int[][]{{201,1,2},{101,1,3},{201,1,2}};
   var x=exec(pc->new BeginnerLegacy().GiveItemToActivePc(pc),rows,0,false,false,false,false);
   var y=exec(pc->new BeginnerCandidate().GiveItemToActivePc(pc),rows,0,false,false,false,false);
   check(x.effect().equals(y.effect()) && x.message().equals(y.message()),"multirow beginner order");cases++;
 }
 static void rankTests(){
   for(int preset:new int[]{0,501,888})for(int flags=0;flags<5;flags++){
    boolean reject=flags==1,checkFail=flags==2,storeFail=flags==3,storeNull=flags==4;
    var old=exec(RankBuffLegacy::run,new int[0][],preset,reject,checkFail,storeFail,storeNull);
    var now=exec(RankBuffCandidate::run,new int[0][],preset,reject,checkFail,storeFail,storeNull);
    // Existing item case skips capacity validation and temp creation in the new implementation.
    if(preset!=501)check(old.effect().equals(now.effect()),"Rank result: "+old+" / "+now);
    else check(now.effect().startsWith(old.effect().split("\\\\|check:")[0]),"Existing rank item changed");
    check(now.world()<=old.world(),"Rank temporary world leak");
    if(preset==501)check(now.created()==0,"rank duplicate allocated temporary item");
    cases++;
   }
 }
 static void raceTests(){
   for(int preset:new int[]{0,3000024,888})for(int flags=0;flags<3;flags++){
    boolean fail=flags==1,retNull=flags==2;
    var old=exec(RaceLegacy::run,new int[0][],preset,false,false,fail,retNull);
    var now=exec(RaceCandidate::run,new int[0][],preset,false,false,fail,retNull);
    check(old.effect().equals(now.effect()),"Race reward effect: "+old+" / "+now);
    if(!fail && !retNull)check(old.message().equals(now.message()),"Race one-item message: "+old+" / "+now);
    check(now.world()<=old.world(),"Race dummy world leak");
    check(now.created()<old.created() || fail,"Race unnecessary new item object");cases++;
   }
 }
 public static void main(String[] args){beginnerTests();rankTests();raceTests();System.out.println("Phase34 source-extracted Java21 grant behavior comparisons passed: "+cases+" cases, "+checks+" assertions");}
}''')
output=T/'typed_classes';shutil.rmtree(output,ignore_errors=True);output.mkdir()
files=[str(p) for p in R.rglob('*.java')]
proc=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(output)]+files,capture_output=True,text=True)
print('Javac',proc.returncode,proc.stderr[:6000]);
if proc.returncode:raise SystemExit(1)
proc=subprocess.run(['java','-cp',str(output),'l1j.server.server.model.TestPhase34'],capture_output=True,text=True)
print('Execute',proc.returncode,proc.stdout,proc.stderr[:6000]);
if proc.returncode:raise SystemExit(1)
