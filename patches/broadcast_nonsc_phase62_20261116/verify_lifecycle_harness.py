#!/usr/bin/env python3
"""Compile/run real phase62 CSharedBroadcast and CAsyncSendConstBuffer with
transport/world stubs to test eligibility, ordering and SEND source lifetime.
This is deliberately NOT a Windows IOCP integration test.
"""
import zipfile,hashlib,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ARCHIVE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
PIN="556a289819e5f746c3ef0ba217cef54b981274ef"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
work=Path(sys.argv[1] if len(sys.argv)>1 else "/tmp/broadcast-phase62-lifecycle")
assert blob(ARCHIVE.read_bytes())==PIN
with zipfile.ZipFile(ARCHIVE) as z:
    assert z.testzip() is None
    files={k:z.read(k) for k in [
      "src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java",
      "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java",
      "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBufferExceptSelf.java"
    ]}
for n,b in files.items():
    o=work/n;o.parent.mkdir(parents=True,exist_ok=True);o.write_bytes(b)

FAKES={
"src/l1j/server/server/model/network/core/transport/CIOServer.java":r"""
package l1j.server.server.model.network.core.transport;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;
public final class CIOServer {
    public final AtomicInteger releases=new AtomicInteger();
    public void releaseConstBuffer(MemorySegment handle) { releases.incrementAndGet(); }
}
""",
"src/l1j/server/server/model/network/core/connection/CUserSocket.java":r"""
package l1j.server.server.model.network.core.connection;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.transport.CIOServer;
public class CUserSocket {
    public static final List<Integer> ORDER=Collections.synchronizedList(new ArrayList<>());
    public final int recipientId;
    public final CIOServer owner;
    public volatile boolean online=true, nativeSupport=true, failNativeCreate=false;
    public final AtomicInteger nativeCreates=new AtomicInteger();
    public final AtomicInteger nativeSends=new AtomicInteger();
    public final AtomicInteger frozenSends=new AtomicInteger();
    private final Queue<CAsyncSendConstBuffer.Source> inFlight=new ConcurrentLinkedQueue<>();
    public CUserSocket(int id, CIOServer s) { recipientId=id; owner=s; }
    public boolean isClientConnected() { return online; }
    public boolean supportsConstBuffer() { return nativeSupport; }
    public int pendingSendBytes() { return 0; }
    public CAsyncSendConstBuffer.Source createConstBuffer(byte[] bytes) {
        if(failNativeCreate)throw new IllegalStateException("synthetic native allocation failure");
        nativeCreates.incrementAndGet();
        return new CAsyncSendConstBuffer.Source(owner,MemorySegment.NULL,bytes);
    }
    public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source s) {
        return s.owner==owner;
    }
    public boolean enqueueConstBufferConfirmed(CAsyncSendConstBuffer.Source s,int limit) {
        if(!online)return false;
        inFlight.add(s.retain());
        nativeSends.incrementAndGet();
        ORDER.add(recipientId);
        return true;
    }
    public boolean sendFrozenWire(byte[] bytes) {
        if(!online)return false;
        if(bytes.length<3 || ((bytes[0]&255)|((bytes[1]&255)<<8))!=bytes.length)
            throw new AssertionError("bad frozen frame");
        frozenSends.incrementAndGet();
        ORDER.add(recipientId);
        return true;
    }
    public void finishAll() {
        CAsyncSendConstBuffer.Source s;
        while((s=inFlight.poll())!=null)s.release();
    }
    public static List<Integer> order() {
        synchronized(ORDER){return new ArrayList<>(ORDER);}
    }
    public static void clearOrder() { ORDER.clear(); }
}
""",
"src/l1j/server/server/model/network/core/session/CIOClient.java":r"""
package l1j.server.server.model.network.core.session;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CIOClient {
    public boolean connected=true;
    private final CUserSocket socket;
    public CIOClient(CUserSocket s) {socket=s;}
    public boolean isConnected() {return connected;}
    public CUserSocket getConnection() {return socket;}
}
""",
"src/l1j/server/server/model/L1Object.java":r"""
package l1j.server.server.model;
public class L1Object {
    private final int id;
    public L1Object(int id) {this.id=id;}
    public int getId() {return id;}
}
""",
"src/l1j/server/server/model/Instance/L1PcInstance.java":r"""
package l1j.server.server.model.Instance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.core.session.CIOClient;
public final class L1PcInstance extends L1Object {
    public final CIOClient client;
    public boolean gm, privateShop, inWorld=true;
    public int teamId;
    public L1PcInstance(int id,CIOClient client) {super(id);this.client=client;}
    public CIOClient getNetConnection() {return client;}
    public boolean isGm() {return gm;}
    public int get_current_combat_team_id() {return teamId;}
    public boolean isPrivateShop() {return privateShop;}
    public boolean is_world() {return inWorld;}
}
""",
"src/l1j/server/server/model/L1World.java":r"""
package l1j.server.server.model;
import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1World {
    public static final L1World W=new L1World();
    public static L1World getInstance() {return W;}
    public List<L1PcInstance> all=new ArrayList<>();
    public List<L1PcInstance> visible=new ArrayList<>();
    public int borrowed,returned;
    public List<L1PcInstance> getAllPlayers() {return all;}
    public List<L1PcInstance> getVisiblePlayers(int mapId) {return visible;}
    public List<L1PcInstance> getVisiblePlayer(L1Object anchor) {return visible;}
    public List<L1PcInstance> getVisiblePlayer(L1Object anchor,int radius) {return visible;}
    public List<L1PcInstance> borrowVisiblePlayerFast(L1Object anchor) {borrowed++;return visible;}
    public void releaseVisiblePlayerFast(List<L1PcInstance> list) {
        if(list!=visible)throw new AssertionError("returned a different borrowed list");
        returned++;
    }
}
""",
"src/l1j/server/server/model/network/core/broadcast/CTLMFilter.java":r"""
package l1j.server.server.model.network.core.broadcast;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
public final class CTLMFilter {
    public static boolean sameWorldOther(L1Object a,L1PcInstance p) {
        return a!=null && p!=null && a.getId()!=p.getId();
    }
}
""",
"src/SharedBroadcastLifecycleTest.java":r"""
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.broadcast.*;
import l1j.server.server.model.network.core.connection.*;
import l1j.server.server.model.network.core.session.*;
import l1j.server.server.model.network.core.transport.*;

public final class SharedBroadcastLifecycleTest {
    private static void yes(boolean b,String s) {if(!b)throw new AssertionError(s);}
    private static void same(int... expected) {
        List<Integer> now=CUserSocket.order();
        List<Integer> want=new ArrayList<>();
        for(int x:expected)want.add(x);
        yes(now.equals(want),"recipient order or duplicates wrong; expected "+want+" actual "+now);
    }
    public static void main(String[] args) throws Exception {
        byte[] frame=new byte[]{4,0,17,127};
        CIOServer first=new CIOServer(),second=new CIOServer();
        CUserSocket one=new CUserSocket(11,first);
        CUserSocket two=new CUserSocket(22,second);
        CUserSocket three=new CUserSocket(33,first);
        CUserSocket offline=new CUserSocket(44,first);
        offline.online=false;
        L1PcInstance a=new L1PcInstance(11,new CIOClient(one));
        L1PcInstance b=new L1PcInstance(22,new CIOClient(two));
        L1PcInstance c=new L1PcInstance(33,new CIOClient(three));
        L1PcInstance d=new L1PcInstance(44,new CIOClient(offline));
        L1World world=L1World.getInstance();
        world.all=new ArrayList<>(Arrays.asList(a,null,b,a,d,c));
        CSharedBroadcast.sendAll(frame);
        same(11,22,11,33); // duplicates and world iteration order retained
        one.finishAll();two.finishAll();three.finishAll();
        yes(first.releases.get()==1,"source released once after all in-flight sends");
        yes(second.releases.get()==0,"non-owner uses frozen wire");
        yes(one.nativeCreates.get()==1,"once per shared fanout");
        yes(two.frozenSends.get()==1,"cross-owner frozen frame");
        CUserSocket.clearOrder();

        CSharedBroadcast.sendArray(new L1PcInstance[]{b,null,a,b},frame);
        same(22,11,22);
        one.finishAll();two.finishAll();three.finishAll();
        yes(second.releases.get()==1,"second owner's first native source released");
        CUserSocket.clearOrder();

        CSharedBroadcast.sendRecipients(Arrays.asList(c,d,a,c),frame);
        same(33,11,33);
        one.finishAll();two.finishAll();three.finishAll();
        CUserSocket.clearOrder();

        world.visible=new ArrayList<>(Arrays.asList(a,b,null,c));
        CSharedBroadcast.send(a,frame,true,-1,null);
        same(22,33); // excludes self and null
        yes(world.borrowed==1 && world.returned==1,"borrowed AOI list released");
        one.finishAll();two.finishAll();three.finishAll();
        CUserSocket.clearOrder();

        CSharedBroadcast.sendGroups(Arrays.asList(b,a),Arrays.asList(b),frame);
        same(22,11,22);
        one.finishAll();two.finishAll();three.finishAll();
        CUserSocket.clearOrder();

        a.gm=true;c.gm=true;
        CSharedBroadcast.sendAllGms(frame);
        same(11,11,33);
        one.finishAll();two.finishAll();three.finishAll();
        CUserSocket.clearOrder();

        CAsyncSendConstBuffer lease=new CAsyncSendConstBuffer(frame,Integer.MIN_VALUE,Integer.MAX_VALUE);
        yes(lease.send(one,11),"first owner send refused");
        final int before=first.releases.get();
        final int producers=12,each=1500;
        ExecutorService exec=Executors.newFixedThreadPool(producers);
        final AtomicInteger accepted=new AtomicInteger();
        List<Future<?>> futures=new ArrayList<>();
        for(int thread=0;thread<producers;thread++)
            futures.add(exec.submit(()->{
                for(int i=0;i<each;i++) {
                    if(lease.send((i&1)==0?one:two,(i&1)==0?11:22))
                        accepted.incrementAndGet();
                }
            }));
        for(Future<?> f:futures)f.get(60,TimeUnit.SECONDS);
        exec.shutdown();
        yes(accepted.get()==producers*each,"concurrent fanout admitted count");
        lease.close();
        lease.close();
        yes(first.releases.get()==before,"native owner released with outstanding leases");
        one.finishAll();two.finishAll();three.finishAll();
        yes(first.releases.get()==before+1,"native owner must release once after all ACKs");
        boolean threw=false;
        try{lease.send(one,11);}catch(IllegalStateException expected){threw=true;}
        yes(threw,"send-after-close not rejected");

        byte[] untrusted=new byte[]{4,0,17,50};
        CUserSocket onlyFrozen=new CUserSocket(55,first);
        onlyFrozen.nativeSupport=false;
        CAsyncSendConstBuffer copy=new CAsyncSendConstBuffer(untrusted,Integer.MIN_VALUE,Integer.MAX_VALUE);
        untrusted[3]=9;
        yes(copy.send(onlyFrozen,55),"frozen send refused");
        copy.close();
        yes(onlyFrozen.frozenSends.get()==1,"frozen transport not used");
        boolean invalid=false;
        try{new CAsyncSendConstBuffer(new byte[]{4,0,17},0,1);}
        catch(IllegalArgumentException expected){invalid=true;}
        yes(invalid,"invalid frame accepted");

        CUserSocket failNative=new CUserSocket(66,first);
        failNative.failNativeCreate=true;
        try(CAsyncSendConstBuffer fallback=new CAsyncSendConstBuffer(frame,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            yes(fallback.send(failNative,66),"native resource failure should retain FIFO frozen send");
        }
        yes(failNative.frozenSends.get()==1,"native allocation failure path not exercised");

        CAsyncSendConstBuffer.Source disposed=new CAsyncSendConstBuffer.Source(
                first,java.lang.foreign.MemorySegment.NULL,frame);
        disposed.release();
        boolean retainedAfterRelease=false;
        try{disposed.retain();}catch(IllegalStateException expected){retainedAfterRelease=true;}
        yes(retainedAfterRelease,"revived released source");

        System.out.println("PHASE62_SHARED_LIFECYCLE_PASS fanouts=6 recipients=15 concurrent_sends="
                +accepted.get()+" ownerReleases="+first.releases.get()
                +" crossOwnerFrozen="+two.frozenSends.get()
                +" borrowReturns="+world.returned);
    }
}
"""
}
for n,s in FAKES.items():
    p=work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding="utf-8")
print("LIFECYCLE_HARNESS_PREPARED",len(files),"production",len(FAKES),"stubs")
