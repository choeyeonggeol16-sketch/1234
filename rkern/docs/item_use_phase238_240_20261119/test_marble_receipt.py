#!/usr/bin/env python3
"""Compile actual production handler with typed game doubles and JDBC proxies.
No live MySQL, CacheD, or full game runtime is claimed."""
from pathlib import Path
import re, tempfile, subprocess
root=Path(__file__).resolve().parents[2]
source=root/'src/l1j/server/server/model/item/collection/CollectionHandler.java'
code=re.sub(r'(?m)^(?:package |import l1j\.).*?;\s*\n','',source.read_text())
obj=(root/'src/l1j/server/server/model/item/collection/CollectionObject.java').read_text()
obj=re.sub(r'(?m)^(?:package |import l1j\.).*?;\s*\n','',obj).replace('public class CollectionObject','class CollectionObject').replace('SC_CTPacket.Data infoPck','Object infoPck')
code=code.replace('public final class CollectionHandler','final class CollectionHandler')
# Classes retain signatures used by production handler. Transaction double stages
# SQL then either publishes all state or discards the clone on an injected failure.
stubs=r'''
class CollectionLoadManager {static final int CTSYSTEM_STORE_ID=3000468, CTSYSTEM_LOAD_ID=3000469; static final String _invalidCharacter="invalid";}
class L1ItemInstance {int id,kind,count=1;boolean stack;L1ItemInstance(int i,int k){id=i;kind=k;}int getId(){return id;}int getItemId(){return kind;}boolean isStackable(){return stack;}int getCount(){return count;}}
class Inventory {java.util.Map<Integer,L1ItemInstance> items=new java.util.HashMap<>(); L1ItemInstance getItem(int id){return items.get(id);}}
class Account {Tam tam=new Tam();Tam getTamHandler(){return tam;}}
class Tam {void invalidateCache(){Test.cacheInvalid++;}}
class MapStub {boolean isSafetyZone(Object loc){return Test.safety;}}
class L1PcInstance {Inventory inv=new Inventory();Account a=new Account();int getId(){return 1;}String getAccountName(){return "owner";}Account getAccount(){return a;}Inventory getInventory(){return inv;}MapStub getMap(){return new MapStub();}Object getLocation(){return this;}CIOClient getNetConnection(){return new CIOClient();}}
class CIOClient {}
class L1World {static L1World getInstance(){return new L1World();}Object findObject(int id){return Test.live?this:null;}}
class CacheD {static CacheD getInstance(){return new CacheD();}static int accountOrderKey(String s){return 1;}boolean flushCharacter(String s,long t){return !Test.pending;}boolean flushItemsByCharacter(int id,long t){return !Test.pending;}}
class SC_SystemMessage {static void send(L1PcInstance p,String s){Test.message=s;}}
class SC_ItemName {static void send(L1PcInstance p,L1ItemInstance i){Test.nameNotice++;}}
class CS_CharSelect {static void restartProcess(L1PcInstance p){Test.restart++;}}
class CS_CommonClick {static void sendCharAmountAndPacks(CIOClient c){Test.amount++;}}
class CollectionSystemLoader {static CollectionSystemLoader getInstance(){return new CollectionSystemLoader();}static void removeCommitted(int id){Test.cacheRemove++;}static void publishCommitted(CollectionObject o){if(o==null)throw new AssertionError();Test.cacheSet++;}}
class User {static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,int key){return new L1InventoryTransaction(p);}}
interface Mutation {void run(Connection c) throws SQLException;}
class L1InventoryTransaction implements AutoCloseable {
 L1PcInstance pc;L1ItemInstance debit,reward;Mutation sql;Runnable failure,durable;boolean closed;
 L1InventoryTransaction(L1PcInstance p){pc=p;}
 boolean debit(L1ItemInstance i,int n){if(Test.debitFail)return false;debit=i;return true;}
 L1ItemInstance grant(int id,int count){if(Test.grantFail)return null; reward=new L1ItemInstance(90,id);reward.stack=Test.stackReward;return reward;}
 void requireTables(String... t){Test.tables=java.util.Set.of(t);}
 void sql(Mutation m){sql=m;}void onFailure(Runnable r){failure=r;}void onDurableCommit(Runnable r){durable=r;}public void close(){closed=true;}
}
class CDB {static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable done){Test.tx=tx;Test.done=done;}}
'''
test=r'''
class Test {
 static boolean safety,live,pending,debitFail,grantFail,stackReward,disconnected;static int cacheSet,cacheRemove,cacheInvalid,nameNotice,restart,amount,completion,checks,failAt,step;
 static String message;static java.util.Set<String> tables;static L1InventoryTransaction tx;static Runnable done;
 static class DB {String owner="owner";int slots=9,number=2,mapping=0,tam=2,online=0,stats=10;boolean account=true,caller=true,dup=false;DB copy(){DB b=new DB();b.owner=owner;b.slots=slots;b.number=number;b.mapping=mapping;b.tam=tam;b.online=online;b.stats=stats;b.account=account;b.caller=caller;b.dup=dup;return b;}}
 static void reset(){safety=true;live=pending=debitFail=grantFail=stackReward=disconnected=false;cacheSet=cacheRemove=cacheInvalid=nameNotice=restart=amount=completion=failAt=step=0;message=null;tx=null;done=null;tables=null;}
 static void ck(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object proxy(Class<?> type,java.lang.reflect.InvocationHandler h){return java.lang.reflect.Proxy.newProxyInstance(Test.class.getClassLoader(),new Class<?>[]{type},h);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static ResultSet rows(java.util.List<java.util.Map<Object,Object>> rows){int[] i={-1};return (ResultSet)proxy(ResultSet.class,(o,m,a)->switch(m.getName()){case "next"->++i[0]<rows.size();case "getInt"->((Number)rows.get(i[0]).getOrDefault(a[0],0)).intValue();case "getString"->(String)rows.get(i[0]).get(a[0]);default->zero(m.getReturnType());});}
 static java.util.Map<Object,Object> row(Object k,Object v){var r=new java.util.HashMap<Object,Object>();r.put(k,v);return r;}
 static Connection connection(DB b){return (Connection)proxy(Connection.class,(o,m,a)->{if(!m.getName().equals("prepareStatement"))return zero(m.getReturnType());String s=(String)a[0];var p=new java.util.HashMap<Integer,Object>();return proxy(PreparedStatement.class,(po,pm,pa)->{String n=pm.getName();if(n.startsWith("set")){p.put((Integer)pa[0],pa[1]);return null;}if(!n.startsWith("execute"))return zero(pm.getReturnType());if(++step==failAt)throw new SQLException("injected");
  if(n.equals("executeQuery")){
   if(s.startsWith("SELECT charslot"))return rows(b.account?java.util.List.of(row(1,b.slots)):java.util.List.of());
   if(s.startsWith("SELECT objid")){var r=new java.util.ArrayList<java.util.Map<Object,Object>>();for(int i=0;i<b.number;i++)r.add(row(1,i==0&&b.caller?1:i+2));return rows(r);}
   if(s.startsWith("SELECT char_name")){if(!b.owner.equals(p.get(1))||!p.get(2).equals(2))return rows(java.util.List.of());var r=row("char_name","target");for(String k:new String[]{"Str","Dex","Con","Wis","Intel","Cha","level","MaxHp","MaxMp"})r.put(k,b.stats);r.put("Class",0);r.put("OnlineStatus",b.online);return rows(java.util.List.of(r));}
   if(s.startsWith("SELECT marble_id"))return rows(b.mapping==0?java.util.List.of():java.util.List.of(row(1,b.mapping)));
   if(s.startsWith("SELECT char_id"))return rows(b.mapping==0||!p.get(1).equals(b.mapping)?java.util.List.of():b.dup?java.util.List.of(row(1,2),row(1,2)):java.util.List.of(row(1,2)));
  }else if(n.equals("executeUpdate")){
   if(s.startsWith("UPDATE characters")){if(!p.get(2).equals(2)||!b.owner.equals(p.get(3))||b.online!=0)return 0;b.owner=(String)p.get(1);return 1;}
   if(s.startsWith("DELETE FROM tam")){ck(p.get(1).equals(2));if(b.tam==0)return 0;b.tam--;return 1;}
   if(s.startsWith("INSERT INTO tb_mjct_mapping")){ck(p.size()==14&&p.get(2).equals(2)&&p.get(14).equals("target"));if(b.mapping!=0)return 0;b.mapping=(int)p.get(1);return 1;}
   if(s.startsWith("DELETE FROM tb_mjct_mapping")){if(!p.get(1).equals(b.mapping)||!p.get(2).equals(2))return 0;b.mapping=0;return 1;}
  }throw new SQLException("unexpected SQL: "+s);
 });});}
 static L1PcInstance pc(boolean box){L1PcInstance p=new L1PcInstance();p.inv.items.put(90,new L1ItemInstance(90,box?CollectionLoadManager.CTSYSTEM_LOAD_ID:CollectionLoadManager.CTSYSTEM_STORE_ID));return p;}
 static boolean start(L1PcInstance p,boolean box){return box?CollectionHandler.load(p,p.inv.getItem(90)):CollectionHandler.store(p,p.inv.getItem(90),2,()->completion++);}
 static DB finish(DB b,boolean commit){DB copy=b.copy();try{tx.sql.run(connection(copy));if(!commit)throw new SQLException("post-SQL item/commit failure");}catch(SQLException e){tx.failure.run();return b;}tx.durable.run();if(disconnected)return copy;tx.pc.inv.items.remove(tx.debit.id);if(tx.reward!=null)tx.pc.inv.items.put(tx.reward.id,tx.reward);done.run();return copy;}
 static void scenario(boolean box,int failure,boolean commit,int invalid){reset();DB b=new DB();if(box){b.owner="[MJCTSYSTEM]";b.mapping=90;}
  if(invalid==1)b.account=false;if(invalid==2)b.caller=false;if(invalid==3)b.owner="foreign";if(invalid==4)b.online=1;if(invalid==5)pending=true;if(invalid==6){if(box)b.dup=true;else b.mapping=80;}if(invalid==7){if(box)b.slots=2;else b.stats=0;}if(invalid==8){if(box)b.number=9;else b.stats=0;}
  L1PcInstance p=pc(box);ck(start(p,box));ck(tx!=null&&tx.closed&&completion==0&&restart==0&&p.inv.getItem(90)!=null);ck(tables.contains("tb_mjct_mapping")&&tables.contains("characters")&&tables.contains("accounts"));failAt=failure;DB r=finish(b,commit);boolean success=invalid==0&&commit&&failure==0;
  if(success){ck(r!=b);ck(r.owner.equals(box?"owner":"[MJCTSYSTEM]"));ck(r.mapping==(box?0:90));ck(r.tam==(box?2:1));ck(restart==1&&amount==1&&cacheInvalid==1);ck(cacheSet==(box?0:1)&&cacheRemove==(box?1:0)&&completion==(box?0:1));ck((p.inv.getItem(90)==null)==box);}
  else{ck(r==b);ck(restart==0&&cacheSet==0&&cacheRemove==0&&completion==0&&p.inv.getItem(90)!=null&&message!=null);}
 }
 public static void main(String[] args){for(boolean box:new boolean[]{false,true}){
  reset();DB stale=new DB();if(box){stale.owner="[MJCTSYSTEM]";stale.mapping=90;}L1PcInstance stalePc=pc(box);ck(start(stalePc,box));disconnected=true;DB committed=finish(stale,true);ck(committed!=stale);ck(cacheSet==(box?0:1)&&cacheRemove==(box?1:0)&&cacheInvalid==1);ck(restart==0&&amount==0&&completion==0&&nameNotice==0);ck(committed.mapping==(box?0:90));scenario(box,0,true,0);scenario(box,0,false,0);for(int invalid=1;invalid<=8;invalid++)scenario(box,0,true,invalid);for(int failure=1;failure<=(box?6:7);failure++)scenario(box,failure,true,0);
  for(int invalid=0;invalid<5;invalid++){reset();L1PcInstance p=pc(box);switch(invalid){case 0->safety=false;case 1->debitFail=true;case 2->p.inv.getItem(90).kind=1;case 3->{if(box)p.inv.getItem(90).stack=true;else grantFail=true;}case 4->{if(box)p.inv.getItem(90).count=2;else stackReward=true;}}ck(!start(p,box));ck(tx==null&&p.inv.getItem(90)!=null&&restart==0);}}
 System.out.println("MARBLE_RECEIPT_PASS checks="+checks+" (actual handler, JDBC/receipt doubles)");}
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Test.java';p.write_text(code+obj+stubs+test)
 subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True)
 subprocess.run(['java','-cp',d,'Test'],check=True)
loader=(root/'src/l1j/server/server/model/item/collection/loader/CollectionSystemLoader.java').read_text()
assert 'delete from tb_mjct_mapping' not in loader.lower()
assert 'size\t= list.size();' in loader or re.search(r'int\s+size\s*=\s*list.size\(\)',loader)
assert 'spQ = null;' in loader
print('MARBLE_MAPPING_CACHE_PASS no shutdown rewrite; list iteration and empty skill queue repaired')
