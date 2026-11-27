"""Compile actual owner admission and detached-shop retirement methods using isolated doubles."""
from pathlib import Path
import tempfile
import subprocess

root = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model"
owners = (root / "inventory/InventoryTransactionOwners.java").read_text(encoding="utf-8")
owners = owners[owners.index("final class InventoryTransactionOwners"):]
owners = owners.replace("final class InventoryTransactionOwners", "static final class InventoryTransactionOwners", 1)
login = (root / "network/core/cs/packet/CS_EnterWorld.java").read_text(encoding="utf-8")
login = login[login.index("    private static boolean currentPrivateStoreLogin("):login.index("    private static EnterData loadEnterData(")]
socket = (root / "network/core/connection/CUserSocket.java").read_text(encoding="utf-8")
socket = socket[socket.index("    private volatile boolean privateStoreRetirementPending;"):socket.index("    private volatile PlayerShopView playerShopView;")]
game = (root.parent / "GameServer.java").read_text(encoding="utf-8")
game = game[game.index("    public static boolean tryDisconnectChar("):game.index("public static void disconnectChar(String name)")].rstrip()
fixture = r"""
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.RejectedExecutionException;
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
        boolean tryKickAndLogout(L1PcInstance expected, int stamp){
            if (pc!=expected || pc.getNetConnection()!=this || pc.stamp!=stamp) return false;
            complete=true;return true;
        }
""" + socket + r"""
    }
    static class CWorld {
        static CWorld world=new CWorld();
        static CWorld getInstance(){return world;}
        final Map<Integer,L1PcInstance> players=new HashMap<>();
        Object findObject(int id){return players.get(id);}
    }
    static class CacheD {
        static final CacheD INSTANCE=new CacheD();
        boolean pending,quarantined;
        boolean isOwnerPersistenceQuarantined(int id){return quarantined;}
        static CacheD getInstance(){return INSTANCE;}
        boolean hasPendingOwnerApply(int id){return pending;}
    }
    static class LoginController {
        static final LoginController INSTANCE=new LoginController();
        CUserSocket current;
        static LoginController getInstance(){return INSTANCE;}
        CUserSocket getClientByAccount(String a){return current;}
    }
    static class CSessionLifecycle {
        static final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        static void retireCharacter(Runnable r){jobs.add(r);}
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
        L1PcInstance pc; int token; Runnable afterReserve;
        boolean tryBeginEquipmentChange(){token=pc.tryReserveItemTransfer();
            if(token!=0 && afterReserve!=null) afterReserve.run();
            return token!=0;}
        void endEquipmentChange(){pc.endItemTransfer(token);token=0;}
    }
    static class L1PcInstance {
        int id,stamp=1, logouts, trade;
        boolean shop,dead,done,quarantined,display,continuation;
        boolean hasItemTransferContinuation(){return continuation;}
        int deletes;
        record Opening(boolean open,int generation){}
        Opening opening;
        Opening privateStoreState() {
            if(opening==null||opening.open()!=shop) opening=new Opening(shop, opening==null?1:opening.generation()+1);
            return opening;
        }
        boolean closePrivateStore(int generation){
            check(isItemTransferBusy(),"administrative close requires merchant gate");
            if (!shop||privateStoreState().generation()!=generation)return false;
            if (!display && User.reject)throw new RejectedExecutionException();
            if (!display)deletes++;
            shop=false;return true;
        }
        String account;
        CUserSocket socket;
        final Inventory inv=new Inventory();
        final AtomicInteger gate=new AtomicInteger();
        L1PcInstance(int id,String a){this.id=id;account=a;inv.pc=this;CWorld.world.players.put(id,this);}
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
        boolean ownsItemTransferToken(int t){return (t&1)!=0&&gate.get()==t;}
        boolean isItemTransferReplaySession(CUserSocket s,int st,int t){
            return ownsItemTransferToken(t)&&!dead&&s!=null&&socket==s&&s.pc==this&&stamp==st;
        }
        boolean tryBeginItemTransferContinuation(CUserSocket s,int st,int t){return false;}
        boolean isItemTransferContinuationSession(CUserSocket s,int st,int t){return false;}
        boolean beginRunningItemTransferContinuation(CUserSocket s,int st,int t){return false;}
        void endItemTransferContinuation(int t){}
        void quarantineItemTransferSession(CUserSocket s,int st){quarantined=true;}
        void logout(){check(isItemTransferBusy(),"logout must own inventory gate");dead=true;done=true;logouts++;CWorld.world.players.remove(id,this);}
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
""" + login + owners + game + r"""
    static CUserSocket login(String name){
        var s=new CUserSocket();s.account=new Account(name);
        LoginController.INSTANCE.current=s;return s;
    }
    static void attach(L1PcInstance p){
        p.socket=new CUserSocket();p.socket.pc=p;
        p.socket.state=CConnectionState.IN_GAME;
    }
    static L1PcInstance shop(int id){var p=new L1PcInstance(id,"merchant");p.shop=true;return p;}
    static void drain(){CSessionLifecycle.jobs.remove().run();CIOThreadCallback.INSTANCE.jobs.remove().run();}
    public static void main(String[] a){
        var world=CWorld.world;
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
        check(c.isPrivateStoreRetirementPending()&&CSessionLifecycle.jobs.size()==1,"one retirement job");
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);
        check(CSessionLifecycle.jobs.size()==1,"duplicate re-entry coalesced");
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
        retirePrivateStoreForLogin("m",c,c.account,world,merchant);CSessionLifecycle.jobs.remove().run();
        check(!c.connected&&!c.isPrivateStoreRetirementPending()&&merchant.done,"rejected callback clears pending");
        var adminTarget=shop(201);
        int reserved=adminTarget.tryReserveItemTransfer();
        check(!tryDisconnectChar(adminTarget)&&adminTarget.shop&&adminTarget.logouts==0,"busy detached merchant not retired");
        adminTarget.endItemTransfer(reserved);
        CacheD.INSTANCE.pending=true;
        check(!tryDisconnectChar(adminTarget)&&!adminTarget.isItemTransferBusy(),"pending receipt blocks and releases gate");
        CacheD.INSTANCE.pending=false;
        adminTarget.continuation=true;
        check(!tryDisconnectChar(adminTarget)&&adminTarget.deletes==0,"active continuation preserved");
        adminTarget.continuation=false;
        CacheD.INSTANCE.quarantined=true;
        check(!tryDisconnectChar(adminTarget)&&adminTarget.logouts==0,"quarantined owner not treated as ordinary retirement");
        CacheD.INSTANCE.quarantined=false;
        adminTarget.inv.afterReserve=()->adminTarget.opening=new L1PcInstance.Opening(true,8);
        check(!tryDisconnectChar(adminTarget)&&adminTarget.deletes==0&&adminTarget.shop,"replacement opening survives");
        adminTarget.inv.afterReserve=null;
        User.reject=true;
        check(!tryDisconnectChar(adminTarget)&&adminTarget.shop&&adminTarget.logouts==0
                &&!adminTarget.isItemTransferBusy(),"rejected delete leaves merchant live");
        User.reject=false;
        check(tryDisconnectChar(adminTarget)&&adminTarget.deletes==1&&adminTarget.logouts==1
                &&!adminTarget.isItemTransferBusy(),"exact merchant deleted then retired");
        check(!tryDisconnectChar(adminTarget)&&adminTarget.deletes==1,"stale actor cannot delete replacement");
        var ad=shop(202);ad.display=true;User.reject=true;
        check(tryDisconnectChar(ad)&&ad.deletes==0&&ad.logouts==1,"display-only merchant retires without SQL");
        User.reject=false;
        var online=shop(203);attach(online);
        check(tryDisconnectChar(online)&&online.socket.complete&&online.shop,"online kick uses transport drain");
        online.socket.pc=null;
        check(!tryDisconnectChar(online),"replaced online actor rejects kick");
        System.out.println("PASS private store retirement and owner gate ("+checks+" checks)");
    }
}
"""
with tempfile.TemporaryDirectory() as td:
    file = Path(td) / "RetirementCheck.java"
    file.write_text(fixture,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(file)],check=True)
    subprocess.run(["java","-cp",td,"RetirementCheck"],check=True)
