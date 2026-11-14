package l1j.server.server.model.network.core.connection;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.utill.ConnectionState;

public final class UserSocketContract {
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    static boolean allow(int op, ConnectionState state, boolean account, boolean pc, boolean ready) {
        return CUserSocket.allowed(op & 255, state, account, pc, ready);
    }
    public static void main(String[] args) throws Exception {
        check(allow(Opcodes.C_LOGIN, ConnectionState.CONNECTED, false, false, false), "initial L1 login denied");
        check(!allow(Opcodes.C_LOGIN, ConnectionState.AUTHENTICATED, true, false, false), "repeated login admitted");
        check(allow(Opcodes.C_ENTER_WORLD, ConnectionState.AUTHENTICATED, true, false, false), "character entry denied");
        check(!allow(Opcodes.C_ENTER_WORLD, ConnectionState.CONNECTED, false, false, false), "entry before auth");
        check(!allow(Opcodes.C_ENTER_WORLD, ConnectionState.IN_GAME, true, true, true), "entry replaced active player");
        for (int op : new int[]{Opcodes.C_MOVE, Opcodes.C_ATTACK, Opcodes.C_USE_ITEM, Opcodes.C_USE_SPELL}) {
            check(allow(op, ConnectionState.IN_GAME, true, true, true), "ready gameplay denied");
            check(!allow(op, ConnectionState.IN_GAME, true, true, false), "half-loaded player admitted");
            check(!allow(op, ConnectionState.AUTHENTICATED, true, false, false), "gameplay without character");
        }
        for (int op : new int[]{Opcodes.C_EXTENDED, Opcodes.C_EXTENDED_PROTOBUF, Opcodes.C_CHANNEL, Opcodes.C_HYPERTEXT_INPUT_RESULT}) {
            check(allow(op, ConnectionState.CONNECTED, false, false, false), "prelogin multiplexed protocol denied");
            check(allow(op, ConnectionState.IN_GAME, true, true, true), "in-game multiplexed protocol denied");
        }
        for (int op=0;op<256;op++) check(!CUserSocket.allowed(op,ConnectionState.DISCONNECTED,true,true,true), "closed session admitted");
        check(CUserSocket.sessionOwner(Opcodes.C_LOGIN & 255,false), "login escaped session owner");
        check(CUserSocket.sessionOwner(Opcodes.C_EXTENDED_PROTOBUF & 255,true), "multiplexed session escaped owner");
        check(!CUserSocket.sessionOwner(Opcodes.C_ATTACK & 255,true), "gameplay packet sent to timer owner");
        System.out.println("PASS production L1 opcode/state matrix, loading barrier, multiplexed protocol compatibility, native game/session-owner execution roles");

        CUserSocketRegistry<Object> registry = new CUserSocketRegistry<>();
        Object old = new Object(), replacement = new Object();
        check(registry.register(old) && registry.register(replacement), "registry admission");
        registry.retire(old); check(!registry.isActive(old) && registry.isActive(replacement), "early unregister affected replacement");
        check(registry.transportDrained(old) && !registry.transportDrained(old), "terminal event repeated");
        check(registry.pendingCleanup()==1 && registry.retained().contains(old), "old socket released before L1 logout");
        registry.cleanupComplete(old); check(!registry.retained().contains(old) && registry.isActive(replacement), "old cleanup removed replacement");
        registry.cleanupComplete(replacement); check(registry.retained().contains(replacement), "cleanup raced ahead of I/O drain");
        check(registry.transportDrained(replacement) && registry.retained().isEmpty(), "two-phase release leak");
        var pool = Executors.newFixedThreadPool(8);
        try {
            AtomicInteger terminals = new AtomicInteger();
            var futures = new ArrayList<Future<?>>();
            for (int n=0;n<400;n++) {
                Object socket=new Object(); registry.register(socket);
                futures.add(pool.submit(() -> { registry.retire(socket); if(registry.transportDrained(socket)) terminals.incrementAndGet(); }));
                futures.add(pool.submit(() -> { registry.cleanupComplete(socket); if(registry.transportDrained(socket)) terminals.incrementAndGet(); }));
            }
            for(Future<?> future:futures)future.get();
            check(terminals.get()==400 && registry.retained().isEmpty(), "concurrent terminal/cleanup lost lifetime or counted twice");
            AtomicInteger logout = new AtomicInteger(), cleanup = new AtomicInteger();
            CUserSocketCloseTask task = new CUserSocketCloseTask(() -> true, logout::incrementAndGet, cleanup::incrementAndGet);
            futures.clear();for(int n=0;n<100;n++)futures.add(pool.submit(task));
            for(Future<?> future:futures)future.get();
            check(logout.get()==1 && cleanup.get()==1,"deferred close executed twice after retry");
            new CUserSocketCloseTask(() -> false, logout::incrementAndGet, cleanup::incrementAndGet).run();
            check(logout.get()==1 && cleanup.get()==2,"stale/replaced session was logged out");
            try {new CUserSocketCloseTask(() -> true, () -> {throw new IllegalStateException("fixture");}, cleanup::incrementAndGet).run();}
            catch(IllegalStateException expected){}
            check(cleanup.get()==3,"failed logout skipped final cleanup");
        } finally {pool.shutdownNow();}
        System.out.println("PASS production registry/deferred close: early retirement, I/O+logout fence, replacement identity, 400 concurrent close races, once-only stale-safe cleanup");
    }
}
