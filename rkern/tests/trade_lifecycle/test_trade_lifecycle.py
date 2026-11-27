"""Compile the actual CTrade/CTradeManager against isolated owner, packet and receipt doubles.
This tests lifecycle and handoff, not the actual JDBC transaction or L1 client."""
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src/l1j/server/server/model/inventory"
M = "l1j.server.server.model"
I = M + ".inventory"
S = {}
def stub(pkg, name, body, imports=""):
    S[pkg.replace(".", "/") + "/" + name + ".java"] = f"package {pkg};\n{imports}\n{body}\n"

stub("it.unimi.dsi.fastutil.ints", "Int2ObjectOpenHashMap", """
public class Int2ObjectOpenHashMap<V> extends java.util.HashMap<Integer,V> {
    public Int2ObjectOpenHashMap(int n) { super(n); }
}
""")
stub(I, "CInventory", "public class CInventory { public static final int MAX_AMOUNT=2000000000, OK=0; }")
stub(I, "CUserInventory", """
public class CUserInventory extends CInventory {
    public static final int MAX_SIZE=180;
    public final java.util.HashMap<Integer,L1ItemInstance> items=new java.util.HashMap<>();
    public boolean full;
    public L1ItemInstance GetByServerID(int id){return items.get(id);}
    public int maxIncoming=Integer.MAX_VALUE;
    public int CheckAddable(L1ItemInstance item,int count){return full || count>maxIncoming ? -1 : OK;}
    public int CheckAddableTransfer(L1ItemInstance item,int count){return CheckAddable(item,count);}
}
""", f"import {M}.Instance.L1ItemInstance;")
stub(M+".Instance", "L1ItemInstance", """
public class L1ItemInstance {
    public final int id, template; public int count, enchant; public boolean equipped, stack=true;
    public int bless; public final Definition item=new Definition();
    public L1ItemInstance(int id,int template,int count){this.id=id;this.template=template;this.count=count;}
    public L1ItemInstance copy(int n){L1ItemInstance v=new L1ItemInstance(id,template,n);
        v.enchant=enchant;v.bless=bless;v.stack=stack;v.equipped=equipped;v.item.tradable=item.tradable;return v;}
    public static final class Definition { public boolean tradable=true;
        public boolean isTradable(){return tradable;} public String getName(){return "item";} }
    public int getId(){return id;} public int getItemId(){return template;}
    public int getCount(){return count;} public void setCount(int n){count=n;}
    public Definition getItem(){return item;} public boolean isStackable(){return stack;}
    public boolean isEquipped(){return equipped;} public int getEnchantLevel(){return enchant;}
    public int getBless(){return bless;} public int getChargeCount(){return 0;}
    public int getAttrEnchantLevel(){return 0;} public int getSpecialEnchant(){return 0;}
    public boolean isPackage(){return false;} public boolean isIdentified(){return true;}
    public int get_durability(){return 0;} public int getRemainingTime(){return 0;}
    public int get_bless_level(){return 0;} public int get_bless_level2(){return 0;}
    public int get_item_level(){return 0;} public int getKeyId(){return 0;}
    public Object getLastUsed(){return null;} public Object getEndTime(){return null;}
    public Object getHotel_Town(){return null;}
}
""")
stub(M+".network.core.connection", "CUserSocket", """
public class CUserSocket {
    public volatile boolean connected=true;
    public volatile L1PcInstance active;
    public int starts, adds, result=-1, results, token, resync, messages;
    public boolean failAdd, failAsk, ready=true;
    public boolean ownsReadyGameSession(){return connected && ready && active != null;}
    public int affinityShard(){return 0;}
    public L1PcInstance getActiveChar(){return active;}
}
""", f"import {M}.Instance.L1PcInstance;")
stub(M+".Instance", "L1PcInstance", """
public class L1PcInstance {
    private final int id; private volatile CTrade trade;
    private static final Object SHARED_MAP=new Object();
    public volatile int stamp=1, x=10, y=10, map=4;
    public volatile Object mapInstance=SHARED_MAP;
    public volatile boolean dead, ghost, shop, logout, changeItem, teleport, offlineShop, autoClanJoin;
    public boolean getIsChangeItem(){return changeItem;}
    public boolean getIsChangeItem1(){return false;}
    public boolean getIsChangeItem2(){return false;}
    public boolean getIsChangeItem3(){return false;}
    public boolean getIsChangeItem4(){return false;}
    public boolean getIsChangeItem5(){return false;}
    public boolean getIsChangeItem6(){return false;}
    public boolean getIsChangeItem7(){return false;}
    public boolean getIsChangeItem8(){return false;}
    public boolean getIsChangeItem9(){return false;}
    public boolean getIsChangeItem10(){return false;}
    public boolean getIsChangeItem11(){return false;}
    public boolean getIsChangeItem12(){return false;}
    public boolean getIsChangeItem13(){return false;}
    public boolean getIsChangeItem14(){return false;}
    public boolean getIsChangeItem15(){return false;}
    public boolean getIsChangeItem16(){return false;}
    public boolean getIsChangeItem17(){return false;}
    public boolean getIsChangeItem18(){return false;}
    public boolean getIsChangeItem19(){return false;}
    public boolean getIsChangeItem20(){return false;}
    public String account;
    public final CUserSocket socket=new CUserSocket();
    public final CUserInventory inv=new CUserInventory();
    private final java.util.concurrent.atomic.AtomicBoolean gate=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.Map<Integer,Object> dolls=new java.util.HashMap<>();
    public L1CompanionInstance companion;
    public L1PcInstance(int id){this.id=id;account="a"+id;socket.active=this;}
    public int getId(){return id;} public String getName(){return account;}
    public String getAccountName(){return account;} public int getLevel(){return 80;}
    public int getSessionStamp(){return stamp;} public int getMapId(){return map;}
    public Object getMap(){return mapInstance;}
    public int getX(){return x;} public int getY(){return y;}
    public boolean isGhost(){return ghost;} public boolean isDead(){return dead;}
    public boolean isPrivateShop(){return shop;} public boolean isGm(){return false;}
    public boolean get_teleport(){return teleport;} public boolean is무인상점(){return offlineShop;}
    public boolean isAutoClanjoin(){return autoClanJoin;}
    public CTrade GetTrade(){return trade;} public void SetTrade(CTrade t){trade=t;}
    public CUserSocket getNetConnection(){return socket;}
    public boolean isItemTransferSession(CUserSocket s,int n){return s==socket && s.connected && s.active==this && stamp==n && !logout;}
    public int tryReserveItemTransfer(CUserSocket s,int n){
        if(!isItemTransferSession(s,n)||!gate.compareAndSet(false,true))return 0;
        if(!isItemTransferSession(s,n)){gate.set(false);return 0;}return 1;}
    public void endItemTransfer(int n){if(n==1)gate.set(false);}
    public boolean isItemTransferBusy(){return gate.get();}
    public CUserInventory getInventory(){return inv;}
    public L1CompanionInstance get_companion(){return companion;}
    public java.util.Map<Integer,Object> getDollList(){return dolls;}
    public boolean refreshLight(){return true;}
}
""", f"import {I}.*; import {M}.network.core.connection.CUserSocket;")
stub(M+".Instance", "L1DollInstance", "public class L1DollInstance { public int getItemObjId(){return 0;} }")
stub(M+".Instance", "L1CompanionInstance", "public class L1CompanionInstance { public int control; public int get_control_object_id(){return control;} }")
stub("l1j.server.server.datatables","NoTradable","public class NoTradable { public static boolean blocksPlayerTransfer(boolean gm,int id){return false;} }")
stub(M+".character.companion.instance","CompanionInstanceCache","public class CompanionInstanceCache { public static boolean is_companion_oblivion(int id){return true;} }")
stub(M+".item","L1ItemId","public class L1ItemId { public static final int HIGH_CHARACTER_TRADE=999,LOW_CHARACTER_TRADE=998; }")
stub(I+".warehouse","Warehouse","public class Warehouse { public static L1ItemInstance transferCopy(L1ItemInstance v,int id,int n,int charge){return v.copy(n);} }",f"import {M}.Instance.L1ItemInstance;")
stub("l1j.server.server.logd","CLogD","public class CLogD { public static CLogD getInstance(){return new CLogD();} public void addTrade(boolean b,L1PcInstance a,L1PcInstance z,L1ItemInstance v,int n){} public void addCheckTrade(boolean b,L1PcInstance a,L1PcInstance z,L1ItemInstance v,int n){} }",f"import {M}.Instance.*;")
P=M+".network.core.sc.packet"
imports=f"import {M}.Instance.*; import {M}.network.core.connection.CUserSocket;"
stub(P,"SC_MessageYN","public class SC_MessageYN { public static boolean send(L1PcInstance pc,int token,int type,String name){pc.socket.token=token;return !pc.socket.failAsk;} }",imports)
stub(P,"SC_Trade","public class SC_Trade { public static boolean send(CUserSocket s,String n){s.starts++;return s.connected;} }",imports)
stub(P,"SC_TradeAddItem","public class SC_TradeAddItem { public static boolean send(CUserSocket s,L1ItemInstance item,int n,int type){s.adds++;return !s.failAdd;} }",imports)
stub(P,"SC_TradeStatus","public class SC_TradeStatus { public static boolean send(CUserSocket s,int result){s.result=result;s.results++;return true;} }",imports)
stub(P,"SC_ItemStatus","public class SC_ItemStatus { public static boolean send(L1PcInstance pc,L1ItemInstance item){pc.socket.resync++;return true;} }",imports)
stub(P,"SC_ServerMessage","public class SC_ServerMessage { public static boolean send(L1PcInstance pc,int type,String... text){return true;} }",imports)
stub(P,"SC_SystemMessage","public class SC_SystemMessage { public static boolean send(L1PcInstance pc,String text){pc.socket.messages++;return true;} }",imports)
stub(M+".network.thread","CIOThreadCallback","""
public class CIOThreadCallback {
    private static final CIOThreadCallback INSTANCE=new CIOThreadCallback();
    public static CIOThreadCallback getInstance(){return INSTANCE;}
    public interface WheelTask {void run();boolean isCancelled();}
    public final java.util.List<WheelTask> timers=new java.util.ArrayList<>();
    public boolean reject;
    public int shardCount(){return 1;}
    public static int currentShardIndex(){return 0;}
    public synchronized boolean scheduleNetworkTimeout(int shard,WheelTask task,long delay){
        if(reject)return false;timers.add(task);return true;}
    boolean scheduleServerTimer(int shard,WheelTask task,long delay){return scheduleNetworkTimeout(shard,task,delay);}
    synchronized void cancelDistantServerTimer(int shard,CServerTimer timer){timers.remove(timer);}
    public synchronized WheelTask last(){return timers.get(timers.size()-1);}
    public synchronized int active(){return timers.size();}
    public void discardAll(){
        java.util.List<WheelTask> old;
        synchronized(this){old=java.util.List.copyOf(timers);timers.clear();}
        for(WheelTask task:old) if(task instanceof CServerTimer timer) timer.discard();
    }
}
""")
stub("l1j.server.server.task","COwnedWork","""
public class COwnedWork {
    public boolean reserveTimer(){throw new UnsupportedOperationException();}
    public void releaseTimer(){throw new UnsupportedOperationException();}
    public boolean isClosed(){throw new UnsupportedOperationException();}
    public boolean tryExecute(Runnable action){throw new UnsupportedOperationException();}
}
""")
stub(I,"L1InventoryTransaction","""
public class L1InventoryTransaction implements AutoCloseable {
    public static volatile L1InventoryTransaction pending;
    public static int submitted;
    public static boolean reject, failPlan;
    public static Runnable planHook;
    final L1PcInstance a,b;
    final int at,bt;
    boolean committed;
    Runnable terminal, rollback, done;
    record Move(L1ItemInstance item,int n,L1PcInstance from,L1PcInstance to){}
    final java.util.List<Move> moves=new java.util.ArrayList<>();
    private L1InventoryTransaction(L1PcInstance a,L1PcInstance b,int at,int bt){this.a=a;this.b=b;this.at=at;this.bt=bt;}
    static L1InventoryTransaction beginTrade(CTrade trade,L1PcInstance a,L1PcInstance b){
        if(reject||!trade.ReadyForCommit(a,b))throw new IllegalStateException("rejected");
        int at=a.tryReserveItemTransfer(a.socket,a.stamp);if(at==0)throw new IllegalStateException("busy");
        int bt=b.tryReserveItemTransfer(b.socket,b.stamp);
        if(bt==0){a.endItemTransfer(at);throw new IllegalStateException("busy");}
        return new L1InventoryTransaction(a,b,at,bt);
    }
    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int n,L1PcInstance to){
        if(failPlan)throw new IllegalStateException("plan failure");
        moves.add(new Move(item,n,from,to));if(planHook!=null)planHook.run();return item;
    }
    public void onTerminalFailure(Runnable r){terminal=r;}
    public void onFailure(Runnable r){rollback=r;}
    public void commit(Runnable r){if(pending!=null)throw new IllegalStateException("duplicate commit");
        done=r;committed=true;submitted++;pending=this;}
    private void release(){a.endItemTransfer(at);b.endItemTransfer(bt);}
    public void close(){if(!committed)release();}
    public static void complete(int outcome){
        L1InventoryTransaction tx=pending;if(tx==null)throw new AssertionError("no receipt");
        pending=null;
        if(outcome==1){
            for(Move m:tx.moves){
                m.item.setCount(m.item.getCount()-m.n);
                m.to.inv.items.put(m.item.getId(),m.item.copy(m.n));
            }
            try{tx.done.run();}finally{tx.release();}
        } else {
            tx.terminal.run();
            if(outcome==0){tx.release();if(tx.rollback!=null)tx.rollback.run();}
            // outcome -1: uncertain outcome retains gates; production CacheD quarantines.
        }
    }
}
""", f"import {M}.Instance.*;")
stub(I, "TradeLifecycleTest", """
public class TradeLifecycleTest {
    static final CTradeManager MGR=CTradeManager.getInstance();
    static final CIOThreadCallback WHEEL=CIOThreadCallback.getInstance();
    static int ids=100, checks=0;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static L1PcInstance pc(){return new L1PcInstance(++ids);}
    static CTrade request(L1PcInstance a,L1PcInstance b){
        check(MGR.CreateTrade(a,b),"create");CTrade t=a.GetTrade();
        check(t!=null&&b.GetTrade()==t&&MGR.FindTrade(t.GetID())==t,"shared registry");
        check(!a.isItemTransferBusy()&&!b.isItemTransferBusy(),"short gates released");
        check(b.socket.token==t.GetID(),"dialog identity");return t;
    }
    static CTrade open(L1PcInstance a,L1PcInstance b){
        CTrade t=request(a,b);t.Request(b,t.GetID(),1);
        check(a.socket.starts==1&&b.socket.starts==1,"both opened");return t;
    }
    static L1ItemInstance item(L1PcInstance a,int n){
        L1ItemInstance v=new L1ItemInstance(++ids,501,n);a.inv.items.put(v.id,v);return v;
    }
    static void retired(CTrade t,L1PcInstance a,L1PcInstance b){
        check(a.GetTrade()==null&&b.GetTrade()==null&&MGR.FindTrade(t.GetID())==null,"retired once");
    }
    static void pending(L1PcInstance a,L1PcInstance b,CTrade t,L1ItemInstance v){
        t.RequestAddItems(a,v.id,2);t.PressDone(a);t.PressDone(b);
        check(t.IsResolving()&&L1InventoryTransaction.pending!=null,"receipt pending");
    }
    static void expire(CTrade t)throws Exception{
        var f=CTrade.class.getDeclaredField("lastActivity");f.setAccessible(true);
        f.setLong(t,System.nanoTime()-301_000_000_000L);
    }
    public static void main(String[] args)throws Exception{
        L1PcInstance a=pc(),b=pc(),other=pc();
        CTrade t=request(a,b);var timer=WHEEL.last();
        check(!MGR.CreateTrade(a,other),"one exchange per PC");
        var firstItems=CTrade.class.getDeclaredField("firstItems");firstItems.setAccessible(true);
        check(firstItems.get(t)==null,"no eager list");
        L1ItemInstance v=item(a,10);
        t.RequestAddItems(a,v.id,1); t.PressDone(a);
        check(firstItems.get(t)==null&&L1InventoryTransaction.pending==null,"pending request cannot offer/commit");
        t.Request(a,t.GetID(),1);t.Request(b,t.GetID()+1,1);
        check(a.socket.starts==0&&b.socket.starts==0,"wrong participant/token rejected");
        t.Request(b,t.GetID(),1);
        check(a.socket.starts==1&&b.socket.starts==1,"correct reply");
        t.Request(b,t.GetID(),1);check(a.socket.starts==1,"duplicate reply");
        t.RequestAddItems(a,v.id,2);t.RequestAddItems(a,v.id,3);
        t.RequestAddItems(a,v.id,6);check(v.count==10,"offer not escrow");
        check(((java.util.List<?>)firstItems.get(t)).size()==1&&a.socket.adds==2,"aggregate offer, overflow rejected");
        t.PressDone(a);t.RequestAddItems(a,v.id,1);check(a.socket.adds==2,"confirmed list frozen");
        int commits=L1InventoryTransaction.submitted;
        t.PressDone(b);t.PressDone(b);t.Canceled(a);
        check(WHEEL.active()==0, "receipt retires the distant timer immediately");
        check(L1InventoryTransaction.submitted==commits+1&&v.count==10,"exactly one pending commit");
        check(a.GetTrade()==t&&t.IsResolving(),"cancel cannot interrupt receipt");
        L1InventoryTransaction.complete(1);retired(t,a,b);
        check(v.count==5&&b.inv.items.get(v.id).count==5&&a.socket.result==0&&b.socket.result==0,"success after receipt");
        check(timer.isCancelled(),"old timer cancelled");
        t=request(a,b);timer.run();check(a.GetTrade()==t,"old timeout cannot retire new trade");t.Canceled(b);retired(t,a,b);

        a=pc();b=pc();t=open(a,b);v=item(a,9);pending(a,b,t,v);
        L1InventoryTransaction.complete(0);retired(t,a,b);
        check(v.count==9&&b.inv.items.isEmpty()&&a.socket.result==1&&a.socket.resync>0,"confirmed rollback resync");
        check(!a.isItemTransferBusy()&&!b.isItemTransferBusy(),"rollback gates released");
        a=pc();b=pc();t=open(a,b);v=item(a,9);pending(a,b,t,v);
        L1InventoryTransaction.complete(-1);retired(t,a,b);
        check(v.count==9&&a.isItemTransferBusy()&&b.isItemTransferBusy(),"unknown receipt does not retry/reopen gates");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);v.enchant=1;
        t.PressDone(a);t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"changed offer rejected");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        L1InventoryTransaction.reject=true;t.PressDone(a);t.PressDone(b);L1InventoryTransaction.reject=false;
        retired(t,a,b);check(v.count==9,"receipt admission rejection");
        a=pc();b=pc();t=request(a,b);expire(t);WHEEL.last().run();retired(t,a,b);
        a=pc();b=pc();t=open(a,b);expire(t);WHEEL.last().run();retired(t,a,b);
        a=pc();b=pc();t=open(a,b);var original=WHEEL.last();original.run();
        check(WHEEL.active()==1&&WHEEL.last()!=original&&original.isCancelled(),"activity rearms one timer");
        t.Canceled(a);retired(t,a,b);check(WHEEL.active()==0,"cancel releases the refreshed timer");
        for(int i=0;i<32;i++){
            a=pc();b=pc();t=open(a,b);final var racing=WHEEL.last();
            var firing=Thread.ofVirtual().start(racing::run);
            t.Canceled(a);firing.join();retired(t,a,b);
            check(WHEEL.active()==0,"concurrent timeout refresh and cancel leave no timer");
        }
        a=pc();b=pc();var attach=CTradeManager.class.getDeclaredMethod("attach",L1PcInstance.class,L1PcInstance.class);
        attach.setAccessible(true);t=(CTrade)attach.invoke(MGR,a,b);
        t.Canceled(a);check(!t.SendRequest()&&b.socket.token==0&&WHEEL.active()==0,
                "cancel between registry publication and initial arm leaves no timer or dialog");retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);t.PressDone(a);
        var racingReceipt=WHEEL.last();var receiptTimerThread=Thread.ofVirtual().start(racingReceipt::run);
        t.PressDone(b);receiptTimerThread.join();
        check(WHEEL.active()==0&&t.IsResolving(),"concurrent refresh cannot rearm a committing trade");
        WHEEL.discardAll();check(t.IsResolving(),"wheel shutdown cannot cancel an admitted receipt");
        L1InventoryTransaction.complete(1);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);WHEEL.discardAll();retired(t,a,b);
        check(WHEEL.active()==0,"wheel shutdown discards pending trade");
        a=pc();b=pc();t=request(a,b);b.stamp++;t.Request(b,t.GetID(),1);retired(t,a,b);
        check(b.socket.starts==0,"old session denied");
        a=pc();b=pc();t=open(a,b);v=item(a,9);b.x=20;t.RequestAddItems(a,v.id,1);retired(t,a,b);
        a=pc();b=pc();b.socket.ready=false;
        check(!MGR.CreateTrade(a,b)&&a.GetTrade()==null&&b.GetTrade()==null&&b.socket.token==0,
                "target must be ready before a trade request is published");
        b.socket.ready=true;b.mapInstance=new Object();
        check(!MGR.CreateTrade(a,b)&&b.socket.token==0,"equal map IDs do not link different instances");
        b.mapInstance=a.mapInstance;t=request(a,b);b.socket.ready=false;
        t.Request(b,t.GetID(),1);retired(t,a,b);
        check(a.socket.starts==0&&b.socket.starts==0,"readiness is rechecked before opening");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);t.PressDone(a);
        b.mapInstance=new Object();t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"map instance change blocks settlement");
        a=pc();b=pc();WHEEL.reject=true;check(!MGR.CreateTrade(a,b),"timer rejection");WHEEL.reject=false;
        check(a.GetTrade()==null&&b.GetTrade()==null,"timer reject cleanup");
        b.socket.failAsk=true;check(!MGR.CreateTrade(a,b),"dialog failure");
        check(a.GetTrade()==null&&b.GetTrade()==null,"dialog failure cleanup");
        a=pc();b=pc();t=open(a,b);v=item(a,9);b.socket.failAdd=true;t.RequestAddItems(a,v.id,2);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);v.equipped=true;t.RequestAddItems(a,v.id,2);
        check(a.socket.adds==0,"equipped item rejected");t.Canceled(a);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);
        t.RequestAddItems(a,v.id,10);t.RequestAddItems(a,v.id,Integer.MAX_VALUE);
        check(a.socket.adds==0&&v.count==9,"excessive request is not clamped");
        b.inv.maxIncoming=5;t.RequestAddItems(a,v.id,3);t.RequestAddItems(a,v.id,3);
        check(a.socket.adds==1,"cumulative offer capacity checked");
        t.Canceled(a);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);
        L1ItemInstance w=item(b,8);
        t.RequestAddItems(a,v.id,2);t.RequestAddItems(b,w.id,3);
        int beforeOpposing=L1InventoryTransaction.submitted;
        t.PressDone(a);t.PressDone(b);retired(t,a,b);
        check(L1InventoryTransaction.submitted==beforeOpposing&&v.count==9&&w.count==8,"opposing stackable class rejected");
        a=pc();b=pc();t=open(a,b);v=item(a,9);
        b.inv.items.put(v.id,v.copy(9));t.RequestAddItems(a,v.id,2);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"duplicate durable ID rejected on offer");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        b.inv.items.put(v.id,v.copy(9));t.PressDone(a);t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"duplicate durable ID rejected on commit");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        final L1PcInstance moving=b;L1InventoryTransaction.planHook=()->moving.x=20;
        try{t.PressDone(a);t.PressDone(b);}finally{L1InventoryTransaction.planHook=null;}
        retired(t,a,b);check(v.count==9&&L1InventoryTransaction.pending==null,"position invalidated during planning");
        a=pc();b=pc();t=open(a,b);v=item(a,9);w=new L1ItemInstance(++ids,502,8);b.inv.items.put(w.id,w);
        t.RequestAddItems(a,v.id,2);t.RequestAddItems(b,w.id,3);t.PressDone(a);t.PressDone(b);
        L1InventoryTransaction.complete(1);retired(t,a,b);
        check(v.count==7&&w.count==5&&a.inv.items.get(w.id).count==3&&b.inv.items.get(v.id).count==2,"different stackable classes exchange");
        a=pc();b=pc();t=open(a,b);v=item(a,2);v.stack=false;
        t.RequestAddItems(a,v.id,1);t.RequestAddItems(a,v.id,1);
        check(v.count==2&&a.GetTrade()==t&&b.GetTrade()==t&&a.socket.adds==1&&b.socket.adds==1,
                "duplicate nonstack offer ignored without mutation or repeated notification");
        t.Canceled(a);retired(t,a,b);
        a=pc();b=pc();b.offlineShop=true;check(!MGR.CreateTrade(a,b),"offline shop blocks trade request");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);t.PressDone(a);
        b.teleport=true;t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"pending teleport blocks final confirmation");
        a=pc();b=pc();a.changeItem=true;check(!MGR.CreateTrade(a,b),"pending item change blocks request");
        check(a.GetTrade()==null&&b.GetTrade()==null,"pending change request cleanup");
        a=pc();b=pc();t=request(a,b);b.changeItem=true;t.Request(b,t.GetID(),1);retired(t,a,b);
        check(b.socket.starts==0,"pending change blocks acceptance");
        a=pc();b=pc();t=open(a,b);v=item(a,9);b.changeItem=true;t.RequestAddItems(a,v.id,2);retired(t,a,b);
        check(v.count==9&&a.socket.adds==0,"pending change blocks item addition");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);t.PressDone(a);
        b.changeItem=true;int beforePendingChange=L1InventoryTransaction.submitted;t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.submitted==beforePendingChange,"pending change blocks final confirmation");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        final L1PcInstance pendingChange=b;L1InventoryTransaction.planHook=()->pendingChange.changeItem=true;
        try{t.PressDone(a);t.PressDone(b);}finally{L1InventoryTransaction.planHook=null;}
        retired(t,a,b);check(v.count==9&&L1InventoryTransaction.pending==null,"pending change during planning");
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        t.RejectPickup(a);retired(t,a,b);
        check(a.socket.result==1&&b.socket.result==1&&a.socket.messages==1&&v.count==9,"pickup cancels both, no ownership mutation");
        a=pc();b=pc();t=request(a,b);t.RejectPickup(b);retired(t,a,b);
        check(b.socket.messages==1,"pickup cancels pending request");
        a=pc();b=pc();t=open(a,b);v=item(a,9);pending(a,b,t,v);
        t.RejectPickup(b);check(t.IsResolving()&&b.socket.messages==1&&a.GetTrade()==t,"pickup cannot cancel pending receipt");
        L1InventoryTransaction.complete(1);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);a.companion=new L1CompanionInstance();a.companion.control=v.id;
        t.RequestAddItems(a,v.id,2);check(a.socket.adds==0,"active companion control item rejected");
        t.Canceled(a);retired(t,a,b);
        a=pc();b=pc();t=open(a,b);v=item(a,9);t.RequestAddItems(a,v.id,2);
        a.companion=new L1CompanionInstance();a.companion.control=v.id;
        t.PressDone(a);t.PressDone(b);retired(t,a,b);
        check(v.count==9&&L1InventoryTransaction.pending==null,"companion summoned after offer rejected");
        for(int round=0;round<100;round++){
            final L1PcInstance x=pc(),y=pc(),z=pc();
            Thread left=new Thread(()->MGR.CreateTrade(x,y)),right=new Thread(()->MGR.CreateTrade(x,z));
            left.start();right.start();left.join();right.join();
            CTrade active=x.GetTrade();
            check(active!=null&&((y.GetTrade()==active)^(z.GetTrade()==active)),"concurrent create exclusive");
            active.Canceled(x);
            check(x.GetTrade()==null&&y.GetTrade()==null&&z.GetTrade()==null,"exclusive create retired");
        }
        for(int round=0;round<100;round++){
            final L1PcInstance x=pc(),y=pc();final CTrade active=open(x,y);
            L1ItemInstance row=item(x,10);active.RequestAddItems(x,row.id,2);
            int before=L1InventoryTransaction.submitted;
            Thread left=new Thread(()->active.PressDone(x)),right=new Thread(()->active.PressDone(y));
            left.start();right.start();left.join();right.join();
            check(L1InventoryTransaction.submitted==before+1,"simultaneous confirmation exactly once");
            L1InventoryTransaction.complete(1);retired(active,x,y);
        }
        System.out.println("PASS CTrade/CTradeManager lifecycle: "+checks+" checks (isolated receipts)");
    }
}
""", f"import {M}.Instance.*; import {M}.network.thread.CIOThreadCallback;")
with tempfile.TemporaryDirectory() as directory:
    tmp=Path(directory)
    for name, content in S.items():
        path=tmp/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(content,encoding="utf-8")
    out=tmp/"classes"
    out.mkdir()
    files=[str(p) for p in tmp.rglob("*.java")] + [str(SRC/"CTrade.java"), str(SRC/"CTradeManager.java"),
        str(SRC.parent/"network/thread/CServerTimer.java")]
    subprocess.run(["javac","-encoding","UTF-8","--release","21","-d",str(out),*files],check=True)
    subprocess.run(["java","-cp",str(out),I+".TradeLifecycleTest"],check=True,timeout=30)
