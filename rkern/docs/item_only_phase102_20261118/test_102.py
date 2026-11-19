from pathlib import Path
import zipfile,tempfile,subprocess,re,sys,shutil
PROJECT_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ZIP = None
DOC='docs/item_only_phase73_100_20261118/run_normal_ab.py'
py = (PROJECT_ROOT / DOC).read_text(encoding='utf8')
ns={'__file__':str(Path(__file__).resolve())}
prefix=py.split("MAIN=r\'\'\'")[0]
ROOT = Path(__file__).resolve().parent / 'fixture_work'
for kind in ('baseline', 'updated'):
    target=ROOT/kind/'src/l1j/server/server/model/item/Card'
    target.mkdir(exist_ok=True,parents=True)
    origin=Path(__file__).resolve().parent/'baseline' if kind=='baseline' else PROJECT_ROOT/'src/l1j/server/server/model/item/Card'
    for n in ('L1CardAlchemy1.java', 'L1CardAlchemy2.java', 'L1CardAlchemy3.java', 'CardAlchemyReceipt.java'):
        shutil.copyfile(origin/n, target/n)
    if kind=='updated':
        shutil.copyfile(origin/'CardAlchemyReceipt.java',target/'CardAlchemyReceipt.java')
base=ROOT/'baseline/src/l1j/server/server/model/item/Card' 
all_src='\n'.join((base/f).read_text() for f in ['L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java','CardAlchemyReceipt.java'])
ns.update(GETTERS=sorted(set(re.findall(r'\bacc\.(get\w+)\(',all_src))),
          SETTERS=sorted(set(re.findall(r'\bacc\.(set\w+)\(',all_src)) | {'setInfo_card_a','setInfo_card_b','setInfo_card_c','setInfo_card_d'}),
          UPDATES=sorted(set(re.findall(r'\bacc\.(update\w+)\(',all_src))))
