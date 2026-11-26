import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.Account;
import l1j.server.server.model.item.tam.L1Tam;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;

public final class UserTimerRegression {
    private static int checks;
    private static void check(boolean b, String message) {
        if (!b) throw new AssertionError(message);
        checks++;
    }
    private static final class TestAccount extends Account {
        int saves;
        int saved;
        boolean reject;
        final L1Tam handler = new L1Tam(this) {
            @Override public void SaveTimedReward(int point) {
                check(Thread.holdsLock(TestAccount.this), "account monitor");
                check(point > getTamPoint(), "no publication before admission");
                if (reject) throw new RejectedExecutionException();
                saved = point;
                saves++;
            }
        };
        @Override public L1Tam getTamHandler() { return handler; }
    }
    private static void reward() {
        TestAccount a = new TestAccount();
        a.setName("timer-test");
        a.setTamPoint(100);
        check(!a.AddTimedTamPoint(10, -100, 100), "first tick arms at zero");
        check(!a.AddTimedTamPoint(10, -1, 100), "not due");
        check(a.tryBeginTamAward(), "receipt reservation");
        check(!a.AddTimedTamPoint(10, 0, 100), "pending receipt excludes timed pay");
        a.endTamAward();
        a.reject = true;
        try { a.AddTimedTamPoint(10, 0, 100); throw new AssertionError("expected rejection"); }
        catch (RejectedExecutionException ok) { checks++; }
        check(a.getTamPoint() == 100 && a.saves == 0, "rejected balance untouched");
        a.reject = false;
        check(a.AddTimedTamPoint(10, 0, 100), "rejected deadline retried");
        check(a.getTamPoint() == 110 && a.saves == 1 && a.saved == 110, "exact once payment");
        check(!a.AddTimedTamPoint(10, 0, 100), "duplicate tick");
        check(!a.AddTimedTamPoint(10, 99, 100), "next due not early");
        check(a.AddTimedTamPoint(20, 100, 100), "second interval");
        check(a.getTamPoint() == 130 && a.saves == 2, "second exact payment");
        a.setTamPoint(Integer.MAX_VALUE - 5);
        check(!a.AddTimedTamPoint(10, 200, 100), "overflow rejected");
        check(a.saves == 2, "no extra persistence");
    }
    private static Method method(Class<?> c, String name, Class<?>... args) throws Exception {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true); return m;
    }
    private static void expiryHeap() throws Exception {
        String prefix = "l1j.server.server.model.character.scheduler.CInventoryExpiry";
        Class<?> heapClass = Class.forName(prefix + "$IndexedExpiryHeap");
        Class<?> taskClass = Class.forName(prefix + "$ExpiryTask");
        var hc = heapClass.getDeclaredConstructor(); hc.setAccessible(true);
        var tc = taskClass.getDeclaredConstructor(L1PcInstance.class,L1ItemInstance.class,long.class,long.class,int.class);
        tc.setAccessible(true);
        Method offer = method(heapClass,"offer",taskClass), remove = method(heapClass,"remove",taskClass);
        Method poll = method(heapClass,"poll"), size = method(heapClass,"size");
        Method delay = method(heapClass,"nextDelayMs",long.class), clear = method(heapClass,"clear");
        Object heap = hc.newInstance();
        Object late = tc.newInstance(null,null,0L,60000L,1);
        Object early = tc.newInstance(null,null,0L,-1000L,1);
        offer.invoke(heap,late); offer.invoke(heap,early);
        check((long)delay.invoke(heap,250L)==1L, "expired deadline ready");
        check(poll.invoke(heap)==early, "earliest expiry first");
        check(poll.invoke(heap)==null, "future not processed early");
        check((boolean)remove.invoke(heap,late), "far deadline cancelled immediately");
        check(!(boolean)remove.invoke(heap,late), "cancel idempotent");
        check((int)size.invoke(heap)==0 && (long)delay.invoke(heap,250L)==250L, "idle");
        Object[] all = new Object[4096];
        for (int i=0;i<all.length;i++) {
            all[i]=tc.newInstance(null,null,0L,60000L+i,1);
            offer.invoke(heap,all[i]);
        }
        for (int i=all.length-1;i>=0;i-=2) check((boolean)remove.invoke(heap,all[i]),"indexed removal "+i);
        check((int)size.invoke(heap)==2048,"remaining heap");
        clear.invoke(heap);
        check((int)size.invoke(heap)==0,"shutdown clear");
        for (int i=0;i<all.length;i++) check(!(boolean)remove.invoke(heap,all[i]),"retired index "+i);
    }
    public static void main(String[] args) throws Exception {
        reward(); expiryHeap();
        System.out.println("PASS User timer regression (" + checks + " checks)");
    }
}
