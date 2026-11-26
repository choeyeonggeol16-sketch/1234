"""Compile actual owner admission and detached-shop retirement methods using isolated doubles."""
from pathlib import Path
import tempfile
import subprocess

root = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model"
owners = (root / "InventoryTransactionOwners.java").read_text(encoding="utf-8")
owners = owners[owners.index("final class InventoryTransactionOwners"):]
owners = owners.replace("final class InventoryTransactionOwners", "static final class InventoryTransactionOwners", 1)
login = (root / "network/core/cs/packet/CS_EnterWorld.java").read_text(encoding="utf-8")
login = login[login.index("    private static boolean currentPrivateStoreLogin("):login.index("    private static EnterData loadEnterData(")]
socket = (root / "network/core/connection/CUserSocket.java").read_text(encoding="utf-8")
socket = socket[socket.index("    private volatile boolean privateStoreRetirementPending;"):socket.index("    private volatile PlayerShopView playerShopView;")]
fixture = r"""
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
public final class RetirementCheck {
    static final Logger _log = Logger.getLogger("test");
    static int checks, enters;
    static void check(boolean v,String msg) {checks++;if(!v)throw new AssertionError(msg);}
    enum CConnectionState {AUTHENTICATED,IN_GAME,DISCONNECTED}
    record Account(String getName) {}
    static class CUserSocket {
        boolean connected=true, complete;
        Account account;
        L1PcInstance pc;
        CConnectionState state=CConnectionState.AUTHENTICATED;
        boolean isConnected(){return connected;}
        boolean isLogoutComplete(){return complete;}
        Account getAccount(){return account;}
        L1PcInstance getActiveChar(){return pc;}
        CConnectionState getConnectionState(){return state;}
        void close(){connected=false;}
""" + socket + r"""
    }
    static class L1World {
        static L1World world=new L1World();
        static L1World getInstance(){return world;}
        final Map<Integer,L1PcInstance> players=new HashMap<>();
        Object findObject(int id){return players.get(id);}
    }
    static class CacheD {
        static final CacheD INSTANCE=new CacheD();
        boolean pending;
        static CacheD getInstance(){return INSTANCE;}
        boolean hasPendingOwnerApply(int id){return pending;}
    }
    static class LoginController {
        static final LoginController INSTANCE=new LoginController();
        CUserSocket current;
        static LoginController getInstance(){return INSTANCE;}
        CUserSocket getClientByAccount(String a){return current;}
    }
    static class ThreadPool {
        static final ThreadPool INSTANCE=new ThreadPool();
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        static ThreadPool getInstance(){return INSTANCE;}
        void execute(Runnable r){jobs.add(r);}
    }
    static class CIOThreadCallback {
        static final CIOThreadCallback INSTANCE=new CIOThreadCallback();
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        static CIOThreadCallback getInstance(){return INSTANCE;}
        boolean reject;
        boolean completeSession(CUserSocket c,Runnable r,Consumer<Throwable> end){
            if(reject){end.accept(new IllegalStateException());return false;}
            jobs.add(()->{try{r.run();end.accept(null);}catch(Throwable e){end.accept(e);}});
            return true;
        }
    }
    static class Inventory {
        L1PcInstance pc; int token;
        boolean tryBeginEquipmentChange(){token=pc.tryReserveItemTransfer();return token!=0;}
        void endEquipmentChange(){pc.endItemTransfer(token);token=0;}
    }
    static class L1PcInstance {
        int id,stamp=1, logouts, trade;
        boolean shop,dead,done,quarantined;
        String account;
        CUserSocket socket;
        final Inventory inv=new Inventory();
        final AtomicInteger gate=new AtomicInteger();
        L1PcInstance(int id,String a){this.id=id;account=a;inv.pc=this;L1World.world.players.put(id,this);}
        int getId(){return id;}
        int getSessionStamp(){return stamp;}
        String getAccountName(){return account;}
        boolean isPrivateShop(){return shop;}
        boolean isLogoutStarted(){return dead;}
        boolean isLogoutComplete(){return done;}
        int getTradeID(){return trade;}
        boolean isTradeResolving(){return false;}
        CUserSocket getNetConnection(){return socket;}
        Inventory getInventory(){return inv;}
        boolean isItemTransferBusy(){return (gate.get()&1)!=0;}
        int tryReserveItemTransfer(){int v=gate.get();return (v&1)==0&&gate.compareAndSet(v,v|1)?v|1:0;}
        void endItemTransfer(int t){gate.compareAndSet(t,t+1);}
        boolean isItemTransferSession(CUserSocket s,int st){return s!=null&&!dead&&stamp==st&&socket==s&&s.pc==this&&s.connected;}
        void quarantineItemTransferSession(CUserSocket s,int st){quarantined=true;}
        void logout(){check(isItemTransferBusy(),"logout must own inventory gate");dead=true;done=true;logouts++;L1World.world.players.remove(id,this);}
    }
    static class User {
        static boolean reject;
        static int closes;
        static boolean RemovePrivateStoreList(L1PcInstance p){
            check(p.isItemTransferBusy(),"close must own inventory gate");
            if(reject)return false;
            p.shop=false;closes++;return true;
        }
    }
    static void doEnterWorld(String n,CUserSocket c){check(!c.isPrivateStoreRetirementPending(),"pending released before reload");enters++;}
""" + login + owners + r"""
    static CUserSocket login(String name){
        var s=new CUserSocket();s.account=new Account(name);
        LoginController.INSTANCE.current=s;return s;
    }
    static void attach(L1PcInstance p){
        p.socket=new CUserSocket();p.socket.pc=p;
        p.socket.state=CConnectionState.IN_GAME;
    }
    static L1PcInstance shop(int id){var p=new L1PcInstance(id,"merchant");p.shop=true;return p;}
    static void drain(){ThreadPool.INSTANCE.jobs.remove().run();CIOThreadCallback.INSTANCE.jobs.remove().run();}
    public static void main(String[] a){
        var world=L1World.world;
        var merchant=shop(100);
        var customer=new L1PcInstance(101,"buyer");attach(customer);
        var other=new L1PcInstance(102,"buyer2");attach(other);
        attach(merchant);
        var tx=new InventoryTransactionOwners(customer,merchant,false);
        try {new InventoryTransactionOwners(other,merchant,false);throw new AssertionError("second buyer admitted");}
        catch(IllegalStateException ok){check(!other.isItemTransferBusy(),"second buyer gate released");}
        var old=merchant.socket;old.connected=false;
        check(tx.current(merchant),"same merchant survives physical socket close");
        merchant.socket=null;
        check(tx.current(merchant),"detached merchant still owns reserved inventory");
        merchant.stamp++;
        check(!tx.current(merchant),"changed stamp rejected");
        merchant.stamp--;
        var replacement=new CUserSocket();replacement.connected=false;merchant.socket=replacement;
        check(!tx.current(merchant),"different socket rejected");
        merchant.socket=null;
        customer.socket.connected=false;
        check(!tx.current(customer),"primary customer never becomes offline owner");
        customer.socket.connected=true;
        world.players.put(merchant.id,other);
        check(!tx.current(merchant),"replaced world identity rejected");
        world.players.put(merchant.id,merchant);
        tx.release();tx.release();
        check(!customer.isItemTransferBusy()&&!merchant.isItemTransferBusy(),"idempotent gate release");
        var second=new InventoryTransactionOwners(other,merchant,false);
        check(second.current(merchant),"next buyer admitted after release");
        var c=login("merchant");
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(!c.connected&&merchant.shop&&merchant.logouts==0,"busy shop cannot retire");
        second.release();

        c=login("merchant");CacheD.INSTANCE.pending=true;
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(!c.connected&&merchant.shop,"pending owner replay cannot retire");
        CacheD.INSTANCE.pending=false;
        c=login("wrong");retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(!c.connected&&merchant.shop,"other account cannot retire shop");

        c=login("merchant");retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(c.isPrivateStoreRetirementPending()&&ThreadPool.INSTANCE.jobs.size()==1,"one retirement job");
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(ThreadPool.INSTANCE.jobs.size()==1,"duplicate re-entry coalesced");
        drain();
        check(merchant.logouts==1&&enters==1&&!c.isPrivateStoreRetirementPending()&&!merchant.isItemTransferBusy(),"close then logout then reload");

        merchant=shop(103);c=login("merchant");User.reject=true;
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);drain();
        check(!c.connected&&merchant.shop&&merchant.logouts==0&&!merchant.isItemTransferBusy(),"storage reject preserves shop");
        User.reject=false;
        c=login("merchant");retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        c.connected=false;drain();
        check(merchant.shop&&merchant.logouts==0&&!c.isPrivateStoreRetirementPending(),"stale login cannot retire");
        c=login("merchant");CIOThreadCallback.INSTANCE.reject=true;
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);ThreadPool.INSTANCE.jobs.remove().run();
        check(!c.connected&&!c.isPrivateStoreRetirementPending()&&merchant.done,"rejected callback clears pending");
        System.out.println("PASS private store retirement and owner gate ("+checks+" checks)");
    }
}
"""
with tempfile.TemporaryDirectory() as td:
    file = Path(td) / "RetirementCheck.java"
    file.write_text(fixture,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(file)],check=True)
    subprocess.run(["java","-cp",td,"RetirementCheck"],check=True)