exec(prefix[prefix.index('account='):],ns)
STUBS=ns['STUBS']
STUBS['l1j/server/server/Account.java']=STUBS['l1j/server/server/Account.java'].replace('public class Account {','''public class Account {
 public String getName() { return "audit-account"; }
 public int dbGet(String column) {
   return get(column.startsWith("info_") ? "getInfo_"+column.substring(5) : "get"+column);
 }
''')
STUBS['l1j/server/server/model/Instance/L1PcInstance.java']=STUBS['l1j/server/server/model/Instance/L1PcInstance.java'].replace('private final Account account;', 'private final Account account;').replace(' public boolean checkItem(int id,int count){return seed%11!=4;}',' public boolean checkItem(int id,int count){return seed%11!=4;}')
STUBS.update({
'l1j/server/server/storage/CacheD.java':'''package l1j.server.server.storage;
public class CacheD {public static int accountOrderKey(String name) {return name.hashCode();}}''',
'l1j/server/server/model/network/core/cs/packet/User.java':'''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int key){return L1InventoryTransaction.begin(pc,key);}}''',
'l1j/server/server/model/network/core/cs/packet/CDB.java':'''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
public class CDB {public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable callback){tx.commitAfterInventoryRelease(callback);}}''',
'l1j/server/server/model/L1InventoryTransaction.java':'''package l1j.server.server.model;
import java.util.*;import java.sql.*;import java.lang.reflect.*;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1InventoryTransaction implements AutoCloseable {
 @FunctionalInterface public interface Mutation {void apply(Connection con) throws SQLException;}
 public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER;
 public static int COMMITS,ROLLBACKS,SQL_STAGES;
 public static String LAST_SQL;
 public static L1InventoryTransaction PENDING;
 private final L1PcInstance pc;
 private int cost,reward;private Mutation mutation;private Runnable callback,failure;private boolean submitted;
 private L1InventoryTransaction(L1PcInstance pc){this.pc=pc;}
 public static L1InventoryTransaction begin(L1PcInstance pc,int key){return new L1InventoryTransaction(pc);}
 public boolean debit(int id,int count){if (FAIL_DEBIT) return false; if (id!=40308 || count<=0)throw new Error("debit invalid");cost=count;return true;}
 public Object grant(int id,int count){if(FAIL_GRANT)return null; if(count!=1||id<=0)throw new Error("grant invalid");reward=id;return new Object();}
 public void requireTables(String... names){if(names.length!=1||!names[0].equals("accounts"))throw new Error("must require accounts");}
 public void sql(Mutation m){SQL_STAGES++;mutation=m;}
 public void onFailure(Runnable r){failure=r;}
 public void commitAfterInventoryRelease(Runnable cb){submitted=true;callback=cb;if(DEFER)PENDING=this;else complete();}
 public static void flush(){if(PENDING==null)throw new Error("no pending tx");L1InventoryTransaction t=PENDING;PENDING=null;t.complete();}
 private void complete(){
   try{
     mutation.apply(connection());
     Account a=pc.getAccount();a.events.append("consume:40308:").append(cost).append(';');
     a.events.append("store:").append(reward).append(":1;");
     COMMITS++;callback.run();
   } catch(SQLException ex){ROLLBACKS++;if(failure!=null)failure.run();}
 }
 public void close(){}
 private Connection connection(){
  InvocationHandler h=(o,m,a)->{if(m.getName().equals("prepareStatement")){
   String sql=(String)a[0];LAST_SQL=sql;
   if(!sql.startsWith("UPDATE accounts SET ")||!sql.contains(" WHERE login=?"))throw new Error("unexpected SQL: "+sql);
   java.util.HashMap<Integer,Object> bind=new java.util.HashMap<>();
   InvocationHandler ps=(po,pm,pa)->{
      switch(pm.getName()){
       case "setString":case "setInt":bind.put((Integer)pa[0],pa[1]);return null;
       case "close":return null;
       case "executeUpdate":{
        if(FAIL_SQL)throw new SQLException("injected db exception");
        if(DB_MISMATCH)return 0;
        if(!"audit-account".equals(bind.get(1)))throw new Error("bad login bind");
        String where=sql.substring(sql.indexOf(" WHERE ")+7);
        String[] predicates=where.split(" AND ");
        // The fourth selection slot is now cleared for BOTH two- and four-card
        // synthesis; predicate count follows debited cards, not cleared slots.
        int debits=sql.split("-1,",-1).length-1;
        if(predicates.length!=debits+1)throw new Error("bad predicate count");
        for(int i=1;i<predicates.length;i++){
          String name=predicates[i].substring(0,predicates[i].indexOf('='));
          if(pc.getAccount().dbGet(name)!=(Integer)bind.get(i+1))return 0;
          if((Integer)bind.get(i+1)<2)throw new Error("card count below 2");
          if(!sql.contains(name+"="+name+"-1"))throw new Error("missing decrement: "+name);
        }
        return 1;
       }
       default:throw new UnsupportedOperationException("ps "+pm.getName());
      }
    };
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},ps);
  }else if(m.getName().equals("close"))return null;
  throw new UnsupportedOperationException("con "+m.getName());};
  return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},h);
 }
}''',
})
# ROOT: fixture_work built adjacent to this verifier
FILES=['L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java']
STUB_TEST='''import java.lang.reflect.*;import java.util.*;
import l1j.server.server.Account;import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.*;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.Trace;
public final class Test101 {
 static Class<?>[] classes={L1CardAlchemy1.class,L1CardAlchemy2.class,L1CardAlchemy3.class};
 static String[] methods={"LegendAlchemy","HeroAlchemy1","MythAlchemy"};
 static int total;
 static void check(boolean ok,String where){if(!ok)throw new AssertionError(where);total++;}
 static boolean invoke(int k,L1PcInstance p)throws Exception{
  try {return (Boolean)classes[k].getMethod(methods[k],L1PcInstance.class).invoke(null,p);}
  catch(InvocationTargetException x){throw new RuntimeException(x.getCause());}}
 static int[] ids={101,1,201};
 static void reset(){L1InventoryTransaction.FAIL_DEBIT=false;L1InventoryTransaction.FAIL_GRANT=false;
  L1InventoryTransaction.FAIL_SQL=false;L1InventoryTransaction.DB_MISMATCH=false;L1InventoryTransaction.DEFER=false;
  L1InventoryTransaction.PENDING=null;}
 static int cardBefore(Account a,int k){return k==0?a.getInfo_ds_poly():k==1?a.getInfo_heaven_poly():a.getInfo_class_poly();}
 public static void main(String[] args)throws Exception{
  for(int k=0;k<3;k++){
   for(int i=0;i<1200;i++){
    reset();Account a=new Account(22+i*11,k);L1PcInstance p=new L1PcInstance(a,k,22+i*11);
    boolean accepted=invoke(k,p);
    // Only check positive-path seeds (nonzero selection, safe map, available cash).
    if(accepted){
      check(a.getInfo_card_a()==0 && a.getInfo_card_b()==0,"selector clear "+k);
      check(a.getInfo_card_c()==0,"selector c clear");
      check(a.getInfo_card_d()==0,"selector d clear");
      check(L1InventoryTransaction.LAST_SQL.contains("UPDATE accounts SET"),"sql staged");
      check(L1InventoryTransaction.LAST_SQL.contains("info_card_a=0"),"persist selection");
      check(a.events.toString().contains("store:"),"reward granted");
      check(!a.events.toString().contains("update"),"direct sql account save forbidden");
    }
   }
  }
  for(int k=0;k<3;k++)for(int scenario=0;scenario<6;scenario++){
   reset();Account a=new Account(22,k);L1PcInstance p=new L1PcInstance(a,k,22);
   int before=cardBefore(a,k);int initialCommits=L1InventoryTransaction.COMMITS;
   if(scenario==0)L1InventoryTransaction.FAIL_DEBIT=true;
   if(scenario==1)L1InventoryTransaction.FAIL_GRANT=true;
   if(scenario==2)L1InventoryTransaction.FAIL_SQL=true;
   if(scenario==3)L1InventoryTransaction.DB_MISMATCH=true;
   if(scenario==4)L1InventoryTransaction.DEFER=true;
   boolean accepted=invoke(k,p);
   if(scenario==4){
     check(accepted,"deferred submission accepted");check(cardBefore(a,k)==before,"deferred memory not changed");
     check(a.getInfo_card_a()==ids[k],"deferred selection unchanged");
     L1InventoryTransaction.flush();
     check(cardBefore(a,k)==before-1,"deferred memory applied once");
     check(a.getInfo_card_a()==0,"deferred selector cleared");
   }else if(scenario==5){
     check(accepted,"normal success accepted");check(cardBefore(a,k)==before-1,"normal count applied once");
   }else{
     check(!accepted || scenario>=2,"failed preparation did not accept");
     check(cardBefore(a,k)==before,"failed tx must not decrement card");
     check(a.getInfo_card_a()==ids[k],"failed tx must not clear selected");
     check(L1InventoryTransaction.COMMITS==initialCommits,"failed tx cannot commit");
     check(!a.events.toString().contains("store:"),"failed tx cannot grant");
   }
  }
  System.out.println("PASS: actual production Java 21 type compilation and "+total+" checks");
 }
}'''

def write(d,src):
 p=d/src;p.parent.mkdir(parents=True,exist_ok=True);return p

def run_one(kind):
 dest=ROOT/(kind+'_typed');src_root=dest/'src'
 for fn,content in STUBS.items():write(src_root,fn).write_text(content,encoding='utf8')
 write(src_root,'Test101.java').write_text(STUB_TEST,encoding='utf8')
 src=ROOT/('updated/src' if kind=='new' else 'baseline/src')/'l1j/server/server/model/item/Card'
 java=[str(p) for p in src_root.rglob('*.java')]+[str(src/name) for name in FILES]
 java.append(str(src/'CardAlchemyReceipt.java'))
 classes=dest/'classes';classes.mkdir(exist_ok=True,parents=True)
 r=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes),*java],capture_output=True,text=True,timeout=90)
 if r.returncode:print(r.stderr[:5000]);raise SystemExit('java compile failed')
 if kind=='new':
  r=subprocess.run(['java','-cp',str(classes),'Test101'],capture_output=True,text=True,timeout=90)
  if r.returncode:print(r.stdout,r.stderr[:5000]);raise SystemExit('101 typed tests failed')
  print(r.stdout.strip())

if __name__=='__main__':run_one('new')
