import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import com.zaxxer.hikari.HikariDataSource;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.*;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
public class HpMpPersistenceTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Connection noClose(Connection c){return (Connection)Proxy.newProxyInstance(HpMpPersistenceTest.class.getClassLoader(),new Class[]{Connection.class},(o,m,a)->{
  if(m.getName().equals("close"))return null;
  // H2 lacks the existing MySQL IF() syntax; adapt only that unrelated
  // logout expression. HP/MP/level/history bindings and transaction are real.
  if(m.getName().equals("prepareStatement"))
   a[0]=((String)a[0]).replace("IF(? = 0, NOW(), lastLogoutTime)",
     "CASE WHEN ? = 0 THEN NOW() ELSE lastLogoutTime END");
  try{return m.invoke(c,a);}catch(InvocationTargetException e){throw e.getCause();}
 });}
 static class Source extends HikariDataSource {final Connection c;Source(Connection c){this.c=c;}@Override public Connection getConnection(){return noClose(c);}}
 static String row(Connection c,String col)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT "+col+" FROM characters WHERE objid=201")){check(r.next(),"row missing");return r.getString(1);}}
 static void creationArity(HpMpTest.Pc p)throws Exception{
  final Map<Integer,Object> bound=new HashMap<>();final String[] sql={null};
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(HpMpPersistenceTest.class.getClassLoader(),new Class[]{PreparedStatement.class},(o,m,a)->{
   if(m.getName().startsWith("set")){bound.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("execute"))return false;
   if(m.getName().equals("close"))return null;
   throw new UnsupportedOperationException(m.getName());
  });
  Connection con=(Connection)Proxy.newProxyInstance(HpMpPersistenceTest.class.getClassLoader(),new Class[]{Connection.class},(o,m,a)->{
   if(m.getName().equals("prepareStatement")){sql[0]=(String)a[0];return ps;}
   throw new UnsupportedOperationException(m.getName());
  });
  new MySqlCharacterStorage().createCharacter(con,p);
  int n=(int)sql[0].chars().filter(v->v=='?').count();check(bound.size()==n,"create SQL/binding arity");
  for(int i=1;i<=n;i++)check(bound.containsKey(i),"create bind gap "+i);
  check(Objects.equals(bound.get(n),p.encodeHpMpGrowth()),"creation omitted growth history");
  System.out.println("PASS actual createCharacter JDBC binding: "+n+" placeholders, final history value included");
 }
 static void atomicSnapshots()throws Exception {
  HpMpTest.Pc p=HpMpTest.pc(20,20,1);HpMpTest.move(p,2);int hp2=p.getBaseMaxHp();HpMpTest.move(p,1);
  java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Thread writer=new Thread(()->{try{for(int i=0;i<100;i++){HpMpTest.move(p,2);HpMpTest.move(p,1);}}catch(Throwable t){failure.set(t);}});
  Field v=CharacterSnapshot.class.getDeclaredField("values");v.setAccessible(true);writer.start();
  for(int i=0;i<200;i++){int[] data=(int[])v.get(new CharacterSnapshot(p));check(data[3]==(data[0]==1?1000:hp2),"snapshot split level/growth HP");}
  writer.join(3000);check(!writer.isAlive()&&failure.get()==null,"snapshot/growth race");
  System.out.println("PASS concurrent level transitions and 200 actual snapshot captures preserve level/HP consistency with existing user lock");
 }
 static class SnapshotPc extends HpMpTest.Pc {
  boolean pause;final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  @Override public void onChangeExp(){try{HpMpTest.move(this,l1j.server.server.datatables.ExpTable.getLevelByExp(getExp()));}catch(Exception e){throw new AssertionError(e);}}
  @Override public String getTitle(){if(pause){entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}}return super.getTitle();}
 }
 static void expLockOrder()throws Exception {
  SnapshotPc p=new SnapshotPc();p.getAbility().setBaseCon(20);p.setLevel(1);p.addBaseMaxHp((short)1000);p.pause=true;
  java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
  Thread save=new Thread(()->{try{new CharacterSnapshot(p);}catch(Throwable t){failure.set(t);}});
  Thread exp=new Thread(()->{try{p.addExp(l1j.server.server.datatables.ExpTable.getExpByLevel(2));}catch(Throwable t){failure.set(t);}});
  save.start();check(p.entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"snapshot lock not held");exp.start();Thread.sleep(20);p.release.countDown();
  save.join(3000);exp.join(3000);if(failure.get()!=null)throw new AssertionError("EXP/snapshot fixture failure",failure.get());check(!save.isAlive()&&!exp.isAlive(),"EXP/snapshot lock-order deadlock");
  System.out.println("PASS actual addExp and snapshot contention: existing user lock has consistent order; no PC-monitor/user-lock deadlock");
 }
 public static void main(String[] args)throws Exception{
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  Class.forName("org.h2.Driver");String url="jdbc:h2:mem:hpmp;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=LEVEL,TYPE,STATUS,CLASS";
  Field store=MySqlCharacterStorage.class.getDeclaredField("CHARACTER_STORE_SQL");store.setAccessible(true);String sql=(String)store.get(null);
  Set<String> cols=new LinkedHashSet<>();Matcher matcher=Pattern.compile("(?:SET|,)\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*=").matcher(sql);while(matcher.find())if(!matcher.group(1).equals("hp_mp_growth"))cols.add(matcher.group(1));
  try(Connection c=DriverManager.getConnection(url)){
   StringBuilder ddl=new StringBuilder("CREATE TABLE characters(objid INT PRIMARY KEY");for(String name:cols)ddl.append(",").append(name).append(" VARCHAR(8192)");ddl.append(")");
   c.createStatement().execute(ddl.toString());
   String migration=Files.readString(Path.of(args[0])).replaceAll("(?m)^--.*$","").trim();c.createStatement().execute(migration);
   c.createStatement().execute("INSERT INTO characters(objid,level,MaxHp,MaxMp) VALUES(201,1,'1000','1000')");
   HpMpTest.Pc p=HpMpTest.pc(20,20,1);p.setId(201);HpMpTest.move(p,2);CharacterSnapshot snap=new CharacterSnapshot(p);
   Field pool=DatabaseFactory.class.getDeclaredField("DATABASE_POOL");pool.setAccessible(true);Object before=pool.get(null);pool.set(null,new Source(c));
   try {
    c.setAutoCommit(false);snap.write();check(Integer.parseInt(row(c,"MaxHp"))==p.getBaseMaxHp(),"stored max HP differs");check(row(c,"hp_mp_growth").equals(p.encodeHpMpGrowth()),"ledger missing from HP SQL");
    c.rollback();check(row(c,"MaxHp").equals("1000")&&row(c,"hp_mp_growth")==null,"rollback split HP/ledger");
    snap.write();c.commit();
    try(Connection reopened=DriverManager.getConnection(url)){
     HpMpTest.Pc restored=HpMpTest.pc(20,20,2);restored.addBaseMaxHp((short)(Integer.parseInt(row(reopened,"MaxHp"))-1000));restored.addBaseMaxMp(Integer.parseInt(row(reopened,"MaxMp"))-1000);restored.restoreHpMpGrowth(row(reopened,"hp_mp_growth"));
     HpMpTest.move(restored,1);check(restored.getBaseMaxHp()==1000&&restored.getBaseMaxMp()==1000,"reopened DB growth not reversible");
    }
    CharacterSnapshot old=new CharacterSnapshot(p);HpMpGrowthHistory stage=new HpMpGrowthHistory(1);stage.record(2,1,1);p.completeStatResetGrowth(stage);
    check(!old.sameData(new CharacterSnapshot(p)),"CacheD equality discarded a history-only change");
    creationArity(p);
    System.out.println("PASS H2 MySQL-mode migration, actual CharacterSnapshot SQL, atomic rollback/commit, reopened connection and exact delevel, history-aware snapshot equality");
   }finally{pool.set(null,before);}
  }
  atomicSnapshots();expLockOrder();
  System.out.println("Production JDBC snapshot binding/SQL executed against local H2 fixture; not production MySQL/CacheD daemon/Windows integration.");
 }
}
