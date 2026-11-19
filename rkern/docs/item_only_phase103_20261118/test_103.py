from pathlib import Path
import re, subprocess, os, hashlib, json
W=Path(__file__).resolve().parents[2]
old=(W/'docs/item_only_phase103_20261118/baseline/L1CardAdd.java').read_text()
new=(W/'src/l1j/server/server/model/item/Card/L1CardAdd.java').read_text()
rx=re.compile(r'(LEGEND1_MAP|LEGEND2_MAP|HERO1_MAP|HERO2_MAP|MYTH_MAP)\.put\((\d+),\s*acc\s*->\s*\{\s*acc\.set(\w+)\(acc\.get(\w+)\(\)\s*\+\s*1\);\s*return acc\.get(\w+)\(\);\s*\}\);')
groups=['LEGEND1_MAP','LEGEND2_MAP','HERO1_MAP','HERO2_MAP','MYTH_MAP']
entries=[(groups.index(m.group(1))+1,int(m.group(2)),m.group(3)) for m in rx.finditer(old)]
assert len(entries)==70
assert all(f'case {iid} -> "{f[0].lower()+f[1:]}";' in new and f'case {iid} -> acc.get{f}();' in new and f'case {iid} -> acc.set{f}(acc.get{f}() + 1);' in new for _,iid,f in entries)
trees=[(875640331,'iceq',1),(875640332,'fireq',1),(875640333,'final',1),(875640334,'ices',1),(875640335,'rain',1),(875640336,'cri',2),(875640337,'crim1',2),(875640338,'crim2',2),(875640339,'ecil1',3),(875640340,'ecil2',3),(875640341,'ecil3',3),(875640446,'atun1',4),(875640447,'atun2',4),(875640448,'atun3',4)]
for id,f,t in trees:
 assert f'case {id} -> "tree_{f}";' in new and f'case {id} -> acc.getTree_{f}();' in new and f'case {id} -> acc.setTree_{f}(1);' in new
# account field getter/setter signatures are generated from 102 actual Account.java
stubs=W/'docs/item_only_phase103_20261118/fixture_work'; stubs.mkdir(exist_ok=True)
def put(path, body):
 f=stubs/'src'/path
 f.parent.mkdir(parents=True,exist_ok=True); f.write_text(body); return f
fields=sorted(set([f for _,_,f in entries]+['Info_col_l','Info_col_h','Info_col_m','Info_kir_poly','Info_col_y']+['Tree_'+f for _,f,t in trees]))
put('l1j/server/server/Account.java', 'package l1j.server.server;\npublic class Account {\n'+''.join(f'public int {f}; public int get{f}(){{return {f};}} public int set{f}(int i){{return {f}=i;}}\n' for f in fields)+'public String getName(){return "unitAccount";}\n}\n')
put('l1j/server/server/model/Instance/L1ItemInstance.java', '''package l1j.server.server.model.Instance;
public class L1ItemInstance {
 public final int id; public int count=1;
 public L1ItemInstance(int n){id=n;} public int getItemId(){return id;}
 public Item getItem(){return new Item(id);} public static class Item { int id; public Item(int n){id=n;}
  public String getNameId(){return "Card-"+id;} public int getItemId(){return id;} }
}
''')
put('l1j/server/server/model/Instance/L1PcInstance.java', '''package l1j.server.server.model.Instance;
import l1j.server.server.Account;import java.util.ArrayList;
public class L1PcInstance {
 public Account acc=new Account(); public L1ItemInstance selected;
 public ArrayList<String> messages=new ArrayList<>(); public int bonuses;
 public Account getAccount(){return acc;}public int getId(){return 1234;}
 public L1ItemInstance getCollecItem(){return selected;}
 public void setCollecItem(L1ItemInstance i){selected=i;}
}
''')
put('l1j/server/server/storage/CacheD.java','package l1j.server.server.storage; public class CacheD {public static int accountOrderKey(String s){return s.hashCode();}}\n')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java', '''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage { public static void send(L1PcInstance p,String s){p.messages.add(s);} }
''')
put('l1j/server/server/model/item/Card/L1CardCollection.java','''package l1j.server.server.model.item.Card;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1CardCollection { public static void legendjoin(L1PcInstance p){p.bonuses=1;}
 public static void herojoin(L1PcInstance p){p.bonuses=2;}
 public static void mythjoin(L1PcInstance p){p.bonuses=3;} public static void solejoin(L1PcInstance p){p.bonuses=4;} }
