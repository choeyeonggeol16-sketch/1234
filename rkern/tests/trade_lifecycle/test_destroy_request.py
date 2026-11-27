"""Execute the actual explicit-destroy request and receipt staging methods with isolated doubles.
No JDBC persistence or in-game client is simulated; this tests request admission,
prepared images, auxiliary SQL dispatch, and result callback boundaries.
"""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "src/l1j/server/server"
M = "l1j.server.server.model"
P = M + ".network.core.cs.packet"
U = SRC / "model/network/core/cs/packet/User.java"
T = SRC / "model/inventory/L1InventoryTransaction.java"

def extract(text, marker):
    start = text.index(marker)
    opening = text.index("{", start)
    level = 1
    pos = opening + 1
    while level:
        if text[pos] == "{":
            level += 1
        elif text[pos] == "}":
            level -= 1
        pos += 1
    return text[start:pos]

u = U.read_text(encoding="utf-8")
t = T.read_text(encoding="utf-8")
request_methods = "\n".join(extract(u, marker) for marker in (
    "static void OnDestroyItemPacket(", "private static boolean CanDestroyItem(",
    "private static void RequestDestroyItem(", "private static void DestroyItemReturn("))
stage_methods = "\n".join(extract(t, marker) for marker in (
    "public boolean StageDestroyItem(", "private static boolean requiresEntityDeletion("))

