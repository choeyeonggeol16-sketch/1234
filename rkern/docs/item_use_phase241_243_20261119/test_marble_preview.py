#!/usr/bin/env python3
"""Compile production loader/object and exercise real scheduling logic with JDBC/executor doubles."""
from pathlib import Path
import re, tempfile, subprocess
root=Path(__file__).resolve().parents[2]
src=root/'src/l1j/server/MJCTSystem'
def source(p):
    return re.sub(r'(?m)^(?:package |import l1j\.).*?;\s*\n','',p.read_text()).replace('public class ','class ')
# Put java imports first for the combined test translation unit.
code='import java.sql.*; import java.util.*; import java.text.*; import java.lang.reflect.*;\n'
for p in [src/'CollectionObject.java',src/'CollectionCharInfo.java',src/'CollectionItem.java',src/'CollectionSpell.java',src/'Loader/CollectionSystemLoader.java']:
    code+=re.sub(r'(?m)^import .*?;\s*\n','',source(p))+'\n'
code+=r'''
class L1Item {int getType2(){return 0;}String getNameId(){return "item";}}
class ItemTable {static ItemTable getInstance(){return new ItemTable();}L1Item getTemplate(int i){return new L1Item();}}
class CollectionSpellLoader {static CollectionSpellLoader getInstance(){return new CollectionSpellLoader();}CollectionSpell get(int i){return new CollectionSpell();}}
class SC_CTPacket {static class Data {} static Data getCharacterInfo(CollectionCharInfo i){return new Data();}}
class SC_WAREHOUSE_ITEM_LIST_NOTI {static SC_WAREHOUSE_ITEM_LIST_NOTI create_ct_item_info(ArrayDeque<CollectionItem> q){return new SC_WAREHOUSE_ITEM_LIST_NOTI();}static SC_WAREHOUSE_ITEM_LIST_NOTI create_ct_sp_info(ArrayDeque<CollectionSpell> q){return new SC_WAREHOUSE_ITEM_LIST_NOTI();}byte[] encode(){return new byte[]{1};}}
class ThreadPool {static final ThreadPool INSTANCE=new ThreadPool();static ThreadPool getInstance(){return INSTANCE;}final ArrayDeque<Runnable> pending=new ArrayDeque<>();boolean reject; synchronized void execute(Runnable r){if(reject)throw new java.util.concurrent.RejectedExecutionException();pending.add(r);} synchronized Runnable take(){return pending.remove();}}
class SQLUtil {static void close(AutoCloseable... xs){for(var x:xs)if(x!=null)try{x.close();}catch(Exception e){throw new RuntimeException(e);}}}
class DatabaseFactory {static final DatabaseFactory INSTANCE=new DatabaseFactory();static DatabaseFactory getInstance(){return INSTANCE;}boolean failConnection,failItems,missingCharacter;int calls,items,skills,info;Connection getConnection()throws SQLException{calls++;if(failConnection)throw new SQLException("injected connection");return (Connection)Test.proxy(Connection.class,(o,m,a)->{if(m.getName().equals("prepareStatement")){String sql=(String)a[0];return Test.proxy(PreparedStatement.class,(q,n,b)->{if(n.getName().equals("executeQuery")){if(sql.contains("character_items")){items++;if(failItems)throw new SQLException("injected items");}if(sql.contains("character_skills"))skills++;if(sql.contains("from characters"))info++;boolean one=sql.contains("from characters")&&!missingCharacter;int[] cursor={0};return Test.proxy(ResultSet.class,(r,f,c)->switch(f.getName()){case "next"->one&&cursor[0]++==0;case "getString"->"hero";case "getInt","getRow"->0;case "getLong"->0L;default->Test.zero(f.getReturnType());});}return Test.zero(n.getReturnType());});}return Test.zero(m.getReturnType());});}}
class Test {
 static int checks;static void ck(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object proxy(Class<?> t,InvocationHandler h){return Proxy.newProxyInstance(Test.class.getClassLoader(),new Class<?>[]{t},h);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static CollectionObject obj(int id){CollectionObject o=new CollectionObject();o.marbleId=id;o.charId=id+10;o.name="hero"+id;return o;}
 static void allowRetry(CollectionObject o)throws Exception{Field f=CollectionObject.class.getDeclaredField("retryAfterNanos");f.setAccessible(true);f.setLong(o,System.nanoTime()-1);}
 static void mainThread()throws Exception{
  var pool=ThreadPool.INSTANCE;var db=DatabaseFactory.INSTANCE; var loader=CollectionSystemLoader.getInstance();
  var a=obj(1);pool.reject=true;CollectionSystemLoader.publishCommitted(a);ck(loader.get(1)==a);ck(pool.pending.isEmpty());ck(!a.previewComplete());pool.reject=false;loader.get(a.name);ck(pool.pending.isEmpty());allowRetry(a);loader.get(a.name);ck(pool.pending.size()==1);
  for(int i=0;i<100;i++)ck(loader.get(a.name)==a);ck(pool.pending.size()==1);
  db.failItems=true;pool.take().run();ck(a.invPck==null&&a.spPck!=null&&a.infoPck!=null);int sk=db.skills,in=db.info;allowRetry(a);db.failItems=false;loader.get(a.name);pool.take().run();ck(a.previewComplete());ck(db.skills==sk&&db.info==in);loader.get(a.name);ck(pool.pending.isEmpty());
  var b=obj(2);CollectionSystemLoader.publishCommitted(b);db.failConnection=true;pool.take().run();ck(!b.previewComplete());allowRetry(b);db.failConnection=false;loader.get(b.name);pool.take().run();ck(b.previewComplete());
  var c=obj(3);CollectionSystemLoader.publishCommitted(c);int calls=db.calls;CollectionSystemLoader.removeCommitted(3);pool.take().run();ck(db.calls==calls&&loader.get(3)==null&&!c.previewComplete());
  var old=obj(4);CollectionSystemLoader.publishCommitted(old);var replacement=obj(4);CollectionSystemLoader.publishCommitted(replacement);calls=db.calls;pool.take().run();ck(db.calls==calls);pool.take().run();ck(loader.get(4)==replacement&&replacement.previewComplete());
  var d=obj(5);CollectionSystemLoader.publishCommitted(d);db.missingCharacter=true;pool.take().run();ck(d.infoPck==null);db.missingCharacter=false;allowRetry(d);loader.get(d.name);pool.take().run();ck(d.previewComplete());
  var e=obj(6);CollectionSystemLoader.publishCommitted(e);calls=db.calls;CollectionSystemLoader.release();pool.take().run();ck(db.calls==calls&&!e.previewComplete());var next=CollectionSystemLoader.getInstance();ck(next!=loader&&next.get(6)==null);
  var f=obj(7);CollectionSystemLoader.publishCommitted(f);Runnable task=pool.take();java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);Thread worker=new Thread(()->{try{start.await();task.run();}catch(Exception ex){throw new RuntimeException(ex);}});worker.start();for(int i=0;i<100;i++)ck(next.get(f.name)==f);start.countDown();long until=System.nanoTime()+5_000_000_000L;while(!f.previewComplete()&&System.nanoTime()-until<0)Thread.onSpinWait();ck(f.previewComplete());worker.join();ck(pool.pending.isEmpty());
  for(String field:new String[]{"invPck","spPck","infoPck"})ck(Modifier.isVolatile(CollectionObject.class.getField(field).getModifiers()));
 }
 public static void main(String[] args)throws Exception{mainThread();System.out.println("MARBLE_PREVIEW_PASS checks="+checks);}
}
'''
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'Test.java';p.write_text(code,encoding='utf-8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True)
    subprocess.run(['java','-cp',d,'Test'],check=True)
# Confirm the actual CacheD callback is downstream of durable persistence and
# upstream of the existing owner-replay validation; SQL never invokes it.
tx=(root/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text()
cache=(root/'src/l1j/server/server/storage/CacheD.java').read_text()
section=tx[tx.index('move.commit(()->{'):tx.index('submitted=true;',tx.index('move.commit(()->{'))]
assert section.index('durableCommitAction = null')<section.index('durable.run()')<section.index('post(()->{')<section.index('validateLive()')
section=cache[cache.index('if (economy) CTransaction.persistEconomy'):cache.index('reservedJob.prepared = false;')]
assert section.index('CTransaction.persistEconomy')<section.index('markCommitConfirmed();')<section.index('action.run();')
print('DURABLE_CALLBACK_ORDER_PASS (source contract)')