''')
put('l1j/server/server/model/item/Card/L1SkillTree.java','''package l1j.server.server.model.item.Card;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1SkillTree { public static void Info(L1PcInstance p){p.bonuses=11;}
 public static void Info2(L1PcInstance p){p.bonuses=12;}
 public static void Info3(L1PcInstance p){p.bonuses=13;}
 public static void Info5(L1PcInstance p){p.bonuses=14;} }
''')
put('l1j/server/server/model/L1InventoryTransaction.java', '''package l1j.server.server.model;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
import l1j.server.server.model.Instance.*;
public class L1InventoryTransaction implements AutoCloseable {
 @FunctionalInterface public interface Mutation {void apply(Connection c) throws SQLException;}
 public L1PcInstance pc;public L1ItemInstance item; public Mutation action;
 public Runnable fail;public boolean pending;public Runnable after;
 public String sql;public Map<Integer,Object> binds=new HashMap<>();
 public boolean changed;
 public static L1InventoryTransaction last;
 public L1InventoryTransaction(L1PcInstance pc){this.pc=pc;last=this;}
 public boolean debit(L1ItemInstance i,int amount){if(i.count<amount)return false; item=i;pending=true;return true;}
 public void requireTables(String... n){if(!Arrays.equals(n,new String[]{"accounts"}))throw new AssertionError();}
 public void sql(Mutation a){action=a;}
 public void onFailure(Runnable r){fail=r;}
 @Override public void close(){}
 public void finish(boolean updateOne) {
  if(!pending)throw new AssertionError("Not submitted");
  final PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().equals("setString") || m.getName().equals("setInt")){binds.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeUpdate"))return updateOne?1:0;
   if(m.getName().equals("close"))return null;
   throw new AssertionError("Unexpected JDBC statement: "+m.getName()); });
  final Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
    if(m.getName().equals("prepareStatement")){sql=(String)a[0];return ps;}
    throw new AssertionError("Unexpected JDBC connection: "+m.getName()); });
  try{action.apply(c);}catch(SQLException e){if(fail!=null)fail.run();return;}
  item.count--;changed=true;if(after!=null)after.run();
 }
}
''')
put('l1j/server/server/model/network/core/cs/packet/User.java','''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.L1InventoryTransaction;import l1j.server.server.model.Instance.L1PcInstance;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int id){return new L1InventoryTransaction(pc);} }
''')
put('l1j/server/server/model/network/core/cs/packet/CDB.java','''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.L1InventoryTransaction;import l1j.server.server.model.Instance.L1PcInstance;
public class CDB { public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable after){tx.after=after;} }
''')
put('l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java',"""package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ChatPacket {public static void sendDirect(L1PcInstance p,L1PcInstance target,String s){p.messages.add(s);} }
""")
put('l1j/server/server/model/item/function/ItemActionContext.java',"""package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionContext {private final L1PcInstance p;private final L1ItemInstance i;
public ItemActionContext(L1PcInstance p,L1ItemInstance i){this.p=p;this.i=i;}
public L1PcInstance player(){return p;}public L1ItemInstance item(){return i;} }
""")
put('l1j/server/server/model/item/function/ItemActionResult.java',"""package l1j.server.server.model.item.function;
public enum ItemActionResult {COMPLETE}
""")
put('l1j/server/server/model/item/function/TransformationCollectionCards.java', (W/'src/l1j/server/server/model/item/function/TransformationCollectionCards.java').read_text())
put('l1j/server/server/model/item/function/UniqueRegistration103Test.java',"""package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.L1InventoryTransaction;
public class UniqueRegistration103Test {
 static int checks;
 static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
 for(int old:new int[]{0,1,7}){
  for(int score:new int[]{0,1}){
   L1PcInstance pc=new L1PcInstance();pc.acc.setInfo_kir_poly(old);pc.acc.setInfo_col_y(score);
   L1ItemInstance item=new L1ItemInstance(875640295);
   check(TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(pc,item))==ItemActionResult.COMPLETE,"return");
   L1InventoryTransaction tx=L1InventoryTransaction.last;
   check(tx.pending && tx.after!=null && item.count==1 && pc.acc.getInfo_kir_poly()==old && pc.acc.getInfo_col_y()==score,"deferred");
   tx.finish(true);
   check(item.count==0 && pc.acc.getInfo_kir_poly()==old+1 && pc.acc.getInfo_col_y()==1,"committed");
   check(tx.binds.get(2).equals(old) && tx.binds.get(3).equals(score) && tx.sql.contains("info_col_y=1"),"SQL CAS");
   check(pc.bonuses==4 && pc.messages.size()==1,"UI after commit");
  }
 }
 L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(875640295);
 TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(pc,item));
 L1InventoryTransaction tx=L1InventoryTransaction.last;tx.finish(false);
 check(pc.acc.getInfo_kir_poly()==0 && pc.acc.getInfo_col_y()==0 && item.count==1,"unique failure rollback");
 check(pc.messages.size()==1 && pc.messages.get(0).contains("저장하지"),"unique failure notification");
 pc=new L1PcInstance();item=new L1ItemInstance(875640295);item.count=0;
 TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(pc,item));
 check(item.count==0 && pc.acc.getInfo_kir_poly()==0 && pc.messages.size()==1,"unique no item");
 pc=new L1PcInstance();item=new L1ItemInstance(875640296);
 TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(pc,item));
 check(item.count==1 && pc.acc.getInfo_kir_poly()==0,"invalid item rejected");
 System.out.println("PASS 103 unique: "+checks+" assertions");
 }
}
""")
# Real production class is compiled, not replicated.
put('l1j/server/server/model/item/Card/L1CardAdd.java',new)
cases=[]
for t,iid,f in entries:
 cases.append(f'{{ "{t}","{iid}","{f}","{f[0].lower()+f[1:]}" }}')
treecases=[]
for iid,f,t in trees: treecases.append(f'{{ "{iid}","{t}","Tree_{f}","tree_{f}" }}')
java='''package l1j.server.server.model.item.Card;
import java.lang.reflect.*;import java.util.*;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.L1InventoryTransaction;
public class CardRegistration103Test {
 static int checks=0;
 static void chk(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final String[][] CARDS={'''+','.join(cases)+'''};
 static final String[][] TREES={'''+','.join(treecases)+'''};
 static int get(Account a,String field)throws Exception{return (Integer)a.getClass().getMethod("get"+field).invoke(a);}
 static void set(Account a,String field,int v)throws Exception{a.getClass().getMethod("set"+field,int.class).invoke(a,v);}
 static String scoreField(int tier){return tier<=2?"Info_col_l":tier<=4?"Info_col_h":"Info_col_m";}
 static void go(int tier,L1PcInstance pc){switch(tier){case 1 -> L1CardAdd.legend1(pc);case 2 -> L1CardAdd.legend2(pc);case 3 -> L1CardAdd.hero1(pc);case 4 -> L1CardAdd.hero2(pc);case 5 -> L1CardAdd.myth(pc);}}
 public static void main(String[] args)throws Exception{
   int passes=0;
   for(String[] record:CARDS){
    int type=Integer.parseInt(record[0]);int itemId=Integer.parseInt(record[1]);String field=record[2], col=record[3];
    for(int before:new int[]{0,1,11}){
     L1PcInstance pc=new L1PcInstance();Account acc=pc.acc;
     set(acc,field,before);set(acc,scoreField(type),13);
     pc.selected=new L1ItemInstance(itemId);L1ItemInstance item=pc.selected;
     go(type,pc);
     L1InventoryTransaction tx=L1InventoryTransaction.last;
     chk(pc.selected==null,"selection cleared on submit");
     chk(tx.pending && tx.after!=null,"receipt submitted");
     chk(get(acc,field)==before && get(acc,scoreField(type))==13 && item.count==1,"no effects before durable commit");
     tx.finish(true);
     chk(item.count==0 && get(acc,field)==before+1,"item and account commit");
     chk(get(acc,scoreField(type))==13+(before==0?1:0),"first score conditional");
     chk(tx.sql.contains("SET "+col+"="+col+"+1"),"SQL column matches original");
     chk(tx.sql.contains(" AND "+col+"=?"),"SQL card preimage");
     chk(tx.binds.get(1).equals("unitAccount") && tx.binds.get(2).equals(before),"SQL bind types");
     chk(tx.sql.contains("info_col_")==(before==0),"SQL score branch");
     chk(pc.bonuses==(type<=2?1:type<=4?2:3) && pc.messages.size()==1,"UI triggered once after commit");
     passes++;
    }
    // Force SQL mismatch: neither item nor account may change.
    L1PcInstance pc=new L1PcInstance();pc.selected=new L1ItemInstance(itemId);set(pc.acc,field,0);
    L1ItemInstance item=pc.selected;go(type,pc);L1InventoryTransaction tx=L1InventoryTransaction.last;
    tx.finish(false);
    chk(item.count==1 && get(pc.acc,field)==0 && pc.bonuses==0,"preimage race rollback");
    chk(pc.messages.size()==1 && pc.messages.get(0).contains("저장하지"),"failure callback");
    passes++;
    // No remaining item: no receipt submitted.
    pc=new L1PcInstance();pc.selected=new L1ItemInstance(itemId);pc.selected.count=0;
    go(type,pc);chk(pc.selected==null && pc.bonuses==0,"empty selected card rejected");
    passes++;
    // Wrong category: never accepts a different type's card.
    pc=new L1PcInstance();pc.selected=new L1ItemInstance(itemId);
    go(type==1?2:1,pc);chk(pc.selected==null && pc.bonuses==0,"type gating");
    passes++;
   }
   for(String[] rec:TREES){
    int itemId=Integer.parseInt(rec[0]);int tier=Integer.parseInt(rec[1]);String field=rec[2];String col=rec[3];
    L1PcInstance pc=new L1PcInstance();pc.selected=new L1ItemInstance(itemId);L1ItemInstance item=pc.selected;
    L1CardAdd.SkillTree(pc);L1InventoryTransaction tx=L1InventoryTransaction.last;
    chk(tx.pending && tx.after!=null && item.count==1 && get(pc.acc,field)==0,"tree deferred");
    tx.finish(true);chk(item.count==0 && get(pc.acc,field)==1,"tree commit");
    chk(tx.sql.contains("SET "+col+"=1 WHERE login=? AND "+col+"=0"),"tree column and CAS");
    chk(pc.bonuses==10+tier && pc.messages.size()==1,"tree UI once");
    passes++;
    pc=new L1PcInstance();set(pc.acc,field,1);pc.selected=new L1ItemInstance(itemId);item=pc.selected;
    L1CardAdd.SkillTree(pc);chk(pc.selected==null && item.count==1 && pc.bonuses==0,"duplicate never debits");
    passes++;
    pc=new L1PcInstance();pc.selected=new L1ItemInstance(itemId);item=pc.selected;
    L1CardAdd.SkillTree(pc);tx=L1InventoryTransaction.last;tx.finish(false);
    chk(item.count==1 && get(pc.acc,field)==0 && pc.bonuses==0,"tree SQL failure rollback");
    passes++;
   }
   System.out.println("PASS 103: "+checks+" assertions / "+passes+" modeled operations / "+CARDS.length+" card IDs / "+TREES.length+" tree IDs");
 }
}
'''
put('l1j/server/server/model/item/Card/CardRegistration103Test.java',java)
files=list((stubs/'src').rglob('*.java'))
cls=stubs/'classes';cls.mkdir(exist_ok=True)
res=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(cls)]+[str(f) for f in files],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
print('javac',res.returncode,res.stdout[:4500]); assert res.returncode==0
r=subprocess.run(['java','-cp',str(cls),'l1j.server.server.model.item.Card.CardRegistration103Test'],capture_output=True,text=True)
print('java',r.returncode,r.stdout,r.stderr[:4000]);assert r.returncode==0
r=subprocess.run(['java','-cp',str(cls),'l1j.server.server.model.item.function.UniqueRegistration103Test'],capture_output=True,text=True)
print('unique java',r.returncode,r.stdout,r.stderr[:4000]);assert r.returncode==0
print('mapping python checks PASSED')