with tempfile.TemporaryDirectory() as directory:
    root = pathlib.Path(directory)
    def write(pkg, cls, body, imports=""):
        f = root / pathlib.Path(pkg.replace(".", "/")) / (cls + ".java")
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(f"package {pkg};\n{imports}\n{body}\n", encoding="utf-8")

    write(P, "User", "public final class User {\n" + request_methods + "\n}",
          f"""import {M}.*;
import {M}.Instance.*;
import {M}.character.companion.instance.CompanionInstanceCache;
import {M}.network.core.connection.CUserSocket;
import {M}.network.core.sc.packet.*;
import l1j.server.server.datatables.*;
import l1j.server.server.logd.CLogD.ItemActionType;
import l1j.server.server.logd.CLogD;""")
    write(M, "CInventory", "public class CInventory { public static final int MAX_AMOUNT=2000000000; }")
    write(M, "L1Object", "public class L1Object {}")
    write(M, "CWorld", """public class CWorld {
        public static final CWorld INSTANCE=new CWorld();
        public final java.util.Map<Integer,java.util.List<l1j.server.server.model.Instance.L1FurnitureInstance>> placements=new java.util.HashMap<>();
        public static CWorld getInstance(){return INSTANCE;}
        public java.util.List<l1j.server.server.model.Instance.L1FurnitureInstance> findFurnitureByItem(int id){
            return placements.getOrDefault(id,java.util.List.of());
        }
    }""")
    write(M+".Instance", "L1ItemInstance", """public class L1ItemInstance {
        public int id, template, count, bless; public boolean equipped, stackable=true;
        public final Template def=new Template();
        public L1ItemInstance(int id,int template,int count){this.id=id;this.template=template;this.count=count;}
        public int getId(){return id;} public int getItemId(){return template;}
        public int getCount(){return count;} public void setCount(int n){count=n;}
        public int getBless(){return bless;} public boolean isEquipped(){return equipped;}
        public boolean isStackable(){return stackable;} public Template getItem(){return def;}
        public static class Template {public boolean protectedItem;
            public boolean isCantDelete(){return protectedItem;} public String getName(){return "item";}}
    }""")
    write(M+".Instance", "L1PcInstance", """public class L1PcInstance {
        public boolean dead,ghost,teleport,shop,logout,busy,gm;
        public Object trade; public L1CompanionInstance companion;
        public final Inventory inv=new Inventory();
        public final java.util.Map<Integer,Object> dolls=new java.util.HashMap<>();
        public int messages, lights;
        public boolean isDead(){return dead;} public boolean isGhost(){return ghost;}
        public boolean get_teleport(){return teleport;} public boolean isPrivateShop(){return shop;}
        public boolean isLogoutStarted(){return logout;} public boolean isItemTransferBusy(){return busy;}
        public Object GetTrade(){return trade;} public boolean isGm(){return gm;}
        public String getName(){return "owner";} public Inventory getInventory(){return inv;}
        public L1CompanionInstance get_companion(){return companion;}
        public java.util.Map<Integer,Object> getDollList(){return dolls;}
        public boolean refreshLight(){lights++;return true;}
        public static class Inventory {
            public final java.util.Map<Integer,L1ItemInstance> rows=new java.util.HashMap<>();
            public L1ItemInstance GetByServerID(int id){return rows.get(id);}
        }
    }""")
    write(M+".Instance", "L1CompanionInstance", """public class L1CompanionInstance {
        public int id; public int get_control_object_id(){return id;}
    }""")
    write(M+".Instance", "L1DollInstance", """public class L1DollInstance {
        public int id; public int getItemObjId(){return id;}
    }""")
    write(M+".Instance", "L1FurnitureInstance", f"""public class L1FurnitureInstance extends {M}.L1Object {{
        public int id; public int getItemObjId(){{return id;}}
    }}""")
    write(M+".character.companion.instance", "CompanionInstanceCache",
          """public class CompanionInstanceCache {
              public static int protectedId;
              public static boolean is_companion_oblivion(int id){return id!=protectedId;}
          }""")
    write(M+".network.core.connection", "CUserSocket", """public class CUserSocket {
        public L1PcInstance pc; public L1PcInstance getActiveChar(){return pc;}
    }""", f"import {M}.Instance.L1PcInstance;")
    for name, typ in (("SC_ServerMessage", "int code, String... text"), ("SC_SystemMessage", "String text")):
        write(M+".network.core.sc.packet", name, f"""public class {name} {{
            public static void send(L1PcInstance pc,{typ}){{pc.messages++;}}
        }}""", f"import {M}.Instance.L1PcInstance;")
    write("l1j.server.server.datatables", "LetterTable", """public class LetterTable {
        public static int deletes; public static int mailboxOrderKey(String name){return 100;}
        public static void deleteOwnedItemLetter(java.sql.Connection c,int id,String name){
            if(!name.equals("owner")) throw new AssertionError("receiver"); deletes++;
        }
    }""")
    write("l1j.server.server.logd", "CLogD", """public class CLogD {
        public enum ItemActionType { Delete }
        public static int logged,amounts;
        public static CLogD getInstance(){return new CLogD();}
        public void addItemAction(ItemActionType type,L1PcInstance pc,L1ItemInstance item,int amount){
            logged++;amounts+=amount;
        }
    }""", f"import {M}.Instance.*;")
    write(M, "L1InventoryTransaction", """public class L1InventoryTransaction implements AutoCloseable {
        public static L1InventoryTransaction pending;
        public static boolean reject, furnitureRow;
        public final L1PcInstance primary;
        public final java.util.Map<Integer,Entry> entries=new java.util.LinkedHashMap<>();
        public final java.util.List<Mutation> mutations=new java.util.ArrayList<>();
        private Runnable durable,failed,done;
        private boolean submitted;
        public static class Entry {public L1ItemInstance image; public L1ItemInstance original;}
        interface Mutation {void apply(java.sql.Connection c) throws java.sql.SQLException;}
        public static L1InventoryTransaction begin(L1PcInstance pc,int lane) {
            if(reject||pc.busy||pc.trade!=null) throw new IllegalStateException("busy");
            if(lane!=100) throw new AssertionError("mailbox lane");
            pc.busy=true; return new L1InventoryTransaction(pc);
        }
        private L1InventoryTransaction(L1PcInstance pc){primary=pc;}
        private void open(){}
        private Entry findEntry(int id){return entries.get(id);}
        private Entry entry(L1PcInstance pc,L1ItemInstance item){
            if(pc.inv.GetByServerID(item.id)!=item)throw new IllegalStateException("stale");
            return entries.computeIfAbsent(item.id,id->{Entry e=new Entry();e.original=item;
                e.image=new L1ItemInstance(id,item.template,item.count);return e;});
        }
        public boolean debit(L1ItemInstance item,int count){
            if(requiresEntityDeletion(item.template)||count<=0||count>item.count||item.equipped)return false;
            Entry e=entry(primary,item); if(count>e.image.count)return false;
            e.image.count-=count;return true;
        }
        public void requireTables(String... names){}
        public void sql(Mutation m){mutations.add(m);}
        public void onDurableCommit(Runnable r){durable=r;}
        public void onFailure(Runnable r){failed=r;}
        public void commit(Runnable r){if(pending!=null)throw new AssertionError("double submit");
            submitted=true;done=r;pending=this;
        }
        public void close(){if(!submitted)primary.busy=false;}
        static Object proxy(Class<?> type,java.lang.reflect.InvocationHandler handler){
            return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
        }
        static java.sql.Connection connection(){
            java.sql.ResultSet rs=(java.sql.ResultSet)proxy(java.sql.ResultSet.class,
                (p,m,a)->m.getName().equals("next")?furnitureRow:null);
            java.sql.PreparedStatement ps=(java.sql.PreparedStatement)proxy(java.sql.PreparedStatement.class,
                (p,m,a)->m.getName().equals("executeQuery")?rs:null);
            return (java.sql.Connection)proxy(java.sql.Connection.class,(p,m,a)->{
                if(m.getName().equals("prepareStatement")){
                    if(!((String)a[0]).contains("FOR UPDATE"))throw new AssertionError("placement not locked");
                    return ps;
                } return null;
            });
        }
        public static void complete(boolean success){
            L1InventoryTransaction x=pending;if(x==null)throw new AssertionError("no submission");pending=null;
            if(success)try{for(Mutation m:x.mutations)m.apply(connection());}
                catch(java.sql.SQLException invalid){success=false;}
            if(success){
                if(x.durable!=null)x.durable.run();
                for(Entry e:x.entries.values()){
                    if(e.image.count==0)x.primary.inv.rows.remove(e.original.id);
                    else e.original.count=e.image.count;
                }
                x.done.run();
            }else if(x.failed!=null)x.failed.run();
            x.primary.busy=false;
        }
    """ + stage_methods + "\n}", f"import {M}.Instance.*;")
    write(P, "DestroyRequestTest", """public class DestroyRequestTest {
        static int checks;
        static void check(boolean value,String what){checks++;if(!value)throw new AssertionError(what);}
        static L1ItemInstance add(L1PcInstance pc,int id,int template,int count){
            L1ItemInstance item=new L1ItemInstance(id,template,count);pc.inv.rows.put(id,item);return item;
        }
        static void noSubmit(L1PcInstance pc,String what){
            check(L1InventoryTransaction.pending==null&&!pc.busy,what);
        }
        public static void main(String[] args) {
            L1PcInstance pc=new L1PcInstance(); CUserSocket sock=new CUserSocket();sock.pc=pc;
            L1ItemInstance a=add(pc,1,100,10), b=add(pc,2,101,5);
            CS_DestroyItem.handle(sock,1,1,3,null);
            check(pc.busy&&a.count==10&&CLogD.logged==0,"no mutation or log before DB");
            CS_DestroyItem.handle(sock,1,1,1,null);
            L1InventoryTransaction.complete(true);
            check(a.count==7&&CLogD.logged==1&&CLogD.amounts==3&&pc.lights==1,"partial success");
            CS_DestroyItem.handle(sock,1,1,0,null);
            L1InventoryTransaction.complete(false);
            check(a.count==7&&CLogD.logged==1&&pc.lights==1&&!pc.busy,"rollback no change");
            CS_DestroyItem.handle(sock,1,1,8,null);noSubmit(pc,"overcount");
            CS_DestroyItem.handle(sock,1,1,-1,null);noSubmit(pc,"negative");
            CS_DestroyItem.handle(sock,2,0,0,new int[]{1,1,1,1});noSubmit(pc,"duplicate id");
            CS_DestroyItem.handle(sock,2,0,0,new int[]{1,1,999,1});noSubmit(pc,"foreign/missing row");
            b.def.protectedItem=true;
            CS_DestroyItem.handle(sock,2,0,0,new int[]{1,1,2,1});noSubmit(pc,"protected second row");
            check(a.count==7&&b.count==5,"rejected batch intact");
            b.def.protectedItem=false;b.equipped=true;
            CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"equipped");
            b.equipped=false;b.bless=128;
            CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"protected blessing");
            b.bless=0;b.stackable=false;
            CS_DestroyItem.handle(sock,1,2,2,null);noSubmit(pc,"nonstack amount");
            b.stackable=true;
            pc.trade=new Object();CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"trade");
            pc.trade=null;pc.companion=new L1CompanionInstance();pc.companion.id=2;
            CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"active companion");
            pc.companion=null;CompanionInstanceCache.protectedId=2;
            CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"companion cache");
            CompanionInstanceCache.protectedId=0;L1DollInstance doll=new L1DollInstance();doll.id=2;pc.dolls.put(2,doll);
            CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"active doll");pc.dolls.clear();
            L1InventoryTransaction.reject=true;CS_DestroyItem.handle(sock,1,2,1,null);noSubmit(pc,"admission reject");
            L1InventoryTransaction.reject=false;
            CS_DestroyItem.handle(sock,2,0,0,new int[]{1,0,2,2});
            check(a.count==7&&b.count==5,"batch prepared only");
            L1InventoryTransaction.complete(true);
            check(!pc.inv.rows.containsKey(1)&&b.count==3&&CLogD.logged==3&&CLogD.amounts==12,"atomic success and zero count");
            add(pc,3,40314,1);add(pc,4,49016,1);
            CS_DestroyItem.handle(sock,2,0,0,new int[]{3,1,4,1});
            check(LetterTable.deletes==0,"aux before commit");
            L1InventoryTransaction.complete(false);
            check(pc.inv.rows.containsKey(3)&&pc.inv.rows.containsKey(4)&&LetterTable.deletes==0,"aux rollback");
            CS_DestroyItem.handle(sock,2,0,0,new int[]{3,1,4,1});
            L1InventoryTransaction.complete(true);
            check(LetterTable.deletes==1
                &&!pc.inv.rows.containsKey(3)&&!pc.inv.rows.containsKey(4),"linked deletion in receipt");
            add(pc,5,41383,1);L1FurnitureInstance f=new L1FurnitureInstance();f.id=5;
            CWorld.INSTANCE.placements.put(f.getItemObjId(),java.util.List.of(f));
            CS_DestroyItem.handle(sock,1,5,1,null);noSubmit(pc,"deployed furniture");
            CWorld.INSTANCE.placements.clear();L1InventoryTransaction.furnitureRow=true;
            CS_DestroyItem.handle(sock,1,5,1,null);L1InventoryTransaction.complete(true);
            check(pc.inv.rows.containsKey(5),"persisted placement rejects");
            L1InventoryTransaction.furnitureRow=false;
            CS_DestroyItem.handle(sock,1,5,1,null);L1InventoryTransaction.complete(true);
            check(!pc.inv.rows.containsKey(5),"retrieved furniture delete");
            System.out.println("PASS explicit destroy request/receipt regression ("+checks+" checks)");
        }
    }""", f"""import {M}.*;
import {M}.Instance.*;
import {M}.network.core.connection.CUserSocket;
import {M}.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.logd.CLogD;
import l1j.server.server.datatables.*;""")
    classes = root / "classes"
    classes.mkdir()
    sources = [str(f) for f in root.rglob("*.java")]
    sources.append(str(SRC / "model/network/core/cs/packet/CS_DestroyItem.java"))
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes), *sources], check=True)
    subprocess.run(["java", "-cp", str(classes), P + ".DestroyRequestTest"], check=True)
